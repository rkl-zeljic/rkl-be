package com.rkl.backend.controller

import com.rkl.backend.dto.measurement.DistinctValuesResponse
import com.rkl.backend.dto.prevoznica.*
import com.rkl.backend.service.PrevoznicaPdfService
import com.rkl.backend.service.PrevoznicaService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/prevoznice")
class PrevoznicaController(
    private val prevoznicaService: PrevoznicaService,
    private val prevoznicaPdfService: PrevoznicaPdfService
) {

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun listPrevoznice(): PrevozniceResponse {
        return prevoznicaService.listPrevoznice()
    }

    @GetMapping("/mesta-istovara")
    @PreAuthorize("hasRole('ADMIN')")
    fun getDistinctMestaIstovara(@RequestParam(required = false) search: String?): DistinctValuesResponse {
        val values = prevoznicaService.getDistinctMestaIstovara(search)
        return DistinctValuesResponse(field = "mestoIstovara", data = values, totalCount = values.size)
    }

    @GetMapping("/{id}")
    fun getPrevoznica(@PathVariable id: Long): PrevoznicaDetailResponse {
        return prevoznicaService.getPrevoznica(id)
    }

    @GetMapping("/my")
    fun getMyPrevoznice(authentication: Authentication): PrevozniceResponse {
        return prevoznicaService.getMyPrevoznice(authentication.name)
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun createPrevoznica(
        @Valid @RequestBody request: CreatePrevoznicaRequest,
        authentication: Authentication?
    ): PrevoznicaDetailResponse {
        return prevoznicaService.createPrevoznica(request, createdBy = authentication?.name)
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updatePrevoznica(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdatePrevoznicaRequest,
        authentication: Authentication?
    ): PrevoznicaDetailResponse {
        return prevoznicaService.updatePrevoznica(id, request, updatedBy = authentication?.name)
    }

    @PatchMapping("/{id}/signature")
    fun updatePrimalacSignature(
        @PathVariable id: Long,
        @RequestBody request: UpdatePrevoznicaSignatureRequest
    ): PrevoznicaDetailResponse {
        return prevoznicaService.updatePrimalacSignature(id, request)
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deletePrevoznica(@PathVariable id: Long): PrevoznicaDeleteResponse {
        return prevoznicaService.deletePrevoznica(id)
    }

    @GetMapping("/{id}/pdf")
    fun downloadPdf(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val prevoznica = prevoznicaService.getPrevoznica(id)
        val pdfBytes = prevoznicaPdfService.generatePdf(id)
        val filename = "Prevoznica-${prevoznica.data.brojPrevoznice}.pdf"

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdfBytes.size.toLong())
            .body(pdfBytes)
    }
}
