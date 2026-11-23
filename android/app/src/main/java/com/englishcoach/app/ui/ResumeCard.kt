package com.englishcoach.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel

/** Upload, check and switch on/off the CV that the interviewer uses. */
@Composable
fun ResumeCard(vm: CoachViewModel) {
    val s = vm.strings
    val context = LocalContext.current
    LaunchedEffect(vm.paired) { vm.loadResume() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { vm.uploadResume(it.readBytes()) }
    }
    val r = vm.resume
    Column(Modifier.fillMaxWidth().card(24).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            !vm.paired -> Text(s.cvNeedsPair, color = Muted, fontSize = 13.sp)
            vm.resumeBusy -> Row(verticalAlignment = Alignment.CenterVertically) {
                val t = rememberInfiniteTransition(label = "cv")
                val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin")
                Box(Modifier.size(22.dp).rotate(a).border(3.dp, Violet, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(s.cvReading, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            r == null -> {
                Text(s.cvText, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                Pill(s.cvUpload, Modifier.fillMaxWidth()) { pick.launch(arrayOf("application/pdf")) }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(Pastel, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Text("CV", color = Violet, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title.ifBlank { "—" }, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Ink)
                        if (r.years.isNotBlank()) Text(s.cvYears.format(r.years), color = Muted, fontSize = 12.sp)
                    }
                }
                if (r.summary.isNotBlank()) Text(r.summary, color = Ink, fontSize = 13.sp, lineHeight = 18.sp)
                if (r.skills.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.skills.forEach { Text(it, Modifier.background(Pastel, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                        color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
                r.projects.take(4).forEach { (name, what) ->
                    Column(Modifier.fillMaxWidth().background(lt(Color(0xFFF7F5FD)), RoundedCornerShape(14.dp)).padding(10.dp)) {
                        Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Ink)
                        if (what.isNotBlank()) Text(what, fontSize = 12.sp, color = Muted, lineHeight = 16.sp)
                    }
                }
                SettingRow(s.cvUse) { AppSwitch(vm.useResume) { vm.updateUseResume(it) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(s.cvReplace, Modifier.weight(1f), outline = true) { pick.launch(arrayOf("application/pdf")) }
                    Text(s.cvRemove, Modifier.weight(1f).press { vm.deleteResume() }.padding(vertical = 16.dp),
                        textAlign = TextAlign.Center, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                }
            }
        }
        vm.resumeError?.let { Text(it, color = Pink, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    }
}
