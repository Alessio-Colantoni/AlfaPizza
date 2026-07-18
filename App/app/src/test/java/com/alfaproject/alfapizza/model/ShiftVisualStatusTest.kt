package com.alfaproject.alfapizza.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftVisualStatusTest {
    @Test
    fun `uncovered rider takes precedence over its stored color`() {
        assertEquals(ShiftVisualStatus.UNCOVERED, shiftVisualStatus("transparent", -99))
        assertEquals(ShiftVisualStatus.UNCOVERED, shiftVisualStatus("red", -99))
    }

    @Test
    fun `constraint colors map to accessible states`() {
        assertEquals(ShiftVisualStatus.ABSOLUTE_CONSTRAINT, shiftVisualStatus("red", 12))
        assertEquals(ShiftVisualStatus.PREFERENCE, shiftVisualStatus("yellow", 12))
        assertEquals(ShiftVisualStatus.NONE, shiftVisualStatus("transparent", 12))
    }
}
