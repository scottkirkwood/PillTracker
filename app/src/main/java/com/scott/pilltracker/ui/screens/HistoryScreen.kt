package com.scott.pilltracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scott.pilltracker.data.PillRepository
import com.scott.pilltracker.model.PillLog
import com.scott.pilltracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    repository: PillRepository
) {
    val logs by repository.logsFlow.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Adherence Stats
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = BgSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Adherence & Audit History", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "All intakes recorded on device (Android is Source of Truth for logs). Automatically backed up to forusers.com.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatPill(label = "Total Logs", value = "${logs.size}")
                        StatPill(label = "Prescriptions", value = "100%", valueColor = AccentGreen)
                        StatPill(label = "Unsynced", value = "${logs.count { !it.isSynced }}", valueColor = if (logs.any { !it.isSynced }) AccentAmber else AccentGreen)
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No intake logs recorded yet.", color = TextMuted, fontSize = 14.sp)
                }
            }
        } else {
            items(logs, key = { it.id }) { log ->
                HistoryLogCard(log = log)
            }
        }
    }
}

@Composable
fun StatPill(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = TextPrimary
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BgCard)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
fun HistoryLogCard(log: PillLog) {
    val (icon, iconColor) = when (log.routine.lowercase()) {
        "morning" -> Icons.Filled.WbSunny to AccentAmber
        "evening" -> Icons.Filled.NightsStay to AccentPurple
        else -> Icons.Filled.Bolt to AccentGreen
    }

    val displayDate = try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val date = parser.parse(log.timestamp.take(19))
        SimpleDateFormat("EEE, MMM d • h:mm a", Locale.US).format(date ?: Date())
    } catch (e: Exception) {
        log.timestamp
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = BgSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                Column {
                    Text(
                        "${log.routine.replaceFirstChar { it.uppercase() }} Routine",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(displayDate, color = TextSecondary, fontSize = 11.sp)
                    if (log.itemsTaken.isNotEmpty()) {
                        Text("Taken: ${log.itemsTaken.joinToString()}", color = TextMuted, fontSize = 11.sp)
                    }
                    if (log.notes.isNotBlank()) {
                        Text("\"${log.notes}\"", color = AccentAmber, fontSize = 11.sp)
                    }
                }
            }

            Icon(
                if (log.isSynced) Icons.Filled.CloudDone else Icons.Filled.CloudUpload,
                contentDescription = null,
                tint = if (log.isSynced) AccentGreen else AccentBlue,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
