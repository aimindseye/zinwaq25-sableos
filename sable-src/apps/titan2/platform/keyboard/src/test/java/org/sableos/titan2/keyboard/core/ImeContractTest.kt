package org.sableos.titan2.keyboard.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeContractTest {
    private val sable = "org.sableos.titan2.keyboard/.android.SableImeService"
    private val other = "com.example.other/.OtherIme"

    // --- component identity ---

    @Test fun componentIsThePackageAndTheService() {
        assertEquals("org.sableos.titan2.keyboard/.android.SableImeService", ImeContract.COMPONENT)
        assertEquals(
            "org.sableos.titan2.keyboard.android.SableImeService",
            ImeContract.SERVICE_CLASS
        )
    }

    @Test fun bothFlattenedFormsMatch() {
        assertTrue(ImeContract.matches(sable))
        assertTrue(
            ImeContract.matches(
                "org.sableos.titan2.keyboard/org.sableos.titan2.keyboard.android.SableImeService"
            )
        )
        assertTrue(ImeContract.matches("  $sable  "))
    }

    @Test fun lookAlikesAndGarbageDoNotMatch() {
        listOf(
            null,
            "",
            "   ",
            "org.sableos.titan2.keyboard",
            "org.sableos.titan2.keyboardx/.android.SableImeService",
            "org.sableos.titan2.keyboard/.android.OtherService",
            "org.sableos.titan2.keyboard/.android.SableImeService.Fake",
            "x/org.sableos.titan2.keyboard/.android.SableImeService",
            other
        ).forEach { assertFalse(it.toString(), ImeContract.matches(it)) }
    }

    // --- product policy ---

    @Test fun userChoiceIsNeverForced() {
        assertFalse(ImeContract.FORCE_AFTER_USER_SELECTION)
        assertTrue(ImeContract.USER_SELECTABLE_THIRD_PARTY_IME)
        assertTrue(ImeContract.FACTORY_DEFAULT_ONLY)
        assertFalse(ImeStatusModel.mayChangeSelection())
    }

    @Test fun firstUnlockTypingNeedsNoCredentialProtectedState() {
        assertFalse(ImeContract.CREDENTIAL_PROTECTED_STATE_REQUIRED)
    }

    // --- observation model ---

    @Test fun sableSelectedWinsEvenWithoutAnEnabledList() {
        assertEquals(ImeStatus.SableSelected, ImeStatusModel.evaluate(sable, null))
        assertEquals(ImeStatus.SableSelected, ImeStatusModel.evaluate(sable, emptyList()))
    }

    @Test fun anotherSelectedKeyboardIsPreservedNotCalledWrong() {
        val s = ImeStatusModel.evaluate(other, listOf(other, sable))
        assertEquals(ImeStatus.SableEnabledOtherSelected, s)
        val text = ImeStatusModel.describe(s).lowercase()
        assertTrue(text.contains("your choice is kept"))
        assertFalse(text.contains("wrong") || text.contains("incorrect") || text.contains("fix"))
    }

    @Test fun sableMissingFromTheEnabledListIsNotEnabled() {
        assertEquals(ImeStatus.SableNotEnabled, ImeStatusModel.evaluate(other, listOf(other)))
    }

    @Test fun anUnreadableSelectionOrListIsUnknownNotNotSelected() {
        assertEquals(ImeStatus.Unknown, ImeStatusModel.evaluate(null, listOf(other)))
        assertEquals(ImeStatus.Unknown, ImeStatusModel.evaluate("", listOf(sable)))
        assertEquals(ImeStatus.Unknown, ImeStatusModel.evaluate(other, null))
    }

    @Test fun everyStatusHasADistinctHonestDescription() {
        val all = ImeStatus.values().map { ImeStatusModel.describe(it) }
        assertEquals(all.size, all.toSet().size)
        all.forEach { assertTrue(it.length < 120) }
    }

    @Test fun descriptionsNeverPromiseOrClaimForcing() {
        ImeStatus.values().map { ImeStatusModel.describe(it).lowercase() }.forEach {
            assertFalse(
                it,
                it.contains("will switch") || it.contains("automatically") || it.contains("forced")
            )
        }
    }

    // --- actions: only screens the user confirms in ---

    @Test fun selectedKeyboardOffersNoAction() {
        assertTrue(ImeStatusModel.actions(ImeStatus.SableSelected).isEmpty())
    }

    @Test fun eachStatusOffersTheMatchingScreens() {
        assertEquals(
            listOf(ImeAction.ShowPicker),
            ImeStatusModel.actions(ImeStatus.SableEnabledOtherSelected)
        )
        assertEquals(
            listOf(ImeAction.OpenInputMethodSettings),
            ImeStatusModel.actions(ImeStatus.SableNotEnabled)
        )
        assertEquals(2, ImeStatusModel.actions(ImeStatus.Unknown).size)
    }

    // --- bounded launch failures ---

    @Test fun successfulOpenShowsNothing() {
        assertNull(ImeLaunchReport.message(ImeAction.ShowPicker, ImeLaunchOutcome.Launched))
    }

    @Test fun everyFailureIsOneBoundedLineNamingTheScreen() {
        ImeLaunchOutcome.values().filter { it != ImeLaunchOutcome.Launched }.forEach { o ->
            ImeAction.values().forEach { a ->
                val m = ImeLaunchReport.message(a, o)
                assertNotNull(m)
                assertTrue(m!!.length <= ImeLaunchReport.MAX_MESSAGE)
                assertTrue(m.contains(a.label))
            }
        }
    }

    @Test fun missingAndBlockedAreDistinguished() {
        val a = ImeLaunchReport.message(ImeAction.ShowPicker, ImeLaunchOutcome.TargetMissing)
        val b = ImeLaunchReport.message(ImeAction.ShowPicker, ImeLaunchOutcome.Denied)
        assertTrue(a != b)
    }

    // --- first-boot source requirements, read from the real manifest and sources ---

    private fun keyboardSrc(): File {
        var d: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (d != null) {
            listOf(File(d, "apps/titan2/platform/keyboard/src"), File(d, "src")).forEach {
                if (File(
                        it,
                        "main/AndroidManifest.xml"
                    ).readTextOrEmpty().contains("SableImeService")
                ) {
                    return it
                }
            }
            d = d.parentFile
        }
        error("keyboard sources not found")
    }

    private fun File.readTextOrEmpty() = if (isFile) readText() else ""

    private val manifest get() = File(keyboardSrc(), "main/AndroidManifest.xml").readText()

    private fun mainKt(): List<File> = File(keyboardSrc(), "main").walkTopDown().filter {
        it.isFile && it.name.endsWith(".kt")
    }.toList()

    @Test fun applicationAndImeServiceAreDirectBootAware() {
        assertTrue(
            Regex("<application[^>]*android:directBootAware=\"true\"").containsMatchIn(manifest)
        )
        val service = Regex("<service[^>]*>", RegexOption.DOT_MATCHES_ALL).find(manifest)!!.value
        assertTrue(service.contains("android:directBootAware=\"true\""))
        assertTrue(service.contains("android:permission=\"android.permission.BIND_INPUT_METHOD\""))
        assertTrue(service.contains("android:name=\".android.SableImeService\""))
    }

    @Test fun settingsActivityIsDirectBootAwareAndNoPermissionIsRequested() {
        val act = Regex(
            "<activity[^>]*KeyboardSettingsActivity[^>]*>",
            RegexOption.DOT_MATCHES_ALL
        ).find(manifest)!!
        assertTrue(act.value.contains("android:directBootAware=\"true\""))
        assertFalse(manifest.contains("<uses-permission"))
    }

    @Test fun preferencesLiveInDeviceProtectedStorageOnly() {
        val prefs = mainKt().first { it.name == "KeyboardPrefs.kt" }.readText()
        assertTrue(prefs.contains("createDeviceProtectedStorageContext()"))
        mainKt().filter { it.name != "KeyboardPrefs.kt" }.forEach {
            assertFalse(it.name, it.readText().contains("getSharedPreferences("))
        }
    }

    @Test fun noCredentialProtectedOrPrivilegedApiIsUsed() {
        val put = "pu" + "t"
        val banned =
            listOf(
                "filesDir", "openFileInput", "openFileOutput", "getDatabasePath",
                "SQLiteDatabase", "externalCacheDir", "Settings.Secure.$put",
                "Settings.Global.$put", "AccessibilityService", "Shi" + "zuku",
                "Runtime.getRuntime", "Process" + "Builder", "setInputMethod"
            )
        mainKt().filter { it.name != "ImeLauncher.kt" }.forEach { f ->
            val text = f.readText()
            banned.forEach { assertFalse("${f.name}: $it", text.contains(it)) }
        }
    }

    @Test fun inputMethodSettingsAreOnlyReadNeverWritten() {
        val launcher = mainKt().first { it.name == "ImeLauncher.kt" }.readText()
        assertTrue(launcher.contains("Settings.Secure.getString"))
        assertFalse(
            Regex("Settings\\.(Secure|Global|System)\\.(put|set)").containsMatchIn(launcher)
        )
    }

    @Test fun methodXmlDeclaresTheSettingsActivityAndSubtypes() {
        val m = File(keyboardSrc(), "main/res/xml/method.xml").readText()
        assertTrue(m.contains("org.sableos.titan2.keyboard.android.KeyboardSettingsActivity"))
        assertTrue(m.contains("imeSubtypeLocale=\"en_US\""))
    }

    @Test fun softKeyboardFallbackIsStillWired() {
        val svc = mainKt().first { it.name == "SableImeService.kt" }.readText()
        assertTrue(svc.contains("FieldPolicy.softKeyboardWanted("))
        assertTrue(svc.contains("super.onEvaluateInputViewShown()"))
        assertTrue(svc.contains("fun onCreateInputView(): View"))
    }
}
