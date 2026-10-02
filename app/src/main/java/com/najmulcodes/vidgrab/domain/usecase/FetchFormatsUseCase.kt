package com.najmulcodes.vidgrab.domain.usecase

import com.najmulcodes.vidgrab.domain.engine.DownloadEngine
import com.najmulcodes.vidgrab.domain.model.AvailableFormats
import com.najmulcodes.vidgrab.domain.model.DownloadError
import com.najmulcodes.vidgrab.domain.model.DownloadException
import javax.inject.Inject

class FetchFormatsUseCase @Inject constructor(
    private val engine: DownloadEngine,
) {
    suspend operator fun invoke(url: String): Result<AvailableFormats> {
        if (url.isBlank()) return Result.failure(DownloadException(DownloadError.InvalidUrl))
        return engine.fetchFormats(url)
    }
}
