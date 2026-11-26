# LINE AI 回覆（LineAiReply）

免 root 的手機端 LINE 自動回覆：讀到 LINE 新訊息 → 交給雲端 AI 產生回覆 → 用通知自帶的「回覆」動作（RemoteInput）直接送出，**不用打開 LINE**。

## 運作方式
1. **通知讀取權限** 讀到 LINE 新訊息（需 LINE 開通知且開「顯示訊息內容」）。
2. 交給 **AI**（Gemini 或 OpenAI 相容端點，你提供金鑰）依你的語氣產生回覆。
3. 兩種模式：
   - **全自動送出**：直接用 RemoteInput 回覆。
   - **擬稿待確認**：AI 擬好草稿發成通知，你按「送出」才發（較安全）。

可設白名單、群組是否回、防洗版延遲。

## 注意
- 不需要 Shizuku／root。
- 對個人 LINE 帳號自動代回可能違反 LINE 服務條款，請自行評估、保守使用。
- 本 App 只在本機處理；訊息內容會送到你設定的 AI 供應商以取得回覆。

## 下載
到 [Releases](../../releases/latest) 下載 `LineAiReply.apk`。
