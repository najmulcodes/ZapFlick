package com.najmulcodes.zapflick.domain.queue

data class QueueConfig(val maxConcurrent: Int = DEFAULT_MAX_CONCURRENT) {
    init {
        require(maxConcurrent >= 1) { "maxConcurrent must be at least 1" }
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENT = 2
    }
}
