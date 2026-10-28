package org.sableos.calculator

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.sableos.design.isSableShortWindow
import org.sableos.design.sableScreenGap
import org.sableos.design.sableScreenPadding

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
    // Overrides the public Activity method; androidx re-declares it as restricted, which lint misreports.
    @SuppressLint("RestrictedApi")
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

    if (isSableShortWindow()) {
        ShortCalculator(mode = mode, onMode = { mode = it })
        return
    }

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

/**
 * A short window (the Q25's square screen): mode tabs on one line, then the display beside a keypad
 * that fills the height, so every key is on screen without scrolling. The converter scrolls.
 */
@Composable
private fun ShortCalculator(
    mode: CalculatorMode,
    onMode: (CalculatorMode) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().sableScreenPadding(),
            verticalArrangement = Arrangement.spacedBy(sableScreenGap()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
            ) {
                CalculatorMode.entries.forEach { choice ->
                    ModeTab(
                        label = choice.label,
                        active = mode == choice,
                        modifier = Modifier.weight(1f),
                        onClick = { onMode(choice) },
                    )
                }
            }
            when (mode) {
                CalculatorMode.Regular -> CalculatorPad(scientific = false, short = true)
                CalculatorMode.Scientific -> CalculatorPad(scientific = true, short = true)
                CalculatorMode.Converter ->
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(sableScreenGap()),
                    ) {
                        ConverterScreen()
                    }
            }
        }
    }
}

/** One mode tab in the short layout: a 36 dp chip, the accent when active. */
@Composable
private fun ModeTab(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        shape = MaterialTheme.shapes.small,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CalculatorPad(
    scientific: Boolean,
    short: Boolean = false,
) {
    var display by remember { mutableStateOf("0") }
    var leftOperand by remember { mutableStateOf<String?>(null) }
    var operation by remember { mutableStateOf<Int?>(null) }
    var replaceInput by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(initialStatus(scientific)) }

    fun clear() {
        display = "0"
        leftOperand = null
        operation = null
        replaceInput = false
        status = initialStatus(scientific)
    }

    fun enterDigit(digit: String) {
        display = withDigit(display, digit, replaceInput)
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
            display = withoutLastChar(display)
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

    val keypad: @Composable ColumnScope.(Boolean) -> Unit = { fill ->
        Keypad(
            fill = fill,
            onDigit = ::enterDigit,
            onDecimal = ::enterDecimal,
            onOperation = ::chooseOperation,
            onEquals = ::equals,
            onClear = ::clear,
            onToggleSign = { display = withToggledSign(display) },
            onBackspace = ::backspace,
        )
    }

    if (short) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            Column(
                modifier = Modifier.weight(0.42f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
            ) {
                SableValuePanel(
                    label = panelLabel(leftOperand, operation, scientific),
                    value = display,
                    supportingText = status,
                )
                if (scientific) {
                    ScientificGrid(columns = 2, onChoice = ::applyScientific)
                }
            }
            Column(
                modifier = Modifier.weight(0.58f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
            ) {
                keypad(true)
            }
        }
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
    ) {
        SableValuePanel(
            label = panelLabel(leftOperand, operation, scientific),
            value = display,
            supportingText = status,
        )

        if (scientific) {
            ScientificGrid(onChoice = ::applyScientific)
        }

        keypad(false)

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

private fun initialStatus(scientific: Boolean): String =
    if (scientific) {
        "Scientific functions use degrees."
    } else {
        "Exact decimal results, never rounded."
    }

private fun withDigit(
    display: String,
    digit: String,
    replaceInput: Boolean,
): String =
    when {
        replaceInput -> digit
        display == "0" -> digit
        display == "-0" -> "-$digit"
        else -> display + digit
    }

private fun withoutLastChar(display: String): String {
    val shorter = display.dropLast(1).ifEmpty { "0" }
    return if (shorter == "-") "0" else shorter
}

private fun withToggledSign(display: String): String =
    when {
        display.startsWith("-") -> display.removePrefix("-")
        display != "0" -> "-$display"
        else -> display
    }

private fun panelLabel(
    leftOperand: String?,
    operation: Int?,
    scientific: Boolean,
): String =
    when {
        operation != null -> "${leftOperand.orEmpty()} ${operationLabels[operation]}"
        scientific -> "scientific"
        else -> "display"
    }

@Composable
private fun ScientificGrid(
    columns: Int = 4,
    onChoice: (ScientificChoice) -> Unit,
) {
    scientificChoices.chunked(columns).forEach { rowChoices ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            rowChoices.forEach { choice ->
                SableTile(
                    title = choice.label,
                    modifier = Modifier.weight(1f),
                    onClick = { onChoice(choice) },
                )
            }
        }
    }
}

/** The keypad; with [fill] its rows share the height left (the short layout) instead of 58 dp keys. */
@Composable
private fun ColumnScope.Keypad(
    fill: Boolean,
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
            modifier = if (fill) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (fill) SableSpacing.Xs else SableSpacing.Sm),
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
                    modifier = if (fill) modifier.fillMaxHeight() else modifier.heightIn(min = 58.dp),
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
        modifier = modifier,
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
