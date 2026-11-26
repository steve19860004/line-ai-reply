package tw.local.lineai.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A：訊息備份／收回救回。
 * 把每一則「收到」的 LINE 文字訊息存進本機，對方之後按「收回」，這裡仍留有原文。
 * 只存通知讀得到的文字（貼圖／圖片無內文救不到）。常駐，不受總開關影響。
 */
object MsgStore {
    private const val MAX = 500
    private fun sp(ctx: Context) = ctx.getSharedPreferences("lineai_msgs", Context.MODE_PRIVATE)

    data class Item(val time: Long, val sender: String, val text: String)

    fun add(ctx: Context, sender: String, text: String) {
        val arr = try { JSONArray(sp(ctx).getString("list", "[]")) } catch (_: Exception) { JSONArray() }
        // 去重：若最新一筆與這筆同人同內容（LINE 常對同一訊息發兩則通知），略過
        if (arr.length() > 0) {
            val f = arr.getJSONObject(0)
            if (f.optString("s") == sender && f.optString("m") == text) return
        }
        val out = JSONArray()
        out.put(JSONObject().put("t", System.currentTimeMillis()).put("s", sender).put("m", text))
        var i = 0
        while (i < arr.length() && out.length() < MAX) { out.put(arr.getJSONObject(i)); i++ }
        sp(ctx).edit().putString("list", out.toString()).apply()
    }

    fun recent(ctx: Context, n: Int): List<Item> {
        val arr = try { JSONArray(sp(ctx).getString("list", "[]")) } catch (_: Exception) { JSONArray() }
        val list = ArrayList<Item>()
        var i = 0
        while (i < arr.length() && list.size < n) {
            val o = arr.getJSONObject(i)
            list.add(Item(o.optLong("t"), o.optString("s"), o.optString("m")))
            i++
        }
        return list
    }

    fun count(ctx: Context): Int =
        try { JSONArray(sp(ctx).getString("list", "[]")).length() } catch (_: Exception) { 0 }

    fun clear(ctx: Context) { sp(ctx).edit().remove("list").apply() }
}
