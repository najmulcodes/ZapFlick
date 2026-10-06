package com.najmulcodes.zapflick.domain.browser

import java.util.Locale

/**
 * A set of ad and tracker hosts. A request is blocked when its host, or any parent domain of it
 * (but never a bare top-level domain), is listed: "ads.example.com" blocks "x.ads.example.com".
 */
class HostBlocklist(private val hosts: Set<String>) {

    val size: Int get() = hosts.size

    fun isBlocked(host: String?): Boolean {
        var current = host?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT).orEmpty()
        if (current.isEmpty()) return false
        while (true) {
            if (current in hosts) return true
            val parent = current.substringAfter('.', missingDelimiterValue = "")
            if ('.' !in parent) return false
            current = parent
        }
    }

    companion object {
        val EMPTY = HostBlocklist(emptySet())

        private val ADDRESS_FIELDS = setOf("0.0.0.0", "127.0.0.1", "::1", "::")
        private val NOT_ADS = setOf(
            "localhost", "localhost.localdomain", "local", "broadcasthost", "ip6-localhost",
            "ip6-loopback", "ip6-localnet", "ip6-mcastprefix", "ip6-allnodes", "ip6-allrouters", "ip6-allhosts",
        )

        /**
         * Reads a hosts file ("0.0.0.0 ads.example.com"), or a plain list with one domain per line.
         * Comments, blank lines and local-machine entries are skipped.
         */
        fun parse(lines: Sequence<String>): HostBlocklist {
            val hosts = HashSet<String>()
            for (raw in lines) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val parts = line.split(WHITESPACE).filter { it.isNotEmpty() }
                val candidate = when {
                    parts.size >= 2 && parts[0] in ADDRESS_FIELDS -> parts[1]
                    parts.size == 1 && '.' in parts[0] -> parts[0]
                    else -> continue
                }.lowercase(Locale.ROOT).trimEnd('.')
                if (candidate.isEmpty() || candidate in NOT_ADS || candidate in ADDRESS_FIELDS) continue
                if ('.' !in candidate) continue
                hosts += candidate
            }
            return HostBlocklist(hosts)
        }

        private val WHITESPACE = Regex("\\s+")
    }
}
