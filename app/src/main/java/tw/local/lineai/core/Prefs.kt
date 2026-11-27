package tw.local.lineai.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

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

    // 新朋友自動歡迎語
    var welcomeEnabled: Boolean
        get() = sp.getBoolean("welcome_enabled", false)
        set(v) = sp.edit().putBoolean("welcome_enabled", v).apply()
    var welcomeText: String
        get() = sp.getString("welcome_text", DEFAULT_WELCOME)!!
        set(v) = sp.edit().putString("welcome_text", v).apply()
    var knownContacts: Set<String>
        get() = sp.getStringSet("known_contacts", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("known_contacts", v).apply()

    // 外出/忙碌模式：期間一律回固定訊息
    var awayEnabled: Boolean
        get() = sp.getBoolean("away_enabled", false)
        set(v) = sp.edit().putBoolean("away_enabled", v).apply()
    var awayText: String
        get() = sp.getString("away_text", DEFAULT_AWAY)!!
        set(v) = sp.edit().putString("away_text", v).apply()

    // 急件關鍵字提醒（出現時另外通知本人）
    var urgentEnabled: Boolean
        get() = sp.getBoolean("urgent_enabled", false)
        set(v) = sp.edit().putBoolean("urgent_enabled", v).apply()
    var urgentKeywords: String
        get() = sp.getString("urgent_kw", "漏水,不冷,沒反應,故障,壞掉,急,停機,跳電")!!
        set(v) = sp.edit().putString("urgent_kw", v).apply()

    // 黑名單：永不自動回
    var blacklist: Set<String>
        get() = sp.getStringSet("blacklist", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("blacklist", v).apply()

    // AI 是否參考前後文
    var useContext: Boolean
        get() = sp.getBoolean("use_context", true)
        set(v) = sp.edit().putBoolean("use_context", v).apply()

    /** 匯出所有設定成 JSON 文字（含 API 金鑰，請妥善保管）。 */
    fun exportJson(): String = JSONObject().apply {
        put("v", 1)
        put("provider", provider); put("apiKey", apiKey)
        put("model", sp.getString("model", "")); put("endpoint", endpoint); put("persona", persona)
        put("autoSend", autoSend); put("useOverlay", useOverlay)
        put("replyGroups", replyGroups); put("onlyWhitelist", onlyWhitelist)
        put("whitelist", JSONArray(whitelist.toList()))
        put("delaySec", delaySec); put("maxDay", maxPerContactPerDay)
        put("kwEnabled", keywordEnabled); put("kwSkipAi", keywordSkipAi); put("kwRules", keywordRules)
        put("hoursEnabled", hoursEnabled); put("hoursStart", hoursStart); put("hoursEnd", hoursEnd)
        put("replyOnce", replyOnce); put("safetyBrake", safetyBrake)
        put("welcomeEnabled", welcomeEnabled); put("welcomeText", welcomeText)
        put("awayEnabled", awayEnabled); put("awayText", awayText)
        put("urgentEnabled", urgentEnabled); put("urgentKeywords", urgentKeywords)
        put("blacklist", JSONArray(blacklist.toList())); put("useContext", useContext)
    }.toString(2)

    /** 從匯出的 JSON 還原設定；成功回 true。 */
    fun importJson(s: String): Boolean = try {
        val o = JSONObject(s)
        if (o.has("provider")) provider = o.getString("provider")
        if (o.has("apiKey")) apiKey = o.getString("apiKey")
        if (o.has("model")) model = o.getString("model")
        if (o.has("endpoint")) endpoint = o.getString("endpoint")
        if (o.has("persona")) persona = o.getString("persona")
        if (o.has("autoSend")) autoSend = o.getBoolean("autoSend")
        if (o.has("useOverlay")) useOverlay = o.getBoolean("useOverlay")
        if (o.has("replyGroups")) replyGroups = o.getBoolean("replyGroups")
        if (o.has("onlyWhitelist")) onlyWhitelist = o.getBoolean("onlyWhitelist")
        if (o.has("whitelist")) { val a = o.getJSONArray("whitelist"); whitelist = (0 until a.length()).map { a.getString(it) }.toSet() }
        if (o.has("delaySec")) delaySec = o.getInt("delaySec")
        if (o.has("maxDay")) maxPerContactPerDay = o.getInt("maxDay")
        if (o.has("kwEnabled")) keywordEnabled = o.getBoolean("kwEnabled")
        if (o.has("kwSkipAi")) keywordSkipAi = o.getBoolean("kwSkipAi")
        if (o.has("kwRules")) keywordRules = o.getString("kwRules")
        if (o.has("hoursEnabled")) hoursEnabled = o.getBoolean("hoursEnabled")
        if (o.has("hoursStart")) hoursStart = o.getInt("hoursStart")
        if (o.has("hoursEnd")) hoursEnd = o.getInt("hoursEnd")
        if (o.has("replyOnce")) replyOnce = o.getBoolean("replyOnce")
        if (o.has("safetyBrake")) safetyBrake = o.getBoolean("safetyBrake")
        if (o.has("welcomeEnabled")) welcomeEnabled = o.getBoolean("welcomeEnabled")
        if (o.has("welcomeText")) welcomeText = o.getString("welcomeText")
        if (o.has("awayEnabled")) awayEnabled = o.getBoolean("awayEnabled")
        if (o.has("awayText")) awayText = o.getString("awayText")
        if (o.has("urgentEnabled")) urgentEnabled = o.getBoolean("urgentEnabled")
        if (o.has("urgentKeywords")) urgentKeywords = o.getString("urgentKeywords")
        if (o.has("blacklist")) { val a = o.getJSONArray("blacklist"); blacklist = (0 until a.length()).map { a.getString(it) }.toSet() }
        if (o.has("useContext")) useContext = o.getBoolean("useContext")
        true
    } catch (_: Exception) { false }

    const val DEFAULT_WELCOME =
        "您好～我是師傅的 AI 小幫手，先幫忙了解需求，師傅看到會親自回覆您。方便先給我地址和冷氣品牌型號嗎？"

    const val DEFAULT_AWAY =
        "師傅目前外出施工中，看到訊息會親自回覆您🙏 急件請直接來電。"

    const val DEFAULT_PERSONA =
        "你是我本人，用我平常在 LINE 上的口氣用繁體中文簡短回覆對方。親切自然、不要太正式、不要像客服或機器人、不要加開頭招呼或署名，直接回話。一句到兩句即可。看不懂或不該代答的訊息就回空字串。"
}

/** 內建人設範例，可在 App 內一鍵套用。 */
object PersonaTemplates {
    val list: List<Pair<String, String>> = listOf(
        "AI 小幫手・會收資訊（推薦）" to
            "你是冷氣師傅的 LINE AI 小幫手。請先自然表明「我是師傅的 AI 小幫手，先幫忙了解狀況，師傅看到會親自回覆」。用繁體中文、親切口氣，簡短一到兩句。主動詢問必要資訊：地址或地區、冷氣品牌/型號、問題狀況（不冷／漏水／異音／要安裝或清洗）、方便到府的時間。不要報確切價格、不要承諾時間，那些留給師傅。看不懂或與冷氣無關就回空字串。",
        "像我本人・簡短自然" to Prefs.DEFAULT_PERSONA,
        "專業親切・報價口氣" to
            "你是專業冷氣師傅本人，用繁體中文、親切專業的口氣簡短回覆（一到兩句）。可給大概的服務方向與粗略費用範圍，但強調到府看了才準。不要像制式客服、不要加開頭招呼或署名。看不懂或不該代答就回空字串。",
        "正式客服" to
            "你是冷氣行的客服，用繁體中文、禮貌正式但不囉嗦的口氣回覆（一到兩句）。先簡短致意再回應，必要時引導對方提供地址與機型。不要過度熱情、不要署名。看不懂就回空字串。",
        "幽默輕鬆" to
            "你是冷氣師傅本人，用繁體中文、輕鬆帶點幽默的口氣簡短回覆（一到兩句），親切不油。不要像客服、不要署名、不要用太多表情符號。看不懂或不該代答就回空字串。"
    )
}
