@file:OptIn(ExperimentalMaterial3Api::class)

package com.shiyin.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.shiyin.player.R
import com.shiyin.player.data.AppStore
import com.shiyin.player.util.CrashLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 设置 Tab:主题切换(深色/浅色)+ 玻璃参数调节,均即时生效 */
@Composable
fun SettingsTab() {
    val s = AppStore.settings.value
    val theme = s.theme
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val exportAllGalbums = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(Json.encodeToString(AppStore.galbums.value).toByteArray(Charsets.UTF_8))
                    }
                }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(ctx, "已导出全部歌单", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val importGalbumsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val text = runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull()
                val r = text?.let { AppStore.importGalbums(it) }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        ctx,
                        when {
                            r == null -> "文件格式无法识别"
                            r.first + r.second == 0 -> "文件里没有歌单"
                            else -> "导入 ${r.first} 个歌单,覆盖 ${r.second} 个"
                        },
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = BottomBarMetrics.clearHeight.value + 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "设置",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                "拾音 v" + com.shiyin.player.BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 主题
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text("主题", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                ThemeOption("深色", "dark", theme, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                ThemeOption("浅色", "light", theme, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "切换后立即生效",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        // 玻璃效果(拖动时底部栏实时变化)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text("玻璃效果", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "调小不透明度玻璃感更强,调大文字更清晰;切到「搜索」页滚动列表即可对比观察",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "不透明度 ${String.format("%.0f%%", s.glassAlpha * 100)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = s.glassAlpha,
                onValueChange = { v -> AppStore.settings.value = AppStore.settings.value.copy(glassAlpha = v) },
                onValueChangeFinished = { AppStore.saveSettings() },
                valueRange = 0f..1f,
            )
            Text(
                "模糊强度 ${s.glassBlur.toInt()}dp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = s.glassBlur,
                onValueChange = { v -> AppStore.settings.value = AppStore.settings.value.copy(glassBlur = v) },
                onValueChangeFinished = { AppStore.saveSettings() },
                valueRange = 0f..24f,
            )
            TextButton(onClick = {
                AppStore.settings.value = AppStore.settings.value.copy(glassAlpha = 0.5f, glassBlur = 0f)
                AppStore.saveSettings()
            }) {
                Text("恢复默认参数(50% / 0dp)")
            }
        }

        Spacer(Modifier.height(16.dp))

        // 滑块动画参数(点 tab / 拖拽吸附的弹簧手感)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text("滑块动画", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "切到「搜索」页点 tab 试手感;过冲越小越弹,1.0 无过冲",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "过冲 ${String.format("%.2f", s.damp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = s.damp,
                onValueChange = { v -> AppStore.settings.value = AppStore.settings.value.copy(damp = v) },
                onValueChangeFinished = { AppStore.saveSettings() },
                valueRange = 0.4f..1f,
            )
            Text(
                "速度 ${s.stiff.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = s.stiff,
                onValueChange = { v -> AppStore.settings.value = AppStore.settings.value.copy(stiff = v) },
                onValueChangeFinished = { AppStore.saveSettings() },
                valueRange = 100f..500f,
            )
            Text(
                "拉伸 ${(s.stretchK * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = s.stretchK,
                onValueChange = { v -> AppStore.settings.value = AppStore.settings.value.copy(stretchK = v) },
                onValueChangeFinished = { AppStore.saveSettings() },
                valueRange = 0f..0.5f,
            )
            Text(
                "图标放大 ${(s.iconZoom * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = s.iconZoom,
                onValueChange = { v -> AppStore.settings.value = AppStore.settings.value.copy(iconZoom = v) },
                onValueChangeFinished = { AppStore.saveSettings() },
                valueRange = 0f..0.3f,
            )
        }

        Spacer(Modifier.height(16.dp))

        // 歌单备份(全部导出 / 导入,JSON)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text("歌单备份", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "导出全部歌单为 JSON;导入时同 ID 歌单将被覆盖,其余追加",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        exportAllGalbums.launch(
                            "shiyin-galbums-" +
                                java.time.LocalDateTime.now()
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) +
                                ".json"
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painterResource(com.shiyin.player.R.drawable.ic_download),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("导出全部")
                }
                OutlinedButton(
                    onClick = { importGalbumsLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painterResource(com.shiyin.player.R.drawable.ic_folder),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("导入")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text("关于", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "拾音 Shiyin · B站音频搜索与播放器",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "仅供个人学习使用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val uriHandler = LocalUriHandler.current
            Text(
                "GitHub:github.com/lysdgit/shiyin",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { uriHandler.openUri("https://github.com/lysdgit/shiyin") },
            )
            // 有闪退记录时提供导出(给开发者排查)
            val ctx = LocalContext.current
            val crashFile = remember { CrashLog.file(ctx) }
            if (crashFile.exists()) {
                Spacer(Modifier.height(8.dp))
                val exportScope = rememberCoroutineScope()
                val exportLog = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/plain")
                ) { uri ->
                    if (uri != null && crashFile.exists()) {
                        exportScope.launch(Dispatchers.IO) {
                            runCatching {
                                ctx.contentResolver.openOutputStream(uri)?.use { os ->
                                    os.write(crashFile.readText().toByteArray(Charsets.UTF_8))
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { exportLog.launch("shiyin-crash.log") }) {
                    Text("导出诊断日志(应用闪退记录)", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(label: String, value: String, current: String, modifier: Modifier = Modifier) {
    val selected = current == value
    val dotColor = if (value == "dark") Color(0xFF161616) else Color(0xFFFAFAFA)
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable {
                AppStore.settings.value = AppStore.settings.value.copy(theme = value)
                AppStore.saveSettings()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
