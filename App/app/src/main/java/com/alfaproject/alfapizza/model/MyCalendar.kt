package com.alfaproject.alfapizza.model

data class MyCalendar (
    var lastUpdate: String,
    var isNext: Boolean,
    var days: List<Workday>,
    var publicationDay: Int,
    var anomalies: String? = "" // Campo opzionale come stringa unica
)
{}
