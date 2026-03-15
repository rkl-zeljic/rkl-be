package com.rkl.backend.service.user

import com.rkl.backend.dto.user.CreateUserRequestDTO
import com.rkl.backend.dto.user.UpdateCurrentUserRequestDTO
import com.rkl.backend.dto.user.UpdateUserRequestDTO
import com.rkl.backend.dto.user.UserResponseDTO
import com.rkl.backend.searchfilter.dto.UserFilter
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface UserService {
    fun findAll(pageable: Pageable, userFilter: UserFilter): Page<UserResponseDTO>
    fun findById(id: Long): UserResponseDTO
    fun create(createUserRequestDTO: CreateUserRequestDTO): UserResponseDTO
    fun update(updateUserRequestDTO: UpdateUserRequestDTO): UserResponseDTO
    fun delete(id: Long)
    fun getCurrentUser(name: String): UserResponseDTO
    fun updateCurrentUser(name: String, updateCurrentUserRequestDTO: UpdateCurrentUserRequestDTO): UserResponseDTO
    fun getCurrentUserOrCreateIfNotExist(name: String, isAdmin: Boolean = false): UserResponseDTO
    fun updateCurrentUserSignature(email: String, signature: String?): UserResponseDTO
}
