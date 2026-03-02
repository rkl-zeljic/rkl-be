package com.rkl.backend.controller

import com.rkl.backend.dto.*
import com.rkl.backend.service.MeasurementService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1")
class MeasurementController(
    private val measurementService: MeasurementService
) {

    @PostMapping("/measurements/import")
    fun importExcel(
        @RequestParam("file") file: MultipartFile,
        authentication: Authentication?
    ): ImportResponse {
        return measurementService.importExcel(file, uploadedBy = authentication?.name)
    }

    @GetMapping("/measurements")
    fun getMeasurements(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) datumOd: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) datumDo: LocalDate?,
        @RequestParam roba: String?,
        @RequestParam registracija: String?,
        @RequestParam prevoznik: String?,
        @RequestParam posiljalac: String?,
        @RequestParam porucilac: String?,
        @RequestParam primalac: String?,
        @RequestParam vozac: String?,
        @RequestParam(defaultValue = "datumIzvestaja") sortBy: String,
        @RequestParam(defaultValue = "DESC") sortOrder: String
    ): MeasurementsResponse {
        return measurementService.queryMeasurements(
            page = page,
            pageSize = pageSize,
            datumOd = datumOd,
            datumDo = datumDo,
            roba = roba,
            registracija = registracija,
            prevoznik = prevoznik,
            posiljalac = posiljalac,
            porucilac = porucilac,
            primalac = primalac,
            vozac = vozac,
            sortBy = sortBy,
            sortOrder = sortOrder
        )
    }

    @GetMapping("/measurements/stats")
    fun getStats(
        @RequestParam groupBy: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) datumOd: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) datumDo: LocalDate?,
        @RequestParam porucilac: String?,
        @RequestParam vozac: String?,
        @RequestParam roba: String?
    ): StatsResponse {
        return measurementService.getStats(
            groupBy = groupBy,
            datumOd = datumOd,
            datumDo = datumDo,
            porucilac = porucilac,
            vozac = vozac,
            roba = roba
        )
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
