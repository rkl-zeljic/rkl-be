package com.rkl.backend.service

import com.lowagie.text.*
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import com.rkl.backend.entity.Prevoznica
import com.rkl.backend.repository.PrevoznicaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.util.Base64

@Service
class PrevoznicaPdfService(
    private val prevoznicaRepository: PrevoznicaRepository
) {

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val TEAL = Color(0, 128, 128)
    }

    @Transactional(readOnly = true)
    fun generatePdf(id: Long): ByteArray {
        val prevoznica = prevoznicaRepository.findById(id).orElseThrow {
            NoSuchElementException("Prevoznica sa id $id nije pronađena")
        }
        return buildPdf(prevoznica)
    }

    private fun buildPdf(p: Prevoznica): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 40f, 40f, 30f, 30f)
        PdfWriter.getInstance(document, outputStream)
        document.open()

        val fontStream = this::class.java.getResourceAsStream("/fonts/DejaVuSans.ttf")!!.readBytes()
        val fontBoldStream = this::class.java.getResourceAsStream("/fonts/DejaVuSans-Bold.ttf")!!.readBytes()
        val baseFont = BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontStream, null)
        val baseFontBold = BaseFont.createFont("DejaVuSans-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBoldStream, null)

        val titleFont = Font(baseFontBold, 16f, Font.BOLD)
        val numberFont = Font(baseFontBold, 16f, Font.BOLD, TEAL)
        val subtitleFont = Font(baseFont, 9f, Font.ITALIC)
        val dateFont = Font(baseFont, 10f)
        val labelFont = Font(baseFont, 9f)
        val valueFont = Font(baseFontBold, 10f)
        val headerFont = Font(baseFontBold, 11f)
        val cellFont = Font(baseFont, 10f)
        val sectionFont = Font(baseFontBold, 12f)
        val smallFont = Font(baseFont, 8f)

        // === HEADER: PREVOZNI LIST  No. ===
        val headerTable = PdfPTable(2)
        headerTable.widthPercentage = 100f
        headerTable.setWidths(floatArrayOf(1f, 1f))

        val titleCell = PdfPCell()
        titleCell.border = Rectangle.NO_BORDER
        val titlePhrase = Phrase()
        titlePhrase.add(Chunk("PREVOZNI LIST  ", titleFont))
        titlePhrase.add(Chunk("No ${p.brojPrevoznice}", numberFont))
        titleCell.addElement(titlePhrase)
        titleCell.addElement(Phrase("(Važi kao ugovor)", subtitleFont))
        headerTable.addCell(titleCell)

        val emptyCell = PdfPCell(Phrase(""))
        emptyCell.border = Rectangle.NO_BORDER
        headerTable.addCell(emptyCell)

        document.add(headerTable)
        document.add(Paragraph("\n"))

        // Date
        document.add(Paragraph("Datum: ${p.datum.format(DATE_FORMAT)} god.", dateFont))
        document.add(Paragraph("\n"))

        // === MAIN INFO TABLE ===
        val infoTable = PdfPTable(2)
        infoTable.widthPercentage = 100f
        infoTable.setWidths(floatArrayOf(1f, 1f))

        addInfoRow(infoTable, "Pošiljalac:", p.posiljalac, "Mesto utovara:", p.mestoUtovara, labelFont, valueFont)
        addInfoRow(infoTable, "Platilac prevoza:", p.platilacPrevoza, "Mesto istovara:", p.mestoIstovara, labelFont, valueFont)
        addInfoRow(infoTable, "Primalac:", p.primalac, "Reg.br. vozila:", p.registracija, labelFont, valueFont)
        addInfoRow(infoTable, "Prevozilac:", p.prevozilac, "Datum utovara:", p.datumUtovara.format(DATE_FORMAT), labelFont, valueFont)
        addInfoRow(infoTable, "Prateća dokumenta:", p.pratecaDokumenta ?: "", "Datum istovara:", p.datumIstovara.format(DATE_FORMAT), labelFont, valueFont)

        document.add(infoTable)
        document.add(Paragraph("\n"))

        // === GOODS TABLE ===
        val goodsTable = PdfPTable(4)
        goodsTable.widthPercentage = 100f
        goodsTable.setWidths(floatArrayOf(3f, 1f, 1f, 1.5f))

        // Header row
        addGoodsHeaderCell(goodsTable, "VRSTA ROBE", headerFont)
        addGoodsHeaderCell(goodsTable, "Km", headerFont)
        addGoodsHeaderCell(goodsTable, "Jed. mere", headerFont)
        addGoodsHeaderCell(goodsTable, "Stvarna težina", headerFont)

        // Data row
        addGoodsDataCell(goodsTable, p.vrstaRobe, cellFont)
        addGoodsDataCell(goodsTable, p.km?.let { "%.0f".format(it) } ?: "", cellFont)
        addGoodsDataCell(goodsTable, p.jedMere, cellFont)
        addGoodsDataCell(goodsTable, p.stvarnaTezina?.let { "%.0f".format(it) } ?: "", cellFont)

        document.add(goodsTable)
        document.add(Paragraph("\n\n"))

        // === SIGNATURES SECTION ===
        val sigTable = PdfPTable(2)
        sigTable.widthPercentage = 100f
        sigTable.setWidths(floatArrayOf(1f, 1f))

        // Left: VOZAČ
        val driverCell = PdfPCell()
        driverCell.border = Rectangle.NO_BORDER
        driverCell.horizontalAlignment = Element.ALIGN_CENTER
        driverCell.addElement(createCenteredParagraph("VOZAČ", sectionFont))
        driverCell.addElement(createCenteredParagraph("robe i prateća dokumenta ispravno primio:", smallFont))
        driverCell.addElement(createCenteredParagraph(p.vozacIme, valueFont))
        driverCell.addElement(Paragraph("\n"))

        // Driver signature
        if (!p.potpisVozaca.isNullOrBlank()) {
            val driverSigImage = base64ToImage(p.potpisVozaca!!)
            if (driverSigImage != null) {
                driverSigImage.scaleToFit(150f, 60f)
                driverSigImage.alignment = Element.ALIGN_CENTER
                driverCell.addElement(driverSigImage)
            }
        }

        driverCell.addElement(Paragraph("\n"))
        driverCell.addElement(createCenteredParagraph("_________________________________", cellFont))
        driverCell.addElement(createCenteredParagraph("(Potpis)", smallFont))
        sigTable.addCell(driverCell)

        // Right: PRIMALAC
        val recipientCell = PdfPCell()
        recipientCell.border = Rectangle.NO_BORDER
        recipientCell.horizontalAlignment = Element.ALIGN_CENTER
        recipientCell.addElement(createCenteredParagraph("PRIMALAC:", sectionFont))
        recipientCell.addElement(createCenteredParagraph("robe i prateća dokumenta ispravno primio:", smallFont))
        recipientCell.addElement(createCenteredParagraph("Datum: ${p.datumIstovara.format(DATE_FORMAT)} god.", dateFont))
        recipientCell.addElement(Paragraph("\n"))

        // Recipient signature
        if (!p.potpisPrimaoca.isNullOrBlank()) {
            val recipientSigImage = base64ToImage(p.potpisPrimaoca!!)
            if (recipientSigImage != null) {
                recipientSigImage.scaleToFit(150f, 60f)
                recipientSigImage.alignment = Element.ALIGN_CENTER
                recipientCell.addElement(recipientSigImage)
            }
        }

        recipientCell.addElement(Paragraph("\n"))
        recipientCell.addElement(createCenteredParagraph("_________________________________", cellFont))
        recipientCell.addElement(createCenteredParagraph("(Potpis ovlašćenog lica)", smallFont))
        sigTable.addCell(recipientCell)

        document.add(sigTable)
        document.close()

        return outputStream.toByteArray()
    }

    private fun addInfoRow(
        table: PdfPTable,
        leftLabel: String, leftValue: String,
        rightLabel: String, rightValue: String,
        labelFont: Font, valueFont: Font
    ) {
        val leftCell = PdfPCell()
        leftCell.borderColor = TEAL
        leftCell.borderWidth = 1f
        leftCell.paddingBottom = 5f
        leftCell.paddingTop = 3f
        leftCell.paddingLeft = 5f
        val leftPhrase = Phrase()
        leftPhrase.add(Chunk("$leftLabel ", labelFont))
        leftPhrase.add(Chunk(leftValue, valueFont))
        leftCell.addElement(leftPhrase)
        table.addCell(leftCell)

        val rightCell = PdfPCell()
        rightCell.borderColor = TEAL
        rightCell.borderWidth = 1f
        rightCell.paddingBottom = 5f
        rightCell.paddingTop = 3f
        rightCell.paddingLeft = 5f
        val rightPhrase = Phrase()
        rightPhrase.add(Chunk("$rightLabel ", labelFont))
        rightPhrase.add(Chunk(rightValue, valueFont))
        rightCell.addElement(rightPhrase)
        table.addCell(rightCell)
    }

    private fun addGoodsHeaderCell(table: PdfPTable, text: String, font: Font) {
        val cell = PdfPCell(Phrase(text, font))
        cell.borderColor = TEAL
        cell.borderWidth = 1f
        cell.horizontalAlignment = Element.ALIGN_CENTER
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        cell.paddingBottom = 8f
        cell.paddingTop = 8f
        table.addCell(cell)
    }

    private fun addGoodsDataCell(table: PdfPTable, text: String, font: Font) {
        val cell = PdfPCell(Phrase(text, font))
        cell.borderColor = TEAL
        cell.borderWidth = 1f
        cell.horizontalAlignment = Element.ALIGN_CENTER
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        cell.paddingBottom = 6f
        cell.paddingTop = 6f
        table.addCell(cell)
    }

    private fun createCenteredParagraph(text: String, font: Font): Paragraph {
        val p = Paragraph(text, font)
        p.alignment = Element.ALIGN_CENTER
        return p
    }

    private fun base64ToImage(dataUrl: String): Image? {
        return try {
            val base64Data = dataUrl.substringAfter("base64,")
            val imageBytes = Base64.getDecoder().decode(base64Data)
            Image.getInstance(imageBytes)
        } catch (e: Exception) {
            null
        }
    }
}
