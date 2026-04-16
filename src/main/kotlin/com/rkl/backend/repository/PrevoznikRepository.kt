package com.rkl.backend.repository

import com.rkl.backend.entity.Prevoznik
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PrevoznikRepository : JpaRepository<Prevoznik, Long> {

    fun findAllByOrderByNazivAsc(): List<Prevoznik>

    fun findByNaziv(naziv: String): Prevoznik?

    fun findByNazivContainingIgnoreCase(naziv: String): List<Prevoznik>
}
