package com.rkl.backend.controller

import com.rkl.backend.dto.measurement.*
import com.rkl.backend.service.MeasurementService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class MeasurementController(
    private val measurementService: MeasurementService
) {

    @PostMapping("/measurements/import")
    fun importExcel(
        @RequestParam("file") file: org.springframework.web.multipart.MultipartFile,
        authentication: org.springframework.security.core.Authentication?
    ): ImportResponse {
        return measurementService.importExcel(file, uploadedBy = authentication?.name)
    }

    @GetMapping("/measurements")
    fun getMeasurements(@Valid filter: MeasurementFilterRequest): MeasurementsResponse {
        return measurementService.queryMeasurements(filter)
    }

    @GetMapping("/measurements/my")
    fun getMyMeasurements(
        @Valid filter: MeasurementFilterRequest,
        authentication: Authentication
    ): MeasurementsResponse {
        return measurementService.queryMyMeasurements(filter, authentication.name)
    }

    @GetMapping("/measurements/stats")
    fun getStats(@Valid filter: StatsFilterRequest): StatsResponse {
        return measurementService.getStats(filter)
    }

    @GetMapping("/measurements/distinct/{field}")
    fun getDistinctValues(
        @PathVariable field: String,
        @RequestParam search: String?
    ): DistinctValuesResponse {
        return measurementService.getDistinctValues(field, search)
    }

    @GetMapping("/customers")
    fun getCustomers(@RequestParam search: String?): CustomersResponse {
        return measurementService.getCustomers(search)
    }

    @GetMapping("/drivers")
    fun getDrivers(@RequestParam search: String?): DriversResponse {
        return measurementService.getDrivers(search)
    }

    @PatchMapping("/measurements/{id}/signature")
    fun updateSignature(
        @PathVariable id: Long,
        @RequestBody request: SignatureRequest
    ): SignatureResponse {
        return measurementService.updateSignature(id, request.signature)
    }
}
