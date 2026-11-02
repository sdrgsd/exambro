package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.data.ExamPreferences
import com.example.ui.SimpleBrowserScreen
import com.example.ui.theme.MyApplicationTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val incomingUrlState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
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
                        initialUrl = incomingUrlState.value,
                        onUrlNavigated = { _ ->
                            // URL sengaja tidak disimpan ke memori internal
                            // agar saat aplikasi ditutup/HP mati, URL selalu kosong dan wajib scan ulang
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri != null) {
            try {
                val scheme = uri.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    incomingUrlState.value = uri.toString()
                } else if (scheme == "content" || scheme == "file") {
                    val localHtmlFile = File(cacheDir, "exam_page.html")
                    contentResolver.openInputStream(uri)?.use { input ->
                        localHtmlFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    incomingUrlState.value = "file://${localHtmlFile.absolutePath}"
                }
            } catch (_: Exception) {}
        }
    }
}
