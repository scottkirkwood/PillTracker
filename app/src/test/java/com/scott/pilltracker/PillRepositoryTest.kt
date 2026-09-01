package com.scott.pilltracker

import com.scott.pilltracker.model.PillItem
import com.scott.pilltracker.model.PillRoutineConfig
import com.scott.pilltracker.model.PillsConfig
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class PillRepositoryTest {

    @Test
    fun testDefaultSeedConfiguration() {
        val morningRoutine = PillRoutineConfig(
            time = "07:30",
            label = "Morning Stack",
            quietRemind = true,
            escalateAlarm = true,
            escalateAfterMinutes = 60
        )
        val eveningRoutine = PillRoutineConfig(
            time = "20:00",
            label = "Evening Stack",
            quietRemind = true,
            escalateAlarm = true,
            escalateAfterMinutes = 60
        )

        val items = listOf(
            PillItem("candesartan", "Candesartan", "8mg", "morning", "prescription", "Doctor's prescription - morning BP", active = true, sortOrder = 1, requiresAlarm = true),
            PillItem("rosuvastatin", "Rosuvastatin", "10mg", "evening", "prescription", "Doctor's prescription - evening cholesterol", active = true, sortOrder = 11, requiresAlarm = true),
            PillItem("creatine", "Creatine", "5g", "adhoc", "optional", "Add to morning coffee", active = true, sortOrder = 15),
            PillItem("glycine", "Glycine", "1500mg", "adhoc", "optional", "Add to evening tea", active = true, sortOrder = 16),
            PillItem("dog_pill", "Dog Pill", "1 dose", "adhoc", "optional", "Give pill to dog", active = true, sortOrder = 17)
        )

        val config = PillsConfig(
            version = 1,
            syncIntervalDays = 7,
            routines = mapOf("morning" to morningRoutine, "evening" to eveningRoutine),
            items = items
        )

        assertEquals(7, config.syncIntervalDays)
        assertEquals(5, config.items.size)

        val candesartan = config.items.first { it.id == "candesartan" }
        assertTrue(candesartan.isPrescription)
        assertTrue(candesartan.requiresAlarm)
        assertEquals("8mg", candesartan.dosage)

        val rosuvastatin = config.items.first { it.id == "rosuvastatin" }
        assertTrue(rosuvastatin.isPrescription)
        assertTrue(rosuvastatin.requiresAlarm)
        assertEquals("10mg", rosuvastatin.dosage)

        val dogPill = config.items.first { it.id == "dog_pill" }
        assertEquals("adhoc", dogPill.routine)
        assertFalse(dogPill.isPrescription)
    }

    @Test
    fun testSevenDayStaleLogic() {
        val syncIntervalDays = 7
        val maxAllowedMillis = syncIntervalDays * 86400000L

        val recentSync = System.currentTimeMillis() - (2 * 86400000L) // 2 days ago
        val staleSync = System.currentTimeMillis() - (9 * 86400000L)  // 9 days ago

        val isRecentStale = (System.currentTimeMillis() - recentSync) > maxAllowedMillis
        val isOldStale = (System.currentTimeMillis() - staleSync) > maxAllowedMillis

        assertFalse("2 days offline should NOT be stale", isRecentStale)
        assertTrue("9 days offline SHOULD be marked stale", isOldStale)
    }

    @Test
    fun testSameLocalDayComparisonAcrossUtcMidnight() {
        // Suppose user is in EDT (UTC-4)
        val origTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

            // Yesterday was Aug 31, 2026. Evening pills taken at 8:30 PM EDT.
            // In UTC, this is 2026-09-01T00:30:00Z (crossing midnight into Sept 1 in UTC!)
            val utcTimestampYesterdayEvening = "2026-09-01T00:30:00Z"

            val sdfUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val logDate = sdfUtc.parse(utcTimestampYesterdayEvening)!!

            // Today is Sept 1, 2026 at 10:00 AM EDT
            val calToday = Calendar.getInstance().apply {
                set(2026, Calendar.SEPTEMBER, 1, 10, 0, 0)
            }
            val todayDate = calToday.time

            // Compare local calendar days
            val calLog = Calendar.getInstance().apply { time = logDate }

            // calLog in EDT should be Aug 31
            assertEquals(Calendar.AUGUST, calLog.get(Calendar.MONTH))
            assertEquals(31, calLog.get(Calendar.DAY_OF_MONTH))

            // calToday in EDT is Sept 1
            assertEquals(Calendar.SEPTEMBER, calToday.get(Calendar.MONTH))
            assertEquals(1, calToday.get(Calendar.DAY_OF_MONTH))

            val isSameDay = (calLog.get(Calendar.YEAR) == calToday.get(Calendar.YEAR) &&
                    calLog.get(Calendar.DAY_OF_YEAR) == calToday.get(Calendar.DAY_OF_YEAR))

            assertFalse("Evening pills taken yesterday at 8:30pm EDT should NOT be considered taken today!", isSameDay)
        } finally {
            TimeZone.setDefault(origTz)
        }
    }
}
