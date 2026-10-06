package com.najmulcodes.zapflick.domain.repository

import com.najmulcodes.zapflick.domain.model.SavedMedia

/** Operations on finished files wherever they ended up (MediaStore, a chosen folder, or app storage). */
interface MediaFiles {
    /** True when the file is gone afterwards, including when it was already gone. False if it could not be removed. */
    suspend fun delete(media: SavedMedia): Boolean

    /** Copies the file into the app's private storage, then removes the public copy. */
    suspend fun moveToPrivate(media: SavedMedia): Result<SavedMedia>

    /** Puts a private file back into public storage (as Settings says) and removes the private copy. */
    suspend fun restoreFromPrivate(media: SavedMedia): Result<SavedMedia>
}

/** Writes the library changes the queue does not own. */
interface PrivateItemsStore {
    /** Marks an item private or public, and points it at the file's new place. */
    suspend fun setPrivate(id: Long, isPrivate: Boolean, media: SavedMedia)
}
