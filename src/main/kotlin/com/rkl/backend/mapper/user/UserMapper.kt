package com.rkl.backend.mapper.user

import com.rkl.backend.dto.user.CreateUserRequestDTO
import com.rkl.backend.dto.user.UserResponseDTO
import com.rkl.backend.entity.RklUser
import org.springframework.stereotype.Component

@Component
class UserMapper {
    fun mapToDTO(source: RklUser): UserResponseDTO {
        return with(source) {
            UserResponseDTO(
                id = id!!,
                email = email,
                type = type,
                createdAt = createdAt
            )
        }
    }

    fun mapToEntity(user: CreateUserRequestDTO): RklUser {
        return RklUser(
            email = user.email,
            type = user.type
        )
    }
}
