package com.rkl.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rkl")
data class AppProperties(
    val allowedExtensions: List<String> = listOf(".xls", ".xlsx"),
    val tempDir: String = System.getProperty("java.io.tmpdir"),
    val azure: AzureProperties = AzureProperties()
) {
    data class AzureProperties(val storage: StorageProperties = StorageProperties()) {
        data class StorageProperties(val connectionString: String = "", val containerName: String = "rklcontainer")
    }
}
