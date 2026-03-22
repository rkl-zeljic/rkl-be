package com.rkl.backend.dto.primalac

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class CreatePrimalacRequest(
    @field:NotBlank(message = "Naziv je obavezan")
    val naziv: String,

    @field:Email(message = "Neispravan email format")
    val email: String? = null,

    val telefon: String? = null,
    val adresa: String? = null,
    val napomena: String? = null
)

data class UpdatePrimalacRequest(
    val naziv: String? = null,

    @field:Email(message = "Neispravan email format")
    val email: String? = null,

    val telefon: String? = null,
    val adresa: String? = null,
    val napomena: String? = null
)

data class PrimalacDto(
    val id: Long?,
    val naziv: String,
    val email: String?,
    val telefon: String?,
    val adresa: String?,
    val napomena: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class PrimaociResponse(
    val status: String = "success",
    val data: List<PrimalacDto>,
    val totalCount: Int
)

data class PrimalacDetailResponse(
    val status: String = "success",
    val data: PrimalacDto
)

data class PrimalacDeleteResponse(
    val status: String = "success",
    val id: Long
)

data class AvailableNamesResponse(
    val status: String = "success",
    val data: List<String>
)
