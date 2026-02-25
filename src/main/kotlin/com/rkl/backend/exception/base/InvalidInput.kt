package com.rkl.backend.exception.base

import org.springframework.http.HttpStatus

open class InvalidInput(
    override val code: String,
    override val message: String
) : HttpStatusException(HttpStatus.BAD_REQUEST, code, message)
