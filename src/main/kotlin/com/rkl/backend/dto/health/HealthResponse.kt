package com.rkl.backend.dto.health

data class HealthResponse(
    val status: String,
    val service: String = "Miki RKL API",
    val version: String = "1.0.0",
    val database: String
)
