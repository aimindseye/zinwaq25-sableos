package org.sableos.titan2.spaces.android

import android.app.Activity
import android.os.Bundle

/** Android's setup hands over the new Work space here; there is no policy to agree to, so finish it. */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WorkProfileSetup.finish(this)
        setResult(RESULT_OK)
        finish()
    }
}
