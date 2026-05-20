# PlannerExecutor Composite 经营诊断 — **C-30～C-40 阶段总收口（C-41）**

> **读者**：Planner / Harness / 后续 Composer / 前台 工程师。  
> **性质**：**仅文档** — 汇总 **C-30～C-40** 完整链路、caseId、已验收观测、当前限制与建议路线图；**不**替代各子文档细节。  
> **权威拆解**：**[`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md)**（计划与 caseId）、**[`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md)**（Composite AnswerPlan）、**[`planner-executor-v1-design.md`](./planner-executor-v1-design.md)**（Executor / Replay / §27 Composite）。

---

## 0. P1-B 验收分层（P1-B Final 已落地）

| 分层 | caseId / 组件 | 状态 |
|------|----------------|------|
| **当前主验收** | **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`**（C-35）、**`…_GROUP_CORE`**（C-48）、**`…_STOCK_DEGRADED_CORE`**（C-42）、**`DISH_PROFIT_MATRIX_P1`** | **保留** |
| **Removed（P1-B2a）** | 四域 **`*_ADAPTER_CORE`**、**`*_ADAPTER_REAL_BRIDGE_CORE`**（8 个） | 已删 |
| **Removed（P1-B Final）** | 四域 **`*_FAKE_OK_CORE`** / **`*_HYDRATED_CORE`** / **`*_GROUP_HYDRATED_CORE`**（12 个）；**C-31** **`…_COMPOSITE_CORE`**；**`Fake*PlannerReadBridge`** | 已删；物化见 **`PlannerCompositeHarnessContext`** |
| **说明** | 勿再 curl 单域 Adapter caseId；Composite strict 为 Planner 主验收轴 |

---

## 1. 阶段路线（C-30～C-40）

| 阶段 | 主题 | 要点 |
|:----:|------|------|
| **C-30** | Composite Plan **设计** | 固定多步计划、同一 STORE + `timeWindow`、六步编排、失败策略与诚实性；**不接** LLM 自由规划。 |
| **C-31** | Composite **skeleton** | **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE`**：**Historical / Retired candidate（P1-B B1）**；前四步全 MOCK；主验收已迁至 **C-35 / C-48 / C-42**。 |
| **C-31.1** | skeleton **trace 口径** | 前四步 **`targetTool=mock_*_hydrated_adapter`**，trace **`usedTools`** **不含**生产 **`revenue_query`** 等，避免误读为已真实执行。 |
| **C-32** | **Revenue real** | **Historical / Retired（P1-A）** — 原 `PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE`；由 **C-35** ALL_REAL / **C-48** GROUP strict case 覆盖。 |
| **C-33** | **Revenue + Purchase real** | **`…_REVENUE_PURCHASE_CORE`**：**`revenue_query`** + **`purchase_overview`**。 |
| **C-34** | **+ StockReduce real** | **`…_REVENUE_PURCHASE_STOCK_CORE`**：再接入 **`stock_reduce_query`**。 |
| **C-35** | **四域 real，诊断/建议 mock** | **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`**：前四步四生产 Tool；**`step_diagnosis_compose` / `step_recommendation`** 仍为 **`mock_*`**。 |
| **C-36** | **AnswerPlan 设计** | **`BusinessDiagnosisCompositeAnswerPlan`** 字段模型：四域 summary、**`diagnosisSignals`**、**`dataCoverage`**、**`riskLevel`** 等；**不**接 LLM 终稿。 |
| **C-37** | **DTO + deterministic compose** | **`BusinessDiagnosisCompositeAnswerPlanBuilder`**：从 trace 等价物 + 上下文 **`AiRunState`** 物化 Plan；**`step_diagnosis_compose`** 确定性聚合。 |
| **C-38** | **Summary 映射增强** | 优先 AnswerPlan / overview，其次 **`toolResults`** 与生产 Tool 形状对齐；缺字段 **null / 空列表**，禁止 **0** 冒充未知。 |
| **C-38.2** | **0 vs unknown** | 出库 / 菜品标量以 Tool payload **key 存在性**为准；**real zero** vs **missing** 写入 **`debug.mappingNotes`**（`phase=C-38.2_zero_vs_missing`）。 |
| **C-39** | **最小 `diagnosisSignals`** | 基于 summary + **`dataCoverage`** 的保守规则（跨域对比、负毛利率、覆盖缺口等）；**`revenueWeakSignal`** 恒 **`null`**；**`riskLevel`** 见 AnswerPlan 文档 §8.8。 |
| **C-40** | **`summaryText`** | 确定性中文短摘要（**非** LLM 终稿）；Harness 根 **`businessDiagnosisSummaryText`**；**`mappingNotes.summaryPhase=C-40_deterministic_zh`**（键语义 **不变**）；**`BusinessDiagnosisCompositeAnswerPlan.builderVersion`** 递进到 **`C-49`**（**C-49** 文档收口；见 **`business-diagnosis-answer-plan-design.md` §8.7**、`business-diagnosis-composite-group-design.md` §10）。 |

---

## 2. 当前主验收 caseId（curl / Replay，P1-B）

**Composite strict（PlannerExecutor 短路）**

| caseId | 说明 |
|--------|------|
| **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`** | C-35 STORE |
| **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** | C-48 GROUP |
| **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE`** | C-42 出库降级 |

**GRAPH 矩阵**

| caseId | 说明 |
|--------|------|
| **`DISH_PROFIT_MATRIX_P1`** | 菜品四轮下钻矩阵 |

---

## 2.1 最终已验收 caseId 详情（C-35 示例）

**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`**（**C-35**）

- **GraphCase**：`AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase`  
- **诚实性根字段**：**`plannerCompositeHonesty=COMPOSITE_ALL_DATA_REAL_DIAGNOSIS_MOCK`**  
- **`harnessReplayMode`**：**`PLANNER_EXECUTOR_MOCK`**（P1-B Final：已摘除单域 Adapter 专用 replayMode）

---

## 3. 最终数据与产出链路

```
revenue_query
  → DailyRevenueAnswerPlan / toolResults[revenue_query]
purchase_overview
  → PurchaseAnswerPlan / toolResults[purchase_overview]
stock_reduce_query
  → StockReduceAnswerPlan / toolResults[stock_reduce_query]
dish_profit_analysis
  → DishProfitAnswerPlan / toolResults[dish_profit_analysis]
        ↓
step_diagnosis_compose（MockPlannerStepExecutor 仅 trace 诚实；实际物化由 CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor + BusinessDiagnosisCompositeAnswerPlanBuilder）
        ↓
BusinessDiagnosisCompositeAnswerPlan
  ├── 四域 summary（revenue / purchase / stockReduce / dishProfit）
  ├── dataCoverage（每域 success / realToolInvoked / …）
  ├── diagnosisSignals（C-39 最小规则）
  ├── riskLevel
  ├── keyFindings
  ├── suggestedNextQuestions（C-40 合并模板问法）
  ├── summaryText（C-40 确定性中文）
  └── debug（builderVersion / mappingNotes：C-38.2 + C-39 + C-40 phase）
        ↓
step_recommendation → 仍为 mock（mock_build_recommendation_plan）
```

---

## 4. C-40 curl 观测结果（验收快照）

以下为一轮 **ALL_REAL_CORE** Replay / curl 下 **根摘要与 AnswerPlan** 的典型观测（具体数字依环境 DB；原则与字段名不变）：

| 观测项 | 典型值 / 说明 |
|--------|----------------|
| **overallStatus** | **SUCCESS** |
| **finalAnswerPlanType** | **BUSINESS_DIAGNOSIS_COMPOSITE** |
| **builderVersion** | **C-40** |
| **debug.mappingNotes.phase** | **C-38.2_zero_vs_missing** |
| **debug.mappingNotes.signalsPhase** | **C-39_minimal_deterministic** |
| **debug.mappingNotes.summaryPhase** | **C-40_deterministic_zh** |
| **riskLevel** | **NORMAL_OBSERVATION**（四域成功、无 WARNING 级信号时） |
| **dataCoverage** | 四域 **success=true** 且 **realToolInvoked=true** |
| **summaryText** | 已生成；仅拼接既有字段；**不**宣称「经营正常」 |
| **recommendation** | **mock**（trace **`mock_build_recommendation_plan`**；无真实 action） |
| **diagnosisSignals** | 典型环境下不误报：采购额未高于营收、出库 0 不当异常、0 销 0 成 0 毛利率不误报「毛利低」 |
| Harness 根（可选） | **businessDiagnosisSummaryText**、**businessDiagnosisSuggestedNextQuestions** 与 DTO 同源 |

---

## 5. 当前限制（C-41 如实记录）

| 限制 | 说明 |
|------|------|
| **范围** | 主要验证 **STORE 单店**（文档/Harness 常以 **AAA**、**departmentId=1** 为场景）。 |
| **GROUP** | **C-43 设计已落地**（**[`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md)**）；**未**做 **Harness / Java** 端到端验收（**C-44**）。 |
| **Master** | **未**接 **MasterBusinessAgent** 生产主链路。 |
| **前台** | **未**接 UI。 |
| **LLM** | Builder / Compose **未**调 LLM。 |
| **recommendation** | **仍为 mock**；无真实 action。 |
| **诊断规则** | **保守、确定性**；无双基期「营收偏弱」等（**`revenueWeakSignal`** 恒 **`null`**）。 |
| **能力缺口** | **未**做指定菜品深挖、排行专项、跨门店对比产品化、同比/环比（除非将来 summary 字段扩展）。 |

---

## 6. 关键设计原则（须保持）

1. **不新写 SQL**：业务数来自 **既有 Tool**（**`revenue_query`** / **`purchase_overview`** / **`stock_reduce_query`** / **`dish_profit_analysis`**）。  
2. **Adapter 不解析 userMessage**：Composite 层 **不**新增用户原文 **contains/regex** 路由。  
3. **trace 诚实**：**真实 `usedTools`**（生产 Tool id）与 **mock `usedTools`**（**`mock_diagnosis_compose`** 等）**显式区分**（C-31.1 精神延续）。  
4. **0 与 unknown**：**禁止**用 **0** 冒充 **missing**；**C-38.2** 映射 notes 必须可审计。  
5. **缺数据不编造**：summary **null** / 空表与降级口径一致。  
6. **summaryText**：**不得**下「经营正常 / 没有问题」式结论；可写「未触发确定性异常信号」并排除等价误读（见 C-40 实现约定）。

---

## 7. 下一阶段建议路线

| 建议编号 | 主题 | 方向 |
|:--------:|------|------|
| **C-42** | Composite **degrade** 专项（**首案已落地**） | 单点出库降级：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE`** — **`INSUFFICIENT_DATA`**、**`dataIncompleteSignal`**、**`summaryText`** 明示出库未完整读取；见 **`business-diagnosis-composite-plan-design.md` §2.5**。 |
| **C-43** | **GROUP 多门店（仅设计）** | caseId **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`**：**`scopeType=GROUP`**、**`visibleStores`（AAA `id=1`、汀兰餐厅 `id=3`）**、四域 Tool **GROUP 矩阵待实测**、**`summaryText` 多店措辞**、**禁止单店 fallback 冒充集团** — 权威 **`business-diagnosis-composite-group-design.md`**；**不**实现 Java。 |
| **C-44** | **Composer 只读 AnswerPlan** | 生产 Composer **只消费** **`BusinessDiagnosisCompositeAnswerPlan`**（含 **summaryText**），**不**扫原始大 JSON。 |
| **C-45** | **前台展示契约** | 字段映射、降级展示、与 **riskLevel / signals** 对齐的 UI 文案边界。 |
| **C-46** | **LLM 解释（可选）** | 是否及如何在 **结构化 Plan 之上** 生成自然语言层；须保持 **Plan 为事实源**、**LLM 不反向改数**。 |

---

## 8. 文档索引

| 文档 | 用途 |
|------|------|
| [`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md) | C-30～C-35 caseId、六步、诚实性、Bridge |
| [`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md) | DTO、§8 C-37～C-43（含 §8.11 GROUP 口径） |
| [`planner-executor-v1-design.md`](./planner-executor-v1-design.md) | Executor、Replay、§27 Composite 总述 |
| [`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md) | **C-43** GROUP **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`**（仅设计） |

**文档版本**：**C-41** — PlannerExecutor Composite **C-30～C-40 阶段总收口**（**仅文档**）。**C-42** **出库降级 Harness** 实装见 **`business-diagnosis-composite-plan-design.md` §2.5**。**C-43** **GROUP 多店 Composite 仅设计**见 **`business-diagnosis-composite-group-design.md`**（**未**注册 caseId / **未**实现 Java）。
