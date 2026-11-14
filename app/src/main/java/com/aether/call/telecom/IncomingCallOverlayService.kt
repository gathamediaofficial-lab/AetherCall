package com.aether.call.telecom

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aether.call.ui.FloatingIncomingCallView
import com.aether.call.ui.InCallActivity

class IncomingCallOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    companion object {
        private const val EXTRA_NUMBER = "extra_incoming_number"

        fun startOverlay(context: Context, rawNumber: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                return
            }
            val intent = Intent(context, IncomingCallOverlayService::class.java).apply {
                putExtra(EXTRA_NUMBER, rawNumber)
            }
            context.startService(intent)
        }

        fun stopOverlay(context: Context) {
            try {
                context.stopService(Intent(context, IncomingCallOverlayService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rawNumber = intent?.getStringExtra(EXTRA_NUMBER).orEmpty()
        val initialResolved = CallerIdResolver.resolveCaller(this, rawNumber)
        CallBridge.updateCallerIdentity(
            name = initialResolved.displayName,
            number = rawNumber,
            photoUri = initialResolved.photoUri,
        )

        if (overlayView == null) {
            showFloatingComposeWindow(rawNumber)
        }
        return START_NOT_STICKY
    }

    private fun showFloatingComposeWindow(fallbackNumber: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 24
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@IncomingCallOverlayService)
            setViewTreeViewModelStoreOwner(this@IncomingCallOverlayService)
            setViewTreeSavedStateRegistryOwner(this@IncomingCallOverlayService)

            setContent {
                val liveName by CallBridge.callerName.collectAsState()
                val liveNumber by CallBridge.callerNumber.collectAsState()
                val livePhoto by CallBridge.callerPhotoUri.collectAsState()
                val simLabel by CallBridge.simLabel.collectAsState()

                val effectiveNumber = liveNumber.ifBlank { fallbackNumber }
                val effectiveName = liveName.takeIf { it.isNotBlank() && it != "Unknown Caller" }
                    ?: effectiveNumber.ifBlank { "Unknown Caller" }

                FloatingIncomingCallView(
                    callerName = effectiveName,
                    callerNumber = effectiveNumber,
                    simLabel = simLabel,
                    posterPhotoUri = livePhoto,
                    onAnswer = {
                        // Launch InCallActivity FIRST while overlay window is still visible on screen
                        // so Android 14/15 allows the activity transition, then answer and close overlay
                        val fullIntent = Intent(this@IncomingCallOverlayService, InCallActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            putExtra("CALL_NUMBER", effectiveNumber)
                            putExtra("CALLER_NAME", effectiveName)
                            putExtra("CALL_DIRECTION", "INCOMING")
                            putExtra("AUTO_ANSWER_CALL", true)
                        }
                        try {
                            startActivity(fullIntent)
                        } catch (_: Exception) {
                        }
                        CallBridge.answerCall()
                        stopSelf()
                    },
                    onDecline = {
                        CallBridge.rejectCall()
                        CallNotificationManager.cancelIncomingCallNotification(this@IncomingCallOverlayService)
                        stopSelf()
                    },
                    onQuickReply = { message ->
                        CallBridge.rejectWithSms(this@IncomingCallOverlayService, message)
                        CallNotificationManager.cancelIncomingCallNotification(this@IncomingCallOverlayService)
                        stopSelf()
                    },
                    onExpandFullScreen = {
                        expandToFullScreen(effectiveNumber, effectiveName)
                        stopSelf()
                    },
                    onSilence = {
                        silenceRinger()
                    },
                )
            }
        }

        try {
            windowManager?.addView(composeView, params)
            overlayView = composeView
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun expandToFullScreen(number: String, name: String) {
        val fullIntent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("CALL_NUMBER", number)
            putExtra("CALLER_NAME", name)
            putExtra("CALL_DIRECTION", "INCOMING")
        }
        startActivity(fullIntent)
    }

    @Suppress("MissingPermission")
    private fun silenceRinger() {
        try {
            val tm = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                tm?.silenceRinger()
            }
        } catch (_: Exception) {
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                @Suppress("DEPRECATION")
                am?.ringerMode = AudioManager.RINGER_MODE_SILENT
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}