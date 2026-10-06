package com.najmulcodes.zapflick

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.najmulcodes.zapflick.domain.settings.SettingsRepository
import com.najmulcodes.zapflick.domain.settings.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import javax.inject.Inject
import com.najmulcodes.zapflick.ui.navigation.AppNavHost
import com.najmulcodes.zapflick.ui.theme.ZapFlickTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
// FragmentActivity (a ComponentActivity) is needed for the system fingerprint prompt.
class MainActivity : FragmentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository


    private var incomingShare by mutableStateOf<String?>(null)
    private var openProgress by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only the first launch carries a fresh share; after rotation the ViewModel already has it.
        if (savedInstanceState == null) {
            incomingShare = sharedTextFrom(intent)
            openProgress = intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true
        }
        setContent {
            val settings by settingsRepository.settings.collectAsState()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            ZapFlickTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                AppNavHost(
                    incomingShare = incomingShare,
                    onShareConsumed = { incomingShare = null },
                    openProgress = openProgress,
                    onOpenProgressConsumed = { openProgress = false },
                    secureScreens = settings.secureScreens,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingShare = sharedTextFrom(intent)
        openProgress = intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)
    }

    private fun sharedTextFrom(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else {
            null
        }

    companion object {
        /** Set by notifications so a tap lands on the Progress screen. */
        const val EXTRA_OPEN_DOWNLOADS = "com.najmulcodes.zapflick.extra.OPEN_DOWNLOADS"
    }
}
