package com.najmulcodes.zapflick.ui.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * While this is on screen and [enabled], screenshots and the recents preview are blocked
 * (FLAG_SECURE). It is switched off again when the screen goes away.
 */
@Composable
fun SecureScreen(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = context.activity()?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
