package com.najmulcodes.zapflick.data.ytdlp

import android.content.Context
import com.najmulcodes.zapflick.domain.browser.RequestSessions
import com.najmulcodes.zapflick.domain.engine.DownloadEngine
import com.najmulcodes.zapflick.domain.engine.YtDlpUpdate
import com.najmulcodes.zapflick.domain.engine.YtDlpUpdater
import com.najmulcodes.zapflick.domain.model.AvailableFormats
import com.najmulcodes.zapflick.domain.model.DownloadError
import com.najmulcodes.zapflick.domain.model.DownloadException
import com.najmulcodes.zapflick.domain.model.DownloadProgress
import com.najmulcodes.zapflick.domain.model.DownloadRequest
import com.najmulcodes.zapflick.domain.model.VideoMetadata
import com.najmulcodes.zapflick.domain.settings.SettingsRepository
import com.najmulcodes.zapflick.domain.settings.YtDlpChannel
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only class that touches the youtubedl-android API. If a library upgrade changes a signature,
 * this is the one file to fix.
 *
 * aria2c is on the classpath but intentionally not initialised: it is a speed optimisation that
 * changes yt-dlp's progress output, so it only goes in if downloads turn out to be slow.
 */
@Singleton
class YoutubeDlEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: RequestSessions,
    private val settings: SettingsRepository,
) : DownloadEngine, YtDlpUpdater {

    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    override suspend fun initialize() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return@withLock
            withContext(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().init(context)
                    FFmpeg.getInstance().init(context)
                    initialized = true
                } catch (e: Exception) {
                    throw DownloadException(DownloadError.Unknown(e.message), e)
                }
            }
        }
    }

    override suspend fun currentVersion(): String? = withContext(Dispatchers.IO) {
        try {
            initialize()
            YoutubeDL.getInstance().version(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun update(channel: YtDlpChannel): Result<YtDlpUpdate> =
        withContext(Dispatchers.IO) {
            guarded {
                initialize()
                val library = YoutubeDL.getInstance()
                val libraryChannel = when (channel) {
                    YtDlpChannel.STABLE -> YoutubeDL.UpdateChannel.STABLE
                    YtDlpChannel.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
                }
                val status = library.updateYoutubeDL(context, libraryChannel)
                val version = library.version(context)
                if (status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) {
                    YtDlpUpdate.AlreadyLatest(version)
                } else {
                    YtDlpUpdate.Updated(version)
                }
            }
        }

    override suspend fun fetchMetadata(url: String): Result<VideoMetadata> =
        withContext(Dispatchers.IO) {
            guarded {
                initialize()
                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-playlist")
                    applySession(url)
                }
                val info = YoutubeDL.getInstance().getInfo(request)
                val rawDuration: Int? = info.duration
                VideoMetadata(
                    sourceUrl = url,
                    title = info.title?.takeIf { it.isNotBlank() } ?: url,
                    thumbnailUrl = info.thumbnail?.takeIf { it.isNotBlank() },
                    durationSeconds = rawDuration?.takeIf { it > 0 }?.toLong(),
                    uploader = info.uploader?.takeIf { it.isNotBlank() },
                )
            }
        }

    override suspend fun fetchFormats(url: String): Result<AvailableFormats> =
        withContext(Dispatchers.IO) {
            guarded {
                initialize()
                val request = YoutubeDLRequest(url).apply {
                    addOption("--dump-single-json")
                    addOption("--no-playlist")
                    addOption("--no-warnings")
                    applySession(url)
                }
                val response = YoutubeDL.getInstance().execute(request, UUID.randomUUID().toString()) { _, _, _ -> }
                FormatListParser.parse(response.out)
            }
        }

    override suspend fun download(
        request: DownloadRequest,
        workDir: File,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<File> = guarded {
        initialize()
        val processId = UUID.randomUUID().toString()
        val ytRequest = YoutubeDLRequest(request.url).apply {
            YtDlpOptions.forDownload(
                request.selection,
                workDir.absolutePath,
                settings.settings.value.filenameStyle.template,
            ).forEach { option ->
                val value = option.value
                if (value == null) addOption(option.name) else addOption(option.name, value)
            }
            applySession(request.url)
        }

        coroutineScope {
            val job = async(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(ytRequest, processId) { progress, etaInSeconds, line ->
                    if (progress >= 0f) {
                        onProgress(
                            DownloadProgress(
                                percent = progress.coerceAtMost(100f),
                                etaSeconds = etaInSeconds.takeIf { it >= 0 },
                                speed = ProgressLineParser.parseSpeed(line),
                            ),
                        )
                    }
                }
            }
            try {
                job.await()
            } catch (e: CancellationException) {
                // execute() blocks a thread; killing the yt-dlp process is what actually stops it.
                YoutubeDL.getInstance().destroyProcessById(processId)
                throw e
            }
        }

        workDir.listFiles().orEmpty()
            .filter { it.isFile && it.extension.lowercase() !in TEMP_EXTENSIONS }
            .maxByOrNull { it.length() }
            ?: throw DownloadException(DownloadError.Unknown("yt-dlp produced no output file"))
    }

    /** Replays what the in-app browser sent (login cookies, user agent, referer) when the link came from it. */
    private fun YoutubeDLRequest.applySession(url: String) {
        val session = sessions.forUrl(url) ?: return
        session.userAgent?.let { addOption("--user-agent", it) }
        session.referer?.let { addOption("--referer", it) }
        session.cookies?.takeIf { it.isNotBlank() }?.let { addOption("--add-header", "Cookie:$it") }
    }

    private inline fun <T> guarded(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: YoutubeDL.CanceledException) {
        Result.failure(DownloadException(DownloadError.Cancelled, e))
    } catch (e: DownloadException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(DownloadException(YtDlpErrorMapper.map(e.message), e))
    }

    private companion object {
        val TEMP_EXTENSIONS = setOf("part", "ytdl", "tmp", "temp")
    }
}
