package org.sableos.hub.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.sableos.hub.AttentionSettingsActivity
import org.sableos.hub.ConnectedAppsActivity
import org.sableos.hub.policy.HubIntents
import org.sableos.hub.policy.SettingsRoute

/** Starts a [SettingsRoute]: the first Android Settings action that resolves, or a Hub screen. */
class AndroidSettingsLauncher(
    private val context: Context,
) {
    fun open(
        route: SettingsRoute,
        packageName: String? = null,
        appUid: Int? = null,
    ): Boolean =
        when (route) {
            is SettingsRoute.Android -> openAndroid(route)
            SettingsRoute.HubConnectedApp -> start(hubAppIntent(packageName, appUid))
            SettingsRoute.SableAttention -> start(Intent(context, AttentionSettingsActivity::class.java))
        }

    private fun openAndroid(route: SettingsRoute.Android): Boolean =
        route.actions.any { action ->
            val intent =
                Intent(action).apply {
                    route.stringExtras.forEach { (key, value) -> putExtra(key, value) }
                    route.intExtras.forEach { (key, value) -> putExtra(key, value) }
                }
            val resolves =
                context.packageManager
                    .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    .isNotEmpty()
            resolves && start(intent)
        }

    private fun hubAppIntent(
        packageName: String?,
        appUid: Int?,
    ): Intent =
        Intent(context, ConnectedAppsActivity::class.java).apply {
            packageName?.let { putExtra(HubIntents.EXTRA_APP_PACKAGE, it) }
            appUid?.let { putExtra(HubIntents.EXTRA_APP_UID, it) }
        }

    private fun start(intent: Intent): Boolean {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // ActivityNotFoundException / SecurityException: the page is missing or not exported.
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
