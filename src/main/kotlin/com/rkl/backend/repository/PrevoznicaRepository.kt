package com.rkl.backend.repository

import com.rkl.backend.entity.Prevoznica
import com.rkl.backend.entity.RklUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PrevoznicaRepository : JpaRepository<Prevoznica, Long> {

    fun findAllByOrderByCreatedAtDesc(): List<Prevoznica>

    fun findByVozacUserOrderByCreatedAtDesc(vozacUser: RklUser): List<Prevoznica>

    @Query("SELECT COALESCE(MAX(p.id), 0) FROM Prevoznica p")
    fun findMaxId(): Long
}
