package com.rkl.backend.service

import com.rkl.backend.entity.Faktura
import com.rkl.backend.entity.Merenje
import com.rkl.backend.repository.MerenjeRepository
import com.rkl.backend.repository.MerenjeSpecification
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter

@Service
class FakturaExcelService(
    private val merenjeRepository: MerenjeRepository
) {

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val COLUMNS = listOf(
            "Datum", "Merni list br", "Pošiljalac", "Poručilac", "Primalac",
            "Roba", "Bruto", "Tara", "Neto", "Prevoznik", "Registracija", "Vozač", "Mesto"
        )
    }

    @Transactional(readOnly = true)
    fun generateExcel(faktura: Faktura): ByteArray {
        val measurements = queryMeasurements(faktura)
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Faktura")

        // Styles
        val titleStyle = createTitleStyle(workbook)
        val infoStyle = createInfoStyle(workbook)
        val headerStyle = createHeaderStyle(workbook)
        val textStyle = createCellStyle(workbook)
        val numericStyle = createNumericStyle(workbook)
        val dateStyle = createDateCellStyle(workbook)

        var rowIdx = 0

        // Row 0: Title "FAKTURA"
        val titleRow = sheet.createRow(rowIdx++)
        setTextCell(titleRow, 0, "FAKTURA", titleStyle)
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 3))

        // Row 1: Faktura number
        val numRow = sheet.createRow(rowIdx++)
        setTextCell(numRow, 0, "Br. fakture:", infoStyle)
        setTextCell(numRow, 1, faktura.brojFakture, infoStyle)

        // Row 2: Customer
        val custRow = sheet.createRow(rowIdx++)
        setTextCell(custRow, 0, "Kupac:", infoStyle)
        setTextCell(custRow, 1, faktura.porucilac, infoStyle)

        // Row 3: Period
        val periodRow = sheet.createRow(rowIdx++)
        setTextCell(periodRow, 0, "Period:", infoStyle)
        setTextCell(periodRow, 1, "${faktura.datumOd.format(DATE_FORMAT)} - ${faktura.datumDo.format(DATE_FORMAT)}", infoStyle)

        // Row 4: Napomena (if present)
        if (!faktura.napomena.isNullOrBlank()) {
            val noteRow = sheet.createRow(rowIdx++)
            setTextCell(noteRow, 0, "Napomena:", infoStyle)
            setTextCell(noteRow, 1, faktura.napomena!!, infoStyle)
        }

        // Empty row
        rowIdx++

        // Header row
        val headerRow = sheet.createRow(rowIdx++)
        COLUMNS.forEachIndexed { colIdx, name ->
            setTextCell(headerRow, colIdx, name, headerStyle)
        }

        // Data rows
        for (m in measurements) {
            val dataRow = sheet.createRow(rowIdx++)

            setTextCell(dataRow, 0, m.datumIzvestaja?.format(DATE_FORMAT) ?: "", dateStyle)
            setNumericCell(dataRow, 1, m.merniListBr.toDouble(), numericStyle)
            setTextCell(dataRow, 2, m.posiljalac ?: "", textStyle)
            setTextCell(dataRow, 3, m.porucilac ?: "", textStyle)
            setTextCell(dataRow, 4, m.primalac ?: "", textStyle)
            setTextCell(dataRow, 5, m.roba ?: "", textStyle)
            setOptionalNumericCell(dataRow, 6, m.bruto, numericStyle)
            setOptionalNumericCell(dataRow, 7, m.tara, numericStyle)
            setOptionalNumericCell(dataRow, 8, m.neto, numericStyle)
            setTextCell(dataRow, 9, m.prevoznik ?: "", textStyle)
            setTextCell(dataRow, 10, m.registracija ?: "", textStyle)
            setTextCell(dataRow, 11, m.vozac ?: "", textStyle)
            setTextCell(dataRow, 12, m.mesto ?: "", textStyle)
        }

        // Auto-size columns
        for (i in COLUMNS.indices) {
            sheet.autoSizeColumn(i)
            // Minimum width to prevent too-narrow columns
            if (sheet.getColumnWidth(i) < 3000) {
                sheet.setColumnWidth(i, 3000)
            }
        }

        val outputStream = ByteArrayOutputStream()
        workbook.use { it.write(outputStream) }
        return outputStream.toByteArray()
    }

    private fun queryMeasurements(faktura: Faktura): List<Merenje> {
        var spec = Specification.where(MerenjeSpecification.textFilter("porucilac", faktura.porucilac))
        spec = spec.and(MerenjeSpecification.datumOd(faktura.datumOd))
        spec = spec.and(MerenjeSpecification.datumDo(faktura.datumDo))
        return merenjeRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "datumIzvestaja", "merniListBr"))
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

    private fun setOptionalNumericCell(row: Row, col: Int, value: Double?, style: CellStyle) {
        val cell = row.createCell(col)
        if (value != null) {
            cell.setCellValue(value)
        } else {
            cell.setCellValue("")
        }
        cell.setCellStyle(style)
    }

    private fun createTitleStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.fontHeightInPoints = 16
        style.setFont(font)
        return style
    }

    private fun createInfoStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.fontHeightInPoints = 10
        style.setFont(font)
        return style
    }

    private fun createHeaderStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.fontHeightInPoints = 10
        font.color = IndexedColors.WHITE.index
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.ROYAL_BLUE.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER
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
        style.verticalAlignment = VerticalAlignment.CENTER
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
