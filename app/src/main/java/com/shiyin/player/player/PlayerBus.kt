package com.shiyin.player.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.shiyin.player.bili.BiliApi
import com.shiyin.player.data.AppStore
import com.shiyin.player.data.HistoryEntry
import com.shiyin.player.data.PageRef
import com.shiyin.player.data.PlayHistory
import com.shiyin.player.data.Track
import com.shiyin.player.util.IPLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * UI ↔ 播放服务桥。队列以 AppStore.queue 为唯一真源,
 * 每次变更同步镜像到 MediaController 时间线,UI 直接读 AppStore 渲染。
 */
object PlayerBus {

    var controller: MediaController? = null
        private set
    val connected = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    private var connecting = false

    /** UI 弹层总线(队列抽屉 / 粘贴弹窗 / 加入歌单弹窗) */
    val showQueue = mutableStateOf(false)
    val showPaste = mutableStateOf(false)
    val addToTrack = mutableStateOf<Track?>(null)

    /** 睡眠定时(到期时间戳,null = 未开启) */
    val sleepUntil = mutableStateOf<Long?>(null)
    private var sleepJob: Job? = null

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun connect(context: Context) {
        if (connecting || connected.value) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                controller = future.get()
                connected.value = true
                attachListener()
                restoreIfNeeded()
                controller?.setPlaybackSpeed(AppStore.settings.value.speed)
                applyRepeatMode()
            } catch (_: Exception) {
                connecting = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        controller?.release()
        controller = null
        connected.value = false
        connecting = false
    }

    private fun attachListener() {
        controller?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = controller?.currentMediaItemIndex ?: -1
                AppStore.queue.value = AppStore.queue.value.copy(index = idx)
                AppStore.saveQueue()
                // 听歌记录:切歌/自动下一首/单曲循环重复才记;恢复队列(PLAYLIST_CHANGED)不记
                val shouldLog = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> true
                    else -> false
                }
                if (shouldLog && mediaItem != null) logPlayHistory(mediaItem)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                this@PlayerBus.isPlaying.value = isPlaying
            }
        })
    }

    /** 写一条听歌记录(歌名 + 播放时间 + IP 归属地点) */
    private fun logPlayHistory(item: MediaItem) {
        scope.launch {
            val track = AppStore.queue.value.tracks.firstOrNull { it.key == item.mediaId }
            val loc = IPLocation.fetch()
            PlayHistory.log(
                HistoryEntry(
                    playedAt = System.currentTimeMillis(),
                    title = track?.title ?: item.mediaMetadata.title?.toString() ?: "",
                    author = track?.author ?: item.mediaMetadata.artist?.toString() ?: "",
                    bvid = track?.bvid,
                    aid = track?.aid,
                    p = track?.p ?: 1,
                    cid = track?.cid,
                    cover = track?.cover,
                    province = loc?.province,
                    city = loc?.city,
                    district = loc?.district,
                )
            )
        }
    }

    /* ---------- MediaItem 构造 ---------- */

    fun mediaItemFor(track: Track): MediaItem {
        val q = AppStore.settings.value.quality
        val uri = Uri.parse("shiyin://track").buildUpon()
            .appendQueryParameter("bvid", track.bvid ?: "")
            .appendQueryParameter("cid", (track.cid ?: 0L).toString())
            .appendQueryParameter("q", q)
            .build()
        val meta = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.author)
            .setArtworkUri(track.cover?.let { Uri.parse(it) })
            .build()
        return MediaItem.Builder()
            .setMediaId(track.key)
            .setUri(uri)
            .setMediaMetadata(meta)
            .build()
    }

    private fun syncStore(tracks: List<Track>, index: Int = AppStore.queue.value.index) {
        AppStore.queue.value = AppStore.queue.value.copy(tracks = tracks, index = index)
        AppStore.saveQueue()
    }

    private fun applyRepeatMode() {
        val c = controller ?: return
        c.repeatMode = if (AppStore.queue.value.mode == "one") Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
    }

    /** 冷启动恢复队列(不自动播放)。MediaController 用 setMediaItems + seekTo 定位 */
    private fun restoreIfNeeded() {
        val c = controller ?: return
        val q = AppStore.queue.value
        if (c.currentTimeline.windowCount > 0 || q.tracks.isEmpty()) return
        val idx = q.index.coerceIn(0, q.tracks.size - 1)
        c.setMediaItems(q.tracks.map { mediaItemFor(it) })
        c.seekTo(idx, 0)
        c.prepare()
        applyRepeatMode()
    }

    private suspend fun ensureAll(tracks: List<Track>): List<Track> = coroutineScope {
        val sem = Semaphore(3)
        tracks.map { t -> async { sem.withPermit { BiliApi.ensureMeta(t) } } }.awaitAll()
    }

    private fun insertList(list: List<Track>, at: Int, items: List<Track>): List<Track> {
        val l = list.toMutableList()
        l.addAll(at.coerceIn(0, l.size), items)
        return l
    }

    /* ---------- 队列操作 ---------- */

    /** 单曲播放:已在队列 → 跳转;否则插到当前曲目后播放 */
    fun playTrack(track: Track) {
        val c = controller ?: run {
            addToQueueOnly(listOf(track))
            return
        }
        scope.launch {
            val t = BiliApi.ensureMeta(track)
            val q = AppStore.queue.value
            val existing = q.tracks.indexOfFirst { it.key == t.key }
            if (existing >= 0) {
                c.seekTo(existing, 0)
                c.prepare()
                c.play()
                return@launch
            }
            val insertAt = if (q.index >= 0 && q.index < q.tracks.size) q.index + 1 else q.tracks.size
            c.addMediaItems(insertAt, listOf(mediaItemFor(t)))
            syncStore(insertList(q.tracks, insertAt, listOf(t)), insertAt)
            c.seekTo(insertAt, 0)
            c.prepare()
            c.play()
        }
    }

    private fun addToQueueOnly(tracks: List<Track>) {
        val q = AppStore.queue.value
        syncStore(q.tracks + tracks, q.index)
    }

    /** 批量追加到队列尾部(粘贴解析的单曲);队列空闲则自动开播 */
    fun addTracks(tracks: List<Track>) {
        val c = controller ?: run {
            addToQueueOnly(tracks)
            return
        }
        scope.launch {
            val ready = ensureAll(tracks)
            if (ready.isEmpty()) return@launch
            val startAt = AppStore.queue.value.tracks.size
            val wasIdle = startAt == 0 || AppStore.queue.value.index < 0
            c.addMediaItems(ready.map { mediaItemFor(it) })
            syncStore(AppStore.queue.value.tracks + ready, AppStore.queue.value.index)
            if (wasIdle) {
                c.seekTo(startAt, 0)
                c.prepare()
                c.play()
            }
        }
    }

    /** 播放全部:整体替换队列 */
    fun playAll(tracks: List<Track>, startIndex: Int = 0) {
        val c = controller ?: run {
            syncStore(tracks, startIndex)
            return
        }
        scope.launch {
            val ready = ensureAll(tracks)
            if (ready.isEmpty()) return@launch
            val idx = startIndex.coerceIn(0, ready.size - 1)
            c.setMediaItems(ready.map { mediaItemFor(it) })
            c.seekTo(idx, 0)
            syncStore(ready, idx)
            c.prepare()
            c.play()
            applyRepeatMode()
        }
    }

    /** 队列点击跳转 */
    fun playAt(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0)
        c.prepare()
        c.play()
    }

    fun removeAt(index: Int) {
        val c = controller ?: return
        val q = AppStore.queue.value
        if (index < 0 || index >= q.tracks.size) return
        c.removeMediaItem(index)
        val newTracks = q.tracks.toMutableList().also { it.removeAt(index) }
        val newIdx = when {
            index < q.index -> q.index - 1
            index == q.index -> if (newTracks.isEmpty()) -1 else index.coerceAtMost(newTracks.size - 1)
            else -> q.index
        }
        syncStore(newTracks, newIdx)
    }

    fun clearQueue() {
        controller?.let {
            it.stop()
            it.clearMediaItems()
        }
        syncStore(emptyList(), -1)
    }

    /** 队列条目切换分P(整视频条目) */
    fun switchP(index: Int, pg: PageRef) {
        val c = controller ?: return
        val q = AppStore.queue.value
        val old = q.tracks.getOrNull(index) ?: return
        val newTrack = old.copy(
            p = pg.p,
            cid = pg.cid,
            title = "P${pg.p} ${pg.part}",
            duration = pg.duration,
            pagesCount = 1,
            pages = null,
        )
        c.removeMediaItem(index)
        c.addMediaItems(index, listOf(mediaItemFor(newTrack)))
        if (index == q.index) {
            c.seekTo(index, 0)
            c.prepare()
            c.play()
        }
        val newTracks = q.tracks.toMutableList().also { it[index] = newTrack }
        syncStore(newTracks, q.index)
    }

    fun toggleMode() {
        val mode = if (AppStore.queue.value.mode == "one") "loop" else "one"
        AppStore.queue.value = AppStore.queue.value.copy(mode = mode)
        AppStore.saveQueue()
        applyRepeatMode()
    }

    /* ---------- 播放控制 ---------- */

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            c.prepare()
            c.play()
        }
    }

    fun next() {
        val c = controller ?: return
        if (c.hasNextMediaItem()) {
            c.seekToNextMediaItem()
            c.prepare()
            c.play()
        }
    }

    fun prev() {
        val c = controller ?: return
        if (c.hasPreviousMediaItem()) {
            c.seekToPreviousMediaItem()
        } else {
            c.seekTo(0, 0)
        }
        c.prepare()
        c.play()
    }

    fun seekTo(pos: Long) {
        controller?.seekTo(pos)
    }

    /* ---------- 播放设置 ---------- */

    fun setSpeed(x: Float) {
        AppStore.settings.value = AppStore.settings.value.copy(speed = x)
        AppStore.saveSettings()
        controller?.setPlaybackSpeed(x)
    }

    /** 音质切换:重建当前曲目占位 URI(新 q),保留进度 */
    fun setQuality(q: String) {
        val c = controller ?: run {
            AppStore.settings.value = AppStore.settings.value.copy(quality = q)
            AppStore.saveSettings()
            return
        }
        val wasPlaying = c.isPlaying
        val pos = c.currentPosition
        val idx = c.currentMediaItemIndex
        AppStore.settings.value = AppStore.settings.value.copy(quality = q)
        AppStore.saveSettings()
        val tracks = AppStore.queue.value.tracks
        if (tracks.isEmpty()) return
        c.setMediaItems(tracks.map { mediaItemFor(it) })
        c.seekTo(idx.coerceAtLeast(0), pos)
        c.prepare()
        if (wasPlaying) c.play()
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes == null) {
            sleepUntil.value = null
            return
        }
        sleepUntil.value = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            sleepUntil.value = null
        }
    }

    /* ---------- 弹层辅助 ---------- */

    fun openAddTo(track: Track) {
        addToTrack.value = track
    }

    fun closeAddTo() {
        addToTrack.value = null
    }

    fun dispose() {
        scope.cancel()
    }
}
