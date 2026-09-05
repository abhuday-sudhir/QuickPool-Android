package com.quickpool.app.network

data class CreateRideOfferDto(
    val originLat: Double,
    val originLng: Double,
    val destinationLat: Double,
    val destinationLng: Double,
    val departureTime: String, // ISO format, e.g. "2026-08-25T14:00:00"
    val seatsTotal: Int,
    val pricePerSeat: Double?
)

data class RideSearchRequestDto(
    val pickupLat: Double,
    val pickupLng: Double,
    val dropLat: Double,
    val dropLng: Double,
    val earliestTime: String? = null,
    val latestTime: String? = null
)

data class RideOfferResponseDto(
    val id: String,
    val driverId: String,
    val originLat: Double,
    val originLng: Double,
    val destinationLat: Double,
    val destinationLng: Double,
    val departureTime: String,
    val seatsAvailable: Int,
    val pricePerSeat: Double?,
    val driverName: String? = null,
    val driverRating: Double? = null,
    val vehicle: String? = null
)

data class CreateBookingDto(
    val rideOfferId: String,
    val seatsBooked: Int = 1
)

data class BookingWithRideDto(

    val bookingId: String,
    val rideOfferId: String,
    val bookingStatus: String,
    val rideStatus: String,
    val departureTime: String
)