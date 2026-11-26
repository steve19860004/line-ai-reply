package tw.local.lineai.core

import android.app.Notification
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import tw.local.lineai.App

/** 懸浮視窗草稿卡：顯示對方訊息＋可編輯的 AI 草稿，按送出直接用 LINE 回覆動作發出。 */
object Overlay {
    private var wm: WindowManager? = null
    private var view: View? = null

    fun canShow(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun remove() {
        App.ui {
            view?.let { v -> runCatching { wm?.removeView(v) } }
            view = null; wm = null
        }
    }

    fun show(ctx: Context, sender: String, incoming: String, draft: String, action: Notification.Action) {
        App.ui {
            if (!canShow(ctx)) return@ui
            runCatching { view?.let { wm?.removeView(it) } }
            val dp = { v: Int -> (v * ctx.resources.displayMetrics.density).toInt() }
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(12))
                background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#1E2430")) }
            }
            card.addView(TextView(ctx).apply {
                text = "回覆 $sender"; textSize = 15f; setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            card.addView(TextView(ctx).apply {
                text = "對方：$incoming"; textSize = 12.5f; setTextColor(Color.parseColor("#9AA7B4"))
                setPadding(0, dp(4), 0, dp(6))
            })
            val et = EditText(ctx).apply {
                setText(draft); textSize = 15f; setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2B3340"))
                setPadding(dp(10), dp(8), dp(10), dp(8))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setLines(2)
            }
            card.addView(et, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

            val rowBtns = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
            fun mkBtn(label: String, primary: Boolean, onClick: () -> Unit) = Button(ctx).apply {
                text = label; isAllCaps = false; textSize = 14f
                if (primary) { setBackgroundColor(Color.parseColor("#3A7BD5")); setTextColor(Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
                setOnClickListener { onClick() }
            }
            rowBtns.addView(mkBtn("送出", true) {
                val t = et.text.toString().trim()
                if (t.isNotEmpty()) LineListener.fireReply(ctx, action, t)
                LineListener.log("已送出「$sender」：$t")
                remove()
            })
            rowBtns.addView(mkBtn("重擬", false) {
                et.setText("重擬中…"); et.isEnabled = false
                App.bg {
                    val r = Ai.reply(incoming, sender).getOrDefault(draft)
                    App.ui { et.setText(r.ifBlank { draft }); et.isEnabled = true }
                }
            })
            rowBtns.addView(mkBtn("略過", false) { remove() })
            card.addView(rowBtns)

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM; y = dp(80); width = (ctx.resources.displayMetrics.widthPixels * 0.94).toInt() }

            wm = ctx.getSystemService(WindowManager::class.java)
            view = card
            runCatching { wm?.addView(card, lp) }
        }
    }
}
