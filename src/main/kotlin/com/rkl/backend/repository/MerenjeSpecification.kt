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
}
