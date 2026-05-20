# Legacy：`gb_ai_conversation_type`（已删除）

旧设计：表 **`gb_ai_conversation`** 上的整数列 **`gb_ai_conversation_type`**（0 普通聊天 / 1 促销活动·销售额 / 2 公众号相关）。

**当前**：列与 `GbAiConversationEntity.gbAiConversationType` 已从代码移除；会话语义用 **`thread_kind`**、`advisor_id`、`scope_mode`、`last_intent`、`last_path` 及 **`gb_ai_message`** 的 **`role` / `run_id` / `status`**。已有库执行 **`sql/gb_ai_conversation_drop_legacy_type_column.sql`**。消息表同源分类列见 **`docs/legacy-reference/gb_ai_message_type_removed.md`**、**`sql/gb_ai_message_drop_legacy_type_column.sql`**。

---

**English.** Retired integer column on `gb_ai_conversation`. Removed from Java; use `thread_kind`, advisor/scope fields, last intent/path, and per-message `role`/`run_id` instead.
