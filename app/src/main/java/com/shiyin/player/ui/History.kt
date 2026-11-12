@file:OptIn(ExperimentalMaterial3Api::class)

package com.shiyin.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shiyin.player.R
import com.shiyin.player.data.HistoryEntry
import com.shiyin.player.data.PlayHistory
import com.shiyin.player.data.Track
import com.shiyin.player.player.PlayerBus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatTime(ms: Long): String = try {
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(TIME_FMT)
} catch (_: Exception) {
    ""
}

/** 听歌记录 Tab:歌名 + 播放时间 + 播放地点(省市区),可重播、可清空 */
@Composable
fun HistoryTab() {
    val entries = PlayHistory.entries.value
    var clearing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("听歌记录", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                "${entries.size} 条",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { clearing = true }, enabled = entries.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_trash), contentDescription = "清空记录", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有播放记录\n放一首歌试试", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
        AlertDialog(
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
    val canReplay = e.bvid != null || e.aid != null

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = canReplay) {
                PlayerBus.playTrack(
                    Track(
                        bvid = e.bvid,
                        aid = e.aid,
                        p = e.p,
                        title = e.title,
                        author = e.author,
                        cover = e.cover,
                        cid = e.cid,
                    )
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(e.cover, 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.padding(top = 1.dp))
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
        if (canReplay) {
            IconButton(onClick = {
                PlayerBus.playTrack(
                    Track(
                        bvid = e.bvid,
                        aid = e.aid,
                        p = e.p,
                        title = e.title,
                        author = e.author,
                        cover = e.cover,
                        cid = e.cid,
                    )
                )
            }) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = "重播")
            }
        }
    }
}
