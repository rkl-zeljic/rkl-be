package com.rkl.backend.controller

import com.rkl.backend.dto.faktura.*
import com.rkl.backend.repository.FakturaRepository
import com.rkl.backend.service.FakturaExcelService
import com.rkl.backend.service.FakturaPdfService
import com.rkl.backend.service.FakturaService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/fakture")
@PreAuthorize("hasAnyRole('ADMIN')")
class FakturaController(
    private val fakturaService: FakturaService,
    private val fakturaExcelService: FakturaExcelService,
    private val fakturaPdfService: FakturaPdfService,
    private val fakturaRepository: FakturaRepository
) {

    @GetMapping
    fun listFakture(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int,
        @RequestParam(defaultValue = "createdAt") sortBy: String,
        @RequestParam(defaultValue = "DESC") sortOrder: String
    ): FaktureResponse {
        return fakturaService.listFakture(page, pageSize, sortBy, sortOrder)
    }

    @GetMapping("/{id}")
    fun getFaktura(@PathVariable id: Long): FakturaDetailResponse {
        return fakturaService.getFaktura(id)
    }

    @PostMapping
    fun createFaktura(
        @Valid @RequestBody request: CreateFakturaRequest,
        authentication: org.springframework.security.core.Authentication?
    ): FakturaDetailResponse {
        return fakturaService.createFaktura(request, createdBy = authentication?.name)
    }

    @PatchMapping("/{id}/status")
    fun updateStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateFakturaStatusRequest
    ): FakturaDetailResponse {
        return fakturaService.updateStatus(id, request)
    }

    @PostMapping("/{id}/send-email")
    fun sendFakturaEmail(
        @PathVariable id: Long,
        @Valid @RequestBody request: SendFakturaEmailRequest
    ): SendFakturaEmailResponse {
        return fakturaService.sendFakturaEmail(id, request)
    }

    @DeleteMapping("/{id}")
    fun deleteFaktura(@PathVariable id: Long): FakturaDeleteResponse {
        return fakturaService.deleteFaktura(id)
    }

    @GetMapping("/{id}/excel")
    fun downloadExcel(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val faktura = fakturaRepository.findById(id).orElseThrow {
            NoSuchElementException("Faktura sa id $id nije pronađena")
        }

        val excelBytes = fakturaExcelService.generateExcel(faktura)
        val filename = "${faktura.brojFakture}.xlsx"

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .contentLength(excelBytes.size.toLong())
            .body(excelBytes)
    }

    @GetMapping("/{id}/pdf")
    fun downloadPdf(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val faktura = fakturaRepository.findById(id).orElseThrow {
            NoSuchElementException("Faktura sa id $id nije pronađena")
        }

        val pdfBytes = fakturaPdfService.generatePdf(faktura)
        val filename = "${faktura.brojFakture}.pdf"

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdfBytes.size.toLong())
            .body(pdfBytes)
    }
}
