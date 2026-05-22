# DishSales（D-8）— 菜品销售域能力矩阵

> **定位**：与 `dish-profit-domain-capability-matrix.md`（DishProfit / D-7）配合使用。本文档为 **规划与事实梳理**；**D-8 Phase 1（菜品销量/销售额排行闭环）已收口**，Phase 2+ 仍多未落地。

> **语义 vs Tool（勿混写）**：**`DISH_SALES_QUERY`**（intent）与 **`dish_sales_query_path`**（path）为 **现网** D-8 路由；**执行 Tool** 仅为 **`dish_profit_analysis`**（`DishProfitAnalysisTool`）。**Historical removed**：独立 Tool id **`dish_sales_query`** / **`DishSalesQueryTool`**（**D-CLEAN-DISH-SALES-P2**）。**不得**恢复独立 Tool 或 `toolResults["dish_sales_query"]`。

---

## 1. 背景与边界

| 域 | 职责 |
|----|------|
| **DishSales（D-8）** | 菜品**销量**、**销售份数**、**销售额**、**爆品**、**滞销菜**、**菜品销售趋势**（按菜 SKUs / 菜谱维度，非门店流水摘要的 substitute）。 |
| **DishProfit（D-7）** | **毛利率**、**成本**、**理论/实际成本差异**、**低利润原因**；**不**长期承接纯「哪个菜销量最高」类主问法（见 DishProfit 文档「D-7 与 D-8 边界」）。 |
| **Revenue** | **门店/集团营业额**、门店排行等；**不负责**按**菜品**维度销量/销售额排行。 |
| **BusinessDiagnosis** | **多域组合诊断**（营业额 + 采购 + 出库 + 菜品毛利等）；狭义「按菜品卖了多少」单域排行优先 DishSales。 |

---

## 2. D-8 Phase 1 收口结果

> **状态**：**D-8 Phase 1 已在 GRAPH_RUN 下验收收口**（2026-05-15 文档冻结本节事实；实现以仓库代码为准）。本节为 Phase 1 **唯一事实摘要**：已完成能力、验收样例、边界与 Phase 2+ 方向；**纯文档迭代**可只改本节与文末「文档版本」。

### 2.1 已完成能力

- **路由**：**`DISH_SALES_QUERY`**（有效意图）与 **`dish_sales_query_path`**（有效路径）已作为主载体区分「菜品销量/销售额排行」与 DishProfit 专线。
- **Wire 来源（D-1X-D3）**：`DishSalesAnswerPlanBuilder` 仅读 merge 后 **`queryIntent.structuredIntentDetail`** 或 V2 **`semanticSlots.structuredIntentDetailWire` / `currentTurnStructuredIntentDetailWire`**；**不** fallback **`metric.rankingType`**。
- **三种结构化 wire**（经 `canonicalStructuredIntentDetailWire` 归一后接入 Builder）：
  - **`dish_sales_count_ranking_high`**：销售份数 / 销量偏高排行（高）；
  - **`dish_sales_amount_ranking_high`**：菜品侧销售额偏高排行（高）；
  - **`dish_sales_count_ranking_low`**：销售份数偏低排行（低 / 滞销方向）。
- **Planner / Tool**：**复用** **`dish_profit_analysis`** 数据源与执行链路，**Phase 1 不新增**独立 SQL / Tool；`dataPlanTools` / `usedTools` 在 DishSales 场景下与之一致。
- **计划产物**：**`DishSalesAnswerPlan`** 已生成（含 `planType`、`rankingRows`、范围与时间标签、debug 等）。
- **最终答复**：**`DishSalesDeterministicRenderer` + `StubAnswerComposerNode`** 在菜品销量专线且计划有效时，**确定性优先**接管 **`finalAnswerText`**（不调用 LLM Composer）。

### 2.2 GRAPH_RUN 验收结果（示例数据）

在某轮 GRAPH_RUN 回归中，**销量/销售额排行**类问法得到的确定性摘要与以下一致（用于验收对照；环境数据可变）：

| 场景 | 问法示例 | 验收要点（示例数据） |
|------|----------|----------------------|
| **销量最高（COUNT_HIGH）** | 哪个菜销量最高？ | **烩菜**，**120** 份，**4200** 元 |
| **销售额最高（AMOUNT_HIGH）** | 哪个菜销售额最高？ | **烩菜**，**4200** 元，**120** 份 |
| **销售份数最少（COUNT_LOW）** | 哪个菜销售份数最少？ | **香煎青鱼**，**4** 份，**272** 元 |

**回归隔离（仍走 DishProfit 专线）**：

| 场景 | 问法示例 | 路由 / 计划 |
|------|----------|-------------|
| **毛利率排行** | 哪个菜毛利率最低？ | **`DISH_PROFIT` / `dish_profit_path`**，`DishProfitAnswerPlan` |
| **成本排行** | 哪个菜成本最高？ | **`DISH_PROFIT` / `dish_profit_path`**，`DishProfitAnswerPlan` |

上述 DishProfit 问法 **不**经 DishSales 确定性 Composer 抢答；与 Phase 1 DishSales 验收并行通过 GRAPH_RUN。

### 2.3 当前边界（Phase 1 诚实表述）

- **数据源**：仍**复用** **`dish_profit_analysis`** 返回的 **`dishRows`**（标价口径销售额等以 Builder / 工具定义为准）。
- **毛利叙事**：在 DishSales 答复中，**毛利率仅作附带列表字段**（如 Top3 行展示）；**不**在「纯销量/销售额」问题下展开毛利诊断、成本异常、配方核对等 DishProfit 主线话术。
- **Historical removed（D-CLEAN-DISH-SALES-P2）**：独立 Tool **`dish_sales_query`** / **`DishSalesQueryTool`** 已删除。**D-8**（`DISH_SALES_QUERY` / `dish_sales_query_path`）与 **成本链**均执行 **`dish_profit_analysis`**（见 `dish-profit-domain-capability-matrix.md`）。
- **延后（Phase 2+）**：趋势、门店维度菜品排行、分类销量、「销量高但不赚钱」等组合分析——见 **§2.5** 与 §9。

### 2.4 与下文章节的关系

- **§3「当前代码事实」**、**§4「D-8 Phase 1 建议」**及 **§5「接入点评审：推荐方案 B」**与实现对齐时，**以本节 §2「D-8 Phase 1 收口结果」为 Phase 1 验收事实源**。
- §3 中若仍有历史草稿表述，与 §2 冲突的**以 §2 为准**。

### 2.5 Phase 2+ 后续方向（未纳入 Phase 1）

以下为 **规划占位**，与 §9「分期建议」一致；**Phase 1 不实现**：

- **趋势**：单菜或多菜销量/销售额时间序列（如环比、波动），需独立数据窗口与 Tool/契约评审。
- **门店排行**：按门店（或门店子集）的菜品销量/销售额对比，需在 scope 与时间窗上与 Revenue / 门店维度对齐。
- **分类销量**：按菜品分类/品类的聚合排行或对比，依赖分类维稳定键与 Planner wire。
- **「销量高但不赚钱」等组合分析**：DishSales 与 DishProfit **协同**（多域 plan 或组合 AnswerPlan），属于 Phase 4 一类能力，见 §9。

---

## 3. 当前代码事实（只读梳理）

- **`DISH_SALES_QUERY` / `dish_sales_query_path`**：已作为 DishSales 主路由（与 `DISH_PROFIT` / `dish_profit_path` 区分）；Harness / GRAPH_RUN 可观测。
- **`AiQuerySemanticLexicon`**：Phase 1 使用 **`STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH`**、**`STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH`**、**`STRUCTURED_DISH_SALES_COUNT_RANKING_LOW`**（及 **`canonicalStructuredIntentDetailWire`** 对别名归一）；**`STRUCTURED_DISH_SALES_RANKING`（`dish_sales_ranking`）** 仍可作为份数侧泛化入口。
- **`dish_profit_analysis`**：DishSales Phase 1 **数据面唯一 Tool**；逐菜行仍在 `toolResults["dish_profit_analysis"]["data"]["dishRows"]`。
- **`DishSalesAnswerPlan` + `DishSalesAnswerPlanBuilder`**：已落地；Composer 侧 **`DishSalesDeterministicRenderer`** 在专线 + 有效计划时优先写 **`finalAnswerText`**。
- **`revenue_query`**：仍**不**承担按**菜品**维度的销量/销售额排行。
- **`dish_sales_query` Tool**：**Historical removed** — **`DishSalesQueryTool`** 已删除（**2026-05-20 P2**）。D-8 语义 intent 仍为 **`DISH_SALES_QUERY`**，path 仍为 **`dish_sales_query_path`**，执行 Tool 为 **`dish_profit_analysis`**（成本链亦同）。

---

## 4. D-8 Phase 1 建议：菜品销量 / 销售额排行

### 建议短期目标问法

- 哪个菜销量最高？
- 哪个菜卖得最多？
- 哪个菜销售份数最多？
- 哪个菜销售额最高？
- AAA 店哪个菜销量最高？

### 建议短期技术方案

- **产品域命名**：**DishSales**（与 DishProfit、Revenue 并列叙事）。
- **第一阶段**：可**暂复用** `dish_profit_analysis` 返回的 **`dishRows`**，按字段 **`soldPortionsTotal`**、**`listPriceRevenue`** 排序与裁剪 Top N。
- **不**使用 **`revenue_query`** 承接**菜品**侧销量/销售额排行。
- **不**再把纯销量问题作为 **DishProfit 长期文档能力**写入主矩阵（见 DishProfit 文档 D-7/D-8 边界）。

---

## 5. D-8 Phase 1 接入点评审：推荐方案 B

本节为 **方案 B 设计考古与评审记录**。**D-8 Phase 1 已按方案 B 主线收口**（见 §2）；下文表格与字段说明仍可供 Phase 2+ 对照。

### 1. toolResults 中逐菜行路径

当前 **`dish_profit_analysis`** 写入 `AiRunState.toolResults` 的为 **envelope**；逐菜行在内层 **`data`** 中：

`toolResults["dish_profit_analysis"]["data"]["dishRows"]`

### 2. `dishRows` 每行可用于 DishSales Phase 1 的字段

- **`dishName`**
- **`soldPortionsTotal`**：销售份数 / 销量（工具 summarize 后多为字符串形式，排序时按数值语义处理）
- **`listPriceRevenue`**：菜品**标价口径**销售额（同上，多为字符串）
- **`grossMarginRateOnListPrice` / `blendedGrossMarginRateOnListPrice`**：毛利率参考字段
- **`actualCostAmount`**
- **`theoryCostAmount`**

**注意**：没有稳定键名 **`grossProfitRate` / `actualCost` / `theoreticalCost`**，实际应对应上述 **`blended`/`grossMarginRateOnListPrice`**、**`actualCostAmount`**、**`theoryCostAmount`**。

### 3. `dish_sales_ranking` 与排序（份数）

当结构化子意图为 **`dish_sales_ranking`** 时，`DishProfitAnalysisTool#applyDishProfitPresentation` **已按 `soldPortionsTotal` 降序**重排 insight 行（再进入后续 summarize）。

### 4. 销售额排行 wire 与排序

**Phase 1 已落地**：**`dish_sales_amount_ranking_high`** 经 Builder 按 **`listPriceRevenue`** 做销售额降序排行。以下历史表述保留为设计对照：**规划**上曾建议与份数排行并列实现，现已并入 Phase 1。

### 5. `DishProfitAnswerPlan` 不宜作为 DishSales 长期契约

**Phase 1**：纯销量/销售额排行已使用 **`DishSalesAnswerPlan`**（及 Deterministic Composer）；本节仍说明**为何**不宜把长期 DishSales 契约挂在 **`DishProfitAnswerPlan`** 上。

- **只**携带 **Top 行**与少量 **`secondaryRows`**，不是全表。
- **全量 `dishRows`** 仍在 **`toolResults`** 中。
- **不应**继续把纯销量问题的长期输出契约挂在 **`DishProfitAnswerPlan`** 之下（DishProfit 专线仍用其承载毛利率/成本等主线）。

### 6. 三种方案对比与结论

| 方案 | 要点 | 优点 | 缺点 | 结论 |
|------|------|------|------|------|
| **A** | 继续挂在 **`dish_profit_path`** 下 | 改动最小 | 污染 DishProfit；Harness **可观测性差**；不利销量趋势、爆品、滞销菜扩展 | **仅极短期补丁**，不推荐 Phase 1 正式方案 |
| **B** | 新增 **`DISH_SALES_QUERY` / `dish_sales_query_path` / `DishSalesAnswerPlan`**，**`dataPlanTools` 仅含 `dish_profit_analysis`**（**现网已落地**） | **产品域清晰**；**不新写 SQL**；不破坏 DishProfit | 须 intent/path、Planner、AnswerPlan、Composer/Renderer | **推荐（Phase 1 已选）** |
| **C** | 新 DishSales path + **新 Tool / 新 SQL** | 实现彻底解耦 | Phase 1 **成本过高**；**非当前计划** | **Future proposal**（独立 `dish_sales_query` Tool 若再评，须单独 ADR；**禁止** Cursor 误恢复） |

### 7. D-8 Phase 1 推荐落地方案（方案 B 概要）

以下为方案 B **目标 checklist**；**Phase 1 已与 §2 对照收口**。

- 新增 **`DISH_SALES_QUERY`**（意图）与 **`dish_sales_query_path`**（路径常量以代码库为准）。
- 新增 **`DishSalesAnswerPlan`**、**`DishSalesAnswerPlanBuilder`**（或等价命名）。
- **`BusinessDataPlannerNode`**：对 **`dish_sales_query_path`** 生成 **`dataPlanTools = ["dish_profit_analysis"]`**。
- **`BusinessToolExecutionNode`**：**仍只执行** `dish_profit_analysis`，**不**新增 Phase 1 Tool。
- **Builder**：从 **`toolResults["dish_profit_analysis"]["data"]["dishRows"]`** 读取；**销量排行**按 **`soldPortionsTotal`** 排序；**销售额排行**按 **`listPriceRevenue`** 排序。
- **`StubAnswerComposerNode`**：Phase 1 **已走确定性输出**（`DishSalesDeterministicRenderer`），LLM Composer 为非主线。

### 8. 诚实降级（与实现一致）

- **`dishRows` 为空**：**不得**强答「哪个菜最高」。
- **`soldPortionsTotal` 缺失**：只说明缺少可靠销售份数字段，不编造排行。
- **`listPriceRevenue` 缺失**：**不得**回答销售额排行。
- Phase 1 销售额口径为 **`listPriceRevenue`**（标价口径），须在用户可见答复中**说明为标价口径销售额**。
- **复用 `dish_profit_analysis`** = **数据源复用**，**不意味**产品域仍归 DishProfit。

### 9. 代码实现

**D-8 Phase 1 已落地**；后续 Phase 2+ 变更须单独评审。**不在本文档更新中改 Java / prompt / test**（纯文档迭代除外）。

---

## 6. 建议 wire（规划）

| wire（蛇形，规划） | 含义 |
|-------------------|------|
| `dish_sales_count_ranking_high` | 销售份数最高 |
| `dish_sales_amount_ranking_high` | 销售额（菜品侧）最高 |
| `dish_sales_count_ranking_low` | 销售份数最低 / 滞销方向 |
| `dish_sales_trend_down` | 销量下降 |
| `dish_sales_trend_up` | 销量上升 |

**现有**：`dish_sales_ranking` 可作「销量/份数」泛化 Top 排行。

**Phase 1 已落地**：**`dish_sales_count_ranking_high` / `dish_sales_amount_ranking_high` / `dish_sales_count_ranking_low`** 为 Builder 接受的三种结构化 wire（见 §2.1）。下表仍列未来 Phase 2+ 的 **趋势类** wire。

**过渡期（历史）**：若曾计划将 **`dish_sales_ranking_high`** 等 Alias **仅**归一到 `dish_sales_ranking`，与当前「三 wire + canonical」实现并存时，以代码与 §2 为准。

---

## 7. 建议 AnswerPlan（规划）

**Phase 1 已落地 `DishSalesAnswerPlan`**。下表为 **planType 命名** 与后续扩展的对照（实现侧常量名以代码为准）。

| planType（建议常量名） | 说明 |
|------------------------|------|
| `TYPE_DISH_SALES_COUNT_RANKING` | 按份数排行 |
| `TYPE_DISH_SALES_AMOUNT_RANKING` | 按菜品销售额排行 |
| `TYPE_DISH_SALES_LOW_RANKING` | 低销量 / 滞销榜 |
| `TYPE_DISH_SALES_TREND` | 趋势类 |

**Phase 1** 已覆盖 **份数偏高、销售额偏高、份数偏低** 三问法（与上表前三行语义对应，见 §2）；**趋势类** 仍为 Phase 2+。

---

## 8. 诚实降级

- 若仅有可靠 **`soldPortionsTotal`**（逐菜行）：**仅**应答 **销售份数** 排行；**不**承诺「销售额最高」与财务口径完全一致。
- 若 **`listPriceRevenue`**（或等价字段）在逐菜行上**可用且可排序**：方可应答 **销售额** 排行，并在答复中说明口径（如标价营收等，与业务定义对齐）。
- **勿**把 **门店营业额**（`revenue_query`）**说成**「哪个菜卖得最好」的排行依据。
- **若无**逐菜行或字段缺失：**不得**强答「哪个菜最高/最低」。
- **复用 `dish_profit_analysis`** 时：须在内部注释/运营说明中写明——当前基于 **菜品分析/洞察** 数据中的销量与标价营收字段，**域归属仍为 DishSales 产品叙事**，非 DishProfit 主线。

---

## 9. 分期建议

| Phase | 目标 |
|-------|------|
| **Phase 1** | 销量 / 销售额排行（闭环问法见 §4） |
| **Phase 2** | 单菜销量走势 |
| **Phase 3** | 门店维度下菜品销量对比 |
| **Phase 4** | **销量 + 毛利**组合分析（DishSales 与 DishProfit 协同） |
| **Phase 5** | 爆品 / 滞销菜 / 活动建议（依赖规则与数据成熟度） |

---

## 10. 下一步实现建议（评审向）

- **先不新写 SQL**；优先沿用 **`buildInsight`** / 现有 `dish_profit_analysis` **dishRows**。
- **Future proposal（非当前计划）**：是否新增独立 **`dish_sales_query` Tool** 须单独评审；现网 **禁止** 编排该 Tool id。Harness / 路由表变更见 `business-question-routing-d2-design.md`。**Phase 1 落地方案见 §5 方案 B（已落地）**。

---

**文档版本**：**D-8 Phase 1 已收口**（2026-05-15）；**D-CLEAN-DISH-SALES-P2**（2026-05-20）删除独立 **`dish_sales_query` Tool**，保留 **`DISH_SALES_QUERY` / `dish_sales_query_path`**，执行 **`dish_profit_analysis`**。见 `docs/AI_MAINLINE_INDEX.md`。
