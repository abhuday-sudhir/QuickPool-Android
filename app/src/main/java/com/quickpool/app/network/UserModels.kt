package com.quickpool.app.network

data class UserResponseDto(
    val id: String,
    val phone: String,
    val name: String?,
    val email: String?,
    val ratingAvg: Double?,
    val profileComplete: Boolean = false,
    val emailVerified: Boolean = false
)

data class UpdateProfileDto(
    val name: String,
    val email: String
)

data class DestinationDto(
    val name: String,
    val lat: Double,
    val lng: Double,
    val useCount: Int
)

data class RecordDestinationDto(
    val name: String,
    val lat: Double,
    val lng: Double
)

data class EmergencyContactDto(
    val name: String,
    val phone: String
)

data class VerifyEmailDto(val code: String)

data class SavedAddressDto(
    val id: String,
    val label: String,
    val name: String,
    val lat: Double,
    val lng: Double
)

data class SaveAddressDto(
    val label: String,
    val name: String,
    val lat: Double,
    val lng: Double
)

data class ImpactDto(
    val sharedRides: Int,
    val sharedKm: Double,
    val co2SavedKg: Double,
    val treesEquivalent: Double
)
