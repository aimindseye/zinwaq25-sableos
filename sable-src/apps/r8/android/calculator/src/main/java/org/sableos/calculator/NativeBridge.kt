package org.sableos.calculator

internal object NativeBridge {
    init {
        System.loadLibrary("sable_calculator_jni")
    }

    external fun selfTest(): String

    external fun applyBinary(
        left: String,
        operation: Int,
        right: String,
    ): String

    external fun applyScientific(
        input: String,
        operation: Int,
    ): String
}
