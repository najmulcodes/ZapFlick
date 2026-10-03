package com.najmulcodes.zapflick.ui.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.FileUriExposedException
import android.os.Parcelable
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.ui.viewer.ViewerActivity

/**
 * Both helpers pass the same Uri on with a read grant, and never copy the file anywhere.
 * They return false when nothing could handle it.
 */
fun shareDocument(context: Context, uri: Uri, mimeType: String): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        // The chooser only passes the read grant on to the target app through the ClipData.
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return startSafely(context, Intent.createChooser(send, context.getString(R.string.viewer_share_chooser_title)))
}

/** The chooser leaves ZapFlick itself out, otherwise "open in another app" could pick the viewer again. */
fun openDocumentElsewhere(context: Context, uri: Uri, mimeType: String): Boolean {
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(view, context.getString(R.string.viewer_open_with_chooser_title))
        .putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf<Parcelable>(ComponentName(context, ViewerActivity::class.java)),
        )
    return startSafely(context, chooser)
}

private fun startSafely(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
} catch (e: FileUriExposedException) {
    false
}
