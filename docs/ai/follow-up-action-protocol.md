# Follow-up Action Protocol（多轮追问动作协议）— D-13

> **D-13.1（SUPPLIER 锚点 · 供货商排行 → 商品/单价下钻）已封版**：验收清单、不做事项与一键 probe 见 **`docs/ai/protocols/d13-1-supplier-drilldown-closure.md`**；Skill 样板 **`docs/ai/skills/supplier-drilldown-skill.md`**。  
> **D-13.2（STORE 锚点 · 经营诊断 · 门店优先级 → 原因下钻）已封版**：CaseId **`BUSINESS_STORE_PRIORITY_DRILLDOWN_REASONS_3`**（`store_priority_ranking` → `store_risk_reasons_drilldown`）；锚点与非造假约束见 **`docs/ai/result-anchor-protocol.md`**。  
> **全量 Harness 回归**（`scripts/harness/run-local-replay-regression-bundle.sh`，含上述 case 及主链内置用例）已验收 **`overallPass=true`**。**不继续扩展 D-13.3。**

借鉴 **Skills / Subagents / Hooks** 的思想，建立 **餐饮业务自有 Harness 协议**：约束的是 **结构化路由与观测**，不是让 LLM 自由写 SQL。

| 概念 | 餐饮 Harness 映射 |
|------|-------------------|
| **Subagent** | 领域 Agent：`PurchaseAgent`、`SupplierAnalysisAgent`、`DishProfitAgent` 等，各自只解读本域意图与计划 |
| **Skill** | 固定 IO 的能力单元，例如采购侧的 `supplier_purchase_analysis`：支持下钻前的排行、下钻后的商品/单价明细等 **planType / wire** 组合 |
| **Hook** | Resolver → Planner → AnswerPlan → Composer 链路上的 **debug contract**：`followUpAction`、`detailWanted`、anchors 计数等，用于发现协议断裂 |
| **Command / Eval** | Harness **Replay caseId + 固定三轮话术**，回归继承时间与对象下钻 |

---

## 1. `followUpAction`（约定取值）

当前以 **`AiResolvedQueryContext`** 上的 **String** 字段承载（后续可收紧为枚举）。

| 取值 | 含义 |
|------|------|
| （空 / null） | 未识别为多轮协议动作，或沿用默认承接 |
| `TIME_SHIFT` | 已由 **`AiFollowUpResolution.followUpType`** 等表达；**不与对象下钻混用** |
| `OBJECT_DRILLDOWN` | 在用户 **继承上一轮对象焦点**（如 Top 供货商）的前提下切换结构化意图 |
| `DETAIL_DRILLDOWN` | 与对象下钻同类观测预留；采购供货商明细场景可与 `OBJECT_DRILLDOWN` **等价验收** |

扩展新动作时：**同步** `AiHarnessResolvedContextSummarizer`、Replay `AiHarnessReplayExpectedRound` 可选断言字段。

---

## 2. `detailWanted`（约定取值）

同样先以 String 挂在 Context 上（如 `followUpDetailWanted`）。

| 取值 | 含义 |
|------|------|
| `GOODS_UNIT_PRICE` | 本轮要在既定采购来源/时间窗下看 **商品级明细与单价** |
| （扩展） | 门店维度、菜品成本等后续单独文档增量 |

**判定来源**：Resolver 在锚点满足后写入；可与语义 `metric`（如含 `UNIT_PRICE`）一致化，但 **禁止** 仅靠任意 `contains` 用户原文替代结构化条件（实现上应集中在一个方法内完成 Keyword + semantic 合并）。

---

## 3. 配套 Debug 字段（协议断裂观测）

`AiResolvedQueryContext` 中与下钻相关的观测字段：

- `followUpAction`
- `followUpTargetEntityType`
- `followUpTargetEntityName`
- `followUpDetailWanted`
- `followUpSourcePlanType`（上一轮产生锚点的 `PurchaseAnswerPlan.type`，如 `PURCHASE_SUPPLIER_AMOUNT_RANKING`）

Harness 摘要顶层应摊平上述字段（见 `AiHarnessResolvedContextSummarizer`）。

---

## 4. Resolver：何时判定 `OBJECT_DRILLDOWN`（采购 · 供货商）

实现入口：**`AiResolvedQueryContextResolver.resolveFollowUpPurchaseDrilldownFromAnchors`**（概念名），满足 **全部**：

1. **路径**：`effectivePathCode == purchase_overview_path`。
2. **上一轮锚点**：`previousTurn.lastResultAnchors` 中存在 **`entityType=SUPPLIER`**，且 **`sourcePlanType=PURCHASE_SUPPLIER_AMOUNT_RANKING`**，且 **`rank==1`** 或 **仅一条无 rank 锚点**；且 **`entityName` 非空**。
3. **非纯时间追问**：`AiFollowUpResolution` 不为 `TIME_SHIFT`，且用户表述不匹配「仅上个月/本月」类 **时间承接**（详见实现 `purchaseFollowUpSkipsObjectDrilldownForTimeOnly`）。
4. **明细语义**：`purchaseFollowUpRequestsGoodsUnitPriceDetail` 为真（商品、单价、明细、多少钱等 **结构化触发条件**）。

**效果**：

- `structuredIntentDetail` wire → **`purchase_source_goods_query`**（`AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY`）。
- `purchaseSourceType` 缺省或为 `ALL` 时归一为 **`SUPPLIER_PURCHASE`**。
- 写入 `followUpAction=OBJECT_DRILLDOWN`、`followUpDetailWanted=GOODS_UNIT_PRICE`、`followUpTargetEntityName` = 锚点名等。

**明确不触发**：「上个月呢」等 **仅时间平移** — 应保持 **`supplier_amount_ranking`** 类 wire 与排行计划类型。

---

## 5. Planner：如何按 `followUpAction` 选 Tool

- **采购单域**仍以 **`PURCHASE_OVERVIEW` / `purchase_overview`** 为主 Tool；下钻 **不新增随意 Tool**，而是通过 **structured wire + purchaseSourceType** 切换 **`PurchaseAnswerPlan` 类型**。
- **`purchase_source_goods_query` + SUPPLIER_PURCHASE** → **`PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL`**（与 `PurchaseAnswerPlanBuilder.resolvePlanType` 一致）。
- **Replay 探针**：`AiHarnessReplayContextProbes.resolvePurchasePlanType` 必须与 Builder 对齐，避免「协议断裂」假阴性。

---

## 6. AnswerPlan：如何产出 `resultAnchors`

见 **`docs/ai/result-anchor-protocol.md`**。摘要：**仅从已有 focus/overview 行抽取，不重算 SQL**。

---

## 7. TurnMemory：锚点与语义摘要

- **`lastResultAnchors`**：Completed 轮次从 Plan 写入。
- **持久化**：经 `tool_summary` 前缀 `nx_ctm_ra_json=`（详见 result-anchor 文档）。
- **语义输入**：`resultAnchorsSummary` 供下一轮合并层 / LLM 读。

---

## 8. Harness Debug 输出清单（建议）

| 键 | 说明 |
|----|------|
| `followUpAction` | 本轮解析认定动作 |
| `followUpTargetEntityType` / `followUpTargetEntityName` | 对象焦点 |
| `followUpDetailWanted` | 明细类型 |
| `followUpSourcePlanType` | 锚点来源计划类型 |
| `structuredIntentDetailWire` | wire 级意图 |
| `harnessReplayPurchaseAnswerPlanType` | Replay 解析探针计划类型 |
| `resultAnchorsCount` / `previousTurnSummary.resultAnchorsCount` | 锚点条数 |
| `purchaseAnswerPlanResultAnchorsCount` | 当前轮 Plan 锚点数（若已生成） |

---

## 9. Replay：固定用例（第一条落地）

**CaseId**：`PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3`  

**默认模式**：与 `PURCHASE_AGENT_GRAPH_CORE` 相同，**未指定 `replayMode` 时为 `GRAPH_RUN`**。

**三轮话术**：

1. `这个月哪个供应商供货金额最高`
2. `上个月呢`
3. `采购了哪些商品？单价分别是多少？`

**第三轮最小断言**（内置预期：`AiHarnessBuiltinCases.expectationsPurchaseSupplierRankingDrilldownGoodsUnitPrice3`）：

- `effectiveTimeWindowSource == INHERITED_PREVIOUS`，区间为上整月（与 frozenClock 推导一致）。
- `purchaseSourceType == SUPPLIER_PURCHASE`。
- `structuredIntentDetailWire == purchase_source_goods_query`。
- `harnessReplayPurchaseAnswerPlanType == PURCHASE_SUPPLIER_GOODS_DETAIL`。
- `followUpAction == OBJECT_DRILLDOWN`（`DETAIL_DRILLDOWN` 为预留别名，当前实现见 Resolver）。

**脚本**：已与 `scripts/harness/run-local-replay-regression-bundle.sh` 内置 `run_one` 对齐。

---

## 10. D-13.2 与全量回归（记录）

| 项目 | 说明 |
|------|------|
| **D-13.1 SUPPLIER** | 已封版；case `PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3` |
| **D-13.2 STORE** | 已封版；case `BUSINESS_STORE_PRIORITY_DRILLDOWN_REASONS_3`（经营 → 「哪个门店问题最大」→ 「具体是什么问题？」） |
| **全量 replay** | 同一 bundle 脚本下：`BUSINESS_DIAGNOSIS_V1_CORE_3`、`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`、`DISH_PROFIT_AGENT_GRAPH_CORE`、`PURCHASE_AGENT_GRAPH_CORE`、`PURCHASE_MULTITURN_1`、`PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3`、`REVENUE_AGENT_GRAPH_CORE`、`STOCK_REDUCE_AGENT_GRAPH_CORE`、`V2_SEMANTIC_MAINLINE_CORE_10`、`BUSINESS_STORE_PRIORITY_DRILLDOWN_REASONS_3` 均为 **`overallPass=true`**；`PROBE_STORY_7_MULTITURN`（`ignoreExpectations`）`overallPass=null` 为预期。 |
| **D-13.3** | **不继续扩展**（本里程碑收口）。 |

---

## 11. 禁止项（与全局约束一致）

- 不让 LLM 自由写 SQL；不改业务 SQL 口径；不在 Composer 硬编码答案；不单点 `contains` 补丁替代结构化协议。
