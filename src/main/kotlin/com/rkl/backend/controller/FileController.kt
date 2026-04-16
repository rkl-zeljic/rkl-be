package com.rkl.backend.controller

import com.rkl.backend.dto.file.FileDeleteResponse
import com.rkl.backend.dto.file.ImportedFileDto
import com.rkl.backend.dto.file.ImportedFilesResponse
import com.rkl.backend.repository.ImportedFileRepository
import com.rkl.backend.repository.MerenjeRepository
import com.rkl.backend.service.AzureBlobStorageService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/files")
class FileController(
    private val importedFileRepository: ImportedFileRepository,
    private val merenjeRepository: MerenjeRepository,
    private val azureBlobStorageService: AzureBlobStorageService
) {

    @GetMapping
    fun listFiles(): ImportedFilesResponse {
        val files = importedFileRepository.findAllByOrderByCreatedAtDesc()
        return ImportedFilesResponse(
            data = files.map { file ->
                ImportedFileDto(
                    id = file.id,
                    originalFilename = file.originalFilename,
                    fileSize = file.fileSize,
                    recordCount = merenjeRepository.countByImportedFileId(file.id!!),
                    uploadedBy = file.uploadedBy,
                    createdAt = file.createdAt?.toString()
                )
            }
        )
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Transactional
    fun deleteFile(@PathVariable id: Long): FileDeleteResponse {
        val file = importedFileRepository.findById(id).orElseThrow {
            NoSuchElementException("File with id $id not found")
        }

        // Delete blob from Azure
        azureBlobStorageService.delete(file.blobName)

        // Smart delete: only delete import-only merenja, de-validate otpremnica-sourced ones
        var deletedCount = 0L
        var devalidatedCount = 0L
        for (merenje in file.merenja.toList()) {
            if (merenje.otpremnica != null) {
                // Merenje has otpremnica source - just de-validate
                merenje.importedFile = null
                merenjeRepository.save(merenje)
                devalidatedCount++
            } else {
                // Import-only merenje - delete it
                merenjeRepository.delete(merenje)
                deletedCount++
            }
        }

        importedFileRepository.delete(file)

        return FileDeleteResponse(deletedMeasurements = deletedCount, devalidatedMeasurements = devalidatedCount)
    }

    @GetMapping("/{id}/download")
    fun downloadFile(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val file = importedFileRepository.findById(id).orElseThrow {
            NoSuchElementException("File with id $id not found")
        }

        val data = azureBlobStorageService.download(file.blobName)

        val contentType = file.contentType ?: "application/octet-stream"

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.originalFilename}\"")
            .contentType(MediaType.parseMediaType(contentType))
            .contentLength(data.size.toLong())
            .body(data)
    }
}
