package com.quickpool.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quickpool.app.network.ApiClient
import com.quickpool.app.network.EmergencyContactDto
import com.quickpool.app.network.ImpactDto
import com.quickpool.app.network.VerifyEmailDto
import com.quickpool.app.network.SaveVehicleDto
import com.quickpool.app.network.SavedAddressDto
import com.quickpool.app.network.VehicleDto
import com.quickpool.app.network.UpdateProfileDto
import com.quickpool.app.network.UserResponseDto
import com.quickpool.app.ui.components.ImpactCard
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    onLogout: () -> Unit,
    addressesVersion: Int,
    onAddAddress: () -> Unit
) {
    var profile by remember { mutableStateOf<UserResponseDto?>(null) }
    var addresses by remember { mutableStateOf<List<SavedAddressDto>>(emptyList()) }
    var impact by remember { mutableStateOf<ImpactDto?>(null) }
    var vehicle by remember { mutableStateOf<VehicleDto?>(null) }
    var editingVehicle by remember { mutableStateOf(false) }
    var contact by remember { mutableStateOf<EmergencyContactDto?>(null) }
    var editingContact by remember { mutableStateOf(false) }
    var verifyingEmail by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun loadVehicle() {
        vehicle = runCatching {
            val r = ApiClient.userApi.myVehicle()
            if (r.isSuccessful) r.body() else null
        }.getOrNull()
    }

    suspend fun loadContact() {
        contact = runCatching {
            val r = ApiClient.userApi.emergencyContact()
            if (r.isSuccessful) r.body() else null
        }.getOrNull()
    }

    suspend fun refreshProfile() {
        runCatching {
            val r = ApiClient.userApi.me()
            if (r.isSuccessful) profile = r.body()
        }
    }

    suspend fun loadAddresses() {
        addresses = runCatching {
            val r = ApiClient.userApi.savedAddresses()
            if (r.isSuccessful) r.body() ?: emptyList() else emptyList()
        }.getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) {
        try {
            val response = ApiClient.userApi.me()
            if (response.isSuccessful) profile = response.body()
            else errorMessage = "Couldn't load account (${response.code()})"
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
        } finally {
            isLoading = false
        }
        impact = runCatching {
            val r = ApiClient.userApi.impact()
            if (r.isSuccessful) r.body() else null
        }.getOrNull()
    }

    // Reloads when an address is added from the map picker.
    LaunchedEffect(addressesVersion) { loadAddresses() }
    LaunchedEffect(Unit) { loadVehicle() }
    LaunchedEffect(Unit) { loadContact() }

    if (verifyingEmail) {
        VerifyEmailDialog(
            email = profile?.email.orEmpty(),
            onDismiss = { verifyingEmail = false },
            onVerified = {
                verifyingEmail = false
                // The "✓ Email verified" row is the confirmation; a banner too is noise.
                scope.launch { refreshProfile() }
            }
        )
    }

    if (editingContact) {
        EditEmergencyContactDialog(
            existing = contact,
            onDismiss = { editingContact = false },
            onSaved = { saved ->
                contact = saved
                editingContact = false
            }
        )
    }

    if (confirmingDelete) {
        DeleteAccountDialog(
            onDismiss = { confirmingDelete = false },
            onDeleted = { confirmingDelete = false; onLogout() },
            onError = { msg -> confirmingDelete = false; banner = msg }
        )
    }

    if (editingVehicle) {
        EditVehicleDialog(
            existing = vehicle,
            onDismiss = { editingVehicle = false },
            onSaved = { saved ->
                vehicle = saved
                editingVehicle = false
            }
        )
    }

    if (editing) {
        val current = profile
        EditProfileDialog(
            initialName = current?.name.orEmpty(),
            initialEmail = current?.email.orEmpty(),
            onDismiss = { editing = false },
            onSaved = { updated ->
                profile = updated
                editing = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Account", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))

        when {
            isLoading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
            errorMessage != null -> Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error)
            profile != null -> ProfileCard(profile!!)
        }

        profile?.let { p ->
            if (!p.email.isNullOrBlank() && !p.emailVerified) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Email not verified",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Verify it so we can send you ride updates.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            scope.launch {
                                val r = runCatching { ApiClient.userApi.requestEmailCode() }.getOrNull()
                                if (r != null && r.isSuccessful) verifyingEmail = true
                                else banner = "Couldn't send a code right now."
                            }
                        }) { Text("Verify") }
                    }
                }
            } else if (p.emailVerified) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "✓ Email verified",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { editing = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Edit profile") }
        }

        banner?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }

        impact?.let {
            Spacer(modifier = Modifier.height(20.dp))
            ImpactCard(it)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your vehicle",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { editingVehicle = true }) {
                Text(if (vehicle == null) "Add" else "Edit")
            }
        }
        if (vehicle == null) {
            Text(
                "Add your car so passengers know what to look for.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${vehicle!!.color} ${vehicle!!.make} ${vehicle!!.model}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            vehicle!!.plate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            runCatching { ApiClient.userApi.deleteVehicle() }
                            loadVehicle()
                        }
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove vehicle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Saved addresses",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAddAddress) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        }

        if (addresses.isEmpty()) {
            Text(
                "Save Home and Work to pick them in one tap.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                addresses.forEach { address ->
                    AddressRow(address, onDelete = {
                        scope.launch {
                            runCatching { ApiClient.userApi.deleteAddress(address.id) }
                            loadAddresses()
                        }
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Emergency contact",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { editingContact = true }) {
                Text(if (contact == null) "Add" else "Edit")
            }
        }
        if (contact == null) {
            Text(
                "Someone we can point to your live trip if you share it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            contact!!.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            contact!!.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            runCatching { ApiClient.userApi.deleteEmergencyContact() }
                            loadContact()
                        }
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove emergency contact",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = {
                // Revoke the refresh token server-side, not just on this device.
                scope.launch {
                    runCatching { ApiClient.userApi.logout() }
                    onLogout()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log out")
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = { confirmingDelete = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete my account", color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AddressRow(address: SavedAddressDto, onDelete: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                addressIcon(address.label),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(address.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    address.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete ${address.label}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: UserResponseDto) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (profile.name?.firstOrNull() ?: profile.phone.lastOrNull() ?: '?').toString().uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.surface
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    profile.name?.takeIf { it.isNotBlank() } ?: "QuickPool user",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    profile.phone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                profile.email?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                profile.ratingAvg?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "★ %.1f".format(it),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    initialName: String,
    initialEmail: String,
    onDismiss: () -> Unit,
    onSaved: (UserResponseDto) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val nameValid = Validation.isNameValid(name)
    val emailValid = Validation.isEmailValid(email)

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Edit profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    label = { Text("Full name") },
                    singleLine = true,
                    isError = name.isNotEmpty() && !nameValid,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { if (it.length <= 160) email = it },
                    label = { Text("Email") },
                    supportingText = { Text("Used for ride updates and offers") },
                    singleLine = true,
                    isError = email.isNotEmpty() && !emailValid,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nameValid && emailValid && !isSaving,
                onClick = {
                    isSaving = true
                    error = null
                    scope.launch {
                        try {
                            val response = ApiClient.userApi.updateProfile(
                                UpdateProfileDto(name.trim(), email.trim())
                            )
                            val body = response.body()
                            if (response.isSuccessful && body != null) onSaved(body)
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

@Composable
private fun EditVehicleDialog(
    existing: VehicleDto?,
    onDismiss: () -> Unit,
    onSaved: (VehicleDto) -> Unit
) {
    var make by remember { mutableStateOf(existing?.make.orEmpty()) }
    var model by remember { mutableStateOf(existing?.model.orEmpty()) }
    var color by remember { mutableStateOf(existing?.color.orEmpty()) }
    var plate by remember { mutableStateOf(existing?.plate.orEmpty()) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val valid = Validation.isVehicleValid(make, model, color, plate)

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Your vehicle") },
        text = {
            Column {
                Text(
                    "Passengers see this so they can find your car.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = make, onValueChange = { if (it.length <= 40) make = it },
                    label = { Text("Make (e.g. Maruti)") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = model, onValueChange = { if (it.length <= 40) model = it },
                    label = { Text("Model (e.g. Swift)") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = color, onValueChange = { if (it.length <= 24) color = it },
                    label = { Text("Colour") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = plate, onValueChange = { if (it.length <= 16) plate = it },
                    label = { Text("Number plate") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !isSaving,
                onClick = {
                    isSaving = true
                    error = null
                    scope.launch {
                        try {
                            val response = ApiClient.userApi.saveVehicle(
                                SaveVehicleDto(make.trim(), model.trim(), color.trim(), plate.trim())
                            )
                            val body = response.body()
                            if (response.isSuccessful && body != null) onSaved(body)
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

@Composable
private fun VerifyEmailDialog(
    email: String,
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Verify your email") },
        text = {
            Column {
                Text(
                    "We sent a 6-digit code to $email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Code") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Dev build: the code is printed in the backend console.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.length == 6 && !isSaving,
                onClick = {
                    isSaving = true
                    error = null
                    scope.launch {
                        try {
                            val r = ApiClient.userApi.confirmEmail(VerifyEmailDto(code))
                            if (r.isSuccessful) onVerified()
                            else error = apiErrorText(r.code(), r.errorBody()?.string())
                        } catch (e: Exception) {
                            error = "Error: ${e.message}"
                        } finally {
                            isSaving = false
                        }
                    }
                }
            ) { Text(if (isSaving) "Checking…" else "Verify") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") } }
    )
}

@Composable
private fun EditEmergencyContactDialog(
    existing: EmergencyContactDto?,
    onDismiss: () -> Unit,
    onSaved: (EmergencyContactDto) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    // Stored in international format; the field shows it as typed.
    var phone by remember { mutableStateOf(existing?.phone.orEmpty().removePrefix("+91")) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val valid = Validation.isNameValid(name) && Validation.isNationalNumberValid(phone, 10)

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Emergency contact") },
        text = {
            Column {
                Text(
                    "Shown to you when you share a trip, so you can send them the link fast.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() }.take(10) },
                    label = { Text("Phone") },
                    prefix = { Text("+91 ") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !isSaving,
                onClick = {
                    isSaving = true
                    error = null
                    scope.launch {
                        try {
                            val dto = EmergencyContactDto(name.trim(), Validation.toE164("+91", phone))
                            val r = ApiClient.userApi.saveEmergencyContact(dto)
                            val body = r.body()
                            if (r.isSuccessful && body != null) onSaved(body)
                            else error = apiErrorText(r.code(), r.errorBody()?.string())
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

@Composable
private fun DeleteAccountDialog(
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    onError: (String) -> Unit
) {
    var isDeleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text("Delete your account?") },
        text = {
            Text(
                "Your name, email, phone number, vehicle and saved places are erased and " +
                        "you're signed out everywhere. Past rides stay in the other person's " +
                        "history, but nothing links them to you. This cannot be undone."
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isDeleting,
                onClick = {
                    isDeleting = true
                    scope.launch {
                        try {
                            val r = ApiClient.userApi.deleteAccount()
                            if (r.isSuccessful) onDeleted()
                            else onError(apiErrorText(r.code(), r.errorBody()?.string()))
                        } catch (e: Exception) {
                            onError("Error: ${e.message}")
                        } finally {
                            isDeleting = false
                        }
                    }
                }
            ) {
                Text(
                    if (isDeleting) "Deleting…" else "Delete",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Keep my account") } }
    )
}
