package com.alfaproject.alfapizza.model

data class WeekStructure (
    var minRider:Int,
    var maxRider:Int,
    var lastDayConstraint: Int,
    var listShift: ArrayList<Int>,
    var isNext: Boolean = true
)
{}
