package com.aether.call.telecom

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.telecom.Call
import android.util.LruCache
import androidx.core.app.NotificationCompat
import com.aether.call.ui.InCallActivity

object ActiveCallNotificationBuilder {

    private val avatarCache = object : LruCache<String, Bitmap>(16) {}

    private fun backgroundActivityAllowBundle(): android.os.Bundle? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                pendingIntentCreatorBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }.toBundle()
        } else {
            null
        }
    }

    fun loadCallerAvatarBitmap(context: Context, caller: ResolvedCaller): Bitmap {
        val cacheKey = "${caller.rawNumber}_${caller.photoUri.orEmpty()}_${caller.displayName}"
        avatarCache.get(cacheKey)?.let { return it }

        val size = 192
        if (!caller.photoUri.isNullOrBlank()) {
            try {
                @Suppress("DEPRECATION")
                val src = MediaStore.Images.Media.getBitmap(context.contentResolver, Uri.parse(caller.photoUri))
                if (src != null) {
                    val cropped = cropCircleBitmap(Bitmap.createScaledBitmap(src, size, size, true))
                    avatarCache.put(cacheKey, cropped)
                    return cropped
                }
            } catch (_: Exception) {
            }
        }

        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (caller.isSavedContact) Color.parseColor("#10B981") else Color.parseColor("#3B82F6")
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)

        val initial = caller.displayName.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 84f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val textY = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(initial, size / 2f, textY, textPaint)
        avatarCache.put(cacheKey, bmp)
        return bmp
    }

    private fun cropCircleBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(bitmap.width / 2f, bitmap.height / 2f, bitmap.width / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    fun buildIncomingNotification(
        context: Context,
        caller: ResolvedCaller,
        channelId: String,
        attachFullScreenIntent: Boolean = true,
    ): Notification {
        val balBundle = backgroundActivityAllowBundle()

        val fullScreenIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra("CALL_NUMBER", caller.rawNumber)
            putExtra("CALLER_NAME", caller.displayName)
            putExtra("CALL_DIRECTION", "INCOMING")
        }
        val fullScreenPi = PendingIntent.getActivity(
            context,
            100,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            balBundle,
        )

        val answerActivityIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("CALL_NUMBER", caller.rawNumber)
            putExtra("CALLER_NAME", caller.displayName)
            putExtra("CALL_DIRECTION", "ACTIVE")
            putExtra("AUTO_ANSWER_CALL", true)
        }
        val answerPi = PendingIntent.getActivity(
            context,
            101,
            answerActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            balBundle,
        )

        val declinePi = PendingIntent.getBroadcast(
            context,
            102,
            Intent(context, CallActionReceiver::class.java).apply { action = CallActionReceiver.ACTION_DECLINE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val avatarBmp = loadCallerAvatarBitmap(context, caller)
        val subtitle = if (caller.isSavedContact && caller.rawNumber.isNotBlank()) {
            "${caller.label} • ${caller.rawNumber}"
        } else {
            "Incoming call • ${CallBridge.simLabel.value}"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val person = Person.Builder()
                .setName(caller.displayName.ifBlank { caller.rawNumber.ifBlank { "Unknown Caller" } })
                .setIcon(Icon.createWithBitmap(avatarBmp))
                .setImportant(true)
                .build()

            val callStyle = Notification.CallStyle.forIncomingCall(person, declinePi, answerPi)

            val builder = Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(caller.displayName.ifBlank { caller.rawNumber.ifBlank { "Unknown Caller" } })
                .setContentText(subtitle)
                .setColor(Color.parseColor("#10B981"))
                .setCategory(Notification.CATEGORY_CALL)
                .setStyle(callStyle)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setDefaults(0)
                .setSound(null, null)
                .setContentIntent(fullScreenPi)

            if (attachFullScreenIntent) {
                builder.setFullScreenIntent(fullScreenPi, true)
            }
            return builder.build()
        }

        val compat = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setLargeIcon(avatarBmp)
            .setContentTitle(caller.displayName.ifBlank { caller.rawNumber.ifBlank { "Unknown Caller" } })
            .setContentText(subtitle)
            .setColor(Color.parseColor("#10B981"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setDefaults(0)
            .setSound(null)
            .setContentIntent(fullScreenPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePi)
            .addAction(android.R.drawable.sym_action_call, "Answer", answerPi)

        if (attachFullScreenIntent) {
            compat.setFullScreenIntent(fullScreenPi, true)
        }
        return compat.build()
    }

    fun buildOngoingNotification(
        context: Context,
        call: Call?,
        caller: ResolvedCaller,
        channelId: String,
    ): Notification {
        val balBundle = backgroundActivityAllowBundle()

        val openInCallIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("CALL_NUMBER", caller.rawNumber)
            putExtra("CALLER_NAME", caller.displayName)
            putExtra("CALL_DIRECTION", "ACTIVE")
        }
        val contentPi = PendingIntent.getActivity(
            context,
            200,
            openInCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            balBundle,
        )

        val hangupPi = PendingIntent.getBroadcast(
            context,
            201,
            Intent(context, CallActionReceiver::class.java).apply { action = CallActionReceiver.ACTION_HANGUP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val mutePi = PendingIntent.getBroadcast(
            context,
            202,
            Intent(context, CallActionReceiver::class.java).apply { action = CallActionReceiver.ACTION_TOGGLE_MUTE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val speakerPi = PendingIntent.getBroadcast(
            context,
            203,
            Intent(context, CallActionReceiver::class.java).apply { action = CallActionReceiver.ACTION_TOGGLE_SPEAKER },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val isMuted = CallBridge.isMuted.value
        val isSpeaker = CallBridge.isSpeakerOn.value
        @Suppress("DEPRECATION")
        val state = call?.state ?: CallBridge.callState.value
        val isActive = state == Call.STATE_ACTIVE
        val statusText = buildString {
            append(
                when (state) {
                    Call.STATE_DIALING, Call.STATE_CONNECTING -> "Dialing"
                    Call.STATE_HOLDING -> "On hold"
                    else -> "Ongoing call"
                }
            )
            append(" • ${CallBridge.simLabel.value}")
            if (isSpeaker) append(" • Speaker")
            if (isMuted) append(" • Muted")
        }

        val avatarBmp = loadCallerAvatarBitmap(context, caller)
        val connectTimeMillis = call?.details?.connectTimeMillis ?: 0L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val person = Person.Builder()
                .setName(caller.displayName.ifBlank { caller.rawNumber.ifBlank { "Active Call" } })
                .setIcon(Icon.createWithBitmap(avatarBmp))
                .setImportant(true)
                .build()

            val callStyle = Notification.CallStyle.forOngoingCall(person, hangupPi)

            val builder = Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle(caller.displayName.ifBlank { caller.rawNumber.ifBlank { "Active Call" } })
                .setContentText(statusText)
                .setColor(Color.parseColor("#10B981"))
                .setCategory(Notification.CATEGORY_CALL)
                .setStyle(callStyle)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setDefaults(0)
                .setSound(null, null)
                .setContentIntent(contentPi)

            if (isActive && connectTimeMillis > 0L) {
                builder.setUsesChronometer(true)
                builder.setWhen(connectTimeMillis)
            }
            return builder.build()
        }

        val compatBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setLargeIcon(avatarBmp)
            .setContentTitle(caller.displayName.ifBlank { caller.rawNumber.ifBlank { "Active Call" } })
            .setContentText(statusText)
            .setColor(Color.parseColor("#10B981"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setDefaults(0)
            .setSound(null)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPi)
            .addAction(0, if (isMuted) "Unmute" else "Mute", mutePi)
            .addAction(0, if (isSpeaker) "Earpiece" else "Speaker", speakerPi)
            .addAction(0, "Hang up", hangupPi)

        if (isActive && connectTimeMillis > 0L) {
            compatBuilder.setUsesChronometer(true)
            compatBuilder.setWhen(connectTimeMillis)
        }
        return compatBuilder.build()
    }
}