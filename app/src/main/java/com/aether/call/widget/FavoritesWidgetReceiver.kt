package com.aether.call.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * GlanceAppWidgetReceiver binding FavoritesGlanceWidget to system Android AppWidget framework
 * with static updateAllWidgets helper for instant DataStore/Room updates.
 */
class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = FavoritesGlanceWidget()

    companion object {
        fun updateAllWidgets(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                FavoritesGlanceWidget().updateAll(context)
            }
        }
    }
}
