package com.aether.call.telecom

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Hardware SIM account information model representing an active telephony subscription line.
 */
data class SimAccountInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val phoneAccountHandle: PhoneAccountHandle?,
)

/**
 * Utility for querying active dual-SIM slots and dispatching calls via specific telephony lines.
 */
object SimManager {

    /**
     * Detects physically active hardware SIM subscriptions and matches them with call-capable PhoneAccountHandles.
     * Eliminates ghost/duplicate SIM detection and reliably resolves real carrier names.
     */
    @SuppressLint("MissingPermission")
    fun getActiveSims(context: Context): List<SimAccountInfo> {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        @Suppress("DEPRECATION")
        val subInfoList: List<SubscriptionInfo> = try {
            subscriptionManager.activeSubscriptionInfoList ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }

        if (subInfoList.isEmpty()) {
            return emptyList()
        }

        // Strictly filter for physically active subscriptions with valid slotIndex (simSlotIndex >= 0)
        val validSubscriptions = subInfoList.filter { it.simSlotIndex >= 0 }
        if (validSubscriptions.isEmpty()) {
            return emptyList()
        }

        @Suppress("DEPRECATION")
        val phoneAccountHandles: List<PhoneAccountHandle> = try {
            telecomManager?.callCapablePhoneAccounts ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }

        return validSubscriptions.map { info ->
            val subId = info.subscriptionId
            val slotIndex = info.simSlotIndex

            // Carrier name resolution hierarchy:
            // 1. Primary: info.carrierName
            val rawCarrierName = info.carrierName?.toString()?.takeIf { it.isNotBlank() }

            // 2. Fallback 1: info.displayName
            val rawDisplayName = info.displayName?.toString()?.takeIf { it.isNotBlank() }

            // 3. Fallback 2: TelephonyManager.createForSubscriptionId().simOperatorName / networkOperatorName
            val subTelephonyManager = try {
                telephonyManager?.createForSubscriptionId(subId)
            } catch (_: Exception) {
                null
            }
            val simOperatorName = subTelephonyManager?.simOperatorName?.takeIf { it.isNotBlank() }
                ?: subTelephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() }

            val trueCarrierName = rawCarrierName
                ?: rawDisplayName
                ?: simOperatorName
                ?: "SIM ${slotIndex + 1}"

            val displayName = rawDisplayName ?: trueCarrierName

            // Match phone account handle corresponding to this subscription ID
            val matchingHandle = phoneAccountHandles.find { handle ->
                handle.id == subId.toString() ||
                        handle.id.contains(subId.toString()) ||
                        (telecomManager != null && try {
                            val account = telecomManager.getPhoneAccount(handle)
                            account?.hasCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER) == true &&
                                    handle.id.contains(subId.toString())
                        } catch (_: Exception) { false })
            } ?: phoneAccountHandles.getOrNull(slotIndex)

            SimAccountInfo(
                subscriptionId = subId,
                slotIndex = slotIndex,
                displayName = displayName,
                carrierName = trueCarrierName,
                phoneAccountHandle = matchingHandle,
            )
        }.sortedBy { it.slotIndex }
    }

    /**
     * Dispatches an outbound carrier telephone call via a specified hardware SIM account handle using TelecomManager.
     */
    @Suppress("MissingPermission")
    fun makeCallWithSim(
        context: Context,
        number: String,
        phoneAccountHandle: PhoneAccountHandle?,
    ) {
        val cleanNumber = number.trim()
        if (cleanNumber.isBlank()) return

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val uri = Uri.fromParts("tel", cleanNumber, null)
            val extras = android.os.Bundle().apply {
                if (phoneAccountHandle != null) {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                }
            }
            if (telecomManager != null) {
                telecomManager.placeCall(uri, extras)
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
