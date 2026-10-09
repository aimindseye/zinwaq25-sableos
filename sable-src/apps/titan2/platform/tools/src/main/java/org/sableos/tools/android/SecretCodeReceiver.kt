package org.sableos.tools.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.sableos.tools.core.Capability
import org.sableos.tools.core.DialerCodes
import org.sableos.tools.core.Tool
import org.sableos.tools.ui.ToolActivity

/**
 * *#*#3377#*#* compatibility bridge (HARDWARE_DIAGNOSTICS_AND_DIALER_CODES). Opens Sable hardware test categories,
 * or the factory test bridge screen when the profile and developer mode allow it. It never launches a raw vendor
 * factory surface (RAW_FACTORY_TEST_DIRECT_LAUNCH=NO_BY_DEFAULT).
 */
class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (DialerCodes.secretCodeFromUri(intent.dataString) !=
            DialerCodes.FACTORY_TEST_CODE
        ) {
            return
        }
        val env = ToolsEnv(context)
        val decision = DialerCodes.bridge(
            env.resolutions.getValue(Capability.FACTORY_TEST_BRIDGE),
            env.developerMode
        )
        val tool = if (decision.opensBridge) Tool.FACTORY_BRIDGE else Tool.HARDWARE_TESTS
        context.startActivity(
            ToolActivity.intent(context, tool)
                .putExtra(ToolActivity.EXTRA_NOTICE, decision.reason)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
