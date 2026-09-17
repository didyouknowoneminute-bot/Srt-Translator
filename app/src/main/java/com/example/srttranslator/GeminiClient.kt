package com.example.srttranslator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GeminiClient {
    suspend fun translate(apiKey: String, model: String, language: String, part: List<Subtitle>, limiter: RequestLimiter): List<Subtitle> = withContext(Dispatchers.IO) {
        limiter.awaitSlot()
        val payload = JSONArray().apply { part.forEachIndexed { i, s -> put(JSONObject().put("i", i).put("text", s.text)) } }
        val prompt = "Translate each subtitle text into natural $language. Return ONLY a JSON array of exactly ${part.size} strings in the same order. Preserve names, tone and subtitle brevity. Do not add numbering, timestamps or markdown.\\n" + payload
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))).toString()
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 120_000; doOutput = true; setRequestProperty("Content-Type", "application/json") }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val response = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        if (conn.responseCode !in 200..299) error("Gemini HTTP ${conn.responseCode}: $response")
        val root = JSONObject(response)
        val text = root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
            ?: error(root.optJSONObject("error")?.optString("message") ?: "Gemini returned no text")
        val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr = JSONArray(clean); require(arr.length() == part.size) { "Gemini returned ${arr.length()} subtitles; expected ${part.size}. Please retry." }
        part.mapIndexed { i, s -> s.copy(text = arr.getString(i)) }
    }
}
