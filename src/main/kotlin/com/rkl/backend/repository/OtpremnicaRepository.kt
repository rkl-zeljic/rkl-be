package com.rkl.backend.repository

import com.rkl.backend.entity.Otpremnica
import com.rkl.backend.entity.RklUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OtpremnicaRepository : JpaRepository<Otpremnica, Long> {

    fun findAllByOrderByCreatedAtDesc(): List<Otpremnica>

    fun findByVozacUserOrderByCreatedAtDesc(vozacUser: RklUser): List<Otpremnica>

    @Query("SELECT COALESCE(MAX(o.id), 0) FROM Otpremnica o")
    fun findMaxId(): Long

    @Modifying
    @Query("UPDATE Otpremnica o SET o.vozacUser = NULL WHERE o.vozacUser.id = :userId")
    fun unlinkFromUser(@Param("userId") userId: Long): Int
}
