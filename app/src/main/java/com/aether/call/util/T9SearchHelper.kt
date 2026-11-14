package com.aether.call.util

/**
 * High-performance T9 predictive matching helper for contact names and phone numbers.
 */
object T9SearchHelper {

    private val digitToLettersMap = mapOf(
        '2' to "abc",
        '3' to "def",
        '4' to "ghi",
        '5' to "jkl",
        '6' to "mno",
        '7' to "pqrs",
        '8' to "tuv",
        '9' to "wxyz",
    )

    /**
     * Checks if a contact name matches a T9 numeric query sequence.
     */
    fun matchesT9(name: String, queryDigits: String): Boolean {
        if (queryDigits.isEmpty()) return true
        if (!queryDigits.all { it.isDigit() }) return false

        val patternBuilder = StringBuilder()
        for (ch in queryDigits) {
            val letters = digitToLettersMap[ch]
            if (letters != null) {
                patternBuilder.append("[$letters${letters.uppercase()}]")
            } else {
                patternBuilder.append(ch)
            }
        }

        val regexPattern = patternBuilder.toString()
        val regex = Regex("(?i)(?:^|\\s)$regexPattern|.*$regexPattern.*")
        return regex.containsMatchIn(name)
    }

    /**
     * Checks if a phone number matches a numeric query sequence after stripping non-numeric characters.
     */
    fun matchesNumber(phoneNumber: String, queryDigits: String): Boolean {
        if (queryDigits.isEmpty()) return true
        val sanitizedNumber = phoneNumber.replace("[^0-9+]".toRegex(), "")
        return sanitizedNumber.contains(queryDigits)
    }
}