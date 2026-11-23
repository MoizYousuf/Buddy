package com.englishcoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Phase
import com.englishcoach.app.Screen

/** Talking to the Mac: listening wave, what you said, the agent's live steps and its answer (also read aloud). */
@Composable
fun VoiceAgentOverlay(vm: CoachViewModel) {
    val listening = vm.phase == Phase.Listening
    val reply = vm.chat.lastOrNull()?.takeIf { !it.fromMe && vm.voiceHeard.isNotBlank() && vm.voiceHeard != "…" }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(MutableInteractionSource(), null) { if (!listening) vm.voiceClose() },
        contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF2A1F5C), Color(0xFF141225))), RoundedCornerShape(28.dp))
            .clickable(MutableInteractionSource(), null) {}.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Buddy(Violet, if (listening) Mood.Listening else if (vm.speaking) Mood.Talking else Mood.Idle, Modifier.size(84.dp))
            Text(when {
                listening -> "Listening… release to send"
                vm.voiceHeard == "…" -> "I didn't catch that. Hold the mic and speak again."
                reply?.working == true -> "Your Mac is working on it…"
                reply != null -> "Done"
                else -> "Sending to your Mac…"
            }, color = Color(0xFFB4A2FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (listening) VoiceWave(vm.micLevel, Modifier.fillMaxWidth().height(36.dp))
            if (vm.voiceHeard.isNotBlank() && vm.voiceHeard != "…")
                Text("“${vm.voiceHeard}”", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            reply?.let { r ->
                if (r.working) r.steps.takeLast(3).forEach {
                    Text("▸ " + it.removePrefix("▸ "), Modifier.fillMaxWidth(), color = Color(0xFFB4A2FF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())
                    .background(Color(0x1FFFFFFF), RoundedCornerShape(18.dp)).padding(14.dp)) {
                    Text(r.text, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp)
                }
                if (r.denied.isNotEmpty()) Text("Needs permission · Allow & continue", Modifier.background(lt(Color(0xFFFFF3C4)), CircleShape)
                    .clickable { vm.allowAgent(r) }.padding(horizontal = 14.dp, vertical = 8.dp), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
            if (!listening) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Open chat", Modifier.background(Color(0x22FFFFFF), CircleShape).clickable { vm.voiceClose(); vm.screen = Screen.Assistant }
                    .padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.Bold)
                Text("🎤 Ask again", Modifier.background(Violet, CircleShape).clickable { vm.voiceStart() }
                    .padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text("Close", Modifier.background(Color(0x22FFFFFF), CircleShape).clickable { vm.voiceClose() }
                    .padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.Bold)
            } else Text("Release", Modifier.background(Color(0xFFFF3D6E), CircleShape).clickable { vm.voiceRelease() }
                .padding(horizontal = 18.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.ExtraBold)
        }
    }
}
