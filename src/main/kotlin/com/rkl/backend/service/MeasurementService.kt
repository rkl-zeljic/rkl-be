package com.rkl.backend.service

import com.rkl.backend.config.AppProperties
import com.rkl.backend.dto.common.PaginationMeta
import com.rkl.backend.dto.measurement.*
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
    private val userRepository: com.rkl.backend.repository.UserRepository,
    private val excelParsingService: ExcelParsingService,
    private val azureBlobStorageService: AzureBlobStorageService,
    private val appProperties: AppProperties,
    private val labelService: LabelService
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

        if (importedFileRepository.existsByOriginalFilename(filename)) {
            throw IllegalArgumentException("Fajl sa imenom '$filename' je već importovan")
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
                val effectiveDate = row.datum ?: reportDate
                val existingOpt = if (effectiveDate != null) {
                    repository.findByDatumIzvestajaAndMerniListBr(effectiveDate, row.merniListBr)
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
    fun queryMeasurements(filter: MeasurementFilterRequest): MeasurementsResponse {
        if (filter.sortBy !in SORT_WHITELIST) {
            throw IllegalArgumentException("Invalid sortBy field: ${filter.sortBy}. Allowed: $SORT_WHITELIST")
        }

        var spec: Specification<Merenje> = Specification { _, _, _ -> null }
        if (filter.datumOd != null) spec = spec.and(MerenjeSpecification.datumOd(filter.datumOd))
        if (filter.datumDo != null) spec = spec.and(MerenjeSpecification.datumDo(filter.datumDo))
        if (!filter.roba.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textFilter("roba", filter.roba))
        if (!filter.registracija.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textFilter("registracija", filter.registracija))
        if (!filter.prevoznik.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textFilter("prevoznik", filter.prevoznik))
        if (!filter.posiljalac.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textFilter("posiljalac", filter.posiljalac))
        if (!filter.porucilac.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textFilter("porucilac", filter.porucilac))
        if (!filter.primalac.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textFilter("primalac", filter.primalac))
        if (!filter.vozac.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textFilter("vozac", filter.vozac))

        val direction = if (filter.sortOrder.equals("ASC", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(filter.page - 1, filter.pageSize, Sort.by(direction, filter.sortBy))
        val result = repository.findAll(spec, pageable)

        val filtersApplied = mutableMapOf<String, String>()
        if (filter.datumOd != null) filtersApplied["datumOd"] = filter.datumOd.toString()
        if (filter.datumDo != null) filtersApplied["datumDo"] = filter.datumDo.toString()
        if (!filter.roba.isNullOrBlank()) filtersApplied["roba"] = filter.roba
        if (!filter.registracija.isNullOrBlank()) filtersApplied["registracija"] = filter.registracija
        if (!filter.prevoznik.isNullOrBlank()) filtersApplied["prevoznik"] = filter.prevoznik
        if (!filter.posiljalac.isNullOrBlank()) filtersApplied["posiljalac"] = filter.posiljalac
        if (!filter.porucilac.isNullOrBlank()) filtersApplied["porucilac"] = filter.porucilac
        if (!filter.primalac.isNullOrBlank()) filtersApplied["primalac"] = filter.primalac
        if (!filter.vozac.isNullOrBlank()) filtersApplied["vozac"] = filter.vozac

        return MeasurementsResponse(
            data = result.content.map { it.toDto() },
            pagination = PaginationMeta(
                totalCount = result.totalElements,
                page = filter.page,
                pageSize = filter.pageSize,
                totalPages = result.totalPages
            ),
            filtersApplied = filtersApplied
        )
    }

    @Transactional(readOnly = true)
    fun queryMyMeasurements(filter: MeasurementFilterRequest, email: String): MeasurementsResponse {
        val user = userRepository.findByEmail(email)
            ?: throw NoSuchElementException("User not found")

        if (filter.sortBy !in SORT_WHITELIST) {
            throw IllegalArgumentException("Invalid sortBy field: ${filter.sortBy}. Allowed: $SORT_WHITELIST")
        }

        var spec: Specification<Merenje> = MerenjeSpecification.byVozacUserId(user.id!!)
        if (filter.datumOd != null) spec = spec.and(MerenjeSpecification.datumOd(filter.datumOd))
        if (filter.datumDo != null) spec = spec.and(MerenjeSpecification.datumDo(filter.datumDo))
        if (!filter.roba.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textFilter("roba", filter.roba))
        if (!filter.registracija.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textFilter("registracija", filter.registracija))
        if (!filter.prevoznik.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textFilter("prevoznik", filter.prevoznik))
        if (!filter.posiljalac.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textFilter("posiljalac", filter.posiljalac))
        if (!filter.porucilac.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textFilter("porucilac", filter.porucilac))
        if (!filter.primalac.isNullOrBlank()) spec = spec.and(MerenjeSpecification.textFilter("primalac", filter.primalac))

        val direction = if (filter.sortOrder.equals("ASC", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(filter.page - 1, filter.pageSize, Sort.by(direction, filter.sortBy))
        val result = repository.findAll(spec, pageable)

        val filtersApplied = mutableMapOf<String, String>()
        if (filter.datumOd != null) filtersApplied["datumOd"] = filter.datumOd.toString()
        if (filter.datumDo != null) filtersApplied["datumDo"] = filter.datumDo.toString()
        if (!filter.roba.isNullOrBlank()) filtersApplied["roba"] = filter.roba
        if (!filter.registracija.isNullOrBlank()) filtersApplied["registracija"] = filter.registracija
        if (!filter.prevoznik.isNullOrBlank()) filtersApplied["prevoznik"] = filter.prevoznik
        if (!filter.posiljalac.isNullOrBlank()) filtersApplied["posiljalac"] = filter.posiljalac
        if (!filter.porucilac.isNullOrBlank()) filtersApplied["porucilac"] = filter.porucilac
        if (!filter.primalac.isNullOrBlank()) filtersApplied["primalac"] = filter.primalac

        return MeasurementsResponse(
            data = result.content.map { it.toDto() },
            pagination = PaginationMeta(
                totalCount = result.totalElements,
                page = filter.page,
                pageSize = filter.pageSize,
                totalPages = result.totalPages
            ),
            filtersApplied = filtersApplied
        )
    }

    fun getStats(filter: StatsFilterRequest): StatsResponse {
        val format = GROUP_BY_FORMATS[filter.groupBy]
            ?: throw IllegalArgumentException("Invalid groupBy: ${filter.groupBy}. Allowed: ${GROUP_BY_FORMATS.keys}")

        val results = repository.findStats(
            format = format,
            datumOd = filter.datumOd?.toString(),
            datumDo = filter.datumDo?.toString(),
            porucilac = if (!filter.porucilac.isNullOrBlank()) "%${filter.porucilac}%" else null,
            vozac = if (!filter.vozac.isNullOrBlank()) "%${filter.vozac}%" else null,
            roba = if (!filter.roba.isNullOrBlank()) "%${filter.roba}%" else null
        )

        val dataPoints = results.map { StatsDataPoint(period = it.period, count = it.count) }
        val totalCount = dataPoints.sumOf { it.count }

        return StatsResponse(
            groupBy = filter.groupBy,
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

    @Transactional
    fun relinkDriverMeasurements(userId: Long, oldDriverName: String?, newDriverName: String?) {
        // Unlink old measurements if the driverName changed
        if (!oldDriverName.isNullOrBlank() && oldDriverName != newDriverName) {
            val unlinked = repository.unlinkMeasurementsFromDriver(userId)
            logger.info("Unlinked $unlinked measurements from user $userId (old driverName: $oldDriverName)")
        }
        // Link new measurements
        if (!newDriverName.isNullOrBlank()) {
            val linked = repository.linkMeasurementsToDriver(userId, newDriverName)
            logger.info("Linked $linked measurements to user $userId (driverName: $newDriverName)")
        }
    }

    private fun applyRowToEntity(
        entity: Merenje,
        row: ExcelParsingService.ParsedRow,
        importedFile: ImportedFile,
        reportDate: LocalDate?
    ) {
        entity.importedFile = importedFile
        entity.datumIzvestaja = row.datum ?: reportDate
        entity.merniListBr = row.merniListBr
        entity.posiljalac = labelService.resolveValue("posiljalac", row.posiljalac)
        entity.porucilac = labelService.resolveValue("porucilac", row.porucilac)
        entity.primalac = labelService.resolveValue("primalac", row.primalac)
        entity.roba = labelService.resolveValue("roba", row.roba)
        entity.bruto = row.bruto
        entity.tara = row.tara
        entity.neto = row.neto
        entity.prevoznik = labelService.resolveValue("prevoznik", row.prevoznik)
        entity.registracija = labelService.resolveValue("registracija", normalizeRegistrationValue(row.registracija))
        entity.vozac = labelService.resolveValue("vozac", row.vozac)
        entity.mesto = labelService.resolveValue("mesto", row.mesto)

        // Link to driver user if driverName matches
        if (!entity.vozac.isNullOrBlank()) {
            entity.vozacUser = userRepository.findByDriverNameIgnoreCase(entity.vozac!!)
        }
    }

    /**
     * Normalizes a registration plate format: uppercase, groups separated by dashes.
     * This is applied before label resolution so that "SA 194 GB" becomes "SA-194-GB".
     */
    private fun normalizeRegistrationValue(value: String?): String? {
        if (value.isNullOrBlank()) return value
        return com.rkl.backend.util.TextUtils.normalizeRegistration(value)
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
        vozac = vozac,
        mesto = mesto,
        potpis = potpis,
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )
}
