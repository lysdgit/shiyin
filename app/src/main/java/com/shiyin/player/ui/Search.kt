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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.shiyin.player.R
import com.shiyin.player.bili.BiliApi
import com.shiyin.player.bili.BiliUser
import com.shiyin.player.data.AppStore
import com.shiyin.player.data.Track
import com.shiyin.player.player.PlayerBus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 搜索状态持久于对象层 —— 切换底部 Tab 不丢结果(对齐网页版行为) */
object SearchState {
    var query by mutableStateOf(TextFieldValue(""))
    var type by mutableStateOf("video") // video / user
    var videos = mutableStateListOf<Track>()
    var users = mutableStateListOf<BiliUser>()
    var numResults by mutableStateOf(0)
    var page by mutableStateOf(0)
    var isEnd by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var searched by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    val keyword: String get() = query.text.trim()

    fun reset() {
        videos.clear()
        users.clear()
        page = 0
        isEnd = false
        numResults = 0
        searched = false
        error = null
    }
}

@Composable
fun SearchPage(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    fun loadPage(pageNo: Int, fresh: Boolean) {
        val kw = SearchState.keyword
        if (kw.isEmpty() || SearchState.loading) return
        scope.launch {
            SearchState.loading = true
            SearchState.error = null
            try {
                if (SearchState.type == "video") {
                    val r = BiliApi.searchVideos(kw, pageNo)
                    if (fresh) SearchState.videos.clear()
                    SearchState.videos.addAll(r.items)
                    SearchState.numResults = r.numResults
                    SearchState.isEnd = r.isEnd
                } else {
                    val r = BiliApi.searchUsers(kw, pageNo)
                    if (fresh) SearchState.users.clear()
                    SearchState.users.addAll(r.items)
                    SearchState.numResults = r.numResults
                    SearchState.isEnd = r.isEnd
                }
                SearchState.page = pageNo
                SearchState.searched = true
            } catch (e: Exception) {
                SearchState.error = e.message ?: "搜索失败"
            } finally {
                SearchState.loading = false
            }
        }
    }

    fun doSearch() {
        // 中文输入法组词回车不触发(网页版 isComposing 同款防御)
        if (SearchState.query.composition != null) return
        SearchState.reset()
        loadPage(1, fresh = true)
    }

    /** 行 ▶ 智能分流:多P → 聚合歌单并打开;单P → 直接播放(网页版 expandToGalbum 语义) */
    fun onRowPlay(track: Track) {
        scope.launch {
            val t = BiliApi.ensureMeta(track)
            if (t.pagesCount > 1 && t.pages != null && !t.bvid.isNullOrBlank()) {
                AppStore.upsertGalbum(AppStore.multiPGalbumOf(t))
                nav.navigate("galbum/${t.bvid}")
            } else {
                PlayerBus.playTrack(t)
            }
        }
    }

    fun onRowAddTo(track: Track) {
        scope.launch {
            val t = BiliApi.ensureMeta(track)
            if (t.pagesCount > 1 && t.pages != null) {
                AppStore.upsertGalbum(AppStore.multiPGalbumOf(t))
                android.widget.Toast.makeText(ctx, "已存为歌单", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                PlayerBus.openAddTo(t)
            }
        }
    }

    /** 搜索页播放全部:多P条目展开为分P曲目 */
    fun playAllLoaded() {
        scope.launch {
            val sem = Semaphore(3)
            val list = coroutineScope {
                SearchState.videos.map { t ->
                    async {
                        sem.withPermit {
                            val e = BiliApi.ensureMeta(t)
                            if (e.pagesCount > 1 && e.pages != null) {
                                e.pages.map { pg ->
                                    Track(
                                        bvid = e.bvid,
                                        aid = e.aid,
                                        p = pg.p,
                                        title = "P${pg.p} ${pg.part}",
                                        author = e.author,
                                        cover = e.cover,
                                        duration = pg.duration,
                                        pagesCount = 1,
                                        cid = pg.cid,
                                    )
                                }
                            } else listOf(e)
                        }
                    }
                }.awaitAll().flatten()
            }
            if (list.isEmpty()) return@launch
            PlayerBus.playAll(list)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 搜索框 + 粘贴入口
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = SearchState.query,
                onValueChange = { SearchState.query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索 B 站视频 / UP主", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                trailingIcon = {
                    IconButton(onClick = { doSearch() }) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = "搜索")
                    }
                },
            )
            IconButton(onClick = { PlayerBus.showPaste.value = true }) {
                Icon(painterResource(R.drawable.ic_queue), contentDescription = "粘贴解析")
            }
        }
        // 类型切换 + 计数 + 播放全部
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = SearchState.type == "video",
                onClick = {
                    if (SearchState.type != "video") {
                        SearchState.type = "video"
                        if (SearchState.searched) doSearch()
                    }
                },
                label = { Text("视频") },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = SearchState.type == "user",
                onClick = {
                    if (SearchState.type != "user") {
                        SearchState.type = "user"
                        if (SearchState.searched) doSearch()
                    }
                },
                label = { Text("UP主") },
            )
            Spacer(Modifier.weight(1f))
            if (SearchState.searched && SearchState.type == "video" && SearchState.videos.isNotEmpty()) {
                TextButton(onClick = { playAllLoaded() }) {
                    Text("播放全部(${SearchState.videos.size})")
                }
            }
        }
        if (SearchState.searched) {
            Text(
                "约 ${BiliApi.formatCount(SearchState.numResults.toLong())} 个结果",
                style = MaterialTheme.typography.labelSmall,
                color = SubText,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        when {
            SearchState.error != null -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(48.dp))
                    Text(
                        SearchState.error ?: "搜索失败",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { loadPage(1, fresh = true) }) { Text("重试") }
                }
            }
            SearchState.loading && !SearchState.searched -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            !SearchState.searched -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("搜一搜,或者点右上角粘贴列表", color = SubText)
                }
            }
            SearchState.type == "video" -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(SearchState.videos) { _, t ->
                        val q = AppStore.queue.value
                        TrackRow(
                            track = t,
                            active = q.tracks.getOrNull(q.index)?.key == t.key,
                            onPlay = { onRowPlay(t) },
                            onAddTo = { onRowAddTo(t) },
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
                                SearchState.loading -> CircularProgressIndicator(Modifier.height(24.dp))
                                !SearchState.isEnd -> TextButton(
                                    onClick = { loadPage(SearchState.page + 1, fresh = false) }
                                ) { Text("加载更多") }
                                else -> Text(
                                    "没有更多了",
                                    color = SubText,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(SearchState.users) { _, u ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { nav.navigate("user/${u.mid}") }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(u.avatar, 46.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${BiliApi.formatCount(u.fans)}粉丝 · ${u.videos}个作品",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SubText,
                                    maxLines = 1,
                                )
                                if (u.sign.isNotBlank()) {
                                    Text(
                                        u.sign,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SubText,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Icon(
                                painterResource(R.drawable.ic_next),
                                contentDescription = null,
                                tint = SubText,
                            )
                        }
                    }
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (SearchState.loading) CircularProgressIndicator(Modifier.height(24.dp))
                            else if (!SearchState.isEnd) TextButton(
                                onClick = { loadPage(SearchState.page + 1, fresh = false) }
                            ) { Text("加载更多") }
                            else Text(
                                "没有更多了",
                                color = SubText,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** UP主头像(圆形) */
@Composable
fun Avatar(url: String?, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Surface2)
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

/** 粘贴解析弹窗 */
@Composable
fun PasteDialog() {
    var text by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = { PlayerBus.showPaste.value = false },
        title = { Text("粘贴解析") },
        text = {
            Column {
                Text(
                    "支持 BV/av 号、视频链接、b23.tv 短链、收藏夹链接或 ID;多P视频和收藏夹将创建为歌单",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubText,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                )
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("解析中…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && !running,
                onClick = {
                    running = true
                    scope.launch {
                        try {
                            val r = BiliApi.parseText(text)
                            val sb = StringBuilder()
                            if (r.items.isNotEmpty()) {
                                PlayerBus.addTracks(r.items)
                                sb.append("已加入队列 ${r.items.size} 首")
                            }
                            for (g in r.collections) {
                                AppStore.upsertGalbum(g)
                                if (sb.isNotEmpty()) sb.append(";")
                                sb.append("歌单《${g.title}》${g.tracks.size}首")
                            }
                            if (r.errors.isNotEmpty()) {
                                if (sb.isNotEmpty()) sb.append(";")
                                sb.append("${r.errors.size} 条失败")
                            }
                            android.widget.Toast.makeText(
                                ctx,
                                if (sb.isEmpty()) "没有识别到内容" else sb.toString(),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            PlayerBus.showPaste.value = false
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(
                                ctx,
                                e.message ?: "解析失败",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } finally {
                            running = false
                        }
                    }
                },
            ) { Text("解析") }
        },
        dismissButton = {
            TextButton(onClick = { PlayerBus.showPaste.value = false }) { Text("取消") }
        },
    )
}
