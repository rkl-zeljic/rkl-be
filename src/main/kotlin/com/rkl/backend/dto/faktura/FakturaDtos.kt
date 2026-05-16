package com.rkl.backend.dto.faktura

import com.rkl.backend.dto.common.PaginationMeta
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

data class CreateFakturaRequest(
    val porucilac: String,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datumOd: LocalDate,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datumDo: LocalDate,

    val napomena: String? = null,

    // Multi-value filteri — prazna lista znači "ne filtriraj po ovom polju"
    val robaFilter: List<String> = emptyList(),
    val prevoznikFilter: List<String> = emptyList(),
    val primalacFilter: List<String> = emptyList(),
    val posiljalacFilter: List<String> = emptyList(),
    // true = u Excel/PDF idu labelovane (canonical) vrednosti; false = originalne (raw) iz uvoza
    val useLabeledValues: Boolean = true
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
    val robaList: List<String> = emptyList(),
    val prevoznikList: List<String> = emptyList(),
    val primalacList: List<String> = emptyList(),
    val posiljalacList: List<String> = emptyList(),
    val robaFilter: List<String> = emptyList(),
    val prevoznikFilter: List<String> = emptyList(),
    val primalacFilter: List<String> = emptyList(),
    val posiljalacFilter: List<String> = emptyList(),
    val useLabeledValues: Boolean = true,
    val createdAt: String?,
    val updatedAt: String?
)

data class FaktureResponse(
    val status: String = "success",
    val data: List<FakturaDto>,
    val pagination: PaginationMeta? = null
)

data class FakturaDetailResponse(
    val status: String = "success",
    val data: FakturaDto
)

data class FakturaDeleteResponse(
    val status: String = "success",
    val id: Long
)

data class SendFakturaEmailRequest(
    val emails: List<String>,
    val format: String = "pdf" // "pdf" or "excel"
)

data class SendFakturaEmailResponse(
    val status: String = "success",
    val message: String
)
