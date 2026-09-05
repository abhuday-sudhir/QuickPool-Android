package com.quickpool.app.network

import com.quickpool.app.data.TokenStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private const val BASE_URL = "http://localhost:8080/"
    lateinit var tokenStore: TokenStore

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .authenticator(TokenAuthenticator(tokenStore))
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
    val rideApi: RideApi by lazy { retrofit.create(RideApi::class.java) }
    val userApi: UserApi by lazy { retrofit.create(UserApi::class.java) }
    val notificationApi: NotificationApi by lazy { retrofit.create(NotificationApi::class.java) }
    val safetyApi: SafetyApi by lazy { retrofit.create(SafetyApi::class.java) }
    val tripShareApi: TripShareApi by lazy { retrofit.create(TripShareApi::class.java) }
    val directionsApi: DirectionsApi by lazy { retrofit.create(DirectionsApi::class.java) }
}