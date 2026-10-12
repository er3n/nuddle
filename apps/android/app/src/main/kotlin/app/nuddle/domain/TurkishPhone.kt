package app.nuddle.domain

/**
 * Turkish mobile numbers, the only country in the MVP (screen 0c shows a fixed "TR +90").
 * The user types the ten national digits; the API takes E.164.
 */
object TurkishPhone {
    const val COUNTRY_CODE = "+90"
    const val NATIONAL_LENGTH = 10
    private const val MOBILE_PREFIX = '5'
    private val GROUPS = listOf(3, 3, 2, 2)

    /** Keeps digits only, drops a leading trunk 0 ("0532...") and caps the length. */
    fun sanitize(input: String): String = input.filter(Char::isDigit).removePrefix("0").take(NATIONAL_LENGTH)

    fun isValidMobile(national: String): Boolean = national.length == NATIONAL_LENGTH && national.first() == MOBILE_PREFIX

    fun toE164(national: String): String? = if (isValidMobile(national)) COUNTRY_CODE + national else null

    /**
     * A number as a contact stores it ("+90 532 123 45 67", "0090...", "0532 ...", "532-123-4567") to E.164, or null
     * when it is not a Turkish mobile number (1e).
     */
    fun fromContact(raw: String): String? {
        val digits = raw.filter(Char::isDigit)
        val national =
            when {
                digits.length == NATIONAL_LENGTH + 4 && digits.startsWith("0090") -> digits.drop(4)
                digits.length == NATIONAL_LENGTH + 2 && digits.startsWith("90") -> digits.drop(2)
                digits.length == NATIONAL_LENGTH + 1 && digits.startsWith("0") -> digits.drop(1)
                else -> digits
            }
        return toE164(national)
    }

    /** "5321234567" -> "532 123 45 67"; partial input is grouped as far as it goes. */
    fun group(national: String): String {
        val parts = mutableListOf<String>()
        var rest = national
        for (size in GROUPS) {
            if (rest.isEmpty()) break
            parts += rest.take(size)
            rest = rest.drop(size)
        }
        return parts.joinToString(" ")
    }

    /** "+905321234567" -> "+90 532 123 45 67" (screens 0d and 6c). */
    fun display(e164: String): String =
        if (e164.startsWith(COUNTRY_CODE)) "$COUNTRY_CODE ${group(e164.removePrefix(COUNTRY_CODE))}" else e164
}
