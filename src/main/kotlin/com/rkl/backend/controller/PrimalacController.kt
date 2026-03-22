package com.rkl.backend.controller

import com.rkl.backend.dto.primalac.*
import com.rkl.backend.service.PrimalacService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/primaoci")
@PreAuthorize("hasAnyRole('ADMIN')")
class PrimalacController(
    private val primalacService: PrimalacService
) {

    @GetMapping("/available-names")
    fun getAvailableNames(): AvailableNamesResponse {
        return primalacService.getAvailableNames()
    }

    @GetMapping
    fun listPrimaoci(@RequestParam search: String?): PrimaociResponse {
        return primalacService.listPrimaoci(search)
    }

    @GetMapping("/{id}")
    fun getPrimalac(@PathVariable id: Long): PrimalacDetailResponse {
        return primalacService.getPrimalac(id)
    }

    @PostMapping
    fun createPrimalac(@Valid @RequestBody request: CreatePrimalacRequest): PrimalacDetailResponse {
        return primalacService.createPrimalac(request)
    }

    @PutMapping("/{id}")
    fun updatePrimalac(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdatePrimalacRequest
    ): PrimalacDetailResponse {
        return primalacService.updatePrimalac(id, request)
    }

    @DeleteMapping("/{id}")
    fun deletePrimalac(@PathVariable id: Long): PrimalacDeleteResponse {
        return primalacService.deletePrimalac(id)
    }
}
