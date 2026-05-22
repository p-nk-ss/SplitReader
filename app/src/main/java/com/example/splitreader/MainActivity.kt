package com.example.splitreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.example.splitreader.presentation.navigation.SplitReaderNavHost
import com.example.splitreader.presentation.theme.SplitReaderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SplitReaderTheme {
                SplitReaderNavHost()
            }
        }
    }
}
