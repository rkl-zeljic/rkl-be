package com.rkl.backend.exception.base

import org.springframework.http.HttpStatus

open class HttpStatusException(
    val status: HttpStatus,
    open val code: String,
    override val message: String,
    open val metadata: Map<String, String> = mapOf()
) : BaseException(message)
