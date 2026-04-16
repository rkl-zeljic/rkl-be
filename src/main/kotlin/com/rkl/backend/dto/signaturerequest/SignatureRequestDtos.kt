package com.rkl.backend.dto.signaturerequest

import com.rkl.backend.entity.SignatureRequestStatus
import java.time.Instant

data class CreateSignatureRequestDTO(
    val newSignature: String
)

data class SignatureRequestResponseDTO(
    val id: Long,
    val userId: Long,
    val driverName: String?,
    val driverEmail: String?,
    val currentSignature: String?,
    val newSignature: String,
    val status: SignatureRequestStatus,
    val createdAt: Instant?,
    val resolvedAt: Instant?,
    val resolvedByEmail: String?,
)
