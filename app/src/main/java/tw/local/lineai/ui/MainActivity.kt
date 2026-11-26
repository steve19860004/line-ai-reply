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

        // 4. 總開關
        card("④ 總開關") {
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
        // 5. 最近事件（除錯）
        card("⑤ 最近事件（除錯用）") {
            button("重新整理") { build() }
            val evs = LineListener.events.toList()
            if (evs.isEmpty()) text("（還沒有事件。請白名單的人傳一句 LINE，再回來按重新整理。）", color = sub)
            else for (e in evs.take(25)) text(e, color = sub)
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
}
