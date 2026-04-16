package com.rkl.backend.service

import com.rkl.backend.dto.prevoznik.*
import com.rkl.backend.entity.Prevoznik
import com.rkl.backend.repository.LabelRepository
import com.rkl.backend.repository.PrevoznikRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PrevoznikService(
    private val prevoznikRepository: PrevoznikRepository,
    private val labelRepository: LabelRepository
) {

    @Transactional(readOnly = true)
    fun getAvailableNames(): PrevoznikAvailableNamesResponse {
        val labelNames = labelRepository.findByColumnName("prevoznik")
            .map { it.canonicalValue }
        val existingNames = prevoznikRepository.findAllByOrderByNazivAsc()
            .map { it.naziv }
            .toSet()
        val available = labelNames.filter { it !in existingNames }.sorted()
        return PrevoznikAvailableNamesResponse(data = available)
    }

    @Transactional(readOnly = true)
    fun listPrevoznici(search: String?): PrevozniciResponse {
        val prevoznici = if (!search.isNullOrBlank()) {
            prevoznikRepository.findByNazivContainingIgnoreCase(search)
        } else {
            prevoznikRepository.findAllByOrderByNazivAsc()
        }
        return PrevozniciResponse(
            data = prevoznici.map { it.toDto() },
            totalCount = prevoznici.size
        )
    }

    @Transactional(readOnly = true)
    fun getPrevoznik(id: Long): PrevoznikDetailResponse {
        val prevoznik = prevoznikRepository.findById(id).orElseThrow {
            NoSuchElementException("Prevoznik sa id $id nije pronađen")
        }
        return PrevoznikDetailResponse(data = prevoznik.toDto())
    }

    @Transactional(readOnly = true)
    fun findByNaziv(naziv: String): Prevoznik? {
        return prevoznikRepository.findByNaziv(naziv)
    }

    @Transactional
    fun createPrevoznik(request: CreatePrevoznikRequest): PrevoznikDetailResponse {
        val label = labelRepository.findByColumnNameAndCanonicalValue("prevoznik", request.naziv)
        if (label == null) {
            throw IllegalArgumentException(
                "Ne postoji labela za prevoznik sa kanoničkom vrednošću '${request.naziv}'. " +
                "Prvo kreirajte labelu za kolonu 'prevoznik' sa ovim nazivom."
            )
        }

        val existing = prevoznikRepository.findByNaziv(request.naziv)
        if (existing != null) {
            throw IllegalArgumentException("Prevoznik sa nazivom '${request.naziv}' već postoji")
        }

        val prevoznik = Prevoznik(
            naziv = request.naziv,
            email = request.email,
            telefon = request.telefon,
            adresa = request.adresa,
            napomena = request.napomena
        )

        val saved = prevoznikRepository.save(prevoznik)
        return PrevoznikDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun updatePrevoznik(id: Long, request: UpdatePrevoznikRequest): PrevoznikDetailResponse {
        val prevoznik = prevoznikRepository.findById(id).orElseThrow {
            NoSuchElementException("Prevoznik sa id $id nije pronađen")
        }

        request.naziv?.let {
            val label = labelRepository.findByColumnNameAndCanonicalValue("prevoznik", it)
            if (label == null) {
                throw IllegalArgumentException(
                    "Ne postoji labela za prevoznik sa kanoničkom vrednošću '$it'. " +
                    "Prvo kreirajte labelu za kolonu 'prevoznik' sa ovim nazivom."
                )
            }
            val existing = prevoznikRepository.findByNaziv(it)
            if (existing != null && existing.id != id) {
                throw IllegalArgumentException("Prevoznik sa nazivom '$it' već postoji")
            }
            prevoznik.naziv = it
        }
        request.email?.let { prevoznik.email = it }
        request.telefon?.let { prevoznik.telefon = it }
        request.adresa?.let { prevoznik.adresa = it }
        request.napomena?.let { prevoznik.napomena = it }

        val saved = prevoznikRepository.save(prevoznik)
        return PrevoznikDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun deletePrevoznik(id: Long): PrevoznikDeleteResponse {
        val prevoznik = prevoznikRepository.findById(id).orElseThrow {
            NoSuchElementException("Prevoznik sa id $id nije pronađen")
        }
        prevoznikRepository.delete(prevoznik)
        return PrevoznikDeleteResponse(id = id)
    }

    private fun Prevoznik.toDto(): PrevoznikDto = PrevoznikDto(
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
