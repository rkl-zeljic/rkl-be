package com.rkl.backend.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "merenja",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_merenja_porucilac_merni", columnNames = ["porucilac_id", "merni_list_br"])
    ],
    indexes = [
        Index(name = "ix_merenja_datum", columnList = "datum_izvestaja"),
        Index(name = "ix_merenja_roba", columnList = "roba"),
        Index(name = "ix_merenja_registracija", columnList = "registracija"),
        Index(name = "ix_merenja_vozac_user", columnList = "vozac_user_id"),
        Index(name = "ix_merenja_porucilac_id", columnList = "porucilac_id")
    ]
)
class Merenje(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_file_id")
    var importedFile: ImportedFile? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "otpremnica_id")
    var otpremnica: Otpremnica? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prevoznica_id")
    var prevoznica: Prevoznica? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "porucilac_id")
    var kupac: Kupac? = null,

    @Column(name = "izvor")
    var izvor: String? = null,

    @Column(name = "datum_izvestaja")
    var datumIzvestaja: LocalDate? = null,

    @Column(name = "merni_list_br")
    var merniListBr: Int? = null,

    var posiljalac: String? = null,

    var porucilac: String? = null,

    var primalac: String? = null,

    var roba: String? = null,

    var bruto: Double? = null,

    var tara: Double? = null,

    var neto: Double? = null,

    var prevoznik: String? = null,

    var registracija: String? = null,

    var vozac: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vozac_user_id")
    var vozacUser: RklUser? = null,

    var mesto: String? = null,

    @Column(name = "posiljalac_raw")
    var posiljalacRaw: String? = null,

    @Column(name = "porucilac_raw")
    var porucilacRaw: String? = null,

    @Column(name = "primalac_raw")
    var primalacRaw: String? = null,

    @Column(name = "roba_raw")
    var robaRaw: String? = null,

    @Column(name = "prevoznik_raw")
    var prevoznikRaw: String? = null,

    @Column(name = "registracija_raw")
    var registracijaRaw: String? = null,

    @Column(name = "vozac_raw")
    var vozacRaw: String? = null,

    @Column(name = "mesto_raw")
    var mestoRaw: String? = null,

    var potpis: String? = null,

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
