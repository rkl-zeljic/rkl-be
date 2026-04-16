package com.rkl.backend.controller

import com.rkl.backend.dto.common.PaginationMeta
import com.rkl.backend.dto.user.*
import com.rkl.backend.enums.UserType
import com.rkl.backend.searchfilter.dto.UserFilter
import com.rkl.backend.service.user.UserService
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping
    fun getUsers(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int,
        @RequestParam email: String?,
        @RequestParam type: UserType?,
        @RequestParam(defaultValue = "createdAt") sortBy: String,
        @RequestParam(defaultValue = "DESC") sortOrder: String
    ): UsersResponse {
        val sort = Sort.by(
            if (sortOrder.equals("ASC", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC,
            sortBy
        )
        val pageable = PageRequest.of(page - 1, pageSize, sort)
        val filter = UserFilter(email = email, type = type)
        val result = userService.findAll(pageable, filter)

        return UsersResponse(
            data = result.content,
            pagination = PaginationMeta(
                totalCount = result.totalElements,
                page = page,
                pageSize = pageSize,
                totalPages = result.totalPages
            )
        )
    }

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): UserResponseDTO {
        return userService.findById(id)
    }

    @GetMapping("/me")
    fun getCurrentUser(authentication: Authentication): UserResponseDTO {
        return userService.getCurrentUser(authentication.name)
    }

    @PostMapping("/me/signature")
    fun createMySignature(
        authentication: Authentication,
        @RequestBody request: Map<String, String>
    ): UserResponseDTO {
        return userService.createCurrentUserSignature(authentication.name, request["signature"]!!)
    }

    @PatchMapping("/me/signature")
    fun updateMySignature(
        authentication: Authentication,
        @RequestBody request: Map<String, String>
    ): UserResponseDTO {
        return userService.updateCurrentUserSignature(authentication.name, request["signature"]!!)
    }

    @DeleteMapping("/me/signature")
    fun deleteMySignature(authentication: Authentication): UserResponseDTO {
        return userService.deleteCurrentUserSignature(authentication.name)
    }

    @PostMapping
    fun createUser(@Valid @RequestBody request: CreateUserRequestDTO): UserResponseDTO {
        return userService.create(request)
    }

    @PutMapping("/{id}")
    fun updateUser(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateUserRequestDTO
    ): UserResponseDTO {
        request.id = id
        return userService.update(request)
    }

    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Long) {
        userService.delete(id)
    }
}
