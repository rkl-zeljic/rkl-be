package com.rkl.backend.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

enum class PrevoznicaStatus {
    KREIRANA,
    POTPISANA,
    ZAVRSENA
}

@Entity
@Table(
    name = "prevoznice",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_prevoznice_broj", columnNames = ["broj_prevoznice"])
    ],
    indexes = [
        Index(name = "ix_prevoznice_vozac_user", columnList = "vozac_user_id"),
        Index(name = "ix_prevoznice_datum", columnList = "datum")
    ]
)
class Prevoznica(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "broj_prevoznice", nullable = false, unique = true)
    var brojPrevoznice: String = "",

    @Column(nullable = false)
    var datum: LocalDate = LocalDate.now(),

    // Left column fields
    @Column(nullable = false)
    var posiljalac: String = "",

    @Column(name = "platilac_prevoza", nullable = false)
    var platilacPrevoza: String = "",

    @Column(nullable = false)
    var primalac: String = "",

    @Column(nullable = false)
    var prevozilac: String = "",

    @Column(name = "prateca_dokumenta")
    var pratecaDokumenta: String? = null,

    // Right column fields
    @Column(name = "mesto_utovara", nullable = false)
    var mestoUtovara: String = "",

    @Column(name = "mesto_istovara", nullable = false)
    var mestoIstovara: String = "",

    @Column(name = "registracija", nullable = false)
    var registracija: String = "",

    @Column(name = "datum_utovara", nullable = false)
    var datumUtovara: LocalDate = LocalDate.now(),

    @Column(name = "datum_istovara", nullable = false)
    var datumIstovara: LocalDate = LocalDate.now(),

    // Goods table
    @Column(name = "vrsta_robe", nullable = false)
    var vrstaRobe: String = "",

    var km: Double? = null,

    @Column(name = "jed_mere")
    var jedMere: String = "kg",

    @Column(name = "stvarna_tezina")
    var stvarnaTezina: Double? = null,

    // Otpremnica link
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "otpremnica_id")
    var otpremnica: Otpremnica? = null,

    // Driver link
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vozac_user_id")
    var vozacUser: RklUser? = null,

    @Column(name = "vozac_ime")
    var vozacIme: String = "",

    // Signatures
    @Column(name = "potpis_vozaca", columnDefinition = "TEXT")
    var potpisVozaca: String? = null,

    @Column(name = "potpis_primaoca", columnDefinition = "TEXT")
    var potpisPrimaoca: String? = null,

    @Column(name = "additional_emails", columnDefinition = "TEXT")
    var additionalEmails: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PrevoznicaStatus = PrevoznicaStatus.KREIRANA,

    @Column(name = "created_by")
    var createdBy: String? = null,

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
