package com.rkl.backend.service

import com.rkl.backend.dto.prevoznica.*
import com.rkl.backend.entity.Prevoznica
import com.rkl.backend.entity.PrevoznicaStatus
import com.rkl.backend.repository.PrevoznicaRepository
import com.rkl.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class PrevoznicaService(
    private val prevoznicaRepository: PrevoznicaRepository,
    private val userRepository: UserRepository
) {

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
            vozacUser = vozacUser,
            vozacIme = vozacUser.driverName ?: vozacUser.email,
            potpisVozaca = vozacUser.signature,
            status = PrevoznicaStatus.KREIRANA,
            createdBy = createdBy
        )

        val saved = prevoznicaRepository.save(prevoznica)
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
        return PrevoznicaDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun deletePrevoznica(id: Long): PrevoznicaDeleteResponse {
        val prevoznica = prevoznicaRepository.findById(id).orElseThrow {
            NoSuchElementException("Prevoznica sa id $id nije pronađena")
        }
        prevoznicaRepository.delete(prevoznica)
        return PrevoznicaDeleteResponse(id = id)
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
        potpisVozaca = !potpisVozaca.isNullOrBlank(),
        potpisPrimaoca = !potpisPrimaoca.isNullOrBlank(),
        status = status.name,
        createdBy = createdBy,
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )
}
