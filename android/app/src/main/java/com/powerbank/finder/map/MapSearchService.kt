package com.powerbank.finder.map

import android.content.Context
import com.google.gson.Gson
import com.powerbank.finder.model.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 充电宝 POI 地图搜索服务（彻底脱离后端）：
 *   amap  -> 高德 REST API /v3/place/around
 *   baidu -> 百度 REST API /place/v2/search
 *   mock  -> 内置模拟站点，按 haversine 距离升序返回
 * 真实接口 status 非成功、网络异常时抛异常，由上层回退 mock；
 * provider=mock 或对应 Key 未配置时不发真实请求，直接返回模拟数据。
 */
object MapSearchService {

    private const val AMAP_URL = "https://restapi.amap.com/v3/place/around"
    private const val BAIDU_URL = "https://api.map.baidu.com/place/v2/search"
    private const val KEYWORD = "充电宝"
    private const val RADIUS_M = 5000
    // 真实 POI 无库存字段，统一给演示默认值
    private const val DEFAULT_AVAILABLE = 8
    private const val DEFAULT_TOTAL = 8

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /** 按当前配置搜索附近充电宝；请求在 Dispatchers.IO 执行 */
    suspend fun searchNearby(context: Context, lat: Double, lng: Double): List<Station> =
        withContext(Dispatchers.IO) {
            when (MapConfig.provider(context)) {
                MapConfig.PROVIDER_AMAP -> {
                    val key = MapConfig.amapKey(context)
                    if (key.isBlank()) mockSearch(lat, lng) else searchAmap(key, lat, lng)
                }
                MapConfig.PROVIDER_BAIDU -> {
                    val ak = MapConfig.baiduKey(context)
                    if (ak.isBlank()) mockSearch(lat, lng) else searchBaidu(ak, lat, lng)
                }
                else -> mockSearch(lat, lng)
            }
        }

    /** 模拟站点兜底：本地计算距离并按升序排序 */
    fun mockSearch(lat: Double, lng: Double): List<Station> =
        MockStations.ALL
            .map { it.copy(distanceM = haversineM(lat, lng, it.lat, it.lng)) }
            .sortedBy { it.distanceM }

    // ---------- 高德 ----------
    private fun searchAmap(key: String, lat: Double, lng: Double): List<Station> {
        val url = AMAP_URL.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("key", key)
            .addQueryParameter("location", lng.toString() + "," + lat.toString())
            .addQueryParameter("keywords", KEYWORD)
            .addQueryParameter("radius", RADIUS_M.toString())
            .addQueryParameter("offset", "25")
            .addQueryParameter("page", "1")
            .addQueryParameter("extensions", "all")
            .build()
        val body = executeGet(url)
        val resp = gson.fromJson(body, AmapResponse::class.java)
            ?: throw IOException("高德接口返回为空")
        if (resp.status != "1") {
            throw IOException("高德接口失败：" + (resp.info ?: ("status=" + resp.status)))
        }
        val out = mutableListOf<Station>()
        var id = 1
        for (poi in resp.pois.orEmpty()) {
            val name = poi.name?.takeIf { it.isNotBlank() } ?: continue
            val parts = poi.location?.split(",")?.map { it.trim() } ?: continue
            if (parts.size < 2) continue
            val poiLng = parts[0].toDoubleOrNull() ?: continue
            val poiLat = parts[1].toDoubleOrNull() ?: continue
            out += Station(
                id = id++,
                name = name,
                lat = poiLat,
                lng = poiLng,
                available = DEFAULT_AVAILABLE,
                total = DEFAULT_TOTAL,
                pricePerHour = 0.0,
                distanceM = haversineM(lat, lng, poiLat, poiLng),
                address = poi.address ?: ""
            )
        }
        return out.sortedBy { it.distanceM }
    }

    // ---------- 百度 ----------
    private fun searchBaidu(ak: String, lat: Double, lng: Double): List<Station> {
        val url = BAIDU_URL.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("query", KEYWORD)
            .addQueryParameter("location", lat.toString() + "," + lng.toString())
            .addQueryParameter("radius", RADIUS_M.toString())
            .addQueryParameter("output", "json")
            .addQueryParameter("page_size", "20")
            .addQueryParameter("page_num", "0")
            .addQueryParameter("coord_type", "wgs84ll")
            .addQueryParameter("ak", ak)
            .build()
        val body = executeGet(url)
        val resp = gson.fromJson(body, BaiduResponse::class.java)
            ?: throw IOException("百度接口返回为空")
        if (resp.status != 0) {
            throw IOException("百度接口失败：" + (resp.message ?: ("status=" + resp.status)))
        }
        val out = mutableListOf<Station>()
        var id = 1
        for (poi in resp.results.orEmpty()) {
            val name = poi.name?.takeIf { it.isNotBlank() } ?: continue
            val poiLat = poi.location?.lat ?: continue
            val poiLng = poi.location?.lng ?: continue
            out += Station(
                id = id++,
                name = name,
                lat = poiLat,
                lng = poiLng,
                available = DEFAULT_AVAILABLE,
                total = DEFAULT_TOTAL,
                pricePerHour = 0.0,
                distanceM = haversineM(lat, lng, poiLat, poiLng),
                address = poi.address ?: ""
            )
        }
        return out.sortedBy { it.distanceM }
    }

    // ---------- 公共 ----------
    private fun executeGet(url: HttpUrl): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("地图接口 HTTP " + resp.code)
            if (text.isBlank()) throw IOException("地图接口返回空数据")
            return text
        }
    }

    /** 球面距离（米），与 backend/stations_data.py 的 haversine_m 一致 */
    fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lng2 - lng1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return round(2 * r * asin(sqrt(a)) * 10.0) / 10.0
    }

    // ---------- 响应模型（Gson 解析） ----------
    private data class AmapResponse(
        val status: String?,
        val info: String?,
        val pois: List<AmapPoi>?
    )

    private data class AmapPoi(
        val name: String?,
        val location: String?,
        val address: String?
    )

    private data class BaiduResponse(
        val status: Int?,
        val message: String?,
        val results: List<BaiduPoi>?
    )

    private data class BaiduPoi(
        val name: String?,
        val location: BaiduLocation?,
        val address: String?
    )

    private data class BaiduLocation(
        val lat: Double?,
        val lng: Double?
    )
}
