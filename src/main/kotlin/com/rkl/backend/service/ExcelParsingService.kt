package com.rkl.backend.service

import com.rkl.backend.util.TextUtils
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileInputStream

@Service
class ExcelParsingService {

    private val logger = LoggerFactory.getLogger(ExcelParsingService::class.java)

    companion object {
        const val HEADER_MARKER = "Merni list br"
        const val MAX_HEADER_SCAN_ROWS = 250
        val EXPECTED_COLUMNS = listOf(
            "Merni list br",
            "Pošiljalac",
            "Poručilac",
            "Primalac",
            "Roba",
            "Bruto",
            "Tara",
            "Neto",
            "Prevoznik",
            "Registracija",
            "Prikolica",
            "Vozač",
            "Broj lične karte"
        )
    }

    data class ParsedRow(
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
        val brojLicneKarte: String?
    )

    fun parseExcel(file: File): List<ParsedRow> {
        val workbook = openWorkbook(file)
        return workbook.use { wb ->
            val sheet = wb.getSheetAt(0)
            val headerRowIndex = findHeaderRow(sheet)
            val headerRow = sheet.getRow(headerRowIndex)
            val columnIndex = buildColumnIndex(headerRow)
            val rows = mutableListOf<ParsedRow>()

            for (i in (headerRowIndex + 1)..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val parsed = parseRow(row, columnIndex)
                if (parsed != null) {
                    rows.add(parsed)
                }
            }
            rows
        }
    }

    private fun openWorkbook(file: File): Workbook {
        val stream = FileInputStream(file)
        return if (file.name.endsWith(".xls")) {
            HSSFWorkbook(stream)
        } else {
            XSSFWorkbook(stream)
        }
    }

    private fun findHeaderRow(sheet: Sheet): Int {
        val scanLimit = minOf(MAX_HEADER_SCAN_ROWS, sheet.lastRowNum + 1)
        for (i in 0 until scanLimit) {
            val row = sheet.getRow(i) ?: continue
            val cell = row.getCell(0) ?: continue
            val value = getCellStringValue(cell)
            if (value != null && value.trim().equals(HEADER_MARKER, ignoreCase = true)) {
                return i
            }
        }
        throw IllegalArgumentException("Header row with '$HEADER_MARKER' not found in first $MAX_HEADER_SCAN_ROWS rows")
    }

    private fun buildColumnIndex(headerRow: Row): Map<String, Int> {
        val index = mutableMapOf<String, Int>()
        for (i in 0..headerRow.lastCellNum) {
            val cell = headerRow.getCell(i) ?: continue
            val value = getCellStringValue(cell)?.trim() ?: continue
            for (expected in EXPECTED_COLUMNS) {
                if (value.equals(expected, ignoreCase = true)) {
                    index[expected] = i
                    break
                }
            }
        }
        return index
    }

    private fun parseRow(row: Row, columnIndex: Map<String, Int>): ParsedRow? {
        val merniListIdx = columnIndex[HEADER_MARKER] ?: return null
        val merniListCell = row.getCell(merniListIdx) ?: return null
        val merniListBr = getNumericValue(merniListCell)?.toInt() ?: return null

        return ParsedRow(
            merniListBr = merniListBr,
            posiljalac = TextUtils.normalizeWhitespace(getStringFromColumn(row, columnIndex, "Pošiljalac")),
            porucilac = TextUtils.normalizeWhitespace(getStringFromColumn(row, columnIndex, "Poručilac")),
            primalac = TextUtils.normalizeWhitespace(getStringFromColumn(row, columnIndex, "Primalac")),
            roba = TextUtils.normalizeWhitespace(getStringFromColumn(row, columnIndex, "Roba")),
            bruto = getNumericFromColumn(row, columnIndex, "Bruto"),
            tara = getNumericFromColumn(row, columnIndex, "Tara"),
            neto = getNumericFromColumn(row, columnIndex, "Neto"),
            prevoznik = TextUtils.normalizeWhitespace(getStringFromColumn(row, columnIndex, "Prevoznik")),
            registracija = TextUtils.normalizeWhitespace(getStringFromColumn(row, columnIndex, "Registracija")),
            prikolica = TextUtils.normalizeWhitespace(getStringFromColumn(row, columnIndex, "Prikolica")),
            vozac = TextUtils.normalizeWhitespace(getStringFromColumn(row, columnIndex, "Vozač")),
            brojLicneKarte = TextUtils.normalizeWhitespace(getStringFromColumn(row, columnIndex, "Broj lične karte"))
        )
    }

    private fun getStringFromColumn(row: Row, columnIndex: Map<String, Int>, column: String): String? {
        val idx = columnIndex[column] ?: return null
        val cell = row.getCell(idx) ?: return null
        return getCellStringValue(cell)
    }

    private fun getNumericFromColumn(row: Row, columnIndex: Map<String, Int>, column: String): Double? {
        val idx = columnIndex[column] ?: return null
        val cell = row.getCell(idx) ?: return null
        return getNumericValue(cell)
    }

    private fun getCellStringValue(cell: Cell): String? {
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> cell.numericCellValue.toString()
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> try {
                cell.stringCellValue
            } catch (_: Exception) {
                try {
                    cell.numericCellValue.toString()
                } catch (_: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    private fun getNumericValue(cell: Cell): Double? {
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue.trim().toDoubleOrNull()
            CellType.FORMULA -> try {
                cell.numericCellValue
            } catch (_: Exception) {
                null
            }
            else -> null
        }
    }
}
