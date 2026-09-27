package com.quickpool.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.quickpool.app.data.CurrentLocationProvider
import com.quickpool.app.network.ApiClient
import com.quickpool.app.network.CreateBookingDto
import com.quickpool.app.network.DirectionsHelper
import com.quickpool.app.network.ImpactDto
import com.quickpool.app.network.RideOfferResponseDto
import com.quickpool.app.network.RideSearchRequestDto
import com.quickpool.app.ui.components.ImpactCard
import com.quickpool.app.ui.components.PersonActionsMenu
import com.quickpool.app.ui.components.RatingStars
import com.quickpool.app.ui.components.ReportUserDialog
import kotlinx.coroutines.launch

private val DEFAULT_POSITION = LatLng(28.6139, 77.2090) // Delhi fallback

@Composable
fun HomeScreen(
    pickedDestination: PickedPlace?,
    locationPermissionGranted: Boolean,
    onSearchDestination: () -> Unit,
    onClearDestination: () -> Unit
) {
    val context = LocalContext.current
    val locationProvider = remember { CurrentLocationProvider(context) }
    val scope = rememberCoroutineScope()

    val destinationMarker = remember { squareMarker(Color.Black) }
    // Black to match the blinking pin shown while resolving the fix — one visual
    // language for "your position" on this map, whichever state it's in.
    val originMarker = remember { dotMarker(Color.Black) }

    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var locatingFailed by remember { mutableStateOf(false) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }

    var results by remember { mutableStateOf<List<RideOfferResponseDto>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var bookingMessage by remember { mutableStateOf<String?>(null) }
    var impact by remember { mutableStateOf<ImpactDto?>(null) }
    var reportTarget by remember { mutableStateOf<RideOfferResponseDto?>(null) }
    var blockTarget by remember { mutableStateOf<RideOfferResponseDto?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_POSITION, 14f)
    }

    LaunchedEffect(Unit) {
        impact = runCatching {
            val response = ApiClient.userApi.impact()
            if (response.isSuccessful) response.body() else null
        }.getOrNull()
    }

    LaunchedEffect(locationPermissionGranted) {
        if (!locationPermissionGranted) return@LaunchedEffect
        val loc = locationProvider.getCurrentLatLng()
        if (loc != null) {
            currentLocation = loc
            locatingFailed = false
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(loc, 15f))
        } else {
            locatingFailed = true
        }
    }

    LaunchedEffect(pickedDestination, currentLocation) {
        val pickup = currentLocation
        val destination = pickedDestination
        if (pickup == null || destination == null) {
            routePoints = emptyList()
            results = emptyList()
            return@LaunchedEffect
        }

        val routeResult = DirectionsHelper.getRoute(origin = pickup, destination = destination.latLng)
        routePoints = routeResult?.points ?: emptyList()
        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(destination.latLng, 13f))

        isSearching = true
        searchError = null
        try {
            val response = ApiClient.rideApi.searchRides(
                RideSearchRequestDto(
                    pickupLat = pickup.latitude,
                    pickupLng = pickup.longitude,
                    dropLat = destination.latLng.latitude,
                    dropLng = destination.latLng.longitude
                )
            )
            results = if (response.isSuccessful) response.body() ?: emptyList() else {
                searchError = "Search failed (${response.code()})"
                emptyList()
            }
        } catch (e: Exception) {
            searchError = "Error: ${e.message}"
        } finally {
            isSearching = false
        }
    }

    fun book(rideId: String) {
        scope.launch {
            try {
                val response = ApiClient.rideApi.bookRide(CreateBookingDto(rideOfferId = rideId))
                bookingMessage = if (response.isSuccessful)
                    "Request sent. You'll get an alert when the driver responds."
                else
                    apiErrorText(response.code(), response.errorBody()?.string())
            } catch (e: Exception) {
                bookingMessage = "Error: ${e.message}"
            }
        }
    }

    reportTarget?.let { ride ->
        ReportUserDialog(
            personName = ride.driverName ?: "this driver",
            reportedId = ride.driverId,
            rideOfferId = ride.id,
            onDismiss = { reportTarget = null },
            onReported = {
                reportTarget = null
                bookingMessage = "Report sent. Our team will review it."
            }
        )
    }

    blockTarget?.let { ride ->
        AlertDialog(
            onDismissRequest = { blockTarget = null },
            title = { Text("Block ${ride.driverName ?: "this driver"}?") },
            text = { Text("You won't see each other's rides, and neither of you can book the other.") },
            confirmButton = {
                TextButton(onClick = {
                    val driverId = ride.driverId
                    blockTarget = null
                    scope.launch {
                        runCatching { ApiClient.safetyApi.block(driverId) }
                        results = results.filterNot { it.driverId == driverId }
                        bookingMessage = "Blocked. Their rides are hidden from you."
                    }
                }) { Text("Block") }
            },
            dismissButton = { TextButton(onClick = { blockTarget = null }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(0.44f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
            ) {
                currentLocation?.let {
                    Marker(
                        state = MarkerState(position = it),
                        title = "You",
                        icon = originMarker,
                        anchor = Offset(0.5f, 0.5f)   // pinpoint markers centre on the point
                    )
                }
                pickedDestination?.let {
                    Marker(
                        state = MarkerState(position = it.latLng),
                        title = it.name,
                        icon = destinationMarker,
                        anchor = Offset(0.5f, 0.5f)
                    )
                }
                if (routePoints.isNotEmpty()) {
                    Polyline(points = routePoints, color = Color.Black, width = 12f)
                }
            }

            // Waiting on the first GPS fix is normal, not an error — a full cover over the
            // map (which would otherwise show placeholder tiles loading underneath) with a
            // pulsing marker reads better than red text under the sheet. Fixed light grey,
            // not a theme color: the marker itself is fixed black (see PulsingLocationMarker),
            // and MaterialTheme.colorScheme.background is pure black in dark theme — black on
            // black rendered as a plain black screen with no visible pulse at all.
            if (currentLocation == null && locationPermissionGranted && !locatingFailed) {
                Box(
                    modifier = Modifier.matchParentSize().background(com.quickpool.app.ui.theme.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    PulsingLocationMarker()
                }
            }

            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val loc = locationProvider.getCurrentLatLng()
                        if (loc != null) {
                            currentLocation = loc
                            locatingFailed = false
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(loc, 15f))
                        } else {
                            locatingFailed = true
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(44.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = "Recenter")
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.weight(0.56f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("Where to?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                if (pickedDestination == null) {
                    SearchPill(onClick = onSearchDestination)
                    LocationStatus(
                        permissionGranted = locationPermissionGranted,
                        hasLocation = currentLocation != null,
                        failed = locatingFailed
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    impact?.let { ImpactCard(it) }
                } else {
                    DestinationHeader(
                        name = pickedDestination.name,
                        onChange = onSearchDestination,
                        onClear = onClearDestination
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    when {
                        currentLocation == null -> LocationStatus(
                            permissionGranted = locationPermissionGranted,
                            hasLocation = false,
                            failed = locatingFailed
                        )

                        isSearching -> Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface) }

                        searchError != null -> Text(searchError ?: "", color = MaterialTheme.colorScheme.error)

                        results.isEmpty() -> Text(
                            "No rides on this route yet. Tap + to drive it yourself.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )

                        else -> LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(results) { ride ->
                                RideResultCard(
                                    ride = ride,
                                    onBook = { book(ride.id) },
                                    onReport = { reportTarget = ride },
                                    onBlock = { blockTarget = ride }
                                )
                            }
                        }
                    }

                    bookingMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** Pulls the backend's own message out of an error body when there is one. */
fun apiErrorText(code: Int, body: String?): String {
    val detail = body?.takeIf { it.isNotBlank() }?.let {
        Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1)
    }
    return detail ?: "Request failed ($code)"
}

@Composable
private fun LocationStatus(permissionGranted: Boolean, hasLocation: Boolean, failed: Boolean) {
    if (hasLocation) return
    // Actively locating, permission already granted: that's the normal startup path, shown
    // as the pulsing marker on the map instead — nothing to say here, and definitely not in red.
    if (permissionGranted && !failed) return
    val message = if (!permissionGranted)
        "Location permission is off. Enable it in Settings to use QuickPool."
    else
        "Couldn't get your location. Make sure GPS is on, then tap the location button on the map."
    Spacer(modifier = Modifier.height(12.dp))
    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/** A bold radar-style pulse behind a blinking location pin — reads as "still searching," not an error. */
@Composable
private fun PulsingLocationMarker(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "locating")
    val ringScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse-scale"
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse-alpha"
    )
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "blink"
    )
    Box(modifier = modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .scale(ringScale)
                .alpha(ringAlpha)
                .background(Color.Black, CircleShape)
        )
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(40.dp).alpha(blink)
        )
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Search destination",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DestinationHeader(name: String, onChange: () -> Unit, onClear: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Destination",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        TextButton(onClick = onChange) { Text("Change") }
        IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Clear destination")
        }
    }
}

@Composable
private fun RideResultCard(
    ride: RideOfferResponseDto,
    onBook: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ride.driverName ?: "QuickPool driver",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        RatingStars(ride.driverRating)
                    }
                    // What the passenger looks for at the kerb.
                    ride.vehicle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box {
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
                        onReport = onReport,
                        onBlock = onBlock
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        formatDeparture(ride.departureTime),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        buildString {
                            append("${ride.seatsAvailable} seat${if (ride.seatsAvailable == 1) "" else "s"} left")
                            ride.pricePerSeat?.let { append(" · ₹$it / seat") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onBook,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Request")
                }
            }
        }
    }
}
