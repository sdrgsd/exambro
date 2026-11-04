package com.example.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.example.BuildConfig
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.ExamPreferences
import com.example.model.ExamDefaults
import com.example.util.AlarmHelper
import com.example.util.QrCodeGenerator
import com.example.util.TotpManager
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
    val clipboardManager = LocalClipboardManager.current
    val activity = context as? Activity

    var currentLoadedUrl by remember { mutableStateOf(initialUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var isExamLocked by remember { mutableStateOf(false) }
    var isPageLoadedReady by remember { mutableStateOf(false) }
    var hasPageLoadError by remember { mutableStateOf(false) }

    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank() && initialUrl != currentLoadedUrl) {
            val target = ExamDefaults.normalizeUrl(initialUrl)
            currentLoadedUrl = target
            onUrlNavigated(target)
            hasPageLoadError = false
            isPageLoadedReady = false
            progress = 0
            webViewRef?.loadUrl(target)
        }
    }

    // Dialog states
    var showQrMenuModal by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var isExitingLegitimately by remember { mutableStateOf(false) }
    var isAlarmTriggered by remember { mutableStateOf(false) }
    var showAlarmUnlockDialog by remember { mutableStateOf(false) }
    var hasEverBeenPinned by remember { mutableStateOf(false) }
    var manualUrlInput by remember { mutableStateOf("") }
    var pendingSaveCredential by remember { mutableStateOf<PendingSaveCredential?>(null) }

    var isAppPinned by remember { mutableStateOf(false) }
    var exitCodeInput by remember { mutableStateOf("") }
    var exitErrorMessage by remember { mutableStateOf<String?>(null) }
    var alarmCodeInput by remember { mutableStateOf("") }
    var alarmErrorMessage by remember { mutableStateOf<String?>(null) }

    // Pantau status Sematkan Aplikasi & Deteksi Percobaan Keluar Darurat (Pelanggaran)
    LaunchedEffect(isExamLocked) {
        if (isExamLocked) {
            hasEverBeenPinned = false
            isExitingLegitimately = false
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            while (isExamLocked) {
                val state = am?.lockTaskModeState ?: ActivityManager.LOCK_TASK_MODE_NONE
                val pinned = (state != ActivityManager.LOCK_TASK_MODE_NONE)
                isAppPinned = pinned
                if (pinned) {
                    hasEverBeenPinned = true
                } else if (hasEverBeenPinned && !isExitingLegitimately && !isAlarmTriggered) {
                    // Tindakan darurat (swipe-up tahan / tombol darurat) terdeteksi tanpa verifikasi kode Authenticator!
                    isAlarmTriggered = true
                    showAlarmUnlockDialog = false
                    alarmCodeInput = ""
                    alarmErrorMessage = null
                    AlarmHelper.startAlarm(context)
                }
                delay(300L)
            }
        } else {
            isAppPinned = false
            hasEverBeenPinned = false
            isExitingLegitimately = false
            if (isAlarmTriggered) {
                AlarmHelper.stopAlarm()
                isAlarmTriggered = false
                showAlarmUnlockDialog = false
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

    // Kunci Sistem HP (Immersive Sticky, Blokir Screenshot, Layar Tetap Nyala, Lock Task Mode)
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
            AlarmHelper.stopAlarm()
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
    BackHandler(enabled = isExamLocked && isAlarmTriggered) {
        if (showAlarmUnlockDialog) {
            showAlarmUnlockDialog = false
            alarmErrorMessage = null
        }
    }

    BackHandler(enabled = isExamLocked && showExitDialog) {
        showExitDialog = false
        exitCodeInput = ""
        exitErrorMessage = null
    }

    BackHandler(enabled = isExamLocked && !showExitDialog && !isAlarmTriggered) {
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
        hasPageLoadError = false
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
                                val clip = clipboardManager.getText()
                                if (clip != null && clip.text.isNotBlank()) {
                                    manualUrlInput = clip.text.trim()
                                }
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .testTag("paste_url_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Tempel URL",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(TotpManager.SETUP_KEY))
                                Toast.makeText(context, "Kode disalin", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .testTag("copy_setup_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Salin Kode Penyiapan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                showQrMenuModal = true
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .testTag("qr_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Pindai QR",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
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
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
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
                            webSettings.allowFileAccessFromFileURLs = true
                            webSettings.allowUniversalAccessFromFileURLs = true
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
                                    if (newProgress >= 90 && !hasPageLoadError) {
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
                                    hasPageLoadError = false
                                    isPageLoadedReady = false
                                    if (url != null) {
                                        currentLoadedUrl = url
                                        onUrlNavigated(url)
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    if (!hasPageLoadError) {
                                        isPageLoadedReady = true
                                    }
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

                                    // Lakukan autofill universal jika siswa sudah menyimpan kredensial untuk situs ini
                                    val parsedHost = try {
                                        if (!url.isNullOrBlank()) Uri.parse(url).host ?: "" else ""
                                    } catch (_: Exception) { "" }
                                    val currentHost = if (parsedHost.isNotBlank()) parsedHost else (view?.url?.let { try { Uri.parse(it).host } catch(_: Exception) { null } } ?: "")

                                    val saved = preferences?.getWebCredential(currentHost)
                                    if (saved != null) {
                                        val safeUser = saved.first.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "").replace("\r", "")
                                        val safePass = saved.second.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "").replace("\r", "")
                                        val autoFillJs = """
                                            (function() {
                                                function setInputValue(el, val) {
                                                    if (!el || !val) return;
                                                    try {
                                                        var desc = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                                                        if (desc && desc.set) {
                                                            desc.set.call(el, val);
                                                        } else {
                                                            el.value = val;
                                                        }
                                                    } catch(err) {
                                                        el.value = val;
                                                    }
                                                    el.dispatchEvent(new Event('input', { bubbles: true }));
                                                    el.dispatchEvent(new Event('change', { bubbles: true }));
                                                }

                                                function findUsernameInput(pwdEl) {
                                                    if (!pwdEl) return null;
                                                    if (pwdEl.form) {
                                                        var inputs = Array.from(pwdEl.form.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="checkbox"]):not([type="radio"]):not([type="password"])'));
                                                        if (inputs.length > 0) {
                                                            var pwdIdx = Array.from(pwdEl.form.elements).indexOf(pwdEl);
                                                            for (var i = inputs.length - 1; i >= 0; i--) {
                                                                var idx = Array.from(pwdEl.form.elements).indexOf(inputs[i]);
                                                                if (idx < pwdIdx) return inputs[i];
                                                            }
                                                            return inputs[0];
                                                        }
                                                    }
                                                    return document.querySelector('input[name*="user" i], input[name*="nis" i], input[name*="nopes" i], input[name*="login" i], input[name*="id" i], input[name*="peserta" i], input[id*="user" i], input[id*="nis" i], input[id*="login" i], input[type="email"], input[type="text"], input[type="number"], input[type="tel"], input[autocomplete="username"]');
                                                }

                                                function fillCredentials() {
                                                    try {
                                                        var pwd = document.querySelector('input[type="password"]');
                                                        var usr = findUsernameInput(pwd);

                                                        if (usr && !usr.value) {
                                                            setInputValue(usr, '$safeUser');
                                                        }
                                                        if (pwd && !pwd.value) {
                                                            setInputValue(pwd, '$safePass');
                                                        }
                                                    } catch(e) {}
                                                }

                                                // Eksekusi segera dan bertahap untuk menangani SPA (React, Vue, Moodle, Candy CBT)
                                                fillCredentials();
                                                setTimeout(fillCredentials, 150);
                                                setTimeout(fillCredentials, 500);
                                                setTimeout(fillCredentials, 1200);
                                                setTimeout(fillCredentials, 2500);

                                                // Otomatis isi jika siswa menyentuh/fokus ke kolom input
                                                document.addEventListener('focusin', function(e) {
                                                    if (e.target && (e.target.tagName === 'INPUT')) {
                                                        fillCredentials();
                                                    }
                                                }, { passive: true });

                                                if (window.MutationObserver) {
                                                    new MutationObserver(fillCredentials).observe(document.body || document.documentElement, { childList: true, subtree: true });
                                                }
                                            })();
                                        """.trimIndent()
                                        view?.evaluateJavascript(autoFillJs, null)
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request == null || request.isForMainFrame) {
                                        hasPageLoadError = true
                                        isPageLoadedReady = false
                                        isLoading = false
                                    }
                                }

                                @Deprecated("Deprecated in Java")
                                override fun onReceivedError(
                                    view: WebView?,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?
                                ) {
                                    hasPageLoadError = true
                                    isPageLoadedReady = false
                                    isLoading = false
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

                // Bilah footer status di bagian bawah layar saat ujian berlangsung (menampilkan gembok & Build Time di sisi kiri)
                if (isExamLocked && !isAlarmTriggered) {
                    ExamStatusFooter(
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

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
                                        if (currentLoadedUrl.startsWith("file://")) {
                                            "Berkas HTML Ujian (Lokal)"
                                        } else {
                                            Uri.parse(currentLoadedUrl).host ?: currentLoadedUrl
                                        }
                                    } catch (_: Exception) {
                                        currentLoadedUrl
                                    }
                                }

                                val isReady = !hasPageLoadError && (isPageLoadedReady || progress >= 90)

                                Icon(
                                    imageVector = if (hasPageLoadError) {
                                        Icons.Default.Error
                                    } else if (isReady) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.Language
                                    },
                                    contentDescription = null,
                                    tint = if (hasPageLoadError) {
                                        MaterialTheme.colorScheme.error
                                    } else if (isReady) {
                                        Color(0xFF2E7D32)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(48.dp)
                                )

                                Text(
                                    text = domain,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (hasPageLoadError) {
                                    Text(
                                        text = "Halaman Gagal Dimuat",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else if (!isReady && progress < 90) {
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

                                if (hasPageLoadError) {
                                    Button(
                                        onClick = {
                                            hasPageLoadError = false
                                            isPageLoadedReady = false
                                            progress = 0
                                            val target = ExamDefaults.normalizeUrl(currentLoadedUrl)
                                            webViewRef?.loadUrl(target)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("retry_load_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Coba Lagi",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            isExamLocked = true
                                            try {
                                                activity?.startLockTask()
                                            } catch (_: Exception) {}
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

                // Tombol Silang Sederhana di Pojok Kanan Atas untuk Keluar Ujian (Non-Floating)
                if (isExamLocked && !isAlarmTriggered) {
                    IconButton(
                        onClick = {
                            exitCodeInput = ""
                            exitErrorMessage = null
                            showExitDialog = true
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 10.dp, end = 10.dp)
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                shape = CircleShape
                            )
                            .testTag("exit_exam_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Keluar Ujian",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // MODAL PINDAI QR (HANYA PEMINDAI KAMERA UNTUK LINK SOAL UJIAN)
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
                            text = "Pindai QR",
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

                    // Kamera Pindai QR Tampilan Penuh
                    CameraQrScannerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        isFullscreen = true,
                        onQrDetected = { detectedUrl ->
                            showQrMenuModal = false
                            onQrDetected(detectedUrl)
                        }
                    )
                }
            }
        }

        // DIALOG KELUAR UJIAN (INPUT KODE AUTHENTICATOR PENGAWAS, TANPA KAMERA SCANNER)
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = {
                    showExitDialog = false
                    exitCodeInput = ""
                    exitErrorMessage = null
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Kunci",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "Keluar Ujian",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Masukkan 6 digit kode dari Google Authenticator pengawas untuk mengakhiri ujian:",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        OutlinedTextField(
                            value = exitCodeInput,
                            onValueChange = { input ->
                                if (input.length <= 6 && input.all { it.isDigit() }) {
                                    exitCodeInput = input
                                    exitErrorMessage = null
                                }
                            },
                            placeholder = { Text("6 digit kode") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (TotpManager.isCodeValid(exitCodeInput)) {
                                        isExitingLegitimately = true
                                        try {
                                            activity?.stopLockTask()
                                        } catch (_: Exception) {}
                                        isExamLocked = false
                                        showExitDialog = false
                                        activity?.finish()
                                    } else {
                                        exitErrorMessage = "Kode tidak valid"
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("exit_numeric_input")
                        )

                        if (exitErrorMessage != null) {
                            Text(
                                text = exitErrorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (TotpManager.isCodeValid(exitCodeInput)) {
                                isExitingLegitimately = true
                                try {
                                    activity?.stopLockTask()
                                } catch (_: Exception) {}
                                isExamLocked = false
                                showExitDialog = false
                                activity?.finish()
                            } else {
                                exitErrorMessage = "Kode tidak valid"
                            }
                        },
                        modifier = Modifier.testTag("exit_numeric_button")
                    ) {
                        Text("Keluar", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            exitCodeInput = ""
                            exitErrorMessage = null
                        },
                        modifier = Modifier.testTag("cancel_exit_button")
                    ) {
                        Text("Batal")
                    }
                }
            )
        }

        // LAYAR MERAH PENUH KETIKA KELUAR SEMATAN TANPA AUTENTIKASI (SIRENE 100% AKTIF)
        if (isAlarmTriggered) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFD32F2F))
                    .clickable {
                        alarmCodeInput = ""
                        alarmErrorMessage = null
                        showAlarmUnlockDialog = true
                    }
                    .testTag("alarm_violation_screen"),
                contentAlignment = Alignment.Center
            ) {
                // Layar merah polos dengan ikon gembok minimalis (ketuk layar untuk verifikasi pengawas)
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Ketuk untuk memasukkan kode pengawas",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(72.dp)
                )
            }
        }

        // DIALOG VERIFIKASI PENGAWAS UNTUK MEMATIKAN ALARM
        if (isAlarmTriggered && showAlarmUnlockDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAlarmUnlockDialog = false
                    alarmErrorMessage = null
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "Verifikasi Pengawas",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Masukkan 6 digit kode dari Google Authenticator untuk mematikan alarm:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = alarmCodeInput,
                            onValueChange = { input ->
                                if (input.length <= 6 && input.all { it.isDigit() }) {
                                    alarmCodeInput = input
                                    alarmErrorMessage = null
                                }
                            },
                            placeholder = { Text("6 digit kode") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("alarm_unlock_input")
                        )
                        if (alarmErrorMessage != null) {
                            Text(
                                text = alarmErrorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (TotpManager.isCodeValid(alarmCodeInput)) {
                                AlarmHelper.stopAlarm()
                                isAlarmTriggered = false
                                showAlarmUnlockDialog = false
                                alarmCodeInput = ""
                                alarmErrorMessage = null
                                try {
                                    activity?.startLockTask()
                                } catch (_: Exception) {}
                            } else {
                                alarmErrorMessage = "Kode pengawas tidak valid"
                            }
                        },
                        modifier = Modifier.testTag("alarm_resume_button")
                    ) {
                        Text("Lanjut Ujian")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            if (TotpManager.isCodeValid(alarmCodeInput)) {
                                AlarmHelper.stopAlarm()
                                isExitingLegitimately = true
                                isAlarmTriggered = false
                                showAlarmUnlockDialog = false
                                alarmCodeInput = ""
                                alarmErrorMessage = null
                                try {
                                    activity?.stopLockTask()
                                } catch (_: Exception) {}
                                isExamLocked = false
                                activity?.finish()
                            } else {
                                alarmErrorMessage = "Kode pengawas tidak valid"
                            }
                        },
                        modifier = Modifier.testTag("alarm_exit_button")
                    ) {
                        Text("Keluar", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
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

/**
 * Bilah footer status profesional di bagian bawah layar saat ujian berlangsung.
 * Menampilkan ikon gembok dan tanggal/waktu build otomatis di sisi kiri.
 */
@Composable
fun ExamStatusFooter(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("exam_status_footer"),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Status Terkunci",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Build: ${BuildConfig.BUILD_TIME}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
