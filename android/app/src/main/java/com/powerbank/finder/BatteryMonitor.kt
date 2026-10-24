package com.powerbank.finder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/**
 * 电量监听：注册 ACTION_BATTERY_CHANGED（粘性广播）实时读取电量。
 * 电量下穿 20%/10%/5% 时、或系统进入省电模式时触发 [onLowBattery]（仅在放电时触发）。
 * 回调参数 level 取值为 20/10/5，或 -1 表示省电模式。
 */
class BatteryMonitor(private val context: Context) {

    /** 电量下穿 20%/10%/5% 或进入省电模式时回调（自动定位 + 找最近充电宝） */
    var onLowBattery: ((level: Int) -> Unit)? = null
    /** 每次电量变化回调，用于刷新 UI */
    var onBatteryChanged: ((level: Int, charging: Boolean) -> Unit)? = null

    private val thresholds = intArrayOf(20, 10, 5)
    private var lastLevel: Int = 100

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val info = intent?.let { readLevel(it) } ?: return
            val level = info.first
            val charging = info.second
            onBatteryChanged?.invoke(level, charging)
            if (charging) {
                lastLevel = level
                return
            }
            for (t in thresholds) {
                if (level <= t && lastLevel > t) onLowBattery?.invoke(t)
            }
            lastLevel = level
        }
    }

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (isPowerSaveMode() && !isChargingNow()) {
                onLowBattery?.invoke(-1)
            }
        }
    }

    private var started = false

    fun start() {
        if (started) return
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        context.registerReceiver(powerSaveReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        lastLevel = currentLevel() ?: 100
        started = true
    }

    fun stop() {
        if (!started) return
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { context.unregisterReceiver(powerSaveReceiver) }
        started = false
    }

    /** 立即读取当前电量（不依赖广播回调） */
    fun currentLevel(): Int? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.let { readLevel(it)?.first }
    }

    private fun isPowerSaveMode(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isPowerSaveMode ?: false
        } else {
            false
        }
    }

    private fun isChargingNow(): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.let { readLevel(it)?.second } ?: false
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