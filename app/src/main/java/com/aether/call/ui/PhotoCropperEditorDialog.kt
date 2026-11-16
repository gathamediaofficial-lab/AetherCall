package com.aether.call.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PhotoCropProcessor {

    suspend fun cropAndSavePhoto(
        context: Context,
        imageUriStr: String,
        rotationDegrees: Float,
        zoomScale: Float,
        panX: Float,
        panY: Float,
        outputKey: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(imageUriStr)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return@withContext null
            inputStream.close()

            // 1. Matrix Rotation
            val matrix = Matrix()
            if (rotationDegrees != 0f) {
                matrix.postRotate(rotationDegrees)
            }

            val rotatedBitmap = if (rotationDegrees != 0f) {
                Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
            } else {
                originalBitmap
            }

            // 2. Center Crop with Zoom and Offset
            val minDim = Math.min(rotatedBitmap.width, rotatedBitmap.height)
            val cropSize = (minDim / zoomScale).coerceIn(100f, minDim.toFloat()).toInt()

            val centerX = (rotatedBitmap.width / 2f - panX / (zoomScale * 2f)).coerceIn(cropSize / 2f, rotatedBitmap.width - cropSize / 2f)
            val centerY = (rotatedBitmap.height / 2f - panY / (zoomScale * 2f)).coerceIn(cropSize / 2f, rotatedBitmap.height - cropSize / 2f)

            val cropLeft = (centerX - cropSize / 2f).toInt().coerceIn(0, rotatedBitmap.width - cropSize)
            val cropTop = (centerY - cropSize / 2f).toInt().coerceIn(0, rotatedBitmap.height - cropSize)

            val croppedBitmap = Bitmap.createBitmap(rotatedBitmap, cropLeft, cropTop, cropSize, cropSize)

            // 3. Save Compressed JPEG
            val photoFile = File(context.filesDir, "custom_contact_${outputKey}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(photoFile).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            return@withContext Uri.fromFile(photoFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}

@Composable
fun PhotoCropperEditorDialog(
    sourcePhotoUri: String,
    outputKey: String = "photo",
    onPhotoSaved: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1.0f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF090B10),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }

                    Text(
                        text = "Adjust Contact Photo",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )

                    IconButton(
                        onClick = {
                            if (!isSaving) {
                                isSaving = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    val savedUri = PhotoCropProcessor.cropAndSavePhoto(
                                        context = context,
                                        imageUriStr = sourcePhotoUri,
                                        rotationDegrees = rotation,
                                        zoomScale = scale,
                                        panX = offsetX,
                                        panY = offsetY,
                                        outputKey = outputKey,
                                    )
                                    isSaving = false
                                    if (savedUri != null) {
                                        onPhotoSaved(savedUri)
                                    } else {
                                        onPhotoSaved(sourcePhotoUri)
                                    }
                                }
                            }
                        },
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF10B981), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Save", tint = Color(0xFF10B981))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Crop Viewport Frame
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF141619))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1.0f, 4.0f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Image with Pan, Zoom & Rotation Transforms
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(sourcePhotoUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Editing Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY,
                                rotationZ = rotation,
                            ),
                    )

                    // Circular / Square Guide Frame
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .border(BorderStroke(2.dp, Color(0xFF10B981)), CircleShape),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Controls Bar (Rotation, Reset, Zoom Slider)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E2024),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Zoom: ${(scale * 100).toInt()}%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        rotation = (rotation + 90f) % 360f
                                    },
                                ) {
                                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = Color.White)
                                }

                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        scale = 1.0f
                                        rotation = 0f
                                        offsetX = 0f
                                        offsetY = 0f
                                    },
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color(0xFF8E8E93))
                                }
                            }
                        }

                        Slider(
                            value = scale,
                            onValueChange = { scale = it },
                            valueRange = 1.0f..4.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF10B981),
                                activeTrackColor = Color(0xFF10B981),
                                inactiveTrackColor = Color(0xFF2E3138),
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save Action Button
                Button(
                    onClick = {
                        if (!isSaving) {
                            isSaving = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            coroutineScope.launch {
                                val savedUri = PhotoCropProcessor.cropAndSavePhoto(
                                    context = context,
                                    imageUriStr = sourcePhotoUri,
                                    rotationDegrees = rotation,
                                    zoomScale = scale,
                                    panX = offsetX,
                                    panY = offsetY,
                                    outputKey = outputKey,
                                )
                                isSaving = false
                                if (savedUri != null) {
                                    onPhotoSaved(savedUri)
                                } else {
                                    onPhotoSaved(sourcePhotoUri)
                                }
                            }
                        }
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("Apply & Save Photo", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
