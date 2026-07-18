package com.alfaproject.alfapizza.network

import com.alfaproject.alfapizza.BuildConfig

object Constants {
    val BASE_URL = BuildConfig.BASE_URL
    const val NETWORK_TIMEOUT_MS = 60000 // Aumentato a 60 secondi per calcoli complessi
    const val MAX_RETRIES = 3

    object Endpoints {
        const val USERS = "/api/users"
        const val SWAPS = "/api/swaps"
        const val WEEK_STRUCTURE = "/api/weekStructure"
        const val CONSTRAINTS = "/api/constraints"
        const val CALENDARS = "/api/calendars"
        const val NOTIFICATIONS = "/api/notifications"
        const val GENERATE_CALENDAR = "/api/generateCalendar"
    }
}
