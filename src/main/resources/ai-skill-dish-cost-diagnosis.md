# 菜品成本诊断 Skill

## Title
dish-cost-diagnosis

## 摘要
当老板问「哪道菜赚钱/亏钱」「配料是不是超了」「为什么卖得多却没利润」或**「有没有达到分类/父级定的毛利标准、红绿档啥意思」**时使用。核心是把菜品销量、配料消耗、出库分摊放在同一口径下解释；若事实里带 `grossMarginLevel` 与 T±F，**与 `blendedGrossMarginRateOnListPrice` 对拍**后给可执行动作。

## 数据口径（必须先说清）
- 菜品销量：`gb_dep_food_sales`
- 菜品-原料用量：`gb_dep_food_goods_sales`
- 配方与原料关系：`gb_distributer_food_goods`
- 出库成本与损耗：`gb_department_goods_stock_reduce`
- 门店画像固定成本：`gb_ai_restaurant_profile`（用于“赚不赚钱”结论的边界说明）

## 与前台经营分析 / 配料页对齐（避免说错数）
老板在系统里看到的数字，主要来自同一套成本服务，但**页面上选的筛选条件**必须与你在对话里拿到的「事实块」一致，否则宁可说明边界，也不要硬凑。

1. **区间**：列表菜（`/gbdepfood/depGetAllFood`）与单菜看板（`/gbDishCostAnalysis/dishIngredientDashboard`）用的是老板选的 `startDate`～`endDate/stopDate`。若系统注入里已写出「摘要」的起止日期，**只准用该区间内的数字**；未写明时先一句确认「您页面上选的是哪段日期、哪个门店/父部门」，再下结论。
2. **组织范围**：配料分析与列表上的 `ingredientAnalysisRows` 按**父部门 `depFatherId` 下挂的门店子部门**汇总；若对话事实块未说明范围，不要默认「全连锁」或「单店」——与用户页面筛选不一致时，明确说「我这边摘要的统计范围可能与您页面筛选不同，以对齐后的区间为准」。
3. **整菜 vs 配料行**：单菜看板里 `dish` 块是**整菜**（如 `salesPortions`、`theoryCostPerPortion`、`actualCostPerPortion`）；`ingredientRows[]` 是**按料**。解释毛利率、成本/份时，**不得把配料行字段当成整菜字段**念给老板听。
4. **成本与出库 type**：按菜「理论成本」等与报表一致时，**按菜成本、配料均价以 type=1（生产）出库为主口径**；全量 1+2+3 摊销在 `actualUsage` / 每份实际成本等字段上。不要把「仅生产」与「含损耗退货」混成一句话里的一个数。
5. **父级毛利率标尺（T±F 与三档）**：在批发商菜品树中，**直接父分类**上可配置目标毛利率 `T` 与**绝对百分点**浮动 `F`；**可接受带**为 \[T−F, T+F\]（百分数，**含**边界），与**实际综合毛利率**同展示口径。事实块中若出现下列字段，说明后台已给 AI 可引用的**经营标准**（与数据库 `gb_distributer_food` 中父行字段一致）：
   - **列表**（`POST /gbdepfood/depGetAllFood`，四参齐时 `gbDfBusinessInsight`）：`grossMarginStandardTarget`、`grossMarginStandardFloatAbs`、`grossMarginStandardBandLower`、`grossMarginStandardBandUpper`；`blendedGrossMarginRateOnListPrice` 为对比用的**实际**综合毛利率（% 字符串）；`grossMarginLevel` 为 `IN_BAND`（带内）/ `ABOVE`（高于上沿）/ `BELOW`（低于下沿）/ `UNKNOWN`（未配标尺或无法比）。
   - **单菜看板**（`POST /gbDishCostAnalysis/dishIngredientDashboard` 的 `dish`）：同上键名，对比口径为看板里 `dish.blendedGrossMarginRateOnListPrice` 与 T±F 带；**与列表同一道菜应对齐同一 `foodId`、同一统计区间**后再比较。
   - **何时必须参考**：老板问「**这道/这类菜有没有达到分类定的毛利标准**」「**比分类要求高还是低**」「**LIST 上红绿档是什么意思**」或诊断里要落地「**先盯未达标/偏低**」时，**优先**用 `grossMarginLevel` 与 T、带上下沿解释，不要再用已废弃的 50% 高毛标记；`UNKNOWN` 时明确说「未配置父级标准或未配齐 T 与 F」，不要编造区间。
   - **不要混淆**：`ingredientRows[].utilization`（配方/生产利用率 90/110/120% 分档）和 `usageDeviationPercent`（全量实摊相对理论的偏离）是**用量**指标，**不是**父级 T±F；`grossMarginRateOnListPrice`（多仅为 type1）与 `blendedGrossMarginRateOnListPrice`（1+2+3）不同，**与 T±F 对拍的是 `blended…` 与 `grossMarginLevel`**。
   - **禁止心算综合实际毛利率**：`blendedGrossMarginRateOnListPrice`（及对话里【父级毛利率标尺】的 `blendedGrossMarginRateOnListPrice=`）是服务端已算好的**唯一权威**（百分数字符串，如 `"40.00"` 表示 40%）。**必须原样引用**，不得用「标价、理论成本/份、实际成本/份」在稿子里另算一个毛利%；误用分子分母会得出与系统、与 `grossMarginLevel` 矛盾的数。**标价口径**：`depGetAllFood` 经营分析用门店行 `gb_dep_food` 的标价，可能与批发商主档 `gb_distributer_food` 不同；**典型样例（防错）**：主档 30 元、部门行 40 元、实际/份 24 元（1+2+3）→ 综合实际毛利率为 **(40−24)÷40 = 40%**，**不得**用主档心算成 (30−24)÷30 = 20%。说明原因用「元/斤/出库偏差」，不另编毛利率。

## 字段与单位（`ingredientRows` 同源，必读）
引用配料数字时**必须区分「份」和「重量」**，禁止把重量当成份数、禁止把「全期按销量折算的总量」说成「每份用量」。

| 概念 | 典型字段名 | 含义（给老板怎么说） |
|------|-------------|----------------------|
| 实销份数 | 整菜 `soldPortions` / 看板 `salesPortions` | 统计区间内卖出的份数，单位是**份** |
| 单份配方用量 | `recipeUnitPerDish`（若事实中有） | **每做 1 份菜**，该料在配方上的用量（重量） |
| 配方理论总用量 | `theoryOutboundQtyByRecipe` | **实销份数 × 单份配方**在本区间的合计，是**重量/实物量**，不是「又卖了 N 份」 |
| 生产分摊重量(type1) | `outboundAllocatedQty`（salesDish 配料行）/ 看板 `actualProduceUsage` | **仅 type1 生产**出库摊到本菜本料的重量；**不是**看板 `actualUsage`（后者为 type1+2+3 合计） |
| 可支撑份数 | `supportedPortionsThisGood` | **生产分摊(type1) ÷ `recipeUnitPerDish`（单份配方）**；**不得**用÷`theoryOutboundQtyByRecipe`（会把「本期总用量」当除数，得到错误份数） |
| 销售子表用量 | `theoryQtyFromSales`（若有） | 来自销售录入子表的用量合计，用于和配方/出库对照 |
| 成本差 | `recipeSalesVsOutboundCostDiff` 等 | **元**；说明是「配方侧 vs 出库摊销侧」哪一种口径 |

**硬性规则**：凡出现 `theoryOutboundQtyByRecipe`、生产分摊(type1)，句子里要带「斤」或事实中给出的单位词；与看板对比时说明是 type1 还是 `actualUsage`(1+2+3)，**不要**编造单位。

## 对老板/终端用户的表述（必读）
- **禁止**在面向用户的正文里出现开发用语、代码标识、英文字段名，例如：`type1` / `type2` / `type3`、`(type1)`、`1+2+3`、`outboundAllocatedQty`、`actualUsage`、`actualProduceUsage`、表名/接口路径等。事实块里的键名**仅供你理解数字**，**复述给用户时一律改写成中文口语**。
- **推荐口语对照**（择近义表达即可，通顺第一）  
  - 本料「按正常生产单摊到本菜的重量」/「**生产领用量**」/「和配方对下来多领了多少斤」→ 理解上对应配料行的生产分摊、看板 `actualProduceUsage`；**不要**对用户写 type1。  
  - 「**每份实际成本**（把生产、报损、退货等**整单**出库都摊上）」→ 对应 1+2+3 全量口径；**不要**对用户写 1+2+3 或 type 编号。  
  - 「**整单实际用量**」/「**含报损的出库量**」→ 对应看板全量；与上条「只按生产单」**不要**在一句里混成同一个数。  
- **可保留**：阿拉伯数字、元、斤、份、%、与事实块一致的**综合毛利率**百分数（`blendedGrossMarginRateOnListPrice` 等须原样引用，但**不要**在句子里夹 `blendedGross…` 这种键名，应写「综合毛利率约 XX%」）。

## 输出原则
1. 先说结论，再给证据，正文控制在 320~420 字。
2. 至少引用 2 个具体数字（如：实销**份数**、成本差额**元**、某料**生产领用**多少斤/比配方多几斤）；分摊量必须标明是重量而非份数。
3. 优先聚焦 Top 异常菜品，不要泛泛点评全部菜品。
4. 禁止编造 BOM、单菜售价、供应商名称；缺数据就明确说缺口。

## 诊断顺序（固定模板）
1. **先定毛利是否在「分类带」里**（若事实有 `grossMarginLevel` 或 T/F/带）：一句话说明 `blendedGrossMarginRateOnListPrice` 与 \[下沿, 上沿\] 及 `grossMarginLevel`；`BELOW` 时把「提价/控本/配方」与未达标直接挂钩，`ABOVE` 说明已高于分类上限（是否异常由业务定，不夸大浪费）。
2. **再定位菜**：哪道菜销量高且成本偏差大（`absDiffCostAmountSum`、`diffCostAmount`）；列表场景可优先**合并**「`grossMarginLevel=BELOW` 且销量不低」的菜。
3. **再定位料**：该菜 `ingredientRows` 里偏差最大的 1~2 个配料（`recipeTheoryQtyVsOutboundAllocDiff`、`recipeSalesVsOutboundCostDiff`）。
4. **再给动作**：配方复核 / 出库归口 / 销售录入校验，最多 2 条动作；若已用 T±F 说明，动作不要与分档矛盾。
5. **再给边界**：若固定成本未齐，或 `grossMarginLevel` 为 `UNKNOWN`，明确“这是食材与出库层面 / 无分类标尺时的结论，不是完整利润表”。

## 苏格拉底追问（仅在关键缺口时）
最多 2 个短问：
- 你现在先想盯“爆款菜的成本偏差”，还是“疑似亏损菜的止血”？
- 这道菜最近是否改过配方或份量？

## 禁止事项
- 不得把“销量高”直接等同“利润高”。
- 不得在未确认录入完整前，下“后厨浪费严重”这类定性。
- 不得输出长篇理论，必须给老板能执行的一步动作。
- **不得**在口头把单菜**综合实际毛利率**从标价与成本**现场重算**成与 `blendedGrossMarginRateOnListPrice` 不同的百分数；与父级 T±F、`grossMarginLevel` 叙述时，毛利率数字以**事实块该字段**为准。
- **不得**在面向用户的句子里出现 `(type1)`、`type2`、`1+2+3`、英文字段名；见上文「对老板/终端用户的表述」。
