package com.anipub.app

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var bars: WindowInsetsControllerCompat

    private var fsMode = false
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    //  break reminder state
    private val handler = Handler(Looper.getMainLooper())
    private var breakMin = 180            // minutes of continuous use before reminder (0 = off)
    private var usedMs = 0L
    private var resumedAt = 0L
    private var pausedAt = 0L
    private val breakTask = Runnable { fireBreak() }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val chrome = object : WebChromeClient() {
        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?) = false

        // Native fullscreen (player's own fullscreen button)
        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (customView != null || view == null) { callback?.onCustomViewHidden(); return }
            customView = view
            customCb = callback
            root.addView(view, FrameLayout.LayoutParams(-1, -1))
            web.visibility = View.GONE
            enterFullscreen(true)
        }

        override fun onHideCustomView() {
            customView?.let { root.removeView(it) }
            customView = null
            customCb?.onCustomViewHidden()
            customCb = null
            web.visibility = View.VISIBLE
            enterFullscreen(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)   // draw edge-to-edge, we pad manually

        root = FrameLayout(this)
        root.setBackgroundColor(0xFF121218.toInt())
        web = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            webViewClient = AdBlocker(setOf("anipub.xyz"))
            webChromeClient = chrome
            addJavascriptInterface(Bridge(), "Android")
        }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        bars = WindowInsetsControllerCompat(window, root)

        // Keep the page clear of status bar, navigation bar, cutout and keyboard
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            if (fsMode) {
                v.setPadding(0, 0, 0, 0)
            } else {
                val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                v.setPadding(sys.left, sys.top, sys.right, maxOf(sys.bottom, ime.bottom))
            }
            WindowInsetsCompat.CONSUMED
        }

        onBackPressedDispatcher.addCallback(this) {
            when {
                customView != null -> chrome.onHideCustomView()
                fsMode -> web.evaluateJavascript("exitFS()", null)
                web.canGoBack() -> web.goBack()
                else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        }

        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        web.loadUrl("file:///android_asset/index.html")
    }

    /** Functions the page can call as Android.xxx() */
    inner class Bridge {
        @JavascriptInterface
        fun isSystemDark(): Boolean =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        @JavascriptInterface
        fun setTheme(dark: Boolean) { runOnUiThread { applyBars(dark) } }

        @JavascriptInterface
        fun setFullscreen(on: Boolean) { runOnUiThread { enterFullscreen(on) } }

        @JavascriptInterface
        fun setBreakMinutes(m: Int) { runOnUiThread { breakMin = m; schedule() } }
    }

    private fun applyBars(dark: Boolean) {
        root.setBackgroundColor(if (dark) 0xFF121218.toInt() else 0xFFF4F5F9.toInt())
        bars.isAppearanceLightStatusBars = !dark
        bars.isAppearanceLightNavigationBars = !dark
    }

    /** Hide system bars + landscape + keep screen on while watching fullscreen. */
    private fun enterFullscreen(on: Boolean) {
        fsMode = on
        if (on) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            root.setPadding(0, 0, 0, 0)
        } else {
            bars.show(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ViewCompat.requestApplyInsets(root)
        }
    }

    //  break reminder

    private fun schedule() {
        handler.removeCallbacks(breakTask)
        if (breakMin <= 0) return
        handler.postDelayed(breakTask, maxOf(1000L, breakMin * 60_000L - usedMs))
    }

    private fun fireBreak() {
        usedMs = 0
        resumedAt = System.currentTimeMillis()
        val hrs = if (breakMin % 60 == 0) "${breakMin / 60}" else "%.1f".format(breakMin / 60f)
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Break reminders", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_break)
                .setContentTitle("AniPub \u2615 Time for a break!")
                .setContentText("You've been using AniPub for ${hrs}h. Rest your eyes and stretch.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(1, n)
        }
        web.evaluateJavascript("showBreak($breakMin)", null)
        schedule()
    }

    //  lifecycle

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        if (pausedAt > 0 && now - pausedAt > RESET_GAP) usedMs = 0
        resumedAt = now
        schedule()
        web.onResume()
    }

    override fun onPause() {
        super.onPause()
        usedMs += System.currentTimeMillis() - resumedAt
        pausedAt = System.currentTimeMillis()
        handler.removeCallbacks(breakTask)
        web.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        web.evaluateJavascript("applyTheme()", null)
    }

    override fun onDestroy() {
        handler.removeCallbacks(breakTask)
        root.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "break"
        private const val RESET_GAP = 15 * 60 * 1000L
    }
}
