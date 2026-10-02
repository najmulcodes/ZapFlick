package com.najmulcodes.zapflick.domain.usecase

import com.najmulcodes.zapflick.domain.engine.DownloadEngine
import com.najmulcodes.zapflick.domain.model.AvailableFormats
import com.najmulcodes.zapflick.domain.model.DownloadError
import com.najmulcodes.zapflick.domain.model.DownloadException
import com.najmulcodes.zapflick.domain.model.DownloadProgress
import com.najmulcodes.zapflick.domain.model.DownloadRequest
import com.najmulcodes.zapflick.domain.model.VideoMetadata
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private val SAMPLE = VideoMetadata(
    sourceUrl = "https://example.com/v",
    title = "Sample",
    thumbnailUrl = null,
    durationSeconds = 65,
    uploader = "Someone",
)

private class FakeEngine(
    var result: Result<VideoMetadata> = Result.success(SAMPLE),
) : DownloadEngine {
    var fetchedUrl: String? = null

    override suspend fun initialize() = Unit

    override suspend fun fetchMetadata(url: String): Result<VideoMetadata> {
        fetchedUrl = url
        return result
    }

    override suspend fun fetchFormats(url: String): Result<AvailableFormats> =
        Result.failure(UnsupportedOperationException())

    override suspend fun download(
        request: DownloadRequest,
        workDir: File,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<File> = Result.failure(UnsupportedOperationException())
}

class FetchMetadataUseCaseTest {

    @Test
    fun `input without a url fails with InvalidUrl and never calls the engine`() = runTest {
        val engine = FakeEngine()
        val result = FetchMetadataUseCase(engine, ExtractUrlUseCase())("no link here")

        val error = (result.exceptionOrNull() as DownloadException).error
        assertEquals(DownloadError.InvalidUrl, error)
        assertNull(engine.fetchedUrl)
    }

    @Test
    fun `share text is reduced to the url before fetching`() = runTest {
        val engine = FakeEngine()
        val result = FetchMetadataUseCase(engine, ExtractUrlUseCase())("Look: https://example.com/v!")

        assertTrue(result.isSuccess)
        assertEquals("https://example.com/v", engine.fetchedUrl)
        assertEquals(SAMPLE, result.getOrNull())
    }

    @Test
    fun `engine failures are passed through unchanged`() = runTest {
        val engine = FakeEngine(Result.failure(DownloadException(DownloadError.GeoBlocked)))
        val result = FetchMetadataUseCase(engine, ExtractUrlUseCase())("https://example.com/v")

        assertEquals(DownloadError.GeoBlocked, (result.exceptionOrNull() as DownloadException).error)
    }
}
