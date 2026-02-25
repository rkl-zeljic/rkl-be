package com.rkl.backend.searchfilter.criteria.builder

import org.springframework.data.jpa.domain.Specification

interface FilterCriteriaBuilder<T : Any, F> {
    fun buildQuery(source: F): Specification<T>
}
