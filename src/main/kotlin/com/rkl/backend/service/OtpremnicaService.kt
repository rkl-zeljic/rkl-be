package com.rkl.backend.service

import com.rkl.backend.dto.otpremnica.*
import com.rkl.backend.entity.Otpremnica
import com.rkl.backend.entity.OtpremnicaStatus
import com.rkl.backend.repository.OtpremnicaRepository
import com.rkl.backend.repository.PrimalacRepository
import com.rkl.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OtpremnicaService(
    private val otpremnicaRepository: OtpremnicaRepository,
    private val userRepository: UserRepository,
    private val primalacRepository: PrimalacRepository,
    private val otpremnicaPdfService: OtpremnicaPdfService,
    private val emailService: EmailService
) {

    private val log = LoggerFactory.getLogger(OtpremnicaService::class.java)

    companion object {
        private const val DEFAULT_IZDAVALAC_SIGNATURE = "RKL"
    }

    @Transactional(readOnly = true)
    fun listOtpremnice(): OtpremniceResponse {
        val otpremnice = otpremnicaRepository.findAllByOrderByCreatedAtDesc()
        return OtpremniceResponse(data = otpremnice.map { it.toDto() })
    }

    @Transactional(readOnly = true)
    fun getMyOtpremnice(email: String): OtpremniceResponse {
        val user = userRepository.findByEmail(email)
            ?: userRepository.findByUsername(email)
            ?: throw NoSuchElementException("Korisnik nije pronađen")
        val otpremnice = otpremnicaRepository.findByVozacUserOrderByCreatedAtDesc(user)
        return OtpremniceResponse(data = otpremnice.map { it.toDto() })
    }

    @Transactional(readOnly = true)
    fun getOtpremnica(id: Long): OtpremnicaDetailResponse {
        val otpremnica = otpremnicaRepository.findById(id).orElseThrow {
            NoSuchElementException("Otpremnica sa id $id nije pronađena")
        }
        return OtpremnicaDetailResponse(data = otpremnica.toDto())
    }

    @Transactional
    fun createOtpremnica(request: CreateOtpremnicaRequest, createdBy: String?): OtpremnicaDetailResponse {
        val vozacUser = userRepository.findById(request.vozacUserId).orElseThrow {
            NoSuchElementException("Vozač sa id ${request.vozacUserId} nije pronađen")
        }

        // Validate uniqueness of custom number
        val existing = otpremnicaRepository.findByBrojOtpremnice(request.brojOtpremnice)
        if (existing != null) {
            throw IllegalArgumentException("Otpremnica sa brojem ${request.brojOtpremnice} već postoji")
        }

        val otpremnica = Otpremnica(
            brojOtpremnice = request.brojOtpremnice,
            datum = request.datum,
            porucilac = request.porucilac,
            prevoznik = request.prevoznik,
            registracija = request.registracija,
            nazivRobe = request.nazivRobe,
            jedinicaMere = request.jedinicaMere,
            bruto = request.bruto,
            tara = request.tara,
            neto = request.neto,
            vozacUser = vozacUser,
            vozacIme = vozacUser.driverName ?: vozacUser.email ?: vozacUser.username ?: "",
            potpisVozaca = vozacUser.signature,
            potpisIzdavaoca = DEFAULT_IZDAVALAC_SIGNATURE,
            bezMerenja = request.bezMerenja,
            status = OtpremnicaStatus.KREIRANA,
            createdBy = createdBy
        )

        val saved = otpremnicaRepository.save(otpremnica)

        // Send PDF to buyer if email is available
        try {
            val primalac = primalacRepository.findByNaziv(request.porucilac)
            if (primalac?.email != null) {
                val pdfBytes = otpremnicaPdfService.generatePdf(saved.id!!)
                emailService.sendDocumentWithAttachment(
                    toEmail = primalac.email!!,
                    documentType = "Otpremnica",
                    documentNumber = request.brojOtpremnice,
                    porucilac = request.porucilac,
                    pdfBytes = pdfBytes
                )
            }
        } catch (e: Exception) {
            log.error("Failed to send otpremnica email: {}", e.message)
        }

        return OtpremnicaDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun updatePrimalacSignature(id: Long, request: UpdateOtpremnicaSignatureRequest): OtpremnicaDetailResponse {
        val otpremnica = otpremnicaRepository.findById(id).orElseThrow {
            NoSuchElementException("Otpremnica sa id $id nije pronađena")
        }

        if (!request.potpisPrimaoca.startsWith("data:image/png;base64,")) {
            throw IllegalArgumentException("Potpis mora biti u formatu data:image/png;base64,...")
        }

        otpremnica.potpisPrimaoca = request.potpisPrimaoca
        otpremnica.status = OtpremnicaStatus.POTPISANA

        val saved = otpremnicaRepository.save(otpremnica)
        return OtpremnicaDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun deleteOtpremnica(id: Long): OtpremnicaDeleteResponse {
        val otpremnica = otpremnicaRepository.findById(id).orElseThrow {
            NoSuchElementException("Otpremnica sa id $id nije pronađena")
        }
        otpremnicaRepository.delete(otpremnica)
        return OtpremnicaDeleteResponse(id = id)
    }

    private fun Otpremnica.toDto(): OtpremnicaDto = OtpremnicaDto(
        id = id,
        brojOtpremnice = brojOtpremnice,
        datum = datum.toString(),
        porucilac = porucilac,
        prevoznik = prevoznik,
        registracija = registracija,
        nazivRobe = nazivRobe,
        jedinicaMere = jedinicaMere,
        bruto = bruto,
        tara = tara,
        neto = neto,
        vozacUserId = vozacUser?.id,
        vozacIme = vozacIme,
        potpisVozaca = !potpisVozaca.isNullOrBlank(),
        potpisIzdavaoca = !potpisIzdavaoca.isNullOrBlank(),
        potpisPrimaoca = !potpisPrimaoca.isNullOrBlank(),
        bezMerenja = bezMerenja,
        merenjeGenerated = merenjeGeneratedFile != null,
        status = status.name,
        createdBy = createdBy,
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )
}
