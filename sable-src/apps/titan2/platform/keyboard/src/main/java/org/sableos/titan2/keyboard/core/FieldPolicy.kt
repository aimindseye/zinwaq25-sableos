package org.sableos.titan2.keyboard.core

/**
 * Critical text entry policy, kept in the pure core so it is unit-testable on a JVM.
 *
 * The constants mirror android.text.InputType. They are part of the stable public Android ABI; the Android glue
 * passes `EditorInfo.inputType` straight in. A JVM test pins the numeric values.
 */
object InputTypeBits {
    const val TYPE_NULL = 0
    const val MASK_CLASS = 0x0000000f
    const val MASK_VARIATION = 0x00000ff0
    const val CLASS_TEXT = 0x00000001
    const val CLASS_NUMBER = 0x00000002
    const val CLASS_PHONE = 0x00000003
    const val TEXT_VARIATION_PASSWORD = 0x00000080
    const val TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
    const val TEXT_VARIATION_WEB_PASSWORD = 0x000000e0
    const val NUMBER_VARIATION_PASSWORD = 0x00000010
    const val TEXT_FLAG_CAP_SENTENCES = 0x00004000
}

object FieldPolicy {
    private val TEXT_PASSWORD_VARIATIONS = setOf(
        InputTypeBits.TEXT_VARIATION_PASSWORD,
        InputTypeBits.TEXT_VARIATION_VISIBLE_PASSWORD,
        InputTypeBits.TEXT_VARIATION_WEB_PASSWORD
    )

    /** Fields where the on-screen keyboard is offered by default because a PIN/code must be typeable. */
    val DIGIT_ENTRY_CLASSES = setOf(InputClass.Number, InputClass.Phone, InputClass.NumberPassword)

    /** Fields in which letter keys must never become composing text (no candidates, no IME-held text). */
    val SECRET_CLASSES = setOf(InputClass.Password, InputClass.NumberPassword)

    fun classify(inputType: Int?): InputClass {
        if (inputType == null || inputType == InputTypeBits.TYPE_NULL) return InputClass.None
        val variation = inputType and InputTypeBits.MASK_VARIATION
        return when (inputType and InputTypeBits.MASK_CLASS) {
            InputTypeBits.CLASS_NUMBER ->
                if (variation ==
                    InputTypeBits.NUMBER_VARIATION_PASSWORD
                ) {
                    InputClass.NumberPassword
                } else {
                    InputClass.Number
                }

            InputTypeBits.CLASS_PHONE -> InputClass.Phone

            InputTypeBits.CLASS_TEXT ->
                if (variation in TEXT_PASSWORD_VARIATIONS) InputClass.Password else InputClass.Text

            else -> InputClass.Text
        }
    }

    /** True when the field class is a digit-entry class and the user keeps the numeric fallback enabled. */
    fun numericFallback(cls: InputClass, softForNumeric: Boolean): Boolean =
        softForNumeric && cls in DIGIT_ENTRY_CLASSES

    /** Whether the on-screen keyboard is shown: forced, toggled, or the numeric fallback (never merely on focus). */
    fun softKeyboardWanted(
        cls: InputClass,
        forceSoft: Boolean,
        softToggled: Boolean,
        softForNumeric: Boolean
    ): Boolean = forceSoft || softToggled || numericFallback(cls, softForNumeric)

    /** Sentence-start capitalisation is requested only by text-class fields with the cap-sentences flag. */
    fun wantsSentenceCaps(inputType: Int?): Boolean = inputType != null &&
        inputType and InputTypeBits.MASK_CLASS == InputTypeBits.CLASS_TEXT &&
        inputType and InputTypeBits.TEXT_FLAG_CAP_SENTENCES != 0
}
