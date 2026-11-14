@file:OptIn(ExperimentalMaterial3Api::class)

package com.shiyin.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shiyin.player.R
import com.shiyin.player.data.HistoryEntry
import com.shiyin.player.data.PlayHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val TIME_FMT_CSV = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun formatTime(ms: Long): String = try {
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(TIME_FMT)
} catch (_: Exception) {
    ""
}

private fun formatTimeCsv(ms: Long): String = try {
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(TIME_FMT_CSV)
} catch (_: Exception) {
    ""
}

/** CSV 字段转义:含逗号/引号/换行时加引号包裹,内部引号翻倍 */
private fun csvEscape(v: String): String =
    if (v.contains(',') || v.contains('"') || v.contains('\n'))
        "\"" + v.replace("\"", "\"\"") + "\""
    else v

/** 听歌记录 Tab:歌名 + 播放时间 + 播放地点(省市区),支持导出 CSV、清空 */
@Composable
fun HistoryTab() {
    val entries = PlayHistory.entries.value
    var clearing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // 导出 CSV(UTF-8 BOM,Excel 直接打开不乱码)
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // UTF-8 BOM
                        os.write("播放时间,歌曲名称,UP主,省,市,区县\n".toByteArray(Charsets.UTF_8))
                        PlayHistory.entries.value.forEach { e ->
                            val line = listOf(
                                formatTimeCsv(e.playedAt),
                                csvEscape(e.title),
                                csvEscape(e.author),
                                csvEscape(e.province ?: ""),
                                csvEscape(e.city ?: ""),
                                csvEscape(e.district ?: ""),
                            ).joinToString(",")
                            os.write((line + "\n").toByteArray(Charsets.UTF_8))
                        }
                    }
                }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(ctx, "已导出", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "听歌记录",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${entries.size} 条",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 导出 CSV(在删除按钮左侧)
            IconButton(
                onClick = {
                    exportCsv.launch(
                        "shiyin-history-" +
                            java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) +
                            ".csv"
                    )
                },
                enabled = entries.isNotEmpty(),
            ) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = "导出播放记录", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { clearing = true }, enabled = entries.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_trash), contentDescription = "清空记录", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有播放记录\n放一首歌试试",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    bottom = BottomBarMetrics.clearHeight.value + 8.dp,
                ),
            ) {
                itemsIndexed(entries.asReversed()) { _, e ->
                    HistoryRow(e)
                }
            }
        }
    }

    if (clearing) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("清空记录") },
            text = { Text("确定清空全部 ${entries.size} 条听歌记录吗?(不影响歌单)") },
            confirmButton = {
                TextButton(onClick = {
                    PlayHistory.clear()
                    clearing = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun HistoryRow(e: HistoryEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                e.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    if (e.author.isNotBlank()) append("${e.author} · ")
                    append(formatTime(e.playedAt))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                e.placeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
