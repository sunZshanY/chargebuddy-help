package com.powerbank.finder.model

import com.google.gson.annotations.SerializedName

data class Station(
    val id: Int,
    val name: String,
    val lat: Double,
    val lng: Double,
    val available: Int,
    val total: Int,
    @SerializedName("price_per_hour") val pricePerHour: Double = 0.0,
    @SerializedName("distance_m") val distanceM: Double = 0.0
)

data class PathCoord(val id: Int, val lat: Double, val lng: Double)

data class RouteInfo(
    val found: Boolean = false,
    val mode: String = "time",
    val path: List<Int> = emptyList(),
    @SerializedName("total_weight") val totalWeight: Double = 0.0,
    val engine: String? = null,
    @SerializedName("path_coords") val pathCoords: List<PathCoord> = emptyList()
)

data class EmergencyResponse(
    val station: Station,
    val route: RouteInfo,
    val hint: String
)