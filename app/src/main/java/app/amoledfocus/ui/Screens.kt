package app.amoledfocus.ui

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.amoledfocus.app
import app.amoledfocus.data.SessionEntity
import app.amoledfocus.domain.*
import app.amoledfocus.service.Permissions
import kotlinx.coroutines.*
import java.time.*

fun fmtClock(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) }
fun fmtDur(ms: Long): String { val m = ms / 60_000; return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }

@Composable fun Ring(progress: Float, modifier: Modifier = Modifier, desc: String, content: @Composable ColumnScope.() -> Unit) {
    val track = MaterialTheme.colorScheme.outline; val acc = MaterialTheme.colorScheme.primary
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), if (reducedMotion()) snap() else tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier.aspectRatio(1f).semantics { contentDescription = desc }.drawBehind {
        val w = 10.dp.toPx(); val inset = w / 2; val sz = Size(size.width - w, size.height - w)
        drawArc(track, -90f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = sz, style = Stroke(w))
        drawArc(acc, -90f, 360f * p, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = sz, style = Stroke(w, cap = StrokeCap.Round))
    }, contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, content = content) }
}

@Composable fun HoldButton(label: String, holdMs: Int, enabled: Boolean, onConfirmed: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope(); val acc = MaterialTheme.colorScheme.primary; val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().height(56.dp).clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)
        .drawBehind { drawRect(acc.copy(alpha = 0.35f), size = Size(size.width * progress, size.height)) }
        .pointerInput(enabled) { if (enabled) detectTapGestures(onPress = {
            val job = scope.launch { val t0 = System.nanoTime()
                while (progress < 1f) { progress = ((System.nanoTime() - t0) / 1e6f / holdMs).coerceAtMost(1f); delay(16) }
                onConfirmed() }
            tryAwaitRelease(); job.cancel(); progress = 0f }) }
        .semantics { role = Role.Button; contentDescription = "$label. Press and hold for ${holdMs / 1000} seconds"
            if (enabled) onClick("Confirm $label") { onConfirmed(); true } }, // TalkBack users can't hold; custom action keeps it accessible
        contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f)) }
}

/** Shared by MainActivity and BlockActivity. */
@Composable fun SessionPanel(s: ActiveSession, onKeepFocusing: (() -> Unit)? = null, headline: String = "FOCUS MODE") {
    val ctx = LocalContext.current; val repo = ctx.app.repo; val scope = rememberCoroutineScope()
    val tokens by ctx.app.prefs.tokens.collectAsState(2)
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); if (value >= s.endMs) repo.reconcile(); delay(1000) } }
    val total = (s.endMs - s.startMs).coerceAtLeast(1)
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        if (s.state == SessionState.PREPARING) {
            Text("Starting in", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(((s.startMs - now) / 1000 + 1).coerceAtLeast(0).toString(), style = MaterialTheme.typography.displayLarge)
        } else {
            Ring(1f - (s.endMs - now).toFloat() / total, Modifier.fillMaxWidth(0.85f), "${fmtDur(s.endMs - now)} remaining") {
                Text(fmtClock(s.endMs - now), style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }))
                Text(headline, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
            Text("Your phone is taking a step back.", Modifier.padding(top = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${s.blocked.size} apps blocked · ${s.safe.size} essential available", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        if (s.state == SessionState.CANCEL_CONFIRMATION) {
            Text("You're still in focus mode.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text("Reset Tokens remaining: $tokens", Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button({ scope.launch { repo.keepFocusing(); onKeepFocusing?.invoke() } }, Modifier.fillMaxWidth().height(56.dp)) { Text("KEEP FOCUSING") }
            Spacer(Modifier.height(12.dp))
            HoldButton(if (tokens > 0) "HOLD 5s · END SESSION" else "NO RESET TOKENS LEFT", 5000, tokens > 0) { scope.launch { repo.endEarly() } }
        } else if (s.state == SessionState.ACTIVE) {
            TextButton({ scope.launch { repo.requestCancel() } }) { Text("END SESSION", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable fun Home(onStart: () -> Unit, onStats: () -> Unit) {
    val ctx = LocalContext.current; val dao = remember { ctx.app.db.dao() }
    val sessions by dao.sessions().collectAsState(emptyList())
    val recs = sessions.map { SessionRecord(it.startMs, it.endMs, it.completed) }
    val z = ZoneId.systemDefault(); val today = LocalDate.now(z)
    val todayMs = StatsEngine.focusMsOn(recs, today, z); val completed = sessions.count { it.completed }
    val tokens by ctx.app.prefs.tokens.collectAsState(2)
    var enabled by remember { mutableStateOf(Permissions.accessibilityEnabled(ctx)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) { val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) enabled = Permissions.accessibilityEnabled(ctx) }
        lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) } }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
        val h = LocalTime.now().hour
        Text(if (h < 12) "Good morning" else if (h < 18) "Good afternoon" else "Good evening", style = MaterialTheme.typography.titleLarge)
        Text(today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!enabled) Column(Modifier.padding(top = 16.dp).fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(16.dp)) {
            Text("Focus protection is currently disabled.", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            Text("Open Accessibility settings, choose AMOLED Focus under installed or downloaded apps, and turn it on. Sessions won't block apps until you do.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            TextButton({ Permissions.openAccessibilitySettings(ctx) }) { Text("OPEN SETTINGS") } }
        Spacer(Modifier.weight(1f))
        Ring(todayMs / (2 * 3_600_000f), Modifier.fillMaxWidth(0.85f).align(Alignment.CenterHorizontally), "Focused today: ${fmtDur(todayMs)}") {
            Text(fmtDur(todayMs), style = MaterialTheme.typography.displayMedium); Text("FOCUSED TODAY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("${StatsEngine.streak(recs, today, z)}", "day streak"); Stat("$completed", "sessions"); Stat(StatsEngine.gardenStage(completed), "garden") }
        Spacer(Modifier.weight(1f))
        Button(onStart, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(30.dp)) { Text("START FOCUS", style = MaterialTheme.typography.labelLarge) }
        Row(Modifier.fillMaxWidth()) { TextButton(onStats) { Text("STATISTICS") }; Spacer(Modifier.weight(1f)); Text("Reset Tokens: $tokens", Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun Stat(v: String, l: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(v, style = MaterialTheme.typography.titleLarge); Text(l, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable fun Setup(onBack: () -> Unit, onPickBlocked: () -> Unit, onPickSafe: () -> Unit) {
    val ctx = LocalContext.current; val prefs = ctx.app.prefs; val scope = rememberCoroutineScope()
    val blocked by prefs.blocked.collectAsState(emptySet()); val safe by prefs.safe.collectAsState(emptySet())
    val mode by prefs.mode.collectAsState(FocusMode.STRICT); val dur by prefs.durationMin.collectAsState(25)
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
        Text("New focus session", style = MaterialTheme.typography.titleLarge)
        Text("DURATION", Modifier.padding(top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(fmtDur(dur * 60_000L), style = MaterialTheme.typography.displayMedium)
        Slider(dur.toFloat(), { scope.launch { prefs.setDuration(it.toInt().coerceAtLeast(1)) } }, valueRange = 1f..480f)
        FlowRow5(listOf(5, 15, 25, 45, 60, 120, 240)) { m -> FilterChip(dur == m, { scope.launch { prefs.setDuration(m) } }, { Text(if (m >= 60) "${m / 60}h" else "${m}m") }) }
        Text("MODE", Modifier.padding(top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        FlowRow5(FocusMode.entries) { m -> FilterChip(mode == m, { scope.launch { prefs.setMode(m) } }, { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
        Text(when (mode) { FocusMode.DEEP, FocusMode.SLEEP -> "Blocks everything except your essential apps."; else -> "Blocks only the apps you select." },
            Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onPickBlocked, Modifier.fillMaxWidth().height(52.dp)) { Text("Distracting apps · ${blocked.size}") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onPickSafe, Modifier.fillMaxWidth().height(52.dp)) { Text("Essential apps · ${safe.size} / $MAX_SAFE_APPS") }
        Spacer(Modifier.weight(1f))
        Button({ scope.launch { ctx.app.repo.start(mode, dur * 60_000L, blocked, safe) } }, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(30.dp),
            enabled = mode.blocksEverythingElse || blocked.isNotEmpty()) { Text("BEGIN", style = MaterialTheme.typography.labelLarge) }
        TextButton(onBack, Modifier.align(Alignment.CenterHorizontally)) { Text("BACK") }
    }
}
@Composable fun <T> FlowRow5(items: List<T>, chip: @Composable (T) -> Unit) =
    @OptIn(ExperimentalLayoutApi::class) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items.forEach { chip(it) } }

@Composable fun Stats(onBack: () -> Unit) {
    val ctx = LocalContext.current; val dao = remember { ctx.app.db.dao() }
    val sessions by dao.sessions().collectAsState(emptyList()); val attempts by dao.attemptCounts().collectAsState(emptyList())
    val z = ZoneId.systemDefault(); val today = LocalDate.now(z); val recs = sessions.map { SessionRecord(it.startMs, it.endMs, it.completed) }
    val last7 = (0..6).map { StatsEngine.focusMsOn(recs, today.minusDays(it.toLong()), z) }
    val total = sessions.sumOf { it.endMs - it.startMs }; val avg = last7.sum() / 7
    val pm = ctx.packageManager
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Statistics", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(fmtDur(last7[0]), "today"); Stat(fmtDur(avg), "7-day avg"); Stat(fmtDur(total), "lifetime") }
        Text("LAST 7 DAYS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        val maxV = (last7.maxOrNull() ?: 1L).coerceAtLeast(1); val acc = MaterialTheme.colorScheme.primary
        Row(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            last7.reversed().forEachIndexed { i, v -> Box(Modifier.weight(1f).fillMaxHeight((v.toFloat() / maxV).coerceAtLeast(0.03f)).clip(RoundedCornerShape(4.dp)).background(acc)
                .semantics { contentDescription = "${today.minusDays((6 - i).toLong()).dayOfWeek}: ${fmtDur(v)}" }) } }
        Text("At your current average, you've reclaimed about ${avg * 365 / 3_600_000} hours this year.", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("BLOCKED ATTEMPTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("These were moments when you chose to stay focused.", Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        attempts.take(8).forEach { a -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(label(pm, a.pkg), Modifier.weight(1f)); Text("${a.count}") } }
        TextButton(onBack) { Text("BACK") }
    }
}
private fun label(pm: android.content.pm.PackageManager, p: String) = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p)

@Composable fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current; var step by remember { mutableIntStateOf(0) }
    val blocked by ctx.app.prefs.blocked.collectAsState(emptySet()); val safe by ctx.app.prefs.safe.collectAsState(emptySet()); val scope = rememberCoroutineScope()
    when (step) {
        1 -> AppPicker("Choose distracting apps", "these get blocked", blocked, null, { scope.launch { ctx.app.prefs.setBlocked(it) } }) { step = 2 }
        2 -> AppPicker("Choose up to 3 essential apps", "", safe, MAX_SAFE_APPS, { scope.launch { ctx.app.prefs.setSafe(it) } }) { step = 3 }
        else -> Column(Modifier.fillMaxSize().systemBarsPadding().padding(32.dp), verticalArrangement = Arrangement.Center) {
            if (step == 0) { Text("Take back your attention.", style = MaterialTheme.typography.displayMedium)
                Text("Pick the apps that pull you away. We'll keep them out of reach while you focus.", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button({ step = 1 }, Modifier.fillMaxWidth().height(56.dp)) { Text("GET STARTED") }
            } else { Text("One permission.", style = MaterialTheme.typography.displayMedium)
                Text("AMOLED Focus uses Accessibility only to detect when a blocked app is opened during an active focus session. It does not read your screen or messages.", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button({ Permissions.openAccessibilitySettings(ctx) }, Modifier.fillMaxWidth().height(56.dp)) { Text("ENABLE ACCESSIBILITY") }
                TextButton({ scope.launch { ctx.app.prefs.setOnboarded() }; onDone() }, Modifier.fillMaxWidth()) { Text("START MY FIRST SESSION") } } }
    }
}
