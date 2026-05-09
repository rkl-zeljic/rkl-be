package com.rkl.backend.service

import com.rkl.backend.dto.version.ChangelogRelease
import com.rkl.backend.dto.version.ChangelogResponse
import com.rkl.backend.dto.version.ChangelogSection
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

@Service
class ChangelogService {

    private val releaseHeader = Regex("""^##\s+\[([^\]]+)\](?:\s*-\s*(.+))?\s*$""")
    private val sectionHeader = Regex("""^###\s+(.+?)\s*$""")
    private val bullet = Regex("""^[-*]\s+(.+)$""")

    fun load(): ChangelogResponse {
        val resource = ClassPathResource("changelog/CHANGELOG.md")
        if (!resource.exists()) return ChangelogResponse(emptyList())

        val lines = resource.inputStream.bufferedReader(Charsets.UTF_8).use { it.readLines() }
        return ChangelogResponse(parse(lines))
    }

    private fun parse(lines: List<String>): List<ChangelogRelease> {
        val releases = mutableListOf<ChangelogRelease>()
        var currentVersion: String? = null
        var currentDate: String? = null
        var currentSections = mutableListOf<ChangelogSection>()
        var currentSectionType: String? = null
        var currentEntries = mutableListOf<String>()

        fun flushSection() {
            if (currentSectionType != null) {
                currentSections.add(ChangelogSection(currentSectionType!!, currentEntries.toList()))
                currentSectionType = null
                currentEntries = mutableListOf()
            }
        }

        fun flushRelease() {
            flushSection()
            if (currentVersion != null) {
                releases.add(ChangelogRelease(currentVersion!!, currentDate, currentSections.toList()))
                currentSections = mutableListOf()
            }
        }

        for (raw in lines) {
            val line = raw.trimEnd()
            val release = releaseHeader.matchEntire(line)
            if (release != null) {
                flushRelease()
                currentVersion = release.groupValues[1].trim()
                currentDate = release.groupValues.getOrNull(2)?.trim().takeUnless { it.isNullOrEmpty() }
                continue
            }
            val section = sectionHeader.matchEntire(line)
            if (section != null && currentVersion != null) {
                flushSection()
                currentSectionType = section.groupValues[1].trim()
                continue
            }
            val item = bullet.matchEntire(line.trimStart())
            if (item != null && currentSectionType != null) {
                currentEntries.add(item.groupValues[1].trim())
            }
        }
        flushRelease()
        return releases
    }
}
