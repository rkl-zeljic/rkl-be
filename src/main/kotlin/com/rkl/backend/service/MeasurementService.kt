package com.rkl.backend.service

import com.rkl.backend.config.AppProperties
import com.rkl.backend.dto.*
import com.rkl.backend.entity.ImportedFile
import com.rkl.backend.entity.Merenje
import com.rkl.backend.repository.ImportedFileRepository
import com.rkl.backend.repository.MerenjeRepository
import com.rkl.backend.repository.MerenjeSpecification
import com.rkl.backend.util.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.time.LocalDate

@Service
class MeasurementService(
    private val repository: MerenjeRepository,
    private val importedFileRepository: ImportedFileRepository,
    private val excelParsingService: ExcelParsingService,
    private val azureBlobStorageService: AzureBlobStorageService,
    private val appProperties: AppProperties
) {

    private val logger = LoggerFactory.getLogger(MeasurementService::class.java)

    companion object {
        val SORT_WHITELIST = setOf(
            "id", "datumIzvestaja", "merniListBr", "roba", "neto", "bruto", "tara",
            "registracija", "prevoznik", "posiljalac", "porucilac", "primalac", "vozac",
            "createdAt", "updatedAt"
        )

        val DISTINCT_FIELD_WHITELIST = setOf(
            "roba", "registracija", "prevoznik", "primalac", "posiljalac", "porucilac", "vozac"
        )

        val GROUP_BY_FORMATS = mapOf(
            "day" to "YYYY-MM-DD",
            "week" to "IYYY-\"W\"IW",
            "month" to "YYYY-MM",
            "year" to "YYYY"
        )
    }

    @Transactional
    fun importExcel(file: MultipartFile, uploadedBy: String? = null): ImportResponse {
        val startTime = System.currentTimeMillis()
        val filename = file.originalFilename ?: "unknown"

        val extension = filename.substringAfterLast(".", "").let { ".$it" }
        if (extension !in appProperties.allowedExtensions) {
            throw IllegalArgumentException("File extension '$extension' not allowed. Allowed: ${appProperties.allowedExtensions}")
        }

        val tempFile = File.createTempFile("upload_", extension, File(appProperties.tempDir))
        try {
            file.transferTo(tempFile)
            val parsedRows = excelParsingService.parseExcel(tempFile)
            val reportDate = DateUtils.extractReportDateFromFilename(filename)

            // Upload to Azure Blob Storage
            val blobName = azureBlobStorageService.upload(
                tempFile.inputStream(),
                tempFile.length(),
                filename
            )

            // Create ImportedFile record
            val importedFile = importedFileRepository.save(ImportedFile(
                originalFilename = filename,
                blobName = blobName,
                fileSize = file.size,
                contentType = file.contentType,
                uploadedBy = uploadedBy,
                recordCount = parsedRows.size
            ))

            var inserted = 0
            var updated = 0
            for (row in parsedRows) {
                val existingOpt = if (reportDate != null) {
                    repository.findByDatumIzvestajaAndMerniListBr(reportDate, row.merniListBr)
                } else {
                    java.util.Optional.empty()
                }

                if (existingOpt.isPresent) {
                    val entity = existingOpt.get()
                    applyRowToEntity(entity, row, importedFile, reportDate)
                    repository.save(entity)
                    updated++
                } else {
                    val entity = Merenje()
                    applyRowToEntity(entity, row, importedFile, reportDate)
                    repository.save(entity)
                    inserted++
                }
            }

            val processingTimeMs = System.currentTimeMillis() - startTime
            return ImportResponse(
                inserted = inserted,
                updated = updated,
                totalRows = parsedRows.size,
                filename = filename,
                processingTimeMs = processingTimeMs,
                fileId = importedFile.id
            )
        } finally {
            tempFile.delete()
        }
    }

    @Transactional(readOnly = true)
    fun queryMeasurements(
        page: Int,
        pageSize: Int,
        datumOd: LocalDate?,
        datumDo: LocalDate?,
        roba: String?,
        registracija: String?,
        prevoznik: String?,
        posiljalac: String?,
        porucilac: String?,
        primalac: String?,
        vozac: String?,
        sortBy: String,
        sortOrder: String
    ): MeasurementsResponse {
        if (sortBy !in SORT_WHITELIST) {
            throw IllegalArgumentException("Invalid sortBy field: $sortBy. Allowed: $SORT_WHITELIST")
        }

        var spec: Specification<Merenje> = Specification { _, _, _ -> null }
        if (datumOd != null) spec = spec.and(MerenjeSpecification.datumOd(datumOd))
        if (datumDo != null) spec = spec.and(MerenjeSpecification.datumDo(datumDo))
        if (!roba.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textLike("roba", roba))
        if (!registracija.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textLike("registracija", registracija))
        if (!prevoznik.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textLike("prevoznik", prevoznik))
        if (!posiljalac.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textLike("posiljalac", posiljalac))
        if (!porucilac.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textLike("porucilac", porucilac))
        if (!primalac.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textLike("primalac", primalac))
        if (!vozac.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textLike("vozac", vozac))

        val direction = if (sortOrder.equals("ASC", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(page - 1, pageSize, Sort.by(direction, sortBy))
        val result = repository.findAll(spec, pageable)

        val filtersApplied = mutableMapOf<String, String>()
        if (datumOd != null) filtersApplied["datumOd"] = datumOd.toString()
        if (datumDo != null) filtersApplied["datumDo"] = datumDo.toString()
        if (!roba.isNullOrBlank()) filtersApplied["roba"] = roba
        if (!registracija.isNullOrBlank()) filtersApplied["registracija"] = registracija
        if (!prevoznik.isNullOrBlank()) filtersApplied["prevoznik"] = prevoznik
        if (!posiljalac.isNullOrBlank()) filtersApplied["posiljalac"] = posiljalac
        if (!porucilac.isNullOrBlank()) filtersApplied["porucilac"] = porucilac
        if (!primalac.isNullOrBlank()) filtersApplied["primalac"] = primalac
        if (!vozac.isNullOrBlank()) filtersApplied["vozac"] = vozac

        return MeasurementsResponse(
            data = result.content.map { it.toDto() },
            pagination = PaginationMeta(
                totalCount = result.totalElements,
                page = page,
                pageSize = pageSize,
                totalPages = result.totalPages
            ),
            filtersApplied = filtersApplied
        )
    }

    fun getStats(
        groupBy: String,
        datumOd: LocalDate?,
        datumDo: LocalDate?,
        porucilac: String?,
        vozac: String?,
        roba: String?
    ): StatsResponse {
        val format = GROUP_BY_FORMATS[groupBy]
            ?: throw IllegalArgumentException("Invalid groupBy: $groupBy. Allowed: ${GROUP_BY_FORMATS.keys}")

        val results = repository.findStats(
            format = format,
            datumOd = datumOd?.toString(),
            datumDo = datumDo?.toString(),
            porucilac = if (!porucilac.isNullOrBlank()) "%$porucilac%" else null,
            vozac = if (!vozac.isNullOrBlank()) "%$vozac%" else null,
            roba = if (!roba.isNullOrBlank()) "%$roba%" else null
        )

        val dataPoints = results.map { StatsDataPoint(period = it.period, count = it.count) }
        val totalCount = dataPoints.sumOf { it.count }

        return StatsResponse(
            groupBy = groupBy,
            data = dataPoints,
            totalCount = totalCount
        )
    }

    fun getDistinctValues(field: String, search: String?): DistinctValuesResponse {
        if (field !in DISTINCT_FIELD_WHITELIST) {
            throw IllegalArgumentException("Invalid field: $field. Allowed: $DISTINCT_FIELD_WHITELIST")
        }

        val values: List<String> = when (field) {
            "roba" -> repository.findDistinctRoba()
            "registracija" -> repository.findDistinctRegistracija()
            "prevoznik" -> repository.findDistinctPrevoznik()
            "primalac" -> repository.findDistinctPrimalac()
            "posiljalac" -> repository.findDistinctPosiljalac()
            "porucilac" -> repository.findDistinctPorucilac()
            "vozac" -> repository.findDistinctVozac()
            else -> emptyList()
        }

        val filtered = if (!search.isNullOrBlank()) {
            values.filter { it.contains(search, ignoreCase = true) }
        } else {
            values
        }

        return DistinctValuesResponse(
            field = field,
            data = filtered,
            totalCount = filtered.size
        )
    }

    fun getCustomers(search: String?): CustomersResponse {
        val customers = repository.findCustomersWithCounts()
        val filtered = if (!search.isNullOrBlank()) {
            customers.filter { it.porucilac.contains(search, ignoreCase = true) }
        } else {
            customers
        }

        return CustomersResponse(
            data = filtered.map { CustomerRecord(porucilac = it.porucilac, measurementCount = it.measurementCount) },
            totalCount = filtered.size
        )
    }

    fun getDrivers(search: String?): DriversResponse {
        val drivers = repository.findDriversWithCounts()
        val filtered = if (!search.isNullOrBlank()) {
            drivers.filter { it.vozac.contains(search, ignoreCase = true) }
        } else {
            drivers
        }

        return DriversResponse(
            data = filtered.map { DriverRecord(vozac = it.vozac, measurementCount = it.measurementCount) },
            totalCount = filtered.size
        )
    }

    @Transactional
    fun updateSignature(id: Long, signature: String?): SignatureResponse {
        if (signature != null && !signature.startsWith("data:image/png;base64,")) {
            throw IllegalArgumentException("Signature must start with 'data:image/png;base64,'")
        }

        val entity = repository.findById(id).orElseThrow {
            NoSuchElementException("Measurement with id $id not found")
        }

        repository.updateSignature(id, signature)

        return SignatureResponse(
            measurementId = id,
            hasSignature = signature != null
        )
    }

    private fun applyRowToEntity(
        entity: Merenje,
        row: ExcelParsingService.ParsedRow,
        importedFile: ImportedFile,
        reportDate: LocalDate?
    ) {
        entity.importedFile = importedFile
        entity.datumIzvestaja = reportDate
        entity.merniListBr = row.merniListBr
        entity.posiljalac = row.posiljalac
        entity.porucilac = row.porucilac
        entity.primalac = row.primalac
        entity.roba = row.roba
        entity.bruto = row.bruto
        entity.tara = row.tara
        entity.neto = row.neto
        entity.prevoznik = row.prevoznik
        entity.registracija = row.registracija
        entity.prikolica = row.prikolica
        entity.vozac = row.vozac
        entity.brojLicneKarte = row.brojLicneKarte
    }

    private fun Merenje.toDto(): MeasurementDto = MeasurementDto(
        id = id,
        izvorFajl = importedFile?.originalFilename,
        importedFileId = importedFile?.id,
        datumIzvestaja = datumIzvestaja?.toString(),
        merniListBr = merniListBr,
        posiljalac = posiljalac,
        porucilac = porucilac,
        primalac = primalac,
        roba = roba,
        bruto = bruto,
        tara = tara,
        neto = neto,
        prevoznik = prevoznik,
        registracija = registracija,
        prikolica = prikolica,
        vozac = vozac,
        brojLicneKarte = brojLicneKarte,
        potpis = potpis,
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )
}
