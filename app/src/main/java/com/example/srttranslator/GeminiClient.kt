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
        val text = JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim().removePrefix("```").removePrefix("json").removeSuffix("```").trim()
        val arr = JSONArray(text); require(arr.length() == part.size) { "Expected ${part.size} translations, got ${arr.length()}" }
        part.mapIndexed { i, s -> s.copy(text = arr.getString(i)) }
    }
}
