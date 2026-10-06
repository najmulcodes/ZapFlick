package com.najmulcodes.zapflick.domain.engine

import com.najmulcodes.zapflick.domain.settings.YtDlpChannel

sealed interface YtDlpUpdate {
    val version: String?

    data class Updated(override val version: String?) : YtDlpUpdate
    data class AlreadyLatest(override val version: String?) : YtDlpUpdate
}

/** Keeps the bundled yt-dlp current: sites change often and old versions stop working. */
interface YtDlpUpdater {
    /** The installed yt-dlp version, or null if it cannot be read yet. */
    suspend fun currentVersion(): String?

    /** Failures are [Result.failure]; coroutine cancellation is rethrown. */
    suspend fun update(channel: YtDlpChannel): Result<YtDlpUpdate>
}
