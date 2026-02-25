package com.rkl.backend.exception.base

import org.springframework.http.HttpStatus

open class  NotExistsException(
    override val code: String,
    override val message: String
) : HttpStatusException(HttpStatus.NOT_FOUND, code, message)
