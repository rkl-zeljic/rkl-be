package com.rkl.backend.dto.user

import com.rkl.backend.dto.common.PaginationMeta
import com.rkl.backend.enums.UserType
import java.time.Instant

data class UserResponseDTO(
    val id: Long,
    val email: String,
    val type: UserType = UserType.DRIVER,
    val createdAt: Instant?
)

data class UsersResponse(
    val status: String = "success",
    val data: List<UserResponseDTO>,
    val pagination: PaginationMeta
)
