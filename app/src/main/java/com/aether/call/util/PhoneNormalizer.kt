package com.aether.call.util

object PhoneNormalizer {

    fun normalizeDigits(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val digits = raw.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    fun toE164India(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 10 && digits.first() in '6'..'9' -> "91$digits"
            digits.length == 11 && digits.startsWith("0") -> "91${digits.drop(1)}"
            else -> digits
        }
    }

    fun toWhatsAppDigits(raw: String?): String = toE164India(raw)
}