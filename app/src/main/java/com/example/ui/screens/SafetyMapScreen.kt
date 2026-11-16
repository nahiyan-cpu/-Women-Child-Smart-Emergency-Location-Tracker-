package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.NightShelter
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.model.DangerZone
import com.example.data.model.SafePlace
import com.example.data.model.SafetyGeofence
import com.example.data.model.UiState
import com.example.service.LocationAndGeofenceManager
import com.example.ui.components.LocationPermissionBanner
import com.example.ui.components.StateErrorCard
import com.example.ui.components.StateLoadingCard
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.GuardianTeal
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.SosCrimson
import com.example.ui.viewmodel.SafetyViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.cos
import kotlin.math.sin

private enum class MapFilter(val label: String) {
    SAFE_PLACES("Safe Places"),
    DANGER_ZONES("Danger Zones"),
    GEOFENCES("My Geofences"),
}

@Composable
fun SafetyMapScreen(
    viewModel: SafetyViewModel,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locationGranted by viewModel.locationPermissionGranted.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val safePlacesState by viewModel.safePlacesState.collectAsStateWithLifecycle()
    val verifiedDangerState by viewModel.verifiedDangerZonesState.collectAsStateWithLifecycle()
    val myDangerState by viewModel.myDangerZonesState.collectAsStateWithLifecycle()
    val geofencesState by viewModel.geofencesState.collectAsStateWithLifecycle()

    var activeFilter by remember { mutableStateOf(MapFilter.SAFE_PLACES) }
    var showAddGeofenceDialog by remember { mutableStateOf(false) }
    var showReportDangerDialog by remember { mutableStateOf(false) }

    val safePlaces = (safePlacesState as? UiState.Success)?.data.orEmpty()
    val verifiedDangers = (verifiedDangerState as? UiState.Success)?.data.orEmpty()
    val myDangers = (myDangerState as? UiState.Success)?.data.orEmpty()
    val geofences = (geofencesState as? UiState.Success)?.data.orEmpty()

    val anchorLat = currentLocation?.latitude ?: 37.7749
    val anchorLng = currentLocation?.longitude ?: -122.4194

    val hasValidMapsKey = BuildConfig.MAPS_API_KEY.isNotBlank() &&
        BuildConfig.MAPS_API_KEY != "MY_MAPS_API_KEY"

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!locationGranted) {
                item {
                    LocationPermissionBanner(onRequestPermission = onRequestLocationPermission)
                }
            }

            // Map Surface: Live Google Maps Compose when API key configured, else GPS Radar
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        if (hasValidMapsKey) {
                            val cameraState = rememberCameraPositionState {
                                position = CameraPosition.fromLatLngZoom(LatLng(anchorLat, anchorLng), 14f)
                            }
                            GoogleMap(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp),
                                cameraPositionState = cameraState,
                                properties = MapProperties(isMyLocationEnabled = locationGranted),
                            ) {
                                safePlaces.forEach { place ->
                                    Marker(
                                        state = MarkerState(LatLng(place.latitude, place.longitude)),
                                        title = place.name,
                                        snippet = place.address,
                                    )
                                }
                                verifiedDangers.forEach { dz ->
                                    Circle(
                                        center = LatLng(dz.latitude, dz.longitude),
                                        radius = dz.radiusMeters,
                                        strokeColor = SosCrimson,
                                        fillColor = SosCrimson.copy(alpha = 0.22f),
                                        strokeWidth = 3f,
                                    )
                                }
                                geofences.filter { it.isActive }.forEach { gf ->
                                    val color = if (gf.zoneType == "safe_zone") SafeGreen else AlertAmber
                                    Circle(
                                        center = LatLng(gf.latitude, gf.longitude),
                                        radius = gf.radiusMeters,
                                        strokeColor = color,
                                        fillColor = color.copy(alpha = 0.16f),
                                        strokeWidth = 3f,
                                    )
                                }
                            }
                        } else {
                            TacticalGpsRadarCanvas(
                                anchorLat = anchorLat,
                                anchorLng = anchorLng,
                                safePlaces = safePlaces,
                                dangerZones = verifiedDangers,
                                geofences = geofences,
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(R.string.maps_key_notice),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons: Add Geofence + Report Danger Zone
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { showAddGeofenceDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("add_geofence_button"),
                    ) {
                        Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Add Geofence")
                    }
                    OutlinedButton(
                        onClick = { showReportDangerDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("report_danger_zone_button"),
                    ) {
                        Icon(Icons.Filled.ReportProblem, contentDescription = null, modifier = Modifier.size(18.dp), tint = AlertAmber)
                        Spacer(Modifier.size(6.dp))
                        Text("Report Hazard")
                    }
                }
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MapFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter },
                            label = { Text(filter.label) },
                        )
                    }
                }
            }

            when (activeFilter) {
                MapFilter.SAFE_PLACES -> {
                    item {
                        OutlinedButton(
                            onClick = {
                                val geoUri = Uri.parse("geo:$anchorLat,$anchorLng?q=police+station+or+hospital")
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geoUri)) }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Icon(Icons.Filled.Directions, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Search Nearby Police & Hospitals in Maps")
                        }
                    }

                    when (safePlacesState) {
                        is UiState.Loading -> item { StateLoadingCard("Loading verified safe places…") }
                        is UiState.Error -> item { StateErrorCard((safePlacesState as UiState.Error).message) }
                        is UiState.Success -> {
                            val sorted = safePlaces.sortedBy {
                                LocationAndGeofenceManager.distanceMeters(anchorLat, anchorLng, it.latitude, it.longitude)
                            }
                            if (sorted.isEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = "No verified safe places published in Firestore yet. Admins can add verified shelters/stations in the Admin tab, or tap Search Nearby above.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(16.dp),
                                        )
                                    }
                                }
                            } else {
                                items(sorted, key = { it.id }) { place ->
                                    SafePlaceCard(
                                        place = place,
                                        anchorLat = anchorLat,
                                        anchorLng = anchorLng,
                                    )
                                }
                            }
                        }
                    }
                }

                MapFilter.DANGER_ZONES -> {
                    when (verifiedDangerState) {
                        is UiState.Loading -> item { StateLoadingCard("Loading community hazard zones…") }
                        is UiState.Error -> item { StateErrorCard((verifiedDangerState as UiState.Error).message) }
                        is UiState.Success -> {
                            val combined = (verifiedDangers + myDangers).distinctBy { it.id }
                            if (combined.isEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = "No hazard reports nearby. Tap 'Report Hazard' to warn the community about poorly lit or unsafe areas.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(16.dp),
                                        )
                                    }
                                }
                            } else {
                                items(combined, key = { it.id }) { zone ->
                                    DangerZoneCard(
                                        zone = zone,
                                        anchorLat = anchorLat,
                                        anchorLng = anchorLng,
                                        onConfirm = { viewModel.confirmDangerZone(zone.id) },
                                    )
                                }
                            }
                        }
                    }
                }

                MapFilter.GEOFENCES -> {
                    when (geofencesState) {
                        is UiState.Loading -> item { StateLoadingCard("Loading geofences…") }
                        is UiState.Error -> item { StateErrorCard((geofencesState as UiState.Error).message) }
                        is UiState.Success -> {
                            if (geofences.isEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = "No geofences configured yet. Create a Home, School, or Restricted Area perimeter.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(16.dp),
                                        )
                                    }
                                }
                            } else {
                                items(geofences, key = { it.id }) { gf ->
                                    GeofenceCard(
                                        geofence = gf,
                                        onToggle = { viewModel.toggleGeofence(gf) },
                                        onDelete = { viewModel.deleteGeofence(gf.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddGeofenceDialog) {
        AddGeofenceDialog(
            defaultLat = anchorLat,
            defaultLng = anchorLng,
            onDismiss = { showAddGeofenceDialog = false },
            onCreate = { name, type, lat, lng, radius, enter, exit ->
                viewModel.addGeofence(name, type, lat, lng, radius, enter, exit)
                showAddGeofenceDialog = false
            },
        )
    }

    if (showReportDangerDialog) {
        ReportDangerZoneDialog(
            defaultLat = anchorLat,
            defaultLng = anchorLng,
            onDismiss = { showReportDangerDialog = false },
            onSubmit = { title, desc, cat, sev, lat, lng, radius ->
                viewModel.reportDangerZone(title, desc, cat, sev, lat, lng, radius)
                showReportDangerDialog = false
            },
        )
    }
}

@Composable
private fun TacticalGpsRadarCanvas(
    anchorLat: Double,
    anchorLng: Double,
    safePlaces: List<SafePlace>,
    dangerZones: List<DangerZone>,
    geofences: List<SafetyGeofence>,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = Color(0xFF0F172A))
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension * 0.44f
            listOf(0.33f, 0.66f, 1.0f).forEach { frac ->
                drawCircle(
                    color = Color(0xFF1E3A5F),
                    radius = maxR * frac,
                    center = center,
                    style = Stroke(width = 2f),
                )
            }
            drawLine(Color(0xFF1E3A5F), Offset(center.x - maxR, center.y), Offset(center.x + maxR, center.y), 1.5f)
            drawLine(Color(0xFF1E3A5F), Offset(center.x, center.y - maxR), Offset(center.x, center.y + maxR), 1.5f)

            // Plot nearby points relative to current anchor
            fun plotPoint(lat: Double, lng: Double, color: Color, radiusPx: Float) {
                val distM = LocationAndGeofenceManager.distanceMeters(anchorLat, anchorLng, lat, lng)
                val normalized = (distM / 3000f).coerceIn(0.08f, 0.95f)
                val angle = kotlin.math.atan2(lat - anchorLat, lng - anchorLng)
                val px = center.x + (cos(angle) * maxR * normalized).toFloat()
                val py = center.y - (sin(angle) * maxR * normalized).toFloat()
                drawCircle(color = color.copy(alpha = 0.25f), radius = radiusPx * 2f, center = Offset(px, py))
                drawCircle(color = color, radius = radiusPx, center = Offset(px, py))
            }

            geofences.filter { it.isActive }.forEach {
                plotPoint(it.latitude, it.longitude, if (it.zoneType == "safe_zone") Color(0xFF22C55E) else Color(0xFFF59E0B), 8f)
            }
            safePlaces.forEach {
                plotPoint(it.latitude, it.longitude, Color(0xFF2DD4BF), 8f)
            }
            dangerZones.forEach {
                plotPoint(it.latitude, it.longitude, Color(0xFFEF4444), 9f)
            }

            // User center beacon
            drawCircle(color = Color(0xFF38BDF8).copy(alpha = 0.25f), radius = 18f, center = center)
            drawCircle(color = Color(0xFF38BDF8), radius = 8f, center = center)
        }
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
        ) {
            Text(
                text = String.format("GPS RADAR • %.4f, %.4f", anchorLat, anchorLng),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SafePlaceCard(
    place: SafePlace,
    anchorLat: Double,
    anchorLng: Double,
) {
    val context = LocalContext.current
    val dist = LocationAndGeofenceManager.distanceMeters(anchorLat, anchorLng, place.latitude, place.longitude)
    val icon = when (place.category) {
        "police_station" -> Icons.Filled.LocalPolice
        "hospital" -> Icons.Filled.LocalHospital
        "shelter" -> Icons.Filled.NightShelter
        else -> Icons.Filled.Security
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = place.category, tint = GuardianTeal, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                Text("${place.address} • ${LocationAndGeofenceManager.formatDistance(dist)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (place.isOpen24Hours) {
                    Text("Open 24/7 • Verified", style = MaterialTheme.typography.labelMedium, color = SafeGreen)
                }
            }
            IconButton(
                onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${place.phone}"))) }
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Call ${place.name}", tint = SafeGreen)
            }
            IconButton(
                onClick = {
                    val uri = Uri.parse("google.navigation:q=${place.latitude},${place.longitude}")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.Directions, contentDescription = "Navigate to ${place.name}", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DangerZoneCard(
    zone: DangerZone,
    anchorLat: Double,
    anchorLng: Double,
    onConfirm: () -> Unit,
) {
    val dist = LocationAndGeofenceManager.distanceMeters(anchorLat, anchorLng, zone.latitude, zone.longitude)
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
                Text(zone.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (zone.severity in listOf("high", "critical")) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = "${zone.severity.uppercase()} • ${zone.verificationStatus.uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Text(zone.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${LocationAndGeofenceManager.formatDistance(dist)} away • Radius ${zone.radiusMeters.toInt()}m",
                    style = MaterialTheme.typography.labelSmall,
                )
                if (zone.verificationStatus == "verified") {
                    OutlinedButton(onClick = onConfirm, modifier = Modifier.height(40.dp)) {
                        Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Confirm (${zone.confirmCount})")
                    }
                }
            }
        }
    }
}

@Composable
private fun GeofenceCard(
    geofence: SafetyGeofence,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(geofence.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${geofence.zoneType.replace('_', ' ')} • ${geofence.radiusMeters.toInt()}m • Last: ${geofence.lastEvent}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = geofence.isActive, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete Geofence", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddGeofenceDialog(
    defaultLat: Double,
    defaultLng: Double,
    onDismiss: () -> Unit,
    onCreate: (String, String, Double, Double, Double, Boolean, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("Home Safe Zone") }
    var isSafeZone by remember { mutableStateOf(true) }
    var radius by remember { mutableFloatStateOf(250f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Safety Geofence") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Zone Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isSafeZone, onClick = { isSafeZone = true }, label = { Text("Safe Zone") })
                    FilterChip(selected = !isSafeZone, onClick = { isSafeZone = false }, label = { Text("Restricted Zone") })
                }
                Text("Radius: ${radius.toInt()} meters", style = MaterialTheme.typography.bodyMedium)
                Slider(value = radius, onValueChange = { radius = it }, valueRange = 100f..2000f)
                Text(String.format("Center: %.5f, %.5f", defaultLat, defaultLng), style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(
                            name,
                            if (isSafeZone) "safe_zone" else "restricted_zone",
                            defaultLat,
                            defaultLng,
                            radius.toDouble(),
                            !isSafeZone,
                            isSafeZone,
                        )
                    }
                }
            ) { Text("Save Zone") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReportDangerZoneDialog(
    defaultLat: Double,
    defaultLng: Double,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, Double, Double, Double) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("poor_lighting") }
    var severity by remember { mutableStateOf("high") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Community Danger Zone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Short Title (e.g. Broken Streetlights)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Details (min 5 chars)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("medium", "high", "critical").forEach { sev ->
                        FilterChip(selected = severity == sev, onClick = { severity = sev }, label = { Text(sev) })
                    }
                }
                Text("Reports enter admin moderation before public broadcast.", style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = {
            Button(
                enabled = title.trim().length >= 3 && description.trim().length >= 5,
                onClick = {
                    onSubmit(title, description, category, severity, defaultLat, defaultLng, 150.0)
                }
            ) { Text("Submit Report") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
