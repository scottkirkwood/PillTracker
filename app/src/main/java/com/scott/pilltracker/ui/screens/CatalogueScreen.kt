package com.scott.pilltracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scott.pilltracker.data.PillRepository
import com.scott.pilltracker.model.PillItem
import com.scott.pilltracker.model.PillsConfig
import com.scott.pilltracker.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CatalogueScreen(
    repository: PillRepository,
    config: PillsConfig
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf("all") }

    val filteredItems = remember(config.items, selectedFilter) {
        when (selectedFilter) {
            "morning" -> config.items.filter { it.routine.equals("morning", ignoreCase = true) }
            "evening" -> config.items.filter { it.routine.equals("evening", ignoreCase = true) }
            "adhoc" -> config.items.filter { it.routine.equals("adhoc", ignoreCase = true) }
            else -> config.items
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Source of Truth Banner
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgSurface)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                Text(
                    "Pill catalogue and dosages are synced from forusers.com (Source of Truth). Toggle items below when out of stock.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Filter Chips Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipItem(label = "All (${config.items.size})", isSelected = selectedFilter == "all", onClick = { selectedFilter = "all" })
                FilterChipItem(label = "Morning", isSelected = selectedFilter == "morning", onClick = { selectedFilter = "morning" })
                FilterChipItem(label = "Evening", isSelected = selectedFilter == "evening", onClick = { selectedFilter = "evening" })
                FilterChipItem(label = "Ad-hoc", isSelected = selectedFilter == "adhoc", onClick = { selectedFilter = "adhoc" })
            }
        }

        // Pill Item Cards
        items(filteredItems, key = { it.id }) { item ->
            CatalogueItemCard(
                item = item,
                onToggleStock = { active ->
                    scope.launch {
                        repository.toggleItemStock(item.id, active)
                        Toast.makeText(
                            context,
                            "${item.name} marked ${if (active) "In Stock" else "Out of Stock"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }
}

@Composable
fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentBlue,
            selectedLabelColor = TextPrimary,
            containerColor = BgSurface,
            labelColor = TextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = if (isSelected) AccentBlue else BorderColor,
            selectedBorderColor = AccentBlue,
            enabled = true,
            selected = isSelected
        )
    )
}

@Composable
fun CatalogueItemCard(
    item: PillItem,
    onToggleStock: (Boolean) -> Unit
) {
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
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(item.dosage, color = TextSecondary, fontSize = 13.sp)

                    if (item.isPrescription) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentRed.copy(alpha = 0.15f))
                                .border(1.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("🚨 Rx", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Routine: ${item.routine.replaceFirstChar { it.uppercase() }}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    if (item.timingNotes.isNotBlank()) {
                        Text("• ${item.timingNotes}", color = AccentAmber, fontSize = 11.sp)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Switch(
                    checked = item.active,
                    onCheckedChange = onToggleStock,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = BgCard
                    )
                )
                Text(
                    if (item.active) "In Stock" else "Out of Stock",
                    color = if (item.active) AccentGreen else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
