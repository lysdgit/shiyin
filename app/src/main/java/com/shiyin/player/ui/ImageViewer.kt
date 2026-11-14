@file:OptIn(ExperimentalMaterial3Api::class)

package com.shiyin.player.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.shiyin.player.R
import com.shiyin.player.bili.BiliApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** B站封面 URL 去掉 @ 裁剪参数(如 @480w_270h.webp),得到原图地址 */
fun originalImageUrl(cover: String): String =
    if (cover.contains('@')) cover.substringBefore('@') else cover

/** 封面原图全屏查看:单击不关闭(避免误触),长按保存,右下角保存按钮 */
@Composable
fun CoverViewerOverlay(url: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    fun doSave() {
        if (saving) return
        saving = true
        scope.launch(Dispatchers.IO) {
            val msg = runCatching { CoverSaver.save(ctx, url) }
                .fold({ it }, { "保存失败:${it.message}" })
            launch(Dispatchers.Main) {
                saving = false
                android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = url,
                contentDescription = "封面原图",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onDismiss() },
                            onLongPress = { doSave() },
                        )
                    },
            )
            // 左上角:关闭
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = "关闭",
                    tint = Color.White,
                )
            }
            // 右下角:保存原图
            IconButton(
                onClick = { doSave() },
                enabled = !saving,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_download),
                        contentDescription = "保存原图",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/** 保存封面原图:Android 10+ 写入系统相册 Pictures/Shiyin;更早版本写入应用图片目录 */
private object CoverSaver {
    fun save(context: Context, url: String): String {
        val bytes = runCatching {
            val req = okhttp3.Request.Builder().url(url).header("User-Agent", BiliApi.UA).build()
            BiliApi.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("下载失败 HTTP ${resp.code}")
                resp.body?.bytes() ?: throw IOException("空响应")
            }
        }.getOrThrow()

        val ts = System.currentTimeMillis()
        val ext = url.substringAfterLast('.', "").lowercase().take(4)
        val fileName = "shiyin-cover-$ts.${if (ext.isBlank()) "jpg" else ext}"
        val mime = when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Shiyin")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("创建相册文件失败")
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IOException("写入相册失败")
            "已保存到相册 Pictures/Shiyin"
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Shiyin").apply { mkdirs() }
            val f = File(dir, fileName)
            f.writeBytes(bytes)
            "已保存:${f.absolutePath}"
        }
    }
}
