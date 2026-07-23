package com.alfaproject.alfapizza.model

enum class ShiftVisualStatus {
    NONE,
    ABSOLUTE_CONSTRAINT,
    PREFERENCE,
    UNCOVERED
}

fun shiftVisualStatus(color: String, riderCode: Int): ShiftVisualStatus {
    val normalizedColor = color.lowercase().trim()
    return when {
        riderCode == -99 -> ShiftVisualStatus.UNCOVERED
        normalizedColor == "red" || normalizedColor.contains("red") ->
            ShiftVisualStatus.ABSOLUTE_CONSTRAINT
        normalizedColor == "yellow" || normalizedColor.contains("yellow") ->
            ShiftVisualStatus.PREFERENCE
        else -> ShiftVisualStatus.NONE
    }
}
