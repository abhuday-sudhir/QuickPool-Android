package com.quickpool.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quickpool.app.network.ApiClient
import com.quickpool.app.network.BookingWithRideDto
import com.quickpool.app.network.RideOfferResponseDto
import com.quickpool.app.ui.components.RateUserDialog
import com.quickpool.app.ui.components.loadGivenRatings
import com.quickpool.app.ui.components.ratingKey
import com.quickpool.app.ui.components.ShareTripDialog
import kotlinx.coroutines.launch

@Composable
fun MyBookingsScreen(onTrack: (String) -> Unit, onDataChanged: () -> Unit) {
    var bookings by remember { mutableStateOf<List<BookingWithRideDto>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    // driverId per ride, so a passenger can rate whoever drove them
    var drivers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var ratingTarget by remember { mutableStateOf<BookingWithRideDto?>(null) }
    // Ride+driver pairs this user has already rated; the backend allows only one.
    var ratedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var shareTarget by remember { mutableStateOf<BookingWithRideDto?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            ratedKeys = loadGivenRatings()
            val response = ApiClient.rideApi.myBookings()
            if (response.isSuccessful) bookings = response.body() ?: emptyList()
            else errorMessage = "Failed to load (${response.code()})"
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    // My own rides tell us nothing about other people's; the driver id comes from
    // the ride each booking points at.
    LaunchedEffect(bookings) {
        val ids = bookings.map { it.rideOfferId }.distinct()
        if (ids.isEmpty()) return@LaunchedEffect
        drivers = runCatching {
            val r = ApiClient.rideApi.ridesByIds(ids)
            if (r.isSuccessful) (r.body() ?: emptyList()).associate { it.id to it.driverId }
            else emptyMap()
        }.getOrDefault(emptyMap())
    }

    shareTarget?.let { booking ->
        ShareTripDialog(
            rideOfferId = booking.rideOfferId,
            onDismiss = { shareTarget = null }
        )
    }

    ratingTarget?.let { booking ->
        val driverId = drivers[booking.rideOfferId]
        if (driverId == null) {
            ratingTarget = null
        } else {
            RateUserDialog(
                personName = "your driver",
                rideOfferId = booking.rideOfferId,
                rateeId = driverId,
                onDismiss = { ratingTarget = null },
                onRated = {
                    ratingTarget = null
                    ratedKeys = ratedKeys + ratingKey(booking.rideOfferId, driverId)
                    scope.launch { load() }
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface) }
            return@Column
        }

        if (bookings.isEmpty()) {
            Text(
                "You haven't requested any rides yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(bookings) { booking ->
                BookingCard(
                    booking = booking,
                    onShare = { shareTarget = booking },
                    canShare = booking.bookingStatus == "CONFIRMED",
                    canRate = drivers.containsKey(booking.rideOfferId) &&
                            (booking.bookingStatus == "CONFIRMED" || booking.bookingStatus == "COMPLETED") &&
                            (booking.rideStatus == "IN_PROGRESS" || booking.rideStatus == "COMPLETED"),
                    alreadyRated = drivers[booking.rideOfferId]
                        ?.let { ratingKey(booking.rideOfferId, it) in ratedKeys } == true,
                    onRate = { ratingTarget = booking },
                    onTrack = { onTrack(booking.rideOfferId) },
                    onCancel = {
                        scope.launch {
                            runCatching { ApiClient.rideApi.cancelBooking(booking.bookingId) }
                            load()
                            onDataChanged()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BookingCard(
    booking: BookingWithRideDto,
    canShare: Boolean,
    onShare: () -> Unit,
    canRate: Boolean,
    alreadyRated: Boolean,
    onRate: () -> Unit,
    onTrack: () -> Unit,
    onCancel: () -> Unit
) {
    val canTrack = booking.bookingStatus == "CONFIRMED" && booking.rideStatus == "IN_PROGRESS"
    val canCancel = booking.bookingStatus == "PENDING" || booking.bookingStatus == "CONFIRMED"

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDeparture(booking.departureTime),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(booking.bookingStatus)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                statusExplanation(booking.bookingStatus, booking.rideStatus),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (canShare) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Share this trip") }
            }

            if (canRate) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRate,
                    enabled = !alreadyRated,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (alreadyRated) "Driver rated" else "Rate your driver") }
            }

            if (canTrack || canCancel) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canTrack) {
                        Button(
                            onClick = onTrack,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface,
                                contentColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Track driver") }
                    }
                    if (canCancel) {
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (bg, fg) = when (status) {
        "CONFIRMED" -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        "PENDING" -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.outline to MaterialTheme.colorScheme.onSurface
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            status.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun statusExplanation(bookingStatus: String, rideStatus: String): String = when (bookingStatus) {
    "PENDING" -> "Waiting for the driver to accept your request."
    "CONFIRMED" -> when (rideStatus) {
        "IN_PROGRESS" -> "Your ride is underway — track the driver live."
        "CANCELLED" -> "The driver cancelled this ride."
        else -> "Your seat is confirmed. You can track once the driver starts."
    }
    "REJECTED" -> "The driver declined this request."
    "CANCELLED" -> "This booking was cancelled."
    "COMPLETED" -> "Ride completed."
    else -> bookingStatus
}
