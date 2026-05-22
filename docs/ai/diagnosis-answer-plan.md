# DiagnosisPlan：经营诊断 Harness 设计（阶段一 · 文档定稿）

> **状态**：阶段二骨架已落地（`DiagnosisPlanBuilder` + `StubOutcomeReviewNode`）；**现网 `business_diagnosis_path` 主链**见下文 **§0b**。  
> **读者**：接手餐饮 AI 多智能体 Harness 的工程师。  
> **关联**：`docs/ai/harness-composer-architecture.md`、`docs/ai/business-overview-diagnosis-domain-capability-matrix.md`、`docs/AI_MAINLINE_INDEX.md`。

---

## 0b. 现网三条诊断相关链（2026-05-20 · P2 审计）

### 1. 成本诊断 `cost_diagnosis_path`（现网主链）

`BusinessDataPlannerNode` → `DEFAULT_COST_INSIGHT_TOOLS`（四 Tool：`revenue_query` → `purchase_overview` → `stock_reduce_query` → `dish_profit_analysis`）→ `BusinessToolExecutionNode` → `StubOutcomeReviewNode` → **`CostDiagnosisAgentNode`** + **`CostMarginDerivation`**（**无** `gross_margin_calculator` Tool；**无** classic business overview）。

### 2. 经营诊断 `business_diagnosis_path`（现网主链）

`BusinessDataPlannerNode#applyBusinessDiagnosisBranch`（权限裁剪 tools，可与 **MULTI_AGENT** 共用 `MasterBusinessAgent` 四域编排）→ 各域 `*AnswerPlanBuilder` → `StubOutcomeReviewNode` → **`DiagnosisPlanBuilder.attachIfApplicable`** → **`BusinessDiagnosisAgentV1.enrich`**（仅 `businessDiagnosisPath`）→ `StubAnswerComposerNode` / **`DiagnosisDeterministicRenderer`**。

### 3. Composite `BusinessDiagnosisComposite*`（非用户正文主链）

`BusinessDiagnosisCompositeProductionGate`（观测写入 `AiRunState`，**不改**路由）→ `BusinessDiagnosisCompositeExecutionService`：

| 模式 | 用途 |
|------|------|
| **HARNESS_ONLY** | Harness `GRAPH_RUN` 同步跑完后旁路 |
| **SHADOW** | 普通 Run 图完成后旁路；**不写** `finalAnswerText` |
| **PRIMARY** | **未接**现网 |

PlannerExecutor GraphCase（`AiPlannerExecutorBusinessDiagnosisComposite*`）仅验收 Composite，**不是** `/api/ai/runs` 默认 Graph。

---

## 0. DiagnosisPlan 的定位（Harness 职责）

**DiagnosisPlan** 是「经营诊断」问法下的 **中间计划层**，与单域 **AnswerPlan**（采购 / 出库 / 毛利 / 营收）处于 **同一思想层**：服务端完成 **选事实、分级、引用出处**；**Composer 只负责自然语言表达**，不重新计算、不重新排序、不从原始 Tool 大段重扫数据。

与单域链路的区别在于 **输入侧**：

| 层级 | 职责 |
|------|------|
| 各域 **AnswerPlan** | 在 **各自 Tool 结果** 上完成选数、排行、口径、focusRows |
| **DiagnosisPlanBuilder** | **只读** 本轮（或编排得到的）**Purchase / StockReduce / DishProfit / DailyRevenue** 的 AnswerPlan（及其已确认字段），做 **跨域聚合视图** |
| **DiagnosisPlan** | 承载诊断结构：**结论摘要、核心发现、证据行、风险、建议动作、debug** |
| **DiagnosisComposer** | 只读 **DiagnosisPlan**（+ 必要时只读与计划绑定的 scope/time 文案），**禁止**绕过 AnswerPlan 从 `toolResults` 重算 |

**原则一句话**：经营诊断的**业务事实**必须能追溯到 **某条已有 AnswerPlan** 的 `planType` 与字段；不允许 Composer「凭感觉」把四个 Tool 的原始 JSON 拼成诊断报告。

---

## 1. 为什么 DiagnosisPlan 必须读取已有 AnswerPlan？

1. **单一事实来源**：排行、合计、分型口径已在各 **Builder** 内与 Tool 对齐；若诊断层再读原始 Tool，会出现 **重复排序、重复汇总、口径漂移**。  
2. **可测试、可复盘**：Harness 验收依赖「planType + focusRows + summary」；诊断若直接绑 Tool，Replay 无法稳定比对「诊断结论 ↔ 子计划」。  
3. **冻结四条主线**：采购 / 出库 / 毛利 / 营收链路 **禁止** 为诊断需求反向改版；诊断只能 **消费** 其输出。  
4. **Composer 边界**：与 `harness-composer-architecture.md` 一致——**Composer 不心算**；诊断层若把计算留给 Composer，必然违反架构。

---

## 2. 阶段一读取哪些 AnswerPlan？各自提供哪些可诊断事实？

阶段一 Builder 的 **权威输入** 为以下四类（均已在仓库内有 DTO + Builder + 文档）：

### 2.1 `PurchaseAnswerPlan`

| planType（摘录） | 可诊断事实（来自 `summary` / `focusRows` / `secondaryRows`，不另算） |
|------------------|----------------------------------------------------------------------|
| `PURCHASE_OVERVIEW` | 采购总金额、总笔数、结构中自采 / 供货商等已选事实 |
| `PURCHASE_SELF_OVERVIEW` / `PURCHASE_SUPPLIER_OVERVIEW` | 自采或供货商侧聚焦金额与结构 |
| `PURCHASE_GOODS_AMOUNT_RANKING` / `PURCHASE_GOODS_COUNT_RANKING` | 商品采购金额 / 次数排行（顺序以 plan 为准） |
| `PURCHASE_SUPPLIER_AMOUNT_RANKING` | 供货商金额排行 |

字段结构见：`docs/ai/purchase-answer-plan.md`、`PurchaseAnswerPlan.java`。

### 2.2 `StockReduceAnswerPlan`

| planType（摘录） | 可诊断事实 |
|------------------|------------|
| `STOCK_REDUCE_OVERVIEW` | 出库总览及分型汇总 |
| `STOCK_REDUCE_PRODUCTION_OVERVIEW` | 生产耗用（type1） |
| `STOCK_REDUCE_OUTPUT_OVERVIEW` | 出品（与文档一致的 reduceType） |
| `STOCK_REDUCE_WASTE_OVERVIEW` / `STOCK_REDUCE_LOSS_OVERVIEW` / `STOCK_REDUCE_RETURN_OVERVIEW` | **废弃 type2 / 损耗·报损 type3 / 退货 type4**（措辞与出库文档一致，禁止混称） |
| `STOCK_REDUCE_GOODS_AMOUNT_RANKING` / `STOCK_REDUCE_GOODS_COUNT_RANKING` | 商品出库金额 / 次数排行 |

字段结构见：`docs/ai/stock-reduce-answer-plan.md`、`StockReduceAnswerPlan.java`。

### 2.3 `DishProfitAnswerPlan`

| planType（摘录） | 可诊断事实 |
|------------------|------------|
| `DISH_LOWEST_MARGIN` | 低毛利菜品（focusRows 已定序截断） |
| `DISH_HIGHEST_ACTUAL_COST` | 高实际成本菜品 |
| `DISH_PROFIT_REASON` | 利润原因类关切 |
| `DISH_THEORETICAL_COST` / `DISH_ACTUAL_OUTBOUND_COST` | 理论 vs 实际出库成本叙事锚点 |
| `DISH_PROFIT_RATE` | 综合毛利率等已选字段 |
| `DISH_COST_GAP` | 成本差异关切 |

**禁止**：诊断层重新计算毛利率、成本差；一律引用 plan 内数值与 `buildInsight` 对齐字段。  
字段结构见：`docs/ai/dish-profit-answer-plan.md`、`DishProfitAnswerPlan.java`。

### 2.4 `DailyRevenueAnswerPlan`

| planType（摘录） | 可诊断事实 |
|------------------|------------|
| `REVENUE_OVERVIEW` | 总营业额 |
| `REVENUE_DINE_IN_OVERVIEW` / `REVENUE_TAKEOUT_OVERVIEW` | 堂食 / 外卖 |
| `REVENUE_CHANNEL_BREAKDOWN` | 堂食 + 外卖拆分（结构占比可在此层 **只做引用展示**，阈值规则属阶段二） |
| `REVENUE_STORE_AMOUNT_RANKING` / `REVENUE_DAILY_AMOUNT_RANKING` | 门店 / 单日金额高低（日期是否可述以 `revenue-answer-plan.md` §11 为准） |
| `REVENUE_ORDER_COUNT_OVERVIEW` / `REVENUE_CUSTOMER_COUNT_OVERVIEW` / `REVENUE_AVERAGE_ORDER_VALUE` | 订单数、顾客数、客单价 |
| `REVENUE_PLATFORM_RANKING` | **预留**；无平台明细时不作为可靠诊断锚点 |

已知限制（外卖无平台分列等）：**`docs/ai/revenue-answer-plan.md` §11**。

---

## 3. DiagnosisPlan 建议字段结构（阶段一契约）

以下为 **JSON / DTO 设计建议**；落地语言可为 Java，字段名可按项目驼峰习惯映射。**阶段一不写业务代码**，仅锁定语义。

| 字段 | 类型建议 | 含义 |
|------|----------|------|
| `type` | `string` | 诊断类型枚举（见 §4） |
| `scopeLabel` | `string` | 范围展示文案（与用户语义一致，来自 Context / Plan） |
| `timeLabel` | `string` | 时间展示文案 |
| `diagnosisLevel` | `string` | 总级严重程度：`NORMAL` / `NOTICE` / `WARNING` / `RISK`（全项目统一一套即可） |
| `summary` | `string` | **一句话**诊断结论（可由规则模板拼装，**阶段三**前禁止依赖 LLM 自由生成） |
| `focusFindings` | `array<object>` | **1～3 条**最重要发现（见 §5.1） |
| `evidenceRows` | `array<object>` | **证据行**：仅承载 AnswerPlan 已确认事实（见 §5.2） |
| `riskRows` | `array<object>` | 风险项（见 §5.3） |
| `actionSuggestions` | `array<object>` | 建议动作（见 §5.4） |
| `debug` | `object` | **可复盘**调试块（见 §6） |

可选扩展：`dataCompleteness`（各域 `OK` / `PARTIAL` / `MISSING` / `FAILED`）、`usedSourcePlans`。新建链路 **优先** 把等价信息收进 `debug`，避免 Composer 依赖过多顶层键。

### 3.1 与历史 `BusinessDiagnosisPlan` 的关系（Historical removed）

**P2（2026-05-20）**：`BusinessDiagnosisPlan` / `BusinessDiagnosisPlanBuilder` / `BusinessDiagnosisPlanNode` 已从 `src/main` 删除；现网统一为 **`DiagnosisPlan`** + **`DiagnosisPlanBuilder`** + **`BusinessDiagnosisAgentV1.enrich`**。详见 `docs/AI_MAINLINE_INDEX.md`。

**P3 Harness 键（2026-05-20）**：Replay / `GET …/runs` 摘要以 **`diagnosisPlan` / `diagnosisPlanExists` / `diagnosisPlanType`** 为准；**`businessDiagnosisPlanExists`**、**`harnessReplayBusinessDiagnosisPlanType`** 等为 **deprecated compat**（与 `diagnosisPlan*` 同义镜像），不代表旧 DTO。

历史上 **`BusinessDiagnosisPlan`**（字段如 `mainFindings`、`riskItems`、`sourceTools` 等）契约偏 **Tool 摘要**，已由 AnswerPlan 聚合层替代。  
**阶段一产品契约**以 **本文 §3～§5** 为准；后续 Java 收口时可 **演化 DTO 或增加适配映射**，**不在**「仅文档」任务中改代码。

---

## 4. 阶段一支持的 `type`（诊断类型）建议

阶段一 **只做类型枚举与字段占位**，**不做**复杂规则引擎。建议类型：

| `type` | 说明 | 阶段一产出 |
|--------|------|------------|
| `OVERALL_BUSINESS_DIAGNOSIS` | 综合经营诊断（跨四域聚合摘要占位） | 可仅输出 `summary` + `evidenceRows` 罗列事实 + debug |
| `REVENUE_DIAGNOSIS` | 营收侧 | 依赖 `DailyRevenueAnswerPlan` 可用 planType |
| `PURCHASE_DIAGNOSIS` | 采购侧 | 依赖 `PurchaseAnswerPlan` |
| `STOCK_REDUCE_DIAGNOSIS` | 出库 / 核销 / 损耗结构 | 依赖 `StockReduceAnswerPlan` |
| `DISH_PROFIT_DIAGNOSIS` | 菜品毛利 | 依赖 `DishProfitAnswerPlan` |
| `COST_DIAGNOSIS` | 成本结构（跨采购 + 出库 + 毛利 **引用**，不新算） | 仅当有至少两域 AnswerPlan 时拼证据行；无数则不硬答 |

**阶段一默认推荐**：实现路径优先 **`OVERALL_BUSINESS_DIAGNOSIS`** 的 **「只读汇总 + 证据表」**；分域类型留作路由扩展点。

---

## 5. `focusFindings` / `evidenceRows` / `riskRows` / `actionSuggestions` 字段约定

### 5.1 `focusFindings`（核心发现）

每条建议结构：

| 键 | 说明 |
|----|------|
| `findingType` | 稳定机器可读码，如 `LOW_TAKEOUT_SHARE`、`HIGH_SCRAP_AMOUNT`（具体枚举阶段二沉淀） |
| `title` | 短标题 |
| `metric` | 可选；指标逻辑名 |
| `value` | 可选；**必须为 AnswerPlan 中已有数或占比** |
| `level` | `NOTICE` / `WARNING` / `RISK` |
| `sourcePlan` | **必填**；`PurchaseAnswerPlan` \| `StockReduceAnswerPlan` \| `DishProfitAnswerPlan` \| `DailyRevenueAnswerPlan` |
| `sourcePlanType` | 对应子 plan 的 `planType` 常量 |

**阶段一**：允许 `focusFindings` 为空，仅列 `evidenceRows`。

### 5.2 `evidenceRows`（证据行）

**只放**子 AnswerPlan 已出现的量和标签，供 Composer 宣读与 Replay 对齐。

| 键 | 说明 |
|----|------|
| `sourcePlan` | 四类之一（字符串类名或稳定短码 `PURCHASE` / `STOCK_REDUCE` / `DISH_PROFIT` / `REVENUE`） |
| `planType` | 子计划 type，如 `REVENUE_CHANNEL_BREAKDOWN` |
| `label` | 人可读维度名，如「堂食营业额」 |
| `value` | 数字或已格式化的展示串（**来自子 plan**） |
| `optionalNotes` | 可选；如营收侧「无平台明细」类 **已知限制** 提示 |

示例：

```json
{
  "sourcePlan": "DailyRevenueAnswerPlan",
  "planType": "REVENUE_CHANNEL_BREAKDOWN",
  "label": "堂食营业额",
  "value": 4614
}
```

### 5.3 `riskRows`（风险项）

| 键 | 说明 |
|----|------|
| `riskType` | 如 `HIGH_WASTE`、`LOW_MARGIN`、`DATA_INCOMPLETE` |
| `sourcePlan` | 风险所依据的计划来源 |
| `level` | `WARNING` / `RISK` 等 |
| `message` | 短句；**须有 evidenceRows 或 focusFindings 可追溯** |

**阶段一**：可无 `riskRows`，或仅 `DATA_INCOMPLETE`（某域 AnswerPlan 缺失）。

### 5.4 `actionSuggestions`（建议动作）

| 键 | 说明 |
|----|------|
| `actionType` | 如 `CHECK_LOW_PROFIT_DISHES`、`REVIEW_SCRAP_PROCESS` |
| `title` | 短标题 |
| `priority` | `HIGH` / `MEDIUM` / `LOW` |

**阶段一可为空**；若填充，应来自 **规则模板**，非 LLM 创作。

---

## 6. Debug / Replay 应展示的内容

`debug`（及 Summarizer 顶层摘要）**至少**包含：

| 项 | 说明 |
|----|------|
| `consumedAnswerPlans` | 本轮 **实际读取到的** 子计划类型列表（类名 + `planType`） |
| `missingAnswerPlans` | 期望有但缺失的域及原因（未跑该 path / Tool 失败 / 空数据） |
| `findingProvenance` | 每条 `focusFindings[*]` 的 `sourcePlan` + `sourcePlanType` |
| `fallbackUsed` | 是否退回旧 summary / 经营概览口述；**默认 false；禁止 fallback 主导** |
| `undiagnosableReason` | 若无法诊断（四域皆空），人可读原因 |
| `resolvedQueryContextRef` | 可选；`queryScopeKind`、`timeWindow` 摘要键（与 `AiHarnessResolvedContextSummarizer` 对齐，不重复发明范围语义） |

**禁止**：破坏现有 Harness Debug 既有字段；新增键应 **附加**，不与旧客户端假设冲突。

---

## 7. Composer 如何读取 DiagnosisPlan

1. **主输入**：序列化后的 **DiagnosisPlan**（或等价 `answer_delta` 字段，实施阶段定名）。  
2. **宣读顺序建议**：`summary` → `focusFindings`（若有）→ `riskRows` → `evidenceRows`（可并入正文或「依据如下」列表）→ `actionSuggestions`。  
3. **硬约束**：  
   - **不**根据原始 `toolResults` 重算占比、排行、排行顺序。  
   - **不**引入未出现在 `evidenceRows` / 子 AnswerPlan 中的数字或门店 / 菜名。  
   - 出库分型 **文案**与 `stock-reduce-answer-plan.md` 一致。  
4. **多轮继承**：时间 / 门店 / 话题继承由 **Resolver / 会话记忆** 完成；Composer **不推测**继承关系。

---

## 8. 阶段一明确不做（冻结与排除）

| 不做项 | 说明 |
|--------|------|
| 新增复杂 SQL / Mapper | 诊断不直接查库 |
| 改写四条主线 | 不改 `PurchaseAnswerPlan*`、`StockReduceAnswerPlan*`、`DishProfitAnswerPlan*`、`DailyRevenueAnswerPlan*` 及其 Builder、主 Tool、主 Composer 分支 |
| Composer 从 ToolResult 直接拼诊断 | 只能经由 DiagnosisPlan / 子 AnswerPlan |
| LLM 自由生成诊断结论 | 阶段三再议；阶段一仅模板或空 |
| 完整经营顾问 / 大报告 | 阶段一仅结构与证据聚合 |
| 前端 UI / SSE / API 大改 | 后续迭代单列 |
| 以旧 summary 为主回答 | 旧 summary **仅兜底**，且须在 debug 标记 `fallbackUsed` |

---

## 9. 后续路线（阶段二～四）

| 阶段 | 内容 |
|------|------|
| **一（当前）** | 设计文档 + 字段约定 + Debug 清单；**不**改业务代码 |
| **二** | 规则诊断：阈值、环比（配置化）、`findingType` / `riskType` 枚举固化 |
| **三** | **可选** LLM 仅润色 `summary` / 建议语气；**须**锁定「不可改数、不可改排序」 |
| **四** | 前台诊断卡片、与 `answer_delta` 契约对齐（另起 API 文档变更） |

---

## 11. 验收检查清单（阶段二起实现时用）

- [ ] 每条 DiagnosisPlan 能否在 Replay 中 **点击追溯到** 子 `planType` 与字段？  
- [ ] Composer 是否 **完全避免** 从原始 Tool 重算？  
- [ ] 四域 AnswerPlan **缺失** 时是否 **降级说明** 而非编造？  
- [ ] `debug.fallbackUsed` 是否为真时仍 **不占主导**？  
- [ ] 是否 **未** 修改采购 / 出库 / 毛利 / 营收冻结代码路径？

---

**文档版本**：v1.0（2026-05-12，阶段一设计定稿，仅文档）
