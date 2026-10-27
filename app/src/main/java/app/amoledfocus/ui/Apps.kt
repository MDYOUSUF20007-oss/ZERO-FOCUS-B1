package app.amoledfocus.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Cat(val title: String) { SOCIAL("Social"), VIDEO("Video"), ENTERTAINMENT("Entertainment"), GAMES("Games"), SHOPPING("Shopping"),
    BROWSERS("Browsers"), COMMUNICATION("Communication"), PRODUCTIVITY("Productivity"), OTHER("Other") }
data class AppInfo(val pkg: String, val label: String, val cat: Cat)

object AppCatalog {
    /** Runs entirely on-device; nothing about installed apps leaves the phone. */
    fun load(pm: PackageManager, self: String): List<AppInfo> {
        val launch = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val browsers = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com")), 0).map { it.activityInfo.packageName }.toSet()
        return launch.map { it.activityInfo.packageName }.distinct().filter { it != self }.map { p ->
            val ai = pm.getApplicationInfo(p, 0)
            AppInfo(p, pm.getApplicationLabel(ai).toString(), categorize(p, ai, p in browsers)) }.sortedBy { it.label.lowercase() }
    }
    private fun categorize(p: String, ai: ApplicationInfo, browser: Boolean): Cat {
        val l = p.lowercase()
        return when {
            ai.category == ApplicationInfo.CATEGORY_GAME -> Cat.GAMES
            ai.category == ApplicationInfo.CATEGORY_SOCIAL -> Cat.SOCIAL
            ai.category == ApplicationInfo.CATEGORY_VIDEO -> Cat.VIDEO
            browser -> Cat.BROWSERS
            listOf("instagram", "facebook", "twitter", "reddit", "snapchat", "tiktok", "linkedin", "pinterest").any { it in l } -> Cat.SOCIAL
            listOf("youtube", "netflix", "hotstar", "primevideo").any { it in l } -> Cat.VIDEO
            listOf("whatsapp", "telegram", "signal", "messag", "mms", "dialer", "gm", "mail").any { it in l } -> Cat.COMMUNICATION
            listOf("amazon", "flipkart", "shop", "ebay", "myntra").any { it in l } -> Cat.SHOPPING
            ai.category == ApplicationInfo.CATEGORY_AUDIO || ai.category == ApplicationInfo.CATEGORY_NEWS -> Cat.ENTERTAINMENT
            ai.category == ApplicationInfo.CATEGORY_PRODUCTIVITY -> Cat.PRODUCTIVITY
            else -> Cat.OTHER
        }
    }
}

/** limit = null -> unlimited (block list); limit = 3 -> Safe List. */
@Composable fun AppPicker(title: String, subtitle: String, selected: Set<String>, limit: Int?, onChange: (Set<String>) -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState(emptyList<AppInfo>()) { value = withContext(Dispatchers.IO) { AppCatalog.load(ctx.packageManager, ctx.packageName) } }
    var q by remember { mutableStateOf("") }
    val shown = apps.filter { q.isBlank() || it.label.contains(q, true) }.groupBy { it.cat }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(if (limit != null) "${selected.size} / $limit selected" else "${selected.size} selected · $subtitle", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true, placeholder = { Text("Search apps") })
        if (limit == null) Row { TextButton({ onChange(selected + shown.values.flatten().map { it.pkg }) }) { Text("SELECT ALL") }
            TextButton({ onChange(emptySet()) }) { Text("CLEAR ALL") } }
        LazyColumn(Modifier.weight(1f)) {
            shown.toSortedMap().forEach { (cat, list) ->
                item { Text(cat.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                items(list, key = { it.pkg }) { a ->
                    val on = a.pkg in selected
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Checkbox) {
                        onChange(if (on) selected - a.pkg else if (limit == null || selected.size < limit) selected + a.pkg else selected) },
                        verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(a.pkg); Spacer(Modifier.width(14.dp)); Text(a.label, Modifier.weight(1f)); Checkbox(on, null) }
                }
            }
        }
        Button(onDone, Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp)) { Text("DONE") }
    }
}

@Composable fun AppIcon(pkg: String) {
    val pm = LocalContext.current.packageManager
    val bmp by produceState<ImageBitmap?>(null, pkg) { value = withContext(Dispatchers.IO) { runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() } }
    bmp?.let { Image(it, null, Modifier.size(36.dp)) } ?: Spacer(Modifier.size(36.dp))
}
