package com.rkl.backend.service

import com.rkl.backend.dto.primalac.*
import com.rkl.backend.entity.Primalac
import com.rkl.backend.repository.LabelRepository
import com.rkl.backend.repository.PrimalacRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PrimalacService(
    private val primalacRepository: PrimalacRepository,
    private val labelRepository: LabelRepository
) {

    @Transactional(readOnly = true)
    fun getAvailableNames(): AvailableNamesResponse {
        val labelNames = labelRepository.findByColumnName("primalac")
            .map { it.canonicalValue }
        val existingNames = primalacRepository.findAllByOrderByNazivAsc()
            .map { it.naziv }
            .toSet()
        val available = labelNames.filter { it !in existingNames }.sorted()
        return AvailableNamesResponse(data = available)
    }

    @Transactional(readOnly = true)
    fun listPrimaoci(search: String?): PrimaociResponse {
        val primaoci = if (!search.isNullOrBlank()) {
            primalacRepository.findByNazivContainingIgnoreCase(search)
        } else {
            primalacRepository.findAllByOrderByNazivAsc()
        }
        return PrimaociResponse(
            data = primaoci.map { it.toDto() },
            totalCount = primaoci.size
        )
    }

    @Transactional(readOnly = true)
    fun getPrimalac(id: Long): PrimalacDetailResponse {
        val primalac = primalacRepository.findById(id).orElseThrow {
            NoSuchElementException("Primalac sa id $id nije pronađen")
        }
        return PrimalacDetailResponse(data = primalac.toDto())
    }

    @Transactional(readOnly = true)
    fun findByNaziv(naziv: String): Primalac? {
        return primalacRepository.findByNaziv(naziv)
    }

    @Transactional
    fun createPrimalac(request: CreatePrimalacRequest): PrimalacDetailResponse {
        val label = labelRepository.findByColumnNameAndCanonicalValue("primalac", request.naziv)
        if (label == null) {
            throw IllegalArgumentException(
                "Ne postoji labela za primalac sa kanoničkom vrednošću '${request.naziv}'. " +
                "Prvo kreirajte labelu za kolonu 'primalac' sa ovim nazivom."
            )
        }

        val existing = primalacRepository.findByNaziv(request.naziv)
        if (existing != null) {
            throw IllegalArgumentException("Primalac sa nazivom '${request.naziv}' već postoji")
        }

        val primalac = Primalac(
            naziv = request.naziv,
            email = request.email,
            telefon = request.telefon,
            adresa = request.adresa,
            napomena = request.napomena
        )

        val saved = primalacRepository.save(primalac)
        return PrimalacDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun updatePrimalac(id: Long, request: UpdatePrimalacRequest): PrimalacDetailResponse {
        val primalac = primalacRepository.findById(id).orElseThrow {
            NoSuchElementException("Primalac sa id $id nije pronađen")
        }

        request.naziv?.let {
            val label = labelRepository.findByColumnNameAndCanonicalValue("primalac", it)
            if (label == null) {
                throw IllegalArgumentException(
                    "Ne postoji labela za primalac sa kanoničkom vrednošću '$it'. " +
                    "Prvo kreirajte labelu za kolonu 'primalac' sa ovim nazivom."
                )
            }
            val existing = primalacRepository.findByNaziv(it)
            if (existing != null && existing.id != id) {
                throw IllegalArgumentException("Primalac sa nazivom '$it' već postoji")
            }
            primalac.naziv = it
        }
        request.email?.let { primalac.email = it }
        request.telefon?.let { primalac.telefon = it }
        request.adresa?.let { primalac.adresa = it }
        request.napomena?.let { primalac.napomena = it }

        val saved = primalacRepository.save(primalac)
        return PrimalacDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun deletePrimalac(id: Long): PrimalacDeleteResponse {
        val primalac = primalacRepository.findById(id).orElseThrow {
            NoSuchElementException("Primalac sa id $id nije pronađen")
        }
        primalacRepository.delete(primalac)
        return PrimalacDeleteResponse(id = id)
    }

    private fun Primalac.toDto(): PrimalacDto = PrimalacDto(
        id = id,
        naziv = naziv,
        email = email,
        telefon = telefon,
        adresa = adresa,
        napomena = napomena,
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )
}
