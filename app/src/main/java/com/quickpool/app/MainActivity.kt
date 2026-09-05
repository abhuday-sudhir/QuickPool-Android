package com.quickpool.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.quickpool.app.data.TokenStore
import com.quickpool.app.ui.AppNav
import com.quickpool.app.ui.Routes
import com.quickpool.app.ui.theme.QuickPoolTheme
import com.quickpool.app.network.ApiClient
import kotlinx.coroutines.launch


/**
 * Asks the backend whether registration was finished. On any failure we assume it was,
 * so a flaky network sends the user Home rather than trapping them in registration.
 */
private suspend fun profileIncomplete(): Boolean = runCatching {
    val response = ApiClient.userApi.me()
    response.isSuccessful && response.body()?.profileComplete == false
}.getOrDefault(false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ApiClient.tokenStore = TokenStore(applicationContext)
        // BitmapDescriptorFactory (used for our custom map pins) throws unless the
        // Maps SDK has been initialised first.
        com.google.android.gms.maps.MapsInitializer.initialize(applicationContext)
        if (!com.google.android.libraries.places.api.Places.isInitialized()) {
            com.google.android.libraries.places.api.Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }
        setContent {
            QuickPoolTheme {
                var locationPermissionGranted by remember { mutableStateOf(false) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }

                // No outer Scaffold: AppNav owns the Scaffold so its bottom bar can paint
                // all the way to the screen edge instead of leaving a strip below it.
                Box(modifier = Modifier.fillMaxSize()) {
                    var startDestination by remember { mutableStateOf<String?>(null) }
                    val tokenStore = remember { TokenStore(applicationContext) }

                    LaunchedEffect(Unit) {
                        tokenStore.loadIntoMemory()
                        val existingToken = tokenStore.getAccessToken()
                        startDestination = when {
                            existingToken == null -> Routes.LOGIN
                            // Signed in but never finished registering.
                            profileIncomplete() -> Routes.REGISTER
                            else -> Routes.HOME
                        }
                    }

                    val scope = rememberCoroutineScope()
                    startDestination?.let {
                        AppNav(
                            startDestination = it,
                            locationPermissionGranted = locationPermissionGranted,
                            onLogout = {
                                scope.launch {
                                    tokenStore.clear()
                                    com.quickpool.app.data.TokenHolder.accessToken = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}