package com.watchutil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.watchutil.MainViewModel
import com.watchutil.Screen
import com.watchutil.UiState
import com.watchutil.core.Backend
import com.watchutil.core.ServiceEntry
import com.watchutil.core.ServiceState
import java.util.Locale

@Composable
fun WatchUtilApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Only poll the device while the UI is on screen; this avoids waking the
    // bridge (and the CPU) every two seconds in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setVisible(true)
                Lifecycle.Event.ON_STOP -> viewModel.setVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    WatchUtilTheme {
        when (state.screen) {
            Screen.DASHBOARD -> DashboardScreen(
                state = state,
                onManageServices = { viewModel.navigate(Screen.SERVICES) },
                onRefreshBackend = { viewModel.refreshBackend() },
                onRequestReboot = { viewModel.navigate(Screen.CONFIRM_REBOOT) },
            )

            Screen.SERVICES -> ServicesScreen(
                state = state,
                onBack = { viewModel.navigate(Screen.DASHBOARD) },
                onRefresh = { viewModel.loadServices() },
                onToggle = { viewModel.toggleService(it) },
            )

            Screen.CONFIRM_REBOOT -> RebootConfirmScreen(
                onCancel = { viewModel.navigate(Screen.DASHBOARD) },
                onConfirm = {
                    viewModel.reboot()
                    viewModel.navigate(Screen.DASHBOARD)
                },
            )
        }
    }
}

/**
 * Shared scaffold. [ScreenScaffold] centers content vertically and applies the
 * system insets, and [ScalingLazyColumn] shrinks and fades items near the
 * curved edges. Together they make the same layout legible on a round watch and
 * on a square one without branching on screen shape.
 */
@Composable
private fun WatchScreen(
    content: ScalingLazyListScope.() -> Unit,
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun DashboardScreen(
    state: UiState,
    onManageServices: () -> Unit,
    onRefreshBackend: () -> Unit,
    onRequestReboot: () -> Unit,
) {
    WatchScreen {
        item { TimeText() }
        item { ListHeader { Text("WatchUtil") } }

        item {
            StatCard(
                title = "CPU",
                value = "${state.stats.cpuPercent.toInt()}%",
                fraction = state.stats.cpuPercent / 100f,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        item {
            StatCard(
                title = "RAM used",
                value = formatBytes(state.stats.usedRamBytes),
                subtitle = "${formatBytes(state.stats.freeRamBytes)} free of " +
                    formatBytes(state.stats.totalRamBytes),
                fraction = state.stats.usedRamPercent / 100f,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        item {
            ActionCard(
                title = backendLabel(state.backend),
                subtitle = "Tap to re-check privileges",
                onClick = onRefreshBackend,
            )
        }

        item {
            ActionCard(
                title = "Manage services",
                subtitle = if (state.services.isEmpty()) {
                    "List installed packages"
                } else {
                    "${state.services.count { it.state == ServiceState.DISABLED }} disabled"
                },
                onClick = onManageServices,
            )
        }

        item {
            ActionCard(
                title = "Reboot watch",
                subtitle = "Requires privileges",
                onClick = onRequestReboot,
                accent = MaterialTheme.colorScheme.error,
            )
        }

        state.message?.let { message ->
            item {
                Text(
                    text = message,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        item {
            InfoCard(
                title = "Bridge token",
                value = state.bridgeToken,
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ServicesScreen(
    state: UiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (ServiceEntry) -> Unit,
) {
    WatchScreen {
        item { TimeText() }
        item {
            ListHeader {
                Text(
                    text = "Services" + if (state.servicesLoading) "…" else "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        item {
            ActionCard(
                title = "Refresh list",
                subtitle = "${state.services.size} packages from pm",
                onClick = onRefresh,
            )
        }

        items(state.services) { entry ->
            ServiceCard(
                entry = entry,
                busy = state.busyPackage == entry.packageName,
                onClick = { onToggle(entry) },
            )
        }

        item {
            ActionCard(
                title = "Back",
                subtitle = "Return to dashboard",
                onClick = onBack,
            )
        }
    }
}

@Composable
private fun RebootConfirmScreen(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    WatchScreen {
        item { TimeText() }
        item { ListHeader { Text("Reboot?") } }
        item {
            Text(
                text = "The watch will restart. The ADB bridge must be started again " +
                    "after it boots.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        item {
            ActionCard(
                title = "Reboot now",
                subtitle = "Restart the watch",
                onClick = onConfirm,
                accent = MaterialTheme.colorScheme.error,
            )
        }
        item {
            ActionCard(
                title = "Cancel",
                subtitle = "Keep it running",
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    fraction: Float,
    color: Color,
    subtitle: String? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            UsageBar(fraction = fraction, color = color)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A plain progress bar. Avoids the curved progress indicators so it reads
 *  identically on round and square displays. */
@Composable
private fun UsageBar(fraction: Float, color: Color) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}

@Composable
private fun ServiceCard(
    entry: ServiceEntry,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val enabled = entry.state == ServiceState.ENABLED
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                StateChip(
                    text = when {
                        busy -> "…"
                        enabled -> "ON"
                        else -> "OFF"
                    },
                    color = when {
                        busy -> MaterialTheme.colorScheme.tertiary
                        enabled -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(
                text = entry.packageName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StateChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.22f))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(text = text, color = color)
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accent: Color? = null,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                color = accent ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun backendLabel(backend: Backend): String = when (backend) {
    Backend.SHIZUKU_LITE -> "Shizuku-lite: ready"
    Backend.ROOT -> "Root: ready"
    Backend.NONE -> "No privileges"
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.0f MB", mb)
    }
}
