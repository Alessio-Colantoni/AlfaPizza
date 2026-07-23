package com.alfaproject.alfapizza.model

data class User (
    var name: String = "",
    var surname: String = "",
    var email: String? = null,
    var phone: String? = null,
    var code: Int = -1,
    var password: String? = null,
    var isAdmin: Boolean = false,
    var lastAccess: String? = null
)
