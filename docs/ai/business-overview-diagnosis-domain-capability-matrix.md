# Business Overview / Business Diagnosis / 门店经营对比 — 域能力矩阵（D-9）

> **定位**：收口「经营概览 / 经营诊断 / 多店经营对比」与 **门店优先/风险排序** 问法的产品与工程契约。本文档为 **只读梳理 + D-9 分期建议**；**实现以仓库代码为准**，与 `dish-sales-domain-capability-matrix.md`、`inventory-domain-capability-matrix.md`、`business-diagnosis-production-gate-design.md`、`business-question-routing-d2-design.md` 交叉引用。
>
> **文档版本**：2026-05-15（D-9；**Phase 2A** §11、**Phase 2B** §12；**§14** 单域互斥 GRAPH_RUN / Replay **已通过**，含 **§14.2 Harness 摘要/探针** 收口；**§14.3** 与 **D-10**（`AI_HARNESS_REPLAY_CASES.md` · 多轮探针门卫集）交叉引用；实现以仓库代码为准）。

---

## 1. 范围与非目标

| 主题 | 纳入本文 |
|------|-----------|
| `business_overview_path`（经营概览） | ✅ |
| `business_diagnosis_path`（经营诊断） | ✅ |
| 多店「经营对比 / 营业额对比 / 门店问题最大」 | ✅ |
| 单域专线（纯 `REVENUE_OVERVIEW`、`WAREHOUSE_STOCK_OVERVIEW`、`DISH_SALES_QUERY` 等） | 仅在与概览/诊断**混淆点**处引用；详规见各域矩阵 |
| Composite Gate / Shadow / PRIMARY | **不**展开实现；共识：**Gate 只观测；Shadow 旁路不写 `finalAnswerText`；PRIMARY 未接主链**（见 `AiRunService` 与 `BusinessDiagnosisCompositeExecutionMode` 注释） |

---

## 2.「这个月经营怎么样？」— `business_overview_path` 双轨

### 2.1 Planner 如何选择「四域 Multi-Agent」vs「legacy tools」

**代码锚点**：`BusinessDataPlannerNode` — `resolvedBusinessOverview` 分支内：

- **四域 Multi-Agent 条件**：`resolvedContextOrchestrationMultiAgentOverview(resolvedQueryContext)` 为真，即满足其一：
  - `orchestrationTaskMode` 忽略大小写等于 `MULTI_AGENT`；或
  - `orchestrationMultiAgentRequired == true`。
- **此时 `dataPlanTools`**：`buildBusinessOverviewMultiAgentToolsPermissionFiltered` — 在具备权限的前提下，**按固定顺序**保留子集：
  - `revenue_query` → `purchase_overview` → `stock_reduce_query` → `dish_profit_analysis`
  - 若过滤后为空，**保持空 plan**（不回退 classic；见 [classic-business-overview-removed.md](../legacy-reference/classic-business-overview-removed.md)）。
- **Legacy classic 条件（已删除）**：非 MULTI 的 `business_overview_path` → **`dataPlanTools` 为空** + `businessOverviewClassicPlanSuppressed`。

**执行侧重**：`BusinessToolExecutionNode` 在 Multi 门闸满足时先调 `MasterBusinessAgent.tryOrchestrateBusinessOverviewMultiAgent`；四域工具成功则由 Master **跳过**循环内对同 id 的重复执行。

### 2.2 `finalAnswerText` 常见形态（概览路径）

**代码锚点**：`StubAnswerComposerNode`

| 条件 | 主路径 |
|------|--------|
| `businessOverviewMultiAgentFourDomainDeterministicEligible`：Multi 计划类型为 `BUSINESS_OVERVIEW_MULTI_AGENT_V1`、`missingSections` 为空、四份子域 `AnswerPlan` 均非 null | **确定性 Markdown**：`composeBusinessOverviewMultiAgentFourDomainMarkdown` |
| 有 `businessOverviewAnswerPlan`（MULTI_AGENT）但不满足四域确定性 | Composer multi markdown 路径（或 stub） |
| `businessOverviewResult` 单独挂载（无 AnswerPlan） | **已删除 P1F-F2**（原 `renderBusinessOverviewFallback` / `AiBusinessOverviewResult`） |

语义层：解析示例与 v2 规则中「**仅改时间的承接追问**」仍须保持 **`BUSINESS_OVERVIEW` + Multi 编排」** 与 Planner 门闸一致（见 `query_semantic_parser.v2.md`「四域经营综合汇总类」）。

---

## 3.「哪个门店经营最好？」— 三分支（事实边界 + 漂移点）

> **产品目标**：**营业额排行**、**综合经营多域对比**、**需要归因/解释的诊断对比** 必须可被**结构化区分**；否则易落在错误 path 或错误 wire。

### 3.1 分支 A — 营业额（及同类「钱多钱少」）排行

| 项 | 约定 |
|----|------|
| **典型 intent / path** | `REVENUE_OVERVIEW` / `revenue_overview_path`（以解析与 merge 后 `effective*` 为准） |
| **Wire** | `revenue_store_amount_ranking`（`AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING`） |
| **语义规则（v2）** | 用户显式比较 **营业额、销售额、营收、订单、客单价** 等；多店点名将对比落成 **`revenue_store_amount_ranking`**；**≠** 综合经营 wire |
| **与 C 类区别** | 「哪家营业额高」→ **A**；「哪家**经营/生意**好（综合评价）」→ **B 或 C**（见下） |

### 3.2 分支 B — 综合经营对比（多域表面，概览）

| 项 | 约定 |
|----|------|
| **典型 intent / path** | `BUSINESS_OVERVIEW` / `business_overview_path` |
| **Wire** | `business_store_status_compare`（`STRUCTURED_BUSINESS_STORE_STATUS_COMPARE`） |
| **Merge 辅助** | `AiQuerySemanticLlmMergeHelper.applyBusinessStoreStatusCompareWhenMultiStoreMentioned`：**path 已是 `business_overview_path` 且有效点名 ≥2 店** → 将 `structuredIntentDetail` 设为 `business_store_status_compare`（不依赖用户原文正则猜意图） |
| **编排** | 须满足 **§2.1** Multi 门闸时走四域工具；否则 legacy 工具链 |
| **与 A 区别（v2 明文）** | 综合经营类话术用 **`business_status` / `operation_status` 等**；**禁止**用 `revenue` / `sales` / `turnover` 表达「 holistic 经营」 |

### 3.3 分支 C — 需要解释/归因的诊断对比

| 项 | 约定 |
|----|------|
| **典型 intent / path** | `BUSINESS_DIAGNOSIS` / `business_diagnosis_path` |
| **Wire** | `business_store_status_compare_diagnosis`（`STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS`） |
| **Merge 升格** | `remapBusinessOverviewCompareToBusinessDiagnosisWhenMetricSignals`：在已是 canonical `business_store_status_compare` 的前提下，若 **`metric.primaryMetric` 命中「可比 + 归因/因果」类信号** → 升格为诊断 intent/path + 诊断 wire |
| **产品备注** | `business-question-routing-d2-design.md` 强调 **「排行 vs 综合对比」二选一** 与 Gate 白名单；工程上避免同一问句混用 `revenue_store_amount_ranking` 与 `business_store_status_compare` |
| **Phase 2A 落地** | **R5 类**多店归因对比已走本分支 + `DiagnosisPlan.storeCompareEvidence` + `DiagnosisDeterministicRenderer` 门店对比确定性答复；细节见 **§11**。 |

### 3.4 已知漂移点（评审用，非本次改稿范围）

1. **模型填错 `primaryMetric`**：A 与 B/C 边界完全依赖语义 JSON；`revenue` 与 `business_status` 混用会导致 path/wire 整条链偏移。
2. **「哪个门店」未点 ≥2 店名**：`applyBusinessStoreStatusCompareWhenMultiStoreMentioned` **不**触发；可能停在泛问或其它 scope，需追问或默认集团 visible 范围产品策略。
3. **v1 / v2 解析并存**：以**线上实际启用版本**为准；运维上需单一事实源，否则文档与验收易分叉。

---

## 4.「哪个门店问题最大？」— `store_priority_ranking` 与 `business_diagnosis_path`

### 4.1 Wire 与 canonical

| 输入别名（示例） | Canonical |
|------------------|-----------|
| `store_risk_ranking` 等（以 Lexicon `canonicalStructuredIntentDetailWire` 为准） | **`store_priority_ranking`**（`STRUCTURED_STORE_PRIORITY_RANKING`） |

### 4.2 进入诊断 path 的方式

1. **解析层**产出 `BUSINESS_DIAGNOSIS` + `business_diagnosis_path` + structured 为 priority/risk 归一后的 **`store_priority_ranking`**。
2. **Planner**：`applyBusinessDiagnosisBranch` 设置 `businessDiagnosisPath` 与 tools；**`syncResolvedQueryContextToBusinessDiagnosis`** 将有效 intent/path 与 Harness 可见上下文对齐为诊断；若 structured **为空**会默认补 `business_diagnosis_summary`（**有** 明确 priority wire 时以解析为准）。
3. **计划**：`StubOutcomeReviewNode` → **`DiagnosisPlanBuilder.attachIfApplicable`**；`store_priority_ranking` 时由 **`BusinessDiagnosisAgentV1.enrich`** 写入 `DiagnosisPlan.debug`（如 `diagnosisQuestionType=STORE_PRIORITY_RANKING`）。**Historical removed**：`BusinessDiagnosisPlanNode` / `BusinessDiagnosisPlanBuilder`（见 [business-diagnosis-plan-removed.md](../legacy-reference/business-diagnosis-plan-removed.md)）。

### 4.3 答复链（现网）

- **`DiagnosisDeterministicRenderer.isBusinessDiagnosisStorePriorityTurn`** 为真时：走门店优先专用确定性编排（`DiagnosisPlan` + `BusinessDiagnosisAgentV1` debug 字段），**不**宣读通用 `DiagnosisPlan` 全文模板。
- **可选润色**：`COMPOSER_DIAGNOSIS_STORE_PRIORITY_V1`（LLM）；确定性层为 **`DiagnosisDeterministicRenderer`**，**非**已删 `BusinessDiagnosisPlan` fallback。
- **【意图说明】（仅 store_priority_ranking）**：`StubAnswerComposerNode` 在组装 `intentP` 时，若 Planner **`costIntentConvergenceNote`** 含短语「按集团权限范围内门店合并做经营诊断」，则替换为 **「按集团权限范围内各门店做综合风险优先排序」**，**保留**尾随「统计时间」「含采购、出库/核销、营业额」等与权限说明；**不参与**日常集团经营诊断综述（其它 `business_diagnosis` 分支仍直接使用 Planner 原句）。

### 4.4 Phase 2B — 产品与 Harness 收口（摘要）

- **能力语义**：对用户侧宜描述为 **「集团权限范围内各门店综合风险优先排序」**；**文案避免** 「门店合并诊断」，以免与 Composite / 其它多域聚合叙事混淆（本能力是 **`business_diagnosis_path` + `store_priority_ranking` + `DiagnosisPlan`**，见 `BusinessDiagnosisAgentV1` / `DiagnosisDeterministicRenderer`）。
- **评分边界**：Phase 2B 为 **简版** 加权/信号组合（见 **§12.3**），**不等同** 完整风险评分模型。
- **验收与字段**：已通过 GRAPH_RUN / Harness Replay 验收；摘录字段与健康检查项见 **§12**。

---

## 5. `business_diagnosis_path` — 当前规划的 Tools（代码事实）

**Planner 列表**（`applyBusinessDiagnosisBranch`，权限裁剪后）：

| Tool id | 典型条件（摘录） |
|---------|------------------|
| `purchase_overview` | `VIEW_PURCHASE` 或（库房类角色 + `VIEW_STOCK`）等 |
| `stock_reduce_query` | `VIEW_STOCK` |
| `dish_profit_analysis` | 非采购收敛/非纯库房角色；`VIEW_DISH_SALES` + `VIEW_COST` |
| **`revenue_query`** | **`VIEW_REVENUE` 时追加**（与 `DEFAULT_BUSINESS_DIAGNOSIS_TOOLS` 注释「三工具」相比，**运行时会多挂营收**） |

**执行**：可与 `business_overview_path` 共用 **`MasterBusinessAgent` 四域编排**（门闸满足时），顺序与跳过逻辑见 `BusinessToolExecutionNode`。

---

## 6. 当前能力缺口（D-9 立项必须写清）

| 缺口 | 说明 |
|------|------|
| **库存现量 / 积压 / 门店库存排行** | **`warehouse_stock_overview`** **未**纳入 `business_diagnosis_path` 默认 `dataPlanTools`。门店「库存压力」若语义落在 **`WAREHOUSE_STOCK_OVERVIEW` + `store_stock_amount_ranking`** 等，走库存专线，而**不在**诊断四域内。 |
| **菜品销量/销售额 AnswerPlan** | **`DishSalesAnswerPlan`** 由 **`DISH_SALES_QUERY` / `dish_sales_query_path`** 挂载；**`DiagnosisPlanBuilder` / 诊断默认 Planner 不消费**该计划。诊断链菜品侧仅 **`DishProfitAnswerPlan` + dish tool**。 |

**影响**：多域「经营健康」叙事在诊断态下**缺少**库存与「纯销量/销售额排行」结构化输入；收口前需在文档与产品上接受「诚实缺域」或排期扩展。

---

## 7. `business_overview_path` — Multi vs Legacy（与 §2 对照表）

| 模式 | `dataPlanTools` 要点 |
|------|----------------------|
| **Multi-Agent** | `revenue_query`, `purchase_overview`, `stock_reduce_query`, `dish_profit_analysis`（权限子集） |
| **Legacy** | 另含 `business_overview_query`, `dish_sales_query`, `purchase_query`, `gross_margin_calculator`（**Historical**：classic 链已删；**`dish_sales_query` / `purchase_query` Tool 已删（P2）**） |
| **成本链（`cost_diagnosis_path`）** | `revenue_query`, **`purchase_overview`**, `stock_reduce_query`, `dish_profit_analysis` + **`CostDiagnosisAgent`**（毛利由 **`CostMarginDerivation` 内部推导**；**Historical removed**：`gross_margin_calculator` Tool） |

**仍偏 legacy 的场景**：`business_overview_path` **且** Multi 门闸**未**满足；或岗位收敛（如门店采购/库房）将「经营怎么样」收窄为**单视角**工具链（非完整四域）。

---

## 8. Master / Gate / Shadow / PRIMARY（一句话）

| 组件 | 对 D-9 终稿的影响 |
|------|-------------------|
| **Master** | **四域**（营收/采购/出库/菜品毛利）在 overview/diagnosis 共面 Multi 门闸下的串联编排；另有单域 Master 入口（本文不展开）。 |
| **Composite Gate** | **只写入观测**；**不**改 `finalAnswerText`。 |
| **Shadow** | 图完成后旁路 Composite；**不**写 `finalAnswerText`。 |
| **PRIMARY** | **未接主链**。 |

---

## 9. 计划对象关系（诊断深读 · 现网）

| 对象 | 职责 |
|------|------|
| **`DiagnosisPlan`** | **现网主计划**：`DiagnosisPlanBuilder`（`StubOutcomeReviewNode` 内）**只读聚合** 四域 `*AnswerPlan`；`business_diagnosis_path` 上 **`BusinessDiagnosisAgentV1.enrich`**。**Phase 2A**：wire 为 **`business_store_status_compare_diagnosis`** 时从 **`toolResults`** 组装 **`storeCompareEvidence`**（§11） |
| **`BusinessDiagnosisAgentV1`** | **现网 enrich**（非 Graph 节点）：规则型 findings、门店优先/风险追问 debug；**勿**与 Composite 主链混淆 |
| **`BusinessDiagnosisCompositeAnswerPlan`** | **非现网用户正文**：SHADOW / HARNESS_ONLY 旁路（§8）；**PRIMARY 未接** |
| **`BusinessDiagnosisPlan`** | **Historical removed**（P2）：见 [business-diagnosis-plan-removed.md](../legacy-reference/business-diagnosis-plan-removed.md) |
| **缺口** | **无** Warehouse 工具 / 库存 AnswerPlan 进入诊断默认 tools；**无** `DishSalesAnswerPlan` 进入四方聚合 |

---

## 10. D-9 分期收口建议

### Phase 1 — 语义边界与文档（**先**）

- 冻结三类用户话术的**意图对照表**：「经营怎么样」、「门店经营最好」、「门店问题最大」— 与 **§2–§4** 及 **`query_semantic_parser.v2.md` / `business-question-routing-d2-design.md`** 对齐。
- 明确 **库存门店压力**（`store_stock_amount_ranking`）与 **经营诊断门店优先**（`store_priority_ranking`）的 **互斥与承接**话术，避免一线混线为「经营问题」。
- **单一解析事实源**：确认线上 v1/v2 与 **`AiQuerySemanticLlmMergeHelper`** 的发布组合，避免文档与验收分叉。

### Phase 2 — Planner 是否扩展（**产品决策后**）

- **可选 A**：`business_diagnosis_path`（或特定 wire）**追加** `warehouse_stock_overview` — 需权限、时序、与库存 AnswerPlan 消费方一致。
- **可选 B**：诊断或四域概览 **挂载 / 引用** `DishSalesAnswerPlan` — 需明确与 `dish_profit_analysis` 并行或条件触发，避免重复拉数。
- **可选 C**：维持现状 — 在 **`DiagnosisPlan`** 中强化 **缺失域声明**（诚实降级）。

### Phase 3 — AnswerPlan / Composer（**后**）

- 将「缺库存/缺销量域」**结构化**写入计划 `missingSections` / `warnings` / 专用降级字段，**收窄** LLM 自由发挥。
- 门店优先等路径逐步 **加厚确定性宣读**、**减薄** `COMPOSER_DIAGNOSIS_*` 仅作润色（与项目原则「LLM 不算数、不排序」一致）。

---

## 11. D-9 Phase 2A — 门店经营对比诊断（已落地）

本节描述 **§3.3 分支 C**（`business_store_status_compare_diagnosis`）在工程上的 **Phase 2A** 收口结果；**实现以仓库代码为准**（`DiagnosisPlanBuilder`、`DiagnosisDeterministicRenderer`、`StubAnswerComposerNode` 等）。

### 11.1 R5 示例问法与路由

**样例 R5**：「AAA 和汀兰餐厅哪个经营更好，主要原因是什么？」当前主链已走：

| 维度 | 取值 |
|------|------|
| Intent | `BUSINESS_DIAGNOSIS` |
| Path | `business_diagnosis_path` |
| Structured（canonical wire） | `business_store_status_compare_diagnosis`（`STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS`） |

### 11.2 `dataPlanTools`（四域）

Planner 在诊断分支下已为该路径准备 **四域工具**（权限裁剪后仍可能为子集），与 §5 一致，工程上包含：

- `purchase_overview`
- `stock_reduce_query`
- `dish_profit_analysis`
- `revenue_query`

### 11.3 `DiagnosisPlan.storeCompareEvidence`

当且仅当 canonical 为 **`business_store_status_compare_diagnosis`** 时，`DiagnosisPlanBuilder` 尝试从 **`AiRunState.toolResults`** 组装 **`storeCompareEvidence`**（`List<Map<String, Object>>`），**不**改 Tool / SQL / Prompt / 不重算指标。典型行字段与含义：

| 字段 | 说明 |
|------|------|
| `revenueAmount` | 来自 `revenue_query` 信封 `data.storeRevenueRanking` |
| `purchaseAmount` | 来自 `purchase_overview` 信封 `data.purchaseOverview`（`coveredStores` / `dataMissingStores` / `visibleStores` 等门店行） |
| `stockReduceAmount` | 来自 `stock_reduce_query` 信封 `data.topStoresOutboundByGrandTotal`（**若本轮无门店级表或缺行，须诚实降级为缺失**） |
| `dishProfitCoverage` | 仅 **`AGGREGATE_ONLY`** 或 **`NA`**：工具无门店级毛利时 **不伪造** 门店毛利 |
| `dataCoverage` | `revenueAvailable` / `purchaseAvailable` / `stockReduceAvailable` / `dishProfitStoreLevelAvailable=false` / `missingReasons` |
| `mainReasons` | 与该门店行相关的可读原因要点 |

### 11.4 `finalAnswerText`（确定性门店对比）

- **`DiagnosisDeterministicRenderer`**：在 **`storeCompareEvidence` 非空** 且意图 canonical 命中 **`business_store_status_compare_diagnosis`** 时，输出 **「门店经营对比」** 确定性正文（按门店列出营业额、采购、占比、出库与菜品边界说明，并以 **【谨慎结论】** 收束）。
- **不再**在同场景沿用旧版 Harness **「经营诊断·证据型」** 大段聚合话术作为对用户可见的主答案主体。
- **`StubAnswerComposerNode`**：该分支下【意图说明】使用 **门店对比专用前缀**（按可见门店范围 + 点名），**替换** Planner 侧常见的「按集团权限范围内门店合并做经营诊断」类 **`costIntentConvergenceNote` 展示**（权限前缀、查询范围、正文证据仍保留）。

### 11.5 当前产品 / 数据边界

1. **出库**：若 `stock_reduce_query` **未**返回 **`topStoresOutboundByGrandTotal`**（或本轮无可靠门店级行），则 **不做** 门店级出库对比；`dataCoverage` 与正文须如实反映。
2. **菜品毛利**：工具链 **无** 门店级拆分时，**不伪造** 门店毛利；`dishProfitCoverage` 与结论话术仅限 **`AGGREGATE_ONLY` / `NA`** 语义。
3. **结论**：必须使用 **「谨慎结论」** 表述习惯，**禁止** 仅依据营业额等指标给出「谁经营更好」的简化定论。

### 11.6 GRAPH_RUN 验收摘要（示例）

以下为一次 GRAPH_RUN / Harness 对齐用的 **摘录数值**（便于回放与人工核对）；**非**固定死数据。

| 门店 | 营业额（约） | 采购（约） | 采购占营业额（约） |
|------|--------------|------------|---------------------|
| AAA | 4644 元 | 2976 元 | 64.1% |
| 汀兰餐厅 | 1187 元 | 327 元 | 27.5% |

**结论（与确定性渲染策略一致）**：

- 从营业额看，**AAA 更高**；从采购占营业额比例看，**汀兰采购压力相对更小**。
- **出库**与**菜品毛利**因当前数据粒度不足以支撑门店级对比，**不参与**「哪家经营更好」的完整判断；答复须保持上述边界，避免越权断言。

---

## 12. D-9 Phase 2B — 集团权限范围内各门店综合风险优先排序（已落地 · Replay 已验收）

本节描述 **`store_priority_ranking`**（§4）在 **Phase 2B** 的 **Harness / GRAPH_RUN Replay** 收口结果；现网排序与 debug 在 **`DiagnosisPlan`** + **`BusinessDiagnosisAgentV1`**（`DiagnosisDeterministicRenderer` 宣读）。Harness **推荐**扁平键 **`diagnosisPlan*`**；**deprecated compat**（`businessDiagnosisPlan*`、`harnessReplayBusinessDiagnosisPlan*`、`storePriorityRanking*`）由 Summarizer 镜像，**非**已删 `BusinessDiagnosisPlan` DTO — 见 [business-diagnosis-plan-removed.md](../legacy-reference/business-diagnosis-plan-removed.md) · Harness 键名表。

### 12.1 产品表述（与 §4.4 一致）

| 做法 | 说明 |
|------|------|
| **推荐** | 对外/对内文档使用 **「集团权限范围内各门店综合风险优先排序」**（或等价自然语言）。 |
| **避免** | **「门店合并诊断」**：易与 Composite 或多域合并表述混淆；本能力本质是 **诊断 path 下的门店排序 wire + 计划块**，而非单独命名为「合并诊断」的产品形态。 |

### 12.2 已验收用户问法（均归 **`store_priority_ranking`**）

以下内容已在 Replay 中用 **同一路由与计划形态**验收通过（canonical **`structuredIntentDetailWire=store_priority_ranking`**）：

1. 「哪个门店问题最大？」
2. 「哪个门店风险最高？」
3. 「全部门店哪个最需要关注？」
4. 「老板今天先处理哪个门店？」

### 12.3 当前能力与模型边界（简版 Phase 2B）

- **现状**：门店优先级排序为 Phase 2B **简版**，主要综合考虑 **采购金额**、**低毛利菜品风险类信号**、**数据完整度** 等因素（以实现代码为准）。
- **非目标**：上述组合 **不等同于** 完整风险评分模型或财务/合规级「风险评级」产品；迭代若引入新因子须在矩阵与 Harness 契约中另行声明。

### 12.4 GRAPH_RUN / Harness Replay 摘录字段（验收样例）

以下为一次通过的 **对齐摘录**（具体数值与环境相关；表中 **Top1** 指 `storePriorityRankingTop1*` 语义；样例验收时 **Top1 门店名为 AAA**，**`storePriorityRankingRowsLen=2`**）。

| 键 | 样例取值 |
|----|-----------|
| `effectiveIntentCode` | `BUSINESS_DIAGNOSIS` |
| `effectivePathCode` | `business_diagnosis_path` |
| `structuredIntentDetailWire` | `store_priority_ranking` |
| `diagnosisPlanType` | `OVERALL_BUSINESS_DIAGNOSIS`（推荐） |
| `businessDiagnosisPlanType` | `BUSINESS_DIAGNOSIS`（**deprecated compat**：path 语义，非 `planType`） |
| `storePriorityRankingPlanType` | `STORE_PRIORITY_RANKING`（**deprecated compat**，同 `DiagnosisPlan.debug`） |
| `storePriorityRankingRowsLen` | `2` |
| `storePriorityRankingTop1StoreName` | `AAA` |

（Explorer 回放每轮 **`probe`** 中另含 **`harnessReplayBusinessDiagnosisPlanType`**、**`harnessReplayStorePriorityRanking*`**，与全量 **`resolvedQueryContextSummary`** 同源、键名前缀不同。）

---

## 13. 相关文档索引

| 文档 | 用途 |
|------|------|
| `docs/ai/business-question-routing-d2-design.md` | 门店「最好」双轨、Composite 路由产品表 |
| `docs/ai/business-diagnosis-production-gate-design.md` | path/wire 与 Gate 允许行 |
| `docs/ai/dish-sales-domain-capability-matrix.md` | DishSales 与诊断边界 |
| `docs/ai/inventory-domain-capability-matrix.md` | 库存排行与经营对比 **互斥** |
| `docs/HARNESS_ORCHESTRATION_DECISION.md` | Multi-Agent / Replay 决策 |
| `docs/TODO_MULTI_AGENT.md` | 集团经营概览、历史约定 |

---

## 14. D-9 单域互斥回归与 Harness 探针收口

以下 **GRAPH_RUN + Harness Replay（或等价离线回放 JSON）六轮**，用于确认 **单域排行/库存问法不误落 `business_diagnosis_path`**，`store_priority_ranking` / `business_store_status_compare_diagnosis` 仍可正确落地；本项目 **已通过复测**（以你方最新回放工件为准）。

### 14.1 六轮流向（effective* + canonical wire）

| 序号 | 用户问法示例 | `effectiveIntentCode` | canonical `structuredIntentDetailWire` |
|:---:|:-------------|----------------------:|---------------------------------------|
| 1 | 「哪个门店营业额最高」等 | `REVENUE_OVERVIEW`（`revenue_overview_path`） | **`revenue_store_amount_ranking`** |
| 2 | 「哪个门店采购金额最高」等 | `PURCHASE_OVERVIEW`（`purchase_cost_insight_path`） | **`purchase_store_amount_ranking`** |
| 3 | 「哪个门店出库金额最高」等 | `STOCK_REDUCE_QUERY`（`stock_reduce_query_path`） | **`store_outbound_amount_ranking`** |
| 4 | 「哪个门店库存金额最高」等 | `WAREHOUSE_STOCK_OVERVIEW`（`warehouse_stock_overview_path`） | **`store_stock_amount_ranking`** |
| 5 | 「哪个门店问题最大」等（Phase 2B） | `BUSINESS_DIAGNOSIS`（`business_diagnosis_path`） | **`store_priority_ranking`** |
| 6 | 「AAA 和汀兰餐厅哪个经营更好…」 | `BUSINESS_DIAGNOSIS`（`business_diagnosis_path`） | **`business_store_status_compare_diagnosis`** |

### 14.2 `AiHarnessResolvedContextSummarizer` / Replay `probe` 修复记录（与用户终稿对齐）

为避免 **探针半截话**并与门店对比 Harness 对齐，摘要与探索型 Replay 探针曾有如下收口（**不涉及** Planner / Builder / SQL）：

| 能力 | 行为 |
|:-----|:-----|
| **`finalAnswerText`** | 写入 **Composer 终稿全文**（`AiRunState#getFinalAnswerText()` 对齐），供 Replay / 调试读完整作答。 |
| **`answerPreview`** | **仍为前 500 字**短文预览（历史 SSE/面板「简略预览」约定）。 |
| **门店对比证据（`business_store_status_compare_diagnosis`）** | 摊平：**`businessStoreCompareEvidenceRowsLen`**；探针：**`harnessReplayStoreCompareEvidenceRowsLen`**；可选 Top 排序（与确定性渲染营业额序一致）：**`businessStoreCompareTop1StoreName`**、**`businessStoreCompareTop2StoreName`**。 |

### 14.3 D-10 多轮 GRAPH_RUN · 与本域探针对齐（摘录）

在长会话末尾轮次若仍为 **门店经营对比归因**（**`business_store_status_compare_diagnosis`**），除 §14.1 **第 6 行** 外，宜在回放 JSON 中核对 **`businessDiagnosisPath`**、**`dataPlanTools`**（四域工具齐全）、**`diagnosisPlanExists`**、**`businessDiagnosisPlanExists`**、**`businessStoreCompareEvidenceRowsLen`**、**`finalAnswerTextBlank`**、**`needSemanticClarification`**/**`needClarification`**、**`permissionDenials`**（详见 **`docs/AI_HARNESS_REPLAY_CASES.md`** · **「D-10」**）。

---

**变更记录**

| 日期 | 说明 |
|------|------|
| 2026-05-15 | 初版：D-9 评审用能力矩阵与 Phase 1/2/3 建议；仅文档，无代码变更。 |
| 2026-05-15 | **§11**：补充 D-9 Phase 2A（`storeCompareEvidence`、四域工具、确定性门店对比 `finalAnswer`、边界与 GRAPH_RUN 验收摘要）；§3.3 / §9 交叉更新；相关文档索引后随 Phase 2B **§13** 顺延。 |
| 2026-05-15 | **§12**：D-9 Phase 2B（`store_priority_ranking`）、四问法与 GRAPH_RUN **`storePriorityRanking*`** Replay 摘录、简版评分边界及「门店合并诊断」用语回避；§4 **§4.4**、§9 **BusinessDiagnosisPlan** 交叉更新；相关文档索引顺延为 §13。 |
| 2026-05-15 | **§4.3**：`store_priority_ranking` 【意图说明】Composer 话术替换（非 Planner）；新增 **§14** 六轮「单域互斥」回归已与 **`resolvedQueryContextSummary` / Replay `probe`**（`finalAnswerText` 全文、`answerPreview` 500 字、`storeCompareEvidence` 扁平键）记录。代码：`StubAnswerComposerNode`、`AiHarnessResolvedContextSummarizer`、`AiHarnessReplayProbeView`。 |
| 2026-05-15 | **§14.3**：D-10 收口 — 长会话 GRAPH_RUN **全局 RunState 探针**与本域门店对比摘录字段交叉引用 **`AI_HARNESS_REPLAY_CASES.md`**。 |
