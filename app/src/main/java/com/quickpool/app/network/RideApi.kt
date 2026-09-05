package com.quickpool.app.network

import retrofit2.Response
import retrofit2.http.*

interface RideApi {

    @POST("api/v1/ride-offers")
    suspend fun createRideOffer(@Body dto: CreateRideOfferDto): Response<RideOfferResponseDto>

    @POST("api/v1/ride-offers/search")
    suspend fun searchRides(@Body dto: RideSearchRequestDto): Response<List<RideOfferResponseDto>>

    @PUT("api/v1/ride-offers/{id}/cancel")
    suspend fun cancelRideOffer(@Path("id") id: String): Response<Unit>

    @POST("api/v1/bookings")
    suspend fun bookRide(@Body dto: CreateBookingDto): Response<String>

    @PUT("api/v1/bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") id: String): Response<Unit>

    @PUT("api/v1/bookings/{id}/accept")
    suspend fun acceptBooking(@Path("id") id: String): Response<Unit>

    @PUT("api/v1/bookings/{id}/reject")
    suspend fun rejectBooking(@Path("id") id: String): Response<Unit>

    @GET("api/v1/bookings/requests")
    suspend fun bookingRequests(): Response<List<BookingRequestDto>>

    @PUT("api/v1/ride-offers/{id}/start")
    suspend fun startRide(@Path("id") id: String): Response<Unit>

    @GET("api/v1/ride-offers/mine")
    suspend fun myRides(): Response<List<RideOfferResponseDto>>

    @GET("api/v1/ride-offers")
    suspend fun ridesByIds(@Query("ids") ids: List<String>): Response<List<RideOfferResponseDto>>

    @GET("api/v1/bookings/mine")
    suspend fun myBookings(): Response<List<BookingWithRideDto>>
}