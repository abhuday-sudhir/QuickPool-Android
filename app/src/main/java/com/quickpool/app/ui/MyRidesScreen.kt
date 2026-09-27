package com.quickpool.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quickpool.app.network.ApiClient
import com.quickpool.app.network.BookingRequestDto
import com.quickpool.app.network.RideOfferResponseDto
import com.quickpool.app.ui.components.LoadMoreRow
import com.quickpool.app.ui.components.PersonActionsMenu
import com.quickpool.app.ui.components.RateUserDialog
import com.quickpool.app.ui.components.RatingStars
import com.quickpool.app.ui.components.loadGivenRatings
import com.quickpool.app.ui.components.ratingKey
import com.quickpool.app.ui.components.ReportUserDialog
import kotlinx.coroutines.launch

@Composable
fun MyRidesScreen(onStartRide: (String) -> Unit, onDataChanged: () -> Unit) {
    var rides by remember { mutableStateOf<List<RideOfferResponseDto>>(emptyList()) }
    var ridesPage by remember { mutableStateOf(0) }
    var ridesHasNext by remember { mutableStateOf(false) }
    var ridesLoadingMore by remember { mutableStateOf(false) }
    var requests by remember { mutableStateOf<List<BookingRequestDto>>(emptyList()) }
    var requestsPage by remember { mutableStateOf(0) }
    var requestsHasNext by remember { mutableStateOf(false) }
    var requestsLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var ratingTarget by remember { mutableStateOf<BookingRequestDto?>(null) }
    // Ride+passenger pairs this driver has already rated; the backend allows only one.
    var ratedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reportTarget by remember { mutableStateOf<BookingRequestDto?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            ratedKeys = loadGivenRatings()
            val ridesResponse = ApiClient.rideApi.myRides(page = 0)
            if (ridesResponse.isSuccessful) {
                val body = ridesResponse.body()
                rides = body?.content ?: emptyList()
                ridesHasNext = body?.hasNext ?: false
                ridesPage = 0
            } else errorMessage = "Failed to load rides (${ridesResponse.code()})"

            val requestsResponse = ApiClient.rideApi.bookingRequests(page = 0)
            if (requestsResponse.isSuccessful) {
                val body = requestsResponse.body()
                requests = body?.content ?: emptyList()
                requestsHasNext = body?.hasNext ?: false
                requestsPage = 0
            }
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    suspend fun loadMoreRides() {
        if (ridesLoadingMore || !ridesHasNext) return
        ridesLoadingMore = true
        try {
            val response = ApiClient.rideApi.myRides(page = ridesPage + 1)
            if (response.isSuccessful) {
                val body = response.body()
                rides = rides + (body?.content ?: emptyList())
                ridesHasNext = body?.hasNext ?: false
                ridesPage += 1
            }
        } catch (_: Exception) {
        } finally {
            ridesLoadingMore = false
        }
    }

    suspend fun loadMoreRequests() {
        if (requestsLoadingMore || !requestsHasNext) return
        requestsLoadingMore = true
        try {
            val response = ApiClient.rideApi.bookingRequests(page = requestsPage + 1)
            if (response.isSuccessful) {
                val body = response.body()
                requests = requests + (body?.content ?: emptyList())
                requestsHasNext = body?.hasNext ?: false
                requestsPage += 1
            }
        } catch (_: Exception) {
        } finally {
            requestsLoadingMore = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val pending = requests.filter { it.bookingStatus == "PENDING" }
    // Rate someone only once the ride they shared is actually under way.
    // COMPLETED matters as much as CONFIRMED: the lifecycle sweep completes bookings
    // when a ride ends, and that is exactly when people want to rate each other.
    val rateable = requests.filter {
        (it.bookingStatus == "CONFIRMED" || it.bookingStatus == "COMPLETED") &&
                (it.rideStatus == "IN_PROGRESS" || it.rideStatus == "COMPLETED")
    }

    ratingTarget?.let { req ->
        RateUserDialog(
            personName = req.passengerName ?: "your passenger",
            rideOfferId = req.rideOfferId,
            rateeId = req.passengerId,
            onDismiss = { ratingTarget = null },
            onRated = {
                ratingTarget = null
                ratedKeys = ratedKeys + ratingKey(req.rideOfferId, req.passengerId)
                scope.launch { load() }
            }
        )
    }

    reportTarget?.let { req ->
        ReportUserDialog(
            personName = req.passengerName ?: "your passenger",
            reportedId = req.passengerId,
            rideOfferId = req.rideOfferId,
            onDismiss = { reportTarget = null },
            onReported = { reportTarget = null }
        )
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

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (pending.isNotEmpty()) {
                item {
                    Text(
                        "Requests waiting on you",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(pending) { req ->
                    RequestCard(
                        req = req,
                        onAccept = {
                            scope.launch {
                                runCatching { ApiClient.rideApi.acceptBooking(req.bookingId) }
                                load()
                                onDataChanged()
                            }
                        },
                        onDecline = {
                            scope.launch {
                                runCatching { ApiClient.rideApi.rejectBooking(req.bookingId) }
                                load()
                                onDataChanged()
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            if (rateable.isNotEmpty()) {
                item {
                    Text(
                        "Rate your passengers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(rateable) { req ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        req.passengerName ?: "A passenger",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    RatingStars(req.passengerRating)
                                }
                                req.passengerPhone?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            val rated = ratingKey(req.rideOfferId, req.passengerId) in ratedKeys
                            TextButton(
                                onClick = { ratingTarget = req },
                                enabled = !rated
                            ) { Text(if (rated) "Rated" else "Rate") }
                            Box {
                                var menuOpen by remember { mutableStateOf(false) }
                                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                PersonActionsMenu(
                                    expanded = menuOpen,
                                    onDismiss = { menuOpen = false },
                                    onReport = { reportTarget = req },
                                    onBlock = {
                                        scope.launch {
                                            runCatching { ApiClient.safetyApi.block(req.passengerId) }
                                            load()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            if (requestsHasNext) {
                item { LoadMoreRow(isLoading = requestsLoadingMore) { scope.launch { loadMoreRequests() } } }
            }

            item {
                Text(
                    "Rides you're driving",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (rides.isEmpty()) {
                item {
                    Text(
                        "You haven't offered any rides yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            items(rides) { ride ->
                val confirmed = requests.count {
                    it.rideOfferId == ride.id && it.bookingStatus == "CONFIRMED"
                }
                RideCard(ride = ride, confirmedCount = confirmed, startable = isStartable(ride.departureTime), onStart = {
                    scope.launch {
                        try {
                            val response = ApiClient.rideApi.startRide(ride.id)
                            if (response.isSuccessful) {
                                onDataChanged()
                                onStartRide(ride.id)
                            } else {
                                errorMessage = "Could not start ride (${response.code()})"
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error: ${e.message}"
                        }
                    }
                })
            }

            if (ridesHasNext) {
                item { LoadMoreRow(isLoading = ridesLoadingMore) { scope.launch { loadMoreRides() } } }
            }
        }
    }
}

@Composable
private fun RequestCard(req: BookingRequestDto, onAccept: () -> Unit, onDecline: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                req.passengerName?.takeIf { it.isNotBlank() } ?: "A passenger",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "${req.seatsBooked} seat${if (req.seatsBooked == 1) "" else "s"} · ${formatDeparture(req.departureTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Requested ${formatRelative(req.requestedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Accept") }
                OutlinedButton(
                    onClick = onDecline,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Decline") }
            }
        }
    }
}

@Composable
private fun RideCard(
    ride: RideOfferResponseDto,
    confirmedCount: Int,
    startable: Boolean,
    onStart: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatDeparture(ride.departureTime),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$confirmedCount confirmed · ${ride.seatsAvailable} seat${if (ride.seatsAvailable == 1) "" else "s"} left",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (startable) {
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Start") }
            } else {
                Text(
                    "Departed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The server expires unstarted rides a few hours after departure, so offering
 * "Start" past that point can only produce a 409.
 */
private fun isStartable(departureIso: String): Boolean = runCatching {
    java.time.LocalDateTime.parse(departureIso)
        .isAfter(java.time.LocalDateTime.now().minusHours(3))
}.getOrDefault(true)
