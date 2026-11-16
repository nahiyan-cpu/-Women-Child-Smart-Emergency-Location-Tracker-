package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.EmergencyContact
import com.example.data.model.UiState
import com.example.ui.components.StateErrorCard
import com.example.ui.components.StateLoadingCard
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.SafeGreen
import com.example.ui.viewmodel.SafetyViewModel

@Composable
fun ContactsAndProfileScreen(
    viewModel: SafetyViewModel,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val contactsState by viewModel.contactsState.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    var displayName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var bloodType by remember { mutableStateOf("") }
    var medicalNotes by remember { mutableStateOf("") }
    var homeAddress by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var autoSiren by remember { mutableStateOf(true) }
    var locationSharing by remember { mutableStateOf(true) }

    LaunchedEffect(profile) {
        if (profile != null) {
            displayName = profile.displayName
            phone = profile.phone.orEmpty()
            bloodType = profile.bloodType.orEmpty()
            medicalNotes = profile.medicalNotes.orEmpty()
            homeAddress = profile.homeAddress.orEmpty()
            autoSiren = profile.sosAutoSiren
            locationSharing = profile.locationSharingEnabled
        }
    }

    var editingContact by remember { mutableStateOf<EmergencyContact?>(null) }
    var showContactDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Emergency Contacts Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Emergency Contacts", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Trusted guardians notified when SOS or geofence alerts trigger.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        editingContact = null
                        showContactDialog = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_contact_button"),
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Add Emergency Contact")
                }
            }

            when (contactsState) {
                is UiState.Loading -> item { StateLoadingCard("Loading emergency contacts…") }
                is UiState.Error -> item { StateErrorCard((contactsState as UiState.Error).message) }
                is UiState.Success -> {
                    val contacts = (contactsState as UiState.Success<List<EmergencyContact>>).data
                    if (contacts.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "No contacts added yet. Add at least one primary emergency contact.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    } else {
                        items(contacts, key = { it.id }) { contact ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (contact.isPrimary) {
                                        Icon(Icons.Filled.Star, contentDescription = "Primary Contact", tint = AlertAmber)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(contact.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${contact.relationship} • ${contact.phone}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            buildString {
                                                if (contact.notifyOnSos) append("SOS ")
                                                if (contact.notifyOnGeofence) append("• Geofence")
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}")))
                                            }
                                        },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(Icons.Filled.Call, contentDescription = "Call", tint = SafeGreen)
                                    }
                                    IconButton(
                                        onClick = {
                                            editingContact = contact
                                            showContactDialog = true
                                        },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit Contact")
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteContact(contact.id) },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Contact", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Medical & Safety Profile Card
            item {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.MedicalInformation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Personal & Medical Safety Profile", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            text = "Account: ${viewModel.currentUserEmail ?: viewModel.currentUserId}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Full / Preferred Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Phone") },
                                singleLine = true,
                                modifier = Modifier.weight(1.4f),
                            )
                            OutlinedTextField(
                                value = bloodType,
                                onValueChange = { bloodType = it },
                                label = { Text("Blood Type") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedTextField(
                            value = medicalNotes,
                            onValueChange = { medicalNotes = it },
                            label = { Text("Allergies / Medical Notes") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = homeAddress,
                            onValueChange = { homeAddress = it },
                            label = { Text("Home / Safe Address (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { newPin = it },
                            label = {
                                Text(
                                    if (profile?.emergencyPinHash.isNullOrBlank())
                                        "Set Emergency Cancel PIN (Optional)"
                                    else
                                        "Update Emergency Cancel PIN (PIN Set)"
                                )
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Auto-sound loud siren on SOS", style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = autoSiren, onCheckedChange = { autoSiren = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Enable live beacon sharing on SOS", style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = locationSharing, onCheckedChange = { locationSharing = it })
                        }
                        Button(
                            onClick = {
                                viewModel.saveProfile(
                                    displayName = displayName,
                                    phone = phone,
                                    bloodType = bloodType,
                                    medicalNotes = medicalNotes,
                                    homeAddress = homeAddress,
                                    newPin = newPin,
                                    sosAutoSiren = autoSiren,
                                    locationSharingEnabled = locationSharing,
                                )
                                newPin = ""
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_profile_button"),
                        ) {
                            Text("Save Safety Profile", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onSignOutClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("sign_out_button"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Sign Out")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showContactDialog) {
        EmergencyContactDialog(
            initial = editingContact,
            onDismiss = { showContactDialog = false },
            onSave = { contact ->
                viewModel.addOrUpdateContact(contact)
                showContactDialog = false
            },
        )
    }
}

@Composable
private fun EmergencyContactDialog(
    initial: EmergencyContact?,
    onDismiss: () -> Unit,
    onSave: (EmergencyContact) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var relationship by remember { mutableStateOf(initial?.relationship ?: "Parent / Guardian") }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var isPrimary by remember { mutableStateOf(initial?.isPrimary ?: false) }
    var notifySos by remember { mutableStateOf(initial?.notifyOnSos ?: true) }
    var notifyGeofence by remember { mutableStateOf(initial?.notifyOnGeofence ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Trusted Contact" else "Edit Contact") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("Relationship") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPrimary, onCheckedChange = { isPrimary = it })
                    TextButton(onClick = { isPrimary = !isPrimary }) { Text("Primary SOS Contact") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = notifySos, onCheckedChange = { notifySos = it })
                    TextButton(onClick = { notifySos = !notifySos }) { Text("Notify on SOS") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = notifyGeofence, onCheckedChange = { notifyGeofence = it })
                    TextButton(onClick = { notifyGeofence = !notifyGeofence }) { Text("Notify on Geofence Events") }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && relationship.isNotBlank() && phone.trim().length >= 3,
                onClick = {
                    onSave(
                        (initial ?: EmergencyContact()).copy(
                            name = name,
                            relationship = relationship,
                            phone = phone,
                            email = email.ifBlank { null },
                            isPrimary = isPrimary,
                            notifyOnSos = notifySos,
                            notifyOnGeofence = notifyGeofence,
                        )
                    )
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
