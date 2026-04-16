package com.rkl.backend.repository

import com.rkl.backend.entity.Kupac
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface KupacRepository : JpaRepository<Kupac, Long> {

    fun findAllByOrderByNazivAsc(): List<Kupac>

    fun findByNaziv(naziv: String): Kupac?

    fun findByNazivContainingIgnoreCase(naziv: String): List<Kupac>
}
