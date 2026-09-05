package com.quickpool.app.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.quickpool.app.network.ApiClient
import com.quickpool.app.network.EmergencyContactDto
import kotlinx.coroutines.launch

/**
 * Creates (or reuses) a public link for the trip and hands it to the OS share sheet,
 * so it can go to anyone — the recipient does not need a QuickPool account.
 */
@Composable
fun ShareTripDialog(
    rideOfferId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var link by remember { mutableStateOf<String?>(null) }
    var contact by remember { mutableStateOf<EmergencyContactDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(rideOfferId) {
        contact = runCatching {
            val r = ApiClient.userApi.emergencyContact()
            if (r.isSuccessful) r.body() else null
        }.getOrNull()

        try {
            val r = ApiClient.tripShareApi.share(rideOfferId)
            val body = r.body()
            if (r.isSuccessful && body != null) link = body.url
            else error = messageOf(r.code(), r.errorBody()?.string())
        } catch (e: Exception) {
            error = "Error: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share this trip") },
        text = {
            Column {
                when {
                    isLoading -> Text("Creating a link…")
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    else -> {
                        Text(
                            "Anyone with this link can follow the trip until it ends. " +
                                    "It expires on its own and shows no phone numbers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(link.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        contact?.let {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Emergency contact: ${it.name} (${it.phone})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = link != null,
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Follow my QuickPool trip: $link")
                    }
                    context.startActivity(Intent.createChooser(send, "Share trip"))
                    onDismiss()
                }
            ) { Text("Share") }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch { runCatching { ApiClient.tripShareApi.revoke(rideOfferId) } }
                onDismiss()
            }) { Text(if (link != null) "Stop sharing" else "Close") }
        }
    )
}
