package com.example.srttranslator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GeminiClient {
    suspend fun translate(apiKey: String, model: String, language: String, part: List<Subtitle>, limiter: RequestLimiter): List<Subtitle> = withContext(Dispatchers.IO) {
        val payload = JSONArray().apply { part.forEachIndexed { i, s -> put(JSONObject().put("i", i).put("text", s.text)) } }
        val prompt = "Translate each subtitle text into natural $language. Return ONLY a JSON array of exactly ${part.size} strings in the same order. Preserve names, tone and subtitle brevity. Do not add numbering, timestamps or markdown.\n" + payload
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))).toString()
        var lastError = ""
        for (attempt in 0 until 3) {
            limiter.awaitSlot()
            val result = request(apiKey, model, body)
            if (result.code in 200..299) return@withContext parse(result.body, part)
            lastError = "Gemini HTTP ${result.code}: ${result.body}"
            if (result.code != 503) error(lastError)
            if (attempt < 2) delay(if (attempt == 0) 5_000L else 10_000L)
        }
        error("$lastError (retried 3 times)")
    }

    private fun request(apiKey: String, model: String, body: String): HttpResult {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 120_000; doOutput = true; setRequestProperty("Content-Type", "application/json") }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            HttpResult(conn.responseCode, stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally { conn.disconnect() }
    }

    private fun parse(response: String, part: List<Subtitle>): List<Subtitle> {
        val root = JSONObject(response)
        val text = root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
            ?: error(root.optJSONObject("error")?.optString("message") ?: "Gemini returned no text")
        val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr = JSONArray(clean)
        require(arr.length() == part.size) { "Gemini returned ${arr.length()} subtitles; expected ${part.size}. Please retry." }
        return part.mapIndexed { i, s -> s.copy(text = arr.getString(i)) }
    }

    private data class HttpResult(val code: Int, val body: String)
}
