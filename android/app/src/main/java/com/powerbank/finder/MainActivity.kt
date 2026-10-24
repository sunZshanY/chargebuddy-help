package com.powerbank.finder

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.powerbank.finder.api.ApiClient
import com.powerbank.finder.api.RentBody
import com.powerbank.finder.databinding.ActivityMainBinding
import com.powerbank.finder.map.MapConfig
import com.powerbank.finder.map.MapNavigator
import com.powerbank.finder.map.MapSearchService
import com.powerbank.finder.model.PathCoord
import com.powerbank.finder.model.Station
import com.powerbank.finder.native.GraphBuilder
import com.powerbank.finder.native.RouteJNI
import com.powerbank.finder.ui.StationAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        if (grants.values.any { it }) locateUser { _, _ ->
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

        binding.etServer.setText(ApiClient.load(this))
        setupMapSettings()

        batteryMonitor.onBatteryChanged = { level, charging ->
            val status = if (charging) getString(R.string.battery_charging)
            else getString(R.string.battery_discharging)
            binding.tvBattery.text = getString(R.string.battery_line, level, status)
        }
        batteryMonitor.onLowBattery = { level -> handleLowBattery(level) }
        batteryMonitor.start()
        val cur = batteryMonitor.currentLevel() ?: -1
        binding.tvBattery.text = getString(R.string.battery_line_boot, cur)

        binding.btnRefresh.setOnClickListener {
            locateUser { _, _ -> refreshStations() }
        }
        binding.btnSimulate.setOnClickListener {
            handleLowBattery(20)
        }
        binding.btnSaveServer.setOnClickListener {
            val url = ApiClient.save(this, binding.etServer.text.toString())
            Toast.makeText(this, getString(R.string.server_saved, url), Toast.LENGTH_SHORT).show()
            refreshStations()
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

    // ---------- 地图来源设置 ----------
    private fun setupMapSettings() {
        binding.spinnerMapProvider.adapter = ArrayAdapter.createFromResource(
            this,
            R.array.map_provider_labels,
            android.R.layout.simple_spinner_item
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.spinnerMapProvider.setSelection(providerIndex(MapConfig.provider(this)))
        binding.etAmapKey.setText(MapConfig.amapKey(this))
        binding.etBaiduKey.setText(MapConfig.baiduKey(this))

        binding.btnSaveMap.setOnClickListener {
            val provider = providerValue(binding.spinnerMapProvider.selectedItemPosition)
            MapConfig.save(
                this,
                provider,
                binding.etAmapKey.text.toString(),
                binding.etBaiduKey.text.toString()
            )
            Toast.makeText(
                this,
                getString(R.string.map_settings_saved, providerLabel(provider)),
                Toast.LENGTH_SHORT
            ).show()
            locateUser { _, _ -> refreshStations() }
        }
    }

    private fun providerIndex(provider: String): Int = when (provider) {
        MapConfig.PROVIDER_BAIDU -> 1
        MapConfig.PROVIDER_MOCK -> 2
        else -> 0
    }

    private fun providerValue(index: Int): String = when (index) {
        1 -> MapConfig.PROVIDER_BAIDU
        2 -> MapConfig.PROVIDER_MOCK
        else -> MapConfig.PROVIDER_AMAP
    }

    private fun providerLabel(provider: String): String = when (provider) {
        MapConfig.PROVIDER_BAIDU -> getString(R.string.map_source_baidu)
        MapConfig.PROVIDER_MOCK -> getString(R.string.map_source_mock)
        else -> getString(R.string.map_source_amap)
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

    // ---------- 数据（地图搜索 + mock 兜底，不依赖后端） ----------
    private fun refreshStations() {
        val ctx = this
        lifecycleScope.launch {
            val list = runCatching {
                withContext(Dispatchers.IO) {
                    MapSearchService.searchNearby(ctx, currentLat, currentLng)
                }
            }.getOrElse {
                Toast.makeText(ctx, R.string.map_api_failed_fallback, Toast.LENGTH_LONG).show()
                withContext(Dispatchers.IO) {
                    MapSearchService.mockSearch(currentLat, currentLng)
                }
            }
            stations = list
            renderMap()
        }
    }

    private fun renderMap() {
        val adapter = StationAdapter(stations) { s ->
            onStationTap(s)
        }
        binding.stationList.adapter = adapter
        binding.mapView.setData(currentLat, currentLng, stations, currentRoute)
    }

    /** 点击站点：提供步行导航与借用两个动作（不再请求后端路线） */
    private fun onStationTap(s: Station) {
        val ctx = this
        AlertDialog.Builder(ctx)
            .setTitle(s.name)
            .setMessage(
                getString(R.string.station_dialog_meta, s.available, s.total, s.distanceM) +
                    if (s.address.isBlank()) "" else "\n" + getString(R.string.station_address_line, s.address)
            )
            .setPositiveButton(R.string.btn_navigate) { _, _ ->
                MapNavigator.openWalkingNavigation(ctx, MapConfig.provider(ctx), s.lat, s.lng, s.name)
            }
            .setNeutralButton(R.string.btn_rent) { _, _ -> rent(s.id) }
            .setNegativeButton(R.string.btn_got_it, null)
            .show()
    }

    // ---------- 低电量弹窗（定位 -> 地图搜索 -> 最近可借站点） ----------
    private fun handleLowBattery(level: Int) {
        if (emergencyTriggered) return
        emergencyTriggered = true
        val ctx = this
        val trigger = if (level < 0) getString(R.string.low_battery_power_save)
        else getString(R.string.low_battery_level, level)

        locateUser { lat, lng ->
            lifecycleScope.launch {
                try {
                    val list = runCatching {
                        withContext(Dispatchers.IO) {
                            MapSearchService.searchNearby(ctx, lat, lng)
                        }
                    }.getOrElse {
                        Toast.makeText(ctx, R.string.map_api_failed_fallback, Toast.LENGTH_LONG).show()
                        withContext(Dispatchers.IO) {
                            MapSearchService.mockSearch(lat, lng)
                        }
                    }
                    if (list.isEmpty()) {
                        Toast.makeText(ctx, R.string.no_station_found, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    // 可借的最近站；全部不可借则取距离最近
                    val nearest = list.filter { it.available > 0 }.minByOrNull { it.distanceM }
                        ?: list.minByOrNull { it.distanceM }!!
                    stations = list
                    currentRoute = emptyList()
                    binding.mapView.setData(lat, lng, stations, currentRoute)

                    val message = buildString {
                        append(trigger)
                        append("\n")
                        append(getString(R.string.dialog_station_name, nearest.name))
                        append("\n")
                        append(getString(R.string.dialog_available, nearest.available))
                        append("\n")
                        append(getString(R.string.dialog_distance, nearest.distanceM))
                        if (nearest.address.isNotBlank()) {
                            append("\n")
                            append(getString(R.string.dialog_address, nearest.address))
                        }
                    }
                    AlertDialog.Builder(ctx)
                        .setTitle(R.string.dialog_low_battery_title)
                        .setMessage(message)
                        .setPositiveButton(R.string.btn_navigate) { _, _ ->
                            MapNavigator.openWalkingNavigation(
                                ctx,
                                MapConfig.provider(ctx),
                                nearest.lat,
                                nearest.lng,
                                nearest.name
                            )
                        }
                        .setNegativeButton(R.string.btn_got_it, null)
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(
                        ctx,
                        getString(R.string.emergency_failed, e.message ?: ""),
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    emergencyTriggered = false
                }
            }
        }
    }

    // ---------- 借用/归还（仍走后端；失败仅提示，不影响找电宝与弹窗） ----------
    private fun rent(stationId: Int) {
        val ctx = this
        lifecycleScope.launch {
            runCatching { ApiClient.api.rent(RentBody(stationId)) }
                .onSuccess {
                    Toast.makeText(ctx, R.string.rent_success, Toast.LENGTH_SHORT).show()
                    refreshStations()
                }
                .onFailure {
                    Toast.makeText(
                        ctx,
                        getString(R.string.rent_failed, it.message ?: ""),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    // ---------- 本地 C++ 最快路线演示 ----------
    private fun runLocalRoute() {
        if (stations.isEmpty()) {
            Toast.makeText(this, R.string.local_route_need_refresh, Toast.LENGTH_SHORT).show()
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
                getString(R.string.local_route_success, result.totalWeight, result.path.size - 1),
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, R.string.local_route_fail, Toast.LENGTH_SHORT).show()
        }
    }
}
