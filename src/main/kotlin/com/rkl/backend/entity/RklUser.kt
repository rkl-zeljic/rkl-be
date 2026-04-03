package com.rkl.backend.entity

import com.rkl.backend.enums.UserType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.annotation.CreatedDate
import java.time.Instant

@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(name = "user_email_unique", columnNames = ["email"]),
        UniqueConstraint(name = "user_username_unique", columnNames = ["username"])
    ]
)
class RklUser(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    val id: Long? = null,

    @Column
    var email: String? = null,

    @Enumerated(EnumType.STRING)
    var type: UserType,

    @Column
    var driverName: String? = null,

    @Column
    var username: String? = null,

    @Column
    var passwordHash: String? = null,

    @Column(columnDefinition = "TEXT")
    var signature: String? = null,

    @Column(nullable = false)
    @CreatedDate
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now()
        }
    }
}