package org.sableos.titan2.displaycompat.android

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.MediaStore
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import org.sableos.titan2.displaycompat.core.AppTraits
import org.sableos.titan2.displaycompat.core.TraitHeuristics

/** Builds [AppTraits] from public PackageManager queries only. Names are heuristics and only ever add protection. */
class TraitsResolver(private val context: Context) {
    private val pm = context.packageManager
    private fun pkgs(i: Intent) =
        pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet()
    private val home = pkgs(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    private val dialer = pkgs(Intent(Intent.ACTION_DIAL))
    private val camera = pkgs(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
    private val ime = (
        context.getSystemService(InputMethodManager::class.java)?.inputMethodList
            ?: emptyList()
        ).map { it.packageName }.toSet()
    private val a11y = (
        context.getSystemService(
            AccessibilityManager::class.java
        )?.installedAccessibilityServiceList
            ?: emptyList()
        )
        .map { it.resolveInfo.serviceInfo.packageName }.toSet()

    fun resolve(pkg: String): AppTraits {
        val cat = try {
            pm.getApplicationInfo(pkg, 0).category
        } catch (
            _: Exception
        ) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        return AppTraits(
            packageName = pkg, isHome = pkg in home, isDialer = pkg in dialer,
            isInputMethod =
                pkg in ime,
            isAccessibilityService = pkg in a11y,
            isSystemUiOrSettings = TraitHeuristics.isProtectedSystemPackage(
                pkg
            ),
            isAuthenticatorOrPayment = TraitHeuristics.nameLooksSensitive(pkg),
            isCamera =
                pkg in camera,
            isMedia = cat == ApplicationInfo.CATEGORY_VIDEO || cat == ApplicationInfo.CATEGORY_AUDIO
        )
    }

    data class Entry(val pkg: String, val label: String)

    fun launchable(): List<Entry> =
        pkgs(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)).mapNotNull { p ->
            try {
                Entry(p, pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString())
            } catch (
                _: Exception
            ) {
                null
            }
        }.sortedBy { it.label.lowercase() }
}
