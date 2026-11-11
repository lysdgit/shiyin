package com.shiyin.player.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 持久化存储 —— 对应网页版 localStorage(bilimusic_pl_v2 / bilimusic_galbums_v2 / settings_v1)。
 * 状态用 Compose mutableStateOf 承载,UI 直接读取即可响应更新。
 */
object AppStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var queueFile: File
    private lateinit var galbumsFile: File
    private lateinit var settingsFile: File

    val settings = mutableStateOf(Settings())
    val galbums = mutableStateOf<List<Galbum>>(emptyList())
    val queue = mutableStateOf(QueueState())

    fun load(context: Context) {
        val d = context.filesDir
        queueFile = File(d, "queue.json")
        galbumsFile = File(d, "galbums.json")
        settingsFile = File(d, "settings.json")
        try {
            if (settingsFile.exists()) {
                settings.value = json.decodeFromString<Settings>(settingsFile.readText())
            }
        } catch (_: Exception) { }
        try {
            if (galbumsFile.exists()) {
                galbums.value = json.decodeFromString<List<Galbum>>(galbumsFile.readText())
            }
        } catch (_: Exception) { }
        try {
            if (queueFile.exists()) {
                queue.value = json.decodeFromString<QueueState>(queueFile.readText())
            }
        } catch (_: Exception) { }
    }

    fun saveSettings() {
        val v = settings.value
        io.launch { runCatching { settingsFile.writeText(json.encodeToString(v)) } }
    }

    fun saveGalbums() {
        val v = galbums.value
        io.launch { runCatching { galbumsFile.writeText(json.encodeToString(v)) } }
    }

    fun saveQueue() {
        val v = queue.value
        io.launch { runCatching { queueFile.writeText(json.encodeToString(v)) } }
    }

    /* ---------------- 歌单操作 ---------------- */

    /** 从曲目列表里随机选一个非空封面(新建歌单自动取封面,网页版 pickGalbumCover 语义) */
    fun pickCover(tracks: List<Track>): String? =
        tracks.filter { !it.cover.isNullOrBlank() }.randomOrNull()?.cover

    /** 多P视频 → 聚合歌单(网页版 expandToGalbum 语义,id = bvid) */
    fun multiPGalbumOf(t: Track): Galbum = Galbum(
        id = t.bvid ?: "av${t.aid ?: 0}",
        title = t.title,
        author = t.author,
        cover = t.cover,
        tracks = (t.pages ?: emptyList()).map { pg ->
            Track(
                bvid = t.bvid,
                aid = t.aid,
                p = pg.p,
                title = "P${pg.p} ${pg.part}",
                author = t.author,
                cover = t.cover,
                duration = pg.duration,
                pagesCount = 1,
                cid = pg.cid,
            )
        }.toMutableList(),
    )

    /** 歌单不存在则追加,存在则原地替换(粘贴解析的歌单更新用) */
    fun upsertGalbum(g: Galbum) {
        val idx = galbums.value.indexOfFirst { it.id == g.id }
        val list = galbums.value.toMutableList()
        if (idx >= 0) list[idx] = g else list.add(0, g)
        galbums.value = list
        saveGalbums()
    }

    fun removeGalbum(id: String) {
        galbums.value = galbums.value.filter { it.id != id }
        saveGalbums()
    }

    fun findGalbum(id: String): Galbum? = galbums.value.firstOrNull { it.id == id }

    /** 向歌单追加曲目,已存在(按 key 去重)返回 false;无封面歌单回填封面 */
    fun addTrackTo(g: Galbum, track: Track): Boolean {
        if (g.tracks.any { it.key == track.key }) return false
        g.tracks.add(track)
        if (g.cover.isNullOrBlank() && !track.cover.isNullOrBlank()) {
            g.cover = track.cover
        }
        galbums.value = galbums.value.toList()
        saveGalbums()
        return true
    }
}
