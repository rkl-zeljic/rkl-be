package com.rkl.backend.searchfilter.dto

import com.rkl.backend.enums.UserType

data class UserFilter(
    val email: String? = null,
    val type: UserType? = null,
)
