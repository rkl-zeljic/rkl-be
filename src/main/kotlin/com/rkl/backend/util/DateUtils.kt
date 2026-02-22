package com.rkl.backend.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DateUtils {

    private val DATE_PATTERN = Regex("""(\d{2})\.(\d{2})\.(\d{4})""")
    private val FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun extractReportDateFromFilename(filename: String): LocalDate? {
        val match = DATE_PATTERN.find(filename) ?: return null
        return try {
            val dateStr = match.value
            LocalDate.parse(dateStr, FORMATTER)
        } catch (_: Exception) {
            null
        }
    }
}
