package app.amoledfocus

import android.app.Application
import android.content.Context
import app.amoledfocus.data.*
import kotlinx.coroutines.*

class FocusApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var prefs: Prefs; lateinit var db: AppDb; lateinit var repo: FocusRepository
    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this); db = AppDb.get(this); repo = FocusRepository(this, db, prefs)
        scope.launch { repo.resume() }
    }
}
val Context.app get() = applicationContext as FocusApp
