package com.echo.recall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recall.core.data.settings.ThemeMode
import com.echo.recall.core.designsystem.theme.EchoTheme
import com.echo.recall.feature.settings.AppStateViewModel
import com.echo.recall.nav.EchoNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppStateViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            EchoTheme(
                darkTheme = when (settings.themeMode) {
                    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
            ) {
                EchoNavHost(settings = settings, viewModel = viewModel)
            }
        }
    }
}
