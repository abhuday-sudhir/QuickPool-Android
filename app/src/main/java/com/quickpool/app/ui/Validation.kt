package com.quickpool.app.ui

/**
 * Pure input rules, kept out of the composables so they can be unit-tested on the JVM
 * without an emulator.
 */
object Validation {

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun isEmailValid(email: String): Boolean = EMAIL.matches(email.trim())

    fun isNameValid(name: String): Boolean = name.trim().length >= 2

    /** Digits only, and exactly as many as the chosen country expects. */
    fun isNationalNumberValid(number: String, expectedDigits: Int): Boolean =
        number.length == expectedDigits && number.all { it.isDigit() }

    fun toE164(dialCode: String, nationalNumber: String): String = dialCode + nationalNumber

    /** Mirrors the server: uppercase, strip anything that is not A-Z or 0-9. */
    fun normalisePlate(raw: String): String =
        raw.uppercase().replace(Regex("[^A-Z0-9]"), "")

    fun isPlateValid(raw: String): Boolean = normalisePlate(raw).length in 4..16

    fun isVehicleValid(make: String, model: String, color: String, plate: String): Boolean =
        make.isNotBlank() && model.isNotBlank() && color.isNotBlank() && isPlateValid(plate)

    /** Stars must be a real 1-5 choice; 0 means "not picked yet". */
    fun isRatingValid(stars: Int): Boolean = stars in 1..5
}
