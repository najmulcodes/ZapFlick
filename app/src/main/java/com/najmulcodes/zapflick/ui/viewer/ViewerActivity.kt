package com.najmulcodes.zapflick.ui.viewer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.najmulcodes.zapflick.ui.theme.ZapFlickTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Shows a PDF (and, later, an HTML file) handed over by another app or by ZapFlick itself.
 *
 * It is a separate Activity from MainActivity on purpose: launchMode is standard, so every file
 * opens in the caller's task and Back returns to the app the file came from. A rotation recreates
 * the Activity; the document and the open PDF live in the ViewModel and survive it.
 */
@AndroidEntryPoint
class ViewerActivity : ComponentActivity() {

    private val viewModel: ViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The theme is dark-first, so the system bars use light icons whatever the phone setting is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        viewModel.start(ViewerIntents.documentUri(intent), intent?.type)
        setContent {
            ZapFlickTheme {
                ViewerScreen(viewModel = viewModel, onBack = ::finish)
            }
        }
    }
}
