package com.rkl.backend.service

import com.rkl.backend.config.RklConfig
import com.rkl.backend.dto.otpremnica.*
import com.rkl.backend.entity.Otpremnica
import com.rkl.backend.entity.OtpremnicaStatus
import com.rkl.backend.entity.PrevoznicaStatus
import com.rkl.backend.repository.MerenjeRepository
import com.rkl.backend.repository.OtpremnicaRepository
import com.rkl.backend.repository.PrevoznicaRepository
import com.rkl.backend.repository.PrimalacRepository
import com.rkl.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OtpremnicaService(
    private val otpremnicaRepository: OtpremnicaRepository,
    private val prevoznicaRepository: PrevoznicaRepository,
    private val merenjeRepository: MerenjeRepository,
    private val userRepository: UserRepository,
    private val primalacRepository: PrimalacRepository,
    private val otpremnicaPdfService: OtpremnicaPdfService,
    private val emailService: EmailService,
    private val mailConfig: RklConfig.Mail,
    private val merenjeFromOtpremnicaService: MerenjeFromOtpremnicaService
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
            merniListBr = request.merniListBr,
            vozacUser = vozacUser,
            vozacIme = vozacUser.driverName ?: vozacUser.email ?: vozacUser.username ?: "",
            potpisVozaca = vozacUser.signature,
            potpisIzdavaoca = DEFAULT_IZDAVALAC_SIGNATURE,
            bezMerenja = request.bezMerenja,
            additionalEmails = request.additionalEmails.takeIf { it.isNotEmpty() }?.joinToString(","),
            status = OtpremnicaStatus.KREIRANA,
            createdBy = createdBy
        )

        val saved = otpremnicaRepository.save(otpremnica)

        // Create merenje from otpremnica
        if (saved.merniListBr != null) {
            merenjeFromOtpremnicaService.createMerenjeFromOtpremnica(saved)
        }

        return OtpremnicaDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun updateOtpremnica(id: Long, request: UpdateOtpremnicaRequest, updatedBy: String?): OtpremnicaDetailResponse {
        val otpremnica = otpremnicaRepository.findById(id).orElseThrow {
            NoSuchElementException("Otpremnica sa id $id nije pronađena")
        }

        if (otpremnica.status != OtpremnicaStatus.KREIRANA) {
            throw IllegalStateException("Potpisana otpremnica se ne može menjati")
        }

        // Check if linked prevoznica is signed
        val linkedPrevoznica = otpremnica.id?.let { prevoznicaRepository.findByOtpremnicaId(it) }
        if (linkedPrevoznica != null && linkedPrevoznica.status != PrevoznicaStatus.KREIRANA) {
            throw IllegalStateException("Ne može se menjati otpremnica čija prevoznica je potpisana")
        }

        val vozacUser = userRepository.findById(request.vozacUserId).orElseThrow {
            NoSuchElementException("Vozač sa id ${request.vozacUserId} nije pronađen")
        }

        // Validate uniqueness of number if changed
        if (request.brojOtpremnice != otpremnica.brojOtpremnice) {
            val existing = otpremnicaRepository.findByBrojOtpremnice(request.brojOtpremnice)
            if (existing != null) {
                throw IllegalArgumentException("Otpremnica sa brojem ${request.brojOtpremnice} već postoji")
            }
        }

        otpremnica.brojOtpremnice = request.brojOtpremnice
        otpremnica.datum = request.datum
        otpremnica.porucilac = request.porucilac
        otpremnica.prevoznik = request.prevoznik
        otpremnica.registracija = request.registracija
        otpremnica.nazivRobe = request.nazivRobe
        otpremnica.jedinicaMere = request.jedinicaMere
        otpremnica.bruto = request.bruto
        otpremnica.tara = request.tara
        otpremnica.neto = request.neto
        otpremnica.vozacUser = vozacUser
        otpremnica.vozacIme = vozacUser.driverName ?: vozacUser.email ?: vozacUser.username ?: ""
        otpremnica.potpisVozaca = vozacUser.signature
        otpremnica.merniListBr = request.merniListBr
        otpremnica.bezMerenja = request.bezMerenja
        otpremnica.additionalEmails = request.additionalEmails.takeIf { it.isNotEmpty() }?.joinToString(",")

        val saved = otpremnicaRepository.save(otpremnica)

        // Update linked merenje
        if (saved.merniListBr != null) {
            merenjeFromOtpremnicaService.updateMerenjeFromOtpremnica(saved)
        }

        // Cascade update to linked prevoznica (guaranteed KREIRANA at this point)
        if (linkedPrevoznica != null) {
            linkedPrevoznica.pratecaDokumenta = request.brojOtpremnice
            linkedPrevoznica.primalac = request.porucilac
            linkedPrevoznica.prevozilac = request.prevoznik
            linkedPrevoznica.registracija = request.registracija
            linkedPrevoznica.vrstaRobe = request.nazivRobe
            linkedPrevoznica.jedMere = request.jedinicaMere
            linkedPrevoznica.stvarnaTezina = request.neto
            linkedPrevoznica.datumUtovara = request.datum
            linkedPrevoznica.vozacUser = vozacUser
            linkedPrevoznica.vozacIme = vozacUser.driverName ?: vozacUser.email ?: vozacUser.username ?: ""
            linkedPrevoznica.potpisVozaca = vozacUser.signature
            prevoznicaRepository.save(linkedPrevoznica)
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

        // Send PDF to all relevant parties on signature
        try {
            val recipients = mutableListOf<String>()
            recipients.add(mailConfig.adminEmail)
            val primalac = primalacRepository.findByNaziv(saved.porucilac)
            if (primalac?.email != null) {
                recipients.add(primalac.email!!)
            }
            saved.additionalEmails?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                ?.let { recipients.addAll(it) }

            val uniqueRecipients = recipients.distinct()
            if (uniqueRecipients.isNotEmpty()) {
                val pdfBytes = otpremnicaPdfService.generatePdf(saved.id!!)
                emailService.sendDocumentWithAttachment(
                    toEmails = uniqueRecipients,
                    documentType = "Otpremnica",
                    documentNumber = saved.brojOtpremnice,
                    porucilac = saved.porucilac,
                    pdfBytes = pdfBytes
                )
            }
        } catch (e: Exception) {
            log.error("Failed to send otpremnica email on signature: {}", e.message)
        }

        return OtpremnicaDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun deleteOtpremnica(id: Long): OtpremnicaDeleteResponse {
        val otpremnica = otpremnicaRepository.findById(id).orElseThrow {
            NoSuchElementException("Otpremnica sa id $id nije pronađena")
        }

        if (otpremnica.status == OtpremnicaStatus.POTPISANA) {
            throw IllegalStateException("Potpisana otpremnica se ne može obrisati")
        }

        // Delete linked merenje first
        merenjeFromOtpremnicaService.deleteMerenjeFromOtpremnica(otpremnica)

        val linkedPrevoznica = otpremnica.id?.let { prevoznicaRepository.findByOtpremnicaId(it) }
        if (linkedPrevoznica != null) {
            if (linkedPrevoznica.status == PrevoznicaStatus.POTPISANA) {
                throw IllegalStateException("Ne može se obrisati otpremnica čija prevoznica je potpisana")
            }
            prevoznicaRepository.delete(linkedPrevoznica)
        }

        otpremnicaRepository.delete(otpremnica)
        return OtpremnicaDeleteResponse(id = id)
    }

    private fun Otpremnica.toDto(): OtpremnicaDto {
        val linkedMerenje = id?.let { merenjeRepository.findByOtpremnicaId(it) }
        return OtpremnicaDto(
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
            merniListBr = merniListBr,
            merenjeId = linkedMerenje?.id,
            merenjeValidated = linkedMerenje?.importedFile != null,
            vozacUserId = vozacUser?.id,
            vozacIme = vozacIme,
            potpisVozaca = !potpisVozaca.isNullOrBlank(),
            potpisIzdavaoca = !potpisIzdavaoca.isNullOrBlank(),
            potpisPrimaoca = !potpisPrimaoca.isNullOrBlank(),
            bezMerenja = bezMerenja,
            merenjeGenerated = merenjeGeneratedFile != null,
            additionalEmails = additionalEmails?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
            status = status.name,
            createdBy = createdBy,
            createdAt = createdAt?.toString(),
            updatedAt = updatedAt?.toString()
        )
    }
}
