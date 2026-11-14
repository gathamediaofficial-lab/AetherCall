package com.aether.call.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * Glance ActionCallback executing direct phone calls or falling back to system dialer if permission is missing.
 */
class DirectCallActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val number = parameters[phoneNumberKey] ?: return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            com.aether.call.data.CallLogRepository.placeCarrierCall(context, number)
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
        }
    }

    companion object {
        val phoneNumberKey = ActionParameters.Key<String>("phone_number")
    }
}
