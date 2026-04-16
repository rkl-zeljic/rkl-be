package com.rkl.backend.service

import jakarta.mail.internet.MimeMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.username:noreply@rklzeljic.com}") private val fromEmail: String
) {

    private val log = LoggerFactory.getLogger(EmailService::class.java)

    @Async
    fun sendFakturaStatusEmail(toEmail: String, primalacNaziv: String, brojFakture: String, newStatus: String) {
        try {
            val message = SimpleMailMessage()
            message.from = fromEmail
            message.setTo(toEmail)
            message.subject = "Faktura $brojFakture - promena statusa"
            message.text = """
                Poštovani,

                Obaveštavamo Vas da je faktura $brojFakture za primaoca $primalacNaziv promenila status na: $newStatus.

                Srdačan pozdrav,
                Miki RKL
            """.trimIndent()

            mailSender.send(message)
            log.info("Email sent to {} for faktura {}", toEmail, brojFakture)
        } catch (e: Exception) {
            log.error("Failed to send email to {} for faktura {}: {}", toEmail, brojFakture, e.message)
        }
    }

    @Async
    fun sendFakturaWithAttachment(
        toEmails: List<String>,
        brojFakture: String,
        porucilac: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String
    ) {
        if (toEmails.isEmpty()) return
        try {
            val mimeMessage: MimeMessage = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(mimeMessage, true, "UTF-8")

            helper.setFrom(fromEmail)
            helper.setTo(toEmails.toTypedArray())
            helper.setSubject("Faktura $brojFakture - $porucilac")
            helper.setText("""
                Poštovani,

                U prilogu se nalazi faktura $brojFakture za kupca $porucilac.

                Srdačan pozdrav,
                Miki RKL
            """.trimIndent())

            helper.addAttachment(fileName, ByteArrayResource(fileBytes), mimeType)

            mailSender.send(mimeMessage)
            log.info("Faktura {} sent as attachment to {}", brojFakture, toEmails)
        } catch (e: Exception) {
            log.error("Failed to send faktura {} to {}: {}", brojFakture, toEmails, e.message)
            throw RuntimeException("Greška pri slanju emaila: ${e.message}")
        }
    }

    @Async
    fun sendDocumentWithAttachment(
        toEmails: List<String>,
        documentType: String,
        documentNumber: String,
        porucilac: String,
        pdfBytes: ByteArray
    ) {
        if (toEmails.isEmpty()) return
        try {
            val mimeMessage: MimeMessage = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(mimeMessage, true, "UTF-8")

            helper.setFrom(fromEmail)
            helper.setTo(toEmails.toTypedArray())
            helper.setSubject("$documentType $documentNumber - $porucilac")
            helper.setText("""
                Poštovani,

                U prilogu se nalazi $documentType $documentNumber za kupca $porucilac.

                Srdačan pozdrav,
                Miki RKL
            """.trimIndent())

            val fileName = "$documentType-$documentNumber.pdf"
            helper.addAttachment(fileName, ByteArrayResource(pdfBytes), "application/pdf")

            mailSender.send(mimeMessage)
            log.info("{} {} sent to {}", documentType, documentNumber, toEmails)
        } catch (e: Exception) {
            log.error("Failed to send {} {} to {}: {}", documentType, documentNumber, toEmails, e.message)
        }
    }
}
