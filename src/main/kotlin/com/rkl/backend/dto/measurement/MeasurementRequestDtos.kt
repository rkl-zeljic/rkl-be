package com.rkl.backend.dto.measurement

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

data class SignatureRequest(
    val signature: String?
)

data class MeasurementFilterRequest(
    @field:Min(value = 1, message = "Page must be at least 1")
    val page: Int = 1,

    @field:Min(value = 1, message = "Page size must be at least 1")
    @field:Max(value = 200, message = "Page size must be at most 200")
    val pageSize: Int = 50,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datumOd: LocalDate? = null,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datumDo: LocalDate? = null,

    val roba: String? = null,
    val registracija: String? = null,
    val prevoznik: String? = null,
    val posiljalac: String? = null,
    val porucilac: String? = null,
    val primalac: String? = null,
    val vozac: String? = null,

    val sortBy: String = "datumIzvestaja",

    @field:Pattern(regexp = "ASC|DESC", flags = [Pattern.Flag.CASE_INSENSITIVE], message = "Sort order must be ASC or DESC")
    val sortOrder: String = "DESC"
)

data class StatsFilterRequest(
    @field:Pattern(regexp = "day|week|month|year", message = "groupBy must be one of: day, week, month, year")
    val groupBy: String,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datumOd: LocalDate? = null,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datumDo: LocalDate? = null,

    val porucilac: String? = null,
    val vozac: String? = null,
    val roba: String? = null
)
