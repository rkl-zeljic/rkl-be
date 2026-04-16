package com.rkl.backend.repository

import com.rkl.backend.entity.SignatureRequest
import com.rkl.backend.entity.SignatureRequestStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SignatureRequestRepository : JpaRepository<SignatureRequest, Long> {
    fun findByStatusOrderByCreatedAtDesc(status: SignatureRequestStatus): List<SignatureRequest>
    fun findByUserIdAndStatus(userId: Long, status: SignatureRequestStatus): SignatureRequest?
    fun findAllByOrderByCreatedAtDesc(): List<SignatureRequest>
}
