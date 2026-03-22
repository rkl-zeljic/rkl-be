package com.rkl.backend.repository

import com.rkl.backend.entity.Primalac
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PrimalacRepository : JpaRepository<Primalac, Long> {

    fun findAllByOrderByNazivAsc(): List<Primalac>

    fun findByNaziv(naziv: String): Primalac?

    fun findByNazivContainingIgnoreCase(naziv: String): List<Primalac>
}
