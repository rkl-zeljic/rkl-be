package com.rkl.backend.dto.prevoznica

import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

data class CreatePrevoznicaRequest(
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datum: LocalDate,

    val posiljalac: String,
    val platilacPrevoza: String,
    val primalac: String,
    val prevozilac: String,
    val pratecaDokumenta: String? = null,

    val mestoUtovara: String,
    val mestoIstovara: String,
    val registracija: String,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datumUtovara: LocalDate,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datumIstovara: LocalDate,

    val vrstaRobe: String,
    val km: Double? = null,
    val jedMere: String = "kg",
    val stvarnaTezina: Double? = null,

    val vozacUserId: Long,
    val otpremnicaId: Long? = null,
    val additionalEmails: List<String> = emptyList()
)

data class UpdatePrevoznicaRequest(
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datum: LocalDate,

    val posiljalac: String,
    val platilacPrevoza: String,
    val primalac: String,
    val prevozilac: String,
    val pratecaDokumenta: String? = null,

    val mestoUtovara: String,
    val mestoIstovara: String,
    val registracija: String,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datumUtovara: LocalDate,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datumIstovara: LocalDate,

    val vrstaRobe: String,
    val km: Double? = null,
    val jedMere: String = "kg",
    val stvarnaTezina: Double? = null,

    val vozacUserId: Long,
    val otpremnicaId: Long? = null,
    val additionalEmails: List<String> = emptyList()
)

data class UpdatePrevoznicaSignatureRequest(
    val potpisPrimaoca: String
)

data class PrevoznicaDto(
    val id: Long?,
    val brojPrevoznice: String,
    val datum: String,
    val posiljalac: String,
    val platilacPrevoza: String,
    val primalac: String,
    val prevozilac: String,
    val pratecaDokumenta: String?,
    val mestoUtovara: String,
    val mestoIstovara: String,
    val registracija: String,
    val datumUtovara: String,
    val datumIstovara: String,
    val vrstaRobe: String,
    val km: Double?,
    val jedMere: String,
    val stvarnaTezina: Double?,
    val vozacUserId: Long?,
    val vozacIme: String,
    val otpremnicaId: Long?,
    val otpremnicaBroj: String?,
    val potpisVozaca: Boolean,
    val potpisPrimaoca: Boolean,
    val additionalEmails: List<String> = emptyList(),
    val status: String,
    val createdBy: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class PrevozniceResponse(
    val status: String = "success",
    val data: List<PrevoznicaDto>
)

data class PrevoznicaDetailResponse(
    val status: String = "success",
    val data: PrevoznicaDto
)

data class PrevoznicaDeleteResponse(
    val status: String = "success",
    val id: Long
)
