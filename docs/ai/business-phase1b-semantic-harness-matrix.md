# 经营类阶段 1B：语义层最小矩阵（Harness / RESOLVED_CONTEXT_ONLY）

> **目的**：为「经营概览 / 经营诊断 / 营业额专线 / 多店对比 / 时间继承 / 跨域接力」建立 **仅解析层（Resolver + TurnMemory + 摘要）** 的可重复验收口径。  
> **语义契约（D-CLEAN-V1）**：**V2-only** — 断言 `effectivePathCode` / `canonicalStructuredIntentDetailWire` / `semanticSlots`；**不**验 Java 关键词 fallback 或已删 Normalizer。  
> **本阶段不验**：Tool 行、SQL、`AnswerPlan` 行集、Composer、前台展示。  
> **Registry**：经营类 **没有** `business.*` / `revenue.*` / `diagnosis.*` capability；`matchedCapabilityId` **预期为 null**（勿设 `matchedCapabilityIdExpected`，除非刻意验采购误配）。

**阶段 1B 验收边界（再次明确）**：Harness **`BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT`** 在 **`dryRunStage=RESOLVED_CONTEXT_ONLY`** 下 **只验收语义层**（`effectiveIntentCode` / `effectivePathCode` / canonical wire / 时间窗与 v2 动作 / scope 探针等摘要字段）。**不验收**：业务 Tool 执行结果行、任意业务数据 SQL、Composer 输出、前端展示。若需验 Tool rows 或 SQL，应使用 **图跑（FULL）** 或其它阶段矩阵，而非 1B 本文件范围。

---

## 1. Harness 用法（推荐）

| 配置 | 值 | 说明 |
|------|-----|------|
| `dryRunStage` | `RESOLVED_CONTEXT_ONLY` | 强制只跑 Resolver + `AiHarnessResolvedContextSummarizer.summarize(ctx, conversationId)`，**不**进入同步业务图（见 `AiHarnessReplayService`）。 |
| `replayMode` | `GRAPH_RUN` 或默认 | 在 `RESOLVED_CONTEXT_ONLY` 下 **不会**执行图；可任意，以团队习惯为准。 |
| `strictStoreSqlMatch` | `false`（推荐） | 多店名用例依赖环境与 `visibleStores` 解析，避免因部门树差异失败。 |
| `frozenClockDate` | 固定 `yyyy-MM-dd` | 稳定「本月至今 / 上月」区间；与现有 `AiHarnessBuiltinCases.LocalDateAnchor` 文档一致。 |
| `messages` | 各条用例列出的中文问句 | 须与线上 v2 语义输入契约一致（含 User JSON 包装由 Harness 入口处理）。 |

**摘要键与 `AiHarnessReplayExpectedRound` 字段对应（常用）**：

| 预期对象字段 | 摘要 `Map` 键 |
|----------------|---------------|
| `effectiveIntentCode` | `effectiveIntentCode` |
| `effectivePathCode` | `effectivePathCode` |
| `canonicalStructuredIntentDetailWire` | `canonicalStructuredIntentDetailWire`（来自 `queryIntent.structuredIntentDetail` 的 canonical；若仅槽位有 wire 而 intent 未写满，该键可能为空，**宜同时用** `structuredIntentDetail` 预期对齐 `structuredIntentDetailWire` 摘要键） |
| `semanticSlotQueryObject` 等 | `queryObject` / `operation` / `metric` / `sourceFacet` / `anchorPolicy` |
| v2 时间动作 | `querySemanticV2TimeActionExpected` → 摘要 `querySemanticV2TimeAction` |
| v2 时间 `timeSource`（嵌套） | 摘要 `querySemanticV2.time.timeSource`（探针/人工看 JSON；`AiHarnessReplayExpectedRound` 无专用字段时可 **`ignoreExpectations: true`** 单轮肉眼看） |
| 时间窗来源 | `effectiveTimeWindowSource` / `effectiveTimeWindowSourceAnyOf` / `effectiveTimeWindowSourceNoneOf` |
| 店名（合并后） | `querySemanticEffectiveMentionedStoreNames` |
| 编排（v2 候选并 resolver 采纳） | `orchestrationTaskModeExpected` → `orchestrationTaskMode` |
| 营业额计划类型 | `revenueAnswerPlanPlanType` → `revenueAnswerPlanType`；**RESOLVED_CONTEXT_ONLY 下该键通常为空** — `AiHarnessExpectationComparator` 对 **`REVENUE_STORE_AMOUNT_RANKING`** 支持用 `effectivePathCode` + **wire** 探针兜底；对 `REVENUE_OVERVIEW` **无**兜底，见下条。 |
| 诊断计划类型 | 无 Graph 时 **`diagnosisPlanType` 一般为空**；1B 用 **intent + path + wire +（可选）`orchestrationTaskMode`** 作 **归属代理断言**。 |

**建议的「目标 AnswerPlan 归属」在 RESOLVED_CONTEXT_ONLY 下的落地方式**：

- **`DailyRevenueAnswerPlan`**：`REVENUE_OVERVIEW` + `revenue_overview_path` + wire `revenue_*`；若必须写 `revenueAnswerPlanPlanType`，需 **FULL 图跑** 或接受「仅排行子类有 comparator 兜底」的现状。  
- **`BusinessOverviewAnswerPlan`（MultiAgent）**：`BUSINESS_OVERVIEW` + `business_overview_path` + **`orchestrationTaskMode=MULTI_AGENT`**（及常见 `orchestrationMultiAgentRequired=true`，摘要里可查）。  
- **`DiagnosisPlan` / 诊断 MultiAgent 壳**：`BUSINESS_DIAGNOSIS` + `business_diagnosis_path` + 对应诊断类 wire；同属 **intent/path/wire 代理**。

---

## 2. 内置 `caseId`（已注册）

| `caseId` | 含义 |
|----------------|------|
| **`BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT`** | 单文件 13 轮：`messagesBusinessSemantic1bResolvedContext()` 与内置 `expectationsBusinessSemantic1bResolvedContext` 对齐 R01–R10（含 R08–R10 多轮）。服务端对本案 **`caseId` 默认 `dryRunStage=RESOLVED_CONTEXT_ONLY`**（仍建议请求体显式写明）。常量见 `AiHarnessBuiltinCases#BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT`。 |
| `BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT_R01` … `_R10` | 每条单轮独立 case（可选拆分策略）；**当前仓库未拆**，失败定位用内置 case + 轮次号。 |

**与现有 case 的关系（可参考，不等价）**：

- `BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`：**GRAPH_RUN** 向，覆盖「本月经营 → 上个月 → 双店」，断言更重。  
- `BUSINESS_DIAGNOSIS_V1_CORE_3`：文档称 Resolver replay + 探针，场景偏诊断链。  
- `REVENUE_AGENT_GRAPH_CORE`：营业额 **图跑**。  

1B 矩阵 **刻意** 使用 `RESOLVED_CONTEXT_ONLY`，与上述 **默认图跑** case 互补。

---

## 3. 最小矩阵：逐条 expected 说明

以下 **expected** 均指向 `AiHarnessReplayExpectedRound` 的语义；日期请按 `LocalDateAnchor` 与 `frozenClockDate` 替换占位符。

**共同（除非另述）**：

- **不要**设置 `matchedCapabilityIdExpected`。  
- **`scopeType`**：集团无店名 → `GROUP`；双店名 → `GROUP` + `querySemanticEffectiveMentionedStoreNames` 含两字面（**仅当环境可见店名解析稳定时**断言，否则仅验 `visibleStoreRootCountMin` ≥ 2）。  
- **semanticSlots**：经营/营业额场景 **LLM 可能不吐满槽位**；若 `queryObject` 等为 null，**不将 1B 判失败** — 仅当摘要中 **已出现** 槽位时再与下表「建议槽位」对齐（可把槽位预期留空仅作文档黄金值）。

---

### R01 — 经营概览

| 项目 | 值 |
|------|-----|
| 用户句 | 这个月经营得怎么样？ |
| `effectiveIntentCode` | `BUSINESS_OVERVIEW` |
| `effectivePathCode` | `business_overview_path` |
| `structuredIntentDetail` 或 `structuredIntentDetailAnyOf` | `business_overview_summary` **或** `business_overview_status`（`primaryMetric` 含 `BUSINESS_STATUS` / `OPERATION_STATUS` 等时 merge 倾向后者） |
| `canonicalStructuredIntentDetailWire` | 与上栏 canonical 一致；若使用 `AnyOf`，则以 `structuredIntentDetailAnyOf` 为主断言 |
| `orchestrationTaskModeExpected` | `MULTI_AGENT`（v2 硬规则；与现网 prompt 一致） |
| 目标计划归属（代理） | **BusinessOverviewAnswerPlan**（MultiAgent）：意图 + path + MULTI_AGENT |

**建议槽位（黄金值，可选）**：`queryObject=STORE`，`operation=SUMMARY` 或 `DIAGNOSIS`，`metric` 含 `BUSINESS` / `STATUS` 族；以线上 v2 示例为准，**不作为硬门禁**。

---

### R02 — 经营诊断（综述）

| 项目 | 值 |
|------|-----|
| 用户句 | 这个月整体有什么风险？ |
| `effectiveIntentCode` | `BUSINESS_DIAGNOSIS` |
| `effectivePathCode` | `business_diagnosis_path` |
| `structuredIntentDetail` | `business_diagnosis_summary`（path 缺省时 `normalizePathsLikeKeywordResolver` 亦会补齐同类口径） |
| 目标计划归属（代理） | **DiagnosisPlan**（`TYPE_OVERALL_BUSINESS_DIAGNOSIS`）+ **BUSINESS_DIAGNOSIS_MULTI_AGENT** 编排意图；1B 仅验 **BUSINESS_DIAGNOSIS** + wire |

---

### R03 — 门店优先关注排序

| 项目 | 值 |
|------|-----|
| 用户句 | 哪个门店最需要关注？ |
| `effectiveIntentCode` | `BUSINESS_DIAGNOSIS` |
| `effectivePathCode` | `business_diagnosis_path` |
| `structuredIntentDetail` 或 `canonicalStructuredIntentDetailWire` | **`store_priority_ranking`**（LLM 若写 `store_risk_ranking`，Lexicon canonical 仍为 `store_priority_ranking`） |
| 目标计划归属（代理） | **DiagnosisPlan**（门店优先序 **语义**）；**不验** 排序结果、不验 `focusRows` |

---

### R04 — 营业额概览

| 项目 | 值 |
|------|-----|
| 用户句 | 这个月营业额怎么样？ |
| `effectiveIntentCode` | `REVENUE_OVERVIEW` |
| `effectivePathCode` | `revenue_overview_path` |
| `structuredIntentDetail` | `revenue_overview_summary` |
| `revenueAnswerPlanPlanType` | **`REVENUE_OVERVIEW`** 在 **RESOLVED_CONTEXT_ONLY** 下 **可能无法断言**（comparator 无 path+wire 兜底）→ **1B 以 intent+path+wire 为主**；若需强验 planType，改用 **FULL 图跑** |

---

### R05 — 门店营业额排行

| 项目 | 值 |
|------|-----|
| 用户句 | 哪个门店营业额最高？ |
| `effectiveIntentCode` | `REVENUE_OVERVIEW` |
| `effectivePathCode` | `revenue_overview_path` |
| `structuredIntentDetail` | `revenue_store_amount_ranking` |
| `revenueAnswerPlanPlanType` | `REVENUE_STORE_AMOUNT_RANKING` — **RESOLVED_CONTEXT_ONLY 下 comparator 可用 path+wire 探针通过**（见 `AiHarnessExpectationComparator.assertRevenueAnswerPlanPlanType`） |

---

### R06 — 双店综合经营对比（**按现有 merge 代码的实际分叉**）

| 项目 | 值 |
|------|-----|
| 用户句 | AAA 和汀兰餐厅哪个经营情况好？ |
| **路径 A（默认）** | LLM：`BUSINESS_OVERVIEW` + `business_overview_path`，点名 ≥2 店 → `applyBusinessStoreStatusCompareWhenMultiStoreMentioned` → wire **`business_store_status_compare`**；**不落** `REVENUE_OVERVIEW`，除非走「 degraded to revenue」异常路径（摘要可能出现 `degradedBusinessCompareByRevenue`）。 |
| **路径 B（升格）** | 在上述基础上，若 `metric.primaryMetric` 为 **`BUSINESS_STATUS_COMPARE_DIAGNOSIS` / `BUSINESS_STORE_COMPARE_DIAGNOSIS` / `COMPARE_WITH_REASON`**（大小写归一后）→ `remapBusinessOverviewCompareToBusinessDiagnosisWhenMetricSignals` → **`BUSINESS_DIAGNOSIS`** + `business_diagnosis_path` + wire **`business_store_status_compare_diagnosis`**。 |
| **建议断言写法** | **`effectiveIntentCodeAnyOf`**：`BUSINESS_OVERVIEW` **与** `BUSINESS_DIAGNOSIS`；**`structuredIntentDetailAnyOf`**：`business_store_status_compare`、`business_store_status_compare_diagnosis` — **不要硬改 Java**，以 **AnyOf** 反映真实 merge。 |
| 目标计划归属（代理） | 路径 A：**BusinessOverviewAnswerPlan**（多店综合四域）；路径 B：**DiagnosisPlan**（并排归因诊断） |

---

### R07 — 双店营业额对比

| 项目 | 值 |
|------|-----|
| 用户句 | AAA 和汀兰餐厅哪个营业额高？ |
| `effectiveIntentCode` | `REVENUE_OVERVIEW` |
| `effectivePathCode` | `revenue_overview_path` |
| `structuredIntentDetail` | `revenue_store_amount_ranking`（≥2 店 + 营收口径时 `applyRevenueStoreAmountRankingWhenMultiStoreMentioned`） |
| `querySemanticEffectiveMentionedStoreNames` | 含 `AAA`、`汀兰餐厅`（视环境匹配） |
| `revenueAnswerPlanPlanType` | `REVENUE_STORE_AMOUNT_RANKING`（RESOLVED_CONTEXT 下可用 path+wire 兜底） |

---

### R08 — 时间继承（经营概览）

| 轮次 | 用户句 | 期望（第二轮） |
|------|--------|----------------|
| 1 | 这个月经营得怎么样？ | `BUSINESS_OVERVIEW` / `business_overview_path` / `business_overview_summary` 或 `business_overview_status`；时间：本月至今 |
| 2 | 那上个月呢？ | **仍** `BUSINESS_OVERVIEW` / `business_overview_path`；wire 同上类；**`querySemanticV2TimeActionExpected`**：`OVERRIDE` 或 **`NEW`**（与 v2 输出一致即可）；**时间窗**：上月首尾日与 `LocalDateAnchor` 一致；**`effectiveTimeWindowSource`**：`SEMANTIC_EXPLICIT` / `TIME_SHIFT` 等 **允许的**来源之一 — 使用 **`effectiveTimeWindowSourceNoneOf`** 排除误把整轮标成 **纯默认本月** 的来源（按你们环境收紧，例如排除未继承的 `DEFAULT_MONTH_TO_DATE` **单独**作为错误信号需谨慎，以摘要 `startDate`/`endDate` 为准更稳） |

**推荐强断言**：第二轮 **`startDate`/`endDate`** = `anchor.previousMonthFirstDay()` / `previousMonthLastDay()`；**`effectiveIntentCodeNoneOf`** 不包含 `REVENUE_OVERVIEW`。

---

### R09 — 跨域接力：经营 → 采购

| 轮次 | 用户句 | 期望（第二轮） |
|------|--------|----------------|
| 1 | 这个月经营得怎么样？ | 同 R01 |
| 2 | 那采购呢？ | `effectiveIntentCode`：`PURCHASE_OVERVIEW`；`effectivePathCode`：`purchase_overview_path`；**时间**继承上轮（`INHERITED_PREVIOUS` 或等价 `startDate`/`endDate` 与第一轮一致）；**scope** `GROUP` 继承（`effectiveScopeSource` 含继承类来源时更稳）；**`structuredIntentDetail`**：`purchase_overview_summary`（normalize 默认）；**`matchedCapabilityId`**：null |

---

### R10 — 跨域接力：经营 → 出库

| 轮次 | 用户句 | 期望（第二轮） |
|------|--------|----------------|
| 1 | 这个月经营得怎么样？ | 同 R01 |
| 2 | 那出库呢？ | `effectiveIntentCode`：**`STOCK_REDUCE_QUERY`**；`effectivePathCode`：**`stock_reduce_query_path`**；时间与 scope 继承原则同 R09；**wire 主断言**：**`structuredIntentDetailWire` / `structuredIntentDetail`**（常见 **`stock_reduce_overview`**）；`metric.rankingType` 仅 **debug/deprecated** — **不断言 Tool / SQL / rows** |

---

## 4. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-19 | 初版：1B 经营类 RESOLVED_CONTEXT_ONLY 矩阵与 Harness 字段对照；R06 按 `AiQuerySemanticLlmMergeHelper` 路径 A/B 说明。 |
| 2026-05-19 | 注册内置 `caseId` **`BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT`**（`AiHarnessBuiltinCases` + `AiHarnessReplayService#resolveExpectations`）；消息见 `messagesBusinessSemantic1bResolvedContext()`。 |
| 2026-05-19 | **收口**：内置 case **`BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT`** 回归 **`overallPass=true`**，**13 轮全部 pass**；验收条件 **`frozenClockDate=2026-05-19`**、**`dryRunStage=RESOLVED_CONTEXT_ONLY`**（与同会话链路一致）。 |
| 2026-05-19 | **本次语义 / 期望修复摘要**（实现见当周 Java 提交，本文件仅作文档记录）：（1）**R03**：`BUSINESS_DIAGNOSIS` + `store_priority_ranking` 不再被采购侧 **`CurrentSemanticFrameValidator`** 误伤；（2）**全流程第 13 轮**（文档 **R10** 之第二轮「那出库呢？」）：`stockReduceType=ALL` 不再覆盖 **`stock_reduce_overview`** wire；（3）**R03 / R05 / R06 / R07 / R08 第一轮**：`effectiveTimeWindowSourceAnyOf` 允许 **`INHERITED_PREVIOUS`**，与同会话内「本月」已建立、本句无新时间时的继承行为一致。 |

---

## 5. 下一阶段

**阶段 1C（规划）**：在延续「仅语义层 / RESOLVED_CONTEXT_ONLY 或等价轻量 Harness」的前提下，进入 **出库 / 核销 / 废弃 / 损失** 等子口径的 **语义层矩阵**（v2 wire、`metric`、跨轮继承与跨域接力与原表风格对齐）。**不**在 1C 文档中要求验 Tool 结果行、业务 SQL 或 Composer；若需端到端断言，单列阶段或 case 族。
