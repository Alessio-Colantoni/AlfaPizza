package com.alfaproject.alfapizza.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.Date

class AppTimeTest {
    @Test
    fun `day changes at midnight in Rome during daylight saving time`() {
        val sundayInRome = Date.from(Instant.parse("2026-07-12T21:59:59Z"))
        val mondayInRome = Date.from(Instant.parse("2026-07-12T22:00:00Z"))

        assertEquals(6, AppTime.dayOfWeek(sundayInRome))
        assertEquals(0, AppTime.dayOfWeek(mondayInRome))
    }

    @Test
    fun `current week starts at Rome midnight`() {
        val wednesday = Date.from(Instant.parse("2026-07-15T10:00:00Z"))

        val monday = AppTime.dateInCurrentWeek(0, wednesday)

        assertEquals(Instant.parse("2026-07-12T22:00:00Z"), monday.toInstant())
    }

    @Test
    fun `day index is clamped to the supported week`() {
        val wednesday = Date.from(Instant.parse("2026-07-15T10:00:00Z"))

        assertEquals(
            AppTime.dateInCurrentWeek(0, wednesday),
            AppTime.dateInCurrentWeek(-10, wednesday)
        )
        assertEquals(
            AppTime.dateInCurrentWeek(6, wednesday),
            AppTime.dateInCurrentWeek(10, wednesday)
        )
    }
}
