package tw.local.lineai.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.app.RemoteInput
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import tw.local.lineai.App
import java.util.concurrent.ConcurrentHashMap

class LineListener : NotificationListenerService() {

    override fun onListenerConnected() {
        connected = true
        ensureChannel(this)
        log("通知監聽已連線")
    }
    override fun onListenerDisconnected() { connected = false }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return  // 忽略本 App 自己發的通知
        if (pkg != LINE_PKG) { if (Prefs.enabled) log("· 通知來源：$pkg"); return }

        val n = sbn.notification ?: return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = latestMessage(ex) ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()

        // A：訊息備份／收回救回（常駐，不受總開關影響）
        if (!title.isNullOrBlank() && text.isNotBlank()) MsgStore.add(this, title, text)

        // 以下為「自動回覆」邏輯，需要總開關
        if (!Prefs.enabled) return
        if (title.isNullOrBlank()) return
        val hasAction = n.actions?.any { !it.remoteInputs.isNullOrEmpty() } == true
        if (text.isBlank()) { log("「$title」無文字(貼圖/圖片)，略過"); return }
        log("偵測到「$title」：$text（回覆鈕：${if (hasAction) "有" else "無"}）")

        val isGroup = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        if (isGroup && !Prefs.replyGroups) { log("「$title」是群組，略過"); return }

        // 急件關鍵字提醒：不受白名單限制，出現即通知本人（避免急單被埋沒）
        if (Prefs.urgentEnabled && matchUrgent(text)) postUrgent(this, title, text, n.contentIntent)

        // 白名單：模糊比對（名稱有包含即可，不分大小寫）
        if (Prefs.onlyWhitelist && Prefs.whitelist.none { title.contains(it, true) }) {
            log("「$title」不在白名單，略過"); return
        }

        // 黑名單：永不自動回
        if (Prefs.blacklist.any { title.contains(it, true) }) { log("「$title」在黑名單，略過不回"); return }

        val action = n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
        val openIntent = n.contentIntent  // 開啟該 LINE 對話用（沒有回覆鈕時的備援）

        // 同一則訊息，LINE 常同時發「有回覆鈕」「無回覆鈕」兩個通知。
        // 有鈕那個會自動送出，就先標記，讓無鈕那個不要又跳多餘的懸浮視窗草稿。
        val msgKey = "$title\u0001$text"
        val intendAuto = Prefs.autoSend && action != null && inAutoWindow() &&
            !(Prefs.replyOnce && Prefs.repliedContacts.any { title.equals(it, true) }) && dayCountOk(title)
        if (intendAuto) autoPending[msgKey] = System.currentTimeMillis()

        val key = sbn.key ?: title
        if (text == lastSentReply[key]) return  // 這句正是我們剛送出的回覆（回聲），略過
        if (lastHandled[key] == text) return  // 同一句不重複
        lastHandled[key] = text
        val now = System.currentTimeMillis()
        if (now - (lastReplyAt[key] ?: 0) < Prefs.delaySec * 1000L) { log("「$title」間隔太短，略過"); return }

        log("收到「$title」：$text → 處理中…")
        App.bg {
            try { Thread.sleep(Prefs.delaySec * 1000L) } catch (_: Exception) {}
            if (!Prefs.enabled) return@bg

            // 新朋友歡迎語：第一次遇到這個對話 → 先回歡迎語（之後正常流程）
            val isNew = title !in Prefs.knownContacts
            if (isNew) Prefs.knownContacts = Prefs.knownContacts + title
            val base: String = when {
                // 外出/忙碌模式最優先：一律回固定訊息
                Prefs.awayEnabled && Prefs.awayText.isNotBlank() -> {
                    log("外出模式 →「${Prefs.awayText}」"); Prefs.awayText
                }
                Prefs.welcomeEnabled && isNew && Prefs.welcomeText.isNotBlank() -> {
                    log("新朋友「$title」→ 歡迎語"); Prefs.welcomeText
                }
                else -> {
                    // B：關鍵字優先（命中可免 AI）
                    val kw = matchKeyword(text)
                    if (kw != null && Prefs.keywordSkipAi) {
                        log("關鍵字命中 →「$kw」"); kw
                    } else {
                        val ai = Ai.reply(text, title, buildHistory(this, title)).getOrElse { log("AI 失敗：${it.message}"); return@bg }
                        if (ai.isBlank()) {
                            if (kw != null) kw else { log("「$title」AI 判斷不回(空回覆)"); return@bg }
                        } else ai
                    }
                }
            }
            val ans = applyVars(base, title)  // 變數替換 {名字}{時間}

            // C/D：是否允許自動送出
            val windowOk = inAutoWindow()
            val alreadyOnce = Prefs.replyOnce && Prefs.repliedContacts.any { title.equals(it, true) }
            val dayOk = dayCountOk(title)  // 每日每人上限
            val autoAllowed = Prefs.autoSend && action != null && windowOk && !alreadyOnce && dayOk

            if (autoAllowed) {
                // E：安全煞車（短時間自動回太多次 → 疑似迴圈，切回擬稿保護 LINE）
                if (Prefs.safetyBrake && recentReplies() >= BRAKE_MAX) {
                    Prefs.autoSend = false
                    log("⚠️ 短時間自動回太多次，已自動切回擬稿保護 LINE")
                    deliverDraft(this, title, text, ans, action, openIntent)
                    return@bg
                }
                // 避免同一則訊息的雙通知各送一次
                if (recentMsg(autoFired, msgKey)) { log("「$title」已自動回覆過(重複通知)，略過"); return@bg }
                val sendAction = liveReplyAction(key) ?: action  // 送出前重抓當下還活著的回覆鈕
                val ok = fireReply(this, sendAction, ans)
                lastReplyAt[key] = System.currentTimeMillis()
                if (ok) {
                    lastSentReply[key] = ans  // 記住剛送出的，避免被自己的回覆觸發迴圈
                    autoFired[msgKey] = System.currentTimeMillis()
                    replyTimes.addFirst(System.currentTimeMillis())
                    incDayCount(title)
                    if (Prefs.replyOnce) Prefs.repliedContacts = Prefs.repliedContacts + title
                }
                log(if (ok) "已自動回覆「$title」：$ans" else "送出失敗「$title」")
            } else {
                // 同一則訊息已由「有回覆鈕」的通知自動送出（或正要送出）→ 不要再跳多餘草稿
                if (recentMsg(autoPending, msgKey) || recentMsg(autoFired, msgKey)) {
                    log("「$title」已由自動回覆處理，略過多餘草稿"); return@bg
                }
                when {
                    Prefs.autoSend && !windowOk -> log("「$title」非自動時段，改擬稿")
                    Prefs.autoSend && alreadyOnce -> log("「$title」已自動回過一次，改擬稿交給你")
                    Prefs.autoSend && !dayOk -> log("「$title」今天已達自動回上限，改擬稿")
                    Prefs.autoSend && action == null -> log("「$title」無回覆鈕，改擬稿")
                }
                deliverDraft(this, title, text, ans, action, openIntent)
            }
        }
    }

    private fun latestMessage(ex: Bundle): String? {
        val arr = ex.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val last = arr.lastOrNull() as? Bundle ?: return null
        return last.getCharSequence("text")?.toString()?.trim()
    }

    /**
     * B：關鍵字比對。規則每行「關鍵字=回覆」，關鍵字可逗號多個。
     * 可在關鍵字前加模式標記：[完全] 完全相符、[開頭] 開頭符合、[任意] 任何訊息都回（放最後一行）；無標記＝包含。
     */
    private fun matchKeyword(text: String): String? {
        if (!Prefs.keywordEnabled) return null
        for (raw in Prefs.keywordRules.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val i = line.indexOf('=')
            if (i < 0) continue
            var left = line.substring(0, i).trim()
            val reply = line.substring(i + 1).trim()
            if (reply.isEmpty()) continue
            var mode = 0  // 0 包含 1 完全 2 開頭 3 任意
            when {
                left.startsWith("[任意]") -> { mode = 3 }
                left.startsWith("[完全]") -> { mode = 1; left = left.removePrefix("[完全]").trim() }
                left.startsWith("[開頭]") -> { mode = 2; left = left.removePrefix("[開頭]").trim() }
            }
            if (mode == 3) return reply
            val kws = left.split('，', ',').map { it.trim() }.filter { it.isNotEmpty() }
            if (kws.isEmpty()) continue
            val hit = when (mode) {
                1 -> kws.any { text.equals(it, true) }
                2 -> kws.any { text.startsWith(it, true) }
                else -> kws.any { text.contains(it, true) }
            }
            if (hit) return reply
        }
        return null
    }

    /** 回覆裡的變數替換：{名字}{name}=對方暱稱、{時間}{time}=現在時間、{日期}{date}=今天。 */
    private fun applyVars(s: String, sender: String): String {
        val c = java.util.Calendar.getInstance()
        val hm = String.format("%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
        val ymd = String.format("%d/%02d/%02d", c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
        return s.replace("{名字}", sender).replace("{name}", sender)
            .replace("{時間}", hm).replace("{time}", hm)
            .replace("{日期}", ymd).replace("{date}", ymd)
    }

    /** 前後文：優先用匯入的 LINE 聊天記錄，再補上通知欄抓到的近期訊息。 */
    private fun buildHistory(ctx: android.content.Context, sender: String): String {
        if (!Prefs.useContext) return ""
        val imported = CtxStore.get(ctx, sender)
        val prev = MsgStore.bySender(ctx, sender, 6).drop(1).take(5)  // 新到舊、去掉目前這則
        val live = if (prev.isEmpty()) "" else prev.reversed().joinToString("\n") { "- ${it.text}" }
        return when {
            imported.isNotBlank() && live.isNotBlank() -> "$imported\n$live"
            imported.isNotBlank() -> imported
            else -> live
        }
    }

    /** 急件關鍵字比對。 */
    private fun matchUrgent(text: String): Boolean {
        val kws = Prefs.urgentKeywords.split('，', ',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        return kws.any { text.contains(it, true) }
    }

    /** C：現在是否在「可自動送出」的時段（支援跨午夜）。 */
    private fun inAutoWindow(): Boolean {
        if (!Prefs.hoursEnabled) return true
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val s = Prefs.hoursStart; val e = Prefs.hoursEnd
        return if (s <= e) h in s until e else (h >= s || h < e)
    }

    /** 送出前重新抓目前還在通知列、屬於同一則(key)的即時回覆鈕，避免用到失效的舊按鈕。 */
    private fun liveReplyAction(key: String): Notification.Action? {
        val sbns = runCatching { activeNotifications }.getOrNull() ?: return null
        val sbn = sbns.firstOrNull { it.key == key } ?: return null
        return sbn.notification?.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
    }

    /** 這則訊息(標題+內文)最近是否已被標記/送出（用於雙通知去重）。 */
    private fun recentMsg(m: java.util.concurrent.ConcurrentHashMap<String, Long>, k: String): Boolean {
        val t = m[k] ?: return false
        return System.currentTimeMillis() - t < MSG_DEDUP_MS
    }

    /** E：最近 60 秒內自動送出的次數。 */
    private fun recentReplies(): Int {
        val now = System.currentTimeMillis()
        while (replyTimes.isNotEmpty() && now - (replyTimes.peekLast() ?: now) > 60_000L) replyTimes.pollLast()
        return replyTimes.size
    }

    private fun today(): String =
        java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
    private fun rollDay() { val t = today(); if (t != dayKey) { dayKey = t; dayCount.clear() } }
    private fun dayCountOk(sender: String): Boolean { rollDay(); return (dayCount[sender] ?: 0) < Prefs.maxPerContactPerDay }
    private fun incDayCount(sender: String) { rollDay(); dayCount[sender] = (dayCount[sender] ?: 0) + 1 }

    /** 急件提醒：另外發一則高優先通知給本人。 */
    private fun postUrgent(ctx: android.content.Context, sender: String, text: String, open: PendingIntent?) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_URGENT) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_URGENT, "急件提醒", NotificationManager.IMPORTANCE_HIGH))
        val b = Notification.Builder(ctx, CHANNEL_URGENT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("⚠️ 急件：$sender")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        if (open != null) b.setContentIntent(open)
        nm.notify(900000 + (sender.hashCode() and 0xffff), b.build())
        log("⚠️ 急件提醒：$sender")
    }

    private fun toast(s: String) = App.ui {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val LINE_PKG = "jp.naver.line.android"
        const val CHANNEL = "lineai_draft"
        @Volatile var connected = false
        private val lastHandled = ConcurrentHashMap<String, String>()
        private val lastReplyAt = ConcurrentHashMap<String, Long>()
        private val lastSentReply = ConcurrentHashMap<String, String>()  // 每個對話最後一次自動送出的內容
        private val replyTimes = java.util.concurrent.ConcurrentLinkedDeque<Long>()  // E：自動送出時間戳
        private const val BRAKE_MAX = 6  // 60 秒內自動送出超過此數 → 觸發安全煞車
        const val CHANNEL_URGENT = "lineai_urgent"
        private var dayKey = ""  // 每日計數的日期
        private val dayCount = ConcurrentHashMap<String, Int>()  // 每人今日自動回次數
        private val autoPending = ConcurrentHashMap<String, Long>()  // (標題+內文) → 準備自動送出的時間
        private val autoFired = ConcurrentHashMap<String, Long>()    // (標題+內文) → 已自動送出的時間
        private const val MSG_DEDUP_MS = 20_000L  // 同訊息雙通知去重的時間窗

        // 最近事件（除錯用）
        val events = java.util.concurrent.ConcurrentLinkedDeque<String>()
        private val tf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        fun log(s: String) {
            events.addFirst(tf.format(java.util.Date()) + "  " + s)
            while (events.size > 50) events.pollLast()
        }

        // 擬稿模式：暫存通知自帶的回覆動作，等使用者按「送出」再觸發
        val heldActions = ConcurrentHashMap<Int, Notification.Action>()
        val heldTexts = ConcurrentHashMap<Int, String>()
        val heldOpen = ConcurrentHashMap<Int, PendingIntent>()
        private var seq = 1000

        fun copyTo(ctx: Context, text: String) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("reply", text))
        }

        /** 依設定選擇用懸浮視窗或通知顯示草稿。 */
        fun deliverDraft(ctx: Context, sender: String, incoming: String, draft: String,
                         action: Notification.Action?, openIntent: PendingIntent?) {
            if (Prefs.useOverlay && Overlay.canShow(ctx)) {
                Overlay.show(ctx, sender, incoming, draft, action, openIntent)
                log("已擬稿給「$sender」：$draft（看懸浮視窗）")
            } else {
                postDraft(ctx, sender, draft, action, openIntent)
                log("已擬稿給「$sender」：$draft（看通知）")
            }
        }

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "AI 擬稿", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }

        /** 用通知自帶的 RemoteInput 把文字送出（不開 LINE）。 */
        fun fireReply(ctx: Context, action: Notification.Action, text: String): Boolean {
            val ris = action.remoteInputs ?: return false
            val intent = Intent()
            val bundle = Bundle()
            for (ri in ris) bundle.putCharSequence(ri.resultKey, text)
            RemoteInput.addResultsToIntent(ris, intent, bundle)
            RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
            return runCatching { action.actionIntent.send(ctx, 0, intent) }.isSuccess
        }

        /** 擬稿：發一則自家通知，顯示草稿，附「送出／略過」。無回覆鈕時送出＝複製並開啟 LINE。 */
        fun postDraft(ctx: Context, sender: String, draft: String, action: Notification.Action?, openIntent: PendingIntent?) {
            ensureChannel(ctx)
            val id = seq++
            if (action != null) heldActions[id] = action
            if (openIntent != null) heldOpen[id] = openIntent
            heldTexts[id] = draft
            val send = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, ReplyReceiver::class.java).putExtra("id", id).setAction("send"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val skip = PendingIntent.getBroadcast(
                ctx, id + 100000, Intent(ctx, ReplyReceiver::class.java).putExtra("id", id).setAction("skip"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val sendLabel = if (action != null) "送出" else "複製並開啟 LINE"
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentTitle("AI 擬稿給 $sender")
                .setContentText(draft)
                .setStyle(Notification.BigTextStyle().bigText(draft))
                .addAction(Notification.Action.Builder(null, sendLabel, send).build())
                .addAction(Notification.Action.Builder(null, "略過", skip).build())
                .setAutoCancel(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        }
    }
}
