package com.quickpool.app

import com.quickpool.app.ui.Validation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    @Test
    fun `accepts ordinary email addresses`() {
        assertTrue(Validation.isEmailValid("abhuday@example.com"))
        assertTrue(Validation.isEmailValid("a.b+tag@sub.domain.co.in"))
        assertTrue(Validation.isEmailValid("  spaced@example.com  "))  // trimmed
    }

    @Test
    fun `rejects malformed email addresses`() {
        assertFalse(Validation.isEmailValid(""))
        assertFalse(Validation.isEmailValid("not-an-email"))
        assertFalse(Validation.isEmailValid("missing@domain"))
        assertFalse(Validation.isEmailValid("@example.com"))
        assertFalse(Validation.isEmailValid("two spaces@example.com"))
    }

    @Test
    fun `name needs at least two visible characters`() {
        assertTrue(Validation.isNameValid("Jo"))
        assertTrue(Validation.isNameValid("  Abhuday  "))
        assertFalse(Validation.isNameValid(""))
        assertFalse(Validation.isNameValid("A"))
        assertFalse(Validation.isNameValid("   "))
    }

    @Test
    fun `phone number must match the country's digit count`() {
        assertTrue(Validation.isNationalNumberValid("9267020669", 10))
        assertFalse(Validation.isNationalNumberValid("926702066", 10))   // too short
        assertFalse(Validation.isNationalNumberValid("92670206691", 10)) // too long
        assertFalse(Validation.isNationalNumberValid("92670206a9", 10))  // not digits
        assertTrue(Validation.isNationalNumberValid("81234567", 8))      // Singapore
    }

    @Test
    fun `builds E164 without the user typing a country code`() {
        assertEquals("+919267020669", Validation.toE164("+91", "9267020669"))
        assertEquals("+6581234567", Validation.toE164("+65", "81234567"))
    }

    @Test
    fun `plate normalisation matches the server rule`() {
        assertEquals("DL3CAB1234", Validation.normalisePlate("dl 3c ab-1234"))
        assertEquals("UP80AB1111", Validation.normalisePlate("UP80AB1111"))
        assertEquals("DL3CAB1234", Validation.normalisePlate("DL-3C-AB-1234"))
    }

    @Test
    fun `plate must survive normalisation with enough characters`() {
        assertTrue(Validation.isPlateValid("DL3CAB1234"))
        assertTrue(Validation.isPlateValid("dl 3c ab 1234"))
        assertFalse(Validation.isPlateValid("!!"))
        assertFalse(Validation.isPlateValid("---"))
        assertFalse(Validation.isPlateValid("AB1"))
    }

    @Test
    fun `vehicle needs every field`() {
        assertTrue(Validation.isVehicleValid("Maruti", "Swift", "White", "DL3CAB1234"))
        assertFalse(Validation.isVehicleValid("", "Swift", "White", "DL3CAB1234"))
        assertFalse(Validation.isVehicleValid("Maruti", "", "White", "DL3CAB1234"))
        assertFalse(Validation.isVehicleValid("Maruti", "Swift", "", "DL3CAB1234"))
        assertFalse(Validation.isVehicleValid("Maruti", "Swift", "White", "!"))
    }

    @Test
    fun `rating must be a real star choice`() {
        (1..5).forEach { assertTrue(Validation.isRatingValid(it)) }
        assertFalse(Validation.isRatingValid(0))   // nothing picked yet
        assertFalse(Validation.isRatingValid(6))
        assertFalse(Validation.isRatingValid(-1))
    }
}
