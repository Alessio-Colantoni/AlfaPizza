package com.alfaproject.alfapizza.time

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

const val APP_TIME_ZONE_ID = "Europe/Rome"

object AppTime {
    private val timeZone: TimeZone
        get() = TimeZone.getTimeZone(APP_TIME_ZONE_ID)

    fun dayOfWeek(date: Date = Date()): Int {
        val calendar = Calendar.getInstance(timeZone, Locale.ITALY).apply {
            time = date
        }
        val calendarDay = calendar.get(Calendar.DAY_OF_WEEK)
        return if (calendarDay == Calendar.SUNDAY) 6 else calendarDay - Calendar.MONDAY
    }

    fun dateInCurrentWeek(dayIndex: Int, now: Date = Date()): Date {
        val normalizedDayIndex = dayIndex.coerceIn(0, 6)
        return Calendar.getInstance(timeZone, Locale.ITALY).apply {
            time = now
            add(Calendar.DAY_OF_YEAR, normalizedDayIndex - dayOfWeek(now))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    fun timeZone(): TimeZone = timeZone
}
