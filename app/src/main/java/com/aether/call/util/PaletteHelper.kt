package com.aether.call.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracted color theme for calling posters and In-Call UI backdrop contrast.
 */
data class CallThemeColors(
    val primaryAccent: Color = Color(0xFF6366F1),
    val onPrimaryAccent: Color = Color.White,
    val surfaceTint: Color = Color(0xFF1E293B),
    val scrimGradient: List<Color> = listOf(
        Color.Black.copy(alpha = 0.45f),
        Color.Transparent,
        Color.Black.copy(alpha = 0.88f),
    ),
)

/**
 * Asynchronous color extraction helper utilizing Coil and AndroidX Palette.
 */
object PaletteHelper {

    private val defaultColors = CallThemeColors()

    /**
     * Extracts dynamic [CallThemeColors] from a contact photo URI asynchronously on Dispatchers.IO.
     */
    suspend fun extractColorsFromUri(context: Context, photoUri: String?): CallThemeColors =
        withContext(Dispatchers.IO) {
            if (photoUri.isNullOrBlank()) {
                return@withContext defaultColors
            }

            try {
                val request = ImageRequest.Builder(context)
                    .data(photoUri)
                    .allowHardware(enable = false)
                    .build()

                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    if (drawable is BitmapDrawable) {
                        val bitmap = drawable.bitmap
                        val palette = Palette.from(bitmap).generate()

                        val swatch = palette.vibrantSwatch
                            ?: palette.dominantSwatch
                            ?: palette.mutedSwatch
                            ?: palette.lightVibrantSwatch

                        if (swatch != null) {
                            val primary = Color(swatch.rgb)
                            val onPrimary = Color(swatch.bodyTextColor)
                            val surface = primary.copy(alpha = 0.35f)
                            val scrim = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent,
                                Color(swatch.rgb).copy(alpha = 0.90f),
                            )
                            return@withContext CallThemeColors(
                                primaryAccent = primary,
                                onPrimaryAccent = onPrimary,
                                surfaceTint = surface,
                                scrimGradient = scrim,
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                return@withContext defaultColors
            }

            return@withContext defaultColors
        }
}