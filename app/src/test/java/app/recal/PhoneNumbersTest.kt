package app.recal

import app.recal.data.PhoneNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {
    @Test fun international_and_local_forms_match() {
        assertTrue(PhoneNumbers.same("+91 98765 43210", "9876543210"))
        assertTrue(PhoneNumbers.same("+91 98765 43210", "098765 43210"))
        assertTrue(PhoneNumbers.same("(415) 555-2671", "+1 415 555 2671"))
        assertTrue(PhoneNumbers.same("+45 12 34 56 78", "12345678"))
    }

    @Test fun different_numbers_do_not_match() {
        assertFalse(PhoneNumbers.same("9876543210", "9876543211"))
        assertFalse(PhoneNumbers.same("+91 98765 43210", "+91 88765 43210"))
    }

    @Test fun short_codes_need_exact_match() {
        assertTrue(PhoneNumbers.same("121", "121"))
        assertFalse(PhoneNumbers.same("121", "9876543121"))
        assertFalse(PhoneNumbers.same("5556", "15555215556"))
    }

    @Test fun hidden_numbers_never_match() {
        assertFalse(PhoneNumbers.same(null, null))
        assertFalse(PhoneNumbers.same("", "9876543210"))
        assertFalse(PhoneNumbers.same("Private number", "Unknown"))
    }

    @Test fun canon_and_key() {
        assertEquals("919876543210", PhoneNumbers.canon("+91 (98765) 43-210"))
        assertEquals("9876543210", PhoneNumbers.canon("09876543210"))
        assertEquals("6543210", PhoneNumbers.key("+91 98765 43210"))
        assertEquals(PhoneNumbers.key("+91 98765 43210"), PhoneNumbers.key("098765 43210"))
        assertEquals("", PhoneNumbers.canon(null))
    }

    @Test fun extensions_and_pauses_are_ignored() {
        assertTrue(PhoneNumbers.same("555 010 0199,123", "5550100199"))
        assertEquals("5550100199", PhoneNumbers.canon("555 010 0199;45"))
    }
}
