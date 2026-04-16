package com.rkl.backend.dto.kupac

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class CreateKupacRequest(
    @field:NotBlank(message = "Naziv je obavezan")
    val naziv: String,

    @field:Email(message = "Neispravan email format")
    val email: String? = null,

    val telefon: String? = null,
    val adresa: String? = null,
    val napomena: String? = null
)

data class UpdateKupacRequest(
    val naziv: String? = null,

    @field:Email(message = "Neispravan email format")
    val email: String? = null,

    val telefon: String? = null,
    val adresa: String? = null,
    val napomena: String? = null
)

data class KupacDto(
    val id: Long?,
    val naziv: String,
    val email: String?,
    val telefon: String?,
    val adresa: String?,
    val napomena: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class KupciResponse(
    val status: String = "success",
    val data: List<KupacDto>,
    val totalCount: Int
)

data class KupacDetailResponse(
    val status: String = "success",
    val data: KupacDto
)

data class KupacDeleteResponse(
    val status: String = "success",
    val id: Long
)

data class KupacAvailableNamesResponse(
    val status: String = "success",
    val data: List<String>
)
