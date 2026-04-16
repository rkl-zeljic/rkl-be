package com.rkl.backend.controller

import com.rkl.backend.dto.prevoznik.*
import com.rkl.backend.service.PrevoznikService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/prevoznici-profili")
@PreAuthorize("hasAnyRole('ADMIN')")
class PrevoznikController(
    private val prevoznikService: PrevoznikService
) {

    @GetMapping("/available-names")
    fun getAvailableNames(): PrevoznikAvailableNamesResponse {
        return prevoznikService.getAvailableNames()
    }

    @GetMapping
    fun listPrevoznici(@RequestParam search: String?): PrevozniciResponse {
        return prevoznikService.listPrevoznici(search)
    }

    @GetMapping("/{id}")
    fun getPrevoznik(@PathVariable id: Long): PrevoznikDetailResponse {
        return prevoznikService.getPrevoznik(id)
    }

    @PostMapping
    fun createPrevoznik(@Valid @RequestBody request: CreatePrevoznikRequest): PrevoznikDetailResponse {
        return prevoznikService.createPrevoznik(request)
    }

    @PutMapping("/{id}")
    fun updatePrevoznik(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdatePrevoznikRequest
    ): PrevoznikDetailResponse {
        return prevoznikService.updatePrevoznik(id, request)
    }

    @DeleteMapping("/{id}")
    fun deletePrevoznik(@PathVariable id: Long): PrevoznikDeleteResponse {
        return prevoznikService.deletePrevoznik(id)
    }
}
