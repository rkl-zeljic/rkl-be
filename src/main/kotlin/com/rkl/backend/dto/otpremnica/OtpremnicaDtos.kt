package com.rkl.backend.dto.otpremnica

import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

data class CreateOtpremnicaRequest(
    val brojOtpremnice: String,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datum: LocalDate,

    val porucilacId: Long? = null,
    val porucilac: String,
    val primalac: String,
    val prevoznik: String,
    val registracija: String,

    val nazivRobe: String,
    val jedinicaMere: String = "Kg",
    val bruto: Double,
    val tara: Double,
    val neto: Double,

    val merniListBr: Int? = null,
    val vozacUserId: Long,
    val bezMerenja: Boolean = false,
    val additionalEmails: List<String> = emptyList(),
    val posaljiPrevozniku: Boolean = true
)

data class UpdateOtpremnicaRequest(
    val brojOtpremnice: String,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val datum: LocalDate,

    val porucilacId: Long? = null,
    val porucilac: String,
    val primalac: String,
    val prevoznik: String,
    val registracija: String,

    val nazivRobe: String,
    val jedinicaMere: String = "Kg",
    val bruto: Double,
    val tara: Double,
    val neto: Double,

    val merniListBr: Int? = null,
    val vozacUserId: Long,
    val bezMerenja: Boolean = false,
    val additionalEmails: List<String> = emptyList(),
    val posaljiPrevozniku: Boolean = true
)

data class UpdateOtpremnicaSignatureRequest(
    val potpisPrimaoca: String
)

data class OtpremnicaDto(
    val id: Long?,
    val brojOtpremnice: String,
    val datum: String,
    val porucilacId: Long? = null,
    val porucilac: String,
    val primalac: String,
    val prevoznik: String,
    val registracija: String,
    val nazivRobe: String,
    val jedinicaMere: String,
    val bruto: Double,
    val tara: Double,
    val neto: Double,
    val merniListBr: Int? = null,
    val merenjeId: Long? = null,
    val merenjeValidated: Boolean = false,
    val vozacUserId: Long?,
    val vozacIme: String,
    val potpisVozaca: Boolean,
    val potpisIzdavaoca: Boolean,
    val potpisPrimaoca: Boolean,
    val bezMerenja: Boolean,
    val merenjeGenerated: Boolean,
    val additionalEmails: List<String> = emptyList(),
    val posaljiPrevozniku: Boolean = true,
    val status: String,
    val createdBy: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class OtpremniceResponse(
    val status: String = "success",
    val data: List<OtpremnicaDto>
)

data class OtpremnicaDetailResponse(
    val status: String = "success",
    val data: OtpremnicaDto
)

data class OtpremnicaDeleteResponse(
    val status: String = "success",
    val id: Long
)

data class GenerateMerenjaResponse(
    val status: String = "success",
    val processedCount: Int,
    val filename: String,
    val importedFileId: Long?
)

data class NextBrojResponse(
    val status: String = "success",
    val data: Int
)
