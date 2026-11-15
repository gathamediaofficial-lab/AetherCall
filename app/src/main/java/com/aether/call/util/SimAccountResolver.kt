package com.aether.call.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager

object SimAccountResolver {

    data class ResolvedSimInfo(
        val carrierName: String,
        val slotIndex: Int, // 0 for SIM 1, 1 for SIM 2, -1 if unknown
        val displayLabel: String, // e.g., "SIM 2 • Airtel" or "Airtel"
    )

    /**
     * Resolves the exact SIM Carrier & Slot for an active/incoming android.telecom.Call.
     */
    @SuppressLint("MissingPermission")
    fun resolveSimForActiveCall(context: Context, call: Call?): ResolvedSimInfo {
        val handle: PhoneAccountHandle? = call?.details?.accountHandle
        return resolveFromPhoneAccountHandle(context, handle)
    }

    /**
     * Resolves the exact SIM Carrier & Slot from a Telecom PhoneAccountHandle.
     */
    @SuppressLint("MissingPermission")
    fun resolveFromPhoneAccountHandle(
        context: Context,
        handle: PhoneAccountHandle?,
    ): ResolvedSimInfo {
        if (handle == null) {
            return ResolvedSimInfo("SIM", -1, "Mobile")
        }

        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val activeSubs: List<SubscriptionInfo> = try {
            subManager?.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }

        val handleId = handle.id.orEmpty().trim()

        // 1. Match handle.id against SubscriptionInfo (subId, iccId, or number)
        val matchedSub = activeSubs.find { sub ->
            val subIdStr = sub.subscriptionId.toString()
            @Suppress("DEPRECATION")
            val iccIdStr = sub.iccId.orEmpty()
            handleId == subIdStr ||
                (iccIdStr.isNotBlank() && (handleId.equals(iccIdStr, ignoreCase = true) || handleId.contains(iccIdStr, ignoreCase = true))) ||
                handleId.endsWith("_$subIdStr") ||
                handleId.endsWith(":$subIdStr")
        } ?: run {
            // Check if handleId is literally slot index "0" or "1" when subIds are > 1
            val asInt = handleId.toIntOrNull()
            if (asInt != null && activeSubs.none { it.subscriptionId == asInt }) {
                activeSubs.find { it.simSlotIndex == asInt }
            } else {
                null
            }
        }

        if (matchedSub != null) {
            val carrier = cleanCarrierName(matchedSub.carrierName?.toString().orEmpty(), matchedSub.displayName?.toString().orEmpty())
            val slot = matchedSub.simSlotIndex
            return ResolvedSimInfo(
                carrierName = carrier,
                slotIndex = slot,
                displayLabel = "SIM ${slot + 1} • $carrier",
            )
        }

        // 2. Fallback: Ask TelecomManager directly for the PhoneAccount label!
        // TelecomManager ALWAYS knows the exact label of the PhoneAccountHandle that received the call.
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val phoneAccount = tm?.getPhoneAccount(handle)
            val accountLabel = phoneAccount?.label?.toString()?.trim().orEmpty()
            val accountDesc = phoneAccount?.shortDescription?.toString()?.trim().orEmpty()
            val bestLabel = accountLabel.ifBlank { accountDesc }

            if (bestLabel.isNotBlank()) {
                // Also try matching the Telecom label against activeSubs carrier/display names to get the slot index
                val subByName = activeSubs.find { sub ->
                    val cName = sub.carrierName?.toString().orEmpty()
                    val dName = sub.displayName?.toString().orEmpty()
                    bestLabel.contains(cName, ignoreCase = true) ||
                        cName.contains(bestLabel, ignoreCase = true) ||
                        bestLabel.contains(dName, ignoreCase = true)
                }
                val slot = subByName?.simSlotIndex ?: -1
                val carrier = cleanCarrierName(bestLabel, subByName?.displayName?.toString().orEmpty())
                return ResolvedSimInfo(
                    carrierName = carrier,
                    slotIndex = slot,
                    displayLabel = if (slot >= 0) "SIM ${slot + 1} • $carrier" else carrier,
                )
            }
        } catch (_: Exception) {
        }

        return ResolvedSimInfo("SIM", -1, "Mobile")
    }

    /**
     * Resolves the exact SIM Carrier & Slot for a CallLog row using PHONE_ACCOUNT_ID,
     * PHONE_ACCOUNT_COMPONENT_NAME, and Samsung/OEM sub_id / sim_id columns.
     */
    @SuppressLint("MissingPermission")
    fun resolveSimForCallLog(
        context: Context,
        phoneAccountId: String?,
        subIdColumnValue: Int? = null,
        simSlotColumnValue: Int? = null,
    ): ResolvedSimInfo {
        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val activeSubs: List<SubscriptionInfo> = try {
            subManager?.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }

        val cleanId = phoneAccountId?.trim().orEmpty()

        // 1. Check TelecomManager's registered callCapablePhoneAccounts first (Exact handle.id match)
        if (cleanId.isNotBlank()) {
            try {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                val matchedHandle = tm?.callCapablePhoneAccounts?.find { handle ->
                    handle.id.equals(cleanId, ignoreCase = true) ||
                        handle.id.contains(cleanId, ignoreCase = true) ||
                        cleanId.contains(handle.id, ignoreCase = true)
                }
                if (matchedHandle != null) {
                    val resolved = resolveFromPhoneAccountHandle(context, matchedHandle)
                    if (resolved.carrierName != "SIM") {
                        return resolved
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 2. Match against active SubscriptionInfo list using subId, iccId, or slot index
        if (activeSubs.isNotEmpty()) {
            val matchedSub = activeSubs.find { sub ->
                val subIdStr = sub.subscriptionId.toString()
                @Suppress("DEPRECATION")
                val iccId = sub.iccId.orEmpty()
                (cleanId.isNotBlank() && (
                    cleanId == subIdStr ||
                        (iccId.isNotBlank() && (cleanId.equals(iccId, ignoreCase = true) || cleanId.contains(iccId, ignoreCase = true))) ||
                        cleanId.endsWith("_$subIdStr") ||
                        cleanId.endsWith(":$subIdStr")
                    )) ||
                    (subIdColumnValue != null && subIdColumnValue > 0 && sub.subscriptionId == subIdColumnValue)
            } ?: run {
                // Check explicit slot column or 0/1 slot index in cleanId
                val slotToMatch = when {
                    simSlotColumnValue != null && simSlotColumnValue in 0..1 -> simSlotColumnValue
                    cleanId == "0" || cleanId.equals("sim1", ignoreCase = true) || cleanId.equals("slot0", ignoreCase = true) -> 0
                    cleanId == "1" || cleanId.equals("sim2", ignoreCase = true) || cleanId.equals("slot1", ignoreCase = true) -> 1
                    else -> -1
                }
                if (slotToMatch >= 0) {
                    activeSubs.find { it.simSlotIndex == slotToMatch }
                } else {
                    null
                }
            }

            if (matchedSub != null) {
                val carrier = cleanCarrierName(matchedSub.carrierName?.toString().orEmpty(), matchedSub.displayName?.toString().orEmpty())
                val slot = matchedSub.simSlotIndex
                return ResolvedSimInfo(
                    carrierName = carrier,
                    slotIndex = slot,
                    displayLabel = "SIM ${slot + 1} • $carrier",
                )
            }

            // 3. Look up historical/inactive SubscriptionInfo via getActiveSubscriptionInfo(subId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val numericId = subIdColumnValue?.takeIf { it > 0 } ?: cleanId.toIntOrNull()
                if (numericId != null) {
                    try {
                        val subInfo = subManager?.getActiveSubscriptionInfo(numericId)
                        if (subInfo != null) {
                            val carrier = cleanCarrierName(subInfo.carrierName?.toString().orEmpty(), subInfo.displayName?.toString().orEmpty())
                            val slot = subInfo.simSlotIndex
                            return ResolvedSimInfo(
                                carrierName = carrier,
                                slotIndex = slot,
                                displayLabel = "SIM ${slot + 1} • $carrier",
                            )
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        // CRITICAL: Never blindly default to activeSubs.first() (SIM 1 / Jio) when a match fails!
        // If only 1 SIM is inserted in the entire phone, we can safely use it; otherwise return neutral "SIM".
        if (activeSubs.size == 1) {
            val onlySub = activeSubs.first()
            val carrier = cleanCarrierName(onlySub.carrierName?.toString().orEmpty(), onlySub.displayName?.toString().orEmpty())
            return ResolvedSimInfo(carrier, onlySub.simSlotIndex, "SIM ${onlySub.simSlotIndex + 1} • $carrier")
        }

        return ResolvedSimInfo("SIM", -1, "SIM")
    }

    /**
     * Checks if Dual-SIM calling preference is set to "Ask every time" (2+ active SIMs and no default outgoing PhoneAccount).
     */
    @SuppressLint("MissingPermission")
    fun shouldAskSimEveryTime(context: Context): Boolean {
        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val activeSubs: List<SubscriptionInfo> = try {
            subManager?.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        if (activeSubs.size < 2) return false

        val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val defaultAccount = try {
            tm?.getDefaultOutgoingPhoneAccount("tel")
        } catch (_: Exception) {
            null
        }
        return defaultAccount == null
    }

    /**
     * Finds the exact Telecom PhoneAccountHandle matching a physical SIM slotIndex (0 for SIM 1, 1 for SIM 2).
     */
    @SuppressLint("MissingPermission")
    fun findPhoneAccountHandleForSlot(context: Context, slotIndex: Int): PhoneAccountHandle? {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return null
        val accounts = try {
            tm.callCapablePhoneAccounts.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        return accounts.find { handle ->
            resolveFromPhoneAccountHandle(context, handle).slotIndex == slotIndex
        } ?: accounts.getOrNull(slotIndex)
    }

    private fun cleanCarrierName(rawCarrier: String, fallbackDisplay: String): String {
        val candidate = rawCarrier.trim().takeIf {
            it.isNotBlank() && !it.equals("null", ignoreCase = true) && !it.contains("No service", ignoreCase = true)
        } ?: fallbackDisplay.trim()

        return when {
            candidate.contains("airtel", ignoreCase = true) -> "Airtel"
            candidate.contains("jio", ignoreCase = true) -> "Jio"
            candidate.contains("vi ", ignoreCase = true) || candidate.equals("vi", ignoreCase = true) || candidate.contains("vodafone", ignoreCase = true) || candidate.contains("idea", ignoreCase = true) -> "Vi"
            candidate.contains("bsnl", ignoreCase = true) -> "BSNL"
            candidate.isNotBlank() -> candidate
            else -> "SIM"
        }
    }
}
