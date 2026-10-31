package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.data.ExamPreferences
import com.example.ui.SimpleBrowserScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val preferences = remember {
                        ExamPreferences(this).apply {
                            clearLastUrl()
                        }
                    }

                    SimpleBrowserScreen(
                        preferences = preferences,
                        initialUrl = "",
                        onUrlNavigated = { _ ->
                            // URL sengaja tidak disimpan ke memori internal
                            // agar saat aplikasi ditutup/HP mati, URL selalu kosong dan wajib scan ulang
                        }
                    )
                }
            }
        }
    }
}
