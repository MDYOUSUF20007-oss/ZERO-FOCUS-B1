package app.amoledfocus.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.amoledfocus.app
import app.amoledfocus.domain.*
import kotlinx.coroutines.launch

private enum class Route { HOME, SETUP, BLOCKED, SAFE, STATS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            val p = app.prefs; val scope = rememberCoroutineScope()
            val appearance by p.appearance.collectAsState("AMOLED"); val accent by p.accent.collectAsState(Accents[0])
            val onboarded by p.onboarded.collectAsState(null); val s by app.repo.active.collectAsState()
            var route by rememberSaveable { mutableStateOf(Route.HOME) }
            val blocked by p.blocked.collectAsState(emptySet()); val safe by p.safe.collectAsState(emptySet())
            FocusTheme(appearance, accent) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val a = s
                when {
                    onboarded == null -> {}
                    a != null -> SessionPanel(a)
                    onboarded == false -> Onboarding { route = Route.SETUP }
                    else -> when (route) {
                        Route.HOME -> Home({ route = Route.SETUP }, { route = Route.STATS })
                        Route.SETUP -> Setup({ route = Route.HOME }, { route = Route.BLOCKED }, { route = Route.SAFE })
                        Route.BLOCKED -> AppPicker("Distracting apps", "blocked during sessions", blocked, null, { scope.launch { p.setBlocked(it) } }) { route = Route.SETUP }
                        Route.SAFE -> AppPicker("Essential apps", "", safe, MAX_SAFE_APPS, { scope.launch { p.setSafe(it) } }) { route = Route.SETUP }
                        Route.STATS -> Stats { route = Route.HOME }
                    } } } }
        }
    }
}

/** Shown when a blocked app is opened. Back goes Home so the blocked app can't be re-entered via back-stack. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = goHome() })
        setContent {
            val appearance by app.prefs.appearance.collectAsState("AMOLED"); val accent by app.prefs.accent.collectAsState(Accents[0])
            val s by app.repo.active.collectAsState()
            LaunchedEffect(s) { if (s == null) finish() }
            FocusTheme(appearance, accent) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                s?.let { a -> Box { SessionPanel(a, onKeepFocusing = { goHome() }, headline = "STAY FOCUSED")
                    if (a.state == SessionState.ACTIVE) Text("This app is paused for now.", Modifier.align(Alignment.TopCenter).padding(top = 72.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
        }
    }
    private fun goHome() { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish() }
}
