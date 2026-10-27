package app.amoledfocus.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class FocusMode { STRICT, DEEP, SLEEP, STUDY, CUSTOM;
    /** Deep and Sleep block everything except safe + system-critical apps. */
    val blocksEverythingElse get() = this == DEEP || this == SLEEP }

enum class SessionState { IDLE, PREPARING, ACTIVE, PAUSED_IF_ALLOWED, COMPLETED, CANCEL_CONFIRMATION, CANCELLED }

data class ActiveSession(
    val id: Long, val mode: FocusMode, val startMs: Long, val endMs: Long,
    val blocked: Set<String>, val safe: Set<String>, val state: SessionState)

sealed interface Decision { data object Allow : Decision; data class Block(val pkg: String) : Decision }

const val MAX_SAFE_APPS = 3
const val PREPARE_MS = 3_000L

object SessionMachine {
    fun start(now: Long, id: Long, mode: FocusMode, durationMs: Long, blocked: Set<String>, safe: Set<String>): ActiveSession {
        require(durationMs > 0) { "duration" }
        require(safe.size <= MAX_SAFE_APPS) { "at most $MAX_SAFE_APPS safe apps" }
        val s = now + PREPARE_MS
        return ActiveSession(id, mode, s, s + durationMs, blocked - safe, safe, SessionState.PREPARING)
    }
    fun requestCancel(s: ActiveSession) = if (s.state == SessionState.ACTIVE) s.copy(state = SessionState.CANCEL_CONFIRMATION) else s
    fun keepFocusing(s: ActiveSession) = if (s.state == SessionState.CANCEL_CONFIRMATION) s.copy(state = SessionState.ACTIVE) else s
    fun cancel(s: ActiveSession, now: Long) =
        if (s.state == SessionState.CANCEL_CONFIRMATION || s.state == SessionState.ACTIVE) s.copy(state = SessionState.CANCELLED, endMs = minOf(now, s.endMs)) else s
    /** Pure function of (session, wall clock): safe after reboot, process death or missed alarms. */
    fun reconcile(s: ActiveSession, now: Long): ActiveSession = when {
        s.state == SessionState.PREPARING && now >= s.endMs -> s.copy(state = SessionState.COMPLETED)
        s.state == SessionState.PREPARING && now >= s.startMs -> s.copy(state = SessionState.ACTIVE)
        (s.state == SessionState.ACTIVE || s.state == SessionState.CANCEL_CONFIRMATION) && now >= s.endMs -> s.copy(state = SessionState.COMPLETED)
        else -> s
    }
}

/** Decides allow/block for a foreground package. No Android dependencies. */
class BlockingEngine(private val alwaysAllowed: () -> Set<String>) {
    fun decide(session: ActiveSession?, now: Long, pkg: String): Decision {
        if (session == null) return Decision.Allow
        if (session.state != SessionState.ACTIVE && session.state != SessionState.CANCEL_CONFIRMATION) return Decision.Allow
        if (now < session.startMs || now >= session.endMs) return Decision.Allow
        if (pkg in session.safe || pkg in alwaysAllowed()) return Decision.Allow
        val block = session.mode.blocksEverythingElse || pkg in session.blocked
        return if (block) Decision.Block(pkg) else Decision.Allow
    }
}

data class SessionRecord(val startMs: Long, val endMs: Long, val completed: Boolean)

object StatsEngine {
    fun focusMsOn(records: List<SessionRecord>, day: LocalDate, zone: ZoneId): Long {
        val a = day.atStartOfDay(zone).toInstant().toEpochMilli(); val b = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return records.sumOf { (minOf(it.endMs, b) - maxOf(it.startMs, a)).coerceAtLeast(0) }
    }
    fun streak(records: List<SessionRecord>, today: LocalDate, zone: ZoneId): Int {
        val days = records.filter { it.completed }.map { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }.toSet()
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }
    fun gardenStage(completed: Int) = when { completed >= 7 -> "Plant"; completed >= 3 -> "Sprout"; completed >= 1 -> "Seed"; else -> "Empty" }
}
