package com.rkl.backend.exception

class ErrorCode {
    companion object {
        const val NOT_ADMIN = "NOT_ADMIN"
        const val ENTITY_DOES_NOT_EXIST_ERROR = "ENTITY_DOES_NOT_EXIST_ERROR"
        const val FILE_UPLOAD_ERROR = "FILE_UPLOAD_ERROR"
        const val ENTITY_ALREADY_EXIST_ERROR = "ENTITY_ALREADY_EXIST_ERROR"
        const val GENERAL_VALIDATION_ERROR = "GENERAL_VALIDATION_ERROR"
        const val UNKNOWN_ERROR = "UNKNOWN_ERROR"
        const val UNAUTHORIZED_ERROR = "UNAUTHORIZED_ERROR"
    }
}