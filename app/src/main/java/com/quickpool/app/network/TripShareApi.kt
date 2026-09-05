package com.quickpool.app.network

import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

data class TripShareDto(
    val token: String,
    val url: String,
    val expiresAt: String?
)

interface TripShareApi {

    @POST("api/v1/rides/{rideId}/share")
    suspend fun share(@Path("rideId") rideId: String): Response<TripShareDto>

    @DELETE("api/v1/rides/{rideId}/share")
    suspend fun revoke(@Path("rideId") rideId: String): Response<Unit>
}
