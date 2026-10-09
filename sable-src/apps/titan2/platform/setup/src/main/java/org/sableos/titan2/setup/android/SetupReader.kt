package org.sableos.titan2.setup.android

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import org.sableos.titan2.setup.core.Observed
import org.sableos.titan2.setup.core.Snapshot

/** Reads the current defaults through public APIs only. A failed read is [Observed.Failed], never "none". */
class SetupReader(private val context: Context) {
    fun snapshot(): Snapshot = Snapshot(home(), keyboard(), dialer(), sms())

    private fun home(): Observed = observe {
        context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0
        )?.activityInfo?.packageName
    }

    private fun keyboard(): Observed = observe {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    }

    private fun dialer(): Observed {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return Observed.Failed
        return observe { telecom.defaultDialerPackage }
    }

    private fun sms(): Observed = observe { Telephony.Sms.getDefaultSmsPackage(context) }

    private fun observe(read: () -> String?): Observed = try {
        val v = read()
        if (v.isNullOrBlank()) Observed.Absent else Observed.Value(v)
    } catch (_: SecurityException) {
        Observed.Failed
    } catch (_: RuntimeException) {
        Observed.Failed
    }
}
