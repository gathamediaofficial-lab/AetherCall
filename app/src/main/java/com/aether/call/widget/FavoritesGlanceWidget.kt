package com.aether.call.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.aether.call.data.ContactItem
import com.aether.call.data.ContactsRepository
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * Jetpack Glance home screen widget for favorite contacts speed dial.
 */
class FavoritesGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val favorites = ContactsRepository.getStarredContacts(context).take(8)

        provideContent {
            WidgetContent(favorites = favorites)
        }
    }
}

@Composable
private fun WidgetContent(favorites: List<ContactItem>) {
    val bg = Color(0xFF0F172A)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(day = bg, night = bg))
            .cornerRadius(24.dp)
            .padding(12.dp),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) {
            // Header Row
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val gold = Color(0xFFFBBF24)
                val gray = Color(0xFF94A3B8)

                Text(
                    text = "★ VIP Speed Dial",
                    style = TextStyle(
                        color = ColorProvider(day = gold, night = gold),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "${favorites.size} saved",
                    style = TextStyle(
                        color = ColorProvider(day = gray, night = gray),
                        fontSize = 11.sp,
                    ),
                )
            }

            if (favorites.isEmpty()) {
                val gray = Color(0xFF94A3B8)
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No starred contacts",
                        style = TextStyle(
                            color = ColorProvider(day = gray, night = gray),
                            fontSize = 13.sp,
                        ),
                    )
                }
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    favorites.forEach { contact ->
                        val initial = contact.name.trimStart().take(1).uppercase(Locale.getDefault())
                        val white = Color.White
                        val slate = Color(0xFF1E293B)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = GlanceModifier
                                .padding(horizontal = 4.dp)
                                .clickable(
                                    actionRunCallback<DirectCallActionCallback>(
                                        actionParametersOf(DirectCallActionCallback.phoneNumberKey to contact.number),
                                    ),
                                ),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = GlanceModifier
                                    .size(48.dp)
                                    .cornerRadius(24.dp)
                                    .background(ColorProvider(day = slate, night = slate)),
                            ) {
                                Text(
                                    text = initial.ifEmpty { "?" },
                                    style = TextStyle(
                                        color = ColorProvider(day = white, night = white),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                    ),
                                )
                            }

                            Spacer(modifier = GlanceModifier.height(4.dp))

                            Text(
                                text = contact.name.take(6),
                                style = TextStyle(
                                    color = ColorProvider(day = white, night = white),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
