package com.rkl.backend.controller

import com.rkl.backend.dto.label.*
import com.rkl.backend.service.LabelService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/labels")
class LabelController(
    private val labelService: LabelService
) {

    @GetMapping("/columns")
    fun getColumns(): LabelColumnsResponse {
        return labelService.getColumns()
    }

    @GetMapping
    fun getLabels(@RequestParam columnName: String?): LabelsResponse {
        return if (columnName != null) {
            labelService.getLabelsByColumn(columnName)
        } else {
            labelService.getAllLabels()
        }
    }

    @PostMapping
    fun createLabel(@Valid @RequestBody request: CreateLabelRequest): LabelResponseDTO {
        return labelService.createLabel(request)
    }

    @PutMapping("/{id}")
    fun updateLabel(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateLabelRequest
    ): LabelResponseDTO {
        return labelService.updateLabel(id, request)
    }

    @PostMapping("/{id}/variations")
    fun addVariation(
        @PathVariable id: Long,
        @Valid @RequestBody request: AddVariationRequest
    ): LabelResponseDTO {
        return labelService.addVariation(id, request)
    }

    @DeleteMapping("/{id}/variations")
    fun removeVariation(
        @PathVariable id: Long,
        @RequestParam variation: String
    ): LabelResponseDTO {
        return labelService.removeVariation(id, variation)
    }

    @DeleteMapping("/{id}")
    fun deleteLabel(@PathVariable id: Long) {
        labelService.deleteLabel(id)
    }
}
