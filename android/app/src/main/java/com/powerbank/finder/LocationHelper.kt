package com.powerbank.finder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.app.ActivityCompat

/**
 * 定位助手：获取最后一次已知位置 / 请求一次定位。
 * Demo 中若模拟器未开启定位，将回退到上海人民广场坐标以方便演示。
 */
class LocationHelper(private val context: Context) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun lastKnown(): Location? {
        if (!hasPermission()) return null
        return lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
    }

    fun requestSingle(callback: (Location?) -> Unit) {
        if (!hasPermission()) {
            callback(null)
            return
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lm.removeUpdates(this)
                callback(location)
            }

            override fun onProviderDisabled(provider: String) {
                lm.removeUpdates(this)
                callback(null)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        try {
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, null)
        } catch (_: Exception) {
            callback(lastKnown())
        }
    }

    companion object {
        // 上海人民广场（模拟器无定位时使用）
        const val FALLBACK_LAT = 31.2304
        const val FALLBACK_LNG = 121.4737
    }
}