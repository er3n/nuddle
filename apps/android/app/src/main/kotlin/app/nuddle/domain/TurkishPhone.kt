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
