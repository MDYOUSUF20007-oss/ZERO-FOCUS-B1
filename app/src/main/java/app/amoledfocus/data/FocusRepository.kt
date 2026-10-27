package app.amoledfocus.data

import android.app.*
import android.content.Context
import android.content.Intent
import app.amoledfocus.R
import app.amoledfocus.domain.*
import app.amoledfocus.service.SessionEndReceiver
import app.amoledfocus.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FocusRepository(private val ctx: Context, db: AppDb, private val prefs: Prefs, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.dao(); private val mutex = Mutex()
    // One-time blocking read so the AccessibilityService never sees a stale "no session" after process death.
    private val _active = MutableStateFlow<ActiveSession?>(runBlocking(Dispatchers.IO) { prefs.loadActive() })
    val active: StateFlow<ActiveSession?> = _active

    suspend fun resume() { reconcile(); _active.value?.let { schedule(it); showNotification(it) } }

    suspend fun start(mode: FocusMode, durationMs: Long, blocked: Set<String>, safe: Set<String>): Boolean = mutex.withLock {
        if (_active.value != null) return@withLock false
        val s = SessionMachine.start(clock(), clock(), mode, durationMs, blocked, safe)
        prefs.saveActive(s); _active.value = s; schedule(s); showNotification(s); true
    }
    suspend fun requestCancel() = update { SessionMachine.requestCancel(it) }
    suspend fun keepFocusing() = update { SessionMachine.keepFocusing(it) }
    /** Ends the session early; costs one Reset Token. */
    suspend fun endEarly(): Boolean = mutex.withLock {
        val s = _active.value ?: return@withLock false
        if (s.state != SessionState.CANCEL_CONFIRMATION) return@withLock false
        if (!prefs.spendToken()) return@withLock false
        finish(SessionMachine.cancel(s, clock())); true
    }
    suspend fun reconcile() = update { SessionMachine.reconcile(it, clock()) }
    suspend fun recordAttempt(pkg: String) = dao.insert(AttemptEntity(pkg = pkg, atMs = clock()))

    private suspend fun update(f: (ActiveSession) -> ActiveSession) = mutex.withLock {
        val s = _active.value ?: return@withLock; val n = f(s)
        when { n.state == SessionState.COMPLETED || n.state == SessionState.CANCELLED -> finish(n)
               n != s -> { prefs.saveActive(n); _active.value = n } }
    }
    private suspend fun finish(s: ActiveSession) {
        dao.insert(SessionEntity(mode = s.mode.name, startMs = s.startMs, endMs = s.endMs, plannedMs = s.endMs - s.startMs, completed = s.state == SessionState.COMPLETED))
        prefs.saveActive(null); _active.value = null; cancelAlarm(); ctx.getSystemService(NotificationManager::class.java).cancel(1)
        if (s.state == SessionState.COMPLETED && dao.completedCount() % 5 == 0) prefs.addTokens(1) // earn a Reset Token every 5 completed sessions
    }

    private fun pi() = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, SessionEndReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun schedule(s: ActiveSession) = ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.endMs, pi())
    private fun cancelAlarm() = ctx.getSystemService(AlarmManager::class.java).cancel(pi())
    private fun showNotification(s: ActiveSession) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("focus", "Focus session", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(1, Notification.Builder(ctx, "focus").setSmallIcon(R.drawable.ic_stat).setContentTitle("FOCUS MODE")
            .setContentText("Time remaining").setUsesChronometer(true).setChronometerCountDown(true).setWhen(s.endMs)
            .setShowWhen(true).setOngoing(true).setContentIntent(open).build())
    }
}
