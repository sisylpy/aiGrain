# 菜品毛利：AnswerPlan 与字段契约

> **前提**：算法与口径**不重新发明**——以 `GbDepFoodBusinessInsightService#buildInsight`、`GbDishCostAnalysisServiceImpl`、`DishProfitAnalysisTool` 及 `docs/gb-dish-cost-analysis-frontend.md` 为准。  
> **目标**：用少量稳定的 **AnswerPlan** 类型收口「哪个最低/最高/为什么/差异多大」等问法，**避免**为每种口语写 `message.contains(...)`。  
> **Composer**：只根据 AnswerPlan + Tool 返回说人话；**禁止**心算毛利率（与 `harness-composer-architecture.md` 一致）。

---

## 1. 与 Harness 分层的关系

```text
AiResolvedQueryContext（含 dataScope、timeWindow、structuredIntentDetail、mentionedDishName）
    → DishProfitAnalysisTool 参数（必须可追溯到 Context）
    → ToolResult / buildInsight 衍生结构（事实）
    → AnswerPlan（本轮回答任务类型 + 选行/焦点载荷）
    → Composer（自然语言，不重新计算指标）
```

全局分层说明见：**`docs/ai/harness-composer-architecture.md`**。

---

## 2. AnswerPlan 类型（第一版）

下列为**业务任务类型**枚举，**不是**固定话术。同一类型下，Composer 仅展开 Tool 已提供的行与字段。

| AnswerPlan | 含义（业务） | 典型用户问法（归一后） |
|------------|--------------|------------------------|
| `DISH_PROFIT_OVERVIEW` | 时段+范围内菜品毛利综合透视 | 「这个月菜品毛利怎么样」「集团/某店菜品毛利」 |
| `DISH_LOWEST_MARGIN` | 毛利最低（或表现最差）排行/焦点菜 | 「哪个菜品毛利最低」「哪道菜不赚钱」「哪个拖后腿」 |
| `DISH_HIGHEST_ACTUAL_COST` | 实际出库成本（到菜）偏高排行 | 「哪个菜品实际成本最高」 |
| `DISH_ACTUAL_OUTBOUND_COST` | 强调实际出库成本口径的说明或单品 | 「出库成本是多少」「某菜实际成本」（语义对齐 type1+2+3 整单/单份时以服务端字段为准） |
| `DISH_PROFIT_REASON` | 单品毛利偏低的原因型叙述 | 「核桃芽菜西芹为什么毛利低」 |
| `DISH_COST_GAP` | 理论 vs 实际成本差异最大 | 「哪个菜理论和实际差异最大」 |

**正确做法**：多种问法 → 同一 `structuredIntentDetail`（或等价 wire）→ 同一 **AnswerPlan** → 由 **Tool 行数据排序/筛选** 得到焦点菜 → Composer 只写解释性文字。

**错误做法**：`if (message.contains("哪个菜品毛利最低")) { ... }` 再复制多份近义词分支。

---

## 3. 从 structuredIntentDetail 到 AnswerPlan（映射原则）

`AiResolvedQueryContext` 已包含：

- `queryIntent.structuredIntentDetail`（wire 字符串，经 `AiQuerySemanticLexicon` 可在 Debug 中显示为枚举名）
- `mentionedDishName`：点名菜品
- `dishProfitMetricType`：由 structured 推导的指标类别（Harness 用）

**建议**：在 **Resolver / 规划层** 将 wire 归一到上表 AnswerPlan；若 wire 缺失但 LLM/规则识别为排行类，仍应写入**单一** structured 码，避免 Agent 内散落字符串判断。

Debug 字段参考：`AiHarnessResolvedContextSummarizer` 的 `dishProfitStructuredDetail`。

---

## 4. 选行与载荷（服务端职责）

对 `DISH_LOWEST_MARGIN`、`DISH_HIGHEST_ACTUAL_COST`、`DISH_COST_GAP` 等：

1. 使用 `buildInsight` 返回的 `dishes`（或图内已映射的 `dishRows`）作为**唯一**排序数据源。  
2. 排序键必须与**旧版报表**一致，例如（具体字段以 `GbDepFoodBusinessInsightServiceImpl` / 行 Map 为准）：  
   - 最低毛利：综合实际毛利率 `blendedGrossMarginRateOnListPrice` 或对应的可解析数值、或毛利额。  
   - 最高实际成本：`actualCostAmount` / `actualCostTotalAmount123` 等 **服务端已算**字段。  
   - 最大成本差：`diffCostAmount`、`absDiffCostAmountSum` 等。  
3. 输出 **AnswerPlan payload**（建议在 Run 状态中可序列化）：  
   - `planType`、`focusDishIds` 或 `focusRows`（浅拷贝关键字段）、`sortKey`、`topN`。  
4. Composer **只读**该 payload + 原始 summary，**不**重新排序。

单菜原因型 `DISH_PROFIT_REASON`：优先用 `mentionedDishName` 过滤到单行，再结合 `riskReason`、`grossMarginLevel`、配料级字段（若后续 Tool 展开）生成计划段落，仍不心算毛利率。

---

## 5. 出库分型（与毛利成本口径）

与项目统一口径一致：

| type | 含义 | 用户可见称呼 |
|------|------|----------------|
| type1 | 生产耗用 / 制作菜品正常消耗 | 生产耗用 |
| type2 | 废弃 / 过保鲜期废弃 | **废弃**（**不要**叫损耗） |
| type3 | 损耗 / 丢失、破损、自然损耗 | **损耗**（**不要**叫废弃） |
| type4 | 退货 | 退货（单独口径） |

**菜品实际毛利/综合毛利率**默认成本侧为 **type1+type2+type3**；**type4 退货**不默认纳入「菜品实际毛利成本」（见 legacy 复盘）。偏差率 **`devianceRate`**：分子**仅 type1**，不把 type2/type3 放进分子。

---

## 6. 字段语义与 Composer 用法

下列字段名以 **`buildInsight` / 报表行** 为主；SSE 卡片可能映射为 `AiDishProfitDishBrief` 的 `salesAmount`、`theoreticalCost`、`actualCost`、`grossProfitRate` 等——**Composer 以最终注入 JSON 的键为准，数值含义以下表为准**。

### 6.1 核心金额与收入

| 字段（典型） | label | meaning | composer_usage |
|--------------|-------|---------|------------------|
| `actualRevenue`（及汇总） | 菜品标价销售额 | 当前时间窗与部门范围内，菜品销量 × **部门菜品标价**得到的销售额（具体以服务端实现为准） | 可展示；**不得**用别的方式重算分母 |
| `theoryCostAmount` | 理论成本 | 配方/BOM、销量、扣库均价链汇总的理论成本 | 可展示；与 legacy 一致 |
| `actualCostAmount` | 实际出库成本（整菜行常见口径） | 出库核销分摊到菜品的实际成本；默认含 **type1+type2+type3**，**不含 type4**（除非接口单独说明） | 可展示；**禁止** Composer 用「心算」从配料反推 |
| `actualCostTotalAmount123` / `actualCostPerPortion123` | 单份或汇总「1+2+3」成本 | 服务端已算好的单份/汇总实际成本口径 | 展示时引用原文字段或格式化值；不自行换算 |
| `diffCostAmount` | 实际与理论成本差异 | `actual` 与 `theory` 的金额差（符号以接口为准） | 用于「差异大」类 AnswerPlan；不手算 |

### 6.2 毛利率与档位（必须直接使用服务端值）

| 字段（典型） | label | meaning | composer_usage |
|--------------|-------|---------|------------------|
| `grossMarginRateTheoryOnListPrice` | 理论毛利率（对标价） | 基于理论成本与标价收入的服务端比率 | **直接使用**格式化字符串或百分数；不心算 |
| `blendedGrossMarginRateOnListPrice` | **综合实际毛利率** | 旧版权威字段：对标价收入，成本为 **type1+2+3** 等综合口径 | **必须与 T±F、经营诊断对拍时使用本字段**；禁止心算 |
| `grossMarginRateOnListPrice` | 仅 type1 相关展示毛利率 | 多与「仅生产」口径相关；**不等于** `blended…` | 仅在 AnswerPlan 明确要求「仅生产口径」时引用；避免与 blended 混谈 |
| `grossMarginLevel` | 毛利档位 | 相对父级标准带（T±F）的档位 | 可引用；不自己判断阈值 |

### 6.3 偏差率与结构指标

| 字段（典型） | label | meaning | composer_usage |
|--------------|-------|---------|------------------|
| `devianceRate` | 偏差率 | **仅 type1** 分摊相对理论用量；**不含** type2/type3 进分子 | 说明时勿与「制作率」「损耗率」混淆；见 `ai-data-field-lexicon.md` |
| `scopeOutboundSubtotals` 等 | 区间出库结构 | type1/2/3 金额及占比等 | 用于总览上下文，不单菜心算 |

---

## 7. 与现有 DTO / Composer 提示的衔接

- `AiDishProfitOverviewResult`：`summary`、`queryScopeBanner`、`topProfitDishes` / `lowProfitDishes` / `costDataIncompleteDishes`、`grossProfitRateUncertain` 等 —— 总览型 **AnswerPlan** 应与之对齐。  
- `AiDishProfitDishBrief`：行内 `grossProfitRate`、`riskReason` 等多来自旧版格式化字段 —— **排行/原因类 AnswerPlan** 应对齐这些行结构后再交给 `DISH_PROFIT_COMPOSER_SYSTEM` 或后续分面提示。

引入 AnswerPlan 后建议：

1. 在注入 Composer 的 JSON 顶层增加 `answerPlan: { "type": "DISH_LOWEST_MARGIN", "focus": [...] }`（示例）。  
2. 在系统提示中增加：**仅就 `answerPlan.focus` 中的菜展开数字；其余菜名不得编造**。  
3. **不**在 Java 中为每个新口头禅加 `contains`；改增 `structuredIntentDetail` 枚举 + AnswerPlan。

---

## 8. 当前明确不要做的事

- **不要**修改采购链路、出库基础链路（阶段已通过部分）。  
- **不要**重写 `buildInsight` / 成本分摊算法。  
- **不要让** Composer 自己算毛利率或成本差。  
- **不要**恢复混合语义字段 `queryDepartmentIds` 作为对外主字段。  
- **不要**无限扩展 `DishProfitAgentNode` 的 `isXxxQuestion()` / `shrinkToYyyPresentation()` **模式**；新需求走本文 AnswerPlan。

---

## 9. 相关文档与代码

| 资源 | 路径 |
|------|------|
| 字段与 API | `docs/gb-dish-cost-analysis-frontend.md`、`docs/gb-dish-cost-allocation-model.md` |
| Harness 总架构 | `docs/ai/harness-composer-architecture.md` |
| Composer 约束 | `src/main/resources/ai-prompts/composer/dish_profit.v1.md` |
| Tool | `DishProfitAnalysisTool.java` |
| Agent（迁移参考） | `DishProfitAgentNode.java` |
| Composer | `StubAnswerComposerNode.java`（`DISH_PROFIT_COMPOSER_SYSTEM`） |

---

## 10. Run 状态中的 AnswerPlan JSON 示例

以下示例为 **Run / Replay / Debug** 中与 `AiRunState.dishProfitAnswerPlan` 及 `answer_delta.data.dishProfitAnswerPlan` 对齐的标准形态示例（数值为演示占位，真值均以 `buildInsight` / Tool 返回为准）。  
**禁止**在 JSON 中恢复 `queryDepartmentIds`；范围仍以 `resolvedQueryContextSummary.queryScopeKind`、`queryStoreIds`、`expandedSqlDepartmentIds` 等为准。

### 10.1 共性约定

| 键 | 说明 |
|-----|------|
| `answerPlan.type` | 与本文 §2 AnswerPlan 枚举一致：`DISH_LOWEST_MARGIN`、`DISH_HIGHEST_ACTUAL_COST`、`DISH_PROFIT_REASON` |
| `scopeLabel` | 用户可读范围（如「汀兰餐厅（单店）」「集团下属 N 家门店合并」），来自 `AiDishProfitOverviewResult.scopeName` 或 `queryScopeBanner`，**非** SQL ID 列表 |
| `timeLabel` | 时间窗可读标签，与 `AiTimeWindowTextFormatter` / `statStartDate`～`statEndDate` 一致 |
| `sortKey` / `sortDirection` | **服务端**选行时使用的排序字段与方向；`DISH_PROFIT_REASON` 为单菜焦点时可都为 `null` |
| `focusRows` | **主答**菜品行；元素为 `buildInsight` 行抽取字段（浅拷贝），一行一对象 |
| `secondaryRows` | 排行类 **建议** 附 2～3 行便于 Debug 对比；原因类 **不需要**（空数组） |
| **Composer 仅可读** | `answerPlan` 全对象、`focusRows` / `secondaryRows` 内已出现字段、以及既有的 `summary` / `queryScopeBanner`（若与 `focusRows` 冲突以 **focusRows** 为准）；**不得**读取其它菜品行来编造答案；**不得**自行排序或心算毛利率 |
| **Debug / Replay 建议展示** | `answerPlan.type`、`scopeLabel`、`timeLabel`、`sortKey`、`sortDirection`、`topN`、`focusRows`、`secondaryRows`、`structuredIntentDetail`（wire/摘要）、`mentionedDishName`（原因类）、以及 Harness 既有 `queryScopeKind`、`queryStoreIds`、`expandedSqlDepartmentIds`、`startDate`/`endDate` |

### 10.2 场景 1：哪个菜品毛利最低？

`secondaryRows`：**需要**（次低 2～3 行，便于核对排序）。

```json
{
  "answerPlan": {
    "type": "DISH_LOWEST_MARGIN",
    "scopeLabel": "汀兰餐厅（单店）",
    "timeLabel": "2026-04-01 至 2026-04-30",
    "sortKey": "blendedGrossMarginRateOnListPrice",
    "sortDirection": "ASC",
    "topN": 1,
    "focusRows": [
      {
        "dishId": "120883",
        "dishName": "椒麻鸡",
        "salesQuantity": "128",
        "actualRevenue": "8960.00",
        "theoryCostAmount": "5120.00",
        "actualCostAmount": "7420.50",
        "actualCostTotalAmount123": "7420.50",
        "blendedGrossMarginRateOnListPrice": "17.18%",
        "grossMarginRateTheoryOnListPrice": "42.86%",
        "diffCostAmount": "2300.50",
        "grossMarginLevel": "BELOW",
        "riskReason": "实际成本明显高于理论用量成本，建议核对出库与配方",
        "devianceRate": "76.2%"
      }
    ],
    "secondaryRows": [
      {
        "dishId": "120901",
        "dishName": "凉拌木耳",
        "salesQuantity": "56",
        "actualRevenue": "1680.00",
        "theoryCostAmount": "420.00",
        "actualCostAmount": "890.20",
        "actualCostTotalAmount123": "890.20",
        "blendedGrossMarginRateOnListPrice": "47.01%",
        "grossMarginRateTheoryOnListPrice": "75.00%",
        "diffCostAmount": "470.20",
        "grossMarginLevel": "IN_BAND",
        "riskReason": "",
        "devianceRate": "81.0%"
      }
    ]
  }
}
```

### 10.3 场景 2：哪个菜品实际成本最高？

`secondaryRows`：**需要**（次高金额 2 行）。

```json
{
  "answerPlan": {
    "type": "DISH_HIGHEST_ACTUAL_COST",
    "scopeLabel": "AAA 门店（单店）",
    "timeLabel": "2026-04-01 至 2026-04-30",
    "sortKey": "actualCostAmount",
    "sortDirection": "DESC",
    "topN": 1,
    "focusRows": [
      {
        "dishId": "98102",
        "dishName": "招牌水煮鱼",
        "salesQuantity": "203",
        "actualRevenue": "14210.00",
        "theoryCostAmount": "6100.40",
        "actualCostAmount": "9850.00",
        "actualCostTotalAmount123": "9850.00",
        "blendedGrossMarginRateOnListPrice": "30.68%",
        "grossMarginRateTheoryOnListPrice": "57.07%",
        "diffCostAmount": "3749.60",
        "grossMarginLevel": "IN_BAND",
        "riskReason": "实际成本明显高于理论用量成本，建议核对出库与配方",
        "devianceRate": "72.5%"
      }
    ],
    "secondaryRows": [
      {
        "dishId": "98120",
        "dishName": "毛血旺",
        "salesQuantity": "88",
        "actualRevenue": "7040.00",
        "theoryCostAmount": "2900.00",
        "actualCostAmount": "5100.00",
        "actualCostTotalAmount123": "5100.00",
        "blendedGrossMarginRateOnListPrice": "27.56%",
        "grossMarginRateTheoryOnListPrice": "58.81%",
        "diffCostAmount": "2200.00",
        "grossMarginLevel": "BELOW",
        "riskReason": "",
        "devianceRate": "70.0%"
      }
    ]
  }
}
```

### 10.4 场景 3：核桃芽菜西芹为什么毛利低？

`sortKey` / `sortDirection`：**null**（非排行）；`secondaryRows`：**不需要**（空数组）。`mentionedDishName` 应在 Harness 摘要层与解析上下文一并展示。

```json
{
  "answerPlan": {
    "type": "DISH_PROFIT_REASON",
    "scopeLabel": "集团下属 3 家门店合并",
    "timeLabel": "2026-04-01 至 2026-04-30",
    "sortKey": null,
    "sortDirection": null,
    "topN": 1,
    "focusRows": [
      {
        "dishId": "110045",
        "dishName": "核桃芽菜西芹",
        "salesQuantity": "42",
        "actualRevenue": "2016.00",
        "theoryCostAmount": "680.00",
        "actualCostAmount": "1510.00",
        "actualCostTotalAmount123": "1510.00",
        "blendedGrossMarginRateOnListPrice": "25.10%",
        "grossMarginRateTheoryOnListPrice": "66.27%",
        "diffCostAmount": "830.00",
        "grossMarginLevel": "BELOW",
        "riskReason": "实际成本明显高于理论用量成本，建议核对出库与配方",
        "devianceRate": "65.3%"
      }
    ],
    "secondaryRows": []
  }
}
```

### 10.5 文档与阶段状态

- **Harness Composer 架构文档**（`harness-composer-architecture.md`）：已通过。  
- **Dish Profit AnswerPlan 文档**（本文）：初版已通过；§10 JSON 示例已补齐。  
- **下一步**：按 §10 契约小步落地 Java（`DishProfitAnswerPlan` → `AiRunState` → Agent 生成 → Debug/Replay → `StubAnswerComposerNode`）；**第一批验收**仅 §10.2～§10.4 三句问法，扩展问法后续迭代。

---

*版本：§10 与 `DishProfitAnswerPlan` Java 载荷对齐；字段名以 `buildInsight` dishes 行及本文 §6 为准。*
