package com.rkl.backend.dto.measurement

import com.rkl.backend.dto.common.PaginationMeta

data class ImportResponse(
    val status: String = "success",
    val matched: Int = 0,
    val inserted: Int = 0,
    val totalRows: Int,
    val filename: String,
    val processingTimeMs: Long,
    val fileId: Long? = null,
    val errors: List<String>? = null
)

data class MeasurementDto(
    val id: Long?,
    val izvorFajl: String?,
    val importedFileId: Long? = null,
    val otpremnicaId: Long? = null,
    val otpremnicaBroj: String? = null,
    val hasOtpremnica: Boolean = false,
    val prevoznicaId: Long? = null,
    val prevoznicaBroj: String? = null,
    val isValidated: Boolean = false,
    val izvor: String? = null,
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
    val vozac: String?,
    val mesto: String?,
    val potpis: String?,
    val createdAt: String?,
    val updatedAt: String?
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

data class SignatureResponse(
    val status: String = "success",
    val measurementId: Long,
    val hasSignature: Boolean
)
