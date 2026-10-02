package com.najmulcodes.zapflick

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.najmulcodes.zapflick.ui.navigation.AppNavHost
import com.najmulcodes.zapflick.ui.theme.ZapFlickTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var incomingShare by mutableStateOf<String?>(null)
    private var openDownloads by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only the first launch carries a fresh share; after rotation the ViewModel already has it.
        if (savedInstanceState == null) {
            incomingShare = sharedTextFrom(intent)
            openDownloads = intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true
        }
        setContent {
            ZapFlickTheme {
                AppNavHost(
                    incomingShare = incomingShare,
                    onShareConsumed = { incomingShare = null },
                    openDownloads = openDownloads,
                    onOpenDownloadsConsumed = { openDownloads = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingShare = sharedTextFrom(intent)
        openDownloads = intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)
    }

    private fun sharedTextFrom(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else {
            null
        }

    companion object {
        /** Set by notifications so a tap lands on the Downloads screen. */
        const val EXTRA_OPEN_DOWNLOADS = "com.najmulcodes.zapflick.extra.OPEN_DOWNLOADS"
    }
}
