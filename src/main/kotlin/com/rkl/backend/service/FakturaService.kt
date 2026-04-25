package com.rkl.backend.service

import com.rkl.backend.config.RklConfig
import com.rkl.backend.dto.common.PaginationMeta
import com.rkl.backend.dto.faktura.*
import com.rkl.backend.entity.Faktura
import com.rkl.backend.entity.FakturaStatus
import com.rkl.backend.repository.FakturaRepository
import com.rkl.backend.repository.MerenjeRepository
import com.rkl.backend.repository.MerenjeSpecification
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class FakturaService(
    private val fakturaRepository: FakturaRepository,
    private val merenjeRepository: MerenjeRepository,
    private val emailService: EmailService,
    private val fakturaExcelService: FakturaExcelService,
    private val fakturaPdfService: FakturaPdfService,
    private val mailConfig: RklConfig.Mail
) {

    companion object {
        val SORT_WHITELIST = setOf(
            "id", "brojFakture", "porucilac", "datumOd", "datumDo",
            "status", "datumSlanja", "measurementCount", "createdAt"
        )
    }

    @Transactional(readOnly = true)
    fun listFakture(page: Int, pageSize: Int, sortBy: String, sortOrder: String): FaktureResponse {
        val safeSortBy = if (sortBy in SORT_WHITELIST) sortBy else "createdAt"
        val direction = if (sortOrder.equals("ASC", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(page - 1, pageSize, Sort.by(direction, safeSortBy))

        val pageResult = fakturaRepository.findAll(pageable)

        val dtos = pageResult.content.map { faktura ->
            val fields = getDistinctMeasurementFields(faktura.porucilac, faktura.datumOd, faktura.datumDo)
            faktura.toDto(fields)
        }

        return FaktureResponse(
            data = dtos,
            pagination = PaginationMeta(
                totalCount = pageResult.totalElements,
                page = page,
                pageSize = pageSize,
                totalPages = pageResult.totalPages
            )
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
        return FakturaDetailResponse(data = saved.toDto())
    }

    fun sendFakturaEmail(id: Long, request: SendFakturaEmailRequest): SendFakturaEmailResponse {
        val faktura = fakturaRepository.findById(id).orElseThrow {
            NoSuchElementException("Faktura sa id $id nije pronađena")
        }

        val (fileBytes, fileName, mimeType) = when (request.format.lowercase()) {
            "excel" -> Triple(
                fakturaExcelService.generateExcel(faktura),
                "${faktura.brojFakture}.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
            else -> Triple(
                fakturaPdfService.generatePdf(faktura),
                "${faktura.brojFakture}.pdf",
                "application/pdf"
            )
        }

        val allEmails = (request.emails + mailConfig.adminEmail).distinct()

        emailService.sendFakturaWithAttachment(
            toEmails = allEmails,
            brojFakture = faktura.brojFakture,
            porucilac = faktura.porucilac,
            fileBytes = fileBytes,
            fileName = fileName,
            mimeType = mimeType
        )

        return SendFakturaEmailResponse(message = "Email poslat na ${allEmails.joinToString(", ")}")
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

    private data class MeasurementFields(
        val robaList: List<String>,
        val prevoznikList: List<String>,
        val primalacList: List<String>,
        val posiljalacList: List<String>
    )

    private fun getDistinctMeasurementFields(porucilac: String, datumOd: LocalDate, datumDo: LocalDate): MeasurementFields {
        return MeasurementFields(
            robaList = merenjeRepository.findDistinctRobaByFaktura(porucilac, datumOd, datumDo),
            prevoznikList = merenjeRepository.findDistinctPrevoznikByFaktura(porucilac, datumOd, datumDo),
            primalacList = merenjeRepository.findDistinctPrimalacByFaktura(porucilac, datumOd, datumDo),
            posiljalacList = merenjeRepository.findDistinctPosiljalacByFaktura(porucilac, datumOd, datumDo)
        )
    }

    private fun Faktura.toDto(fields: MeasurementFields? = null): FakturaDto = FakturaDto(
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
        robaList = fields?.robaList ?: emptyList(),
        prevoznikList = fields?.prevoznikList ?: emptyList(),
        primalacList = fields?.primalacList ?: emptyList(),
        posiljalacList = fields?.posiljalacList ?: emptyList(),
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )
}
