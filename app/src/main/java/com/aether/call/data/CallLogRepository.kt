package com.aether.call.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.aether.call.telecom.CallManager

/**
 * Data model for a call history record.
 */
data class CallRecord(
    val id: Long,
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long,
    val simDisplayName: String = "SIM",
)

/**
 * UI Data model for a call log item with SIM and display information.
 */
data class CallLogItem(
    val id: Long,
    val name: String?,
    val number: String,
    val type: Int,
    val dateMillis: Long,
    val durationSeconds: Long,
    val simDisplayName: String = "SIM",
    val repeatCount: Int = 1,
    val isWifiCall: Boolean = false,
)

/**
 * Repository accessing Android system ContentProviders for call logs and placing carrier calls.
 */
object CallLogRepository {

    /**
     * Retrieves up to 50 recent call records from system CallLog provider.
     */
    fun getRecentCalls(context: Context): List<CallRecord> {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val callList = mutableListOf<CallRecord>()

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null,
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)

                val accountIdIndex = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                val subIdIdx = cursor.getColumnIndex("sub_id").takeIf { it != -1 }
                    ?: cursor.getColumnIndex("subscription_id").takeIf { it != -1 }
                val simSlotIdx = cursor.getColumnIndex("sim_id").takeIf { it != -1 }
                    ?: cursor.getColumnIndex("sim_slot").takeIf { it != -1 }

                var count = 0
                while (cursor.moveToNext() && (count < 50)) {
                    val id = if (idIndex != -1) cursor.getLong(idIndex) else count.toLong()
                    val number = if (numberIndex != -1) cursor.getString(numberIndex) ?: "Unknown" else "Unknown"
                    val name = if (nameIndex != -1) cursor.getString(nameIndex) else null
                    val type = if (typeIndex != -1) cursor.getInt(typeIndex) else CallLog.Calls.INCOMING_TYPE
                    val date = if (dateIndex != -1) cursor.getLong(dateIndex) else 0L
                    val duration = if (durationIndex != -1) cursor.getLong(durationIndex) else 0L

                    val phoneAccountId = if (accountIdIndex != -1) cursor.getString(accountIdIndex) else null
                    val subIdVal = if (subIdIdx != null) cursor.getInt(subIdIdx) else null
                    val simSlotVal = if (simSlotIdx != null) cursor.getInt(simSlotIdx) else null

                    val resolvedSim = com.aether.call.util.SimAccountResolver.resolveSimForCallLog(
                        context = context,
                        phoneAccountId = phoneAccountId,
                        subIdColumnValue = subIdVal,
                        simSlotColumnValue = simSlotVal,
                    )

                    val simDisplayName = resolvedSim.displayLabel

                    callList.add(
                        CallRecord(
                            id = id,
                            number = number,
                            name = name,
                            type = type,
                            date = date,
                            duration = duration,
                            simDisplayName = simDisplayName,
                        ),
                    )
                    count++
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }

        return callList
    }

    /**
     * Places a carrier telephone or video call using system TelecomManager.
     */
    @Suppress("MissingPermission")
    fun placeCarrierCall(
        context: Context,
        number: String,
        isVideo: Boolean = false,
        preferredAccountHandle: PhoneAccountHandle? = null,
    ) {
        if (isVideo) {
            placeCarrierVideoCall(context, number, preferredAccountHandle)
            return
        }
        val cleanNumber = number.trim()
        if (cleanNumber.isBlank()) return
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (telecomManager != null) {
                val uri = Uri.fromParts("tel", cleanNumber, null)
                val extras = Bundle().apply {
                    if (preferredAccountHandle != null) {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, preferredAccountHandle)
                    }
                }
                telecomManager.placeCall(uri, extras)
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun placeCarrierVideoCall(
        context: Context,
        rawNumber: String,
        preferredAccountHandle: PhoneAccountHandle? = null,
    ) {
        val cleanNumber = rawNumber.trim()
        if (cleanNumber.isBlank()) {
            Toast.makeText(context, "Phone number is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val hasCallPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCallPerm) {
            Toast.makeText(context, "Please grant Phone permission first", Toast.LENGTH_SHORT).show()
            return
        }

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val uri = Uri.fromParts("tel", cleanNumber, null)

        // 1. Pre-stage CallManager & CallBridge in Video Call mode BEFORE Telecom places the call
        CallManager.prepareOutgoingVideoCall()
        com.aether.call.telecom.CallBridge.callerNumber.value = cleanNumber

        // 2. Resolve a guaranteed non-null PhoneAccountHandle (even when Dual-SIM is set to "Ask every time")
        val accountToUse: PhoneAccountHandle? = preferredAccountHandle ?: run {
            try {
                val capableAccounts = telecomManager?.callCapablePhoneAccounts.orEmpty()
                val videoCapable = capableAccounts.firstOrNull { handle ->
                    val account = telecomManager?.getPhoneAccount(handle)
                    account?.hasCapabilities(PhoneAccount.CAPABILITY_VIDEO_CALLING) == true
                }
                videoCapable
                    ?: telecomManager?.getDefaultOutgoingPhoneAccount("tel")
                    ?: capableAccounts.firstOrNull()
            } catch (_: Exception) {
                null
            }
        }

        val extras = Bundle().apply {
            putInt(
                TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                VideoProfile.STATE_BIDIRECTIONAL,
            )
            putBoolean(
                TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE,
                true,
            )
            if (accountToUse != null) {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountToUse)
            }
        }

        try {
            if (telecomManager != null) {
                telecomManager.placeCall(uri, extras)
            } else {
                val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                    putExtras(extras)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
            }

            // 3. Immediately open InCallActivity so the user sees the outgoing video call screen right away
            val openUiIntent = Intent(context, com.aether.call.ui.InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("CALL_NUMBER", cleanNumber)
                putExtra("CALL_DIRECTION", "OUTGOING_VIDEO")
            }
            context.startActivity(openUiIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Unable to start video call: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
