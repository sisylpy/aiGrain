# Menu Operation Agent P1 — 菜单经营顾问设计（第一版）

> **状态**：设计稿（P1 初版）；矩阵表含 **MO-D/E/F 等未落地行**。  
> **已实现 P1 卡片与问法**：见 **`docs/ai/menu-operation-p1-card-summary.md`**（接手/联调以该文档为准）。  
> **关联文档**：`docs/ai/dish-profit-domain-capability-matrix.md`、`docs/ai/dish-sales-domain-capability-matrix.md`、`docs/ai/harness-java-boundary-rules.md`、`docs/gb-dep-get-all-food-business-frontend.md`、`docs/gb-dish-cost-analysis-frontend.md`。  
> **前置梳理**：2026-05 菜品经营能力只读盘点（Controller / Service / Tool / AnswerPlan / Composer）。

---

## 1. MenuOperationAgent 定位

### 1.1 是什么

**Menu Operation Agent（菜单经营顾问）** 是 **老板视角** 的菜单优化顾问：帮助经营者理解「菜单整体赚不赚钱、结构是否健康、该推什么、该改什么价、该查什么成本、什么菜该考虑下架」。

它回答的是 **经营决策** 问题，而不是 **财务口径解释** 或 **单指标排行** 问题。

### 1.2 不是什么

| 域 | 职责 | MenuOperation 与之关系 |
|----|------|------------------------|
| **DishProfit** | 菜品毛利 / 成本 **透视与排行**（毛利率、理论/实际成本、低毛利原因） | **底层能力**；MenuOperation **复用其数据**，**不**替代其 path / AnswerPlan |
| **DishSales** | 菜品销量 / 销售额 **排行与单菜销售卡片** | **底层能力**；MenuOperation 读销量结构，**不**占用 `dish_sales_query_path` 主链 |
| **DishCost** | 单菜成本 + 配料明细（`dish_cost_analysis_path`） | **底层能力**；用于单菜深潜与 `RECIPE_REVIEW` 证据 |
| **StockReduce** | 出库 / 核销 / 损耗金额 | **辅助证据**；支撑 `CHECK_STOCK_REDUCE`、区间损耗率解释 |
| **BusinessDiagnosis** | 多域组合诊断（营收 + 采购 + 出库 + 菜品） | 并列域；MenuOperation **不**嵌入 diagnosis fallback |

### 1.3 产品原则

1. **建议必须可举证**：每条 `recommendedActions` 对应 `evidenceRows` 中的字段与 Tool 快照键，Composer **只宣读 AnswerPlan**，不现场拼事实。
2. **口径单轨**：MenuOperation 默认 **type1+2+3** 为「实际经营成本」；type1 仅作 **辅助解释**（见 §3）。
3. **语义单轨**：wire 来自 **LLM + contract + matrix**；Java **不** contains / alias / 关键词猜意图。
4. **独立 AnswerPlan**：`MenuOperationAnswerPlan` **不得**塞进 `DishProfitAnswerPlan` 或复用其 `planType` 枚举。

### 1.4 建议新增的路由常量（P1 设计，待实现）

| 层级 | 建议常量 | 建议值 |
|------|----------|--------|
| Intent | `AiResolvedQueryIntent.MENU_OPERATION` | `MENU_OPERATION` |
| Path | `AiResolvedQueryIntent.PATH_MENU_OPERATION` | `menu_operation_path` |
| Agent | `BusinessAgentNames`（待增） | `MenuOperationAgent` |
| Matrix | `MenuOperationSemanticCapabilityMatrix` | 见 §2 |
| Contract Catalog | `MenuOperationSemanticCapabilityContractExporter` | 见 §2 `selectedContractId` |

---

## 2. P1 能力矩阵

> **Wire 命名约定**：`menu_*` 前缀，与 `dish_profit_*` / `dish_sales_*` **命名空间隔离**。  
> **contract-locked 规则**：仅 merge 后 `structuredIntentDetail`（canonical wire）驱动 AnswerPlan；见 `harness-java-boundary-rules.md` §2。

### 2.1 总览

| 能力 ID | 用户问法示例 | selectedContractId | canonical wire | Tools | 输入槽位 | AnswerPlan `planType` | 老板侧建议动作（示例） |
|---------|-------------|-------------------|----------------|-------|----------|----------------------|------------------------|
| **menu_operation_overview** | 「这个月菜单经营怎么样？」「哪些菜在拖后腿？」 | `menu.operation.overview.v1` | `menu_operation_overview` | `dish_profit_analysis`；可选 `stock_reduce_query`（仅 scope 损耗摘要） | `timeWindow`；`orgScope`（门店/集团）；可选 `topN`（默认 5） | `MENU_OPERATION_OVERVIEW` | 汇总：赚钱菜占比、高风险菜数量、区间损耗率；动作：`KEEP_AND_PROMOTE`（头部）、`CONSIDER_DROP` / `REDUCE_COST`（尾部草稿，须带证据） |
| **menu_dish_profit_ranking** | 「哪些菜最赚钱？」「毛利率最低的菜是哪些？」 | `menu.dish.profit_ranking.v1` | `menu_dish_profit_ranking_low` / `menu_dish_profit_ranking_high` | `dish_profit_analysis` | `timeWindow`；`orgScope`；`rankDirection`（HIGH/LOW）；`topN` | `MENU_DISH_PROFIT_RANKING` | 低毛利：`REDUCE_COST`、`RECIPE_REVIEW`、`RAISE_PRICE`（需标价与成本证据）；高毛利：`KEEP_AND_PROMOTE` |
| **menu_dish_sales_ranking** | 「哪些菜卖得最好？」「有没有滞销菜？」 | `menu.dish.sales_ranking.v1` | `menu_dish_sales_ranking_high` / `menu_dish_sales_ranking_low` | `dish_profit_analysis`（P1 复用 dishRows）；单菜深潜时 `dish_sales_analysis_card` | `timeWindow`；`orgScope`；`rankMetric`（COUNT/AMOUNT）；`rankDirection`；`topN` | `MENU_DISH_SALES_RANKING` | 高销量：`KEEP_AND_PROMOTE`、`IMPROVE_EXPOSURE`（若毛利偏低则转介 high_sales_low_profit）；低销量：`CONSIDER_DROP`、`IMPROVE_EXPOSURE` |
| **menu_dish_high_sales_low_profit** | 「卖得火但不赚钱的菜有哪些？」「爆品是不是在亏钱？」 | `menu.dish.high_sales_low_profit.v1` | `menu_dish_high_sales_low_profit` | `dish_profit_analysis`；可选 `dish_ingredient_cost_breakdown`（Top 风险菜） | `timeWindow`；`orgScope`；`salesRankThreshold`（如 Top 30% 销量）；`marginThreshold`（如低于 portfolio 综合毛利率）；`topN` | `MENU_DISH_HIGH_SALES_LOW_PROFIT` | `RAISE_PRICE`；`REDUCE_COST`；`RECIPE_REVIEW`；`CHECK_STOCK_REDUCE`（损耗偏高时） |
| **menu_dish_single_analysis** | 「宫保鸡丁这菜经营上该怎么弄？」「帮我看看水煮鱼」 | `menu.dish.single_analysis.v1` | `menu_dish_single_analysis` | `dish_sales_analysis_card` 或 `dish_cost_analysis`（有 cost 深潜诉求时）；`dish_ingredient_cost_breakdown`（需配料证据时） | `timeWindow`；`orgScope`；**`dishAnchor`**（`foodId` 优先，见 §8 技术债）；`analysisDepth`（SUMMARY / COST_INGREDIENT） | `MENU_DISH_SINGLE_ANALYSIS` | 综合：`KEEP_AND_PROMOTE` / `RAISE_PRICE` / `REDUCE_COST` / `RECIPE_REVIEW` / `CHECK_STOCK_REDUCE`（按 evidence 择一或多条，**非** LLM 自由发挥） |
| **menu_dish_pricing_advice** | 「哪些菜该涨价？」「定价是不是偏低？」 | `menu.dish.pricing_advice.v1` | `menu_dish_pricing_advice` | `dish_profit_analysis` | `timeWindow`；`orgScope`；`pricingSignal`（LOW_MARGIN_HIGH_SALES / BELOW_STANDARD_BAND）；可选 `mentionedDishName` → **`foodId` 锚** | `MENU_DISH_PRICING_ADVICE` | `RAISE_PRICE`（标价低于标准带且销量健康）；`KEEP_AND_PROMOTE`（已在标准带内且毛利健康）；**禁止**无标价证据时给具体涨幅数字 |

### 2.2 各能力补充说明

#### menu_operation_overview

- **与 DishProfit overview 区别**：DishProfit 输出 **指标与排行**；MenuOperation 输出 **结构判断 + 建议动作清单**（`recommendedActions` + `summaryFacts`）。
- **focusDishes / riskDishes / opportunityDishes** 三分法：
  - **focusDishes**：综合毛利与健康度头部（KEEP / PROMOTE 候选）
  - **riskDishes**：低毛利、成本不可信、或高销量低利润（REDUCE_COST / CONSIDER_DROP 候选）
  - **opportunityDishes**：销量中等但毛利高、或接近标准毛利率带（RAISE_PRICE / PROMOTE 候选）

#### menu_dish_profit_ranking

- **排序键（Java AnswerPlanBuilder，非 LLM）**：
  - HIGH：`blendedGrossMarginRateOnListPrice`（type123 单菜口径）或 **实际利润额** `actualRevenue - actualCostTotalAmount123`
  - LOW：同上 ASC
- **不**复用 `DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN` 等 planType；仅复用 **同一 Tool 快照字段**。

#### menu_dish_sales_ranking

- P1 **不新增** `menu_sales_query` Tool；从 `dish_profit_analysis` 的 `dishRows` 读取 `soldPortionsTotal` / `actualRevenue`。
- 与 `DishSalesAnswerPlan` **并行存在**：用户明确只问销量且不需要经营建议时，仍走 `dish_sales_query_path`；问「菜单怎么优化 / 滞销怎么办」走 MenuOperation。

#### menu_dish_high_sales_low_profit

- **判定规则（Java，contract 参数化阈值）**：
  - 销量分位 ≥ `salesRankThreshold`（默认 Top 30% 或 absolute Top N）
  - 且 `blendedGrossMarginRateOnListPrice` < 组合参考线（如同期 `businessInsightSummary.comprehensiveGrossMarginRateOnListPrice` 或标准带 T−F）
  - 或 **实际利润额** < 0（`actualRevenue - actualCostTotalAmount123`）
- 可选拉 `dish_ingredient_cost_breakdown` 仅对 **focus 1～3 道菜** 补充 `RECIPE_REVIEW` 证据。

#### menu_dish_single_analysis

- 单菜 **必须** `EffectiveDishAnchor` / `foodId`；无锚 → clarification，**禁止** contains 猜菜。
- `analysisDepth=COST_INGREDIENT` 时增跑 `dish_ingredient_cost_breakdown`；否则 `dish_sales_analysis_card` + profit 行即可。

#### menu_dish_pricing_advice

- 依赖 `grossMarginStandardTarget` / `grossMarginLevel`（来自 `buildInsight` 行字段，与页面一致）。
- 建议 **定性**（「可考虑调价至标准带附近」），P1 **不给**具体涨幅百分比（避免 Composer 算术）。

---

## 3. 数据口径（MenuOperation 专用）

### 3.1 默认实际经营成本：type1 + type2 + type3

| 概念 | 字段 | 说明 |
|------|------|------|
| **实际成本（主口径）** | `actualCostTotalAmount123` | 单菜区间 **type1+2+3** 分摊总金额 |
| **单份实际成本** | `actualCostPerPortion123` | 与配料分析整菜行一致 |
| **辅助：生产成本** | `actualCostAmount` | **仅 type1**；可在 `evidenceRows[].auxNotes` 中解释「生产耗用 vs 含损耗摊销」 |
| **禁止** | 在同一 `recommendedActions` 条目内混用 type1 与 type123 作分子/分母 | Builder 须显式字段名 |

### 3.2 理论成本

| 字段 | 用途 |
|------|------|
| `theoryCostAmount` | 配方理论成本；用于 `RECIPE_REVIEW`、理论 vs 实际差异说明 |
| `grossMarginRateTheoryOnListPrice` | 理论毛利率；**不**作为 MenuOperation 主排序键 |

### 3.3 销售额

| 字段 | 说明 |
|------|------|
| `actualRevenue` | **标价收入** = `soldPortionsTotal × listPrice`（与页面、DishProfit 一致） |
| 非 POS 实收 | MenuOperation **不**声称「实收」 unless 未来接入实收域 |

### 3.4 实际利润（MenuOperation 标准定义）

```
actualProfitAmount = actualRevenue − actualCostTotalAmount123
```

- 汇总：`portfolioActualProfitAmount = Σ actualProfitAmount`（对参与 overview 的菜行求和）
- 毛利率（主）：优先 **`blendedGrossMarginRateOnListPrice`**（单菜：标价 vs 单份 type123 成本）或汇总 **`comprehensiveGrossMarginRateOnListPrice`**（overview 用）
- **type1 毛利率** `grossMarginRateOnListPrice`：**仅**放入 `auxNotes`，不作为默认排序/建议依据

### 3.5 损耗与出库

| 字段 | 用途 |
|------|------|
| `scopeOutboundSubtotals.wasteLossRatioInOutbound123` | 区间 **部门级** 损耗率；overview 与 `CHECK_STOCK_REDUCE` 引用 |
| `stock_reduce_query` | 单菜/单域深潜时 **可选**；不在 P1 每条能力强制调用 |

### 3.6 配料层（深潜）

- `dish_ingredient_cost_breakdown` / `GbDishCostAnalysisService#buildIngredientAnalysisReport` 的 `ingredientRows`：仅服务 `RECIPE_REVIEW`、`REDUCE_COST` 的 **证据行**，不替代整菜 type123 主口径。

---

## 4. MenuOperationAnswerPlan 草案

> 新建 DTO：`com.nongxinle.ai.dto.business.MenuOperationAnswerPlan`（**设计态**，P1 文档定义，实现时另 PR）。  
> Composer 输入：**仅**本对象 + 权限/范围 label；**不**直读 `toolResults`。

### 4.1 字段结构

```java
// 设计草案 — 非实现代码
public class MenuOperationAnswerPlan {

    /** 与 matrix answerPlanType 对齐，如 MENU_OPERATION_OVERVIEW */
    String planType;

    /** 统计时间，如「2026-04-01 至 2026-04-30」 */
    String timeLabel;

    /** 范围，如「XX 门店」/ queryScopeBanner */
    String scopeLabel;

    /** ISO 或 yyyy-MM-dd 边界（可选，供卡片） */
    String statStartDate;
    String statEndDate;

    /**
     * 已算好的汇总事实（plain string / number），Composer 只读。
     * 示例键：totalActualRevenue, totalActualCost123, portfolioActualProfitAmount,
     *         comprehensiveGrossMarginRate, wasteLossRatioInOutbound123, dishCountAnalyzed
     */
    Map<String, Object> summaryFacts;

    /**
     * 重点菜品（老板应关注的好菜/主推候选）
     * 每行：foodId, dishName, soldPortionsTotal, actualRevenue,
     *       actualCostTotalAmount123, actualProfitAmount, blendedGrossMarginRateOnListPrice,
     *       suggestedActions[]（枚举码）
     */
    List<Map<String, Object>> focusDishes;

    /** 风险菜品（低毛利、亏损、成本不可信） */
    List<Map<String, Object>> riskDishes;

    /** 机会菜品（可涨价、可加大推广） */
    List<Map<String, Object>> opportunityDishes;

    /**
     * 结构化建议动作（去重、有序）
     * 每项：actionCode, priority(1-3), targetDishIds[], rationaleKey, evidenceRefIds[]
     */
    List<MenuOperationRecommendedAction> recommendedActions;

    /**
     * 举证行（Tool 快照映射后的稳定键，供 Debug / Replay / Composer 引用）
     * 每行：evidenceId, sourceTool, fieldPath, displayLabel, value, unit
     */
    List<Map<String, Object>> evidenceRows;

    /**
     * 已知缺口（诚实降级）
     * 示例：MISSING_DISH_ANCHOR, GROUP_WIDE_AGGREGATE_LIMITED, INGREDIENT_BREAKDOWN_SKIPPED
     */
    List<String> knownGaps;

    /** Replay / Harness 专用 */
    Map<String, Object> debug;

    /** 多轮锚点（DISH foodId） */
    List<AiResultAnchor> resultAnchors;
}
```

### 4.2 planType 枚举（P1）

| planType | 对应能力 |
|----------|----------|
| `MENU_OPERATION_OVERVIEW` | menu_operation_overview |
| `MENU_DISH_PROFIT_RANKING` | menu_dish_profit_ranking |
| `MENU_DISH_SALES_RANKING` | menu_dish_sales_ranking |
| `MENU_DISH_HIGH_SALES_LOW_PROFIT` | menu_dish_high_sales_low_profit |
| `MENU_DISH_SINGLE_ANALYSIS` | menu_dish_single_analysis |
| `MENU_DISH_PRICING_ADVICE` | menu_dish_pricing_advice |

### 4.3 Builder 职责边界

| 层 | 职责 |
|----|------|
| **Tool** | 返回原始/半结构化快照（现有 Tool 不改口径） |
| **MenuOperationAnswerPlanBuilder** | 读 Tool 快照 + contract 参数 → 算 `actualProfitAmount`、分档、生成 `recommendedActions`、写 `evidenceRows` |
| **MenuOperationDeterministicRenderer** | 宣读 Plan；**不算术、不排序、不猜菜名** |
| **LLM Composer（若启用）** | 仅润色话术；**输入边界**为 Plan 内已有字段；不得新增事实 |

---

## 5. 建议动作枚举

| 枚举码 | 中文标签（Composer 用） | 典型触发条件（Builder 规则，非 LLM） |
|--------|-------------------------|--------------------------------------|
| `KEEP_AND_PROMOTE` | 继续主推 | 高毛利 + 高/中销量；或 overview 头部菜 |
| `RAISE_PRICE` | 考虑涨价 | 销量健康 + 毛利率低于标准带 / 低于 portfolio 参考线 |
| `REDUCE_COST` | 考虑降本 | 实际成本显著高于理论；或 type123 成本侵蚀利润 |
| `CHECK_STOCK_REDUCE` | 查出库损耗 | 部门损耗率偏高 + 该菜成本偏差大；或 ingredient 行 utilization 低 |
| `IMPROVE_EXPOSURE` | 加强曝光/套餐搭配 | 毛利好但销量偏低 |
| `CONSIDER_DROP` | 考虑下架/缩菜单 | 低销量 + 低毛利 + 长期亏损 |
| `RECIPE_REVIEW` | 复核配方 / BOM | 理论 vs 实际差距大；或 `dish_ingredient_cost_breakdown` 有异常配料 |

**优先级**：`recommendedActions[].priority` — 1=本轮主建议，2=次要，3=附带说明。

**一条 action 必须绑定** `evidenceRefIds` 指向 `evidenceRows`；无证据则 **不得** 输出该 action。

---

## 6. 禁止项（P1 硬约束）

与 `docs/ai/harness-java-boundary-rules.md` 一致，MenuOperation **额外**强调：

1. **禁止** Java `contains` / 关键词 / alias 判断用户业务意图或匹配菜名（含 Tool 内 `foodName.contains` 模式）；单菜必须 **`foodId` / `EffectiveDishAnchor`**，否则 clarification。
2. **禁止** LLM 自造 wire；仅允许 matrix 注册的 canonical wire，经 `SemanticContractCompletionEngine` contract-locked。
3. **禁止** 新增 alias 兼容（如 `depGetAllFood` ↔ `depGeFoodBusiness` 在语义层的 alias）；文档与 API 契约统一为一个对外名（实现期再定，P1 文档只用 **`depGeFoodBusiness`**）。
4. **禁止** Composer 直接从 `toolResults` 拼事实或生成 `recommendedActions`（`dish_cost_analysis` 现网反例需在 MenuOperation 中 **不复刻**）。
5. **禁止** 将 MenuOperation 计划挂载到 `DishProfitAnswerPlan` / `DishSalesAnswerPlan` 或复用其 `planType`。
6. **禁止** 自动 SQL 生成或新 Mapper；P1 仅复用现有 Service。
7. **禁止** 在本设计阶段新增测试文件、编译或运行测试（实现 PR 另议）。

---

## 7. 现有能力复用建议

### 7.1 数据 Service（页面同源）

| 资产 | 复用方式 |
|------|----------|
| **`GbDepFoodBusinessInsightService#buildInsight`** | **主数据源**；overview / 排行 / high_sales_low_profit / pricing 均读 `dishes` + `businessInsightSummary` + `scopeOutboundSubtotals` |
| **`GbDishCostAnalysisService`** | 单菜深潜：`buildIngredientAnalysisReport`（经 `dish_cost_analysis`）；配料证据：`buildIngredientRowsForFoodIds`（经 `dish_ingredient_cost_breakdown`） |

### 7.2 AI Tool（不新增 SQL）

| Tool ID | MenuOperation 用法 |
|---------|-------------------|
| **`dish_profit_analysis`** | **默认必跑**（除纯单菜卡片可选路径外）；提供 dishRows 与汇总 |
| **`dish_sales_analysis_card`** | `menu_dish_single_analysis` 且仅需销售卡片时 |
| **`dish_cost_analysis`** | 单菜 + 成本/配料深潜；结果进入 `evidenceRows`，**须**经 PlanBuilder 转写，Composer 不直读 Tool |
| **`dish_ingredient_cost_breakdown`** | `RECIPE_REVIEW` / `REDUCE_COST` 的配料级证据（限量 Top N 菜） |
| **`stock_reduce_query`** | overview 或 `CHECK_STOCK_REDUCE` 需出库分型金额时 **可选**；P1 可不默认进链 |

### 7.3 不建议复用

| 资产 | 原因 |
|------|------|
| `DishProfitAnswerPlan` / `DishProfitAgentNode` 挂载逻辑 | 域职责不同；仅复用 Tool 快照 |
| `DishSalesAnswerPlan` | 销量 **问答** 仍属 D-8；MenuOperation 自有 `MENU_DISH_SALES_RANKING` |
| `AiDishProfitOverviewResult`（SSE 卡片） | 可 **参考字段**，但 MenuOperation 应有独立 `menuOperationOverview` 卡片（实现期） |
| `DishDashboardModuleRenderer` | 看板模块可后续挂 MenuOperation 摘要，P1 不阻塞 |

### 7.4 编排建议（P1 实现时）

```
Semantic Parser → contract-locked wire
  → BusinessDataPlannerNode（menu_operation_path）
  → MenuOperationAgent
  → Tools（上表）
  → MenuOperationAnswerPlanBuilder.attach
  → MenuOperationDeterministicRenderer / 受控 LLM Composer
  → SSE answer_delta.data.menuOperationAnswerPlan (+ 可选 menuOperationOverview 卡片)
```

---

## 8. 技术债与 P1 规避策略

| 技术债 | 现状 | MenuOperation P1 策略 |
|--------|------|------------------------|
| **`dish_cost_analysis` 无 AnswerPlan** | Composer 直读 Tool 拼正文 | MenuOperation **禁止**沿用；成本证据经 **PlanBuilder → evidenceRows** 进入 Plan |
| **type1 / type123 混用** | `DishProfitActualCostSemanticsSupport` 有 display 回退；页面 summary 中 blended(type1) vs comprehensive(type123) 并存 | MenuOperation **强制 type123** 为默认；type1 **仅** `auxNotes`；Builder 单测（实现期）锁定公式 |
| **contains 菜名匹配** | `DishProfitAnalysisTool`、`DishSalesAnalysisCapabilityAdapter`、`DishProfitAgentNode` 等 | MenuOperation **不调用**含 contains 的 presentation 路径；单菜 **仅 foodId 锚**；存量 Tool 清理另 PR |
| **`depGetAllFood` / `depGeFoodBusiness` 命名不一致** | 文档 alias 与代码路径分裂 | 对外契约统一 **`POST /gbdepfood/depGeFoodBusiness`**；Insight 数据与页面一致 |
| **DishSales 排行依赖 `dish_profit_analysis` 快照** | 无独立 sales Tool | P1 接受；MenuOperation 同样读 `dish_profit_analysis`，但在 **独立 path + Plan** 下产出 **建议动作**，与 D-8 用户预期分离 |
| **150 行截断** | `DishProfitAnalysisTool.MAX_DISH_ROWS` | overview 声明 `knownGaps: DISH_ROWS_TRUNCATED`；排行类 TopN≤20 通常不受影响 |
| **集团广角 `groupWideMendianAggregate`** | 多维汇总有限 | overview 带 `knownGaps: GROUP_WIDE_AGGREGATE_LIMITED`；不编造门店级建议 |
| **`TYPE_DISH_HIGHEST_MARGIN` Composer 不对称** | DishProfit 确定性渲染未全覆盖 | MenuOperation **新建** Renderer，不依赖 DishProfitDeterministicRenderer |

---

## 9. P1 范围外（显式不做）

- 自动改价 / 改配方 / 下架执行（仅 **建议**）
- 菜品分类 / 门店维度销量趋势 / 环比（D-8 Phase 2+）
- 实收 POS 金额 vs 标价收入对账
- 多轮「帮我执行下架」类写操作
- 新 SQL、新表、新 Tool ID（除非 `menu_operation_path` 必须增 **Orchestration-only** 包装 Tool，P1 倾向 **零新 Tool**）

---

## 10. 文档版本

| 版本 | 日期 | 说明 |
|------|------|------|
| P1-design-v1 | 2026-05-27 | 首版设计：能力矩阵、口径、AnswerPlan 草案、禁止项、复用与技术债 |

**下一步（实现 PR，非本文档）**：`MenuOperationSemanticCapabilityMatrix` + ContractExporter → Intent/Path 注册 → `MenuOperationAnswerPlanBuilder` → Harness Replay 用例（`MENU_OPERATION_*`）→ Composer / SSE 契约补充 `docs/api/frontend-api-contract.md`。
