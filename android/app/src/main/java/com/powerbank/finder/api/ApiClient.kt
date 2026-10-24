package com.powerbank.finder.api

import android.content.Context
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.POST

/**
 * 后端接口：仅保留借用 / 归还。
 * 找充电宝与路线已改走本地地图服务（MapSearchService），不依赖后端。
 */
interface ApiService {
    @POST("api/rent")
    suspend fun rent(@retrofit2.http.Body body: RentBody): Map<String, Any>

    @POST("api/return")
    suspend fun returnBank(@retrofit2.http.Body body: RentBody): Map<String, Any>
}

data class RentBody(val station_id: Int)

object ApiClient {
    // 模拟器通过 10.0.2.2 访问宿主机后端；真机可在 App 内保存宿主机局域网 IP
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"
    private const val PREFS = "chargebuddy_prefs"
    private const val KEY_BASE_URL = "base_url"

    @Volatile private var baseUrl = DEFAULT_BASE_URL
    @Volatile private var cachedApi: ApiService? = null
    @Volatile private var cachedUrl = ""

    val api: ApiService
        get() {
            val u = baseUrl
            if (cachedApi == null || cachedUrl != u) {
                cachedApi = Retrofit.Builder()
                    .baseUrl(u)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)
                cachedUrl = u
            }
            return cachedApi!!
        }

    /** 读取已保存的后端地址（无保存值时返回默认地址），返回当前生效地址 */
    fun load(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, null)
        if (!saved.isNullOrBlank()) baseUrl = normalize(saved)
        return baseUrl
    }

    /** 保存后端地址并立即生效，返回规范化后的地址 */
    fun save(context: Context, url: String): String {
        baseUrl = normalize(url)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_BASE_URL, baseUrl).apply()
        return baseUrl
    }

    private fun normalize(url: String): String {
        var u = url.trim()
        if (u.isEmpty()) return DEFAULT_BASE_URL
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u
        if (!u.endsWith("/")) u = u + "/"
        return u
    }
}
