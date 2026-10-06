package com.najmulcodes.zapflick.domain.queue

/** The limits the queue checks every time it decides what to start. Read fresh each time, so Settings apply at once. */
interface QueueConfig {
    val maxConcurrent: Int

    /** False while downloads are held back (for example Wi-Fi only on mobile data). Running ones are paused elsewhere. */
    val canStart: Boolean get() = true

    companion object {
        const val DEFAULT_MAX_CONCURRENT = 2
    }
}

data class FixedQueueConfig(
    override val maxConcurrent: Int = QueueConfig.DEFAULT_MAX_CONCURRENT,
    override val canStart: Boolean = true,
) : QueueConfig {
    init {
        require(maxConcurrent >= 1) { "maxConcurrent must be at least 1" }
    }
}

/** Keeps `QueueConfig(2)` working as a constructor-style call. */
fun QueueConfig(maxConcurrent: Int = QueueConfig.DEFAULT_MAX_CONCURRENT): QueueConfig = FixedQueueConfig(maxConcurrent)
