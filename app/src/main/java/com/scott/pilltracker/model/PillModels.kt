package com.scott.pilltracker.model

import com.google.gson.annotations.SerializedName
import java.util.Date

enum class RoutineType(val key: String, val displayName: String) {
    MORNING("morning", "Morning Stack"),
    EVENING("evening", "Evening Stack"),
    ADHOC("adhoc", "Quick / Ad-hoc")
}

data class PillRoutineConfig(
    @SerializedName("time") val time: String = "07:30", // HH:MM 24h format
    @SerializedName("label") val label: String = "Morning Stack",
    @SerializedName("quiet_remind") val quietRemind: Boolean = true,
    @SerializedName("escalate_alarm") val escalateAlarm: Boolean = true,
    @SerializedName("escalate_after_minutes") val escalateAfterMinutes: Int = 60
)

data class PillItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("dosage") val dosage: String,
    @SerializedName("routine") val routine: String, // "morning", "evening", "adhoc"
    @SerializedName("importance") val importance: String, // "prescription", "supplement", "optional"
    @SerializedName("timing_notes") val timingNotes: String = "",
    @SerializedName("active") val active: Boolean = true,
    @SerializedName("sort_order") val sortOrder: Int = 0,
    @SerializedName("requires_alarm") val requiresAlarm: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String = ""
) {
    val isPrescription: Boolean
        get() = importance.equals("prescription", ignoreCase = true)
}

data class PillsConfig(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("updated_at") val updatedAt: String = "",
    @SerializedName("sync_interval_days") val syncIntervalDays: Int = 7,
    @SerializedName("routines") val routines: Map<String, PillRoutineConfig> = emptyMap(),
    @SerializedName("items") val items: List<PillItem> = emptyList()
)

data class PillLog(
    @SerializedName("id") val id: String,
    @SerializedName("timestamp") val timestamp: String, // ISO 8601 string
    @SerializedName("logged_at") val loggedAt: String,
    @SerializedName("routine") val routine: String,
    @SerializedName("items_taken") val itemsTaken: List<String> = emptyList(),
    @SerializedName("items_skipped") val itemsSkipped: List<String> = emptyList(),
    @SerializedName("notes") val notes: String = "",
    @SerializedName("device_id") val deviceId: String = "android_app",
    @Transient var isSynced: Boolean = false
)

enum class SyncState {
    SYNCED,
    PENDING_UPLOAD,
    OFFLINE_CACHE,
    STALE_CACHE_WARNING
}
