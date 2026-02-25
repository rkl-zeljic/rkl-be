package com.rkl.backend.searchfilter.specification

import com.rkl.backend.searchfilter.criteria.SearchCriteria
import com.rkl.backend.searchfilter.criteria.SearchOperation
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification

class SpecificationImpl<T : Any>(
    private val criteria: SearchCriteria,
    private val columnName: String,
    private val ignoreCase: Boolean = false
) : Specification<T> {

    override fun toPredicate(root: Root<T>, query: CriteriaQuery<*>, criteriaBuilder: CriteriaBuilder): Predicate? {
        val builder = criteriaBuilder
        val path = root.get<Any>(columnName)

        return when (criteria.operation) {
            SearchOperation.EQUALITY -> {
                if (criteria.value == null) {
                    builder.isNull(path)
                } else {
                    if (ignoreCase && criteria.value is String) {
                        builder.equal(builder.lower(path as jakarta.persistence.criteria.Expression<String>), criteria.value.lowercase())
                    } else {
                        builder.equal(path, criteria.value)
                    }
                }
            }
            SearchOperation.NEGATION -> {
                if (criteria.value == null) {
                    builder.isNotNull(path)
                } else {
                    builder.notEqual(path, criteria.value)
                }
            }
            SearchOperation.GREATER_THAN -> builder.greaterThan(path as jakarta.persistence.criteria.Expression<Comparable<Any>>, criteria.value as Comparable<Any>)
            SearchOperation.LESS_THAN -> builder.lessThan(path as jakarta.persistence.criteria.Expression<Comparable<Any>>, criteria.value as Comparable<Any>)
            SearchOperation.LIKE -> {
                if (ignoreCase) {
                    builder.like(builder.lower(path as jakarta.persistence.criteria.Expression<String>), "%${(criteria.value as String).lowercase()}%")
                } else {
                    builder.like(path as jakarta.persistence.criteria.Expression<String>, "%${criteria.value}%")
                }
            }
            SearchOperation.STARTS_WITH -> {
                if (ignoreCase) {
                    builder.like(builder.lower(path as jakarta.persistence.criteria.Expression<String>), "${(criteria.value as String).lowercase()}%")
                } else {
                    builder.like(path as jakarta.persistence.criteria.Expression<String>, "${criteria.value}%")
                }
            }
            SearchOperation.ENDS_WITH -> {
                if (ignoreCase) {
                    builder.like(builder.lower(path as jakarta.persistence.criteria.Expression<String>), "%${(criteria.value as String).lowercase()}")
                } else {
                    builder.like(path as jakarta.persistence.criteria.Expression<String>, "%${criteria.value}")
                }
            }
            SearchOperation.CONTAINS -> {
                if (ignoreCase) {
                    builder.like(builder.lower(path as jakarta.persistence.criteria.Expression<String>), "%${(criteria.value as String).lowercase()}%")
                } else {
                    builder.like(path as jakarta.persistence.criteria.Expression<String>, "%${criteria.value}%")
                }
            }
            SearchOperation.IN -> path.`in`(criteria.value as Collection<*>)
        }
    }
}