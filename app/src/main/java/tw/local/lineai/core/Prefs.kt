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

    // 擬稿用懸浮視窗顯示（而非通知）
    var useOverlay: Boolean
        get() = sp.getBoolean("use_overlay", true)
        set(v) = sp.edit().putBoolean("use_overlay", v).apply()

    // AI 設定
    var provider: String  // "gemini" | "openai"
        get() = sp.getString("provider", "gemini")!!
        set(v) = sp.edit().putString("provider", v).apply()
    var apiKey: String
        get() = sp.getString("api_key", "")!!
        set(v) = sp.edit().putString("api_key", v).apply()
    var model: String
        get() = sp.getString("model", "")!!.ifBlank { if (provider == "gemini") "gemini-3.5-flash-lite" else "gpt-4o-mini" }
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

    // B 關鍵字自動回（免 AI）
    var keywordEnabled: Boolean
        get() = sp.getBoolean("kw_enabled", false)
        set(v) = sp.edit().putBoolean("kw_enabled", v).apply()
    var keywordSkipAi: Boolean  // 命中關鍵字就不再問 AI
        get() = sp.getBoolean("kw_skip_ai", true)
        set(v) = sp.edit().putBoolean("kw_skip_ai", v).apply()
    var keywordRules: String    // 每行「關鍵字=回覆」，關鍵字可用逗號放多個
        get() = sp.getString("kw_rules", "")!!
        set(v) = sp.edit().putString("kw_rules", v).apply()

    // C 營業時間：只在此時段自動送出（支援跨午夜）
    var hoursEnabled: Boolean
        get() = sp.getBoolean("hours_enabled", false)
        set(v) = sp.edit().putBoolean("hours_enabled", v).apply()
    var hoursStart: Int
        get() = sp.getInt("hours_start", 18)
        set(v) = sp.edit().putInt("hours_start", v).apply()
    var hoursEnd: Int
        get() = sp.getInt("hours_end", 8)
        set(v) = sp.edit().putInt("hours_end", v).apply()

    // D 每個對話只自動回一次
    var replyOnce: Boolean
        get() = sp.getBoolean("reply_once", false)
        set(v) = sp.edit().putBoolean("reply_once", v).apply()
    var repliedContacts: Set<String>
        get() = sp.getStringSet("replied_contacts", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("replied_contacts", v).apply()

    // E 安全煞車：短時間回太多次就自動切回擬稿
    var safetyBrake: Boolean
        get() = sp.getBoolean("safety_brake", true)
        set(v) = sp.edit().putBoolean("safety_brake", v).apply()

    const val DEFAULT_PERSONA =
        "你是我本人，用我平常在 LINE 上的口氣用繁體中文簡短回覆對方。親切自然、不要太正式、不要像客服或機器人、不要加開頭招呼或署名，直接回話。一句到兩句即可。看不懂或不該代答的訊息就回空字串。"
}
