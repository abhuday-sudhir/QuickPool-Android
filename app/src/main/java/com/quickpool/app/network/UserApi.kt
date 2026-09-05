package com.quickpool.app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface UserApi {

    @GET("api/v1/users/me")
    suspend fun me(): Response<UserResponseDto>

    @PUT("api/v1/users/me")
    suspend fun updateProfile(@Body dto: UpdateProfileDto): Response<UserResponseDto>

    @GET("api/v1/users/me/destinations")
    suspend fun frequentDestinations(): Response<List<DestinationDto>>

    @GET("api/v1/users/me/destinations/recent")
    suspend fun recentDestinations(): Response<List<DestinationDto>>

    @POST("api/v1/users/me/destinations")
    suspend fun recordDestination(@Body dto: RecordDestinationDto): Response<Unit>

    @GET("api/v1/users/me/addresses")
    suspend fun savedAddresses(): Response<List<SavedAddressDto>>

    @POST("api/v1/users/me/addresses")
    suspend fun saveAddress(@Body dto: SaveAddressDto): Response<SavedAddressDto>

    @DELETE("api/v1/users/me/addresses/{id}")
    suspend fun deleteAddress(@Path("id") id: String): Response<Unit>

    @GET("api/v1/users/me/impact")
    suspend fun impact(): Response<ImpactDto>

    @GET("api/v1/users/me/vehicle")
    suspend fun myVehicle(): Response<VehicleDto>

    @PUT("api/v1/users/me/vehicle")
    suspend fun saveVehicle(@Body dto: SaveVehicleDto): Response<VehicleDto>

    @DELETE("api/v1/users/me/vehicle")
    suspend fun deleteVehicle(): Response<Unit>

    @GET("api/v1/users/me/emergency-contact")
    suspend fun emergencyContact(): Response<EmergencyContactDto>

    @PUT("api/v1/users/me/emergency-contact")
    suspend fun saveEmergencyContact(@Body dto: EmergencyContactDto): Response<EmergencyContactDto>

    @DELETE("api/v1/users/me/emergency-contact")
    suspend fun deleteEmergencyContact(): Response<Unit>

    @POST("api/v1/users/me/email/verify/request")
    suspend fun requestEmailCode(): Response<Unit>

    @POST("api/v1/users/me/email/verify")
    suspend fun confirmEmail(@Body dto: VerifyEmailDto): Response<Unit>

    @POST("api/v1/users/me/logout")
    suspend fun logout(): Response<Unit>

    @DELETE("api/v1/users/me")
    suspend fun deleteAccount(): Response<Unit>
}
