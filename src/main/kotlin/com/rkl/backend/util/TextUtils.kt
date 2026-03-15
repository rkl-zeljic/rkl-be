package com.rkl.backend.util

object TextUtils {

    fun normalizeWhitespace(text: String?): String? {
        return text?.replace(Regex("\\s+"), " ")?.trim()
    }

    /**
     * Normalizes a vehicle registration plate for matching:
     * - Strips all spaces and dashes
     * - Uppercases
     * - Reformats as groups separated by dashes (letters-digits-letters)
     * Example: "sa 194 gb" -> "SA-194-GB"
     */
    fun normalizeRegistration(value: String): String {
        val stripped = value.replace(Regex("[\\s\\-]"), "").uppercase()
        if (stripped.isBlank()) return ""

        // Split into groups: letters vs digits
        val groups = mutableListOf<String>()
        val current = StringBuilder()
        var lastWasDigit: Boolean? = null

        for (ch in stripped) {
            val isDigit = ch.isDigit()
            if (lastWasDigit != null && isDigit != lastWasDigit) {
                groups.add(current.toString())
                current.clear()
            }
            current.append(ch)
            lastWasDigit = isDigit
        }
        if (current.isNotEmpty()) {
            groups.add(current.toString())
        }

        return groups.joinToString("-")
    }
}
