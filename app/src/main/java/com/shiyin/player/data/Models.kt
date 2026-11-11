package com.shiyin.player.data

import kotlinx.serialization.Serializable

/** 分P引用(歌单内曲目的精确 cid) */
@Serializable
data class PageRef(val p: Int, val part: String, val duration: Int = 0, val cid: Long)

/**
 * 统一条目模型 —— 搜索结果、歌单曲目、队列条目共用。
 * 对应网页版 slimTrack 语义(bilimusic_pl_v2 / bilimusic_galbums_v2)。
 */
@Serializable
data class Track(
    val bvid: String? = null,
    val aid: Long? = null,
    val p: Int = 1,
    val title: String = "",
    val author: String = "",
    val cover: String? = null,
    val duration: Int = 0,
    val pagesCount: Int = 1,
    val cid: Long? = null,
    val pages: List<PageRef>? = null,
    val play: Long = 0,
    val pubdate: Long = 0,
) {
    /** 队列/歌单去重键,同网页版 bvid+":p"+p */
    val key: String
        get() = (bvid ?: "av${aid ?: 0}") + ":p" + p

    /** 带完整分P表的多P条目(整视频) */
    val isMultiP: Boolean
        get() = pagesCount > 1 && pages != null

    fun hasRef(): Boolean = bvid != null || aid != null
}

/** 歌单 */
@Serializable
data class Galbum(
    val id: String,
    var title: String,
    var author: String = "我的歌单",
    var cover: String? = null,
    var visibility: String = "private",
    val tracks: MutableList<Track> = mutableListOf(),
)

/** 设置(对应网页版 settings_v1,buvid 一并持久化减少请求) */
@Serializable
data class Settings(
    var quality: String = "high", // low / standard / high
    var speed: Float = 1f,
    var buvid3: String? = null,
    var buvid4: String? = null,
)

/** 播放队列状态 */
@Serializable
data class QueueState(
    val tracks: List<Track> = emptyList(),
    val index: Int = -1,
    val mode: String = "loop", // loop / one
)
