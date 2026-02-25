package com.rkl.backend.exception.base

import java.time.LocalDateTime
import java.util.UUID

open class BaseException(override val message: String) : RuntimeException(message) {
    val id: UUID = UUID.randomUUID()

    val timeStamp: LocalDateTime = LocalDateTime.now()
}
