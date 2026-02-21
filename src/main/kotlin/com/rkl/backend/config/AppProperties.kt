package com.rkl.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rkl")
data class AppProperties(
    val allowedExtensions: List<String> = listOf(".xls", ".xlsx"),
    val tempDir: String = System.getProperty("java.io.tmpdir")
)
