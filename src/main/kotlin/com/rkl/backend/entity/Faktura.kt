package com.rkl.backend.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

enum class FakturaStatus {
    KREIRANA,
    POSLATA,
    PLACENA,
    STORNIRANA
}

@Entity
@Table(
    name = "fakture",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_fakture_broj", columnNames = ["broj_fakture"])
    ],
    indexes = [
        Index(name = "ix_fakture_porucilac", columnList = "porucilac"),
        Index(name = "ix_fakture_status", columnList = "status")
    ]
)
class Faktura(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "broj_fakture", nullable = false, unique = true)
    var brojFakture: String = "",

    @Column(nullable = false)
    var porucilac: String = "",

    @Column(name = "datum_od", nullable = false)
    var datumOd: LocalDate = LocalDate.now(),

    @Column(name = "datum_do", nullable = false)
    var datumDo: LocalDate = LocalDate.now(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: FakturaStatus = FakturaStatus.KREIRANA,

    @Column(name = "datum_slanja")
    var datumSlanja: LocalDate? = null,

    var napomena: String? = null,

    @Column(name = "additional_emails", columnDefinition = "TEXT")
    var additionalEmails: String? = null,

    @Column(name = "created_by")
    var createdBy: String? = null,

    @Column(name = "measurement_count")
    var measurementCount: Int = 0,

    @Column(name = "created_at")
    var createdAt: LocalDateTime? = null,

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
) {

    @PrePersist
    fun onPrePersist() {
        createdAt = LocalDateTime.now()
    }

    @PreUpdate
    fun onPreUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
