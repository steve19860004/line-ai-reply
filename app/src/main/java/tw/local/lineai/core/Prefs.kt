package tw.local.lineai.core

import android.content.Context
import android.content.SharedPreferences

/** 所有設定集中在這裡。 */
object Prefs {
    private lateinit var sp: SharedPreferences
    fun init(ctx: Context) { sp = ctx.getSharedPreferences("lineai", Context.MODE_PRIVATE) }

    // 總開關
    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    // 模式：true = 全自動送出；false = 只擬稿待確認
    var autoSend: Boolean
        get() = sp.getBoolean("auto_send", false)
        set(v) = sp.edit().putBoolean("auto_send", v).apply()

    // AI 設定
    var provider: String  // "gemini" | "openai"
        get() = sp.getString("provider", "gemini")!!
        set(v) = sp.edit().putString("provider", v).apply()
    var apiKey: String
        get() = sp.getString("api_key", "")!!
        set(v) = sp.edit().putString("api_key", v).apply()
    var model: String
        get() = sp.getString("model", "")!!.ifBlank { if (provider == "gemini") "gemini-2.5-flash" else "gpt-4o-mini" }
        set(v) = sp.edit().putString("model", v).apply()
    var endpoint: String  // openai-compatible base url
        get() = sp.getString("endpoint", "https://api.openai.com/v1")!!
        set(v) = sp.edit().putString("endpoint", v).apply()
    var persona: String
        get() = sp.getString("persona", DEFAULT_PERSONA)!!
        set(v) = sp.edit().putString("persona", v).apply()

    // 範圍與防洗版
    var replyGroups: Boolean
        get() = sp.getBoolean("reply_groups", false)
        set(v) = sp.edit().putBoolean("reply_groups", v).apply()
    var onlyWhitelist: Boolean
        get() = sp.getBoolean("only_whitelist", false)
        set(v) = sp.edit().putBoolean("only_whitelist", v).apply()
    var whitelist: Set<String>
        get() = sp.getStringSet("whitelist", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("whitelist", v).apply()
    var delaySec: Int
        get() = sp.getInt("delay_sec", 5)
        set(v) = sp.edit().putInt("delay_sec", v).apply()
    var maxPerContactPerDay: Int
        get() = sp.getInt("max_day", 30)
        set(v) = sp.edit().putInt("max_day", v).apply()

    const val DEFAULT_PERSONA =
        "你是我本人，用我平常在 LINE 上的口氣用繁體中文簡短回覆對方。親切自然、不要太正式、不要像客服或機器人、不要加開頭招呼或署名，直接回話。一句到兩句即可。看不懂或不該代答的訊息就回空字串。"
}
