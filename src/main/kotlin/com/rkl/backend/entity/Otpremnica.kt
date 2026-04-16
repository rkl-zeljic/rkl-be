package com.rkl.backend.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

enum class OtpremnicaStatus {
    KREIRANA,
    POTPISANA
}

@Entity
@Table(
    name = "otpremnice",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_otpremnice_broj", columnNames = ["broj_otpremnice"])
    ],
    indexes = [
        Index(name = "ix_otpremnice_vozac_user", columnList = "vozac_user_id"),
        Index(name = "ix_otpremnice_datum", columnList = "datum")
    ]
)
class Otpremnica(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "broj_otpremnice", nullable = false, unique = true)
    var brojOtpremnice: String = "",

    @Column(nullable = false)
    var datum: LocalDate = LocalDate.now(),

    @Column(nullable = false)
    var porucilac: String = "",

    @Column(nullable = false)
    var prevoznik: String = "",

    @Column(nullable = false)
    var registracija: String = "",

    @Column(name = "naziv_robe", nullable = false)
    var nazivRobe: String = "",

    @Column(name = "jedinica_mere", nullable = false)
    var jedinicaMere: String = "Kg",

    @Column(nullable = false)
    var bruto: Double = 0.0,

    @Column(nullable = false)
    var tara: Double = 0.0,

    @Column(nullable = false)
    var neto: Double = 0.0,

    // Driver link
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vozac_user_id")
    var vozacUser: RklUser? = null,

    @Column(name = "vozac_ime")
    var vozacIme: String = "",

    // Signatures
    @Column(name = "potpis_vozaca", columnDefinition = "TEXT")
    var potpisVozaca: String? = null,

    @Column(name = "potpis_izdavaoca", columnDefinition = "TEXT")
    var potpisIzdavaoca: String? = null,

    @Column(name = "potpis_primaoca", columnDefinition = "TEXT")
    var potpisPrimaoca: String? = null,

    @Column(name = "bez_merenja", nullable = false)
    var bezMerenja: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merenje_generated_file_id")
    var merenjeGeneratedFile: ImportedFile? = null,

    @Column(name = "additional_emails", columnDefinition = "TEXT")
    var additionalEmails: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OtpremnicaStatus = OtpremnicaStatus.KREIRANA,

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
