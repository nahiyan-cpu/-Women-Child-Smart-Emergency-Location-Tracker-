package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.EvidenceRecord
import com.example.data.model.SosIncident
import com.example.data.model.UiState
import com.example.ui.components.StateErrorCard
import com.example.ui.components.StateLoadingCard
import com.example.ui.components.formatFirestoreTimestamp
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.SosCrimson
import com.example.ui.viewmodel.SafetyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun HistoryAndEvidenceScreen(
    viewModel: SafetyViewModel,
    initialTab: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val incidentsState by viewModel.incidentsState.collectAsStateWithLifecycle()
    val evidenceState by viewModel.evidenceState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var showCameraModal by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var consentChecked by remember { mutableStateOf(false) }
    var uploadToCloud by remember { mutableStateOf(false) }
    var pendingCaptureAction by remember { mutableStateOf("text_note") }
    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }
    var decryptedPreview by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Visible foreground audio recording state (NEVER covert)
    var isRecordingAudio by remember { mutableStateOf(false) }
    var audioElapsedSec by remember { mutableIntStateOf(0) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }

    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            audioElapsedSec = 0
            while (isRecordingAudio) {
                delay(1000L)
                audioElapsedSec += 1
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            }
        }
    }

    fun startForegroundAudioRecording() {
        val outDir = File(context.cacheDir, "audio_capture").apply { mkdirs() }
        val file = File(outDir, "voice_${UUID.randomUUID()}.m4a")
        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            audioFile = file
            isRecordingAudio = true
        } catch (_: Exception) {
            isRecordingAudio = false
        }
    }

    fun stopAndEncryptAudioRecording() {
        val file = audioFile
        runCatching {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        }
        mediaRecorder = null
        isRecordingAudio = false
        if (file != null && file.exists()) {
            val bytes = file.readBytes()
            val activeIncId = (incidentsState as? UiState.Success)?.data
                ?.firstOrNull { it.status == "active" }?.id ?: "general"
            viewModel.saveConsentedBytesEvidence(
                rawBytes = bytes,
                captureMode = "audio_note",
                extension = "m4a",
                incidentId = activeIncId,
                title = "Voice Evidence (${audioElapsedSec}s)",
                summary = "User-consented foreground voice note (${audioElapsedSec}s)",
                consentConfirmed = true,
                uploadToCloud = uploadToCloud,
            )
            file.delete()
        }
    }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val activeIncId = (incidentsState as? UiState.Success)?.data
                ?.firstOrNull { it.status == "active" }?.id ?: "general"
            viewModel.saveConsentedMediaEvidence(
                uri = uri,
                captureMode = "gallery_photo",
                extension = "jpg",
                incidentId = activeIncId,
                title = "Selected Photo Evidence",
                summary = "Imported via Android Photo Picker with explicit user consent",
                consentConfirmed = true,
                uploadToCloud = uploadToCloud,
            )
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCameraModal = true
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startForegroundAudioRecording()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Incident History") },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Encrypted Evidence") },
                    icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (incidentsState) {
                        is UiState.Loading -> item { StateLoadingCard("Loading incident audit history…") }
                        is UiState.Error -> item { StateErrorCard((incidentsState as UiState.Error).message) }
                        is UiState.Success -> {
                            val list = (incidentsState as UiState.Success<List<SosIncident>>).data
                            if (list.isEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = "No SOS incidents logged. When you trigger SOS or a check-in, full timestamps and coordinates appear here.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(18.dp),
                                        )
                                    }
                                }
                            } else {
                                items(list, key = { it.id }) { inc ->
                                    IncidentHistoryCard(
                                        incident = inc,
                                        onResolve = {
                                            viewModel.resolveSosIncident(inc.id, inc.shareCode, "resolved")
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = stringResource(R.string.consent_evidence_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }

                    if (isRecordingAudio) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = SosCrimson,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column {
                                        Text("Recording Consented Voice Note", color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("Visible Foreground Capture • ${audioElapsedSec}s", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(
                                        onClick = { stopAndEncryptAudioRecording() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SosCrimson),
                                    ) {
                                        Icon(Icons.Filled.Stop, contentDescription = null)
                                        Spacer(Modifier.size(6.dp))
                                        Text("Stop & Encrypt")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                consentChecked = false
                                showConsentDialog = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("capture_evidence_button"),
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Capture Consented Evidence")
                        }
                    }

                    when (evidenceState) {
                        is UiState.Loading -> item { StateLoadingCard("Decrypting evidence index…") }
                        is UiState.Error -> item { StateErrorCard((evidenceState as UiState.Error).message) }
                        is UiState.Success -> {
                            val records = (evidenceState as UiState.Success<List<EvidenceRecord>>).data
                            if (records.isEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = "Your AES-256-GCM encrypted evidence vault is empty.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(18.dp),
                                        )
                                    }
                                }
                            } else {
                                items(records, key = { it.id }) { ev ->
                                    EvidenceRecordCard(
                                        record = ev,
                                        onDecryptNote = if (ev.captureMode == "text_note") {
                                            {
                                                scope.launch {
                                                    val res = viewModel.decryptNotePreview(ev.localEncryptedUri)
                                                    decryptedPreview = ev.title to (res.getOrElse { it.localizedMessage ?: "Unable to decrypt" })
                                                }
                                            }
                                        } else null,
                                        onDelete = { viewModel.deleteEvidence(ev.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            title = { Text(stringResource(R.string.consent_evidence_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose an explicit capture method. Nothing is recorded in the background.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pendingCaptureAction = "camera_photo" },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Camera")
                        }
                        OutlinedButton(
                            onClick = { pendingCaptureAction = "gallery_photo" },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Picker")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pendingCaptureAction = "audio_note" },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Audio")
                        }
                        OutlinedButton(
                            onClick = { pendingCaptureAction = "text_note" },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Text Note")
                        }
                    }
                    Text("Selected mode: ${pendingCaptureAction.replace('_', ' ')}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Checkbox(
                            checked = consentChecked,
                            onCheckedChange = { consentChecked = it },
                            modifier = Modifier.testTag("consent_checkbox"),
                        )
                        TextButton(
                            onClick = { consentChecked = !consentChecked },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "I explicitly consent to capture and encrypt this evidence.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(checked = uploadToCloud, onCheckedChange = { uploadToCloud = it })
                        TextButton(
                            onClick = { uploadToCloud = !uploadToCloud },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "Also attempt encrypted Firebase Storage backup",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = consentChecked,
                    onClick = {
                        showConsentDialog = false
                        when (pendingCaptureAction) {
                            "text_note" -> showNoteDialog = true
                            "gallery_photo" -> photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                            "camera_photo" -> {
                                val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                    PackageManager.PERMISSION_GRANTED
                                if (hasCam) showCameraModal = true else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                            "audio_note" -> {
                                val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                                if (hasMic) startForegroundAudioRecording() else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                ) {
                    Text("Proceed")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Encrypted Incident Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = noteBody,
                        onValueChange = { noteBody = it },
                        label = { Text("Observation / License Plate / Description") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = noteTitle.isNotBlank() && noteBody.isNotBlank(),
                    onClick = {
                        val activeIncId = (incidentsState as? UiState.Success)?.data
                            ?.firstOrNull { it.status == "active" }?.id ?: "general"
                        viewModel.saveConsentedTextEvidence(
                            incidentId = activeIncId,
                            title = noteTitle,
                            noteText = noteBody,
                            consentConfirmed = true,
                            uploadToCloud = uploadToCloud,
                        )
                        noteTitle = ""
                        noteBody = ""
                        showNoteDialog = false
                    },
                ) { Text("Encrypt & Save") }
            },
            dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") } },
        )
    }

    if (showCameraModal) {
        CameraCaptureModal(
            onCaptured = { uri ->
                showCameraModal = false
                val activeIncId = (incidentsState as? UiState.Success)?.data
                    ?.firstOrNull { it.status == "active" }?.id ?: "general"
                viewModel.saveConsentedMediaEvidence(
                    uri = uri,
                    captureMode = "camera_photo",
                    extension = "jpg",
                    incidentId = activeIncId,
                    title = "Consented Camera Photo",
                    summary = "Captured visibly via CameraX & encrypted locally",
                    consentConfirmed = true,
                    uploadToCloud = uploadToCloud,
                )
            },
            onError = { showCameraModal = false },
            onDismiss = { showCameraModal = false },
        )
    }

    decryptedPreview?.let { (title, content) ->
        AlertDialog(
            onDismissRequest = { decryptedPreview = null },
            title = { Text("Decrypted: $title") },
            text = { Text(content, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { decryptedPreview = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun IncidentHistoryCard(
    incident: SosIncident,
    onResolve: () -> Unit,
) {
    val isActive = incident.status == "active" || incident.status == "acknowledged"
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
                Text(
                    text = incident.triggerType.replace('_', ' ').uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = incident.status.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isActive) SosCrimson else SafeGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Text(incident.notes, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = String.format("%.5f, %.5f • Battery %d%% • %s", incident.latitude, incident.longitude, incident.batteryPercent, formatFirestoreTimestamp(incident.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isActive) {
                Button(onClick = onResolve, modifier = Modifier.height(40.dp)) {
                    Text("Mark Resolved")
                }
            }
        }
    }
}

@Composable
private fun EvidenceRecordCard(
    record: EvidenceRecord,
    onDecryptNote: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(record.title, style = MaterialTheme.typography.titleMedium)
                    Text("${record.captureMode} • ${record.encryptionAlgo} • ${formatFirestoreTimestamp(record.createdAt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete Evidence", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (record.summary.isNotBlank()) {
                Text(record.summary, style = MaterialTheme.typography.bodyMedium)
            }
            Text("SHA-256: ${record.sha256Digest.take(24)}…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onDecryptNote != null) {
                OutlinedButton(onClick = onDecryptNote, modifier = Modifier.height(40.dp)) {
                    Text("Decrypt Note")
                }
            }
        }
    }
}
