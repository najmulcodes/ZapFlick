package com.najmulcodes.zapflick.domain.usecase

import com.najmulcodes.zapflick.domain.engine.DownloadEngine
import com.najmulcodes.zapflick.domain.model.DownloadError
import com.najmulcodes.zapflick.domain.model.DownloadException
import com.najmulcodes.zapflick.domain.model.VideoMetadata
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
