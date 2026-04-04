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

    fun findByBrojOtpremnice(brojOtpremnice: String): Otpremnica?

    @Modifying
    @Query("UPDATE Otpremnica o SET o.vozacUser = NULL WHERE o.vozacUser.id = :userId")
    fun unlinkFromUser(@Param("userId") userId: Long): Int

    @Modifying
    @Query("UPDATE Otpremnica o SET o.potpisVozaca = :signature WHERE o.vozacUser.id = :userId AND (o.potpisVozaca IS NULL OR o.potpisVozaca = '')")
    fun backfillDriverSignature(@Param("userId") userId: Long, @Param("signature") signature: String): Int

    @Modifying
    @Query("UPDATE otpremnice SET vozac_user_id = :userId WHERE LOWER(vozac_ime) = LOWER(:driverName) AND (vozac_user_id IS NULL OR vozac_user_id <> :userId)", nativeQuery = true)
    fun linkToDriver(@Param("userId") userId: Long, @Param("driverName") driverName: String): Int

    @Modifying
    @Query("UPDATE otpremnice SET vozac_user_id = NULL WHERE vozac_user_id = :userId", nativeQuery = true)
    fun unlinkFromDriver(@Param("userId") userId: Long): Int
}
