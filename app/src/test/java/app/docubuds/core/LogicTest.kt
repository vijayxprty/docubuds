package app.docubuds.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogicTest {

    // ---- Inactivity lock (1 minute) ----

    @Test
    fun `no lock before 60 seconds`() {
        assertFalse(LockLogic.shouldLock(1_000L, 1_000L + 59_999))
    }

    @Test
    fun `lock at exactly 60 seconds`() {
        assertTrue(LockLogic.shouldLock(1_000L, 1_000L + 60_000))
    }

    @Test
    fun `lock after time outside the app`() {
        assertTrue(LockLogic.shouldLock(0L, 5 * 60_000L))
    }

    // ---- Reminder dates (30 / 7 / 1) ----

    @Test
    fun `30 day reminder due exactly 30 days before expiry`() {
        val expiry = LocalDate.of(2027, 1, 31).toEpochDay()
        val today = LocalDate.of(2027, 1, 1).toEpochDay()
        assertEquals(listOf(30L), ReminderDates.dueOffsets(expiry, today))
    }

    @Test
    fun `7 day reminder due exactly 7 days before expiry`() {
        val expiry = LocalDate.of(2027, 1, 31).toEpochDay()
        val today = LocalDate.of(2027, 1, 24).toEpochDay()
        assertEquals(listOf(7L), ReminderDates.dueOffsets(expiry, today))
    }

    @Test
    fun `1 day reminder due the day before expiry`() {
        val expiry = LocalDate.of(2027, 1, 31).toEpochDay()
        val today = LocalDate.of(2027, 1, 30).toEpochDay()
        assertEquals(listOf(1L), ReminderDates.dueOffsets(expiry, today))
    }

    @Test
    fun `no reminder on other days`() {
        val expiry = LocalDate.of(2027, 1, 31).toEpochDay()
        val today = LocalDate.of(2027, 1, 15).toEpochDay()
        assertTrue(ReminderDates.dueOffsets(expiry, today).isEmpty())
    }

    @Test
    fun `notification keys are unique per record and offset`() {
        assertEquals("abc:30", ReminderDates.notificationKey("abc", 30L))
        assertFalse(
            ReminderDates.notificationKey("abc", 30L) == ReminderDates.notificationKey("abc", 7L)
        )
    }

    // ---- Trash (30 days) ----

    private fun trashEntry(daysAgo: Long, now: Long) =
        TrashEntry(
            VaultRecord(type = RecordType.NOTE, spaceId = "s", title = "t"),
            deletedAt = now - daysAgo * 24 * 60 * 60 * 1000
        )

    @Test
    fun `trash younger than 30 days is kept`() {
        val now = System.currentTimeMillis()
        val (kept, purged) = TrashLogic.purge(listOf(trashEntry(29, now)), now)
        assertEquals(1, kept.size)
        assertTrue(purged.isEmpty())
    }

    @Test
    fun `trash older than 30 days is purged`() {
        val now = System.currentTimeMillis()
        val (kept, purged) = TrashLogic.purge(listOf(trashEntry(31, now)), now)
        assertTrue(kept.isEmpty())
        assertEquals(1, purged.size)
    }

    @Test
    fun `purge is selective`() {
        val now = System.currentTimeMillis()
        val (kept, purged) = TrashLogic.purge(
            listOf(trashEntry(5, now), trashEntry(40, now), trashEntry(29, now)), now
        )
        assertEquals(2, kept.size)
        assertEquals(1, purged.size)
    }

    // ---- Donate UPI link (exact) ----

    @Test
    fun `upi link is exactly the specified link with no amount`() {
        assertEquals(
            "upi://pay?pa=jaynishad2010@okhdfcbank&pn=Vijay%20Kumar&aid=uGICAgIDQqveaLw",
            Donate.UPI_LINK
        )
        assertTrue(Donate.isValidUpiLink(Donate.UPI_LINK))
        assertFalse(Donate.hasAmountParameter(Donate.UPI_LINK))
    }

    @Test
    fun `a link with an amount is rejected`() {
        assertFalse(Donate.isValidUpiLink(Donate.UPI_LINK + "&am=100"))
        assertTrue(Donate.hasAmountParameter(Donate.UPI_LINK + "&am=100"))
    }

    @Test
    fun `credit line and instagram link are exact`() {
        assertEquals("Made with love ♥ by Vijay Mamta Sahani (@momentswithvms)", Donate.CREDIT_LINE)
        assertEquals("https://www.instagram.com/momentswithvms/", Donate.INSTAGRAM_URL)
        assertEquals("Choose an app to pay with", Donate.CHOOSER_TITLE)
        assertEquals("UPI ID copied.", Donate.COPY_TOAST)
    }

    // ---- Expiry parser ----

    @Test
    fun `finds dd-mm-yyyy dates`() {
        val dates = ExpiryParser.findDates("Valid thru 12-03-2028")
        assertEquals(listOf(LocalDate.of(2028, 3, 12)), dates)
    }

    @Test
    fun `finds yyyy-mm-dd dates`() {
        val dates = ExpiryParser.findDates("Expiry: 2028-03-12")
        assertEquals(listOf(LocalDate.of(2028, 3, 12)), dates)
    }

    @Test
    fun `finds month-name dates`() {
        val dates = ExpiryParser.findDates("Valid until 12 March 2028")
        assertEquals(listOf(LocalDate.of(2028, 3, 12)), dates)
    }

    @Test
    fun `suggests the date near the expiry keyword`() {
        val text = "Issued 01-01-2020. Valid thru 12-03-2028. DoB 05-06-1990."
        val suggested = ExpiryParser.suggestExpiry(text, LocalDate.of(2026, 11, 15))
        assertEquals(LocalDate.of(2028, 3, 12), suggested)
    }

    @Test
    fun `no future date means no suggestion`() {
        assertNull(ExpiryParser.suggestExpiry("Valid thru 12-03-2020", LocalDate.of(2026, 11, 15)))
    }
}
