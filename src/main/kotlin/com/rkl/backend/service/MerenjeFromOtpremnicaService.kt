package com.rkl.backend.service

import com.rkl.backend.entity.Merenje
import com.rkl.backend.entity.Otpremnica
import com.rkl.backend.entity.Prevoznica
import com.rkl.backend.repository.KupacRepository
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
    private val kupacRepository: KupacRepository,
    private val labelService: LabelService
) {

    private val log = LoggerFactory.getLogger(MerenjeFromOtpremnicaService::class.java)

    companion object {
        const val IZVOR_OTPREMNICA = "OTPREMNICA"
        const val IZVOR_PREVOZNICA = "PREVOZNICA"
        const val IZVOR_IMPORT = "IMPORT"
    }

    @Transactional
    fun createMerenjeFromOtpremnica(otpremnica: Otpremnica): Merenje? {
        val kupacId = otpremnica.kupac?.id
            ?: throw IllegalStateException("Otpremnica ${otpremnica.brojOtpremnice} nema povezanog kupca")

        val prevoznica = otpremnica.id?.let { prevoznicaRepository.findByOtpremnicaId(it) }
        val merniListBr = otpremnica.merniListBr

        // Kada postoji merniListBr, pokušaj da pronađeš/preuzmeš postojeće merenje sa istim brojem.
        // Kada je merniListBr null (bezMerenja=true), uvek pravimo nov record — nema deduplication ključa.
        if (merniListBr != null) {
            val ownedOpt = merenjeRepository.findByKupacIdAndMerniListBr(kupacId, merniListBr)
            if (ownedOpt.isPresent) {
                val existing = ownedOpt.get()
                if (existing.otpremnica != null && existing.otpremnica?.id != otpremnica.id) {
                    throw IllegalArgumentException(
                        "Merni list br $merniListBr za kupca ${otpremnica.kupac?.naziv} je već zauzet od otpremnice ${existing.otpremnica?.brojOtpremnice}"
                    )
                }
                applyOtpremnicaToMerenje(existing, otpremnica, prevoznica)
                val saved = merenjeRepository.save(existing)
                log.info("Updated owned merenje {} from otpremnica {}", saved.id, otpremnica.brojOtpremnice)
                return saved
            }

            val unassignedOpt = merenjeRepository.findUnassignedByDatumAndMerni(otpremnica.datum, merniListBr)
            if (unassignedOpt.isPresent) {
                val claimed = unassignedOpt.get()
                applyOtpremnicaToMerenje(claimed, otpremnica, prevoznica)
                val saved = merenjeRepository.save(claimed)
                log.info("Claimed merenje {} from otpremnica {}", saved.id, otpremnica.brojOtpremnice)
                return saved
            }
        }

        val newMerenje = Merenje()
        applyOtpremnicaToMerenje(newMerenje, otpremnica, prevoznica)
        val saved = merenjeRepository.save(newMerenje)
        log.info("Created merenje {} from otpremnica {} (merniListBr={})", saved.id, otpremnica.brojOtpremnice, merniListBr)
        return saved
    }

    @Transactional
    fun updateMerenjeFromOtpremnica(otpremnica: Otpremnica) {
        val kupacId = otpremnica.kupac?.id
            ?: throw IllegalStateException("Otpremnica ${otpremnica.brojOtpremnice} nema povezanog kupca")

        val prevoznica = otpremnica.id?.let { prevoznicaRepository.findByOtpremnicaId(it) }
        val merniListBr = otpremnica.merniListBr

        // Find merenje linked to this otpremnica
        val merenje = otpremnica.id?.let { merenjeRepository.findByOtpremnicaId(it) }

        if (merenje != null) {
            // Provera (porucilac_id, merni_list_br) jedinstvenosti — samo kada postoji merniListBr
            if (merniListBr != null && (merenje.kupac?.id != kupacId || merenje.merniListBr != merniListBr)) {
                val conflictOpt = merenjeRepository.findByKupacIdAndMerniListBr(kupacId, merniListBr)
                if (conflictOpt.isPresent && conflictOpt.get().id != merenje.id) {
                    throw IllegalArgumentException(
                        "Merni list br $merniListBr za kupca ${otpremnica.kupac?.naziv} je već zauzet"
                    )
                }
            }
            applyOtpremnicaToMerenje(merenje, otpremnica, prevoznica)
            merenjeRepository.save(merenje)
            log.info("Updated merenje {} from otpremnica {}", merenje.id, otpremnica.brojOtpremnice)
        } else {
            // Merenje ne postoji (edge case), kreiraj ga
            createMerenjeFromOtpremnica(otpremnica)
        }
    }

    @Transactional
    fun updateMerenjeFromPrevoznica(prevoznica: Prevoznica) {
        val otpremnica = prevoznica.otpremnica ?: return
        val merenje = otpremnica.id?.let { merenjeRepository.findByOtpremnicaId(it) } ?: return

        merenje.posiljalacRaw = prevoznica.posiljalac
        merenje.primalacRaw = prevoznica.primalac
        merenje.mestoRaw = prevoznica.mestoUtovara
        merenje.posiljalac = labelService.resolveValue("posiljalac", prevoznica.posiljalac)
        merenje.primalac = labelService.resolveValue("primalac", prevoznica.primalac)
        merenje.mesto = labelService.resolveValue("mesto", prevoznica.mestoUtovara)

        merenjeRepository.save(merenje)
        log.info("Updated merenje {} with prevoznica data (posiljalac, primalac, mesto)", merenje.id)
    }

    /**
     * Standalone prevoznica (bez otpremnice) — kreira/ažurira jedan merenje record.
     * merniListBr ostaje null, kupac se traži po primalac nazivu (best effort).
     */
    @Transactional
    fun upsertMerenjeFromStandalonePrevoznica(prevoznica: Prevoznica): Merenje {
        val prevoznicaId = prevoznica.id
            ?: throw IllegalStateException("Prevoznica nije sačuvana — nedostaje id")

        val existing = merenjeRepository.findByPrevoznicaIdAndOtpremnicaIsNull(prevoznicaId)
        val merenje = existing ?: Merenje()

        val kupac = kupacRepository.findByNaziv(prevoznica.primalac.trim())
        merenje.datumIzvestaja = prevoznica.datum
        merenje.merniListBr = null
        merenje.kupac = kupac
        merenje.prevoznica = prevoznica
        merenje.otpremnica = null
        merenje.izvor = IZVOR_PREVOZNICA

        merenje.posiljalacRaw = prevoznica.posiljalac
        merenje.primalacRaw = prevoznica.primalac
        merenje.prevoznikRaw = prevoznica.prevozilac
        merenje.registracijaRaw = com.rkl.backend.util.TextUtils.normalizeRegistration(prevoznica.registracija)
        merenje.robaRaw = prevoznica.vrstaRobe
        merenje.vozacRaw = prevoznica.vozacIme
        merenje.mestoRaw = prevoznica.mestoUtovara
        merenje.posiljalac = labelService.resolveValue("posiljalac", prevoznica.posiljalac)
        merenje.porucilac = labelService.resolveValue("porucilac", prevoznica.primalac)
        merenje.primalac = labelService.resolveValue("primalac", prevoznica.primalac)
        merenje.prevoznik = labelService.resolveValue("prevoznik", prevoznica.prevozilac)
        merenje.registracija = labelService.resolveValue("registracija", merenje.registracijaRaw)
        merenje.roba = labelService.resolveValue("roba", prevoznica.vrstaRobe)
        merenje.neto = prevoznica.stvarnaTezina
        merenje.vozac = labelService.resolveValue("vozac", prevoznica.vozacIme)
        merenje.mesto = labelService.resolveValue("mesto", prevoznica.mestoUtovara)

        if (!merenje.vozac.isNullOrBlank()) {
            merenje.vozacUser = userRepository.findByDriverNameIgnoreCase(merenje.vozac!!)
        }
        if (merenje.vozacUser == null && prevoznica.vozacUser != null) {
            merenje.vozacUser = prevoznica.vozacUser
        }

        val saved = merenjeRepository.save(merenje)
        log.info("{} merenje {} from standalone prevoznica {}",
            if (existing == null) "Created" else "Updated", saved.id, prevoznica.brojPrevoznice)
        return saved
    }

    @Transactional
    fun deleteMerenjeFromStandalonePrevoznica(prevoznica: Prevoznica) {
        val id = prevoznica.id ?: return
        val merenje = merenjeRepository.findByPrevoznicaIdAndOtpremnicaIsNull(id) ?: return
        merenjeRepository.delete(merenje)
        log.info("Deleted standalone merenje {} linked to prevoznica {}", merenje.id, prevoznica.brojPrevoznice)
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
        merenje.merniListBr = otpremnica.merniListBr
        merenje.kupac = otpremnica.kupac
        val normalizedRegistracija = com.rkl.backend.util.TextUtils.normalizeRegistration(otpremnica.registracija)
        // Raw vrednosti — koriste se za revert kad se izbriše labela
        merenje.porucilacRaw = otpremnica.porucilac
        if (otpremnica.primalac.isNotBlank()) merenje.primalacRaw = otpremnica.primalac
        merenje.prevoznikRaw = otpremnica.prevoznik
        merenje.registracijaRaw = normalizedRegistracija
        merenje.robaRaw = otpremnica.nazivRobe
        merenje.vozacRaw = otpremnica.vozacIme
        merenje.porucilac = labelService.resolveValue("porucilac", otpremnica.porucilac)
        if (otpremnica.primalac.isNotBlank()) {
            merenje.primalac = labelService.resolveValue("primalac", otpremnica.primalac)
        }
        merenje.prevoznik = labelService.resolveValue("prevoznik", otpremnica.prevoznik)
        merenje.registracija = labelService.resolveValue("registracija", normalizedRegistracija)
        merenje.roba = labelService.resolveValue("roba", otpremnica.nazivRobe)
        merenje.bruto = otpremnica.bruto
        merenje.tara = otpremnica.tara
        merenje.neto = otpremnica.neto
        merenje.vozac = labelService.resolveValue("vozac", otpremnica.vozacIme)
        merenje.otpremnica = otpremnica
        merenje.izvor = IZVOR_OTPREMNICA

        // Prevoznica data (if linked) — overwrites otpremnica vrednosti za polja koja samo prevoznica nosi
        if (prevoznica != null) {
            merenje.posiljalacRaw = prevoznica.posiljalac
            merenje.primalacRaw = prevoznica.primalac
            merenje.mestoRaw = prevoznica.mestoUtovara
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
