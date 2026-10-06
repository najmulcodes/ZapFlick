package com.najmulcodes.zapflick.domain.browser

object UserAgents {
    private val androidPlatform = Regex("""\((?:Linux; )?Android[^)]*\)""")

    /**
     * Turns a phone browser's user agent into the desktop one for the same browser, so "Desktop
     * site" asks for the full page: the Android platform part becomes X11 Linux, and "Mobile" goes.
     */
    fun toDesktop(mobile: String): String =
        mobile
            .replace(androidPlatform, "(X11; Linux x86_64)")
            .replace(" Mobile", "")

    /** Without the "; wv" marker that makes Google and others refuse logins or serve a stripped page. */
    fun withoutWebViewMarker(agent: String): String = agent.replace("; wv", "")
}
