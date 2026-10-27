package app.amoledfocus.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import app.amoledfocus.domain.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

private val Context.ds by preferencesDataStore("focus")

class Prefs(private val ctx: Context) {
    private val kBlocked = stringSetPreferencesKey("blocked"); private val kSafe = stringSetPreferencesKey("safe")
    private val kAppear = stringPreferencesKey("appearance"); private val kAccent = intPreferencesKey("accent")
    private val kTokens = intPreferencesKey("tokens"); private val kOnb = booleanPreferencesKey("onboarded")
    private val kActive = stringPreferencesKey("active")
    private val kMode = stringPreferencesKey("mode"); private val kDur = intPreferencesKey("duration_min")

    val blocked = ctx.ds.data.map { it[kBlocked] ?: emptySet() }
    val safe = ctx.ds.data.map { it[kSafe] ?: emptySet() }
    val appearance = ctx.ds.data.map { it[kAppear] ?: "AMOLED" }
    val accent = ctx.ds.data.map { it[kAccent] ?: 0xFF3D8BFF.toInt() }
    val tokens = ctx.ds.data.map { it[kTokens] ?: 2 }
    val onboarded = ctx.ds.data.map { it[kOnb] ?: false }
    val mode = ctx.ds.data.map { runCatching { FocusMode.valueOf(it[kMode] ?: "") }.getOrDefault(FocusMode.STRICT) }
    val durationMin = ctx.ds.data.map { it[kDur] ?: 25 }

    suspend fun setBlocked(v: Set<String>) { ctx.ds.edit { it[kBlocked] = v } }
    suspend fun setSafe(v: Set<String>) { ctx.ds.edit { it[kSafe] = v.take(MAX_SAFE_APPS).toSet() } }
    suspend fun setAppearance(v: String) { ctx.ds.edit { it[kAppear] = v } }
    suspend fun setAccent(v: Int) { ctx.ds.edit { it[kAccent] = v } }
    suspend fun setOnboarded() { ctx.ds.edit { it[kOnb] = true } }
    suspend fun setMode(m: FocusMode) { ctx.ds.edit { it[kMode] = m.name } }
    suspend fun setDuration(min: Int) { ctx.ds.edit { it[kDur] = min } }
    suspend fun addTokens(n: Int) { ctx.ds.edit { it[kTokens] = ((it[kTokens] ?: 2) + n).coerceIn(0, 5) } }
    /** Returns false if no token was available. */
    suspend fun spendToken(): Boolean { var ok = false
        ctx.ds.edit { val t = it[kTokens] ?: 2; if (t > 0) { it[kTokens] = t - 1; ok = true } }; return ok }

    suspend fun saveActive(s: ActiveSession?) { ctx.ds.edit { if (s == null) it.remove(kActive) else it[kActive] = encode(s) } }
    suspend fun loadActive(): ActiveSession? = ctx.ds.data.first()[kActive]?.let { runCatching { decode(it) }.getOrNull() }

    private fun encode(s: ActiveSession) = JSONObject().put("id", s.id).put("mode", s.mode.name).put("start", s.startMs).put("end", s.endMs)
        .put("blocked", JSONArray(s.blocked.toList())).put("safe", JSONArray(s.safe.toList())).put("state", s.state.name).toString()
    private fun decode(t: String): ActiveSession { val o = JSONObject(t)
        fun set(a: JSONArray) = (0 until a.length()).map { a.getString(it) }.toSet()
        return ActiveSession(o.getLong("id"), FocusMode.valueOf(o.getString("mode")), o.getLong("start"), o.getLong("end"),
            set(o.getJSONArray("blocked")), set(o.getJSONArray("safe")), SessionState.valueOf(o.getString("state"))) }
}
