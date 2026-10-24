package com.powerbank.finder.api

import com.powerbank.finder.model.EmergencyResponse
import com.powerbank.finder.model.RouteInfo
import com.powerbank.finder.model.Station
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @GET("api/stations")
    suspend fun stations(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Double = 5000.0
    ): List<Station>

    @GET("api/emergency")
    suspend fun emergency(@Query("lat") lat: Double, @Query("lng") lng: Double): EmergencyResponse

    @GET("api/route")
    suspend fun route(
        @Query("from_lat") fromLat: Double,
        @Query("from_lng") fromLng: Double,
        @Query("station_id") stationId: Int,
        @Query("mode") mode: String = "time"
    ): RouteInfo

    @POST("api/rent")
    suspend fun rent(@retrofit2.http.Body body: RentBody): Map<String, Any>

    @POST("api/return")
    suspend fun returnBank(@retrofit2.http.Body body: RentBody): Map<String, Any>
}

data class RentBody(val station_id: Int)

object ApiClient {
    // 模拟器通过 10.0.2.2 访问宿主机后端；真机请改为宿主机局域网 IP
    const val BASE_URL = "http://10.0.2.2:8000/"

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}