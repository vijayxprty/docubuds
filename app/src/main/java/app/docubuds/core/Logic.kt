package app.docubuds.core

import java.time.LocalDate

/** 1-minute inactivity lock (spec §3), including time spent outside the app. */
object LockLogic {
    const val LOCK_TIMEOUT_MS = 60_000L
    fun shouldLock(lastActiveMs: Long, nowMs: Long): Boolean =
        nowMs - lastActiveMs >= LOCK_TIMEOUT_MS
}

/** Expiry reminders at 30 / 7 / 1 days before expiry (spec §8), local only. */
object ReminderDates {
    val OFFSETS_DAYS = listOf(30L, 7L, 1L)

    /** Offsets that are due exactly today (expiry - offset == today). */
    fun dueOffsets(expiryEpochDay: Long, todayEpochDay: Long): List<Long> =
        OFFSETS_DAYS.filter { todayEpochDay == expiryEpochDay - it }

    fun notificationKey(recordId: String, offsetDays: Long): String = "$recordId:$offsetDays"
}

/** Trash: 30-day retention, then gone for good; manual empty allowed (spec §7). */
object TrashLogic {
    const val RETENTION_DAYS = 30L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Returns (kept, purged). */
    fun purge(trash: List<TrashEntry>, nowMs: Long): Pair<List<TrashEntry>, List<TrashEntry>> {
        val cutoff = nowMs - RETENTION_DAYS * DAY_MS
        return trash.partition { it.deletedAt > cutoff }
    }
}

/** Donate values — exact, from the Remibuds donate brief. Do not change. */
object Donate {
    const val UPI_ID = "jaynishad2010@okhdfcbank"
    const val PAYEE_NAME = "Vijay Kumar"
    const val UPI_LINK = "upi://pay?pa=jaynishad2010@okhdfcbank&pn=Vijay%20Kumar&aid=uGICAgIDQqveaLw"
    const val INSTAGRAM_HANDLE = "@momentswithvms"
    const val INSTAGRAM_URL = "https://www.instagram.com/momentswithvms/"
    const val CREDIT_LINE = "Made with love ♥ by Vijay Mamta Sahani (@momentswithvms)"
    const val CHOOSER_TITLE = "Choose an app to pay with"
    const val COPY_TOAST = "UPI ID copied."

    /** The pay link must never carry an amount parameter. */
    fun hasAmountParameter(link: String): Boolean =
        link.split("?", "&").any { it.startsWith("am=") }

    fun isValidUpiLink(link: String): Boolean =
        link == UPI_LINK && !hasAmountParameter(link)
}

/**
 * Finds dates in OCR text and suggests the expiry date. Dates are assumed
 * day-first (India). Prefers a date near an expiry keyword ("valid", "expiry",
 * "expires", "thru", "till"); otherwise the latest future date.
 * The suggestion is always shown to the user for confirmation — never saved blind.
 */
object ExpiryParser {
    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )
    private val KEYWORDS = listOf("valid", "expir", "thru", "through", "till", "until", "due")

    private val numericDmy = Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})""")
    private val numericYmd = Regex("""(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})""")
    private val monthName = Regex(
        """(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s*,?\s*(\d{4})""",
        RegexOption.IGNORE_CASE
    )

    fun findDates(text: String): List<LocalDate> {
        val found = mutableListOf<LocalDate>()
        numericDmy.findAll(text).forEach { m ->
            runCatching {
                found += LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
            }
        }
        numericYmd.findAll(text).forEach { m ->
            runCatching {
                found += LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }
        }
        monthName.findAll(text).forEach { m ->
            val month = MONTHS[m.groupValues[2].lowercase().take(3)] ?: return@forEach
            runCatching {
                found += LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
            }
        }
        return found.distinct().sorted()
    }

    fun suggestExpiry(text: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val dates = findDates(text).filter { !it.isBefore(today) }
        if (dates.isEmpty()) return null
        val lower = text.lowercase()
        for (kw in KEYWORDS) {
            val idx = lower.indexOf(kw)
            if (idx >= 0) {
                val window = lower.substring(idx, minOf(lower.length, idx + 80))
                val inWindow = findDates(window).filter { !it.isBefore(today) }
                if (inWindow.isNotEmpty()) return inWindow.maxOrNull()
            }
        }
        return dates.maxOrNull()
    }
}
