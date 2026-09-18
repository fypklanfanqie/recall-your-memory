package com.echo.recall.feature.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 壁纸裁剪：拖动 + 双指缩放取景，确认后按所见导出 */
@Composable
fun WallpaperCropScreen(
    uri: Uri,
    onBack: () -> Unit,
    viewModel: AppStateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var src by remember { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        src = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2200) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }.getOrNull()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.Close, contentDescription = "取消", tint = Color.White)
            }
            Text("裁剪壁纸", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 19.5f)
                    .onSizeChanged { frame = it }
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = src
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                            }
                            .pointerInput(bmp, frame) {
                                detectTransformGestures { _, panAdd, zoomAdd, _ ->
                                    zoom = (zoom * zoomAdd).coerceIn(1f, 6f)
                                    pan += panAdd
                                    val maxX = maxOf(0f, (bmp.width * zoom - frame.width) / 2f)
                                    val maxY = maxOf(0f, (bmp.height * zoom - frame.height) / 2f)
                                    pan = Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
                                }
                            },
                    )
                } else {
                    Text("加载中…", color = Color.White)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("拖动 / 双指缩放取景", color = Color(0x99FFFFFF), modifier = Modifier.weight(1f))
            Button(
                enabled = !saving && src != null && frame.width > 0,
                onClick = {
                    val bmp = src ?: return@Button
                    saving = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    kotlinx.coroutines.MainScope().launch {
                        val ok = viewModel.saveCroppedWallpaper(frame, zoom, pan, bmp)
                        saving = false
                        if (ok) onBack()
                    }
                },
            ) {
                Text(if (saving) "保存中…" else "确认使用")
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
