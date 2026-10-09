package org.sableos.tools.core

/**
 * Local-first IR remote (IR_REMOTE_LOCAL_FIRST): remotes live on the device, nothing is fetched or synced
 * (NETWORK_REQUIRED_FOR_REMOTE=NO). Learning is not offered: Android has no public IR receive API, so
 * LEARNING_MODE=ONLY_IF_IR_RECEIVER_PROVEN keeps it off.
 */
enum class ApplianceType(val title: String) {
    TV("TV"),
    AIR_CONDITIONER("Air conditioner"),
    PROJECTOR("Projector"),
    AUDIO("Audio"),
    CUSTOM("Custom")
}

/** Published timings of the NEC and Samsung32 IR protocols. */
object IrTiming {
    const val CARRIER_38K = 38_000
    const val NEC_LEADER_ON_US = 9_000
    const val SAMSUNG_LEADER_ON_US = 4_500
    const val LEADER_OFF_US = 4_500
}

/**
 * Pulse-distance protocols with a 32-bit frame sent most significant bit first (the convention used by common IR
 * code tables, for example 0x20DF10EF). They differ only in the leader.
 */
enum class IrProtocol(val carrierHz: Int, val leaderOnUs: Int, val leaderOffUs: Int) {
    NEC(IrTiming.CARRIER_38K, IrTiming.NEC_LEADER_ON_US, IrTiming.LEADER_OFF_US),
    SAMSUNG32(IrTiming.CARRIER_38K, IrTiming.SAMSUNG_LEADER_ON_US, IrTiming.LEADER_OFF_US);

    /** Microsecond on/off pattern for ConsumerIrManager.transmit, starting with "on". */
    fun pattern(code: Long, bits: Int = FRAME_BITS): IntArray {
        require(bits in 1..FRAME_BITS) { "bits must be 1..$FRAME_BITS" }
        val out = ArrayList<Int>(2 + bits * 2 + 1)
        out += leaderOnUs
        out += leaderOffUs
        for (i in bits - 1 downTo 0) {
            out += BIT_MARK_US
            out += if ((code shr i) and 1L == 1L) ONE_SPACE_US else ZERO_SPACE_US
        }
        out += BIT_MARK_US
        return out.toIntArray()
    }

    companion object {
        const val FRAME_BITS = 32
        const val BIT_MARK_US = 560
        const val ZERO_SPACE_US = 560
        const val ONE_SPACE_US = 1_690

        /** ConsumerIrManager rejects patterns longer than 2 seconds. */
        const val MAX_PATTERN_US = 2_000_000
    }
}

data class IrButton(val name: String, val code: Long)

data class Remote(
    val name: String,
    val type: ApplianceType,
    val protocol: IrProtocol,
    val buttons: List<IrButton>,
    /** Built-in table entries are not verified against real appliances by this project. */
    val builtIn: Boolean = false
) {
    fun button(name: String): IrButton? =
        buttons.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

object RemoteLibrary {
    private fun b(name: String, code: Long) = IrButton(name, code)

    /** Small local table of widely published codes. Marked built-in/unverified; users add their own. */
    val BUILT_IN: List<Remote> = listOf(
        Remote(
            "Samsung TV",
            ApplianceType.TV,
            IrProtocol.SAMSUNG32,
            listOf(
                b("Power", 0xE0E040BFL),
                b("Volume up", 0xE0E0E01FL),
                b("Volume down", 0xE0E0D02FL),
                b("Mute", 0xE0E0F00FL),
                b("Channel up", 0xE0E048B7L),
                b("Channel down", 0xE0E008F7L)
            ),
            builtIn = true
        ),
        Remote(
            "LG TV",
            ApplianceType.TV,
            IrProtocol.NEC,
            listOf(
                b("Power", 0x20DF10EFL),
                b("Volume up", 0x20DF40BFL),
                b("Volume down", 0x20DFC03FL),
                b("Mute", 0x20DF906FL),
                b("Channel up", 0x20DF00FFL),
                b("Channel down", 0x20DF807FL)
            ),
            builtIn = true
        )
    )

    /** NEC frames carry address, ~address, command, ~command; builds the 32-bit word for a custom button. */
    fun necFrame(address: Int, command: Int): Long {
        require(address in 0..BYTE_MAX && command in 0..BYTE_MAX) {
            "address and command are bytes"
        }
        // Bytes are sent LSB first on the wire; the MSB-first word therefore holds each byte bit-reversed.
        fun rev(v: Int) = Integer.reverse(v) ushr (Int.SIZE_BITS - Byte.SIZE_BITS)
        val bytes = listOf(
            address,
            address.inv() and BYTE_MAX,
            command,
            command.inv() and BYTE_MAX
        ).map(::rev)
        return bytes.fold(0L) { acc, v -> (acc shl Byte.SIZE_BITS) or v.toLong() }
    }

    private const val BYTE_MAX = 0xFF
}

/**
 * Line codec for the user's own remotes (one remote per line), stored in the app's private preferences:
 * `name<TAB>TYPE<TAB>PROTOCOL<TAB>button=HEX;button=HEX`.
 */
object RemoteCodec {
    private const val FIELDS = 4
    private const val BUTTONS_FIELD = 3

    fun encode(remotes: List<Remote>): String = remotes.filterNot {
        it.builtIn
    }.joinToString("\n") { r ->
        listOf(
            clean(r.name),
            r.type.name,
            r.protocol.name,
            r.buttons.joinToString(";") {
                "${clean(it.name)}=${java.lang.Long.toHexString(it.code).uppercase()}"
            }
        ).joinToString("\t")
    }

    fun decode(s: String?): List<Remote> = s.orEmpty().lines().mapNotNull { line ->
        val f = line.split('\t')
        if (f.size != FIELDS || f[0].isBlank()) return@mapNotNull null
        val type = ApplianceType.entries.firstOrNull { it.name == f[1] } ?: return@mapNotNull null
        val proto = IrProtocol.entries.firstOrNull { it.name == f[2] } ?: return@mapNotNull null
        val buttons = f[BUTTONS_FIELD].split(';').mapNotNull { kv ->
            val i = kv.lastIndexOf('=')
            if (i <= 0) return@mapNotNull null
            val code =
                kv.substring(i + 1).toLongOrNull(HEX)?.takeIf { it in 0L..MAX_FRAME }
                    ?: return@mapNotNull null
            IrButton(kv.substring(0, i), code)
        }
        Remote(f[0], type, proto, buttons)
    }

    /** Parses user input like "0x20DF10EF" or "20df10ef". */
    fun parseCode(s: String): Long? =
        s.trim().removePrefix("0x").removePrefix("0X").toLongOrNull(HEX)?.takeIf {
            it in
                0L..MAX_FRAME
        }

    private fun clean(s: String) =
        s.replace('\t', ' ').replace('\n', ' ').replace(';', ',').replace('=', '-').trim()

    private const val HEX = 16
    private const val MAX_FRAME = 0xFFFFFFFFL
}

/** Decides whether a transmit can go ahead. Every refusal is explained instead of failing silently. */
object IrTransmit {
    sealed interface Decision {
        data class Send(val carrierHz: Int, val pattern: IntArray) : Decision
        data class Refuse(val reason: String) : Decision
    }

    fun decide(
        remote: Remote,
        button: IrButton,
        resolution: Resolution,
        carrierRanges: List<IntRange>
    ): Decision {
        val hz = remote.protocol.carrierHz
        val p = remote.protocol.pattern(button.code)
        return when {
            resolution.visibility == Visibility.HIDDEN ->
                Decision.Refuse("No IR transmitter on this device profile.")

            carrierRanges.isNotEmpty() && carrierRanges.none { hz in it } ->
                Decision.Refuse("The IR transmitter does not support ${hz / HZ_PER_KHZ} kHz.")

            p.sum() > IrProtocol.MAX_PATTERN_US -> Decision.Refuse("Pattern too long.")

            else -> Decision.Send(hz, p)
        }
    }

    private const val HZ_PER_KHZ = 1000
}
