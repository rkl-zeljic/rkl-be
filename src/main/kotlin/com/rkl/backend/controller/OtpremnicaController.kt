package com.rkl.backend.controller

import com.rkl.backend.dto.otpremnica.*
import com.rkl.backend.service.OtpremnicaPdfService
import com.rkl.backend.service.OtpremnicaService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/otpremnice")
class OtpremnicaController(
    private val otpremnicaService: OtpremnicaService,
    private val otpremnicaPdfService: OtpremnicaPdfService
) {

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun listOtpremnice(): OtpremniceResponse {
        return otpremnicaService.listOtpremnice()
    }

    @GetMapping("/my")
    fun getMyOtpremnice(authentication: Authentication): OtpremniceResponse {
        return otpremnicaService.getMyOtpremnice(authentication.name)
    }

    @GetMapping("/known-additional-emails")
    @PreAuthorize("hasRole('ADMIN')")
    fun getKnownAdditionalEmails(): Map<String, Any> {
        return mapOf(
            "status" to "success",
            "data" to otpremnicaService.getKnownAdditionalEmails()
        )
    }

    @GetMapping("/next-broj")
    @PreAuthorize("hasRole('ADMIN')")
    fun getNextBroj(@RequestParam porucilacId: Long): NextBrojResponse {
        return NextBrojResponse(data = otpremnicaService.getNextBrojForKupac(porucilacId))
    }

    @GetMapping("/{id}")
    fun getOtpremnica(@PathVariable id: Long): OtpremnicaDetailResponse {
        return otpremnicaService.getOtpremnica(id)
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun createOtpremnica(
        @Valid @RequestBody request: CreateOtpremnicaRequest,
        authentication: Authentication?
    ): OtpremnicaDetailResponse {
        return otpremnicaService.createOtpremnica(request, createdBy = authentication?.name)
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateOtpremnica(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateOtpremnicaRequest,
        authentication: Authentication?
    ): OtpremnicaDetailResponse {
        return otpremnicaService.updateOtpremnica(id, request, updatedBy = authentication?.name)
    }

    @PatchMapping("/{id}/signature")
    fun updatePrimalacSignature(
        @PathVariable id: Long,
        @RequestBody request: UpdateOtpremnicaSignatureRequest
    ): OtpremnicaDetailResponse {
        return otpremnicaService.updatePrimalacSignature(id, request)
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteOtpremnica(@PathVariable id: Long): OtpremnicaDeleteResponse {
        return otpremnicaService.deleteOtpremnica(id)
    }

    @GetMapping("/{id}/pdf")
    fun downloadPdf(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val otpremnica = otpremnicaService.getOtpremnica(id)
        val pdfBytes = otpremnicaPdfService.generatePdf(id)
        val filename = "Otpremnica-${otpremnica.data.brojOtpremnice}.pdf"

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdfBytes.size.toLong())
            .body(pdfBytes)
    }
}
