package com.rkl.backend.searchfilter.criteria

data class SearchCriteria(
    val key: String,
    val operation: SearchOperation,
    val value: Any?,
    val orPredicate: Boolean = false
)

enum class SearchOperation {
    EQUALITY,
    NEGATION,
    GREATER_THAN,
    LESS_THAN,
    LIKE,
    STARTS_WITH,
    ENDS_WITH,
    CONTAINS,
    IN
}
