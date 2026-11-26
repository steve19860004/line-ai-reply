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
        val text = LineListener.heldTexts.remove(id)
        if (intent.action == "send" && action != null && text != null) {
            LineListener.fireReply(ctx, action, text)
        }
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
    }
}
