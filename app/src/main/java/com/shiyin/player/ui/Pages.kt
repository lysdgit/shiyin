@file:OptIn(ExperimentalMaterial3Api::class)

package com.shiyin.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.shiyin.player.R
import com.shiyin.player.bili.BiliApi
import com.shiyin.player.data.AppStore
import com.shiyin.player.data.Track
import com.shiyin.player.data.Galbum
import com.shiyin.player.player.PlayerBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/* ================= 歌单 Tab ================= */

@Composable
fun PlaylistsTab(nav: NavHostController) {
    val galbums = AppStore.galbums.value
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Galbum?>(null) }
    var newName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("歌单", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { creating = true }) {
                Icon(painterResource(R.drawable.ic_plus), contentDescription = "新建歌单")
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp, top = 12.dp, end = 12.dp,
                bottom = BottomBarMetrics.clearHeight.value + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { creating = true }
                        .padding(vertical = 20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("新建歌单", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(galbums) { _, g ->
                Box {
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { nav.navigate("galbum/${g.id}") },
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.45f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = g.cover,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Column(Modifier.padding(8.dp)) {
                            Text(
                                g.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${g.tracks.size}首",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // 卡片右上角删除
                    IconButton(
                        onClick = { deleting = g },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(30.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_trash),
                            contentDescription = "删除歌单",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("歌单名称") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        AppStore.upsertGalbum(
                            Galbum(
                                id = "c" + System.currentTimeMillis(),
                                title = newName.trim(),
                                author = "我的歌单",
                            )
                        )
                        newName = ""
                        creating = false
                    },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("取消") } },
        )
    }

    deleting?.let { g ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除歌单") },
            text = { Text("确定删除《${g.title}》吗?(不影响其他歌单)") },
            confirmButton = {
                TextButton(onClick = {
                    AppStore.removeGalbum(g.id)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

/* ================= 歌单详情页 ================= */

@Composable
fun GalbumPage(nav: NavHostController, id: String) {
    val g = AppStore.galbums.value.firstOrNull { it.id == id }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var deleting by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null || g == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(Json.encodeToString(g).toByteArray(Charsets.UTF_8))
                }
            }
            launch(Dispatchers.Main) {
                android.widget.Toast.makeText(ctx, "已导出", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (g == null) {
        Column(Modifier.fillMaxSize()) {
            SubTopBar("歌单", onBack = { nav.popBackStack() })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("歌单不存在或已被删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        SubTopBar("歌单", onBack = { nav.popBackStack() })
        // 头部卡片
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(110.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = g.cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    g.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text("${g.author} · ${g.tracks.size}首", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // 操作行
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Button(
                onClick = { if (g.tracks.isNotEmpty()) PlayerBus.playAll(g.tracks) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("播放全部")
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { exportLauncher.launch("${g.title}.json") }) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = "导出", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { deleting = true }) {
                Icon(painterResource(R.drawable.ic_trash), contentDescription = "删除歌单", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        // 曲目列表
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = BottomBarMetrics.clearHeight.value,
            ),
        ) {
            itemsIndexed(g.tracks) { _, t ->
                val q = AppStore.queue.value
                TrackRow(
                    track = t,
                    active = q.tracks.getOrNull(q.index)?.key == t.key,
                    onPlay = { PlayerBus.playTrack(t) },
                    onAddTo = { PlayerBus.openAddTo(t) },
                    onOpen = { PlayerBus.playTrack(t) },
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("删除歌单") },
            text = { Text("确定删除《${g.title}》吗?") },
            confirmButton = {
                TextButton(onClick = {
                    AppStore.removeGalbum(g.id)
                    deleting = false
                    nav.popBackStack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } },
        )
    }
}

/* ================= UP主详情页 ================= */

@Composable
fun UserPage(nav: NavHostController, mid: Long) {
    val user = SearchState.users.firstOrNull { it.mid == mid }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val works = remember { mutableStateListOf<Track>() }
    var page by remember { mutableStateOf(0) }
    var isEnd by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(pageNo: Int) {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            try {
                val r = BiliApi.getUserVideos(mid, pageNo)
                works.addAll(r.items)
                isEnd = r.isEnd
                page = pageNo
            } catch (e: Exception) {
                error = e.message ?: "获取作品失败"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(mid) { load(1) }

    Column(Modifier.fillMaxSize()) {
        SubTopBar(user?.name ?: "UP主", onBack = { nav.popBackStack() })
        if (user != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(user.avatar, 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${BiliApi.formatCount(user.fans)}粉丝 · ${user.videos}个作品",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (user.sign.isNotBlank()) {
                        Text(
                            user.sign,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Button(
                    onClick = { if (works.isNotEmpty()) PlayerBus.playAll(works) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("播放全部")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (works.isEmpty()) return@Button
                        val g = Galbum(
                            id = "u$mid",
                            title = "${user.name}的作品",
                            author = user.name,
                            cover = works.firstOrNull { !it.cover.isNullOrBlank() }?.cover,
                            tracks = works.toMutableList(),
                        )
                        AppStore.upsertGalbum(g)
                        android.widget.Toast.makeText(ctx, "已存为歌单", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(painterResource(R.drawable.ic_folder), contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("存为歌单")
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = BottomBarMetrics.clearHeight.value,
            ),
        ) {
            itemsIndexed(works) { _, t ->
                val q = AppStore.queue.value
                TrackRow(
                    track = t,
                    active = q.tracks.getOrNull(q.index)?.key == t.key,
                    onPlay = { PlayerBus.playTrack(t) },
                    onAddTo = { PlayerBus.openAddTo(t) },
                    onOpen = { PlayerBus.playTrack(t) },
                )
            }
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error ?: "", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { load(1) }) { Text("重试") }
                        }
                        loading -> CircularProgressIndicator(Modifier.height(24.dp))
                        !isEnd -> TextButton(onClick = { load(page + 1) }) { Text("加载更多") }
                        else -> Text("没有更多了", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
