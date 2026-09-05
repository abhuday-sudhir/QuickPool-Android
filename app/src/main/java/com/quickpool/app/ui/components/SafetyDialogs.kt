package com.quickpool.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quickpool.app.network.ApiClient
import com.quickpool.app.network.RateUserDto
import com.quickpool.app.network.ReportUserDto
import kotlinx.coroutines.launch

/**
 * Identifies "I already rated this person for this ride". The backend allows one
 * rating per (ride, rater, ratee), so that triple is the natural key — and with the
 * rater implied by the session, ride + ratee is enough.
 */
fun ratingKey(rideOfferId: String, rateeId: String) = "$rideOfferId|$rateeId"

/**
 * Ratings the signed-in user has already handed out. Screens use it to disable
 * "Rate" instead of letting the tap fail with a 409 on submit.
 */
suspend fun loadGivenRatings(): Set<String> = runCatching {
    val response = ApiClient.safetyApi.ratingsGiven()
    if (response.isSuccessful) {
        (response.body() ?: emptyList()).map { ratingKey(it.rideOfferId, it.rateeId) }.toSet()
    } else emptySet()
}.getOrDefault(emptySet())

/** Read-only star display, e.g. next to a driver's name. */
@Composable
fun RatingStars(rating: Double?, modifier: Modifier = Modifier) {
    if (rating == null) return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text("%.1f".format(rating), style = MaterialTheme.typography.labelSmall)
    }
}

/** Tappable 1-5 star picker, centred with even spacing and equal touch targets. */
@Composable
private fun StarPicker(value: Int, onChange: (Int) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            (1..5).forEach { star ->
                val filled = star <= value
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onChange(star) }
                ) {
                    Icon(
                        if (filled) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = "$star star${if (star == 1) "" else "s"}",
                        tint = if (filled) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (value == 0) "Tap a star to rate" else STAR_LABELS[value - 1],
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val STAR_LABELS = listOf("Poor", "Not great", "Okay", "Good", "Excellent")

@Composable
fun RateUserDialog(
    personName: String,
    rideOfferId: String,
    rateeId: String,
    onDismiss: () -> Unit,
    onRated: () -> Unit
) {
    var stars by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Rate $personName") },
        text = {
            Column {
                StarPicker(stars) { stars = it }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { if (it.length <= 500) comment = it },
                    label = { Text("Comment (optional)") },
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
                enabled = stars > 0 && !isSaving,
                onClick = {
                    isSaving = true
                    error = null
                    scope.launch {
                        try {
                            val response = ApiClient.safetyApi.rate(
                                RateUserDto(rideOfferId, rateeId, stars, comment.trim().ifBlank { null })
                            )
                            if (response.isSuccessful) onRated()
                            else error = messageOf(response.code(), response.errorBody()?.string())
                        } catch (e: Exception) {
                            error = "Error: ${e.message}"
                        } finally {
                            isSaving = false
                        }
                    }
                }
            ) { Text(if (isSaving) "Sending…" else "Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") } }
    )
}

private val REPORT_REASONS = listOf(
    "UNSAFE_DRIVING" to "Unsafe driving",
    "HARASSMENT" to "Harassment or abuse",
    "NO_SHOW" to "Did not show up",
    "WRONG_VEHICLE" to "Vehicle did not match",
    "OTHER" to "Something else"
)

@Composable
fun ReportUserDialog(
    personName: String,
    reportedId: String,
    rideOfferId: String?,
    onDismiss: () -> Unit,
    onReported: () -> Unit
) {
    var reason by remember { mutableStateOf(REPORT_REASONS.first().first) }
    var details by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Report $personName") },
        text = {
            Column {
                REPORT_REASONS.forEach { (code, label) ->
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { reason = code }
                    ) {
                        RadioButton(selected = reason == code, onClick = { reason = code })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 1000) details = it },
                    label = { Text("What happened? (optional)") },
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
                enabled = !isSaving,
                onClick = {
                    isSaving = true
                    error = null
                    scope.launch {
                        try {
                            val response = ApiClient.safetyApi.report(
                                ReportUserDto(reportedId, rideOfferId, reason, details.trim().ifBlank { null })
                            )
                            if (response.isSuccessful) onReported()
                            else error = messageOf(response.code(), response.errorBody()?.string())
                        } catch (e: Exception) {
                            error = "Error: ${e.message}"
                        } finally {
                            isSaving = false
                        }
                    }
                }
            ) { Text(if (isSaving) "Sending…" else "Report") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") } }
    )
}

/** Overflow menu shared by anywhere another person is shown. */
@Composable
fun PersonActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Report") }, onClick = { onDismiss(); onReport() })
        DropdownMenuItem(text = { Text("Block") }, onClick = { onDismiss(); onBlock() })
    }
}

internal fun messageOf(code: Int, body: String?): String {
    val detail = body?.takeIf { it.isNotBlank() }?.let {
        Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1)
    }
    return detail ?: "Request failed ($code)"
}
