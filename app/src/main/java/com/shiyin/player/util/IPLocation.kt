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

data class LocInfo(val province: String?, val city: String?, val district: String?)

/**
 * IP 归属地查询(免权限、免 GPS):优先百度千帆接口(省/市/区县),
 * 失败退备用接口(省/市/区)。结果缓存 10 分钟,移动切网后自动刷新。
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
        val loc = fetchBaiduQifu() ?: fetchUserAgentInfo()
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

    /** 百度千帆: {"code":0,"data":{"prov":..,"city":..,"district":..}} */
    private fun fetchBaiduQifu(): LocInfo? {
        val el = get("https://qifu-api.baidubce.com/ip/local/geo/v1/district") ?: return null
        val d = el as? JsonObject ?: return null
        val code = (d.get("code") as? JsonPrimitive)?.content ?: return null
        if (code != "0") return null
        val data = d.get("data") as? JsonObject ?: return null
        return LocInfo(
            province = str(data, "prov"),
            city = str(data, "city"),
            district = str(data, "district"),
        )
    }

    /** 备用: {"province":..,"city":..,"area":..} */
    private fun fetchUserAgentInfo(): LocInfo? {
        val el = get("https://ip.useragentinfo.com/json") ?: return null
        val d = el as? JsonObject ?: return null
        return LocInfo(
            province = str(d, "province"),
            city = str(d, "city"),
            district = str(d, "area"),
        )
    }

    private fun str(obj: JsonObject, key: String): String? =
        (obj.get(key) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
}
