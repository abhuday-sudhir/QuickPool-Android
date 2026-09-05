package com.quickpool.app.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

data class DirectionsDto(
    val polyline: String,
    val distanceText: String,
    val durationText: String,
    val distanceMeters: Long,
    val durationSeconds: Long
)

interface DirectionsApi {

    @GET("api/v1/directions")
    suspend fun route(
        @Query("originLat") originLat: Double,
        @Query("originLng") originLng: Double,
        @Query("destLat") destLat: Double,
        @Query("destLng") destLng: Double
    ): Response<DirectionsDto>
}
