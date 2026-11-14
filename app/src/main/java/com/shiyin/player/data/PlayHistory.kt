package com.shiyin.player.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** 一条听歌记录:歌名 + 播放时间 + 播放地点(省/市,来自 IP 归属地)+ 视频标识(一键重播用) */
@Serializable
data class HistoryEntry(
    val playedAt: Long, // 播放开始时间戳(毫秒)
    val title: String,
    val author: String = "",
    val bvid: String? = null,
    val aid: Long? = null,
    val p: Int = 1,
    val cid: Long? = null,
    val cover: String? = null,
    val province: String? = null,
    val city: String? = null,
) {
    /** 地点显示:省·市·区(去重),查不到时"未知地点" */
    val placeText: String
        get() = listOfNotNull(province, city)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("·")
            .ifBlank { "未知地点" }
}

/**
 * 听歌记录存储,按时间正序追加,无上限、不自动删除(用户要求永久保留)。
 * 只存在手机本地,不上传。
 */
object PlayHistory {
    private val json = Json { ignoreUnknownKeys = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var file: File

    val entries = mutableStateOf<List<HistoryEntry>>(emptyList())

    fun load(context: Context) {
        file = File(context.filesDir, "history.json")
        try {
            if (file.exists()) {
                entries.value = json.decodeFromString<List<HistoryEntry>>(file.readText())
            }
        } catch (_: Exception) { }
    }

    fun log(entry: HistoryEntry) {
        entries.value = entries.value + entry
        save()
    }

    /** 异步定位完成后,按时间戳回填某条记录的地点(不影响其他条目) */
    fun updateLocation(playedAt: Long, province: String?, city: String?) {
        entries.value = entries.value.map {
            if (it.playedAt == playedAt) {
                it.copy(province = province, city = city)
            } else it
        }
        save()
    }

    fun clear() {
        entries.value = emptyList()
        save()
    }

    private fun save() {
        val v = entries.value
        io.launch { runCatching { file.writeText(json.encodeToString(v)) } }
    }
}
