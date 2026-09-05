package com.quickpool.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quickpool.app.network.ImpactDto

/**
 * Shows what the user's shared seats have saved. Figures are estimates
 * (0.171 kg CO2/km avoided, 21 kg absorbed per tree per year) and say so.
 */
@Composable
fun ImpactCard(impact: ImpactDto, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Your impact",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                if (impact.sharedRides == 0)
                    "Share your first ride to start saving CO₂."
                else
                    "${impact.sharedRides} shared seat${if (impact.sharedRides == 1) "" else "s"} · ${trim(impact.sharedKm)} km",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Stat("CO₂ avoided", "${trim(impact.co2SavedKg)} kg", Modifier.weight(1f))
                Stat("Trees' yearly work", trim(impact.treesEquivalent), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Estimated from distance shared.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun trim(v: Double): String = when {
    v == v.toLong().toDouble() -> v.toLong().toString()
    v < 1.0 -> "%.2f".format(v)
    else -> "%.1f".format(v)
}
