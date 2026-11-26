package com.debelu.ytoverlay

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MainActivity : Activity() {
    private lateinit var overlayStatusBtn: Button
    private lateinit var notifStatusBtn: Button
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
            text = "برای نمایش زیرنویس روی یوتیوب، مجوزهای زیر را فعال کنید:"
            textSize = 14f
            setTextColor(0xFF9CA3AF.toInt())
            setPadding(0, 0, 0, 36)
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

        val divider = TextView(this).apply {
            text = "تست یا اجرای دستی با لینک یوتیوب"
            textSize = 14f
            setTextColor(0xFF10B981.toInt())
            setPadding(0, 44, 0, 16)
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
                    Toast.makeText(this@MainActivity, "ابتدا مجوز اول (نمایش روی برنامه‌ها) را روشن کنید", Toast.LENGTH_LONG).show()
                } else if (url.isEmpty()) {
                    Toast.makeText(this@MainActivity, "لینک یک ویدئوی یوتیوب را وارد کنید", Toast.LENGTH_SHORT).show()
                } else {
                    startOverlayWithUrl(url)
                    finish()
                }
            }
        }

        val guide = TextView(this).apply {
            text = "راهنما: بعد از سبز شدن مجوز اول، در اپ یوتیوب زیر هر ویدئو دکمه Share را بزنید و «زیرنویس‌یار» را انتخاب کنید تا پنجره خودکار باز شود."
            textSize = 13f
            setTextColor(0xFF9CA3AF.toInt())
            setPadding(0, 36, 0, 0)
        }

        layout.addView(title); layout.addView(subtitle); layout.addView(overlayStatusBtn)
        layout.addView(notifStatusBtn); layout.addView(divider); layout.addView(urlInput)
        layout.addView(startBtn); layout.addView(guide)
        root.addView(layout)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionsUI()
    }

    private fun refreshPermissionsUI() {
        val canOverlay = Settings.canDrawOverlays(this)
        overlayStatusBtn.text = if (canOverlay) "✅ مجوز نمایش روی برنامه‌ها (فعال است)" else "⚠️ ۱. فعال‌سازی مجوز نمایش روی برنامه‌ها (کلیک کنید)"
        (overlayStatusBtn.background as GradientDrawable).setStroke(3, if (canOverlay) 0xFF10B981.toInt() else 0xFFF59E0B.toInt())

        val canNotif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        notifStatusBtn.text = if (canNotif) "✅ مجوز اعلان‌ها (فعال است)" else "🔔 ۲. فعال‌سازی مجوز اعلان‌ها (اختیاری - کلیک کنید)"
        (notifStatusBtn.background as GradientDrawable).setStroke(2, if (canNotif) 0xFF10B981.toInt() else 0xFF374151.toInt())
    }

    private fun createCardButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label; textSize = 14f; isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt()); setPadding(32, 32, 32, 32)
            background = GradientDrawable().apply { setColor(0xFF141B19.toInt()); cornerRadius = 22f; setStroke(2, 0xFF374151.toInt()) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 20) }
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
    private var captionHandled = false

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
        captionHandled = false
        subTextView.text = "در حال اتصال به پلیر یوتیوب..."
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
        uiHandler.post {
            destroyWebView()
            val wv = WebView(this)
            hiddenWebView = wv
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.mediaPlaybackRequiresUserGesture = true
            wv.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

            try {
                val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                val p = WindowManager.LayoutParams(
                    1, 1, flag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.TOP or Gravity.START }
                windowManager.addView(wv, p)
            } catch (_: Exception) { }

            wv.addJavascriptInterface(object {
                @JavascriptInterface
                fun onRawCaptions(rawBody: String) {
                    if (captionHandled) return
                    captionHandled = true
                    uiHandler.post { destroyWebView(); subTextView.text = "در حال ترجمه زیرنویس به فارسی..." }
                    processAndTranslateBody(rawBody)
                }

                @JavascriptInterface
                fun onStatus(msg: String) {
                    if (captionHandled) return
                    uiHandler.post { subTextView.text = msg }
                }

                @JavascriptInterface
                fun onFail(msg: String) {
                    if (captionHandled) return
                    captionHandled = true
                    uiHandler.post { destroyWebView(); subTextView.text = msg }
                }
            }, "AndroidBridge")

            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val reqUrl = request?.url?.toString() ?: return null
                    if (!captionHandled && reqUrl.contains("/api/timedtext")) {
                        captionHandled = true
                        val cookies = CookieManager.getInstance().getCookie(reqUrl) ?: ""
                        uiHandler.post { destroyWebView(); subTextView.text = "در حال ترجمه زیرنویس به فارسی..." }
                        serviceScope.launch {
                            try {
                                val r = Request.Builder().url(reqUrl).header("Cookie", cookies).build()
                                val body = client.newCall(r).execute().body?.string() ?: ""
                                processAndTranslateBody(body)
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { subTextView.text = "خطای دانلود: ${e.message}" }
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val js = """
                        (async function() {
                          try {
                            if (location.hostname.includes('consent.youtube')) {
                              const btn = document.querySelector('form button');
                              if (btn) btn.click();
                              return;
                            }
                            const sleep = ms => new Promise(r => setTimeout(r, ms));
                            let tracks = null;
                            for (let i = 0; i < 16; i++) {
                              document.querySelectorAll('video').forEach(v => { v.muted = true; v.pause(); });
                              const mp = document.getElementById('movie_player');
                              const pr = (mp && mp.getPlayerResponse) ? mp.getPlayerResponse() : window.ytInitialPlayerResponse;
                              tracks = pr?.captions?.playerCaptionsTracklistRenderer?.captionTracks;
                              if (tracks && tracks.length > 0) break;

                              if (i === 4 && window.ytcfg && window.ytcfg.get) {
                                try {
                                  const key = window.ytcfg.get('INNERTUBE_API_KEY');
                                  const ctx = window.ytcfg.get('INNERTUBE_CONTEXT');
                                  if (key && ctx) {
                                    const apiRes = await fetch('/youtubei/v1/player?key=' + key, {
                                      method: 'POST',
                                      headers: {'Content-Type': 'application/json'},
                                      body: JSON.stringify({ context: ctx, videoId: '$videoId' })
                                    });
                                    const apiJson = await apiRes.json();
                                    tracks = apiJson?.captions?.playerCaptionsTracklistRenderer?.captionTracks;
                                    if (tracks && tracks.length > 0) break;
                                  }
                                } catch (_) {}
                              }
                              await sleep(600);
                            }

                            if (!tracks || tracks.length === 0) {
                              const ccBtn = document.querySelector('.ytp-subtitles-button');
                              if (ccBtn) {
                                ccBtn.click();
                                await sleep(2500);
                              }
                              AndroidBridge.onFail("زیرنویسی برای این ویدئو یافت نشد (" + document.title.slice(0, 20) + ")");
                              return;
                            }

                            AndroidBridge.onStatus("در حال دریافت فایل زیرنویس...");
                            let tr = tracks.find(t => t.languageCode && t.languageCode.startsWith("en")) || tracks[0];
                            let uJson = tr.baseUrl + (tr.baseUrl.includes("fmt=") ? "" : "&fmt=json3");
                            let resp = await fetch(uJson);
                            let txt = await resp.text();
                            if (!txt || txt.length < 20) {
                              resp = await fetch(tr.baseUrl);
                              txt = await resp.text();
                            }
                            if (txt && txt.length > 20) {
                              AndroidBridge.onRawCaptions(txt);
                            } else {
                              AndroidBridge.onFail("فایل زیرنویس خالی دریافت شد.");
                            }
                          } catch (e) {
                            AndroidBridge.onFail("خطا: " + e.message);
                          }
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(js, null)
                }
            }
            wv.loadUrl("https://www.youtube.com/watch?v=$videoId&hl=en")
        }
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
                }
            } catch (_: Exception) { }

            if (raw.isEmpty()) {
                withContext(Dispatchers.Main) { subTextView.text = "متن زیرنویس قابل خواندن نبود." }
                return@launch
            }

            val res = mutableListOf<SubLine>()
            for (chunk in raw.chunked(15)) {
                val tr = translateToFa(chunk.joinToString("\n") { it.third }).split("\n")
                chunk.forEachIndexed { idx, line ->
                    res.add(SubLine(line.first, line.second, tr.getOrElse(idx) { line.third }))
                }
            }
            withContext(Dispatchers.Main) {
                subtitles = res
                subTextView.text = "آماده! هم‌زمان با ویدئو ▶ را بزنید."
            }
        }
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
