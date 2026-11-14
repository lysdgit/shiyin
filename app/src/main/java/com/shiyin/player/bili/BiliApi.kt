package com.shiyin.player.bili

import com.shiyin.player.data.Galbum
import com.shiyin.player.data.AppStore
import com.shiyin.player.data.PageRef
import com.shiyin.player.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/* ---------------- 返回结构 ---------------- */

data class SearchResult(val items: List<Track>, val numResults: Int, val isEnd: Boolean)
data class UserSearchResult(val items: List<BiliUser>, val numResults: Int, val isEnd: Boolean)
data class BiliUser(
    val mid: Long,
    val name: String,
    val sign: String,
    val avatar: String?,
    val fans: Long,
    val videos: Int,
)
data class VideoInfo(
    val bvid: String,
    val aid: Long,
    val title: String,
    val author: String,
    val cover: String?,
    val duration: Int,
    val cid: Long,
    val pages: List<PageRef>,
    val pubdate: Long = 0, // 视频发布时间(Unix 秒)
)
data class AudioSrc(val url: String, val kind: String, val bandwidth: Long)
data class UserVideosResult(val items: List<Track>, val count: Int, val isEnd: Boolean)
data class FavList(val title: String, val author: String, val cover: String?, val items: List<Track>)
data class ParseResult(val items: List<Track>, val collections: List<Galbum>, val errors: List<String>)

private data class VideoRef(val bvid: String? = null, val aid: Long? = null, val explicitP: Int? = null)

/**
 * B站接口层 —— bilimusic server.js 的 Kotlin 移植:
 * 搜索(buvid cookie + 412 重试)/ 视频信息 / 音源解析(playurl DASH)/
 * UP主作品(老接口优先 + wbi 签名兜底)/ 收藏夹 / b23.tv 短链 / 粘贴文本解析。
 */
object BiliApi {

    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Safari/537.36 Edg/89.0.774.63"
    const val BILI_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1 Edg/114.0.0.0"

    const val PAGE_SIZE = 20

    /** 共享 HTTP 客户端(音频 DataSource 也用它,需跟随重定向) */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /* ---------------- 内存缓存 ---------------- */

    private val cache = ConcurrentHashMap<String, Pair<Long, Any?>>()

    private fun cacheGet(key: String): Any? {
        val hit = cache[key] ?: return null
        if (System.currentTimeMillis() > hit.first) {
            cache.remove(key)
            return null
        }
        return hit.second
    }

    private fun cacheSet(key: String, value: Any?, ttlMs: Long) {
        cache[key] = Pair(System.currentTimeMillis() + ttlMs, value)
    }

    fun cacheDelPrefix(prefix: String) {
        for (k in cache.keys) if (k.startsWith(prefix)) cache.remove(k)
    }

    /* ---------------- JSON 取值工具 ---------------- */

    private fun JsonElement.o(key: String): JsonElement? = (this as? JsonObject)?.get(key)
    private fun JsonElement.a(key: String): JsonArray? = (this as? JsonObject)?.get(key) as? JsonArray
    private fun JsonElement.s(key: String): String? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.content
    private fun JsonElement.i(key: String): Int? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
    private fun JsonElement.l(key: String): Long? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()
    private fun JsonElement.items(): List<JsonElement> = (this as? JsonArray)?.toList() ?: emptyList()

    /** duration 字段:可能是数字也可能是 "3:25"(在本对象上取 duration 键) */
    private fun JsonElement.dur(): Int {
        val c = s("duration") ?: return 0
        return durationToSec(c)
    }

    /* ---------------- 通用工具 ---------------- */

    fun decodeEntities(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        return s
            .replace(Regex("(<em[^>]*>)|(</em>)"), "")
            .replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
                runCatching { String(Character.toChars(m.groupValues[1].toInt(16))) }.getOrDefault(m.value)
            }
            .replace(Regex("&#(\\d+);")) { m ->
                runCatching { String(Character.toChars(m.groupValues[1].toInt())) }.getOrDefault(m.value)
            }
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }

    fun durationToSec(d: String?): Int {
        if (d.isNullOrBlank()) return 0
        return if (d.contains(':')) {
            d.split(":").fold(0) { acc, cur -> acc * 60 + (cur.trim().toIntOrNull() ?: 0) }
        } else {
            d.toDoubleOrNull()?.toInt() ?: 0
        }
    }

    fun httpsUrl(u: String?): String? {
        if (u.isNullOrEmpty()) return null
        return if (u.startsWith("//")) "https:$u" else u.replace(Regex("^http://"), "https://")
    }

    fun formatDuration(sec: Int): String {
        if (sec <= 0) return "--:--"
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    fun formatCount(n: Long): String = when {
        n >= 100_000_000 -> String.format("%.1f亿", n / 1e8)
        n >= 10_000 -> String.format("%.1f万", n / 1e4)
        else -> n.toString()
    }

    private suspend fun getJson(url: String, headers: Map<String, String>): JsonElement =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: throw IOException("空响应")
                json.parseToJsonElement(body)
            }
        }

    private suspend fun getBili(url: String, headers: Map<String, String>): JsonElement {
        val res = getJson(url, headers)
        val code = res.i("code") ?: -1
        if (code != 0) throw BiliException(code, res.s("message") ?: "")
        return res
    }

    class BiliException(val code: Int, message: String) : IOException(message.ifBlank { "code=$code" })

    private fun encodeParam(v: String): String =
        URLEncoder.encode(v, "UTF-8").replace("+", "%20")

    /* ---------------- B站 Cookie(搜索需要 buvid) ---------------- */

    private val cookieMutex = Mutex()

    suspend fun getCookie(forceRefresh: Boolean = false) = cookieMutex.withLock {
        val s = AppStore.settings.value
        if (!forceRefresh && !s.buvid3.isNullOrBlank()) return@withLock
        val res = getJson("https://api.bilibili.com/x/frontend/finger/spi", mapOf("User-Agent" to BILI_UA))
        val b3 = res.o("data")?.s("b_3")
            ?: throw IOException("获取 buvid cookie 失败")
        val b4 = res.o("data")?.s("b_4") ?: ""
        AppStore.settings.value = s.copy(buvid3 = b3, buvid4 = b4)
        AppStore.saveSettings()
    }

    private fun cookieHeader(): String {
        val s = AppStore.settings.value
        return "buvid3=${s.buvid3 ?: ""};buvid4=${s.buvid4 ?: ""}"
    }

    /* ---------------- 搜索 ---------------- */

    private val SEARCH_HEADERS = mapOf(
        "user-agent" to UA,
        "accept" to "application/json, text/plain, */*",
        "origin" to "https://search.bilibili.com",
        "sec-fetch-site" to "same-site",
        "sec-fetch-mode" to "cors",
        "sec-fetch-dest" to "empty",
        "referer" to "https://search.bilibili.com/",
        "accept-language" to "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6",
    )

    private suspend fun searchOnce(keyword: String, page: Int, searchType: String): JsonElement {
        getCookie()
        val params = mapOf(
            "context" to "",
            "page" to page.toString(),
            "order" to "",
            "page_size" to PAGE_SIZE.toString(),
            "keyword" to keyword,
            "duration" to "",
            "tids_1" to "",
            "tids_2" to "",
            "__refresh__" to "true",
            "_extra" to "",
            "highlight" to "1",
            "single_column" to "0",
            "platform" to "pc",
            "from_source" to "",
            "search_type" to searchType,
            "dynamic_offset" to "0",
        )
        val qs = params.entries.joinToString("&") { "${it.key}=${encodeParam(it.value)}" }
        return getJson(
            "https://api.bilibili.com/x/web-interface/search/type?$qs",
            SEARCH_HEADERS + ("cookie" to cookieHeader()),
        )
    }

    private suspend fun searchWithRetry(keyword: String, page: Int, type: String): JsonElement {
        var res = searchOnce(keyword, page, type)
        val code = res.i("code") ?: -1
        if (code == 412 || code == -412) {
            getCookie(true)
            res = searchOnce(keyword, page, type)
        }
        val retryCode = res.i("code") ?: -1
        if (retryCode != 0) throw BiliException(retryCode, res.s("message") ?: "")
        return res
    }

    private fun formatSearchItem(v: JsonElement): Track = Track(
        bvid = v.s("bvid"),
        aid = v.l("aid"),
        cid = v.l("cid"),
        title = decodeEntities(v.s("title")),
        author = decodeEntities(v.s("author") ?: ""),
        cover = httpsUrl(v.s("pic")),
        duration = v.dur(),
        play = v.l("play") ?: 0,
        pubdate = v.l("pubdate") ?: 0,
    )

    suspend fun searchVideos(keyword: String, page: Int): SearchResult {
        val res = searchWithRetry(keyword, page, "video")
        val data = res.o("data")
        val raw = data?.a("result")?.items().orEmpty().filter { it.s("type") == "video" }
        val items = raw.map { formatSearchItem(it) }
        val numResults = data?.i("numResults") ?: 0
        return SearchResult(items, numResults, numResults <= page * PAGE_SIZE)
    }

    suspend fun searchUsers(keyword: String, page: Int): UserSearchResult {
        val res = searchWithRetry(keyword, page, "bili_user")
        val data = res.o("data")
        val items = data?.a("result")?.items().orEmpty()
            .filter { it.s("type") == "bili_user" }
            .map { u ->
                BiliUser(
                    mid = u.l("mid") ?: 0,
                    name = decodeEntities(u.s("uname") ?: ""),
                    sign = decodeEntities(u.s("usign") ?: ""),
                    avatar = httpsUrl(u.s("upic")),
                    fans = u.l("fans") ?: 0,
                    videos = u.i("videos") ?: 0,
                )
            }
        val numResults = data?.i("numResults") ?: 0
        return UserSearchResult(items, numResults, numResults <= page * PAGE_SIZE)
    }

    /* ---------------- wbi 签名(UP主作品列表兜底) ---------------- */

    private var wbiKeys: Pair<String, String>? = null
    private var wbiKeyDate = ""

    private suspend fun getWbiKeys(): Pair<String, String> {
        if (wbiKeys != null && wbiKeyDate == LocalDate.now().toString()) return wbiKeys!!
        val res = getBili("https://api.bilibili.com/x/web-interface/nav", mapOf("user-agent" to UA))
        val w = res.o("data")?.o("wbi_img") ?: throw IOException("获取 wbi 密钥失败")
        val img = w.s("img_url")?.split("/")?.lastOrNull()?.split(".")?.firstOrNull() ?: ""
        val sub = w.s("sub_url")?.split("/")?.lastOrNull()?.split(".")?.firstOrNull() ?: ""
        wbiKeys = Pair(img, sub)
        wbiKeyDate = LocalDate.now().toString()
        return wbiKeys!!
    }

    // bilibili wbi mixin 表(2023-11 标准版)
    private val MIXIN_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 49, 19, 1, 3, 24, 52, 13, 42,
        27, 41, 25, 28, 12, 55, 34, 21, 44, 7, 9, 40, 6, 26, 29, 33, 30, 45, 51, 22,
        38, 39, 35, 54, 11, 36, 48, 4, 14, 5, 37, 43, 16, 20,
    )

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun wbiSign(params: Map<String, String>, keys: Pair<String, String>): String {
        val mixinKey = MIXIN_TAB.map { (keys.first + keys.second)[it] }.joinToString("").take(32)
        val all = params + ("wts" to (System.currentTimeMillis() / 1000).toString())
        val qs = all.entries.sortedBy { it.key }
            .joinToString("&") {
                "${it.key}=" + encodeParam(it.value.replace(Regex("[!'()*]"), ""))
            }
        return qs + "&w_rid=" + md5(qs + mixinKey)
    }

    private fun formatArcSearch(d: JsonElement, page: Int): UserVideosResult {
        val vlist = d.o("list")?.a("vlist")?.items().orEmpty()
        val items = vlist.map { v ->
            Track(
                bvid = v.s("bvid"),
                aid = v.l("aid"),
                title = decodeEntities(v.s("title")),
                author = decodeEntities(v.s("author") ?: ""),
                cover = httpsUrl(v.s("pic")),
                duration = v.dur(),
                play = v.l("play") ?: 0,
                pubdate = v.l("pubdate") ?: 0,
            )
        }
        val count = d.o("page")?.i("count") ?: items.size
        return UserVideosResult(items, count, page * 30 >= count)
    }

    /** UP主视频列表: 老接口(移动端头)优先,wbi 签名接口兜底 */
    suspend fun getUserVideos(mid: Long, page: Int): UserVideosResult {
        try {
            getCookie()
            val res = getJson(
                "https://api.bilibili.com/x/space/arc/search?mid=$mid&ps=30&pn=$page&order=pubdate",
                mapOf(
                    "user-agent" to BILI_UA,
                    "accept" to "application/json, text/plain, */*",
                    "referer" to "https://m.bilibili.com/space/$mid",
                    "x-requested-with" to "XMLHttpRequest",
                    "cookie" to cookieHeader(),
                ),
            )
            if ((res.i("code") ?: -1) == 0 && res.o("data") != null) {
                return formatArcSearch(res.o("data")!!, page)
            }
        } catch (_: Exception) {
            // fallthrough to wbi
        }
        val keys = getWbiKeys()
        val params = mapOf(
            "mid" to mid.toString(),
            "ps" to "30",
            "tid" to "0",
            "pn" to page.toString(),
            "web_location" to "1550101",
            "order_avoided" to "true",
            "order" to "pubdate",
            "keyword" to "",
            "platform" to "web",
            "dm_img_list" to "[]",
            "dm_img_str" to "V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9tZXVtKQ",
            "dm_cover_img_str" to "QU5HTEUgKE5WSURJQSwgTlZJRElBIEdlRm9yY2UgR1RYIDE2NTAgKDB4MDAwMDFGOTEpIERpcmVjdDNEMTEgdnNfNV8wIHBzXzVfMCwgRDNEMTEpR29vZ2xlIEluYy4gKE5WSURJQQ",
            "dm_img_inter" to "{\"ds\":[],\"wh\":[0,0,0],\"of\":[0,0,0]}",
        )
        val qs = wbiSign(params, keys)
        val res = getBili(
            "https://api.bilibili.com/x/space/wbi/arc/search?$qs",
            mapOf(
                "user-agent" to UA,
                "accept" to "*/*",
                "origin" to "https://space.bilibili.com",
                "sec-fetch-site" to "same-site",
                "sec-fetch-mode" to "cors",
                "sec-fetch-dest" to "empty",
                "referer" to "https://space.bilibili.com/$mid/video",
                "cookie" to cookieHeader(),
            ),
        )
        return formatArcSearch(res.o("data") ?: JsonObject(emptyMap()), page)
    }

    /* ---------------- 视频信息 ---------------- */

    suspend fun videoInfo(bvid: String?, aid: Long?): VideoInfo {
        val cacheKey = if (!bvid.isNullOrBlank()) "view:$bvid" else "view:av${aid ?: 0}"
        (cacheGet(cacheKey) as? VideoInfo)?.let { return it }

        val qs = if (!bvid.isNullOrBlank()) "bvid=${encodeParam(bvid)}" else "aid=${aid ?: 0}"
        val res = getBili(
            "https://api.bilibili.com/x/web-interface/view?$qs",
            mapOf("user-agent" to UA, "referer" to "https://www.bilibili.com/"),
        )
        val d = res.o("data") ?: throw IOException("获取视频信息失败")
        val pages = d.a("pages")?.items().orEmpty().mapIndexed { idx, pg ->
            PageRef(
                p = pg.i("p") ?: (idx + 1),
                part = decodeEntities(pg.s("part") ?: ""),
                duration = pg.dur(),
                cid = pg.l("cid") ?: 0,
            )
        }
        val info = VideoInfo(
            bvid = d.s("bvid") ?: bvid ?: "",
            aid = d.l("aid") ?: aid ?: 0,
            title = decodeEntities(d.s("title")),
            author = decodeEntities(d.o("owner")?.s("name") ?: ""),
            cover = httpsUrl(d.s("pic")),
            duration = d.dur(),
            cid = d.l("cid") ?: pages.firstOrNull()?.cid ?: 0,
            pages = pages,
            pubdate = d.l("pubdate") ?: 0,
        )
        cacheSet(cacheKey, info, 10 * 60 * 1000)
        return info
    }

    /* ---------------- 音源解析 ---------------- */

    /**
     * playurl 解析 DASH 音频流。注意:不能带 buvid cookie,否则 412 风控。
     * CDN 直链有时效,缓存 20 分钟;force = true 跳过缓存(403 重试用)。
     */
    private val audioMutex = Mutex()

    suspend fun resolveAudio(bvid: String, cid: Long, quality: String, force: Boolean = false): AudioSrc =
        audioMutex.withLock {
            val key = "audio:$bvid|$cid|$quality"
            if (!force) (cacheGet(key) as? AudioSrc)?.let { return@withLock it }
            val res = getBili(
                "https://api.bilibili.com/x/player/playurl?bvid=${encodeParam(bvid)}&cid=$cid&fnval=16",
                mapOf("user-agent" to UA, "referer" to "https://www.bilibili.com/"),
            )
            val d = res.o("data") ?: throw IOException("解析音源失败")
            var url: String? = null
            var kind = "durl"
            var bandwidth = 0L
            val dashAudio = d.o("dash")?.a("audio")?.items().orEmpty()
            if (dashAudio.isNotEmpty()) {
                kind = "dash"
                val sorted = dashAudio.sortedBy { it.l("bandwidth") ?: 0 }
                val pick = when (quality) {
                    "low" -> sorted.first()
                    "standard" -> sorted[minOf(1, sorted.size - 1)]
                    else -> sorted.last()
                }
                url = pick.s("baseUrl") ?: pick.a("backupUrl")?.items()?.firstOrNull()?.s("url")
                    ?: pick.a("backup_url")?.items()?.firstOrNull()?.s("url")
                bandwidth = pick.l("bandwidth") ?: 0
            } else {
                url = d.a("durl")?.items()?.firstOrNull()?.s("url")
            }
            if (url.isNullOrEmpty()) throw IOException("该视频没有可用的音频流")
            val out = AudioSrc(httpsUrl(url)!!, kind, bandwidth)
            cacheSet(key, out, 20 * 60 * 1000)
            out
        }

    /* ---------------- b23.tv 短链 ---------------- */

    suspend fun resolveRedirect(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        http.newCall(req).execute().use { resp -> resp.request.url.toString() }
    }

    /* ---------------- 收藏夹 ---------------- */

    suspend fun fetchFavList(mediaId: String): FavList {
        val items = mutableListOf<Track>()
        var title = "收藏夹 $mediaId"
        var author = ""
        var cover: String? = null
        val ps = 20
        for (pn in 1..25) {
            val res = getJson(
                "https://api.bilibili.com/x/v3/fav/resource/list?media_id=$mediaId&platform=web&ps=$ps&pn=$pn",
                mapOf("user-agent" to UA, "referer" to "https://www.bilibili.com/"),
            )
            val code = res.i("code") ?: -1
            if (code != 0) {
                if (pn == 1) throw BiliException(code, res.s("message") ?: "")
                break
            }
            val d = res.o("data")
            if (pn == 1) {
                title = decodeEntities(d?.o("info")?.s("title")).ifBlank { title }
                author = decodeEntities(d?.o("info")?.o("upper")?.s("name") ?: "")
            }
            val medias = d?.a("medias")?.items().orEmpty()
            for (m in medias) {
                if ((m.i("type") ?: 0) == 2 && (m.s("bvid") != null || m.l("id") != null)) {
                    items.add(
                        Track(
                            bvid = m.s("bvid"),
                            aid = m.l("id"),
                            p = 1,
                            title = decodeEntities(m.s("title")),
                            author = decodeEntities(m.o("upper")?.s("name") ?: ""),
                            cover = httpsUrl(m.s("cover")),
                            duration = m.dur(),
                            pubdate = m.l("pubtime") ?: 0,
                            pagesCount = 1,
                        )
                    )
                }
            }
            if (cover == null && items.isNotEmpty()) cover = items[0].cover
            val hasMore = ((d as? JsonObject)?.get("has_more") as? JsonPrimitive)?.content
            if (hasMore == "false" || hasMore == "0") break
            if (medias.isEmpty()) break
        }
        return FavList(title, author, cover, items)
    }

    /* ---------------- 粘贴文本解析 ---------------- */

    private fun extractVideoRef(text: String): VideoRef? {
        val pMatch = Regex("[?&]p=(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val m = Regex("(?:/video/)?\\b(BV[0-9A-Za-z]{8,12})\\b", RegexOption.IGNORE_CASE).find(text)
        if (m != null) {
            val raw = m.groupValues[1]
            return VideoRef(
                bvid = if (raw.startsWith("BV")) raw else "BV" + raw.drop(2),
                explicitP = pMatch,
            )
        }
        val m2 = Regex("\\bav(\\d{1,15})\\b", RegexOption.IGNORE_CASE).find(text)
        if (m2 != null) return VideoRef(aid = m2.groupValues[1].toLong(), explicitP = pMatch)
        return null
    }

    private fun extractFavId(text: String): String? {
        Regex("[?&]fid=(\\d+)").find(text)?.let { return it.groupValues[1] }
        Regex("/playlist/pl(\\d+)", RegexOption.IGNORE_CASE).find(text)?.let { return it.groupValues[1] }
        Regex("/list/ml(\\d+)", RegexOption.IGNORE_CASE).find(text)?.let { return it.groupValues[1] }
        val t = text.trim()
        if (Regex("^\\d{6,}$").matches(t)) return t
        return null
    }

    suspend fun parseText(text: String): ParseResult {
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val errors = mutableListOf<String>()
        val favIds = mutableListOf<String>()
        val videoRefs = mutableListOf<VideoRef>()
        val seen = mutableSetOf<String>()

        fun pushRef(ref: VideoRef) {
            val key = (ref.bvid ?: "av${ref.aid ?: 0}") + ":p" + (ref.explicitP ?: 1)
            if (!seen.contains(key)) {
                seen.add(key)
                videoRefs.add(ref)
            }
        }

        for (token in tokens) {
            try {
                var working = token
                if (Regex("^https?://(www\\.)?b23\\.tv/", RegexOption.IGNORE_CASE).containsMatchIn(token)) {
                    working = resolveRedirect(token)
                }
                val favId = extractFavId(working)
                if (favId != null) {
                    if (!favIds.contains(favId)) favIds.add(favId)
                    continue
                }
                val ref = extractVideoRef(working)
                if (ref != null) {
                    pushRef(ref)
                    continue
                }
                errors.add("无法识别: ${token.take(60)}")
            } catch (e: Exception) {
                errors.add("${token.take(60)}: ${e.message}")
            }
        }

        // 收藏夹 → 组装为歌单(collections),不混入单曲队列
        val collections = mutableListOf<Galbum>()
        for (favId in favIds) {
            try {
                val fav = fetchFavList(favId)
                if (fav.items.isEmpty()) {
                    errors.add("收藏夹 $favId: 没有可播放的视频")
                    continue
                }
                collections.add(
                    Galbum(
                        id = "fav$favId",
                        title = fav.title,
                        author = fav.author.ifBlank { "收藏夹" },
                        cover = fav.cover,
                        tracks = fav.items.toMutableList(),
                    )
                )
            } catch (e: Exception) {
                errors.add("收藏夹 $favId: ${e.message}")
            }
        }

        // 视频条目补全元信息(并发 3),富化后按 bvid+p 去重
        val enriched = coroutineScope {
            val sem = Semaphore(3)
            videoRefs.map { ref ->
                async {
                    try {
                        sem.withPermit { Triple(ref, videoInfo(ref.bvid, ref.aid), null as Exception?) }
                    } catch (e: Exception) {
                        Triple(ref, null, e)
                    }
                }
            }.awaitAll()
        }

        val items = mutableListOf<Track>()
        val seenEnriched = mutableSetOf<String>()

        fun pushItem(item: Track) {
            if (!seenEnriched.contains(item.key)) {
                seenEnriched.add(item.key)
                items.add(item)
            }
        }

        for ((ref, infoOrNull, err) in enriched) {
            if (infoOrNull == null) {
                errors.add(err?.message ?: "获取视频信息失败")
                continue
            }
            val info = infoOrNull
            val pages = info.pages
            if (ref.explicitP == null && pages.size > 1) {
                // 多P视频: 不拍平,作为一个歌单返回,由前端展示供用户选择
                val tracks = pages.take(2000).map { pg ->
                    Track(
                        bvid = info.bvid,
                        aid = info.aid,
                        p = pg.p,
                        title = "P${pg.p} ${pg.part}",
                        author = info.author,
                        cover = info.cover,
                        duration = pg.duration,
                        pubdate = info.pubdate,
                        pagesCount = 1,
                        cid = pg.cid,
                    )
                }
                collections.add(
                    Galbum(
                        id = info.bvid,
                        title = info.title,
                        author = info.author,
                        cover = info.cover,
                        tracks = tracks.toMutableList(),
                    )
                )
            } else {
                val p = (ref.explicitP ?: 1).coerceIn(1, if (pages.isEmpty()) 1 else pages.size)
                val pg = pages.getOrNull(p - 1)
                pushItem(
                    Track(
                        bvid = info.bvid,
                        aid = info.aid,
                        p = p,
                        title = if (pages.size > 1 && pg != null) "${info.title} P$p ${pg.part}" else info.title,
                        author = info.author,
                        cover = info.cover,
                        duration = pg?.duration ?: info.duration,
                        pubdate = info.pubdate,
                        pagesCount = if (pages.size > 1) pages.size else 1,
                        cid = pg?.cid ?: info.cid,
                        pages = if (pages.size > 1) pages else null,
                    )
                )
            }
        }

        return ParseResult(items, collections, errors)
    }

    /**
     * 补全曲目元信息(网页版 ensureMeta):收藏夹曲目无 cid,播放前补全;
     * 多P条目顺便带上分P表。失败时原样返回。
     */
    suspend fun ensureMeta(track: Track): Track {
        if (track.cid != null && track.pages != null) return track
        if (!track.hasRef()) return track
        return try {
            val info = videoInfo(track.bvid, track.aid)
            val pages = info.pages
            val p = track.p.coerceIn(1, if (pages.isEmpty()) 1 else pages.size)
            val pg = pages.getOrNull(p - 1)
            track.copy(
                bvid = track.bvid ?: info.bvid,
                aid = track.aid ?: info.aid,
                author = track.author.ifBlank { info.author },
                cover = track.cover ?: info.cover,
                duration = track.duration.takeIf { it > 0 } ?: (pg?.duration ?: info.duration),
                pagesCount = if (pages.size > 1) pages.size else 1,
                cid = track.cid ?: pg?.cid ?: info.cid,
                pages = track.pages ?: pages.takeIf { it.size > 1 },
            )
        } catch (_: Exception) {
            track
        }
    }
}
