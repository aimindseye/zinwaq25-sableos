package org.sableos.titan2.spaces.android

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import org.sableos.titan2.spaces.core.SpacesText.t
import org.sableos.titan2.spaces.core.WorkSpace

/**
 * Finishes a new Work space from inside it: names it Work, lets the main user's Sable Spaces reach
 * this copy for adding apps, its options and removing the space, and turns the profile on. Nothing else
 * is set: every option starts as Allowed.
 * Safe to run twice (the setup broadcast and the policy compliance screen both call it).
 */
object WorkProfileSetup {
    private const val TAG = "SableSpaces"

    fun finish(context: Context) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (dpm == null || !dpm.isProfileOwnerApp(context.packageName)) {
            Log.w(TAG, "Not the Work space's profile owner; nothing to finish")
            return
        }
        val admin = SpacesAdminReceiver.component(context)
        try {
            dpm.setProfileName(admin, t("work"))
            listOf(
                WorkSpace.ACTION_ADD_APP,
                WorkSpace.ACTION_REMOVE,
                WorkSpace.ACTION_OPTIONS
            ).forEach { action ->
                val filter = IntentFilter(action).apply { addCategory(Intent.CATEGORY_DEFAULT) }
                dpm.addCrossProfileIntentFilter(
                    admin,
                    filter,
                    DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
                )
            }
            listOf(InWorkActivity::class.java, WorkOptionsActivity::class.java).forEach { screen ->
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(context, screen),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            dpm.setProfileEnabled(admin)
        } catch (e: SecurityException) {
            Log.w(TAG, "Android refused part of the Work space setup", e)
        }
    }
}
