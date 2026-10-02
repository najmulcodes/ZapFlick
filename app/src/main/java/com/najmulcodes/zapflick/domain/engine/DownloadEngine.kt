package com.najmulcodes.zapflick.domain.engine

import com.najmulcodes.zapflick.domain.model.AvailableFormats
import com.najmulcodes.zapflick.domain.model.DownloadProgress
import com.najmulcodes.zapflick.domain.model.DownloadRequest
import com.najmulcodes.zapflick.domain.model.VideoMetadata
import java.io.File

/**
 * Everything the app needs from a downloader. The yt-dlp implementation lives in the data layer;
 * tests use fakes. Failures are [Result.failure] carrying a
 * [com.najmulcodes.zapflick.domain.model.DownloadException]; coroutine cancellation is rethrown, never wrapped.
 */
interface DownloadEngine {
    /** Unpacks the bundled binaries on first use. Safe to call repeatedly. */
    suspend fun initialize()

    suspend fun fetchMetadata(url: String): Result<VideoMetadata>

    /** Lists the resolutions and audio streams the site offers, with estimated sizes. */
    suspend fun fetchFormats(url: String): Result<AvailableFormats>

    /** Downloads into [workDir] and returns the finished file. */
    suspend fun download(
        request: DownloadRequest,
        workDir: File,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<File>
}
