@file:OptIn(ExperimentalMaterial3Api::class)

package com.shiyin.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.shiyin.player.R
import com.shiyin.player.bili.BiliApi
import com.shiyin.player.data.AppStore
import com.shiyin.player.data.Track
import com.shiyin.player.player.PlayerBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ================= 迷你播放条 ================= */

@Composable
fun MiniBar(onOpenPlayer: () -> Unit) {
    val q = AppStore.queue.value
    val track = q.tracks.getOrNull(q.index) ?: return
    val playing = PlayerBus.isPlaying.value

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPlayer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.cover, 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { PlayerBus.togglePlay() }) {
            Icon(
                painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = if (playing) "暂停" else "播放",
            )
        }
        IconButton(onClick = { PlayerBus.next() }) {
            Icon(painterResource(R.drawable.ic_next), contentDescription = "下一首")
        }
    }
}

/* ================= 全屏播放页 ================= */

@Composable
fun FullPlayer(nav: NavHostController, backdrop: Backdrop) {
    val q = AppStore.queue.value
    val track = q.tracks.getOrNull(q.index)
    val playing = PlayerBus.isPlaying.value
    val dark = isDarkTheme()
    val speedLabel = AppStore.settings.value.speed.let { s ->
        if (s % 1f == 0f) "${s.toInt()}x" else "${s}x"
    }

    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var dialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val c = PlayerBus.controller
            if (c != null && !dragging) {
                position = c.currentPosition
                duration = c.duration.coerceAtLeast(0)
            }
            delay(300)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dy -> dragAccum += dy },
                    onDragEnd = {
                        if (dragAccum > 140f) nav.popBackStack()
                        dragAccum = 0f
                    },
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶栏:下滑关闭 + 定时倒计时提示
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(
                        painterResource(R.drawable.ic_back),
                        contentDescription = "收起",
                        modifier = Modifier.graphicsLayer(rotationZ = -90f),
                    )
                }
                Spacer(Modifier.weight(1f))
                PlayerBus.sleepUntil.value?.let { until ->
                    val remainMin = ((until - System.currentTimeMillis()).coerceAtLeast(0)) / 60000
                    Text("定时 ${remainMin + 1} 分钟", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.weight(1f))
            // 大封面
            Box(
                Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = track?.cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                track?.title ?: "未在播放",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                track?.author ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            // 进度
            val sliderValue = if (dragging) dragPos else {
                if (duration > 0) position.toFloat() / duration * 100f else 0f
            }
            Slider(
                value = sliderValue.coerceIn(0f, 100f),
                onValueChange = {
                    dragging = true
                    dragPos = it
                },
                onValueChangeFinished = {
                    dragging = false
                    PlayerBus.seekTo((dragPos / 100f * duration).toLong())
                },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    BiliApi.formatDuration((position / 1000).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    BiliApi.formatDuration((duration / 1000).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 主控制
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { PlayerBus.prev() }) {
                    Icon(
                        painterResource(R.drawable.ic_prev),
                        contentDescription = "上一首",
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(56.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                blur(6f.dp.toPx())
                                if (Build.VERSION.SDK_INT >= 33) lens(6f.dp.toPx(), 10f.dp.toPx())
                            },
                            onDrawSurface = { drawRect(Pink.copy(alpha = 0.85f)) },
                        )
                        .clickable { PlayerBus.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (playing) "暂停" else "播放",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { PlayerBus.next() }) {
                    Icon(
                        painterResource(R.drawable.ic_next),
                        contentDescription = "下一首",
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            // 副控制(玻璃胶囊)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(24.dp) },
                        effects = {
                            blur(8f.dp.toPx())
                            if (Build.VERSION.SDK_INT >= 33) lens(8f.dp.toPx(), 12f.dp.toPx())
                        },
                        // 副控制胶囊同样接近实底,保证文字清晰
                        onDrawSurface = {
                            if (dark) drawRect(Color(0xFF1E1E1E).copy(alpha = 0.95f))
                            else drawRect(Color(0xFFFFFFFF).copy(alpha = 0.95f))
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = { dialog = "speed" }) {
                        Text(speedLabel, color = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(onClick = { dialog = "sleep" }) { Text("定时", color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(onClick = { dialog = "quality" }) {
                        Text(
                            when (AppStore.settings.value.quality) {
                                "low" -> "流畅"
                                "standard" -> "标准"
                                else -> "高音质"
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    TextButton(onClick = { PlayerBus.showQueue.value = true }) {
                        Text("队列", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }

    when (dialog) {
        "speed" -> OptionDialog(
            title = "倍速",
            options = listOf("0.75x" to 0.75f, "1x" to 1f, "1.25x" to 1.25f, "1.5x" to 1.5f, "2x" to 2f),
            currentLabel = speedLabel,
            onSelect = { PlayerBus.setSpeed(it) },
            onDismiss = { dialog = null },
        )
        "sleep" -> SleepDialog(onDismiss = { dialog = null })
        "quality" -> OptionDialog(
            title = "音质",
            options = listOf("流畅" to 0f, "标准" to 1f, "高音质" to 2f),
            currentLabel = when (AppStore.settings.value.quality) {
                "low" -> "流畅"
                "standard" -> "标准"
                else -> "高音质"
            },
            onSelect = { value ->
                val q2 = when (value) {
                    0f -> "low"
                    1f -> "standard"
                    else -> "high"
                }
                PlayerBus.setQuality(q2)
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun OptionDialog(
    title: String,
    options: List<Pair<String, Float>>,
    currentLabel: String,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    TextButton(
                        onClick = {
                            onSelect(value)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            label,
                            color = if (label == currentLabel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SleepDialog(onDismiss: () -> Unit) {
    val active = PlayerBus.sleepUntil.value != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("睡眠定时") },
        text = {
            Column {
                listOf(15, 30, 60, 90).forEach { m ->
                    TextButton(
                        onClick = {
                            PlayerBus.setSleepTimer(m)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("$m 分钟") }
                }
                if (active) {
                    TextButton(
                        onClick = {
                            PlayerBus.setSleepTimer(null)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("关闭定时", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/* ================= 队列抽屉 ================= */

@Composable
fun QueueSheet() {
    val q = AppStore.queue.value
    var pPick by remember { mutableStateOf<Pair<Int, Track>?>(null) }

    ModalBottomSheet(
        onDismissRequest = { PlayerBus.showQueue.value = false },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "播放列表(${q.tracks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { PlayerBus.toggleMode() }) {
                    Text(if (q.mode == "one") "单曲循环" else "列表循环", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { PlayerBus.clearQueue() }) {
                    Icon(painterResource(R.drawable.ic_trash), contentDescription = "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
            ) {
                itemsIndexed(q.tracks) { i, t ->
                    val active = i == q.index
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { PlayerBus.playAt(i) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(26.dp),
                        )
                        Cover(t.cover, 40.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                t.author,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (t.isMultiP) {
                            TextButton(onClick = { pPick = Pair(i, t) }) {
                                Text("切P", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { PlayerBus.removeAt(i) }) {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = "移除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    pPick?.let { (idx, t) ->
        AlertDialog(
            onDismissRequest = { pPick = null },
            title = { Text("切换分P") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items((t.pages ?: emptyList())) { pg ->
                        TextButton(
                            onClick = {
                                PlayerBus.switchP(idx, pg)
                                pPick = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "P${pg.p} ${pg.part}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pPick = null }) { Text("取消") } },
        )
    }
}

/* ================= 加入歌单弹窗 ================= */

@Composable
fun AddToSheet(track: Track) {
    val galbums = AppStore.galbums.value
    var newName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = { PlayerBus.closeAddTo() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("加入歌单", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("新歌单名称", style = MaterialTheme.typography.bodyMedium) },
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val t = BiliApi.ensureMeta(track)
                            val g = com.shiyin.player.data.Galbum(
                                id = "c" + System.currentTimeMillis(),
                                title = newName.trim(),
                                author = "我的歌单",
                                cover = t.cover,
                                tracks = mutableListOf(t),
                            )
                            AppStore.upsertGalbum(g)
                            android.widget.Toast.makeText(ctx, "已创建并加入", android.widget.Toast.LENGTH_SHORT).show()
                            PlayerBus.closeAddTo()
                        }
                    },
                ) { Text("创建") }
            }
            Spacer(Modifier.height(8.dp))
            Text("选择已有歌单", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                items(galbums) { g ->
                    val joined = g.tracks.any { it.key == track.key }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !joined) {
                                scope.launch {
                                    val t = BiliApi.ensureMeta(track)
                                    val ok = AppStore.addTrackTo(g, t)
                                    android.widget.Toast.makeText(
                                        ctx,
                                        if (ok) "已加入《${g.title}》" else "已在歌单中",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                    if (ok) PlayerBus.closeAddTo()
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(g.cover, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(g.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (joined) {
                            Text("已加入 ✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
