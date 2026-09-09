package com.toco.ai.util

/**
 * Pulls a dialable number out of free text, and normalises it for WhatsApp
 * (which wants digits only, country code included, no + or spaces).
 */
object PhoneNumbers {

    private val NUMBER = Regex("""\+?[0-9][0-9\s\-()]{6,}[0-9]""")

    fun extract(text: String): String? =
        NUMBER.find(text)?.value?.trim()

    fun toDialable(raw: String): String =
        raw.filter { it.isDigit() || it == '+' }

    /** WhatsApp wa.me format: digits only. Adds [defaultCountryCode] to local numbers. */
    fun toWhatsApp(raw: String, defaultCountryCode: String = "92"): String {
        var digits = raw.filter { it.isDigit() }
        if (raw.trim().startsWith("+")) return digits
        if (digits.startsWith("00")) return digits.drop(2)
        if (digits.startsWith("0")) digits = defaultCountryCode + digits.drop(1)
        return digits
    }
}
