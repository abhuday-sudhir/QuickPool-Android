package com.quickpool.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.quickpool.app.data.CurrentLocationProvider
import com.quickpool.app.network.ApiClient
import com.quickpool.app.network.CreateRideOfferDto
import com.quickpool.app.ui.components.BackButton
import kotlinx.coroutines.launch

@Composable
fun OfferRideScreen(
    origin: PickedPlace?,
    onPickOrigin: () -> Unit,
    destination: PickedPlace?,
    onPickDestination: () -> Unit,
    onPosted: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val locationProvider = remember { CurrentLocationProvider(context) }
    val scope = rememberCoroutineScope()

    // Falls back to GPS the moment the screen opens; a picked [origin] overrides it.
    var gpsOrigin by remember { mutableStateOf<LatLng?>(null) }
    val effectiveOrigin = origin?.latLng ?: gpsOrigin
    var departureTime by remember { mutableStateOf("") }
    var seats by remember { mutableIntStateOf(2) }
    var price by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { gpsOrigin = locationProvider.getCurrentLatLng() }

    fun post() {
        val from = effectiveOrigin ?: return
        val to = destination ?: return
        isPosting = true
        message = null
        scope.launch {
            try {
                val response = ApiClient.rideApi.createRideOffer(
                    CreateRideOfferDto(
                        originLat = from.latitude,
                        originLng = from.longitude,
                        destinationLat = to.latLng.latitude,
                        destinationLng = to.latLng.longitude,
                        departureTime = departureTime,
                        seatsTotal = seats,
                        pricePerSeat = price.toDoubleOrNull()
                    )
                )
                if (response.isSuccessful) onPosted()
                else message = apiErrorText(response.code(), response.errorBody()?.string())
            } catch (e: Exception) {
                message = "Error: ${e.message}"
            } finally {
                isPosting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onClick = onBack)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Offer a ride", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "You're driving anyway — take someone along.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            onClick = onPickOrigin,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "From",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        origin?.name
                            ?: if (gpsOrigin != null) "Your current location" else "Locating…",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Change pickup point",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Surface(
            onClick = onPickDestination,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "To",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        destination?.name ?: "Choose destination",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (destination != null) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
                Icon(
                    if (destination == null) Icons.Default.Search else Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        DateTimePickerField(
            label = "Departure",
            placeholder = "Tap to pick date & time",
            value = departureTime,
            onValueChange = { departureTime = it }
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Seats offered",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("$seats", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                FilledTonalIconButton(onClick = { if (seats > 1) seats-- }, enabled = seats > 1) {
                    Text("−", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalIconButton(onClick = { if (seats < 6) seats++ }, enabled = seats < 6) {
                    Text("+", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        OutlinedTextField(
            value = price,
            onValueChange = { price = it },
            label = { Text("Price per seat (optional)") },
            prefix = { Text("₹") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { post() },
            enabled = !isPosting && destination != null && effectiveOrigin != null && departureTime.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (isPosting) "Posting…" else "Post ride")
        }

        message?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
