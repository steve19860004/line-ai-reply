package tw.local.lineai.core

import android.content.Context

/** 匯入的 LINE 聊天記錄，當某位對方的前後文。key = 對方名稱。 */
object CtxStore {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("lineai_ctx", Context.MODE_PRIVATE)

    fun set(ctx: Context, name: String, history: String) {
        if (name.isBlank()) return
        sp(ctx).edit().putString("c_$name", history).apply()
    }

    /** 找這位對方的前後文：名稱完全相符優先，否則用「包含」比對。 */
    fun get(ctx: Context, sender: String): String {
        val s = sp(ctx)
        s.getString("c_$sender", null)?.let { return it }
        for ((k, v) in s.all) {
            if (k.startsWith("c_")) {
                val n = k.removePrefix("c_")
                if (n.isNotEmpty() && (sender.contains(n, true) || n.contains(sender, true)))
                    return v as? String ?: ""
            }
        }
        return ""
    }

    fun contacts(ctx: Context): List<String> =
        sp(ctx).all.keys.filter { it.startsWith("c_") }.map { it.removePrefix("c_") }.sorted()

    fun clearAll(ctx: Context) {
        val e = sp(ctx).edit()
        for (k in sp(ctx).all.keys) if (k.startsWith("c_")) e.remove(k)
        e.apply()
    }
}
