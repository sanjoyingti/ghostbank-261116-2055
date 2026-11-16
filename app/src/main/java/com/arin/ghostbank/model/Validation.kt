package com.arin.ghostbank.model

object Validation {

    private val ifscRegex = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")
    private val amountRegex = Regex("^\\d{1,7}(\\.\\d{1,2})?$")
    private val hostRegex = Regex("^[A-Za-z0-9.\\-]+$")

    fun accountNumber(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return "Account number is required"
        if (!value.all { it.isDigit() }) return "Digits only"
        if (value.length !in 9..18) return "Use 9 to 18 digits"
        return null
    }

    fun ifsc(raw: String): String? {
        val value = raw.trim().uppercase()
        if (value.isEmpty()) return "IFSC is required"
        if (!ifscRegex.matches(value)) return "Format like HDFC0001234"
        return null
    }

    fun amountRupees(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return "Amount is required"
        if (!amountRegex.matches(value)) return "Up to 2 decimals"
        val paise = toPaise(value)
        if (paise <= 0L) return "Amount must be more than zero"
        return null
    }

    fun toPaise(raw: String): Long {
        val value = raw.trim().ifEmpty { "0" }
        val parts = value.split(".")
        val rupees = parts[0].toLongOrNull() ?: 0L
        val paise = when {
            parts.size < 2 -> 0L
            parts[1].isEmpty() -> 0L
            parts[1].length == 1 -> (parts[1] + "0").toLongOrNull() ?: 0L
            else -> parts[1].take(2).toLongOrNull() ?: 0L
        }
        return rupees * 100L + paise
    }

    fun proxyHost(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return "Host is required"
        if (!hostRegex.matches(value)) return "Letters, digits, dots and dashes only"
        return null
    }

    fun proxyPort(raw: String): String? {
        val port = raw.trim().toIntOrNull() ?: return "Port must be a number"
        if (port !in 1..65535) return "Port 1 to 65535"
        return null
    }

    fun formatPaise(paise: Long): String {
        val rupees = paise / 100L
        val rest = (paise % 100L).toInt()
        return "₹$rupees.${rest.toString().padStart(2, '0')}"
    }
}
