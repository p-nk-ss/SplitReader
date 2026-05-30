package com.example.splitreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitreader.presentation.AppThemeViewModel
import com.example.splitreader.presentation.navigation.SplitReaderNavHost
import com.example.splitreader.presentation.theme.SplitReaderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val themeViewModel: AppThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val themeKey by themeViewModel.themeKey.collectAsStateWithLifecycle()
            SplitReaderTheme(readerThemeKey = themeKey) {
                SplitReaderNavHost()
            }
        }
    }
}
