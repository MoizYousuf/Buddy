package com.englishcoach.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Mic for typing boxes (Ideas, Mac assistant): asks for the mic permission when needed, then keeps listening
 * (Roman Urdu / English, pauses are fine) until [toggle] is tapped again. Each piece of speech goes to onText.
 * Unlike vm.dictate (tuned for the English practice: en-US, stops after 1.8 s), this does not cut the owner off.
 */
class MicState internal constructor(private val start: () -> Unit, private val listener: LongListen) {
    var listening by mutableStateOf(false); internal set
    var partial by mutableStateOf(""); internal set
    var level by mutableStateOf(0f); internal set
    fun toggle() { if (listening) stop() else start() }
    fun stop() { listener.stop(); listening = false; level = 0f }
}

@Composable
fun rememberMic(onText: (String) -> Unit): MicState {
    val ctx = LocalContext.current
    val latest by rememberUpdatedState(onText)
    val holder = remember { arrayOfNulls<MicState>(1) }
    val listener = remember {
        LongListen(ctx, { t -> latest(t) }, { p -> holder[0]?.partial = p }, { l -> holder[0]?.level = l })
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { listener.start(); holder[0]?.listening = true }
    }
    val state = remember {
        MicState({
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                listener.start(); holder[0]?.listening = true
            } else perm.launch(Manifest.permission.RECORD_AUDIO)
        }, listener).also { holder[0] = it }
    }
    DisposableEffect(Unit) { onDispose { listener.stop() } }
    return state
}
