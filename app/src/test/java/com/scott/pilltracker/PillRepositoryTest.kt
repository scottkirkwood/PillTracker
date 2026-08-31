package com.scott.pilltracker

import com.scott.pilltracker.model.PillItem
import com.scott.pilltracker.model.PillRoutineConfig
import com.scott.pilltracker.model.PillsConfig
import org.junit.Assert.*
import org.junit.Test

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
}
