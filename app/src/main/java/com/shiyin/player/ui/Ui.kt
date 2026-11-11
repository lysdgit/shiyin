@file:OptIn(ExperimentalMaterial3Api::class)

package com.shiyin.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.shiyin.player.R
import com.shiyin.player.bili.BiliApi
import com.shiyin.player.data.AppStore
import com.shiyin.player.data.Track
import com.shiyin.player.player.PlayerBus

/** 应用骨架:底部 Tab(搜索/歌单)+ 迷你播放条 + 全局弹层 */
@Composable
fun ShiyinApp() {
    ShiyinTheme {
        val nav: NavHostController = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route ?: "main"
        var currentTab by rememberSaveable { mutableIntStateOf(0) }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (route != "player") {
                    // Scaffold 不替自定义 bottomBar 避让系统手势条,
                    // 这里统一加导航栏 inset,并把小白条区域涂成播放条底色
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Surface1)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        MiniBar(onOpenPlayer = { nav.navigate("player") })
                        if (route == "main") {
                            NavigationBar(
                                containerColor = Surface1,
                                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                            ) {
                                NavigationBarItem(
                                    selected = currentTab == 0,
                                    onClick = { currentTab = 0 },
                                    icon = { Icon(painterResource(R.drawable.ic_search), null) },
                                    label = { Text("搜索") },
                                )
                                NavigationBarItem(
                                    selected = currentTab == 1,
                                    onClick = { currentTab = 1 },
                                    icon = { Icon(painterResource(R.drawable.ic_note), null) },
                                    label = { Text("歌单") },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                nav,
                startDestination = "main",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                composable("main") {
                    Box(Modifier.fillMaxSize()) {
                        if (currentTab == 0) SearchPage(nav) else PlaylistsTab(nav)
                    }
                }
                composable("user/{mid}") { entry ->
                    val mid = entry.arguments?.getString("mid")?.toLongOrNull() ?: 0L
                    UserPage(nav, mid)
                }
                composable("galbum/{id}") { entry ->
                    val id = entry.arguments?.getString("id") ?: ""
                    GalbumPage(nav, id)
                }
                composable("player") {
                    FullPlayer(nav)
                }
            }
        }

        // 全局弹层
        if (PlayerBus.showQueue.value) QueueSheet()
        PlayerBus.addToTrack.value?.let { AddToSheet(it) }
        if (PlayerBus.showPaste.value) PasteDialog()
    }
}

/* ---------------- 通用小组件 ---------------- */

@Composable
fun Cover(url: String?, size: Dp, radius: Int = 6) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(radius.dp))
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

/** 曲目行(搜索结果/歌单曲目/UP主作品通用):▶ 播放 + 📁 加入歌单 */
@Composable
fun TrackRow(
    track: Track,
    active: Boolean,
    onPlay: () -> Unit,
    onAddTo: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable {
                if (track.isMultiP) onOpen?.invoke() else onPlay()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.cover, 52.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(track.author)
                    if (track.duration > 0) append(" · ${BiliApi.formatDuration(track.duration)}")
                    if (track.play > 0) append(" · ${BiliApi.formatCount(track.play)}次播放")
                },
                style = MaterialTheme.typography.bodySmall,
                color = SubText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.pagesCount > 1) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .background(Surface2, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("P${track.pagesCount}", style = MaterialTheme.typography.labelSmall, color = SubText)
            }
        }
        IconButton(onClick = onPlay) {
            Icon(painterResource(R.drawable.ic_play), contentDescription = "播放")
        }
        if (onAddTo != null) {
            IconButton(onClick = onAddTo) {
                Icon(painterResource(R.drawable.ic_folder), contentDescription = "加入歌单")
            }
        }
    }
}

/** 二级页面顶栏:返回键 + 标题 */
@Composable
fun SubTopBar(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = "返回")
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

/** Surface 上的浅色文本小徽章 */
@Composable
fun Badge(text: String) {
    Box(
        Modifier
            .background(Surface2, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = SubText)
    }
}
