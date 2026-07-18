package com.alfaproject.alfapizza.model

data class Constraint (
    var riderCode: Int,
    var priority: Int,
    var day: Int,
    var permanent: Boolean,
    var isNext: Boolean = true
)
{}
