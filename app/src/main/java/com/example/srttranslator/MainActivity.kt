package com.example.srttranslator

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var settings: SettingsStore
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); settings = SettingsStore(this); setContent { App() } }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun App() {
        var fileUri by remember { mutableStateOf<Uri?>(null) }; var fileName by remember { mutableStateOf("") }; var language by remember { mutableStateOf("Myanmar") }
        var model by remember { mutableStateOf(settings.model) }; var apiKey by remember { mutableStateOf(settings.apiKey) }; var showSettings by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("Choose an SRT file to begin.") }; var busy by remember { mutableStateOf(false) }; var output by remember { mutableStateOf<String?>(null) }
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val name = uri?.let { getDisplayName(it) } ?: ""
            if (uri != null && name.lowercase().endsWith(".srt")) { fileUri = uri; fileName = name; status = "File selected" }
            else if (uri != null) { fileUri = null; fileName = ""; status = "Please choose an .srt subtitle file." }
        }
        val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri -> if (uri != null && output != null) { contentResolver.openOutputStream(uri)?.use { it.write(output!!.toByteArray()) }; status = "Saved as Translated.srt" } }
        MaterialTheme(colorScheme = darkColorScheme()) { Scaffold(topBar = { TopAppBar(title = { Text("Srt Translator") }, actions = { TextButton(onClick = { showSettings = true }) { Text("Settings") } }) }) { pad ->
            Column(Modifier.padding(pad).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Translate subtitles with Gemini", style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (fileName.isBlank()) "Upload .srt file" else fileName) }
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) { OutlinedTextField(language, { }, readOnly = true, label = { Text("Target language") }, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(expanded, { expanded = false }) { listOf("Myanmar", "English", "Chinese", "Japanese", "Korean", "Thai").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { language = it; expanded = false }) } } }
                Button(enabled = fileUri != null && apiKey.isNotBlank() && !busy, onClick = { translate(fileUri!!, language, apiKey, model, { status = it }, { busy = it }) { output = it; save.launch("Translated.srt") } }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Translating…" else "Translate and Generate") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Settings") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(apiKey, { apiKey = it }, label = { Text("Gemini API key") }, singleLine = true); var modelExpanded by remember { mutableStateOf(false) }; ExposedDropdownMenuBox(modelExpanded, { modelExpanded = !modelExpanded }) { OutlinedTextField(model, { }, readOnly = true, label = { Text("Gemini model") }, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(modelExpanded, { modelExpanded = false }) { listOf("gemini-3.1-pro-preview", "gemini-3-flash-preview").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { model = it; modelExpanded = false }) } } } } }, confirmButton = { TextButton(onClick = { settings.apiKey = apiKey; settings.model = model; showSettings = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { apiKey = ""; settings.apiKey = "" }) { Text("Clear key") } })
    }
    private fun getDisplayName(uri: Uri): String = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "selected.srt" } ?: (uri.lastPathSegment ?: "selected.srt")
    private fun translate(uri: Uri, language: String, key: String, model: String, setStatus: (String) -> Unit, setBusy: (Boolean) -> Unit, done: (String) -> Unit) { lifecycleScope.launch { try { setBusy(true); val raw = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }; val parts = SrtParser.splitThree(SrtParser.parse(raw)); require(parts.size == 3) { "SRT must contain at least three subtitle blocks" }; val limiter = RequestLimiter(4); val client = GeminiClient(); setStatus("Part 1 translating…"); val one = async { client.translate(key, model, language, parts[0], limiter) }; delay(3_000); setStatus("Part 2 translating…"); val two = async { client.translate(key, model, language, parts[1], limiter) }; delay(3_000); setStatus("Part 3 translating…"); val three = async { client.translate(key, model, language, parts[2], limiter) }; done(SrtParser.render(one.await() + two.await() + three.await())); setStatus("Translation complete"); } catch (e: Exception) { setStatus("Failed: ${e.message}") } finally { setBusy(false) } } }
}
