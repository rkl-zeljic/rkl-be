package com.rkl.backend.dto.version

data class VersionResponse(
    val version: String,
    val buildTime: String?,
    val gitCommit: String?,
    val gitBranch: String?,
)

data class ChangelogResponse(
    val releases: List<ChangelogRelease>,
)

data class ChangelogRelease(
    val version: String,
    val date: String?,
    val sections: List<ChangelogSection>,
)

data class ChangelogSection(
    val type: String,
    val entries: List<String>,
)
