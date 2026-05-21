# 前台接口契约文档（后端确认版）

> 本文档以后端实际代码为准，确认前台 API 清单中的 46 个接口的真实契约。
> 不改业务代码，仅整理接口契约。
> 更新日期：2026-05-21

---

## 一、AI Run / 聊天 / SSE

### 1. POST /ai/runs — 创建 Run

- **方法**：POST
- **路径**：`/api/ai/runs`
- **Controller**：`AiRunController.java:40`
- **Service**：`AiRunService.startRun()`
- **前台调用场景**：`chatStore.sendUserMessage`、`ConsultantsView` 发起聊天
- **请求参数**（`AiRunCreateRequest.java`）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | Long | 是 | gb_department_user 主键 |
| `message` | String | 是 | 用户自然语言输入 |
| `conversationId` | Long | 否 | 首轮不传，服务端创建后返回；后续轮须原样回传 |
| `advisorId` | Long | 否 | 顾问页发起时传入，用于 Run 归因 |
| `departmentId` | Long | 否 | 单店门店父部门 ID |
| `distributerId` | Long | 否 | 集团配送商 disId |
| `scopeMode` | String | 否 | `STORE` / `GROUP`；不传则根据 departmentId/distributerId 推断 |
| `roleCode` | String | 否 | 默认不传；正式链路以 gb_du_admin → AiRoleMapper 为准 |

- **响应结构**：

```json
{
  "code": 0,
  "runId": 1778344855542,
  "conversationId": 123,
  "status": "STARTED"
}
```

- **关键字段说明**：
  - `runId` — 用于订阅 SSE 和查询 Run 详情
  - `conversationId` — 后续轮次必须回传
- **字段稳定性**：stable
- **可能为空的字段**：conversationId（首轮不传时服务端创建并返回）
- **错误返回**：`{ "code": 400, "msg": "错误描述" }`
- **后台确认结论**：稳定接口，主链路入口
- **待补字段**：无

---

### 2. GET /ai/runs/{runId} — 查询 Run 状态

- **方法**：GET
- **路径**：`/api/ai/runs/{runId}`
- **Controller**：`AiRunController.java:57`
- **Service**：`AiRunSessionRegistry.get()` + `AiHarnessResolvedContextSummarizer.summarize()`
- **前台调用场景**：`chatStore`（SSE run_finished 后自动调用；页面恢复时 `checkAndRecoverPendingRun`）
- **请求参数**：URL path `runId`（Long）
- **响应结构**：

```json
{
  "code": 0,
  "runId": 1778344855542,
  "advisorId": 1,
  "status": "COMPLETED",
  "workspaceMode": "BUSINESS_CHAT",
  "cancelled": false,
  "answerPreview": "前500字截断...",
  "harnessDebug": {
    "debugContextEnabled": true,
    "resolvedQueryContextPresent": true,
    "resolvedQueryContextSummary": { /* 完整摘要 Map */ },
    "structuredIntentDetail": "SUPPLIER_AMOUNT_RANKING",
    "structuredIntentDetailWire": "...",
    "structuredIntentDetailCode": "...",
    "structuredIntentDetailPresent": true,
    "conversationId": 123,
    "runId": 1778344855542,
    "advisorId": 1,
    "effectiveIntentCode": "COST_INSIGHT",
    "effectivePathCode": "cost_insight_path",
    "effectiveTimeWindowSource": "CURRENT_MONTH",
    "startDate": "2026-05-01",
    "endDate": "2026-05-21",
    "scopeType": "GROUP",
    "visibleStores": [{ "storeDepartmentId": 1, "storeName": "AAA" }],
    "queryScopeKind": "STORE",
    "queryStoreIds": [1, 2],
    "queryRealDepartmentIds": [101, 102],
    "queryDistributerId": 2,
    "planSource": "activeAnswerPlan",
    "consumedAnswerPlans": ["COST_INSIGHT"],
    "missingAnswerPlans": [],
    "diagnosisPlan": {},
    "diagnosisPlanPresent": false,
    "diagnosisPlanType": null,
    "diagnosisRiskLevel": null,
    "diagnosisDataCompleteness": null,
    "...": "..."
  }
}
```

- **关键字段说明**：
  - 响应体 **不**包在 `data` 内，`harnessDebug` 在顶层
  - `harnessDebug` 仅在 `ai.harness.debug-context-enabled=true`（正式环境默认 false，仅联调开启）时有内容
  - `harnessDebug.debugContextEnabled` — 始终返回的布尔位（反映配置开关状态）
  - `harnessDebug.resolvedQueryContextPresent` — true 时表示有摘要数据
  - `status` — `COMPLETED` / `RUNNING` / `FAILED` / `CANCEL_REQUESTED` 等（大写英文）
- **字段稳定性**：debug-only（`harnessDebug` 内所有字段为非稳定契约，仅调试用）；`status`/`runId` 为 stable
- **可能为空的字段**：`harnessDebug` 内所有调试字段当开关关闭或 Run 未完成时为空
- **错误返回**：404 `{ "error": "Not Found", "message": "run not found" }`（ResponseStatusException）
- **后台确认结论**：
  - 响应外层的 `R` 是 Spring `R`（继承 LinkedHashMap），顶层字段与 `put()` 一致
  - `harnessDebug` 是**调试专用**，不应用于前台业务判断
  - 前台 `unwrapAiRunDetailBody` 的 3 层剥壳（`data`/`result`）在当前版本**不需要**——`harnessDebug` 直接在响应顶层
- **待补字段**：无

---

### 3. GET /ai/runs/{runId}/events — SSE 事件流

- **方法**：GET
- **路径**：`/api/ai/runs/{runId}/events`
- **Controller**：`AiRunController.java:185`
- **Service**：`AiRunSession.subscribe(SseEmitter)`
- **前台调用场景**：`chatStore.connectAiSse` 流式回答
- **请求参数**：URL path `runId`（Long）
- **SSE 事件清单**（详见 `docs/SSE_BACKEND_EVENT_CONTRACT.md`）：

| 事件名 | 关键字段 | 用途 |
|--------|---------|------|
| `run_started` | `event, runId, timestamp, status, displayText` | 步骤时间线 |
| `agent_started` | `event, runId, agent, timestamp, displayText` | 步骤时间线 |
| `agent_finished` | `event, runId, agent, timestamp, displayText, workspaceMode` | 步骤时间线 |
| `tool_started` | `event, runId, tool, timestamp, displayText` | 步骤时间线 |
| `tool_finished` | `event, runId, tool, success, skipped, timestamp, displayText` | 步骤时间线 |
| `review_started` | `event, runId, agent, timestamp, displayText` | 步骤时间线 |
| `review_finished` | `event, runId, agent, passed, score, timestamp, displayText` | 步骤时间线 |
| `answer_delta` | `event, runId, data.text, data.costDiagnosis, data.purchaseAnswerPlan, data.revenueAnswerPlan, data.dishProfitOverview, data.warehouseOverview, data.resolvedQueryContextSummary` | **核心**：流式正文 |
| `run_finished` | `event, runId, status("completed"/"cancelled"/"failed"), displayText, data: {}` | 触发 GET run 详情 + 关流 |
| `error` | `event, runId, displayText, message, type, data.errorCode, data.permissionDenied` | 错误/权限提示 |

- **SSE 信封格式**（所有事件共通）：

```json
{
  "event": "answer_delta",
  "runId": 1778344855542,
  "timestamp": "2026-05-10T00:42:54.546372+08:00",
  "status": "running",
  "displayText": "回答生成中",
  "data": {
    "text": "正文...",
    "costDiagnosis": {},
    "resolvedQueryContextSummary": {}
  },
  "agent": "...",
  "tool": "..."
}
```

- **关键字段说明**：
  - `answer_delta.data.text` — **唯一正文源**（`displayText` 仅是过程提示"回答生成中"）
  - `answer_delta.data.costDiagnosis` — 成本诊断卡片（仅成本主线）
  - `answer_delta.data.resolvedQueryContextSummary` — 调试摘要（与 GET run 的 `harnessDebug.resolvedQueryContextSummary` 同源）
  - `answer_delta.data.purchaseAnswerPlan` / `revenueAnswerPlan` — 回答计划
  - `answer_delta.data.dishProfitOverview` — 菜品毛利卡片
  - `answer_delta.data.warehouseOverview` — 库存概览卡片
- **字段稳定性**：
  - `event, runId, timestamp, status, displayText` — stable
  - `data.text` — stable
  - `data.costDiagnosis` — stable（成本主线）
  - `data.resolvedQueryContextSummary` — debug-only
  - `data.purchaseAnswerPlan` / `revenueAnswerPlan` / `dishProfitOverview` / `warehouseOverview` — stable（对应业务路径）
- **可能为空的字段**：`data` 内除 `text` 外均为可选；`agent`/`tool` 按事件类型存在
- **后台确认结论**：
  - 命名 SSE 事件必须用 `addEventListener` 监听，`onmessage` 收不到
  - `answer_delta` 当前为 **单条整块**（非打字机增量），`data.text` 为完整正文
  - 后端 **不发** `data.delta` / `data.chunk` / `data.content`
  - `run_finished.status` 成功为 `"completed"`（不是 `"success"`）
  - **SSE 重连**：后端不保存事件缓冲区，重连后仅接收新事件，不重放历史事件。Run 在服务端继续执行不受影响
- **待补字段**：无

---

### 4. POST /ai/runs/{runId}/stop — 停止 Run

- **方法**：POST
- **路径**：`/api/ai/runs/{runId}/stop`
- **Controller**：`AiRunController.java:196`
- **Service**：`AiRunService.stopRun()`
- **前台调用场景**：`chatStore.stopGeneratingByUser`
- **请求参数**：URL path `runId`
- **响应结构**：

```json
{
  "code": 0,
  "runId": 1778344855542,
  "status": "CANCEL_REQUESTED"
}
```

- **字段稳定性**：stable
- **后台确认结论**：协作式取消，节点间检测 `cancelled` 标志后提前结束
- **待补字段**：无

---

### 5. POST /ai/messages — 直接发送消息（legacy）

- **方法**：POST
- **路径**：`/api/ai/messages`
- **Controller**：**不存在**
- **后台确认结论**：此接口在后端 **未实现**。前台代码中 `aiApi.sendAiMessage` 未被主链路调用，确认为遗留前端代码。主链路使用 `POST /ai/runs` + SSE。
- **建议**：前台可清理此调用（不再需要的遗留接口）
- **待补字段**：不需要补

---

### 6-7. POST/DELETE /ai/messages/{messageId}/pin — 消息图钉

- **方法**：POST / DELETE
- **路径**：`/api/ai/messages/{messageId}/pin`
- **Controller**：`AiMessagePinController.java:35,48`
- **Service**：`GbAiWorkPinService.pinAssistantMessage()` / `unpinAssistantMessage()`
- **前台调用场景**：`MessageRow.vue` 对单条消息添加/取消图钉
- **请求参数**：
  - URL path `messageId`（Long）
  - Query `userId`（Long）
- **响应结构**（`AiMessagePinResponseDTO`）：

```json
{
  "code": 0,
  "data": {
    "pinned": true,
    "pinId": 456,
    "duplicated": false
  }
}
```

- **字段稳定性**：stable
- **后台确认结论**：
  - 消息图钉和 Work Pin **使用同一张表** `gb_ai_work_pin`
  - `POST /ai/messages/{messageId}/pin` 是便利包装：自动从消息填充 sourceSnapshot，创建 sourceType=MESSAGE 的 work_pin
  - **不与** `POST /ai/work-pins` 冲突，两者互补：消息图钉自动填充，work-pin 手动创建
- **待补字段**：无

---

### 8. POST /ai/messages/{messageId}/note — 消息笔记

- **方法**：POST
- **路径**：`/api/ai/messages/{messageId}/note`
- **Controller**：`AiMessagePinController.java:61`
- **Service**：`GbAiWorkNoteService.saveNoteFromAssistantMessage()`
- **前台调用场景**：`MessageRow.vue` 从单条消息保存笔记
- **请求参数**：
  - URL path `messageId`（Long）
  - Query `userId`（Long）
- **响应结构**：

```json
{
  "code": 0,
  "data": {
    "noted": true,
    "noteId": 789,
    "duplicated": false
  }
}
```

- **字段稳定性**：stable
- **后台确认结论**：
  - 与 Work Note 使用同一张表 `gb_ai_work_note`
  - 自动从消息填充 note 内容
- **待补字段**：无

---

## 二、Conversation（会话）

### 9. GET /ai/conversations — 会话列表

- **方法**：GET
- **路径**：`/api/ai/conversations`
- **Controller**：`AiConversationController.java:31`
- **Service**：`AiConversationHistoryService.listConversations()`
- **请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `userId` | Long | 是 | — | 部门用户 ID |
| `departmentId` | Long | 否 | — | 单店筛选 |
| `distributerId` | Long | 否 | — | 集团筛选 |
| `keyword` | String | 否 | — | 标题/正文关键字 |
| `status` | String | 否 | — | 按最后消息状态过滤 |
| `includeArchived` | boolean | 否 | false | 含归档 |
| `tagId` | Long | 否 | — | 标签筛选 |
| `notebookId` | Long | 否 | — | 笔记本筛选 |
| `pinned` | Boolean | 否 | — | 仅置顶 |
| `page` | int | 否 | 1 | — |
| `pageSize` | int | 否 | 20 | — |

- **响应结构**（`AiConversationListResponseDTO` 包装在 `data` 内）：

```json
{
  "code": 0,
  "data": {
    "total": 50,
    "page": 1,
    "pageSize": 20,
    "items": [
      {
        "conversationId": 123,
        "title": "帮我分析本月成本",
        "conversationStatus": "...",
        "archived": false,
        "departmentId": 1,
        "distributerId": 2,
        "scopeMode": "GROUP",
        "pinned": true,
        "updatedAt": "2026-05-21T10:00:00",
        "lastRunStatus": "COMPLETED",
        "previewText": "本月成本分析...",
        "tags": [],
        "notebooks": [],
        "noteSummary": null
      }
    ]
  }
}
```

- **字段稳定性**：stable
- **后台确认结论**：响应包在 `data` 内（标准化 R 包装）
- **待补字段**：无

---

### 10. GET /ai/conversations/{id}/messages — 会话消息

- **方法**：GET
- **路径**：`/api/ai/conversations/{conversationId}/messages`
- **Controller**：`AiConversationController.java:56`
- **Service**：`AiConversationHistoryService.listMessages()`
- **请求参数**：Query `userId`（Long）
- **响应结构**（`AiConversationMessagesResponseDTO` 包装在 `data` 内）：

```json
{
  "code": 0,
  "data": {
    "conversationId": 123,
    "messages": [
      {
        "messageId": 1,
        "role": "user",
        "content": "帮我分析本月成本",
        "status": null,
        "runId": 1778344855542,
        "pinned": true,
        "pinId": 456,
        "noted": false,
        "noteId": null
      }
    ]
  }
}
```

- **字段稳定性**：stable
- **后台确认结论**：
  - 消息上的 `pinned`/`pinId`/`noted`/`noteId` 来自 `gb_ai_work_pin` / `gb_ai_work_note` 表
  - 仅返回 `gb_ai_message` 表中已有的数据；多智能体默认不写消息表
- **待补字段**：无

---

### 11. POST /ai/conversations/{id}/pin — 置顶会话

- **方法**：POST
- **路径**：`/api/ai/conversations/{conversationId}/pin`
- **Controller**：`AiConversationController.java:99`
- **参数**：Path `conversationId` + Query `userId`
- **响应**：`{ "code": 0, "data": { "pinned": true, "duplicated": false } }`
- **字段稳定性**：stable

---

### 12-13. POST /ai/conversations/{id}/tags & notebooks

- **方法**：POST
- **路径**：`/api/ai/conversations/{conversationId}/tags` / `/notebooks`
- **Controller**：`AiConversationController.java:125,150`
- **请求体**：`{ "userId": 1, "tagName": "...", "tagColor": "..." }` / `{ "userId": 1, "notebookName": "..." }`
- **字段稳定性**：stable

---

### 14. GET /ai/conversations/{id}/pins — 会话下图钉

- **方法**：GET
- **路径**：`/api/ai/conversations/{conversationId}/pins`
- **Controller**：`AiConversationController.java:72`
- **Service**：`GbAiWorkPinService.listPins()`（与 `GET /ai/work-pins` **同一方法**）
- **参数**：Path `conversationId` + Query `userId`
- **字段稳定性**：stable
- **后台确认结论**：
  - 与 `GET /ai/work-pins?conversationId=&userId=` **等价**
  - 两者**共存**，推荐前台优先用此路径（RESTful 风格更清晰），`/ai/work-pins` 作为独立 CRUD 路径也保留
  - 不是新旧替代关系，是同一功能的两条路径

---

### 15. GET /ai/conversations/{id}/notes — 会话下笔记

- **方法**：GET
- **路径**：`/api/ai/conversations/{conversationId}/notes`
- **Controller**：`AiConversationController.java:84`
- **Service**：`GbAiWorkNoteService.listNotes()`（与 `GET /ai/work-notes` **同一方法**）
- **后台确认结论**：与 `GET /ai/work-notes` 等价，两者共存

---

## 三、Advisor（顾问）

### 16. GET /ai/advisors — 顾问列表

- **方法**：GET
- **路径**：`/api/ai/advisors`
- **Controller**：`AiAdvisorController.java:24`
- **Service**：`GbAiAdvisorService.listAdvisors()`
- **响应**：`{ "code": 0, "data": [ { "id/advisorId": ..., "name/title": ..., "code": ..., "description": ... } ] }`
- **字段稳定性**：stable
- **备注**：返回实体对象列表，字段名以 `GbAiAdvisorEntity` 为准

---

### 17. GET /ai/advisors/{id} — 顾问详情

- **方法**：GET
- **路径**：`/api/ai/advisors/{advisorId}`
- **Controller**：`AiAdvisorController.java:30`
- **响应**：`{ "code": 0, "data": { advisor 实体 } }`
- **字段稳定性**：stable

---

### 18. GET /ai/advisors/{id}/conversation — 顾问会话

- **方法**：GET
- **路径**：`/api/ai/advisors/{advisorId}/conversation`
- **Controller**：`AiAdvisorController.java:56`
- **Service**：`AiAdvisorConversationService.getOrBootstrap()`
- **参数**：`userId`（必填）+ `departmentId`/`distributerId`/`scopeMode`（可选）
- **响应**：`{ "code": 0, "data": { "advisorId": ..., "conversationId": ..., "conversationType": "ADVISOR", "messages": [...] } }`
- **字段稳定性**：stable

---

### 19. GET /ai/advisors/{id}/workflows — 顾问绑定工作流

- **方法**：GET
- **路径**：`/api/ai/advisors/{advisorId}/workflows`
- **Controller**：`AiAdvisorController.java:40`
- **响应**：`{ "code": 0, "data": [ workflow 列表 ] }`
- **字段稳定性**：stable

---

### 20. GET /ai/advisors/{id}/workflow-runs — 工作流运行历史

- **方法**：GET
- **路径**：`/api/ai/advisors/{advisorId}/workflow-runs`
- **参数**：`userId`（必填，Long）+ `limit`（默认 10，最大 50）
- **Controller**：`AiAdvisorController.java:74`
- **字段稳定性**：stable

---

## 四、Workflow（工作流）

### 21. GET /ai/workflows — 工作流列表

- **方法**：GET
- **路径**：`/api/ai/workflows`
- **Controller**：`AiWorkflowController.java:23`
- **响应**：`{ "code": 0, "data": [ workflow 列表 ] }`
- **字段稳定性**：stable

---

### 22. POST /ai/workflows/{id}/run — 发起工作流

- **方法**：POST
- **路径**：`/api/ai/workflows/{workflowId}/run`
- **Controller**：`AiWorkflowController.java:29`
- **请求体**：`WorkflowRunCreateRequest`（含 `userId`, `distributerId`, `departmentId`, `scopeMode`, `advisorId`, `inputParams`）
- **响应**：`{ "code": 0, "data": { "workflowRunId": ..., "runId/aiRunId": ..., "conversationId": ..., "status": "..." } }`
- **字段稳定性**：stable

---

### 23. GET /ai/workflow-runs/{id} — 工作流运行详情

- **方法**：GET
- **路径**：`/api/ai/workflow-runs/{id}`
- **Controller**：`AiWorkflowRunController.java:21`
- **响应**：`{ "code": 0, "data": { run 详情 } }`
- **字段稳定性**：stable

---

## 五、Work Pin / Note（工作沉淀）

### 24-28. Work Pin CRUD

| # | 方法 | 路径 | Controller | 说明 |
|---|------|------|-----------|------|
| 24 | GET | `/ai/work-pins?conversationId=&userId=` | `GbAiWorkPinController.java:34` | 按会话列出图钉 |
| 25 | POST | `/ai/work-pins` | `GbAiWorkPinController.java:24` | 创建图钉 |
| 26 | GET | `/ai/work-pins/{id}?userId=` | `GbAiWorkPinController.java:46` | 图钉详情 |
| 27 | DELETE | `/ai/work-pins/{id}?userId=` | `GbAiWorkPinController.java:58` | 软删除图钉 |
| 28 | POST | `/ai/work-pins/{id}/promote-to-note` | `GbAiWorkPinController.java:71` | 升级为笔记 |

- **Service**：`GbAiWorkPinService`
- **请求参数**：
  - #25：`WorkPinCreateRequest` body — 支持顶层扁平字段 + `sourceSnapshot` 嵌套
  - #28：`PromotePinToNoteRequest` body
- **字段稳定性**：stable

### 29-33. Work Note CRUD

| # | 方法 | 路径 | Controller | 说明 |
|---|------|------|-----------|------|
| 29 | GET | `/ai/work-notes?conversationId=&userId=` | `GbAiWorkNoteController.java:34` | 按会话列出笔记 |
| 30 | POST | `/ai/work-notes` | `GbAiWorkNoteController.java:24` | 创建笔记 |
| 31 | GET | `/ai/work-notes/{id}?userId=` | `GbAiWorkNoteController.java:46` | 笔记详情 |
| 32 | PUT | `/ai/work-notes/{id}` | `GbAiWorkNoteController.java:58` | 更新笔记 |
| 33 | DELETE | `/ai/work-notes/{id}?userId=` | `GbAiWorkNoteController.java:68` | 软删除笔记 |

- **Service**：`GbAiWorkNoteService`
- **字段稳定性**：stable

### Pins 新旧路径关系（重点确认）

| 路径 | 说明 | 状态 |
|------|------|------|
| `GET /ai/conversations/{id}/pins` | 按会话 REST 路径拉取图钉 | **主路径** |
| `GET /ai/work-pins?conversationId=&userId=` | 独立图钉查询 | **等价共存** |
| `POST /ai/work-pins` | 创建独立图钉（手动 sourceSnapshot） | **独立 CRUD** |
| `POST /ai/messages/{messageId}/pin` | 消息级图钉（自动填充 sourceSnapshot） | **便利包装** |

**结论**：
- `/ai/conversations/{id}/pins` 与 `GET /ai/work-pins` 调用**同一 Service 方法**，完全等价
- 两者**共存**，不冲突。前台 fallback 逻辑可保留但非必须
- `message pin`（消息图钉）和 `work-pin`（工作图钉）**用同一张表** `gb_ai_work_pin`，不冲突
- 消息图钉自动填充 sourceSnapshot（sourceType=MESSAGE），work-pin 手动创建

---

## 六、Export / Report / Task / Knowledge

### Export（34-37）

- **后台确认结论**：后端 **不存在** `/exports` 相关 Controller
- **状态**：此功能组在前台有调用代码但**后端尚未实现**
- **待补字段**：整个功能组需后端新建

### Report（38-41）

- **路径**：`/api/reports`（注意：实际后端是 `/api/gbreport`，不是 `/api/reports`）
- **Controller**：`GbReportController.java`（`@RequestMapping("gbreport")`）
- **后台确认结论**：
  - 前端引用的 `POST /reports`、`GET /reports/{id}/preview` 等路径与后端实际路径**不一致**
  - 后端实际路径以 `gbreport` 为前缀（如 `/api/gbreport/saveReportCost`）
  - 前端 `/reports` 路径组需要与后端对齐或后端新增 BFF 适配层
- **字段稳定性**：stable（但路径需对齐）
- **待补字段**：需要前端与后端对齐 Report 路径

### Task（42-45）

- **后台确认结论**：后端 **不存在** `/tasks` 相关 Controller
- **状态**：此功能组在前台有调用代码但**后端尚未实现**
- **待补字段**：整个功能组需后端新建

### Knowledge（46）

- **路径**：`/api/ai/knowledge/search`
- **Controller**：`GbAiKnowledgeController.java`（`@RequestMapping("/ai/knowledge")`）
- **后台确认结论**：
  - `/ai/knowledge/search` 端点 **不存在**
  - 后端已实现的相关端点：`/categories`、`/summary`、`/detail/{id}`、`/recommend`、`/list`、`/save`、`/update`、`/delete/{id}`、`/offline/{id}`、`/remove/{id}`
  - 前台 `KnowledgeView.vue` 当前的 mock 结构 `{ query, answer, citations }` 与后端实际模型 `GbAiKnowledgeEntity` 不同
- **待补字段**：需后端新增 `/ai/knowledge/search` 端点，或前台改用现有 `/summary` + `/recommend` 端点

---

## 七、Run Debug 字段契约（重点）

### 7.1 字段位置与来源

Debug 字段的两种下发渠道：

| 来源 | 位置 | 触发时机 |
|------|------|---------|
| GET /ai/runs/{runId} | `harnessDebug` 顶层（直接键） | SSE run_finished 后调用 |
| SSE answer_delta | `data.resolvedQueryContextSummary` | 每个 answer_delta 帧 |

两者**同源**（均经 `AiHarnessResolvedContextSummarizer.summarize()`），但 GET 版本字段更全（包含 AnswerPlan 执行后镜像），SSE 版本是 Run 执行中的快照。

### 7.2 字段稳定性分类

#### stable（前台业务可长期依赖）

| 字段 | GET run 位置 | SSE 位置 | 说明 |
|------|-------------|---------|------|
| `conversationId` | `harnessDebug.conversationId` | — | 会话 ID |
| `runId` | 顶层 + `harnessDebug.runId` | SSE 信封 `runId` | Run ID |
| `effectiveIntentCode` | `harnessDebug.effectiveIntentCode` | — | 最终意图编码 |
| `effectivePathCode` | `harnessDebug.effectivePathCode` | — | 最终路径编码 |
| `effectiveTimeWindowSource` | `harnessDebug.effectiveTimeWindowSource` | — | 时间窗口来源 |
| `startDate` | `harnessDebug.startDate` | — | 查询起始日期 |
| `endDate` | `harnessDebug.endDate` | — | 查询截止日期 |
| `scopeType` | `harnessDebug.scopeType` | — | GROUP / STORE |
| `visibleStores` | `harnessDebug.visibleStores` | — | 可见门店列表（含 storeDepartmentId, storeName） |

#### debug-only（仅 Run 调试面板使用）

所有 `harnessDebug` 内**除上表 stable 字段外**的字段均为 debug-only，包括但不限于：

| 字段 | 位置 | 说明 |
|------|------|------|
| `queryScopeKind` | `harnessDebug.queryScopeKind` | 查询范围类型 |
| `queryStoreIds` | `harnessDebug.queryStoreIds` | 查询门店 ID 列表 |
| `queryRealDepartmentIds` | `harnessDebug.queryRealDepartmentIds` | 真实部门 ID |
| `queryDistributerId` | `harnessDebug.queryDistributerId` | 集团维度 |
| `storeToDepartmentIds` | `harnessDebug.storeToDepartmentIds` | 门店→部门映射 |
| `expandedSqlDepartmentIds` | `harnessDebug.expandedSqlDepartmentIds` | SQL 展开部门集合 |
| `visibleStoreRootIds` | `harnessDebug.visibleStoreRootIds` | 可见门店根 ID |
| `planSource` | `harnessDebug.planSource` | 当前命中的 AnswerPlan 槽位 |
| `consumedAnswerPlans` | `harnessDebug.consumedAnswerPlans` | 已消费的 AnswerPlan |
| `missingAnswerPlans` | `harnessDebug.missingAnswerPlans` | 缺失的 AnswerPlan |
| `diagnosisPlan` | `harnessDebug.diagnosisPlan` | 诊断计划完整对象 |
| `diagnosisPlanPresent` | `harnessDebug.diagnosisPlanPresent` | 是否有诊断计划 |
| `diagnosisPlanType` | `harnessDebug.diagnosisPlanType` | 诊断类型 |
| `purchaseAnswerPlan` | `harnessDebug.purchaseAnswerPlan` | 采购 AnswerPlan |
| `purchaseAnswerPlanType` | `harnessDebug.purchaseAnswerPlanType` | 采购计划类型 |
| `revenueAnswerPlan` | `harnessDebug.revenueAnswerPlan` | 营收 AnswerPlan |
| `revenueAnswerPlanType` | `harnessDebug.revenueAnswerPlanType` | 营收计划类型 |
| `stockReduceAnswerPlan` | `harnessDebug.stockReduceAnswerPlan` | 出库 AnswerPlan |
| `stockReduceAnswerPlanType` | `harnessDebug.stockReduceAnswerPlanType` | 出库计划类型 |
| `dishProfitAnswerPlan` | `harnessDebug.dishProfitAnswerPlan` | 菜品毛利 AnswerPlan |
| `dishProfitAnswerPlanType` | `harnessDebug.dishProfitAnswerPlanType` | 菜品毛利计划类型 |
| `dishSalesAnswerPlan` | `harnessDebug.dishSalesAnswerPlan` | 菜品销售 AnswerPlan |
| `dishSalesAnswerPlanType` | `harnessDebug.dishSalesAnswerPlanType` | 菜品销售计划类型 |
| `warehouseAnswerPlan` | `harnessDebug.warehouseAnswerPlan` | 库存 AnswerPlan |
| `warehouseAnswerPlanType` | `harnessDebug.warehouseAnswerPlanType` | 库存计划类型 |
| 多域 AnswerPlan 扁平字段 | `harnessDebug.purchaseAnswerPlanSortKey/Direction/FocusRows/SecondaryRows/Debug` 等 | 各域扁平字段 |

### 7.3 前台缺失字段逐个确认

#### 1. matrixRowId — 已返回

后端**已下发**，但不是单一字段 `matrixRowId`，而是分为以下域特定字段：

| 后端实际字段 | 返回位置 | 类型 |
|-------------|---------|------|
| `revenueMatrixRowId` | `harnessDebug` 顶层 | String |
| `stockReduceMatrixRowId` | `harnessDebug` 顶层 | String |
| `warehouseMatrixRowId` | `harnessDebug` 顶层 | String |
| `dishSalesMatrixRowId` | `harnessDebug` 顶层 | String |
| `diagnosisDrilldownMatrixRowId` | `diagnosisPlan.debug` 内 | String |

**后台确认结论**：前台需按域分别提取。当前前台代码未提取这些字段（`AiRunDebugFields` 类型中未定义）。

#### 2. knownGap — 已返回

后端**已下发**，同样按域分为：

| 后端实际字段 | 返回位置 | 类型 |
|-------------|---------|------|
| `revenueKnownGap` | `harnessDebug` 顶层 | Object/String |
| `stockReduceKnownGap` | `harnessDebug` 顶层 | Object/String |
| `warehouseKnownGap` | `harnessDebug` 顶层 | Object/String |
| `dishSalesKnownGap` | `harnessDebug` 顶层 | Object/String |
| `diagnosisKnownGap` | `diagnosisPlan.debug` 内 | Object/String |

**后台确认结论**：与 matrixRowId 同样按域分发。

#### 3. diagnosisQuestionType — 已返回

- 后端常量：`BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_QUESTION_TYPE = "diagnosisQuestionType"`
- 返回位置：`diagnosisPlan.debug` 内（**不是** `harnessDebug` 顶层）
- 类型：String（如 `"STORE_PRIORITY_RANKING"`）
- 与 `diagnosisPlanType` 不同：`diagnosisPlanType` 是诊断计划类型，`diagnosisQuestionType` 是对应矩阵行的问题分类
- 代码引用：`DiagnosisDeterministicRenderer.java:256`

#### 4. diagnosisFacet — 已返回

- 后端常量：`BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_FACET = "diagnosisFacet"`
- 返回位置：`diagnosisPlan.debug` 内
- 类型：String
- 出现在 `BusinessDiagnosisDrilldownMatrix.java:169` 中

#### 5. diagnosisChildDomain — 已返回

- 后端常量：`BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_CHILD_DOMAIN = "diagnosisChildDomain"`
- 返回位置：`diagnosisPlan.debug` 内
- 类型：String（如 `"purchase"` / `"stock_reduce"` / `"dish_profit"`）
- 用于子域归因，在 `BusinessDiagnosisDrilldownMatrix.java:189` 中设置

#### 6. diagnosisTargetStoreName — 已返回

- 后端常量：`BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_TARGET_STORE_NAME = "diagnosisTargetStoreName"`
- 返回位置：`diagnosisPlan.debug` 内
- 类型：String（门店名称）

#### 字段提取方式

这 4 个诊断字段（3-6）都在 `harnessDebug.diagnosisPlan.debug` 内，**不在** `harnessDebug` 顶层。前台需从 `diagnosisPlan.debug` 中提取：

```json
{
  "harnessDebug": {
    "diagnosisPlan": {
      "type": "STORE_PRIORITY_RANKING",
      "debug": {
        "diagnosisQuestionType": "STORE_PRIORITY_RANKING",
        "diagnosisFacet": "cost_overview",
        "diagnosisChildDomain": "dish_profit",
        "diagnosisTargetStoreName": "AAA门店",
        "diagnosisDrilldownMatrixRowId": "...",
        "diagnosisKnownGap": "..."
      }
    }
  }
}
```

### 7.4 visibleStores vs queryStoreIds 语义

| 字段 | 来源 | 语义 | 类型 |
|------|------|------|------|
| `visibleStores` | `AiResolvedOrgScope` | 权限可见门店（含 storeName 可读名） | `[{storeDepartmentId, storeName}]` |
| `visibleStoreRootIds` | `AiResolvedDataScope` | 可见门店根 ID 集合 | `[Long]` |
| `queryStoreIds` | `AiResolvedDataScope` | 本次查询实际使用的门店 ID | `[Integer]` |
| `visibleStoreIds` | `AiResolvedDataScope` | 可见门店 ID（Long） | `[Long]` |

**语义差异**：
- `visibleStores` = 权限范围 + 门店名称（展示用）
- `visibleStoreRootIds` = 门店根部门 ID（与 visibleStores 对应）
- `queryStoreIds` = 本次查询实际使用的门店 ID（可能因追问、多店等原因缩小）
- `queryScopeKind` = STORE 用 queryStoreIds；DEPARTMENT 用 queryRealDepartmentIds；DISTRIBUTER 用 queryDistributerId

**后台确认结论**：前台调试面板两个字段都展示是正确的。

### 7.5 consumedAnswerPlans 是否建议独立展示

- 后端已从 `harnessDebug` 顶层下发 `consumedAnswerPlans` 和 `missingAnswerPlans`
- `diagnosisPlan.debug.consumedAnswerPlans` 是诊断计划级别的消费清单
- **建议**：`harnessDebug.consumedAnswerPlans` 作为独立调试行展示（反映整体 Run 的 AnswerPlan 消费情况）；`diagnosisPlan.debug.consumedAnswerPlans` 作为诊断计划的子项展示

### 7.6 GET /ai/runs/{runId} 响应剥壳层级

**当前实际情况**：`harnessDebug` 在响应**顶层**，不在 `data` 内。

```json
{
  "code": 0,
  "runId": 123,
  "status": "COMPLETED",
  "harnessDebug": { ... }  // <-- 顶层
}
```

前台的 `unwrapAiRunDetailBody` 最多 3 层剥壳（`data` / `result`）在当前版本**部分有效**——查找 `data` 层是为了兼容对 `R.ok().put("data", ...)` 的包装，但 `harnessDebug` 不在 `data` 内。

### 7.7 SSE answer_delta 与 GET run 字段一致性

两者经 `AiHarnessResolvedContextSummarizer` 同源，但**时序不同**：

- SSE `answer_delta.data.resolvedQueryContextSummary`：Run 执行中的快照（AnswerPlan 可能未完全挂载）
- GET `harnessDebug.resolvedQueryContextSummary`：Run 结束后的最终态

**已知差异**：
- `dishProfitAnswerPlan`：SSE 中可能为嵌套对象，GET 中由 AnswerPlan Appender 扁平展开为 `dishProfitAnswerPlan*` 系列扁平字段
- 各域 AnswerPlan（purchase/stockReduce/revenue/dishProfit/warehouse）：GET 版本额外带有 `*Present`、`*Type`、`*SortKey`、`*Debug` 等扁平字段

**建议**：调试面板以 GET 版本为准，SSE 版本作预览。

### 7.8 SSE 重连策略

- 后端**不保存**事件缓冲区
- SSE 断开后重连：仅接收新事件，历史事件**不重放**
- Run 在服务端继续执行不受影响
- **不支持** `Last-Event-ID` 增量重放
- 前台的 `localStorage.pendingRun_{conversationId}` 恢复机制是正确的前台补偿策略

### 7.9 scopeMode 默认值

- `scopeMode` 为可选参数
- 省略时：`departmentId` 存在 → `STORE`；`distributerId` 存在 → `GROUP`
- 两者同时存在时，以 `scopeMode` 显式值为准；都不传 `scopeMode` 则按上述规则推断
- `AiRunScopeIntersectService` 在 `BusinessScopeIntersectNode` 中进一步求交（请求 dept 子树 ∩ 身份锚点子树）

### 7.10 POST /ai/messages 是否遗留

- 后端**不存在** `POST /api/ai/messages` 端点
- `AiMessagePinController` 仅提供 `POST /ai/messages/{messageId}/pin`、`DELETE /ai/messages/{messageId}/pin`、`POST /ai/messages/{messageId}/note`
- 前台 `aiApi.sendAiMessage` 未被主链路调用，确认为**遗留前端代码**，建议清理

### 7.11 knowledge/search 是否已实现

- **未实现**。`GbAiKnowledgeController` 无 `/search` 端点
- 前台 `KnowledgeView.vue` 当前使用 mock
- 替代方案：前台可暂用 `GET /ai/knowledge/summary` + `GET /ai/knowledge/recommend` 实现搜索效果
- 若需独立搜索端点，需后端新增

---

## 八、汇总

### 8.1 确认的前台 API（46 个中）

| 状态 | 数量 | 接口 |
|------|------|------|
| 已确认，正常 | 36 | #1-#4, #6-#23, #24-#33（全部 Run/Conversation/Advisor/Workflow/Pin/Note） |
| 后端不存在 | 6 | #5(POST /ai/messages), #34-#37(/exports), #42-#45(/tasks) |
| 路径不一致 | 4 | #38-#41(/reports vs /gbreport) |
| 端点未实现 | 1 | #46(/knowledge/search) |

### 8.2 Run Debug 字段确认

| 字段 | 状态 | 位置 |
|------|------|------|
| matrixRowId（5 个域变体） | ✅ 已返回 | `harnessDebug` 顶层 |
| knownGap（5 个域变体） | ✅ 已返回 | `harnessDebug` 顶层 |
| diagnosisQuestionType | ✅ 已返回 | `diagnosisPlan.debug` 内 |
| diagnosisFacet | ✅ 已返回 | `diagnosisPlan.debug` 内 |
| diagnosisChildDomain | ✅ 已返回 | `diagnosisPlan.debug` 内 |
| diagnosisTargetStoreName | ✅ 已返回 | `diagnosisPlan.debug` 内 |
| diagnosisDrilldownMatrixRowId | ✅ 已返回 | `diagnosisPlan.debug` 内 |
| diagnosisKnownGap | ✅ 已返回 | `diagnosisPlan.debug` 内 |

### 8.3 Stable 字段（前台业务长期依赖）

`conversationId`, `runId`, `status`, `effectiveIntentCode`, `effectivePathCode`, `effectiveTimeWindowSource`, `startDate`, `endDate`, `scopeType`, `visibleStores`

### 8.4 Debug-only 字段

所有 `harnessDebug` 内除 stable 字段外的字段均标记为 debug-only，尤其是：
- 所有 AnswerPlan 系列字段（`*AnswerPlan`, `*AnswerPlanType`, `*AnswerPlanPresent` 等）
- 所有 query 口径字段（`queryScopeKind`, `queryStoreIds`, `queryRealDepartmentIds` 等）
- `diagnosisPlan` 及其子字段
- `planSource`, `consumedAnswerPlans`, `missingAnswerPlans`
- 所有 semantic 系列字段

### 8.5 前台需要但后端暂缺

| 缺失项 | 说明 |
|--------|------|
| `/exports` 全组 | 整个功能组未实现（导出记录 CRUD） |
| `/tasks` 全组 | 整个功能组未实现（任务 CRUD） |
| `/knowledge/search` | 搜索端点不存在，需新增或前台改用 `/summary` + `/recommend` |
| `/reports` 路径对齐 | 后端实际为 `/gbreport`，路径不一致 |

### 8.6 旧路径/新路径并存

| 路径对 | 关系 | 建议 |
|--------|------|------|
| `GET /ai/conversations/{id}/pins` ↔ `GET /ai/work-pins` | 等价共存 | 保留两者，推荐前者 |
| `GET /ai/conversations/{id}/notes` ↔ `GET /ai/work-notes` | 等价共存 | 保留两者，推荐前者 |
| `POST /ai/messages/{id}/pin` ↔ `POST /ai/work-pins` | 互补（自动 vs 手动） | 两者均保留 |
| `POST /ai/runs` ↔ `POST /ai/messages`（不存在） | 后者不存在 | 清理前台遗留调用 |

### 8.7 后续补后端接口建议

1. **紧急**：`/knowledge/search` — 前台已有 UI 需要真实数据
2. **建议**：`/exports` + `/tasks` — 评估产品优先级后补
3. **建议**：`/reports` 路径 — 与前端对齐为 `/api/reports` 或修改前端为 `/api/gbreport`
