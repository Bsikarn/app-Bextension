package com.example.workspacerouter.presentation.commandcenter

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.workspacerouter.domain.model.DisplayActionMode
import com.example.workspacerouter.domain.model.LaunchableApp
import com.example.workspacerouter.domain.model.RoutedDisplay
import com.example.workspacerouter.presentation.MainUiEvent
import com.example.workspacerouter.presentation.MainUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandCenterScreen(
    state: MainUiState,
    events: Flow<MainUiEvent>,
    onSetMode: (Int, DisplayActionMode) -> Unit,
    onSelectApp: (Int, String?) -> Unit,
    onTestDisplay: (Int) -> Unit,
    onLaunchWorkspace: () -> Unit,
    onRefreshDisplays: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var pickingForDisplayId by remember { mutableIntStateOf(-1) }

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is MainUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bextension", fontWeight = FontWeight.Bold)
                        Text(
                            "${state.displays.size} connected display(s)",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshDisplays) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh displays")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Button(
                onClick = onLaunchWorkspace,
                enabled = state.displays.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Icon(Icons.Default.RocketLaunch, contentDescription = null)
                Text("  Launch Workspace")
            }
        },
    ) { contentPadding ->
        if (state.displays.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Looking for connected displays…")
            }
        } else {
            LazyColumn(
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                item { InputRelaySetupCard() }
                items(state.displays, key = { it.display.id }) { route ->
                    DisplayCard(
                        route = route,
                        apps = state.apps,
                        loadingApps = state.isLoadingApps,
                        onSetMode = onSetMode,
                        onPickApp = { pickingForDisplayId = route.display.id },
                        onTest = { onTestDisplay(route.display.id) },
                    )
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    if (pickingForDisplayId >= 0) {
        AppPickerDialog(
            apps = state.apps,
            onDismiss = { pickingForDisplayId = -1 },
            onSelect = { app ->
                onSelectApp(pickingForDisplayId, app.packageName)
                pickingForDisplayId = -1
            },
        )
    }
}

@Composable
private fun InputRelaySetupCard() {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Multi-display input relay", style = MaterialTheme.typography.titleMedium)
            Text(
                "Enable Bextension Input Relay once to use floating display tabs, " +
                    "virtual cursor control, and direct Moonlight input handoff.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Emergency stop: press the tablet power button to turn its screen off. " +
                    "Bextension will immediately release input and close its overlays.",
                style = MaterialTheme.typography.bodySmall,
            )
            FilledTonalButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Allow restricted settings")
            }
            FilledTonalButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Open Accessibility settings")
            }
        }
    }
}

@Composable
private fun DisplayCard(
    route: RoutedDisplay,
    apps: List<LaunchableApp>,
    loadingApps: Boolean,
    onSetMode: (Int, DisplayActionMode) -> Unit,
    onPickApp: () -> Unit,
    onTest: () -> Unit,
) {
    val descriptor = route.display
    val selectedApp = apps.firstOrNull { it.packageName == route.assignment.packageName }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(descriptor.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "Display ${descriptor.id}  •  ${descriptor.physicalWidthPx} × " +
                    "${descriptor.physicalHeightPx}  •  ${"%.1f".format(descriptor.refreshRateHz)} Hz",
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = route.assignment.mode == DisplayActionMode.LAUNCH_APP,
                    onClick = { onSetMode(descriptor.id, DisplayActionMode.LAUNCH_APP) },
                )
                Text("Launch app")
                RadioButton(
                    selected = route.assignment.mode == DisplayActionMode.BLACK_OUT,
                    onClick = { onSetMode(descriptor.id, DisplayActionMode.BLACK_OUT) },
                )
                Text("Blackout")
            }

            if (route.assignment.mode == DisplayActionMode.LAUNCH_APP) {
                FilledTonalButton(
                    onClick = onPickApp,
                    enabled = !loadingApps,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (selectedApp != null) {
                        AppIcon(selectedApp, Modifier.size(28.dp))
                    } else {
                        Icon(Icons.Default.Apps, contentDescription = null)
                    }
                    Text("  ${selectedApp?.label ?: if (loadingApps) "Loading apps…" else "Choose app"}")
                }
            }

            TextButton(onClick = onTest, modifier = Modifier.align(Alignment.End)) {
                Text("Test display")
            }
        }
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<LaunchableApp>,
    onDismiss: () -> Unit,
    onSelect: (LaunchableApp) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(apps, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            apps
        } else {
            apps.filter { app ->
                app.label.contains(normalizedQuery, ignoreCase = true) ||
                    app.packageName.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an app (${filteredApps.size})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search apps") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                )

                if (filteredApps.isEmpty()) {
                    Text(
                        text = "No apps match ‘$query’",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp, max = 560.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        gridItems(filteredApps, key = { it.packageName }) { app ->
                            Card(
                                onClick = { onSelect(app) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    AppIcon(app, Modifier.size(56.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AppIcon(
    app: LaunchableApp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = app.packageName,
        key2 = app.activityClassName,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getActivityIcon(ComponentName(app.packageName, app.activityClassName))
                    .toBitmap(width = 128, height = 128)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = checkNotNull(bitmap),
            contentDescription = "${app.label} icon",
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        Icon(
            imageVector = Icons.Default.Apps,
            contentDescription = null,
            modifier = modifier,
        )
    }
}
