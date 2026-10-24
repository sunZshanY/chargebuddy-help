package com.powerbank.finder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

/**
 * 电量监听：注册 ACTION_BATTERY_CHANGED（粘性广播）实时读取电量。
 * 当电量降到 1%（<=1%）时触发 [onLowBattery]，用于"低电量自动获取位置"。
 */
class BatteryMonitor(private val context: Context) {

    /** 电量降到 1% 时回调（自动定位 + 找最近充电宝） */
    var onLowBattery: (() -> Unit)? = null
    /** 每次电量变化回调，用于刷新 UI */
    var onBatteryChanged: ((level: Int, charging: Boolean) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val info = intent?.let { readLevel(it) } ?: return
            onBatteryChanged?.invoke(info.first, info.second)
            if (info.first <= 1) {
                onLowBattery?.invoke()
            }
        }
    }

    private var started = false

    fun start() {
        if (started) return
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        started = true
    }

    fun stop() {
        if (!started) return
        runCatching { context.unregisterReceiver(receiver) }
        started = false
    }

    /** 立即读取当前电量（不依赖广播回调） */
    fun currentLevel(): Int? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.let { readLevel(it)?.first }
    }

    companion object {
        /** 解析电量百分比与是否充电中 */
        fun readLevel(intent: Intent): Pair<Int, Boolean>? {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return null
            val pct = level * 100 / scale

            val charging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            } else {
                intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            }
            return pct to charging
        }
    }
}