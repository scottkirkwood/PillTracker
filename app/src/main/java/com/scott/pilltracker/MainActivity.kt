package com.scott.pilltracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.scott.pilltracker.data.PillRepository
import com.scott.pilltracker.ui.screens.CatalogueScreen
import com.scott.pilltracker.ui.screens.HistoryScreen
import com.scott.pilltracker.ui.screens.HomeScreen
import com.scott.pilltracker.ui.screens.SettingsScreen
import com.scott.pilltracker.ui.theme.*

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: PillRepository

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PillRepository.getInstance(this)

        checkNotificationPermission()

        setContent {
            PillTrackerTheme {
                MainAppScaffold(repository = repository)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.scott.pilltracker.alarm.AlarmRingtonePlayer.stop()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.syncWithCloud()
            } catch (_: Exception) {}
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

enum class NavigationItem(val label: String, val iconSelected: androidx.compose.ui.graphics.vector.ImageVector, val iconUnselected: androidx.compose.ui.graphics.vector.ImageVector) {
    TODAY("Today", Icons.Filled.CalendarToday, Icons.Outlined.CalendarToday),
    CATALOGUE("Catalogue", Icons.Filled.ListAlt, Icons.Outlined.ListAlt),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(repository: PillRepository) {
    val config by repository.configFlow.collectAsState()
    val syncState by repository.syncStateFlow.collectAsState()
    var currentScreen by remember { mutableStateOf(NavigationItem.TODAY) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (currentScreen) {
                                NavigationItem.TODAY -> "Pill & Supplement Hub"
                                NavigationItem.CATALOGUE -> "Pill Catalogue"
                                NavigationItem.HISTORY -> "Intake History"
                                NavigationItem.SETTINGS -> "Settings & Sync"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = BgSurface,
                tonalElevation = 8.dp
            ) {
                NavigationItem.values().forEach { item ->
                    val isSelected = currentScreen == item
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = item },
                        icon = {
                            Icon(
                                if (isSelected) item.iconSelected else item.iconUnselected,
                                contentDescription = item.label,
                                tint = if (isSelected) AccentBlue else TextMuted
                            )
                        },
                        label = {
                            Text(
                                item.label,
                                color = if (isSelected) AccentBlue else TextMuted,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = AccentBlue.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        },
        containerColor = BgPrimary
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BgPrimary)
        ) {
            when (currentScreen) {
                NavigationItem.TODAY -> HomeScreen(
                    repository = repository,
                    config = config,
                    syncState = syncState
                )
                NavigationItem.CATALOGUE -> CatalogueScreen(
                    repository = repository,
                    config = config
                )
                NavigationItem.HISTORY -> HistoryScreen(
                    repository = repository
                )
                NavigationItem.SETTINGS -> SettingsScreen(
                    repository = repository,
                    config = config,
                    syncState = syncState
                )
            }
        }
    }
}
