package com.rkl.backend.searchfilter.criteria.builder

import com.rkl.backend.entity.RklUser
import com.rkl.backend.searchfilter.criteria.SearchCriteria
import com.rkl.backend.searchfilter.criteria.SearchOperation
import com.rkl.backend.searchfilter.dto.UserFilter
import com.rkl.backend.searchfilter.specification.SpecificationImpl
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component

@Component
class UserFilterCriteriaBuilder : FilterCriteriaBuilder<RklUser, UserFilter> {

    override fun buildQuery(source: UserFilter): Specification<RklUser> {
        val specifications = mutableListOf<Specification<RklUser>>()

        source.email?.let {
            specifications.add(
                SpecificationImpl(
                    SearchCriteria("email", SearchOperation.CONTAINS, it),
                    "email",
                    true
                )
            )
        }

        source.type?.let {
            specifications.add(
                SpecificationImpl(
                    SearchCriteria("type", SearchOperation.EQUALITY, it),
                    "type"
                )
            )
        }

        return Specification.allOf(specifications)
    }
}
