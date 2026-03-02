package com.rkl.backend.controller

import com.rkl.backend.dto.health.HealthResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

@RestController
class HealthController(
    private val dataSource: DataSource
) {

    @GetMapping("/")
    fun health(): HealthResponse {
        val dbStatus = try {
            dataSource.connection.use { conn ->
                if (conn.isValid(2)) "connected" else "disconnected"
            }
        } catch (_: Exception) {
            "disconnected"
        }

        return HealthResponse(
            status = if (dbStatus == "connected") "ok" else "unhealthy",
            database = dbStatus
        )
    }
}
