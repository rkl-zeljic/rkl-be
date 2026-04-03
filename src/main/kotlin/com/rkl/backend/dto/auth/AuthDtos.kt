package com.rkl.backend.dto.auth

import com.rkl.backend.enums.UserType

data class LoginRequestDTO(
    val username: String,
    val password: String,
)

data class LoginResponseDTO(
    val token: String,
    val email: String,
    val type: UserType,
    val driverName: String? = null,
    val username: String? = null,
)
