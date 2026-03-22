package com.rkl.backend.service

import com.rkl.backend.dto.faktura.*
import com.rkl.backend.entity.Faktura
import com.rkl.backend.entity.FakturaStatus
import com.rkl.backend.repository.FakturaRepository
import com.rkl.backend.repository.MerenjeRepository
import com.rkl.backend.repository.MerenjeSpecification
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class FakturaService(
    private val fakturaRepository: FakturaRepository,
    private val merenjeRepository: MerenjeRepository,
    private val primalacService: PrimalacService,
    private val emailService: EmailService
) {

    @Transactional(readOnly = true)
    fun listFakture(): FaktureResponse {
        val fakture = fakturaRepository.findAllByOrderByCreatedAtDesc()
        return FaktureResponse(
            data = fakture.map { it.toDto() }
        )
    }

    @Transactional(readOnly = true)
    fun getFaktura(id: Long): FakturaDetailResponse {
        val faktura = fakturaRepository.findById(id).orElseThrow {
            NoSuchElementException("Faktura sa id $id nije pronađena")
        }
        return FakturaDetailResponse(data = faktura.toDto())
    }

    @Transactional
    fun createFaktura(request: CreateFakturaRequest, createdBy: String?): FakturaDetailResponse {
        if (request.datumOd.isAfter(request.datumDo)) {
            throw IllegalArgumentException("Datum od ne može biti posle datum do")
        }

        val measurementCount = countMeasurements(request.porucilac, request.datumOd, request.datumDo)

        val brojFakture = generateBrojFakture()

        val faktura = Faktura(
            brojFakture = brojFakture,
            porucilac = request.porucilac,
            datumOd = request.datumOd,
            datumDo = request.datumDo,
            napomena = request.napomena,
            createdBy = createdBy,
            measurementCount = measurementCount.toInt()
        )

        val saved = fakturaRepository.save(faktura)
        return FakturaDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun updateStatus(id: Long, request: UpdateFakturaStatusRequest): FakturaDetailResponse {
        val faktura = fakturaRepository.findById(id).orElseThrow {
            NoSuchElementException("Faktura sa id $id nije pronađena")
        }

        val newStatus = try {
            FakturaStatus.valueOf(request.status)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Nepoznat status: ${request.status}. Dozvoljeni: ${FakturaStatus.entries.map { it.name }}")
        }

        faktura.status = newStatus

        if (newStatus == FakturaStatus.POSLATA && faktura.datumSlanja == null) {
            faktura.datumSlanja = LocalDate.now()
        }

        val saved = fakturaRepository.save(faktura)

        // Send email notification to primalac
        val primalac = primalacService.findByNaziv(faktura.porucilac)
        if (primalac?.email != null) {
            emailService.sendFakturaStatusEmail(
                toEmail = primalac.email!!,
                primalacNaziv = primalac.naziv,
                brojFakture = faktura.brojFakture,
                newStatus = newStatus.name
            )
        }

        return FakturaDetailResponse(data = saved.toDto())
    }

    @Transactional
    fun deleteFaktura(id: Long): FakturaDeleteResponse {
        val faktura = fakturaRepository.findById(id).orElseThrow {
            NoSuchElementException("Faktura sa id $id nije pronađena")
        }
        fakturaRepository.delete(faktura)
        return FakturaDeleteResponse(id = id)
    }

    private fun countMeasurements(porucilac: String, datumOd: LocalDate, datumDo: LocalDate): Long {
        var spec = Specification.where(MerenjeSpecification.textFilter("porucilac", porucilac))
        spec = spec.and(MerenjeSpecification.datumOd(datumOd))
        spec = spec.and(MerenjeSpecification.datumDo(datumDo))
        return merenjeRepository.count(spec)
    }

    private fun generateBrojFakture(): String {
        val maxId = fakturaRepository.findMaxId()
        val nextNumber = maxId + 1
        return "F-%04d".format(nextNumber)
    }

    private fun Faktura.toDto(): FakturaDto = FakturaDto(
        id = id,
        brojFakture = brojFakture,
        porucilac = porucilac,
        datumOd = datumOd.toString(),
        datumDo = datumDo.toString(),
        status = status.name,
        datumSlanja = datumSlanja?.toString(),
        napomena = napomena,
        createdBy = createdBy,
        measurementCount = measurementCount,
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )
}
