# DishProfit / 菜品毛利域 — 能力矩阵与语义梳理（只读）

> **范围**：本轮仅做代码与 prompt 契约梳理，**不**改 Java、MergeHelper、Tool、SQL、Planner、Composer、Gate、Shadow、PRIMARY、test。  
> **关联文档**：`docs/ai/dish-profit-answer-plan.md`、`docs/ai/dish-profit-planner-adapter-design.md`、`docs/gb-dish-cost-analysis-frontend.md`。

---

## 1. Intent / Path / Wire 常量摘要

### 1.1 主 intent 与 path（Java）

| 常量 | 值 | 说明 |
|------|-----|------|
| `AiResolvedQueryIntent.DISH_PROFIT` | `DISH_PROFIT` | 菜品毛利/成本透视主 intent。 |
| `AiResolvedQueryIntent.PATH_DISH_PROFIT` | `dish_profit_path` | 菜品专线有效路径（Planner 分支与 `AiRunState#dishProfitPath` 对齐）。 |

### 1.2 结构化子意图 wire（`AiQuerySemanticLexicon` — 与 `structuredIntentDetail` / canonical 对齐）

**概览与单菜**

| Wire 常量 | 字面量 |
|-----------|--------|
| `STRUCTURED_DISH_PROFIT_OVERVIEW` | `dish_profit_overview` |
| `STRUCTURED_DISH_GROSS_MARGIN_QUERY` | `dish_gross_margin_query` |
| `STRUCTURED_DISH_THEORETICAL_COST` | `dish_theoretical_cost` |
| `STRUCTURED_DISH_ACTUAL_OUTBOUND_COST` | `dish_actual_outbound_cost` |
| `STRUCTURED_DISH_COST_GAP` | `dish_cost_gap` |
| `STRUCTURED_DISH_LOW_PROFIT_REASON` | `dish_low_profit_reason` |

**排行类（现网：V2 `semanticSlots.structuredIntentDetailWire` → merge → `structuredIntentDetail`；LLM 可填 `metric.rankingType` 作 **debug/deprecated**，服务端不以其写 wire）**

| LLM 常见输出（v2 表格） | canonical 归一后（示例） |
|------------------------|--------------------------|
| `dish_gross_profit_rate_ranking_low` | `dish_profit_ranking_low_margin` |
| `dish_gross_profit_rate_ranking_high` | `dish_profit_ranking_high_margin` |
| `dish_profit_ranking_high_profit_amount` | `dish_profit_ranking_high_profit_amount` |
| `dish_profit_ranking_low_profit_amount` | `dish_profit_ranking_low_profit_amount` |
| `dish_actual_cost_ranking_high`（及别名） | `dish_actual_cost_ranking_high` |
| `dish_actual_cost_ranking_low` | `dish_actual_cost_ranking_low` |
| `dish_theoretical_cost_ranking_high` / `_low` | 同名字面量 |
| `dish_gap_ranking_max` | `dish_gap_ranking_max` |
| `dish_sales_ranking` | `dish_sales_ranking` |

**诊断 / 复合场景用 planType（非独立业务 path）**

- `DishProfitAnswerPlan.TYPE_BUSINESS_DIAGNOSIS_DISH_OVERVIEW`、`TYPE_AGGREGATED_DISH_PORTFOLIO_FALLBACK`：在 `business_diagnosis_path` / `business_overview_path` / `dish_profit_path` 上，结构化子意图**未**挂载专用 AnswerPlan 时，由 `DishProfitAgentNode#maybeAttachPortfolioAggregatePlan` 从 Overview 档位派生。

### 1.3 编排层 Agent / Tool 名称（prompt 与文档）

- **Agent**：`DishProfitAgent`（`query_semantic_parser.v2.md` 与 `master-business-agent-design.md`）。  
- **Tool ID**：`dish_profit_analysis`（`AiBusinessToolIds.DISH_PROFIT_ANALYSIS`）。

**注意**：**`dish_profit_analysis`** 同时服务 **菜品毛利专线**（`dish_profit_path`）、**D-8 销量专线**（`dish_sales_query_path`，语义 intent 仍为 **`DISH_SALES_QUERY`**）与 **成本诊断链**（`cost_diagnosis_path`，`DEFAULT_COST_INSIGHT_TOOLS` 第 4 步）。**Historical removed（D-CLEAN-DISH-SALES-P2）**：独立 Tool **`dish_sales_query`** / **`DishSalesQueryTool`** 已删除；**不再**编排或读取 `toolResults["dish_sales_query"]`。标价收入读 **`data.businessInsightSummary.totalActualRevenue`**。见下文 §4。

---

## 2. `query_semantic_parser` v2 与典型问法映射

> **D-CLEAN-V1**：`query_semantic_parser.v1.md` 已从生产 prompt 目录删除；下表「v1 / v2 要点」列中 v1 描述仅作 Git 历史对照，现行契约以 **v2 + `semantic-output-schema.md`** 为准。

下列为用户列举问法在 **prompt 正文**中的定义方式（LLM 侧常同时输出 **`semanticSlots` + `metric.rankingType`**）。**服务端主链**仅经 **slots wire / `structuredIntentDetail`** 落到 canonical wire（**D-1X-D3**）；勿按「仅填 rankingType 即可落 wire」实现 Java。

| 用户问法（示例） | v1 / v2 要点 | 备注 |
|------------------|--------------|------|
| **上个月哪个菜毛利率最低？** | `intent=DISH_PROFIT`；时间 `LAST_MONTH`（或等价起止）；排行：`metric.rankingType=dish_gross_profit_rate_ranking_low`。 | v2 **「菜品毛利」专节表格**明确该行映射；canonical → `dish_profit_ranking_low_margin`。 |
| **核桃芽菜西芹毛利怎么样？** | `intent=DISH_PROFIT`；`mentionedDishName` 填菜名；**不要**输出 `dish_actual_cost_ranking_*`；`metric.rankingType=null`。 | v2：服务端落 `dish_gross_margin_query` 类单菜口径；承接排行榜后点菜名时**强制** `metricAction=OVERRIDE` 且 `rankingType=null`。 |
| **哪个菜利润最高 / 最挣钱 / 挣的钱最多？** | Intake/V2 选 **`dish_profit.ranking_high_profit_amount`** → wire **`dish_profit_ranking_high_profit_amount`** → **`GROSS_PROFIT_AMOUNT`** → AnswerPlan **`DISH_HIGHEST_PROFIT_AMOUNT`**，`sortKey=grossProfitAmount`（元）。 | **与毛利率排行互斥**；勿选 `ranking_high_margin`。 |
| **哪个菜毛利率最高 / 利润率最高？** | **`dish_profit.ranking_high_margin`** → **`dish_profit_ranking_high_margin`** → **`GROSS_MARGIN`** → **`DISH_HIGHEST_MARGIN`**，`sortKey=blendedGrossMarginRateOnListPrice`（%）。 | **保留**；合同 selectionHints 明确 **拒绝** 利润/最挣钱类问法。 |
| **哪个菜成本最高？** | v1：**DISH_PROFIT** + `dish_actual_cost_ranking_high`（勿标 `COST_DIAGNOSIS`）；v2 表格「实际成本最高」。 | 与门店/部门成本诊断（`COST_DIAGNOSIS`）区分。 |
| **哪个菜销量最高？** | 走 **`DISH_SALES_QUERY` / `dish_sales_query_path`** + **`DishSalesAnswerPlan`**（数据 **`dish_profit_analysis`**）。 | v2 须在 **`semanticSlots.structuredIntentDetailWire`** 给出销量排行 wire；**不**依赖服务端读 `metric.rankingType` 定 planType。 |
| **哪个菜毛利异常？** | v1 `metric` 枚举中列出 `dish_low_profit_reason`；**无**单独「anomaly」字面 wire。 | 运行态：`DishProfitAgentNode` 对 `abnormalDishes` 有启发式筛选；**全域「异常排行榜」**无与采购异常同级的封闭 `rankingType` 专节。 |
| **哪个菜原料成本变化大？** | **近义**：理论 vs 实际差额最大 → `dish_gap_ranking_max`（v2 表「理论/实际」落差排行）。 | **原料采购价环比/历史波动**未在菜品毛利 v2 专节定义，更可能落入 **采购域**或需多域证据；当前梳理**不**延伸臆造 wire。 |

---

## 3. 真实 Tool 与实现入口

| 项 | 内容 |
|----|------|
| **类** | `com.nongxinle.ai.tool.business.DishProfitAnalysisTool` |
| **Tool ID** | `dish_profit_analysis` |
| **数据口径** | 直接复用 `GbDepFoodBusinessInsightService#buildInsight`（与 legacy 透视一致）。 |
| **结构化参数** | 工具参数含 `dishProfitStructuredDetail`（`ARG_DISH_PROFIT_STRUCTURED_DETAIL`）等，由 `DishProfitQueryToolExecutor#buildDishProfitAnalysisToolArgs` 组装。 |
| **行级排序** | `applyDishProfitPresentation` 按 wire 对 `dishRows` 排序：低/高毛利率、实际成本、gap、**销量**（`STRUCTURED_DISH_SALES_RANKING`）等。 |

---

## 4. `BusinessDataPlannerNode` 与 `dataPlanTools`

当 **`effectivePathCode`（或回退 `queryIntent.pathCode`）** 为 **`dish_profit_path`** 且非「需语义澄清」状态时：

- 置 `state.setDishProfitPath(true)`；
- **`dataPlanTools`** = `new ArrayList<>(AiBusinessToolIds.DEFAULT_DISH_PROFIT_TOOLS)`；
- **`DEFAULT_DISH_PROFIT_TOOLS`** = **`["dish_profit_analysis"]`**（单元素列表）。

**经营诊断** `business_diagnosis_path`：在权限满足时**额外**加入 `dish_profit_analysis`（与采购、出库并列），见 `mayDishProfitToolForDiagnosis`。

---

## 5. `BusinessToolExecutionNode` 与 `toolResults` key

- 按 `dataPlanTools` 顺序执行；对 `dish_profit_analysis` 走 **`ToolRegistry`** → `DishProfitAnalysisTool#execute`。
- 成功后用 **`state.getToolResults().put(toolId, payload)`**，故 **key 为字面量 `dish_profit_analysis`**（与 `AiBusinessToolIds.DISH_PROFIT_ANALYSIS` 一致）。
- **MasterBusinessAgent** 若已代为执行菜品工具，可能 **`legacyDishProfitSkipped`**，本节点循环内 **continue** 跳过重复执行，但 **toolResults 仍应已由 Master 路径写入**（与采购/出库对称）。
- **部门 ID 规则**：`departmentIdArgumentForTool` 对 `dish_profit_analysis` 使用 **buildInsight 部门**（`resolveBuildInsightDepartmentFatherId`）。

---

## 6. `DishProfitAnswerPlan` 与 `planType`

**存在**：`com.nongxinle.ai.dto.business.DishProfitAnswerPlan`，由 `DishProfitAgentNode#tryAttachDishProfitAnswerPlan` / `maybeAttachPortfolioAggregatePlan` 挂载。

**`planType` 取值（`DishProfitAnswerPlan` 常量）**：

| planType | 含义摘要 |
|----------|----------|
| `DISH_LOWEST_MARGIN` | 综合毛利率 **最低** 排行 Top（`blendedGrossMarginRateOnListPrice` ASC）。 |
| `DISH_HIGHEST_MARGIN` | 综合毛利率 **最高** 排行 Top（DESC）。 |
| `DISH_HIGHEST_ACTUAL_COST` | **实际成本**最高菜。 |
| `DISH_PROFIT_REASON` | 低毛利 **原因**（需 `dish_low_profit_reason` + 点名等条件）。 |
| `DISH_THEORETICAL_COST` | 单菜 **理论成本**。 |
| `DISH_ACTUAL_OUTBOUND_COST` | 单菜 **实际出库成本**。 |
| `DISH_PROFIT_RATE` | 单菜 **综合毛利率**（`dish_gross_margin_query` 等）。 |
| `DISH_COST_GAP` | 理论 vs 实际 **差额**（含 `dish_gap_ranking_max`）。 |
| `BUSINESS_DIAGNOSIS_DISH_OVERVIEW` | 经营诊断用菜品概览 fallback。 |
| `AGGREGATED_DISH_PORTFOLIO_FALLBACK` | 经营概览 / 菜品 path 聚合档位 fallback。 |

**Builder 挂载入口补充**：`DishProfitAnswerPlanBuilder.attachForAgentEnvelope` 供子 Agent 与 `computeOverviewAndAttachPlans` 同源挂载（与 `DishProfitAgentNode` 注释一致）。

---

## 7. `finalAnswer` 生成路径（`dish_profit_path`）

主入口：**`StubAnswerComposerNode`** — `state.isDishProfitPath()` 分支。

| 条件 | 行为 |
|------|------|
| 有 `DishProfitAnswerPlan` 且窄口径/确定性宣读条件满足 | **`composeDishProfitDeterministicFromAnswerPlan`**（只读 Plan，跳过 LLM）。 |
| 否则 | 调用 **`COMPOSER_DISH_PROFIT_V1`**，`pickLlmSanitized(llm, planAwareFallback)`。 |
| 无 Plan / 无 overview | `composeDishProfitNoPlanFallback`（**不**恢复 `renderDishProfitFallback` / `DishProfitDeterministicRenderer`）。 |

**确定性渲染**内部逻辑要点：

- 若存在 **`DishProfitAnswerPlan.focusRows`** 且属于「窄口径」类型，优先 **`composeDishProfitDeterministicFromAnswerPlan`**（宣读 plan 内字段，禁止心算替代 Tool 字段）。
- **综述类**使用 `AiDishProfitOverviewResult` 的 summary + 高/低毛利/不完整/异常列表段落。

**legacy**：本 path 以 **AnswerPlan + Deterministic Renderer +（可选）Composer LLM** 为主干；**不是**纯 LLM 无工具路径。

---

## 8. 能力矩阵（当前是否支持）

表中 **支持**指：具备 **path + tool +（通常）overview 或 answerPlan** 闭环；**部分**表示 tool/lexicon 有痕迹但 AnswerPlan/Composer/专节未完全对齐。

| 能力 | 状态 | 说明 |
|------|------|------|
| **菜品毛利总览** | **支持** | `dish_profit_overview` + `DishProfitAgentNode` 汇总句 + 档位列表；无专用 plan 时可聚合 fallback。 |
| **单菜毛利查询** | **支持** | `dish_gross_margin_query` + `TYPE_DISH_PROFIT_RATE`；工具点名收窄与多轮 `mentionedDishName` 继承。 |
| **菜品毛利率排行（低）** | **支持** | `dish_profit_ranking_low_margin` ↔ `TYPE_DISH_LOWEST_MARGIN`；Composer 窄口径走确定性。 |
| **菜品毛利率排行（高）** | **部分** | `TYPE_DISH_HIGHEST_MARGIN` 在 Agent 侧可挂载；**`StubAnswerComposerNode#dishProfitNarrowRankingOrReasonPlan` 未包含 `TYPE_DISH_HIGHEST_MARGIN`**，主流程更易走 **LLM Composer** 或长模板 fallback（与「最低毛利」不对称）。 |
| **菜品利润排行（绝对利润额）** | **弱 / 未闭环** | 解析层倾向 **毛利率** 排行；未见「按毛利金额」独立 wire 与 planType。 |
| **菜品成本排行（实际）** | **支持** | `dish_actual_cost_ranking_high` / low；`TYPE_DISH_HIGHEST_ACTUAL_COST` 等。 |
| **菜品成本排行（理论）** | **支持（tool + plan）** | Lexicon + `attachSingleDish…` / 排行逻辑在 toolchain 中存在；依赖解析器输出 `dish_theoretical_cost_ranking_*`。 |
| **菜品销量关联** | **部分** | Tool 层可按 `dish_sales_ranking` **排序**；D-8 / 成本链均执行 **`dish_profit_analysis`**，**不**编排 **`dish_sales_query`**；**无**与 `DISH_LOWEST_MARGIN` 同级的销量 AnswerPlan 类型。 |
| **菜品成本 / 毛利「异常」** | **部分** | Overview 层 `abnormalDishes`、`lowProfitDishes` 启发式；`dish_low_profit_reason` 偏 **点名解释**；无统一「异常榜」封闭契约。 |
| **理论 vs 实际成本变化（差额）** | **支持** | `dish_gap_ranking_max` → `TYPE_DISH_COST_GAP`。 |

---

## D-7 PROBE 执行链路评审：菜品毛利第一批

本节记录 D-7 PROBE 在 **DISH_PROFIT / dish_profit_path** 主链路上的第一批结论与根因假设（仅文档化，不作为已实现需求契约）。

### 1. 当前主链路（已存在）

`DISH_PROFIT` / `dish_profit_path` → `BusinessDataPlannerNode` → `dish_profit_analysis` → `DishProfitAnswerPlan` → `DishProfitDeterministicRenderer` / `COMPOSER_DISH_PROFIT_V1`。

### 2. PROBE 中已通过或基本通过的问题

- 「上个月哪个菜毛利率最低？」→ `dish_profit_ranking_low_margin` / `DISH_LOWEST_MARGIN`。
- 「核桃芽菜西芹毛利怎么样？」→ `dish_gross_margin_query` / `DISH_PROFIT_RATE`。
- 「AAA 店哪个菜毛利率最低？」→ `STORE` + `AAA` + `DISH_LOWEST_MARGIN`。
- 「哪个菜利润最高？」当前等价于 `dish_profit_ranking_high_margin` / `DISH_HIGHEST_MARGIN`，但需注明：**当前语义是「毛利率最高」，不是「毛利额最高」**。

### 3. 当前明显问题

- 「哪个菜成本最高？」错误落到 `dish_profit_ranking_high_margin` / `DISH_HIGHEST_MARGIN`；期望是 `dish_actual_cost_ranking_high` / `DISH_HIGHEST_ACTUAL_COST`。
- 「哪个菜销量最高？」出现 `intent` / `path` = `DISH_PROFIT`，但 `structuredIntentDetailWire` = `dish_actual_cost_ranking_high`，`orchestrationSelectedAgents` = `RevenueAgent`，`selectedTools` = `revenue_query`，属于 **intent/path、wire、agent/tool 三层不一致**。
- 「哪个菜原料成本变化大？」当前落到 `dish_actual_cost_ranking_high`；更合理的短期目标是 `dish_gap_ranking_max` / `TYPE_DISH_COST_GAP`。
- 「哪个菜毛利异常？」当前落到低毛利榜，可暂作 **低毛利风险** 处理，但**不等价于**真正异常检测。

### 4. 关键代码事实（梳理用）

- `AiQuerySemanticLexicon` 已有 `dish_profit_ranking_low_margin`、`dish_profit_ranking_high_margin`、`dish_actual_cost_ranking_high`、`dish_gap_ranking_max`、`dish_sales_ranking`、`dish_gross_margin_query` 等正式 wire。
- `canonical` 已将 `dish_gross_profit_rate_ranking_low` / `high` 归一到 `dish_profit_ranking_low_margin` / `high_margin`。
- 但 **`dish_profit_rate_ranking_low` / `high` 这类无 `gross` 别名未归一**。
- `DishProfitAnswerPlanBuilder` **不是**主要映射点；真正 **wire → planType** 映射主要在 **`DishProfitAgentNode#tryAttachDishProfitAnswerPlan`**。
- `DishProfitAnswerPlan` 已有 `TYPE_DISH_LOWEST_MARGIN`、`TYPE_DISH_HIGHEST_MARGIN`、`TYPE_DISH_HIGHEST_ACTUAL_COST`、`TYPE_DISH_COST_GAP`。
- **当前没有** `TYPE_DISH_SALES_RANKING`。

### 5. 「成本最高」误判根因（历史）

D-1X-B 前 `AiQuerySemanticV2DishProfitGate.sanitize` 曾把 `dish_actual_cost_ranking_high` + `PROFIT_MARGIN` 类 primaryMetric 改成毛利率排行 wire；**该类已删除**，现由 v2 semanticSlots + Validator 落地。

### 6. 「销量最高」混乱根因

v2 菜品排行白名单与销量 wire 归属仍在 prompt/schema 侧评审；**不再**经 Resolver Java Gate 拦截。

### 7. D-7 后续 Phase 建议

- **Phase 1**：基础毛利问法，已基本通过。
- **Phase 2**：成本 / gap / 原料成本变化，优先收口。
- **Phase 3**：**不再**把「哪个菜销量最高」等纯销量问法作为 DishProfit 长期能力承接；**转 D-8 DishSales**（见下文「D-7 与 D-8 边界」及 `dish-sales-domain-capability-matrix.md`）。
- **Phase 4**：毛利异常与「利润最高」语义，后续再定义 anomaly / 毛利额。

### 8. 最小改动建议（评审向）

优先从 **prompt / schema + Validator** 评审入手；**不要优先**改 Tool / SQL / Composer；**`DishProfitAnswerPlanBuilder` 不是当前主要受力点**。

### D-7 与 D-8 边界：菜品销量不作为 DishProfit 长期能力承接

1. **DishProfit 长期**只承接：菜品**毛利**、**成本**、**理论/实际成本差异**、**低利润原因**（及与之直接相关的单菜毛利、排行类子意图）。
2. 「哪个菜**销量**最高 / **卖得最多** / **销售份数**最多 / **销售额**最高」**不再**作为 DishProfit **长期主能力**；不要求在 DishProfit 文档或产品叙事中把纯销量排行列为核心交付。
3. **菜品销量**拆入 **D-8 DishSales / 菜品销售域**；独立路径、wire、AnswerPlan（若落地）与 DishProfit **分域**。
4. DishProfit **只**在「**销量 + 毛利**」**组合分析**中参与，例如「销量高但不赚钱」「销量高但毛利率低」等——此时 DishProfit 提供毛利/成本侧证据，销量侧由 D-8 或复用数据源协同表述（以最终实现为准）。
5. **短期**若 D-8 Phase 1 **复用** `dish_profit_analysis` 的 **`soldPortionsTotal` / `actualRevenue`**（及 `dishRows`）：须在对外说明中标明为 **数据源复用 / buildInsight 行内字段**，**不代表**产品域长期仍归 **DishProfit**。
6. **D-7 Phase 3** **不再**继续把「哪个菜销量最高」**修进** DishProfit；后续 **D-8** 统一处理纯销量/销售额排行问法。

---

## 9. 收口建议 Phase（对标采购 / 出库 / 库存文档化节奏）

以下仅为**规划建议**，实施时需遵守「不新写 SQL、优先既有 buildInsight / Tool」原则。

| Phase | 目标 | 建议动作（概念级） |
|-------|------|-------------------|
| **Phase 1** | **语义与文档对齐** | 固化为本文档 + `dish-profit-answer-plan.md` 单一事实来源；列出 **v2 表格缺口**（销量榜、利润额榜、异常榜）。 |
| **Phase 2** | **Composer / AnswerPlan 对称性** | 将 **`TYPE_DISH_HIGHEST_MARGIN`** 纳入与 `TYPE_DISH_LOWEST_MARGIN` 相同的「窄口径确定性」分支，避免高低毛利用户体感不一致。 |
| **Phase 3** | **纯销量 / 销售额排行** | **划交 D-8**：见 `dish-sales-domain-capability-matrix.md`。**DishProfit** 仅保留「销量 + 毛利」组合场景；不在本条再扩展「哪个菜销量最高」为 DishProfit 主能力。辨析「菜品销量」与出库排行（`STOCK_REDUCE_QUERY`）、门店营业额（`revenue_query`）边界。 |
| **Phase 4** | **异常与原因** | 统一「毛利异常」是走 **排行 + 阈值**、**dish_low_profit_reason**、还是 **BUSINESS_DIAGNOSIS** 四域；避免口头「异常」无 wire。 |
| **Phase 5** | **GROUP / 多店** | 与 `dish-profit-planner-adapter-design.md`、Harness **GROUP_HYDRATED** 用例对齐；诚实降级策略已与库存域类似需可观测。 |
| **Phase 6** | **利润金额 vs 毛利率** | 若用户强需「最赚钱」按 **额**排序，需新增 metric 与 planType（或明确拒绝并澄清问法），避免与 `dish_gross_profit_rate_ranking_high` 混读。 |

---

## 10. 代码锚点（便于评审跳转）

| 主题 | 位置 |
|------|------|
| Intent / path 常量 | `AiResolvedQueryIntent` |
| Wire / canonical | `AiQuerySemanticLexicon` |
| Planner 分支 | `BusinessDataPlannerNode`（`PATH_DISH_PROFIT` → `DEFAULT_DISH_PROFIT_TOOLS`） |
| Tool 执行与 `toolResults` | `BusinessToolExecutionNode` |
| Tool 实现 | `DishProfitAnalysisTool` |
| Overview + AnswerPlan | `DishProfitAgentNode` |
| AnswerPlan DTO | `DishProfitAnswerPlan` |
| Composer 分支 | `StubAnswerComposerNode`（`isDishProfitPath`） |
| 确定性渲染 | `DishProfitDeterministicRenderer`、`DeterministicAnswerRenderer` |
| Parser 契约 | **`query_semantic_parser.v2.md`** + **`semantic-output-schema.md`**（v1 prompt 已于 D-CLEAN-V1 删除，仅 Git 历史） |

---

**文档版本**：2026-05-14，DishProfit 域只读梳理稿；增补「D-7 PROBE 执行链路评审：菜品毛利第一批」「D-7 与 D-8 边界」；D-8 能力矩阵见同目录 `dish-sales-domain-capability-matrix.md`。
