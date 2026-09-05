package com.quickpool.app.network

data class VehicleDto(
    val make: String,
    val model: String,
    val color: String,
    val plate: String
)

data class SaveVehicleDto(
    val make: String,
    val model: String,
    val color: String,
    val plate: String
)

data class RateUserDto(
    val rideOfferId: String,
    val rateeId: String,
    val stars: Int,
    val comment: String?
)

data class RatingDto(
    val stars: Int,
    val comment: String?,
    val raterName: String,
    val createdAt: String?
)

/** A rating the signed-in user has already given; keyed by ride + person. */
data class GivenRatingDto(
    val rideOfferId: String,
    val rateeId: String,
    val stars: Int
)

data class ReportUserDto(
    val reportedId: String,
    val rideOfferId: String?,
    val reason: String,
    val details: String?
)
