package com.rkl.backend.util

object TextUtils {

    fun normalizeWhitespace(text: String?): String? {
        return text?.replace(Regex("\\s+"), " ")?.trim()
    }
}
