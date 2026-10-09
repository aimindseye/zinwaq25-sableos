package org.sableos.titan2.keyboard.core

/**
 * Typing languages. Every pack here is Latin-script, so the printed QWERTY legends stay valid; a pack only adds
 * accented variants per base letter (long-press on the on-screen keyboard; Sym+letter, repeated to cycle, on the
 * physical keys). Non-Latin scripts need their own base layout and are a separate step. Variants are lowercase;
 * uppercase is derived.
 */
data class Language(
    val tag: String,
    val label: String,
    val accents: Map<Char, String>,
    /**
     * Three letter rows for the on-screen keyboard when the layout is not QWERTY Latin; null = QWERTY. A row is either
     * compact characters ("йцук") or space-separated keys ("ض ص لا") when a key holds more than one code point.
     */
    val rows: List<String>? = null,
    /** Shift layer for scripts without case (Arabic tashkeel, Indic independent vowels). Null: shift = uppercase. */
    val shiftRows: List<String>? = null,
    val engine: Engine = Engine.Direct,
    /** Long-press alternatives for the `,` and `.` keys. */
    val punct: Map<String, List<String>> = emptyMap(),
    /**
     * Long-press alternatives per key (conjuncts, candra and short vowel signs). Keys and values may be several
     * code points.
     */
    val more: Map<String, List<String>> = emptyMap(),
    /** Roman-letter typing table when [engine] is [Engine.Phonetic]. */
    val phonetic: PhoneticTable? = null,
    /**
     * Which layout of the language this is: "" is the default (InScript for Indic scripts), "phonetic" types
     * Roman letters.
     */
    val layout: String = ""
) {
    val latin get() = rows == null

    private fun toks(r: String): List<String> = if (' ' in
        r
    ) {
        r.split(' ').filter { it.isNotEmpty() }
    } else {
        r.map { it.toString() }
    }

    /** Keys of letter row [i] (0..2) for the on-screen keyboard; null rows means QWERTY. */
    fun rowKeys(i: Int, upper: Boolean): List<String> {
        val base = toks(rows?.get(i) ?: listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")[i])
        if (!upper) return base
        val shifted = shiftRows
        return if (shifted != null) toks(shifted[i]) else base.map { it.uppercase() }
    }

    /** False for scripts without case and without a shift layer: the shift key is not shown. */
    val hasShift: Boolean get() = rows == null || shiftRows != null ||
        (0..2).any { i -> rowKeys(i, false).any { it.uppercase() != it } }

    /** Variants of [c] for this language, in the case of [c]. Empty when none. */
    fun variants(c: Char): List<String> {
        val v = accents[c.lowercaseChar()] ?: return emptyList()
        val list = v.map { it.toString() }
        return if (c.isUpperCase()) list.map { it.uppercase() } else list
    }
}

object Languages {
    val English = Language("en_US", "English (US)", emptyMap())
    val all: List<Language> = listOf(
        English,
        Language(
            "es_ES",
            "Español",
            mapOf(
                'a' to "á",
                'e' to "é",
                'i' to "í",
                'o' to "ó",
                'u' to "úü",
                'n' to "ñ"
            )
        ),
        Language(
            "fr_FR",
            "Français",
            mapOf(
                'a' to "àâæ",
                'c' to "ç",
                'e' to "éèêë",
                'i' to "îï",
                'o' to "ôœ",
                'u' to "ùûü",
                'y' to "ÿ"
            )
        ),
        Language("de_DE", "Deutsch", mapOf('a' to "ä", 'o' to "ö", 'u' to "ü", 's' to "ß")),
        Language(
            "pt_BR",
            "Português",
            mapOf(
                'a' to "ãáàâ",
                'c' to "ç",
                'e' to "éê",
                'i' to "í",
                'o' to "õóô",
                'u' to "úü"
            )
        ),
        Language(
            "it_IT",
            "Italiano",
            mapOf(
                'a' to "à",
                'e' to "èé",
                'i' to "ì",
                'o' to "òó",
                'u' to "ù"
            )
        ),
        Language(
            "pl_PL",
            "Polski",
            mapOf(
                'a' to "ą",
                'c' to "ć",
                'e' to "ę",
                'l' to "ł",
                'n' to "ń",
                'o' to "ó",
                's' to "ś",
                'z' to "żź"
            )
        ),
        Language(
            "tr_TR",
            "Türkçe",
            mapOf(
                'c' to "ç",
                'g' to "ğ",
                'i' to "ı",
                'o' to "ö",
                's' to "ş",
                'u' to "ü"
            )
        ),
        Language("sv_SE", "Svenska", mapOf('a' to "åä", 'o' to "ö", 'e' to "é")),
        Language(
            "nl_NL",
            "Nederlands",
            mapOf(
                'e' to "éë",
                'i' to "ï",
                'o' to "ö",
                'u' to "ü",
                'a' to "ä"
            )
        ),
        // On-screen only: standard ЙЦУКЕН / Greek layouts. The printed Titan 2 key tops are Latin, so the hardware
        // keys keep typing Latin.
        Language(
            "ru_RU",
            "Русский",
            mapOf('е' to "ё"),
            rows = listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю")
        ),
        Language(
            "uk_UA",
            "Українська",
            mapOf('г' to "ґ", 'ь' to "'"),
            rows = listOf("йцукенгшщзхї", "фівапролджє", "ячсмитьбю")
        ),
        Language(
            "el_GR",
            "Ελληνικά",
            mapOf(
                'α' to "ά",
                'ε' to "έ",
                'η' to "ή",
                'ι' to "ίϊΐ",
                'ο' to "ό",
                'υ' to "ύϋΰ",
                'ω' to "ώ"
            ),
            rows = listOf("ςερτυθιοπ", "ασδφγηξκλ", "ζχψωβνμ")
        ),
        // Right-to-left scripts: keys are laid out in logical order, the text engine of the app does the shaping
        // and direction.
        Language(
            "ar_SA",
            "العربية",
            emptyMap(),
            rows = listOf(
                "ض ص ث ق ف غ ع ه خ ح ج د",
                "ش س ي ب ل ا ت ن م ك ط",
                "ذ ئ ء ؤ ر لا ى ة و ز ظ"
            ),
            // Persian/Urdu letters and tashkeel as long-press, as AnySoftKeyboard offers them.
            more = mapOf(
                "ج" to listOf("چ"),
                "ب" to listOf("پ"),
                "ك" to listOf("گ"),
                "ز" to listOf("ژ"),
                "ف" to listOf("ڤ"),
                "ي" to listOf("ی", "ئ"),
                "ض" to listOf("َ", "ً", "ُ", "ٌ", "ِ", "ٍ", "ّ", "ْ")
            ),
            shiftRows = listOf(
                "َ ً ُ ٌ لإ إ ‘ ÷ × ؛ < >",
                "ِ ٍ ] [ لأ أ ـ ، / : \"",
                "~ ْ } { لآ آ ’ , . ؟"
            ),
            punct = mapOf("," to listOf("،", "؛"), "." to listOf("؟", "۔"))
        ),
        Language(
            "he_IL",
            "עברית",
            emptyMap(),
            rows = listOf("ק ר א ט ו ן ם פ", "ש ד ג כ ע י ח ל ך ף", "ז ס ב ה נ מ צ ת ץ"),
            more = mapOf("ש" to listOf("₪"))
        ),
        // Indic scripts: InScript, the Indian standard layout (same sounds on the same keys across scripts) ...
        indic("hi_IN", "हिन्दी", IndicRows.devN, IndicRows.devS, IndicRows.devMore),
        indic(
            "mr_IN",
            "मराठी",
            IndicRows.devN,
            IndicRows.devS,
            IndicRows.devMore + mapOf("अ" to listOf("ॲ"))
        ),
        indic(
            "sa_IN",
            "संस्कृतम्",
            IndicRows.devN,
            IndicRows.devS,
            IndicRows.devMore + mapOf("्" to listOf("॑", "॒"))
        ),
        indic("gu_IN", "ગુજરાતી", IndicRows.gujN, IndicRows.gujS, IndicRows.gujMore),
        indic("bn_IN", "বাংলা", IndicRows.benN, IndicRows.benS, IndicRows.benMore),
        indic("pa_IN", "ਪੰਜਾਬੀ", IndicRows.panN, IndicRows.panS, IndicRows.panMore),
        indic(
            "ta_IN",
            "தமிழ்",
            IndicRows.tamN,
            IndicRows.tamS,
            IndicRows.tamMore
        ).copy(punct = emptyMap()),
        indic("te_IN", "తెలుగు", IndicRows.telN, IndicRows.telS, IndicRows.telMore),
        indic("kn_IN", "ಕನ್ನಡ", IndicRows.kanN, IndicRows.kanS, IndicRows.kanMore),
        indic(
            "ml_IN",
            "മലയാളം",
            IndicRows.malN,
            IndicRows.malS,
            IndicRows.malMore
        ).copy(punct = emptyMap()),
        // Urdu (Perso-Arabic, right to left). Layout after AnySoftKeyboard's Urdu pack; the rarer letters are
        // long-press.
        Language(
            "ur_PK",
            "اردو",
            emptyMap(),
            rows = listOf("ق و ع ر ت ے ء ی ہ پ", "ا س د ف گ ح ج ک ل", "ز ذ ش چ ط ب ن م ں"),
            more = mapOf(
                "ا" to listOf("آ", "أ", "إ"), "ت" to listOf("ٹ", "ث", "ۃ", "ة"),
                "د" to listOf("ڈ"), "ر" to listOf("ڑ"), "ز" to listOf("ژ"),
                "ہ" to listOf("ھ", "ۂ"), "س" to listOf("ص"), "ج" to listOf("ض"), "ط" to listOf("ظ"),
                "گ" to listOf("غ"), "ک" to listOf("خ"),
                "ی" to listOf("ئ", "ۓ"), "و" to listOf("ؤ"), "ن" to listOf("ں"),
                "ق" to listOf("ْ", "ّ", "َ", "ِ", "ُ")
            ),
            punct = mapOf("," to listOf("،", "؛"), "." to listOf("۔", "؟"))
        ),
        // ... and Roman-letter (phonetic) typing for the same languages: type "namaste", get नमस्ते.
        phonetic(
            "hi_IN",
            "हिन्दी",
            PhoneticTables.devanagari
        ),
        phonetic("mr_IN", "मराठी", PhoneticTables.devanagari),
        phonetic(
            "sa_IN",
            "संस्कृतम्",
            PhoneticTables.devanagari
        ),
        phonetic("gu_IN", "ગુજરાતી", PhoneticTables.gujarati),
        phonetic(
            "bn_IN",
            "বাংলা",
            PhoneticTables.bengali
        ),
        phonetic("pa_IN", "ਪੰਜਾਬੀ", PhoneticTables.gurmukhi),
        phonetic(
            "ta_IN",
            "தமிழ்",
            PhoneticTables.tamil
        ),
        phonetic("te_IN", "తెలుగు", PhoneticTables.telugu),
        phonetic(
            "kn_IN",
            "ಕನ್ನಡ",
            PhoneticTables.kannada
        ),
        phonetic("ml_IN", "മലയാളം", PhoneticTables.malayalam),
        // Composed scripts. Physical QWERTY keys type these too (Dubeolsik for Korean, romaji, pinyin).
        Language(
            "ko_KR",
            "한국어",
            emptyMap(),
            rows = listOf("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", "ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", "ㅋㅌㅊㅍㅠㅜㅡ"),
            shiftRows = listOf("ㅃㅉㄸㄲㅆㅛㅕㅑㅒㅖ", "ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", "ㅋㅌㅊㅍㅠㅜㅡ"),
            engine = Engine.Hangul
        ),
        Language("ja_JP", "日本語 (かな)", emptyMap(), engine = Engine.Kana),
        Language("zh_CN", "中文 (拼音)", emptyMap(), engine = Engine.Pinyin)
    )

    private fun indic(
        tag: String,
        label: String,
        n: List<String>,
        sh: List<String>,
        more: Map<String, List<String>>
    ) = Language(
        tag,
        label,
        emptyMap(),
        rows = n,
        shiftRows = sh,
        more = more,
        punct = mapOf("." to listOf("।", "॥"))
    )

    private fun phonetic(tag: String, label: String, table: PhoneticTable) = Language(
        tag,
        "$label · ABC",
        emptyMap(),
        engine = Engine.Phonetic,
        phonetic = table,
        layout = "phonetic"
    )

    /**
     * Matches on full tag, then on language part, else English. Accepts `es_ES`, `es-ES` or `es`. [layout] picks a
     * variant ("phonetic"); when the language has no such variant the default layout is returned.
     */
    fun forTag(tag: String?, layout: String = ""): Language {
        if (tag.isNullOrBlank()) return English
        val t = tag.replace('-', '_')
        val want = layout.trim().lowercase()
        fun pick(match: (Language) -> Boolean) = all.firstOrNull { match(it) && it.layout == want }
            ?: all.firstOrNull { match(it) && it.layout == "" }
        return pick { it.tag.equals(t, true) }
            ?: pick { it.tag.substringBefore('_').equals(t.substringBefore('_'), true) }
            ?: English
    }

    /** `layout=phonetic` out of an IME subtype's extra value (comma-separated key=value pairs). */
    fun layoutOf(extraValue: String?): String = extraValue?.split(',')?.map {
        it.trim()
    }?.firstOrNull { it.startsWith("layout=") }?.substringAfter('=')
        ?: ""
}

/** On-screen shift: tap = one letter, double-tap or long-press = caps lock, tap again = off. */
class SoftShift(private val doubleTapMs: Long = 400) {
    enum class State { Off, Once, Lock }
    var state = State.Off
        private set
    private var lastTap = Long.MIN_VALUE

    val upper get() = state != State.Off

    fun tap(now: Long) {
        state = when {
            state == State.Lock -> State.Off
            state == State.Once && now - lastTap <= doubleTapMs -> State.Lock
            state == State.Once -> State.Off
            else -> State.Once
        }
        lastTap = now
    }
    fun longPress() {
        state = State.Lock
    }

    /** Call after a letter is typed: one-shot ends, lock stays. */
    fun letterTyped() {
        if (state == State.Once) state = State.Off
    }

    /** Start of a sentence turns one-shot shift on, never overrides lock. */
    fun autoCap(on: Boolean) {
        if (state == State.Off && on) state = State.Once
    }
}
