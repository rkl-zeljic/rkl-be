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
        const val HEADER_MARKER = "Datum"
        const val MAX_HEADER_SCAN_ROWS = 250
        val EXPECTED_COLUMNS = listOf(
            "Datum",
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
            "Vozač",
            "Mesto"
        )
    }

    data class ParsedRow(
        val datum: java.time.LocalDate?,
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
        val mesto: String?
    )

    fun parseExcel(file: File): List<ParsedRow> {
        val workbook = openWorkbook(file)
        return workbook.use { wb ->
            val sheet = wb.getSheetAt(0)
            val headerRowIndex = findHeaderRow(sheet)
            val headerRow = sheet.getRow(headerRowIndex)
            val columnIndex = buildColumnIndex(headerRow)

            val missingColumns = EXPECTED_COLUMNS.filter { it !in columnIndex }
            if (missingColumns.isNotEmpty()) {
                throw IllegalArgumentException(
                    "Sledeća obavezna polja ne postoje u fajlu: ${missingColumns.joinToString(", ")}. " +
                    "Očekivane kolone su: ${EXPECTED_COLUMNS.joinToString(", ")}"
                )
            }

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
            for (j in 0..row.lastCellNum) {
                val cell = row.getCell(j) ?: continue
                val value = getCellStringValue(cell)?.trim() ?: continue
                if (EXPECTED_COLUMNS.any { value.equals(it, ignoreCase = true) }) {
                    return i
                }
            }
        }
        throw IllegalArgumentException(
            "Nije pronađen red sa zaglavljem tabele u prvih $MAX_HEADER_SCAN_ROWS redova. " +
            "Fajl mora sadržati zaglavlje sa sledećim kolonama: ${EXPECTED_COLUMNS.joinToString(", ")}"
        )
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
        val merniListIdx = columnIndex["Merni list br"] ?: return null
        val merniListCell = row.getCell(merniListIdx) ?: return null
        val merniListBr = getNumericValue(merniListCell)?.toInt() ?: return null

        return ParsedRow(
            datum = getDateFromColumn(row, columnIndex, "Datum"),
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
            vozac = TextUtils.normalizeWhitespace(getStringFromColumn(row, columnIndex, "Vozač")),
            mesto = TextUtils.normalizeWhitespace(getStringFromColumn(row, columnIndex, "Mesto"))
        )
    }

    private fun getDateFromColumn(row: Row, columnIndex: Map<String, Int>, column: String): java.time.LocalDate? {
        val idx = columnIndex[column] ?: return null
        val cell = row.getCell(idx) ?: return null
        return try {
            when (cell.cellType) {
                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        cell.localDateTimeCellValue.toLocalDate()
                    } else {
                        null
                    }
                }
                CellType.STRING -> {
                    val str = cell.stringCellValue.trim()
                    try {
                        java.time.LocalDate.parse(str, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    } catch (_: Exception) {
                        try {
                            java.time.LocalDate.parse(str)
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
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
