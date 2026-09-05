package com.quickpool.app.network

data class OtpRequestDto(
    val phone: String
)

data class OtpVerifyDto(
    val phone: String,
    val otp: String
)

data class AuthResponseDto(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val profileComplete: Boolean = false
)

data class RefreshTokenDto(
    val refreshToken: String
)