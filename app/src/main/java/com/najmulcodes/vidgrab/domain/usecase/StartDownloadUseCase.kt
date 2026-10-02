package com.najmulcodes.vidgrab.domain.usecase

import com.najmulcodes.vidgrab.domain.model.FormatSelection
import com.najmulcodes.vidgrab.domain.model.VideoMetadata
import com.najmulcodes.vidgrab.domain.queue.DownloadQueue
import javax.inject.Inject

/** Adds a video to the download queue; it starts as soon as a slot is free. */
class StartDownloadUseCase @Inject constructor(
    private val queue: DownloadQueue,
) {
    suspend operator fun invoke(
        metadata: VideoMetadata,
        selection: FormatSelection = FormatSelection.Best,
    ): Long = queue.enqueue(metadata, selection)
}
