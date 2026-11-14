package com.aether.call.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.telecom.Call
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import com.aether.call.MainActivity

object CallNotificationManager {

    const val INCOMING_CHANNEL_ID = "aether_incoming_calls_v4"
    const val ONGOING_CHANNEL_ID = "aether_ongoing_calls_v4"
    const val MISSED_CHANNEL_ID = "aether_missed_calls_v4"

    const val INCOMING_NOTIFICATION_ID = 1001
    const val ONGOING_NOTIFICATION_ID = 1002
    const val CALL_NOTIFICATION_ID = ONGOING_NOTIFICATION_ID
    private const val MISSED_BASE_NOTIFICATION_ID = 2000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val incomingChannel = NotificationChannel(
            INCOMING_CHANNEL_ID,
            "Incoming Calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Incoming call pop-up banners and full-screen alerts"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setSound(ringtoneUri, audioAttrs)
            setBypassDnd(true)
        }

        val ongoingChannel = NotificationChannel(
            ONGOING_CHANNEL_ID,
            "Ongoing Calls",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Ongoing active call status bar chip and Hang Up control"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }

        val missedChannel = NotificationChannel(
            MISSED_CHANNEL_ID,
            "Missed Calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Missed call alerts with Call Back and Message buttons"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }

        nm.createNotificationChannels(listOf(incomingChannel, ongoingChannel, missedChannel))
    }

    fun buildIncomingCallNotification(
        context: Context,
        caller: ResolvedCaller,
        overlayIsShowing: Boolean = false,
    ): Notification {
        createNotificationChannel(context)
        return ActiveCallNotificationBuilder.buildIncomingNotification(
            context = context,
            caller = caller,
            channelId = INCOMING_CHANNEL_ID,
            attachFullScreenIntent = true,
        )
    }

    fun buildOngoingCallNotification(context: Context, call: Call?, caller: ResolvedCaller): Notification {
        createNotificationChannel(context)
        return ActiveCallNotificationBuilder.buildOngoingNotification(
            context = context,
            call = call,
            caller = caller,
            channelId = ONGOING_CHANNEL_ID,
        )
    }

    fun showIncomingCallNotification(
        context: Context,
        call: Call,
        caller: ResolvedCaller,
        overlayIsShowing: Boolean = false,
    ) {
        @Suppress("DEPRECATION")
        if (call.state != Call.STATE_RINGING) return
        createNotificationChannel(context)
        val notification = buildIncomingCallNotification(context, caller, false)
        val service = CallManager.instance
        if (service != null) {
            service.promoteToForeground(INCOMING_NOTIFICATION_ID, notification)
        } else {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(INCOMING_NOTIFICATION_ID, notification)
            } catch (_: Exception) {
            }
        }
    }

    fun showOngoingCallNotification(context: Context, call: Call?, caller: ResolvedCaller) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(INCOMING_NOTIFICATION_ID)

        @Suppress("DEPRECATION")
        val state = call?.state ?: Call.STATE_DISCONNECTED
        if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING || CallManager.currentCall.value == null) {
            cancelNotification(context)
            return
        }
        createNotificationChannel(context)
        val notification = buildOngoingCallNotification(context, call, caller)
        val service = CallManager.instance
        if (service != null) {
            service.promoteToForeground(ONGOING_NOTIFICATION_ID, notification)
        } else {
            try {
                nm.notify(ONGOING_NOTIFICATION_ID, notification)
            } catch (_: Exception) {
            }
        }
    }

    @Suppress("MissingPermission")
    fun suppressNativeSamsungMissedCallNotification(context: Context) {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecomManager?.cancelMissedCallsNotification()
        } catch (_: Exception) {
        }

        try {
            val values = ContentValues().apply {
                put(CallLog.Calls.NEW, 0)
                put(CallLog.Calls.IS_READ, 1)
            }
            context.contentResolver.update(
                CallLog.Calls.CONTENT_URI,
                values,
                "${CallLog.Calls.TYPE} = ? AND (${CallLog.Calls.NEW} = 1 OR ${CallLog.Calls.IS_READ} = 0)",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
            )
        } catch (_: Exception) {
        }
    }

    fun showMissedCallNotification(context: Context, caller: ResolvedCaller) {
        createNotificationChannel(context)
        suppressNativeSamsungMissedCallNotification(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notifId = MISSED_BASE_NOTIFICATION_ID + (caller.rawNumber.hashCode() and 0x7FFF)

        val openDialerIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context,
            notifId,
            openDialerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val callBackIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_CALL_BACK
            putExtra(CallActionReceiver.EXTRA_PHONE_NUMBER, caller.rawNumber)
            putExtra(CallActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        val callBackPi = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            callBackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${caller.rawNumber}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val smsPi = PendingIntent.getActivity(
            context,
            notifId + 2,
            smsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val avatarBmp = ActiveCallNotificationBuilder.loadCallerAvatarBitmap(context, caller)
        val title = caller.displayName.ifBlank { caller.rawNumber.ifBlank { "Unknown Caller" } }
        val text = if (caller.isSavedContact && caller.rawNumber.isNotBlank()) {
            "Missed call • ${caller.rawNumber}"
        } else {
            "Missed call"
        }

        val notification = NotificationCompat.Builder(context, MISSED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setLargeIcon(avatarBmp)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Aether Call")
            .setColor(android.graphics.Color.parseColor("#F43F5E"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPi)
            .addAction(android.R.drawable.sym_action_call, "Call back", callBackPi)
            .addAction(android.R.drawable.sym_action_chat, "Message", smsPi)
            .build()

        nm.notify(notifId, notification)
    }

    fun cancelIncomingCallNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(INCOMING_NOTIFICATION_ID)
    }

    fun cancelNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(INCOMING_NOTIFICATION_ID)
        nm?.cancel(ONGOING_NOTIFICATION_ID)
    }

    fun cancelById(context: Context, notificationId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(notificationId)
    }
}