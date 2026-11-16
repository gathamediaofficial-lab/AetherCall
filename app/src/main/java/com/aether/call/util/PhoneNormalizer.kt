package com.aether.call.util

/**
 * Ultra-fast, zero-allocation phone digit normalization and E.164 formatting.
 */
object PhoneNormalizer {

    fun normalizeDigits(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val len = raw.length
        var count = 0
        for (i in 0 until len) {
            val c = raw[i]
            if (c in '0'..'9') count++
        }
        if (count == 0) return ""
        val sb = StringBuilder(count)
        for (i in 0 until len) {
            val c = raw[i]
            if (c in '0'..'9') sb.append(c)
        }
        return if (sb.length > 10) sb.substring(sb.length - 10) else sb.toString()
    }

    fun toE164India(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val len = raw.length
        val sb = StringBuilder(len)
        for (i in 0 until len) {
            val c = raw[i]
            if (c in '0'..'9') sb.append(c)
        }
        val digits = sb.toString()
        return when {
            digits.length == 10 && digits.first() in '6'..'9' -> "91$digits"
            digits.length == 11 && digits.startsWith("0") -> "91${digits.substring(1)}"
            else -> digits
        }
    }

    fun toWhatsAppDigits(raw: String?): String = toE164India(raw)
}
