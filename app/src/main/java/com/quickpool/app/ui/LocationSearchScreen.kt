package com.quickpool.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.quickpool.app.data.CurrentLocationProvider
import com.quickpool.app.network.ApiClient
import com.quickpool.app.network.DestinationDto
import com.quickpool.app.network.SaveAddressDto
import com.quickpool.app.network.SavedAddressDto
import com.quickpool.app.ui.components.BackButton
import kotlinx.coroutines.launch

data class PickedPlace(val name: String, val latLng: LatLng)

/**
 * [onRefineOnMap] hands the choice to the map picker so the pin lands on the place and
 * the user can nudge it to the exact gate/door. A null argument means "no starting
 * point" — the plain "Choose on map" entry. Saved places and the GPS fix return
 * straight away; those are already exact.
 */
@Composable
fun LocationSearchScreen(
    onLocationPicked: (PickedPlace) -> Unit,
    onRefineOnMap: (PickedPlace?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val placesClient = remember { Places.createClient(context) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var predictions by remember {
        mutableStateOf<List<com.google.android.libraries.places.api.model.AutocompletePrediction>>(emptyList())
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<List<SavedAddressDto>>(emptyList()) }
    var recent by remember { mutableStateOf<List<DestinationDto>>(emptyList()) }
    var savingPlace by remember { mutableStateOf<PickedPlace?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    val sessionToken = remember { AutocompleteSessionToken.newInstance() }
    val locationProvider = remember { CurrentLocationProvider(context) }

    suspend fun loadLists() {
        saved = runCatching {
            val r = ApiClient.userApi.savedAddresses()
            if (r.isSuccessful) r.body() ?: emptyList() else emptyList()
        }.getOrDefault(emptyList())
        recent = runCatching {
            val r = ApiClient.userApi.recentDestinations()
            if (r.isSuccessful) r.body() ?: emptyList() else emptyList()
        }.getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) {
        loadLists()
        focusRequester.requestFocus()
    }

    fun search(text: String) {
        query = text
        errorMessage = null
        if (text.isBlank()) {
            predictions = emptyList()
            return
        }
        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(sessionToken)
            .setQuery(text)
            .build()
        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response -> predictions = response.autocompletePredictions }
            .addOnFailureListener { e ->
                predictions = emptyList()
                errorMessage = "Search unavailable: ${e.message}"
            }
    }

    fun pick(placeId: String, name: String) {
        val fields = listOf(Place.Field.LOCATION, Place.Field.DISPLAY_NAME)
        placesClient.fetchPlace(FetchPlaceRequest.newInstance(placeId, fields))
            .addOnSuccessListener { response ->
                response.place.location?.let {
                    onRefineOnMap(PickedPlace(response.place.displayName ?: name, it))
                }
            }
            .addOnFailureListener { e -> errorMessage = "Couldn't load that place: ${e.message}" }
    }

    savingPlace?.let { place ->
        SaveAddressDialog(
            place = place,
            onDismiss = { savingPlace = null },
            onSaved = {
                savingPlace = null
                scope.launch { loadLists() }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onBack)
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { search(it) },
                placeholder = { Text("Search destination") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).focusRequester(focusRequester)
            )
        }
        errorMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        ListItem(
            headlineContent = { Text("Use my current location", fontWeight = FontWeight.SemiBold) },
            supportingContent = {
                Text(if (isLocating) "Getting your location…" else "Pick where you are right now")
            },
            leadingContent = {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            },
            modifier = Modifier.clickable(enabled = !isLocating) {
                isLocating = true
                scope.launch {
                    val here = locationProvider.getCurrentLatLng()
                    isLocating = false
                    if (here == null) {
                        errorMessage = "Couldn't get your location. Check GPS and permissions."
                    } else {
                        onLocationPicked(PickedPlace(reverseGeocode(context, here), here))
                    }
                }
            }
        )
        ListItem(
            headlineContent = { Text("Choose on map", fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text("Drop a pin exactly where you want") },
            leadingContent = {
                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            },
            modifier = Modifier.clickable { onRefineOnMap(null) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LazyColumn {
            if (query.isBlank()) {
                if (saved.isNotEmpty()) {
                    item { SectionHeader("Saved places") }
                    items(saved) { address ->
                        ListItem(
                            headlineContent = { Text(address.label, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(address.name, maxLines = 1) },
                            leadingContent = {
                                Icon(
                                    addressIcon(address.label),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            modifier = Modifier.clickable {
                                onLocationPicked(PickedPlace(address.name, LatLng(address.lat, address.lng)))
                            }
                        )
                    }
                }

                if (recent.isNotEmpty()) {
                    item { SectionHeader("Recent") }
                    items(recent) { dest ->
                        ListItem(
                            headlineContent = { Text(dest.name, maxLines = 1) },
                            supportingContent = {
                                Text("Used ${dest.useCount} time${if (dest.useCount == 1) "" else "s"}")
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    savingPlace = PickedPlace(dest.name, LatLng(dest.lat, dest.lng))
                                }) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Save this address",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                onLocationPicked(PickedPlace(dest.name, LatLng(dest.lat, dest.lng)))
                            }
                        )
                    }
                }
            }

            items(predictions) { prediction ->
                ListItem(
                    headlineContent = { Text(prediction.getPrimaryText(null).toString()) },
                    supportingContent = { Text(prediction.getSecondaryText(null).toString()) },
                    leadingContent = {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.clickable {
                        pick(prediction.placeId, prediction.getPrimaryText(null).toString())
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

/**
 * Saved addresses beyond Home and Work get their own icon, so a list of five places
 * isn't five house icons.
 */
fun addressIcon(label: String): ImageVector =
    if (label.equals("Home", ignoreCase = true)) Icons.Default.Home else Icons.Default.Place

/**
 * Asks for a label before saving a place to the user's address book. Home and Work are
 * one tap; "Other" opens the field for a name of the user's own ("Gym", "Mum's"), which
 * also keeps it from colliding — the backend overwrites a saved address of the same label.
 */
@Composable
fun SaveAddressDialog(
    place: PickedPlace,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    // "Other" is a mode, not a label: it hands the naming over to the text field.
    var customLabel by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val labelFocus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Save address") },
        text = {
            Column {
                Text(place.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Home", "Work").forEach { preset ->
                        FilterChip(
                            selected = !customLabel && label == preset,
                            onClick = {
                                customLabel = false
                                label = preset
                            },
                            label = { Text(preset) }
                        )
                    }
                    FilterChip(
                        selected = customLabel,
                        onClick = {
                            customLabel = true
                            label = ""
                        },
                        label = { Text("Other") }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        if (it.length <= 40) {
                            label = it
                            // Typing over a preset is itself a custom label.
                            customLabel = !it.equals("Home", true) && !it.equals("Work", true)
                        }
                    },
                    label = { Text(if (customLabel) "Name this place" else "Label") },
                    placeholder = { Text("Gym, College, Mum's…") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().focusRequester(labelFocus)
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                LaunchedEffect(customLabel) {
                    if (customLabel) runCatching { labelFocus.requestFocus() }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && !isSaving,
                onClick = {
                    isSaving = true
                    error = null
                    scope.launch {
                        try {
                            val response = ApiClient.userApi.saveAddress(
                                SaveAddressDto(
                                    label.trim(), place.name,
                                    place.latLng.latitude, place.latLng.longitude
                                )
                            )
                            if (response.isSuccessful) onSaved()
                            else error = apiErrorText(response.code(), response.errorBody()?.string())
                        } catch (e: Exception) {
                            error = "Error: ${e.message}"
                        } finally {
                            isSaving = false
                        }
                    }
                }
            ) { Text(if (isSaving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") } }
    )
}
