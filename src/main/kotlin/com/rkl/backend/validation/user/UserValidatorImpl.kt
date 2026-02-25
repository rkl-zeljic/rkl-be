package com.rkl.backend.validation.user

import com.rkl.backend.exception.specific.NotSuperAdminException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class UserValidatorImpl(
) : UserValidator {
    override fun validateUserIsAdmin() {
        if (isNotAdmin()) {
            throw NotSuperAdminException()
        }
    }

    private fun isNotAdmin(): Boolean {
        return !SecurityContextHolder.getContext().authentication?.authorities?.any { it.authority == "ROLE_ADMIN" }!!
    }
}