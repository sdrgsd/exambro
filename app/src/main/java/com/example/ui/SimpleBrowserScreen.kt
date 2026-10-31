package com.example.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.http.SslError
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.model.ExamDefaults
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SimpleBrowserScreen(
    initialUrl: String,
    onUrlNavigated: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var currentLoadedUrl by remember { mutableStateOf(initialUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var isExamLocked by remember { mutableStateOf(false) }

    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    // Dialog states
    var showFullscreenQrModal by remember { mutableStateOf(false) }
    var showExitScanModal by remember { mutableStateOf(false) }
    var unlockInputCode by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf(false) }

    var exitQrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Dynamic QR generation: updates token every 5 seconds while fullscreen QR is open
    LaunchedEffect(showFullscreenQrModal) {
        if (showFullscreenQrModal) {
            while (showFullscreenQrModal) {
                val token = QrCodeGenerator.getCurrentExitToken()
                exitQrBitmap = QrCodeGenerator.generateQrBitmap(token, 800)
                delay(5000L)
            }
        }
    }

    // Network Connectivity Signal Monitor: Hanya auto-reload jika memang ada sinyal koneksi terputus lalu kembali
    DisposableEffect(Unit) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        var wasDisconnected = false

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                wasDisconnected = true
            }

            override fun onAvailable(network: Network) {
                if (wasDisconnected) {
                    wasDisconnected = false
                    // Auto-refresh sekali saat sinyal kembali terhubung
                    activity?.runOnUiThread {
                        webViewRef?.reload()
                    }
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {}

        onDispose {
            try {
                connectivityManager?.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {}
        }
    }

    // File upload callback holder
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val uris = when {
                data?.clipData != null -> {
                    val count = data.clipData?.itemCount ?: 0
                    Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
            fileUploadCallback?.onReceiveValue(uris)
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    // Kunci Sistem HP (Lock Task Mode, Immersive Sticky, Blokir Screenshot, Layar Tetap Nyala)
    DisposableEffect(isExamLocked) {
        val window = activity?.window
        if (isExamLocked) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }

            try {
                activity?.startLockTask()
            } catch (_: Exception) {}
        } else {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            try {
                activity?.stopLockTask()
            } catch (_: Exception) {}
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            try {
                activity?.stopLockTask()
            } catch (_: Exception) {}
        }
    }

    // Kunci Tombol Back Fisik & Virtual
    BackHandler(enabled = isExamLocked) {
        // MATI TOTAL saat ujian terkunci
    }

    BackHandler(enabled = !isExamLocked) {
        activity?.finish()
    }

    fun startExamWithUrl(rawUrl: String) {
        val normalized = ExamDefaults.normalizeUrl(rawUrl)
        currentLoadedUrl = normalized
        onUrlNavigated(normalized)
        webViewRef?.loadUrl(normalized)
        isExamLocked = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Layar Awal (Sebelum Ujian): Bilah Atas Minimalis Tanpa Logo + Kotak Kamera Scanner di Tengah
        if (!isExamLocked && currentLoadedUrl.isBlank()) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Bilah Atas: Hanya tombol QR Keluar (tanpa logo)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showFullscreenQrModal = true },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("qr_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "QR Keluar Fullscreen",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // Kotak Kamera Scanner di Tengah Layar (tanpa teks/logo, bersih)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CameraQrScannerBox(
                        boxSize = 260.dp,
                        onQrDetected = { detectedUrl ->
                            startExamWithUrl(detectedUrl)
                        }
                    )
                }
            }
        }

        // Halaman Web Ujian (Saat Ujian Aktif atau Sedang Memuat URL)
        AnimatedVisibility(
            visible = isExamLocked || currentLoadedUrl.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            isFocusable = true
                            isFocusableInTouchMode = true

                            // Blokir seleksi teks / copy-paste soal
                            isLongClickable = false
                            setOnLongClickListener { true }

                            setLayerType(View.LAYER_TYPE_SOFTWARE, null)

                            val webSettings = this.settings
                            webSettings.javaScriptEnabled = true
                            webSettings.domStorageEnabled = true
                            webSettings.databaseEnabled = true
                            webSettings.allowFileAccess = true
                            webSettings.allowContentAccess = true
                            webSettings.loadsImagesAutomatically = true
                            webSettings.useWideViewPort = true
                            webSettings.loadWithOverviewMode = true
                            webSettings.setSupportZoom(true)
                            webSettings.builtInZoomControls = true
                            webSettings.displayZoomControls = false
                            webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            webSettings.cacheMode = WebSettings.LOAD_DEFAULT

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                    isLoading = newProgress < 100
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    fileUploadCallback?.onReceiveValue(null)
                                    fileUploadCallback = filePathCallback
                                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "*/*"
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                    }
                                    try {
                                        filePickerLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        fileUploadCallback?.onReceiveValue(null)
                                        fileUploadCallback = null
                                        return false
                                    }
                                    return true
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    if (url != null) {
                                        currentLoadedUrl = url
                                        onUrlNavigated(url)
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    if (url != null) {
                                        currentLoadedUrl = url
                                        onUrlNavigated(url)
                                    }

                                    // Suntikkan CSS anti-copy paste
                                    view?.evaluateJavascript(
                                        "var style = document.createElement('style'); style.innerHTML = '* { -webkit-user-select: none !important; user-select: none !important; }'; document.head.appendChild(style);",
                                        null
                                    )
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    // Sinyal gagal terputus ditangani, reload akan terpicu otomatis saat koneksi kembali
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    handler?.proceed()
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val uri = request?.url ?: return false
                                    val scheme = uri.scheme?.lowercase()
                                    if (scheme == "http" || scheme == "https") {
                                        return false
                                    }
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                        view?.context?.startActivity(intent)
                                    } catch (_: Exception) {}
                                    return true
                                }
                            }

                            if (initialUrl.isNotBlank()) {
                                val target = ExamDefaults.normalizeUrl(initialUrl)
                                loadUrl(target)
                            }
                            webViewRef = this
                        }
                    },
                    update = { webView ->
                        webViewRef = webView
                    }
                )

                // Indikator garis progres pemuatan tipis di bagian atas
                if (isLoading && progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.TopCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }

                // Tombol Scanner QR Keluar di Pojok Kanan Atas saat Ujian Terkunci
                if (isExamLocked) {
                    IconButton(
                        onClick = {
                            unlockInputCode = ""
                            unlockError = false
                            showExitScanModal = true
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(17.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR Keluar",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // FULLSCREEN QR DIALOG UNTUK GURU: Langsung Layar Penuh QR
        if (showFullscreenQrModal) {
            Dialog(
                onDismissRequest = { showFullscreenQrModal = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            showFullscreenQrModal = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    exitQrBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp,
                            contentDescription = "Fullscreen QR Keluar",
                            modifier = Modifier
                                .size(320.dp)
                                .padding(16.dp)
                        )
                    }

                    // Tombol silang kecil di pojok untuk kembali
                    IconButton(
                        onClick = { showFullscreenQrModal = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // MODAL SCANNER QR KELUAR UNTUK SISWA SAAT SELESAI
        if (showExitScanModal) {
            Dialog(onDismissRequest = { showExitScanModal = false }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Kotak Kamera Scan QR Keluar
                        CameraQrScannerBox(
                            boxSize = 220.dp,
                            onQrDetected = { scannedCode ->
                                if (QrCodeGenerator.isTokenValid(scannedCode)) {
                                    isExamLocked = false
                                    showExitScanModal = false
                                    activity?.finish()
                                }
                            }
                        )

                        // Input manual cadangan jika kamera bermasalah (Master PIN 1234)
                        OutlinedTextField(
                            value = unlockInputCode,
                            onValueChange = {
                                unlockInputCode = it
                                unlockError = false
                                if (QrCodeGenerator.isTokenValid(it)) {
                                    isExamLocked = false
                                    showExitScanModal = false
                                    activity?.finish()
                                }
                            },
                            placeholder = { Text("Kode QR / PIN (1234)") },
                            singleLine = true,
                            isError = unlockError,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (QrCodeGenerator.isTokenValid(unlockInputCode)) {
                                        isExamLocked = false
                                        showExitScanModal = false
                                        activity?.finish()
                                    } else {
                                        unlockError = true
                                    }
                                }
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showExitScanModal = false }) {
                                Text("Batal")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (QrCodeGenerator.isTokenValid(unlockInputCode)) {
                                        isExamLocked = false
                                        showExitScanModal = false
                                        activity?.finish()
                                    } else {
                                        unlockError = true
                                    }
                                }
                            ) {
                                Text("Keluar")
                            }
                        }
                    }
                }
            }
        }
    }
}
