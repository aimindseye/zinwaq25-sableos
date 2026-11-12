package org.sableos.browser

import org.junit.Assert.assertEquals
import org.junit.Test
import org.sableos.browser.ui.standaloneUserAgent

class UserAgentTest {
    @Test
    fun dropsTheInAppWebViewMarks() {
        val webView =
            "Mozilla/5.0 (Linux; Android 17; Q25 Build/BP4A; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Version/4.0 Chrome/140.0.0.0 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 17; Q25 Build/BP4A) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/140.0.0.0 Mobile Safari/537.36",
            standaloneUserAgent(webView),
        )
    }
}
