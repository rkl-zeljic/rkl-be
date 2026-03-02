package com.rkl.backend.repository

import com.rkl.backend.entity.ImportedFile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ImportedFileRepository : JpaRepository<ImportedFile, Long> {

    fun findAllByOrderByCreatedAtDesc(): List<ImportedFile>
}
