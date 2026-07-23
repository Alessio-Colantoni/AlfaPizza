package com.alfaproject.alfapizza.model
data class Swap (
    var fromRider: Int,
    var firstRiderAccepted: Int,
    var fromDay: Int,
    var toDay: Int,
    var requestDate: String,
    var isNext: Boolean,
    var isReadyForAdmin: Boolean,
    var _id: String? = null
)
{}
