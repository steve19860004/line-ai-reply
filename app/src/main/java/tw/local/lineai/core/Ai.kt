package tw.local.lineai.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 呼叫雲端 LLM 產生回覆。支援 Gemini 與 OpenAI 相容端點。 */
object Ai {

    /** 回傳 AI 回覆文字；失敗回 Result.failure。空字串代表「不回」。 */
    fun reply(incoming: String, sender: String): Result<String> = runCatching {
        val key = Prefs.apiKey
        require(key.isNotBlank()) { "尚未設定 API 金鑰" }
        val sys = Prefs.persona + "\n（對方暱稱：$sender）"
        val text = when (Prefs.provider) {
            "openai" -> callOpenAi(sys, incoming, key)
            else -> callGemini(sys, incoming, key)
        }
        text.trim()
    }

    private fun post(urlStr: String, body: JSONObject, headers: Map<String, String>): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000; readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) error("HTTP $code：${resp.take(300)}")
        return resp
    }

    private fun callGemini(sys: String, user: String, key: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${Prefs.model}:generateContent?key=$key"
        val body = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", user)))
            }))
        }
        val resp = JSONObject(post(url, body, emptyMap()))
        return resp.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
            ?.optString("text", "") ?: ""
    }

    private fun callOpenAi(sys: String, user: String, key: String): String {
        val url = Prefs.endpoint.trimEnd('/') + "/chat/completions"
        val body = JSONObject().apply {
            put("model", Prefs.model)
            put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", sys))
                .put(JSONObject().put("role", "user").put("content", user)))
            put("temperature", 0.7)
        }
        val resp = JSONObject(post(url, body, mapOf("Authorization" to "Bearer $key")))
        return resp.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content", "") ?: ""
    }
}
