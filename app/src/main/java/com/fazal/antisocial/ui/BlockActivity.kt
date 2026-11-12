package com.fazal.antisocial.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fazal.antisocial.data.RedeemResult
import com.fazal.antisocial.data.Store
import kotlinx.coroutines.delay

class BlockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PKG = "pkg"
    }

    private val pkgState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        pkgState.value = intent.getStringExtra(EXTRA_PKG) ?: ""
        setContent {
            AntiSocialTheme {
                Box(Modifier.fillMaxSize().background(Ink).systemBarsPadding()) {
                    BlockScreen(
                        pkg = pkgState.value,
                        onHome = { goHome() },
                        onUnlocked = { reopen(pkgState.value) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_PKG)?.let { pkgState.value = it }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun reopen(pkg: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        } else {
            goHome()
        }
    }
}

@Composable
private fun BlockScreen(pkg: String, onHome: () -> Unit, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    Store.version.intValue
    BackHandler { onHome() }

    val label = remember(pkg) { Store.app(pkg)?.label ?: appLabel(ctx, pkg) }
    var code by remember(pkg) { mutableStateOf("") }
    var message by remember(pkg) { mutableStateOf<String?>(null) }
    var unlocked by remember(pkg) { mutableStateOf<Int?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000); now = System.currentTimeMillis()
        }
    }
    val lockedFor = Store.lockedUntil() - now
    val locked = lockedFor > 0

    LaunchedEffect(unlocked) {
        if (unlocked != null) {
            delay(1400); onUnlocked()
        }
    }

    fun submit() {
        if (code.length != 6) return
        when (val r = Store.redeem(code, pkg)) {
            is RedeemResult.Ok -> { unlocked = r.minutes; message = null }
            is RedeemResult.Wrong -> message = "Ghalat ya istemal shuda code. ${r.triesLeft} koshish baqi."
            is RedeemResult.Locked -> message = "Bohat ghalat koshishen. Thora ruko."
        }
        code = ""
    }

    val ok = unlocked
    if (ok != null) {
        val s by animateFloatAsState(1f, label = "pop")
        Column(
            Modifier.fillMaxSize().background(Lime).padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("+${ok}", color = Ink, fontSize = 120.sp, fontWeight = FontWeight.Black, letterSpacing = (-6).sp, modifier = Modifier.scale(s))
            Text("minute mil gaye", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("$label khul rahi hai. Waqt ka khayal rakhna.", color = Ink.copy(alpha = 0.7f), fontSize = 15.sp)
        }
        return
    }

    val used = Store.usedMs(pkg)
    val noCoupons = Store.couponsLeft().values.sum() == 0

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ANTI", color = Lime, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
            Text("SOCIAL", color = TextHi, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            AppIcon(pkg, 30.dp)
        }
        Spacer(Modifier.height(28.dp))
        Text("Bas.", color = Lime, fontSize = 92.sp, fontWeight = FontWeight.Black, letterSpacing = (-4).sp, lineHeight = 92.sp)
        Text(
            "$label ka aaj ka waqt khatam.",
            color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold,
        )
        Text(
            "Aaj ${fmtDuration(used)} guzar chuke. Kal phir milenge.",
            color = TextLo, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.weight(1f))

        Eyebrow(if (locked) "Ruko • ${fmtCountdown(lockedFor)}" else "Coupon code", if (locked) Coral else TextLo)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until 6) {
                val ch = code.getOrNull(i)
                val active = i == code.length && !locked
                Box(
                    Modifier.weight(1f).aspectRatio(0.8f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Card)
                        .border(
                            if (active) 2.dp else 1.dp,
                            if (active) Lime else Line,
                            RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ch?.toString() ?: "", color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                }
            }
        }
        Text(
            message ?: if (noCoupons) "Koi coupon baqi nahi. Coupons section se book banao." else " ",
            color = if (message != null) Coral else TextLo, fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp).height(20.dp),
        )
        Spacer(Modifier.height(10.dp))

        Keypad(
            enabled = !locked,
            onDigit = { d ->
                if (code.length < 6) {
                    code += d; message = null
                    if (code.length == 6) submit()
                }
            },
            onDelete = { if (code.isNotEmpty()) code = code.dropLast(1) },
        )
        Spacer(Modifier.height(14.dp))
        BigButton("Home par wapis jao", Modifier.fillMaxWidth(), filled = false, color = TextHi, onClick = onHome)
    }
}

@Composable
private fun Keypad(enabled: Boolean, onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    val rows = listOf("123", "456", "789", "<0 ")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(58.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (k == ' ') Color.Transparent else CardHi)
                            .clickable(enabled = enabled && k != ' ') {
                                if (k == '<') onDelete() else onDigit(k)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            '<' -> Icon(Icons.AutoMirrored.Filled.ArrowBack, "Delete", tint = if (enabled) TextHi else TextLo)
                            ' ' -> {}
                            else -> Text(
                                "$k", color = if (enabled) TextHi else TextLo,
                                fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Mono,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private val keepWidth = Modifier.width(0.dp)
