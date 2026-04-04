package com.rkl.backend.service

import com.lowagie.text.*
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import com.rkl.backend.entity.Faktura
import com.rkl.backend.entity.Merenje
import com.rkl.backend.repository.MerenjeRepository
import com.rkl.backend.repository.MerenjeSpecification
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter

@Service
class FakturaPdfService(
    private val merenjeRepository: MerenjeRepository
) {

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val COLUMNS = arrayOf(
            "Datum", "Merni list br", "Pošiljalac", "Poručilac", "Primalac",
            "Roba", "Bruto", "Tara", "Neto", "Prevoznik", "Registracija", "Vozač", "Mesto"
        )
        private val COL_WIDTHS = floatArrayOf(
            2.2f, 2f, 2.5f, 2.5f, 2.5f,
            2f, 1.5f, 1.5f, 1.5f, 2.2f, 2.2f, 2.2f, 2f
        )
        private val HEADER_BG = Color(65, 105, 225) // Royal blue
        private val BORDER_COLOR = Color(200, 200, 200)
    }

    @Transactional(readOnly = true)
    fun generatePdf(faktura: Faktura): ByteArray {
        val measurements = queryMeasurements(faktura)
        val outputStream = ByteArrayOutputStream()

        val document = Document(PageSize.A4.rotate(), 20f, 20f, 25f, 25f)
        PdfWriter.getInstance(document, outputStream)
        document.open()

        // Title
        val titleFont = Font(Font.HELVETICA, 18f, Font.BOLD)
        val title = Paragraph("FAKTURA", titleFont)
        title.alignment = Element.ALIGN_LEFT
        title.spacingAfter = 10f
        document.add(title)

        // Info section
        val infoFont = Font(Font.HELVETICA, 10f, Font.NORMAL)
        val boldInfoFont = Font(Font.HELVETICA, 10f, Font.BOLD)

        addInfoLine(document, "Br. fakture:", faktura.brojFakture, boldInfoFont, infoFont)
        addInfoLine(document, "Kupac:", faktura.porucilac, boldInfoFont, infoFont)
        addInfoLine(
            document, "Period:",
            "${faktura.datumOd.format(DATE_FORMAT)} - ${faktura.datumDo.format(DATE_FORMAT)}",
            boldInfoFont, infoFont
        )
        if (!faktura.napomena.isNullOrBlank()) {
            addInfoLine(document, "Napomena:", faktura.napomena!!, boldInfoFont, infoFont)
        }

        document.add(Paragraph(" ")) // spacer

        // Data table
        val table = PdfPTable(COLUMNS.size)
        table.widthPercentage = 100f
        table.setWidths(COL_WIDTHS)

        // Header row
        val headerFont = Font(Font.HELVETICA, 7f, Font.BOLD, Color.WHITE)
        for (col in COLUMNS) {
            val cell = PdfPCell(Phrase(col, headerFont))
            cell.backgroundColor = HEADER_BG
            cell.horizontalAlignment = Element.ALIGN_CENTER
            cell.verticalAlignment = Element.ALIGN_MIDDLE
            cell.setPadding(4f)
            cell.borderColor = BORDER_COLOR
            table.addCell(cell)
        }

        // Data rows
        val cellFont = Font(Font.HELVETICA, 7f, Font.NORMAL)
        val numFont = Font(Font.HELVETICA, 7f, Font.NORMAL)
        val altRowBg = Color(245, 247, 250)

        for ((idx, m) in measurements.withIndex()) {
            val bg = if (idx % 2 == 1) altRowBg else Color.WHITE
            addTextCell(table, m.datumIzvestaja?.format(DATE_FORMAT) ?: "", cellFont, Element.ALIGN_CENTER, bg)
            addTextCell(table, m.merniListBr.toString(), numFont, Element.ALIGN_CENTER, bg)
            addTextCell(table, m.posiljalac ?: "", cellFont, Element.ALIGN_LEFT, bg)
            addTextCell(table, m.porucilac ?: "", cellFont, Element.ALIGN_LEFT, bg)
            addTextCell(table, m.primalac ?: "", cellFont, Element.ALIGN_LEFT, bg)
            addTextCell(table, m.roba ?: "", cellFont, Element.ALIGN_LEFT, bg)
            addNumericCell(table, m.bruto, numFont, bg)
            addNumericCell(table, m.tara, numFont, bg)
            addNumericCell(table, m.neto, numFont, bg)
            addTextCell(table, m.prevoznik ?: "", cellFont, Element.ALIGN_LEFT, bg)
            addTextCell(table, m.registracija ?: "", cellFont, Element.ALIGN_LEFT, bg)
            addTextCell(table, m.vozac ?: "", cellFont, Element.ALIGN_LEFT, bg)
            addTextCell(table, m.mesto ?: "", cellFont, Element.ALIGN_LEFT, bg)
        }

        // Sum row
        val sumFont = Font(Font.HELVETICA, 7f, Font.BOLD)
        val sumBg = Color(230, 230, 230)
        for (i in 0 until 8) {
            addTextCell(table, if (i == 0) "UKUPNO:" else "", sumFont, Element.ALIGN_RIGHT, sumBg)
        }
        val netoSum = measurements.sumOf { it.neto ?: 0.0 }
        addNumericCell(table, netoSum, sumFont, sumBg)
        for (i in 9 until COLUMNS.size) {
            addTextCell(table, "", sumFont, Element.ALIGN_LEFT, sumBg)
        }

        document.add(table)
        document.close()
        return outputStream.toByteArray()
    }

    private fun queryMeasurements(faktura: Faktura): List<Merenje> {
        var spec = Specification.where(MerenjeSpecification.textFilter("porucilac", faktura.porucilac))
        spec = spec.and(MerenjeSpecification.datumOd(faktura.datumOd))
        spec = spec.and(MerenjeSpecification.datumDo(faktura.datumDo))
        return merenjeRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "datumIzvestaja", "merniListBr"))
    }

    private fun addInfoLine(document: Document, label: String, value: String, labelFont: Font, valueFont: Font) {
        val p = Paragraph()
        p.add(Chunk(label, labelFont))
        p.add(Chunk(" $value", valueFont))
        p.spacingAfter = 2f
        document.add(p)
    }

    private fun addTextCell(table: PdfPTable, value: String, font: Font, align: Int, bg: Color) {
        val cell = PdfPCell(Phrase(value, font))
        cell.horizontalAlignment = align
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        cell.setPadding(3f)
        cell.borderColor = BORDER_COLOR
        cell.backgroundColor = bg
        table.addCell(cell)
    }

    private fun addNumericCell(table: PdfPTable, value: Double?, font: Font, bg: Color) {
        val text = value?.let { String.format("%.2f", it) } ?: ""
        val cell = PdfPCell(Phrase(text, font))
        cell.horizontalAlignment = Element.ALIGN_RIGHT
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        cell.setPadding(3f)
        cell.borderColor = BORDER_COLOR
        cell.backgroundColor = bg
        table.addCell(cell)
    }
}
