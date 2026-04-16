package com.rkl.backend.controller

import com.rkl.backend.dto.signaturerequest.CreateSignatureRequestDTO
import com.rkl.backend.dto.signaturerequest.SignatureRequestResponseDTO
import com.rkl.backend.service.SignatureRequestService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/signature-requests")
class SignatureRequestController(
    private val signatureRequestService: SignatureRequestService
) {

    @PostMapping
    fun createRequest(
        authentication: Authentication,
        @RequestBody request: CreateSignatureRequestDTO
    ): SignatureRequestResponseDTO {
        return signatureRequestService.createRequest(authentication.name, request)
    }

    @GetMapping("/my/pending")
    fun getMyPendingRequest(authentication: Authentication): SignatureRequestResponseDTO? {
        return signatureRequestService.getMyPendingRequest(authentication.name)
    }

    @GetMapping
    fun getPendingRequests(): List<SignatureRequestResponseDTO> {
        return signatureRequestService.getPendingRequests()
    }

    @GetMapping("/all")
    fun getAllRequests(): List<SignatureRequestResponseDTO> {
        return signatureRequestService.getAllRequests()
    }

    @PatchMapping("/{id}/approve")
    fun approveRequest(
        @PathVariable id: Long,
        authentication: Authentication
    ): SignatureRequestResponseDTO {
        return signatureRequestService.approveRequest(id, authentication.name)
    }

    @PatchMapping("/{id}/reject")
    fun rejectRequest(
        @PathVariable id: Long,
        authentication: Authentication
    ): SignatureRequestResponseDTO {
        return signatureRequestService.rejectRequest(id, authentication.name)
    }
}
