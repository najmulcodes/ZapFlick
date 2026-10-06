package com.najmulcodes.zapflick.domain.network

enum class NetworkKind { NONE, UNMETERED, METERED }

/** What the Wi-Fi-only controller should do when the "downloads blocked" state changes. */
enum class GateAction { NONE, PAUSE_RUNNING, RESUME_PAUSED }

object WifiOnlyPolicy {
    /** Offline is not "blocked": the downloads simply fail or wait on their own. Only mobile data is held back. */
    fun isBlocked(wifiOnly: Boolean, network: NetworkKind): Boolean =
        wifiOnly && network == NetworkKind.METERED

    fun actionFor(wasBlocked: Boolean, nowBlocked: Boolean): GateAction = when {
        !wasBlocked && nowBlocked -> GateAction.PAUSE_RUNNING
        wasBlocked && !nowBlocked -> GateAction.RESUME_PAUSED
        else -> GateAction.NONE
    }
}
