package com.rkl.backend.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "kupci",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_kupci_naziv", columnNames = ["naziv"])
    ],
    indexes = [
        Index(name = "ix_kupci_naziv", columnList = "naziv")
    ]
)
class Kupac(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var naziv: String = "",

    @Column
    var email: String? = null,

    @Column
    var telefon: String? = null,

    @Column
    var adresa: String? = null,

    @Column(columnDefinition = "TEXT")
    var napomena: String? = null,

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
