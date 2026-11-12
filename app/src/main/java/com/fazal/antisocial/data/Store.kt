package com.fazal.antisocial.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class GuardedApp(val pkg: String, val label: String, val limitMin: Int)

enum class PendingType { REMOVE, INCREASE, NEW_BOOK }

data class PendingChange(
    val id: Long,
    val type: PendingType,
    val pkg: String?,
    val newLimit: Int,
    val applyAt: Long,
)

data class Coupon(val hash: String, val minutes: Int, val used: Boolean)

sealed class RedeemResult {
    data class Ok(val minutes: Int) : RedeemResult()
    data class Wrong(val triesLeft: Int) : RedeemResult()
    data class Locked(val untilMs: Long) : RedeemResult()
}

data class DayTotal(val label: String, val ms: Long, val isToday: Boolean)

/**
 * All app data lives here, on the phone only (SharedPreferences).
 * Single process: the guard service and the screens share this object.
 */
object Store {
    const val WAIT_MS = 2L * 60 * 60 * 1000
    /** coupon minutes to how many coupons of that kind in one book */
    val TIERS = listOf(10 to 5, 30 to 3, 60 to 2)
    private const val MAX_TRIES = 3

    private lateinit var prefs: SharedPreferences
    private var initialized = false

    /** Screens read this so they redraw whenever data changes. */
    val version = mutableIntStateOf(0)

    private var apps = mutableListOf<GuardedApp>()
    private var pending = mutableListOf<PendingChange>()
    private var coupons = mutableListOf<Coupon>()

    private var day = ""
    private val usage = mutableMapOf<String, Long>() // ms used today
    private val bonus = mutableMapOf<String, Int>()  // coupon minutes today
    private var usageDirty = false

    private val dayFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun init(ctx: Context) {
        if (initialized) return
        prefs = ctx.applicationContext.getSharedPreferences("antisocial", Context.MODE_PRIVATE)
        apps = readApps()
        pending = readPending()
        coupons = readCoupons()
        initialized = true
        ensureDay()
        applyDuePending()
    }

    private fun bump() {
        version.intValue = version.intValue + 1
    }

    fun today(): String = dayFmt.format(Date())

    // ---------------- day / usage ----------------

    private fun ensureDay() {
        val t = today()
        if (t == day) return
        if (day.isNotEmpty()) flush()
        day = t
        usage.clear()
        bonus.clear()
        readMap("usage_$t").forEach { (k, v) -> usage[k] = v }
        readMap("bonus_$t").forEach { (k, v) -> bonus[k] = v.toInt() }
        cleanupOldDays()
    }

    fun addUsageMs(pkg: String, ms: Long) {
        if (ms <= 0) return
        ensureDay()
        usage[pkg] = (usage[pkg] ?: 0L) + ms
        usageDirty = true
        bump()
    }

    fun usedMs(pkg: String): Long {
        ensureDay(); return usage[pkg] ?: 0L
    }

    fun bonusMin(pkg: String): Int {
        ensureDay(); return bonus[pkg] ?: 0
    }

    fun allowedMs(pkg: String): Long {
        val app = app(pkg) ?: return Long.MAX_VALUE
        return (app.limitMin + bonusMin(pkg)) * 60_000L
    }

    fun remainingMs(pkg: String): Long = allowedMs(pkg) - usedMs(pkg)

    fun totalTodayMs(): Long {
        ensureDay(); return usage.values.sum()
    }

    private fun addBonus(pkg: String, minutes: Int) {
        ensureDay()
        bonus[pkg] = (bonus[pkg] ?: 0) + minutes
        writeMap("bonus_$day", bonus.mapValues { it.value.toLong() })
        bump()
    }

    fun flush() {
        if (!initialized || !usageDirty || day.isEmpty()) return
        writeMap("usage_$day", usage)
        usageDirty = false
    }

    fun lastDays(n: Int = 7): List<DayTotal> {
        ensureDay()
        val cal = Calendar.getInstance()
        val labelFmt = SimpleDateFormat("EEE", Locale.US)
        val out = ArrayList<DayTotal>()
        for (i in 0 until n) {
            val key = dayFmt.format(cal.time)
            val ms = if (i == 0) usage.values.sum() else readMap("usage_$key").values.sum()
            out.add(DayTotal(labelFmt.format(cal.time).take(2), ms, i == 0))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return out.reversed()
    }

    private fun cleanupOldDays() {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
        val cutoff = dayFmt.format(cal.time)
        val ed = prefs.edit()
        for (k in prefs.all.keys) {
            val d = when {
                k.startsWith("usage_") -> k.removePrefix("usage_")
                k.startsWith("bonus_") -> k.removePrefix("bonus_")
                else -> continue
            }
            if (d < cutoff) ed.remove(k)
        }
        ed.apply()
    }

    // ---------------- guarded apps ----------------

    fun apps(): List<GuardedApp> = apps.toList()
    fun app(pkg: String): GuardedApp? = apps.firstOrNull { it.pkg == pkg }
    fun isGuarded(pkg: String): Boolean = apps.any { it.pkg == pkg }
    fun label(pkg: String): String = app(pkg)?.label ?: pkg

    fun addApp(pkg: String, label: String, limitMin: Int) {
        if (isGuarded(pkg)) return
        apps.add(GuardedApp(pkg, label, limitMin))
        saveApps(); bump()
    }

    /** Lower limit = instant. Higher limit = waits 2 hours. Returns true if applied now. */
    fun setLimit(pkg: String, newLimit: Int): Boolean {
        val cur = app(pkg) ?: return false
        pending.removeAll { it.type == PendingType.INCREASE && it.pkg == pkg }
        val now = newLimit <= cur.limitMin
        if (now) {
            apps = apps.map { if (it.pkg == pkg) it.copy(limitMin = newLimit) else it }.toMutableList()
            saveApps()
        } else {
            pending.add(newPending(PendingType.INCREASE, pkg, newLimit))
        }
        savePending(); bump()
        return now
    }

    fun requestRemove(pkg: String) {
        if (pending.any { it.type == PendingType.REMOVE && it.pkg == pkg }) return
        pending.add(newPending(PendingType.REMOVE, pkg, 0))
        savePending(); bump()
    }

    // ---------------- pending (2 hour wait) ----------------

    fun pending(): List<PendingChange> = pending.sortedBy { it.applyAt }
    fun pendingFor(pkg: String): List<PendingChange> = pending.filter { it.pkg == pkg }

    fun cancelPending(id: Long) {
        pending.removeAll { it.id == id }
        savePending(); bump()
    }

    private fun newPending(type: PendingType, pkg: String?, newLimit: Int): PendingChange {
        val now = System.currentTimeMillis()
        return PendingChange(now, type, pkg, newLimit, now + WAIT_MS)
    }

    fun applyDuePending(): Boolean {
        if (!initialized) return false
        val now = System.currentTimeMillis()
        val due = pending.filter { it.applyAt <= now }
        if (due.isEmpty()) return false
        for (p in due) {
            when (p.type) {
                PendingType.REMOVE -> {
                    apps.removeAll { it.pkg == p.pkg }
                    pending.removeAll { it.pkg == p.pkg }
                }
                PendingType.INCREASE -> apps = apps.map {
                    if (it.pkg == p.pkg) it.copy(limitMin = p.newLimit) else it
                }.toMutableList()
                PendingType.NEW_BOOK -> prefs.edit().putBoolean("book_unlocked", true).apply()
            }
        }
        pending.removeAll(due)
        saveApps(); savePending(); bump()
        return true
    }

    // ---------------- coupons ----------------

    fun hasEverHadBook(): Boolean = prefs.getBoolean("book_ever", false)
    fun canGenerateBook(): Boolean = !hasEverHadBook() || prefs.getBoolean("book_unlocked", false)
    fun newBookPending(): PendingChange? = pending.firstOrNull { it.type == PendingType.NEW_BOOK }

    fun requestNewBook() {
        if (newBookPending() != null || canGenerateBook()) return
        pending.add(newPending(PendingType.NEW_BOOK, null, 0))
        savePending(); bump()
    }

    /** minutes -> unused count */
    fun couponsLeft(): Map<Int, Int> =
        TIERS.associate { (m, _) -> m to coupons.count { it.minutes == m && !it.used } }

    fun couponsUsed(): Int = coupons.count { it.used }

    /** Makes a fresh book. The plain codes are returned ONCE and never stored. */
    fun generateBook(): List<Pair<String, Int>> {
        if (!canGenerateBook()) return emptyList()
        val rnd = SecureRandom()
        val seen = HashSet<String>()
        val out = ArrayList<Pair<String, Int>>()
        for ((minutes, count) in TIERS) {
            repeat(count) {
                var c: String
                do {
                    c = String.format(Locale.US, "%06d", rnd.nextInt(1_000_000))
                } while (!seen.add(c))
                out.add(c to minutes)
            }
        }
        coupons = out.map { Coupon(hash(it.first), it.second, false) }.toMutableList()
        prefs.edit().putBoolean("book_ever", true).putBoolean("book_unlocked", false).apply()
        saveCoupons(); bump()
        return out
    }

    fun lockedUntil(): Long = prefs.getLong("lock_until", 0L)

    fun redeem(code: String, pkg: String): RedeemResult {
        val now = System.currentTimeMillis()
        val lock = lockedUntil()
        if (now < lock) return RedeemResult.Locked(lock)
        val h = hash(code)
        val idx = coupons.indexOfFirst { it.hash == h && !it.used }
        if (idx < 0) {
            val fails = prefs.getInt("fails", 0) + 1
            if (fails >= MAX_TRIES) {
                val lockouts = prefs.getInt("lockouts", 0)
                val until = now + 60_000L * (1L shl lockouts.coerceAtMost(6))
                prefs.edit().putInt("fails", 0).putInt("lockouts", lockouts + 1)
                    .putLong("lock_until", until).apply()
                bump()
                return RedeemResult.Locked(until)
            }
            prefs.edit().putInt("fails", fails).apply()
            return RedeemResult.Wrong(MAX_TRIES - fails)
        }
        val c = coupons[idx]
        coupons[idx] = c.copy(used = true)
        saveCoupons()
        prefs.edit().putInt("fails", 0).putInt("lockouts", 0).apply()
        addBonus(pkg, c.minutes)
        return RedeemResult.Ok(c.minutes)
    }

    private fun hash(code: String): String {
        var salt = prefs.getString("salt", null)
        if (salt == null) {
            val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
            salt = b.joinToString("") { "%02x".format(it) }
            prefs.edit().putString("salt", salt).apply()
        }
        val d = MessageDigest.getInstance("SHA-256").digest((salt + code).toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    // ---------------- persistence ----------------

    private fun readApps(): MutableList<GuardedApp> {
        val arr = JSONArray(prefs.getString("apps", "[]"))
        return MutableList(arr.length()) {
            val o = arr.getJSONObject(it)
            GuardedApp(o.getString("pkg"), o.getString("label"), o.getInt("limit"))
        }
    }

    private fun saveApps() {
        val arr = JSONArray()
        apps.forEach { arr.put(JSONObject().put("pkg", it.pkg).put("label", it.label).put("limit", it.limitMin)) }
        prefs.edit().putString("apps", arr.toString()).apply()
    }

    private fun readPending(): MutableList<PendingChange> {
        val arr = JSONArray(prefs.getString("pending", "[]"))
        return MutableList(arr.length()) {
            val o = arr.getJSONObject(it)
            PendingChange(
                o.getLong("id"),
                PendingType.valueOf(o.getString("type")),
                if (o.isNull("pkg")) null else o.getString("pkg"),
                o.getInt("newLimit"),
                o.getLong("applyAt"),
            )
        }
    }

    private fun savePending() {
        val arr = JSONArray()
        pending.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("type", it.type.name)
                    .put("pkg", it.pkg ?: JSONObject.NULL)
                    .put("newLimit", it.newLimit).put("applyAt", it.applyAt)
            )
        }
        prefs.edit().putString("pending", arr.toString()).apply()
    }

    private fun readCoupons(): MutableList<Coupon> {
        val arr = JSONArray(prefs.getString("coupons", "[]"))
        return MutableList(arr.length()) {
            val o = arr.getJSONObject(it)
            Coupon(o.getString("h"), o.getInt("m"), o.getBoolean("u"))
        }
    }

    private fun saveCoupons() {
        val arr = JSONArray()
        coupons.forEach { arr.put(JSONObject().put("h", it.hash).put("m", it.minutes).put("u", it.used)) }
        prefs.edit().putString("coupons", arr.toString()).apply()
    }

    private fun readMap(key: String): Map<String, Long> {
        val o = JSONObject(prefs.getString(key, "{}") ?: "{}")
        val m = HashMap<String, Long>()
        o.keys().forEach { m[it] = o.getLong(it) }
        return m
    }

    private fun writeMap(key: String, map: Map<String, Long>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(key, o.toString()).apply()
    }
}
