package com.fazal.antisocial.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.fazal.antisocial.service.GuardService

// ---------- palette: ink + electric lime ----------
val Ink = Color(0xFF0F1110)
val Card = Color(0xFF181B19)
val CardHi = Color(0xFF212522)
val Line = Color(0xFF2B302D)
val Lime = Color(0xFFD4FF3A)
val Coral = Color(0xFFFF6B4A)
val Amber = Color(0xFFFFC145)
val TextHi = Color(0xFFF3F4EE)
val TextLo = Color(0xFF8C938E)

val Mono = FontFamily.Monospace

@Composable
fun AntiSocialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lime, onPrimary = Ink,
            secondary = Lime, onSecondary = Ink,
            background = Ink, onBackground = TextHi,
            surface = Card, onSurface = TextHi,
            surfaceVariant = CardHi, onSurfaceVariant = TextLo,
            error = Coral, outline = Line,
        ),
        content = content,
    )
}

// ---------- helpers ----------

fun fmtDuration(ms: Long): String {
    val totalMin = (ms.coerceAtLeast(0) / 60_000L)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

fun fmtMin(min: Int): String = fmtDuration(min * 60_000L)

fun fmtCountdown(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${sec}s" else "${sec}s"
}

fun appLabel(ctx: Context, pkg: String): String = runCatching {
    val pm = ctx.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

fun isGuardEnabled(ctx: Context): Boolean {
    val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    val me = ComponentName(ctx, GuardService::class.java)
    return s.split(':').any { ComponentName.unflattenFromString(it) == me }
}

fun isBatteryFree(ctx: Context): Boolean =
    (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(ctx.packageName)

// ---------- components ----------

@Composable
fun AppIcon(pkg: String, size: Dp) {
    val ctx = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val bmp = remember(pkg) {
        runCatching {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(px, px).asImageBitmap()
        }.getOrNull()
    }
    if (bmp != null) {
        Image(bmp, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(CardHi))
    }
}

fun Modifier.card(color: Color = Card, radius: Dp = 22.dp): Modifier =
    this.clip(RoundedCornerShape(radius)).background(color).border(1.dp, Line, RoundedCornerShape(radius))

@Composable
fun Eyebrow(text: String, color: Color = TextLo) {
    Text(
        text.uppercase(),
        style = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 2.sp, color = color),
    )
}

@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    color: Color = Lime,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> CardHi
        filled -> color
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> TextLo
        filled -> Ink
        else -> color
    }
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .then(if (!filled && enabled) Modifier.border(1.5.dp, color, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun UsageBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(CardHi)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

@Composable
fun Chip(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, back: ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(back, contentDescription = "Back", tint = TextHi) }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextHi)
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow(title)
        content()
    }
}

@Suppress("unused")
private fun pmFlags() = PackageManager.GET_META_DATA
