package com.rkl.backend.dto.faktura

import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

data class CreateFakturaRequest(
    val porucilac: String,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datumOd: LocalDate,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datumDo: LocalDate,

    val napomena: String? = null
)

data class UpdateFakturaStatusRequest(
    val status: String
)

data class FakturaDto(
    val id: Long?,
    val brojFakture: String,
    val porucilac: String,
    val datumOd: String,
    val datumDo: String,
    val status: String,
    val datumSlanja: String?,
    val napomena: String?,
    val createdBy: String?,
    val measurementCount: Int,
    val createdAt: String?,
    val updatedAt: String?
)

data class FaktureResponse(
    val status: String = "success",
    val data: List<FakturaDto>
)

data class FakturaDetailResponse(
    val status: String = "success",
    val data: FakturaDto
)

data class FakturaDeleteResponse(
    val status: String = "success",
    val id: Long
)
