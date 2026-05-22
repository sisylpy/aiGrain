# Composite 经营诊断 — Readonly Composer（`BusinessDiagnosisCompositeAnswerPlan`）

> **读者**：Harness / Composer 工程师。  
> **现网**：**`com.nongxinle.ai.planner.BusinessDiagnosisCompositeReadonlyComposer`**（`COMPOSER_VERSION=C-51_READONLY_COMPOSER`）已落地；**只读** **`BusinessDiagnosisCompositeAnswerPlan`**，**不**解析 **`toolResults`** / 原始 Tool payload，**不**另起炉灶做诊断，**不调 LLM**。  
> **生产终稿**：普通 **`/api/ai/runs`** 仍以 **semantic contract + 各域 AnswerPlan + `StubAnswerComposerNode`** 为准；Composite Readonly Composer 用于 **Harness `GRAPH_RUN` / `HARNESS_ONLY` / `SHADOW` 观测**（**不替换** `finalAnswerText`，见 **[`business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md)**）。  
> **权威 DTO**：**[`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md)**。  
> **编排**：**[`planner-executor-v1-design.md`](./planner-executor-v1-design.md)**、**[`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md)**。

---

## 1. Composer 在 Harness 架构中的位置

```text
AiResolvedQueryContext（唯一解析入口，冻结）
  → PlannerExecutor：固定六步 Composite 计划 + 四域 Adapter / Tool
  → BusinessDiagnosisCompositeAnswerPlanBuilder（C-37～C-49）：确定性物化 Composite AnswerPlan
  → 【C-50/C-51：Composite 专用 Composer】只读 Composite AnswerPlan → 终稿结构与文案
```

- **上游**：**`BusinessDiagnosisCompositeAnswerPlan`** 已由 Builder 从 **各域 AnswerPlan / overview** 与 **诚实的 `dataCoverage`** 物化；**事实与诊断信号**在此闭合。  
- **本 Composer 段**：处于 **「结构化计划已冻结」之后**；**不**承担再规划、**不**再调 Tool、**不**修改 **`PlannerExecutionPlan`**。  
- **与 Master / 前台**：生产 **`finalAnswerText`** 不由本 Composer 写入；**SHADOW** 仅旁路产出 **`compositeFinalAnswerText`** 等观测字段。未来若 **PRIMARY** 切换策略，须 **仍**以 AnswerPlan 为唯一事实源。

---

## 2. 为什么 Composer 只能读 AnswerPlan，不能重读 `toolResults`

| 原因 | 说明 |
|------|------|
| **单一事实源** | **`BusinessDiagnosisCompositeAnswerPlan`** 已是 **四域归并 + `dataCoverage` + `diagnosisSignals` + `riskLevel` + `summaryText`** 的 **审计闭包**；再读 **`toolResults`** 会引入 **第二套解析路径**，易与 Builder 的 **0 / missing** 规则不一致。 |
| **重复解析 = 隐性再诊断** | 从原始 JSON **重抽数字** 等价于 **绕过** C-38.2 / C-39 / C-40 的确定性约定，可能 **复活**「未知当 0」「缺域仍下结论」等问题。 |
| **可 Replay** | Harness Replay 已能校验 **AnswerPlan + trace**；Composer 只读 Plan 使 **终稿 diff** 与 **Builder 版本**（如 **`BUILDER_VERSION=C-49`**）对齐。 |
| **职责边界** | **`toolResults`** 的规范化属于 **Adapter / Builder**；Composer 只做 **呈现与结构组织**（及未来 **LLM 润色**，见 §9）。 |

**结论**：Composer **禁止**读取 **`AiRunState.toolResults`**、禁止 **按 Tool id 再解析 payload**；**禁止**为「补充一句话」而自己 **重算** 诊断或金额。

---

## 3. Composer 输入

### 3.1 必填：`BusinessDiagnosisCompositeAnswerPlan`

#### 3.1.1 权威字段（叙事与合规的主输入）

Composer **必须**仅基于下列字段组织用户可见叙事（**不得**用它们之外的来源 **编造事实**）：

| 字段 | 用途 |
|------|------|
| **`type`** | 路由/模板判别（固定 **`BUSINESS_DIAGNOSIS_COMPOSITE`**） |
| **`scopeLabel`** | 组织范围措辞 **必须与** Builder 一致（STORE / GROUP 见 §6） |
| **`timeLabel`** | 时间窗展示 |
| **`summaryText`** | **确定性**短摘要；可作为 **终稿主段落** 或 **核心段落基底** |
| **`riskLevel`** | 总体风险档位；**`INSUFFICIENT_DATA`** 时 **禁止**确定性断言「经营结论」 |
| **`dataCoverage`** | 每域是否成功、是否真实 Tool、**`stepId` / `degradedReason`**；降级叙事 **必须**与此一致 |
| **`diagnosisSignals`** | 结构化信号（含 **`severity` / `reason` / `evidenceRefs`**）；**禁止** Composer 自创与 signals 矛盾的业务判断 |
| **`keyFindings`** | 短句级发现；可映射为 **`answerBlocks` / `sections` 的要点列表 |
| **`suggestedNextQuestions`** | **原样或有序**曝光给用户；**不得**用 LLM 生成 **与事实冲突** 的追问 |

#### 3.1.2 `debug.mappingNotes`（含 `phase` / `signalsPhase` / `summaryPhase`）

- **仅供**开发/调试/Replay 对齐；**禁止**直接拼接进 **用户可见 `finalAnswerText`**（除非产品明确要「调试模式」且与本项目当前范围无关）。

#### 3.1.3 可选扩展（**C-51 实现时**）：四域 `*Summary` **只读展示**

- **C-50 闭包**以 **§3.1.1** 为准。  
- **若** 终稿需要 **表格化四域数字**（金额、毛利率等），**C-51** 可评审增加 **仅**对 **`revenueSummary` / `purchaseSummary` / `stockReduceSummary` / `dishProfitSummary` 的只读引用**（数值 **必须**与 Builder 写入一致），**仍禁止**触碰 **`toolResults`**。

### 3.2 可选：`plannerExecutorTrace` **摘要**

- **允许**只读 **高层摘要**：如 **`overallStatus`**、**`degradedSteps`**、**`finalAnswerPlanType`**、**诚实性根字段**（**`plannerCompositeHonesty` / `plannerCompositeNote`**）等（与 Harness 根摘要同构）。  
- **用途**：调试块、内部日志、**不**与用户可见结论 **矛盾** 时的「执行过程说明」（产品可开关）。  
- **禁止**：为绕开 AnswerPlan 而从 trace **重解析** **step payload / tool dump**。

### 3.3 用户问题 `userMessage`（**仅措辞参考**）

- **允许**：语序调整、礼貌用语、**在不说谎前提下** 切分段落。  
- **禁止**：凭用户措辞 **重新判断** 门店范围、时间窗、**是否**缺数据、**是否**「正常」；这些 **仅以 AnswerPlan 为准**。

---

## 4. Composer 输出

| 输出 | 说明 |
|------|------|
| **`finalAnswerText`** | 用户可见 **完整中文**（或由 sections 拼接）；**事实**不超出 **§3.1.1**（及 C-51 允许的 **`*Summary` 展示**） |
| **`answerBlocks` 或 `sections`** | 结构化区块（如：**范围与时间**、**数据覆盖说明**、**摘要**、**要点**、**信号说明**、**建议追问**）；每块 **应可回溯**到 AnswerPlan 字段 |
| **`suggestedNextQuestions`** | 与输入 **一致或为其有序子集/去重**；**不**新增 **无 AnswerPlan 依据** 的追问 |
| **`debug`（可选）** | `composerVersion`、`sourceBuilderVersion`、`traceRef`、块级 `sourceField` 指针；**不**默认展示给终端用户 |

**与 `summaryText` 关系**：允许 **`finalAnswerText`** **以 `summaryText` 为核** 增删连接词与小标题；**不允许**引入 **新数字、新域结论**。

---

## 5. STORE 与 GROUP 的回答差异（口径）

| 维度 | STORE（如 **ALL_REAL**） | GROUP（**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`**） |
|------|--------------------------|------------------------------------------------------------------------|
| **主语** | 可使用 **「本门店」**、用户在 **`scopeLabel`** 中的单店名（如 **AAA**） | 必须使用 **`scopeLabel`** 中的 **多店/集团** 措辞：**「当前可见门店」**、**「全部门店」**、或 **显式枚举**（如 **AAA、汀兰餐厅**）；**禁止** **默认** **「AAA 在……」** 作为 **集团汇总** 全文主语 |
| **数值含义** | 单店或 Builder 标定的 STORE 合计 | **禁止** 把 **单店金额/排行** **写成了** **集团口径**；**`dataCoverage`** 与 **`revenueSummary.storeRows`** 等 **须与 GROUP 语义一致** |
| **信源** | 同左 | 同左；**详细** 规则见 **`business-diagnosis-composite-group-design.md` §7** |

---

## 6. 降级场景回答

| 条件 | 回答要求 |
|------|-----------|
| **某域 `dataCoverage.success=false`**（或 **等价** Harness 出库未读） | **必须** **明示该域「未完整读取 / 不可用」**；**禁止** **宣称四域均已读取**；**禁止** 用其他域 **臆造** 该域数字 |
| **`riskLevel=INSUFFICIENT_DATA`** | **禁止** **「经营正常」「问题已确定」** 等 **强结论**；**应** 引导 **补数据 / 看 `suggestedNextQuestions`** |
| **`stockReduceSummary=null` 等** | **禁止** 写 **无来源的 0** 冒充出库/核销 |
| **`unknown` vs 数值** | **禁止**把 **未知 / 缺失** 表述为 **0** 或 **具体排名** |

与 **C-42** 对齐：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE`** — **`summaryText` 已含** 出库/核销未完整读取时，Composer **不得弱化或删除**该语义。

---

## 7. 禁止话术与禁止行为（Composer）

| 禁止项 | 说明 |
|--------|------|
| **「经营正常 / 没问题 / 一切良好」** | 与 C-40 **`summaryText`** 约束一致；可用 **「当前未触发确定性异常信号」** 类 **保守** 表述 **且** **不**与 **`dataIncompleteSignal`** 矛盾 |
| **无依据排行** | **禁止** **Top N**、**「第几名」** 除非 **`keyFindings` / `*Summary` / `summaryText`** 已提供 **可核对** 依据 |
| **把 recommendation mock 当真实智能建议** | **`step_recommendation`** 仍为 **mock**；若终稿含建议块，须 **标注占位/模板** 或 **不输出** mock 内容（产品策略在 C-51 固化） |
| **改写金额、毛利率、门店范围** | **逐字**遵从 AnswerPlan 已物化字段；**禁止** **换算错误**、**禁止** **合并口径** 导致语义漂移 |
| **把 `debug.mappingNotes` 当用户文案** | 见 §3.1.2 |
| **读取 `toolResults` / 原始 Tool JSON** | 见 §2 |
| **基于用户原文做事实路由** | **禁止** **contains/regex** **新开**事实判断（见项目冻结约束） |

---

## 8. 后续 LLM（远期，仅原则）

- **若**接入 LLM：**仅**允许 **在 §3.1.1 已锁定事实** 上做 **表达润色**（连接词、分段、语气）。  
- **禁止** LLM **修改金额/毛利率/覆盖状态/门店范围**，**禁止** **引入 AnswerPlan 中不存在** 的断言。  
- **事实校验**：润色后 **仍能** 机械对照 **`BusinessDiagnosisCompositeAnswerPlan`** 逐项核对。

---

## 9. C-51 实装（Java skeleton）

| 项 | 说明 |
|----|------|
| **类** | **`com.nongxinle.ai.planner.BusinessDiagnosisCompositeReadonlyComposer`**，`COMPOSER_VERSION = C-51_READONLY_COMPOSER` |
| **入参** | **`BusinessDiagnosisCompositeAnswerPlan`**（**仅此**；**不**收 `toolResults` / `AiRunState`） |
| **出参** | **`com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeComposeResult`**（`finalAnswerText`、`suggestedNextQuestions`、`riskLevel`、`scopeLabel`、`timeLabel`、`answerPlanType`、`debug`） |
| **规则摘要** | **`finalAnswerText`** 优先 **`plan.summaryText`**；空则 **`scopeLabel` + `timeLabel` + `riskLevel` + `keyFindings`** 保守拼装，**不编造数字**；**`dataCoverage`** 有 **`success=false`** 且正文尚未体现不完整读时 **追加**说明句；**`suggestedNextQuestions`** 去重截断、**不**当真实智能建议；**`debug.mappingNotes`** **不**入正文 |
| **Harness** | **`AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase#toHarnessSummary`** 与 **`AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase#toHarnessSummary`** 增加 **`businessDiagnosisFinalAnswerText`**、**`businessDiagnosisComposerVersion`**（**仅观测**；**不**接 Master / 前台） |

---

## 10. C-52 生产入口 Composite Gate（已实装）

真实聊天主链路在 **`AiRunService#startRun`** 写入 **`businessDiagnosisCompositeGateResult`**（**`BusinessDiagnosisCompositeProductionGate`**）；**只读**结构化 intent/path/scope/time，**禁止**用户原文 **`contains`/`regex`**。**规则与 intent 映射表**见 **[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md)** §3.3。

---

## 11. 参考索引

| 文档 | 用途 |
|------|------|
| [`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md) | DTO、§8 Builder 行为 |
| [`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md) | caseId、六步、C-49 |
| [`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md) | GROUP **`scopeLabel` / `summaryText`** |
| [`planner-executor-v1-design.md`](./planner-executor-v1-design.md) | 流水线位置、§27 |
| [`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md) | **C-52** 生产入口 Gate（**`BusinessDiagnosisCompositeProductionGate`**） |

**文档版本**：**`BusinessDiagnosisCompositeReadonlyComposer`** + **`BusinessDiagnosisCompositeComposeResult`** 已实装；Harness 摘要 **`businessDiagnosisFinalAnswerText`** / **`businessDiagnosisComposerVersion`**。
