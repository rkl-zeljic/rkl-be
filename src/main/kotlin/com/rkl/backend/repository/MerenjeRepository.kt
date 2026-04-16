package com.rkl.backend.repository

import com.rkl.backend.entity.Merenje
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.Optional

interface CustomerProjection {
    val porucilac: String
    val measurementCount: Long
}

interface DriverProjection {
    val vozac: String
    val measurementCount: Long
}

interface StatsProjection {
    val period: String
    val count: Long
}

@Repository
interface MerenjeRepository : JpaRepository<Merenje, Long>, JpaSpecificationExecutor<Merenje> {

    fun findByDatumIzvestajaAndMerniListBr(datumIzvestaja: LocalDate, merniListBr: Int): Optional<Merenje>

    fun findByOtpremnicaId(otpremnicaId: Long): Merenje?

    fun deleteByOtpremnicaId(otpremnicaId: Long)

    @Query("SELECT COALESCE(MAX(m.merniListBr), 0) FROM Merenje m WHERE m.datumIzvestaja = :datum")
    fun findMaxMerniListBrByDatum(@Param("datum") datum: LocalDate): Int

    fun countByImportedFileId(id: Long): Long

    @Query(
        """
        SELECT m.porucilac AS porucilac, COUNT(m) AS measurementCount
        FROM Merenje m
        WHERE m.porucilac IS NOT NULL AND m.porucilac <> ''
        GROUP BY m.porucilac
        ORDER BY m.porucilac ASC
        """
    )
    fun findCustomersWithCounts(): List<CustomerProjection>

    @Query(
        """
        SELECT m.vozac AS vozac, COUNT(m) AS measurementCount
        FROM Merenje m
        WHERE m.vozac IS NOT NULL AND m.vozac <> ''
        GROUP BY m.vozac
        ORDER BY m.vozac ASC
        """
    )
    fun findDriversWithCounts(): List<DriverProjection>

    @Query("SELECT DISTINCT m.roba FROM Merenje m WHERE m.roba IS NOT NULL AND m.roba <> '' ORDER BY m.roba ASC")
    fun findDistinctRoba(): List<String>

    @Query("SELECT DISTINCT m.roba FROM Merenje m WHERE m.porucilac = :porucilac AND m.datumIzvestaja >= :datumOd AND m.datumIzvestaja <= :datumDo AND m.roba IS NOT NULL AND m.roba <> ''")
    fun findDistinctRobaByFaktura(@Param("porucilac") porucilac: String, @Param("datumOd") datumOd: LocalDate, @Param("datumDo") datumDo: LocalDate): List<String>

    @Query("SELECT DISTINCT m.prevoznik FROM Merenje m WHERE m.porucilac = :porucilac AND m.datumIzvestaja >= :datumOd AND m.datumIzvestaja <= :datumDo AND m.prevoznik IS NOT NULL AND m.prevoznik <> ''")
    fun findDistinctPrevoznikByFaktura(@Param("porucilac") porucilac: String, @Param("datumOd") datumOd: LocalDate, @Param("datumDo") datumDo: LocalDate): List<String>

    @Query("SELECT DISTINCT m.primalac FROM Merenje m WHERE m.porucilac = :porucilac AND m.datumIzvestaja >= :datumOd AND m.datumIzvestaja <= :datumDo AND m.primalac IS NOT NULL AND m.primalac <> ''")
    fun findDistinctPrimalacByFaktura(@Param("porucilac") porucilac: String, @Param("datumOd") datumOd: LocalDate, @Param("datumDo") datumDo: LocalDate): List<String>

    @Query("SELECT DISTINCT m.posiljalac FROM Merenje m WHERE m.porucilac = :porucilac AND m.datumIzvestaja >= :datumOd AND m.datumIzvestaja <= :datumDo AND m.posiljalac IS NOT NULL AND m.posiljalac <> ''")
    fun findDistinctPosiljalacByFaktura(@Param("porucilac") porucilac: String, @Param("datumOd") datumOd: LocalDate, @Param("datumDo") datumDo: LocalDate): List<String>

    @Query("SELECT DISTINCT m.registracija FROM Merenje m WHERE m.registracija IS NOT NULL AND m.registracija <> '' ORDER BY m.registracija ASC")
    fun findDistinctRegistracija(): List<String>

    @Query("SELECT DISTINCT m.prevoznik FROM Merenje m WHERE m.prevoznik IS NOT NULL AND m.prevoznik <> '' ORDER BY m.prevoznik ASC")
    fun findDistinctPrevoznik(): List<String>

    @Query("SELECT DISTINCT m.posiljalac FROM Merenje m WHERE m.posiljalac IS NOT NULL AND m.posiljalac <> '' ORDER BY m.posiljalac ASC")
    fun findDistinctPosiljalac(): List<String>

    @Query("SELECT DISTINCT m.porucilac FROM Merenje m WHERE m.porucilac IS NOT NULL AND m.porucilac <> '' ORDER BY m.porucilac ASC")
    fun findDistinctPorucilac(): List<String>

    @Query("SELECT DISTINCT m.primalac FROM Merenje m WHERE m.primalac IS NOT NULL AND m.primalac <> '' ORDER BY m.primalac ASC")
    fun findDistinctPrimalac(): List<String>

    @Query("SELECT DISTINCT m.vozac FROM Merenje m WHERE m.vozac IS NOT NULL AND m.vozac <> '' ORDER BY m.vozac ASC")
    fun findDistinctVozac(): List<String>

    @Query("SELECT DISTINCT m.mesto FROM Merenje m WHERE m.mesto IS NOT NULL AND m.mesto <> '' ORDER BY m.mesto ASC")
    fun findDistinctMesto(): List<String>

    @Modifying
    @Query("UPDATE Merenje m SET m.potpis = :signature WHERE m.id = :id")
    fun updateSignature(@Param("id") id: Long, @Param("signature") signature: String?)

    @Modifying
    @Query("UPDATE Merenje m SET m.vozacUser.id = :userId WHERE LOWER(m.vozac) = LOWER(:driverName) AND (m.vozacUser IS NULL OR m.vozacUser.id <> :userId)")
    fun linkMeasurementsToDriver(@Param("userId") userId: Long, @Param("driverName") driverName: String): Int

    @Modifying
    @Query("UPDATE Merenje m SET m.vozacUser = NULL WHERE m.vozacUser.id = :userId")
    fun unlinkMeasurementsFromDriver(@Param("userId") userId: Long): Int

    @Query(
        value = """
            SELECT TO_CHAR(datum_izvestaja, :format) AS period, COUNT(*) AS count
            FROM merenja
            WHERE 1=1
              AND (:datumOd IS NULL OR datum_izvestaja >= CAST(:datumOd AS DATE))
              AND (:datumDo IS NULL OR datum_izvestaja <= CAST(:datumDo AS DATE))
              AND (:porucilac IS NULL OR porucilac LIKE :porucilac)
              AND (:vozac IS NULL OR vozac LIKE :vozac)
              AND (:roba IS NULL OR roba LIKE :roba)
            GROUP BY 1
            ORDER BY 1 ASC
        """,
        nativeQuery = true
    )
    fun findStats(
        @Param("format") format: String,
        @Param("datumOd") datumOd: String?,
        @Param("datumDo") datumDo: String?,
        @Param("porucilac") porucilac: String?,
        @Param("vozac") vozac: String?,
        @Param("roba") roba: String?
    ): List<StatsProjection>
}
