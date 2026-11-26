package com.debelu.ytoverlay

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
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

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(overlayStatusBtn)
        layout.addView(notifStatusBtn)
        layout.addView(divider)
        layout.addView(urlInput)
        layout.addView(startBtn)
        layout.addView(guide)
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

    private fun createCardButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(32, 32, 32, 32)
        background = GradientDrawable().apply {
            setColor(0xFF141B19.toInt())
            cornerRadius = 22f
            setStroke(2, 0xFF374151.toInt())
        }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, 20)
        }
        setOnClickListener { onClick() }
    }

    private fun startOverlayWithUrl(url: String) {
        try {
            val i = Intent(this, OverlayService::class.java).apply { putExtra("YOUTUBE_URL", url) }
            startService(i)
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

    private val client = OkHttpClient()
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

        isPlaying = false; currentTimeMs = 0L
        subTextView.text = "در حال دریافت و ترجمه زیرنویس..."

        serviceScope.launch {
            val loaded = fetchAndTranslateSubtitles(videoId)
            withContext(Dispatchers.Main) {
                if (loaded.isEmpty()) subTextView.text = "زیرنویسی برای این ویدئو پیدا نشد."
                else { subtitles = loaded; subTextView.text = "آماده! هم‌زمان با ویدئو ▶ را بزنید." }
            }
        }
        return START_NOT_STICKY
    }

    private fun setupOverlayWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, flag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 220
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

    private fun miniBtn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; textSize = 11f; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFF1F2937.toInt())
        setPadding(16, 4, 16, 4); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 84).apply { setMargins(6, 0, 6, 0) }
        setOnClickListener { onClick() }
    }

    private fun updateSubtitleUI() {
        val sec = currentTimeMs / 1000
        statusTextView.text = String.format("⠿ %02d:%02d", sec / 60, sec % 60)
        subtitles.find { currentTimeMs in it.startMs..it.endMs }?.let { subTextView.text = it.textFa }
    }

    private fun fetchAndTranslateSubtitles(videoId: String): List<SubLine> = try {
        val payload = """{"context":{"client":{"clientName":"ANDROID","clientVersion":"19.09.37","androidSdkVersion":30}},"videoId":"$videoId"}"""
        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val jsonStr = client.newCall(req).execute().body?.string() ?: return emptyList()
        val tracks = JSONObject(jsonStr)
            .getJSONObject("captions")
            .getJSONObject("playerCaptionsTracklistRenderer")
            .getJSONArray("captionTracks")
        if (tracks.length() == 0) return emptyList()

        val baseUrl = tracks.getJSONObject(0).getString("baseUrl") + "&fmt=json3"
        val subJson = client.newCall(Request.Builder().url(baseUrl).build()).execute().body?.string() ?: return emptyList()
        val events = JSONObject(subJson).getJSONArray("events")
        val raw = mutableListOf<Triple<Long, Long, String>>()
        for (i in 0 until events.length()) {
            val ev = events.getJSONObject(i)
            if (!ev.has("segs")) continue
            val st = ev.optLong("tStartMs", 0L); val dur = ev.optLong("dDurationMs", 2500L)
            val segs = ev.getJSONArray("segs"); val sb = StringBuilder()
            for (j in 0 until segs.length()) sb.append(segs.getJSONObject(j).optString("utf8", ""))
            val txt = sb.toString().replace("\n", " ").trim()
            if (txt.isNotEmpty()) raw.add(Triple(st, st + dur, txt))
        }
        val res = mutableListOf<SubLine>()
        for (chunk in raw.chunked(15)) {
            val tr = translateToFa(chunk.joinToString("\n") { it.third }).split("\n")
            chunk.forEachIndexed { i, it -> res.add(SubLine(it.first, it.second, tr.getOrElse(i) { it.third })) }
        }
        res
    } catch (e: Exception) { emptyList() }

    private fun translateToFa(text: String): String = try {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=fa&dt=t&q=${URLEncoder.encode(text, "UTF-8")}"
        val arr = JSONArray(client.newCall(Request.Builder().url(url).build()).execute().body?.string() ?: return text).getJSONArray(0)
        buildString { for (i in 0 until arr.length()) append(arr.getJSONArray(i).getString(0)) }
    } catch (e: Exception) { text }

    override fun onDestroy() {
        super.onDestroy(); uiHandler.removeCallbacks(ticker); serviceScope.cancel()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
    }
}
