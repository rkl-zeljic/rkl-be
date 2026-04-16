package com.rkl.backend.controller

import com.rkl.backend.dto.kupac.*
import com.rkl.backend.service.KupacService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/kupci-profili")
@PreAuthorize("hasAnyRole('ADMIN')")
class KupacController(
    private val kupacService: KupacService
) {

    @GetMapping("/available-names")
    fun getAvailableNames(): KupacAvailableNamesResponse {
        return kupacService.getAvailableNames()
    }

    @GetMapping
    fun listKupci(@RequestParam search: String?): KupciResponse {
        return kupacService.listKupci(search)
    }

    @GetMapping("/{id}")
    fun getKupac(@PathVariable id: Long): KupacDetailResponse {
        return kupacService.getKupac(id)
    }

    @PostMapping
    fun createKupac(@Valid @RequestBody request: CreateKupacRequest): KupacDetailResponse {
        return kupacService.createKupac(request)
    }

    @PutMapping("/{id}")
    fun updateKupac(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateKupacRequest
    ): KupacDetailResponse {
        return kupacService.updateKupac(id, request)
    }

    @DeleteMapping("/{id}")
    fun deleteKupac(@PathVariable id: Long): KupacDeleteResponse {
        return kupacService.deleteKupac(id)
    }
}
