package com.fazal.antisocial.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fazal.antisocial.data.GuardedApp
import com.fazal.antisocial.data.PendingType
import com.fazal.antisocial.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val BackIcon get() = Icons.AutoMirrored.Filled.ArrowBack

@Composable
fun rememberNow(periodMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
            Store.applyDuePending()
        }
    }
    return now
}

// =====================================================================
// HOME
// =====================================================================

@Composable
fun HomeScreen(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    Store.version.intValue
    Ui.resumed.intValue
    val now = rememberNow(5_000L)
    val guardOn = isGuardEnabled(ctx)
    val apps = Store.apps()
    val total = Store.totalTodayMs()
    val blocked = apps.count { Store.remainingMs(it.pkg) <= 0 }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ANTI", color = Lime, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.sp)
                Text("SOCIAL", color = TextHi, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Card)
                        .clickable { go(Screen.Setup) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (guardOn) Lime else Coral))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (guardOn) "Guard ON" else "Guard OFF",
                        color = if (guardOn) TextHi else Coral, fontSize = 12.sp, fontFamily = Mono,
                    )
                }
            }
        }

        item {
            Column {
                Eyebrow("Aaj ka social waqt")
                Text(
                    fmtDuration(total),
                    color = TextHi, fontSize = 76.sp, fontWeight = FontWeight.Black,
                    letterSpacing = (-3).sp, lineHeight = 80.sp,
                )
                Text(
                    when {
                        apps.isEmpty() -> "Abhi koi app guard nahi ho rahi"
                        blocked > 0 -> "$blocked app aaj ke liye band • ${apps.size} guarded"
                        else -> "${apps.size} apps par nazar hai"
                    },
                    color = TextLo, fontSize = 15.sp,
                )
            }
        }

        if (!guardOn) {
            item {
                Row(
                    Modifier.fillMaxWidth().card(Coral.copy(alpha = 0.12f))
                        .clickable { go(Screen.Setup) }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, null, tint = Coral)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Guard band hai", color = Coral, fontWeight = FontWeight.Bold)
                        Text("Is ke baghair limit kaam nahi karegi. Tap karke ON karo.", color = TextHi, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Section("Guarded apps") {
                if (apps.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().card().padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Pehli app add karo", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            "Instagram, TikTok, YouTube — jo bhi waqt khaati hai. Har app ki apni daily limit.",
                            color = TextLo, fontSize = 14.sp,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        apps.forEach { AppRow(it, now) { go(Screen.Detail(it.pkg)) } }
                    }
                }
            }
        }

        item { WeekChart() }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("App add karo", Modifier.weight(1f), icon = Icons.Filled.Add) { go(Screen.Picker) }
                BigButton("Coupons", Modifier.weight(1f), filled = false, icon = Icons.Filled.Lock) { go(Screen.Coupons) }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun AppRow(app: GuardedApp, now: Long, onClick: () -> Unit) {
    val used = Store.usedMs(app.pkg)
    val allowed = Store.allowedMs(app.pkg)
    val frac = if (allowed > 0) used.toFloat() / allowed else 1f
    val color = when {
        frac >= 1f -> Coral
        frac >= 0.8f -> Amber
        else -> Lime
    }
    val removing = Store.pendingFor(app.pkg).firstOrNull { it.type == PendingType.REMOVE }
    val bonus = Store.bonusMin(app.pkg)

    Row(
        Modifier.fillMaxWidth().card().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.pkg, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.label, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    maxLines = 1, modifier = Modifier.weight(1f),
                )
                if (frac >= 1f) Chip("BAND", Coral) else Chip("${fmtDuration(allowed - used)} baqi", color)
            }
            Text(
                "${fmtDuration(used)} / ${fmtMin(app.limitMin)}" + if (bonus > 0) "  +${bonus}m coupon" else "",
                color = TextLo, fontSize = 13.sp, fontFamily = Mono,
            )
            UsageBar(frac, color)
            if (removing != null) {
                Text(
                    "Guard se hat rahi hai • ${fmtCountdown(removing.applyAt - now)}",
                    color = Amber, fontSize = 12.sp, fontFamily = Mono,
                )
            }
        }
    }
}

@Composable
private fun WeekChart() {
    val days = Store.lastDays(7)
    val max = (days.maxOfOrNull { it.ms } ?: 0L).coerceAtLeast(60_000L)
    val avg = days.map { it.ms }.average().toLong()
    Section("Pichle 7 din") {
        Column(Modifier.fillMaxWidth().card().padding(18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmtDuration(avg), color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp))
                Text("rozana average", color = TextLo, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.Bottom) {
                days.forEach { d ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val h = (96f * d.ms / max).coerceAtLeast(4f)
                        Box(
                            Modifier.width(22.dp).height(h.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (d.isToday) Lime else CardHi)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(d.label, color = if (d.isToday) Lime else TextLo, fontSize = 11.sp, fontFamily = Mono)
                    }
                }
            }
        }
    }
}

// =====================================================================
// PICKER
// =====================================================================

private data class InstalledApp(val pkg: String, val label: String)

private fun loadLaunchable(ctx: Context): List<InstalledApp> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    return pm.queryIntentActivities(intent, 0)
        .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.pkg }
        .filter { it.pkg != ctx.packageName }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun PickerScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    Store.version.intValue
    var all by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<InstalledApp?>(null) }

    LaunchedEffect(Unit) { all = withContext(Dispatchers.IO) { loadLaunchable(ctx) } }

    val list = (all ?: emptyList())
        .filter { !Store.isGuarded(it.pkg) }
        .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        TopBar("App chuno", back, BackIcon)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Dhoondo… (Instagram, YouTube)", color = TextLo) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextLo) },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Lime, unfocusedBorderColor = Line,
                focusedTextColor = TextHi, unfocusedTextColor = TextHi, cursorColor = Lime,
                focusedContainerColor = Card, unfocusedContainerColor = Card,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (all == null) {
            Text("Apps load ho rahi hain…", color = TextLo, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.pkg }) { a ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { picked = a }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(a.pkg, 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(a.label, color = TextHi, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Add, null, tint = Lime)
                }
            }
        }
    }

    picked?.let { a ->
        LimitDialog(
            pkg = a.pkg, title = a.label, initial = 30,
            note = "Limit kam karna hamesha foran hota hai. Barhana 2 ghante baad.",
            confirm = "Guard karo",
            onDismiss = { picked = null },
        ) { min ->
            Store.addApp(a.pkg, a.label, min)
            picked = null
            back()
        }
    }
}

@Composable
fun LimitDialog(
    pkg: String,
    title: String,
    initial: Int,
    note: String?,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var v by remember { mutableFloatStateOf(initial.toFloat()) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().card(Card, 28.dp).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Eyebrow("Rozana limit")
            Text(fmtMin(v.toInt()), color = Lime, fontSize = 52.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
            Slider(
                value = v,
                onValueChange = { v = it },
                valueRange = 5f..240f,
                steps = 46,
                colors = SliderDefaults.colors(
                    thumbColor = Lime, activeTrackColor = Lime, inactiveTrackColor = CardHi,
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 120).forEach { m ->
                    val sel = v.toInt() == m
                    Text(
                        fmtMin(m),
                        color = if (sel) Ink else TextHi, fontFamily = Mono, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Lime else CardHi)
                            .clickable { v = m.toFloat() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            if (note != null) Text(note, color = TextLo, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Cancel", Modifier.weight(1f), filled = false, color = TextLo, onClick = onDismiss)
                BigButton(confirm, Modifier.weight(1f)) { onConfirm(v.toInt()) }
            }
        }
    }
}

// =====================================================================
// DETAIL
// =====================================================================

@Composable
fun DetailScreen(pkg: String, back: () -> Unit) {
    Store.version.intValue
    val now = rememberNow(1000L)
    val app = Store.app(pkg)
    if (app == null) {
        LaunchedEffect(Unit) { back() }
        return
    }
    var editing by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    val used = Store.usedMs(pkg)
    val allowed = Store.allowedMs(pkg)
    val bonus = Store.bonusMin(pkg)
    val frac = if (allowed > 0) used.toFloat() / allowed else 1f
    val pend = Store.pendingFor(pkg)
    val removing = pend.any { it.type == PendingType.REMOVE }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("", back, BackIcon)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(app.label, color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text(pkg, color = TextLo, fontSize = 11.sp, fontFamily = Mono, maxLines = 1)
                }
            }

            Column(Modifier.fillMaxWidth().card().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row {
                    Stat("Aaj use", fmtDuration(used), Modifier.weight(1f))
                    Stat("Limit", fmtMin(app.limitMin), Modifier.weight(1f))
                    Stat("Coupon", if (bonus > 0) "+${bonus}m" else "—", Modifier.weight(1f))
                }
                UsageBar(frac, if (frac >= 1f) Coral else if (frac >= 0.8f) Amber else Lime)
            }

            pend.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().card(Amber.copy(alpha = 0.10f)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (p.type == PendingType.REMOVE) "Guard list se hat jayegi"
                            else "Limit ${fmtMin(p.newLimit)} ho jayegi",
                            color = Amber, fontWeight = FontWeight.Bold,
                        )
                        Text("${fmtCountdown(p.applyAt - now)} baad", color = TextHi, fontSize = 13.sp, fontFamily = Mono)
                    }
                    Text(
                        "Cancel", color = TextHi, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { Store.cancelPending(p.id) }
                            .padding(10.dp),
                    )
                }
            }

            info?.let { Text(it, color = Lime, fontSize = 14.sp) }

            BigButton("Limit badlo", Modifier.fillMaxWidth()) { editing = true }
            BigButton(
                if (removing) "Hatane ki request lag chuki" else "Guard se hatao",
                Modifier.fillMaxWidth(), filled = false, color = Coral, enabled = !removing,
            ) {
                Store.requestRemove(pkg)
                info = "Theek hai — 2 ghante baad ${app.label} guard se hat jayegi. Tab tak dil badle to Cancel kar dena."
            }
            Text(
                "Limit kam karna foran hota hai. Limit barhana ya app hatana 2 ghante baad apply hota hai — taake jazbati faisle na hon. Aaj ke liye extra waqt sirf coupon se.",
                color = TextLo, fontSize = 13.sp,
            )
            Spacer(Modifier.height(20.dp))
        }
    }

    if (editing) {
        LimitDialog(
            pkg = pkg, title = app.label, initial = app.limitMin,
            note = "Kam = foran. Zyada = 2 ghante baad.",
            confirm = "Save",
            onDismiss = { editing = false },
        ) { v ->
            val instant = Store.setLimit(pkg, v)
            info = if (instant) "Limit ${fmtMin(v)} set ho gayi." else "Request lag gayi — 2 ghante baad limit ${fmtMin(v)} ho jayegi."
            editing = false
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Eyebrow(label)
        Text(value, color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

// =====================================================================
// COUPONS
// =====================================================================

@Composable
fun CouponsScreen(back: () -> Unit) {
    Store.version.intValue
    val now = rememberNow(1000L)
    var fresh by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }

    val shown = fresh
    if (shown != null) {
        FreshBook(shown) { fresh = null }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("Coupons", back, BackIcon)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                "Extra waqt ka\nsirf ek raasta.",
                color = TextHi, fontSize = 32.sp, fontWeight = FontWeight.Black, lineHeight = 36.sp,
            )
            Text(
                "Har coupon 6 digit ka hai aur sirf ek dafa chalta hai. Limit khatam hone par block screen par code daalo — us app ko utne minute mil jayenge.",
                color = TextLo, fontSize = 14.sp,
            )

            if (Store.hasEverHadBook()) {
                Section("Baqi coupons") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Store.couponsLeft().forEach { (m, count) ->
                            Column(Modifier.weight(1f).card().padding(16.dp)) {
                                Text(
                                    "$count", color = if (count > 0) Lime else TextLo,
                                    fontSize = 40.sp, fontWeight = FontWeight.Black,
                                )
                                Text("× ${fmtMin(m)}", color = TextHi, fontFamily = Mono, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Text("${Store.couponsUsed()} coupon istemal ho chuke", color = TextLo, fontSize = 13.sp, fontFamily = Mono)
            }

            val pendingBook = Store.newBookPending()
            when {
                Store.canGenerateBook() -> {
                    Column(
                        Modifier.fillMaxWidth().card(CardHi).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            if (Store.hasEverHadBook()) "Nayi book tayyar hai" else "Pehli coupon book banao",
                            color = TextHi, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        )
                        Text(
                            Store.TIERS.joinToString("  •  ") { (m, c) -> "$c × ${fmtMin(m)}" },
                            color = Lime, fontFamily = Mono, fontSize = 13.sp,
                        )
                        Text(
                            "Codes sirf EK dafa dikhenge. Kisi dost ko de do ya kaghaz par likh kar door rakh do." +
                                if (Store.hasEverHadBook()) " Nayi book purane bache hue coupons khatam kar degi." else "",
                            color = TextLo, fontSize = 13.sp,
                        )
                        BigButton("Coupon book banao", Modifier.fillMaxWidth()) { fresh = Store.generateBook() }
                    }
                }
                pendingBook != null -> {
                    Row(
                        Modifier.fillMaxWidth().card(Amber.copy(alpha = 0.10f)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Nayi book aa rahi hai", color = Amber, fontWeight = FontWeight.Bold)
                            Text("${fmtCountdown(pendingBook.applyAt - now)} baad", color = TextHi, fontFamily = Mono, fontSize = 13.sp)
                        }
                        Text(
                            "Cancel", color = TextHi, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .clickable { Store.cancelPending(pendingBook.id) }.padding(10.dp),
                        )
                    }
                }
                else -> {
                    BigButton("Nayi book mangwao (2 ghante)", Modifier.fillMaxWidth(), filled = false) {
                        Store.requestNewBook()
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FreshBook(codes: List<Pair<String, Int>>, onDone: () -> Unit) {
    BackHandler(enabled = true) { }
    var ack by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Eyebrow("Sirf ek dafa", Coral)
        Text("Ye rahe aap ke coupons", color = TextHi, fontSize = 30.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp)
        Text(
            "Is screen ke baad ye codes dobara kabhi nazar nahi aayenge. Kaghaz par likh lo ya kisi bharosemand bande ko de do — khud se door rakhna hi asal fayda hai.",
            color = TextLo, fontSize = 14.sp,
        )
        codes.groupBy { it.second }.forEach { (m, list) ->
            Section("${fmtMin(m)} wale") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    list.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (c, _) ->
                                Text(
                                    c.chunked(3).joinToString(" "),
                                    color = Lime, fontFamily = Mono, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f).card().padding(vertical = 16.dp, horizontal = 18.dp),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { ack = !ack }.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = ack, onCheckedChange = { ack = it },
                colors = CheckboxDefaults.colors(checkedColor = Lime, checkmarkColor = Ink, uncheckedColor = TextLo),
            )
            Text("Main ne likh liye / kisi ko de diye", color = TextHi)
        }
        BigButton("Done", Modifier.fillMaxWidth(), enabled = ack, onClick = onDone)
        Spacer(Modifier.height(20.dp))
    }
}

// =====================================================================
// SETUP
// =====================================================================

@Composable
fun SetupScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    Ui.resumed.intValue
    rememberNow(2000L)
    val guard = isGuardEnabled(ctx)
    val battery = isBatteryFree(ctx)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("Setup", back, BackIcon)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Guard ON karo", color = TextHi, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text(
                "Ek dafa ka kaam hai. Sab kuch phone par hi rehta hai — koi internet, koi account nahi.",
                color = TextLo, fontSize = 14.sp,
            )
            StepCard(
                1, "Restricted settings allow karo",
                "Android 13 par bahar se install hui app ko pehle ye ijazat chahiye. App info khulegi → upar ⋮ (3 dots) → \"Allow restricted settings\" → fingerprint/PIN.",
                done = if (guard) true else null, action = "App info kholo",
            ) {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            StepCard(
                2, "AntiSocial Guard ON karo",
                "Accessibility → Downloaded apps (ya Installed services) → AntiSocial Guard → ON → Allow.",
                done = guard, action = "Accessibility kholo",
            ) {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            StepCard(
                3, "Battery se azad karo",
                "Taake XOS isay background mein band na kare.",
                done = battery, action = "Allow karo",
            ) {
                runCatching {
                    @Suppress("BatteryLife")
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            StepCard(
                4, "Infinix tip",
                "Recent apps mein AntiSocial card ko neeche kheench kar lock kar do. Phone Master → App management → Auto-start mein bhi AntiSocial ON kar do.",
                done = null, action = null,
            ) {}
            if (guard && battery) {
                BigButton("Sab set hai — chalo", Modifier.fillMaxWidth(), icon = Icons.Filled.Check, onClick = back)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StepCard(
    n: Int,
    title: String,
    body: String,
    done: Boolean?,
    action: String?,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().card(if (done == true) Lime.copy(alpha = 0.08f) else Card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(if (done == true) Lime else CardHi),
                contentAlignment = Alignment.Center,
            ) {
                if (done == true) Icon(Icons.Filled.Check, null, tint = Ink, modifier = Modifier.size(18.dp))
                else Text("$n", color = TextHi, fontWeight = FontWeight.Bold, fontFamily = Mono)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Text(body, color = TextLo, fontSize = 13.sp)
        if (action != null && done != true) {
            BigButton(action, Modifier.fillMaxWidth(), filled = false, onClick = onAction)
        }
    }
}
