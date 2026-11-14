package com.aether.call.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aether.call.data.CallLogRepository

class CallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER = "com.aether.call.action.ANSWER_CALL"
        const val ACTION_DECLINE = "com.aether.call.action.DECLINE_CALL"
        const val ACTION_HANGUP = "com.aether.call.action.HANGUP_CALL"
        const val ACTION_TOGGLE_MUTE = "com.aether.call.action.TOGGLE_MUTE"
        const val ACTION_TOGGLE_SPEAKER = "com.aether.call.action.TOGGLE_SPEAKER"
        const val ACTION_CALL_BACK = "com.aether.call.action.CALL_BACK"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_ANSWER -> {
                IncomingCallOverlayService.stopOverlay(context)
                CallBridge.answerCall()
                val number = CallBridge.callerNumber.value.ifBlank {
                    CallManager.currentCall.value?.details?.handle?.schemeSpecificPart.orEmpty()
                }
                val openInCall = Intent(context, com.aether.call.ui.InCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra("CALL_NUMBER", number)
                    putExtra("CALLER_NAME", CallBridge.callerName.value)
                    putExtra("CALL_DIRECTION", "INCOMING")
                }
                try {
                    context.startActivity(openInCall)
                } catch (_: Exception) {
                }
            }
            ACTION_DECLINE -> {
                IncomingCallOverlayService.stopOverlay(context)
                CallBridge.rejectCall()
                CallNotificationManager.cancelIncomingCallNotification(context)
            }
            ACTION_HANGUP -> {
                IncomingCallOverlayService.stopOverlay(context)
                CallBridge.disconnectCall()
                CallNotificationManager.cancelNotification(context)
            }
            ACTION_TOGGLE_MUTE -> {
                CallBridge.toggleMute()
                CallManager.currentCall.value?.let { call ->
                    val raw = call.details?.handle?.schemeSpecificPart.orEmpty()
                    val resolved = CallerIdResolver.resolveCaller(context, raw)
                    CallNotificationManager.showOngoingCallNotification(context, call, resolved)
                }
            }
            ACTION_TOGGLE_SPEAKER -> {
                CallBridge.toggleSpeaker()
                CallManager.currentCall.value?.let { call ->
                    val raw = call.details?.handle?.schemeSpecificPart.orEmpty()
                    val resolved = CallerIdResolver.resolveCaller(context, raw)
                    CallNotificationManager.showOngoingCallNotification(context, call, resolved)
                }
            }
            ACTION_CALL_BACK -> {
                val number = intent.getStringExtra(EXTRA_PHONE_NUMBER).orEmpty()
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                if (notifId != 0) {
                    CallNotificationManager.cancelById(context, notifId)
                }
                if (number.isNotBlank()) {
                    CallLogRepository.placeCarrierCall(context, number)
                }
            }
        }
    }
}