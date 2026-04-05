package com.rkl.backend.dto.label

import jakarta.validation.constraints.NotBlank

data class LabelResponseDTO(
    val id: Long,
    val columnName: String,
    val canonicalValue: String,
    val variations: List<String>,
    val createdAt: String?,
    val updatedAt: String?
)

data class LabelsResponse(
    val status: String = "success",
    val data: List<LabelResponseDTO>,
    val totalCount: Int
)

data class CreateLabelRequest(
    @field:NotBlank(message = "Column name is required")
    val columnName: String,
    @field:NotBlank(message = "Canonical value is required")
    val canonicalValue: String,
    val variations: List<String> = emptyList()
)

data class UpdateLabelRequest(
    val canonicalValue: String? = null,
    val variations: List<String>? = null
)

data class AddVariationRequest(
    @field:NotBlank(message = "Variation is required")
    val variation: String
)

data class LabelColumnsResponse(
    val status: String = "success",
    val data: List<String>
)

data class UnassignedValueDTO(
    val value: String,
    val count: Long
)

data class UnassignedValuesResponse(
    val status: String = "success",
    val data: List<UnassignedValueDTO>,
    val totalCount: Int
)
