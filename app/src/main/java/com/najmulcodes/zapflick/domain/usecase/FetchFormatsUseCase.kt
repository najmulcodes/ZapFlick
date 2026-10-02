package com.najmulcodes.zapflick.domain.usecase

import com.najmulcodes.zapflick.domain.engine.DownloadEngine
import com.najmulcodes.zapflick.domain.model.AvailableFormats
import com.najmulcodes.zapflick.domain.model.DownloadError
import com.najmulcodes.zapflick.domain.model.DownloadException
import javax.inject.Inject

class FetchFormatsUseCase @Inject constructor(
    private val engine: DownloadEngine,
) {
    suspend operator fun invoke(url: String): Result<AvailableFormats> {
        if (url.isBlank()) return Result.failure(DownloadException(DownloadError.InvalidUrl))
        return engine.fetchFormats(url)
    }
}
