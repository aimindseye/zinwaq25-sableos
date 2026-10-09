package org.sableos.titan2.keyboard.core

/** How a language turns typed keys into text. */
enum class Engine { Direct, Hangul, Kana, Pinyin, Phonetic }

/** A candidate the user can tap. [consumed] is how many typed characters it replaces (pinyin may consume a prefix). */
data class Cand(val text: String, val consumed: Int)

data class SpaceResult(val commit: String, val addSpace: Boolean)

/**
 * Text composition for scripts that are not typed one key = one character. Every method that can finish text
 * returns the text to COMMIT; [composing] is what stays underlined in the field afterwards. The Android layer
 * commits first (which replaces the old composing region) and then sets the new composing text.
 */
interface Composer {
    val composing: String
    val candidates: List<Cand> get() = emptyList()

    /** One key's output (a letter, a jamo, punctuation). Non-composable text flushes the composition first. */
    fun type(s: String): String

    /** True when a composition was edited (the caller must not send a real Backspace). */
    fun backspace(): Boolean
    fun space(): SpaceResult

    /** Commit whatever is composing as-is (Enter, focus change). */
    fun flush(): String
    fun pick(index: Int): String = ""
    fun reset()
}

object Composers {
    fun create(
        engine: Engine,
        pinyin: () -> PinyinDict? = { null },
        phonetic: PhoneticTable? = null
    ): Composer? = when (engine) {
        Engine.Direct -> null
        Engine.Hangul -> HangulComposer()
        Engine.Kana -> KanaComposer()
        Engine.Pinyin -> PinyinComposer(pinyin)
        Engine.Phonetic -> phonetic?.let { PhoneticComposer(it) }
    }
}

/** Korean: Dubeolsik keys. Latin QWERTY keys map to jamo so the physical keyboard types Korean too. */
object Dubeolsik {
    private const val NORMAL = "qwertyuiopasdfghjklzxcvbnm"
    private const val JAMO = "ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ"
    private val shiftPairs = mapOf(
        'q' to 'ㅃ',
        'w' to 'ㅉ',
        'e' to 'ㄸ',
        'r' to 'ㄲ',
        't' to 'ㅆ',
        'o' to 'ㅒ',
        'p' to 'ㅖ'
    )
    fun jamo(c: Char, shift: Boolean): Char? {
        val l = c.lowercaseChar()
        if (shift) shiftPairs[l]?.let { return it }
        val i = NORMAL.indexOf(l)
        return if (i >= 0) JAMO[i] else null
    }
}

class HangulComposer : Composer {
    private var l: Char? = null
    private var v: Char? = null
    private var t = ArrayList<Char>(2) // 0, 1 or 2 final consonants (2 = compound)

    private companion object {
        const val L = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        const val V = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
        const val T = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
        val vCombo = mapOf(
            ('ㅗ' to 'ㅏ') to 'ㅘ',
            ('ㅗ' to 'ㅐ') to 'ㅙ',
            ('ㅗ' to 'ㅣ') to 'ㅚ',
            ('ㅜ' to 'ㅓ') to 'ㅝ',
            ('ㅜ' to 'ㅔ') to 'ㅞ',
            ('ㅜ' to 'ㅣ') to 'ㅟ',
            ('ㅡ' to 'ㅣ') to 'ㅢ'
        )
        val vSplit = vCombo.entries.associate { it.value to it.key.first }
        val tCombo = mapOf(
            ('ㄱ' to 'ㅅ') to 'ㄳ', ('ㄴ' to 'ㅈ') to 'ㄵ', ('ㄴ' to 'ㅎ') to 'ㄶ',
            ('ㄹ' to 'ㄱ') to 'ㄺ',
            ('ㄹ' to 'ㅁ') to 'ㄻ', ('ㄹ' to 'ㅂ') to 'ㄼ', ('ㄹ' to 'ㅅ') to 'ㄽ', ('ㄹ' to 'ㅌ') to 'ㄾ',
            ('ㄹ' to 'ㅍ') to 'ㄿ', ('ㄹ' to 'ㅎ') to 'ㅀ', ('ㅂ' to 'ㅅ') to 'ㅄ'
        )
        const val HANGUL_BASE = 0xAC00
        const val VOWEL_COUNT = 21
        const val FINAL_COUNT = 28
        fun isJamo(c: Char) = c in L || c in V || c in T
        fun isVowel(c: Char) = c in V
        fun canFinal(c: Char) = c in T && c != ' '
    }

    private fun finalIndex(): Int = when (t.size) {
        0 -> 0
        1 -> T.indexOf(t[0])
        else -> T.indexOf(tCombo.getValue(t[0] to t[1]))
    }

    override val composing: String get() = render()

    private fun render(): String {
        val lv = l
        val vv = v
        return when {
            lv != null && vv != null -> (
                HANGUL_BASE + (L.indexOf(lv) * VOWEL_COUNT + V.indexOf(vv)) * FINAL_COUNT +
                    finalIndex()
                ).toChar().toString()

            lv != null -> lv.toString()

            vv != null -> vv.toString()

            else -> ""
        }
    }

    private fun clear() {
        l = null
        v = null
        t.clear()
    }

    override fun type(s: String): String {
        val j = if (s.length == 1) s[0] else null
        if (j == null || !isJamo(j)) {
            val out = flush()
            return out + s
        }
        return if (isVowel(j)) vowel(j) else consonant(j)
    }

    private fun vowel(j: Char): String {
        val cur = v
        return when {
            cur == null && (l == null || t.isEmpty()) -> {
                v = j
                ""
            }

            cur != null && t.isEmpty() -> {
                val combo = vCombo[cur to j]
                if (combo != null) {
                    v = combo
                    ""
                } else {
                    val done = render()
                    clear()
                    v = j
                    done
                }
            }

            else -> {
                // final consonant moves to the new syllable (only the last one of a compound)
                val moved = t.removeAt(t.size - 1)
                val done = render()
                clear()
                l = moved
                v = j
                done
            }
        }
    }

    private fun restartWith(j: Char): String {
        val done = render()
        clear()
        l = j
        return done
    }

    private fun consonant(j: Char): String {
        val lv = l
        val vv = v
        return when {
            lv == null && vv == null -> {
                l = j
                ""
            }

            // lone consonant then another, lone vowel then consonant, or a consonant that cannot be final
            vv == null || lv == null || !canFinal(j) -> restartWith(j)

            else -> finalConsonant(j)
        }
    }

    private fun finalConsonant(j: Char): String = when (t.size) {
        0 -> {
            t.add(j)
            ""
        }

        1 -> if (tCombo.containsKey(t[0] to j)) {
            t.add(j)
            ""
        } else {
            restartWith(j)
        }

        else -> restartWith(j)
    }

    override fun backspace(): Boolean {
        when {
            t.isNotEmpty() -> t.removeAt(t.size - 1)
            v != null -> v = vSplit[v!!]
            l != null -> l = null
            else -> return false
        }
        return true
    }

    override fun space(): SpaceResult = SpaceResult(flush(), true)
    override fun flush(): String {
        val d = render()
        clear()
        return d
    }
    override fun reset() = clear()
}

/** Japanese: romaji to kana. No kanji conversion (that needs a dictionary engine); offers hiragana/katakana. */
class KanaComposer : Composer {
    private val kana = StringBuilder()
    private var pending = ""

    override val composing: String get() = kana.toString() + pending
    override val candidates: List<Cand>
        get() = if (composing.isEmpty()) {
            emptyList()
        } else {
            val hira = kana.toString() + (if (pending == "n") "ん" else pending)
            listOf(Cand(hira, composing.length), Cand(toKatakana(hira), composing.length))
        }

    companion object {
        private const val KATAKANA_OFFSET = 0x60
        private val table: Map<String, String> = buildMap {
            fun add(romaji: String, kana: String) {
                put(romaji, kana)
            }
            val vowels = "aiueo"
            fun row(consonant: String, kanas: String) {
                kanas.forEachIndexed { i, k -> add(consonant + vowels[i], k.toString()) }
            }
            row("", "あいうえお")
            row("k", "かきくけこ")
            row("s", "さしすせそ")
            row("t", "たちつてと")
            row("n", "なにぬねの")
            row("h", "はひふへほ")
            row("m", "まみむめも")
            row("r", "らりるれろ")
            row("g", "がぎぐげご")
            row("z", "ざじずぜぞ")
            row("d", "だぢづでど")
            row("b", "ばびぶべぼ")
            row("p", "ぱぴぷぺぽ")
            row("x", "ぁぃぅぇぉ")
            row("l", "ぁぃぅぇぉ")
            add("ya", "や")
            add("yu", "ゆ")
            add("yo", "よ")
            add("wa", "わ")
            add("wo", "を")
            add("nn", "ん")
            add("n'", "ん")
            add("shi", "し")
            add("chi", "ち")
            add("tsu", "つ")
            add("fu", "ふ")
            add("ji", "じ")
            add("si", "し")
            add("ti", "ち")
            add("tu", "つ")
            add("hu", "ふ")
            add("zi", "じ")
            add("xtu", "っ")
            add("xtsu", "っ")
            add("ltu", "っ")
            add("ltsu", "っ")
            add("xya", "ゃ")
            add("xyu", "ゅ")
            add("xyo", "ょ")
            add("lya", "ゃ")
            add("lyu", "ゅ")
            add("lyo", "ょ")
            add("xwa", "ゎ")
            add("wi", "ゐ")
            add("we", "ゑ")
            add("vu", "ゔ")
            for ((c, base) in listOf(
                "k" to "き",
                "g" to "ぎ",
                "n" to "に",
                "h" to "ひ",
                "m" to "み",
                "r" to "り",
                "b" to "び",
                "p" to "ぴ"
            )) {
                add(c + "ya", base + "ゃ")
                add(c + "yu", base + "ゅ")
                add(c + "yo", base + "ょ")
            }
            for ((c, base) in listOf(
                "sh" to "し",
                "ch" to "ち",
                "j" to "じ",
                "sy" to "し",
                "ty" to "ち",
                "jy" to "じ",
                "zy" to "じ",
                "dy" to "ぢ"
            )) {
                add(c + "a", base + "ゃ")
                add(c + "u", base + "ゅ")
                add(c + "o", base + "ょ")
            }
            add("she", "しぇ")
            add("che", "ちぇ")
            add("je", "じぇ")
            add("fa", "ふぁ")
            add("fi", "ふぃ")
            add("fe", "ふぇ")
            add("fo", "ふぉ")
            add("tsa", "つぁ")
            add("tsi", "つぃ")
            add("tse", "つぇ")
            add("tso", "つぉ")
            add("-", "ー")
            add(",", "、")
            add(".", "。")
        }
        private val prefixes: Set<String> = table.keys.flatMap { k ->
            (1 until k.length).map { k.substring(0, it) }
        }.toSet()

        fun toKatakana(s: String): String = s.map {
            if (it in
                'ぁ'..'ゖ'
            ) {
                (it.code + KATAKANA_OFFSET).toChar()
            } else {
                it
            }
        }.joinToString("")
    }

    override fun type(s: String): String {
        val c = if (s.length == 1) s[0].lowercaseChar() else null
        if (c == null || !isKanaInput(c)) {
            return flush() + s
        }
        pending += c
        drain()
        return ""
    }

    private fun isVowel(c: Char) = c in "aiueo"

    private fun isKanaInput(c: Char) = c in 'a'..'z' || c in "-,.'"

    private fun drain() {
        var more = pending.isNotEmpty()
        while (more) {
            more = drainStep() && pending.isNotEmpty()
        }
    }

    /** Doubled consonant: kka -> っか. */
    private fun isDoubledConsonant(p: String): Boolean =
        p.length >= 2 && p[0] == p[1] && p[0] !in "aiueon" && p[0] in 'a'..'z'

    /** A syllabic n that is not the start of na/ni/nu/ne/no/ny-. */
    private fun isSyllabicN(p: String): Boolean = p[0] == 'n' && p.length >= 2 && p[1] !in "aiueoy'"

    /** One reduction step on [pending]; returns true when the loop should try again. */
    private fun drainStep(): Boolean {
        val p = pending
        val hit = table[p]
        return when {
            hit != null -> {
                kana.append(hit)
                pending = ""
                false
            }

            p in prefixes -> false

            isDoubledConsonant(p) -> {
                kana.append('っ')
                pending = p.substring(1)
                true
            }

            isSyllabicN(p) -> {
                kana.append('ん')
                pending = p.substring(1)
                true
            }

            else -> {
                // not romaji at all: keep the first character literally and retry with the rest
                kana.append(p[0])
                pending = p.substring(1)
                true
            }
        }
    }

    override fun backspace(): Boolean {
        when {
            pending.isNotEmpty() -> pending = pending.dropLast(1)
            kana.isNotEmpty() -> kana.setLength(kana.length - 1)
            else -> return false
        }
        return true
    }

    override fun space(): SpaceResult = SpaceResult(flush(), true)
    override fun flush(): String {
        if (pending == "n") {
            kana.append('ん')
        } else {
            kana.append(pending)
        }
        val d = kana.toString()
        kana.setLength(0)
        pending = ""
        return d
    }
    override fun pick(index: Int): String {
        val c =
            candidates.getOrNull(index) ?: return ""
        kana.setLength(0)
        pending = ""
        return c.text
    }
    override fun reset() {
        kana.setLength(0)
        pending = ""
    }
}

/**
 * Sorted pinyin dictionary: keys ascending, words within a key by ranking weight descending, ties by word code
 * point (as written by tools/gen_pinyin_dict.py). The weight is a neutral ranking weight, NOT a usage frequency.
 */
class PinyinDict(
    private val keys: Array<String>,
    private val words: Array<String>,
    private val weight: IntArray
) {
    val size get() = keys.size

    companion object {
        private const val FIELD_COUNT = 3

        /** Lines are `pinyin<TAB>word<TAB>weight`, sorted as produced by tools/gen_pinyin_dict.py. */
        fun parse(lines: Sequence<String>): PinyinDict {
            val k = ArrayList<String>()
            val w = ArrayList<String>()
            val wt = ArrayList<Int>()
            for (line in lines) {
                val p = line.split('\t')
                if (p.size < FIELD_COUNT) continue
                k.add(p[0])
                w.add(p[1])
                wt.add(p[2].toIntOrNull() ?: 0)
            }
            return PinyinDict(k.toTypedArray(), w.toTypedArray(), wt.toIntArray())
        }
    }

    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = keys.size
        while (lo <
            hi
        ) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun exact(key: String, limit: Int = 12): List<String> {
        val out = ArrayList<String>()
        var i = lowerBound(key)
        while (i < keys.size && keys[i] == key && out.size < limit) {
            out.add(words[i])
            i++
        }
        return out
    }

    /** Best words whose key starts with [prefix] but is longer (completions), by weight; ties keep file order. */
    fun completions(prefix: String, limit: Int = 8): List<String> {
        val hits = ArrayList<Int>()
        var i = lowerBound(prefix)
        while (i < keys.size &&
            keys[i].startsWith(prefix)
        ) {
            if (keys[i].length > prefix.length) hits.add(i)
            i++
        }
        return hits.sortedByDescending { weight[it] }.take(limit).map { words[it] }
    }
}

/** Simplified Chinese pinyin. Typed letters are the composition; candidates come from [dict]. */
class PinyinComposer(private val dict: () -> PinyinDict?) : Composer {
    private var typed = ""
    private var cands: List<Cand> = emptyList()

    private companion object {
        const val MAX_CANDIDATES = 40
        const val PREFIX_LIMIT = 5
    }

    override val composing: String get() = typed
    override val candidates: List<Cand> get() = cands

    private val fullWidth = mapOf(
        ',' to "，",
        '.' to "。",
        '?' to "？",
        '!' to "！",
        ':' to "：",
        ';' to "；",
        '(' to "（",
        ')' to "）"
    )

    override fun type(s: String): String {
        val c = if (s.length == 1) s[0] else null
        if (c != null && isPinyinKey(c)) {
            typed += c.lowercaseChar()
            refresh()
            return ""
        }
        val head = if (typed.isEmpty()) "" else commitBest()
        return head + (if (c != null) fullWidth[c] ?: s else s)
    }

    private fun isPinyinKey(c: Char) =
        c in 'a'..'z' || c in 'A'..'Z' || (c == '\'' && typed.isNotEmpty())

    private fun clean(s: String) = s.replace("'", "")

    /** Index in [typed] after [n] letters (apostrophes do not count). */
    private fun rawIndex(n: Int): Int {
        var seen = 0
        for (i in typed.indices) {
            if (typed[i] !=
                '\''
            ) {
                if (seen == n) return i
                seen++
            }
        }
        return typed.length
    }

    private fun refresh() {
        val d = dict()
        val key = clean(typed)
        if (d == null || key.isEmpty()) {
            cands = emptyList()
            return
        }
        val out = LinkedHashMap<String, Cand>()
        fun add(w: String, letters: Int) {
            if (w !in out &&
                out.size < MAX_CANDIDATES
            ) {
                out[w] =
                    Cand(w, if (letters >= key.length) typed.length else rawIndex(letters))
            }
        }
        d.exact(key).forEach { add(it, key.length) }
        d.completions(key).forEach { add(it, key.length) }
        for (len in key.length - 1 downTo 1) {
            d.exact(key.substring(0, len), PREFIX_LIMIT).forEach {
                add(it, len)
            }
        }
        cands = out.values.toList()
    }

    private fun commitBest(): String {
        val best = cands.firstOrNull()
        val text = best?.text ?: typed
        typed = ""
        cands = emptyList()
        return text
    }

    override fun pick(index: Int): String {
        val c = cands.getOrNull(index) ?: return ""
        typed = typed.substring(minOf(c.consumed, typed.length)).trimStart('\'')
        refresh()
        return c.text
    }

    override fun backspace(): Boolean {
        if (typed.isEmpty()) return false
        typed = typed.dropLast(1)
        refresh()
        return true
    }

    override fun space(): SpaceResult = when {
        typed.isEmpty() -> SpaceResult("", true)

        cands.isEmpty() -> SpaceResult(flush(), false)

        // first candidate; an unconsumed remainder stays composing
        else -> SpaceResult(pick(0), false)
    }

    override fun flush(): String {
        val d = typed
        typed = ""
        cands = emptyList()
        return d
    }
    override fun reset() {
        typed = ""
        cands = emptyList()
    }
}

/**
 * Roman-letter ("phonetic") typing for Indic scripts, ITRANS-like and case sensitive.
 *  - consonants carry their inherent "a": `k` = क, `ka` = क, `kt` = क्त (the virama is inserted between consonants);
 *  - a vowel after a consonant becomes its vowel sign (`ki` = कि), anywhere else the independent vowel (`i` = इ);
 *  - `aa ii uu ee oo ai au`, capitals `A I U E O`, `M` anusvara, `MM`/`~` chandrabindu, `H` visarga,
 *    `_` explicit virama;
 *  - the whole Roman buffer is re-converted on every key, so Backspace simply removes the last Roman letter.
 * Tables are generated per script (tools/gen_phonetic.py).
 */
class PhoneticTable(
    val consonants: Map<String, String>,
    val vowels: Map<String, Pair<String, String>>, // roman -> (independent vowel, vowel sign)
    val marks: Map<String, String>,
    val specials: Map<String, String>,
    val virama: String,
    /** South Indian scripts write a pronounced final consonant with a virama; north Indian ones drop the schwa. */
    val finalVirama: Boolean
) {
    internal val keys: List<String> =
        (consonants.keys + vowels.keys + marks.keys + specials.keys).sortedByDescending {
            it.length
        }
}

class PhoneticComposer(private val t: PhoneticTable) : Composer {
    private val roman = StringBuilder()
    private enum class Kind { None, Consonant, Vowel, Mark, Halant, Other }

    override val composing: String get() = convert(roman.toString())

    internal fun convert(s: String): String {
        val sb = StringBuilder()
        var prev = Kind.None
        var i = 0
        while (i < s.length) {
            val m = t.keys.firstOrNull { s.startsWith(it, i) }
            if (m == null) {
                prev = appendLiteral(sb, s[i], prev)
                i++
            } else {
                i += m.length
                prev = appendToken(sb, m, prev)
            }
        }
        if (t.finalVirama && prev == Kind.Consonant) sb.append(t.virama)
        return sb.toString()
    }

    private fun appendLiteral(sb: StringBuilder, ch: Char, prev: Kind): Kind =
        if (ch == '_' && prev == Kind.Consonant) {
            sb.append(t.virama)
            Kind.Halant
        } else {
            sb.append(ch)
            Kind.Other
        }

    private fun appendToken(sb: StringBuilder, m: String, prev: Kind): Kind {
        val c = t.consonants[m]
        val v = t.vowels[m]
        return when {
            c != null -> {
                if (prev == Kind.Consonant) sb.append(t.virama)
                sb.append(c)
                Kind.Consonant
            }

            v != null -> {
                sb.append(if (prev == Kind.Consonant) v.second else v.first)
                Kind.Vowel
            }

            else -> {
                sb.append(t.marks[m] ?: t.specials[m] ?: m)
                Kind.Mark
            }
        }
    }

    override fun type(s: String): String {
        val c = if (s.length == 1) s[0] else null
        if (c != null && isRomanKey(c)) {
            roman.append(c)
            return ""
        }
        return flush() + s
    }

    private fun isRomanKey(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in "_~"

    override fun backspace(): Boolean {
        if (roman.isEmpty()) return false
        roman.setLength(roman.length - 1)
        return true
    }
    override fun space(): SpaceResult = SpaceResult(flush(), true)
    override fun flush(): String {
        val d = composing
        roman.setLength(0)
        return d
    }
    override fun reset() {
        roman.setLength(0)
    }
}
