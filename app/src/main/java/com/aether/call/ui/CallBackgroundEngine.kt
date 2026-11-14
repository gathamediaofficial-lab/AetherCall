package com.aether.call.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class CallBgCategory(val label: String) {
    AUTO_AVATAR("Auto (From Contact Avatar)"),
    SOLID("Solid Colors"),
    GRADIENT("Gradient Themes"),
    CUSTOM_PHOTO("Custom Photo from Gallery"),
}

data class CallBackgroundPreset(
    val id: String,
    val title: String,
    val category: CallBgCategory,
    val colors: List<Color>,
)

object CallBackgroundEngine {

    val solidPresets: List<CallBackgroundPreset> = listOf(
        CallBackgroundPreset(
            id = "SOLID_AMOLED",
            title = "AMOLED Pitch Black",
            category = CallBgCategory.SOLID,
            colors = listOf(Color(0xFF000000), Color(0xFF000000), Color(0xFF000000)),
        ),
        CallBackgroundPreset(
            id = "SOLID_ONEUI_SLATE",
            title = "One UI Dark Slate",
            category = CallBgCategory.SOLID,
            colors = listOf(Color(0xFF15171C), Color(0xFF15171C), Color(0xFF15171C)),
        ),
        CallBackgroundPreset(
            id = "SOLID_MIDNIGHT_NAVY",
            title = "Midnight Navy",
            category = CallBgCategory.SOLID,
            colors = listOf(Color(0xFF0B1528), Color(0xFF0B1528), Color(0xFF0B1528)),
        ),
        CallBackgroundPreset(
            id = "SOLID_DEEP_FOREST",
            title = "Deep Forest",
            category = CallBgCategory.SOLID,
            colors = listOf(Color(0xFF071F18), Color(0xFF071F18), Color(0xFF071F18)),
        ),
        CallBackgroundPreset(
            id = "SOLID_OBSIDIAN_PLUM",
            title = "Obsidian Plum",
            category = CallBgCategory.SOLID,
            colors = listOf(Color(0xFF1F0D29), Color(0xFF1F0D29), Color(0xFF1F0D29)),
        ),
        CallBackgroundPreset(
            id = "SOLID_CHARCOAL_MOCHA",
            title = "Charcoal Mocha",
            category = CallBgCategory.SOLID,
            colors = listOf(Color(0xFF1C1614), Color(0xFF1C1614), Color(0xFF1C1614)),
        ),
    )

    val gradientPresets: List<CallBackgroundPreset> = listOf(
        CallBackgroundPreset(
            id = "GRAD_AUTO_AVATAR",
            title = "Auto (Extract from Contact Photo / Icon)",
            category = CallBgCategory.AUTO_AVATAR,
            colors = listOf(Color(0xFF0F766E), Color(0xFF0F172A), Color(0xFF000000)),
        ),
        CallBackgroundPreset(
            id = "GRAD_ONEUI_AURORA",
            title = "One UI 7 Aurora",
            category = CallBgCategory.GRADIENT,
            colors = listOf(Color(0xFF0D9488), Color(0xFF1E1B4B), Color(0xFF05070B)),
        ),
        CallBackgroundPreset(
            id = "GRAD_EMERALD_DARK",
            title = "Emerald Dark",
            category = CallBgCategory.GRADIENT,
            colors = listOf(Color(0xFF065F46), Color(0xFF022C22), Color(0xFF000000)),
        ),
        CallBackgroundPreset(
            id = "GRAD_INDIGO_GLOW",
            title = "Indigo Glow",
            category = CallBgCategory.GRADIENT,
            colors = listOf(Color(0xFF312E81), Color(0xFF0F172A), Color(0xFF000000)),
        ),
        CallBackgroundPreset(
            id = "GRAD_CRIMSON_PULSE",
            title = "Crimson Pulse",
            category = CallBgCategory.GRADIENT,
            colors = listOf(Color(0xFF7F1D1D), Color(0xFF2A0810), Color(0xFF000000)),
        ),
        CallBackgroundPreset(
            id = "GRAD_ROYAL_AMETHYST",
            title = "Royal Amethyst",
            category = CallBgCategory.GRADIENT,
            colors = listOf(Color(0xFF581C87), Color(0xFF1E1035), Color(0xFF040208)),
        ),
        CallBackgroundPreset(
            id = "GRAD_SUNSET_COPPER",
            title = "Sunset Copper",
            category = CallBgCategory.GRADIENT,
            colors = listOf(Color(0xFF7C2D12), Color(0xFF27120B), Color(0xFF050302)),
        ),
        CallBackgroundPreset(
            id = "GRAD_OCEANIC_ABYSS",
            title = "Oceanic Abyss",
            category = CallBgCategory.GRADIENT,
            colors = listOf(Color(0xFF0369A1), Color(0xFF082F49), Color(0xFF020813)),
        ),
    )

    val allPresets: List<CallBackgroundPreset> = gradientPresets + solidPresets

    // Matching avatar palette colors used in OneUiContactsScreen
    private val avatarFallbackPalette = listOf(
        Color(0xFFF59E0B),
        Color(0xFF38BDF8),
        Color(0xFF10B981),
        Color(0xFFEC4899),
        Color(0xFFA855F7),
        Color(0xFF3B82F6),
    )

    /**
     * Deterministic avatar color from the contact's prefix-free first initial or name.
     */
    fun getAvatarAccentColor(callerName: String, callerNumber: String): Color {
        val cleanKey = extractCleanFirstNameLetter(callerName, callerNumber)
        val idx = (cleanKey.hashCode() and 0x7FFFFFFF) % avatarFallbackPalette.size
        return avatarFallbackPalette[idx]
    }

    /**
     * Extracts the first letter of the first name, skipping titles/prefixes like Mr., Dr., Mrs., Prof.
     */
    fun extractCleanFirstNameLetter(rawName: String, fallbackNumber: String = ""): String {
        val source = rawName.trim().ifBlank { fallbackNumber.trim() }
        if (source.isEmpty()) return "#"
        val skippedPrefixes = setOf("mr", "mrs", "ms", "miss", "dr", "prof", "shri", "smt", "er", "adv", "rev", "col", "maj", "capt")
        val tokens = source.split(Regex("\\s+"))
        val firstValidToken = tokens.firstOrNull { token ->
            val alpha = token.replace(Regex("[^A-Za-z\\p{L}]"), "").lowercase()
            alpha.isNotEmpty() && !skippedPrefixes.contains(alpha)
        } ?: tokens.firstOrNull() ?: "#"

        return firstValidToken.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
    }

    /**
     * Persists read access to a gallery photo selected via Photo Picker so InCallActivity can always load it.
     */
    fun persistGalleryUriPermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Extracts a rich 3-stop dark-mode-safe gradient from a contact photo URI,
     * or falls back to the contact icon's deterministic accent color if no photo is set.
     */
    suspend fun extractPaletteFromUriOrAvatar(
        context: Context,
        photoUriString: String?,
        callerName: String,
        callerNumber: String,
    ): List<Color> = withContext(Dispatchers.IO) {
        if (!photoUriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(photoUriString)
                val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.setTargetSize(64, 64)
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }

                if (bitmap != null) {
                    val dominantArgb = sampleVibrantColorFromBitmap(bitmap)
                    return@withContext buildThreeStopDarkGradient(Color(dominantArgb))
                }
            } catch (_: Exception) {
            }
        }

        // Fallback: build 3-stop ambient gradient from the contact icon's deterministic color
        val baseAvatarColor = getAvatarAccentColor(callerName, callerNumber)
        buildThreeStopDarkGradient(baseAvatarColor)
    }

    private fun sampleVibrantColorFromBitmap(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        val hsl = FloatArray(3)
        var bestScore = -1f
        var bestColor = android.graphics.Color.parseColor("#0F766E")

        val stepX = (width / 12).coerceAtLeast(1)
        val stepY = (height / 12).coerceAtLeast(1)

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(pixel)
                if (alpha < 128) continue

                ColorUtils.colorToHSL(pixel, hsl)
                val saturation = hsl[1]
                val lightness = hsl[2]

                // Favor saturated mid-tone pixels (avoid pure black/white)
                if (lightness in 0.15f..0.85f) {
                    val score = saturation * 2.2f + (1f - kotlin.math.abs(lightness - 0.5f))
                    if (score > bestScore) {
                        bestScore = score
                        bestColor = pixel
                    }
                }
            }
        }
        return bestColor
    }

    fun buildThreeStopDarkGradient(seedColor: Color): List<Color> {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seedColor.toArgb(), hsl)

        // Top Stop: Rich dark saturated hue (Lightness ~ 26%)
        val topHsl = floatArrayOf(hsl[0], (hsl[1].coerceAtLeast(0.45f)).coerceAtMost(0.85f), 0.26f)
        // Middle Stop: Deep ambient tone (Lightness ~ 13%)
        val midHsl = floatArrayOf((hsl[0] + 12f) % 360f, (hsl[1] * 0.8f).coerceAtLeast(0.35f), 0.13f)
        // Bottom Stop: Near-AMOLED pitch dark (Lightness ~ 4%)
        val botHsl = floatArrayOf(hsl[0], (hsl[1] * 0.5f), 0.04f)

        return listOf(
            Color(ColorUtils.HSLToColor(topHsl)),
            Color(ColorUtils.HSLToColor(midHsl)),
            Color(ColorUtils.HSLToColor(botHsl)),
        )
    }

    @Composable
    fun rememberResolvedCallBackgroundColors(
        themeIdOrTitle: String,
        callerPhotoUri: String?,
        callerName: String,
        callerNumber: String,
        isEnded: Boolean,
    ): State<List<Color>> {
        val context = androidx.compose.ui.platform.LocalContext.current
        val colorsState = remember {
            mutableStateOf(listOf(Color(0xFF111827), Color(0xFF07090E), Color(0xFF000000)))
        }

        LaunchedEffect(themeIdOrTitle, callerPhotoUri, callerName, callerNumber, isEnded) {
            if (isEnded) {
                colorsState.value = listOf(Color(0xFF2A0A12), Color(0xFF110509), Color(0xFF000000))
                return@LaunchedEffect
            }

            // Match by ID or legacy title
            val matchedPreset = allPresets.find {
                it.id.equals(themeIdOrTitle, ignoreCase = true) ||
                    it.title.equals(themeIdOrTitle, ignoreCase = true)
            }

            if (themeIdOrTitle == "GRAD_AUTO_AVATAR" ||
                themeIdOrTitle.contains("Auto", ignoreCase = true) ||
                matchedPreset?.category == CallBgCategory.AUTO_AVATAR
            ) {
                colorsState.value = extractPaletteFromUriOrAvatar(
                    context = context,
                    photoUriString = callerPhotoUri,
                    callerName = callerName,
                    callerNumber = callerNumber,
                )
            } else if (matchedPreset != null) {
                colorsState.value = matchedPreset.colors
            } else {
                // Default fallback to Auto-Extract from Contact Avatar/Icon!
                colorsState.value = extractPaletteFromUriOrAvatar(
                    context = context,
                    photoUriString = callerPhotoUri,
                    callerName = callerName,
                    callerNumber = callerNumber,
                )
            }
        }

        return colorsState
    }
}