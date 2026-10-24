package com.powerbank.finder

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.powerbank.finder.api.ApiClient
import com.powerbank.finder.databinding.ActivityMainBinding
import com.powerbank.finder.model.PathCoord
import com.powerbank.finder.model.Station
import com.powerbank.finder.native.GraphBuilder
import com.powerbank.finder.native.RouteJNI
import com.powerbank.finder.ui.StationAdapter
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationHelper: LocationHelper
    private lateinit var batteryMonitor: BatteryMonitor

    private var currentLat = LocationHelper.FALLBACK_LAT
    private var currentLng = LocationHelper.FALLBACK_LNG
    private var stations: List<Station> = emptyList()
    private var currentRoute: List<PathCoord> = emptyList()
    private var emergencyTriggered = false

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) locateUser { lat, lng ->
            refreshStations()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationHelper = LocationHelper(this)
        batteryMonitor = BatteryMonitor(this)

        binding.stationList.layoutManager = LinearLayoutManager(this)

        batteryMonitor.onBatteryChanged = { level, charging ->
            val status = if (charging) "充电中" else "放电"
            binding.tvBattery.text = getString(R.string.battery_prefix) + " $level%（$status）"
        }
        batteryMonitor.onLowBattery = { handleLowBattery() }
        batteryMonitor.start()
        val cur = batteryMonitor.currentLevel() ?: -1
        binding.tvBattery.text = getString(R.string.battery_prefix) + " $cur%"

        binding.btnRefresh.setOnClickListener {
            locateUser { _, _ -> refreshStations() }
        }
        binding.btnSimulate.setOnClickListener {
            handleLowBattery()
        }
        binding.btnLocalRoute.setOnClickListener {
            runLocalRoute()
        }

        requestLocationIfNeeded()
        refreshStations()
    }

    override fun onDestroy() {
        super.onDestroy()
        batteryMonitor.stop()
    }

    // ---------- 定位 ----------
    private fun requestLocationIfNeeded() {
        if (locationHelper.hasPermission()) return
        locationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun locateUser(callback: (Double, Double) -> Unit) {
        locationHelper.requestSingle { loc ->
            if (loc != null) {
                currentLat = loc.latitude
                currentLng = loc.longitude
            } else {
                locationHelper.lastKnown()?.let {
                    currentLat = it.latitude
                    currentLng = it.longitude
                }
            }
            callback(currentLat, currentLng)
        }
    }

    // ---------- 数据 ----------
    private fun refreshStations() {
        lifecycleScope.launch {
            runCatching {
                ApiClient.api.stations(currentLat, currentLng)
            }.onSuccess { list ->
                stations = list
                renderMap()
            }.onFailure {
                Toast.makeText(this@MainActivity, "后端不可用：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderMap() {
        val adapter = StationAdapter(stations) { s ->
            showRouteToStation(s)
        }
        binding.stationList.adapter = adapter
        binding.mapView.setData(currentLat, currentLng, stations, currentRoute)
    }

    private fun showRouteToStation(s: Station) {
        lifecycleScope.launch {
            runCatching {
                ApiClient.api.route(currentLat, currentLng, s.id, "time")
            }.onSuccess { route ->
                currentRoute = route.pathCoords
                binding.mapView.setData(currentLat, currentLng, stations, currentRoute)
                Toast.makeText(
                    this@MainActivity,
                    String.format(Locale.CHINA, "最快路线：%.0f 秒（%s）", route.totalWeight, route.engine ?: "-"),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, "路线获取失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- 低电量兜底（自动获取位置 -> 找最近充电宝） ----------
    private fun handleLowBattery() {
        if (emergencyTriggered) return
        emergencyTriggered = true

        locateUser { lat, lng ->
            lifecycleScope.launch {
                runCatching {
                    ApiClient.api.emergency(lat, lng)
                }.onSuccess { resp ->
                    stations = listOf(resp.station)
                    currentRoute = resp.route.pathCoords
                    binding.mapView.setData(lat, lng, stations, currentRoute)
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("电量即将耗尽！")
                        .setMessage(
                            "为你定位到最近可借码宝：${resp.station.name}\n" +
                                "可用 ${resp.station.available} 个，步行约 " +
                                String.format(Locale.CHINA, "%.0f 米\n", resp.station.distanceM) +
                                resp.hint
                        )
                        .setPositiveButton("去借用") { _, _ -> rent(resp.station.id) }
                        .setNegativeButton("取消", null)
                        .show()
                }.onFailure {
                    Toast.makeText(this@MainActivity, "兜底失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        emergencyTriggered = false
    }

    private fun rent(stationId: Int) {
        lifecycleScope.launch {
            runCatching { ApiClient.api.rent(com.powerbank.finder.api.RentBody(stationId)) }
                .onSuccess {
                    Toast.makeText(this@MainActivity, "借用成功！", Toast.LENGTH_SHORT).show()
                    refreshStations()
                }
                .onFailure {
                    Toast.makeText(this@MainActivity, "借用失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // ---------- 本地 C++ 最快路线演示 ----------
    private fun runLocalRoute() {
        if (stations.isEmpty()) {
            Toast.makeText(this, "请先刷新附近站点", Toast.LENGTH_SHORT).show()
            return
        }
        val (graph, goal) = GraphBuilder.build(currentLat, currentLng, stations)
        val result = RouteJNI.findRoute(graph, 0, goal)
        if (result != null && result.found) {
            val coords = result.path.map { nodeId ->
                graph.nodes.first { it.id == nodeId }.let { PathCoord(it.id, it.lat, it.lng) }
            }
            currentRoute = coords
            binding.mapView.setData(currentLat, currentLng, stations, currentRoute)
            Toast.makeText(
                this,
                String.format(Locale.CHINA, "本地 C++ 最快路线：%.1f 秒（%d 跳）", result.totalWeight, result.path.size - 1),
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "本地路线不可达", Toast.LENGTH_SHORT).show()
        }
    }
}