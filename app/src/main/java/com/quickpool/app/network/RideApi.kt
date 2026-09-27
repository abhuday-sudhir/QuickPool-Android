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
    suspend fun bookingRequests(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = DEFAULT_PAGE_SIZE
    ): Response<PageResponse<BookingRequestDto>>

    @PUT("api/v1/ride-offers/{id}/start")
    suspend fun startRide(@Path("id") id: String): Response<Unit>

    @GET("api/v1/ride-offers/mine")
    suspend fun myRides(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = DEFAULT_PAGE_SIZE
    ): Response<PageResponse<RideOfferResponseDto>>

    @GET("api/v1/ride-offers")
    suspend fun ridesByIds(@Query("ids") ids: List<String>): Response<List<RideOfferResponseDto>>

    @GET("api/v1/bookings/mine")
    suspend fun myBookings(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = DEFAULT_PAGE_SIZE
    ): Response<PageResponse<BookingWithRideDto>>

    /** Confirmed passenger ids in booking order — used to number live-map markers 1, 2, 3... */
    @GET("api/v1/ride-offers/{id}/passengers")
    suspend fun passengerOrder(@Path("id") id: String): Response<List<String>>
}