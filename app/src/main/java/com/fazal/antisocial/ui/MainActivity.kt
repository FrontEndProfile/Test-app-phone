package com.fazal.antisocial.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fazal.antisocial.data.Store

sealed interface Screen {
    data object Home : Screen
    data object Picker : Screen
    data class Detail(val pkg: String) : Screen
    data object Coupons : Screen
    data object Setup : Screen
}

object Ui {
    /** bumps every time the app comes back to the front, so permission status refreshes */
    val resumed = mutableIntStateOf(0)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val startOnSetup = !isGuardEnabled(this)
        setContent {
            AntiSocialTheme {
                var stack by remember {
                    mutableStateOf(if (startOnSetup) listOf(Screen.Home, Screen.Setup) else listOf<Screen>(Screen.Home))
                }
                val go: (Screen) -> Unit = { stack = stack + it }
                val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
                BackHandler(enabled = stack.size > 1) { back() }

                Box(Modifier.fillMaxSize().background(Ink).systemBarsPadding()) {
                    when (val s = stack.last()) {
                        Screen.Home -> HomeScreen(go)
                        Screen.Picker -> PickerScreen(back)
                        is Screen.Detail -> DetailScreen(s.pkg, back)
                        Screen.Coupons -> CouponsScreen(back)
                        Screen.Setup -> SetupScreen(back)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Store.applyDuePending()
        Ui.resumed.intValue = Ui.resumed.intValue + 1
    }

    override fun onPause() {
        Store.flush()
        super.onPause()
    }
}
