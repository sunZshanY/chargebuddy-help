package com.powerbank.finder.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.powerbank.finder.R

/**
 * 打开手机地图 App 步行导航：
 *   amap  -> amapuri:// 协议
 *   baidu -> baidumap:// 协议
 * 地图 App 未安装（ActivityNotFoundException）时回退系统 geo: URI。
 */
object MapNavigator {

    fun openWalkingNavigation(context: Context, provider: String, lat: Double, lng: Double, name: String) {
        val fallback = { openGeo(context, lat, lng, name) }
        when (provider) {
            MapConfig.PROVIDER_AMAP -> startOr(
                context,
                "amapuri://navi?sourceApplication=chargebuddy&lat=" + lat + "&lon=" + lng + "&dev=1&style=2",
                fallback
            )
            MapConfig.PROVIDER_BAIDU -> startOr(
                context,
                "baidumap://map/direction?destination=" + lat + "," + lng + "&mode=walking&src=chargebuddy",
                fallback
            )
            else -> fallback()
        }
    }

    private fun startOr(context: Context, uri: String, fallback: () -> Unit) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        } catch (_: ActivityNotFoundException) {
            fallback()
        }
    }

    /** 系统 geo: URI 兜底（URI 用 Uri.parse 构造，名称参数 URL 编码） */
    private fun openGeo(context: Context, lat: Double, lng: Double, name: String) {
        val uri = Uri.parse("geo:" + lat + "," + lng + "?q=" + Uri.encode(name))
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.navigation_not_found, Toast.LENGTH_SHORT).show()
        }
    }
}
