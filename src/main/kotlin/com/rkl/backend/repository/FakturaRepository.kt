package com.rkl.backend.repository

import com.rkl.backend.entity.Faktura
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface FakturaRepository : JpaRepository<Faktura, Long> {

    fun findAllByOrderByCreatedAtDesc(): List<Faktura>

    @Query("SELECT COALESCE(MAX(f.id), 0) FROM Faktura f")
    fun findMaxId(): Long
}
