package com.najmulcodes.vidgrab.domain.repository

import com.najmulcodes.vidgrab.domain.model.SavedMedia
import java.io.File

interface MediaSaver {
    /** Moves a finished download into public storage. The source file is left in place. */
    suspend fun save(file: File): Result<SavedMedia>
}
