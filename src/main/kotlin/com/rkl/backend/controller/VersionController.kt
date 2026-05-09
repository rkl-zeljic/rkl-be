package com.rkl.backend.controller

import com.rkl.backend.dto.version.ChangelogResponse
import com.rkl.backend.dto.version.VersionResponse
import com.rkl.backend.service.ChangelogService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.info.GitProperties
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/v1")
class VersionController(
    private val changelogService: ChangelogService,
    @Autowired(required = false) private val buildProperties: BuildProperties? = null,
    @Autowired(required = false) private val gitProperties: GitProperties? = null,
) {

    @GetMapping("/version")
    fun version(): VersionResponse {
        return VersionResponse(
            version = buildProperties?.version ?: "dev",
            buildTime = buildProperties?.time?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
            gitCommit = gitProperties?.shortCommitId,
            gitBranch = gitProperties?.branch,
        )
    }

    @GetMapping("/changelog")
    @PreAuthorize("hasRole('ADMIN')")
    fun changelog(): ChangelogResponse {
        return changelogService.load()
    }
}
