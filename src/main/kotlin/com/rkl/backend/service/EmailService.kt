package com.rkl.backend.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
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
}
