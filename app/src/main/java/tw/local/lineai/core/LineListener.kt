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
        if (!Prefs.enabled) return
        if (sbn.packageName != LINE_PKG) return
        val n = sbn.notification ?: return
        val ex = n.extras

        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = latestMessage(ex) ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()

        // LINE 會發摘要通知（無標題），略過不吵
        if (title.isNullOrBlank()) return
        // 診斷：只要是有內文的 LINE 通知就先記一筆，方便看到底收到什麼
        val hasAction = n.actions?.any { !it.remoteInputs.isNullOrEmpty() } == true
        if (text.isBlank()) { log("「$title」無文字(貼圖/圖片)，略過"); return }
        log("偵測到「$title」：$text（回覆鈕：${if (hasAction) "有" else "無"}）")

        val isGroup = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        if (isGroup && !Prefs.replyGroups) { log("「$title」是群組，略過"); return }

        // 白名單：模糊比對（名稱有包含即可，不分大小寫）
        if (Prefs.onlyWhitelist && Prefs.whitelist.none { title.contains(it, true) }) {
            log("「$title」不在白名單，略過"); return
        }

        val action = n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
        val openIntent = n.contentIntent  // 開啟該 LINE 對話用（沒有回覆鈕時的備援）

        val key = sbn.key ?: title
        if (text == lastSentReply[key]) return  // 這句正是我們剛送出的回覆（回聲），略過
        if (lastHandled[key] == text) return  // 同一句不重複
        lastHandled[key] = text
        val now = System.currentTimeMillis()
        if (now - (lastReplyAt[key] ?: 0) < Prefs.delaySec * 1000L) { log("「$title」間隔太短，略過"); return }

        log("收到「$title」：$text → AI 處理中…")
        App.bg {
            try { Thread.sleep(Prefs.delaySec * 1000L) } catch (_: Exception) {}
            if (!Prefs.enabled) return@bg
            val ans = Ai.reply(text, title).getOrElse {
                log("AI 失敗：${it.message}"); return@bg
            }
            if (ans.isBlank()) { log("「$title」AI 判斷不回(空回覆)"); return@bg }
            if (Prefs.autoSend && action != null) {
                val ok = fireReply(this, action, ans)
                lastReplyAt[key] = System.currentTimeMillis()
                if (ok) lastSentReply[key] = ans  // 記住剛送出的，避免被自己的回覆觸發迴圈
                log(if (ok) "已自動回覆「$title」：$ans" else "送出失敗「$title」")
            } else {
                if (Prefs.autoSend && action == null) log("「$title」通知無回覆按鈕，無法自動送出，改擬稿")
                deliverDraft(this, title, text, ans, action, openIntent)
            }
        }
    }

    private fun latestMessage(ex: Bundle): String? {
        val arr = ex.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val last = arr.lastOrNull() as? Bundle ?: return null
        return last.getCharSequence("text")?.toString()?.trim()
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

        // 最近事件（除錯用）
        val events = java.util.concurrent.ConcurrentLinkedDeque<String>()
        private val tf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        fun log(s: String) {
            events.addFirst(tf.format(java.util.Date()) + "  " + s)
            while (events.size > 25) events.pollLast()
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
