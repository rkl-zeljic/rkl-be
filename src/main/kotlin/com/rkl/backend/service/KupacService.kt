package com.rkl.backend.service

import com.rkl.backend.dto.kupac.*
import com.rkl.backend.entity.Kupac
import com.rkl.backend.repository.KupacRepository
import com.rkl.backend.repository.LabelRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class KupacService(
    private val kupacRepository: KupacRepository,
    private val labelRepository: LabelRepository
) {

    @Transactional(readOnly = true)
    fun getAvailableNames(): KupacAvailableNamesResponse {
        val labelNames = labelRepository.findByColumnName("porucilac")
            .map { it.canonicalValue }
        val existingNames = kupacRepository.findAllByOrderByNazivAsc()
            .map { it.naziv }
            .toSet()
        val available = labelNames.filter { it !in existingNames }.sorted()
        return KupacAvailableNamesResponse(data = available)
    }

    @Transactional(readOnly = true)
    fun listKupci(search: String?): KupciResponse {
        val kupci = if (!search.isNullOrBlank()) {
            kupacRepository.findByNazivContainingIgnoreCase(search)
        } else {
            kupacRepository.findAllByOrderByNazivAsc()
        }
        return KupciResponse(
            data = kupci.map { it.toDto() },
            totalCount = kupci.size
        )
    }

    @Transactional(readOnly = true)
    fun getKupac(id: Long): KupacDetailResponse {
        val kupac = kupacRepository.findById(id).orElseThrow {
            NoSuchElementException("Kupac sa id $id nije pronađen")
        }
        return KupacDetailResponse(data = kupac.toDto())
    }

    @Transactional(readOnly = true)
    fun findByNaziv(naziv: String): Kupac? {
        return kupacRepository.findByNaziv(naziv)
    }

    @Transactional
    fun createKupac(request: CreateKupacRequest): KupacDetailResponse {
        val label = labelRepository.findByColumnNameAndCanonicalValue("porucilac", request.naziv)
        if (label == null) {
            throw IllegalArgumentException(
                "Ne postoji labela za porucilac sa kanoničkom vrednošću '${request.naziv}'. " +
                "Prvo kreirajte labelu za kolonu 'porucilac' sa ovim nazivom."
            )
        }

        val existing = kupacRepository.findByNaziv(request.naziv)
        if (existing != null) {
            throw IllegalArgumentException("Kupac sa nazivom '${request.naziv}' već postoji")
        }

        val kupac = Kupac(
            naziv = request.naziv,
            email = request.email,
            telefon = request.telefon,
            adresa = request.adresa,
            napomena = request.napomena
        )

        val saved = kupacRepository.save(kupac)
        return KupacDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun updateKupac(id: Long, request: UpdateKupacRequest): KupacDetailResponse {
        val kupac = kupacRepository.findById(id).orElseThrow {
            NoSuchElementException("Kupac sa id $id nije pronađen")
        }

        request.naziv?.let {
            val label = labelRepository.findByColumnNameAndCanonicalValue("porucilac", it)
            if (label == null) {
                throw IllegalArgumentException(
                    "Ne postoji labela za porucilac sa kanoničkom vrednošću '$it'. " +
                    "Prvo kreirajte labelu za kolonu 'porucilac' sa ovim nazivom."
                )
            }
            val existing = kupacRepository.findByNaziv(it)
            if (existing != null && existing.id != id) {
                throw IllegalArgumentException("Kupac sa nazivom '$it' već postoji")
            }
            kupac.naziv = it
        }
        request.email?.let { kupac.email = it }
        request.telefon?.let { kupac.telefon = it }
        request.adresa?.let { kupac.adresa = it }
        request.napomena?.let { kupac.napomena = it }

        val saved = kupacRepository.save(kupac)
        return KupacDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun deleteKupac(id: Long): KupacDeleteResponse {
        val kupac = kupacRepository.findById(id).orElseThrow {
            NoSuchElementException("Kupac sa id $id nije pronađen")
        }
        kupacRepository.delete(kupac)
        return KupacDeleteResponse(id = id)
    }

    private fun Kupac.toDto(): KupacDto = KupacDto(
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
