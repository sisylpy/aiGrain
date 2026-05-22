# AI Run SSE 后端契约（真实帧格式与 JSON 字段）

**唯一事实来源（本文件）**：`GET /api/ai/runs/{runId}/events` 返回的 **SSE 文本流** 与 **`data:` 行单行 JSON** 结构。  
实现类：`com.nongxinle.ai.trace.AiRunSession`、`AiSseEventPublisher`；逻辑入口：`AiRunService` 与各 `AgentNode`。

**与 `docs/API_INTEGRATION.md` 的关系**：业务说明、成本诊断 UI 仍以 `API_INTEGRATION` 为准；**帧级字节形态、字段必选/可选、错误与收口** 以 **本文件** 为准。

---

## 0. 总则

| 项 | 约定 |
|----|------|
| **传输** | `Content-Type: text/event-stream;charset=UTF-8`，UTF-8 |
| **分隔** | 每个事件以 **空行** `\n\n` 结束（标准 SSE） |
| **`event:`** | **命名事件**：`event: <name>` 与 JSON 内 **`"event":"<name>"`** 一致 |
| **`data:`** | 一般为 **单行 JSON**；若未来 JSON 内含换行，框架可能拆多行 `data:`，前端需按 RFC 合并后再 `JSON.parse` |
| **JSON 字段顺序** | **不保证**，仅按 **键名** 解析 |
| **`data` 根字段** | 过程类事件往往 **无** 顶层 `data` 键，等价于「无结构化负载」；**不要**强依赖 `"data": null` |
| **聊天正文** | **仅**在 **`answer_delta`** 的 **`data.text`**（及兼容根级 **`text`**）中；**不要**用 `run_finished`、`GET /ai/runs/{id}` 的 `answerPreview` 当 SSE 主展示 |
| **`costDiagnosis`** | **仅**在 **`answer_delta.data.costDiagnosis`**；不要放信封根、`displayText`、或单靠 `answerPreview` |
| **帧字节** | `AiRunSession` 用 **`SseEmitter.event().name(…).data(envelopeMap, MediaType.APPLICATION_JSON)`**：`name`/`data` 由 Spring 拼行，**勿**手拼整帧。`WebMvcConfig` 须在 FastJson 前注册 **`StringHttpMessageConverter`**：Spring 内部会先把 **`event:…\\ndata:`** 前缀按 **text/plain** 写出，若列表里只有 FastJson 且它也 `canWrite(String, TEXT_PLAIN)`，前缀会被再 JSON 引用 → **首字节 `0x22`、大量 `5c 6e`** |
| **CORS** | 若前端 `credentials: 'include'`，需响应 `Access-Control-Allow-Credentials: true`（见 `WebMvcConfig`） |

---

## 1. 命名事件（必读）：不是默认 `message`

后端通过 Spring `SseEmitter.event().name(<业务名>)` 发送，**原生 `EventSource.onmessage` 不会收到**下列帧。

前端请使用：

```ts
eventSource.addEventListener('answer_delta', (e) => { /* JSON.parse(e.data) */ });
eventSource.addEventListener('run_finished', () => { eventSource.close(); });
// … 其它 event 名同理
```

**原始帧外壳**形如（仅结构演示）：

```http
event: answer_delta
data: {...单行 JSON...}

```

---

## 2. JSON 信封（所有事件共通）

每条 `data:` 解析后均为 **扁平 JSON**。`AiSseEventPublisher.buildEnvelope` 在业务字段之后 **追加**（顺序以运行库序列化为准，以下用逻辑列表描述）：

| 键 | 类型 | 说明 |
|----|------|------|
| `event` | `string` | 与 SSE `event:` 同名 |
| `runId` | `number` | Long |
| `timestamp` | `string` | `Asia/Shanghai` 的 ISO-8601 偏移，示例 `2026-05-10T00:41:38.504012+08:00` |
| `status` | `string` | 语义态，见下 |
| `displayText` | `string` | 给人看的进度/结果短文 |

### `status` 语义（JSON 内）

- 多数过程事件：`"running"`
- `error`：默认 `"failed"`
- `run_finished`：**由本次 Run 终态决定**，小写英文：
  - 正常跑完：`"completed"`
  - 用户协作取消：`"cancelled"`
  - 异常：`"failed"`

> 注意：**没有** `"success"` 字样；成功结束请认 `run_finished` + `status === "completed"`。

---

## 3. 原始 SSE 样例（真实形态示例）

以下 `data:` 为 **单行**（此处为可读性在 Markdown 中折行，**线上无换行**）。`timestamp` 以实际为准。

### 3.1 `run_started`

```http
event: run_started
data: {"displayText":"任务已接收，开始执行…","event":"run_started","runId":1778344855542,"timestamp":"2026-05-10T00:41:38.504012+08:00","status":"running"}

```

### 3.2 `agent_started` / `agent_finished`（工作区路由）

```http
event: agent_started
data: {"displayText":"正在识别任务类型…","agent":"WorkspaceRouterAgent","event":"agent_started","runId":1778344855542,"timestamp":"2026-05-10T00:41:41.175650+08:00","status":"running"}

event: agent_finished
data: {"agent":"WorkspaceRouterAgent","displayText":"工作空间路由完成","workspaceMode":"BUSINESS_CHAT","event":"agent_finished","runId":1778344855542,"timestamp":"2026-05-10T00:41:41.178437+08:00","status":"running"}

```

### 3.3 `tool_started` / `tool_finished`

```http
event: tool_started
data: {"displayText":"调用工具：revenue_query","tool":"revenue_query","event":"tool_started","runId":1778344855542,"timestamp":"2026-05-10T00:41:47.007030+08:00","status":"running"}

event: tool_finished
data: {"success":true,"tool":"revenue_query","displayText":"工具已完成：revenue_query","event":"tool_finished","runId":1778344855542,"timestamp":"2026-05-10T00:41:49.609674+08:00","status":"running"}

```

取消路径下可能出现的 **跳过工具**（**Historical** 样例中的 `tool` 字段可能为已删 `purchase_query`；现网成本链为 **`purchase_overview`**）：

```http
event: tool_finished
data: {"tool":"purchase_overview","skipped":true,"displayText":"运行已取消，跳过后续工具","success":false,"event":"tool_finished","runId":1778344855542,"timestamp":"...","status":"running"}

```

### 3.4 `review_started` / `review_finished`

```http
event: review_started
data: {"displayText":"正在审核输出…","agent":"OutcomeReviewAgent","event":"review_started","runId":1778344855542,"timestamp":"2026-05-10T00:42:51.499961+08:00","status":"running"}

event: review_finished
data: {"displayText":"审核完成","passed":true,"score":85,"agent":"OutcomeReviewAgent","event":"review_finished","runId":1778344855542,"timestamp":"2026-05-10T00:42:51.501053+08:00","status":"running"}

```

### 3.5 `answer_delta`（正文 + 可选成本诊断）

- **`displayText`**：**过程提示**，固定 **`回答生成中`**；**不要**当主正文（前端应优先解析 `data.text` / 根级 `text`）。
- **必选**：`data.text` — 会话区应展示的全文  
- **兼容**：根级 `text` — 与 `data.text` 相同  
- **可选**：`data.costDiagnosis` — **唯一**结构化成本卡片数据源（camelCase，见 `API_INTEGRATION.md`）；`riskLevel` 可为 `ok` | `warning` | `high` | **`data_incomplete`**（数据口径不足/链路可能断点，非必然经营风险）。

```http
event: answer_delta
data: {"displayText":"回答生成中","data":{"text":"本月暂未发现明显损耗异常…","costDiagnosis":{"agentName":"CostDiagnosisAgent","summary":"本月暂未发现明显损耗异常…","riskLevel":"data_incomplete","keyMetrics":[],"findings":[],"recommendations":[],"needMoreData":false,"questions":[]}},"text":"本月暂未发现明显损耗异常…","event":"answer_delta","runId":1778344855542,"timestamp":"2026-05-10T00:42:54.546372+08:00","status":"running"}

```

### 3.6 `run_finished`（收口，非正文）

- **必有**：`data` 对象（当前为 **空对象** `{}`）— 便于前端统一按 `envelope.data` 解析  
- **`status`**：`completed` | `cancelled` | `failed`  
- **`displayText`**：人类可读收口（**不要**当模型正文）  

成功结束：

```http
event: run_finished
data: {"status":"completed","displayText":"完成","data":{},"event":"run_finished","runId":1778344855542,"timestamp":"2026-05-10T00:42:56.395076+08:00"}

```

取消结束（协作式 `/stop` 生效后）：

```http
event: run_finished
data: {"status":"cancelled","displayText":"已取消","data":{},"event":"run_finished","runId":1778344855542,"timestamp":"2026-05-10T00:42:56.395076+08:00"}

```

> **无**单独的 `run_cancelled` 事件名；取消与成功共用 `run_finished`，靠 **`status`** 区分。

失败后（先有 `error`，后有 `run_finished`）：

```http
event: error
data: {...见 §6...}

event: run_finished
data: {"status":"failed","displayText":"运行失败","data":{},"event":"run_finished","runId":1778344855542,"timestamp":"2026-05-10T00:43:00.000000+08:00"}

```

**无单独的 `run_cancelled` 命名事件**：取消亦为 **`run_finished` + `status: "cancelled"`**。

---

## 4. `answer_delta` 逻辑结构（对齐前端 TypeScript）

```json
{
  "displayText": "回答生成中",
  "text": "<与 data.text 相同，可选解析>",
  "data": {
    "text": "<应追加到聊天框的完整回答>",
    "costDiagnosis": {}
  },
  "event": "answer_delta",
  "runId": 1778344855542,
  "timestamp": "2026-05-10T00:42:54.546372+08:00",
  "status": "running"
}
```

- **冗余键**：前端若优先读 `payload.data.delta` / `chunk` / `content`，当前后端**不输出**这三项（仅 `data.text` + 根级 `text`）；需要时双方约定后再加。
- `costDiagnosis`：**可能省略**（非成本链路或序列化失败时见 `costDiagnosisWarning` 等字段，少见）。  
- 当前实现为 **单条整块 `answer_delta`**（非打字机增量）；若未来改为流式，将在 `API_INTEGRATION` / CHANGELOG 另述。

---

## 5. 过程事件典型结构（`tool_started`）

```json
{
  "displayText": "调用工具：revenue_query",
  "tool": "revenue_query",
  "event": "tool_started",
  "runId": 1778344855542,
  "timestamp": "2026-05-10T00:41:47.007030+08:00",
  "status": "running"
}
```

`agent_*` 类事件 **`agent` 有值**，`tool` 一般无：

```json
{
  "displayText": "正在识别任务类型…",
  "agent": "WorkspaceRouterAgent",
  "event": "agent_started",
  "runId": 1778344855542,
  "timestamp": "...",
  "status": "running"
}
```

---

## 6. `error` 逻辑结构（失败 / 权限软拒绝）

经由 `AiSseEventPublisher.publishError` 统一发送 **（2026-05-10 起）**。基础壳：

```json
{
  "displayText": "<人类可读>",
  "message": "<同 data.message>",
  "type": "<异常类简短名或 BusinessError>",
  "tool": "<可选 Tool id，权限拒绝时>",
  "data": {
    "errorCode": "<如 TOOL_PERMISSION_DENIED 或 Java 异常类名>",
    "message": "<说明>",
    "type": "<与根级 type 一致>",
    "permissionDenied": "<可选，结构化越权载荷，见下>"
  },
  "event": "error",
  "runId": 1778344855542,
  "timestamp": "...",
  "status": "failed"
}
```

**历史示例 A（已删毛利 Tool 被拒，Historical raw capture / 非现网契约）**：

```json
{
  "displayText": "你当前账号没有权限使用「毛利率估算」（需要权限：查看成本/毛利结构化分析）。",
  "message": "tool permission denied",
  "type": "BusinessError",
  "tool": "gross_margin_calculator",
  "data": {
    "errorCode": "TOOL_PERMISSION_DENIED",
    "message": "tool permission denied",
    "type": "BusinessError",
    "permissionDenied": {
      "allowed": false,
      "reason": "你当前账号没有权限使用「毛利率估算」（需要权限：查看成本/毛利结构化分析）。",
      "suggestedScope": "你可以查看自己职责范围内的门店/分销经营数据；若需跨店或集团视图，请联系管理员开通相应权限。",
      "requiredPermission": "VIEW_COST",
      "subject": "gross_margin_calculator"
    }
  },
  "event": "error",
  "runId": 1778344855542,
  "timestamp": "...",
  "status": "failed"
}
```

**现网等价（成本诊断 Agent 被拒，`CostDiagnosisAgent` + `VIEW_COST`）**：

```json
{
  "displayText": "成本诊断因权限不足已跳过",
  "agent": "CostDiagnosisAgent",
  "skipped": true,
  "permissionDenied": {
    "allowed": false,
    "requiredPermission": "VIEW_COST",
    "subject": "CostDiagnosisAgent"
  },
  "event": "agent_finished",
  "runId": 1778344855542,
  "timestamp": "...",
  "status": "running"
}
```

**历史示例 B（营销工作台被拒，`WORKSPACE_ACCESS_DENIED`）**：曾由已删除的 **`AiWorkspaceAccessGuard`** / **`BusinessWorkspaceRouteNode`** 发出；当前主链 **不会**再经该路径产生此错误码，示例仅保留契约参考。

```json
{
  "displayText": "当前账号暂无「营销增长」工作台权限，如需营销方案请在具备权限的岗位下使用，或使用经营分析话术。",
  "message": "workspace access denied",
  "type": "BusinessError",
  "data": {
    "errorCode": "WORKSPACE_ACCESS_DENIED",
    "message": "workspace access denied",
    "type": "BusinessError",
    "permissionDenied": {
      "allowed": false,
      "reason": "当前账号暂无「营销增长」工作台权限，如需营销方案请在具备权限的岗位下使用，或使用经营分析话术。",
      "suggestedScope": "可尝试「这个月生意怎么样」「帮我看本月成本怎么样」等与采购/营业额相关的问题。",
      "requiredPermission": "ACCESS_MARKETING_WORKSPACE",
      "subject": "workspace:MARKETING_GROWTH"
    }
  },
  "event": "error",
  "runId": 9002,
  "timestamp": "...",
  "status": "failed"
}
```

- **`TOOL_PERMISSION_DENIED`** / **`WORKSPACE_ACCESS_DENIED`**：`type === "BusinessError"`。`permissionDenied` 字段：`allowed`、`reason`、`suggestedScope`、可选 **`requiredPermission`** / **`subject`**（纯组织口径冲突时可能仅有 **`reason`** / **`suggestedScope`**）。
- **`event:error`** 根级 **`status`** 常为 **`failed`**；但若仅为 Tool/工作台软拒绝、链路未抛异常，`AiRunService` **仍会将会话标为已完成**，随后 **`run_finished.status` 常为 `completed`**。请结合 **`answer_delta`**（是否含「权限提示」「查询范围」前缀）、`tool_finished.skipped`、`data.permissionDenied`、`workspaceMode` 回退与否判断。
- **JVM 未捕获异常**：随后 **`run_finished.status` 一般为 `failed`**。

之后在 `finally` **仍会发送** `run_finished`（**completed** 或 **failed** 视是否抛错而定）。

## 7. `run_finished` 逻辑结构（完成 / 取消 / 失败）

```json
{
  "status": "completed",
  "displayText": "完成",
  "data": {},
  "event": "run_finished",
  "runId": 1778344855542,
  "timestamp": "2026-05-10T00:42:56.395076+08:00"
}
```

| 终态 | `status` | `displayText`（当前后端） |
|------|----------|---------------------------|
| 正常 | `completed` | `完成` |
| 取消 | `cancelled` | `已取消` |
| 异常 | `failed` | `运行失败` |

---

## 8. 事件一览（Business 成本主线常见顺序）

以下为常见顺序；实际条数依赖节点 `shouldRun` 与分支。

1. `run_started`  
2. `agent_started` / `agent_finished` — WorkspaceRoute  
3. `agent_started` / `agent_finished` — **ScopeIntersect**（组织范围与用户锚点 subtree 求交，必要时重写 `departmentId` / `distributerId`)  
4. `agent_started` / `agent_finished` — TimeWindow  
5. `agent_started` / `agent_finished` — DataPlanner  
6. `agent_started` → 多组 `tool_started` / `tool_finished` → `agent_finished` — ToolExecution  
7. `agent_started` / `agent_finished` — CostDiagnosisAgent  
8. `review_started` / `review_finished`  
9. `agent_started` / `agent_finished` — AnswerComposer  
10. **`answer_delta`**  
11. **`run_finished`**  

异常：任意位置可出现 **`error`**。**未捕获异常**：随后 **`run_finished`**（`failed`）。**仅 Tool / 工作台权限软拒绝**（`TOOL_PERMISSION_DENIED`、`WORKSPACE_ACCESS_DENIED`）：可同时存在 **`error`**（`BusinessError`，带 `data.permissionDenied`）与 **`run_finished`**（**`completed`**）——见 **§6**。

---

## 9. 与前端解析相关的坑（摘要）

1. **`addEventListener('answer_delta')`** ✓，`onmessage` ✗  
2. **`run_finished`**：只做 **关流 / loading / status**，正文看 **`answer_delta`**  
3. **`status: "completed"`** 表示成功，**不是 `"success"`**  
4. `fetch`+流：**按 `\n\n` 切块**，再根据 `event:` 行分发  

---

## 10. 与前端 `SSE_FRONTEND_PARSE_CHECKLIST.md`（桌面端仓库）差异对照

前端 checklist 通常在**前端仓库** `docs/` 下；本表按常见条目与**本后端实现对账**：

| 检查项 | 后端 |
|--------|------|
| 命名 SSE + JSON 体内 `event` 一致 | ✅ `event:` + 信封 `"event"` |
| `answer_delta` 正文链：`data.text` → … → `displayText` | ✅ 主：`data.text`、根级 `text`；`displayText`=过程提示 **`回答生成中`** |
| `costDiagnosis` **仅** `answer_delta.data.costDiagnosis` | ✅ 不落根级 / 不写进 `answerPreview` 依赖路径 |
| 取消：`POST /stop` vs 关 EventSource | ✅ **仅**协作式 **`POST …/stop`**（`AiRunSession.state.cancelled`）后走取消分支；前端关流不传终态 SSE |
| 取消收口事件 | ✅ **`run_finished` + `status:"cancelled"` + `displayText:"已取消"`**；无 `run_cancelled` 命名帧 |
| 终态「成功」字面量 `"success"` | ❌ 使用 **`completed`** |
| `data.delta` / `chunk` / `content` 镜像 | 第一版**不发**；**正式正文**仅以 **`answer_delta.data.text`** 为准 |

---

## 11. 对齐结论（SSE 契约层）

在当前代码与本文档版本下：**与前端 checklist 基本一致**。成功终态为 **`completed`**（**不是 `success`**，前端请以 `completed` 判成功）。

**正文契约（第一版）**：`answer_delta` **正式正文**仅 **`answer_delta.data.text`**；根级 `text` 为兼容副本。**不**在后端冗余 `data.delta` / `chunk` / `content`。前端可把其它字段作兜底读取，但以 **`data.text`** 为准。

`run_finished.status`：

| `status` | 含义 |
|----------|------|
| `completed` | 正常跑完 |
| `cancelled` | 协作式 **`POST /api/ai/runs/{id}/stop`** 生效后的取消 |
| `failed` | 抛错后经 `error` 收口 |

**务必重新编译并重启运行中的进程**：仅换 class 而不同停 Tomcat/spring-boot JAR，`curl`/浏览器仍可能对 **旧非标字节流**取样。

JDK 与本机 Maven 校准见 **`docs/JDK_MAVEN.md`**。帧层由 **`AiRunSseEventsWireMvcTest`** 与 **§12.4** 线上 `curl | xxd` 背书；信封 **`data:`** 单行 JSON 形态仍可由 **`AiRunSession.sseDataJson` / `AiRunSessionSseWireTest`** 校验（以 **`{` 开头**，外层不得为 **`"event:…`** 整段）。

---

## 12. 附录：curl / xxd 校验与实测原始帧（须标准换行）

### 12.1 编译与环境

参见 **`docs/JDK_MAVEN.md`**：`mvn -version` 中 **Java ≥17**，再 **`mvn compile test`**。

### 12.2 自检命令（端口以实际监听为准，`/api` 为 context-path）

创建 Run：

```bash
curl -sS -X POST http://localhost:8090/api/ai/runs \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"departmentId":1,"distributerId":2,"message":"帮我看本月成本怎么样"}'
```

订阅 SSE：

```bash
curl -Ns http://localhost:8090/api/ai/runs/<runId>/events > /tmp/sse.out
curl -Ns http://localhost:8090/api/ai/runs/<runId>/events | head -c 640 | xxd | head -20
```

**通过标准**：首字节 **`0x65`（`'e'`）**，即 **`event:`**；帧间 **`0x0a`（LF）** 为真实换行。若为 **`0x22` (`"`) **且前缀呈 **`“event`**（整帧被当成 JSON 字符串）→ **仍是非标旧流**（通常为未升级或未含 **`WebMvcConfig` 中 FastJson 前的 `StringHttpMessageConverter`** 的进程）。若在 **`answer_delta`** 的 **`data.text`** JSON 字符串内见到 **`5c 6e`**，多为**正文内的 `\n` 转义**，与 SSE **帧边界**区分后再判。

文本查看（截取前几帧，`data:` **单行 JSON**，帧间一空行）：

```bash
head -c 2500 /tmp/sse.out
```

### 12.3 仓库内最近一次环境结论（本会话）

| 步骤 | 结果 |
|------|------|
| `JAVA_HOME`= JDK **17**，`mvn compile test` | ✅ 已通过 |
| 本仓库单测 **`AiRunSessionSseWireTest`**：`data:` 负载形态 | ✅ 以 `{` 开头、非 `"event:` 字面串包裹 |
| `localhost:8090` **未替换 JAR/Tomcat 前** 的 `curl \| xxd` | ❌ **仍以 `22`/`5c6e` 开头**——属 **已知旧进程**，**不能**当契约样例存档 |
| `localhost:8090` **JDK17 + 含 `StringHttpMessageConverter` 前置** 的实测 | ✅ 见 **§12.4**（`runId=1778347972417`） |

### 12.4 SSE 修复验收存档（`runId=1778347972417`，2026-05-10）

本节为 **一次已通过**线网抓取的存档，便于后人 **一眼对上：之前为什么错、这次怎么修、怎么验过、别再动发送层**。

| 问题 | 说明 |
|------|------|
| **之前为何错** | Spring `SseEmitter` 会先把 **`event:…`** 与前半段 **`data:`** 前缀按 **text/plain 字符串**写出；若在 **`configureMessageConverters` 里只挂 `FastJsonHttpMessageConverter`** 且它又对该 **String + TEXT_PLAIN** `canWrite`，整段前缀会被 **再打成 JSON 字符串** → **`curl \| xxd` 首节以 `0x22`（`"`）起头**、读出整段形如 **`"event:…`** 的包壳，并在 **帧边界**误判 **`5c 6e`**。 |
| **这次怎么修** | **`AiRunSession`**：`SseEmitter.event().name(...).data(envelopeMap, APPLICATION_JSON)`，不手拼整帧。**`WebMvcConfig`**：在 FastJson **之前**注册 **`StringHttpMessageConverter(UTF_8)`**，保证上述 **plain 前缀**走字符串写出、不再被 FastJson 引用。详见「总则 · 帧字节」。 |
| **如何验证通过** | 见下：**真实 `curl \| xxd`、首节十六进制、`event:`/`data:` 节选**。 |
| **接下来** | **发送层已与本次结论对齐并冻结**；后续以 **前端 `EventSource`** 回归为准，**请勿再改** SSE 写入链，除非契约再版。 |

**校验命令**

```bash
curl -Ns http://localhost:8090/api/ai/runs/1778347972417/events | xxd
```

**抓包证据（新流首节，`xxd`）**

```text
00000000: 6576 656e 743a ...
```

- **新流首节十六进制**：**`6576 656e 743a`** = ASCII **`event:`** → **首字节 `0x65`**（`'e'`），为标准 SSE **帧头**而非 JSON 字面量开头。**帧行间**为真实 **LF（`0x0a`）**，非 JSON 字符串里的 **`5c 6e` 字面**。
- **旧流对照（错误形态）**：首节 **`2265 7665 6e 74`** = **`"event`**（`0x22` + `event`）；与上对比即可区分 **未升级的进程 / 仅有 FastJson 的转换链**。

**标准帧节选（与同一次抓取一致）**

```text
event:run_started
data:{...}

event:agent_started
data:{...}
```

（后续尚有工具、`answer_delta`、`run_finished` 等帧；每条 **`data:`** 后为 **单行** JSON，`{...}` 此处从略。）

**业务侧**：本 Run **BusinessGraph 成本主线**已走通至 **`run_finished` + `status:"completed"`**。

**关于 `5c 6e`（`\` + `n`）**：若在 **`answer_delta`** 负载里、**`data.text` 的 JSON 字符串内部**见到 **`5c 6e`**，属正文 **`\n` 转义**，**可接受**；帧层已通过时 **不与**首节 **`0x22`** 的旧 bug 混淆。

**占位示例已压缩**：多帧完整 JSON 占位（原 `runId=999` 长块）勿再在此复制；字段语义 **`event:` 行与信封内 `event` 一致、`run_finished.status`/`data`** 等见 **总则**及 **§10 / §12.2**。若有 **`event:error`**，多出现在 **`run_finished`** 之前；**`run_finished.status`** 是否为 **`failed`** 取决于是否发生未捕获异常（**权限软拒绝**常为 **`completed`**，见 **§6**）。

---

## 13. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-05-10 | 新增本文；`publishError` 统一 `error.data`；`run_finished` 固定带 `data: {}`；`run_finished.displayText` 按终态区分「完成 / 已取消 / 运行失败」。 |
| 2026-05-10 | `answer_delta.displayText`=**回答生成中**；`AiRunSession`：`data(envelopeMap, APPLICATION_JSON)`；**`WebMvcConfig`**：`StringHttpMessageConverter` 在 FastJson 之前（见「总则 · 帧字节」）；checklist/`docs/JDK_MAVEN.md`。 |
| 2026-05-10 | **权限第一波**：`publishError` → `data.permissionDenied`（`requiredPermission`/`subject` 等）；**§6** 明确 **Tool 软拒绝**与 **`run_finished.completed`** 共存；与 `API_INTEGRATION.md` 对齐。 |
| 2026-05-10 | **权限第二波**：**§8** 在 **`WorkspaceRoute` → `ScopeIntersect`** → **`TimeWindow`** 间插入 **`ScopeIntersect`**；**§6** 增补 **`WORKSPACE_ACCESS_DENIED`** 信封示例与共存说明；与当时实现 **`BusinessScopeIntersectNode`** / **`AiWorkspaceAccessGuard`** 对齐。 |
| 2026-05-17 | **`BusinessWorkspaceRouteNode` / `AiWorkspaceAccessGuard`** 等已删除；**`WORKSPACE_ACCESS_DENIED`** 示例在 §6 **仅作历史契约参考**；现行 Graph 以 **`BusinessScopeIntersectNode`** 起始。 |

---
