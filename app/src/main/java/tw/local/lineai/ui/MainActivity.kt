package tw.local.lineai.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import tw.local.lineai.App
import tw.local.lineai.core.Ai
import tw.local.lineai.core.LineListener
import tw.local.lineai.core.Prefs

class MainActivity : Activity() {

    private val pad by lazy { dp(16) }
    private lateinit var root: LinearLayout

    companion object { private const val REQ_IMPORT = 7001 }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        scroll.addView(root); setContentView(scroll)
    }

    override fun onResume() { super.onResume(); build() }

    private fun build() {
        root.removeAllViews()
        title("LINE AI 回覆")
        hint("讀到 LINE 新訊息 → 交給 AI → 用通知的「回覆」直接送出，不用打開 LINE。需要通知讀取權限與 AI 金鑰。對個人帳號自動代回請斟酌使用。")

        // 1. 通知權限
        card("① 通知讀取權限") {
            val ok = notifAccessOn()
            text(if (ok) "狀態：已開啟 ✓" else "狀態：未開啟 ✗（必要）", color = if (ok) sub else bad)
            button(if (ok) "重新設定通知讀取" else "前往開啟通知讀取", primary = !ok) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            if (Build.VERSION.SDK_INT >= 33) button("允許本 App 發通知（擬稿用）") {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }

        // 2. AI 設定
        card("② AI 設定") {
            text("供應商", bold = true)
            rowButtons(listOf("Gemini" to "gemini", "OpenAI 相容" to "openai"), Prefs.provider) { Prefs.provider = it; build() }
            field("API 金鑰", Prefs.apiKey, password = true) { Prefs.apiKey = it.trim() }
            field("模型（留空用預設：${if (Prefs.provider == "gemini") "gemini-3.5-flash-lite" else "gpt-4o-mini"}）", prefsModelRaw()) { Prefs.model = it.trim() }
            if (Prefs.provider == "openai")
                field("OpenAI 相容端點", Prefs.endpoint) { Prefs.endpoint = it.trim() }
            field("語氣／人設（給 AI 的指示）", Prefs.persona, lines = 4) { Prefs.persona = it }
            button("套用人設範例") { pickPersonaTemplate() }
            button("用 AI 幫我產生人設") { generatePersona() }
            switch("AI 參考前後文（讀這位客人先前訊息，回得更連貫）", Prefs.useContext) { Prefs.useContext = it }
            button("匯入 LINE 聊天記錄當前後文") { importChat() }
            val imp = tw.local.lineai.core.CtxStore.contacts(this@MainActivity)
            if (imp.isNotEmpty()) {
                text("已匯入前後文：${imp.joinToString("、")}", color = sub)
                button("清除匯入的前後文") { tw.local.lineai.core.CtxStore.clearAll(this@MainActivity); toast("已清除"); build() }
            }
            button("測試 AI（擬一句回覆）") {
                toast("測試中…")
                App.bg {
                    val r = Ai.reply("在嗎？晚上有空嗎", "測試好友")
                    App.ui { showText("測試結果", r.getOrElse { "失敗：${it.message}" }.ifBlank { "(AI 回空字串＝不回)" }) }
                }
            }
        }

        // 3. 回覆模式與範圍
        card("③ 回覆模式與範圍") {
            switch("全自動送出（關＝只擬稿待你按送出）", Prefs.autoSend) { Prefs.autoSend = it }
            switch("草稿用懸浮視窗顯示（可當場編輯，比通知好用）", Prefs.useOverlay) { Prefs.useOverlay = it; build() }
            if (Prefs.useOverlay) {
                val ov = Settings.canDrawOverlays(this@MainActivity)
                text(if (ov) "懸浮窗權限：已開 ✓" else "懸浮窗權限：未開 ✗（擬稿卡才跳得出來）", color = if (ov) sub else bad)
                if (!ov) button("前往開啟懸浮窗權限", primary = true) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                } else button("測試懸浮窗（顯示假草稿）") {
                    if (tw.local.lineai.core.Overlay.canShow(this@MainActivity)) tw.local.lineai.core.Overlay.showTest(this@MainActivity)
                    else toast("懸浮窗權限未開")
                }
            }
            switch("也自動回群組", Prefs.replyGroups) { Prefs.replyGroups = it }
            switch("只回白名單聯絡人", Prefs.onlyWhitelist) { Prefs.onlyWhitelist = it }
            field("白名單（對話名稱，用逗號分隔）", Prefs.whitelist.joinToString("，")) {
                Prefs.whitelist = it.split('，', ',').map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.toSet()
            }
            field("同一對話最短間隔／延遲（秒）", Prefs.delaySec.toString(), number = true) {
                Prefs.delaySec = it.toIntOrNull()?.coerceIn(1, 600) ?: 5
            }
        }

        // 4. 關鍵字自動回（免 AI）
        card("④ 關鍵字自動回（免 AI）") {
            switch("啟用關鍵字自動回", Prefs.keywordEnabled) { Prefs.keywordEnabled = it; build() }
            if (Prefs.keywordEnabled) {
                switch("命中關鍵字就不再問 AI（省費用、更可控）", Prefs.keywordSkipAi) { Prefs.keywordSkipAi = it }
                field("規則（每行一條，格式：關鍵字=回覆；關鍵字可用逗號放多個）", Prefs.keywordRules, lines = 5) { Prefs.keywordRules = it }
                text("比對模式（加在關鍵字最前面）：\n・無標記＝包含即命中\n・[完全]在嗎=…（整句相同才命中）\n・[開頭]你好=…（開頭符合）\n・[任意]=…（任何訊息都回，放最後一行）\n回覆可用變數：{名字} {時間} {日期}", color = sub)
                text("範例：\n報價，多少錢，費用=洗一台室內機 1200 起，到府看更準喔\n地址={名字}您好，方便給我地址和樓層嗎？", color = sub)
            }
        }

        // 5. 進階自動回規則
        card("⑤ 進階（時段／只回一次／安全煞車）") {
            switch("只在指定時段才自動送出", Prefs.hoursEnabled) { Prefs.hoursEnabled = it; build() }
            if (Prefs.hoursEnabled) {
                field("開始（0-23 時）", Prefs.hoursStart.toString(), number = true) {
                    Prefs.hoursStart = it.toIntOrNull()?.coerceIn(0, 23) ?: 18
                }
                field("結束（0-23 時，可跨午夜）", Prefs.hoursEnd.toString(), number = true) {
                    Prefs.hoursEnd = it.toIntOrNull()?.coerceIn(0, 23) ?: 8
                }
                text("目前：${Prefs.hoursStart} 點～${Prefs.hoursEnd} 點自動送出，其餘時間只擬稿。例如 18～8＝晚上到隔天早上自動回。", color = sub)
            }
            switch("每個對話只自動回一次（之後交給你）", Prefs.replyOnce) { Prefs.replyOnce = it; build() }
            if (Prefs.replyOnce && Prefs.repliedContacts.isNotEmpty()) {
                text("已自動回過：${Prefs.repliedContacts.joinToString("、")}", color = sub)
                button("清除「已回一次」紀錄") { Prefs.repliedContacts = emptySet(); toast("已清除"); build() }
            }
            switch("安全煞車（短時間回太多次自動切回擬稿，保護 LINE）", Prefs.safetyBrake) { Prefs.safetyBrake = it }
            field("黑名單（永不自動回，逗號分隔）", Prefs.blacklist.joinToString("，")) {
                Prefs.blacklist = it.split('，', ',').map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.toSet()
            }
            field("同一人每天自動回上限（次）", Prefs.maxPerContactPerDay.toString(), number = true) {
                Prefs.maxPerContactPerDay = it.toIntOrNull()?.coerceIn(1, 999) ?: 30
            }
            switch("急件關鍵字提醒（出現時另外通知你本人，不受白名單限制）", Prefs.urgentEnabled) { Prefs.urgentEnabled = it; build() }
            if (Prefs.urgentEnabled)
                field("急件關鍵字（逗號分隔）", Prefs.urgentKeywords) { Prefs.urgentKeywords = it }
            switch("新朋友自動歡迎語（第一次密你時先回一句）", Prefs.welcomeEnabled) { Prefs.welcomeEnabled = it; build() }
            if (Prefs.welcomeEnabled) {
                field("歡迎語（可用變數 {名字} {時間}）", Prefs.welcomeText, lines = 3) { Prefs.welcomeText = it }
                if (Prefs.knownContacts.isNotEmpty())
                    button("清除「已認識名單」（目前 ${Prefs.knownContacts.size} 人，清除後會重新歡迎）") {
                        Prefs.knownContacts = emptySet(); toast("已清除"); build()
                    }
            }
        }

        // 6. 總開關
        card("⑥ 總開關") {
            switch("外出／忙碌模式（期間一律回下方固定訊息）", Prefs.awayEnabled) { Prefs.awayEnabled = it; build() }
            if (Prefs.awayEnabled) field("外出訊息（可用 {名字}）", Prefs.awayText, lines = 2) { Prefs.awayText = it }
            val listenerOk = notifAccessOn() && LineListener.connected
            switch("啟用 LINE AI 回覆", Prefs.enabled) { on ->
                if (on && !notifAccessOn()) { toast("請先開啟通知讀取權限"); Prefs.enabled = false; build(); return@switch }
                Prefs.enabled = on
            }
            text(
                if (Prefs.enabled && listenerOk) "運作中：" + (if (Prefs.autoSend) "全自動送出" else "擬稿待確認")
                else if (Prefs.enabled) "已啟用，但通知監聽尚未連上（到①開啟並重開 App）"
                else "未啟用", color = sub
            )
            val pm = getSystemService(android.os.PowerManager::class.java)
            val exempt = pm.isIgnoringBatteryOptimizations(packageName)
            text(if (exempt) "省電限制：已關閉（休眠也能回）✓" else "省電限制：仍在限制 ✗（休眠可能收不到/不回）", color = if (exempt) sub else bad)
            if (!exempt) button("關閉省電限制（背景常駐，休眠也能回）", primary = true) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:$packageName")))
            }
            text("※ 想在睡覺／螢幕關閉時也自動回，請：開「全自動送出」＋關省電限制；vivo／小米另到設定開本 App 的「自啟動」。", color = sub)
        }
        // 7. 訊息備份／收回救回
        card("⑦ 訊息備份／收回救回") {
            text("自動存下每一則收到的文字訊息，對方「收回」後這裡仍看得到原文。常駐，不受總開關影響。", color = sub)
            button("重新整理") { build() }
            val msgs = tw.local.lineai.core.MsgStore.recent(this@MainActivity, 30)
            if (msgs.isEmpty()) text("（還沒有備份。收到 LINE 文字訊息後會自動出現。）", color = sub)
            else {
                text("共備份 ${tw.local.lineai.core.MsgStore.count(this@MainActivity)} 則，顯示最近 30 則：", color = sub)
                val tf = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.US)
                for (m in msgs) text("${tf.format(java.util.Date(m.time))}  ${m.sender}：${m.text}", color = sub)
                button("匯出備份（複製全部）") {
                    val txt = tw.local.lineai.core.MsgStore.exportText(this@MainActivity)
                    (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("lineai_msgs", txt))
                    toast("已複製 ${tw.local.lineai.core.MsgStore.count(this@MainActivity)} 則到剪貼簿")
                }
                button("清空備份") { tw.local.lineai.core.MsgStore.clear(this@MainActivity); toast("已清空"); build() }
            }
        }

        // 8. 最近事件（除錯）
        card("⑧ 最近事件（除錯用）") {
            button("重新整理") { build() }
            val evs = LineListener.events.toList()
            if (evs.isEmpty()) text("（還沒有事件。請白名單的人傳一句 LINE，再回來按重新整理。）", color = sub)
            else for (e in evs.take(50)) text(e, color = sub)
        }

        // 9. 設定備份
        card("⑨ 設定備份（匯出／匯入）") {
            text("把金鑰、人設、關鍵字規則等設定備份成一段文字，換機或重裝可還原。注意：匯出內容含 API 金鑰，請妥善保管、勿外流。", color = sub)
            button("匯出設定（複製到剪貼簿）") {
                val js = Prefs.exportJson()
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("lineai", js))
                showText("已複製設定（可貼到記事本保存）", js)
            }
            button("匯入設定（貼上文字）") {
                val et = EditText(this@MainActivity).apply {
                    hint = "貼上先前匯出的設定文字"
                    setLines(6); gravity = Gravity.TOP
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                }
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("匯入設定").setView(et)
                    .setPositiveButton("匯入") { _, _ ->
                        if (Prefs.importJson(et.text.toString())) { toast("匯入成功"); build() }
                        else toast("格式錯誤，匯入失敗")
                    }
                    .setNegativeButton("取消", null).show()
            }
        }

        hint("提醒：LINE 要開通知且開「顯示訊息內容」，否則讀不到內文。對個人帳號自動代回可能違反 LINE 條款，請自行評估。")
    }

    private fun prefsModelRaw(): String =
        getSharedPreferences("lineai", MODE_PRIVATE).getString("model", "") ?: ""

    private fun notifAccessOn(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(':').any { it.contains(packageName) }
    }

    // ---------- 小 UI 工具 ----------
    private val sub get() = Color.parseColor("#8899AA")
    private val bad get() = Color.parseColor("#C0504D")
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun title(t: String) = root.addView(TextView(this).apply { text = t; textSize = 20f; setPadding(0, 0, 0, dp(8)) })
    private fun hint(t: String) = root.addView(TextView(this).apply {
        text = t; textSize = 12.5f; setTextColor(sub); setPadding(dp(4), dp(4), dp(4), dp(8))
    })
    private fun card(t: String, body: LinearLayout.() -> Unit) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = GradientDrawableCard()
        }
        c.addView(TextView(this).apply { text = t; textSize = 16f; setPadding(0, 0, 0, dp(6)) })
        c.body()
        root.addView(c, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
    }
    @Suppress("FunctionName")
    private fun GradientDrawableCard() = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#1A000000"))
    }
    private fun LinearLayout.text(t: String, bold: Boolean = false, color: Int? = null) =
        addView(TextView(this@MainActivity).apply {
            text = t; textSize = 13.5f; if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            if (color != null) setTextColor(color); setPadding(0, dp(2), 0, dp(2))
        })
    private fun LinearLayout.button(label: String, primary: Boolean = false, onClick: () -> Unit) =
        addView(Button(this@MainActivity).apply {
            text = label; isAllCaps = false
            if (primary) { setBackgroundColor(Color.parseColor("#3A7BD5")); setTextColor(Color.WHITE) }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) }
            setOnClickListener { onClick() }
        })
    private fun LinearLayout.switch(label: String, value: Boolean, onChange: (Boolean) -> Unit) =
        addView(Switch(this@MainActivity).apply {
            text = label; textSize = 13.5f; isChecked = value
            setPadding(0, dp(6), 0, dp(6))
            setOnCheckedChangeListener { _, b -> onChange(b) }
        })
    private fun LinearLayout.field(label: String, value: String, password: Boolean = false, number: Boolean = false, lines: Int = 1, onSet: (String) -> Unit) {
        text(label, bold = true)
        val et = EditText(this@MainActivity).apply {
            setText(value); textSize = 13.5f
            inputType = when {
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                number -> InputType.TYPE_CLASS_NUMBER
                lines > 1 -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
            if (lines > 1) { setLines(lines); gravity = Gravity.TOP }
        }
        addView(et, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(Button(this@MainActivity).apply {
            text = "儲存"; isAllCaps = false; textSize = 12f
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            setOnClickListener { onSet(et.text.toString()); toast("已儲存") }
        })
    }
    private fun LinearLayout.rowButtons(opts: List<Pair<String, String>>, cur: String, onPick: (String) -> Unit) {
        val rowL = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
        for ((label, v) in opts) rowL.addView(Button(this@MainActivity).apply {
            text = label; isAllCaps = false; textSize = 13f
            if (v == cur) { setBackgroundColor(Color.parseColor("#3A7BD5")); setTextColor(Color.WHITE) }
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
            setOnClickListener { onPick(v) }
        })
        addView(rowL)
    }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    private fun showText(t: String, b: String) =
        android.app.AlertDialog.Builder(this).setTitle(t).setMessage(b).setPositiveButton("好", null).show()

    // 選內建人設範例
    private fun pickPersonaTemplate() {
        val tpl = tw.local.lineai.core.PersonaTemplates.list
        val names = tpl.map { it.first }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("選一個人設範例")
            .setItems(names) { _, i ->
                Prefs.persona = tpl[i].second
                toast("已套用：${names[i]}"); build()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 匯入 LINE 匯出的聊天記錄 .txt 當前後文
    private fun importChat() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
        try { startActivityForResult(i, REQ_IMPORT) } catch (e: Exception) { toast("找不到檔案選擇器") }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != REQ_IMPORT || res != RESULT_OK) return
        val uri = data?.data ?: return
        val raw = try { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } } catch (e: Exception) { null }
        if (raw.isNullOrBlank()) { toast("讀取失敗或檔案是空的"); return }
        val r = tw.local.lineai.core.LineChatParser.parse(raw)
        if (r.count == 0) { toast("看起來不是 LINE 聊天記錄格式"); return }
        val et = EditText(this).apply {
            setText(r.name)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("匯入前後文（共 ${r.count} 則，取最後 40 則）")
            .setMessage("請確認對象名稱，需與 LINE 聊天室顯示的名稱一致，AI 才對得上。")
            .setView(et)
            .setPositiveButton("匯入") { _, _ ->
                val nm = et.text.toString().trim()
                if (nm.isEmpty()) { toast("請填對象名稱"); return@setPositiveButton }
                tw.local.lineai.core.CtxStore.set(this, nm, r.history)
                toast("已匯入「$nm」的前後文"); build()
            }
            .setNegativeButton("取消", null).show()
    }

    // 用 AI 依描述產生人設
    private fun generatePersona() {
        val et = EditText(this).apply {
            setText("專業冷氣師傅，親切口氣，會先問地址和冷氣品牌型號、問題狀況，並說師傅看到會親自回")
            setLines(4); gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("描述你要的身分／語氣")
            .setView(et)
            .setPositiveButton("產生") { _, _ ->
                toast("AI 產生中…")
                App.bg {
                    val r = Ai.makePersona(et.text.toString())
                    App.ui {
                        val txt = r.getOrElse { "失敗：${it.message}" }
                        android.app.AlertDialog.Builder(this)
                            .setTitle("產生結果（可直接套用）")
                            .setMessage(txt)
                            .setPositiveButton("套用") { _, _ ->
                                if (r.isSuccess && txt.isNotBlank()) { Prefs.persona = txt; toast("已套用"); build() }
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
