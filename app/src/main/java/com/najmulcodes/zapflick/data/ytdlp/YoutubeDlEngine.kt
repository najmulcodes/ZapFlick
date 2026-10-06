package com.najmulcodes.zapflick.data.ytdlp

import android.content.Context
import com.najmulcodes.zapflick.domain.browser.RequestSessions
import com.najmulcodes.zapflick.domain.engine.DownloadEngine
import com.najmulcodes.zapflick.domain.engine.FailureEntry
import com.najmulcodes.zapflick.domain.engine.FailureLog
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only class that touches the youtubedl-android API. If a library upgrade changes a signature,
 * this is the one file to fix.
 *
 * Sites change often, so yt-dlp is refreshed in the background about once a day, and again on the
 * spot (followed by one retry) when a site fails in the way an out-of-date yt-dlp fails.
 *
 * aria2c is on the classpath but intentionally not initialised: it is a speed optimisation that
 * changes yt-dlp's progress output, so it only goes in if downloads turn out to be slow.
 */
@Singleton
class YoutubeDlEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: RequestSessions,
    private val settings: SettingsRepository,
    private val failureLog: FailureLog,
) : DownloadEngine, YtDlpUpdater {

    private val initMutex = Mutex()

    /** Held while yt-dlp is being replaced; lookups and downloads wait for it to finish. */
    private val updateMutex = Mutex()
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var initialized = false

    @Volatile
    private var cachedVersion: String? = null

    @Volatile
    private var lastBreakageUpdate = 0L

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
                deleteStaleCookieFiles()
                cachedVersion = readVersion()
            }
            // Inside the lock on purpose: nothing starts on a half-updated yt-dlp.
            refreshIfStale()
        }
    }

    override suspend fun currentVersion(): String? = withContext(Dispatchers.IO) {
        try {
            initialize()
            cachedVersion ?: readVersion().also { cachedVersion = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun update(channel: YtDlpChannel): Result<YtDlpUpdate> {
        // Initialise first, outside the update lock: initialisation may take that lock itself.
        val ready = withContext(Dispatchers.IO) { guarded("update", "") { initialize() } }
        ready.exceptionOrNull()?.let { return Result.failure(it) }

        val result = updateMutex.withLock {
            withContext(Dispatchers.IO) { guarded("update", "") { runUpdate(channel) } }
        }
        cachedVersion = readVersion()
        if (result.isSuccess) markUpdated()
        return result
    }

    override suspend fun fetchMetadata(url: String): Result<VideoMetadata> =
        withContext(Dispatchers.IO) { withFreshExtractor { lookupMetadata(url) } }

    override suspend fun fetchFormats(url: String): Result<AvailableFormats> =
        withContext(Dispatchers.IO) { withFreshExtractor { lookupFormats(url) } }

    override suspend fun download(
        request: DownloadRequest,
        workDir: File,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<File> = withFreshExtractor { downloadOnce(request, workDir, onProgress) }

    private suspend fun lookupMetadata(url: String): Result<VideoMetadata> = guarded("lookup", url) {
        initialize()
        awaitUpdateIdle()
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            add(YtDlpOptions.networkOptions())
        }
        val cookieFile = request.applySession(url)
        try {
            val info = YoutubeDL.getInstance().getInfo(request)
            val rawDuration: Int? = info.duration
            VideoMetadata(
                sourceUrl = url,
                title = info.title?.takeIf { it.isNotBlank() } ?: url,
                thumbnailUrl = info.thumbnail?.takeIf { it.isNotBlank() },
                durationSeconds = rawDuration?.takeIf { it > 0 }?.toLong(),
                uploader = info.uploader?.takeIf { it.isNotBlank() },
            )
        } finally {
            cookieFile?.delete()
        }
    }

    private suspend fun lookupFormats(url: String): Result<AvailableFormats> = guarded("formats", url) {
        initialize()
        awaitUpdateIdle()
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-playlist")
            addOption("--no-warnings")
            add(YtDlpOptions.networkOptions())
        }
        val cookieFile = request.applySession(url)
        try {
            val response = YoutubeDL.getInstance().execute(request, UUID.randomUUID().toString()) { _, _, _ -> }
            FormatListParser.parse(response.out)
        } finally {
            cookieFile?.delete()
        }
    }

    private suspend fun downloadOnce(
        request: DownloadRequest,
        workDir: File,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<File> = guarded("download", request.url) {
        initialize()
        awaitUpdateIdle()
        val processId = UUID.randomUUID().toString()
        val ytRequest = YoutubeDLRequest(request.url).apply {
            add(
                YtDlpOptions.forDownload(
                    request.selection,
                    workDir.absolutePath,
                    settings.settings.value.filenameStyle.template,
                ),
            )
        }
        val cookieFile = ytRequest.applySession(request.url)

        try {
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
        } finally {
            cookieFile?.delete()
        }

        workDir.listFiles().orEmpty()
            .filter { it.isFile && it.extension.lowercase() !in TEMP_EXTENSIONS }
            .maxByOrNull { it.length() }
            ?: throw DownloadException(DownloadError.Unknown("yt-dlp produced no output file"))
    }

    // ---- keeping yt-dlp current -------------------------------------------------------------

    /** The blocking part of an update. Callers hold [updateMutex]. */
    private fun runUpdate(channel: YtDlpChannel): YtDlpUpdate {
        val library = YoutubeDL.getInstance()
        val libraryChannel = when (channel) {
            YtDlpChannel.STABLE -> YoutubeDL.UpdateChannel.STABLE
            YtDlpChannel.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
        }
        val status = library.updateYoutubeDL(context, libraryChannel)
        val version = library.version(context)
        return if (status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) {
            YtDlpUpdate.AlreadyLatest(version)
        } else {
            YtDlpUpdate.Updated(version)
        }
    }

    /** At most once a day, and never more than once every six hours when it keeps failing. */
    private suspend fun refreshIfStale() {
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_UPDATE_OK, 0L) < DAY_MS) return
        if (now - prefs.getLong(KEY_LAST_UPDATE_TRY, 0L) < RETRY_GAP_MS) return
        prefs.edit().putLong(KEY_LAST_UPDATE_TRY, now).apply()

        val channel = settings.settings.value.ytDlpChannel
        // The update blocks a thread and cannot be interrupted, so it runs detached and is only waited for
        // for a while: a dead connection must not stop every download from starting.
        val update = updateScope.async {
            updateMutex.withLock { guarded("auto-update", "") { runUpdate(channel) } }
        }
        val result = withTimeoutOrNull(AUTO_UPDATE_TIMEOUT_MS) { update.await() }
        if (result?.isSuccess == true) markUpdated()
        cachedVersion = readVersion()
    }

    /** Runs [block]; if the site failed the way an out-of-date yt-dlp fails, updates yt-dlp and tries once more. */
    private suspend fun <T> withFreshExtractor(block: suspend () -> Result<T>): Result<T> {
        val first = block()
        val error = (first.exceptionOrNull() as? DownloadException)?.error
        if (error != DownloadError.OutdatedExtractor) return first
        if (!updateBecauseOfBreakage()) return first
        return block()
    }

    /** True when a newer yt-dlp was actually installed, so a retry has a chance. */
    private suspend fun updateBecauseOfBreakage(): Boolean = updateMutex.withLock {
        val now = System.currentTimeMillis()
        if (now - lastBreakageUpdate < BREAKAGE_UPDATE_GAP_MS) return@withLock false
        lastBreakageUpdate = now
        val channel = settings.settings.value.ytDlpChannel
        val result = withContext(Dispatchers.IO) { guarded("update", "") { runUpdate(channel) } }
        cachedVersion = readVersion()
        if (result.isSuccess) markUpdated()
        result.getOrNull() is YtDlpUpdate.Updated
    }

    private suspend fun awaitUpdateIdle() {
        updateMutex.withLock { }
    }

    private fun markUpdated() {
        prefs.edit().putLong(KEY_LAST_UPDATE_OK, System.currentTimeMillis()).apply()
    }

    private fun readVersion(): String? = try {
        YoutubeDL.getInstance().version(context)
    } catch (e: Exception) {
        null
    }

    // ---- the in-app browser's session --------------------------------------------------------

    /**
     * Replays what the in-app browser knew (user agent, referer, login cookies) when the link came from it.
     * Returns the temporary cookie file, which the caller deletes when the process is done.
     */
    private fun YoutubeDLRequest.applySession(url: String): File? {
        val session = sessions.forUrl(url) ?: return null
        session.userAgent?.let { addOption("--user-agent", it) }
        session.referer?.let { addOption("--referer", it) }
        val text = session.cookies?.takeIf { it.isNotBlank() }?.let { NetscapeCookies.format(url, it) } ?: return null
        val file = File(context.cacheDir, "$COOKIE_PREFIX${UUID.randomUUID()}.txt")
        return try {
            file.writeText(text)
            addOption("--cookies", file.absolutePath)
            file
        } catch (e: IOException) {
            file.delete()
            null
        }
    }

    private fun deleteStaleCookieFiles() {
        context.cacheDir.listFiles { file -> file.name.startsWith(COOKIE_PREFIX) }?.forEach { it.delete() }
    }

    private fun YoutubeDLRequest.add(options: List<YtDlpOption>) {
        options.forEach { option ->
            val value = option.value
            if (value == null) addOption(option.name) else addOption(option.name, value)
        }
    }

    private inline fun <T> guarded(operation: String, url: String, block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: YoutubeDL.CanceledException) {
        Result.failure(DownloadException(DownloadError.Cancelled, e))
    } catch (e: DownloadException) {
        record(operation, url, e.error.code, e.cause?.message ?: e.message)
        Result.failure(e)
    } catch (e: Exception) {
        val error = YtDlpErrorMapper.map(e.message)
        record(operation, url, error.code, e.message)
        Result.failure(DownloadException(error, e))
    }

    private fun record(operation: String, url: String, code: String, raw: String?) {
        failureLog.record(FailureEntry(System.currentTimeMillis(), operation, url, code, raw.orEmpty()))
    }

    private companion object {
        val TEMP_EXTENSIONS = setOf("part", "ytdl", "tmp", "temp")
        const val COOKIE_PREFIX = "cookies-"
        const val PREFS = "zapflick_ytdlp"
        const val KEY_LAST_UPDATE_OK = "last_update_ok"
        const val KEY_LAST_UPDATE_TRY = "last_update_try"
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val RETRY_GAP_MS = 6L * 60 * 60 * 1000
        const val BREAKAGE_UPDATE_GAP_MS = 30L * 60 * 1000
        const val AUTO_UPDATE_TIMEOUT_MS = 30_000L
    }
}
