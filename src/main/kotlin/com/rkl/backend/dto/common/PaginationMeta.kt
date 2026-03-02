package com.rkl.backend.dto.common

data class PaginationMeta(
    val totalCount: Long,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int
)
