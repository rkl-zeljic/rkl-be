package com.rkl.backend.service

import com.rkl.backend.dto.label.*
import com.rkl.backend.entity.Label
import com.rkl.backend.repository.LabelRepository
import com.rkl.backend.repository.MerenjeRepository
import com.rkl.backend.repository.UserRepository
import com.rkl.backend.util.TextUtils
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LabelService(
    private val repository: LabelRepository,
    private val entityManager: EntityManager,
    private val userRepository: UserRepository,
    private val merenjeRepository: MerenjeRepository
) {

    private val logger = LoggerFactory.getLogger(LabelService::class.java)

    companion object {
        val LABELABLE_COLUMNS = listOf(
            "posiljalac", "porucilac", "primalac", "roba",
            "prevoznik", "registracija", "vozac", "mesto"
        )
    }

    fun getColumns(): LabelColumnsResponse {
        return LabelColumnsResponse(data = LABELABLE_COLUMNS)
    }

    fun getLabelsByColumn(columnName: String): LabelsResponse {
        validateColumn(columnName)
        val labels = repository.findByColumnName(columnName)
        return LabelsResponse(
            data = labels.map { it.toDto() },
            totalCount = labels.size
        )
    }

    fun getAllLabels(): LabelsResponse {
        val labels = repository.findAll()
        return LabelsResponse(
            data = labels.map { it.toDto() },
            totalCount = labels.size
        )
    }

    @Transactional
    fun createLabel(request: CreateLabelRequest): LabelResponseDTO {
        validateColumn(request.columnName)

        val existing = repository.findByColumnNameAndCanonicalValue(request.columnName, request.canonicalValue)
        if (existing != null) {
            throw IllegalArgumentException("Labela '${request.canonicalValue}' već postoji za kolonu '${request.columnName}'")
        }

        val normalizedVariations = request.variations
            .map { normalizeVariation(request.columnName, it) }
            .filter { it.isNotBlank() }
            .toMutableSet()

        val label = Label(
            columnName = request.columnName,
            canonicalValue = request.canonicalValue,
            variations = normalizedVariations
        )

        val saved = repository.save(label)
        renormalizeMeasurements(request.columnName, request.canonicalValue, normalizedVariations)
        return saved.toDto()
    }

    @Transactional
    fun updateLabel(id: Long, request: UpdateLabelRequest): LabelResponseDTO {
        val label = repository.findById(id).orElseThrow {
            NoSuchElementException("Labela sa id $id nije pronađena")
        }

        if (request.canonicalValue != null) {
            label.canonicalValue = request.canonicalValue
        }

        if (request.variations != null) {
            label.variations = request.variations
                .map { normalizeVariation(label.columnName, it) }
                .filter { it.isNotBlank() }
                .toMutableSet()
        }

        val saved = repository.save(label)
        renormalizeMeasurements(label.columnName, label.canonicalValue, label.variations)
        return saved.toDto()
    }

    @Transactional
    fun addVariation(id: Long, request: AddVariationRequest): LabelResponseDTO {
        val label = repository.findById(id).orElseThrow {
            NoSuchElementException("Labela sa id $id nije pronađena")
        }

        val normalized = normalizeVariation(label.columnName, request.variation)
        if (normalized.isBlank()) {
            throw IllegalArgumentException("Varijacija ne može biti prazna")
        }

        label.variations.add(normalized)
        val saved = repository.save(label)
        renormalizeMeasurements(label.columnName, label.canonicalValue, listOf(normalized))
        return saved.toDto()
    }

    @Transactional
    fun removeVariation(id: Long, variation: String): LabelResponseDTO {
        val label = repository.findById(id).orElseThrow {
            NoSuchElementException("Labela sa id $id nije pronađena")
        }

        val normalized = normalizeVariation(label.columnName, variation)
        label.variations.remove(normalized)
        return repository.save(label).toDto()
    }

    @Transactional
    fun deleteLabel(id: Long) {
        if (!repository.existsById(id)) {
            throw NoSuchElementException("Labela sa id $id nije pronađena")
        }
        repository.deleteById(id)
    }

    /**
     * Resolves a value to its canonical label for a given column.
     * Returns the canonical value if a matching label is found, otherwise the original value.
     */
    fun resolveValue(columnName: String, value: String?): String? {
        if (value.isNullOrBlank()) return value

        val labels = repository.findByColumnName(columnName)
        val normalizedInput = normalizeVariation(columnName, value)

        for (label in labels) {
            // Check if it matches canonical value (case-insensitive)
            if (normalizeVariation(columnName, label.canonicalValue).equals(normalizedInput, ignoreCase = true)) {
                return label.canonicalValue
            }
            // Check variations
            for (variation in label.variations) {
                if (variation.equals(normalizedInput, ignoreCase = true)) {
                    return label.canonicalValue
                }
            }
        }

        return value
    }

    /**
     * Normalizes a variation for storage/matching.
     * For registracija: strips spaces/dashes, uppercases, reformats as XX-NNN-XX.
     * For other columns: just trims.
     */
    private fun normalizeVariation(columnName: String, value: String): String {
        return if (columnName == "registracija") {
            TextUtils.normalizeRegistration(value)
        } else {
            value.trim()
        }
    }

    /**
     * Retroactively updates existing measurements: replaces variation values with the canonical value.
     * Column name is validated against LABELABLE_COLUMNS whitelist to prevent SQL injection.
     */
    private fun renormalizeMeasurements(columnName: String, canonicalValue: String, variations: Collection<String>) {
        if (variations.isEmpty()) return
        validateColumn(columnName) // whitelist guard against SQL injection

        var totalUpdated = 0
        for (variation in variations) {
            if (variation.isBlank()) continue
            val updated = entityManager.createNativeQuery(
                "UPDATE merenja SET $columnName = :canonical WHERE LOWER($columnName) = LOWER(:variation)"
            )
                .setParameter("canonical", canonicalValue)
                .setParameter("variation", variation)
                .executeUpdate()
            totalUpdated += updated
        }

        // Also normalize rows that match the canonical value with different casing
        val caseFixed = entityManager.createNativeQuery(
            "UPDATE merenja SET $columnName = :canonical WHERE LOWER($columnName) = LOWER(:canonical) AND $columnName <> :canonical"
        )
            .setParameter("canonical", canonicalValue)
            .executeUpdate()
        totalUpdated += caseFixed

        if (totalUpdated > 0) {
            logger.info("Renormalized $totalUpdated measurements for $columnName -> '$canonicalValue'")
        }

        // If vozac column was renormalized, relink measurements to driver users
        if (columnName == "vozac" && totalUpdated > 0) {
            val driverUser = userRepository.findByDriverNameIgnoreCase(canonicalValue)
            if (driverUser != null) {
                val linked = merenjeRepository.linkMeasurementsToDriver(driverUser.id!!, canonicalValue)
                if (linked > 0) {
                    logger.info("Relinked $linked measurements to driver user ${driverUser.id} after vozac renormalization")
                }
            }
        }
    }

    private fun validateColumn(columnName: String) {
        if (columnName !in LABELABLE_COLUMNS) {
            throw IllegalArgumentException("Kolona '$columnName' nije dozvoljena. Dozvoljene: $LABELABLE_COLUMNS")
        }
    }

    private fun Label.toDto(): LabelResponseDTO = LabelResponseDTO(
        id = id!!,
        columnName = columnName,
        canonicalValue = canonicalValue,
        variations = variations.toList().sorted(),
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )
}
