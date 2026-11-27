package tw.local.lineai.core

/** 解析 LINE 匯出的聊天記錄 .txt（繁中格式）。 */
object LineChatParser {

    data class Result(val name: String, val history: String, val count: Int)

    private val TIME = Regex("""^(上午|下午)?\s*\d{1,2}:\d{2}\t""")
    private val DATE = Regex("""^\d{4}/\d{1,2}/\d{1,2}""")

    /** maxLines：只保留最後幾則當前後文（避免太長）。 */
    fun parse(raw: String, maxLines: Int = 40): Result {
        val lines = raw.replace("\r", "").split('\n')

        // 從開頭幾行找聊天對象名稱
        var name = ""
        for (l in lines.take(4)) {
            val m = Regex("""(?:\[LINE])?\s*(?:與)?(.+?)(?:的)?聊天記錄""").find(l)
            if (m != null) { name = m.groupValues[1].trim(); break }
        }

        val msgs = ArrayList<Pair<String, String>>()  // sender, text
        for (l in lines) {
            if (TIME.containsMatchIn(l)) {
                val parts = l.split('\t')
                if (parts.size >= 3) {
                    val sender = parts[1].trim()
                    val msg = parts.subList(2, parts.size).joinToString("\t").trim()
                    if (msg.isNotEmpty()) msgs.add(sender to msg)
                }
            } else if (msgs.isNotEmpty() && l.isNotBlank() &&
                !DATE.containsMatchIn(l) && !l.contains("聊天記錄") && !l.startsWith("儲存日期")
            ) {
                // 多行訊息的延續行，接到上一則
                val last = msgs.removeAt(msgs.size - 1)
                msgs.add(last.first to (last.second + " " + l.trim()))
            }
        }

        val tail = msgs.takeLast(maxLines)
        val hist = tail.joinToString("\n") { "${it.first}：${it.second}" }
        return Result(name, hist, msgs.size)
    }
}
