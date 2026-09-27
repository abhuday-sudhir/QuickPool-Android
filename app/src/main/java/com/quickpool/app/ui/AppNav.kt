package com.quickpool.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.gms.maps.model.LatLng
import com.quickpool.app.network.ApiClient
import com.quickpool.app.network.RecordDestinationDto
import com.quickpool.app.ui.components.BottomTab
import com.quickpool.app.ui.components.QuickPoolBottomBar
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val ACTIVITY = "activity"
    const val ALERTS = "alerts"
    const val ACCOUNT = "account"
    const val OFFER_RIDE = "offer_ride"
    const val LOCATION_SEARCH = "location_search/{purpose}"
    const val MAP_PICKER = "map_picker/{purpose}?lat={lat}&lng={lng}&label={label}"
    const val LIVE_LOCATION = "live_location/{rideId}/{isDriver}"

    fun locationSearch(purpose: String) = "location_search/$purpose"
    /** [seed] pre-centres the picker on a place chosen from search, so the pin marks it. */
    fun mapPicker(purpose: String, seed: PickedPlace? = null) =
        if (seed == null) "map_picker/$purpose"
        else "map_picker/$purpose?lat=${seed.latLng.latitude}" +
                "&lng=${seed.latLng.longitude}&label=${Uri.encode(seed.name)}"
    fun liveLocation(rideId: String, isDriver: Boolean) = "live_location/$rideId/$isDriver"
}

/** Which flow asked for a destination, so the result goes to the right place. */
private const val PURPOSE_FIND = "find"
private const val PURPOSE_OFFER = "offer"
private const val PURPOSE_OFFER_ORIGIN = "offer_origin"
private const val PURPOSE_SAVE = "save"

private val TAB_ROUTES = setOf(Routes.HOME, Routes.ACTIVITY, Routes.ALERTS, Routes.ACCOUNT)

@Composable
fun AppNav(
    startDestination: String,
    locationPermissionGranted: Boolean,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    var findDestination by remember { mutableStateOf<PickedPlace?>(null) }
    var offerDestination by remember { mutableStateOf<PickedPlace?>(null) }
    // null means "use current location", same default OfferRideScreen falls back to on its own.
    var offerOrigin by remember { mutableStateOf<PickedPlace?>(null) }
    var pendingSave by remember { mutableStateOf<PickedPlace?>(null) }
    var addressesVersion by remember { mutableIntStateOf(0) }
    var unreadCount by remember { mutableIntStateOf(0) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun refreshUnread() {
        scope.launch {
            unreadCount = runCatching {
                val response = ApiClient.notificationApi.unreadCount()
                if (response.isSuccessful) response.body()?.count ?: 0 else 0
            }.getOrDefault(0)
        }
    }

    // Remember the choice and feed the frequent/recent ranking.
    fun recordDestination(place: PickedPlace) {
        scope.launch {
            runCatching {
                ApiClient.userApi.recordDestination(
                    RecordDestinationDto(place.name, place.latLng.latitude, place.latLng.longitude)
                )
            }
        }
    }

    fun applyDestination(purpose: String?, place: PickedPlace) {
        when (purpose) {
            PURPOSE_SAVE -> pendingSave = place       // Account asked for a place to bookmark
            PURPOSE_OFFER -> { offerDestination = place; recordDestination(place) }
            PURPOSE_OFFER_ORIGIN -> offerOrigin = place   // not a "destination" — nothing to record
            else -> { findDestination = place; recordDestination(place) }
        }
    }

    pendingSave?.let { place ->
        SaveAddressDialog(
            place = place,
            onDismiss = { pendingSave = null },
            onSaved = {
                pendingSave = null
                addressesVersion++
            }
        )
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute in TAB_ROUTES) refreshUnread()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (currentRoute in TAB_ROUTES) {
                val selected = when (currentRoute) {
                    Routes.ACTIVITY -> BottomTab.ACTIVITY
                    Routes.ALERTS -> BottomTab.ALERTS
                    Routes.ACCOUNT -> BottomTab.ACCOUNT
                    else -> BottomTab.HOME
                }
                QuickPoolBottomBar(
                    selected = selected,
                    unreadCount = unreadCount,
                    onSelect = { tab ->
                        if (tab.route != currentRoute) {
                            navController.navigate(tab.route) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onOfferRide = { navController.navigate(Routes.OFFER_RIDE) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.LOGIN) {
                val context = androidx.compose.ui.platform.LocalContext.current
                LoginScreen(onLoginSuccess = { profileComplete ->
                    // The FCM token exists well before this point; this is the first moment
                    // there is a user to attach it to.
                    com.quickpool.app.notifications.DeviceRegistrar
                        .registerIfSignedIn(context)
                    val next = if (profileComplete) Routes.HOME else Routes.REGISTER
                    navController.navigate(next) { popUpTo(Routes.LOGIN) { inclusive = true } }
                })
            }
            composable(Routes.REGISTER) {
                RegisterScreen(onRegistered = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.REGISTER) { inclusive = true } }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    pickedDestination = findDestination,
                    locationPermissionGranted = locationPermissionGranted,
                    onSearchDestination = {
                        navController.navigate(Routes.locationSearch(PURPOSE_FIND))
                    },
                    onClearDestination = { findDestination = null }
                )
            }
            composable(Routes.OFFER_RIDE) {
                OfferRideScreen(
                    origin = offerOrigin,
                    onPickOrigin = {
                        navController.navigate(Routes.locationSearch(PURPOSE_OFFER_ORIGIN))
                    },
                    destination = offerDestination,
                    onPickDestination = {
                        navController.navigate(Routes.locationSearch(PURPOSE_OFFER))
                    },
                    onPosted = {
                        offerOrigin = null
                        offerDestination = null
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                Routes.LOCATION_SEARCH,
                arguments = listOf(navArgument("purpose") { type = NavType.StringType })
            ) { entry ->
                val purpose = entry.arguments?.getString("purpose")
                LocationSearchScreen(
                    onLocationPicked = { picked ->
                        applyDestination(purpose, picked)
                        navController.popBackStack()
                    },
                    onRefineOnMap = { seed ->
                        navController.navigate(Routes.mapPicker(purpose ?: PURPOSE_FIND, seed))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                Routes.MAP_PICKER,
                arguments = listOf(
                    navArgument("purpose") { type = NavType.StringType },
                    navArgument("lat") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("lng") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("label") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { entry ->
                val purpose = entry.arguments?.getString("purpose")
                val lat = entry.arguments?.getString("lat")?.toDoubleOrNull()
                val lng = entry.arguments?.getString("lng")?.toDoubleOrNull()
                val seed = if (lat != null && lng != null) {
                    PickedPlace(entry.arguments?.getString("label") ?: "Dropped pin", LatLng(lat, lng))
                } else null
                MapLocationPickerScreen(
                    initial = seed,
                    onLocationPicked = { picked ->
                        applyDestination(purpose, picked)
                        // Skip the search sheet on the way back.
                        val target = when (purpose) {
                            PURPOSE_OFFER, PURPOSE_OFFER_ORIGIN -> Routes.OFFER_RIDE
                            PURPOSE_SAVE -> Routes.ACCOUNT
                            else -> Routes.HOME
                        }
                        navController.popBackStack(target, inclusive = false)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.ACTIVITY) {
                ActivityScreen(
                    onStartRide = { rideId -> navController.navigate(Routes.liveLocation(rideId, true)) },
                    onTrackBooking = { rideId -> navController.navigate(Routes.liveLocation(rideId, false)) },
                    onDataChanged = { refreshUnread() }
                )
            }
            composable(Routes.ALERTS) {
                AlertsScreen(onNotificationsChanged = { refreshUnread() })
            }
            composable(Routes.ACCOUNT) {
                AccountScreen(
                    onLogout = {
                        onLogout()
                        navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                    },
                    addressesVersion = addressesVersion,
                    // Search first, so an address can come from search, the map, or GPS.
                    onAddAddress = { navController.navigate(Routes.locationSearch(PURPOSE_SAVE)) }
                )
            }
            composable(
                Routes.LIVE_LOCATION,
                arguments = listOf(
                    navArgument("rideId") { type = NavType.StringType },
                    navArgument("isDriver") { type = NavType.BoolType }
                )
            ) { entry ->
                val rideId = entry.arguments?.getString("rideId") ?: ""
                val isDriver = entry.arguments?.getBoolean("isDriver") ?: false
                LiveLocationScreen(rideId = rideId, isDriver = isDriver)
            }
        }
    }
}
