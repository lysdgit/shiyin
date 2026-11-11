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

/** 一条听歌记录:歌名 + 播放时间 + 播放地点(省/市/区县,来自 IP 归属地) */
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
    val district: String? = null,
) {
    /** 地点显示:省·市·区(去重),查不到时"未知地点" */
    val placeText: String
        get() = listOfNotNull(province, city, district)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("·")
            .ifBlank { "未知地点" }
}

/**
 * 听歌记录存储,按时间正序追加,上限 5000 条(超出裁掉最旧)。
 * 只存在手机本地,不上传。
 */
object PlayHistory {
    private val json = Json { ignoreUnknownKeys = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var file: File
    private const val MAX = 5000

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
        if (entries.value.size > MAX) {
            entries.value = entries.value.takeLast(MAX)
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
