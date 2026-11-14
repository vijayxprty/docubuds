package app.docubuds.core

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    private val gson = Gson()

    @Test
    fun `vault data survives json round trip with leading zeros intact`() {
        val record = VaultRecord(
            type = RecordType.BANK_ACCOUNT,
            spaceId = "s1",
            title = "SBI Savings",
            fields = mapOf("account number" to "0012345678", "ifsc" to "SBIN0001234"),
            expiryEpochDay = null
        )
        val data = VaultData(
            displayName = "Vijay",
            spaces = listOf(Space(name = "Property 1")),
            records = listOf(record),
            trash = emptyList()
        )
        val restored = gson.fromJson(gson.toJson(data), VaultData::class.java)
        assertEquals("Vijay", restored.displayName)
        assertEquals("0012345678", restored.records[0].fields["account number"])
        assertEquals("SBIN0001234", restored.records[0].fields["ifsc"])
        assertEquals("Property 1", restored.spaces[0].name)
    }

    @Test
    fun `suggested spaces are seeded as seven`() {
        assertEquals(7, VaultData.SUGGESTED_SPACES.size)
        assertTrue(VaultData.SUGGESTED_SPACES.contains("Work"))
        assertTrue(VaultData.SUGGESTED_SPACES.contains("Resumes"))
    }

    @Test
    fun `record ids are unique`() {
        val a = VaultRecord(type = RecordType.NOTE, spaceId = "s", title = "a")
        val b = VaultRecord(type = RecordType.NOTE, spaceId = "s", title = "b")
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun `card record keeps pin and cvv fields`() {
        val record = VaultRecord(
            type = RecordType.CARD,
            spaceId = "s",
            title = "HDFC",
            fields = mapOf("card number" to "4111111111111111", "pin" to "1234", "cvv" to "123")
        )
        val restored = gson.fromJson(gson.toJson(record), VaultRecord::class.java)
        assertEquals("1234", restored.fields["pin"])
        assertEquals("123", restored.fields["cvv"])
    }
}
