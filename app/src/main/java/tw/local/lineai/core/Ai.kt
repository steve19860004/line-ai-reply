package tw.local.lineai.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 呼叫雲端 LLM 產生回覆。支援 Gemini 與 OpenAI 相容端點。 */
object Ai {

    /** 回傳 AI 回覆文字；失敗回 Result.failure。空字串代表「不回」。history 為前後文（可空）。 */
    fun reply(incoming: String, sender: String, history: String = ""): Result<String> = runCatching {
        val key = Prefs.apiKey
        require(key.isNotBlank()) { "尚未設定 API 金鑰" }
        val sys = Prefs.persona + "\n（對方暱稱：$sender）"
        val user = if (history.isNotBlank())
            "（先前的對話記錄，供你了解前後文）\n$history\n\n（對方現在說）$incoming"
        else incoming
        val text = when (Prefs.provider) {
            "openai" -> callOpenAi(sys, user, key)
            else -> callGemini(sys, user, key)
        }
        text.trim()
    }

    /** 用 AI 把一段描述改寫成完整的「人設／語氣指示」。 */
    fun makePersona(desc: String): Result<String> = runCatching {
        val key = Prefs.apiKey
        require(key.isNotBlank()) { "尚未設定 API 金鑰" }
        require(desc.isNotBlank()) { "請先描述你要的身分或語氣" }
        val sys = "你是提示詞工程師。請把使用者的描述，改寫成一段「要交給另一個 AI 看的人設／語氣指示」，用繁體中文、第二人稱（你是…）。要具體寫出：身分與立場、說話語氣、回覆長度（一到兩句）、該做與不該做（例如不要像制式客服、不要加開頭招呼或署名、看不懂或不該代答就回空字串）。只輸出那段指示本身，不要加任何前後說明或引號。"
        val out = when (Prefs.provider) {
            "openai" -> callOpenAi(sys, desc, key)
            else -> callGemini(sys, desc, key)
        }
        out.trim()
    }

    private val RETRY = setOf(429, 500, 502, 503, 504)

    private fun post(urlStr: String, body: JSONObject, headers: Map<String, String>): String {
        var last = ""
        for (attempt in 0..2) {
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
            if (code in 200..299) return resp
            last = "HTTP $code：${resp.take(300)}"
            if (code in RETRY && attempt < 2) { try { Thread.sleep(1500L * (attempt + 1)) } catch (_: Exception) {}; continue }
            error(last)
        }
        error(last)
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
