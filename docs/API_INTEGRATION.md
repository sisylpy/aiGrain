> 所有涉及 AI 多智能体、经营分析、采购、库存、菜品毛利、权限、组织范围、时间范围、多轮追问的改动，必须先阅读 `docs/AI_AGENT_DEVELOPMENT_GUIDE.md`。

> 任何涉及组织范围、门店、部门、集团汇总、权限范围、经营看板查询的改动，必须先阅读 `docs/DOMAIN_ORG_MODEL.md`。本项目中 `distributerId` 是集团/配送商主体 ID；`gb_department.gbDepartmentFatherId = 0` 的记录才是门店；子部门需要归一化到所属门店。

# 联调用：多智能体 Run API 与 SSE（前后端统一协议）

本文档为 **AiRunController** 新链路的**单一事实来源**。若与旧文档或规划稿冲突，以本文为准；前端请按本节实现订阅与渲染。**后端本地编译**：请使用 **JDK 17+**，见 **`docs/JDK_MAVEN.md`**（避免出现 `无效的标记: --release`）。

**维护约定（与 `docs/TODO_MULTI_AGENT.md` 一致）**：每条业务链路在 TODO 中 **阶段收口**（或等价「该链路可交付」）时，**必须** 在此处补齐或修订 **REST / SSE 契约**（尤其是 **`answer_delta.data.*`** 与 **`AiRunController`** 相关说明），**JSON 字段名以当前 Java DTO / 序列化为准**，避免文档滞后。

### 前端联调（成本主线，`VITE_USE_MOCK=false`）

- **稳定端点**（勿改路径与动词）：`POST /api/ai/runs` → `GET /api/ai/runs/{runId}/events`（SSE）→ 可选 `POST /api/ai/runs/{runId}/stop`。
- **经营路由 vs 成本意图（`BusinessDataPlanner`）**：**显式「菜品毛利 / 菜品分析 / 哪些菜赚钱…」优先 `dish_profit_path`**（单 Tool **`dish_profit_analysis`** + **`DishProfitAgent`**，`answer_delta.data.dishProfitOverview`）。否则，含 **成本/毛利（泛指）/核销/采购/出库…** 子串会先命中 **成本类原始意图**。在 Run 挂载 **`AiUserContext`** 时执行 **意图收敛**（全额 **5 Tool + `CostDiagnosisAgent`** ｜ **采购视角 Tool 链** ｜ **优惠券端拒答**），规则与话术见 **`docs/PERMISSION_MODEL.md` §7**。再否则，话术如 **「这个月生意怎么样」** → **经营概览链**（4 Tool + `BusinessOverviewAgent`）。最终以 **`answer_delta.data.text`** 展示：**【查询范围】/【意图说明】/【权限提示】** 等可由 **`StubAnswerComposerNode`** 前置拼接；有 DeepSeek 时为其扩写段落 + 确定性摘要分段；仅调试 **`ai.agent.llm.stub=true`** 时为**纯确定性摘要**，不再向前端附带「LLM未接入」类占位句。**成本诊断卡片**仍仅 **`answer_delta.data.costDiagnosis`**（全链成功时）。
- **SSE 信封**：扁平 JSON（见下节「扁平信封」），顶层含 `event`、`runId`、`timestamp`、`status`、`displayText`，按需 `agent`、`tool`、`data` 等；**不要**解析旧的 `{ type, payload }` 形态。
- **成本诊断 UI**：**仅**在 **`purchaseCostInsightPath` / `couponCostInsightBlocked` 均未命中**且 **`CostDiagnosisAgent`** 已产出结构化结果时，使用 SSE **`answer_delta`** 的 **`data.costDiagnosis`**（稳定契约见下文）；采购视角或优惠券拒答链路**无此卡片**。**不要**依赖 **`GET /api/ai/runs/{runId}`** 的 **`answerPreview`**。
- **菜品毛利 UI**：命中 **`dish_profit_path`**（如「菜品毛利怎么样」「水煮鱼毛利怎么样」「哪些菜赚钱」）且 **`DishProfitAgent`** 已产出结构化结果时，使用 **`answer_delta.data.dishProfitOverview`**（与 **`AiDishProfitOverviewResult`** 一致，契约见下文「`dishProfitOverview`」）；正文仍为 **`data.text`**。
- **经营概览 UI**：命中 **经营概览链**（如「这个月生意怎么样」「本月经营情况怎么样」，`business_overview_path`）且 **`BusinessOverviewAgent`** 已产出结构化结果时，使用 **`answer_delta.data.businessOverview`**（与 `AiBusinessOverviewResult` 一致，契约见下文「`businessOverview`」）；正文仍以 **`data.text`** 为准，须引用 Tool 口径数字、禁止空泛编造。
- **超时**：整机 Run 当前常见 **50s+**（仅占位数据时约 **55～60s**）；**真实主键** 下若 **`dish_sales_query` 占主导**，常见 **~90s+**。联调请把 **EventSource / fetch 的 read 超时** 调到 **≥120s**；性能优化见 `docs/TODO_MULTI_AGENT.md` backlog，**非本阶段必做**。
- **跨域（CORS）**：前端在 **`http://localhost:5173`**（或其它本机 dev 端口）、API 在 **`http://localhost:8090`** 时属于**跨源**；`POST` + `application/json` 会先发 **OPTIONS** 预检。后端通过 **`WebMvcConfig` 中注册的 `CorsFilter`** 放行本地源（含显式 **`http://localhost:5173`** / **`http://127.0.0.1:5173`** 与本机端口通配），并 **`Access-Control-Allow-Credentials: true`**，以兼容 `fetch`/SSE 使用 **`credentials:'include'`**（否则浏览器会因凭据模式报 CORS 失败，即使 HTTP 状态为 200）。避免仅依赖 `WebMvcConfigurer#addCorsMappings` 时预检仍为 **403**。若 OPTIONS 仍为 403：**重启后端**使 Filter 生效，并在 Network 核对响应体是否仍为 CORS 拒绝。

---

## `AiUserContext` · `AiOrgScope` · 范围求交 · `permissionDenied`（第一、二波已落地）

> **状态（2026-05-10）**：Run 起始由 **`AiUserContextResolver`** / **`AiOrgScopeResolver`**（`POST` Body）装配上下文并写入 **`AiRunState`**；**`AiPermissionGuard`** 在 **`BusinessToolExecutionNode`**（逐 Tool）、**`CostDiagnosisAgentNode`**（`VIEW_COST`）前判定。无 **`AiUserContext`** 挂载时 Guard **放行**（兼容仅用 bare `AiRunState` 的单元测试）。**第二波**：**`AiRunScopeIntersectService`**、**`AiWorkspaceAccessGuard`** 已落地（见 **`docs/SSE_BACKEND_EVENT_CONTRACT.md`**）。**身份主数据**：**`userId` ↔ `gb_department_user`**，`gb_du_admin` → **`AiRoleMapper`** → **`roleCode` / `permissions`**（完整表见 **`docs/PERMISSION_MODEL.md`**）。

### 服务端解析顺序

```
POST /api/ai/runs
 → AiUserContextResolver
 → AiOrgScopeResolver
 → AiRunService 装配 AiRunState
 → BusinessWorkspaceRouteNode：WorkspaceRouterService + AiWorkspaceAccessGuard（工作台入口）
 → BusinessScopeIntersectNode：AiRunScopeIntersectService（请求 dept 子树 ∩ 身份锚点子树 → 写回 departmentId、AiQueryScope、刷新 AiOrgScope）
 → BusinessTimeWindowNode → DataPlanner → ToolExecution（逐 Tool：AiPermissionGuard）→ …
```

### `AiUserContext`（建议挂载在 `AiRunState` / Trace；可选 SSE 快照）

| 字段 | 说明 |
|------|------|
| `userId` | 必填；须为 **`gb_department_user.gb_department_user_id`**（Integer PK 范围） |
| `sourceAdminRole` | 原始 **`gb_du_admin`**；合成角色（见下 **`roleCode` 过渡期**）时为 null |
| `roleCode` / `roleName` | 由 **`gb_du_admin`** 经 **`AiRoleMapper`** 映射（如 `GROUP_MANAGER` / 集团管理端）；全量见 **`docs/PERMISSION_MODEL.md`** |
| `groupId` / `regionId` / `departmentId` / `storeId` / `distributerId` | 锚点：来自部门用户行 + 区域类角色推断（`regionId`） |
| `departmentFatherId` | `gb_du_department_father_id`（可选，供组织策略扩展） |
| `allowedStoreIds` | Run 内可显式遍历的部门/门店 id（非集团向常含挂靠部门） |
| `permissions` | 能力码：`VIEW_*`、`EXPORT_REPORT`、`ACCESS_*_WORKSPACE`、`MANAGE_MARKETING` 等（见 **`docs/PERMISSION_MODEL.md`**） |

### 与 `AiQueryScope` / `AiQueryScopeAccess` 的关系（第二波）

- **会话（旧链路）**：仍由 **`AiScopeResolver`** + **`AiQueryScopeAccess.narrowForDepartmentUser(departmentUserPk)`** 按 **`gb_department_user`** 收窄。
- **Run API（新链路）**：**`AiRunScopeIntersectService`** 在 **`departmentMapper` 子树**上对 **「请求 `departmentId` 展开」∩「`AiUserContext.departmentId` 锚点展开」** 求交；无交时 **回退锚点**并写入 **`AiRunState.scopeConvergenceNote`**（Composer 以 **【查询范围】** 前缀展示）。**`GROUP_MANAGER`**（**`admin=0`**）不向非集团身份错误收窄本次请求锚点，仅在有部门时填充 **`AiRunState.scope`** 快照。后续可将两路 **统一为同一 subtree 加载** 以降低双实现成本。

### `roleCode` 与真实身份体系

| 层面 | 做法 |
|------|------|
| **正式** | **`POST.userId`** → **`gb_department_user`** → **`gb_du_admin`** → **`AiRoleMapper`** → **`roleCode` + `permissions`**；请求体 **`roleCode`** **忽略**（勿与业务库冲突）。 |
| **过渡期 / 单测** | 仅当显式传 **`FINANCE_MANAGER`** 或 **`MARKETING_MANAGER`** 时跳过 DB，走合成权限（见 **`AiUserContextResolver`**）。 |
| **详细表** | admin 数值、中文、`roleCode`、默认权限、组织范围、工作台、Tool → **`docs/PERMISSION_MODEL.md`**。 |

### 工作空间入口 permission（第二波）

| `workspaceMode`（路由命中） | 所需 permission |
|-----------------------------|-----------------|
| `MARKETING_GROWTH` | `ACCESS_MARKETING_WORKSPACE` |

若缺码：降级 **`BUSINESS_CHAT`**，并发 **`error`**（**`WORKSPACE_ACCESS_DENIED`**，`data.permissionDenied` 见 **`docs/SSE_BACKEND_EVENT_CONTRACT.md`** §6）。

### `AiOrgScope`

| 字段 | 说明 |
|------|------|
| `scopeType` | `GROUP` / `REGION` / `STORE` / `DEPARTMENT` / `DISTRIBUTER` |
| 对应 ids | `groupId`、`regionId`、`storeId`、`departmentId`、`distributerId` |
| `storeIds` | 展开后的门店 id（Resolver 单次算妥） |

**`ScopeIntersect` 之后**：会按收窄后的 **`departmentId` / `distributerId`** 再 **`AiOrgScopeResolver`** 刷新一次快照，供 **`AiPermissionGuard`** 与 Trace 一致使用。与会话域 **`AiQueryScopeAccess`** 仍为 **互补**：后者按 **`gb_department_user`**，Run 本条按 **Resolver 锚点子树**（见上「与 AiQueryScope…」）。

### Tool → Permission（映射表）

| Tool / Agent 能力 | `permission` |
|-------------------|----------------|
| `revenue_query` | `VIEW_REVENUE` |
| `business_overview_query` | `VIEW_REVENUE` |
| `purchase_query` | `VIEW_PURCHASE` |
| `stock_reduce_query` | `VIEW_STOCK` |
| `dish_sales_query` | `VIEW_DISH_SALES` |
| `gross_margin_calculator` | `VIEW_COST` |
| `CostDiagnosisAgent` | `VIEW_COST` |
| （预留）导出报表 | `EXPORT_REPORT` |
| （预留）营销 Agent | `MANAGE_MARKETING` |

### 真机回归备忘（成本主线）

业务机启动后端（例 **`server.port=8090`**、`context-path=/api`）后：

```bash
curl -sS -X POST http://localhost:8090/api/ai/runs -H 'Content-Type: application/json' \
  -d '{"userId":1,"departmentId":1,"distributerId":2,"message":"帮我看本月成本怎么样"}'
# 使用返回的 runId:
curl -Ns "http://localhost:8090/api/ai/runs/<runId>/events"
```

**预期**：**`userId`** 在 **`gb_department_user`** 存在；**`gb_du_admin`** 映射为可读 **`roleCode`**（如 **`admin=0` → `GROUP_MANAGER`**，原 **`GROUP_BOSS` 别名已废弃**）；**`ScopeIntersect`** 对 **`GROUP_MANAGER`** 不错误收窄本次业务样例中的 **1/2** 锚点；五 Tool 不因权限误拦截；**`answer_delta.data.costDiagnosis`** 有结构；**`run_finished.status`** 为 **`completed`**；卡片联调仍看 **`answer_delta.data.costDiagnosis`**。

**已验一回（2026-05-10，本机，`runId=1778350824377`）**：`WorkspaceRouter`→**`BUSINESS_CHAT`**；**`ScopeIntersectNode`** 完成，`resolvedDepartmentCount:3`；**DataPlanner** 五 Tool 全编排；**五 Tool** `tool_finished.success` 均为 **`true`**（其中 **`dish_sales_query`** 约 **31s**）；**`CostDiagnosisAgent`** `riskLevel:data_incomplete`、`needMoreData:false`；**`answer_delta`** 含 **`data.costDiagnosis`** 与 **`data.text`**；无 **`event:error`** / **`permissionDenied`**；**`run_finished`** **`status:completed`**。**`GET /api/ai/runs/{id}`** 返回 **`status:COMPLETED`**、**`workspaceMode:BUSINESS_CHAT`**。

> **说明**：无监听端口的 IDE/CI Agent 环境可能无法代抓取 SSE；门禁仍以 **`mvn test`** 为准。真机可把 **`curl -Ns .../events`** 输出重定向留档。

### SSE `permissionDenied`（挂载在 `error` 的 `data` 内）

与 **`TOOL_PERMISSION_DENIED`** 等并存时，结构化字段落在 **`publishError`** 写入的 **`data.permissionDenied`** 内；**不向用户抛出未捕获 500**。以下仅为 **`data`** 对象的典型内核（外层完整 **`error`** 信封见 SSE 契约 §6）：

```json
{
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
}
```

说明：**`subject`** 为被拒的 Tool id 或 **`CostDiagnosisAgent`**；**`requiredPermission`** 可为空（纯组织越权时只有 **`reason`**/**`suggestedScope`**）。

完整 **`error`** 信封见 **`docs/SSE_BACKEND_EVENT_CONTRACT.md`** §6。

---

## Bean 命名与 SSE「agent」字段

- **`SSE 中的 `agent` 字段是产品展示名称**，用于 UI 与人类可读日志，**不一定等于 Spring Bean 名称**。  
  例：`WorkspaceRouterAgent` 为展示名；实际编排 Bean 为 **`WorkspaceRouterService`**，图节点为 **`BusinessWorkspaceRouteNode`**（`name()` = `WorkspaceRoute`）。

---

## REST 前缀

应用配置 **`server.servlet.context-path=/api`**（见 `src/main/resources/application.properties`）。以下路径均需此前缀拼接。

---

## REST 契约（终审）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/ai/runs` | 创建异步 Run，返回 `runId` |
| `GET` | `/api/ai/runs/{runId}` | 内存态查询 Run 状态（非 DB）。**生产 UI 勿依赖**除 `status`/`cancelled`/`answerPreview` 外的字段。响应始终含 **`harnessDebug.debugContextEnabled`**；联调设 `ai.harness.debug-context-enabled=true` 时另有 **`harnessDebug.resolvedQueryContextPresent`**，且仅当为 `true` 时附带 **`harnessDebug.resolvedQueryContextSummary`**（`AiResolvedQueryContext` 摘要，**非稳定契约**、仅调试）。 |
| `GET` | `/api/ai/runs/{runId}/events` | **SSE** 订阅该 Run 事件流 |
| `POST` | `/api/ai/runs/{runId}/stop` | 协作式取消（节点轮询 `cancelled`） |

### `POST /api/ai/runs`

**Request Body（JSON）**

```json
{
  "conversationId": null,
  "userId": 1,
  "departmentId": null,
  "distributerId": null,
  "roleCode": null,
  "message": "用户的自然语言输入"
}
```

- **`userId`**、**`message`** 必填（服务端校验）。
- **`roleCode`**：**默认不传**。正式链路以 **`gb_department_user.gb_du_admin`** → **`AiRoleMapper`** 为准；仅 **`FINANCE_MANAGER`**｜**`MARKETING_MANAGER`** 保留为过渡期合成角色（单测）。完整 **`admin`/`roleCode`/权限** 见 **`docs/PERMISSION_MODEL.md`**。
- **`conversationId`** 等与现有业务对齐后可传。

**成功响应**：与现有 **`R`** 包装一致，`code===0`，并包含：

```json
{
  "code": 0,
  "runId": 1778339369299,
  "status": "STARTED"
}
```

**参数错误**：`code` 为 `400`，`msg` 为提示文案。

订阅 SSE 建议在 **紧邻 POST 成功后**立刻 `GET …/events`；服务端会在会话缓冲中回放已产生事件（仍可连接晚到客户端）。

### `POST /api/ai/runs/{runId}/stop`

协作式取消：请求后各 `AgentNode` 在下一轮开始前看到 `state.cancelled`，可提前结束链路；**不保证**正在执行中的 JDBC/HTTP 立刻中断。

**示例**

```bash
curl -s -X POST http://localhost:8090/api/ai/runs/1778339369299/stop \
  -H 'Content-Type: application/json'
```

**成功时**（`R`：`code===0`）：通常包含 `runId` 与 `status`：

```json
{
  "code": 0,
  "runId": 1778339369299,
  "status": "CANCEL_REQUESTED"
}
```

若 `runId` 不存在：`404`（`ResponseStatusException`，非 `R` 错误体）。

---

帧级原始 `event:` / `data:` 行、终态与自检说明见 **`docs/SSE_BACKEND_EVENT_CONTRACT.md`**。

## SSE 契约（终审）：**扁平信封** — 废止 `{ type, payload }`

前后端约定 **不再** 使用形如 `{ "type": "...", "payload": {...} }` 的嵌套；统一采用 **单层 JSON**，与 Spring `SseEmitter` 每条 `data` 一致：

| 字段 | 必选 | 说明 |
|------|------|------|
| `event` | 是 | 与 SSE 原生 `event:` 同名；亦为 JSON 内业务事件名 |
| `runId` | 是 | 本次 Run 唯一 id |
| `timestamp` | 是 | ISO-8601 带时区，当前为 **`Asia/Shanghai`** 偏移 |
| `status` | 是 | 面向 UI 的运行态：`running`、`completed`、`failed` 等小写语义字 |
| `displayText` | 是 | 给人看的进度/结果短描述 |
| `agent` | 否 | 展示用智能体名（非 Spring Bean 名） |
| `tool` | 否 | 工具名（若有） |
| `data` | 否 | 结构化附加负载。**`answer_delta` 中必选 `text`（与根级 `text` 兼容副本）**；若走成本主线，可含 **`costDiagnosis`**；若走经营概览链，可含 **`businessOverview`**（与 `BusinessOverviewAgent` 输出一致）；若走 **`dish_profit_path`**，可含 **`dishProfitOverview`**（`AiDishProfitOverviewResult`，camelCase）；若走 **`purchase_overview_path` / `purchaseCostInsightPath`** 且 Composer 已写入快照，可含 **`purchaseOverview`**（与 **`PurchaseOverviewTool`** 产出的 `purchaseOverview` 对象同源，`camelCase`）。 |
| 其它 | — | 各事件可附带 `message`、`type`、`workspaceMode`、`text` 等；前端应容错未知字段 |

**示例**

```json
{
  "event": "agent_started",
  "runId": 1,
  "timestamp": "2026-05-09T23:06:08.781+08:00",
  "status": "running",
  "displayText": "正在识别任务类型…",
  "agent": "WorkspaceRouterAgent"
}
```

- `tool`、`data` 在无内容时可能 **不出现**（勿强依赖 `"tool": null`）。

### 原生 SSE 帧

- **`event:`** 与各条 JSON 的 **`event`** 字段一致。
- **`data:`** 为单行 JSON（UTF-8）。

### EventSource / 自解析：**`onmessage` 不会收到命名事件（空白页元凶）**

本链路 **每一条** SSE 帧都带 **`event: run_started` / `answer_delta` / `run_finished` 等**，不是匿名 `message`。

- **原生 `EventSource`**：只有在 **没有 `event:` 行**（或等价于默认类型 **`message`**）时，浏览器才会触发 **`EventSource.prototype.onmessage`**。  
  因此若前端只写了 **`es.onmessage = …`**，`run_started`、`answer_delta`、`run_finished` **全部进不来**，表现像「跑完了但没内容」——**Network 里 Response 实际是完整 SSE**。
- **正确写法**：为每种业务事件单独监听，或对 `answer_delta` 至少注册一次：

```js
const es = new EventSource(`${apiBase}/ai/runs/${runId}/events`, { withCredentials: true }); // credentials 时需后端 Allow-Credentials
es.addEventListener('answer_delta', (e) => {
  const envelope = JSON.parse(e.data); // e.data 为单行 UTF-8 JSON
  const body = envelope.data?.text ?? envelope.text ?? '';
  const costCard = envelope.data?.costDiagnosis; // 成本卡片，可选
  const overviewCard = envelope.data?.businessOverview; // 经营概览卡片，可选
  const dishProfitCard = envelope.data?.dishProfitOverview; // 菜品毛利卡片，可选
  const purchaseCard = envelope.data?.purchaseOverview; // 采购入库概览快照，可选（purchase_overview / 采购视角）
  // 渲染 body / costCard / overviewCard / dishProfitCard / purchaseCard …
});
es.addEventListener('run_finished', () => {
  es.close();
});
// error、run_started… 按需 addEventListener
```

- **`fetch` + `ReadableStream` 自解析**：按 SSE 文本协议按 `\n\n` 分帧，读 **`event:`** 行的名字再分发；**不要**仅用 `reader.read()` 的 chunk 当「一行 JSON」——一块里可能不含完整事件或对帧不对齐。

### Business 成本主线（第一版）

当语义命中 **BUSINESS_CHAT + 成本类意图**（如「本月成本」「毛利」「采购」「损耗」「经营」），且请求体建议携带 **`departmentId`（部门父 id）与 `distributerId`**（用于真实 Tool 接线）时，典型事件序列包括：

1. `run_started`
2. `agent_started` / `agent_finished`，`agent`：`WorkspaceRouterAgent`
3. `agent_started` / `agent_finished`，`agent`：`TimeWindowNode`
4. `agent_started` / `agent_finished`，`agent`：`DataPlannerNode`
5. `agent_started`，`agent`：`ToolExecutionNode`
6. 多组 `tool_started` / `tool_finished`
7. `agent_finished`，`agent`：`ToolExecutionNode`
8. `agent_started` / `agent_finished`，`agent`：`CostDiagnosisAgent`
9. `review_started` / `review_finished`
10. `agent_started` / `agent_finished`，`agent`：`AnswerComposerNode`
11. `answer_delta`（`data.text` + 可选 `data.costDiagnosis`，见下节「稳定契约」）
12. `run_finished`

### 单次 Business（非成本或未编排工具）

在无成本编排或链路被跳过（例如仅占位对话）时，事件仍至少包含：

1. `run_started`
2. `agent_started` / `agent_finished`（可多对）
3. `answer_delta`（正文在 **`data.text`** 与根级 **`text`** 均有，`text` 为兼容副本）
4. `run_finished`
5. 异常路径：`error`，随后仍有 `run_finished`

---

## `answer_delta.data.costDiagnosis` 稳定契约（前端）

成本主线完成且走通 **CostDiagnosisAgent** 时，`answer_delta` 的 **`data`** 对象结构与 **`GET /api/ai/runs/{runId}` 无关**──后者仅有截断字段 **`answerPreview`**；**请以 SSE `answer_delta` 为准** 渲染「诊断卡片」（或后续若扩展 REST 快照再对齐）。

数据来源：后端 **`AiCostDiagnosisResult`**，经 Fastjson 序列化进 `data.costDiagnosis`：**字段名为 camelCase，须视为稳定契约**（与该类字段一致）。

| JSON 字段 | 类型 | 说明 |
|-----------|------|------|
| `agentName` | `string` | 固定可读名，当前默认为 `CostDiagnosisAgent` |
| `summary` | `string` | 一句话综述 |
| `riskLevel` | `string` | 粗分级：`ok` / `warning` / `high` / **`data_incomplete`**（核销/出库等数据不足或入库-核销链路疑似断点时使用，不等同于经营「高危」）；规则见 **`CostDiagnosisAgentNode`** |
| `keyMetrics` | `array<object>` | 每项至少含 **`name`**、**`value`**；可有 **`unit`**（见下方示例） |
| `findings` | `array<string>` | 发现条目 |
| `recommendations` | `array<string>` | 建议条目 |
| `needMoreData` | `boolean` | 是否需要更多上下文/数据再继续 |
| `questions` | `array<string>` | 需用户补充的简短问题 |

**`keyMetrics` 单项示例**

```json
{ "name": "统计天数", "value": 31, "unit": "天" }
```

**`costDiagnosis` 整体示例**（占位数值，仅供形态参考）

```json
{
  "agentName": "CostDiagnosisAgent",
  "summary": "本月成本存在改进空间，采购与核销/损耗需重点关注。",
  "riskLevel": "warning",
  "keyMetrics": [
    { "name": "统计天数", "value": 31, "unit": "天" },
    { "name": "区间营业额(日营收汇总)", "value": "128000", "unit": "元" }
  ],
  "findings": ["…"],
  "recommendations": ["…"],
  "needMoreData": false,
  "questions": []
}
```

前端展示 **Agent 过程**：仍订阅 **`GET /api/ai/runs/{runId}/events`**，依赖各 `event`、`agent`、`tool`、`displayText`；**不要将 `costDiagnosis` 与 Tool 原始 envelope 混为同一 Redux slice**，避免耦合。

---

## `answer_delta.data.businessOverview` 稳定契约（前端）

经营概览链（「这个月生意怎么样」等，`business_overview_path`：**`business_overview_query` → `dish_sales_query` → `purchase_query` → `gross_margin_calculator`**）走完 **`BusinessOverviewAgent`** 后，SSE **`answer_delta`** 的 **`data.businessOverview`** 为 **`AiBusinessOverviewResult`** 的 Fastjson 序列化：**字段名为 camelCase，视为稳定契约**。

| JSON 字段 | 类型 | 说明 |
|-----------|------|------|
| `agentName` | `string` | 默认 `BusinessOverviewAgent` |
| `summary` | `string` | 一句话结论（须与营业额等真实指标一致） |
| `riskLevel` | `string` | `ok` \| `warning` \| `high` \| `data_incomplete` |
| `keyMetrics` | `array<object>` | `{ name, value, unit? }`，由 Agent 将看板/Tools 收口 |
| `findings` | `array<string>` | 基于数据的观察 |
| `recommendations` | `array<string>` | 可执行建议（禁止脱离指标空泛泛化） |
| `needMoreData` | `boolean` \| 省略 | 是否需补数据再继续 |
| `questions` | `array<string>` | 需用户补充的问题 |
| `dashboardStatsCn` | `object` | 与日营收 **`buildStatsDashboard`** 输出的 **`stats`** 对齐的扁平中文键值副本（无前端的卡片可直接用之渲染） |
| `dashboardBindings` | `object` | 看板 **`dashboard.bindings`** 副本（英文键） |
| `overviewScope` | `object` | 可选。集团/门店**查询口径与门店覆盖**：`scopeType`（`GROUP` \| `STORE`）、`scopeName`、`aggregationModeHint`、`departmentCount`、`visibleDepartmentNodeCount`、`dataAvailableDepartmentCount`、`dataMissingDepartmentCount`、`primaryBanner`、`coverageDetail`。**集团广角**还可能含命名门店计数：`dataMissingStoreCount`、`attentionStoreCount`（≠ 抽象的「节点缺记录」：`dataMissingDepartmentCount`）。仅业务语义与自然语言，**不透明部门主键**。 |
| `dataMissingStores` | `array<object>` | 可选。集团广角 v1：**数据缺口**命名门店清单（画像未配、子树内无日营收记录等）；**与经营异常分列**，无 `riskLevel`。**权限**：本轮仅 **`GROUP_MANAGER`** 返回按集团可视范围归因的条目；店长/采购等后续再接角色裁剪。每项：`storeName`、`reason`。 |
| `attentionStores` | `array<object>` | 可选。**有可用日营收台账**前提下，启发式经营关注点（采购入库高于营业额、单量偏低等）。每项：`storeName`、`reason`、`riskLevel`（如 `warning` \| `high`）。 |
| `priorityStoresBrief` | `string` \| 省略 | 可选。供正文/模型点到为止：**至多 Top3** 家门店的一句话摘要（与 `business_overview_query.data.priorityStoresBrief` 同源）；**完整列表仅在** `dataMissingStores` **`/`** `attentionStores`。

与 **`business_overview_query` Tool**：`tool_finished` 成功后 **`data.data.stats`**、`data.days`、`totalRevenue`、`dashboardBindings`、`anomalyHints` 等与旧版 **`GET …/daily-revenue/stats/{id}`** 同源；**集团广角且聚合成功时**，同层 **`data` 还可能含** `dataMissingStores`、`attentionStores`、`priorityStoresBrief`（camelCase）；Composer 优先引用 **`toolResults.business_overview_query`** 中的指标，**勿**编造未返回数字。

**集团管理端（`GROUP_MANAGER`，第一版口径说明）**：`BusinessToolExecutionNode` 仍会向 Tool 传入 **`POST` 的 `departmentId`**（解释为「父部门/餐厅」键，与日营收 Mapper 一致）。**`AiRunScopeIntersectService`** 对集团广角账号**不写回**收窄后的单体门店 ID，因而若传入的是「集团组织根节点」而非某一门店的经营父 department，常与 **`gb_ai_restaurant_profile`** / 日营收表粒度不匹配。此时 **`business_overview_query`** 会带上 **`groupWideOverviewHint`**，失败载荷中的 **`failureKind`** / **`note` / `anomalyHints`** 会说明「**集团多维汇总暂未接入**」，**不再**以「门店画像未配置」作为唯一话术。下属门店 rollup 详见 **`docs/TODO_MULTI_AGENT.md`「集团经营概览聚合口径」**。

**`businessOverview` 示例**（占位，仅供形态）

```json
{
  "agentName": "BusinessOverviewAgent",
  "summary": "本月截至目前本门店营业额与订单情况如下，后续可关注采购与核销侧数据完整性。",
  "riskLevel": "ok",
  "keyMetrics": [
    { "name": "总营业额", "value": "32800", "unit": "元" },
    { "name": "统计天数", "value": 10, "unit": "天" }
  ],
  "findings": ["..."],
  "recommendations": ["..."],
  "needMoreData": false,
  "questions": [],
  "dashboardStatsCn": { "总营业额": "32800", "日均营业额": "3280", "统计天数": 10 },
  "dashboardBindings": {},
  "overviewScope": {
    "scopeType": "GROUP",
    "scopeName": "集团汇总（账号可见组织）",
    "aggregationModeHint": "GROUP_SQL_ROLLUP",
    "departmentCount": 12,
    "visibleDepartmentNodeCount": 380,
    "dataAvailableDepartmentCount": 10,
    "dataMissingDepartmentCount": 2,
    "primaryBanner": "你当前账号可查看集团范围。",
    "coverageDetail": "本期系统识别到 x 家门店有日营收数据…（或「共 y 家有数据、z 家暂无」）；下面按有数据门店汇总分析。",
    "dataMissingStoreCount": 2,
    "attentionStoreCount": 1
  },
  "dataMissingStores": [
    { "storeName": "朝阳店", "reason": "暂无日营收记录" },
    { "storeName": "国贸店", "reason": "餐厅画像未配置" }
  ],
  "attentionStores": [
    {
      "storeName": "望京店",
      "reason": "采购入库额高于营业额，需要结合库存出库核对",
      "riskLevel": "warning"
    }
  ],
  "priorityStoresBrief": "需要优先关注的门店：\n1. 朝阳店：暂无日营收记录；"
}
```

---

## `answer_delta.data.purchaseOverview` 稳定契约（前端）

用于 **`purchase_overview_path`** 专用 **`purchase_overview`** 工具，以及 **`purchaseCostInsightPath`**（经营问句收敛为采购视角、`purchase_overview` + `stock_reduce_query` 等）下，**`StubAnswerComposerNode`** 从 Tool 信封提取并挂入 Run、随 **`answer_delta.data`** 一并下发的 **采购入库概览** 快照。结构为 **`PurchaseOverviewTool`** 中 `inner.purchaseOverview` 的 **`Map`**（Fastjson **`camelCase` 键名**），视为稳定契约；**请以 SSE `answer_delta` 为准**，勿依赖 **`GET /api/ai/runs/{runId}`** 的 **`answerPreview`**。

### 字段说明（与 `PurchaseOverviewTool` 一致）

| JSON 字段 | 类型 | 说明 |
|-----------|------|------|
| `queryScopeBanner` | `string` \| 省略 | **`BusinessToolExecutionNode`** 注入的开篇范围句（集团 / 店长 / 采购员 / 库管等角色文案不一）。 |
| `visibleStores` | `array<object>` | 解析后的可见门店：`storeDepartmentId`、`storeName`。 |
| `coveredStores` | `array<object>` | 本期 **有采购金额** 的门店摘要：`storeDepartmentId`、`storeName`、`purchaseSubtotal`（string，1 位小数）。 |
| `dataMissingStores` | `array<object>` | 同上结构，本期 **采购额为 0** 的可见门店。 |
| `storeCoverageSummary` | `string` \| `null` | **仅集团聚合**（`groupPurchaseAggregation=true`）时的人话覆盖句：枚举可见店、区分「均有采购」/「部分有、部分暂无」等。 |
| `purchaseOrderCount` | `number` | 采购明细行数 / 笔数（与统计 SQL 一致）。 |
| `totalPurchaseAmount` | `string` | 采购金额合计（1 位小数字符串，单位元）。 |
| `purchaseMethodBreakdownSupported` | `boolean` | 为 `true` 时，「按采购方式」分项与总笔数、总金额已对账，可对用户口述 **`purchaseMethodSummaryFragment`**。 |
| `purchaseMethodBreakdown` | `array<object>` | 采购方式分项：`label`（如「供货商采购」「自采」「其它方式」）、`lineCount`、`amountYuan`（1 位小数字符串）。 |
| `purchaseMethodSummaryFragment` | `string` \| 省略 | 中文片段，形如 `供货商采购4笔、金额3150.0元；自采2笔、金额153.0元`（无句首「其中」）。 |
| `purchaseMethodNote` | `string` \| 省略 | 当 `purchaseMethodBreakdownSupported=false` 时，说明为何不拆分（勿对用户照抄字段名）。 |
| `goodsPurchaseFrequencyTop` | `array<object>` | 合并同名/标准名后的 **采购次数 Top**：`goodsName`、`purchaseTimes`（number）。 |
| `goodsPurchaseAmountTop` | `array<object>` | 同上合并规则下的 **采购金额 Top**：`goodsName`、`purchaseSubtotal`（string，元）。 |
| `topGoods` | `array<object>` | 兼容保留：Top 商品 `kind`（`by_times` / `by_amount` ）、`goodsName`、`purchaseTimes` 或 `purchaseSubtotal`（与上两项同源数据，格局可能交错）。 |
| `topSuppliers` | `array<object>` | Top 供货商：`supplierId`、`supplierName`（有则库内真名；否则 **`未维护供货商名称`** 或 **`供货商ID {id}（名称未维护）`**）、`totalPurchaseAmount`、`purchaseLineCount`。 |

**口径**：总笔数、总金额、采购方式拆分、商品 Top、供货商 Top 与 **`purDepIds`**（由集团 **`visibleStores` → 门店根 → `expandStoreRootsToDailyRevenueScopeIds`**）同一套筛选；采购方式与旧版 `GbAiChatServiceImpl#appendPurchaseSupplyMixSummary` 一致：**`gb_DPG_purchase_type`=5 或（=1 且 `gb_DPG_purchase_nx_supplier_id` 为正）**计为「供货商采购」；**type=1 且 nx 为 null 或 -1** 计为「自采」；其余 `purchase_type` 为「其它方式」；退货类型 9 已排除。AI 答复**不再提供** `totalPurchaseWeight`：采购数量单位混杂（斤/瓶/箱等），总重量统一「斤」易误导。
| `priceChangeItems` | `array<object>` | 价格波动项：`goodsName`、`minPrice`、`maxPrice`、`priceFluctuationPercent`。 |
| `highAmountItems` | `array<object>` | 高金额采购项：`goodsName`、`purchaseSubtotal`。 |
| `purchaseWithoutSalesItems` | `array` | 预留，当前多为空数组。 |
| `recommendations` | `array<string>` | 简短建议列表。 |

### 示例（形态示意）

```json
{
  "queryScopeBanner": "你当前可查看集团范围，本次识别到 2 家门店：AAA、汀兰餐厅。下面按集团范围汇总采购入库。",
  "visibleStores": [
    { "storeDepartmentId": 101, "storeName": "AAA" },
    { "storeDepartmentId": 102, "storeName": "汀兰餐厅" }
  ],
  "coveredStores": [
    { "storeDepartmentId": 101, "storeName": "AAA", "purchaseSubtotal": "2976.0" }
  ],
  "dataMissingStores": [
    { "storeDepartmentId": 102, "storeName": "汀兰餐厅", "purchaseSubtotal": "0.0" }
  ],
  "storeCoverageSummary": "本次识别到 2 家门店：AAA、汀兰餐厅。其中 AAA 有采购数据，汀兰餐厅暂无采购记录。",
  "purchaseOrderCount": 6,
  "totalPurchaseAmount": "3303.0",
  "purchaseMethodBreakdownSupported": true,
  "purchaseMethodBreakdown": [
    { "label": "供货商采购", "lineCount": 4, "amountYuan": "3150.0" },
    { "label": "自采", "lineCount": 2, "amountYuan": "153.0" }
  ],
  "purchaseMethodSummaryFragment": "供货商采购4笔、金额3150.0元；自采2笔、金额153.0元",
  "goodsPurchaseFrequencyTop": [
    { "goodsName": "鲜三黄鸡", "purchaseTimes": 2 },
    { "goodsName": "海天5度白醋", "purchaseTimes": 2 }
  ],
  "goodsPurchaseAmountTop": [
    { "goodsName": "海天5度白醋", "purchaseSubtotal": "2970.0" },
    { "goodsName": "去皮核桃仁", "purchaseSubtotal": "144.0" }
  ],
  "topGoods": [],
  "topSuppliers": [],
  "priceChangeItems": [],
  "highAmountItems": [],
  "purchaseWithoutSalesItems": [],
  "recommendations": ["建议结合采购金额 Top…"]
}
```

（`storeCoverageSummary` 在「各家均有采购」时为「…均有采购入库数据。」；全无时为「…本期均无采购入库记录。」；字段值以运行为准。）

---

## `answer_delta.data.warehouseOverview` 稳定契约（前端）

用于 **`warehouse_stock_overview_path`**（工具 **`warehouse_stock_overview`**）：库存概览链路完成后，`StubAnswerComposerNode` 将 Tool 返回的 **`data.warehouseOverview`** 快照写入 RunState，SSE **`answer_delta`** 的 **`data.warehouseOverview`** 与该 **`Map` 同源**（Fastjson 序列化，**camelCase 键名以 `WarehouseStockOverviewTool` 为准**）。

### 组织与范围

| JSON 字段 | 类型 | 说明 |
|-----------|------|------|
| `queryScopeBanner` | `string` | 人读范围句（与 `BusinessToolExecutionNode` 注入的 `AiResolvedQueryContext` 对齐）。 |
| `scopeType` | `string` | `GROUP`（集团多门店合并）或 `STORE`（单门店/库房）。 |
| `scopeName` | `string` | 范围标签，如「集团范围」「单门店/库房范围」。 |
| `visibleStores` | `array<object>` | **可见门店**（权限解析结果透传；元素常见键：`storeDepartmentId`、`storeName`；集团合并场景下也可能由聚合回填）。 |
| `visibleWarehouses` | `array` | **可见库房**（有则来自解析入参透传；结构以运行时注入为准）。 |
| `visibleStoreCount` | `number` | 纳入统计的门店根数量（集团合并）。 |
| `dataAvailableStoreCount` | `number` | **有库存信号**的门店数（集团）。 |
| `dataMissingStoreCount` | `number` | **暂无库存信号**的门店数（集团）。 |
| `coveredStores` | `array<object>` | **有库存数据的门店**摘要（集团；元素可能含 `departmentId`/`name` 与归一化后的 `storeDepartmentId`/`storeName`）。 |
| `dataMissingStores` | `array<object>` | **暂无库存数据的门店**摘要（集团）。 |

### 汇总指标（与 Tool 聚合一致）

| JSON 字段 | 类型 | 说明 |
|-----------|------|------|
| `summary` | `string` | 一句话/一段话综述（含种数、批次、金额重量、核销分型要点）。 |
| `stockItemCount` | `number` | **仍有账面剩余的商品种数**（库存商品数）。 |
| `stockBatchRowCount` | `number` | **库存批次行数**（全库可见批次约计）。 |
| `totalStockAmount` | `number` | **库存剩余金额**汇总（元）。 |
| `totalStockWeight` | `number` | **库存剩余重量**汇总（业务展示常见单位为斤，条目中可有 `weightDisplayUnit`）。 |
| `inboundAmount` | `number` | 查询区间内 **入库金额**（元）。 |
| `inboundWeight` | `number` | 查询区间内 **入库重量**。 |
| `produceAmount` | `number` | 核销 **出品** 金额（元）。 |
| `outboundAmount` | `number` | 与出品侧汇总一致字段（集团合并内与出品合计同源；实现见 `WarehouseStockOverviewTool`）。 |
| `stockReduceAmount` | `number` | 核销 **各类型合计**金额（出品 + 损耗 + 报损 + 退货，元）。 |
| `wasteAmount` | `number` | 核销 **损耗** 金额（元）。 |
| `lossAmount` | `number` | 核销 **报损** 金额（元）。 |
| `returnAmount` | `number` | 核销 **退货** 金额（元）。 |

### 关注清单（与正文三段标题对齐）

| JSON 字段 | 含义 |
|-----------|------|
| `lowStockItems` | **低库存 / 需补货**（如 `goodsName`、`restWeightTotal`、`restAmountTotal`、`note`；可有 `storeName`、`goodsId`）。 |
| `overStockItems` | **库存偏高 / 建议优先消耗**。 |
| `inactiveStockItems` | **早入库仍有剩余批次**（建议盘点）；元素可含 `goodsName`、`batchDate`、`restWeight`、`stockBatchId`、`weightDisplayUnit` 等。 |
| `priorityStocktakeItems` | 与 **`inactiveStockItems` 同源镜像**，供前端/卡片与 Composer 第三段「早入库批次 / 建议盘点」一致读取。 |

其他：**`recommendations`**（`array` 建议文案）；异常降级时可能出现 **`queryErrorCode`** / **`queryErrorMessage`**。

**话术语义（岗位）**：集团管理端（`GROUP_MANAGER`）开篇为集团客观句，勿默认「店长」；门店店长（`STORE_MANAGER`）可称「店长」；库管（`WAREHOUSE_MANAGER` 等）用可查看库房/门店客观句；采购岗库存侧可用「以下按采购视角分析」——见 `StubAnswerComposerNode` 库存 Composer 与称谓指令。

---

## `answer_delta.data.dishProfitOverview` 稳定契约（前端）

用于 **菜品毛利 / 菜品利润分析**（如「这个月菜品毛利怎么样」「哪些菜赚钱」）。**`dish_profit_path`**（DataPlanner：`dishProfitPath=true`，工具 **`dish_profit_analysis`**）走完 **`DishProfitAgent`** 后，SSE **`answer_delta`** 的 **`data.dishProfitOverview`** 为 **`AiDishProfitOverviewResult`** 的 Fastjson 序列化：**字段名为 camelCase，视为稳定契约**。与 **`businessOverview` / `costDiagnosis`** 一样，请以 **`answer_delta`** 为准；**勿**依赖 **`GET /api/ai/runs/{runId}`** 的 **`answerPreview`**。

### 集团范围与门店字段（与 `AiResolvedQueryContext` 对齐）

- **集团用户**的 **`dishProfitOverview`** 范围须来自 **`AiResolvedQueryContext.orgScope.visibleStores`**（解析后的组织口径），而非仅把请求体里的管理部门 `departmentId` 当作门店。
- **`visibleStores`**：**权限范围内**识别到的全部门店根列表（`AiOverviewVisibleStoreItem`：`storeDepartmentId`、`storeName`）。
- **`coveredStores`**：本轮**有菜品销售 / 毛利汇总数据**的门店（同形对象列表）。
- **`dataMissingStores`**：**在可见范围内**但本轮**暂无菜品侧数据**的门店；元素为 **`AiOverviewStoreIssueItem`**（`storeDepartmentId`、`storeName`、`reason`、`riskLevel` 可选），语义上以 `reason` 说明缺口。
- 辅助计数（与列表一致时可快速展示）：**`visibleStoreCount`**、**`dataAvailableStoreCount`**、**`dataMissingStoreCount`**。
- **`queryScopeBanner`**：开篇人类可读范围句（可见门店家数、店名枚举等），与经营概览 **`businessOverview.queryScopeBanner`** 风格对齐。
- **`scopeType` / `scopeName`**：透视范围标签（如 `GROUP`、可读「集团范围」等），与 **`AiDishProfitOverviewResult`** 一致。

### 汇总金额与综合毛利率

| JSON 字段 | 类型 | 说明 |
|-----------|------|------|
| `agentName` | `string` | 默认 `DishProfitAgent` |
| `summary` | `string` | 综合结论文本（含销售额、理论/实际成本、毛利额、综合毛利率等要点） |
| `statStartDate` / `statEndDate` | `string` | 统计区间（与 **`AiResolvedQueryContext.timeWindow`** / Run 状态解析一致） |
| `dishCount` | `number` | 本轮参与汇总的菜品行数 |
| `totalDishSalesAmount` | `string` | **菜品标价销售额**汇总（元，plain string，与旧版标价收入口径一致） |
| `totalTheoreticalCost` | `string` | 配方**理论成本**汇总（元） |
| `totalActualCost` | `string` | **实际出库成本**汇总（元；实现上对应旧版生产出库分摊口径，**对客表述勿写 type1**） |
| `grossProfitAmount` | `string` | 毛利额（标价销售额 − 与 `totalActualCost` 同口径的实际成本） |
| `grossProfitRate` | `string` | 综合毛利率可读串（如 **`88.67%`**），无收入或不适用时为「暂不适用」等文案 |
| `grossProfitRateUncertain` | `boolean` | **`true`** 时：**前端与文案均不得**将综合毛利率表述为**已审计的最终准确毛利**；应明确为 **「按当前可取得成本粗算，仅供参考」**，与 `summary` 中不确定说明一致 |
| `riskLevel` | `string` | `ok` \| `warning` \| `data_incomplete` 等 |
| `recommendations` | `array<string>` | 可执行建议 |

### 三类菜品列表（`AiDishProfitDishBrief`）

列表元素字段（camelCase）：**`dishName`**、**`salesQty`**、**`salesAmount`**、**`theoreticalCost`**、**`actualCost`**、**`grossProfitAmount`**、**`grossProfitRate`**、**`mainCostItems`**、**`riskReason`**。

| JSON 字段 | 含义 |
|-----------|------|
| **`reliableProfitDishes`** | **成本数据相对完整**、**毛利表现较好**的菜（可进「高毛利/表现好」卡片）。 |
| **`lowProfitDishes`** | **低毛利**，或 **实际成本明显高于理论成本**（出库相对配方放大）的菜；**仅**在成本口径已相对可信时归入此类。 |
| **`costDataIncompleteDishes`** | **成本数据不完整**（缺 BOM/出库核销等）、**单项毛利率不可信**的菜（例如表面 **100%** 但无完整成本）。 |

**强制分列规则**：**因成本缺失**导致的 **100%** 等「虚高」毛利率 **不得** 归入 **`reliableProfitDishes`**，**必须** 归入 **`costDataIncompleteDishes`**。

兼容字段：**`topProfitDishes`** 与 **`reliableProfitDishes`** 同源（旧名保留，前端可优先读 **`reliableProfitDishes`**）。**`abnormalDishes`** 为异常/关注列表（与 Agent 实现一致，可与低毛利等并列展示）。

数据来源：**`GbDepFoodBusinessInsightService#buildInsight`**（与旧版 Insight 对齐）。权限与采购/库房收敛见 **`PERMISSION_MODEL.md`**（ **`dish_profit_analysis`** 行）。

### `dishProfitOverview` 示例（形态示意，金额类型以源码为准均为 string）

```json
{
  "agentName": "DishProfitAgent",
  "summary": "（综合结论文本，含标价销售额与综合毛利率说明…）",
  "statStartDate": "2026-05-01",
  "statEndDate": "2026-05-10",
  "scopeType": "GROUP",
  "scopeName": "集团范围",
  "queryScopeBanner": "你当前可查看集团范围，本次识别到 2 家门店：AAA、汀兰餐厅。",
  "visibleStores": [
    { "storeDepartmentId": 1, "storeName": "AAA" },
    { "storeDepartmentId": 3, "storeName": "汀兰餐厅" }
  ],
  "grossProfitRateUncertain": true,
  "visibleStoreCount": 2,
  "dataAvailableStoreCount": 2,
  "dataMissingStoreCount": 0,
  "coveredStores": [
    { "storeDepartmentId": 1, "storeName": "AAA" },
    { "storeDepartmentId": 3, "storeName": "汀兰餐厅" }
  ],
  "dataMissingStores": [],
  "dishCount": 6,
  "totalDishSalesAmount": "5651",
  "totalTheoreticalCost": "366.06",
  "totalActualCost": "640.05",
  "grossProfitAmount": "5010.95",
  "grossProfitRate": "88.67%",
  "reliableProfitDishes": [],
  "lowProfitDishes": [],
  "costDataIncompleteDishes": [],
  "topProfitDishes": [],
  "abnormalDishes": [],
  "recommendations": [],
  "riskLevel": "data_incomplete"
}
```

---

## 成本分析主线验证样例

本节记录**如何自检**「本月成本分析」链路；**勿将业务库真实主键写入 Git**。文档中仅用 **`DEPT_PARENT_ID` / `DIS_ID` / `USER_ID` 占位符**。

### 如何取得合法的 `departmentId` / `distributerId`

- **`departmentId`**：与日营收 / 核销 / 菜品报表一致的 **父部门 id**（与 `GbAiDailyRevenueService#getStatsByDepartmentId`、 insight 口径一致）。
- **`distributerId`**：**分销商 id**（`disId`），采购与菜品链路需要。

以下为**示意 SQL**（表名与筛选条件请以你环境为准，结果请脱敏后再分享）：

```sql
SELECT gb_department_id, gb_department_father_id, gb_department_dis_id, gb_department_name
FROM gb_department
WHERE gb_department_father_id IS NOT NULL
LIMIT 20;
```

在联调环境里挑选一对与真实营业数据匹配的 **`departmentId`（常为父门店）与 `distributerId`**。

### 请求示例（占位符）

```bash
curl -s -X POST http://localhost:8090/api/ai/runs \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":USER_ID,\"departmentId\":DEPT_PARENT_ID,\"distributerId\":DIS_ID,\"message\":\"帮我看本月成本怎么样\"}"
```

```bash
curl -Ns http://localhost:8090/api/ai/runs/<runId>/events
```

可选轮询（**无 `costDiagnosis`**，仅有 `answerPreview` 摘要）：

```bash
curl -s http://localhost:8090/api/ai/runs/<runId>
```

### 端到端自检清单（与产品验收对齐）

| # | 检查项 | 如何确认 |
|---|--------|----------|
| 1 | 进入 **BUSINESS_CHAT** | SSE `agent_finished` `WorkspaceRouterAgent` 上出现 `workspaceMode":"BUSINESS_CHAT"` |
| 2 | 「本月」时间窗 | `TimeWindowNode` 的 **`agent_finished`** 带 **`startDate`/`endDate`**（当月首日至当日或语义等价） |
| 3 | 五个 Tool **顺序执行** | 依次出现 `revenue_query` → `purchase_query` → `stock_reduce_query` → `dish_sales_query` → `gross_margin_calculator` 的 `tool_started`/`tool_finished` |
| 4 | Tool **真实 vs mock / success** | 各 `tool_finished` 的 `success`；响应体信封见 **`com.nongxinle.ai.tool.business`**：`schemaVersion`=`v1`，**`mock`**、`success`、`data` 内指标 |
| 5 | **`costDiagnosis` 结构化** | `CostDiagnosisAgent` 成对 `agent_*` 后出现 |
| 6 | **`answer_delta.data.costDiagnosis`** | 事件中 `data.costDiagnosis` 非空对象，字段遵守上节契约 |
| 7 | **自然语言可读** | **`data.text`**（及根级 `text`）；当前 **LLM 为占位网关**时，正文前段为服务端拼接的确定性摘要，`---` 后为占位模型润色 |

### Tool 数据来源说明（概要）

实现类均在 `src/main/java/com/nongxinle/ai/tool/business/`。统一信封字段包括：`schemaVersion`、`tool`、`success`、`mock`、可选 `note`、**`data`（载荷）**。当库中 **无行 / 查询异常 / 占位 ID**，常见现象为：**`mock: true`** 或 **`success: false`**，**`costDiagnosis` 仍会给结论**（如 `needMoreData: true`）。

| Tool | 主要后端依赖 | 「像真实」的判据 |
|------|----------------|------------------|
| `revenue_query` | `GbAiDailyRevenueService#getStatsByDepartmentId` | `success=true` 且 **`data.days>0`** 或 **`data.rawStats`** 有关键聚合 |
| `business_overview_query` | 同上 + `GbAiRestaurantProfileService` + **`GbAiDailyRevenueDashboardService#buildStatsDashboard`** | `success=true` 且 **`data.stats`**（中文 KPI map）非空、`totalRevenue` 可用于毛利推导 |
| `purchase_query` | `GbDistributerPurchaseGoodsService`（count/subTotal） | **`data.purchaseRowCount>0`** |
| `stock_reduce_query` | `GbDepartmentGoodsStockReduceService#queryReduceAllTypesTotalOnDailyRevenueDays` | **`data`** 中各类 total 不全为 0 或 **`mock`** 为 false |
| `dish_sales_query` | `GbDepFoodBusinessInsightService#buildInsight` | **`data.dishLineCount>0`**（该步较慢，常见于 数十秒） |
| `gross_margin_calculator` | 纯推导（读快照中 **`revenue_query`** 或 **`business_overview_query`** 的 **`totalRevenue`**） | **`success`** 通常为 true；可信度依赖上游 `data` |

### 与本仓库联调一致的验证记录（占位 ID）

在 **本地应用已连接业务库、`profile=local`、`ai.trace.persist-enabled=false`** 的前提下，使用 **虚构主键** `departmentId=999001`、`distributerId=888001`、`userId=1` 跑一次「帮我看本月成本怎么样」，已观测到：

- **BUSINESS_CHAT**、本月 **`2026-05-01`～`2026-05-09`**（与服务器「今天」对齐）、**五步 Tool 均被调用**
- Run **约 55～60 秒完成**（`dish_sales_query` 最重）
- 因 ID 与库内数据不对齐：**指标多为 0，`needMoreData: true`**，属**预期兜底行为**，也证明链路与结构化诊断可走通

换为 **真实门店父部门与分销商 ID** 后，应再在同样清单下复检 **数值非零与 mock 语义**。**请勿把本人跑出的 `runId` 写死进文档**，避免环境与数据漂移。

### 真实主键抽检记录（2026-05-09，本机一次）

以下在 **连接业务库** 的机器上、由助手按用户给定主键 **跑通一次 SSE** 后的**定性**结论（**不写具体金额/行内容**，仅状态与耗时级别）。参数：`userId=1`、`departmentId=1`、`distributerId=2`；问句：**「帮我看本月成本怎么样」**。修复前参考 `runId=1778341723440`，整机约 **91～92s**。

**历史备注**：下表为 **修复 Mapper 前（2026-05-09）** 快照；**`revenue_query` 异常** 根因与修复见下节 **「`revenue_query` 失败根因与修复」**。**2026-05-10** 起需 **重启应用** 后再用同参复测，预期五步均为 **`success:true`**（未重启则仍可能 `revenue` 失败）。

| # | 检查项 | 结果（定性，修复前） |
|---|--------|----------------|
| 1 | **`WorkspaceRouterAgent` → BUSINESS_CHAT** | ✅ `agent_finished` 含 `workspaceMode":"BUSINESS_CHAT"` |
| 2 | **`TimeWindowNode` → 本月** | ✅ 「本月截至目前」；`startDate`/`endDate` 覆盖当月首日～当日（与服务器日期对齐） |
| 3 | **五步 Tool 顺序** | ✅ `revenue_query` → `purchase_query` → `stock_reduce_query` → `dish_sales_query` → `gross_margin_calculator` |
| 4 | **真实数据 / mock / success** | **`revenue_query`：`success:false`**（`query_failed`，非无行）。其余四步 **`success:true`** |
| 5 | **`dish_sales_query` 耗时** | ✅ **最重**：约 **60s** 量级 |
| 6 | **`answer_delta.data.costDiagnosis`** | ✅ **结构完整** |
| 7 | **`needMoreData`** | **`false`** |
| 8 | **自然语言 `data.text`** | ✅ 可读 |

| 项 | 说明 |
|----|------|
| **现象** | SSE `tool_finished`：`revenue_query`、`success:false`，`RevenueQueryTool` 走 **`query_failed`**（**不是**无行：`无行` 时为 `success:true` + `message=no_rows` + `mock`）。 |
| **根因** | `GbAiDailyRevenueMapper.xml` 的 **`selectStatsByDepartmentId`** 对内层按日汇总使用了列 **`gb_ai_daily_revenue_gross_revenue`**。该列在参考 DDL（如 `beData/ai_marketing.sql`）中为 **生成列**；**业务库若尚未包含该列**，MySQL 报 **未知列**，查询失败。`departmentId` / 日期区间传参在本次抽检中为 **正确**（非「缺少父部门 ID」）。 |
| **修改** | **`src/main/resources/mapper/GbAiDailyRevenueMapper.xml`**：`day_gross` 改为用 **`COALESCE(dine_in,0)+COALESCE(takeout,0)`** 汇总（与同表生成列语义一致，**兼容无 gross 列的库表**）；`day_orders` 对两项订单数 **`COALESCE` 后再相加**，避免 NULL 语义问题。**`RevenueQueryTool.java`**：异常日志增补 **日期区间**，便于检索 `[RevenueQueryTool]`。 |
| **部署注意** | 修改 XML 后需 **重启 Spring Boot**（通常不会热替换 Mapper）。未重启时对同一环境的探测 Run（如 `runId=1778342321970`）仍可能 **`revenue` 失败**，属旧 SQL 缓存/已加载资源。 |
| **复测验收（同一组 ID）** | `userId=1`、`departmentId=1`、`distributerId=2`、问句「帮我看本月成本怎么样」→ 预期 **`revenue_query`～`gross_margin_calculator` 均为 `success:true`**；`answer_delta` 正常；文案不再因 **revenue SQL 异常** 提示 mock/链路失败（若仍有个别 mock，多为业务数据空集，与 `no_rows` 语义区分）。**整机耗时**与 **`dish_sales_query` 单独耗时** 仍为性能优化项（本阶段不排期），常见 **~75～90s** 量级、`dish` **~40～60s**。请把 **重启后** 新 `runId` 记入你们内部运行簿；本文档不写具体金额。 |

### `revenue_query` 语义速查（保持）

- **无行 / 空统计**：`success:true`，`message` 常为 **`no_rows`**，`mock:true`（见 `RevenueQueryTool`）。  
- **SQL/服务异常**：`success:false`，载荷内 **`errorCode":"query_failed"`**（见 `RevenueQueryTool` catch 分支）。

---

## Trace 写库与本机联调（无 MySQL）

- DDL：`sql/gb_ai_agent_run_step.sql`。  
- **`ai.trace.persist-enabled=false`** 时跳过 `gb_ai_agent_run` / `gb_ai_agent_step` 写入，避免 JDBC 长时间建连 **阻塞 SSE**。  
- 推荐使用 **`spring.profiles.active=local`**（见 `application-local.properties`，其中默认 **`ai.trace.persist-enabled=false`**）。
- **打开 Trace**：在可用的 MySQL 上执行 DDL 后，将 **`ai.trace.persist-enabled=true`** 写入你的本地配置（可临时加到 `spring-boot.run.arguments` 或通过环境变量等价覆盖），并用 **同一数据源**启动应用后再跑一遍成本主线。

### Trace 入账规则（核对用）

| 行为 | `gb_ai_agent_step` |
|------|---------------------|
| 节点 `shouldRun()==true` 且执行完成 | **`SUCCESS`**，`step_name` 为图节点的 `AgentNode#name()`（如 `TimeWindow`、`DataPlanner`），**不是** SSE 展示名 |
| 节点抛错 | **`FAILED`**，带 `error_message` |
| 节点 **`shouldRun()==false`**（跳过） | **无行**——不要为「skipped」伪造 step |

核验 SQL（**脱敏**：仅列结构）。

```sql
SELECT id, status, start_time FROM gb_ai_agent_run ORDER BY id DESC LIMIT 5;
SELECT run_id, step_order, step_name, status, LEFT(error_message, 80) FROM gb_ai_agent_step WHERE run_id = ? ORDER BY step_order;
```

```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local --server.port=8091 --ai.trace.persist-enabled=true"
```

勿用 **逗号** 把 `local,--server.port=…` 拼在同一参数里，否则 Spring 会把 `--server.port=8091` 误当作 **第二个 profile 名**，端口不生效。

生产环境保持默认 **`ai.trace.persist-enabled=true`**。

---

## 与其它文档

- `docs/ARCHITECTURE_DECISIONS.md`
- `docs/TODO_MULTI_AGENT.md`
