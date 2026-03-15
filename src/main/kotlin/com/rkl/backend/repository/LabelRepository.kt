package com.rkl.backend.repository

import com.rkl.backend.entity.Label
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LabelRepository : JpaRepository<Label, Long> {
    fun findByColumnName(columnName: String): List<Label>
    fun findByColumnNameAndCanonicalValue(columnName: String, canonicalValue: String): Label?
}
