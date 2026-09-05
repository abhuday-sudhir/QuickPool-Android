package com.quickpool.app.network

data class NotificationDto(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val entityId: String?,
    val read: Boolean,
    val createdAt: String
)

data class UnreadCountDto(val count: Int)

data class BookingRequestDto(
    val bookingId: String,
    val rideOfferId: String,
    val passengerId: String,
    val passengerName: String?,
    val passengerPhone: String?,
    val passengerRating: Double? = null,
    val seatsBooked: Int,
    val bookingStatus: String,
    val rideStatus: String,
    val departureTime: String,
    val requestedAt: String
)
