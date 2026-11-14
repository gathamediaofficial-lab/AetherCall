package com.aether.call.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.content.ContextCompat

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
    val simDisplayName: String = "IND-JIO",
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
                Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val callList = mutableListOf<CallRecord>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)

                var count = 0
                while (cursor.moveToNext() && (count < 50)) {
                    val id = if (idIndex != -1) cursor.getLong(idIndex) else count.toLong()
                    val number = if (numberIndex != -1) cursor.getString(numberIndex) ?: "Unknown" else "Unknown"
                    val name = if (nameIndex != -1) cursor.getString(nameIndex) else null
                    val type = if (typeIndex != -1) cursor.getInt(typeIndex) else CallLog.Calls.INCOMING_TYPE
                    val date = if (dateIndex != -1) cursor.getLong(dateIndex) else 0L
                    val duration = if (durationIndex != -1) cursor.getLong(durationIndex) else 0L

                    callList.add(
                        CallRecord(
                            id = id,
                            number = number,
                            name = name,
                            type = type,
                            date = date,
                            duration = duration
                        )
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
    fun placeCarrierCall(context: Context, number: String, isVideo: Boolean = false) {
        val cleanNumber = number.trim()
        if (cleanNumber.isBlank()) return
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (telecomManager != null) {
                val uri = Uri.fromParts("tel", cleanNumber, null)
                val extras = android.os.Bundle().apply {
                    if (isVideo) {
                        putInt(
                            TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                            VideoProfile.STATE_BIDIRECTIONAL,
                        )
                    }
                }
                telecomManager.placeCall(uri, extras)
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}