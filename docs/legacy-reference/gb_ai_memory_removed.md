# gb_ai_memory 与 gb_ai_message_memory_extracted（已移除）

- **gb_ai_memory** / **GbAiMemoryService**：原单 Agent 关会话摘要与「记忆点」落库（含 `gb_ai_memory_type` 0/1/2）。入口 `POST /api/ai/chat/end` 已删，当前代码无 `@Autowired`/调用方。
- **gb_ai_message_memory_extracted**：仅由上述服务在抽取后批量标记消息；与新 Harness、`AiConversationTurnMemory`、`AiFollowUpIntentSnapshot` 无关。

**现存库**：依次执行（按需）

- `sql/gb_ai_memory_drop_legacy_table.sql`
- `sql/gb_ai_message_drop_legacy_memory_extracted_column.sql`

绿场请参考 `beData/ai_marketing.sql`（已不含上述结构与列）。
