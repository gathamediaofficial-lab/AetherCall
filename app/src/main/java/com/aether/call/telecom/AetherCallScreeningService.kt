package com.aether.call.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import com.aether.call.data.AetherDatabase
import com.aether.call.data.AppSettingsManager
import com.aether.call.util.PhoneNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Telecom CallScreeningService implementation for evaluating incoming calls within
 * Android Telecom's 5-second deadline and screening spam or blocked numbers.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class AetherCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            val allowResponse = CallResponse.Builder().setDisallowCall(false).build()
            respondToCall(callDetails, allowResponse)
            return
        }

        val handle = callDetails.handle ?: run {
            val allowResponse = CallResponse.Builder().setDisallowCall(false).build()
            respondToCall(callDetails, allowResponse)
            return
        }

        val rawNumber = handle.schemeSpecificPart.orEmpty()
        if (rawNumber.isBlank()) {
            val allowResponse = CallResponse.Builder().setDisallowCall(false).build()
            respondToCall(callDetails, allowResponse)
            return
        }

        val normNumber = PhoneNormalizer.normalizeDigits(rawNumber)

        serviceScope.launch {
            val isSpamOrBlocked = withTimeoutOrNull(3800L) {
                try {
                    val settings = AppSettingsManager(this@AetherCallScreeningService).settingsFlow.first()
                    val isExplicitlyBlockedInSettings = settings.blockedNumbers.any {
                        PhoneNormalizer.normalizeDigits(it) == normNumber && normNumber.isNotEmpty()
                    }

                    if (isExplicitlyBlockedInSettings) return@withTimeoutOrNull true

                    val database = AetherDatabase.getDatabase(this@AetherCallScreeningService)
                    val isInDb = database.blockedNumberDao().isBlocked(normNumber)
                    if (isInDb) return@withTimeoutOrNull true

                    if (settings.blockUnknownNumbers && normNumber.isNotEmpty()) {
                        val isContact = database.contactPosterDao().getPosterForNumber(rawNumber) != null
                        if (!isContact) return@withTimeoutOrNull true
                    }

                    false
                } catch (_: Exception) {
                    false
                }
            } ?: false

            val response = if (isSpamOrBlocked) {
                CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSilenceCall(true)
                    .setSkipCallLog(false) // Keep record in call log for user review
                    .setSkipNotification(true)
                    .build()
            } else {
                CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSilenceCall(false)
                    .build()
            }

            respondToCall(callDetails, response)
        }
    }
}