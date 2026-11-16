package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DangerZone
import com.example.data.model.SosGlobalAlert
import com.example.data.model.UiState
import com.example.ui.components.StateErrorCard
import com.example.ui.components.StateLoadingCard
import com.example.ui.components.formatFirestoreTimestamp
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.SosCrimson
import com.example.ui.viewmodel.SafetyViewModel

@Composable
fun AdminDashboardScreen(
    viewModel: SafetyViewModel,
    modifier: Modifier = Modifier,
) {
    val alertsState by viewModel.adminSosAlertsState.collectAsStateWithLifecycle()
    val zonesState by viewModel.adminDangerZonesState.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()

    var showAddSafePlace by remember { mutableStateOf(false) }

    if (!viewModel.isAdminUser) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.widthIn(max = 480.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Admin Role Required", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Zero-trust Firestore rules restrict global SOS dispatch and danger-zone moderation to verified administrators (${SafetyViewModel.BOOTSTRAP_ADMIN_EMAIL} or /admins/{uid}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Admin Dispatch & Moderation", style = MaterialTheme.typography.headlineSmall)
                        Text("Real-time SOS monitoring, danger-zone review, and safe-place curation.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Button(
                    onClick = { showAddSafePlace = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_add_safe_place_button"),
                ) {
                    Icon(Icons.Filled.AddBusiness, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Publish Verified Safe Place")
                }
            }

            // Live SOS Feed
            item {
                Text("Live SOS Dispatch Feed", style = MaterialTheme.typography.titleLarge)
            }
            when (alertsState) {
                is UiState.Loading -> item { StateLoadingCard("Connecting to SOS dispatch feed…") }
                is UiState.Error -> item { StateErrorCard((alertsState as UiState.Error).message) }
                is UiState.Success -> {
                    val alerts = (alertsState as UiState.Success<List<SosGlobalAlert>>).data
                    if (alerts.isEmpty()) {
                        item {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                                Text("No global SOS alerts recorded.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        items(alerts, key = { it.id }) { alert ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("${alert.userAlias} • ${alert.triggerType}", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            alert.status.uppercase(),
                                            color = if (alert.status == "active") SosCrimson else SafeGreen,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                    Text(
                                        String.format("Coords: %.5f, %.5f • Battery %d%% • %s", alert.latitude, alert.longitude, alert.batteryPercent, formatFirestoreTimestamp(alert.createdAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    if (alert.status == "active" || alert.status == "acknowledged") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (alert.status == "active") {
                                                OutlinedButton(
                                                    onClick = { viewModel.updateGlobalSosStatusAdmin(alert, "acknowledged") },
                                                    modifier = Modifier.height(40.dp),
                                                ) { Text("Acknowledge") }
                                            }
                                            Button(
                                                onClick = { viewModel.updateGlobalSosStatusAdmin(alert, "resolved") },
                                                modifier = Modifier.height(40.dp),
                                            ) { Text("Resolve") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Danger Zone Moderation Queue
            item {
                Spacer(Modifier.height(6.dp))
                Text("Danger Zone Moderation Queue", style = MaterialTheme.typography.titleLarge)
            }
            when (zonesState) {
                is UiState.Loading -> item { StateLoadingCard("Loading danger zone reports…") }
                is UiState.Error -> item { StateErrorCard((zonesState as UiState.Error).message) }
                is UiState.Success -> {
                    val zones = (zonesState as UiState.Success<List<DangerZone>>).data
                    if (zones.isEmpty()) {
                        item {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                                Text("No danger zone reports in queue.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        items(zones, key = { it.id }) { zone ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(zone.title, style = MaterialTheme.typography.titleMedium)
                                        Text(zone.verificationStatus.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(zone.description, style = MaterialTheme.typography.bodyMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.moderateDangerZoneAdmin(zone.id, "verified") },
                                            modifier = Modifier.height(40.dp),
                                        ) {
                                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text("Verify")
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.moderateDangerZoneAdmin(zone.id, "rejected") },
                                            modifier = Modifier.height(40.dp),
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text("Reject")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSafePlace) {
        AddSafePlaceAdminDialog(
            defaultLat = currentLocation?.latitude ?: 37.7749,
            defaultLng = currentLocation?.longitude ?: -122.4194,
            onDismiss = { showAddSafePlace = false },
            onSave = { name, cat, addr, phone, lat, lng, is24h ->
                viewModel.addSafePlaceAdmin(name, cat, addr, phone, lat, lng, is24h)
                showAddSafePlace = false
            },
        )
    }
}

@Composable
private fun AddSafePlaceAdminDialog(
    defaultLat: Double,
    defaultLng: Double,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Double, Double, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("police_station") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("911") }
    var is24h by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Verified Safe Place") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Place Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("police_station" to "Police", "hospital" to "Hospital", "shelter" to "Shelter").forEach { (key, label) ->
                        FilterChip(selected = category == key, onClick = { category = key }, label = { Text(label) })
                    }
                }
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Emergency Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = is24h, onCheckedChange = { is24h = it })
                    Text("Open 24/7")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.trim().length >= 2 && address.trim().length >= 3 && phone.trim().length >= 3,
                onClick = { onSave(name, category, address, phone, defaultLat, defaultLng, is24h) },
            ) { Text("Publish") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
