package com.powerbank.finder.map

import android.content.Context

/**
 * 地图来源与 Key 配置（复用 App 级 SharedPreferences）。
 * provider 取值：amap / baidu / mock，默认 amap；
 * 对应 Key 为空表示未配置，搜索时不允许发真实请求、直接回退模拟数据。
 */
object MapConfig {

    const val PROVIDER_AMAP = "amap"
    const val PROVIDER_BAIDU = "baidu"
    const val PROVIDER_MOCK = "mock"
    const val DEFAULT_PROVIDER = PROVIDER_AMAP

    private const val PREFS = "chargebuddy_prefs"
    private const val KEY_PROVIDER = "map_provider"
    private const val KEY_AMAP_KEY = "amap_key"
    private const val KEY_BAIDU_AK = "baidu_ak"

    fun provider(context: Context): String =
        prefs(context).getString(KEY_PROVIDER, DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER

    fun amapKey(context: Context): String =
        prefs(context).getString(KEY_AMAP_KEY, "") ?: ""

    fun baiduKey(context: Context): String =
        prefs(context).getString(KEY_BAIDU_AK, "") ?: ""

    fun save(context: Context, provider: String, amapKey: String, baiduKey: String) {
        prefs(context).edit()
            .putString(KEY_PROVIDER, provider)
            .putString(KEY_AMAP_KEY, amapKey.trim())
            .putString(KEY_BAIDU_AK, baiduKey.trim())
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
