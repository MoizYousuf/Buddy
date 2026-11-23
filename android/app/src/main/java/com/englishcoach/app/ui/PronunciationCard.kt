package com.englishcoach.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.WordScore

private val GOOD = Color(0xFF1FA85A)
private val OK = Color(0xFFE08A00)
private val BAD = Color(0xFFE5484D)

/** The corrected sentence, word by word in green / amber / red, with a score. Tap a word for its sound and a tip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PronunciationCard(vm: CoachViewModel) {
    val s = vm.strings
    if (vm.pronChecking) {
        Row(Modifier.fillMaxWidth().background(lt(Color(0xFFF7F5FD)), RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val t = rememberInfiniteTransition(label = "pron")
            val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "spin")
            Box(Modifier.size(20.dp).rotate(a).border(3.dp, Violet, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(s.pronChecking, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        return
    }
    val p = vm.pron ?: return
    var open by remember(p) { mutableStateOf(p.words.firstOrNull { it.level == "bad" } ?: p.words.firstOrNull { it.level == "ok" }) }
    val color = if (p.overall >= 80) GOOD else if (p.overall >= 60) OK else BAD
    val shown by animateFloatAsState(p.overall / 100f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "score")

    Column(Modifier.fillMaxWidth().background(lt(Color(0xFFF7F5FD)), RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.pronTitle.uppercase(), Modifier.weight(1f), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
            Text("${(shown * 100).toInt()}%", color = color, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(lt(Color(0xFFE9E5F6)), CircleShape)) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(color, CircleShape))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            p.words.forEach { w -> WordChip(w, w == open) { open = w; vm.sayWord(w.word) } }
        }
        Text(s.pronTap, color = Muted, fontSize = 11.sp)
        open?.takeIf { it.level != "good" }?.let { w ->
            Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(w.word, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Ink)
                    if (w.ipa.isNotBlank()) Text("  /${w.ipa}/", color = Violet, fontSize = 14.sp)
                }
                Text(if (w.heard == null) s.pronMissing else "“${w.heard}”", color = Muted, fontSize = 12.sp)
                w.tip?.let { Text(if (vm.roman) it.roman else it.en, color = Ink, fontSize = 13.sp, lineHeight = 18.sp) }
            }
        }
    }
}

@Composable
private fun WordChip(w: WordScore, selected: Boolean, onClick: () -> Unit) {
    val c = when (w.level) { "good" -> GOOD; "ok" -> OK; else -> BAD }
    Text(w.word, Modifier.press(onClick)
        .background(c.copy(alpha = if (selected) 0.28f else 0.13f), RoundedCornerShape(10.dp))
        .then(if (selected) Modifier.border(2.dp, c, RoundedCornerShape(10.dp)) else Modifier)
        .padding(horizontal = 10.dp, vertical = 6.dp),
        color = c, fontWeight = FontWeight.Bold, fontSize = 15.sp,
        textDecoration = if (w.heard == null) TextDecoration.LineThrough else null)
}
