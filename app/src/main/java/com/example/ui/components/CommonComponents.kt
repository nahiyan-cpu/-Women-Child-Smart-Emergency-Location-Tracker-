package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun OfflineStatusBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_status_banner"),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = "Offline Mode",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = stringResource(R.string.offline_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
fun LocationPermissionBanner(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("location_permission_denied_banner"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOff,
                    contentDescription = "Location Permission Denied",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GPS Location Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = "Grant location access so SOS alerts, geofencing, and safe-place distance tracking work accurately.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.testTag("grant_location_permission_button"),
                ) {
                    Text("Grant GPS")
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    },
                ) {
                    Text("App Settings")
                }
            }
        }
    }
}

@Composable
fun StateLoadingCard(label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun StateErrorCard(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (onRetry != null) {
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Box(Modifier.size(6.dp))
                    Text("Retry")
                }
            }
        }
    }
}

fun formatFirestoreTimestamp(ts: Timestamp?): String {
    if (ts == null) return "Syncing…"
    val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return sdf.format(ts.toDate())
}
