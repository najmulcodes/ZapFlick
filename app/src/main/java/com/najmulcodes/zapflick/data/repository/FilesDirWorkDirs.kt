package com.najmulcodes.zapflick.data.repository

import android.content.Context
import com.najmulcodes.zapflick.domain.queue.WorkDirProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Partial downloads live in the app's private storage until they are saved or discarded. */
@Singleton
class FilesDirWorkDirs @Inject constructor(
    @ApplicationContext private val context: Context,
) : WorkDirProvider {

    override fun dirFor(id: Long): File = File(context.filesDir, "downloads/$id").apply { mkdirs() }

    override fun delete(id: Long) {
        File(context.filesDir, "downloads/$id").deleteRecursively()
    }
}
