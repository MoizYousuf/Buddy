package com.englishcoach.app.ui

import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Phase
import com.englishcoach.app.ROLES
import com.englishcoach.app.Role
import com.englishcoach.app.Screen
import com.englishcoach.app.TAB_SCREENS

/** App theme: 0 = light, 1 = dark gold. Colours below are read live, so every screen follows the switch. */
var appDark by androidx.compose.runtime.mutableStateOf(false)
val Ink get() = if (appDark) Color(0xFFF5EBD0) else Color(0xFF1A1530)
val Muted get() = if (appDark) Color(0xFFB5A887) else Color(0xFF6E6890)
val Surface get() = if (appDark) Color(0xFF221D14) else Color.White
/** A light pastel background turned into a dark tint of the same colour in the dark theme (text stays readable). */
fun lt(c: Color): Color = if (appDark) androidx.compose.ui.graphics.lerp(c, Color(0xFF15120C), 0.84f) else c
/** Soft lavender for chips and segmented controls; a warm dark tone in the dark theme. */
val Pastel get() = if (appDark) Color(0xFF2E2719) else Color(0xFFEDE8FF)
/** Dark text for light/pastel chips, readable in both themes. */
val InkOnLight = Color(0xFF1A1530)
val Violet = Color(0xFF6C4DFF)
val Lime = Color(0xFFB8F03A)
val Pink = Color(0xFFFF5FA2)
val Paper get() = if (appDark) Color(0xFF15120C) else Color(0xFFF6F5FB)
val RAINBOW = listOf(Violet, Color(0xFF3BC9FF), Lime, Color(0xFFFFC83D), Pink, Violet)

/** A springy press effect, used on every tappable card and button. */
/** Set from settings; read by [press] so every tap respects the vibration switch. */
var hapticsEnabled = true

@Composable
fun Modifier.press(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "press")
    val haptic = LocalHapticFeedback.current
    return scale(s).clickable(source, null) { if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
}

fun Modifier.card(radius: Int = 28) = shadow(14.dp, RoundedCornerShape(radius.dp), ambientColor = Color(0x221E1450),
    spotColor = if (appDark) Color(0x66C9A227) else Color(0x331E1450))
    .background(Surface, RoundedCornerShape(radius.dp))
    .then(if (appDark) Modifier.border(1.dp, Color(0x33C9A227), RoundedCornerShape(radius.dp)) else Modifier)

@Composable
fun App(vm: CoachViewModel) {
    androidx.compose.material3.MaterialTheme(colorScheme = if (appDark) androidx.compose.material3.darkColorScheme(
        primary = Violet, onPrimary = Color.White, surface = Color(0xFF221D14), onSurface = Color(0xFFF5EBD0),
        surfaceVariant = Color(0xFF2E2719), onSurfaceVariant = Color(0xFFD6C9A6), background = Color(0xFF15120C), onBackground = Color(0xFFF5EBD0),
        surfaceContainerHighest = Color(0xFF2E2719), outline = Color(0xFF6B5D3E))
        else androidx.compose.material3.lightColorScheme(primary = Violet)) { AppBody(vm) }
}

@Composable
private fun AppBody(vm: CoachViewModel) {
    hapticsEnabled = vm.haptics
    val uiCtx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { appDark = uiCtx.getSharedPreferences("ui", 0).getBoolean("dark", false) }
    com.englishcoach.app.GuardAsk.ask?.let { (why, go) -> CreditWarning(why, { com.englishcoach.app.GuardAsk.ask = null; go() }) { com.englishcoach.app.GuardAsk.ask = null } }
    Box(Modifier.fillMaxSize().background(Paper)) {
        // soft colour blobs behind everything
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Color(0xAACFC4FF), Color.Transparent), Offset(0f, 0f), size.width * 0.8f), size.width * 0.8f, Offset(0f, 0f))
            drawCircle(Brush.radialGradient(listOf(Color(0xAAC9F5FF), Color.Transparent), Offset(size.width, size.height * 0.3f), size.width * 0.6f), size.width * 0.6f, Offset(size.width, size.height * 0.3f))
        }
        if (!vm.onboarded) { OnboardingScreen(vm); return@Box }
        androidx.compose.animation.AnimatedContent(vm.screen, transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(320)) { it / 14 }) togetherWith fadeOut(tween(150))
        }, label = "screen") { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(vm)
                Screen.Practice -> PracticeScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
                Screen.Progress -> ProgressScreen(vm)
                Screen.Review -> ReviewScreen(vm)
                Screen.Report -> ReportScreen(vm)
                Screen.Mac -> MacScreen(vm)
                Screen.Assistant -> AssistantScreen(vm)
                Screen.Remote -> RemoteScreen(vm)
                Screen.Cleaner -> CleanerScreen(vm)
                Screen.Studio -> StudioScreen(vm)
                Screen.Alerts -> AlertsScreen(vm)
                Screen.Videos -> VideosScreen(vm)
                Screen.Transfer -> TransferScreen(vm)
                Screen.Routines -> RoutinesScreen(vm)
                Screen.Naat -> NaatScreen(vm)
                Screen.Weekly -> WeeklyScreen(vm)
                Screen.Activity -> ActivityScreen(vm)
                Screen.Rules -> RulesScreen(vm)
                Screen.Stores -> StoreStudioScreen(vm)
                Screen.Money -> MoneyScreen(vm)
                Screen.Ideas -> IdeasScreen(vm)
                Screen.Staff -> StaffScreen(vm)
                Screen.AppAssistant -> AppAssistantScreen(vm)
                Screen.Cv -> CvStudioScreen(vm)
            }
        }
        if (vm.screen in TAB_SCREENS) NavBar(vm, Modifier.align(Alignment.BottomCenter))
        if (vm.voiceOpen) VoiceAgentOverlay(vm)
    }
}

@Composable
fun LanguageToggle(vm: CoachViewModel) {
    Row(Modifier.card(999).padding(3.dp)) {
        listOf(false to "EN", true to "Roman").forEach { (ro, label) ->
            val on = vm.roman == ro
            Text(label, Modifier.clip(CircleShape).background(if (on) Violet else Color.Transparent)
                .clickable { vm.setRomanUrdu(ro) }.padding(horizontal = 14.dp, vertical = 8.dp),
                color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
fun HomeScreen(vm: CoachViewModel) {
    val s = vm.strings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(18.dp).padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        vm.storeVersion
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Chip("🔥 ${vm.store.streak}")
            Spacer(Modifier.width(8.dp))
            Chip("⭐ ${vm.store.xp} XP")
            Spacer(Modifier.weight(1f))
            LanguageToggle(vm)
        }
        if (vm.paired) BriefCard(vm)
        WordsCard(vm)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.hello + vm.ownerName.let { if (it.isBlank()) "" else " $it" }, color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(buildAnnotatedString {
                    append(s.headline1 + " ")
                    withStyle(SpanStyle(color = Violet)) { append(s.headline2) }
                }, fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            }
            Buddy(Violet, com.englishcoach.app.ui.Mood.Talking, Modifier.size(116.dp))
        }
        Row(Modifier.fillMaxWidth().press { vm.start(ROLES[0]) }
            .shadow(20.dp, RoundedCornerShape(30.dp), spotColor = Violet).background(InkOnLight, RoundedCornerShape(30.dp)).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.drillTag.uppercase(), color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(6.dp))
                Text(s.drillText, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 23.sp)
            }
            Box(Modifier.size(56.dp).background(Lime, CircleShape), contentAlignment = Alignment.Center) {
                LineIcon(Icons.PLAY, Ink, filled = true)
            }
        }
        JourneyCard(vm)
        if (vm.paired) Row(Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFF7C3AED))), RoundedCornerShape(26.dp))
            .press { vm.cvOpen = null; vm.screen = Screen.Cv }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📄", fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("CV studio", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text("Share a job → a CV made for it · edit it here · PDF", color = Color.White.copy(alpha = .9f), fontSize = 13.sp)
            }
            Text("→", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (vm.resume != null) Row(Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF0E7490))), RoundedCornerShape(26.dp))
            .press { vm.startCvInterview() }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("💼", fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("CV Interview", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(if (vm.cvPlanLoading) "Preparing questions from your CV…" else "10 real questions from your CV · coach's report at the end",
                    color = Color.White.copy(alpha = .9f), fontSize = 13.sp)
            }
            Text("▶", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(s.practiceWith, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ROLES.forEach { RoleCard(it) { vm.start(it) } }
        }
        CustomRoleCard(vm)
        NotebookCard(vm)
        MacStatus(vm)
    }
}

@Composable
fun RoleCard(role: Role, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).height(196.dp).press(onClick).background(role.tint, RoundedCornerShape(28.dp)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Buddy(role.color, Mood.Idle, Modifier.size(96.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(role.title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = InkOnLight)
            Text(role.persona, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = InkOnLight.copy(alpha = 0.65f))
        }
    }
}

@Composable
fun MacStatus(vm: CoachViewModel) {
    val s = vm.strings
    val online = vm.macOnline
    Row(Modifier.fillMaxWidth().card(22).press { vm.checkServer() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(when (online) { true -> Color(0xFF2BC46B); false -> Pink; null -> Muted }, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(if (online == false) s.macOffline else s.macOnline, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)
            if (online == false) Text(s.macOfflineHint, fontSize = 12.sp, color = Muted)
        }
    }
}

/** Spinning rainbow ring around the character; spins faster while listening. */
@Composable
fun AiRing(fast: Boolean, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "ring")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (fast) 1400 else 5000, easing = LinearEasing)), label = "angle")
    Canvas(modifier) {
        rotate(angle) {
            drawCircle(Brush.sweepGradient(RAINBOW), size.minDimension / 2 - 4.dp.toPx(), style = Stroke(5.dp.toPx()))
        }
    }
}

@Composable
fun PracticeScreen(vm: CoachViewModel) {
    val s = vm.strings
    val role = vm.role
    val mood = when (vm.phase) {
        Phase.Praise -> Mood.Happy
        Phase.Listening -> Mood.Listening
        Phase.Thinking, Phase.Sheet -> Mood.Thinking
        else -> if (vm.speaking) Mood.Talking else Mood.Idle
    }
    Box(Modifier.fillMaxSize()) {
        LiveWaves(if (vm.phase == Phase.Listening) vm.micLevel else 0f, vm.speaking, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).card(22).press { vm.leave() }, contentAlignment = Alignment.Center) { LineIcon(Icons.BACK, Ink, strokeWidth = 2.4f) }
                Spacer(Modifier.width(10.dp))
                val total = vm.sessionLength
                val progress by animateFloatAsState(vm.questionNumber.coerceAtMost(total) / total.toFloat(), spring(Spring.DampingRatioMediumBouncy), label = "progress")
                Box(Modifier.weight(1f).height(12.dp).background(lt(Color(0xFFE4E0F5)), CircleShape)) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Lime, CircleShape))
                }
                Spacer(Modifier.width(10.dp))
                Text("${vm.questionNumber.coerceAtMost(total)}/$total", fontWeight = FontWeight.ExtraBold, color = Ink)
            }
            Column(Modifier.fillMaxWidth().weight(1f).shadow(24.dp, RoundedCornerShape(34.dp), spotColor = role.color)
                .background(role.tint, RoundedCornerShape(34.dp)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(role.persona, Modifier.align(Alignment.End).background(Color(0xBFFFFFFF), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InkOnLight)
                Spacer(Modifier.weight(0.4f))
                Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                    AiRing(vm.phase == Phase.Listening, Modifier.fillMaxSize())
                    Buddy(role.color, mood, Modifier.size(176.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text(vm.question, Modifier.fillMaxWidth().background(Color(0xE0FFFFFF), RoundedCornerShape(22.dp)).padding(16.dp),
                    textAlign = TextAlign.Center, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, color = InkOnLight)
                Spacer(Modifier.weight(0.6f))
            }
            val status = vm.notice ?: when (vm.phase) {
                Phase.Listening -> when {
                    vm.transcribing -> s.transcribing
                    vm.urduMode -> s.listeningUrdu
                    else -> vm.heard.ifBlank { s.listening }
                }
                Phase.Thinking -> "“${vm.heard}” · ${s.thinking}"
                Phase.Praise -> s.praise
                else -> s.tapToAnswer
            }
            if (vm.phase == Phase.Listening) VoiceWave(vm.micLevel, Modifier.fillMaxWidth().height(44.dp))
            Text(status, Modifier.fillMaxWidth().heightIn(min = 40.dp), textAlign = TextAlign.Center,
                color = if (vm.phase == Phase.Praise) Violet else Muted, fontWeight = FontWeight.SemiBold, fontSize = if (vm.phase == Phase.Praise) 20.sp else 14.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // left: say it in Urdu (Whisper on the Mac turns it into a lesson)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(Modifier.press { vm.helpInUrdu() }.card(22).padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("اردو", color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text(s.helpUrdu, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                MicButton(vm.phase == Phase.Listening, vm.micLevel) { vm.mic() }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (vm.useWhisper) Text("Whisper", Modifier.background(Pastel, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                        color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        AnimatedVisibility(vm.phase == Phase.Sheet, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(Spring.DampingRatioLowBouncy)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()) {
            CorrectionSheet(vm)
        }
    }
}

/** Rainbow bars that jump with the learner's voice, so it's obvious the mic is hearing them. */
@Composable
fun VoiceWave(level: Float, modifier: Modifier) {
    val smooth by animateFloatAsState(level, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium), label = "level")
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
    Canvas(modifier) {
        val bars = 21
        val gap = size.width / bars
        val w = gap * 0.45f
        for (i in 0 until bars) {
            val center = 1f - kotlin.math.abs(i - bars / 2f) / (bars / 2f)  // taller in the middle
            val wobble = 0.5f + 0.5f * kotlin.math.sin(phase + i * 0.7f)
            val h = size.height * (0.08f + 0.92f * smooth * (0.35f + 0.65f * center) * (0.55f + 0.45f * wobble))
            val x = gap * i + (gap - w) / 2
            drawRoundRect(RAINBOW[i % (RAINBOW.size - 1)], androidx.compose.ui.geometry.Offset(x, (size.height - h) / 2),
                androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(w / 2))
        }
    }
}

@Composable
fun MicButton(listening: Boolean, level: Float = 0f, onClick: () -> Unit) {
    val bg = if (listening) Color(0xFFFF3D6E) else Violet
    val t = rememberInfiniteTransition(label = "mic")
    val breathe by t.animateFloat(1f, 1.08f, infiniteRepeatable(tween(1600), androidx.compose.animation.core.RepeatMode.Reverse), label = "breathe")
    val glow by animateFloatAsState(if (listening) level else 0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium), label = "glow")
    Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
        // halo that grows with the voice
        Box(Modifier.size(90.dp).scale(1f + glow * 0.45f).background(bg.copy(alpha = 0.18f + glow * 0.2f), CircleShape))
        Box(Modifier.size(90.dp).scale(1f + glow * 0.2f).background(bg.copy(alpha = 0.25f), CircleShape))
        Box(Modifier.size(90.dp).scale(if (listening) 1f else breathe).press(onClick)
            .shadow(18.dp, CircleShape, spotColor = bg).background(bg, CircleShape), contentAlignment = Alignment.Center) {
            if (listening) Box(Modifier.size(26.dp).background(Color.White, RoundedCornerShape(8.dp)))
            else LineIcon(Icons.MIC, Color.White, size = 34.dp, strokeWidth = 2.2f)
        }
    }
}

@Composable
fun CorrectionSheet(vm: CoachViewModel) {
    val s = vm.strings
    val r = vm.turn ?: return
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp)
        .shadow(30.dp, RoundedCornerShape(34.dp)).background(Surface, RoundedCornerShape(34.dp))
        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Buddy(vm.role.color, Mood.Thinking, Modifier.size(52.dp))
            Spacer(Modifier.width(10.dp))
            Text(s.almost, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        }
        Block(s.youSaid, Color(0xFFFFF0F5), Color(0xFFC8205F)) { Text(vm.heard, fontSize = 16.sp, color = Ink) }
        Block(s.correct, Color(0xFFF2FBDD), Color(0xFF3F7A0E)) { Text(r.correct, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink, lineHeight = 26.sp) }
        PronunciationCard(vm)
        if (r.natural.isNotBlank() && r.natural != r.correct)
            Block(s.better, Color(0xFFF0ECFF), Color(0xFF5136D9)) { Text("“${r.natural}”", fontSize = 16.sp, color = Ink) }
        if (vm.explanations.isEmpty()) Text(s.explaining, color = Muted, fontSize = 14.sp)
        vm.explanations.forEach { ex ->
            if (vm.roman) {
                Text(ex.urdu, Modifier.fillMaxWidth(), style = TextStyle(textDirection = TextDirection.Rtl), fontSize = 17.sp, lineHeight = 30.sp, color = Ink)
                Text(ex.roman, fontSize = 14.sp, color = Color(0xFF4A4466), lineHeight = 20.sp)
            } else Text(ex.en, fontSize = 14.sp, color = Color(0xFF4A4466), lineHeight = 20.sp)
        }
        vm.notice?.let { Text(it, color = Pink, fontWeight = FontWeight.Bold) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(s.listen, Modifier.weight(1f), outline = true) { vm.replay() }
            Pill(s.retry, Modifier.weight(1f)) { vm.retry() }
        }
        Text(s.skip, Modifier.align(Alignment.CenterHorizontally).clickable { vm.next() }.padding(6.dp), color = Muted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Block(label: String, bg: Color, accent: Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(label.uppercase(), color = accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
fun Pill(label: String, modifier: Modifier = Modifier, outline: Boolean = false, onClick: () -> Unit) {
    val m = if (outline) modifier.border(2.dp, Color(0xFFE4E0F5), CircleShape) else modifier.background(Violet, CircleShape)
    Box(m.heightIn(min = 54.dp).press(onClick).padding(horizontal = 20.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (outline) Ink else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun NavBar(vm: CoachViewModel, modifier: Modifier) {
    // 4 tabs + raised mic in the middle. Everything else lives in the "More" sheet so the bar stays clean.
    val more = setOf(Screen.Mac, Screen.Progress, Screen.Settings)
    val tabs = listOf(Triple(Screen.Home, Icons.HOME, "Home"), Triple(Screen.Money, Icons.WALLET, "Money"), Triple(Screen.Practice, Icons.MIC, ""),
        Triple(Screen.Stores, Icons.SHOP, "Stores"), Triple(Screen.Settings, Icons.MORE, "More"))
    var sheet by remember { mutableStateOf(false) }
    if (sheet) MoreSheet(vm) { sheet = false }
    fun isOn(sc: Screen) = if (sc == Screen.Settings) vm.screen in more else vm.screen == sc
    Box(modifier.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 8.dp).fillMaxWidth().height(84.dp),
        contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.fillMaxWidth().height(64.dp)
            .shadow(28.dp, CircleShape, ambientColor = Color(0x471A1530), spotColor = Color(0x471A1530))
            .background(if (appDark) Color(0xFA221D14) else Color(0xFAFFFFFF), CircleShape).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { (screen, icon, label) ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (screen != Screen.Practice) {
                        val active = isOn(screen)
                        val sc by animateFloatAsState(if (active) 1.1f else 1f, spring(Spring.DampingRatioHighBouncy), label = "tab")
                        val bg by androidx.compose.animation.animateColorAsState(if (active) Color(0xFFEDE8FF) else Color.Transparent, label = "bg")
                        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(bg).press {
                            if (screen == Screen.Settings) sheet = true
                            else { if (screen == Screen.Stores) { vm.etsyStore = null; vm.etsyRun = null }; vm.screen = screen }
                        }.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            LineIcon(icon, if (active) Violet else Color(0xFF9A93B8), Modifier.scale(sc), size = 21.dp, strokeWidth = 2.1f)
                            Text(label, color = if (active) Violet else Color(0xFF9A93B8), fontSize = 10.sp, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        // raised centre mic: 64dp, 5dp paper border, ring 9dp outside, sits 34dp above the bar's top edge
        // tap: English practice · hold: talk to the Mac agent (release to send)
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        Box(Modifier.align(Alignment.TopCenter).size(66.dp).pointerInput(vm.paired) {
            detectTapGestures(onPress = {
                val held = kotlinx.coroutines.coroutineScope {
                    val job = launch {
                        kotlinx.coroutines.delay(350)
                        if (vm.paired) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); vm.voiceStart() }
                    }
                    tryAwaitRelease()
                    val started = job.isCompleted && !job.isCancelled
                    job.cancel(); started
                }
                if (held && vm.paired) vm.voiceRelease() else vm.start(ROLES[0])
            })
        }, contentAlignment = Alignment.Center) {
            AiRing(vm.voiceOpen, Modifier.fillMaxSize())
            Box(Modifier.size(54.dp).shadow(12.dp, CircleShape, spotColor = Violet).background(Paper, CircleShape).padding(4.dp)
                .background(Violet, CircleShape), contentAlignment = Alignment.Center) {
                LineIcon(Icons.MIC, Color.White, size = 22.dp, strokeWidth = 2.3f)
            }
        }
    }
}


/** "More" sheet: the other screens as big colourful tiles. */
@Composable
private fun MoreSheet(vm: CoachViewModel, onClose: () -> Unit) {
    val items = listOf(
        Triple("📱", "App assistant", Screen.AppAssistant), Triple("💡", "Ideas", Screen.Ideas), Triple("🖥", "Mac", Screen.Mac), Triple("📊", "Progress", Screen.Progress), Triple("⚙️", "Settings", Screen.Settings),
        Triple("🎬", "YouTube Studio", Screen.Studio), Triple("🎯", "Video rules", Screen.Rules), Triple("📄", "CV studio", Screen.Cv),
        Triple("📲", "Clipboard & files", Screen.Transfer), Triple("⚡", "Routines", Screen.Routines), Triple("🔔", "Alerts", Screen.Alerts), Triple("🧹", "Helpers & staff", Screen.Staff))
    val colors = listOf(0xFFE0E7FF, 0xFFFEF3C7, 0xFFEDE8FF, 0xFFE0F2FE, 0xFFF1F5F9, 0xFFFFE4E6, 0xFFFEF3C7, 0xFFE0E7FF, 0xFFCCFBF1, 0xFFFFEDD5, 0xFFFCE7F3, 0xFFDCFCE7)
    androidx.compose.ui.window.Dialog(onClose, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        val a = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = .75f, stiffness = 300f)) }
        Box(Modifier.fillMaxSize().background(Color(0x661A1530)).press { onClose() }, contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().graphicsLayer { translationY = (1 - a.value) * 600f }
                .background(Surface, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).press { }
                .navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(44.dp, 5.dp).background(lt(Color(0xFFDDD8EE)), CircleShape))
                Text("Sab screens", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                items.chunked(3).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEachIndexed { c, (e, t, sc) ->
                            val i = r * 3 + c
                            val pop = remember { androidx.compose.animation.core.Animatable(0f) }
                            LaunchedEffect(Unit) { kotlinx.coroutines.delay(60L + i * 35); pop.animateTo(1f, spring(dampingRatio = .55f)) }
                            Column(Modifier.weight(1f).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value }
                                .background(Color(colors[i]), RoundedCornerShape(22.dp)).press { vm.screen = sc; onClose() }.padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(e, fontSize = 28.sp)
                                Text(t, color = InkOnLight, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun CreditWarning(why: String, onOverride: () -> Unit, onCancel: () -> Unit) {
    androidx.compose.ui.window.Dialog(onCancel) {
        Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(28.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("⚠️ Credit limit", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(why.replace("Credit guard: ", "").replace(" Raise today's limit in Studio, or pick a smaller budget.", ""), color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            Text("Override karein to aaj ki had ke bawajood video shuru ho jayegi.", color = Muted, fontSize = 12.sp)
            Text("⚠️ Override & Start", Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFE11D48))), RoundedCornerShape(16.dp))
                .press(onOverride).padding(14.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center)
            Text("Cancel", Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(16.dp)).press(onCancel).padding(14.dp), color = Ink,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
