package com.scott.pilltracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scott.pilltracker.alarm.NotificationHelper
import com.scott.pilltracker.data.PillRepository
import com.scott.pilltracker.model.PillsConfig
import com.scott.pilltracker.model.SyncState
import com.scott.pilltracker.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(
    repository: PillRepository,
    config: PillsConfig,
    syncState: SyncState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var serverUrlText by remember { mutableStateOf(repository.baseUrl) }
    var isSyncing by remember { mutableStateOf(false) }

    val lastSyncFormatted = remember(repository.lastSyncTimestamp) {
        if (repository.lastSyncTimestamp == 0L) "Never"
        else SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).format(Date(repository.lastSyncTimestamp))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Scheduled Routines Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = BgSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Reminder & Alarm Schedules", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    val morning = config.routines["morning"]
                    val evening = config.routines["evening"]

                    ScheduleItemRow(
                        title = "Morning Stack",
                        time = morning?.time ?: "07:30",
                        rule = "Quiet notification -> Loud alarm after ${morning?.escalateAfterMinutes ?: 60}m"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ScheduleItemRow(
                        title = "Evening Stack",
                        time = evening?.time ?: "20:00",
                        rule = "Quiet notification -> Loud alarm after ${evening?.escalateAfterMinutes ?: 60}m"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            if (com.scott.pilltracker.alarm.AlarmRingtonePlayer.isPlaying) {
                                com.scott.pilltracker.alarm.AlarmRingtonePlayer.stop()
                                Toast.makeText(context, "Alarm stopped", Toast.LENGTH_SHORT).show()
                            } else {
                                com.scott.pilltracker.alarm.AlarmRingtonePlayer.play(context, 5000L)
                                Toast.makeText(context, "Playing test alarm sound for 5s...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                    ) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = "Test Alarm", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Audible Alarm Sound (5s)")
                    }
                }
            }
        }

        // Offline Cache & Cloud Sync Settings Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = BgSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cloud Sync & 7-Day Offline Policy", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Source of Truth for medications and schedules is forusers.com. Cached data is assumed valid for 7 days when completely offline.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Last Synced: $lastSyncFormatted", color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = serverUrlText,
                        onValueChange = { serverUrlText = it },
                        label = { Text("Server URL") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            repository.baseUrl = serverUrlText.trim()
                            scope.launch {
                                isSyncing = true
                                val res = repository.syncWithCloud()
                                isSyncing = false
                                Toast.makeText(context, res.getOrNull() ?: "Sync error", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        enabled = !isSyncing
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSyncing) "Syncing..." else "Save & Sync Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Notification Diagnostic / Test Buttons
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = BgSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Notification & Alarm Diagnostics", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Test both reminder stages on this phone:", color = TextSecondary, fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Quiet Notification
                    OutlinedButton(
                        onClick = {
                            val activeMorning = config.items.filter { it.routine == "morning" && it.active }
                            NotificationHelper(context).showQuietNotification(
                                routine = "morning",
                                title = "Test: Morning Stack (Quiet)",
                                message = "Silent heads-up with Done & Remind Later buttons",
                                activeItems = activeMorning
                            )
                            Toast.makeText(context, "Quiet notification posted to shade!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(AccentBlue))
                    ) {
                        Icon(Icons.Filled.NotificationsNone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Stage 1: Quiet Reminder (Silent)", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Test Escalated Alarm
                    Button(
                        onClick = {
                            val activeMorning = config.items.filter { it.routine == "morning" && it.active }
                            NotificationHelper(context).showEscalatedAlarm(
                                routine = "morning",
                                title = "Test: Morning Stack (Escalated)",
                                message = "Audible alarm ringtone + vibration trigger",
                                activeItems = activeMorning
                            )
                            Toast.makeText(context, "Escalated alarm fired!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Stage 2: Escalated Alarm (Loud Beep)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleItemRow(
    title: String,
    time: String,
    rule: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BgCard)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(time, color = AccentAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(rule, color = TextMuted, fontSize = 11.sp)
    }
}
