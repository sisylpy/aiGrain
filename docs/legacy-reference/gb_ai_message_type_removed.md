# Legacy：`gb_ai_message_type`（已删除）

旧设计：**`gb_ai_message`** 整数列 **`gb_ai_message_type`**（0 普通 / 1 促销·销售额 / 2 公众号），与已移除的 **`gb_ai_conversation_type`** 同套单智能体分类。

**当前**：列与 `GbAiMessageEntity.gbAiMessageType`、接口 DTO **`messageType`**、`upsertRunScopedMessage` 传参已全部移除。区分消息用 **`role`**、**`run_id`**、**`status`**、`conversation_id`、`content`。

已有库：**`sql/gb_ai_message_drop_legacy_type_column.sql`**（手工执行前确认列存在）。

---

**English.** Retired per-message enum column; semantics live in `role` / `run_id` / `status`.
