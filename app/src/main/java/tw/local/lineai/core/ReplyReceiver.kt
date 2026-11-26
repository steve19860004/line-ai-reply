package tw.local.lineai.core

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 擬稿通知上的「送出／略過」由此處理。 */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        if (id < 0) return
        val action = LineListener.heldActions.remove(id)
        val open = LineListener.heldOpen.remove(id)
        val text = LineListener.heldTexts.remove(id)
        if (intent.action == "send" && text != null) {
            if (action != null) {
                LineListener.fireReply(ctx, action, text)
            } else {
                // 沒有回覆鈕：複製草稿並開啟該 LINE 對話，讓使用者貼上送出
                LineListener.copyTo(ctx, text)
                runCatching { open?.send() }
                android.widget.Toast.makeText(ctx, "已複製回覆，請在 LINE 長按貼上送出", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
    }
}
