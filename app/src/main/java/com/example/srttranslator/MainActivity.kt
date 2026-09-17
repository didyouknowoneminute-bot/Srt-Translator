package com.example.srttranslator

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class PartProgress(val name: String, val state: String = "Queued", val completed: Int = 0, val total: Int = 0, val attempt: Int = 0, val detail: String = "Waiting to start")

class MainActivity : ComponentActivity() {
    private lateinit var settings: SettingsStore
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); settings = SettingsStore(this); setContent { App() } }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun App() {
        var fileUri by remember { mutableStateOf<Uri?>(null) }; var fileName by remember { mutableStateOf("") }; var language by remember { mutableStateOf("Myanmar") }
        var model by remember { mutableStateOf(settings.model) }; var apiKey by remember { mutableStateOf(settings.apiKey) }; var showSettings by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("Choose an SRT file to begin.") }; var busy by remember { mutableStateOf(false) }; var output by remember { mutableStateOf<String?>(null) }
        var parts by remember { mutableStateOf(listOf(PartProgress("Part 1"), PartProgress("Part 2"), PartProgress("Part 3"))) }
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> val name = uri?.let { getDisplayName(it) } ?: ""; if (uri != null && name.lowercase().endsWith(".srt")) { fileUri = uri; fileName = name; status = "File selected" } else if (uri != null) { fileUri = null; fileName = ""; status = "Please choose an .srt subtitle file." } }
        val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri -> if (uri != null && output != null) { contentResolver.openOutputStream(uri)?.use { it.write(output!!.toByteArray()) }; status = "Saved as Translated.srt" } }
        MaterialTheme(colorScheme = darkColorScheme()) { Scaffold(topBar = { TopAppBar(title = { Text("Srt Translator") }, actions = { TextButton(onClick = { showSettings = true }) { Text("Settings") } }) }) { pad ->
            Column(Modifier.padding(pad).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Translate subtitles with Gemini", style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (fileName.isBlank()) "Upload .srt file" else fileName) }
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) { OutlinedTextField(language, { }, readOnly = true, label = { Text("Target language") }, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(expanded, { expanded = false }) { listOf("Myanmar", "English", "Chinese", "Japanese", "Korean", "Thai").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { language = it; expanded = false }) } } }
                Button(enabled = fileUri != null && apiKey.isNotBlank() && !busy, onClick = { parts = listOf(PartProgress("Part 1"), PartProgress("Part 2"), PartProgress("Part 3")); translate(fileUri!!, language, apiKey, model, { status = it }, { busy = it }, { index, update -> parts = parts.toMutableList().also { it[index] = update } }) { output = it; save.launch("Translated.srt") } }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Translating…" else "Translate and Generate") }
                if (busy) { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { RunningIndicator(); Text("LIVE — translation is running", style = MaterialTheme.typography.titleMedium) } }
                if (busy || parts.any { it.total > 0 || it.state != "Queued" }) { Text("Detailed live progress", style = MaterialTheme.typography.titleMedium); parts.forEach { PartCard(it) } }
                SelectionContainer { Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (status.startsWith("Failed") || status.contains("HTTP ") || status.contains("retry")) TextButton(onClick = { val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText("Srt Translator error", status)) }) { Text("Copy error") }
            }
        } }
        if (showSettings) SettingsDialog(apiKey, model, { apiKey = it }, { model = it }, { settings.apiKey = apiKey; settings.model = model; showSettings = false }, { apiKey = ""; settings.apiKey = "" })
    }

    @Composable private fun RunningIndicator() { val transition = rememberInfiniteTransition(label = "running"); val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "rotation"); CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp) }
    @Composable private fun PartCard(p: PartProgress) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(p.name, style = MaterialTheme.typography.titleSmall); Text(p.state, color = if (p.state == "Failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }; if (p.total > 0) { LinearProgressIndicator(progress = { p.completed.toFloat() / p.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth()); Text("${p.completed}/${p.total} subtitles • ${(p.completed * 100 / p.total.coerceAtLeast(1))}% • attempt ${p.attempt}", style = MaterialTheme.typography.bodySmall) }; Text(p.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun SettingsDialog(apiKey: String, model: String, setKey: (String) -> Unit, setModel: (String) -> Unit, save: () -> Unit, clear: () -> Unit) { AlertDialog(onDismissRequest = {}, title = { Text("Settings") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(apiKey, setKey, label = { Text("Gemini API key") }, singleLine = true); var expanded by remember { mutableStateOf(false) }; val models = listOf("gemini-3.7-flash" to "Gemini 3.7 Flash (Hybrid Reasoning)", "gemini-3.6-flash" to "Gemini 3.6 Flash (Agentic)", "gemini-3.5-flash" to "Gemini 3.5 Flash (Stable)", "gemini-3.5-flash-lite" to "Gemini 3.5 Flash-Lite (Low-latency)", "gemini-3.1-flash-lite" to "Gemini 3.1 Flash-Lite (Low-cost)"); ExposedDropdownMenuBox(expanded, { expanded = !expanded }) { OutlinedTextField(models.firstOrNull { it.first == model }?.second ?: model, { }, readOnly = true, label = { Text("Gemini model") }, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(expanded, { expanded = false }) { models.forEach { (id, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { setModel(id); expanded = false }) } } } } }, confirmButton = { TextButton(onClick = save) { Text("Save") } }, dismissButton = { TextButton(onClick = clear) { Text("Clear key") } }) }

    private fun getDisplayName(uri: Uri): String = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else "selected.srt" } ?: (uri.lastPathSegment ?: "selected.srt")
    private fun translate(uri: Uri, language: String, key: String, model: String, setStatus: (String) -> Unit, setBusy: (Boolean) -> Unit, setPart: (Int, PartProgress) -> Unit, done: (String) -> Unit) {
        val handler = CoroutineExceptionHandler { _, e -> setBusy(false); setStatus("Failed safely: ${e.message ?: e.javaClass.simpleName}") }
        lifecycleScope.launch(handler) { try { setBusy(true); val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Unable to read selected SRT file"); val all = SrtParser.parse(raw); val parts = SrtParser.splitThree(all); require(parts.size == 3) { "SRT must contain at least three subtitle blocks" }; val limiter = RequestLimiter(4); val client = GeminiClient(); parts.forEachIndexed { i, p -> setPart(i, PartProgress("Part ${i + 1}", "Queued", 0, p.size, 0, "Queued • waiting for scheduled start")) }
            fun task(index: Int, label: String) = async { setPart(index, PartProgress(label, "Running", 0, parts[index].size, 1, "Starting request • waiting for RPM slot")); client.translate(key.trim(), model, language, parts[index], limiter) { retry, wait, reason -> setPart(index, PartProgress(label, "Retrying", 0, parts[index].size, retry + 1, "Retry $retry/13 scheduled in ${wait}s • $reason")); setStatus("$label retry $retry/13 in ${wait}s") }.also { setPart(index, PartProgress(label, "Completed", it.size, parts[index].size, 1, "All ${it.size} subtitles translated")) } }
            val one = task(0, "Part 1"); delay(3_000); val two = task(1, "Part 2"); delay(3_000); val three = task(2, "Part 3"); val a = one.await(); val b = two.await(); val c = three.await(); done(SrtParser.render(a + b + c)); setStatus("Translation complete • ${all.size}/${all.size} subtitles"); setBusy(false)
        } catch (e: Throwable) { setStatus("Failed: ${e.message ?: e.javaClass.simpleName}"); setBusy(false) } }
    }
}
