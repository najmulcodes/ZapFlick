package com.najmulcodes.zapflick.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import com.najmulcodes.zapflick.domain.model.SavedMedia
import java.util.Locale

/** Only MediaStore entries can be handed to other apps; the Android 8-9 fallback is a bare file. */
fun SavedMedia.canOpen(): Boolean = uri.startsWith("content:")

/** Returns false when no installed app can play the file. */
fun openMedia(context: Context, media: SavedMedia): Boolean {
    val extension = media.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "video/*"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(media.uri), mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
