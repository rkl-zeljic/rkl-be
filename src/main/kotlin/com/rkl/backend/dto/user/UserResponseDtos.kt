package com.rkl.backend.dto.user

import com.rkl.backend.dto.common.PaginationMeta
import com.rkl.backend.enums.UserType
import java.time.Instant

data class UserResponseDTO(
    val id: Long,
    val email: String? = null,
    val type: UserType = UserType.DRIVER,
    val driverName: String? = null,
    val username: String? = null,
    val signature: String? = null,
    val createdAt: Instant?
)

data class UsersResponse(
    val status: String = "success",
    val data: List<UserResponseDTO>,
    val pagination: PaginationMeta
)
