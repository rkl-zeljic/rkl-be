package com.rkl.backend.service

import com.rkl.backend.entity.Merenje
import com.rkl.backend.entity.Otpremnica
import com.rkl.backend.entity.Prevoznica
import com.rkl.backend.repository.MerenjeRepository
import com.rkl.backend.repository.PrevoznicaRepository
import com.rkl.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MerenjeFromOtpremnicaService(
    private val merenjeRepository: MerenjeRepository,
    private val prevoznicaRepository: PrevoznicaRepository,
    private val userRepository: UserRepository,
    private val labelService: LabelService
) {

    private val log = LoggerFactory.getLogger(MerenjeFromOtpremnicaService::class.java)

    companion object {
        const val IZVOR_OTPREMNICA = "OTPREMNICA"
        const val IZVOR_IMPORT = "IMPORT"
    }

    @Transactional
    fun createMerenjeFromOtpremnica(otpremnica: Otpremnica): Merenje? {
        val merniListBr = otpremnica.merniListBr ?: return null

        val prevoznica = otpremnica.id?.let { prevoznicaRepository.findByOtpremnicaId(it) }

        // Check if merenje already exists for this datum+merniListBr
        val existingOpt = merenjeRepository.findByDatumIzvestajaAndMerniListBr(otpremnica.datum, merniListBr)

        val merenje = if (existingOpt.isPresent) {
            val existing = existingOpt.get()
            // If owned by a different otpremnica, error
            if (existing.otpremnica != null && existing.otpremnica?.id != otpremnica.id) {
                throw IllegalArgumentException(
                    "Merni list br $merniListBr za datum ${otpremnica.datum} je već zauzet od otpremnice ${existing.otpremnica?.brojOtpremnice}"
                )
            }
            // Adopt existing import merenje or update own
            applyOtpremnicaToMerenje(existing, otpremnica, prevoznica)
            existing
        } else {
            val newMerenje = Merenje()
            applyOtpremnicaToMerenje(newMerenje, otpremnica, prevoznica)
            newMerenje
        }

        val saved = merenjeRepository.save(merenje)
        log.info("Created/updated merenje {} from otpremnica {}", saved.id, otpremnica.brojOtpremnice)
        return saved
    }

    @Transactional
    fun updateMerenjeFromOtpremnica(otpremnica: Otpremnica) {
        val merniListBr = otpremnica.merniListBr ?: return

        val prevoznica = otpremnica.id?.let { prevoznicaRepository.findByOtpremnicaId(it) }

        // Find merenje linked to this otpremnica
        val merenje = otpremnica.id?.let { merenjeRepository.findByOtpremnicaId(it) }

        if (merenje != null) {
            // Check if datum/merniListBr changed and new combo is unique
            if (merenje.datumIzvestaja != otpremnica.datum || merenje.merniListBr != merniListBr) {
                val conflictOpt = merenjeRepository.findByDatumIzvestajaAndMerniListBr(otpremnica.datum, merniListBr)
                if (conflictOpt.isPresent && conflictOpt.get().id != merenje.id) {
                    throw IllegalArgumentException(
                        "Merni list br $merniListBr za datum ${otpremnica.datum} je već zauzet"
                    )
                }
            }
            applyOtpremnicaToMerenje(merenje, otpremnica, prevoznica)
            merenjeRepository.save(merenje)
            log.info("Updated merenje {} from otpremnica {}", merenje.id, otpremnica.brojOtpremnice)
        } else {
            // Merenje doesn't exist yet (edge case), create it
            createMerenjeFromOtpremnica(otpremnica)
        }
    }

    @Transactional
    fun updateMerenjeFromPrevoznica(prevoznica: Prevoznica) {
        val otpremnica = prevoznica.otpremnica ?: return
        val merenje = otpremnica.id?.let { merenjeRepository.findByOtpremnicaId(it) } ?: return

        merenje.posiljalac = labelService.resolveValue("posiljalac", prevoznica.posiljalac)
        merenje.primalac = labelService.resolveValue("primalac", prevoznica.primalac)
        merenje.mesto = labelService.resolveValue("mesto", prevoznica.mestoUtovara)

        merenjeRepository.save(merenje)
        log.info("Updated merenje {} with prevoznica data (posiljalac, primalac, mesto)", merenje.id)
    }

    @Transactional
    fun deleteMerenjeFromOtpremnica(otpremnica: Otpremnica) {
        val otpremnicaId = otpremnica.id ?: return
        val merenje = merenjeRepository.findByOtpremnicaId(otpremnicaId)
        if (merenje != null) {
            merenjeRepository.delete(merenje)
            log.info("Deleted merenje {} linked to otpremnica {}", merenje.id, otpremnica.brojOtpremnice)
        }
    }

    private fun applyOtpremnicaToMerenje(merenje: Merenje, otpremnica: Otpremnica, prevoznica: Prevoznica?) {
        merenje.datumIzvestaja = otpremnica.datum
        merenje.merniListBr = otpremnica.merniListBr!!
        merenje.porucilac = labelService.resolveValue("porucilac", otpremnica.porucilac)
        merenje.prevoznik = labelService.resolveValue("prevoznik", otpremnica.prevoznik)
        merenje.registracija = labelService.resolveValue("registracija",
            com.rkl.backend.util.TextUtils.normalizeRegistration(otpremnica.registracija))
        merenje.roba = labelService.resolveValue("roba", otpremnica.nazivRobe)
        merenje.bruto = otpremnica.bruto
        merenje.tara = otpremnica.tara
        merenje.neto = otpremnica.neto
        merenje.vozac = labelService.resolveValue("vozac", otpremnica.vozacIme)
        merenje.otpremnica = otpremnica
        merenje.izvor = IZVOR_OTPREMNICA

        // Prevoznica data (if linked)
        if (prevoznica != null) {
            merenje.posiljalac = labelService.resolveValue("posiljalac", prevoznica.posiljalac)
            merenje.primalac = labelService.resolveValue("primalac", prevoznica.primalac)
            merenje.mesto = labelService.resolveValue("mesto", prevoznica.mestoUtovara)
        }

        // Link to driver user
        if (!merenje.vozac.isNullOrBlank()) {
            merenje.vozacUser = userRepository.findByDriverNameIgnoreCase(merenje.vozac!!)
        }
        if (merenje.vozacUser == null && otpremnica.vozacUser != null) {
            merenje.vozacUser = otpremnica.vozacUser
        }
    }
}
