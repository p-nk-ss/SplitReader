package com.example.splitreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.splitreader.presentation.navigation.SplitReaderNavHost
import com.example.splitreader.presentation.theme.SplitReaderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplitReaderTheme {
                SplitReaderNavHost()
            }
        }
    }
}
