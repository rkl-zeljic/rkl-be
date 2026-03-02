package com.rkl.backend.dto.file

data class ImportedFileDto(
    val id: Long?,
    val originalFilename: String,
    val fileSize: Long,
    val recordCount: Long,
    val uploadedBy: String?,
    val createdAt: String?
)

data class ImportedFilesResponse(
    val status: String = "success",
    val data: List<ImportedFileDto>
)

data class FileDeleteResponse(
    val status: String = "success",
    val deletedMeasurements: Long
)
