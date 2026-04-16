package com.rkl.backend.dto.prevoznik

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class CreatePrevoznikRequest(
    @field:NotBlank(message = "Naziv je obavezan")
    val naziv: String,

    @field:Email(message = "Neispravan email format")
    val email: String? = null,

    val telefon: String? = null,
    val adresa: String? = null,
    val napomena: String? = null
)

data class UpdatePrevoznikRequest(
    val naziv: String? = null,

    @field:Email(message = "Neispravan email format")
    val email: String? = null,

    val telefon: String? = null,
    val adresa: String? = null,
    val napomena: String? = null
)

data class PrevoznikDto(
    val id: Long?,
    val naziv: String,
    val email: String?,
    val telefon: String?,
    val adresa: String?,
    val napomena: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class PrevozniciResponse(
    val status: String = "success",
    val data: List<PrevoznikDto>,
    val totalCount: Int
)

data class PrevoznikDetailResponse(
    val status: String = "success",
    val data: PrevoznikDto
)

data class PrevoznikDeleteResponse(
    val status: String = "success",
    val id: Long
)

data class PrevoznikAvailableNamesResponse(
    val status: String = "success",
    val data: List<String>
)
