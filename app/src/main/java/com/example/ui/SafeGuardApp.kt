package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.remote.BackendDispatchClient
import com.example.data.repository.SafetyRepository
import com.example.security.CryptoVaultManager
import com.example.service.LocationAndGeofenceManager
import com.example.service.SirenPlayer
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.ContactsAndProfileScreen
import com.example.ui.screens.HistoryAndEvidenceScreen
import com.example.ui.screens.HomeSosScreen
import com.example.ui.screens.SafetyMapScreen
import com.example.ui.viewmodel.SafetyViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object MapRoute
@Serializable data object ContactsRoute
@Serializable data object HistoryRoute
@Serializable data object AdminRoute

private data class NavDestinationItem(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@Composable
fun SafeGuardAppRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedSafetyShell(
                currentUserId = user.uid,
                currentUserEmail = user.email,
                currentUserName = user.displayName,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedSafetyShell(
    currentUserId: String,
    currentUserEmail: String?,
    currentUserName: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: SafetyViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SafetyViewModel(
                    appContext = app,
                    repository = SafetyRepository(db, Firebase.auth),
                    locationManager = LocationAndGeofenceManager(app),
                    sirenPlayer = SirenPlayer(app),
                    cryptoVault = CryptoVaultManager(app),
                    backendClient = BackendDispatchClient(),
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    currentUserName = currentUserName,
                )
            }
        }
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (results[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        viewModel.onLocationPermissionResult(granted)
    }

    val requestLocationPermissions = {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        locationPermissionLauncher.launch(perms)
    }

    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBanner()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val destinations = remember(viewModel.isAdminUser) {
        buildList {
            add(NavDestinationItem(HomeRoute, "SOS", Icons.Filled.Shield, Icons.Outlined.Shield))
            add(NavDestinationItem(MapRoute, "Map", Icons.Filled.Map, Icons.Outlined.Map))
            add(NavDestinationItem(ContactsRoute, "Circle", Icons.Filled.Contacts, Icons.Outlined.Contacts))
            add(NavDestinationItem(HistoryRoute, "Vault", Icons.Filled.History, Icons.Outlined.History))
            if (viewModel.isAdminUser) {
                add(NavDestinationItem(AdminRoute, "Admin", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings))
            }
        }
    }

    fun navigateTo(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "SafeGuard SOS",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        destinations.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                    )
                                },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail {
                        destinations.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                    )
                                },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.weight(1f),
                ) {
                    composable<HomeRoute> {
                        HomeSosScreen(
                            viewModel = viewModel,
                            onRequestLocationPermission = requestLocationPermissions,
                            onNavigateToContacts = { navigateTo(ContactsRoute) },
                            onNavigateToEvidence = { navigateTo(HistoryRoute) },
                        )
                    }
                    composable<MapRoute> {
                        SafetyMapScreen(
                            viewModel = viewModel,
                            onRequestLocationPermission = requestLocationPermissions,
                        )
                    }
                    composable<ContactsRoute> {
                        ContactsAndProfileScreen(
                            viewModel = viewModel,
                            onSignOutClick = {
                                signOutUser(credentialManager, scope)
                            },
                        )
                    }
                    composable<HistoryRoute> {
                        HistoryAndEvidenceScreen(viewModel = viewModel)
                    }
                    composable<AdminRoute> {
                        AdminDashboardScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
