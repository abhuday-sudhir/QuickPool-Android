package com.quickpool.app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SafetyApi {

    @POST("api/v1/ratings")
    suspend fun rate(@Body dto: RateUserDto): Response<Unit>

    @GET("api/v1/ratings/mine")
    suspend fun ratingsGiven(): Response<List<GivenRatingDto>>

    @GET("api/v1/users/{id}/ratings")
    suspend fun ratingsFor(@Path("id") userId: String): Response<List<RatingDto>>

    @POST("api/v1/users/{id}/block")
    suspend fun block(@Path("id") userId: String): Response<Unit>

    @DELETE("api/v1/users/{id}/block")
    suspend fun unblock(@Path("id") userId: String): Response<Unit>

    @GET("api/v1/users/me/blocked")
    suspend fun blocked(): Response<List<String>>

    @POST("api/v1/reports")
    suspend fun report(@Body dto: ReportUserDto): Response<Unit>
}
