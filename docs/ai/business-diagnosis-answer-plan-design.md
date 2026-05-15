# BusinessDiagnosisCompositeAnswerPlan — **C-36 设计（无代码）+ C-50 Composer 只读引用**

> **读者**：Planner / Diagnosis / Composer 工程师。  
> **阶段**：**C-36** — **仅**定义结构化 **`BusinessDiagnosisCompositeAnswerPlan`**（下文 **Composite AnswerPlan**），承接四域真实读数摘要，并为后续 **确定性诊断汇总**（C-37）、**Composer**、可选 LLM **稳定输入**。  
> **阶段**：**C-50** — **仅文档**：Composite **Composer** **只读**本 DTO 的 **权威字段**（见 **§8.12**），**不**重读 **`toolResults`**；全文 **[`business-diagnosis-composer-readonly-design.md`](./business-diagnosis-composer-readonly-design.md)**。  
> **本阶段**：**不写 Java**、**不接** Master / 生产 Graph、**不**改 Resolver / Composer、**不**调 LLM、**不**写 SQL、**不**触发真实 action。  
> **权威编排上下文**：**[`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md)**（C-30～C-35）；Planner trace：**[`planner-executor-v1-design.md`](./planner-executor-v1-design.md)** §12 / §27；单域 AnswerPlan 边界：**[`diagnosis-answer-plan.md`](./diagnosis-answer-plan.md)**。  
> **C-43 GROUP 规格 + C-48 Harness + C-49 收口**：**[`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md)**（§10 **curl 快照与限制**）；**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase#toHarnessSummary`**（与 **§8.11**）；**`BusinessDiagnosisCompositeAnswerPlanBuilder#BUILDER_VERSION=C-49`**。  
> **C-41 阶段总收口（仅文档）**：**C-36～C-40** 与 Composite 全链路的汇总、验收 caseId、限制与下一阶段见 **[`planner-executor-composite-c30-c40-summary.md`](./planner-executor-composite-c30-c40-summary.md)**。

---

## 1. 设计目标

| 目标 | 说明 |
|------|------|
| **单一事实入口** | Composite 执行后，下游（诊断 compose、Composer、Debug）**优先只读**本 DTO，**不**直接扫原始 `toolResults` JSON |
| **四域并排** | 营收 / 采购 / 出库核销 / 菜品毛利 **同 scope、同 timeWindow** 下的 **最小可比摘要** |
| **诚实降级** | 任一类数据 **缺失 / 失败 / mock 未接 Tool** 时，**不编造**该域数值；**整体仍可生成** Composite AnswerPlan，但必须 **`degradedSteps` / `dataCoverage` / `riskLevel` 口径一致** |
| **诊断信号可解释** | **`diagnosisSignals`**（§4）每项带 **`sourceStep`、`severity`、`reason`、`evidenceRefs`**，便于审计与后续 LLM（远期） |
| **与 trace 对齐** | **`dataCoverage`** 与 **`plannerExecutorTrace.stepResults`**、`stepId`、`usedTools`、`degradedReason` **可机械映射**（C-37） |

---

## 2. 根类型：`BusinessDiagnosisCompositeAnswerPlan`

逻辑名（实现阶段可映射为 Java `record` / DTO / JSON schema）。**根判别字段**：

| 字段 | 类型（概念） | 必填 | 说明 |
|------|----------------|------|------|
| **`type`** | 字面常量 | 是 | 固定 **`BUSINESS_DIAGNOSIS_COMPOSITE`**（与 **`finalAnswerPlanType`** / Composer 路由对齐） |
| **`scopeLabel`** | `string` | 是 | 人类可读组织范围（例：单店名 + id 摘要；**不**替代 `AiResolvedOrgScope` 权威） |
| **`timeLabel`** | `string` | 是 | 与 **`AiResolvedTimeWindow`** 一致的展示标签（**不**替代起止日期权威） |
| **`revenueSummary`** | `RevenueSummary \| null` | 条件 | **成功且有数据** 时非空；失败 / 跳过 / 未执行时为 **`null`**（**禁止**用 0 伪装「有数」） |
| **`purchaseSummary`** | `PurchaseSummary \| null` | 条件 | 同上 |
| **`stockReduceSummary`** | `StockReduceSummary \| null` | 条件 | 同上 |
| **`dishProfitSummary`** | `DishProfitSummary \| null` | 条件 | 同上 |
| **`diagnosisSignals`** | `DiagnosisSignals` | 是 | 见 §4；**允许**在数据极缺时仅含 **`dataIncompleteSignal`** |
| **`riskLevel`** | 枚举（概念） | 是 | **仅**基于 **已存在且非 null** 的 summary 与 signal **推导**（见 §6）；缺失域 **不得** 假定为高风险事实 |
| **`summaryText`** | `string` | 是 | **C-40**：**确定性中文短摘要**（Harness / Composer 输入）；**仅**由本对象已有字段拼接；**非** LLM 终稿；**禁止**写「经营正常/无问题」 |
| **`keyFindings`** | `List<string>` | 是 | **短句级**发现（**非**最终用户可见长文）；可来自确定性规则（C-37） |
| **`suggestedNextQuestions`** | `List<string>` | 是 | **澄清 / 下钻**建议问法；**无** LLM 时可为模板句 |
| **`dataCoverage`** | `List<DataDomainCoverage>` | 是 | 见 §5；**四域各一项**（固定顺序：`REVENUE` → `PURCHASE` → `STOCK_REDUCE` → `DISH_PROFIT`） |
| **`degradedSteps`** | `List<string>` | 是 | **`stepId`** 列表；与 **`plannerExecutorTrace.degradedSteps`** **同构或子集**（实现时明确是否含 SKIPPED） |
| **`debug`** | `CompositeAnswerPlanDebug` | 否 | 构建版本、`planId`/`runId` 摘要、映射哈希等 |

**约定**：**C-36** 原不包含长段自然语言；**C-40** 允许 **`summaryText`** 作为**短段落、可审计、确定性**陈述（仍 **不**等价于 LLM 终稿）。

---

## 3. 四域最小摘要 DTO

### 3.1 `RevenueSummary`

| 字段 | 类型（概念） | 必填 | 说明 |
|------|----------------|------|------|
| **`totalRevenue`** | `decimal` / `Money` | 是 | 时间窗内营业额合计（与单域 **`DailyRevenueAnswerPlan`** 口径对齐） |
| **`storeRows`** | `List<RevenueStoreRow>` | 否 | 门店级明细行（**STORE 单店** 时可为 1 行或空表但 **`totalRevenue` 仍有效**） |
| **`priorPeriodTotalRevenue`** | `decimal` \| null | 否 | **环比/同比**对照用；无对照窗口时为 `null` |
| **`compareLabel`** | `string` \| null | 否 | 如「上月同期」「上周」；无对照时为 `null` |
| **`trendDirection`** | `FLAT \| UP \| DOWN \| UNKNOWN` \| null | 否 | **仅**在 **`priorPeriodTotalRevenue` 与当前均可比** 时非 `UNKNOWN`；否则 `null` 或 **`UNKNOWN`** |

`RevenueStoreRow`（概念）：至少 **`storeLabel`**、**`amount`**；可加 **`orderCount`** 等与现有计划对齐的字段（C-37 从 `DailyRevenueAnswerPlan` 映射）。

### 3.2 `PurchaseSummary`

| 字段 | 类型（概念） | 必填 | 说明 |
|------|----------------|------|------|
| **`purchaseAmount`** | `decimal` | 是 | 采购金额合计（窗口内） |
| **`purchaseCount`** | `int` / `long` | 是 | 笔数或单据数（与 **`PurchaseAnswerPlan`** 权威字段对齐） |
| **`purchaseSourceType`** | `string` / 枚举 | 否 | 主来源维度（如渠道/类型摘要）；无则 `null` |
| **`focusRows`** | `List<PurchaseFocusRow>` | 否 | Top-N 供应商 / 品类等 **脱敏摘要行**（结构 C-37 对齐 `PurchaseAnswerPlan`） |

### 3.3 `StockReduceSummary`

| 字段 | 类型（概念） | 必填 | 说明 |
|------|----------------|------|------|
| **`grandTotalAmount`** | `decimal` | 是 | 出库/核销 **总发生额**（与 Tool 返回层级一致） |
| **`produceTotal`** | `decimal` \| null | 否 | 生产耗用等；无拆分则为 `null` |
| **`wasteTotal`** | `decimal` \| null | 否 | 浪费 |
| **`lossTotal`** | `decimal` \| null | 否 | 报损 |
| **`returnTotal`** | `decimal` \| null | 否 | 退货/退库等 |
| **`totalsBasis`** | `string` | 是 | **短说明**：金额口径（含税/不含税/标价层等）与 **`StockReduceAnswerPlan`** 一致，避免跨域误读 |

### 3.4 `DishProfitSummary`

| 字段 | 类型（概念） | 必填 | 说明 |
|------|----------------|------|------|
| **`grossProfitAmount`** | `decimal` | 是 | 毛利额 |
| **`grossProfitRate`** | `decimal` / `ratio` | 是 | 毛利率（0–1 或 0–100%，实现统一） |
| **`salesAmount`** | `decimal` | 是 | 销售额 |
| **`costAmount`** | `decimal` | 是 | 成本额 |
| **`focusRows`** | `List<DishProfitFocusRow>` | 否 | Top 菜品/维度行（与 **`DishProfitAnswerPlan`** 对齐） |

**四域共性**：若某域 **`PlannerStepResult`** 为 FAILED/DEGRADED 且无 **`payloadRef`** 可解析，则对应 summary 为 **`null`**，**不**填充默认数值。

---

## 4. `DiagnosisSignals` 结构

容器对象，包含下列 **命名信号槽**（每项类型均为 **`DiagnosisSignal \| null`**）：

| 槽位 | 语义 | 触发直觉（仅规则示意，C-37 实现） |
|------|------|-------------------------------------|
| **`revenueWeakSignal`** | 营收偏弱 | **`totalRevenue`** 低于阈值或 **`trendDirection=DOWN`**（有对照且可信时） |
| **`purchaseHighSignal`** | 采购偏高 | **`purchaseAmount`** 相对 **`salesAmount`/`totalRevenue`** 比例异常（**两域均可用**时） |
| **`stockReduceHighSignal`** | 出库/核销异常偏高 | **`grandTotalAmount`** 相对历史或营收偏高（**有数**时） |
| **`dishProfitLowSignal`** | 毛利率偏低 | **`grossProfitRate`** 低于阈值（**有数**时） |
| **`dataIncompleteSignal`** | 数据不完整 | 任一域 **`dataCoverage.success=false`** 或 summary **`null`** |

### 4.1 `DiagnosisSignal`（每个信号）

| 字段 | 类型（概念） | 必填 | 说明 |
|------|----------------|------|------|
| **`sourceStep`** | `string` | 是 | 归因 **`stepId`**（如 **`step_revenue_hydrated`**）或 **`COMPOSITE_DIAGNOSIS_RULE`**（规则跨步时） |
| **`severity`** | `INFO \| NOTICE \| WARNING \| CRITICAL` | 是 | **CRITICAL** 仅当 **多域一致**且规则明确；**数据缺失不得单独抬到 CRITICAL「事实」** |
| **`reason`** | `string` | 是 | 机器可读 **短因**（含阈值 id 或规则 id 更佳） |
| **`evidenceRefs`** | `List<EvidenceRef>` | 是 | 指向 **域 summary 字段路径** 或 **`payloadRef` 稳定键**（C-37 定义，**禁止**伪造不存在的 path） |

### 4.2 `EvidenceRef`（概念）

| 字段 | 说明 |
|------|------|
| **`domain`** | `REVENUE` / `PURCHASE` / `STOCK_REDUCE` / `DISH_PROFIT` / `TRACE` |
| **`refKind`** | `SUMMARY_FIELD` / `STEP_RESULT` / `ANSWER_PLAN_ID` |
| **`key`** | 如 **`revenueSummary.totalRevenue`** 或 **`step_purchase_hydrated`** |

---

## 5. `DataDomainCoverage`（`dataCoverage` 列表元素）

**每个数据域一条**，固定顺序，便于 Replay diff。

| 字段 | 类型（概念） | 必填 | 说明 |
|------|----------------|------|------|
| **`domain`** | 枚举 | 是 | **`REVENUE` \| `PURCHASE` \| `STOCK_REDUCE` \| `DISH_PROFIT`** |
| **`success`** | `boolean` | 是 | **`true`** 仅当该步 **`PlannerStepResult.status`** 为 SUCCESS **且** 可解析出合法 summary（PARTIAL 可在 C-37 映射为 `success=false` 并填 **`degradedReason`**，实现约定） |
| **`realToolInvoked`** | `boolean` | 是 | **`true`** 当 **`usedTools`** 含 **`revenue_query` / `purchase_overview` / `stock_reduce_query` / `dish_profit_analysis`** 中对应 id；**C-31 MOCK** 场景为 **`false`** |
| **`stepId`** | `string` | 是 | 如 **`step_revenue_hydrated`** |
| **`usedTool`** | `string` \| null | 是 | 生产 Tool id **或** **`mock_*`**（与 trace **一致**） |
| **`degradedReason`** | `string` \| null | 否 | 来自 **`PlannerStepResult.degradedReason`** / error 分类；成功且无歧义时为 `null` |

---

## 6. 降级、`riskLevel` 与禁止编造

### 6.1 整体策略

| 规则 | 说明 |
|------|------|
| **仍可生成 AnswerPlan** | 哪怕 **四域 summary 全 `null`**（极端失败），仍输出 **Composite AnswerPlan**：**`diagnosisSignals.dataIncompleteSignal` 必非空**、**`riskLevel`** 落在 **`UNKNOWN` 或 `INSUFFICIENT_DATA`**（实现枚举见下） |
| **禁止编造缺失域** | 缺失域 **`*_Summary=null`**，**不**用其他域推算「虚假」该域金额 |
| **`riskLevel` 仅用已有数据** | **不得**因「采购未加载」而断言「采购风险高」；**可**说「采购数据不可用，无法评估采购风险」——落在 **`keyFindings` / `dataIncompleteSignal`** |
| **`degradedSteps`** | 与 trace 对齐；若计划级 **`CONTINUE_WITH_DEGRADED`**，**failed 步**进入 **`degradedSteps`** |

### 6.2 `riskLevel`（建议枚举）

| 值 | 含义 |
|----|------|
| **`LOW`** | 有足量域数据且 **无** WARNING+ 信号 |
| **`MEDIUM`** | 少量 WARNING 或部分域 PARTIAL  
| **`HIGH`** | 多域 WARNING 或存在 CRITICAL 信号（**且**信号 **`evidenceRefs`** 均指向 **非空** 证据） |
| **`UNKNOWN`** | 域数据矛盾或规则不可判定 |
| **`INSUFFICIENT_DATA`** | **多数域失败**或 summary **不可用**，**不**做强结论 |

**映射约束**：**`HIGH`** 不允许在 **`INSUFFICIENT_DATA`** 同时作为事实结论；实现可 **优先级**：数据不足 **优先** `INSUFFICIENT_DATA`。

### 6.3 `keyFindings` / `suggestedNextQuestions`

- **`keyFindings`**：**确定性**模板 + 占位符（已从各 summary 取值）；**C-36/C-37** **不**要求自然语言华丽。
- **`suggestedNextQuestions`**：针对 **缺失域**（「请确认采购权限/时间窗」）、**单域异常**（「是否查看出库明细」）的 **固定问法库**，**不**调用 LLM 生成。

---

## 7. `debug`（`CompositeAnswerPlanDebug`）

| 字段 | 说明 |
|------|------|
| **`builderVersion`** | Composite mapper 版本 |
| **`sourceTraceFingerprint`** | 对 **`stepResults` 关键字段** 的 hash（可选） |
| **`mappingNotes`** | 人读说明（如「purchase 行级字段未接入 v1」） |

---

## 8. C-37 已落地（Composite ALL_REAL Harness 确定性骨架）

| 步骤 | 内容 |
|------|------|
| **8.1 映射入口** | **`BusinessDiagnosisCompositeAnswerPlanBuilder#build(PlannerStepExecutionRequest)`**：从 **`planSnapshot.finalAnswerPlanType`**、**`priorStepResults`**（四域 hydrated **`stepId`**）、**`degradedStepsSoFar`**（与 trace 前缀一致）、各域 **`Planner*ExecutionContext#getRunState()`** 已物化的 AnswerPlan / overview **摘要**拼装 |
| **8.2 步级关联** | **`dataCoverage`**：按域写入 **`BusinessDiagnosisDomainCoverage`**（**`success`**、**`usedTool`**、**`realToolInvoked`** 对照生产 Tool id） |
| **8.3 Summary 抽取** | **只读**各域 **已有 DTO 字段**（**不**扫原始 DB / 大 JSON） |
| **8.4 诊断 compose** | **`diagnosisSignals`**：**C-39** 最小确定性规则（见 **§8.8**）；**`revenueWeakSignal`** 恒 **`null`**（v1 无同比/环比）。**`riskLevel`**：**C-39 规则不变**（见 §8.8）。**`keyFindings`** / **`suggestedNextQuestions`** 由信号与覆盖 **确定性**生成；**`suggestedNextQuestions`**（**C-40**）合并模板下钻问法（见 **§8.9**）；**不**调 LLM |
| **8.5 Composer（Composite）** | **C-50** 契约 + **C-51** 实装：**`BusinessDiagnosisCompositeReadonlyComposer#compose(BusinessDiagnosisCompositeAnswerPlan)`** → **`BusinessDiagnosisCompositeComposeResult`**；**不**读 **`toolResults`**。详见 **[`business-diagnosis-composer-readonly-design.md`](./business-diagnosis-composer-readonly-design.md)** |
| **8.6 Harness** | **`AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase#toHarnessSummary`** / **`AiPlannerExecutorBusinessDiagnosisCompositeStockDegradedGraphCase#toHarnessSummary`**（C-42）/ **`AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase#toHarnessSummary`**（**C-48 GROUP**）根字段：**`businessDiagnosisAnswerPlanType`**、**`businessDiagnosisRiskLevel`**、**`businessDiagnosisDataCoverage`**、**`businessDiagnosisCompositeAnswerPlan`**（JSON Map）、**`businessDiagnosisSummaryText`**（=`summaryText`）、**`businessDiagnosisSuggestedNextQuestions`**（=`suggestedNextQuestions`）；**C-51** 另增 **`businessDiagnosisFinalAnswerText`**、**`businessDiagnosisComposerVersion`**（**`C-51_READONLY_COMPOSER`**，**Harness 观测**）；**C-48** 另含 **`plannerCompositeHonesty` / `plannerCompositeNote` / `visibleStoreRootDepartmentIds`** |
| **8.7 C-38 四域 summary 映射增强** | **`BusinessDiagnosisCompositeAnswerPlanBuilder`**：**`BUILDER_VERSION=C-49`**（**C-49** 文档收口标记；能力演进 **C-38.1 → C-38.2 → C-39 → C-40 → C-42 → C-48**）。在 **不**调用 LLM、**不**新 SQL、**不改** `dataCoverage` 机械映射前提下，**优先**各域 **AnswerPlan / overview**，**其次** **`AiRunState.toolResults`**；缺字段 **保留 null / 空列表**，**禁止**用 **0** 冒充未知；**`debug.mappingNotes`** 固定键：**`phase=C-38.2_zero_vs_missing`**、**`signalsPhase=C-39_minimal_deterministic`**、**`summaryPhase=C-40_deterministic_zh`**（+ 分域键；**C-42** 可选 **`harnessStockReduceDegraded` / `degradeClausePhase`**）；**版本号递增不改写上述相位键的语义** |
| **8.8 C-39 最小确定性 `diagnosisSignals`** | **`dataIncompleteSignal`**：任一域 **`success=false`** 或 **`realToolInvoked=false`** 时非空；**`severity`**：存在任一 **`success=false` → `WARNING`**；否则（仅 **`realToolInvoked=false`**）→ **`NOTICE`**；**`evidenceRefs`**：按域 **`DATA_COVERAGE`** + **`success=false` / `realToolInvoked=false`**；对 **`success=false`** 另附 **`PLANNER_STEP`** + **`stepId`**（C-42）。四域 **`success=true` 且 `realToolInvoked=true`** 时 **`null`**。**`revenueWeakSignal`**：**`null`**。**`purchaseHighSignal`**：**`totalRevenue`** 与 **`purchaseAmount`** **均非 null** 且 **`purchaseAmount > totalRevenue` → `WARNING`**，`reason` 标明 **仅异常信号、不下结论**；`evidenceRefs`：`PURCHASE.purchaseAmount`、`REVENUE.totalRevenue`。**`stockReduceHighSignal`**：**`grandTotalAmount`** 与 **`totalRevenue`** 均非 null 且 **`grandTotalAmount > totalRevenue` → `WARNING`**；**真实 0 不触发**（`0 > revenue` 为假）。**`dishProfitLowSignal`**：**`grossProfitRate < 0` → `WARNING`**；**`grossProfitRate = 0`** 且 **`salesAmount > 0` → `NOTICE`**（**不作「毛利偏低」表述**）；**`salesAmount = 0` 且 `costAmount = 0`** 且 **毛利率为 0 → `null`**（不误报毛利低）。**每条 signal** 均带 **`evidenceRefs`**。**`riskLevel`**：任一步 **`dataCoverage.success=false` → `INSUFFICIENT_DATA`**；否则存在任一 **`severity=WARNING`** 的信号 → **`MEDIUM`**；否则 → **`NORMAL_OBSERVATION`** |
| **8.9 C-40 确定性中文 `summaryText`** | **`BUILDER_VERSION`** 见 **§8.7**（**`C-49`**）。**`summaryText`**：仅由 **`scopeLabel`、`timeLabel`、四域 summary、`diagnosisSignals`、`riskLevel`、覆盖状态** 拼接；**0** 与 **null** 分述；**真实 0** **不**解释为异常；**missing** **不**写成 0；**C-42**：**`STOCK_REDUCE` 域未成功**时在首段 **追加**「出库/核销未完整读取」句（见 **§8.10**）；收尾 **可**写「当前未触发确定性异常信号」，并 **显式排除**「经营正常」式结论（**`dataIncompleteSignal` 为 WARNING 时**仍以「详见 diagnosisSignals」为主）。**不**调用 LLM。根 Replay 字段 **`businessDiagnosisSummaryText`** / **`businessDiagnosisSuggestedNextQuestions`** 与 DTO 同源 |
| **8.10 C-42 出库 Harness 降级** | CaseId **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE`**：**`step_stock_reduce_hydrated`** **DEGRADED**、`usedTools` **空** → **`stockReduceSummary=null`**、**`mappingNotes.stockReduce`** 记录 **无 Tool 结果 / `degradedReason`**；**不假 SUCCESS**；**`summaryText` 不**写「四类均已读取」、**不**写无来源的出库 **0**；**`riskLevel=INSUFFICIENT_DATA`** |
| **8.11 C-43 / C-48 GROUP 多店 + C-49 收口** | **规格（C-43）**：**`scopeLabel` / `timeLabel`** 与 **`visibleStores`** 一致；各域 summary **有则填、无则不编造**；**`dataCoverage`** / **`riskLevel`** / **禁止单店 fallback 冒充集团** 见 §8.8 与 **`business-diagnosis-composite-group-design.md` §7–§8**。**Harness（C-48）**：CaseId **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** — **`scopeType=GROUP`** 时 **`summaryText`** **集团安全措辞**（示例：营收 **`totalRevenue`** 口径不确定则不强判集团合计；采购明细极少不编排行；菜品 **`AGGREGATED_DISH_PORTFOLIO_FALLBACK`** 保守）；**不**默认 **「AAA 在……」** 单店主语。**C-49**：**curl SUCCESS** 观测与 **已知限制** 见 **`business-diagnosis-composite-group-design.md` §10**；**`builderVersion=C-49`**；**`mappingNotes.phase` / `signalsPhase` / `summaryPhase`** 仍 **§8.7**。 |
| **8.12 C-50 / C-51 Composite Composer** | **C-50** 设计 + **C-51** Java：**[`business-diagnosis-composer-readonly-design.md`](./business-diagnosis-composer-readonly-design.md)** §9；**不接** Master / 前台 / LLM（主链路）。 |
| **8.13 C-52 生产入口 Gate** | **仅文档**：主链路进入 Composite 前须满足结构化 Gate（**不**用用户原文 pattern）；与 **`dataCoverage` / `riskLevel` / fallback** 对齐。**[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md)**。 |

---

## 9. 当前不做（C-36 / C-37 / C-38 前置约束重申）

| 不做项 | 说明 |
|--------|------|
| **接 Master 生产主链路** | Composite AnswerPlan **仅** Planner / Harness / 平行子图；**C-52** Gate 见 **[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md)**（**C-53** 再实装） |
| **接前台** | **无** UI |
| **调用 LLM** | C-37 / C-38 诊断与 summary 映射 **确定性优先** |
| **真实 action** | **无**通知 / 调价 / 下单 |
| **新 SQL** | **无** |
| **改 Resolver / Composer 主逻辑** | **C-50** 仅设计 Composite 只读 Composer 契约；**不**动既有 Composer **主模板** |
| **用户原文 contains/regex** | **禁止**在映射层新增 |
| **修改既有 Replay 期望** | **非必要**不改 **`src/test/**`** |

---

## 10. 参考文档

| 文档 | 用途 |
|------|------|
| [`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md) | Composite 六步、caseId、诚实性 |
| [`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md) | **C-43** 规格 + **C-48** Harness + **C-49** §10 |
| [`planner-executor-v1-design.md`](./planner-executor-v1-design.md) | `PlannerExecutorTrace`、`stepResults` |
| [`business-diagnosis-composer-readonly-design.md`](./business-diagnosis-composer-readonly-design.md) | **C-50** Composite Composer **只读 AnswerPlan** |
| [`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md) | **C-52** 生产入口 Gate（仅文档） |
| [`diagnosis-answer-plan.md`](./diagnosis-answer-plan.md) | DiagnosisPlan / 单域 AnswerPlan |

**文档版本**：**C-43 / C-48 / C-49 / C-50 / C-51 / C-52** — §8.7 **`BUILDER_VERSION=C-49`**（**`phase` / `signalsPhase` / `summaryPhase` 键与语义不变**）+ **§8.11** + **§8.12** + **§8.13**（**[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md)**，仅设计）。
