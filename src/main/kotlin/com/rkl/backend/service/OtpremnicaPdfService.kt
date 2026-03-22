package com.rkl.backend.service

import com.lowagie.text.*
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import com.rkl.backend.entity.Otpremnica
import com.rkl.backend.repository.OtpremnicaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.util.Base64

@Service
class OtpremnicaPdfService(
    private val otpremnicaRepository: OtpremnicaRepository
) {

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy.")
        private val BLACK = Color(0, 0, 0)
    }

    @Transactional(readOnly = true)
    fun generatePdf(id: Long): ByteArray {
        val otpremnica = otpremnicaRepository.findById(id).orElseThrow {
            NoSuchElementException("Otpremnica sa id $id nije pronađena")
        }
        return buildPdf(otpremnica)
    }

    private fun buildPdf(o: Otpremnica): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 40f, 40f, 30f, 30f)
        PdfWriter.getInstance(document, outputStream)
        document.open()

        val baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1250, BaseFont.EMBEDDED)
        val baseFontBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1250, BaseFont.EMBEDDED)

        val subtitleFont = Font(baseFont, 9f, Font.ITALIC)
        val labelFont = Font(baseFont, 9f)
        val valueFont = Font(baseFontBold, 10f)
        val titleFont = Font(baseFontBold, 14f)
        val numberFont = Font(baseFontBold, 14f)
        val dateFont = Font(baseFont, 10f)
        val headerFont = Font(baseFontBold, 10f)
        val cellFont = Font(baseFont, 10f)
        val cellBoldFont = Font(baseFontBold, 12f)
        val sectionFont = Font(baseFontBold, 11f)
        val smallFont = Font(baseFont, 8f)

        // === "Otpremni list (važi kao ugovor)" ===
        val headerParagraph = Paragraph("Otpremni list (važi kao ugovor)", subtitleFont)
        document.add(headerParagraph)
        document.add(Paragraph("\n"))

        // === TWO BOXES: Prodavac (left) | Poručilac info (right) ===
        val infoTable = PdfPTable(2)
        infoTable.widthPercentage = 100f
        infoTable.setWidths(floatArrayOf(1f, 1f))

        // Left box: Prodavac (fixed)
        val prodavacCell = PdfPCell()
        prodavacCell.borderWidth = 1f
        prodavacCell.borderColor = BLACK
        prodavacCell.setPadding(8f)
        prodavacCell.addElement(createLabelValue("Prodavac:", "Rudnik kamena Likodra", labelFont, valueFont))
        prodavacCell.addElement(createLabelValue("Adresa:", "Likodra bb", labelFont, valueFont))
        prodavacCell.addElement(createLabelValue("Mesto:", "Likodra", labelFont, valueFont))
        prodavacCell.addElement(createLabelValue("PIB:", "110458699", labelFont, valueFont))
        infoTable.addCell(prodavacCell)

        // Right box: Poručilac, Prevoznik, Vozač, Registracija
        val kupacCell = PdfPCell()
        kupacCell.borderWidth = 1f
        kupacCell.borderColor = BLACK
        kupacCell.setPadding(8f)
        kupacCell.addElement(createLabelValue("Poručilac:", o.porucilac, labelFont, valueFont))
        kupacCell.addElement(createLabelValue("Prevoznik:", o.prevoznik, labelFont, valueFont))
        kupacCell.addElement(createLabelValue("Vozač:", o.vozacIme, labelFont, valueFont))
        kupacCell.addElement(createLabelValue("Registracija:", o.registracija, labelFont, valueFont))
        infoTable.addCell(kupacCell)

        document.add(infoTable)
        document.add(Paragraph("\n"))

        // === OTPREMNICA BR. and DATUM ===
        val numberTable = PdfPTable(2)
        numberTable.widthPercentage = 100f
        numberTable.setWidths(floatArrayOf(1.2f, 0.8f))

        // Left: OTPREMNICA BR. + Datum
        val leftNumCell = PdfPCell()
        leftNumCell.border = Rectangle.NO_BORDER
        leftNumCell.paddingBottom = 5f

        val otpremnicaBrPhrase = Phrase()
        otpremnicaBrPhrase.add(Chunk("OTPREMNICA BR.  ", titleFont))
        otpremnicaBrPhrase.add(Chunk(o.brojOtpremnice, numberFont))
        leftNumCell.addElement(Paragraph(otpremnicaBrPhrase))
        leftNumCell.addElement(Paragraph("\n"))

        val datumPhrase = Phrase()
        datumPhrase.add(Chunk("Datum izdavanja robe:  ", labelFont))
        datumPhrase.add(Chunk(o.datum.format(DATE_FORMAT), valueFont))
        leftNumCell.addElement(Paragraph(datumPhrase))
        numberTable.addCell(leftNumCell)

        // Right: Potpis vozača box
        val sigVozacCell = PdfPCell()
        sigVozacCell.borderWidth = 1f
        sigVozacCell.borderColor = BLACK
        sigVozacCell.minimumHeight = 60f
        sigVozacCell.horizontalAlignment = Element.ALIGN_CENTER
        sigVozacCell.verticalAlignment = Element.ALIGN_MIDDLE
        sigVozacCell.setPadding(5f)

        if (!o.potpisVozaca.isNullOrBlank() && o.potpisVozaca!!.startsWith("data:image")) {
            val sigImage = base64ToImage(o.potpisVozaca!!)
            if (sigImage != null) {
                sigImage.scaleToFit(120f, 50f)
                sigImage.alignment = Element.ALIGN_CENTER
                sigVozacCell.addElement(sigImage)
            }
        }
        numberTable.addCell(sigVozacCell)

        // Add label "Potpis vozača" underneath the right column
        document.add(numberTable)

        val potpisVozacaLabel = Paragraph("Potpis vozača", smallFont)
        potpisVozacaLabel.alignment = Element.ALIGN_RIGHT
        potpisVozacaLabel.indentationRight = 40f
        document.add(potpisVozacaLabel)
        document.add(Paragraph("\n"))

        // === GOODS TABLE ===
        val goodsTable = PdfPTable(5)
        goodsTable.widthPercentage = 100f
        goodsTable.setWidths(floatArrayOf(3f, 1.2f, 1f, 1f, 1f))

        // Header row
        addGoodsCell(goodsTable, "Naziv robe", headerFont, true)
        addGoodsCell(goodsTable, "Jedinica\nMere", headerFont, true)
        addGoodsCell(goodsTable, "Bruto", headerFont, true)
        addGoodsCell(goodsTable, "Tara", headerFont, true)
        addGoodsCell(goodsTable, "Neto", headerFont, true)

        // Data row
        addGoodsCell(goodsTable, o.nazivRobe, cellFont, false)
        addGoodsCell(goodsTable, o.jedinicaMere, cellFont, false)
        addGoodsCell(goodsTable, "%.0f".format(o.bruto), cellFont, false)
        addGoodsCell(goodsTable, "%.0f".format(o.tara), cellFont, false)
        addGoodsCell(goodsTable, "%.0f".format(o.neto), cellBoldFont, false)

        document.add(goodsTable)
        document.add(Paragraph("\n\n\n"))

        // === SIGNATURES SECTION: Robu izdao (left) | Robu primio (right) ===
        val sigTable = PdfPTable(2)
        sigTable.widthPercentage = 100f
        sigTable.setWidths(floatArrayOf(1f, 1f))

        // Left: Robu izdao
        val izdaoCell = PdfPCell()
        izdaoCell.border = Rectangle.NO_BORDER
        izdaoCell.horizontalAlignment = Element.ALIGN_CENTER
        izdaoCell.addElement(createCenteredParagraph("Robu izdao", sectionFont))
        izdaoCell.addElement(Paragraph("\n"))

        // Signature box for izdavalac
        val izdaoSigTable = PdfPTable(1)
        izdaoSigTable.widthPercentage = 80f
        val izdaoSigCell = PdfPCell()
        izdaoSigCell.borderWidth = 1f
        izdaoSigCell.borderColor = BLACK
        izdaoSigCell.minimumHeight = 60f
        izdaoSigCell.horizontalAlignment = Element.ALIGN_CENTER
        izdaoSigCell.verticalAlignment = Element.ALIGN_MIDDLE
        izdaoSigCell.setPadding(5f)

        if (!o.potpisIzdavaoca.isNullOrBlank() && o.potpisIzdavaoca!!.startsWith("data:image")) {
            val izdSigImage = base64ToImage(o.potpisIzdavaoca!!)
            if (izdSigImage != null) {
                izdSigImage.scaleToFit(120f, 50f)
                izdSigImage.alignment = Element.ALIGN_CENTER
                izdaoSigCell.addElement(izdSigImage)
            }
        }
        izdaoSigTable.addCell(izdaoSigCell)
        izdaoCell.addElement(izdaoSigTable)

        izdaoCell.addElement(Paragraph("\n"))
        izdaoCell.addElement(createCenteredParagraph("Potpis izdavaoca robe", smallFont))
        sigTable.addCell(izdaoCell)

        // Right: Robu primio
        val primioCell = PdfPCell()
        primioCell.border = Rectangle.NO_BORDER
        primioCell.horizontalAlignment = Element.ALIGN_CENTER
        primioCell.addElement(createCenteredParagraph("Robu primio", sectionFont))
        primioCell.addElement(Paragraph("\n"))

        // Signature box for primalac
        val primioSigTable = PdfPTable(1)
        primioSigTable.widthPercentage = 80f
        val primioSigCell = PdfPCell()
        primioSigCell.borderWidth = 1f
        primioSigCell.borderColor = BLACK
        primioSigCell.minimumHeight = 60f
        primioSigCell.horizontalAlignment = Element.ALIGN_CENTER
        primioSigCell.verticalAlignment = Element.ALIGN_MIDDLE
        primioSigCell.setPadding(5f)

        if (!o.potpisPrimaoca.isNullOrBlank()) {
            val primSigImage = base64ToImage(o.potpisPrimaoca!!)
            if (primSigImage != null) {
                primSigImage.scaleToFit(120f, 50f)
                primSigImage.alignment = Element.ALIGN_CENTER
                primioSigCell.addElement(primSigImage)
            }
        }
        primioSigTable.addCell(primioSigCell)
        primioCell.addElement(primioSigTable)

        primioCell.addElement(Paragraph("\n"))
        primioCell.addElement(createCenteredParagraph("Potpis primaoca robe", smallFont))
        sigTable.addCell(primioCell)

        document.add(sigTable)
        document.close()

        return outputStream.toByteArray()
    }

    private fun createLabelValue(label: String, value: String, labelFont: Font, valueFont: Font): Paragraph {
        val phrase = Phrase()
        phrase.add(Chunk("$label ", labelFont))
        phrase.add(Chunk(value, valueFont))
        return Paragraph(phrase)
    }

    private fun addGoodsCell(table: PdfPTable, text: String, font: Font, isHeader: Boolean) {
        val cell = PdfPCell(Phrase(text, font))
        cell.borderWidth = 1f
        cell.borderColor = BLACK
        cell.horizontalAlignment = Element.ALIGN_CENTER
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        cell.paddingBottom = if (isHeader) 8f else 6f
        cell.paddingTop = if (isHeader) 8f else 6f
        cell.paddingLeft = 4f
        cell.paddingRight = 4f
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
