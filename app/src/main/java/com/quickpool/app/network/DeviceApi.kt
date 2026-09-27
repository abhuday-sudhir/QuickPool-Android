package com.quickpool.app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.POST

data class RegisterDeviceRequest(
    val token: String,
    val platform: String = "android"
)

interface DeviceApi {

    @POST("api/v1/devices")
    suspend fun register(@Body body: RegisterDeviceRequest): Response<Unit>

    /**
     * @HTTP rather than @DELETE because an FCM token is too long and too punctuated to sit
     * in a path segment safely, and Retrofit's @DELETE takes no body.
     */
    @HTTP(method = "DELETE", path = "api/v1/devices", hasBody = true)
    suspend fun unregister(@Body body: RegisterDeviceRequest): Response<Unit>
}
