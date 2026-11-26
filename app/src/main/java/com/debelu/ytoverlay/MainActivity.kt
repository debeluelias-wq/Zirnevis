package com.debelu.ytoverlay

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.Html
import android.view.*
import android.webkit.*
import android.widget.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

class MainActivity : Activity() {
    private lateinit var overlayStatusBtn: Button
    private lateinit var notifStatusBtn: Button
    private lateinit var loginStatusBtn: Button
    private lateinit var urlInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        if (sharedText != null && Settings.canDrawOverlays(this)) {
            startOverlayWithUrl(sharedText)
            finish()
            return
        }

        val root = ScrollView(this).apply { setBackgroundColor(0xFF090D0C.toInt()) }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 48, 48)
        }

        val title = TextView(this).apply {
            text = "زیرنویس‌یار شناور یوتیوب"
            textSize = 22f
            setTextColor(0xFF10B981.toInt())
            setPadding(0, 0, 0, 12)
        }

        val subtitle = TextView(this).apply {
            text = "مجوزها و اتصال حساب یوتیوب را فعال کنید:"
            textSize = 14f
            setTextColor(0xFF9CA3AF.toInt())
            setPadding(0, 0, 0, 32)
        }

        overlayStatusBtn = createCardButton("") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "مجوز نمایش روی برنامه‌ها فعال است", Toast.LENGTH_SHORT).show()
            }
        }

        notifStatusBtn = createCardButton("") {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            } else {
                Toast.makeText(this, "مجوز اعلان‌ها فعال است", Toast.LENGTH_SHORT).show()
            }
        }

        loginStatusBtn = createCardButton("") {
            openYoutubeLogin()
        }

        val divider = TextView(this).apply {
            text = "تست یا اجرای دستی با لینک یوتیوب"
            textSize = 14f
            setTextColor(0xFF10B981.toInt())
            setPadding(0, 36, 0, 16)
        }

        urlInput = EditText(this).apply {
            hint = "لینک ویدئوی یوتیوب را اینجا بچسبانید..."
            setText(sharedText ?: "")
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF6B7280.toInt())
            setPadding(32, 28, 32, 28)
            background = GradientDrawable().apply {
                setColor(0xFF141B19.toInt())
                cornerRadius = 20f
                setStroke(2, 0xFF374151.toInt())
            }
        }

        val startBtn = Button(this).apply {
            text = "▶ اجرای پنجره شناور"
            textSize = 15f
            setTextColor(0xFF000000.toInt())
            background = GradientDrawable().apply {
                setColor(0xFF10B981.toInt())
                cornerRadius = 20f
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 24, 0, 0)
            }
            setOnClickListener {
                val url = urlInput.text.toString().trim()
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "ابتدا مجوز اول را روشن کنید", Toast.LENGTH_LONG).show()
                } else if (url.isEmpty()) {
                    Toast.makeText(this@MainActivity, "لینک یک ویدئوی یوتیوب را وارد کنید", Toast.LENGTH_SHORT).show()
                } else {
                    startOverlayWithUrl(url)
                    finish()
                }
            }
        }

        val guide = TextView(this).apply {
            text = "راهنما: یک بار با گزینه ۳ وارد یوتیوب شوید؛ بعد از آن در اپ یوتیوب روی Share → زیرنویس‌یار بزنید."
            textSize = 13f
            setTextColor(0xFF9CA3AF.toInt())
            setPadding(0, 32, 0, 0)
        }

        layout.addView(title); layout.addView(subtitle); layout.addView(overlayStatusBtn)
        layout.addView(notifStatusBtn); layout.addView(loginStatusBtn); layout.addView(divider)
        layout.addView(urlInput); layout.addView(startBtn); layout.addView(guide)
        root.addView(layout)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionsUI()
    }

    private fun isYtLoggedIn(): Boolean {
        val c = CookieManager.getInstance().getCookie("https://www.youtube.com") ?: ""
        return c.contains("SID=") || c.contains("SAPISID=") || c.contains("LOGIN_INFO=")
    }

    private fun refreshPermissionsUI() {
        val canOverlay = Settings.canDrawOverlays(this)
        overlayStatusBtn.text = if (canOverlay) "✅ مجوز نمایش روی برنامه‌ها (فعال است)" else "⚠️ ۱. فعال‌سازی مجوز نمایش روی برنامه‌ها"
        (overlayStatusBtn.background as GradientDrawable).setStroke(3, if (canOverlay) 0xFF10B981.toInt() else 0xFFF59E0B.toInt())

        val canNotif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        notifStatusBtn.text = if (canNotif) "✅ مجوز اعلان‌ها (فعال است)" else "🔔 ۲. فعال‌سازی مجوز اعلان‌ها (اختیاری)"
        (notifStatusBtn.background as GradientDrawable).setStroke(2, if (canNotif) 0xFF10B981.toInt() else 0xFF374151.toInt())

        val loggedIn = isYtLoggedIn()
        loginStatusBtn.text = if (loggedIn) "✅ حساب یوتیوب متصل است (برای تغییر کلیک کنید)" else "🔑 ۳. ورود به حساب یوتیوب (رفع دائمی خطای ربات)"
        (loginStatusBtn.background as GradientDrawable).setStroke(3, if (loggedIn) 0xFF10B981.toInt() else 0xFFF59E0B.toInt())
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openYoutubeLogin() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF090D0C.toInt())
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(0xFF141B19.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        val info = TextView(this).apply {
            text = "بعد از ورود به حساب، دکمه ثبت را بزنید"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val doneBtn = Button(this).apply {
            text = "✅ ثبت و بازگشت"
            textSize = 12f
            setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFF10B981.toInt())
            setOnClickListener {
                CookieManager.getInstance().flush()
                dialog.dismiss()
                refreshPermissionsUI()
            }
        }
        topBar.addView(info); topBar.addView(doneBtn)

        val wv = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F")

        container.addView(topBar); container.addView(wv)
        dialog.setContentView(container)
        dialog.setOnDismissListener {
            CookieManager.getInstance().flush()
            wv.destroy()
            refreshPermissionsUI()
        }
        dialog.show()
    }

    private fun createCardButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label; textSize = 14f; isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt()); setPadding(32, 30, 32, 30)
            background = GradientDrawable().apply { setColor(0xFF141B19.toInt()); cornerRadius = 22f; setStroke(2, 0xFF374151.toInt()) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 18) }
            setOnClickListener { onClick() }
        }
    }

    private fun startOverlayWithUrl(url: String) {
        try {
            startService(Intent(this, OverlayService::class.java).apply { putExtra("YOUTUBE_URL", url) })
        } catch (e: Exception) {
            Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
data class SubLine(val startMs: Long, val endMs: Long, val textFa: String)

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: LinearLayout
    private lateinit var subTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var playPauseBtn: Button

    private var hiddenWebView: WebView? = null
    private val captionHandled = AtomicBoolean(false)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val uiHandler = Handler(Looper.getMainLooper())
    private var subtitles: List<SubLine> = emptyList()
    private var currentTimeMs: Long = 0L
    private var isPlaying: Boolean = false
    private var lastTickTime: Long = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (isPlaying) {
                val now = System.currentTimeMillis()
                currentTimeMs += (now - lastTickTime)
                lastTickTime = now
                updateSubtitleUI()
            }
            uiHandler.postDelayed(this, 150)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            setupOverlayWindow()
            uiHandler.post(ticker)
        } catch (e: Exception) {
            Toast.makeText(this, "خطای پنجره شناور: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("YOUTUBE_URL") ?: return START_NOT_STICKY
        val videoId = Pattern.compile("(?:v=|youtu\\.be/|shorts/)([a-zA-Z0-9_-]{11})").matcher(url).let {
            if (it.find()) it.group(1) else null
        }
        if (videoId == null) {
            subTextView.text = "لینک یوتیوب معتبر نیست."
            return START_NOT_STICKY
        }

        isPlaying = false
        currentTimeMs = 0L
        captionHandled.set(false)
        subTextView.text = "در حال اتصال به یوتیوب..."
        loadCaptionsViaWebView(videoId)
        return START_NOT_STICKY
    }

    private fun setupOverlayWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, flag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 220
        }
        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(0xE6090D0C.toInt()); cornerRadius = 28f; setStroke(3, 0xFF10B981.toInt()) }
            setPadding(28, 20, 28, 20)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusTextView = TextView(this).apply { text = "⠿ 00:00"; setTextColor(0xFF10B981.toInt()); textSize = 12f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val backBtn = miniBtn("-0.5s") { currentTimeMs = (currentTimeMs - 500).coerceAtLeast(0L); updateSubtitleUI() }
        playPauseBtn = miniBtn("▶") { isPlaying = !isPlaying; playPauseBtn.text = if (isPlaying) "⏸" else "▶"; if (isPlaying) lastTickTime = System.currentTimeMillis() }
        val fwdBtn = miniBtn("+0.5s") { currentTimeMs += 500; updateSubtitleUI() }
        val closeBtn = miniBtn("✕") { stopSelf() }

        row.addView(statusTextView); row.addView(backBtn); row.addView(playPauseBtn); row.addView(fwdBtn); row.addView(closeBtn)
        subTextView = TextView(this).apply { text = "در حال اتصال..."; setTextColor(0xFFFFFFFF.toInt()); textSize = 16f; gravity = Gravity.CENTER; setPadding(0, 14, 0, 6) }
        overlayView.addView(row); overlayView.addView(subTextView)

        overlayView.setOnTouchListener(object : View.OnTouchListener {
            var initY = 0; var touchY = 0f
            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN) { initY = params.y; touchY = e.rawY; return true }
                if (e.action == MotionEvent.ACTION_MOVE) { params.y = initY + (e.rawY - touchY).toInt(); windowManager.updateViewLayout(overlayView, params); return true }
                return false
            }
        })
        windowManager.addView(overlayView, params)
    }

    private fun miniBtn(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label; textSize = 11f; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFF1F2937.toInt())
            setPadding(16, 4, 16, 4); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 84).apply { setMargins(6, 0, 6, 0) }
            setOnClickListener { onClick() }
        }
    }

    private fun updateSubtitleUI() {
        val sec = currentTimeMs / 1000
        statusTextView.text = String.format("⠿ %02d:%02d", sec / 60, sec % 60)
        subtitles.find { currentTimeMs in it.startMs..it.endMs }?.let { subTextView.text = it.textFa }
    }
        @SuppressLint("SetJavaScriptEnabled")
    private fun loadCaptionsViaWebView(videoId: String) {
        val cookieStr = CookieManager.getInstance().getCookie("https://www.youtube.com") ?: ""
        val loggedIn = cookieStr.contains("SID=") || cookieStr.contains("SAPISID=") || cookieStr.contains("LOGIN_INFO=")
        if (loggedIn) {
            uiHandler.post {
                subTextView.text = "در حال دریافت زیرنویس از پلیر یوتیوب..."
                startWebViewFallback(videoId, "LOGGED_IN")
            }
            return
        }

        serviceScope.launch {
            withContext(Dispatchers.Main) { subTextView.text = "در حال دریافت زیرنویس..." }
            val (apiBody, apiStatus) = fetchCaptionsFromClients(videoId)
            if (!apiBody.isNullOrEmpty()) {
                onCaptionCaptured(apiBody)
                return@launch
            }
            withContext(Dispatchers.Main) {
                subTextView.text = "تلاش با پلیر داخلی..."
                startWebViewFallback(videoId, apiStatus)
            }
        }
    }

    private fun fetchCaptionsFromClients(videoId: String): Pair<String?, String> {
        val clients = listOf(
            Triple(
                "VR",
                "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; Quest 3) gzip",
                """{"context":{"client":{"clientName":"ANDROID_VR","clientVersion":"1.60.19","deviceMake":"Oculus","deviceModel":"Quest 3","osName":"Android","osVersion":"12L","androidSdkVersion":32,"hl":"en"}},"videoId":"$videoId"}"""
            ),
            Triple(
                "IOS",
                "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
                """{"context":{"client":{"clientName":"IOS","clientVersion":"19.45.4","deviceMake":"Apple","deviceModel":"iPhone16,2","osName":"iPhone","osVersion":"18.1.0.22B83","hl":"en"}},"videoId":"$videoId"}"""
            )
        )

        var lastStatus = "NO_TRACKS"
        for ((tag, ua, payload) in clients) {
            try {
                val conn = URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", ua)
                conn.doOutput = true
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val jsonStr = stream?.bufferedReader()?.use { it.readText() } ?: continue
                val root = JSONObject(jsonStr)
                val playStatus = root.optJSONObject("playabilityStatus")?.optString("status", "") ?: ""
                if (playStatus.isNotEmpty()) lastStatus = "$tag:$playStatus"

                val tracks = root.optJSONObject("captions")
                    ?.optJSONObject("playerCaptionsTracklistRenderer")
                    ?.optJSONArray("captionTracks") ?: continue
                if (tracks.length() == 0) continue

                var baseUrl = tracks.getJSONObject(0).getString("baseUrl")
                for (i in 0 until tracks.length()) {
                    val tr = tracks.getJSONObject(i)
                    if (tr.optString("languageCode").startsWith("en")) {
                        baseUrl = tr.getString("baseUrl")
                        break
                    }
                }

                val jsonUrl = if (baseUrl.contains("fmt=")) baseUrl else "$baseUrl&fmt=json3"
                var body = client.newCall(Request.Builder().url(jsonUrl).header("User-Agent", ua).build()).execute().body?.string() ?: ""
                if (body.length < 20) {
                    body = client.newCall(Request.Builder().url(baseUrl).header("User-Agent", ua).build()).execute().body?.string() ?: ""
                }
                if (body.length >= 20) return Pair(body, "OK")
                lastStatus = "$tag:EMPTY_SUB"
            } catch (e: Exception) {
                lastStatus = "$tag:ERR"
            }
        }
        return Pair(null, lastStatus)
    }

    private fun onCaptionCaptured(rawBody: String) {
        if (!captionHandled.compareAndSet(false, true)) return
        uiHandler.post {
            destroyWebView()
            subTextView.text = "در حال ترجمه زیرنویس به فارسی..."
        }
        processAndTranslateBody(rawBody)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startWebViewFallback(videoId: String, apiStatus: String) {
        destroyWebView()
        val wv = WebView(this)
        hiddenWebView = wv
        wv.alpha = 0f
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        try {
            val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val p = WindowManager.LayoutParams(
                360, 240, flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
            windowManager.addView(wv, p)
        } catch (_: Exception) { }

        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun onRawCaptions(rawBody: String) {
                if (rawBody.length >= 20) onCaptionCaptured(rawBody)
            }

            @JavascriptInterface
            fun onFail(msg: String) {
                if (!captionHandled.compareAndSet(false, true)) return
                uiHandler.post { destroyWebView(); subTextView.text = "$msg | $apiStatus" }
            }
        }, "AndroidBridge")

        val hookJs = """
            (function() {
              if (window.__subHooked) return;
              window.__subHooked = true;
              const origOpen = XMLHttpRequest.prototype.open;
              XMLHttpRequest.prototype.open = function(m, u) {
                this.__u = u ? u.toString() : '';
                return origOpen.apply(this, arguments);
              };
              const origSend = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.send = function() {
                if (this.__u && this.__u.includes('/api/timedtext')) {
                  this.addEventListener('load', function() {
                    try {
                      let txt = '';
                      if (!this.responseType || this.responseType === 'text') {
                        txt = this.responseText || '';
                      } else if (this.responseType === 'arraybuffer' && this.response) {
                        txt = new TextDecoder('utf-8').decode(this.response);
                      } else if (this.responseType === 'json' && this.response) {
                        txt = JSON.stringify(this.response);
                      } else if (this.responseType === 'document' && this.response) {
                        txt = new XMLSerializer().serializeToString(this.response);
                      }
                      if (txt && txt.length >= 20) AndroidBridge.onRawCaptions(txt);
                    } catch(_) {}
                  });
                }
                return origSend.apply(this, arguments);
              };
              const origFetch = window.fetch;
              window.fetch = async function() {
                const res = await origFetch.apply(this, arguments);
                try {
                  const a = arguments[0];
                  const u = (typeof a === 'string') ? a : (a && a.url ? a.url : '');
                  if (u.includes('/api/timedtext')) {
                    const txt = await res.clone().text();
                    if (txt && txt.length >= 20) AndroidBridge.onRawCaptions(txt);
                  }
                } catch(_) {}
                return res;
              };
            })();
        """.trimIndent()

        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val reqUrl = request?.url?.toString()
                if (reqUrl != null && reqUrl.contains("/api/timedtext") && !captionHandled.get()) {
                    val reqHeaders = request.requestHeaders?.toMap() ?: emptyMap()
                    val cookies = CookieManager.getInstance().getCookie(reqUrl) ?: ""
                    serviceScope.launch {
                        delay(1500)
                        if (captionHandled.get()) return@launch
                        try {
                            val rb = Request.Builder().url(reqUrl)
                            reqHeaders.forEach { (k, v) -> rb.header(k, v) }
                            if (cookies.isNotEmpty()) rb.header("Cookie", cookies)
                            val body = client.newCall(rb.build()).execute().body?.string() ?: ""
                            if (body.length >= 20) onCaptionCaptured(body)
                        } catch (_: Exception) { }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view?.evaluateJavascript(hookJs, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(hookJs, null)
                val triggerJs = """
                    (async function() {
                      const sleep = ms => new Promise(r => setTimeout(r, ms));
                      for (let i = 0; i < 24; i++) {
                        document.querySelectorAll('video').forEach(v => {
                          v.muted = true;
                          v.volume = 0;
                          if (v.paused) v.play().catch(()=>{});
                        });
                        const mp = document.getElementById('movie_player');
                        if (mp) {
                          try {
                            if (mp.mute) mp.mute();
                            if (i === 4 && mp.unloadModule) {
                              mp.unloadModule('captions');
                              await sleep(350);
                            }
                            if (mp.loadModule) mp.loadModule('captions');
                            let tracks = mp.getOption ? mp.getOption('captions', 'tracklist') : null;
                            if (tracks && tracks.length > 0) {
                              let target = tracks.find(t => t.languageCode && t.languageCode.startsWith('en')) || tracks[0];
                              mp.setOption('captions', 'track', target);
                              mp.setOption('captions', 'reload', true);
                            }
                          } catch(_) {}
                        }
                        const btn = document.querySelector('.ytp-subtitles-button, button[aria-label*="Subtitles"], button[aria-label*="Captions"]');
                        if (btn && btn.getAttribute('aria-pressed') !== 'true') {
                          btn.click();
                        }
                        await sleep(750);
                      }
                      AndroidBridge.onFail("پلیر زیرنویس را ارسال نکرد");
                    })();
                """.trimIndent()
                view?.evaluateJavascript(triggerJs, null)
            }
        }
        wv.loadUrl("https://www.youtube.com/watch?v=$videoId&hl=en&app=desktop&persist_app=1")
    }

    private fun processAndTranslateBody(body: String) {
        serviceScope.launch {
            val raw = mutableListOf<Triple<Long, Long, String>>()
            try {
                if (body.trim().startsWith("{")) {
                    val events = JSONObject(body).optJSONArray("events")
                    if (events != null) {
                        for (i in 0 until events.length()) {
                            val ev = events.getJSONObject(i)
                            if (!ev.has("segs")) continue
                            val st = ev.optLong("tStartMs", 0L)
                            val dur = ev.optLong("dDurationMs", 2500L)
                            val segs = ev.getJSONArray("segs")
                            val sb = StringBuilder()
                            for (j in 0 until segs.length()) sb.append(segs.getJSONObject(j).optString("utf8", ""))
                            val txt = sb.toString().replace("\n", " ").trim()
                            if (txt.isNotEmpty()) raw.add(Triple(st, st + dur, txt))
                        }
                    }
                } else if (body.contains("<text")) {
                    val m = Pattern.compile("<text start=\"([0-9.]+)\"(?: dur=\"([0-9.]+)\")?[^>]*>(.*?)</text>", Pattern.DOTALL).matcher(body)
                    while (m.find()) {
                        val st = ((m.group(1)?.toDoubleOrNull() ?: 0.0) * 1000).toLong()
                        val dur = ((m.group(2)?.toDoubleOrNull() ?: 2.5) * 1000).toLong()
                        val decoded = Html.fromHtml(m.group(3) ?: "", Html.FROM_HTML_MODE_LEGACY).toString().replace("\n", " ").trim()
                        if (decoded.isNotEmpty()) raw.add(Triple(st, st + dur, decoded))
                    }
                } else if (body.contains("<p ")) {
                    val m = Pattern.compile("<p[^>]*\\bt=\"([0-9]+)\"(?:[^>]*\\bd=\"([0-9]+)\")?[^>]*>(.*?)</p>", Pattern.DOTALL).matcher(body)
                    while (m.find()) {
                        val st = m.group(1)?.toLongOrNull() ?: 0L
                        val dur = m.group(2)?.toLongOrNull() ?: 2500L
                        val decoded = Html.fromHtml(m.group(3) ?: "", Html.FROM_HTML_MODE_LEGACY).toString().replace("\n", " ").trim()
                        if (decoded.isNotEmpty()) raw.add(Triple(st, st + dur, decoded))
                    }
                } else if (body.contains("-->")) {
                    val lines = body.replace("\r", "").split("\n")
                    var i = 0
                    while (i < lines.size) {
                        val line = lines[i].trim()
                        if (line.contains("-->")) {
                            val parts = line.split("-->")
                            val st = parseVttTimeMs(parts[0].trim())
                            val en = parseVttTimeMs(parts.getOrElse(1) { "" }.trim().split(" ")[0])
                            val sb = StringBuilder()
                            i++
                            while (i < lines.size && lines[i].trim().isNotEmpty()) {
                                val cleaned = lines[i].replace(Regex("<[^>]+>"), "").trim()
                                if (cleaned.isNotEmpty()) {
                                    if (sb.isNotEmpty()) sb.append(" ")
                                    sb.append(cleaned)
                                }
                                i++
                            }
                            val txt = Html.fromHtml(sb.toString(), Html.FROM_HTML_MODE_LEGACY).toString().trim()
                            if (txt.isNotEmpty()) raw.add(Triple(st, if (en > st) en else st + 2500L, txt))
                        }
                        i++
                    }
                }
            } catch (_: Exception) { }

            if (raw.isEmpty()) {
                withContext(Dispatchers.Main) { subTextView.text = "متن زیرنویس قابل خواندن نبود." }
                return@launch
            }

            val res = mutableListOf<SubLine>()
            for (chunk in raw.chunked(25)) {
                val tr = translateToFa(chunk.joinToString("\n") { it.third }).split("\n")
                chunk.forEachIndexed { idx, line ->
                    res.add(SubLine(line.first, line.second, tr.getOrElse(idx) { line.third }))
                }
                withContext(Dispatchers.Main) {
                    subTextView.text = "در حال ترجمه (${res.size}/${raw.size})..."
                }
            }
            withContext(Dispatchers.Main) {
                subtitles = res
                subTextView.text = "آماده! هم‌زمان با ویدئو ▶ را بزنید."
            }
        }
    }

    private fun parseVttTimeMs(t: String): Long {
        return try {
            val parts = t.replace(",", ".").split(":")
            if (parts.size == 3) {
                ((parts[0].toLong() * 3600 + parts[1].toLong() * 60) * 1000L) + (parts[2].toDouble() * 1000).toLong()
            } else if (parts.size == 2) {
                (parts[0].toLong() * 60 * 1000L) + (parts[1].toDouble() * 1000).toLong()
            } else 0L
        } catch (_: Exception) { 0L }
    }

    private fun translateToFa(text: String): String {
        return try {
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=fa&dt=t&q=${URLEncoder.encode(text, "UTF-8")}"
            val body = client.newCall(Request.Builder().url(url).build()).execute().body?.string() ?: return text
            val arr = JSONArray(body).getJSONArray(0)
            buildString { for (i in 0 until arr.length()) append(arr.getJSONArray(i).getString(0)) }
        } catch (e: Exception) {
            text
        }
    }

    private fun destroyWebView() {
        hiddenWebView?.let {
            runCatching { windowManager.removeView(it) }
            it.stopLoading()
            it.loadUrl("about:blank")
            it.destroy()
        }
        hiddenWebView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacks(ticker)
        serviceScope.cancel()
        destroyWebView()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
    }
}
