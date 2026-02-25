package com.rkl.backend.searchfilter.specification

import com.rkl.backend.searchfilter.criteria.SearchCriteria
import com.rkl.backend.searchfilter.criteria.SearchOperation
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class JoinSpecificationImpl<T : Any, J>(
    private val criteria: SearchCriteria,
    private val joinKey: String,
    private val nullable: Boolean
) : Specification<T> {
    override fun toPredicate(root: Root<T>, query: CriteriaQuery<*>, builder: CriteriaBuilder): Predicate? {
        val join: Join<T, J> = root.join(joinKey, JoinType.LEFT)
        return when (criteria.operation) {
            SearchOperation.GREATER_THAN ->
                if (root.get<Any>(criteria.key).javaType == Instant::class.java) {
                    builder.greaterThanOrEqualTo(root.get(criteria.key), criteria.value as Instant)
                } else if (root.get<Any>(criteria.key).javaType == LocalDate::class.java) {
                    builder.greaterThanOrEqualTo(root.get(criteria.key), criteria.value as LocalDate)
                } else if (root.get<Any>(criteria.key).javaType == LocalTime::class.java) {
                    builder.greaterThanOrEqualTo(root.get(criteria.key), criteria.value as LocalTime)
                } else {
                    builder.greaterThanOrEqualTo(root[criteria.key], criteria.value.toString())
                }

            SearchOperation.LESS_THAN ->
                if (root.get<Any>(criteria.key).javaType == Instant::class.java) {
                    builder.lessThanOrEqualTo(root.get(criteria.key), criteria.value as Instant)
                } else if (root.get<Any>(criteria.key).javaType == LocalDate::class.java) {
                    builder.lessThanOrEqualTo(root.get(criteria.key), criteria.value as LocalDate)
                } else if (root.get<Any>(criteria.key).javaType == LocalTime::class.java) {
                    builder.lessThanOrEqualTo(root.get(criteria.key), criteria.value as LocalTime)
                } else {
                    builder.lessThanOrEqualTo(root[criteria.key], criteria.value.toString())
                }

            SearchOperation.CONTAINS -> if (join.get<Any>(criteria.key).javaType == String::class.java) {
                builder.like(builder.lower(join[criteria.key]), "%" + criteria.value.toString().lowercase() + "%")
            } else {
                if (nullable) {
                    builder.or(builder.isNull(join), builder.equal(join.get<Any>(criteria.key), criteria.value))
                } else {
                    builder.equal(join.get<Any>(criteria.key), criteria.value)
                }
            }

            SearchOperation.EQUALITY ->
                if (nullable) {
                    builder.or(builder.isNull(join), builder.equal(join.get<Any>(criteria.key), criteria.value))
                } else {
                    builder.equal(join.get<Any>(criteria.key), criteria.value)
                }

            else -> null
        }
    }
}