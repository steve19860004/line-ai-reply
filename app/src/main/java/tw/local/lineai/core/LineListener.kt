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
    }
    override fun onListenerDisconnected() { connected = false }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Prefs.enabled) return
        if (sbn.packageName != LINE_PKG) return
        val n = sbn.notification ?: return
        val ex = n.extras

        // 取最新一句訊息與對話名稱
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return
        val text = latestMessage(ex) ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (text.isBlank()) return

        // 群組過濾
        val isGroup = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        if (isGroup && !Prefs.replyGroups) return

        // 白名單
        if (Prefs.onlyWhitelist && title !in Prefs.whitelist) return

        // 找出通知自帶的「回覆」動作（含 RemoteInput）
        val action = n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() } ?: return

        val key = sbn.key ?: title
        // 去重：同一對話同一句不重複處理
        if (lastHandled[key] == text) return
        lastHandled[key] = text
        // 防洗版：同一對話最短間隔
        val now = System.currentTimeMillis()
        if (now - (lastReplyAt[key] ?: 0) < Prefs.delaySec * 1000L) return

        App.bg {
            try { Thread.sleep(Prefs.delaySec * 1000L) } catch (_: Exception) {}
            if (!Prefs.enabled) return@bg
            val ans = Ai.reply(text, title).getOrElse {
                App.ui { toast("AI 失敗：${it.message}") }; return@bg
            }
            if (ans.isBlank()) return@bg
            if (Prefs.autoSend) {
                fireReply(this, action, ans)
                lastReplyAt[key] = System.currentTimeMillis()
            } else {
                postDraft(this, title, ans, action)
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

        // 擬稿模式：暫存通知自帶的回覆動作，等使用者按「送出」再觸發
        val heldActions = ConcurrentHashMap<Int, Notification.Action>()
        val heldTexts = ConcurrentHashMap<Int, String>()
        private var seq = 1000

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

        /** 擬稿：發一則自家通知，顯示草稿，附「送出／略過」。 */
        fun postDraft(ctx: Context, sender: String, draft: String, action: Notification.Action) {
            ensureChannel(ctx)
            val id = seq++
            heldActions[id] = action
            heldTexts[id] = draft
            val send = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, ReplyReceiver::class.java).putExtra("id", id).setAction("send"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val skip = PendingIntent.getBroadcast(
                ctx, id + 100000, Intent(ctx, ReplyReceiver::class.java).putExtra("id", id).setAction("skip"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentTitle("AI 擬稿給 $sender")
                .setContentText(draft)
                .setStyle(Notification.BigTextStyle().bigText(draft))
                .addAction(Notification.Action.Builder(null, "送出", send).build())
                .addAction(Notification.Action.Builder(null, "略過", skip).build())
                .setAutoCancel(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        }
    }
}
