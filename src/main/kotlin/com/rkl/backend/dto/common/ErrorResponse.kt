package com.rkl.backend.dto.common

data class ErrorResponse(
    val status: String = "error",
    val message: String,
    val details: String? = null
)
