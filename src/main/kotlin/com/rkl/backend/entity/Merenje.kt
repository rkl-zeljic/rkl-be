package com.rkl.backend.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "merenja",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_merenja_datum_merni", columnNames = ["datum_izvestaja", "merni_list_br"])
    ],
    indexes = [
        Index(name = "ix_merenja_datum", columnList = "datum_izvestaja"),
        Index(name = "ix_merenja_roba", columnList = "roba"),
        Index(name = "ix_merenja_registracija", columnList = "registracija"),
        Index(name = "ix_merenja_vozac_user", columnList = "vozac_user_id")
    ]
)
class Merenje(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_file_id")
    var importedFile: ImportedFile? = null,

    @Column(name = "datum_izvestaja")
    var datumIzvestaja: LocalDate? = null,

    @Column(name = "merni_list_br", nullable = false)
    var merniListBr: Int = 0,

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
