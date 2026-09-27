package com.quickpool.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quickpool.app.network.ApiClient
import com.quickpool.app.network.NotificationDto
import com.quickpool.app.ui.components.LoadMoreRow
import kotlinx.coroutines.launch

@Composable
fun AlertsScreen(onNotificationsChanged: () -> Unit) {
    var items by remember { mutableStateOf<List<NotificationDto>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var hasNext by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val response = ApiClient.notificationApi.list(page = 0)
            if (response.isSuccessful) {
                val body = response.body()
                items = body?.content ?: emptyList()
                hasNext = body?.hasNext ?: false
                page = 0
                errorMessage = null
            } else {
                errorMessage = "Couldn't load alerts (${response.code()})"
            }
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    suspend fun loadMore() {
        if (isLoadingMore || !hasNext) return
        isLoadingMore = true
        try {
            val response = ApiClient.notificationApi.list(page = page + 1)
            if (response.isSuccessful) {
                val body = response.body()
                items = items + (body?.content ?: emptyList())
                hasNext = body?.hasNext ?: false
                page += 1
            }
        } catch (_: Exception) {
            // A failed "load more" leaves the list as-is; the button just stays put to retry.
        } finally {
            isLoadingMore = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Alerts",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (items.any { !it.read }) {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { ApiClient.notificationApi.markAllRead() }
                        load()
                        onNotificationsChanged()
                    }
                }) { Text("Mark all read") }
            }
        }

        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface) }

            errorMessage != null -> Text(
                errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            items.isEmpty() -> Text(
                "Nothing yet. Booking requests and ride updates will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { n ->
                    NotificationRow(n) {
                        scope.launch {
                            if (!n.read) {
                                runCatching { ApiClient.notificationApi.markRead(n.id) }
                                load()
                                onNotificationsChanged()
                            }
                        }
                    }
                }
                if (hasNext) {
                    item {
                        LoadMoreRow(isLoading = isLoadingMore) { scope.launch { loadMore() } }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: com.quickpool.app.network.NotificationDto, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (n.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (n.read) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.secondary
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    n.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (n.read) FontWeight.Medium else FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    n.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    formatRelative(n.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
