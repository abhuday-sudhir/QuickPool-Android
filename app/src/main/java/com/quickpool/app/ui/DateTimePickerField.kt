package com.quickpool.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Now, rounded up to the next quarter hour — the earliest sensible departure. */
private fun defaultDeparture(): LocalDateTime {
    val now = LocalDateTime.now().withSecond(0).withNano(0)
    return now.plusMinutes((15 - now.minute % 15).toLong())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showTimeKeyboard by remember { mutableStateOf(false) }

    // Open on today / the next quarter hour rather than an empty picker.
    val initial = remember { defaultDeparture() }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    val timePickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        // Material3 1.3.1 ships no TimePickerDialog, so the dial is hand-hosted.
        // usePlatformDefaultWidth = false is required: the platform dialog width
        // is narrower than the dial's ~256dp face plus its period selector, so
        // the selector ended up drawn over the clock.
        val configuration = LocalConfiguration.current
        // Vertical stacks AM/PM above the dial and needs the height for it;
        // landscape has none, so fall back to the side-by-side layout, which now
        // has the width it always needed.
        val layout = if (configuration.screenHeightDp < 480) {
            TimePickerLayoutType.Horizontal
        } else {
            TimePickerLayoutType.Vertical
        }
        Dialog(
            onDismissRequest = { showTimePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    // Without usePlatformDefaultWidth the dialog would otherwise
                    // stretch the full width of a landscape screen.
                    .widthIn(max = 560.dp)
                    .heightIn(max = (configuration.screenHeightDp - 32).dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (showTimeKeyboard) "Enter time" else "Select time",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // The picker scrolls if it still cannot fit; the action row
                    // below stays outside the scroll so Done is always reachable.
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (showTimeKeyboard) {
                            TimeInput(state = timePickerState)
                        } else {
                            TimePicker(state = timePickerState, layoutType = layout)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Escape hatch: a cramped screen can always type the time.
                        TextButton(onClick = { showTimeKeyboard = !showTimeKeyboard }) {
                            Text(if (showTimeKeyboard) "Use dial" else "Enter time")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            showTimePicker = false
                            val date = Instant
                                .ofEpochMilli(datePickerState.selectedDateMillis ?: System.currentTimeMillis())
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            val dateTime = LocalDateTime.of(
                                date.year, date.month, date.dayOfMonth,
                                timePickerState.hour, timePickerState.minute
                            )
                            onValueChange(dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        }) { Text("Done") }
                    }
                }
            }
        }
    }

    Surface(
        onClick = { showDatePicker = true },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (value.isNotEmpty()) {
                Text(
                    formatDeparture(value),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
