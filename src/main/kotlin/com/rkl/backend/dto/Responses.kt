package com.rkl.backend.dto

data class ImportResponse(
    val status: String = "success",
    val inserted: Int,
    val updated: Int,
    val totalRows: Int,
    val filename: String,
    val processingTimeMs: Long,
    val errors: List<String>? = null
)

data class MeasurementDto(
    val id: Long?,
    val izvorFajl: String?,
    val datumIzvestaja: String?,
    val merniListBr: Int,
    val posiljalac: String?,
    val porucilac: String?,
    val primalac: String?,
    val roba: String?,
    val bruto: Double?,
    val tara: Double?,
    val neto: Double?,
    val prevoznik: String?,
    val registracija: String?,
    val prikolica: String?,
    val vozac: String?,
    val brojLicneKarte: String?,
    val potpis: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class PaginationMeta(
    val totalCount: Long,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int
)

data class MeasurementsResponse(
    val status: String = "success",
    val data: List<MeasurementDto>,
    val pagination: PaginationMeta,
    val filtersApplied: Map<String, String>
)

data class StatsDataPoint(
    val period: String,
    val count: Long
)

data class StatsResponse(
    val status: String = "success",
    val groupBy: String,
    val data: List<StatsDataPoint>,
    val totalCount: Long
)

data class DistinctValuesResponse(
    val status: String = "success",
    val field: String,
    val data: List<String>,
    val totalCount: Int
)

data class CustomerRecord(
    val porucilac: String,
    val measurementCount: Long
)

data class CustomersResponse(
    val status: String = "success",
    val data: List<CustomerRecord>,
    val totalCount: Int
)

data class DriverRecord(
    val vozac: String,
    val measurementCount: Long
)

data class DriversResponse(
    val status: String = "success",
    val data: List<DriverRecord>,
    val totalCount: Int
)

data class HealthResponse(
    val status: String,
    val service: String = "Miki RKL API",
    val version: String = "1.0.0",
    val database: String
)

data class ErrorResponse(
    val status: String = "error",
    val message: String,
    val details: String? = null
)

data class SignatureResponse(
    val status: String = "success",
    val measurementId: Long,
    val hasSignature: Boolean
)
