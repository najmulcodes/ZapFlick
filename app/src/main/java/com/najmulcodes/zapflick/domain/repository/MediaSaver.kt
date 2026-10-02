package com.najmulcodes.zapflick.domain.repository

import com.najmulcodes.zapflick.domain.model.SavedMedia
import java.io.File

interface MediaSaver {
    /** Moves a finished download into public storage. The source file is left in place. */
    suspend fun save(file: File): Result<SavedMedia>
}
