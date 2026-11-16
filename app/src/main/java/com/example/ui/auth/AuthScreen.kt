package com.example.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var isCheckingAutoSignIn by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingAutoSignIn = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingAutoSignIn = false
            },
            scope = scope,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "SafeGuard SOS Emblem",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(com.example.R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Women & Child Smart Safety Shield",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box {
                    Image(
                        painter = painterResource(id = com.example.R.drawable.img_safeguard_hero_1794832370866),
                        contentDescription = "Protective community safety dome illustration",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.65f),
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp),
                    ) {
                        Text(
                            text = "One-Touch Protection & Live Beacon",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            text = "Real-time GPS sharing, geofence alerts, encrypted evidence & community danger zones.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TrustFeatureRow(
                    icon = Icons.Filled.MyLocation,
                    title = "Zero-Trust Location Privacy",
                    subtitle = "Your live beacon and emergency contacts are isolated to your verified Google account.",
                )
                TrustFeatureRow(
                    icon = Icons.Filled.Lock,
                    title = "Hardware-Encrypted Evidence Vault",
                    subtitle = "Consent-only photo, audio, and note capture encrypted with AES-256-GCM.",
                )
                TrustFeatureRow(
                    icon = Icons.Filled.VerifiedUser,
                    title = "Transparent OS Permissions",
                    subtitle = "No covert recording or hidden background tracking. Every sensor action is visible.",
                )
            }

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isLoading = false
                        },
                    )
                },
                enabled = !isLoading && !isCheckingAutoSignIn,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("google_sign_in_button"),
            ) {
                if (isLoading || isCheckingAutoSignIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Text(
                        text = stringResource(com.example.R.string.sign_in_google),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrustFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
