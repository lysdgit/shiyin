@file:OptIn(ExperimentalMaterial3Api::class)

package com.shiyin.player.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.shiyin.player.R
import com.shiyin.player.bili.BiliApi
import com.shiyin.player.data.AppStore
import com.shiyin.player.data.Track
import com.shiyin.player.player.PlayerBus

/** 内容列表需要避让的底栏实测高度(玻璃栏渲染后写回,页面列表读它加 contentPadding) */
object BottomBarMetrics {
    val clearHeight = mutableStateOf(110.dp)
}

/** 应用骨架:内容全幅 + 液态玻璃底栏(Android 12+;更低版本回退实色)+ 全局弹层 */
@Composable
fun ShiyinApp() {
    ShiyinTheme {
        val nav: NavHostController = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route ?: "main"
        var currentTab by rememberSaveable { mutableIntStateOf(0) }

        // 状态栏图标深浅跟随应用主题(切主题即时生效)
        LaunchedEffect(dark) {
            (LocalContext.current as? android.app.Activity)?.enableEdgeToEdge(
                statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                                 else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                                 else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val backdrop = rememberLayerBackdrop()
            NavHost(
                nav,
                startDestination = "main",
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .layerBackdrop(backdrop),
            ) {
                composable("main") {
                    Box(Modifier.fillMaxSize()) {
                        when (currentTab) {
                            0 -> SearchPage(nav)
                            1 -> PlaylistsTab(nav)
                            2 -> HistoryTab()
                            else -> SettingsTab()
                        }
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

            if (route != "player") {
                // 点 tab:切换内容页,并把二级页(歌单详情/UP主详情)弹回主界面
                val selectTab: (Int) -> Unit = { target ->
                    currentTab = target
                    nav.popBackStack("main", inclusive = false)
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    GlassBottomBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        backdrop = backdrop,
                        currentTab = currentTab,
                        onTab = selectTab,
                        onOpenPlayer = { nav.navigate("player") },
                    )
                } else {
                    SolidBottomBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        currentTab = currentTab,
                        onTab = selectTab,
                        onOpenPlayer = { nav.navigate("player") },
                    )
                }
            }
        }

        // 全局弹层
        if (PlayerBus.showQueue.value) QueueSheet()
        PlayerBus.addToTrack.value?.let { AddToSheet(it) }
        if (PlayerBus.showPaste.value) PasteDialog()
    }
}

/* ---------------- 底部栏(玻璃 / 实色,dock 交互) ---------------- */

@Composable
private fun GlassBottomBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    currentTab: Int,
    onTab: (Int) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val density = LocalDensity.current
    val dark = isDarkTheme()
    val s = AppStore.settings.value
    val glassAlpha = s.glassAlpha
    val glassBlur = s.glassBlur
    val scope = rememberCoroutineScope()
    val pillAnim = remember { Animatable(currentTab.toFloat()) } // 初始即对齐当前 tab
    val recoil = remember { Animatable(0f) }
    var panelInnerW by remember { mutableIntStateOf(0) }
    var dragStartTab by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val slot = if (panelInnerW > 0) panelInnerW / 4f else 0f
    // 点 tab:滑块从当前位置平滑滑过去,绝不跳回旧位置
    val onTabAnimated: (Int) -> Unit = { target ->
        scope.launch { pillAnim.animateTo(target.toFloat(), spring(dampingRatio = s.damp, stiffness = s.stiff)) }
        onTab(target)
    }
    val position = if (dragging && slot > 0) dragStartTab + dragOffset / slot else pillAnim.value
    val stretch = if (dragging) (dragOffset * s.stretchK).coerceIn(-48f, 48f) else recoil.value

    // dock 式悬浮:避让手势条 + 左右留边,面板本体为全圆角胶囊
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .onGloballyPositioned { coords ->
                BottomBarMetrics.clearHeight.value = with(density) { coords.size.height.toDp() }
            },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { panelInnerW = it.size.width }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (dragging) {
                            // 拖出边界时加橡皮筋阻尼,滑块拖不跑
                            val raw = dragStartTab + (dragOffset + delta) / slot
                            dragOffset += if (raw < 0f || raw > 3f) delta * 0.3f else delta
                        }
                    },
                    onDragStarted = {
                        dragging = true
                        dragStartTab = currentTab
                        dragOffset = 0f
                    },
                    onDragStopped = {
                        val raw = if (slot > 0) dragStartTab + dragOffset / slot else dragStartTab.toFloat()
                        val target = raw.roundToInt().coerceIn(0, 3)
                        val lastStretch = (dragOffset * s.stretchK).coerceIn(-48f, 48f)
                        dragging = false
                        dragOffset = 0f
                        // 滑块立即从松手位置吸附 + 页面立即切换(不等回弹)
                        pillAnim.snapTo(raw)
                        pillAnim.animateTo(target.toFloat(), spring(dampingRatio = s.damp, stiffness = s.stiff))
                        onTab(target)
                        // 拉伸回弹并行跑,不阻塞主流程
                        scope.launch {
                            recoil.snapTo(lastStretch)
                            recoil.animateTo(0f, spring(dampingRatio = s.damp, stiffness = s.stiff))
                        }
                    },
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(28.dp) },
                    effects = {
                        vibrancy()
                        blur(glassBlur.dp.toPx())
                        // 折射(lens)基于 AGSL RuntimeShader,仅 Android 13+ 支持完整效果
                        if (Build.VERSION.SDK_INT >= 33) lens(12f.dp.toPx(), 24f.dp.toPx())
                    },
                    // 不透明度可在设置页调,越低玻璃感越强,越高文字越清晰
                    onDrawSurface = {
                        if (dark) drawRect(Color(0xFF181818).copy(alpha = glassAlpha))
                        else drawRect(Color(0xFFFBFBFB).copy(alpha = glassAlpha))
                    },
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            MiniBar(onOpenPlayer = onOpenPlayer)
            TabRow(
                onTab = onTabAnimated,
                currentTab = currentTab,
                backdrop = backdrop,
                useGlass = true,
                position = position,
                stretch = stretch,
                iconZoom = s.iconZoom,
            )
        }
    }
}

@Composable
private fun SolidBottomBar(
    modifier: Modifier = Modifier,
    currentTab: Int,
    onTab: (Int) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val s = AppStore.settings.value
    val pillAnim = remember { Animatable(currentTab.toFloat()) } // 初始即对齐当前 tab
    val recoil = remember { Animatable(0f) }
    var panelInnerW by remember { mutableIntStateOf(0) }
    var dragStartTab by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val slot = if (panelInnerW > 0) panelInnerW / 4f else 0f
    // 点 tab:滑块从当前位置平滑滑过去,绝不跳回旧位置
    val onTabAnimated: (Int) -> Unit = { target ->
        scope.launch { pillAnim.animateTo(target.toFloat(), spring(dampingRatio = s.damp, stiffness = s.stiff)) }
        onTab(target)
    }
    val position = if (dragging && slot > 0) dragStartTab + dragOffset / slot else pillAnim.value
    val stretch = if (dragging) (dragOffset * s.stretchK).coerceIn(-48f, 48f) else recoil.value

    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .onGloballyPositioned { coords ->
                BottomBarMetrics.clearHeight.value = with(density) { coords.size.height.toDp() }
            },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { panelInnerW = it.size.width }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (dragging) {
                            // 拖出边界时加橡皮筋阻尼,滑块拖不跑
                            val raw = dragStartTab + (dragOffset + delta) / slot
                            dragOffset += if (raw < 0f || raw > 3f) delta * 0.3f else delta
                        }
                    },
                    onDragStarted = {
                        dragging = true
                        dragStartTab = currentTab
                        dragOffset = 0f
                    },
                    onDragStopped = {
                        val raw = if (slot > 0) dragStartTab + dragOffset / slot else dragStartTab.toFloat()
                        val target = raw.roundToInt().coerceIn(0, 3)
                        val lastStretch = (dragOffset * s.stretchK).coerceIn(-48f, 48f)
                        dragging = false
                        dragOffset = 0f
                        // 滑块立即从松手位置吸附 + 页面立即切换(不等回弹)
                        pillAnim.snapTo(raw)
                        pillAnim.animateTo(target.toFloat(), spring(dampingRatio = s.damp, stiffness = s.stiff))
                        onTab(target)
                        // 拉伸回弹并行跑,不阻塞主流程
                        scope.launch {
                            recoil.snapTo(lastStretch)
                            recoil.animateTo(0f, spring(dampingRatio = s.damp, stiffness = s.stiff))
                        }
                    },
                )
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            MiniBar(onOpenPlayer = onOpenPlayer)
            TabRow(
                onTab = onTabAnimated,
                currentTab = currentTab,
                backdrop = null,
                useGlass = false,
                position = position,
                stretch = stretch,
                iconZoom = s.iconZoom,
            )
        }
    }
}

/** 底部 Tab 行:选中滑块跟随 position(拖拽中跟手,松手后弹簧吸附);图标随滑块距离轻微放大 */
@Composable
private fun TabRow(
    onTab: (Int) -> Unit,
    currentTab: Int,
    backdrop: Backdrop?,
    useGlass: Boolean,
    position: Float,
    stretch: Float,
    iconZoom: Float,
) {
    var rowWidthPx by remember { mutableIntStateOf(0) }

    fun tabScale(index: Int): Float {
        if (rowWidthPx <= 0) return 1f
        val slot = rowWidthPx / 4f
        val pillCenter = position * slot + slot / 2f
        val center = index * slot + slot / 2f
        val dist = (kotlin.math.abs(center - pillCenter) / slot).coerceIn(0f, 1f)
        return 1f + iconZoom * (1f - dist)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .onGloballyPositioned { rowWidthPx = it.size.width },
    ) {
        // 选中滑块(玻璃或实色,画在文字下层)
        if (rowWidthPx > 0) {
            if (useGlass && backdrop != null && Build.VERSION.SDK_INT >= 31) {
                GlassTabPill(backdrop, position, stretch, rowWidthPx)
            } else {
                SolidTabPill(position, stretch, rowWidthPx)
            }
        }
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem("搜索", R.drawable.ic_search, 0, currentTab, onTab, tabScale(0), Modifier.weight(1f))
            TabItem("歌单", R.drawable.ic_note, 1, currentTab, onTab, tabScale(1), Modifier.weight(1f))
            TabItem("记录", R.drawable.ic_history, 2, currentTab, onTab, tabScale(2), Modifier.weight(1f))
            TabItem("设置", R.drawable.ic_settings, 3, currentTab, onTab, tabScale(3), Modifier.weight(1f))
        }
    }
}

/** 选中 tab 的液态玻璃滑块:拖拽时顺手指方向拉长形变,松手弹簧吸附回弹 */
@Composable
private fun BoxScope.GlassTabPill(backdrop: Backdrop, position: Float, stretch: Float, rowWidthPx: Int) {
    val density = LocalDensity.current
    val dark = isDarkTheme()
    val slot = rowWidthPx / 4f
    val pillW = slot - with(density) { 16.dp.toPx() } + kotlin.math.abs(stretch)
    val pillH = with(density) { 48.dp.toPx() }
    val rowH = with(density) { 52.dp.toPx() }
    Box(
        Modifier
            .offset {
                IntOffset(
                    (position * slot + (slot - pillW) / 2f + stretch / 2f).roundToInt(),
                    ((rowH - pillH) / 2f).roundToInt(),
                )
            }
            .size(with(density) { pillW.toDp() }, 48.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(18.dp) },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    if (Build.VERSION.SDK_INT >= 33) lens(6f.dp.toPx(), 10f.dp.toPx())
                },
                onDrawSurface = {
                    if (dark) drawRect(Color(0xFF332A28).copy(alpha = 0.72f))
                    else drawRect(Color(0xFFF9EBE9).copy(alpha = 0.72f))
                },
            ),
    )
}

/** Android 11 及以下的实色滑块 */
@Composable
private fun BoxScope.SolidTabPill(position: Float, stretch: Float, rowWidthPx: Int) {
    val density = LocalDensity.current
    val slot = rowWidthPx / 4f
    val pillW = slot - with(density) { 16.dp.toPx() } + kotlin.math.abs(stretch)
    val pillH = with(density) { 48.dp.toPx() }
    val rowH = with(density) { 52.dp.toPx() }
    Box(
        Modifier
            .offset {
                IntOffset(
                    (position * slot + (slot - pillW) / 2f + stretch / 2f).roundToInt(),
                    ((rowH - pillH) / 2f).roundToInt(),
                )
            }
            .size(with(density) { pillW.toDp() }, 48.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(18.dp)),
    )
}

@Composable
private fun TabItem(
    label: String,
    iconRes: Int,
    index: Int,
    currentTab: Int,
    onTab: (Int) -> Unit,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val selected = currentTab == index
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onTab(index) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else idleColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else idleColor,
        )
    }
}

/* ---------------- 通用小组件 ---------------- */

@Composable
fun Cover(url: String?, size: Dp, radius: Int = 6) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(radius.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.pagesCount > 1) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("P${track.pagesCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}
