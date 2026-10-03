package com.najmulcodes.zapflick.ui.viewer

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** Reads the document out of the Intents ViewerActivity accepts. */
object ViewerIntents {

    /**
     * ACTION_VIEW carries the link in the data; ACTION_SEND carries it in EXTRA_STREAM (or the
     * ClipData). Only content: and file: links are accepted, so an Intent cannot point the viewer
     * at a web address or an app-internal scheme.
     */
    fun documentUri(intent: Intent?): Uri? {
        if (intent == null) return null
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            else -> null
        } ?: return null
        return uri.takeIf {
            it.scheme == ContentResolver.SCHEME_CONTENT || it.scheme == ContentResolver.SCHEME_FILE
        }
    }
}
