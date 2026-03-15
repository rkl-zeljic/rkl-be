package com.rkl.backend.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "labels",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_labels_column_canonical", columnNames = ["column_name", "canonical_value"])
    ],
    indexes = [
        Index(name = "ix_labels_column", columnList = "column_name")
    ]
)
class Label(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "column_name", nullable = false)
    var columnName: String = "",

    @Column(name = "canonical_value", nullable = false)
    var canonicalValue: String = "",

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "label_variations",
        joinColumns = [JoinColumn(name = "label_id")],
        indexes = [Index(name = "ix_label_variations_value", columnList = "variation")]
    )
    @Column(name = "variation", nullable = false)
    var variations: MutableSet<String> = mutableSetOf(),

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
