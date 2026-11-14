package com.shiyin.player.util

import com.shiyin.player.bili.BiliApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class LocInfo(val province: String?, val city: String?)

/**
 * IP 归属地查询(免权限、免 GPS):
 * 1. 首选 B站 zone 接口(HTTPS 国内直连最稳,省/市,格式与历史记录一致)
 * 2. 次选 myip.ipip.net(HTTPS,中文文本解析 省/市)
 * 结果缓存 10 分钟,移动切网后自动刷新。
 */
object IPLocation {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private const val CACHE_MS = 10 * 60 * 1000L

    @Volatile
    private var cache: Pair<Long, LocInfo?>? = null

    suspend fun fetch(): LocInfo? = withContext(Dispatchers.IO) {
        val hit = cache
        if (hit != null && System.currentTimeMillis() < hit.first) {
            return@withContext hit.second
        }
        val loc = fetchBiliZone() ?: fetchIipNet()
        cache = Pair(System.currentTimeMillis() + CACHE_MS, loc)
        loc
    }

    private fun get(url: String): JsonElement? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", BiliApi.UA).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            resp.body?.string()?.let { json.parseToJsonElement(it) }
        }
    }.getOrNull()

    private fun getText(url: String): String? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", BiliApi.UA).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            resp.body?.string()
        }
    }.getOrNull()

    /** B站 zone(首选):{"code":0,"data":{"province":"广东","city":"东莞",...}} */
    private fun fetchBiliZone(): LocInfo? {
        val el = get("https://api.bilibili.com/x/web-interface/zone") ?: return null
        val d = el as? JsonObject ?: return null
        if (str(d, "code") != "0") return null
        val data = d.get("data") as? JsonObject ?: return null
        return LocInfo(
            province = str(data, "province"),
            city = str(data, "city"),

        )
    }

    /** myip.ipip.net(次选,中文文本):"当前 IP:x.x.x.x 来自于:中国 广东 深圳 电信" */
    private fun fetchIipNet(): LocInfo? {
        val text = getText("https://myip.ipip.net") ?: return null
        val m = Regex("来自于:\\s*\\S+\\s+(\\S+)\\s+(\\S+)").find(text) ?: return null
        return LocInfo(province = m.groupValues[1], city = m.groupValues[2])
    }

    private fun str(obj: JsonObject, key: String): String? =
        (obj.get(key) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
}
