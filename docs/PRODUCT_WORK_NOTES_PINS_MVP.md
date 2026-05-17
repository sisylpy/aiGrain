# 工作笔记 / 图钉 / 会话沉淀 MVP — 后端产品设计（V1）

> **文档性质**：产品与数据契约设计，并跟踪落地进度。**后端 Step 1**（两张表 + Pin/WorkNote REST + promote）已实现并通过 smoke，详见 **「十、后端 Step 1 验收结果」**。Harness/SSE/conversation/run **主链路**仍不在本轮改动范围内。
>
> **依据**：当前仓库已存在的 `GbAiConversationEntity`、`AiRunService` / `AiRunController`、`AiRunState`、`GbAiMessageEntity`、`AiConversationMemoryService` / `gb_ai_conversation_turn_memory`、`AiAgentTraceService` / `gb_ai_agent_run` 等行为（详见文末「与现有系统的关系」）。

### V1 表名约定（固定）

- **仅**使用 **`gb_ai_work_pin`**、`**gb_ai_work_note`** 两张业务表；文档、SQL、代码命名均**不要**混用无前缀的 `ai_work_pin`、`ai_work_note`。
- V2 若拆多来源子表，沿用同一前缀风格（例如 `gb_ai_work_note_source`）。

### ID 类型约定（对齐现有项目，不要求改造现有体系）

- **`conversationId`、`runId`、`userId`、`messageId`、`Pin.id`、`WorkNote.id`**：与现有 `GbAi*` 实体一致，**统一为 `Long`**（数据库 `BIGINT`）；**不使用 UUID** 作为这些标识的类型。
- **`sourceType`、`noteType`、`title`、枚举类字符串** 等：Java 侧 **`String`**；库表可用 `VARCHAR`。
- **不要求**改动 `gb_ai_conversation`、`gb_ai_message`、`gb_ai_agent_run` 等既有主键或分配策略。

---

## 一、目标

### V1 MVP 范围（要做）

1. **当前会话内图钉（Pin）**：在同一 `conversationId` 下创建、列出、查看、删除图钉。
2. **当前会话内工作笔记（WorkNote）**：在同一 `conversationId` 下创建（含可选无来源空白笔记）、列表、查看、更新、删除。
3. **从当前 Run 加图钉**：前端携带当前 Run 上下文及 **`sourceTextSnapshot`（必选）**，服务端校验会话归属后落库。
4. **从选中文本加图钉**：`sourceType = SELECTION`，快照为选中段落全文（仍须满足「快照必填」契约）。
5. **从当前 Run 保存为工作笔记**：创建 WorkNote 时写入 **`sourceTextSnapshot`**（从 Run 创建时**必选**），`content` 可由前端预填与快照相同或由用户再编辑。
6. **从单个 Pin 升级为 WorkNote**：固定接口（如 `promote-to-note`），生成 WorkNote，**复制或迁移** Pin 上的快照字段，Pin 可软删除或保留（产品可选，见接口草案）。
7. **从笔记 / 图钉跳回原 conversation / run**：以 **`conversationId` + `runId`（可空）+ `messageId`（可空）** 为 **跳转锚点**；跳转失败（会话不存在、无权限、Run 已无内存态）时 **展示 `sourceTextSnapshot`**，保证用户仍可读证据原文。

### V1 明确不做

- AI 总结、多 Run 合并、批量升级、标签体系、全文搜索、分享协作、版本历史、复杂全局知识库页面、PDF/Markdown 导出。

---

## 二、核心原则

1. **原始对话是证据**：跳转锚点可能失效（进程重启、未持久化的 Run、旧链路无 Run），界面必须以 **`sourceTextSnapshot`** 保留可追溯正文。
2. **`sourceId`（conversationId / runId / messageId）是跳转锚点**：用于「回到会话」「尽力定位到那条气泡」；**不保证**长期可用。
3. **`sourceTextSnapshot` 是防丢失备份**：创建 Pin、以及从 Run/Pin 创建 WorkNote 时 **必填**（或由服务端拒绝）；列表可不返回全文，但库里须有完整快照字段。
4. **`WorkNote.content` 是用户整理后的资产**：Markdown 正文，可与快照不同；用户可在快照基础上改写。
5. **Pin 是临时重点，WorkNote 是长期沉淀**：Pin 用于会话内快速回看；WorkNote 用于沉淀与后续迭代（V2+）。
6. **V1 允许 WorkNote 无来源**：支持手动新建空白笔记（无上文的 Run/Message）；此时 **`sourceTextSnapshot` 可为空**，由 **`noteType` 或等价字段** 标明「手动」。
7. **从 Run / Pin / 选中文本创建时，自动带 `sourceSnapshot`**：前端在「保存瞬间」把当前可见正文写入快照字段；**不得依赖**服务端异步再去补拉 `finalAnswerText`。
8. **标识类型**：见文首「ID 类型约定」：`conversationId` / `runId` / `userId` / `messageId` / 业务主键均为 **`Long`**；扩展展示字段可用 **`String`**；**不使用 UUID**。

### 用户归属（V1）

- **`userId` 是数据归属字段**：Pin / WorkNote 行必须与所有者 `userId` 绑定；列表、详情、更新、删除均需校验「当前操作主体 == 行上的 `userId`」（或与会话归属推导一致）。
- **接口 Request**：V1 可与现有 **`AiRunCreateRequest`**、**`GbAiChatController.SendMessageDTO`** 一样，**暂时由请求体或 Query 传入 `userId`**；后续若项目统一从登录态 / ThreadLocal 解析用户，**改为服务端注入即可**，本文档**不**要求新建一套独立认证机制。
- **实现优先级**：遵循当前工程已有的用户上下文与会话校验方式（例如 **`GbAiChatService#requireConversationOwnedByUser`**）；Pin/Note **旁路新增**，不强行引入新的 Auth 框架。

### 快照与列表契约（V1）

- **`sourceTextSnapshot`**：**完整正文快照**，用于详情展示与跳转失败兜底；库类型建议 **`TEXT` / `LONGTEXT` / `MEDIUMTEXT`**（按正文上限选型，实现阶段与 DBA 确认）。
- **`sourceAnswerPreview`**：**短预览**（列表卡片）；库类型建议 **`VARCHAR(256～512)`** 或必要时 **`TEXT`**，由前端截取或服务端生成。
- **列表接口**：**默认只返回 `sourceAnswerPreview`**，**不返回**完整 `sourceTextSnapshot`（除非显式调试开关，V1 可不开放）。
- **详情接口**：返回完整 **`sourceTextSnapshot`**（及 Pin/Note 其余字段）。

---

## 三、对象设计（概念模型，非 Java 类）

### 1. Pin（图钉）

| 字段 | 类型（概念） | 必填 | 说明 |
|------|--------------|------|------|
| id | Long | 自动 | 主键 |
| userId | Long | 是 | 与 `gb_department_user` / 现有 AI 请求对齐 |
| conversationId | Long | 是 | 会话归属；服务端校验「会话属于该 user」 |
| runId | Long | 否 | Run 锚点；`MESSAGE`/纯会话上下文可能为空 |
| messageId | Long | 否 | 旧链路 `gb_ai_message` 锚点；新 Run 链路常为 null |
| title | String | 否 | 列表标题；可由预览自动生成 |
| sourceType | Enum | 是 | `RUN` \| `MESSAGE` \| `SELECTION` |
| sourceTextSnapshot | String | **是** | **完整快照**（详情与防丢失）；库：**TEXT / LONGTEXT / MEDIUMTEXT** |
| sourceAnswerPreview | String | 建议 | **列表专用短预览**；库：**VARCHAR 或 TEXT**；列表接口默认仅暴露此字段，不暴露全文快照 |
| sourceRole | String | 否 | `user` / `assistant` / `system` |
| sourceAgentName | String | 否 | 便于 UI 展示（若 SSE/agent 名可得） |
| sourceCreatedAt | DateTime | 否 | 源事件发生时间（客户端或服务端时钟） |
| createdAt | DateTime | 是 | 创建时间 |
| updatedAt | DateTime | 是 | 更新时间 |
| deletedFlag / isDeleted | tinyint/boolean | 是 | **软删除**；具体列名 **`gb_ai_wp_deleted`** 或与现有 `GbAi*` 表逻辑删除风格对齐（见第四节） |

**说明（避免误导）**：

- Pin **不要求**单独的来源关联表，但必须 **`sourceTextSnapshot` 必有**：这是对用户的可读证据底线。
- `runId`/`messageId` **应尽量填写**，便于跳转；填不上时仍可用快照兜底。
- 「不要求复杂来源关系表」≠「不要求完整快照」：**快照是 V1 硬契约**。

### 2. WorkNote（工作笔记）

| 字段 | 类型（概念） | 必填 | 说明 |
|------|--------------|------|------|
| id | Long | 自动 | 主键 |
| userId | Long | 是 | 所有者 |
| conversationId | Long | **条件** | 手动全局笔记可为 null；会话内笔记建议必填便于筛选 |
| title | String | 是 | 标题 |
| content | String | 是 | **Markdown** 正文；可为空串是否允许由产品再定（建议最小 MVP 允许 `""`） |
| noteType / sourceType | Enum/String | 建议 | 如 `MANUAL` \| `FROM_RUN` \| `FROM_PIN` \| `FROM_SELECTION` |
| primarySourceType | String | 否 | `RUN` / `MESSAGE` / `SELECTION` / null |
| primaryConversationId | Long | 否 | 跳转主锚点之一 |
| primaryRunId | Long | 否 | |
| primaryMessageId | Long | 否 | |
| sourceTextSnapshot | String | **条件必填** | **手动新建（无来源）可为 null/省略**；**从 Run / Pin / Selection 创建时必须非空**（服务端校验） |
| sourceAnswerPreview | String | 否 | 列表预览；库 VARCHAR/TEXT |
| createdAt | DateTime | 是 | |
| updatedAt | DateTime | 是 | |
| deletedFlag / isDeleted | | 是 | **软删除**；列名建议 **`gb_ai_wn_deleted`**，风格与 Pin 表一致 |

**原则**：不强制 `primaryConversationId` / `primaryRunId` 必填；有来源时写满以利于跳转。**手动笔记**：`noteType=MANUAL` 时可无 `primary*`、可无 `sourceTextSnapshot`。**有来源创建**：必须带 **`sourceTextSnapshot`**。

### 3. SourceSnapshot（嵌入语义）

V1 **可作为 Pin / WorkNote 表上的扁平字段集合**，不必单独表。

| 逻辑分组字段 | 说明 |
|--------------|------|
| sourceType | 与 Pin.sourceType 或笔记来源一致 |
| conversationId | |
| runId | |
| messageId | |
| sourceTitle | 可选，便于跳转 UI |
| sourceTextSnapshot | 正文快照 |
| sourceAnswerPreview | 列表预览 |
| sourceCreatedAt | |
| sourceRole | |
| sourceAgentName | |

实现时直接映射到表列即可；Promote 时从 Pin 行拷贝到 WorkNote 行。

---

## 四、建议数据库表（仅建议，本轮不执行 SQL）

### 命名风格

与现有 AI 表一致：**`gb_ai_*`**（如 `gb_ai_conversation`、`gb_ai_message`、`gb_ai_agent_run`）。

**V1 仅两张表**：`**gb_ai_work_pin**`、`**gb_ai_work_note**`（全文统一此命名，无别名）。

### 表 1：`gb_ai_work_pin`

| 列（建议） | 类型建议 | 说明 |
|------------|-----------|------|
| gb_ai_wp_id | BIGINT PK AUTO_INCREMENT | |
| gb_ai_wp_user_id | BIGINT NOT NULL | |
| gb_ai_wp_conversation_id | BIGINT NOT NULL | |
| gb_ai_wp_run_id | BIGINT NULL | |
| gb_ai_wp_message_id | BIGINT NULL | |
| gb_ai_wp_title | VARCHAR(512) NULL | |
| gb_ai_wp_source_type | VARCHAR(32) NOT NULL | RUN / MESSAGE / SELECTION |
| gb_ai_wp_source_text_snapshot | LONGTEXT 或 MEDIUMTEXT NOT NULL | **完整快照**（详情 / 防丢失） |
| gb_ai_wp_source_answer_preview | VARCHAR(512) NULL（或 TEXT） | **列表预览**，非全文 |
| gb_ai_wp_source_role | VARCHAR(32) NULL | |
| gb_ai_wp_source_agent_name | VARCHAR(128) NULL | |
| gb_ai_wp_source_created_at | DATETIME NULL | |
| gb_ai_wp_created_at | DATETIME NOT NULL | |
| gb_ai_wp_updated_at | DATETIME NOT NULL | |
| gb_ai_wp_deleted | TINYINT(1) NOT NULL DEFAULT 0 | 软删除 |

**索引建议**：

- `(gb_ai_wp_user_id, gb_ai_wp_conversation_id, gb_ai_wp_deleted, gb_ai_wp_created_at DESC)` — 会话下列 Pin。
- 可选 `(gb_ai_wp_run_id)` — 按 Run 反查（调试或冲突排查）。

### 表 2：`gb_ai_work_note`

| 列（建议） | 类型建议 | 说明 |
|------------|-----------|------|
| gb_ai_wn_id | BIGINT PK AUTO_INCREMENT | |
| gb_ai_wn_user_id | BIGINT NOT NULL | |
| gb_ai_wn_conversation_id | BIGINT NULL | 手动笔记可空 |
| gb_ai_wn_title | VARCHAR(512) NOT NULL | |
| gb_ai_wn_content_md | MEDIUMTEXT NOT NULL | Markdown |
| gb_ai_wn_note_type | VARCHAR(32) NOT NULL | MANUAL / FROM_RUN / FROM_PIN / … |
| gb_ai_wn_primary_source_type | VARCHAR(32) NULL | |
| gb_ai_wn_primary_conversation_id | BIGINT NULL | |
| gb_ai_wn_primary_run_id | BIGINT NULL | |
| gb_ai_wn_primary_message_id | BIGINT NULL | |
| gb_ai_wn_source_text_snapshot | LONGTEXT 或 MEDIUMTEXT NULL | **有来源创建时 NOT NULL**；手动笔记可 NULL |
| gb_ai_wn_source_answer_preview | VARCHAR(512) NULL（或 TEXT） | **列表预览** |
| gb_ai_wn_created_at | DATETIME NOT NULL | |
| gb_ai_wn_updated_at | DATETIME NOT NULL | |
| gb_ai_wn_deleted | TINYINT(1) NOT NULL DEFAULT 0 | |

**索引建议**：

- `(gb_ai_wn_user_id, gb_ai_wn_conversation_id, gb_ai_wn_deleted, gb_ai_wn_updated_at DESC)` — conversationId 为 null 时查询需注意：**`(gb_ai_wn_user_id, gb_ai_wn_deleted)`** 辅索引。
- 可选 `(gb_ai_wn_primary_run_id)`。

### 软删除

- **`DELETE /api/ai/work-pins/{id}`**、**`DELETE /api/ai/work-notes/{id}`** 语义均为 **软删除**（**非物理删行**）。
- 字段实现：建议 **`gb_ai_wp_deleted` / `gb_ai_wn_deleted`**（`TINYINT(1)`，`0` 有效、`1` 已删）；若项目其它 `GbAi*` 表已统一用 **`status`** 枚举表达删除，**实现阶段对齐现有惯例**，本文不强制改名。

### 为何 V1 不单独建 `note_source` 表

- 每条 Pin **单一来源**；每条 WorkNote V1 **仅 primary 一组锚点 + 一份快照** 足够。
- 单独来源表会增加 JOIN、事务边界与「哪一行是展示快照」的歧义，**不利于 MVP 交付**。

### V2 多来源时再拆 `gb_ai_work_note_source`

- 多文档引用、批量引用同一 Run 不同段落、跨会话汇编时再引入 **`note_id + ordinal + snapshot`** 子表；V1 不预埋复杂度。

---

## 五、后端接口草案（V1 最小集）

前缀：**`/api`**（与现有 `context-path` 一致）；以下为 **`/api/ai/...`**。

### Pin

#### 1. `POST /api/ai/work-pins`

| 项 | 内容 |
|----|------|
| 用途 | 新建 Pin |
| Request（JSON） | `userId?`（与现有 AI 接口一致可暂传，最终以服务端用户上下文为准）, `conversationId`, `runId?`, `messageId?`, `title?`, `sourceType`, **`sourceTextSnapshot`（必填）**, `sourceAnswerPreview?`, `sourceRole?`, `sourceAgentName?`, `sourceCreatedAt?` |
| Response | `id` + 持久化后的完整 DTO（或标准 `R` Map） |
| MVP 必须 | **是** |
| sourceTextSnapshot | **必填**；缺失 → **400** |

#### 2. `GET /api/ai/work-pins?conversationId={id}`

| 项 | 内容 |
|----|------|
| 用途 | 当前会话 Pin 列表 |
| Request | Query：`conversationId`；**默认 `includeSnapshot=false`**（或不支持 include，列表永不返回全文） |
| Response | `items[]`：`id`, `title`, `sourceType`, **`sourceAnswerPreview`**, `runId`, `messageId`, `createdAt`, … — **默认不含 `sourceTextSnapshot`** |
| MVP 必须 | **是** |
| sourceTextSnapshot | **列表默认不返回**；仅 **`GET .../work-pins/{id}`** 返回全文快照 |

#### 3. `GET /api/ai/work-pins/{id}`

| 项 | 内容 |
|----|------|
| 用途 | Pin 详情（含 **`sourceTextSnapshot` 全文**） |
| Response | 完整字段 |
| MVP 必须 | **是** |
| sourceTextSnapshot | **返回** |

#### 4. `DELETE /api/ai/work-pins/{id}`

| 项 | 内容 |
|----|------|
| 用途 | **软删除** Pin（置 `deleted`/等价状态，保留行便于审计） |
| MVP 必须 | **是** |

#### 5. `POST /api/ai/work-pins/{id}/promote-to-note`

| 项 | 内容 |
|----|------|
| 用途 | **单个** Pin → WorkNote：拷贝快照与锚点到笔记，`noteType=FROM_PIN` |
| 约束 | **V1 仅支持一次处理一个 Pin**；**不支持**批量 promote、**不支持**多 Pin 合并为一篇笔记 |
| Request | `title?`（默认沿用 Pin）、`content?`（默认可复制快照或留空由前端传） |
| Response | 新建 `workNoteId`（`Long`）+ Note DTO |
| MVP 必须 | **是** |
| sourceTextSnapshot | **从 Pin 完整复制**，服务端不得清空 |

### WorkNote

#### 1. `POST /api/ai/work-notes`

| 项 | 内容 |
|----|------|
| 用途 | 新建笔记（手动或来自 Run/Pin/Selection；Pin 升级优先走 promote 接口） |
| Request | `userId?`（与现有接口一致可暂传）、`conversationId?`, `title`, `content`（Markdown）, `noteType`, `primary*` 可选, **`sourceTextSnapshot`**：`MANUAL` 可省略；**`FROM_RUN` / `FROM_PIN` / `FROM_SELECTION` 必填非空** |
| Response | `id` + DTO |
| MVP 必须 | **是** |
| sourceTextSnapshot | **有条件必填**（见第二节原则） |

#### 2. `GET /api/ai/work-notes?conversationId={id}`

| 项 | 内容 |
|----|------|
| 用途 | 某会话下笔记列表 |
| Request | `conversationId`；可选 `includeManualWithoutConversation=false` |
| Response | 列表项：**`title` + `sourceAnswerPreview`**（若有）；**默认不包含**完整 **`sourceTextSnapshot`** |
| MVP 必须 | **是** |
| sourceTextSnapshot | **列表默认不返回**；仅详情返回 |

#### 3. `GET /api/ai/work-notes/{id}`

| 项 | 内容 |
|----|------|
| 用途 | 笔记详情（**content + snapshot**） |
| MVP 必须 | **是** |

#### 4. `PUT /api/ai/work-notes/{id}`

| 项 | 内容 |
|----|------|
| 用途 | 更新标题、`content`；**不建议 V1 允许改 snapshot**（保持证据immutable）；若产品允许「更正错别字」，需单独字段审计策略 |
| MVP 必须 | **是** |

#### 5. `DELETE /api/ai/work-notes/{id}`

| 项 | 内容 |
|----|------|
| 用途 | **软删除**笔记 |
| MVP 必须 | **是** |

**通用校验**：

- **归属**：行级 **`userId`** 必须与当前请求用户一致；会话维度可继续复用 **`GbAiChatService#requireConversationOwnedByUser`**（当 `conversationId` 非空时）。**`conversationId` 为 null** 的笔记仅允许所有者本人的 **`MANUAL`** 类型。
- **严禁**仅凭 `runId` 写入而无快照（Run / Selection 路径）。
- **`userId` 传递**：V1 可与 **`POST /api/ai/runs`** 相同由客户端传 `userId`，后续改为网关/会话解析即可，**不新建认证体系**。

---

## 六、和现有 conversation / run 的关系（代码现状）

**V1 硬边界（再强调）**：

- **不修改** `gb_ai_conversation`、`gb_ai_agent_run` 表结构及现有写入路径；**不修改** SSE、`AiRunSession`、`AiRunService` 主执行链、Harness Replay 契约。
- Pin / WorkNote **独立落库**；保存时 **必须由前端**在调用 POST 的瞬间提交 **`sourceTextSnapshot`**（来自当前页面已缓冲的正文），服务端**不**承担异步补拉全文职责。

1. **`conversationId` 来源**  
   - 表：`gb_ai_conversation.gb_ai_conversation_id`。  
   - API：**`POST /api/ai/runs`** 首轮可不传，服务端 `GbAiChatService#createNewConversationForAgentRun` 插入后在响应体返回；后续轮须传同一 id。**`POST /api/ai/chat/conversation`** 也会创建/恢复会话。

2. **`runId` 来源**  
   - **`AiRunSessionRegistry#nextRunId()`** 分配；**`POST /api/ai/runs`** 响应体 **`runId`**。  
   - **`GET /api/ai/runs/{runId}`** 仅能通过内存 Session 查看近期 Run。

3. **`finalAnswerText` 是否稳定落库**  
   - **否**。权威在 **`AiRunState.finalAnswerText`** 与 SSE **`answer_delta`**；DB 侧 **`gb_ai_conversation_turn_memory`** 仅有 **`trimSummary` 后约 200 字的 `answer_summary`**，不能当作全文。**不能把跳转全文托付给 Run 记录或 turn memory**。

4. **为何 V1 不改 `conversation` / `run` 表**  
   - 会话与 Run 已有清晰边界；笔记与钉是 **用户生成内容**，独立生命周期与索引更合适；避免耦合 Harness Trace、`intent` JSON 等运维字段。

5. **为何 V1 不改 SSE / Harness / 主链路**  
   - 主链路复杂度高且已通过 Harness 收口；Pin/Note 为「并行产品线」，仅依赖 REST + 前端快照即可闭环；服务端异步回填快照会与 **`persist-enabled`**、进程生命周期耦合，**违背原则 7**。

6. **前端保存时必须携带 `sourceTextSnapshot`**  
   - 在 **`answer_delta` / `run_finished`** 合并得到完整正文后、在用户点击钉/保存的一刻写入请求体；若正文分段到达，须在客户端缓冲拼接后再提交。

---

## 七、后端包位置建议（与现有结构对齐）

| 层级 | 建议路径 | 说明 |
|------|----------|------|
| Controller | `com.nongxinle.controller` | 与 `AiRunController`、`GbAiChatController` 并列；可 **`GbAiWorkPinController` / `GbAiWorkNoteController`** 或统一 **`AiWorkspaceController`** 分两 resource |
| DTO（请求/响应） | `com.nongxinle.ai.platform.dto` 或新建 `com.nongxinle.ai.workspace.dto` | 现有 Run DTO 在 `ai.platform.dto` |
| Entity | `com.nongxinle.entity` | 与 `GbAiConversationEntity`、`GbAiMessageEntity` 一致 |
| Mapper 接口 | `com.nongxinle.mapper` | MyBatis-Plus `BaseMapper` |
| Service | `com.nongxinle.service` + `impl` **或** `com.nongxinle.ai.workspace` | 若逻辑薄可走前者；跨会话校验多时专属包更清晰 |
| Mapper XML | `src/main/resources/mapper/` | 若有自定义 SQL：`GbAiWorkPinMapper.xml`、`GbAiWorkNoteMapper.xml` |

---

## 八、风险

1. **前端拿不到完整 `finalAnswerText`**：SSE 中断、过早点击保存 → **`sourceTextSnapshot` 为空或残缺**。缓解：禁用按钮直至 `run_finished` 或本地缓冲就绪；服务端校验非空白 MEDIUMTEXT。
2. **只存 `runId` 不存 snapshot**：进程重启或会话过期后 **永久丢失正文**。**禁止**作为唯一证据。
3. **第一版多来源**：徒增关联表与 UI，延误 MVP。**V1 单一 primary + 单快照**。
4. **Pin 与 WorkNote 混成一类**：字段语义打架（临时 vs 长期、`content` vs 快照）。**保持两张表 / 两套 API**。
5. **列表返回全文快照**：大字段拖垮带宽与渲染。**列表必须用 `sourceAnswerPreview`**；详情再拉全文。

---

## 九、第一版后端代码落地计划

| Step | 内容 | 状态 |
|------|------|------|
| **Step 1** | 建表 SQL：**仅** `gb_ai_work_pin`、`gb_ai_work_note`；Entity / DTO / Mapper / Service / Controller；Pin CRUD、WorkNote CRUD、单 Pin **`promote-to-note`**；会话归属校验复用 **`GbAiChatService#requireConversationOwnedByUser`**（适用路径） | **已完成**（验收见 **第十节**） |
| **Step 2（前端）** | 当前会话右侧栏接入 Pin / WorkNote（列表 / 详情 / 创建 / 更新 / 删除 / promote）；Run/SSE 缓冲完成后提交快照 | **待办** |

其余历史拆分（Entity/DTO、Mapper、Controller 分步）已在 Step 1 一并交付，不再单列。

---

## 十、后端 Step 1 验收结果

### 10.1 Smoke test

**结论**：后端 Step 1 **smoke test 已通过**（手工联调，非自动化测试套件）。

### 10.2 已落地接口（`context-path=/api` 前缀）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/ai/work-pins` | 创建图钉 |
| `GET` | `/api/ai/work-pins` | 列表（Query：`conversationId`、`userId`） |
| `GET` | `/api/ai/work-pins/{id}` | 详情（Query：`userId`） |
| `DELETE` | `/api/ai/work-pins/{id}` | 软删除 Pin（Query：`userId`） |
| `POST` | `/api/ai/work-pins/{id}/promote-to-note` | 单 Pin 升级为 WorkNote（Body：`userId` 等） |
| `POST` | `/api/ai/work-notes` | 创建工作笔记 |
| `GET` | `/api/ai/work-notes` | 列表（Query：`conversationId`、`userId`） |
| `GET` | `/api/ai/work-notes/{id}` | 详情（Query：`userId`） |
| `PUT` | `/api/ai/work-notes/{id}` | 更新笔记（Body 含 `userId`） |
| `DELETE` | `/api/ai/work-notes/{id}` | 软删除笔记（Query：`userId`） |

验证记录中与文档口径一致的抽样：**POST 创建 Pin**、**GET 列表/详情**、**promote-to-note**、**WorkNote 列表/详情/PUT**、**DELETE Pin/Note** 均符合预期。

### 10.3 关键契约（已验证）

- **列表**响应：**不返回**完整 **`sourceTextSnapshot`**（仅预览等字段）。
- **详情**响应：**返回** **`sourceTextSnapshot`**。
- **`POST …/promote-to-note`**：成功生成 **WorkNote**；默认**保留**原 Pin（本次验收末尾曾对测试 Pin 做过手动删除，与「promote 不自动删 Pin」产品规则不冲突）。
- **`DELETE`**：**软删除**（`gb_ai_wp_deleted` / `gb_ai_wn_deleted`）；删除后列表不再出现对应 id。

### 10.4 下一步

1. **前端 Step 2**：在当前会话 **右侧栏** 接入 **Pin / WorkNote**（列表、详情、创建、编辑、删除、单 Pin promote），保存瞬间继续由前端提交 **`sourceTextSnapshot`**，与本文原则一致。
2. **仍不在本轮范围**：**AI 总结**、**标签**、**全文搜索**、**多来源子表（note_source）**、批量 promote、改动 Harness/SSE/会话 Run 主链路（与文首硬边界一致）。

---

## 附录：与现有系统的简要映射（便于评审）

| 概念 | 代码/SQL 参考 |
|------|----------------|
| 会话 | `GbAiConversationEntity`、`GbAiChatService` |
| Run | `AiRunController`、`AiRunService`、`AiRunState`、`AiRunSessionRegistry` |
| 聊天消息（旧链路） | `GbAiMessageEntity`、`GbAiChatController` |
| 非全文「上轮语义」 | `gb_ai_conversation_turn_memory`、`AiConversationMemoryService` |
| Run Trace | `gb_ai_agent_run`、`AiAgentTraceService` |

本文档版本：**V1 设计稿 + 后端 Step 1 验收记录**。后续实现若有字段或接口变更，须同步更新本节与 OpenAPI/Swagger 描述。
