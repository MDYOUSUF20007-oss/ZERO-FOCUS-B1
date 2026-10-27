package app.amoledfocus.service

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import app.amoledfocus.app
import app.amoledfocus.domain.*
import app.amoledfocus.ui.BlockActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Reads ONLY event.packageName of window-state changes. canRetrieveWindowContent=false in the config,
 * so the system never gives this service screen content, text or keystrokes.
 */
class FocusAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var session: ActiveSession? = null
    @Volatile private var lastBlockAt = 0L
    @Volatile private var safeSystem: Set<String> = emptySet()
    private val engine = BlockingEngine { safeSystem }

    override fun onServiceConnected() {
        safeSystem = computeSystemSafe()
        scope.launch { app.repo.active.collectLatest { session = it } }
        scope.launch { app.repo.reconcile() }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val s = session ?: return                       // no session -> zero work
        val pkg = e.packageName?.toString() ?: return
        val now = System.currentTimeMillis()
        if (now >= s.endMs || s.state == SessionState.PREPARING && now >= s.startMs) { scope.launch { app.repo.reconcile() } }
        val d = engine.decide(s, now, pkg) as? Decision.Block ?: return
        if (now - lastBlockAt < 400) return              // debounce window-change bursts
        lastBlockAt = now
        scope.launch { app.repo.recordAttempt(d.pkg) }
        startActivity(Intent(this, BlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    private fun computeSystemSafe(): Set<String> {
        val out = mutableSetOf(packageName, "com.android.systemui", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.android.emergency")
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.let(out::add)
        getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(out::add)
        getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { out.add(it.packageName) }
        return out
    }
    override fun onInterrupt() {}
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

class SessionEndReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) { val p = goAsync(); c.app.scope.launch { try { c.app.repo.reconcile() } finally { p.finish() } } }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) { val p = goAsync(); c.app.scope.launch { try { c.app.repo.resume() } finally { p.finish() } } }
}

object Permissions {
    fun accessibilityEnabled(c: Context): Boolean {
        val me = ComponentName(c, FocusAccessibilityService::class.java).flattenToString()
        val list = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.split(':').any { it.equals(me, true) }
    }
    fun openAccessibilitySettings(c: Context) = c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
