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

    fun findByOtpremnicaId(otpremnicaId: Long): Prevoznica?

    fun findByOtpremnicaIdIn(otpremnicaIds: Collection<Long>): List<Prevoznica>

    fun findAllByOrderByCreatedAtDesc(): List<Prevoznica>

    fun findByVozacUserOrderByCreatedAtDesc(vozacUser: RklUser): List<Prevoznica>

    @Query("SELECT COALESCE(MAX(p.id), 0) FROM Prevoznica p")
    fun findMaxId(): Long

    @Query("SELECT p.additionalEmails FROM Prevoznica p WHERE p.additionalEmails IS NOT NULL AND p.additionalEmails <> ''")
    fun findAllAdditionalEmailsRaw(): List<String>

    @Query("SELECT DISTINCT p.mestoIstovara FROM Prevoznica p WHERE p.mestoIstovara IS NOT NULL AND p.mestoIstovara <> '' ORDER BY p.mestoIstovara")
    fun findDistinctMestoIstovara(): List<String>

    @Query("SELECT DISTINCT p.mestoIstovara FROM Prevoznica p WHERE p.mestoIstovara IS NOT NULL AND p.mestoIstovara <> '' AND LOWER(p.mestoIstovara) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY p.mestoIstovara")
    fun findDistinctMestoIstovara(@Param("search") search: String): List<String>

    @Modifying
    @Query("UPDATE Prevoznica p SET p.vozacUser = NULL WHERE p.vozacUser.id = :userId")
    fun unlinkFromUser(@Param("userId") userId: Long): Int

    @Modifying
    @Query("UPDATE Prevoznica p SET p.potpisVozaca = :signature WHERE p.vozacUser.id = :userId AND (p.potpisVozaca IS NULL OR p.potpisVozaca = '')")
    fun backfillDriverSignature(@Param("userId") userId: Long, @Param("signature") signature: String): Int

    @Modifying
    @Query("UPDATE prevoznice SET vozac_user_id = :userId WHERE LOWER(vozac_ime) = LOWER(:driverName) AND (vozac_user_id IS NULL OR vozac_user_id <> :userId)", nativeQuery = true)
    fun linkToDriver(@Param("userId") userId: Long, @Param("driverName") driverName: String): Int

    @Modifying
    @Query("UPDATE prevoznice SET vozac_user_id = NULL WHERE vozac_user_id = :userId", nativeQuery = true)
    fun unlinkFromDriver(@Param("userId") userId: Long): Int
}
