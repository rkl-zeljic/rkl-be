package com.rkl.backend.service

import com.rkl.backend.dao.user.UserDao
import com.rkl.backend.dto.signaturerequest.CreateSignatureRequestDTO
import com.rkl.backend.dto.signaturerequest.SignatureRequestResponseDTO
import com.rkl.backend.entity.SignatureRequest
import com.rkl.backend.entity.SignatureRequestStatus
import com.rkl.backend.repository.OtpremnicaRepository
import com.rkl.backend.repository.PrevoznicaRepository
import com.rkl.backend.repository.SignatureRequestRepository
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class SignatureRequestService(
    private val signatureRequestRepository: SignatureRequestRepository,
    private val userDao: UserDao,
    private val otpremnicaRepository: OtpremnicaRepository,
    private val prevoznicaRepository: PrevoznicaRepository,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Transactional
    fun createRequest(email: String, dto: CreateSignatureRequestDTO): SignatureRequestResponseDTO {
        val user = userDao.findByName(email)
        require(user.signature != null) { "Nemate postojeći potpis. Prvo kreirajte potpis." }

        val existing = signatureRequestRepository.findByUserIdAndStatus(user.id!!, SignatureRequestStatus.NA_CEKANJU)
        check(existing == null) { "Već imate zahtev na čekanju." }

        val request = SignatureRequest(
            user = user,
            newSignature = dto.newSignature,
        )
        val saved = signatureRequestRepository.save(request)
        log.info("Signature change request created: id=${saved.id} for user=${user.id}")
        return mapToDTO(saved)
    }

    @Transactional(readOnly = true)
    fun getMyPendingRequest(email: String): SignatureRequestResponseDTO? {
        val user = userDao.findByName(email)
        val pending = signatureRequestRepository.findByUserIdAndStatus(user.id!!, SignatureRequestStatus.NA_CEKANJU)
        return pending?.let { mapToDTO(it) }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN')")
    fun getPendingRequests(): List<SignatureRequestResponseDTO> {
        return signatureRequestRepository
            .findByStatusOrderByCreatedAtDesc(SignatureRequestStatus.NA_CEKANJU)
            .map { mapToDTO(it) }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN')")
    fun getAllRequests(): List<SignatureRequestResponseDTO> {
        return signatureRequestRepository
            .findAllByOrderByCreatedAtDesc()
            .map { mapToDTO(it) }
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN')")
    fun approveRequest(id: Long, adminEmail: String): SignatureRequestResponseDTO {
        val request = signatureRequestRepository.findById(id)
            .orElseThrow { NoSuchElementException("Zahtev nije pronađen") }
        check(request.status == SignatureRequestStatus.NA_CEKANJU) { "Zahtev je već obrađen." }

        val admin = userDao.findByName(adminEmail)
        val user = request.user

        // Update user signature
        user.signature = request.newSignature
        userDao.update(user)

        // Backfill documents
        val otpCount = otpremnicaRepository.backfillDriverSignature(user.id!!, request.newSignature)
        val prevCount = prevoznicaRepository.backfillDriverSignature(user.id!!, request.newSignature)
        if (otpCount > 0 || prevCount > 0) {
            log.info("Backfilled driver signature for user ${user.id}: $otpCount otpremnice, $prevCount prevoznice")
        }

        // Update request status
        request.status = SignatureRequestStatus.ODOBRENO
        request.resolvedAt = Instant.now()
        request.resolvedBy = admin
        val saved = signatureRequestRepository.save(request)

        log.info("Signature request ${saved.id} approved by admin ${admin.id} for user ${user.id}")
        return mapToDTO(saved)
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN')")
    fun rejectRequest(id: Long, adminEmail: String): SignatureRequestResponseDTO {
        val request = signatureRequestRepository.findById(id)
            .orElseThrow { NoSuchElementException("Zahtev nije pronađen") }
        check(request.status == SignatureRequestStatus.NA_CEKANJU) { "Zahtev je već obrađen." }

        val admin = userDao.findByName(adminEmail)

        request.status = SignatureRequestStatus.ODBIJENO
        request.resolvedAt = Instant.now()
        request.resolvedBy = admin
        val saved = signatureRequestRepository.save(request)

        log.info("Signature request ${saved.id} rejected by admin ${admin.id}")
        return mapToDTO(saved)
    }

    private fun mapToDTO(entity: SignatureRequest): SignatureRequestResponseDTO {
        return SignatureRequestResponseDTO(
            id = entity.id!!,
            userId = entity.user.id!!,
            driverName = entity.user.driverName,
            driverEmail = entity.user.email,
            currentSignature = entity.user.signature,
            newSignature = entity.newSignature,
            status = entity.status,
            createdAt = entity.createdAt,
            resolvedAt = entity.resolvedAt,
            resolvedByEmail = entity.resolvedBy?.email,
        )
    }
}
