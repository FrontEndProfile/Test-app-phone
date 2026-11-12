package com.fazal.antisocial.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.fazal.antisocial.data.Store
import com.fazal.antisocial.ui.BlockActivity

/**
 * Watches which app is on screen, counts time for guarded apps,
 * and shows the block screen when today's limit is over.
 * Everything stays on the phone.
 */
class GuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg: String? = null
    private var lastTick = 0L
    private var lastBlockAt = 0L
    private var lastBlockedPkg: String? = null
    private var ticks = 0
    private var imePkgs: Set<String> = emptySet()
    private val warned = HashSet<String>()
    private lateinit var power: PowerManager

    private val ignored = setOf("com.android.systemui")

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        power = getSystemService(POWER_SERVICE) as PowerManager
        imePkgs = runCatching {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList.map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
        lastTick = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored || pkg in imePkgs) return
        if (pkg == currentPkg) return
        account()
        currentPkg = pkg
        enforce()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        account()
        Store.flush()
        super.onDestroy()
    }

    private val tick = object : Runnable {
        override fun run() {
            account()
            enforce()
            ticks++
            if (ticks % 15 == 0) {
                Store.flush()
                Store.applyDuePending()
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun account() {
        val now = SystemClock.elapsedRealtime()
        val dt = now - lastTick
        lastTick = now
        val pkg = currentPkg ?: return
        if (!power.isInteractive) return
        if (Store.isGuarded(pkg)) Store.addUsageMs(pkg, dt.coerceIn(0L, 5_000L))
    }

    private fun enforce() {
        val pkg = currentPkg ?: return
        if (!power.isInteractive || !Store.isGuarded(pkg)) return
        val left = Store.remainingMs(pkg)
        if (left <= 0) {
            val now = SystemClock.elapsedRealtime()
            if (pkg == lastBlockedPkg && now - lastBlockAt < 1500) return
            lastBlockAt = now
            lastBlockedPkg = pkg
            val i = Intent(this, BlockActivity::class.java)
                .putExtra(BlockActivity.EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
        } else if (left <= 60_000L && warned.add(Store.today() + "|" + pkg + "|" + Store.bonusMin(pkg))) {
            Toast.makeText(this, "${Store.label(pkg)}: sirf 1 minute baqi", Toast.LENGTH_SHORT).show()
        }
    }
}
