package com.aether.call.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Safely shows a Toast on the Main UI thread, regardless of which thread/dispatcher calls it.
 */
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    val appContext = applicationContext ?: this
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Toast.makeText(appContext, message, duration).show()
    } else {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, message, duration).show()
        }
    }
}
