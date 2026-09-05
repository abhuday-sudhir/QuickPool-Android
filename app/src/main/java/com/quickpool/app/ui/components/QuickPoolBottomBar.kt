package com.quickpool.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Default.Home),
    ACTIVITY("activity", "Activity", Icons.Default.List),
    ALERTS("alerts", "Alerts", Icons.Default.Notifications),
    ACCOUNT("account", "Account", Icons.Default.Person)
}

private val BAR_HEIGHT = 62.dp
private val FAB_SIZE = 58.dp
private val NOTCH_RADIUS = 39.dp

/** Bar with a semicircle scooped out of its top edge so the raised + sits inside it. */
private class NotchedBarShape(private val notchRadius: Dp) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { notchRadius.toPx() }
        val cx = size.width / 2f
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(cx - r, 0f)
            // Sweeping -180° from the left edge dips the arc downward into the bar.
            arcTo(
                rect = Rect(left = cx - r, top = -r, right = cx + r, bottom = r),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun QuickPoolBottomBar(
    selected: BottomTab,
    unreadCount: Int,
    onSelect: (BottomTab) -> Unit,
    onOfferRide: () -> Unit
) {
    // Extra height above the bar so the + can overhang without being clipped.
    // navigationBarsPadding sits *inside* the Surface so its colour reaches the screen edge.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT + FAB_SIZE / 2)
            .navigationBarsPadding()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = NotchedBarShape(NOTCH_RADIUS),
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BAR_HEIGHT)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomTabItem(BottomTab.HOME, selected == BottomTab.HOME, 0, Modifier.weight(1f)) {
                    onSelect(BottomTab.HOME)
                }
                BottomTabItem(BottomTab.ACTIVITY, selected == BottomTab.ACTIVITY, 0, Modifier.weight(1f)) {
                    onSelect(BottomTab.ACTIVITY)
                }
                // Gap for the notch.
                Spacer(modifier = Modifier.width(NOTCH_RADIUS * 2))
                BottomTabItem(BottomTab.ALERTS, selected == BottomTab.ALERTS, unreadCount, Modifier.weight(1f)) {
                    onSelect(BottomTab.ALERTS)
                }
                BottomTabItem(BottomTab.ACCOUNT, selected == BottomTab.ACCOUNT, 0, Modifier.weight(1f)) {
                    onSelect(BottomTab.ACCOUNT)
                }
            }
        }

        FloatingActionButton(
            onClick = onOfferRide,
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.align(Alignment.TopCenter).size(FAB_SIZE)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Offer a ride", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: BottomTab,
    isSelected: Boolean,
    badgeCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        if (badgeCount > 0) {
            BadgedBox(badge = {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) { Text(if (badgeCount > 99) "99+" else badgeCount.toString()) }
            }) {
                Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(23.dp))
            }
        } else {
            Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(23.dp))
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            tab.label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
