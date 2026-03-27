package com.rkl.backend.repository

import com.rkl.backend.entity.Prevoznica
import com.rkl.backend.entity.RklUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PrevoznicaRepository : JpaRepository<Prevoznica, Long> {

    fun findAllByOrderByCreatedAtDesc(): List<Prevoznica>

    fun findByVozacUserOrderByCreatedAtDesc(vozacUser: RklUser): List<Prevoznica>

    @Query("SELECT COALESCE(MAX(p.id), 0) FROM Prevoznica p")
    fun findMaxId(): Long

    @Modifying
    @Query("UPDATE Prevoznica p SET p.vozacUser = NULL WHERE p.vozacUser.id = :userId")
    fun unlinkFromUser(@Param("userId") userId: Long): Int

    @Modifying
    @Query("UPDATE Prevoznica p SET p.potpisVozaca = :signature WHERE p.vozacUser.id = :userId AND (p.potpisVozaca IS NULL OR p.potpisVozaca = '')")
    fun backfillDriverSignature(@Param("userId") userId: Long, @Param("signature") signature: String): Int
}
