package com.quickpool.app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/v1/auth/otp/request")
    suspend fun requestOtp(@Body dto: OtpRequestDto): Response<Unit>

    @POST("api/v1/auth/otp/verify")
    suspend fun verifyOtp(@Body dto: OtpVerifyDto): Response<AuthResponseDto>

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body dto: RefreshTokenDto): Response<AuthResponseDto>
}