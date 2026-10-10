package org.sableos.calculator

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.sableos.convert.ConverterScreen
import org.sableos.design.SableActionButton
import org.sableos.design.SableGlobalTheme
import org.sableos.design.SableHeroHeader
import org.sableos.design.SableScrollableScreen
import org.sableos.design.SableSpacing
import org.sableos.design.SableTile
import org.sableos.design.SableValuePanel

private enum class CalculatorMode(
    val label: String,
) {
    Regular("Basic"),
    Scientific("Scientific"),
    Converter("Convert"),
}

private data class ScientificChoice(
    val code: Int,
    val label: String,
)

private val operationLabels = listOf("+", "−", "×", "÷")

private val scientificChoices =
    listOf(
        ScientificChoice(0, "√x"),
        ScientificChoice(1, "x²"),
        ScientificChoice(2, "1/x"),
        ScientificChoice(3, "sin"),
        ScientificChoice(4, "cos"),
        ScientificChoice(5, "tan"),
        ScientificChoice(6, "ln"),
        ScientificChoice(7, "log₁₀"),
    )

/** The keypad currently on screen, if any; hardware keys go to it (see [CalculatorKeys]). */
private object HardwareKeypad {
    var handler: ((CalcKey) -> Unit)? = null
}

class MainActivity : ComponentActivity() {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = HardwareKeypad.handler
        if (handler != null) {
            val key =
                CalculatorKeys.resolve(
                    event.keyCode,
                    event.unicodeChar.printable(),
                    event.getUnicodeChar(KeyEvent.META_ALT_ON).printable(),
                    event.isCtrlPressed || event.isMetaPressed,
                )
            if (key != null) {
                if (event.action == KeyEvent.ACTION_DOWN) handler(key)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nativeQualification = NativeBridge.selfTest()
        check(nativeQualification.startsWith("PASS:")) { nativeQualification }

        setContent {
            SableGlobalTheme(window = window) {
                CalculatorApp()
            }
        }
    }
}

@Composable
private fun CalculatorApp() {
    var mode by remember { mutableStateOf(CalculatorMode.Regular) }

    SableScrollableScreen {
        SableHeroHeader(
            eyebrow = "Sable",
            title = "calculator",
            subtitle = "Basic, scientific and conversion tools in one app.",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            CalculatorMode.entries.forEach { choice ->
                SableTile(
                    title = choice.label,
                    active = mode == choice,
                    modifier = Modifier.weight(1f),
                    onClick = { mode = choice },
                )
            }
        }

        when (mode) {
            CalculatorMode.Regular -> {
                CalculatorPad(
                    scientific = false,
                )
            }

            CalculatorMode.Scientific -> {
                CalculatorPad(
                    scientific = true,
                )
            }

            CalculatorMode.Converter -> {
                ConverterScreen()
            }
        }
    }
}

@Composable
private fun CalculatorPad(scientific: Boolean) {
    var display by remember { mutableStateOf("0") }
    var leftOperand by remember { mutableStateOf<String?>(null) }
    var operation by remember { mutableStateOf<Int?>(null) }
    var replaceInput by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(
            if (scientific) {
                "Scientific functions use degrees."
            } else {
                "Exact decimal arithmetic runs in the Rust domain."
            },
        )
    }

    fun clear() {
        display = "0"
        leftOperand = null
        operation = null
        replaceInput = false
        status =
            if (scientific) {
                "Scientific functions use degrees."
            } else {
                "Exact decimal arithmetic runs in the Rust domain."
            }
    }

    fun enterDigit(digit: String) {
        display =
            when {
                replaceInput -> digit
                display == "0" -> digit
                display == "-0" -> "-$digit"
                else -> display + digit
            }
        replaceInput = false
    }

    fun enterDecimal() {
        if (replaceInput) {
            display = "0."
            replaceInput = false
        } else if (!display.contains('.')) {
            display += "."
        }
    }

    fun backspace() {
        if (!replaceInput) {
            display =
                display
                    .dropLast(1)
                    .ifEmpty { "0" }
            if (display == "-") {
                display = "0"
            }
        }
    }

    fun applyBinaryNow(
        left: String,
        op: Int,
        right: String,
    ): String? {
        val response =
            renderResult(
                NativeBridge.applyBinary(
                    left,
                    op,
                    right,
                ),
            )
        return if (response.error == null) {
            status = "Exact result"
            response.value
        } else {
            status = response.error
            null
        }
    }

    fun chooseOperation(nextOperation: Int) {
        val priorLeft = leftOperand
        val priorOperation = operation

        if (
            priorLeft != null &&
            priorOperation != null &&
            !replaceInput
        ) {
            val result =
                applyBinaryNow(
                    priorLeft,
                    priorOperation,
                    display,
                )
            if (result != null) {
                display = result
                leftOperand = result
            }
        } else {
            leftOperand = display
        }

        operation = nextOperation
        replaceInput = true
    }

    fun equals() {
        val left = leftOperand
        val op = operation

        if (left == null || op == null) {
            status = "Choose an operation first."
            return
        }

        val result =
            applyBinaryNow(
                left,
                op,
                display,
            )
        if (result != null) {
            display = result
            leftOperand = null
            operation = null
            replaceInput = true
        }
    }

    fun applyScientific(choice: ScientificChoice) {
        val response =
            renderResult(
                NativeBridge.applyScientific(
                    display,
                    choice.code,
                ),
            )

        if (response.error == null) {
            display = response.value
            leftOperand = null
            operation = null
            replaceInput = true
            status = "${choice.label} · degrees for trigonometry"
        } else {
            status = response.error
        }
    }

    fun onHardwareKey(key: CalcKey) {
        when (key) {
            is CalcKey.Digit -> enterDigit(key.digit)
            is CalcKey.Operation -> chooseOperation(key.code)
            CalcKey.Decimal -> enterDecimal()
            CalcKey.Equals -> equals()
            CalcKey.Backspace -> backspace()
            CalcKey.Clear -> clear()
        }
    }

    DisposableEffect(scientific) {
        val handler: (CalcKey) -> Unit = ::onHardwareKey
        HardwareKeypad.handler = handler
        onDispose {
            if (HardwareKeypad.handler === handler) HardwareKeypad.handler = null
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
    ) {
        SableValuePanel(
            label =
                operation?.let { index ->
                    "${leftOperand.orEmpty()} ${operationLabels[index]}"
                } ?: if (scientific) {
                    "scientific"
                } else {
                    "display"
                },
            value = display,
            supportingText = status,
        )

        if (scientific) {
            scientificChoices.chunked(4).forEach { rowChoices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
                ) {
                    rowChoices.forEach { choice ->
                        SableTile(
                            title = choice.label,
                            modifier = Modifier.weight(1f),
                            onClick = { applyScientific(choice) },
                        )
                    }
                }
            }
        }

        Keypad(
            onDigit = ::enterDigit,
            onDecimal = ::enterDecimal,
            onOperation = ::chooseOperation,
            onEquals = ::equals,
            onClear = ::clear,
            onToggleSign = {
                display =
                    if (display.startsWith("-")) {
                        display.removePrefix("-")
                    } else if (display != "0") {
                        "-$display"
                    } else {
                        display
                    }
            },
            onBackspace = ::backspace,
        )

        Text(
            text =
                if (scientific) {
                    "Scientific operations and the core arithmetic path execute through the native calculator domain."
                } else {
                    "Results are not silently rounded into floating-point values."
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onDecimal: () -> Unit,
    onOperation: (Int) -> Unit,
    onEquals: () -> Unit,
    onClear: () -> Unit,
    onToggleSign: () -> Unit,
    onBackspace: () -> Unit,
) {
    val rows =
        listOf(
            listOf("C", "±", "⌫", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "="),
        )

    rows.forEachIndexed { rowIndex, labels ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            labels.forEach { label ->
                val modifier =
                    if (rowIndex == rows.lastIndex && label == "0") {
                        Modifier.weight(2f)
                    } else {
                        Modifier.weight(1f)
                    }

                CalculatorKey(
                    label = label,
                    accent =
                        label == "=" ||
                            label in operationLabels,
                    modifier = modifier,
                    onClick = {
                        when (label) {
                            "C" -> onClear()
                            "±" -> onToggleSign()
                            "⌫" -> onBackspace()
                            "." -> onDecimal()
                            "=" -> onEquals()
                            "+" -> onOperation(0)
                            "−" -> onOperation(1)
                            "×" -> onOperation(2)
                            "÷" -> onOperation(3)
                            else -> onDigit(label)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CalculatorKey(
    label: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val utility = label == "C" || label == "±" || label == "⌫"
    val container =
        when {
            accent -> MaterialTheme.colorScheme.primary
            utility -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        }
    val content =
        if (accent) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 58.dp),
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border =
            if (accent) {
                null
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
        tonalElevation = 0.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

/** getUnicodeChar result as a character; 0 (none) and dead keys (negative, combining accent) give null. */
private fun Int.printable(): Char? = takeIf { it > 0 }?.toChar()

private data class RenderedResult(
    val value: String,
    val error: String?,
)

private fun renderResult(protocol: String): RenderedResult =
    when {
        protocol.startsWith("OK:") -> {
            RenderedResult(
                value = protocol.removePrefix("OK:"),
                error = null,
            )
        }

        protocol.startsWith("ERR:") -> {
            val error =
                protocol
                    .removePrefix("ERR:")
                    .replace('_', ' ')
            RenderedResult(
                value = "0",
                error = error,
            )
        }

        else -> {
            RenderedResult(
                value = "0",
                error = "Native bridge failure",
            )
        }
    }
