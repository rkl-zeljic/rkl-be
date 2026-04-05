package com.rkl.backend.service

import com.rkl.backend.dto.otpremnica.GenerateMerenjaResponse
import com.rkl.backend.entity.ImportedFile
import com.rkl.backend.repository.ImportedFileRepository
import com.rkl.backend.repository.MerenjeRepository
import com.rkl.backend.repository.OtpremnicaRepository
import com.rkl.backend.repository.PrevoznicaRepository
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class BezMerenjaService(
    private val otpremnicaRepository: OtpremnicaRepository,
    private val prevoznicaRepository: PrevoznicaRepository,
    private val merenjeRepository: MerenjeRepository,
    private val importedFileRepository: ImportedFileRepository,
    private val measurementService: MeasurementService,
    private val azureBlobStorageService: AzureBlobStorageService
) {

    private val log = LoggerFactory.getLogger(BezMerenjaService::class.java)

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val COLUMNS = listOf(
            "Datum", "Merni list br", "Pošiljalac", "Poručilac", "Primalac",
            "Roba", "Bruto", "Tara", "Neto", "Prevoznik", "Registracija", "Vozač", "Mesto"
        )
    }

    @Transactional
    fun generateMerenjaFromOtpremnice(datum: LocalDate, triggeredBy: String?): GenerateMerenjaResponse {
        val otpremnice = otpremnicaRepository.findByDatumAndBezMerenjaTrue(datum)

        if (otpremnice.isEmpty()) {
            throw IllegalStateException("Nema otpremnica sa 'bez merenja' za datum ${datum.format(DATE_FORMAT)}")
        }

        // Clear FK references first, then delete old files
        val previousFiles = otpremnice.mapNotNull { it.merenjeGeneratedFile }.distinctBy { it.id }
        for (o in otpremnice) {
            o.merenjeGeneratedFile = null
        }
        otpremnicaRepository.saveAllAndFlush(otpremnice)

        // Now safe to delete old files (cascade deletes old measurements)
        for (oldFile in previousFiles) {
            deleteGeneratedFile(oldFile)
        }

        val maxMerniListBr = merenjeRepository.findMaxMerniListBrByDatum(datum)

        // Generate Excel
        val excelBytes = generateExcel(otpremnice, datum, maxMerniListBr)
        val shortUuid = UUID.randomUUID().toString().take(4)
        val filename = "bezmerenja-${datum.format(DATE_FORMAT)}-${shortUuid}.xlsx"

        // Import through existing pipeline
        val importResponse = measurementService.importExcelFromBytes(excelBytes, filename, uploadedBy = triggeredBy)

        // Link all otpremnice to the new file
        val importedFile = importedFileRepository.findById(importResponse.fileId!!).orElseThrow()
        for (o in otpremnice) {
            o.merenjeGeneratedFile = importedFile
        }
        otpremnicaRepository.saveAll(otpremnice)

        log.info("Generated {} measurements from otpremnice for date {}, file: {}", otpremnice.size, datum, filename)

        return GenerateMerenjaResponse(
            processedCount = otpremnice.size,
            filename = filename,
            importedFileId = importResponse.fileId
        )
    }

    private fun deleteGeneratedFile(file: ImportedFile) {
        try {
            azureBlobStorageService.delete(file.blobName)
        } catch (e: Exception) {
            log.warn("Failed to delete blob {}: {}", file.blobName, e.message)
        }
        // Cascade deletes all linked merenja
        importedFileRepository.delete(file)
        importedFileRepository.flush()
    }

    private fun generateExcel(
        otpremnice: List<com.rkl.backend.entity.Otpremnica>,
        datum: LocalDate,
        startMerniListBr: Int
    ): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Merenja")

        val headerStyle = createHeaderStyle(workbook)
        val textStyle = createCellStyle(workbook)
        val numericStyle = createNumericStyle(workbook)
        val dateStyle = createDateCellStyle(workbook)

        // Header row
        val headerRow = sheet.createRow(0)
        COLUMNS.forEachIndexed { colIdx, name ->
            val cell = headerRow.createCell(colIdx)
            cell.setCellValue(name)
            cell.setCellStyle(headerStyle)
        }

        // Data rows — enrich from linked prevoznica if available
        otpremnice.forEachIndexed { index, o ->
            val row = sheet.createRow(index + 1)
            val merniListBr = startMerniListBr + index + 1

            // Lookup linked prevoznica for posiljalac, primalac, mesto
            val prevoznica = o.id?.let { prevoznicaRepository.findByOtpremnicaId(it) }

            setTextCell(row, 0, datum.format(DATE_FORMAT), dateStyle)
            setNumericCell(row, 1, merniListBr.toDouble(), numericStyle)
            setTextCell(row, 2, prevoznica?.posiljalac ?: "", textStyle)
            setTextCell(row, 3, o.porucilac, textStyle)
            setTextCell(row, 4, prevoznica?.primalac ?: "", textStyle)
            setTextCell(row, 5, o.nazivRobe, textStyle)
            setNumericCell(row, 6, o.bruto, numericStyle)
            setNumericCell(row, 7, o.tara, numericStyle)
            setNumericCell(row, 8, o.neto, numericStyle)
            setTextCell(row, 9, o.prevoznik, textStyle)
            setTextCell(row, 10, o.registracija, textStyle)
            setTextCell(row, 11, o.vozacIme, textStyle)
            setTextCell(row, 12, prevoznica?.mestoUtovara ?: "", textStyle)
        }

        // Auto-size columns
        for (i in COLUMNS.indices) {
            sheet.autoSizeColumn(i)
            if (sheet.getColumnWidth(i) < 3000) {
                sheet.setColumnWidth(i, 3000)
            }
        }

        val outputStream = ByteArrayOutputStream()
        workbook.use { it.write(outputStream) }
        return outputStream.toByteArray()
    }

    private fun setTextCell(row: Row, col: Int, value: String, style: CellStyle) {
        val cell = row.createCell(col)
        cell.setCellValue(value)
        cell.setCellStyle(style)
    }

    private fun setNumericCell(row: Row, col: Int, value: Double, style: CellStyle) {
        val cell = row.createCell(col)
        cell.setCellValue(value)
        cell.setCellStyle(style)
    }

    private fun createHeaderStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.fontHeightInPoints = 10
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        style.borderBottom = BorderStyle.THIN
        style.borderTop = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
        return style
    }

    private fun createCellStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.fontHeightInPoints = 9
        style.setFont(font)
        style.borderBottom = BorderStyle.THIN
        style.borderTop = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
        return style
    }

    private fun createNumericStyle(workbook: Workbook): CellStyle {
        val style = createCellStyle(workbook)
        style.alignment = HorizontalAlignment.RIGHT
        return style
    }

    private fun createDateCellStyle(workbook: Workbook): CellStyle {
        val style = createCellStyle(workbook)
        style.alignment = HorizontalAlignment.CENTER
        return style
    }
}
