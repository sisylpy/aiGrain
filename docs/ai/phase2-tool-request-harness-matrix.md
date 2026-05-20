# 阶段 2：Tool Request / SQL 入参 Harness 矩阵

> **状态**：规划文档（2026-05-19）；**方案 A** — 新增 `dryRunStage=TOOL_REQUEST_ONLY`（Java **未实现**，本文仅定义验收契约）。  
> **上游**：[phase2-tool-request-sql-input-plan.md](./phase2-tool-request-sql-input-plan.md)、[AI_HARNESS_REPLAY_CASES.md](../AI_HARNESS_REPLAY_CASES.md)（阶段 2 索引）、阶段 1 矩阵（[1B](./business-phase1b-semantic-harness-matrix.md) / [1C](./stock-reduce-phase1c-semantic-harness-matrix.md)）。  
> **原则**：只验「准备查什么参数」，不验查出来的数对不对、话术对不对。

---

## 1. 阶段 2 验收边界

### 1.1 在范围内

| 层 | 验收对象 |
|----|----------|
| **DataPlanner** | `dataPlanTools`（计划 `toolId` 列表）、`purchaseOverviewPath` / `revenueOverviewPath` / `businessOverviewPath` / `stockReduceQueryPath` / `businessDiagnosisPath`、`groupPurchaseOverview` / `groupStockReduceQuery` / `groupWideOverviewHint`（摘要或 args 体现） |
| **RequestContext** | `BusinessToolExecutionRequestResolver` 输出：`PurchaseToolRequestContext`、`RevenueToolRequestResolution`、`StockReduceToolRequestContext` 等；摘要键 `*RequestResolutionDebug` |
| **ToolRequest.args** | 各 `*ToolExecutor.build*ToolArgs` 产物；Harness 汇总为 **`plannedToolArgsByToolId`** |
| **时间** | `startDate` / `endDate`（ISO）；args 中 `startDate` / `stopDate` 与 Context 一致 |
| **范围** | `scopeType`、`visibleStores`、`queryScopeKind`、`queryStoreIds`、`expandedSqlDepartmentIds`（及域别名 `purchaseSqlDepartmentIds` / `stockReduceSqlDepartmentIds`） |
| **语义 wire / 来源** | `structuredIntentDetailWire` → `queryIntent.structuredIntentDetail` → args `purchaseNarrativeMode` / `stockReduceNarrativeMode`；`purchaseSourceType` / `sourceFacet` → `purchaseSourceFocus`；`stockReduceType`（compat，来自 `metric.stockReduceType`） |
| **Debug 溯源** | `resolutionDebug.*Source` 字段（不得含用户原文 regex） |

### 1.2 明确不在范围内

- `Tool.execute` 返回行、payload 空/non-empty、金额数值  
- SQL 文本、Mapper 执行、DB 行数  
- **`AnswerPlan`**（`*AnswerPlanPresent`、planType、focusRows）  
- **`Composer`**（`finalAnswerText`、`answerPreview`）  
- 前端展示  
- **`master*ToolResultSuccess`**、`**ToolResultSuccess`（阶段 2 应 **不写入** 或 **忽略** 此类探针）

### 1.3 与阶段 1 的关系

- 阶段 1（`RESOLVED_CONTEXT_ONLY`）已验：`effectiveIntentCode` / wire / 时间 / scope **摘要**。  
- 阶段 2 在 **同一 Resolver 输出** 之上，追加 **Planner + args 快照** 断言；**不得**新增平行 rankingType 写口。

---

## 2. `dryRunStage=TOOL_REQUEST_ONLY` 设计

### 2.1 截断链路（方案 A）

```mermaid
flowchart LR
  RQC[AiResolvedQueryContext]
  DP[DataPlannerNode]
  BTR[BusinessToolExecutionRequestResolver]
  BUILD["*ToolExecutor.build*ToolArgs"]
  CAP[plannedToolArgsByToolId]
  STOP((STOP))
  RQC --> DP --> BTR --> BUILD --> CAP --> STOP
```

| 步骤 | 组件 | 阶段 2 行为 |
|------|------|-------------|
| 1 | Resolver | 与现网相同，产出 `AiResolvedQueryContext` |
| 2 | `BusinessDataPlannerNode` | 与现网相同，设置 `dataPlanTools` + path/group 旗标 |
| 3 | `BusinessToolExecutionRequestResolver` | 按域构建 `*ToolRequestContext`（**不**解析用户原文） |
| 4 | `*ToolExecutor.build*ToolArgs` | 由 `AiRunState` + RQC 组装 `Map<String,Object>` args |
| 5 | **Capture** | 写入 `AiRunState.plannedToolArgsByToolId` + `*RequestResolutionDebug` |
| 6 | **STOP** | **不得**调用 `ToolRegistry.execute` / **不得**写 `toolResults` / **不得**跑 AnswerPlanBuilder / Composer |

### 2.2 Harness 请求配置（目标态）

| 配置 | 值 |
|------|-----|
| `dryRunStage` | **`TOOL_REQUEST_ONLY`** |
| `replayMode` | `GRAPH_RUN`（必须进 DataPlanner + Tool 组装链） |
| `frozenClockDate` | 固定，推荐 `2026-05-19`（与阶段 1 一致） |
| `strictStoreSqlMatch` | `false`（多店占位 ID 与环境解耦） |
| `scopeMode` | 集团 case 建议 `GROUP` |

### 2.3 与现有 dry-run 对比

| `dryRunStage` | 停在哪 |
|---------------|--------|
| `RESOLVED_CONTEXT_ONLY` | Resolver + 摘要 |
| **`TOOL_REQUEST_ONLY`** | **DataPlanner + args 快照** |
| `FULL` / null | 完整 Graph（含 execute） |

### 2.4 澄清 / 权限短路

- 若 `needSemanticClarification=true` 或 DataPlanner 未编排 Tool：**`plannedToolArgsByToolId` 为空或缺失对应 toolId**；阶段 2 case 应区分「预期无 Tool 请求」与「预期有 args」两种轮次（见 §4 P2A-03 备注）。

---

## 3. `plannedToolArgsByToolId` 输出结构

Harness 摘要（每轮 `resolvedQueryContextSummary`）建议新增顶层键：

```json
{
  "dryRunStage": "TOOL_REQUEST_ONLY",
  "dataPlanTools": ["purchase_overview"],
  "purchaseOverviewPath": true,
  "groupPurchaseOverview": true,
  "plannedToolArgsByToolId": {
    "purchase_overview": {
      "toolId": "purchase_overview",
      "effectiveIntentCode": "PURCHASE_OVERVIEW",
      "effectivePathCode": "purchase_overview_path",
      "structuredIntentDetailWire": "purchase_overview_summary",
      "requestContextType": "PurchaseToolRequestContext",
      "startDate": "2026-05-01",
      "endDate": "2026-05-19",
      "scopeType": "GROUP",
      "queryScopeKind": "STORE",
      "visibleStores": [
        {"storeDepartmentId": 1, "storeName": "AAA"},
        {"storeDepartmentId": 3, "storeName": "汀兰餐厅"}
      ],
      "visibleStoreRootIds": [1, 3],
      "expandedSqlDepartmentIds": [1, 2, 5, 3, 4],
      "purchaseSqlDepartmentIds": [1, 2, 5, 3, 4],
      "purchaseSourceType": "ALL",
      "sourceFacet": "ALL",
      "args": {
        "disId": 2,
        "groupPurchaseAggregation": true,
        "resolvedDepartmentIds": [1, 3],
        "parentStoreCount": 2,
        "startDate": "2026-05-01",
        "stopDate": "2026-05-19",
        "purchaseSourceFocus": "ALL",
        "purchaseNarrativeMode": "purchase_overview_summary",
        "queryScopeBanner": "…",
        "visibleStores": ["…"]
      },
      "narrativeMode": "purchase_overview_summary",
      "sourceFocus": "ALL",
      "stockReduceType": null,
      "resolutionDebug": {
        "timeWindowSource": "resolvedQueryContext.timeWindow.explicitDates",
        "purchaseSourceTypeSource": "resolvedQueryContext.queryIntent.purchaseSourceType",
        "structuredIntentDetailSource": "resolvedQueryContext.queryIntent.structuredIntentDetail",
        "purchaseSqlDepartmentIdsSource": "resolvedQueryContext.dataScope.sqlDepartmentIdsForDomain(purchase)"
      }
    }
  },
  "toolRequestCaptured": true,
  "toolExecuteSkipped": true
}
```

### 3.1 字段说明

| 字段 | 说明 |
|------|------|
| `toolId` | 与 `dataPlanTools` 中项一致 |
| `effectiveIntentCode` / `effectivePathCode` | 来自 RQC（截断前已解析） |
| `structuredIntentDetailWire` | 摘要 `canonicalStructuredIntentDetailWire` 或 `queryIntent.structuredIntentDetail` canonical |
| `requestContextType` | `PurchaseToolRequestContext` / `RevenueToolRequestResolution` / `StockReduceToolRequestContext` / … |
| `startDate` / `endDate` | ISO；与 args `startDate`/`stopDate` 一致 |
| `scopeType` | `orgScope.scopeType`（`GROUP` / `STORE` / …） |
| `visibleStores` | 与 args `visibleStores` 或 RQC `orgScope.visibleStores` 对齐 |
| `expandedSqlDepartmentIds` | `dataScope.effectiveSqlDepartmentIds` 快照 |
| `args` | **完整** `ToolRequest.args`（Comparator 主断言对象） |
| `narrativeMode` | 域相关：`purchaseNarrativeMode` / `stockReduceNarrativeMode` / … |
| `sourceFocus` | 采购：`purchaseSourceFocus` |
| `stockReduceType` | compat；可 null |
| `resolutionDebug` | 来自 `*ToolRequestContext.resolutionDebug` |

### 3.2 `AiHarnessReplayExpectedRound` 扩展（目标态）

建议新增嵌套期望（实现阶段再落 Java）：

- `expectedPlannedToolArgs.toolId`  
- `expectedPlannedToolArgs.startDate` / `endDate`  
- `expectedPlannedToolArgs.argsMustContain`（键值对）  
- `expectedPlannedToolArgs.argsMustNotContain`（如单店不得出现 `groupPurchaseAggregation`）  
- `dataPlanToolsMustContain` / `dataPlanToolsExact`  
- `groupPurchaseOverviewExpected` / `groupStockReduceQueryExpected` / `revenueOverviewPathExpected` / …

---

## 4. 采购 2A 矩阵

**建议 `caseId`（待注册）**：`PURCHASE_TOOL_REQUEST_2A_CORE_5`  
**会话**：单会话 5 轮（P2A-01～03 为采购金额多轮；P2A-04～05 为独立语义，可拆为两轮新会话或接在前序 GROUP 上下文后 — 下文按 **5 轮单会话** 描述，04/05 继承 01 的时间/范围 unless noted）。

**日期占位**（`frozenClockDate=2026-05-19`）：

| 占位 | 值 |
|------|-----|
| `{M0}` | `2026-05-01` |
| `{M1}` | `2026-05-19` |
| `{P0}` | `2026-04-01` |
| `{P1}` | `2026-04-30` |

**门店/SQL 占位**（与 [`AI_HARNESS_REPLAY_CASES.md`](../AI_HARNESS_REPLAY_CASES.md) 一致）：AAA 根 `1`、汀兰根 `3`；`expandedSqlDepartmentIds` 示例 `[1,2,5,3,4]`。

---

### P2A-01 — 这个月采购金额多少？

| 项 | Expected |
|----|----------|
| **DataPlanner** | `dataPlanTools=[purchase_overview]`；`purchaseOverviewPath=true`；`groupPurchaseOverview=true` |
| **toolId** | `purchase_overview` |
| **RequestContext** | `PurchaseToolRequestContext`；`purchaseSqlDepartmentIds` ≈ `[1,2,5,3,4]` |
| **时间** | `startDate={M0}`，`endDate={M1}` |
| **scope** | `scopeType=GROUP`；`visibleStores` 含 AAA、汀兰；`queryStoreIds=[1,3]` |
| **wire / 来源** | `structuredIntentDetailWire=purchase_overview_summary`；`purchaseSourceType=ALL`；`sourceFacet=ALL` |
| **args 必含** | `groupPurchaseAggregation=true`；`resolvedDepartmentIds=[1,3]`；`startDate={M0}`；`stopDate={M1}`；`purchaseSourceFocus=ALL`；`purchaseNarrativeMode=purchase_overview_summary` |
| **args 必不含** | `departmentFatherId`（集团聚合分支不写单店锚点） |

---

### P2A-02 — 上个月呢？（接 P2A-01）

| 项 | Expected |
|----|----------|
| **DataPlanner** | 同 P2A-01 |
| **toolId** | `purchase_overview` |
| **时间** | `startDate={P0}`，`endDate={P1}`（继承采购域 + 时间 OVERRIDE） |
| **scope** | `scopeType=GROUP`；`expandedSqlDepartmentIds` 同 P2A-01 |
| **wire / 来源** | `purchase_overview_summary`；`purchaseSourceType=ALL` |
| **args** | 同 P2A-01，仅 `startDate`/`stopDate` 改为 `{P0}`/`{P1}` |

---

### P2A-03 — AAA 这个月采购金额多少？（接 P2A-02）

| 项 | Expected |
|----|----------|
| **DataPlanner** | `dataPlanTools=[purchase_overview]`；`purchaseOverviewPath=true`；**`groupPurchaseOverview=false`** |
| **toolId** | `purchase_overview` |
| **时间** | `startDate={M0}`，`endDate={M1}` |
| **scope** | **`scopeType=STORE`**；`mentionedStore`/visible 收窄 **AAA**；`queryStoreIds=[1]`；`expandedSqlDepartmentIds` 含 `[1,2,5]`（AAA 根∪子，**不含** 3/4） |
| **wire / 来源** | `purchase_overview_summary`；`purchaseSourceType=ALL` |
| **args 必含** | `departmentFatherId=1`（或环境 AAA 根）；`purchaseDepartmentId=1`；`startDate={M0}`；`stopDate={M1}`；`purchaseSourceFocus=ALL`；`purchaseNarrativeMode=purchase_overview_summary` |
| **args 必不含** | `groupPurchaseAggregation`；`resolvedDepartmentIds` 多店列表 |

**备注**：若 Resolver 因 `detailWanted` 帧校验进入 `needSemanticClarification`，则 **`plannedToolArgsByToolId` 为空** — 该轮属语义层缺陷，**不**作为阶段 2 通过；矩阵期望为「澄清通过后」的目标 args（与 `PURCHASE_AGENT_GRAPH_CORE` r3 一致）。

---

### P2A-04 — 哪个供货商金额最高？

| 项 | Expected |
|----|----------|
| **语境** | 独立问句或接 GROUP 采购上下文；**不得**继承上一轮 `purchaseSourceFocus=SUPPLIER_PURCHASE` 作为统计窄化（见 Case 1 文档） |
| **DataPlanner** | `dataPlanTools=[purchase_overview]`；`purchaseOverviewPath=true`；集团账号 → `groupPurchaseOverview=true`（无点名单店时） |
| **toolId** | `purchase_overview` |
| **时间** | 默认 `{M0}`～`{M1}`（无显式时间时） |
| **scope** | `scopeType=GROUP`（或继承 STORE 若前序已收窄 — 单会话 5 轮时建议 **新开会话** 测本句，GROUP） |
| **wire / 来源** | `structuredIntentDetailWire=supplier_amount_ranking`；`purchaseSourceType` **可为** `SUPPLIER_PURCHASE`（LLM 常给出）或 **`ALL`**（Resolver 校准后）；`sourceFacet` 对齐 |
| **args 必含** | `purchaseNarrativeMode=supplier_amount_ranking`；`startDate`/`stopDate` 与 RQC 一致 |
| **args 采购来源** | `purchaseSourceFocus` 与 **`queryIntent.purchaseSourceType`** 一致；若 semantic 为排行且 source 为 null，args **可不写** `purchaseSourceFocus` |

---

### P2A-05 — 定了什么东西？

| 项 | Expected |
|----|----------|
| **语境** | 典型为供货商渠道 overview 后追问；**单轮验收** 可 Hydrate 前序 `purchaseSourceType=SUPPLIER_PURCHASE` + 上月时间窗（见 `PURCHASE_SUPPLIER_CHANNEL_OVERVIEW_GOODS_DETAIL_2`） |
| **DataPlanner** | `dataPlanTools=[purchase_overview]` |
| **toolId** | `purchase_overview` |
| **wire / 来源** | `structuredIntentDetailWire=purchase_source_goods_query`；`purchaseSourceType=SUPPLIER_PURCHASE`；`sourceFacet=SUPPLIER_PURCHASE` |
| **args 必含** | `purchaseNarrativeMode=purchase_source_goods_query`；`purchaseSourceFocus=SUPPLIER_PURCHASE` |
| **args 可选（下钻）** | `focusSupplierId` / `followUpDetailWanted` 等 — 仅当 RQC 锚点存在时由 `PurchaseOverviewGoodsDrilldownArgs` 写入；阶段 2 只验 **键是否存在**，不验 SQL 结果 |

---

## 5. 营业额 / 经营 2B 矩阵

**建议 `caseId`**：`BUSINESS_TOOL_REQUEST_2B_CORE_5`  
**消息序**（与 1B R04/R05/R07/R01/R08b 对齐）：

1. 这个月营业额怎么样？  
2. 哪个门店营业额最高？  
3. AAA 和汀兰餐厅哪个营业额高？  
4. 这个月经营得怎么样？  
5. 那上个月呢？（接第 4 轮）

---

### P2B-01 — 这个月营业额怎么样？

| 项 | Expected |
|----|----------|
| **DataPlanner** | `dataPlanTools=[revenue_query]`；`revenueOverviewPath=true` |
| **toolId** | `revenue_query` |
| **RequestContext** | `RevenueToolRequestResolution` |
| **时间** | `{M0}`～`{M1}` |
| **scope** | `scopeType=GROUP`；`expandedSqlDepartmentIds` 多店 |
| **wire** | `revenue_overview_summary`（语义层；营业额 Tool **无** narrative args） |
| **args 必含** | `startDate={M0}`；`stopDate={M1}` |
| **args 集团** | `groupWideOverviewHint=true`；`resolvedDepartmentIds=[1,3]`（visible 店根） |
| **path 旗标** | `revenueOverviewPath=true`；`businessOverviewPath=false` |

---

### P2B-02 — 哪个门店营业额最高？

| 项 | Expected |
|----|----------|
| **DataPlanner** | `[revenue_query]`；`revenueOverviewPath=true` |
| **toolId** | `revenue_query` |
| **wire** | `revenue_store_amount_ranking` |
| **scope** | `GROUP`；`visibleStoreRootCount≥2` |
| **args** | 同 P2B-01（营业额 Tool 不区分 wire 入参；wire 仅作 RQC/Planner 归属探针） |

---

### P2B-03 — AAA 和汀兰餐厅哪个营业额高？

| 项 | Expected |
|----|----------|
| **DataPlanner** | `[revenue_query]` |
| **toolId** | `revenue_query` |
| **wire** | `revenue_store_amount_ranking` |
| **scope** | `GROUP`（双店对比 **不** 收窄单店）；`multiStoreScopeApplied=true`；点名 AAA、汀兰 |
| **args** | `groupWideOverviewHint=true`；`resolvedDepartmentIds` 含 `[1,3]` |

---

### P2B-04 — 这个月经营得怎么样？

| 项 | Expected |
|----|----------|
| **DataPlanner** | `businessOverviewPath=true`；MULTI_AGENT 时 `dataPlanTools` 为四域子集（`revenue_query`、`purchase_overview`、`stock_reduce_query`、`dish_profit_analysis`，权限裁剪）；**非 MULTI 时为空 plan**（classic 六工具链已删除） |
| **主断言 toolId** | 阶段 2 **首批**对每个计划 Tool 均输出 `plannedToolArgsByToolId` 条目 |
| **revenue_query args** | 同 P2B-01（`groupWideOverviewHint` + 日期） |
| **purchase_overview args** | 同 P2A-01（集团采购聚合） |
| **stock_reduce_query / dish_profit_analysis args** | MULTI_AGENT 四域子集内按域断言（classic `business_overview_query` 已删） |
| **wire / path** | `effectivePathCode=business_overview_path`；wire `business_overview_summary` 或 `business_overview_status`；`orchestrationTaskMode=MULTI_AGENT`（摘要探针，非 args） |

---

### P2B-05 — 那上个月呢？（接 P2B-04）

| 项 | Expected |
|----|----------|
| **DataPlanner** | 仍 `businessOverviewPath=true`；tool 列表与 P2B-04 相同 |
| **时间** | 各 Tool args 统一 `{P0}`～`{P1}` |
| **scope** | 继承 GROUP；`expandedSqlDepartmentIds` 不变 |
| **不得** | 因追问退化为单域 `revenue_query` only（除非 intent 切换 — 本 case 预期 **仍经营概览编排**） |

---

## 6. 出库 2C 矩阵

**建议 `caseId`**：`STOCK_REDUCE_TOOL_REQUEST_2C_CORE_4`  
**消息序**（与 1C R01/R02/R04/R09 对齐）：

1. 这个月出库怎么样？  
2. 这个月核销多少？  
3. 废弃多少？  
4. AAA 和汀兰餐厅哪个出库金额高？

---

### P2C-01 — 这个月出库怎么样？

| 项 | Expected |
|----|----------|
| **DataPlanner** | `dataPlanTools=[stock_reduce_query]`；`stockReduceQueryPath=true`；`groupStockReduceQuery=true`（集团账号） |
| **toolId** | `stock_reduce_query` |
| **RequestContext** | `StockReduceToolRequestContext` |
| **时间** | `{M0}`～`{M1}` |
| **scope** | `GROUP`；`stockReduceSqlDepartmentIds` ≈ `[1,2,5,3,4]` |
| **wire** | `stock_reduce_overview` |
| **stockReduceType** | null 或 `ALL`（compat；**不**覆盖 wire） |
| **args 必含** | `stockReduceHarnessPath=true`；`groupStockReduceAggregation=true`；`resolvedDepartmentIds=[1,3]`；`startDate`/`stopDate`；`stockReduceNarrativeMode=stock_reduce_overview` |

---

### P2C-02 — 这个月核销多少？

| 项 | Expected |
|----|----------|
| **DataPlanner** | 同 P2C-01 |
| **wire** | **`produce_consume`**（核销/出品耗用子口径） |
| **args** | `stockReduceNarrativeMode=produce_consume`；其余同 P2C-01 |
| **stockReduceType** | 若 LLM 填 `type1` 等，compat 字段可观测；**主断言 narrativeMode** |

---

### P2C-03 — 废弃多少？

| 项 | Expected |
|----|----------|
| **wire** | **`waste`**（对应 1C「这个月废弃金额多少？」） |
| **args** | `stockReduceNarrativeMode=waste` |
| **说明** | 用户短句「废弃多少？」在 v2 中应落到 `waste` wire；时间默认本月 |

---

### P2C-04 — AAA 和汀兰餐厅哪个出库金额高？

| 项 | Expected |
|----|----------|
| **DataPlanner** | `[stock_reduce_query]`；`groupStockReduceQuery=true` |
| **wire** | `store_outbound_amount_ranking` |
| **scope** | `GROUP`；双店点名；**不**收窄 `groupStockReduceQuery=false` |
| **args** | `stockReduceNarrativeMode=store_outbound_amount_ranking`；`groupStockReduceAggregation=true`；`resolvedDepartmentIds=[1,3]` |
| **Executor 归一** | 若 wire 为 `goods_outbound_count_ranking` 入参，Tool 层 canonical 为 `goods_outbound_ranking` — 阶段 2 以 **写入 args 前的 wire** 为准 |

---

## 7. 跨域风险 2D（暂列，不实现）

以下 wire 在 1C/采购语义中已出现，**跨采购↔出库** 易误路由；**不纳入** 阶段 2 第一批 Java / case 注册，仅作 backlog：

| wire | 风险说明 |
|------|----------|
| `purchase_stock_reduce_mismatch` | 采购与出库双域语义；Tool 入参可能同时触达采购/出库 Planner |
| `purchase_slow_moving_risk` | 慢动销风险；易与出库排行/采购排行混淆 |

**记录位置**：[`stock-reduce-phase1c-semantic-harness-matrix.md`](./stock-reduce-phase1c-semantic-harness-matrix.md) R14–R15；待阶段 2B+ 单独矩阵。

---

## 8. 实施 checklist（后续 Java，本文档不执行）

- [ ] **`AiHarnessReplayDryRunStage`**：新增 `TOOL_REQUEST_ONLY`  
- [ ] **`AiHarnessReplayService`**：`GRAPH_RUN && dryRunStage==TOOL_REQUEST_ONLY` → 调用截断版图同步（新 hook 或 `executeBusinessGraphSyncForHarnessUntilToolRequest`）  
- [ ] **`BusinessToolExecutionNode`**（或统一 Capture 组件）：在 `toolRegistry.execute` **之前** 对每个计划 toolId 调用既有 `build*ToolArgs` + `build*RequestContext`，写入 `plannedToolArgsByToolId`  
- [ ] **`AiRunState`**：新增 `Map<String, PlannedToolArgsSnapshot> plannedToolArgsByToolId`；布尔 `toolExecuteSkipped`  
- [ ] **`AiHarnessResolvedContextSummarizer`**：Graph 摘要摊平 §3 字段；保留现有 `*RequestResolutionDebug` 与 args 一致性校验探针  
- [ ] **`AiHarnessReplayExpectedRound` + Comparator**：`expectedPlannedToolArgs` / `dataPlanToolsMustContain` / path 旗标断言  
- [ ] **内置 case**：`PURCHASE_TOOL_REQUEST_2A_CORE_5`、`BUSINESS_TOOL_REQUEST_2B_CORE_5`、`STOCK_REDUCE_TOOL_REQUEST_2C_CORE_4`  
- [ ] **脚本**：`scripts/harness/replay-tool-request-core.sh`（调用 `replay-harness-common.sh`，`dryRunStage=TOOL_REQUEST_ONLY`）  
- [ ] **文档**：更新 [`AI_HARNESS_REPLAY_CASES.md`](../AI_HARNESS_REPLAY_CASES.md) 阶段 2 索引  
- [ ] **显式不做**：`Tool.execute`、AnswerPlan attach、Composer、SQL 断言  

---

## 9. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-19 | 初版：方案 A 截断设计、`plannedToolArgsByToolId` 契约、2A/2B/2C 首批矩阵、2D backlog、实施 checklist。 |
| 2026-05-19 | 已补充来自 [`phase2-tool-request-sql-input-plan.md`](./phase2-tool-request-sql-input-plan.md) 与 [`AI_HARNESS_REPLAY_CASES.md`](../AI_HARNESS_REPLAY_CASES.md) 的交叉索引。 |
