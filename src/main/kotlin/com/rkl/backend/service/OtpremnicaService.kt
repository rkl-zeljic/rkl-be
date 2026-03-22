package com.rkl.backend.service

import com.rkl.backend.dto.otpremnica.*
import com.rkl.backend.entity.Otpremnica
import com.rkl.backend.entity.OtpremnicaStatus
import com.rkl.backend.repository.OtpremnicaRepository
import com.rkl.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class OtpremnicaService(
    private val otpremnicaRepository: OtpremnicaRepository,
    private val userRepository: UserRepository
) {

    companion object {
        private val NUMBER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
        // Default RKL signature - can be replaced with actual signature image
        private const val DEFAULT_IZDAVALAC_SIGNATURE = "RKL"
    }

    @Transactional(readOnly = true)
    fun listOtpremnice(): OtpremniceResponse {
        val otpremnice = otpremnicaRepository.findAllByOrderByCreatedAtDesc()
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

        val brojOtpremnice = generateBrojOtpremnice(request.datum)

        val otpremnica = Otpremnica(
            brojOtpremnice = brojOtpremnice,
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
            vozacIme = vozacUser.driverName ?: vozacUser.email,
            potpisVozaca = vozacUser.signature,
            potpisIzdavaoca = DEFAULT_IZDAVALAC_SIGNATURE,
            status = OtpremnicaStatus.KREIRANA,
            createdBy = createdBy
        )

        val saved = otpremnicaRepository.save(otpremnica)
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

    private fun generateBrojOtpremnice(datum: LocalDate): String {
        val maxId = otpremnicaRepository.findMaxId()
        val nextNumber = maxId + 1
        return "%06d".format(nextNumber)
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
        status = status.name,
        createdBy = createdBy,
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )
}
