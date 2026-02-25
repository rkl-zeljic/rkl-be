package com.rkl.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationPropertiesScan
class RklConfig {

    @ConfigurationProperties(prefix = "rkl.security")
    class Security(
        val enabled: Boolean,
        val corsEnabled: Boolean,
        val allowedOrigins: List<String>,
        val expectedIssuer: String,
        val jwtPublicKey: String,
        val jwksUrl: String,
        val permitAllEndpoints: List<String>,
        val permitSpecificEndpoints: List<PermitSpecificEntry> = emptyList(),
        val admins: List<String>
    )

    data class PermitSpecificEntry(
        var path: String,
        var method: String
    )
}