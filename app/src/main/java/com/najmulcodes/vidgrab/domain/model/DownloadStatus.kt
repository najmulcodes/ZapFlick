package com.najmulcodes.vidgrab.domain.model

/** Lifecycle of one queued download. Persisted by name, so do not rename entries. */
enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    /** Waiting for a free slot or downloading right now. */
    val isActive: Boolean get() = this == QUEUED || this == RUNNING

    /** Nothing more will happen to this download unless the user retries it. */
    val isFinished: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
}
