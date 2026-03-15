package com.rkl.backend.repository

import com.rkl.backend.entity.Merenje
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate

object MerenjeSpecification {

    fun datumOd(date: LocalDate): Specification<Merenje> =
        Specification { root, _, cb ->
            cb.greaterThanOrEqualTo(root.get("datumIzvestaja"), date)
        }

    fun datumDo(date: LocalDate): Specification<Merenje> =
        Specification { root, _, cb ->
            cb.lessThanOrEqualTo(root.get("datumIzvestaja"), date)
        }

    fun textLike(field: String, value: String): Specification<Merenje> =
        Specification { root, _, cb ->
            cb.like(cb.lower(root.get(field)), "%${value.lowercase()}%")
        }

    fun byVozacUserId(userId: Long): Specification<Merenje> =
        Specification { root, _, cb ->
            cb.equal(root.get<Any>("vozacUser").get<Long>("id"), userId)
        }

    /**
     * Handles comma-separated multi-value filters.
     * If the value contains commas, splits and matches any (OR with case-insensitive exact match).
     * Otherwise falls back to LIKE for single values.
     */
    fun textFilter(field: String, value: String): Specification<Merenje> {
        val parts = value.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size <= 1) {
            return textLike(field, value)
        }
        return Specification { root, _, cb ->
            val lower = cb.lower(root.get<String>(field))
            cb.or(*parts.map { cb.equal(lower, it.lowercase()) }.toTypedArray())
        }
    }
}
