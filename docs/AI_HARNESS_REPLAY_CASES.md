# AI Harness — Replay Cases（第二阶段稳定性）

本文件为多轮链路 **replay / 断言** 的用例草稿：每一步给出 **预期语义字段**，便于 Harness、日志对照与回归。

- **占位门店名**：`AAA`、`汀兰餐厅` 等为示例；实际断言应以环境内 **`gb_department` 门店根名称** 与权限可见列表为准。
- **日期**：`startDate` / `endDate` 为 **`yyyy-MM-dd`**，与 `AiResolvedTimeWindow`、`AiRunState#stat*` 对齐。内置用例 `PURCHASE_MULTITURN_1` 根据 **`frozenClockDate`**（语义「今天」锚点）计算「本月至此日」起止以及「上个月」闭合区间。
- **开关**：`GET /api/ai/runs/{runId}` 始终含 **`harnessDebug.debugContextEnabled`**（与运行时 `ai.harness.debug-context-enabled` 一致）。若为 `true`，另含 **`harnessDebug.resolvedQueryContextPresent`**；仅当为 `true` 且内存态有 `resolvedQueryContext` 时才有 **`harnessDebug.resolvedQueryContextSummary`**。（本地联调见 `application-local.properties`：`ai.harness.debug-context-enabled=true`。）
- **Replay 接口**：`POST /api/ai/harness/replay`；需 **`ai.harness.replay-enabled=true`**（本地 profile 已默认开启）。全路径带 `server.servlet.context-path=/api` 时为 **`/api/ai/harness/replay`**。

---

## 自动化 Replay 接口

**请求** `POST /api/ai/harness/replay`（`Content-Type: application/json`）

| 字段 | 说明 |
|------|------|
| `userId` | 必填；须能在 `gb_department_user` 解析出 admin（与正式 Run 一致） |
| `departmentId` / `distributerId` | 与正式 Run 一致 |
| `scopeMode` | 可选；**集团多轮 Case 1 建议显式传 `GROUP`**（若仅传 `departmentId` 而不传 `scopeMode`，会话创建规则与 `AiRunService` 相同：有 `departmentId` 会走 **STORE** 会话，易与集团预期不符） |
| `frozenClockDate` | 可选，`yyyy-MM-dd`；不传则用 JVM 当天，断言不稳定 |
| `caseId` | 可选；`PURCHASE_MULTITURN_1` 加载内置 7 轮预期（与下文 Case 1 表一致，日期由 `frozenClockDate` 推导） |
| `expectations` | 可选；与 `messages` 等长的自定义预期，**优先于** `caseId` |
| `messages` | 必填；多轮问句顺序 |
| `strictStoreSqlMatch` | 默认 `true`；`false` 时跳过 `visibleStoreRootIds` / `effectiveSqlDepartmentIds` 的强校验（库与占位 ID 不一致时用） |

Replay **断言门店 visible 范围**时请以 **`visibleStoreRootIds` / `storeRootDepartmentIds` / `visibleStores`** 为准；**不要**把 **`sqlQueryDepartmentIds`（及 `queryDepartmentIds` / `effectiveSqlDepartmentIds`）**当成「门店列表」——其中含子部门，故常见 `storeRoot=[3]` 而 SQL 列表为 `[3,4]`。

- `conversationId`：本次新开会话（每请求一条，避免污染线上会话）
- `overallPass`：所有带断言的轮次均通过
- `frozenClockDate`：实际使用的锚点日
- `rounds[]`：每轮含 `roundIndex`、`message`、`runId`（合成 id）、`conversationId`、`resolvedQueryContextSummary`、`pass`、`failedFields[]`

**`failedFields` 项**（`AiHarnessMismatch`）：`type`（`AiHarnessFailureType` 枚举）、`field`、`expected`、`actual`。示例：第 6 轮 `PURCHASE_SOURCE_MISMATCH`，`field=purchaseSourceType`，`expected=SUPPLIER_PURCHASE`，`actual=SELF_PURCHASE`。

**请求示例**（与 Case 1 对齐；`frozenClockDate` 与文档表一致时「本月」为 2026-05-01～2026-05-11）：

```json
{
  "userId": 1,
  "departmentId": 1,
  "distributerId": 2,
  "scopeMode": "GROUP",
  "frozenClockDate": "2026-05-11",
  "caseId": "PURCHASE_MULTITURN_1",
  "strictStoreSqlMatch": false,
  "messages": [
    "这个月采购多少钱？",
    "上个月呢？",
    "AAA 呢？",
    "自采购呢？",
    "汀兰餐厅呢？",
    "供货商订货呢？",
    "哪个供货商金额最高？"
  ]
}
```

本阶段 **不跑 Graph / DeepSeek**，仅验证解析与 Harness 摘要字段。

---

## Case 1 — 采购金额多轮追问（集团管理员 `admin=0`）

**身份 / 初始参数（与正式联调一致）**：`userId=1`，`departmentId=1`，`distributerId=2`，**会话请使用集团模式**（建议请求体带 `"scopeMode": "GROUP"`）。

**对话**：  
`这个月采购多少钱？` → `上个月呢？` → `AAA 呢？` → `自采购呢？` → `汀兰餐厅呢？` → `供货商订货呢？` → `哪个供货商金额最高？`

**当以 `frozenClockDate = 2026-05-11` 为锚点时，各轮典型预期如下**：

| 问句 | `effectiveIntentCode` | `effectivePathCode` | `effectiveTimeWindowSource`（典型） | 时间区间 | `scopeType` | `visibleStoreRootIds`（占位） | `purchaseSourceType` | `structuredIntentDetail` | `mentionedStore` |
|------|----------------------|---------------------|--------------------------------------|----------|-------------|-------------------------------|---------------------|--------------------------|------------------|
| 这个月采购多少钱？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `CURRENT_MESSAGE_EXPLICIT` 或 `DEFAULT_MONTH_TO_DATE` | `2026-05-01`～`2026-05-11` | `GROUP` | `[1,3]` | `null` | `null` | `null` |
| 上个月呢？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `CURRENT_MESSAGE_EXPLICIT` 或 `TIME_SHIFT` | `2026-04-01`～`2026-04-30` | `GROUP` | `[1,3]` | `null` | `null` | `null` |
| AAA 呢？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `INHERITED_PREVIOUS` | 同上上月 | `STORE` | `[1]` | `null` | `null` | `AAA` |
| 自采购呢？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `INHERITED_PREVIOUS` | 同上 | `STORE` | `[1]` | `SELF_PURCHASE` | `null` | `AAA` |
| 汀兰餐厅呢？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `INHERITED_PREVIOUS` | 同上 | `STORE` | `[3]` | **`null`**（仅切店、未声明自采/供货商渠道时不继承上一轮来源） | `purchase_overview_summary`（典型） | `汀兰餐厅` |
| 供货商订货呢？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `INHERITED_PREVIOUS` | 同上 | `STORE` | `[3]` | `SUPPLIER_PURCHASE` | `null` | `汀兰餐厅` |
| 哪个供货商金额最高？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `INHERITED_PREVIOUS` | 同上 | `STORE` | `[3]` | **`null`**（排行语义不收窄为单一渠道统计） | **`supplier_amount_ranking`** | `汀兰餐厅` |

**重点回归（断言失败时会映射到 `AiHarnessFailureType`）**：

- 「汀兰餐厅呢？」等**仅切换门店**、未再声明自采/供货商渠道时：`purchaseSourceType` 应为 **`null`**（全口径采购），**不得**继承上一轮「自采呢？」的 `SELF_PURCHASE`。
- 「供货商订货呢？」**不得**判成 `SELF_PURCHASE`（应为 `SUPPLIER_PURCHASE`）。
- 「哪个供货商金额最高？」应带上 `structuredIntentDetail=supplier_amount_ranking`，且 **不得**沿用上一轮 `purchaseSourceType=SUPPLIER_PURCHASE`（统计侧仍按采购概览聚合；供货商 Top 仅在真实 `nx_supplier_id>0` 上排行）。
- **不得**因为是集团账号就始终在 `GROUP` 范围不回缩门店：点到具体店后应为 `STORE` + 单根 `visibleStoreRootIds`。
- `visibleStoreRootIds`**只含门店根**（示例 `[1,3]`、`[1]`、`[3]`），子部门只允许出现在 **`effectiveSqlDepartmentIds`**（等价 `sqlQueryDepartmentIds`），不得混进门店展示字段。

枚举 **`AiHarnessFailureType`**：`INTENT_MISMATCH`，`PATH_MISMATCH`，`TIME_WINDOW_MISMATCH`，`TIME_SOURCE_MISMATCH`，`SCOPE_TYPE_MISMATCH`，`STORE_SCOPE_MISMATCH`，`DEPARTMENT_SCOPE_MISMATCH`，`PURCHASE_SOURCE_MISMATCH`，`TOOL_ARGUMENT_MISMATCH`，`SQL_RESULT_MISMATCH`，`COMPOSER_TEXT_MISMATCH`（前两阶段以后项预留）。

---

## 字段说明（ Harness 对齐 `harnessDebug.resolvedQueryContextSummary`）

| 字段 | 含义 |
|------|------|
| `effectiveIntentCode` | `AiResolvedQueryContext#getEffectiveIntentCode()` |
| `effectivePathCode` | `AiResolvedQueryContext#getEffectivePathCode()` |
| `effectiveTimeWindowSource` | `INHERITED_PREVIOUS` / `CURRENT_MESSAGE_EXPLICIT` / `DEFAULT_MONTH_TO_DATE` 等 |
| `startDate` / `endDate` | 本轮有效统计窗（闭合区间） |
| `scopeType` | `AiResolvedOrgScope#scopeType`：`GROUP` / `STORE` / … |
| `visibleStores` | 可见门店根列表 `{ storeDepartmentId, storeName }`（展示口径） |
| `visibleStoreIds` | 与本轮 `AiResolvedDataScope#getVisibleStoreIds()` 对齐 |
| `storeRootDepartmentIds` | `resolveStoreRootDepartmentIds()`（门店根列表） |
| **`visibleStoreRootIds`** | **别名**：与 `storeRootDepartmentIds` 相同（与其它文档统一） |
| `sqlQueryDepartmentIds` | 实际 SQL IN 用的部门 ID（门店根 ∪ 直属子部门等）；**不单算「几家店」** |
| **`effectiveSqlDepartmentIds`** | **别名**：与 `sqlQueryDepartmentIds`、`queryDepartmentIds` 一致 |
| `expandedChildDepartmentIds` | 由门店根展开出的直属子部门 ID（扁平） |
| `storeToChildDepartmentIds` | 门店根 → 子部门映射（Harness 中用字符串键，如 `"1":[2,5]`） |
| `visibleWarehouseIds` | 库房场景的库房部门 ID |
| `explicitChildDepartmentIds` | 用户点名子部门（当前多为空，预留） |
| `queryScopeMode` | 如 `STORE_ROOTS_AND_DIRECT_CHILDREN`、`WAREHOUSE_DEPARTMENT`、`EMPTY` |
| **`queryLevel`** | **别名**：与 `queryScopeMode` 相同 |
| `queryDepartmentIds` | **遗留别名**，内容与 `sqlQueryDepartmentIds` 相同 |
| `purchaseSourceType` | `SELF_PURCHASE` / `SUPPLIER_PURCHASE` 或 **`null`**（全口径采购） |
| `structuredIntentDetail` | 如 `supplier_amount_ranking`（供货商/供应商采购金额排行类追问） |
| **`effectiveIntentSource`** | 对齐 `AiResolvedQueryContext#getEffectiveIntentSource()` |
| **`effectiveScopeSource`** | 对齐 `AiResolvedQueryContext#getEffectiveScopeSource()` |
| **`effectiveTimeWindowSource`** | 时间锚来源（如 `INHERITED_PREVIOUS`、`CURRENT_MESSAGE_EXPLICIT`） |

`followUpResolution.followUpType` 可对齐日志：`TIME_SHIFT`、`STORE_SCOPE_FOLLOW_UP`、`PURCHASE_DETAIL_FOLLOW_UP` 等（可选写入 Harness 断言）。

---

## Replay A — 采购金额多轮（简表）

口径与 **Case 1**、`caseId=PURCHASE_MULTITURN_1` 一致；以下仅作速查，细则与 API 见上文 **Case 1** 与 **`POST /api/ai/harness/replay`**。

## Replay B — 经营情况 → 时间 → 门店切换

| Step | User text | Expected `effectiveIntentCode` | Expected `effectivePathCode` | `effectiveTimeWindowSource` | Notes |
|------|-----------|-------------------------------|------------------------------|-----------------------------|--------|
| 1 | 经营情况怎么样？ / 生意怎么样？ | `BUSINESS_OVERVIEW` | `business_overview_path` | `DEFAULT` / 显式本月 | |
| 2 | 上个月呢？ | `BUSINESS_OVERVIEW` | `business_overview_path` | `CURRENT_MESSAGE_EXPLICIT` | `LAST_MONTH` |
| 3 | AAA 呢？ | `BUSINESS_OVERVIEW` | `business_overview_path` | 多为继承 | 门店收窄为 AAA |
| 4 | 汀兰餐厅呢？ | `BUSINESS_OVERVIEW` | `business_overview_path` | 继承 | `visibleStores` 为汀兰（若别名可解析） |

`purchaseSourceType`：整链 **`null`**（非采购路径）。

---

## Replay C — 库存 → 门店 → 低库存追问

| Step | User text | Expected `effectiveIntentCode` | Expected `effectivePathCode` |
|------|-----------|-------------------------------|------------------------------|
| 1 | 库存情况怎么样？ | `WAREHOUSE_STOCK_OVERVIEW` | `warehouse_stock_overview_path` |
| 2 | AAA 呢？ | `WAREHOUSE_STOCK_OVERVIEW` | `warehouse_stock_overview_path` |
| 3 | 低库存呢？ | 仍为库房/库存语义（Planner 用词略同时以 **effectiveIntentCode/path** + DataPlanner 标志为准） | `warehouse_stock_overview_path` 或等价收敛 |

断言时建议一并核对 **`workspaceMode`** 与 DataPlanner 选中的 tool id（见 SSE / trace），本条仅锁 **intent/path/scope/time**。

---

## Replay D — 菜品毛利 → 时间 → 单品

| Step | User text | Expected `effectiveIntentCode` | Expected `effectivePathCode` |
|------|-----------|-------------------------------|------------------------------|
| 1 | 菜品毛利怎么样？ | `DISH_PROFIT` | `dish_profit_path` |
| 2 | 上个月呢？ | `DISH_PROFIT` | `dish_profit_path` |
| 3 | 某个菜品呢？（点名具体菜名） | `DISH_PROFIT` | `dish_profit_path` |

`purchaseSourceType`：整链 **`null`**。

---

## Unknown semantic 采集（采购短追问）

当 **上一轮** `lastIntentCode=PURCHASE_OVERVIEW` 且 `lastPathCode=purchase_overview_path`，本轮进入 **采购短追问 augment 长度包络**（≤40 字）且 **`augmentPurchaseOverviewSourceFromShortCue` 未收窄出 `purchaseSourceType`**、且 **`mergePurchaseCuesInto` 也未给出来源** 时：

- 若 `ai.harness.unknown-purchase-semantic-log-enabled=true`，打出结构化日志：**`AIHarnessUnknownPurchaseSemantic`**。
- 用于后续扩充 **`AiQuerySemanticLexicon`**，避免仅靠临时猜词。

---

## 后续（非本轮）

- 已提供 **`POST /api/ai/harness/replay`** 做解析链专用回归；与「每步 POST run + 拉 SSE」互补。
- 不要将本摘要用于正式用户界面；生产环境默认 **`ai.harness.debug-context-enabled=false`**、**`ai.harness.replay-enabled=false`**。
