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
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.ExamPreferences
import com.example.model.ExamDefaults
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.delay

data class PendingSaveCredential(
    val host: String,
    val username: String,
    val password: String
)

class WebPasswordBridge(
    private val onOfferSave: (String, String, String) -> Unit
) {
    @JavascriptInterface
    fun onPasswordSubmitted(host: String, user: String, pass: String) {
        onOfferSave(host, user, pass)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SimpleBrowserScreen(
    preferences: ExamPreferences? = null,
    initialUrl: String = "",
    onUrlNavigated: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var currentLoadedUrl by remember { mutableStateOf(initialUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var isExamLocked by remember { mutableStateOf(false) }
    var isPageLoadedReady by remember { mutableStateOf(false) }

    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    // Dialog states
    var showQrMenuModal by remember { mutableStateOf(false) }
    var selectedQrTab by remember { mutableIntStateOf(0) } // 0: Pindai QR, 1: Kode QR
    var showExitScanModal by remember { mutableStateOf(false) }
    var exitBubbleOffsetX by remember { mutableFloatStateOf(0f) }
    var exitBubbleOffsetY by remember { mutableFloatStateOf(0f) }
    var manualUrlInput by remember { mutableStateOf("") }
    var pendingSaveCredential by remember { mutableStateOf<PendingSaveCredential?>(null) }

    var exitQrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Dynamic QR generation: updates token every 5 seconds while Kode QR tab is open
    LaunchedEffect(showQrMenuModal, selectedQrTab) {
        if (showQrMenuModal && selectedQrTab == 1) {
            while (showQrMenuModal && selectedQrTab == 1) {
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

    // Kunci Sistem HP (Immersive Sticky, Blokir Screenshot, Layar Tetap Nyala)
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
        } else {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Kunci Tombol Back Fisik & Virtual
    BackHandler(enabled = isExamLocked && showExitScanModal) {
        showExitScanModal = false
    }

    BackHandler(enabled = isExamLocked && !showExitScanModal) {
        // MATI TOTAL saat ujian terkunci
    }

    BackHandler(enabled = !isExamLocked && showQrMenuModal) {
        showQrMenuModal = false
    }

    BackHandler(enabled = !isExamLocked && !showQrMenuModal && currentLoadedUrl.isNotBlank()) {
        currentLoadedUrl = ""
        isPageLoadedReady = false
        progress = 0
        webViewRef?.stopLoading()
    }

    BackHandler(enabled = !isExamLocked && !showQrMenuModal && currentLoadedUrl.isBlank()) {
        activity?.finish()
    }

    fun onQrDetected(rawUrl: String) {
        val normalized = ExamDefaults.normalizeUrl(rawUrl)
        currentLoadedUrl = normalized
        onUrlNavigated(normalized)
        isPageLoadedReady = false
        progress = 0
        webViewRef?.loadUrl(normalized)
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
                // Bilah Atas: Address Bar + Tombol QR
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualUrlInput,
                            onValueChange = { manualUrlInput = it },
                            placeholder = {
                                Text(
                                    text = "Ketik URL / IP ujian...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (manualUrlInput.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            onQrDetected(manualUrlInput.trim())
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Buka URL",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    if (manualUrlInput.isNotBlank()) {
                                        onQrDetected(manualUrlInput.trim())
                                    }
                                }
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("address_bar_input")
                        )

                        IconButton(
                            onClick = {
                                selectedQrTab = 0
                                showQrMenuModal = true
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .testTag("qr_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Menu QR",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // Tampilan Tengah Dikosongkan Bersih Sesuai Permintaan
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
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

                            // Gunakan akselerasi hardware standar dengan background putih bersih (cegah blank hitam)
                            setBackgroundColor(android.graphics.Color.WHITE)

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            val webSettings = this.settings
                            webSettings.javaScriptEnabled = true
                            webSettings.domStorageEnabled = true
                            webSettings.databaseEnabled = true
                            webSettings.saveFormData = true
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

                            addJavascriptInterface(
                                WebPasswordBridge { host, user, pass ->
                                    activity?.runOnUiThread {
                                        if (host.isNotBlank() && pass.isNotBlank()) {
                                            val existing = preferences?.getWebCredential(host)
                                            if (existing == null || existing.second != pass) {
                                                pendingSaveCredential = PendingSaveCredential(host, user, pass)
                                            }
                                        }
                                    }
                                },
                                "AndroidPasswordBridge"
                            )

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                    isLoading = newProgress < 100
                                    if (newProgress >= 90) {
                                        isPageLoadedReady = true
                                    }
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
                                    isPageLoadedReady = true
                                    if (url != null) {
                                        currentLoadedUrl = url
                                        onUrlNavigated(url)
                                    }

                                    // Suntikkan CSS anti-copy paste
                                    view?.evaluateJavascript(
                                        "var style = document.createElement('style'); style.innerHTML = '* { -webkit-user-select: none !important; user-select: none !important; }'; document.head.appendChild(style);",
                                        null
                                    )

                                    // Pasang hook submit form untuk mendeteksi input password dan menawarkan simpan sandi
                                    val passwordDetectorJs = """
                                        (function() {
                                            function attachSubmitHooks() {
                                                var forms = document.querySelectorAll('form');
                                                forms.forEach(function(f) {
                                                    if (f._hasExambroHook) return;
                                                    f._hasExambroHook = true;
                                                    f.addEventListener('submit', function() {
                                                        try {
                                                            var pwd = f.querySelector('input[type="password"]');
                                                            if (pwd && pwd.value) {
                                                                var usr = f.querySelector('input[type="text"], input[type="email"], input[name*="user"], input[name*="nis"], input[name*="login"], input[name*="id"]');
                                                                var uVal = usr ? usr.value : '';
                                                                var hVal = window.location.hostname || '';
                                                                if (window.AndroidPasswordBridge) {
                                                                    window.AndroidPasswordBridge.onPasswordSubmitted(hVal, uVal, pwd.value);
                                                                }
                                                            }
                                                        } catch(e) {}
                                                    });
                                                });
                                                var btns = document.querySelectorAll('button, input[type="button"], input[type="submit"]');
                                                btns.forEach(function(b) {
                                                    if (b._hasExambroHook) return;
                                                    b._hasExambroHook = true;
                                                    b.addEventListener('click', function() {
                                                        setTimeout(function() {
                                                            try {
                                                                var pwd = document.querySelector('input[type="password"]');
                                                                if (pwd && pwd.value) {
                                                                    var usr = document.querySelector('input[type="text"], input[type="email"], input[name*="user"], input[name*="nis"], input[name*="login"], input[name*="id"]');
                                                                    var uVal = usr ? usr.value : '';
                                                                    var hVal = window.location.hostname || '';
                                                                    if (window.AndroidPasswordBridge) {
                                                                        window.AndroidPasswordBridge.onPasswordSubmitted(hVal, uVal, pwd.value);
                                                                    }
                                                                }
                                                            } catch(e) {}
                                                        }, 80);
                                                    });
                                                });
                                            }
                                            attachSubmitHooks();
                                            if (window.MutationObserver) {
                                                new MutationObserver(attachSubmitHooks).observe(document.body || document.documentElement, { childList: true, subtree: true });
                                            }
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(passwordDetectorJs, null)

                                    // Lakukan autofill jika sebelumnya siswa sudah menyimpan kata sandi untuk situs ini
                                    val currentHost = try {
                                        if (!url.isNullOrBlank()) Uri.parse(url).host ?: "" else ""
                                    } catch (_: Exception) { "" }

                                    if (currentHost.isNotBlank()) {
                                        val saved = preferences?.getWebCredential(currentHost)
                                        if (saved != null) {
                                            val safeUser = saved.first.replace("\\", "\\\\").replace("'", "\\'")
                                            val safePass = saved.second.replace("\\", "\\\\").replace("'", "\\'")
                                            val autoFillJs = """
                                                (function() {
                                                    setTimeout(function() {
                                                        try {
                                                            var pwd = document.querySelector('input[type="password"]');
                                                            var usr = document.querySelector('input[type="text"], input[type="email"], input[name*="user"], input[name*="nis"], input[name*="login"], input[name*="id"]');
                                                            if (usr && !usr.value) {
                                                                usr.value = '$safeUser';
                                                                usr.dispatchEvent(new Event('input', { bubbles: true }));
                                                                usr.dispatchEvent(new Event('change', { bubbles: true }));
                                                            }
                                                            if (pwd && !pwd.value) {
                                                                pwd.value = '$safePass';
                                                                pwd.dispatchEvent(new Event('input', { bubbles: true }));
                                                                pwd.dispatchEvent(new Event('change', { bubbles: true }));
                                                            }
                                                        } catch(e) {}
                                                    }, 300);
                                                })();
                                            """.trimIndent()
                                            view?.evaluateJavascript(autoFillJs, null)
                                        }
                                    }
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

                            if (currentLoadedUrl.isNotBlank()) {
                                val target = ExamDefaults.normalizeUrl(currentLoadedUrl)
                                loadUrl(target)
                            }
                            webViewRef = this
                        }
                    },
                    update = { webView ->
                        webViewRef = webView
                        if (currentLoadedUrl.isNotBlank() && webView.url == null) {
                            val target = ExamDefaults.normalizeUrl(currentLoadedUrl)
                            webView.loadUrl(target)
                        }
                    }
                )

                // OVERLAY PERSIAPAN UJIAN DENGAN TOMBOL "MULAI"
                if (!isExamLocked && currentLoadedUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .padding(24.dp)
                                .widthIn(max = 380.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val domain = remember(currentLoadedUrl) {
                                    try {
                                        Uri.parse(currentLoadedUrl).host ?: currentLoadedUrl
                                    } catch (_: Exception) {
                                        currentLoadedUrl
                                    }
                                }

                                val isReady = isPageLoadedReady || progress >= 90

                                Icon(
                                    imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (isReady) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )

                                Text(
                                    text = domain,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!isReady && progress < 90) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { progress / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        Text(
                                            text = "Memuat halaman... $progress%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Halaman siap",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Tombol "Mulai" saja
                                Button(
                                    onClick = {
                                        isExamLocked = true
                                    },
                                    enabled = isReady,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("start_exam_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Mulai",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        currentLoadedUrl = ""
                                        isPageLoadedReady = false
                                        progress = 0
                                        webViewRef?.stopLoading()
                                    },
                                    modifier = Modifier.testTag("rescan_qr_button")
                                ) {
                                    Text(
                                        text = "Scan Ulang",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

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

                // Tombol Balon Scanner QR Keluar Mengambang (Bisa Di-drag Bebas Kemana-mana)
                if (isExamLocked && !showExitScanModal) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 16.dp, top = 76.dp)
                            .offset { IntOffset(exitBubbleOffsetX.roundToInt(), exitBubbleOffsetY.roundToInt()) }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var dragged = false
                                    var lastPos = down.position

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            if (!dragged) {
                                                showExitScanModal = true
                                            }
                                            break
                                        }
                                        val diff = change.position - lastPos
                                        if (!dragged && diff.getDistance() > 10f) {
                                            dragged = true
                                        }
                                        if (dragged) {
                                            exitBubbleOffsetX += diff.x
                                            exitBubbleOffsetY += diff.y
                                            change.consume()
                                        }
                                        lastPos = change.position
                                    }
                                }
                            }
                            .size(54.dp)
                            .testTag("floating_exit_qr_bubble")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR Keluar",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        // MODAL MENU QR DENGAN 2 TAB: "QR" dan "Scan QR"
        if (showQrMenuModal) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Bilah Atas Modal: Judul & Tombol Tutup (X)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Menu QR",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showQrMenuModal = false },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("close_qr_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 2 TAB: "Pindai QR" dan "Kode QR"
                    TabRow(
                        selectedTabIndex = selectedQrTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = selectedQrTab == 0,
                            onClick = { selectedQrTab = 0 },
                            text = { Text("Pindai QR", fontWeight = FontWeight.SemiBold) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                        Tab(
                            selected = selectedQrTab == 1,
                            onClick = { selectedQrTab = 1 },
                            text = { Text("Kode QR", fontWeight = FontWeight.SemiBold) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }

                    // Konten Tab Berdasarkan Pilihan
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedQrTab == 0) {
                            // TAB 1: Pindai QR (Kamera Tampilan Penuh di Bawah Tab Tanpa Kotak & Tanpa Teks)
                            CameraQrScannerBox(
                                modifier = Modifier.fillMaxSize(),
                                isFullscreen = true,
                                onQrDetected = { detectedUrl ->
                                    showQrMenuModal = false
                                    onQrDetected(detectedUrl)
                                }
                            )
                        } else {
                            // TAB 2: Kode QR (Benar-benar Hanya Kode QR Tanpa Informasi Lain)
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                exitQrBitmap?.let { bmp ->
                                    Card(
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = "Kode QR",
                                            modifier = Modifier
                                                .size(300.dp)
                                                .padding(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // OVERLAY SCANNER QR KELUAR LANGSUNG DI LAYAR UTAMA (TANPA DIALOG, BEBAS BLANK HITAM)
        if (showExitScanModal) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f)),
                contentAlignment = Alignment.Center
            ) {
                // Tombol Silang (X) di Pojok Kanan Atas untuk menutup jika tidak sengaja terbuka
                IconButton(
                    onClick = { showExitScanModal = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(48.dp)
                        .testTag("close_exit_scanner_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup Scanner",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Kotak Kamera Scan QR Keluar langsung di jendela utama (jernih dan responsif)
                    CameraQrScannerBox(
                        boxSize = 250.dp,
                        onQrDetected = { scannedCode ->
                            if (QrCodeGenerator.isTokenValid(scannedCode)) {
                                isExamLocked = false
                                showExitScanModal = false
                                activity?.finish()
                            }
                        }
                    )

                    Text(
                        text = "Arahkan kamera ke QR Keluar Pengawas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // DIALOG TAWARAN SIMPAN KATA SANDI
        pendingSaveCredential?.let { cred ->
            AlertDialog(
                onDismissRequest = { pendingSaveCredential = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Simpan Sandi",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "Simpan Sandi?",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Apakah Anda ingin menyimpan kata sandi untuk situs ini?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Situs: ${cred.host}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (cred.username.isNotBlank()) {
                            Text(
                                text = "Pengguna: ${cred.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Kata sandi akan otomatis terisi saat Anda membuka halaman login berikutnya.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            preferences?.saveWebCredential(cred.host, cred.username, cred.password)
                            pendingSaveCredential = null
                        },
                        modifier = Modifier.testTag("save_password_confirm_button")
                    ) {
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { pendingSaveCredential = null },
                        modifier = Modifier.testTag("save_password_dismiss_button")
                    ) {
                        Text("Jangan Sekarang")
                    }
                }
            )
        }
    }
}
