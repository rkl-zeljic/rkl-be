package com.rkl.backend.dto.user

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.rkl.backend.enums.UserType
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.util.Optional

data class CreateUserRequestDTO(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,
    val type: UserType = UserType.DRIVER,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateUserRequestDTO(
    @JsonIgnore
    var id: Long? = null,
    @field:Email(message = "Invalid email format")
    val email: String? = null,
    val type: UserType? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateCurrentUserRequestDTO(
    val alias: Optional<String>?,
)
