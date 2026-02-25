package com.rkl.backend.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "jwt_key_cache")
class JwtKeyCache(
    @Id
    val id: Long = 1, // Single row table

    @Column(nullable = false, length = 1000)
    var publicKeyJson: String,

    @Column(nullable = false)
    var fetchedAt: Instant = Instant.now(),
)