package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.EmergencyContact
import com.example.data.model.SosIncident
import com.example.data.model.UiState
import com.example.ui.components.LocationPermissionBanner
import com.example.ui.components.OfflineStatusBanner
import com.example.ui.components.StateErrorCard
import com.example.ui.components.formatFirestoreTimestamp
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.SosCrimson
import com.example.ui.theme.SosCrimsonBright
import com.example.ui.viewmodel.SafetyViewModel

@Composable
fun HomeSosScreen(
    viewModel: SafetyViewModel,
    onRequestLocationPermission: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToEvidence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val contactsState by viewModel.contactsState.collectAsStateWithLifecycle()
    val incidentsState by viewModel.incidentsState.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val locationGranted by viewModel.locationPermissionGranted.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val isSirenPlaying by viewModel.isSirenPlaying.collectAsStateWithLifecycle()
    val isSosTriggering by viewModel.isSosTriggering.collectAsStateWithLifecycle()
    val trackedBeacon by viewModel.trackedBeacon.collectAsStateWithLifecycle()
    val beaconLookupError by viewModel.beaconLookupError.collectAsStateWithLifecycle()

    val incidents = (incidentsState as? UiState.Success)?.data.orEmpty()
    val activeIncident: SosIncident? = incidents.firstOrNull {
        it.status == "active" || it.status == "acknowledged"
    }
    val contacts = (contactsState as? UiState.Success)?.data.orEmpty()
    val profile = (profileState as? UiState.Success)?.data

    var showResolveDialog by remember { mutableStateOf(false) }
    var showBeaconLookupDialog by remember { mutableStateOf(false) }
    var resolvePinInput by remember { mutableStateOf("") }
    var shareIdInput by remember { mutableStateOf("") }

    val pulseTransition = rememberInfiniteTransition(label = "sos_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (activeIncident != null) 1.12f else 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (activeIncident != null) 650 else 1300),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    val sosContainerColor by animateColorAsState(
        targetValue = if (activeIncident != null) Color(0xFF991B1B) else SosCrimson,
        label = "sos_bg",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isOffline) {
                OfflineStatusBanner()
            }

            if (!locationGranted) {
                LocationPermissionBanner(onRequestPermission = onRequestLocationPermission)
            }

            // Header Status Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hello, ${profile?.displayName ?: viewModel.currentUserName ?: "Guardian"}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        val locText = currentLocation?.let {
                            String.format("%.5f, %.5f (±%.0fm)", it.latitude, it.longitude, it.accuracyMeters)
                        } ?: if (locationGranted) "Acquiring GPS fix…" else "GPS permission disabled"
                        Text(
                            text = locText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (locationGranted) viewModel.refreshLocation() else onRequestLocationPermission()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("refresh_gps_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GpsFixed,
                            contentDescription = "Refresh GPS Location",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (incidentsState is UiState.Error) {
                StateErrorCard((incidentsState as UiState.Error).message)
            }

            // Active SOS Alert Banner or One-Touch SOS Hero Button
            if (activeIncident != null) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_sos_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = "Active SOS",
                                tint = SosCrimson,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "EMERGENCY SOS ${activeIncident.status.uppercase()}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    text = "Started ${formatFirestoreTimestamp(activeIncident.createdAt)} • Battery ${activeIncident.batteryPercent}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Live Beacon Share ID",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = activeIncident.shareCode,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(activeIncident.shareCode))
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Share ID")
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = {
                                    val mapsUrl = "https://maps.google.com/?q=${activeIncident.latitude},${activeIncident.longitude}"
                                    val phones = contacts.filter { it.notifyOnSos }.joinToString(",") { it.phone }
                                    val smsUri = Uri.parse("smsto:$phones")
                                    val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                                        putExtra(
                                            "sms_body",
                                            "EMERGENCY SOS! My live location: $mapsUrl (SafeGuard Beacon ID: ${activeIncident.shareCode})"
                                        )
                                    }
                                    runCatching { context.startActivity(smsIntent) }
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("SMS Contacts")
                            }

                            FilledTonalButton(
                                onClick = { showResolveDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("resolve_sos_button"),
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("I'm Safe")
                            }
                        }
                    }
                }
            }

            // Prominent Red SOS Button Arena
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 26.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(214.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(204.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(SosCrimson.copy(alpha = 0.16f))
                        )
                        Box(
                            modifier = Modifier
                                .size(174.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(SosCrimsonBright, sosContainerColor)
                                    )
                                )
                                .border(4.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "One-Touch Emergency SOS Button"
                                }
                                .pointerInput(activeIncident, isSosTriggering) {
                                    detectTapGestures(
                                        onTap = {
                                            if (activeIncident == null && !isSosTriggering) {
                                                viewModel.triggerOneTouchSos()
                                            }
                                        },
                                        onLongPress = {
                                            if (activeIncident == null && !isSosTriggering) {
                                                viewModel.triggerOneTouchSos(notes = "Long-press urgent SOS")
                                            }
                                        }
                                    )
                                }
                                .testTag("sos_hero_button"),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSosTriggering) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (activeIncident != null) "ACTIVE" else "SOS",
                                        color = Color.White,
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 2.sp,
                                    )
                                    Text(
                                        text = if (activeIncident != null) "BEACON LIVE" else "TAP FOR HELP",
                                        color = Color.White.copy(alpha = 0.9f),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = if (activeIncident != null) {
                            "Live GPS breadcrumbs are streaming visibly. Tap 'I'm Safe' above to stop."
                        } else {
                            "Triggers instant Firestore alert, live GPS sharing beacon, and audible siren."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Quick Actions Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { viewModel.toggleSiren() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSirenPlaying) SosCrimson else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isSirenPlaying) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("siren_toggle_button"),
                ) {
                    Icon(
                        imageVector = if (isSirenPlaying) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(if (isSirenPlaying) "Stop Siren" else "Loud Siren")
                }

                Button(
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))
                        runCatching { context.startActivity(dialIntent) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("call_emergency_button"),
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Call 911")
                }
            }

            // Secondary Safety Tools
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { showBeaconLookupDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("track_contact_beacon_button"),
                ) {
                    Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Track Beacon")
                }

                OutlinedButton(
                    onClick = onNavigateToEvidence,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("quick_evidence_button"),
                ) {
                    Icon(Icons.Filled.ShareLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Evidence Vault")
                }
            }

            // Trusted Contacts Summary Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Trusted Circle (${contacts.size})", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = onNavigateToContacts) {
                            Text(if (contacts.isEmpty()) "Add Contact" else "Manage")
                        }
                    }

                    if (contacts.isEmpty()) {
                        Text(
                            text = "No emergency contacts yet. Add a guardian or family member for rapid SMS & SOS dispatch.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        contacts.take(3).forEach { contact: EmergencyContact ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(contact.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text("${contact.relationship} • ${contact.phone}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}")))
                                        }
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.Filled.Call, contentDescription = "Call ${contact.name}", tint = SafeGreen)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showResolveDialog && activeIncident != null) {
        val hasPin = !profile?.emergencyPinHash.isNullOrBlank()
        AlertDialog(
            onDismissRequest = { showResolveDialog = false },
            title = { Text("End Emergency SOS?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Confirm you are safe to stop live GPS sharing and silence the emergency siren.")
                    if (hasPin) {
                        OutlinedTextField(
                            value = resolvePinInput,
                            onValueChange = { resolvePinInput = it },
                            label = { Text("Emergency Cancel PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (viewModel.resolveSosIncident(activeIncident.id, activeIncident.shareCode, "resolved", resolvePinInput)) {
                            resolvePinInput = ""
                            showResolveDialog = false
                        }
                    }
                ) {
                    Text("Confirm Safe")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (viewModel.resolveSosIncident(activeIncident.id, activeIncident.shareCode, "false_alarm", resolvePinInput)) {
                            resolvePinInput = ""
                            showResolveDialog = false
                        }
                    }
                ) {
                    Text("False Alarm")
                }
            },
        )
    }

    if (showBeaconLookupDialog) {
        AlertDialog(
            onDismissRequest = {
                showBeaconLookupDialog = false
                viewModel.clearTrackedBeacon()
            },
            title = { Text("Track Live Share Beacon") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter a SafeGuard Share ID shared by a contact to view their real-time coordinates.")
                    OutlinedTextField(
                        value = shareIdInput,
                        onValueChange = { shareIdInput = it },
                        label = { Text("Share ID (e.g. shr_...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (beaconLookupError != null) {
                        Text(
                            text = beaconLookupError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    trackedBeacon?.let { beacon ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${beacon.ownerAlias} (${if (beacon.isActive) "LIVE" else "ENDED"})", fontWeight = FontWeight.Bold)
                                Text(String.format("Lat: %.5f, Lng: %.5f", beacon.latitude, beacon.longitude), style = MaterialTheme.typography.labelSmall)
                                Text("Battery: ${beacon.batteryPercent}% • ${beacon.statusMessage}", style = MaterialTheme.typography.bodyMedium)
                                TextButton(
                                    onClick = {
                                        val mapUri = Uri.parse("geo:${beacon.latitude},${beacon.longitude}?q=${beacon.latitude},${beacon.longitude}(${Uri.encode(beacon.ownerAlias)})")
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, mapUri)) }
                                    }
                                ) {
                                    Text("Open in Maps")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.observeLiveShareById(shareIdInput) }) {
                    Text("Track Live")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBeaconLookupDialog = false
                    viewModel.clearTrackedBeacon()
                }) {
                    Text("Close")
                }
            },
        )
    }
}
