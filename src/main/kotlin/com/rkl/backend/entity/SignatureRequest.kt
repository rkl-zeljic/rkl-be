package com.rkl.backend.entity

import jakarta.persistence.*
import java.time.Instant

enum class SignatureRequestStatus {
    NA_CEKANJU,
    ODOBRENO,
    ODBIJENO
}

@Entity
@Table(
    name = "signature_requests",
    indexes = [
        Index(name = "ix_signature_requests_user_id", columnList = "user_id"),
        Index(name = "ix_signature_requests_status", columnList = "status")
    ]
)
class SignatureRequest(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: RklUser,

    @Column(name = "new_signature", columnDefinition = "TEXT", nullable = false)
    var newSignature: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SignatureRequestStatus = SignatureRequestStatus.NA_CEKANJU,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    var resolvedBy: RklUser? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now()
        }
    }
}
