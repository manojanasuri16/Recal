package app.recal.data

/**
 * Offline phone-number matching.
 *
 * The same person shows up as "+91 98765 43210", "098765 43210" and "9876543210"
 * depending on who placed the call and how the network reports it. We do not ship a
 * libphonenumber-sized dependency; instead numbers are reduced to their digits with
 * any trunk zeros removed, and two numbers are the same when one is a suffix of the
 * other (with at least [MIN_MATCH] digits in common).
 */
object PhoneNumbers {
    const val MIN_MATCH = 7

    /** Digits only, leading zeros dropped. Empty when the number is hidden/unknown. */
    fun canon(raw: String?): String {
        if (raw == null) return ""
        // "555 0100,123" / "555 0100;123" carry an extension or pause digits after the number itself.
        val cut = raw.indexOfFirst { it == ',' || it == ';' }
        val body = if (cut >= 0) raw.substring(0, cut) else raw
        val digits = StringBuilder(body.length)
        for (c in body) if (c in '0'..'9') digits.append(c)
        return digits.trimStart('0').toString()
    }

    /** Index key: the last [MIN_MATCH] digits (or everything for short codes). */
    fun key(raw: String?): String = canon(raw).takeLast(MIN_MATCH)

    fun same(a: String?, b: String?): Boolean {
        val x = canon(a)
        val y = canon(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x.length < MIN_MATCH || y.length < MIN_MATCH) return x == y
        return if (x.length >= y.length) x.endsWith(y) else y.endsWith(x)
    }

    /** Human-friendly grouping for display, e.g. "(555) 123-4567". Falls back to the raw input. */
    fun pretty(raw: String): String = try {
        val country = java.util.Locale.getDefault().country.ifEmpty { "US" }
        android.telephony.PhoneNumberUtils.formatNumber(raw, country) ?: raw
    } catch (_: Throwable) {
        raw
    }
}
