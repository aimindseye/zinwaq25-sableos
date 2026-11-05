package org.sableos.titan2.spaces.android

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Sable Spaces as the Work space's profile owner. It asks for no device admin policy. */
class SpacesAdminReceiver : DeviceAdminReceiver() {
    /** Sent inside the new Work space when Android's setup has made it. */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        WorkProfileSetup.finish(context)
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context, SpacesAdminReceiver::class.java)
    }
}
