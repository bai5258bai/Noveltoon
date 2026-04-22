package com.noveltoon.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun rememberBatteryLevel(): State<Int> {
    val context = LocalContext.current
    val state = remember { mutableIntStateOf(getBatteryPercentage(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent != null) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        state.value = (level * 100 / scale)
                    }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

private fun getBatteryPercentage(context: Context): Int {
    return try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
    } catch (_: Exception) {
        0
    }
}

@Composable
fun rememberCurrentTime(): State<String> {
    val state = remember { mutableStateOf(formatNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = formatNow()
            delay(15_000)
        }
    }
    return state
}

private fun formatNow(): String {
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    return format.format(Date())
}
