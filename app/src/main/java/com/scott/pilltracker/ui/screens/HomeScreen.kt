package com.scott.pilltracker.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scott.pilltracker.alarm.AlarmScheduler
import com.scott.pilltracker.alarm.NotificationHelper
import com.scott.pilltracker.data.PillRepository
import com.scott.pilltracker.model.PillItem
import com.scott.pilltracker.model.PillLog
import com.scott.pilltracker.model.PillsConfig
import com.scott.pilltracker.model.SyncState
import com.scott.pilltracker.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

fun formatLogTime(log: PillLog?, repository: PillRepository): String? {
    if (log == null) return null
    val date = repository.parseUtcIsoDate(log.timestamp) ?: return null
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
}

@Composable
fun HomeScreen(
    repository: PillRepository,
    config: PillsConfig,
    syncState: SyncState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logs by repository.logsFlow.collectAsState()

    // Reactively compute whether routines and adhoc items were taken today in local device time
    val morningLog = remember(logs) { repository.getLastRoutineTakenToday("morning") }
    val eveningLog = remember(logs) { repository.getLastRoutineTakenToday("evening") }

    val morningTakenTime = remember(morningLog) { formatLogTime(morningLog, repository) }
    val eveningTakenTime = remember(eveningLog) { formatLogTime(eveningLog, repository) }

    val creatineLog = remember(logs) { repository.getLastItemTakenToday("creatine") }
    val glycineLog = remember(logs) { repository.getLastItemTakenToday("glycine") }
    val dogPillLog = remember(logs) { repository.getLastItemTakenToday("dog_pill") }

    val creatineTime = remember(creatineLog) { formatLogTime(creatineLog, repository) }
    val glycineTime = remember(glycineLog) { formatLogTime(glycineLog, repository) }
    val dogPillTime = remember(dogPillLog) { formatLogTime(dogPillLog, repository) }

    var isSyncing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch {
            isSyncing = true
            repository.syncWithCloud()
            isSyncing = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sync & Header Status Bar
        item {
            SyncStatusBar(
                syncState = syncState,
                isSyncing = isSyncing,
                onSyncClick = {
                    scope.launch {
                        isSyncing = true
                        val res = repository.syncWithCloud()
                        isSyncing = false
                        Toast.makeText(
                            context,
                            res.getOrNull() ?: "Sync failed: ${res.exceptionOrNull()?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        // Morning Routine Card
        item {
            val morningRoutine = config.routines["morning"]
            val morningItems = config.items.filter { it.routine.equals("morning", ignoreCase = true) && it.active }

            RoutineCard(
                title = "Morning Stack",
                time = morningRoutine?.time ?: "07:30",
                icon = Icons.Filled.WbSunny,
                iconColor = AccentAmber,
                isTaken = morningLog != null,
                takenTime = morningTakenTime,
                items = morningItems,
                escalateMinutes = morningRoutine?.escalateAfterMinutes ?: 60,
                onTakeAll = { selectedIds ->
                    scope.launch {
                        val skipped = morningItems.map { it.id }.filterNot { selectedIds.contains(it) }
                        repository.logRoutineTaken("morning", selectedIds, skipped)
                        AlarmScheduler(context).cancelEscalationAlarm("morning")
                        NotificationHelper(context).cancelNotification("morning")
                        Toast.makeText(context, "Morning stack logged! Uploading...", Toast.LENGTH_SHORT).show()
                        isSyncing = true
                        repository.syncWithCloud()
                        isSyncing = false
                    }
                }
            )
        }

        // Evening Routine Card
        item {
            val eveningRoutine = config.routines["evening"]
            val eveningItems = config.items.filter { it.routine.equals("evening", ignoreCase = true) && it.active }

            RoutineCard(
                title = "Evening Stack",
                time = eveningRoutine?.time ?: "20:00",
                icon = Icons.Filled.NightsStay,
                iconColor = AccentPurple,
                isTaken = eveningLog != null,
                takenTime = eveningTakenTime,
                items = eveningItems,
                escalateMinutes = eveningRoutine?.escalateAfterMinutes ?: 60,
                onTakeAll = { selectedIds ->
                    scope.launch {
                        val skipped = eveningItems.map { it.id }.filterNot { selectedIds.contains(it) }
                        repository.logRoutineTaken("evening", selectedIds, skipped)
                        AlarmScheduler(context).cancelEscalationAlarm("evening")
                        NotificationHelper(context).cancelNotification("evening")
                        Toast.makeText(context, "Evening stack logged! Uploading...", Toast.LENGTH_SHORT).show()
                        isSyncing = true
                        repository.syncWithCloud()
                        isSyncing = false
                    }
                }
            )
        }

        // Quick / Ad-hoc Logging Card
        item {
            AdhocLoggingCard(
                creatineTaken = creatineLog != null,
                creatineTime = creatineTime,
                glycineTaken = glycineLog != null,
                glycineTime = glycineTime,
                dogPillTaken = dogPillLog != null,
                dogPillTime = dogPillTime,
                onLogAdhoc = { id, name ->
                    scope.launch {
                        repository.logAdhocTaken(id, "Logged: $name")
                        Toast.makeText(context, "$name logged! Uploading...", Toast.LENGTH_SHORT).show()
                        isSyncing = true
                        repository.syncWithCloud()
                        isSyncing = false
                    }
                }
            )
        }
    }
}

@Composable
fun SyncStatusBar(
    syncState: SyncState,
    isSyncing: Boolean,
    onSyncClick: () -> Unit
) {
    val (statusText, statusColor, icon) = when (syncState) {
        SyncState.SYNCED -> Triple("Cloud Synced", AccentGreen, Icons.Filled.CloudDone)
        SyncState.PENDING_UPLOAD -> Triple("Pending Upload", AccentBlue, Icons.Filled.CloudUpload)
        SyncState.STALE_CACHE_WARNING -> Triple("Offline > 7 Days (Sync Recommended)", AccentRed, Icons.Filled.Warning)
        SyncState.OFFLINE_CACHE -> Triple("Offline Cache", TextSecondary, Icons.Filled.CloudOff)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgSurface)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
            Column {
                Text(statusText, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Source of Truth: forusers.com", color = TextMuted, fontSize = 11.sp)
            }
        }

        IconButton(
            onClick = onSyncClick,
            enabled = !isSyncing,
            modifier = Modifier.size(36.dp)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AccentBlue, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Sync, contentDescription = "Sync", tint = AccentBlue)
            }
        }
    }
}

@Composable
fun RoutineCard(
    title: String,
    time: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isTaken: Boolean,
    takenTime: String?,
    items: List<PillItem>,
    escalateMinutes: Int,
    onTakeAll: (List<String>) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedItemIds = remember(items) { mutableStateListOf(*items.map { it.id }.toTypedArray()) }
    var showRetakeConfirmDialog by remember { mutableStateOf(false) }

    if (showRetakeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRetakeConfirmDialog = false },
            title = { Text("Retake $title?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                val timeMsg = if (!takenTime.isNullOrBlank()) " at $takenTime" else ""
                Text(
                    "You have already taken your $title today$timeMsg.\n\nDo you really want to take it again?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRetakeConfirmDialog = false
                        onTakeAll(selectedItemIds.toList())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Yes, Take Again", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showRetakeConfirmDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = BgSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (isTaken) AccentGreen.copy(alpha = 0.5f) else BorderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = BgSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("$time • Quiet then Alarm (${escalateMinutes}m)", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                if (isTaken) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AccentGreen.copy(alpha = 0.15f))
                            .border(1.dp, AccentGreen.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                            val label = if (!takenTime.isNullOrBlank()) "Taken at $takenTime" else "Taken Today"
                            Text(label, color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Prescription highlight badge
            val prescriptions = items.filter { it.isPrescription }
            if (prescriptions.isNotEmpty()) {
                prescriptions.forEach { rx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentRed.copy(alpha = 0.12f))
                            .border(1.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.MedicalServices, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                            Text("${rx.name} ${rx.dosage}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("🚨 Prescription", color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Summary of supplements
            val otherItemsCount = items.size - prescriptions.size
            if (otherItemsCount > 0) {
                Text(
                    "+ $otherItemsCount other active supplements (${items.filterNot { it.isPrescription }.joinToString { it.name }})",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2
                )
            }

            // Expandable checklist
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items.forEach { item ->
                        val isChecked = selectedItemIds.contains(item.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgCard)
                                .clickable {
                                    if (isChecked) selectedItemIds.remove(item.id) else selectedItemIds.add(item.id)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) selectedItemIds.add(item.id) else selectedItemIds.remove(item.id)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AccentBlue,
                                    uncheckedColor = TextMuted
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${item.name} (${item.dosage})",
                                    color = if (item.isPrescription) AccentRed else TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (item.isPrescription) FontWeight.Bold else FontWeight.Normal
                                )
                                if (item.timingNotes.isNotBlank()) {
                                    Text(item.timingNotes, color = AccentAmber, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.weight(0.4f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BorderColor))
                ) {
                    Text(if (isExpanded) "Hide" else "Details", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        if (isTaken) {
                            showRetakeConfirmDialog = true
                        } else {
                            onTakeAll(selectedItemIds.toList())
                        }
                    },
                    modifier = Modifier.weight(0.6f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTaken) AccentGreen.copy(alpha = 0.18f) else AccentBlue,
                        contentColor = if (isTaken) AccentGreen else TextPrimary
                    ),
                    border = if (isTaken) ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(AccentGreen.copy(alpha = 0.4f))
                    ) else null
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isTaken) AccentGreen else TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isTaken) "Taken" else "Take All",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AdhocLoggingCard(
    creatineTaken: Boolean,
    creatineTime: String?,
    glycineTaken: Boolean,
    glycineTime: String?,
    dogPillTaken: Boolean,
    dogPillTime: String?,
    onLogAdhoc: (String, String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = BgSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Quick / Ad-hoc Logging", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("No scheduled alarm • One-tap log", color = TextSecondary, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AdhocItemRow(
                title = "Creatine 5g",
                subtitle = if (creatineTaken && !creatineTime.isNullOrBlank()) "Taken today at $creatineTime" else "In Morning Coffee",
                icon = Icons.Filled.Coffee,
                isTaken = creatineTaken,
                takenTime = creatineTime,
                actionLabel = "Log Taken",
                takenLabel = "Taken",
                onLog = { onLogAdhoc("creatine", "Creatine 5g") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            AdhocItemRow(
                title = "Glycine 1500mg",
                subtitle = if (glycineTaken && !glycineTime.isNullOrBlank()) "Taken today at $glycineTime" else "In Evening Tea",
                icon = Icons.Filled.EmojiFoodBeverage,
                isTaken = glycineTaken,
                takenTime = glycineTime,
                actionLabel = "Log Taken",
                takenLabel = "Taken",
                onLog = { onLogAdhoc("glycine", "Glycine 1500mg") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            AdhocItemRow(
                title = "Dog Pill",
                subtitle = if (dogPillTaken && !dogPillTime.isNullOrBlank()) "Given today at $dogPillTime" else "Daily Dog Medication",
                icon = Icons.Filled.Pets,
                isTaken = dogPillTaken,
                takenTime = dogPillTime,
                actionLabel = "Log Given",
                takenLabel = "Given",
                onLog = { onLogAdhoc("dog_pill", "Dog Pill") }
            )
        }
    }
}

@Composable
fun AdhocItemRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isTaken: Boolean = false,
    takenTime: String? = null,
    actionLabel: String = "Log Taken",
    takenLabel: String = "Taken",
    onLog: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Log $title Again?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                val timeMsg = if (!takenTime.isNullOrBlank()) " at $takenTime" else ""
                Text(
                    "You have already logged $title today$timeMsg.\n\nDo you really want to log it again?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onLog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Text("Yes, Log Again", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = BgSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgCard)
            .border(1.dp, if (isTaken) AccentGreen.copy(alpha = 0.4f) else BorderColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = if (isTaken) AccentGreen else TextSecondary, modifier = Modifier.size(20.dp))
            Column {
                Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = if (isTaken) AccentGreen else TextSecondary, fontSize = 11.sp)
            }
        }

        Button(
            onClick = {
                if (isTaken) {
                    showConfirmDialog = true
                } else {
                    onLog()
                }
            },
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTaken) AccentGreen.copy(alpha = 0.2f) else AccentGreen.copy(alpha = 0.25f),
                contentColor = AccentGreen
            ),
            border = if (isTaken) ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(AccentGreen.copy(alpha = 0.5f))
            ) else null
        ) {
            if (isTaken) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentGreen)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(if (isTaken) takenLabel else actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
