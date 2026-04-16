package com.rkl.backend.service

import com.rkl.backend.dto.prevoznica.*
import com.rkl.backend.entity.Prevoznica
import com.rkl.backend.entity.PrevoznicaStatus
import com.rkl.backend.repository.OtpremnicaRepository
import com.rkl.backend.repository.PrevoznicaRepository
import com.rkl.backend.repository.PrimalacRepository
import com.rkl.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class PrevoznicaService(
    private val prevoznicaRepository: PrevoznicaRepository,
    private val otpremnicaRepository: OtpremnicaRepository,
    private val userRepository: UserRepository,
    private val primalacRepository: PrimalacRepository,
    private val prevoznicaPdfService: PrevoznicaPdfService,
    private val emailService: EmailService,
    private val merenjeFromOtpremnicaService: MerenjeFromOtpremnicaService
) {

    private val log = LoggerFactory.getLogger(PrevoznicaService::class.java)

    companion object {
        private val NUMBER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    @Transactional(readOnly = true)
    fun listPrevoznice(): PrevozniceResponse {
        val prevoznice = prevoznicaRepository.findAllByOrderByCreatedAtDesc()
        return PrevozniceResponse(data = prevoznice.map { it.toDto() })
    }

    @Transactional(readOnly = true)
    fun getPrevoznica(id: Long): PrevoznicaDetailResponse {
        val prevoznica = prevoznicaRepository.findById(id).orElseThrow {
            NoSuchElementException("Prevoznica sa id $id nije pronađena")
        }
        return PrevoznicaDetailResponse(data = prevoznica.toDto())
    }

    @Transactional(readOnly = true)
    fun getMyPrevoznice(email: String): PrevozniceResponse {
        val user = userRepository.findByEmail(email)
            ?: userRepository.findByUsername(email)
            ?: throw NoSuchElementException("Korisnik nije pronađen")
        val prevoznice = prevoznicaRepository.findByVozacUserOrderByCreatedAtDesc(user)
        return PrevozniceResponse(data = prevoznice.map { it.toDto() })
    }

    @Transactional
    fun createPrevoznica(request: CreatePrevoznicaRequest, createdBy: String?): PrevoznicaDetailResponse {
        val vozacUser = userRepository.findById(request.vozacUserId).orElseThrow {
            NoSuchElementException("Vozač sa id ${request.vozacUserId} nije pronađen")
        }

        val brojPrevoznice = generateBrojPrevoznice(request.datum)

        val otpremnica = request.otpremnicaId?.let {
            otpremnicaRepository.findById(it).orElseThrow {
                NoSuchElementException("Otpremnica sa id $it nije pronađena")
            }
        }

        val prevoznica = Prevoznica(
            brojPrevoznice = brojPrevoznice,
            datum = request.datum,
            posiljalac = request.posiljalac,
            platilacPrevoza = request.platilacPrevoza,
            primalac = request.primalac,
            prevozilac = request.prevozilac,
            pratecaDokumenta = request.pratecaDokumenta,
            mestoUtovara = request.mestoUtovara,
            mestoIstovara = request.mestoIstovara,
            registracija = request.registracija,
            datumUtovara = request.datumUtovara,
            datumIstovara = request.datumIstovara,
            vrstaRobe = request.vrstaRobe,
            km = request.km,
            jedMere = request.jedMere,
            stvarnaTezina = request.stvarnaTezina,
            otpremnica = otpremnica,
            vozacUser = vozacUser,
            vozacIme = vozacUser.driverName ?: vozacUser.email ?: vozacUser.username ?: "",
            potpisVozaca = vozacUser.signature,
            additionalEmails = request.additionalEmails.takeIf { it.isNotEmpty() }?.joinToString(","),
            status = PrevoznicaStatus.KREIRANA,
            createdBy = createdBy
        )

        val saved = prevoznicaRepository.save(prevoznica)

        // Update linked merenje with prevoznica data
        if (saved.otpremnica != null) {
            merenjeFromOtpremnicaService.updateMerenjeFromPrevoznica(saved)
        }

        return PrevoznicaDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun updatePrevoznica(id: Long, request: UpdatePrevoznicaRequest, updatedBy: String?): PrevoznicaDetailResponse {
        val prevoznica = prevoznicaRepository.findById(id).orElseThrow {
            NoSuchElementException("Prevoznica sa id $id nije pronađena")
        }

        if (prevoznica.status != PrevoznicaStatus.KREIRANA) {
            throw IllegalStateException("Potpisana prevoznica se ne može menjati")
        }

        val vozacUser = userRepository.findById(request.vozacUserId).orElseThrow {
            NoSuchElementException("Vozač sa id ${request.vozacUserId} nije pronađen")
        }

        val otpremnica = request.otpremnicaId?.let {
            otpremnicaRepository.findById(it).orElseThrow {
                NoSuchElementException("Otpremnica sa id $it nije pronađena")
            }
        }

        prevoznica.datum = request.datum
        prevoznica.posiljalac = request.posiljalac
        prevoznica.platilacPrevoza = request.platilacPrevoza
        prevoznica.primalac = request.primalac
        prevoznica.prevozilac = request.prevozilac
        prevoznica.pratecaDokumenta = request.pratecaDokumenta
        prevoznica.mestoUtovara = request.mestoUtovara
        prevoznica.mestoIstovara = request.mestoIstovara
        prevoznica.registracija = request.registracija
        prevoznica.datumUtovara = request.datumUtovara
        prevoznica.datumIstovara = request.datumIstovara
        prevoznica.vrstaRobe = request.vrstaRobe
        prevoznica.km = request.km
        prevoznica.jedMere = request.jedMere
        prevoznica.stvarnaTezina = request.stvarnaTezina
        prevoznica.otpremnica = otpremnica
        prevoznica.vozacUser = vozacUser
        prevoznica.vozacIme = vozacUser.driverName ?: vozacUser.email ?: vozacUser.username ?: ""
        prevoznica.potpisVozaca = vozacUser.signature
        prevoznica.additionalEmails = request.additionalEmails.takeIf { it.isNotEmpty() }?.joinToString(",")

        val saved = prevoznicaRepository.save(prevoznica)

        // Update linked merenje with prevoznica data
        if (saved.otpremnica != null) {
            merenjeFromOtpremnicaService.updateMerenjeFromPrevoznica(saved)
        }

        return PrevoznicaDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun updatePrimalacSignature(id: Long, request: UpdatePrevoznicaSignatureRequest): PrevoznicaDetailResponse {
        val prevoznica = prevoznicaRepository.findById(id).orElseThrow {
            NoSuchElementException("Prevoznica sa id $id nije pronađena")
        }

        if (!request.potpisPrimaoca.startsWith("data:image/png;base64,")) {
            throw IllegalArgumentException("Potpis mora biti u formatu data:image/png;base64,...")
        }

        prevoznica.potpisPrimaoca = request.potpisPrimaoca
        prevoznica.status = PrevoznicaStatus.POTPISANA

        val saved = prevoznicaRepository.save(prevoznica)

        // Send PDF to all relevant parties on signature
        try {
            val recipients = mutableListOf<String>()
            // Firma koja prevozi
            val prevozilacEntity = primalacRepository.findByNaziv(saved.prevozilac)
            if (prevozilacEntity?.email != null) {
                recipients.add(prevozilacEntity.email!!)
            }
            // Firma koja prima
            val primalacEntity = primalacRepository.findByNaziv(saved.primalac)
            if (primalacEntity?.email != null) {
                recipients.add(primalacEntity.email!!)
            }
            // Additional emails
            saved.additionalEmails?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                ?.let { recipients.addAll(it) }

            val uniqueRecipients = recipients.distinct()
            if (uniqueRecipients.isNotEmpty()) {
                val pdfBytes = prevoznicaPdfService.generatePdf(saved.id!!)
                emailService.sendDocumentWithAttachment(
                    toEmails = uniqueRecipients,
                    documentType = "Prevoznica",
                    documentNumber = saved.brojPrevoznice,
                    porucilac = saved.primalac,
                    pdfBytes = pdfBytes
                )
            }
        } catch (e: Exception) {
            log.error("Failed to send prevoznica email on signature: {}", e.message)
        }

        return PrevoznicaDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun deletePrevoznica(id: Long): PrevoznicaDeleteResponse {
        val prevoznica = prevoznicaRepository.findById(id).orElseThrow {
            NoSuchElementException("Prevoznica sa id $id nije pronađena")
        }

        if (prevoznica.status != PrevoznicaStatus.KREIRANA) {
            throw IllegalStateException("Potpisana prevoznica se ne može obrisati")
        }

        prevoznicaRepository.delete(prevoznica)
        return PrevoznicaDeleteResponse(id = id)
    }

    @Transactional(readOnly = true)
    fun getDistinctMestaIstovara(search: String?): List<String> {
        return if (search.isNullOrBlank()) {
            prevoznicaRepository.findDistinctMestoIstovara()
        } else {
            prevoznicaRepository.findDistinctMestoIstovara(search)
        }
    }

    private fun generateBrojPrevoznice(datum: LocalDate): String {
        val datePart = datum.format(NUMBER_DATE_FORMAT)
        val maxId = prevoznicaRepository.findMaxId()
        val nextNumber = maxId + 1
        return "${datePart}%03d".format(nextNumber)
    }

    private fun Prevoznica.toDto(): PrevoznicaDto = PrevoznicaDto(
        id = id,
        brojPrevoznice = brojPrevoznice,
        datum = datum.toString(),
        posiljalac = posiljalac,
        platilacPrevoza = platilacPrevoza,
        primalac = primalac,
        prevozilac = prevozilac,
        pratecaDokumenta = pratecaDokumenta,
        mestoUtovara = mestoUtovara,
        mestoIstovara = mestoIstovara,
        registracija = registracija,
        datumUtovara = datumUtovara.toString(),
        datumIstovara = datumIstovara.toString(),
        vrstaRobe = vrstaRobe,
        km = km,
        jedMere = jedMere,
        stvarnaTezina = stvarnaTezina,
        vozacUserId = vozacUser?.id,
        vozacIme = vozacIme,
        otpremnicaId = otpremnica?.id,
        otpremnicaBroj = otpremnica?.brojOtpremnice,
        potpisVozaca = !potpisVozaca.isNullOrBlank(),
        potpisPrimaoca = !potpisPrimaoca.isNullOrBlank(),
        additionalEmails = additionalEmails?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
        status = status.name,
        createdBy = createdBy,
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )
}
