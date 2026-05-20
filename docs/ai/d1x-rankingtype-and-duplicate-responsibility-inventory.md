# D-1X-D：`metric.rankingType` 与重复职责梳理清单

> **状态（2026-05-20）**：**D-1X-D3-RANKINGTYPE-FINAL 已完成** — 下文 **「现网契约」** 为 Cursor / 工程师唯一应遵循的生产口径；**§2 及以后** 为 **2026-05-19 盘点快照（Historical inventory）**，**不是** 当前待办清单。  
> **禁止**：按 §2 表格恢复 `applyMetricStructuredWire`、`backfillSlotsFromMetric`、rankingType → `qi.structuredIntentDetail`、AnswerPlan / Composer rankingType fallback。

---

## 现网契约（Current production contract）

**主语义依据（写 `queryIntent.structuredIntentDetail` / path / AnswerPlan planType）**

| 来源 | 说明 |
|------|------|
| V2 **`semanticSlots`** | `queryObject` / `operation` / `metric` / `sourceFacet` / `anchorPolicy` / **`structuredIntentDetailWire`** |
| **`currentTurnStructuredIntentDetailWire`** | 本轮 LLM JSON 显式 wire 快照（`AiQuerySemanticParseResult`） |
| Merge | `AiQuerySemanticLlmMergeHelper.apply*StructuredWireFromSemanticSlots` + `applyCanonicalStructuredIntentDetailWireFromSemanticSlots` |
| Resolver | slots 驱动纠偏（如 `correctPurchaseIntentWireFromSemanticSlots`）；**不**用 rankingType 升级 wire |

**`metric.rankingType`（compat / debug only）**

| 允许 | 禁止 |
|------|------|
| LLM JSON 字段解析（`AiQuerySemanticParseResultJsonParser`） | 写 `queryIntent.structuredIntentDetail` |
| Debug 序列化（`AiQuerySemanticParseResultDebugSerializer`、`AiHarnessResolvedContextSummarizer.metric.rankingType`） | 定 `effectivePathCode` / `effectiveIntentCode` |
| `metricRankingTypeCompat` / `metricRankingTypeCompatForLog`（plan debug、Composer 日志） | `DishSalesAnswerPlanBuilder` planType |
| Resolver `describeAdoptedSemanticFields` 变更键列表 | Composer / Renderer 排行 takeover |

**消费层（AnswerPlan / Composer）**

- `PurchaseAnswerPlanBuilder` / `StockReduceAnswerPlanBuilder` / `DailyRevenueAnswerPlanBuilder` / `DishProfitAgentNode`：只读 **`queryIntent.structuredIntentDetail`**（及 AnswerPlan 已选事实）。
- `DishSalesAnswerPlanBuilder`：只读 **`structuredIntentDetail`** 或 V2 **`semanticSlots.structuredIntentDetailWire` / `currentTurnStructuredIntentDetailWire`**（**D-1X-D3**）。
- `WarehouseDeterministicRenderer` / `StubAnswerComposerNode`（库房排行）：只读 **`structuredIntentDetail`**（**D-CLEAN-RENDERER-FALLBACK-FINAL**）。

**Historical removed（勿恢复）**：`applyMetricStructuredWire`、`backfillSlotsFromMetric`、`AiQuerySemanticV2*Normalizer` 中 rankingType 主路由、`StockReduceDeterministicRenderer`、已删 Tool 的 Composer raw fallback。

---

## §1 背景与目标（Historical — 盘点动机）

### 1.1 背景

D-1X-C 已完成 V2-only 时间源与 prompt/schema 收口。下一阶段 **D-1X-D** 聚焦 `metric.rankingType` 在主链路中的角色，以及由此暴露的 **重复业务逻辑 / 重复职责** 问题。

历史上，LLM 解析 JSON 中的 `metric.rankingType` 曾作为 structured 子口径（wire）的主要推断来源。Phase 1（采购 1A、经营 1B、出库 1C）已引入：

- `semanticSlots.structuredIntentDetailWire`（及 queryObject / operation / metric / sourceFacet 等槽位）
- `queryIntent.structuredIntentDetail`（canonical wire 落地）
- `CurrentSemanticFrameValidator` 等帧校验

盘点时（2026-05-19），Merge、Resolver、消费层仍有多条 **compat / fallback** 路径；**截至 D-1X-D3（2026-05-20）主链已收口**，见上文 **现网契约**。

### 1.2 D-1X-D 目标

D-1X-D **不是**简单删除 `metric.rankingType` 字段，而是：

1. **确认主语义来源**：当前轮 **`semanticSlots` + `canonicalStructuredIntentDetailWire`**（落地为 `queryIntent.structuredIntentDetail`）为唯一权威。
2. **降级 compat 字段**：`metric.rankingType` 仅允许作为 **debug 展示、历史 compat、deprecated 观测**；不得覆盖当前轮已明确的 slots wire。
3. **消除重复写口**：合并、Resolver、AnswerPlan、Composer 中重复的 wire / planType / source 推断，避免继续叠加业务域 if/else。
4. **分阶段实施**：D1 → D2 → D3 → D4，每阶段独立 PR + 固定 Harness 回归。

### 1.3 设计原则（实施时必须遵守）

| # | 原则 |
|---|------|
| 1 | 当前主语义依据是 `semanticSlots` + `canonicalStructuredIntentDetailWire` |
| 2 | `metric.rankingType` 只能作为 compat / debug / 辅助字段 |
| 3 | 当前轮 `semanticSlots` 已明确 wire 时，`metric.rankingType` **不得**覆盖 |
| 4 | `previousTurn` 的 rankingType **不得**污染当前轮 broad overview / summary |
| 5 | 不为单个 replay case 写中文关键词业务 if |
| 6 | 时间只走 V2 `time` / `timeAction` / `timeSource` + **`SemanticTimeContractCheck`**（**Historical removed**：`AiQuerySemanticTimeLexicon`、`AiMultiTurnTimeWindowPolicy`） |

---

## §2 `metric.rankingType` 全仓库读取点（Historical inventory — Pre-D3 audit）

> **读表须知**：本表为 **2026-05-19 代码审计快照**。若某行描述「rankingType → qi」「主写口」「抢权」且对应类/方法已在 **D-CLEAN-V1 / D-1X-D3** 删除，以 **现网契约** 与当前 `src/main/java` 为准，**勿** 当作待修复项。

说明列：

- **写 qi**：是否写入 `queryIntent.structuredIntentDetail`
- **写 path**：是否改变 `queryIntent.pathCode` / intent
- **写 planType**：是否在 AnswerPlan 层决定 planType（通常经 wire 间接决定）
- **覆盖 slots**：是否可能覆盖或无视 `semanticSlots.structuredIntentDetailWire`
- **分类**：debug / compat / 抢权 / 可摘链 / 暂留

### 2.1 解析与模型定义

| 文件 | 方法 / 位置 | 读取 rankingType 做什么 | 写 qi | 写 path | 写 planType | 覆盖 slots | 分类 |
|------|-------------|-------------------------|-------|---------|-------------|------------|------|
| `AiQuerySemanticParseResultJsonParser` | JSON `metric.rankingType` 解析 | 从 LLM 输出装入 `MetricPart` | 否 | 否 | 否 | 否 | debug（解析入口） |
| `AiQuerySemanticParseResult` | `MetricPart`、类注释 | 字段定义；注释声明 wire 不得单独由 rankingType 反推 | 否 | 否 | 否 | 否 | debug |
| `AiQuerySemanticLexicon` | `canonicalStructuredIntentDetailWire` 等 | rankingType 值与 structured wire 的 canonical 映射（基础设施） | 否 | 否 | 否 | 否 | compat（映射表） |

### 2.2 Normalize / Gate（Resolver 收养前）

| 文件 | 方法 | 读取 rankingType 做什么 | 写 qi | 写 path | 写 planType | 覆盖 slots | 分类 |
|------|------|-------------------------|-------|---------|-------------|------------|------|
| ~~`AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer`~~ | ~~`rankingTypeSuggestsStockReduce`~~ | **D-CLEAN-V1 Historical removed**；改由 V2 `intent` + `semanticSlots` + `hasExplicitStockReduceRouteSignal` | — | — | — | — | 已删 |
| ~~`AiQuerySemanticV2DishProfitGate`~~ | ~~`sanitize`~~ | **D-1X-B 已删除**；菜品毛利改走 v2 semanticSlots → Validator | — | — | — | — | 已删 |

### 2.3 `AiQuerySemanticSlotMerge`（Historical — 部分方法已删）

| 方法 | 读取 rankingType 做什么 | 写 qi | 写 path | 写 planType | 覆盖 slots | 分类 |
|------|-------------------------|-------|---------|-------------|------------|------|
| ~~`applyPreviousFrameInheritance` → `backfillSlotsFromMetric`~~ | **Historical removed（D-1X-D3）**；曾用 rankingType 反推 SlotTriple | — | — | — | — | 已删 |
| `semSignalsExplicitBusinessCompareOrRanking` | 读 **当前轮** `metric.rankingType` 判断是否显式排行/对比 | 否 | 否 | 否 | 否（影响 broad 判定） | 抢权 |
| `semSignalsExplicitRevenueRankingOrCompare` | 同上（营业额域） | 否 | 否 | 否 | 否 | 抢权 |
| `clearInheritedBusinessRankingMetricIfAbsentOnCurrent` | broad 经营总览时 **清空** 继承的 `metric.rankingType` | 否 | 否 | 否 | 否 | 暂留（1B guard） |
| `clearInheritedRevenueRankingMetricIfAbsentOnCurrent` | broad 营业额总览时 **清空** 继承 rankingType | 否 | 否 | 否 | 否 | 暂留（1B 对称） |
| `sanitizeBusinessOverviewBroadAgainstRankingInheritance` | broad 经营总览时净化继承的 **slots wire / operation** | 否 | 否 | 否 | 是（改 slots wire） | 暂留（1B guard） |
| `sanitizeRevenueOverviewBroadAgainstRankingInheritance` | broad 营业额同理 | 否 | 否 | 否 | 是 | 暂留 |
| `applyPurchaseMatrixSemanticRepairs` | 单价追问场景 **清空** rankingType | 否 | 否 | 否 | 否 | 暂留 |
| `hasPurchaseStructuredIntentWireFromSlots` | gate：slots 已有 wire → 下游跳过 rankingType 写 qi | 否 | 否 | 否 | 否 | 暂留（D1 模板） |
| `purchaseMetricRankingWireBlockedByExplicitOperation` | 非 RANKING operation → 禁止 rankingType 写 qi wire | 否 | 否 | 否 | 否 | 暂留 |
| `applySemanticSlotsGoodsAmountRankingWire` | 由 **slots** 写 goods 排行 wire 到 qi（**不读** rankingType） | **是** | 否 | 间接 | 否（权威路径） | compat（slots 主路径） |
| `inferSlotsFromWireAndSource` | 从 wire + purchaseSourceType 反推 slots（不用 rankingType） | 否 | 否 | 否 | 否 | compat |

### 2.4 `AiQuerySemanticLlmMergeHelper`（Historical — 现网仅 slots 写 wire）

**现网（2026-05-20）**：`mergeIntent` 仅调用 `applyPurchaseStructuredWireFromSemanticSlots`、`applyStockReduceStructuredWireFromSemanticSlots`、`applyCanonicalStructuredIntentDetailWireFromSemanticSlots`；**无** `applyMetricStructuredWire` / rankingType remap。

合并顺序（**Historical inventory — Pre-D3**，已不适用）：

```text
… → applyMetricStructuredWire  ← Historical removed（曾：rankingType 主写口）
```

| 方法 | 读取 rankingType 做什么 | 写 qi | 写 path | 写 planType | 覆盖 slots | 分类 |
|------|-------------------------|-------|---------|-------------|------------|------|
| ~~`applyMetricStructuredWire`~~ | **Historical removed** | — | — | — | — | 已删 |
| ~~`remapWarehouseToStockReduceWhenOutboundRankingWire`~~ | **Historical removed** | — | — | — | — | 已删 |
| ~~`remapCostDiagnosisToDishProfitWhenDishMetricWire`~~ | **Historical removed** | — | — | — | — | 已删 |
| ~~`applyPurchaseAnomalyStructuredWireOverrideWhenMetricOrToolsSignal`~~ | **Historical removed** | — | — | — | — | 已删 |
| `applyPurchaseStructuredWireFromSemanticSlots` | **不读** rankingType；slots wire → qi | **是** | 否 | 间接 | 否 | **现网权威** |
| `applyStockReduceStructuredWireFromSemanticSlots` | **不读** rankingType；slots wire → qi | **是** | 否 | 间接 | 否 | **现网权威** |
| `applyCanonicalStructuredIntentDetailWireFromSemanticSlots` | **不读** rankingType | **是** | 否 | 间接 | 否 | **现网权威** |

### 2.5 `AiResolvedQueryContextResolver`（Historical — 现网 rankingType 仅 debug 键）

| 方法 | 读取 rankingType 做什么 | 写 qi | 写 path | 写 planType | 覆盖 slots | 分类 |
|------|-------------------------|-------|---------|-------------|------------|------|
| ~~`upgradePurchaseSupplierDimensionFromResolverSignals`~~ | **Historical removed（D-1X-D3）** | — | — | — | — | 已删 |
| ~~`metricRankingTypeSuggestsSupplierAmountRanking`~~ | **Historical removed** | — | — | — | — | 已删 |
| `correctPurchaseIntentWireFromSemanticSlots` | **不读** rankingType；slots goods 排行纠偏 supplier wire | **是** | 否 | 间接 | 否（slots 优先） | 暂留 |
| `describeAdoptedSemanticFields` | debug 列出已收养字段含 `metric.rankingType` | 否 | 否 | 否 | 否 | debug |
| `stabilizeDishProfitFollowUpStructuredIntent` | 恢复上一轮单菜子意图（读 prev structured，**非** rankingType） | **是** | 否 | 间接 | 否 | 暂留 |
| ~~`augmentV2SemanticWithInheritedHarnessMultiStores`~~ | harness 多店 scope 补丁 | **D-1X-B 已从 Resolver 主链路删除** | — | — | — | — | 已删 |

### 2.6 Harness / Debug

| 文件 | 方法 | 读取 rankingType 做什么 | 写 qi | 写 path | 写 planType | 覆盖 slots | 分类 |
|------|------|-------------------------|-------|---------|-------------|------------|------|
| `AiQuerySemanticParseResultDebugSerializer` | `metric.rankingType` 序列化 | 否 | 否 | 否 | 否 | debug |
| `AiHarnessResolvedContextSummarizer` | 输出 `querySemanticParse.metric.rankingType` | 否 | 否 | 否 | 否 | debug |
| `AiHarnessResolvedContextSummarizer` | `rawStructuredIntentDetail`：**D-1X-D3** 改读 `currentTurnStructuredIntentDetailWire` / slots；**不再**用 rankingType | 否 | 否 | 否 | 否 | debug |
| `AiHarnessResolvedContextSummarizer` | `structuredIntentDetailWire`：优先 `qi.structuredIntentDetail`，其次 slots wire | 否 | 否 | 否 | 否 | debug（生产比对口径） |
| `PurchaseFollowUpProtocolHydrator` | hydrate metric：复制上一轮 rankingType 到 hydration 帧 | 否 | 否 | 否 | 否 | 暂留 |

### 2.7 AnswerPlan / Composer（消费层）

| 文件 | 方法 | 读取 rankingType 做什么 | 写 qi | 写 path | 写 planType | 覆盖 slots | 分类 |
|------|------|-------------------------|-------|---------|-------------|------------|------|
| `PurchaseAnswerPlanBuilder` | wire 解析 | **不读** rankingType；只读 `queryIntent.structuredIntentDetail` | 否 | 否 | **是**（经 wire） | 否 | 合规 |
| `StockReduceAnswerPlanBuilder` | wire 解析 | **不读** rankingType | 否 | 否 | **是** | 否 | 合规 |
| `DailyRevenueAnswerPlanBuilder` | wire 解析 | **不读** rankingType | 否 | 否 | **是** | 否 | 合规 |
| `DishProfitAgentNode` | 多处 | **不读** rankingType；只读 structuredIntentDetail | 否 | 否 | 间接 | 否 | 合规 |
| `DishSalesAnswerPlanBuilder` | `resolveDishSalesWire` | **D-1X-D3**：仅 `qi.structuredIntentDetail` / V2 slots wire；`metricRankingTypeCompat` 仅 debug | 否 | 否 | **是** | 否 | **合规** |
| `WarehouseDeterministicRenderer` | `resolveStockRankingWire` | **D-CLEAN-RENDERER-FALLBACK-FINAL**：仅 `qi.structuredIntentDetail` → canonical wire（`DeterministicRendererSupport`） | 否 | 否 | 间接（渲染分支） | 否 | **合规** |
| `StubAnswerComposerNode` | `warehouseStockRankingDeterministicTakeoverEligible` | **同上**；日志 `metricRankingTypeCompat` 仅 debug | 否 | 否 | 间接 | 否 | **合规** |

### 2.8 Frame / 其他

| 文件 | 说明 | 分类 |
|------|------|------|
| `CurrentSemanticFrame` | `fromParseResult` 只用 slots wire；注释明确不从 rankingType 推断 | 合规 |
| `BusinessToolExecutionRequestResolver` | 读 `metric.stockReduceType`（非 rankingType）作 Tool 参数 | 无关 rankingType |

### 2.9 测试代码（非生产，实施时需同步）

- `AiQuerySemanticLlmMergeHelperTest`
- `AiQuerySemanticLlmMergeHelperPurchaseGoodsRankingRemapTest`
- `AiQuerySemanticV2StockReducePurchaseDeconflictNormalizerTest`

---

## §3 wire 多写口清单

凡是可以影响 **`queryIntent.structuredIntentDetail`**（canonical wire）或等价业务口径的路径，按来源分类如下。

### 3.1 semanticSlots 路径（**现网权威写口**）

| 位置 | 方法 | 行为 |
|------|------|------|
| `AiQuerySemanticLlmMergeHelper` | `applyPurchaseStructuredWireFromSemanticSlots` | `semanticSlots.structuredIntentDetailWire` → `qi.structuredIntentDetail`；`sourceFacet` → `qi.purchaseSourceType` |
| `AiQuerySemanticLlmMergeHelper` | `applyStockReduceStructuredWireFromSemanticSlots` | slots wire → qi |
| `AiQuerySemanticSlotMerge` | `applySemanticSlotsGoodsAmountRankingWire` | 采购 goods 排行：slots → qi wire |
| `AiQuerySemanticSlotMerge` | `sanitizeBusinessOverviewBroadAgainstRankingInheritance` | broad 经营：净化 **slots** wire（非 qi） |
| `AiQuerySemanticSlotMerge` | `sanitizeRevenueOverviewBroadAgainstRankingInheritance` | broad 营业额：净化 slots wire |
| `AiResolvedQueryContextResolver` | `correctPurchaseIntentWireFromSemanticSlots` | slots goods 排行 → 纠偏 qi supplier wire |

**结论**：Merge 阶段 slots → qi 的两次调用（purchase/stock）+ `applySemanticSlotsGoodsAmountRankingWire` 为 **唯一权威写口**；Resolver 仅做 slots 驱动的 **纠偏**，不做 rankingType 升级。

### 3.2 rankingType 路径（**Historical inventory — 均已删除或收口**）

| 位置 | 方法 | 状态 |
|------|------|------|
| ~~`AiQuerySemanticLlmMergeHelper.applyMetricStructuredWire`~~ | rankingType → qi | **Historical removed** |
| ~~`applyPurchaseAnomalyStructuredWireOverrideWhenMetricOrToolsSignal`~~ | rankingType 覆盖 wire | **Historical removed** |
| ~~`upgradePurchaseSupplierDimensionFromResolverSignals`~~ | rankingType → qi wire | **Historical removed** |
| ~~`backfillSlotsFromMetric`~~ | rankingType → slots 三元组 | **Historical removed** |
| ~~`DishSalesAnswerPlanBuilder.resolveDishSalesWire`~~ | rankingType fallback | **D-1X-D3 已删** |
| ~~库房 Composer/Renderer rankingType fallback~~ | — | **D-CLEAN-RENDERER-FALLBACK-FINAL 已删** |

**结论（现网）**：**无** rankingType → `qi.structuredIntentDetail` 主路径；见文首 **现网契约**。

### 3.3 stockReduceType 路径（facet，**非 wire 权威**）

| 位置 | 方法 | 行为 |
|------|------|------|
| `AiQuerySemanticLlmMergeHelper` | `applyStockReduceMetricFacetToStructuredWireIfAllowed` | facet → qi wire（无 explicit wire 时；ALL → overview summary） |
| `AiQuerySemanticLlmMergeHelper` | `hasExplicitStockReduceRouteSignal` | 读 facet 作 intent 路由信号（deconflict，不写 qi） |
| `AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer` | 多处 | 读 facet；采购误标时清空 pst / 改 intent |
| `BusinessToolExecutionRequestResolver` | Tool 参数组装 | 读 `metric.stockReduceType` → Tool 入参（**正确归属**） |
| `AiQuerySemanticLexicon` | canonical 映射 | facet 值可映射为 structured wire |

**结论**：facet 职责 = **Tool 过滤维度 + intent 纠偏信号**；wire 权威在 slots。已有 explicit wire（含排行）时 facet **不得**覆盖（部分 guard 已存在，D1 需 universal slots gate）。

### 3.4 purchaseSourceType / sourceFacet 路径

| 位置 | 方法 | 行为 |
|------|------|------|
| `AiQuerySemanticSlotMerge` | `reconcileMetricWithSourceFacet` | **sourceFacet → metric.purchaseSourceType**（单向同步） |
| `AiQuerySemanticLlmMergeHelper` | `applyPurchaseStructuredWireFromSemanticSlots` | sourceFacet → qi.purchaseSourceType |
| `AiQuerySemanticLlmMergeHelper` | `effectivePurchaseSourceTypeForPurchaseMerge` | 读 sourceFacet → metric.pst → qi.pst（Merge 决策） |
| `AiQuerySemanticLlmMergeHelper` | `applyPurchaseSupplierChannelAmountSummaryWire` | pst/supplier 信号 → qi wire |
| `AiResolvedQueryContextResolver` | `upgradePurchaseSupplierDimensionFromResolverSignals` | rankingType / pst / **中文话术** → qi wire + pst |
| `AiQuerySemanticSlotMerge` | `applyUtteranceAlignedSourceFacetAndAnchorPolicy` | `PurchaseSemanticSurfaceSignals` → 改 sourceFacet / anchorPolicy |
| `AiHarnessResolvedContextSummarizer` | `reconcileHarnessPurchaseSourceType` | AnswerPlan planType → 摘要 pst（观测修正） |

**结论**：语义源 = **sourceFacet**；Tool 入参 = **qi.purchaseSourceType**；metric.purchaseSourceType = LLM compat（由 reconcile 同步）。Resolver 话术升级 wire 属于 **技术债**（§6），不在 D-1X-D 主 scope 全量收口。

### 3.5 previousTurn inheritance 路径

| 位置 | 方法 | 行为 |
|------|------|------|
| `AiQuerySemanticSlotMerge` | `applyPreviousFrameInheritance` | 合并 prev slots / wire 反推 slots |
| `AiQuerySemanticSlotMerge` | `enrichStructuredWireFromPreviousTurnDetailIfAbsent` | prev structured → slots wire |
| ~~`AiQuerySemanticSlotMerge`~~ | ~~`backfillSlotsFromMetric`~~ | **Historical removed（D-1X-D3）** |
| `AiQuerySemanticSlotMerge` | `clearInheritedBusinessRankingMetricIfAbsentOnCurrent` | broad 经营：清 metric.rankingType |
| `AiQuerySemanticSlotMerge` | `clearInheritedRevenueRankingMetricIfAbsentOnCurrent` | broad 营业额：清 metric.rankingType |
| `PurchaseContinuationFrameHydrator` | hydrate 采购时间续接 | 读 prev frame / timeAction |
| `AiResolvedQueryContextResolver` | `stabilizeDishProfitFollowUpStructuredIntent` | prev structured → qi |

**结论**：继承应限于 **slots 显式 INHERIT** 与 **time/scope pivot**；rankingType inherit 在 broad overview 必须 strip（1B 已有 business/revenue；stockReduce 缺对称）。

### 3.6 Resolver surface signal 路径

| 位置 | 方法 | 行为 |
|------|------|------|
| `AiResolvedQueryContextResolver` | `upgradePurchaseSupplierDimensionFromResolverSignals` | rankingType + pst + orchestration + **parsePurchaseSupplierTextSignals** → qi |
| `AiResolvedQueryContextResolver` | `upgradePurchaseStoreRankingAfterRevenueFollowUp` | 多店 scope 升级（非 rankingType 直读） |
| `AiQuerySemanticLlmMergeHelper` | `applyBusinessStoreStatusCompareWhenMultiStoreMentioned` | 多店 + explicit compare → qi compare wire |
| `AiQuerySemanticLlmMergeHelper` | `applyRevenueStoreAmountRankingWhenMultiStoreMentioned` | 多店 scope → store ranking wire |
| `AiQuerySemanticLlmMergeHelper` | `applyPurchaseStoreAmountRankingWhenMultiStoreMentioned` | 多店 scope → purchase store ranking wire |
| `AiQuerySemanticLlmMergeHelper` | `applyStockReduceStoreAmountRankingWhenMultiStoreMentioned` | 多店 scope → stock store compare wire |

**结论**：多店 compare/ranking 应 **仅** 在 slots operation=RANKING/COMPARE 或显式 compare intent + 多店 scope 时触发；Resolver 中文话术写 wire **应删除**（技术债）。

### 3.7 默认 overview 路径（最后 compat）

| 位置 | 方法 | 行为 |
|------|------|------|
| `AiQuerySemanticLlmMergeHelper` | `normalizePathsLikeKeywordResolver` | path 已知且 qi wire 空 → 默认 overview summary wire |

**结论**：仅当 slots wire 与 rankingType 与 facet 皆无法落地时使用；D-1X-D3 标注 deprecated。

### 3.8 主写口 vs 已删除/降级（汇总 — **D-1X-D3 已落地**）

| 优先级 | 写口 | 处置（现网） |
|--------|------|----------------|
| **1（权威）** | slots → `applyPurchase/StockReduceStructuredWireFromSemanticSlots` | ✅ 保留 |
| **1（权威）** | slots → `applySemanticSlotsGoodsAmountRankingWire` | ✅ 保留 |
| **2（compat）** | `normalizePathsLikeKeywordResolver` | 最后默认 overview（slots 皆空时） |
| **3** | ~~`applyMetricStructuredWire`~~ | **Historical removed** |
| **3** | ~~`upgradePurchaseSupplierDimension` rankingType 分支~~ | **Historical removed / 话术路径 §6** |
| **3** | 消费层 rankingType fallback | **D-1X-D3 + D-CLEAN-RENDERER 已删** |
| **4（技术债）** | Resolver 中文话术、sourceFacet 话术双轨 | §6（非 rankingType 主链） |

---

## §4 重复业务逻辑 / 重复职责清单

分类说明：

- **A**：必须随 D-1X-D 处理
- **B**：技术债，后续专项处理
- **C**：合法兼容，D-1X-D 不删除，可文档化
- **D**：可删除的重复逻辑

---

### R1. Business / Revenue broad overview 防 ranking 污染（双份对称实现）

- **涉及文件 / 方法**：
  - `AiQuerySemanticSlotMerge.semSignalsBusinessOverviewBroadQuery`
  - `AiQuerySemanticSlotMerge.semSignalsRevenueOverviewBroadQuery`
  - `AiQuerySemanticSlotMerge.clearInheritedBusinessRankingMetricIfAbsentOnCurrent`
  - `AiQuerySemanticSlotMerge.clearInheritedRevenueRankingMetricIfAbsentOnCurrent`
  - `AiQuerySemanticSlotMerge.sanitizeBusinessOverviewBroadAgainstRankingInheritance`
  - `AiQuerySemanticSlotMerge.sanitizeRevenueOverviewBroadAgainstRankingInheritance`
  - `AiQuerySemanticSlotMerge.businessOverviewBroadBlocksInheritedRankingWire`
  - `AiQuerySemanticSlotMerge.revenueOverviewBroadBlocksInheritedRankingWire`
- **重复了什么职责**：同一套「broad 总览 ≠ 继承排行/compare」在经营、营业额各实现一遍；出库（StockReduce）**缺少第三份对称实现**
- **当前风险**：修 1B 漏出库；经营域 qi 层无 Merge skip（营业额有 `applyMetricStructuredWire` revenue broad return）
- **建议归属**：`AiQuerySemanticSlotMerge` 或独立 `OverviewBroadPolicy`：domain-agnostic 的 broad 判定 + inherit strip + merge skip
- **分类**：**A**（D-1X-D2）

---

### R2. `applyMetricStructuredWire` 与 slots wire 多入口写 qi

- **涉及文件 / 方法**：
  - `AiQuerySemanticLlmMergeHelper.applyPurchaseStructuredWireFromSemanticSlots`
  - `AiQuerySemanticLlmMergeHelper.applyStockReduceStructuredWireFromSemanticSlots`
  - `AiQuerySemanticLlmMergeHelper.applyMetricStructuredWire`
  - `AiQuerySemanticSlotMerge.applySemanticSlotsGoodsAmountRankingWire`
  - `AiQuerySemanticLlmMergeHelper.applyPurchaseSupplierChannelAmountSummaryWire`
  - `AiQuerySemanticLlmMergeHelper.normalizePathsLikeKeywordResolver`
  - `AiResolvedQueryContextResolver.upgradePurchaseSupplierDimensionFromResolverSignals`
- **重复了什么职责**：同一 canonical wire 可由 slots、rankingType、默认 overview、Resolver 信号分别写入 qi
- **当前风险**：后写覆盖先写；Harness 观测与生产分叉
- **建议归属**：唯一权威写口 = slots → qi；其余路径必须 `slotsWireCanonicalNonEmpty → return`
- **分类**：**A**（D-1X-D1）

---

### R3. rankingType → SlotTriple 回填 vs slots 已完整

- **涉及文件 / 方法**：`AiQuerySemanticSlotMerge.backfillSlotsFromMetric`、`inferTripleFromRankingCanon`
- **重复了什么职责**：LLM 应直接输出 slots；rankingType 又反推 queryObject/operation/metric
- **当前风险**：`semSignalsExplicit*CompareOrRanking` 可能在 clear inherited rankingType 之前读到污染值
- **建议归属**：SlotMerge 仅 `reconcileMetricWithSourceFacet`；禁止 rankingType 回填 slots（采购注释已声明，但未全域执行）
- **分类**：**A**（D-1X-D1/D2）

---

### R4. 显式 compare/ranking 判定重复读 metric.rankingType

- **涉及文件 / 方法**：
  - `AiQuerySemanticSlotMerge.semSignalsExplicitBusinessCompareOrRanking`
  - `AiQuerySemanticSlotMerge.semSignalsExplicitRevenueRankingOrCompare`
- **重复了什么职责**：slots 已有 `operation=RANKING/COMPARE` 与 wire，仍读 `metric.rankingType` 与 primaryMetric
- **当前风险**：inherit 清空前误判「本句显式排行」→ 不走 broad 路径
- **建议归属**：broad 判定 **仅读 slots**（operation + structuredIntentDetailWire + 多店 scope）
- **分类**：**A**（D-1X-D2）

---

### R5. StockReduce broad overview 无 symmetric 防污染

- **涉及文件 / 方法**：对比 Business/Revenue 完整链；StockReduce 仅有 `applyMetricStructuredWire` 内 overview vs outbound ranking 局部 guard、`applyStockReduceMetricFacetToStructuredWireIfAllowed`
- **重复了什么职责**：1C validator 在 slots 层防污染，但 **无** metric.rankingType inherit clear 与 qi 层 broad skip
- **当前风险**：「出库总览 + 继承排行 rankingType」仍可能写 qi
- **建议归属**：复用 R1 抽象 policy + `PATH_STOCK_REDUCE_QUERY` + `STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY`
- **分类**：**A**（D-1X-D2；对齐 1C，不改 validator 既有语义）

---

### R6. purchaseSourceType 三处互写（sourceFacet / metric / qi）

- **涉及文件 / 方法**：
  - `AiQuerySemanticSlotMerge.reconcileMetricWithSourceFacet`
  - `AiQuerySemanticLlmMergeHelper.applyPurchaseStructuredWireFromSemanticSlots`
  - `AiQuerySemanticLlmMergeHelper.effectivePurchaseSourceTypeForPurchaseMerge`
  - `AiResolvedQueryContextResolver.upgradePurchaseSupplierDimensionFromResolverSignals`
  - `AiHarnessResolvedContextSummarizer.reconcileHarnessPurchaseSourceType`
- **重复了什么职责**：sourceFacet 是语义源，purchaseSourceType 是 Tool 入参，但 Merge、Resolver、Harness 均可改 pst
- **当前风险**：Validator 校验 pst↔facet 一致后，Resolver 仍 upgrade；Harness 从 AnswerPlan 反写 pst
- **建议归属**：sourceFacet → qi.purchaseSourceType **单向一次**（Merge）；Resolver 禁止改 pst；Harness 只观测
- **分类**：**B**（采购专项；D-1X-D 仅禁 rankingType 写 wire）

---

### R7. Resolver 供货商升级：rankingType + 中文话术 + orchestration 三信号写 wire

- **涉及文件 / 方法**：
  - `AiResolvedQueryContextResolver.upgradePurchaseSupplierDimensionFromResolverSignals`
  - `AiResolvedQueryContextResolver.parsePurchaseSupplierTextSignals`
  - `AiResolvedQueryContextResolver.metricRankingTypeSuggestsSupplierAmountRanking`
- **重复了什么职责**：与 slots wire、`applyPurchaseSupplierChannelAmountSummaryWire`、`applyMetricStructuredWire` 采购分支重复
- **当前风险**：违反「不写中文关键词 if」原则；slots 已有 summary wire 时仍可能 force upgrade
- **建议归属**：Resolver 只补 agent 路由，不写 wire；wire 仅来自 Merge(slots)
- **分类**：**A**（D-1X-D1：强化 slots gate，禁 rankingType 写 wire）

---

### R8. stockReduceType facet 与 rankingType 双 compat 写 wire

- **涉及文件 / 方法**：
  - `AiQuerySemanticLlmMergeHelper.applyStockReduceMetricFacetToStructuredWireIfAllowed`
  - `AiQuerySemanticLlmMergeHelper.applyMetricStructuredWire`（stock 分支）
  - `AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal`
- **重复了什么职责**：facet 与 rankingType 都能推 wire
- **当前风险**：slots 有 overview wire 时 rankingType 仍可能覆盖（部分 guard）
- **建议归属**：slots wire 权威；facet 仅 Tool；rankingType compat 删除
- **分类**：**A**（D-1X-D1/D3）

---

### R9. path 默认 overview wire（normalizePathsLikeKeywordResolver）

- **涉及文件 / 方法**：`AiQuerySemanticLlmMergeHelper.normalizePathsLikeKeywordResolver`
- **重复了什么职责**：与 LLM slots wire、rankingType compat 重复填默认 overview
- **当前风险**：structuredIntentDetail 空时 Java 猜 overview，掩盖 slots 缺失
- **建议归属**：保留为 **最后 compat**（slots + metric 皆空）；D-1X-D3 文档标注 deprecated
- **分类**：**C**

---

### R10. 时间 canonical：MergeHelper 与 Summarizer 双实现

- **涉及文件 / 方法**：
  - `AiQuerySemanticLlmMergeHelper.canonicalQuerySemanticV2TimeActionForHarness`
  - `AiHarnessResolvedContextSummarizer` 内 effectiveTimeWindowSource 推断（读 `AiQuerySemanticTimeLexicon`）
- **重复了什么职责**：OVERRIDE + THIS_MONTH 纠正逻辑两处维护
- **当前风险**：Harness 与生产 timeAction 漂移
- **建议归属**：Summarizer 只调用 MergeHelper canonical 方法
- **分类**：**B**（D-1X-D4 Harness 专项）

---

### R11. needClarification / detailWanted / followUp 三角判定

- **涉及文件 / 方法**：
  - `SemanticParseFallbackPolicy.needSemanticParseClarification`
  - `AiResolvedQueryContextResolver.trySemanticAdoption`（time pivot 清 needClarification）
  - `CurrentSemanticFrameValidator.validate`
  - `PurchaseFollowUpSlotSignals`（`isEffectiveStructuralPurchaseFollowUp`、`resolveSlotDetailWanted`、`isPurchaseOverviewSummaryScopeTimePivotFollowUp`、`shouldSkipObjectDrilldownForTimeOnly`）
  - `PurchaseFollowUpProtocolHydrator.maybeHydrateStructuralFollowUpSlots`
  - `AiResolvedQueryContextResolver` L2996+ 再次调用 SlotSignals
- **重复了什么职责**：谁决定「要不要澄清 / 要不要 detailWanted」在 Validator 与 SlotSignals 与 trySemanticAdoption 间交叉
- **当前风险**：time pivot 在 adoption 层 override；Validator 又用 SlotSignals 豁免 detailRequired
- **建议归属**：SlotSignals = 是否结构化追问；Validator = 帧自洽；FallbackPolicy = parse 不可用；trySemanticAdoption 不直接改 needClarification
- **分类**：**B**（follow-up 专项；D-1X-D2 broad pivot 可能触及）

---

### R12. AnswerPlan 与 Composer/Renderer 重复解析 wire

- **涉及文件 / 方法**：
  - ~~`DishSalesAnswerPlanBuilder.resolveDishSalesWire`~~（**D-1X-D3 已完成**）
  - ~~`WarehouseDeterministicRenderer.resolveStockRankingWire`~~（**D-CLEAN-RENDERER-FALLBACK-FINAL 已收口**）
  - ~~`StubAnswerComposerNode.warehouseStockRankingDeterministicTakeoverEligible`~~（**同上**）
- **重复了什么职责**：Plan 已选 planType 后，Composer/Renderer 又用 rankingType 重算 wire
- **当前风险**：已收口；库房排行与菜品销量 AnswerPlan 均只读 structured wire / slots
- **建议归属**：Composer 只读 AnswerPlan / qi.structuredIntentDetail；删除 rankingType fallback
- **分类**：**A**（D-1X-D3；库房侧 **已部分完成**）

---

### R13. Harness rawStructuredIntentDetail 与 structuredIntentDetailWire 双轨

- **涉及文件 / 方法**：`AiHarnessResolvedContextSummarizer`（raw 优先 rankingType；wire 优先 qi 再 slots）
- **重复了什么职责**：同一 structured 语义两个观测口径
- **当前风险**：期望写 raw 而生产走 wire → 假红/假绿
- **建议归属**：Harness 主断言 `canonicalStructuredIntentDetailWire`；raw 仅 optional debug
- **分类**：**A**（D-1X-D4）

---

### R14. AiHarnessReplayContextProbes 与 AnswerPlanBuilder resolvePlanType 映射双份

- **涉及文件 / 方法**：
  - `AiHarnessReplayContextProbes`（注释对齐 `PurchaseAnswerPlanBuilder.resolvePlanType`、`StockReduceAnswerPlanBuilder.resolvePlanType`、`DailyRevenueAnswerPlanBuilder.resolvePlanType`）
  - 各 `*AnswerPlanBuilder.resolvePlanType`
- **重复了什么职责**：wire → planType 映射逻辑双份维护
- **当前风险**：Builder 改映射，Probe 未同步
- **建议归属**：Probe **直接调用** Builder static 方法（单源）
- **分类**：**C**（Harness 合法；D-1X-D4 确保单源）

---

### R15. 采购 goods 排行 wire：slots 路径 + rankingType 路径

- **涉及文件 / 方法**：
  - `AiQuerySemanticSlotMerge.applySemanticSlotsGoodsAmountRankingWire`
  - `AiQuerySemanticLlmMergeHelper.applyMetricStructuredWire` 内 `slotsIndicateGoodsPurchaseAmountRanking` 分支
- **重复了什么职责**：同一 goods amount ranking 两条写入 qi
- **当前风险**：rankingType 分支在 slots 不完整时仍写 wire
- **建议归属**：只保留 slots 路径；删除 rankingType 分支
- **分类**：**D**（D-1X-D1）

---

### R16. applyUtteranceAlignedSourceFacetAndAnchorPolicy vs Resolver supplier text signals

- **涉及文件 / 方法**：
  - `AiQuerySemanticSlotMerge.applyUtteranceAlignedSourceFacetAndAnchorPolicy`（`PurchaseSemanticSurfaceSignals`）
  - `AiResolvedQueryContextResolver.parsePurchaseSupplierTextSignals`
- **重复了什么职责**：都从用户句推 supplier/self/排行意图
- **当前风险**：Slot 层改 facet，Resolver 层又改 wire
- **建议归属**：话术表面信号仅在 SlotMerge 收养前一次；Resolver 不读原文
- **分类**：**B**（采购/sourceFacet 专项）

---

### R17. 业务域时间判断重复（专项核对）

- **涉及文件 / 方法**：
  - **合规主链**：`SemanticTimeContractCheck`、`AiQuerySemanticLlmMergeHelper`（候选窗镜像）、`BusinessTimeWindowNode`（**Historical removed**：`AiQuerySemanticTimeLexicon`、`AiMultiTurnTimeWindowPolicy`）
  - **展示层**：`AiTimeWindowTextFormatter`（回答展示「上个月」，非路由）
  - **重复/违规**：
    - `AiQuerySemanticLlmMergeHelper` 与 `AiHarnessResolvedContextSummarizer` 双份 timeAction canonical（R10）
    - `AiResolvedQueryContextResolver.parsePurchaseSupplierTextSignals` 含中文 contains（非时间，但同类话术 if 债务）
- **重复了什么职责**：时间 canonical 在 Merge 与 Harness 各一份；无 AnswerPlan 层月份判断
- **当前风险**：Harness timeAction 与生产不一致
- **建议归属**：时间路由仅 Lexicon + timeAction + MultiTurnTimeWindowPolicy
- **分类**：**B**（R10 归 D-1X-D4）；主链 **C**（已合规）

---

## §5 D-1X-D 分阶段实施方案

原则：**不要一次性大改**；每阶段独立 PR；不新增业务域中文关键词 if；不动 1A/1B/1C 已通过 guard 的语义。

---

### D-1X-D1：禁止 rankingType 覆盖当前轮 semanticSlots

**目标**：统一 slots wire 优先 gate（对齐采购已有 `hasPurchaseStructuredIntentWireFromSlots` 模式，扩展到全 path）。

**实施要点**：

1. 在 `applyMetricStructuredWire` 入口增加通用短路：  
   `semanticSlots.structuredIntentDetailWire` canonical 非空 → **return**（全 path，不仅采购/出库/营业额）。
2. `remapWarehouseToStockReduceWhenOutboundRankingWire` / `remapCostDiagnosisToDishProfitWhenDishMetricWire`：若 slots wire 已 canonical 且与 remap 目标冲突 → skip。
3. `upgradePurchaseSupplierDimensionFromResolverSignals`：slots wire 已设 → 禁止 rankingType 触发写 qi wire（已有 partial gate，需与 D1 统一）。
4. `applyPurchaseAnomalyStructuredWireOverrideWhenMetricOrToolsSignal`：slots wire 已设且非 store ranking → 禁止 rankingType override。
5. **删除采购 goods 排行双路径中 rankingType 写 wire 的分支**（R15）：只保留 `applySemanticSlotsGoodsAmountRankingWire` 与 `applyPurchaseStructuredWireFromSemanticSlots`。
6. `applyStockReduceMetricFacetToStructuredWireIfAllowed`：slots wire 非空 → skip（与 D1 gate 一致）。

**不在 D1 做**：broad overview policy 抽象（D2）、消费层 fallback 删除（D3）、Harness 文档（D4）。

---

### D-1X-D2：抽象 broad overview 防 ranking/compare 污染公共 policy

**目标**：避免 business / revenue / stockReduce 各自无限加 guard；补齐 StockReduce 对称能力。

**实施要点**：

1. 抽取 `OverviewBroadPolicy`（或 SlotMerge 内 domain-agnostic 辅助方法）：
   - 当前轮显式 `semanticSlots.structuredIntentDetailWire` 优先
   - 当前轮 SUMMARY/OVERVIEW 不继承 previous ranking/compare
   - 当前轮显式多店 compare 才允许 compare wire（仅读 slots operation + wire + scope，**不读** rankingType）
2. 合并 Business/Revenue 对称实现（R1）为单套 policy + domain 参数（path / overview wire 常量）。
3. 补 StockReduce 第三份（R5）：`clearInheritedStockReduceRankingMetricIfAbsentOnCurrent`（或等价）、slots sanitize、`applyMetricStructuredWire` broad skip（对齐 revenue）。
4. `backfillSlotsFromMetric`：broad 场景禁止从 inherited rankingType 反推 SlotTriple（R3）。
5. `semSignalsExplicit*CompareOrRanking`：改为仅读 slots（R4）。

**约束**：不修改 `CurrentSemanticFrameValidator` / `PurchaseFollowUpSlotSignals` 已通过 1B/1C 的 broad-overview 逻辑语义；只加强 qi 层与 metric 层一致性。

---

### D-1X-D3：消费层 rankingType fallback 降级

**目标**：AnswerPlan / Renderer / Composer **不得**用 rankingType 重新决定核心事实口径。

**实施要点**：

1. ~~`DishSalesAnswerPlanBuilder.resolveDishSalesWire`~~：**D-1X-D3-RANKINGTYPE-FINAL 已完成**。
2. ~~`WarehouseDeterministicRenderer.resolveStockRankingWire`~~：**D-CLEAN-RENDERER-FALLBACK-FINAL 已完成**（`DeterministicRendererSupport.resolveStructuredIntentDetailWireFromQueryIntent`）。
3. ~~`StubAnswerComposerNode.warehouseStockRankingDeterministicTakeoverEligible`~~：**同上**。
4. Merge 层：`applyMetricStructuredWire` 标注 deprecated compat，仅当 slots wire 与 qi wire 皆空且 LLM 显式 rankingType 时执行（或直接移除写 qi，仅保留 parse 字段供 debug）。
5. `PurchaseFollowUpProtocolHydrator`：停止复制 rankingType 到 hydration 帧（可选，降低 inherit 污染）。

**原则**：Composer 只能表达，不能重新算业务语义（R12）。

---

### D-1X-D4：Harness / 文档同步（部分已完成）

**目标**：观测口径与生产一致；planType 映射单源。

**实施要点**：

1. [x] 更新 `docs/ai/d1x-v2-only-time-source-cleanup-inventory.md` §7–§8，标记 D-1X-D 完成项（**D-CLEAN-DOCS-REPLAY-CONTRACT-FINAL**）。
2. [x] Harness 主断言 **`structuredIntentDetailWire` / slots**；`rawStructuredIntentDetail` 来自 slots（**D-1X-D3**）；`AI_HARNESS_REPLAY_CASES.md` **Replay 断言契约**（**D-CLEAN-DOCS**）。
3. [x] 矩阵文档（1A/1B/1C）已注明：rankingType 仅 compat/debug，不得作为 wire 主来源（**D-CLEAN-DOCS**）。
4. `semantic-output-schema.md`：明确 rankingType = compat 字段。
5. `AiHarnessReplayContextProbes`：确保直接调用 `*AnswerPlanBuilder.resolvePlanType`，消除映射双份（R14）。
6. `AiHarnessResolvedContextSummarizer`：timeAction canonical 单源调用 MergeHelper（R10）。

---

## §6 不在 D-1X-D 处理的技术债

以下问题已在梳理中识别，**明确不在 D-1X-D 主 PR 全量收口**，避免 scope 膨胀。

| 技术债 | 说明 | 建议专项 |
|--------|------|----------|
| **purchaseSourceType / sourceFacet 全链路统一** | sourceFacet → qi.purchaseSourceType 单向；禁止 Resolver/Harness 反写 pst（R6） | 采购 Phase 2 source 专项 |
| **followUp / validator / fallback 职责边界** | needClarification、detailWanted、structural followUp 三角判定（R11） | follow-up protocol 专项 |
| **采购 surface text signals 双轨** | `PurchaseSemanticSurfaceSignals` vs `parsePurchaseSupplierTextSignals`（R16）；含中文 contains | 删除 Resolver 话术写 wire；SlotMerge surface 收口 |
| **TimeLexicon 相关内容** | MergeHelper 与 Summarizer 双份 timeAction canonical（R10）；主链已合规 | D-1X-D4 Harness 单源即可 |
| **metric.rankingType 字段最终删除时机** | JSON schema、LLM 输出、parse 字段可长期保留作 debug；删除须等 D1–D3 无写 qi / 无消费 fallback 且 Harness 全绿 | D-1X-D 之后独立「字段 deprecated 移除」PR |
| **normalizePathsLikeKeywordResolver 默认 overview** | 最后 compat（R9） | D3 文档化后可择机删除 |
| **AiFollowUpHintSupport 域切换 regex** | 仅「换成经营|成本|…」，非 rankingType | 保留；与 D-1X-D 无关 |

---

## §7 验收范围

每个 D 子阶段合并前，至少回归以下 Harness case（`overallPass=true`）：

| Case ID | 域 | 说明 |
|---------|-----|------|
| `PURCHASE_AGENT_GRAPH_CORE` | 采购 1A | 采购 Graph 核心 + slots 权威 |
| `BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT` | 经营 1B | broad overview 防 ranking 污染 |
| `STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT` | 出库 1C | slots + wire 解析层断言 |

**必要时补充**（涉及 rankingType 或 follow-up 改动时）：

- 采购 follow-up core bundle（如 `scripts/harness/replay-purchase-followup-core.sh` 覆盖的 case）
- 全量 regression bundle（`scripts/harness/run-local-replay-regression-bundle.sh`）在 D-1X-D4 完成后执行

**验收 grep 检查**（D-1X-D3 完成后）：

- 生产路径无「rankingType → `setStructuredIntentDetail`」except 明确 compat 分支且带 slots-empty guard
- `WarehouseDeterministicRenderer` / `StubAnswerComposerNode`（库房排行）无 rankingType fallback（**D-CLEAN-RENDERER-FALLBACK-FINAL**）
- ~~`DishSalesAnswerPlanBuilder` rankingType fallback~~（**D-1X-D3 已完成**）

**禁止作为验收手段**：

- 为 failing case 新增中文关键词 if
- 恢复 V1 解析路径
- 修改 Tool / SQL / AnswerPlan 业务 SQL / Composer 文案模板 / 前端

---

## 附录 A：Merge 阶段 rankingType 相关 guard 现状（采购/出库/营业额/经营）

| 域 | slots wire gate | explicit operation block | broad overview skip（applyMetricStructuredWire） | metric inherit clear |
|----|---------------|--------------------------|--------------------------------------------------|----------------------|
| 采购 | `hasPurchaseStructuredIntentWireFromSlots` | `purchaseMetricRankingWireBlockedByExplicitOperation` | 无（靠前两列） | 无单独 clear（靠 operation block） |
| 出库 | partial（slot wire + overview vs ranking） | 无 universal | 无 | 无 |
| 营业额 | 无 universal | 无 | **有** `semSignalsRevenueOverviewBroadQuery` | `clearInheritedRevenueRankingMetricIfAbsentOnCurrent` |
| 经营 | 无 universal | 无 | **无**（缺口） | `clearInheritedBusinessRankingMetricIfAbsentOnCurrent` |
| 库存/仓库 | 无 | 无 | 无 | 无 |

---

## 附录 B：文档修订记录

| 日期 | 修订 |
|------|------|
| 2026-05-19 | 初版：D-1X-D 只读梳理；rankingType 读取点 + wire 多写口 + 重复职责 R1–R17 + 分阶段方案 |
