package com.najmulcodes.vidgrab.domain.usecase

import com.najmulcodes.vidgrab.domain.engine.DownloadEngine
import com.najmulcodes.vidgrab.domain.model.DownloadError
import com.najmulcodes.vidgrab.domain.model.DownloadException
import com.najmulcodes.vidgrab.domain.model.VideoMetadata
import javax.inject.Inject

class FetchMetadataUseCase @Inject constructor(
    private val engine: DownloadEngine,
    private val extractUrl: ExtractUrlUseCase,
) {
    suspend operator fun invoke(input: String): Result<VideoMetadata> {
        val url = extractUrl(input)
            ?: return Result.failure(DownloadException(DownloadError.InvalidUrl))
        return engine.fetchMetadata(url)
    }
}
