package com.quickpool.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.quickpool.app.network.ApiClient
import com.quickpool.app.network.DirectionsHelper
import com.quickpool.app.network.LocationBroadcast
import com.quickpool.app.network.StompManager
import com.quickpool.app.data.TokenHolder

/** Location fixes arrive on this cadence; the marker is interpolated across it. */
private const val FIX_INTERVAL_MS = 4000L

/** A fix this far off the polyline is a real deviation rather than GPS noise. */
private const val OFF_ROUTE_METRES = 150.0

/** …but only after this many consecutive fixes, so one bad fix cannot bill us. */
private const val OFF_ROUTE_FIXES = 3

/** Hard floor between Directions requests, whatever else happens. */
private const val MIN_REFETCH_INTERVAL_MS = 120_000L

@SuppressLint("MissingPermission")
@Composable
fun LiveLocationScreen(rideId: String, isDriver: Boolean) {
    val context = LocalContext.current

    var myLocation by remember { mutableStateOf<LatLng?>(null) }
    var otherPartyLocation by remember { mutableStateOf<LatLng?>(null) }
    var myUserId by remember { mutableStateOf<String?>(null) }
    var destination by remember { mutableStateOf<LatLng?>(null) }
    var route by remember { mutableStateOf<SnappedRoute?>(null) }
    var progress by remember { mutableStateOf<RouteProgress?>(null) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val speedTracker = remember { SpeedTracker() }
    var lastRouteFetchMs by remember { mutableStateOf(0L) }
    var offRouteStreak by remember { mutableStateOf(0) }
    var routedTo by remember { mutableStateOf<LatLng?>(null) }

    val stompManager = remember {
        StompManager(rideId = rideId, token = TokenHolder.accessToken ?: "")
    }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Own id first: without it the echo filter below cannot tell my own
    // broadcast from the other party's.
    LaunchedEffect(Unit) {
        runCatching { ApiClient.userApi.me() }
            .getOrNull()?.takeIf { it.isSuccessful }?.body()?.let { myUserId = it.id }
    }

    // The route runs to the *ride's* destination, not to the other party. The
    // screen is only handed rideId/isDriver, so the ride is fetched on entry.
    LaunchedEffect(rideId) {
        runCatching { ApiClient.rideApi.ridesByIds(listOf(rideId)) }
            .getOrNull()?.takeIf { it.isSuccessful }?.body()?.firstOrNull()?.let {
                destination = LatLng(it.destinationLat, it.destinationLng)
            }
    }

    DisposableEffect(myUserId) {
        stompManager.connect { broadcast: LocationBroadcast ->
            // C1 — echo filter. We subscribe to the topic we publish to, so our
            // own fixes come straight back. Filtering on userId rather than role
            // also survives passenger-to-passenger echo once C3 lands.
            if (broadcast.userId != myUserId) {
                otherPartyLocation = LatLng(broadcast.lat, broadcast.lng)
            }
        }
        onDispose { stompManager.disconnect() }
    }

    // fusedLocationClient.lastLocation returns a cached fix that can be stale or
    // null; requestLocationUpdates delivers real ones.
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@DisposableEffect onDispose { }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, FIX_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FIX_INTERVAL_MS)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                myLocation = LatLng(loc.latitude, loc.longitude)
                if (isDriver) stompManager.sendLocation(loc.latitude, loc.longitude)
            }
        }
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        onDispose { fusedLocationClient.removeLocationUpdates(callback) }
    }

    // Whichever party is actually driving is the one moving along the route.
    val vehicleLocation = if (isDriver) myLocation else otherPartyLocation

    // C2 — the route is fetched once and only re-fetched on a real trigger.
    // Previously this LaunchedEffect re-keyed on every 4s fix: ~900 billable
    // Directions calls per hour, per open tracking screen.
    LaunchedEffect(vehicleLocation, destination) {
        val from = vehicleLocation ?: return@LaunchedEffect
        val to = destination ?: return@LaunchedEffect
        val now = System.currentTimeMillis()

        speedTracker.record(now, from)
        val current = route
        val snapped = current?.snap(from)

        offRouteStreak = when {
            snapped == null -> offRouteStreak
            snapped.offRouteMeters > OFF_ROUTE_METRES -> offRouteStreak + 1
            else -> 0
        }

        val destinationChanged = routedTo != null && routedTo != to
        val needsRoute = current == null ||
                destinationChanged ||
                offRouteStreak >= OFF_ROUTE_FIXES
        val allowed = destinationChanged || now - lastRouteFetchMs >= MIN_REFETCH_INTERVAL_MS

        if (needsRoute && allowed) {
            lastRouteFetchMs = now
            DirectionsHelper.getRoute(origin = from, destination = to)?.let { result ->
                route = SnappedRoute(result.points)
                routedTo = to
                offRouteStreak = 0
            }
        }

        // Snap against whatever route we now hold and derive the ETA locally.
        route?.snap(from)?.let {
            progress = it
            statusText = routeStatusText(it.remainingMeters, speedTracker.metresPerSecond())
        }
    }

    // Interpolate the vehicle across the 4s gap between fixes so the map reads as
    // continuous motion rather than a marker teleporting.
    val target = progress?.snapped ?: vehicleLocation
    var renderedVehicle by remember { mutableStateOf<LatLng?>(null) }
    LaunchedEffect(target) {
        val to = target ?: return@LaunchedEffect
        val from = renderedVehicle
        if (from == null) { renderedVehicle = to; return@LaunchedEffect }
        animate(0f, 1f, animationSpec = tween(FIX_INTERVAL_MS.toInt(), easing = LinearEasing)) { f, _ ->
            renderedVehicle = LatLng(
                from.latitude + (to.latitude - from.latitude) * f,
                from.longitude + (to.longitude - from.longitude) * f
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = if (isDriver) "Sharing your location" else "Tracking your driver",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        statusText?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (!hasLocationPermission) {
            Text(
                "Location permission needed to share your position.",
                modifier = Modifier.padding(16.dp)
            )
        }

        val defaultPosition = vehicleLocation ?: myLocation ?: LatLng(27.1767, 78.0081)
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(defaultPosition, 14f)
        }

        LaunchedEffect(target) {
            target?.let {
                cameraPositionState.animate(
                    com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(it, 15f)
                )
            }
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            progress?.let { p ->
                // Driven prefix greyed out, the road still ahead in primary.
                if (p.traveled.size > 1) {
                    Polyline(points = p.traveled, color = Color(0xFF9E9E9E), width = 10f)
                }
                if (p.remaining.size > 1) {
                    Polyline(points = p.remaining, color = MaterialTheme.colorScheme.primary, width = 10f)
                }
            }
            renderedVehicle?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = if (isDriver) "You" else "Driver"
                )
            }
            // The party who is not driving still gets a plain pin. C3 replaces
            // this with a per-passenger map keyed by userId.
            val passenger = if (isDriver) otherPartyLocation else myLocation
            passenger?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = if (isDriver) "Passenger" else "You"
                )
            }
            destination?.let {
                Marker(state = MarkerState(position = it), title = "Destination")
            }
        }
    }
}
