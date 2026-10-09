package org.sableos.titan2.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageTest {
    private var t = 0L
    private val ctx = FieldCtx(InputClass.Text, before = "hello", autoCapAllowed = true)
    private fun Resolver.sym() {
        t += 50
        onKey(KeyEv(Key.Modifier(Mod.Sym), true, t), ctx)
        t += 20
        onKey(KeyEv(Key.Modifier(Mod.Sym), false, t), ctx)
    }
    private fun Resolver.press(k: Key): List<Out> {
        t += 50
        val d = onKey(KeyEv(k, true, t), ctx)
        t +=
            20
        onKey(KeyEv(k, false, t), ctx)
        return d
    }

    @Test fun tagLookupFallsBackByLanguageThenEnglish() {
        assertEquals("es_ES", Languages.forTag("es-MX").tag)
        assertEquals("fr_FR", Languages.forTag("fr_FR").tag)
        assertEquals("en_US", Languages.forTag("sw_KE").tag)
        assertEquals("en_US", Languages.forTag(null).tag)
    }

    @Test fun variantsFollowCase() {
        val fr = Languages.forTag("fr")
        assertEquals(listOf("é", "è", "ê", "ë"), fr.variants('e'))
        assertEquals(listOf("É", "È", "Ê", "Ë"), fr.variants('E'))
        assertTrue(Languages.English.variants('e').isEmpty())
    }

    @Test fun everyPackOnlyAccentsLatinLetters() {
        Languages.all.filter { it.latin }.forEach { l ->
            l.accents.keys.forEach {
                assertTrue(
                    "${l.tag} $it",
                    it in 'a'..'z'
                )
            }
        }
    }

    @Test fun symLetterCyclesVariantsAndReplacesPrevious() {
        val r = Resolver().also { it.language = Languages.forTag("fr") }
        r.sym()
        assertEquals(listOf<Out>(Out.Commit("é")), r.press(Key.Letter('e')))
        r.sym()
        assertEquals(listOf(Out.DeleteBefore(1), Out.Commit("è")), r.press(Key.Letter('e')))
        r.sym()
        r.press(Key.Letter('e'))
        r.sym()
        r.press(Key.Letter('e')) // ê, ë
        r.sym()
        // wraps
        assertEquals(listOf(Out.DeleteBefore(1), Out.Commit("é")), r.press(Key.Letter('e')))
    }

    @Test fun cycleResetsAfterAnotherKey() {
        val r = Resolver().also { it.language = Languages.forTag("es") }
        r.sym()
        r.press(Key.Letter('a'))
        r.press(Key.Letter('b'))
        r.sym()
        assertEquals(listOf<Out>(Out.Commit("á")), r.press(Key.Letter('a')))
    }

    @Test fun symLetterWithoutVariantsKeepsSymTable() {
        val r = Resolver().also { it.language = Languages.forTag("es") }
        r.sym()
        assertEquals(listOf<Out>(Out.Commit("~")), r.press(Key.Letter('q')))
    }

    @Test fun symSpaceSwitchesLanguage() {
        val r = Resolver()
        r.sym()
        assertEquals(listOf<Out>(Out.NextLanguage), r.press(Key.Space))
    }

    @Test fun softShiftTapDoubleTapLongPress() {
        val s = SoftShift()
        s.tap(0)
        assertEquals(SoftShift.State.Once, s.state)
        s.letterTyped()
        assertEquals(SoftShift.State.Off, s.state)
        s.tap(1000)
        s.tap(1200)
        assertEquals(SoftShift.State.Lock, s.state)
        s.letterTyped()
        assertEquals(SoftShift.State.Lock, s.state)
        s.tap(5000)
        assertEquals(SoftShift.State.Off, s.state)
        s.tap(6000)
        s.tap(7000)
        assertEquals(SoftShift.State.Off, s.state) // slow second tap cancels
        s.longPress()
        assertEquals(SoftShift.State.Lock, s.state)
        s.autoCap(true)
        assertEquals(SoftShift.State.Lock, s.state)
    }

    @Test fun nonLatinRowsAreWellFormed() {
        val nl = Languages.all.filter { it.tag in listOf("ru_RU", "uk_UA", "el_GR") }
        assertEquals(listOf("ru_RU", "uk_UA", "el_GR"), nl.map { it.tag })
        nl.forEach { l ->
            val r = l.rows!!
            assertEquals(3, r.size)
            val all = r.joinToString("")
            assertEquals("${l.tag} duplicate letters", all.length, all.toSet().size)
            all.forEach { assertTrue("${l.tag} $it has no case", it.isLowerCase()) }
            l.accents.keys.forEach { k ->
                assertTrue(
                    "${l.tag} accent base $k not on the layout",
                    k in all
                )
            }
        }
    }

    @Test fun nonLatinVariantsAndNoEffectOnPhysicalLatinKeys() {
        assertEquals(listOf("Ё"), Languages.forTag("ru").variants('Е'))
        assertEquals(listOf("ί", "ϊ", "ΐ"), Languages.forTag("el").variants('ι'))
        val r = Resolver().also { it.language = Languages.forTag("ru") }
        r.sym()
        // physical keys unchanged
        assertEquals(listOf<Out>(Out.Commit("~")), r.press(Key.Letter('q')))
    }
}

class ScriptLayoutTest {
    @Test fun everyLanguageHasLayoutOrIsQwerty() {
        Languages.all.forEach { l ->
            if (l.rows != null) {
                (0..2).forEach { i ->
                    assertTrue("${l.tag} row $i empty", l.rowKeys(i, false).isNotEmpty())
                }
                val keys = (0..2).flatMap { l.rowKeys(it, false) }
                assertEquals("${l.tag} duplicate keys", keys.size, keys.toSet().size)
            }
        }
    }

    @Test fun keysBelongToTheirScript() {
        fun cp(l: String) = Languages.forTag(l).let { lang ->
            (0..2).flatMap {
                lang.rowKeys(it, false) +
                    lang.rowKeys(it, true)
            }.joinToString("")
        }
        fun inRange(s: String, lo: Int, hi: Int) = s.filter { !it.isWhitespace() }.all {
            it.code in
                lo..hi ||
                it.code in 0x0300..0x036F
        }
        assertTrue(cp("he").all { it.code in 0x05D0..0x05EA })
        assertTrue(inRange(cp("hi"), 0x0900, 0x097F))
        assertTrue(inRange(cp("gu"), 0x0A80, 0x0AFF))
        assertTrue(inRange(cp("ta"), 0x0B80, 0x0BFF))
        assertTrue(inRange(cp("te"), 0x0C00, 0x0C7F))
        assertTrue(cp("ko").all { it.code in 0x3130..0x318F })
    }

    @Test fun arabicHasHamzaFormsAndShiftLayerAndNoCaseShiftForHebrew() {
        val ar = Languages.forTag("ar")
        assertTrue(ar.hasShift)
        assertTrue("لا" in ar.rowKeys(2, false))
        assertTrue("َ" in ar.rowKeys(0, true))
        assertFalse(Languages.forTag("he").hasShift)
        assertTrue(Languages.forTag("ru").hasShift)
        assertTrue(Languages.English.hasShift)
        assertTrue(Languages.forTag("ko").hasShift)
    }

    @Test fun indicLayersCoverCoreLettersAndSigns() {
        fun all(tag: String) = Languages.forTag(tag).let { l ->
            (0..2).flatMap {
                l.rowKeys(it, false) +
                    l.rowKeys(it, true)
            }
        }
        val hi = all("hi")
        listOf("क", "ख", "ग", "अ", "आ", "ा", "ि", "्", "ं", "श", "ष", "ज्ञ").filter {
            it != "ज्ञ"
        }.forEach {
            assertTrue(
                "hi $it",
                it in hi
            )
        }
        assertTrue("ப" in all("ta") && "ழ" in all("ta") && "்" in all("ta") && "ஃ" in all("ta"))
        assertTrue("క" in all("te") && "ె" in all("te") && "ొ" in all("te") && "్" in all("te"))
        assertTrue("ક" in all("gu") && "્" in all("gu") && "ૐ" in all("gu"))
    }

    @Test fun engineAssignments() {
        assertEquals(Engine.Hangul, Languages.forTag("ko").engine)
        assertEquals(Engine.Kana, Languages.forTag("ja").engine)
        assertEquals(Engine.Pinyin, Languages.forTag("zh").engine)
        assertEquals(Engine.Direct, Languages.forTag("fr").engine)
    }

    @Test fun resolverComposesPlainLettersOnlyForComposingLanguages() {
        val t0 = 1000L
        val r = Resolver().also { it.language = Languages.forTag("zh") }
        val ctx = FieldCtx(InputClass.Text, before = "x", autoCapAllowed = true)
        assertEquals(
            listOf<Out>(Out.Compose('n', false)),
            r.onKey(KeyEv(Key.Letter('n'), true, t0), ctx)
        )
        assertEquals(
            listOf<Out>(Out.Consume),
            r.onKey(KeyEv(Key.Letter('n'), true, t0 + 500, 1), ctx)
        )
        val d = Resolver()
        assertEquals(listOf<Out>(Out.Commit("n")), d.onKey(KeyEv(Key.Letter('n'), true, t0), ctx))
    }

    private fun letters(tag: String): Set<String> = Languages.forTag(tag).let { l ->
        (
            (0..2).flatMap {
                l.rowKeys(
                    it,
                    false
                ) + l.rowKeys(it, true)
            } + l.more.values.flatten() +
                l.accents.values.flatMap { v -> v.map { it.toString() } }
            ).toSet()
    }

    @Test fun alphabetsAreComplete() {
        // Letters every user of the language needs, reachable by a key or a long-press
        // (cross-checked against AnySoftKeyboard).
        val ru = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя".map { it.toString() }
        val uk = "абвгґдеєжзиіїйклмнопрстуфхцчшщьюя".map { it.toString() }
        val el = "αβγδεζηθικλμνξοπρσςτυφχψω".map { it.toString() }
        val ar =
            "ابتثجحخدذرزسشصضطظعغفقكلمنهوي".map { it.toString() } +
                listOf("ة", "ى", "ء", "ئ", "ؤ", "أ".let { "ا" })
        val he = "אבגדהוזחטיכלמנסעפצקרשתךםןףץ".map { it.toString() }
        mapOf("ru" to ru, "uk" to uk, "el" to el, "ar" to ar, "he" to he).forEach { (tag, need) ->
            val have = letters(tag)
            need.forEach { assertTrue("$tag missing $it", it in have) }
        }
    }

    @Test fun hindiCoversIndicKeyboardInscriptKeys() {
        // Every character on Indic Keyboard's Devanagari InScript rows 2-4, plus its conjunct and nukta long-presses.
        val need = (
            "ौ ै ा ी ू ब ह ग द ज ड ़ ॉ औ ऐ आ ई ऊ भ ङ घ ध झ ढ ञ ऑ ो े ् ि ु प र क त च ट ओ ए अ इ " +
                "उ फ ऱ ख थ छ ठ ॆ ं म न व ल स य " +
                "ऎ ँ ण ऩ ऴ ळ श ष ः ऋ ृ ऒ ऍ ॅ ॊ क्ष त्र ज्ञ श्र"
            ).split(' ')
        val have = letters("hi")
        need.forEach { assertTrue("hi missing $it", it in have) }
    }

    @Test fun dravidianLongAndShortVowelsBothPresent() {
        val ta = letters("ta")
        val te = letters("te")
        listOf("எ", "ஏ", "ஒ", "ஓ", "ெ", "ே", "ொ", "ோ", "ழ", "ற", "ஃ").forEach {
            assertTrue(
                "ta $it",
                it in ta
            )
        }
        listOf("ఎ", "ఏ", "ఒ", "ఓ", "ె", "ే", "ొ", "ో", "ళ", "ఱ").forEach {
            assertTrue(
                "te $it",
                it in te
            )
        }
    }

    /**
     * Every key on Indic Keyboard's InScript rows 2-4 for these scripts
     * (github.com/smc/Indic-Keyboard, Apache-2.0).
     */
    private val indicKeyboardInscript = mapOf(
        "bn" to
            "ৌ ৈ া ী ূ ব হ গ দ জ ড ় ঔ ঐ আ ঈ ঊ ভ ঙ ঘ ধ ঝ ঢ ঞ ো ে ্ ি ু প র ক ত চ ট ও এ অ ই উ ফ " +
            "ৎ খ থ ছ ঠ ং ম ন ল স য় ঁ ণ শ ষ য",
        "pa" to
            "ੌ ੈ ਾ ੀ ੂ ਬ ਹ ਗ ਦ ਜ ਡ ਼ ਔ ਐ ਆ ਈ ਊ ਭ ਙ ਘ ਧ ਝ ਢ ਞ ੋ ੇ ੍ ਿ ੁ ਪ ਰ ਕ ਤ ਚ ਟ ਓ ਏ ਅ ਇ ਉ ਫ " +
            "ੜ ਖ ਥ ਛ ਠ ੰ ਜ਼ ਮ ਨ ਵ ਲ ਸ ਯ ੱ ਫ਼ ਣ ਂ ਲ਼ ਸ਼ ੳ ੲ",
        "kn" to
            "ೌ ೈ ಾ ೀ ೂ ಬ ಹ ಗ ದ ಜ ಡ ಼ ಔ ಐ ಆ ಈ ಊ ಭ ಙ ಘ ಧ ಝ ಢ ಞ ೋ ೇ ್ ಿ ು ಪ ರ ಕ ತ ಚ ಟ ಓ ಏ ಅ ಇ ಉ ಫ " +
            "ಱ ಖ ಥ ಛ ಠ ೆ ಂ ಮ ನ ವ ಲ ಸ ಯ ಎ ಣ ೞ ಳ ಶ ಷ",
        "ml" to
            "ൗ ൈ ാ ീ ൂ ബ ഹ ഗ ദ ജ ഡ ഔ ഐ ആ ഈ ഊ ഭ ങ ഘ ധ ഝ ഢ ഞ ർ ോ േ ് ി ു പ ര ക ത ച ട ഓ ഏ അ ഇ ഉ ഫ " +
            "റ ഖ ഥ ഛ ഠ െ ം മ ന വ ല സ യ എ ൺ ണ ൻ ഴ ള ശ ഷ ൽ"
    )

    @Test fun newIndicLayoutsCoverIndicKeyboardAndTheirScripts() {
        val ranges = mapOf(
            "bn" to (0x0980..0x09FF),
            "pa" to (0x0A00..0x0A7F),
            "kn" to (0x0C80..0x0CFF),
            "ml" to (0x0D00..0x0D7F)
        )
        indicKeyboardInscript.forEach { (tag, keys) ->
            val have = letters(tag)
            keys.split(' ').forEach {
                assertTrue(
                    "$tag missing $it",
                    it in have
                )
            }
            val l = Languages.forTag(tag)
            (0..2).flatMap { l.rowKeys(it, false) + l.rowKeys(it, true) }.joinToString("").forEach {
                assertTrue(
                    "$tag stray $it",
                    it.code in ranges.getValue(tag)
                )
            }
        }
    }

    @Test fun urduHasTheFullAlphabetAndNoShift() {
        val have = letters("ur")
        ("ابپتٹثجچحخدڈذرڑزژسشصضطظعغفقکگلمنںوہھءیے" + "آ").forEach {
            assertTrue(
                "ur missing $it",
                it.toString() in have
            )
        }
        assertFalse(Languages.forTag("ur").hasShift)
    }

    @Test fun marathiAndSanskritShareDevanagariWithTheirExtras() {
        assertTrue("ॲ" in letters("mr"))
        assertTrue("॑" in letters("sa"))
        assertTrue("ळ" in letters("mr"))
        assertEquals(Languages.forTag("hi").rows, Languages.forTag("mr").rows)
    }

    @Test fun everyIndicLanguageHasInscriptAndPhoneticLayouts() {
        listOf("hi", "mr", "sa", "gu", "bn", "pa", "ta", "te", "kn", "ml").forEach { t ->
            assertEquals(Engine.Direct, Languages.forTag(t).engine)
            assertEquals(Engine.Phonetic, Languages.forTag(t, "phonetic").engine)
        }
    }
}
