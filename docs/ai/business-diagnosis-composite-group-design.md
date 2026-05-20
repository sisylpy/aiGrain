# Composite 经营诊断 — **GROUP 多门店（C-43 规格 + C-48 Harness + C-49 文档收口 + C-44～C-47 单域切片）**

> **读者**：Planner / Harness / Tool / Resolver 工程师。  
> **阶段**：**C-43** — **GROUP 规格与设计前置**（§1～§8）；**C-48** — **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** Harness（六步、四域 real Tool、诊断确定性、建议 mock）；**C-49** — **curl 验收快照与已知限制**（§10）、**`BusinessDiagnosisCompositeAnswerPlanBuilder#BUILDER_VERSION=C-49`**（mappingNotes 相位键不变）。**C-44～C-47** — 各域独立 GROUP Hydrated 切片。**不**写 SQL、**不**接 Master / 前台调度 LLM、**不**改 Resolver / Composer 主逻辑、**不**在 Composite 层新增用户原文 **contains/regex**。  
> **STORE 单店 Composite**（C-30～C-42）权威：**[`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md)**。  
> **Composite AnswerPlan 通用字段**：**[`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md)**；本文仅 **GROUP 增量口径** 与 **C-44 实装门禁**。

---

## 1. caseId 与目标链路

| 项 | 规格 |
|----|------|
| **caseId** | **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** |
| **计划形态** | 与 STORE **相同六步**：`step_revenue_hydrated` … `step_recommendation`（**`finalAnswerPlanType=BUSINESS_DIAGNOSIS_COMPOSITE`** 不变） |
| **四数据域** | **C-48 Harness**：四步均为 **real**（`revenue_query`、`purchase_overview`、`stock_reduce_query`、`dish_profit_analysis`），上下文与 **C-44～C-47** 单域 GROUP GraphCase **同构**；环境失败时 **诚实 DEGRADED**（见 §8）。**§6** 仍保留 **设计门禁** 与各域历史说明。 |
| **诊断** | **确定性 compose**（与 STORE 同哲学；**不**调 LLM） |
| **建议** | **mock**（`mock_build_recommendation_plan` 等价诚实口径） |

**与 STORE 的差异（须显式）**：

| 维度 | STORE（C-35） | GROUP（C-43 设计） |
|------|---------------|---------------------|
| **`orgScope.scopeType`** | **`STORE`**（或等价单店枚举） | **`GROUP`** |
| **`visibleStores`** | 常等价单店（如 AAA `id=1`） | **至少** **`[{id:1, name:"AAA"}, {id:3, name:"汀兰餐厅"}]`**（与人类可读 label 一并写入设计期望） |
| **`scopeLabel`** | 单店名 + id | **「当前可见门店」** 或 **「全部门店」**（与 Resolver / 权限真实语义对齐；**不**在本文臆造权限模型） |
| **`departmentId`（RunState）** | 常作单店查询锚点 | **不得**在无证明的情况下 **等同于「集团查询门店 id」**；**仅**当生产 **Resolver / Tool** 契约明确要求时写入 **`currentStoreDepartmentId`** 等字段 |
| **`summaryText`** | 可出现单店主语 | **禁止**默认 **「AAA 在……」** 单店主语；须 **多店/集团安全措辞**（见 §7） |

---

## 2. `AiRunState`（GROUP 最小字段集合 — 设计）

以下为 **Harness / PlannerExecutor 回放与 Builder 物化** 所需 **最小概念字段**；**不**要求在 trace 或 API 响应中 **完整序列化** `AiRunState` / `AiResolvedQueryContext` **大对象**。

**与 STORE 共有（仍必填概念）**：

| 字段 | GROUP 备注 |
|------|------------|
| **`runId`** | 与现有 Run 一致 |
| **`conversationId`** | 与现有多轮一致 |
| **`userId`** | 与现有鉴权一致 |
| **`departmentId`** | **保留字段**；语义以 **生产 Resolver** 为准 — **GROUP 下不假设**其等于「被聚合的唯一门店」 |
| **`distributerId`** | 与现有租户/分销商边界一致 |
| **`resolvedQueryContext`** | 见 §3；**`scopeType=GROUP`** |
| **`toolResults`** | 四域生产 Tool id → 信封（与 STORE 同键名）；**GROUP 下 payload 形状以各 Tool 实测为准** |

**路径旗标（不臆造）**：

- 各域现有 **`AiRunState`** / **`AiResolvedQueryContext`** 上 **已存在** 的 path 字段（如营收侧 **`revenueOverviewPath`**、**`businessOverviewPath`**、**`businessDiagnosisPath`**、**`ARG_GROUP_WIDE_OVERVIEW_HINT`** 等）**仅按各 Tool / Executor 真实语义置位**。  
- **C-43 设计原则**：若某域 **STORE Hydrated** 路径要求 **path 全 false** 以走单店分支，则 **GROUP Composite** 必须 **显式走** 文档化的 **group-wide** 分支（若存在）；**不得**静默把 GROUP 上下文 **伪造** 成单店 **`departmentId`** 调用同一 Tool 却宣称「集团汇总」。

---

## 3. `AiResolvedQueryContext`（GROUP 最小字段 — 设计）

| 字段区 | 最小规格 |
|--------|-----------|
| **`timeWindow`** | 与 STORE 相同：**单一** `AiResolvedTimeWindow`（`startDate` / `endDate` / `timeLabel`）；四域 **同一窗口** |
| **`orgScope.scopeType`** | **`GROUP`** |
| **`orgScope.visibleStores`** | **至少** 含 **`id=1`（AAA）**、**`id=3`（汀兰餐厅）**；每项含稳定 **`storeDepartmentId`**（或项目中等价主键）与 **展示名** |
| **`orgScope.currentStoreDepartmentId`** | **不强行单店化**。若生产 Resolver **必须**填锚点部门 id，则文档化：**该字段表示「会话锚点 / 默认部门」而非「唯一 SQL 过滤门店」**；**真实 SQL 门店集合** 以 **`dataScope`** / **`visibleStores`** / Tool 参数为准 |
| **`dataScope`（GROUP 下 SQL 范围表达）** | **设计约束**：须能表达 **多门店 IN 列表**（或等价 **expandedSqlDepartmentIds** / **`queryStoreIds`** 等 — **字段名以现网 `AiResolvedDataScope` / 各 Tool Request 为准**）。**禁止**：仅填单店 id 却对外宣称 GROUP 汇总成功 |
| **`queryIntent`** | 与 STORE Composite **业务一致**：经营诊断 / 四域概览；**不**新增用户原文 **contains/regex** 路由 |

---

## 4. 四域 ReadRequest / ExecutionContext — GROUP 口径（设计）

### 4.1 Revenue

- **应有能力**：时间窗内 **多门店营业额汇总**；若 Tool / AnswerPlan 提供 **门店排行**，则可映射到 **`revenueSummary.storeRows`**（与现有 **`storeRevenueRanking`** 概念对齐）。  
- **待实测**：在 **`scopeType=GROUP`** + 正确 **`dataScope`** + **group-wide path** 下，**`revenue_query`** 是否返回 **可信多店合计** 与 **逐店行**（见 **§6**）。

### 4.2 Purchase

- **应有能力**：**多门店采购金额 / 笔数聚合**；**`focusRows`**（供应商 / 品类 Top-N）在 GROUP 下 **可能** 为 **跨店合并** 或 **需按店拆分** — **以 `PurchaseAnswerPlan` 实测形状为准**。  
- **C-43 声明**：本阶段 **只保证设计位**；**不保证** C-44 首版即可 **无改 Tool** 跑通 GROUP 采购汇总。

### 4.3 StockReduce

- **应有能力**：**多门店出库/核销金额汇总** + **门店维度**（映射 **`stockReduceSummary`** 下 **`focusRows` / `topStores`** 等 — **DTO 名以实现阶段对齐** **`StockReduceAnswerPlan`**）。  
- **待实测**：**`stock_reduce_query`** 在 GROUP **`dataScope`** 下是否返回 **门店拆分** 与 **集团级 `grandTotalAmount`** 一致且不重复累计。

### 4.4 DishProfit

- **现网文档提示**：单域菜品 Adapter 设计曾明确 **「GROUP 多门店暂不纳入 v1」**（见 **`planner-executor-v1-design.md`** 引 **`dish-profit-planner-adapter-design.md`**）。  
- **C-43 强制 honesty**：**是否在 GROUP 下已有真实 **`dish_profit_analysis` 支持 — 待实测**；**禁止**在规格书中 **假设** 四域 GROUP 全自动成功。若实测不支持：须走 **§8 降级**，**不得** fallback 单店成功后假装集团成功。

---

## 5. AnswerPlan — GROUP 字段增量（`BusinessDiagnosisCompositeAnswerPlan`）

在 **[`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md)** 根 DTO 上，GROUP **至少** 约束下列 **语义**（字段名可与 STORE 复用，**含义**扩展为多店）：

| 字段 | GROUP 语义 |
|------|------------|
| **`scopeLabel`** | **多店/集团可读范围**（例：**「当前可见门店（含 AAA、汀兰餐厅）」** / **「全部门店」** — 与实际 **`visibleStores`** 一致） |
| **`timeLabel`** | 与 **`AiResolvedTimeWindow`** 一致 |
| **`revenueSummary.storeRows`** | **若** Tool 提供多店排行/明细则 **必填为非空列表**；**若**仅有集团总数无数建行 → **不编造**逐店排行，`storeRows` **空或 null** 与 **`mappingNotes`** 说明 |
| **`purchaseSummary.focusRows`** | GROUP 下 **聚合维** Top-N；**缺明细** 时不编造 |
| **`stockReduceSummary`** | 除 **标量汇总** 外，**宜**含 **`focusRows` 或 `topStores`（概念）** — **门店维出库对比**；**无门店维数据** 则不填 **假 0** |
| **`dishProfitSummary.focusRows`** | 同上；**GROUP 不支持** 时 **整域 `null`** |
| **`dataCoverage`** | **四域各一条**；**任域 GROUP 不支持或缺数据 → `success=false`** |
| **`diagnosisSignals`** | 与 STORE 规则相容；**数据不全 → `dataIncompleteSignal` 非空** |
| **`summaryText`** | **§7** 安全措辞 |

---

## 6. 各 Tool 在 GROUP 下的支持情况 — **C-44 / C-45 / C-46 门禁表**

| 域 | Tool id | **设计预期** | **是否已确认可跑 GROUP（本仓库文档）** |
|----|---------|--------------|----------------------------------------|
| Revenue | `revenue_query` | group-wide 分支存在（**`RevenueQueryToolExecutor`** / path 与 **`ARG_GROUP_WIDE_OVERVIEW_HINT`** 等） | **C-44 切片** — **`PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE`**。**C-48** — **`AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase#buildPlan`** 复用 **`AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase`** 的 Hydrated 上下文与 ReadRequest；营收 **`scopeName`** 可物化为 **「当前可见门店 AAA、汀兰餐厅（集团口径）」** 以供 Builder 生成 **GROUP `summaryText`**（**不**复制 STORE Composite 单店 `scopeLabel`）。 |
| Purchase | `purchase_overview` | **`PurchaseOverviewToolExecutor#buildPurchaseOverviewToolArgs`**：`groupPurchaseOverview=true` 时 **`ARG_GROUP_PURCHASE_AGGREGATION`** + 多店 **`ARG_RESOLVED_DEPARTMENT_IDS`** | **C-45 切片** — 独立 Harness **`PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE`** + **`groupPurchaseOverview=true`**（与 C-19 STORE **`false`** 对照）；诚实 **`REAL_BRIDGE_HYDRATED_PURCHASE_GROUP_TOOL_*`** |
| StockReduce | `stock_reduce_query` | **`StockReduceQueryToolExecutor#buildHarnessToolArgs`**：`groupStockReduceQuery=true` 时 **`ARG_GROUP_STOCK_REDUCE_AGGREGATION`** + 多店 **`ARG_RESOLVED_DEPARTMENT_IDS`** | **C-46 切片** — 独立 Harness **`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE`** + **`groupStockReduceQuery=true`**（与 C-24 STORE **`false`** 对照）；诚实 **`REAL_BRIDGE_HYDRATED_STOCK_REDUCE_GROUP_TOOL_*`** |
| DishProfit | `dish_profit_analysis` | **`DishProfitQueryToolExecutor#buildDishProfitAnalysisToolArgs`**：`BusinessToolExecutionNode#shouldRouteGroupWideDishInsight` 为 true 时 **集团广角** `ARG_DEPARTMENT_FATHER_ID` + 多店 **`ARG_RESOLVED_DEPARTMENT_IDS`**（见生产代码） | **C-47 切片** — **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE`** + 物化 **`scopeType=GROUP`** + **`AiUserContext=GROUP_MANAGER`**（**`shouldRouteGroupWideBusinessOverview` 在 user 为空时为 false** — 见 **`dish-profit-planner-adapter-design.md` §7.10**）；诚实 **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_GROUP_TOOL_OK` / `…_GROUP_TOOL_DEGRADED`**；**禁止**单店 AAA fallback 冒充 GROUP |

**C-44 实现前置条件（摘自需求）**：

1. **逐 Tool 实测** GROUP 支持矩阵（上表打勾 / 文档化限制）。  
2. **`visibleStores` 与 `dataScope`** 的最小字段集合 **与 Resolver / Tool 契约** 对齐。  
3. **`summaryText` GROUP 安全措辞** 模板库（§7）**先行冻结** 再写 Builder。  
4. **不支持域的降级路径**（§8）**与 trace / `dataCoverage` 一致**，**禁止**单店顶替集团。

---

## 7. `summaryText` — GROUP 口径（确定性、非 LLM）

**必须**：

- 使用 **「当前可见门店」「全部门店」** 或 **显式枚举「AAA 与汀兰餐厅」** 等与 **`visibleStores` 一致** 的主语。  
- **若** **`revenueSummary.storeRows`**（或等价）**含可信排行**：可写 **「营收最高的门店为……」**（**证据** 来自 DTO 字段，**不**口胡排行）。  
- **若** **无多店明细**：**不编造**排行、**不**把 **单店** 数字写作 **集团总数**。

**禁止**：

- 默认 **「AAA 在……」** 作为全文主语（除非用户问题 **显式** 仅针对 AAA — **仍**由上游 intent 给出，**Composite 不解析原文**）。  
- **「四类数据均已读取」** 当 **任域 `dataCoverage.success=false`**。  
- **无来源的 0** 冒充 **已读集团出库/毛利**。  
- **「经营正常 / 没有问题」** 式结论（与 C-40 精神一致）。

---

## 8. 降级策略（GROUP）

| 条件 | `dataCoverage` | `riskLevel` | `summaryText` / 信号 |
|------|----------------|-------------|----------------------|
| **任一域** GROUP **不支持**、Tool **FAILED/DEGRADED**、或 **payload 无法构成合法 GROUP summary** | 该域 **`success=false`**，**`realToolInvoked`** 按 trace **如实** | **`INSUFFICIENT_DATA`**（或 **`UNKNOWN`** — **不**与「高经营风险」混同，遵守 **`business-diagnosis-answer-plan-design.md` §6**） | **`summaryText` 明示哪一域未完整读取/不支持 GROUP**；**`dataIncompleteSignal` 非空** |
| **禁止** | 将 GROUP 失败 **静默 fallback** 为 **单店查询成功** 后仍标 **GROUP 成功** | **禁止** 在 **数据不足** 时给 **「集团经营正常」** | **禁止** 用 **其他域** 臆造失败域数值 |

**trace（设计）**：

- **`finalAnswerPlanType`**：**`BUSINESS_DIAGNOSIS_COMPOSITE`**  
- **`usedTools`**：四域 **以实际调用为准**；成功步须 **真实 Tool id**；mock 步 **`mock_*`**  
- **`degradedSteps`**：**可审计** **`stepId` + `degradedReason`**  
- **Replay 根摘要**：**不**输出完整 **`AiRunState` / `AiResolvedQueryContext`** 大二进制/JSON 树 — **仅** 允许与 STORE 同构的 **裁剪摘要字段**（如既有 **`businessDiagnosis*`** 根字段）

---

## 9. 当前范围（C-43 + C-48 + C-44～C-47 切片）

- **C-43**：**GROUP 规格**（本文 §1～§8）— 设计约束仍适用。  
- **C-48**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase#buildPlan`**；**`CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor`**（四数据步 **Adapter Registry**，诊断 **`BusinessDiagnosisCompositeAnswerPlanBuilder`**，建议 **mock**）；**`AiHarnessReplayPlannerExecutorMock`** 分支；**`AiHarnessBuiltinCases`** + **`isPlannerExecutorMockHarnessCase`**；**`resolveReplayMode`** 与 **C-35** 同为 **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER`** 系摘要。根摘要：**`plannerCompositeNote`** = **`group composite; four group hydrated adapters invoked; diagnosis deterministic; recommendation mock`**；**`visibleStoreRootDepartmentIds`** 默认 **`[1,3]`**（或由 **`BusinessScopeResolutionSupport#extractVisibleStoreRootDepartmentIds`** 自营收上下文覆盖）。**不影响** **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`**（STORE）与 C-44～C-47 单域 case。  
- **C-44**：**仅营收** — Harness **`PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE`**（§11）。  
- **C-45**：**仅采购** — Harness **`PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE`**（§12）。  
- **C-46**：**仅出库/核销** — Harness **`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE`**（§13）。  
- **C-47**：**仅菜品毛利** — Harness **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE`**（§14）。  
- **仍不做**：前台、Master 生产主链路调度、Composite 层用户原文 **contains/regex** 路由、为「好看」而改四条生产 Tool。

---

## 10. C-49：GROUP Composite curl 验收快照与已知限制（文档收口）

下列为 **Harness Replay / curl** 在一次 **SUCCESS** 轮次下的 **典型观测**（依环境 DB；**非**生产 Master）：

| 观测项 | 值 |
|--------|-----|
| **caseId** | **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** |
| **`plannerExecutorTrace.overallStatus`** | **`SUCCESS`** |
| **四数据步汇总 `usedTools`** | **`revenue_query`**、**`purchase_overview`**、**`stock_reduce_query`**、**`dish_profit_analysis`**（及诊断 **`mock_diagnosis_compose`**、建议 **`mock_build_recommendation_plan`**） |
| **`visibleStoreRootDepartmentIds`**（根摘要） | **`[1, 3]`**（或与营收 **`AiResolvedQueryContext`** 提取一致） |
| **`businessDiagnosisDataCoverage`（四域）** | 典型：**`success=true`** 且 **`realToolInvoked=true`**（若某域真实降级则按 §8 **诚实**反写） |
| **`businessDiagnosisSummaryText`** | **GROUP 口径**（多店/当前可见门店措辞；**不**默认单店主语 **「AAA 在……」**） |

**已知限制（C-49 冻结表述）**

- **Revenue**：**`totalRevenue` 是否等价「集团合计」** 仍待产品与 Tool 契约 **后续确认**；**`summaryText`** 对此 **不强断言**（可导向「营收数据已读、明细见门店行」类保守句）。
- **Purchase**：**门店明细少** 时 **不**做门店排行推断。
- **DishProfit**：若 **`planType=AGGREGATED_DISH_PORTFOLIO_FALLBACK`**，诊断与摘要 **保守**（不强调单店/集团代表性）。
- **Recommendation**：**仍** **`mock_build_recommendation_plan`**，**无**生产 Action。
- **未接**：**Master** 生产主链路、**前台**、**LLM** 自由诊断。

**Builder 版本**：**`BusinessDiagnosisCompositeAnswerPlan.debug.mappingNotes`** 仍含 **`phase=C-38.2_zero_vs_missing`**、**`signalsPhase=C-39_minimal_deterministic`**、**`summaryPhase=C-40_deterministic_zh`**（及 C-42 出库降级时的 **`degradeClausePhase`** 等）；**`builderVersion`** 字段为 **`C-49`**（仅版本标记递进，**不改**上述相位语义）。

---

## 11. C-44 切片：`PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE`（Harness-only）

| 项 | 规格 |
|----|------|
| **类** | **`AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase`** |
| **`scopeType`** | **`GROUP`**；**`visibleStores`**：门店根 **1 / AAA**、**3 / 汀兰餐厅** |
| **`AiResolvedDataScope`** | **`AiResolvedDataScope.fromOrgScope(org)`**（与生产一致） |
| **`AiRunState.departmentId`** | **`null`**（**不**当作单店查询锚点）；**`resolvedQueryContext`** 与 **`PlannerRevenueExecutionContext.resolvedQueryContext`** **同一引用**；**`toolResults`** 初始空 `Map` |
| **`RevenuePlannerReadRequest`** | **`scopeType=GROUP`**；**`queryDepartmentIds=[1,3]`**；**`targetStoreDepartmentId=null`** |
| **Bridge / Tool** | **`RevenuePlannerRealReadBridge`** → 真实 **`revenue_query`**（**无**新 SQL） |
| **诚实摘要** | 成功：**`plannerRevenueAdapterHonesty=REAL_BRIDGE_HYDRATED_REVENUE_GROUP_TOOL_OK`**；失败：**`…_GROUP_TOOL_DEGRADED`**（**不** fallback 单店假成功） |
| **观测摘要**（Replay 根级） | **`harnessRevenueGroupVisibleStoreRootDepartmentIds`**；**`harnessRevenueQueryEnvelopePresent`**；**`harnessRevenueQueryTotalRevenue`**（若有）；**`harnessRevenueQueryStoreRevenueRankingSize`** / **`harnessRevenueQueryRankingStoreDepartmentIds`**（若 Tool 返回排行） |

**注册**：`AiHarnessBuiltinCases`、`isPlannerExecutorMockHarnessCase`、`AiHarnessReplayPlannerExecutorMock`、`AiHarnessReplayService#resolveReplayMode`（与 **C-13** 同 **`PLANNER_EXECUTOR_REVENUE_ADAPTER`** replay 族）。

---

## 12. C-45 切片：`PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE`（Harness-only）

| 项 | 规格 |
|----|------|
| **类** | **`AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase`** |
| **`scopeType`** | **`GROUP`**；**`visibleStores`**：门店根 **1 / AAA**、**3 / 汀兰餐厅** |
| **`AiResolvedDataScope`** | **`AiResolvedDataScope.fromOrgScope(org)`** |
| **`queryIntent`** | **`PURCHASE_OVERVIEW`** / **`purchase_overview_path`**；**`structuredIntentDetail=purchase_overview_summary`**；**`purchaseSourceType=ALL`**（与 C-19 对齐） |
| **`AiRunState.departmentId`** | **`null`**；**`distributerId=2`**（与 C-19 文档占位一致）；**`groupPurchaseOverview=true`** — 生产 **`buildPurchaseOverviewToolArgs`** 据此走集团聚合参数（**C-19 STORE 为 `false`**） |
| **`PurchasePlannerReadRequest`** | **`scopeType=GROUP`**；**`queryDepartmentIds=[1,3]`**；**`targetStoreDepartmentId=null`** |
| **Bridge / Tool** | **`PurchasePlannerRealReadBridge`** → **`purchase_overview`** |
| **诚实摘要** | 成功：**`plannerPurchaseAdapterHonesty=REAL_BRIDGE_HYDRATED_PURCHASE_GROUP_TOOL_OK`**；失败：**`…_GROUP_TOOL_DEGRADED`** |
| **观测摘要**（Replay 根级） | **`harnessPurchaseGroupVisibleStoreRootDepartmentIds`**；**`harnessPurchaseOverviewEnvelopePresent`**；**`harnessPurchaseAnswerPlanType`**；**`harnessPurchaseQueryTotalPurchaseAmount`** / **`harnessPurchaseQueryPurchaseOrderCount`**；**`harnessPurchaseFocusRowsSize`** / **`harnessPurchaseSecondaryRowsSize`**；**`harnessPurchaseFocusRowStoreDepartmentIds`**（若排行行含门店 id） |

> **`harnessPurchaseQueryTotalPurchaseAmount`** / **`harnessPurchaseQueryPurchaseOrderCount`** 为历史命名的 Harness 观测键；当前数据来源为 **`purchase_overview`** 的 **`purchaseOverview`** 汇总，**不代表** 已删除的 **`purchase_query`** Tool。

**注册**：与 **C-19** 同 **`PLANNER_EXECUTOR_PURCHASE_ADAPTER`** replay 族。

---

## 13. C-46 切片：`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE`（Harness-only）

| 项 | 规格 |
|----|------|
| **类** | **`AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase`** |
| **`scopeType`** | **`GROUP`**；**`visibleStores`**：门店根 **1 / AAA**、**3 / 汀兰餐厅** |
| **`AiResolvedDataScope`** | **`AiResolvedDataScope.fromOrgScope(org)`** |
| **`queryIntent`** | **`STOCK_REDUCE_QUERY`** / **`PATH_STOCK_REDUCE_QUERY`**；**`structuredIntentDetail=stock_reduce_overview`**；**`querySemanticParse.metric.stockReduceType=ALL`** |
| **`AiRunState.departmentId`** | **`null`**；**`distributerId=2`**（与 C-24 占位一致）；**`groupStockReduceQuery=true`** — 生产 **`StockReduceQueryToolExecutor#buildHarnessToolArgs`** 据此走 **`ARG_GROUP_STOCK_REDUCE_AGGREGATION`** + 多店 **`ARG_RESOLVED_DEPARTMENT_IDS`**（**C-24 STORE 为 `false`**） |
| **`StockReducePlannerReadRequest`** | **`scopeType=GROUP`**；**`queryDepartmentIds=[1,3]`**；**`targetStoreDepartmentId=null`**；**`reduceType=ALL`**；**`totalsBasis=CALENDAR_NATURAL_DAY`** |
| **Bridge / Tool** | **`StockReducePlannerRealReadBridge`** → **`stock_reduce_query`** |
| **诚实摘要** | 成功：**`plannerStockReduceAdapterHonesty=REAL_BRIDGE_HYDRATED_STOCK_REDUCE_GROUP_TOOL_OK`**；失败：**`…_GROUP_TOOL_DEGRADED`** |
| **观测摘要**（Replay 根级） | **`harnessStockReduceGroupVisibleStoreRootDepartmentIds`**；**`harnessStockReduceQueryEnvelopePresent`**；**`harnessStockReduceQueryGrandTotalFourTypes`** / 分型合计 / **`harnessStockReduceTotalsBasis`**；**`harnessStockReduceFocusRowsSize`** / **`harnessStockReduceSecondaryRowsSize`**；**`harnessStockReduceFocusRowStoreDepartmentIds`**（若行内带门店 id） |

**注册**：与 **C-24** 同 **`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER`** replay 族（**`AiHarnessReplayService#resolveReplayMode`**）。

---

## 14. C-47 切片：`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE`（Harness-only）

| 项 | 规格 |
|----|------|
| **类** | **`AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase`** |
| **`scopeType`** | **`GROUP`**；**`visibleStores`**：门店根 **1 / AAA**、**3 / 汀兰餐厅** |
| **`AiResolvedDataScope`** | **`AiResolvedDataScope.fromOrgScope(org)`** |
| **`queryIntent`** | **`DISH_PROFIT`** / **`PATH_DISH_PROFIT`**；**`structuredIntentDetail=dish_profit_overview`**；**`dishProfitMetricType=OVERVIEW`**；**`mentionedDishName=null`** |
| **`AiRunState.departmentId`** | **`null`**（**不**当单店查询锚点）；**`distributerId=2`**；**`dishProfitPath=true`**（与 C-29 对齐）；**`aiUserContext`**：**`GROUP_MANAGER`** + **`AiRoleMapper.permissionsForAiRole`** — **须**满足 **`shouldRouteGroupWideBusinessOverview`**（user 为空时该方法恒 false，见 **`dish-profit-planner-adapter-design.md` §7.10**）；**`resolvedQueryContext`** 与 **`DishProfitPlannerExecutionContext.resolvedQueryContext`** **同一引用** |
| **`DishProfitPlannerReadRequest`** | **`scopeType=GROUP`**；**`queryDepartmentIds=[1,3]`**；**`targetStoreDepartmentId=null`** |
| **Bridge / Tool** | **`DishProfitPlannerRealReadBridge`** → **`dish_profit_analysis`**（**无**新 SQL） |
| **诚实摘要** | 成功：**`plannerDishProfitAdapterHonesty=REAL_BRIDGE_HYDRATED_DISH_PROFIT_GROUP_TOOL_OK`**；失败：**`…_GROUP_TOOL_DEGRADED`**（**不** fallback 单店假成功） |
| **观测摘要**（Replay 根级） | **`harnessDishProfitGroupVisibleStoreRootDepartmentIds`**；**`harnessDishProfitAnalysisEnvelopePresent`**；**`harnessDishProfitFocusRowsSize`** / **`harnessDishProfitSecondaryRowsSize`**；**`harnessDishProfitFocusGrossProfitAmount`** / **Rate** / **sales/cost**；**`harnessDishProfitFocusRowStoreDepartmentIds`**（若行内含门店 id） |

**注册**：与 **C-29** 同 **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER`** replay 族。

---

## 15. 文档索引

| 文档 | 用途 |
|------|------|
| [`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md) | STORE caseId、六步、C-42 降级 |
| [`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md) | Composite AnswerPlan DTO、§8 C-37～C-49 |
| [`planner-executor-v1-design.md`](./planner-executor-v1-design.md) | Executor、§22 营收 Hydrated、§27 Composite、Tool 路径 |
| [`planner-executor-composite-c30-c40-summary.md`](./planner-executor-composite-c30-c40-summary.md) | C-30～C-42 收口与路线图 |

**文档版本**：**C-43**（GROUP 规格）+ **C-48**（**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** Harness）+ **C-49**（§10 curl 快照与限制、**`BUILDER_VERSION=C-49`**）+ **C-44～C-47**（单域 GROUP Hydrated §11～§14）。
