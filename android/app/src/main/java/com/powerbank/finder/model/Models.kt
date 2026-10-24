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
    @SerializedName("distance_m") val distanceM: Double = 0.0,
    val address: String = ""
)

data class PathCoord(val id: Int, val lat: Double, val lng: Double)
