package com.quickpool.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quickpool.app.R
import com.quickpool.app.ui.components.BackButton
import com.quickpool.app.ui.theme.AccentGreen
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.quickpool.app.data.CurrentLocationProvider
import kotlinx.coroutines.launch

/**
 * Drops a pin the user can drag the map under. When [initial] is set — the place they
 * just picked out of search — the camera opens there so the pin already marks it, and
 * the search result's own name is kept until they actually move the map. Re-geocoding
 * straight away would replace "Indira Gandhi International Airport" with a street line.
 */
@Composable
fun MapLocationPickerScreen(
    onLocationPicked: (PickedPlace) -> Unit,
    onBack: () -> Unit,
    initial: PickedPlace? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationProvider = remember { CurrentLocationProvider(context) }

    var label by remember { mutableStateOf(initial?.name) }
    var isResolving by remember { mutableStateOf(false) }
    // Until the map is dragged, a seeded pin keeps the name search gave it.
    var hasMoved by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            initial?.latLng ?: LatLng(28.6139, 77.2090),
            if (initial != null) 17f else 15f
        )
    }

    // With no seed, start over the user's own position when we can get it.
    LaunchedEffect(Unit) {
        if (initial != null) return@LaunchedEffect
        locationProvider.getCurrentLatLng()?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 16f))
        }
    }

    // Re-resolve the address every time the map settles.
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) {
            hasMoved = true
            isResolving = true
            return@LaunchedEffect
        }
        if (initial != null && !hasMoved) return@LaunchedEffect
        val target = cameraPositionState.position.target
        label = reverseGeocode(context, target)
        isResolving = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
        )

        // Fixed centre pin — the map moves underneath it, so the tip marks the target.
        Icon(
            painter = painterResource(R.drawable.ic_map_pin),
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-24).dp)
                .size(width = 36.dp, height = 48.dp)
        )

        BackButton(
            onClick = onBack,
            onSurface = false,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        )

        FloatingActionButton(
            onClick = {
                scope.launch {
                    locationProvider.getCurrentLatLng()?.let {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 16f))
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 180.dp)
                .size(44.dp)
        ) { Icon(Icons.Default.LocationOn, contentDescription = "My location") }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    if (initial != null && !hasMoved) "Drag the map to fine-tune the exact spot"
                    else "Move the map to set your destination",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    when {
                        isResolving -> "Locating…"
                        else -> label ?: "Dropped pin"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val target = cameraPositionState.position.target
                        onLocationPicked(PickedPlace(label ?: initial?.name ?: "Dropped pin", target))
                    },
                    enabled = !isResolving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Confirm location") }
            }
        }
    }
}
