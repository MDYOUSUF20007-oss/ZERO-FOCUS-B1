package app.amoledfocus

import app.amoledfocus.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DomainTest {
    private val engine = BlockingEngine { setOf("sys.home") }
    private fun active(mode: FocusMode = FocusMode.STRICT, state: SessionState = SessionState.ACTIVE) =
        ActiveSession(1, mode, 1000, 100_000, setOf("insta"), setOf("phone"), state)

    @Test fun noSessionAllowsEverything() = assertEquals(Decision.Allow, engine.decide(null, 5000, "insta"))
    @Test fun strictBlocksListedOnly() {
        assertEquals(Decision.Block("insta"), engine.decide(active(), 5000, "insta"))
        assertEquals(Decision.Allow, engine.decide(active(), 5000, "maps"))
    }
    @Test fun deepBlocksEverythingExceptSafeAndSystem() {
        val s = active(FocusMode.DEEP)
        assertEquals(Decision.Block("maps"), engine.decide(s, 5000, "maps"))
        assertEquals(Decision.Allow, engine.decide(s, 5000, "phone"))
        assertEquals(Decision.Allow, engine.decide(s, 5000, "sys.home"))
    }
    @Test fun expiredOrPreparingSessionDoesNotBlock() {
        assertEquals(Decision.Allow, engine.decide(active(), 200_000, "insta"))
        assertEquals(Decision.Allow, engine.decide(active(state = SessionState.PREPARING), 5000, "insta"))
    }
    @Test fun cancelConfirmationStillBlocks() = assertEquals(Decision.Block("insta"), engine.decide(active(state = SessionState.CANCEL_CONFIRMATION), 5000, "insta"))

    @Test fun safeAppsCappedAtThree() { assertThrows(IllegalArgumentException::class.java) { SessionMachine.start(0, 1, FocusMode.STRICT, 1000, emptySet(), setOf("a", "b", "c", "d")) } }
    @Test fun lifecycleAndRestoreAfterDowntime() {
        val s = SessionMachine.start(0, 1, FocusMode.STRICT, 60_000, setOf("x"), emptySet())
        assertEquals(SessionState.PREPARING, s.state)
        assertEquals(SessionState.ACTIVE, SessionMachine.reconcile(s, PREPARE_MS).state)
        assertEquals(SessionState.COMPLETED, SessionMachine.reconcile(s, 10_000_000).state) // device was off past the end
        val c = SessionMachine.requestCancel(SessionMachine.reconcile(s, PREPARE_MS))
        assertEquals(SessionState.CANCEL_CONFIRMATION, c.state)
        assertEquals(SessionState.ACTIVE, SessionMachine.keepFocusing(c).state)
        assertEquals(SessionState.CANCELLED, SessionMachine.cancel(c, 20_000).state)
    }
    @Test fun streakCountsConsecutiveCompletedDays() {
        val z = ZoneOffset.UTC; val today = LocalDate.of(2026, 10, 26)
        fun rec(d: LocalDate, ok: Boolean = true) = SessionRecord(d.atTime(10, 0).toInstant(z).toEpochMilli(), d.atTime(10, 30).toInstant(z).toEpochMilli(), ok)
        assertEquals(3, StatsEngine.streak(listOf(rec(today), rec(today.minusDays(1)), rec(today.minusDays(2)), rec(today.minusDays(4))), today, z))
        assertEquals(1, StatsEngine.streak(listOf(rec(today.minusDays(1))), today, z))
        assertEquals(0, StatsEngine.streak(listOf(rec(today, false)), today, z))
        assertEquals(30 * 60_000L, StatsEngine.focusMsOn(listOf(rec(today)), today, z))
    }
}
