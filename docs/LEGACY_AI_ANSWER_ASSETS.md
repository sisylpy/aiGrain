> 所有涉及 AI 多智能体、经营分析、采购、库存、菜品毛利、权限、组织范围、时间范围、多轮追问的改动，必须先阅读 `docs/AI_AGENT_DEVELOPMENT_GUIDE.md`。

> 任何涉及组织范围、门店、部门、集团汇总、权限范围、经营看板查询的改动，必须先阅读 `docs/DOMAIN_ORG_MODEL.md`。本项目中 `distributerId` 是集团/配送商主体 ID；`gb_department.gbDepartmentFatherId = 0` 的记录才是门店；子部门需要归一化到所属门店。

# 旧版 AI 回答资产清单（Legacy Answer Assets）

> **目的**：把旧版会话链路里已经成熟的数据查询、结构化指标与 skill 话术，对齐到新多智能体（`AiRunService` / `BusinessGraph`）的编排点，避免只靠空 prompt 泛泛建议。  
> **说明**：本节为盘点结论；新旧差异与接入缺口见文末「与新链路差距」。

---

## 旧版会话主链路（事实 + Skill + 模型）

| 工件 | 位置 | 作用 |
|------|------|------|
| HTTP | `GbAiChatController` | `/ai/chat/send`、`/stream`、`/topics` |
| 编排中枢 | `GbAiChatServiceImpl` | Skill 初选/消歧、`callDeepSeekApi`、**事实块拼装**（本月营业额、采购、核销、画像等）、主回答 system/user 组装 |
| Skill 路由 | `SkillRouter` + `SkillRouteCatalog` + `SkillRouteFallback` | 关键词/规则补全所选 `ai-skill-*.md` |
| Skill 正文 | `src/main/resources/ai-skill-*.md` | 成本总控、菜品诊断、算账驾驶舱、营销营收等行文规则与数据口径 |

**旧版不存在单独「AnswerComposer Java 类」**：回答风格与硬性规则主要在 **Markdown skill** + **GbAiChatServiceImpl 注入块**里完成。

---

## 经营口径 REST（与 AI 同源数据，但更「看板完整」）

| 工件 | 位置 | 作用 |
|------|------|------|
| Controller | `GbAiDailyRevenueController#getStats` | `GET .../ai/daily-revenue/stats/{departmentId}` |
| 原始聚合 | `GbAiDailyRevenueService#getStatsByDepartmentId` | Mapper 聚合：日营收多字段（`_` 命名 map） |
| 看板装配 | `GbAiDailyRevenueDashboardServiceImpl#buildStatsDashboard` | **中文键扁平 stats**：营业额区间、天数、日均、订单、客单、券/平台费相关、退款、外卖分拆、核销成本分拆、利润率/经营净利率、盈亏状态文案等 |

DTO 语义参考：`GbAiDailyRevenueStatsDTO`（与接口文档对应的基础/外卖/成本/毛利率/盈亏字段说明）。

新链路 **`business_overview_path`** 已用 **`BusinessOverviewQueryTool`** 直接复用上表 **`getStatsByDepartmentId` + `buildStatsDashboard`**；整机卡片见 SSE **`answer_delta.data.businessOverview`**（`AiBusinessOverviewResult`）。**集团管理端**多门店 rollup 仍为后续能力（详见 **`docs/TODO_MULTI_AGENT.md` §集团经营概览聚合口径**）；当前广角失败场景应读 Tool 信封 **`failureKind` / `note` / `anomalyHints`**，勿误译为单店「画像未配置」。

---

## 菜品毛利 / 菜品经营分析（旧版成熟服务）

| 工件 | 位置 | 作用 |
|------|------|------|
| Service | `GbDepFoodBusinessInsightService` / `GbDepFoodBusinessInsightServiceImpl` | `buildInsight`、`attachToFoodRows` — 菜品销量、标价收入、出库分摊、分类 T±F 带、`grossMarginLevel` 等 |
| Controller | `GbDepFoodController`（如 `depGetAllFood` 等） | 列表 + 经营分析字段挂接 |
| 单菜看板 | `GbDishCostAnalysisService` / `GbDishCostAnalysisController` | `dishIngredientDashboard` 等整菜 vs 配料行口径 |

---

## 问题类型 → 旧版资产映射（表格）

以下为 **「店长式问法」→ 应对齐的新版 Graph 支线**（与实现现状一致：`businessOverviewPath`、`dish_profit_path`、`CostDiagnosis` 链路、`dish_sales`/`gross_margin` 工具）。

| 旧版问题（示例） | 旧代码位置 | 数据指标（旧版一般有） | 旧回答特点 | 新版接入点 |
|------------------|------------|----------------------|------------|------------|
| 这个月经营怎么样 / 这个月生意怎么样 / 营业额怎么样 | `GbAiChatServiceImpl` 注入「本月营业额」等事实块；`ai-skill-profit-pilot.md` / `ai-skill-cost.md` / `ai-skill-revenue-boost.md` 视路由；**REST** `GbAiDailyRevenueController` + `GbAiDailyRevenueDashboardServiceImpl` | 统计天数、总/日均营业额、订单与客单价、优惠券/平台费口径、退款、外卖分项、核销成本拆分、利润率/经营净利率、盈亏状态、固定成本画像 | 先复述**具体数字**，再给 2～3 条判断与动作；数据不全时苏格拉底澄清 | **`business_overview_path`**：**应改用与 `buildStatsDashboard` 同级或等价字段**（或通过新 Tool 调同一 Service），不能只传 `RevenueQueryTool` 的精简 `rawStats` |
| 本月成本怎么样 / 成本分析 | `ai-skill-cost.md`；`GbAiChatServiceImpl` 注入库存核销、采购等；与 `gb_department_goods_stock_reduce` 口径一致 | 生产/损耗/损失/退货核销金额、占比、画像月租/工资、与营收对照 | 固定成本不全则**先补数**；禁止在流水不齐时吓老板 | **`CostDiagnosisAgentNode`** + `PurchaseQueryTool` / `StockReduceQueryTool` / `RevenueQueryTool` / `GrossMarginCalculatorTool`；**对齐**旧 skill 的「先照抄数字再结论」 |
| 菜品毛利怎么样 / 菜品分析 / 哪道菜赚钱 | `ai-skill-dish-cost-diagnosis.md`；`GbDepFoodBusinessInsightService`、`GbDishCostAnalysisService` | 销量、标价收入、理论/实际成本每份、`blendedGrossMarginRateOnListPrice`、`grossMarginLevel`、T±F 带 | 必须引用 **`blended…` 权威毛利率**禁止心算另编 %；中英字段名对用户脱敏 | **`dish_profit_path`**：**`dish_profit_analysis`**（`DishProfitAnalysisTool`→`GbDepFoodBusinessInsightService#buildInsight`）+ **`answer_delta.data.dishProfitOverview`**；泛经营链路仍可走 **`business_overview_path`** 内含 **`dish_sales_query`** + **`gross_margin_calculator`** |

补充（旧版有相关 skill，本轮用户要求**延后**）：  
| 钱花在哪 / 采购结构 | `ai-skill-procurement-structure.md` + `SkillRouteCatalog` 规则 | 采购金额结构、供应商、未结账 | 结构拆解 + 风险句 | Report/采购支线（**不在本阶段**） |

---

## 关键资源 Markdown（Prompt 资产文件名）

| 文件 | Title / 触发 |
|------|----------------|
| `ai-skill-cost.md` | 成本总控；与核销、画像、苏格拉底规则 |
| `ai-skill-dish-cost-diagnosis.md` | 菜品成本与父级毛利带 |
| `ai-skill-profit-pilot.md` | 算账驾驶舱 / 保本 / 这个月赚不赚钱 |
| `ai-skill-revenue-boost.md` | 提升营业额、营销（泛指问法先追问） |
| `ai-skill-procurement-structure.md` | 采购与应付 |

`GbAiChatServiceImpl` 中固定加载的文件名列表可参考源码常量（含上述若干项）。

---

## 与新链路（多智能体）的主要差距 —— 为什么这么问会「变淡」

1. **`RevenueQueryTool`**（`AiBusinessToolIds.REVENUE_QUERY`）当前只把 `getStatsByDepartmentId` 的 **`rawStats` + 极简派生（days、totalRevenue、avgDailyRevenue）** 放进信封；**订单数、客单价、优惠券、退款、环比同比、dashboard 的中文经营判断**均未进入 **`BusinessOverviewAgentNode`**。
2. **`BusinessOverviewAgentNode`** 从工具信封读的字段集合 **远小于** `GbAiDailyRevenueDashboardServiceImpl#buildStatsDashboard` 输出；Composer 侧的 DeepSeek prompt 若没有「可复制粘贴的数字块」，容易产生空泛建议。
3. 旧链路 **同一问句** 往往合并 **营业额事实 + 核销 + 画像 + profit-pilot/cost skill**；新链路 **`business_overview_path`** 与 **成本链路**拆开，须在经营支线显式拉回「旧看板等价指标」或通过单一聚合 Tool 收口。

---

## 后续接入建议（实现阶段备忘，非本文件交付范围）

1. **`business_overview_path`**：`RevenueOverviewTool` 或扩展现 Tool，内部调用 `GbAiDailyRevenueDashboardService.buildStatsDashboard`（或与 Controller 同源），把时间窗已由 `AiRunState` 解析好的 `departmentFatherId`、`start`/`stop` 传入；信封内给 **扁平中文键或稳定英文键**，供 Composer 与 `BusinessOverviewAgent` 消费。  
2. **DeepSeek**：在 composer 侧增加硬规则——**仅引用 toolResults 中显式字段**；缺项写「暂无」；禁止编造环比/同比若未提供。  
3. **`costInsightPath`**：对照 `ai-skill-cost.md` 检查 `CostDiagnosisAgent` 输出是否覆盖「先照抄注入块数字」的等价结构。  
4. ~~**`dish_profit_path`**~~：**第一版已接** **`DishProfitAnalysisTool`** + **`AiDishProfitOverviewResult`**/`dishProfitOverview`；仍可增强与 **dashboard API** 完全同字段的一页摘要。

---

## 菜品毛利 / 菜品分析旧版资产

以下为 **店长式「菜品毛利 / 透视」→ 旧版已实现能力** 与本版 Tool 收口对照。

| 旧版能力 | 代码位置 | 指标字段 | 回答特点 | 新版接入点 |
|---------|----------|---------|----------|------------|
| 菜品多维经营透视（销量/标价收入/BOM vs 出库 type1） | `GbDepFoodBusinessInsightServiceImpl#buildInsight` | `soldPortionsTotal`、`listPriceRevenue`、`theoryCostAmount`、`actualCostAmount`、`blendedGrossMarginRateOnListPrice`、`grossMarginRateOnListPrice` 等 | 按菜品列问题与高/低毛利；缺核销/配方须有「不可用」提示 | **`dish_profit_path`**：**`dish_profit_analysis`** → **`AiDishProfitOverviewResult`** → **`answer_delta.data.dishProfitOverview`** |
| 单菜 BOM / 成本看板 drill-down | `GbDishCostAnalysisService`、`GbDishCostAnalysisController` | 菜谱配料行、出库与理论差异（依报表） | 单菜归因、主料异常 | **`dish_profit_path`** 后续增强（问句可归一：**`narrowByUserHint`**） |
| Skill 行文约束 | `ai-skill-dish-cost-diagnosis.md` | — | 「先数字后结论」 | **`DISH_PROFIT_COMPOSER_SYSTEM`** |

---

### 强制规则（集团与部门锚点）

集团用户的 `departmentId` 通常是管理部门，不是门店。所有集团范围查询，包括经营概览、菜品毛利、库存、采购、报表，都必须优先根据 `distributerId` 找集团下 `gbDepartmentFatherId=0` 的门店列表，再按门店汇总，不允许直接把集团用户 `departmentId` 当门店 ID。

---

## 库房库存查询旧版资产

> **范围**：部门商品库存批次（`gb_department_goods_stock`）、核销流水（`gb_department_goods_stock_reduce`）、订货/库存预警编排（`GbDepartmentReorderReminderServiceImpl`）等与「库房视角」相关的可读 REST 与 Service。

### 核心数据表与实体

| 工件 | 说明 |
|------|------|
| `GbDepartmentGoodsStockEntity` | 库存批次：剩余重量/金额、入库日期、关联分销商商品、部门/父部门等 |
| `GbDepartmentGoodsStockReduceEntity` | 核销：出品、损耗、报损、退货等分型流水 |
| `GbDepartmentGoodsStockMapper.xml` | `queryGoodsStockByParams`、`queryGoodsStockListForMendianPeriod`、`queryGoodsStockCount`、`queryDepGoodsRestTotal`、`queryDepGoodsSubtotal`、`queryDepStockWeightTotal` 等聚合 |

### Service（查询与编排）

| Service | 作用 |
|---------|------|
| `GbDepartmentGoodsStockService` / `GbDepartmentGoodsStockServiceImpl` | 库存列表与汇总查询入口 |
| `GbDepartmentGoodsStockLedgerService` | 入库后记账、制作/损耗/退货/废弃等库存变动编排 |
| `GbDepartmentGoodsStockReduceService` | 核销分型汇总：`queryReduceAllTypesTotalOnDailyRevenueDays`（与日营收自然日对齐）、按类型金额/重量等 |
| `GbDepartmentGoodsStockQueryService` | Controller 侧库存列表/树形查询编排（与 AI Tool 可后续对齐） |
| `GbDepartmentReorderReminderServiceImpl` | 订货提醒：按批次剩余汇总展示库存等（可参考预警口径） |
| `GbAiDailyRevenueDashboardServiceImpl` | 经营看板中会引用核销 Service（经营链路与库房链路分离） |

### REST Controller（旧版页面同源）

| Controller | 路径前缀 | 典型能力 |
|------------|-----------|----------|
| `GbDepartmentGoodsStockController` | `gbdepartmentgoodsstock` | 分页库存商品树、`getGbStockPurGoods`、关联采购填充等 |
| `GbDepartmentGoodsStockReduceController` | `gbdepartmentgoodsstockreduce` | `getGoodsReduceWithDayData`（按日损耗+采购+原料）、`getGbGoodsCostStatistics`（成本汇总）、损耗命令入口 |
| `GbDistributerPurchaseGoodsController` | — | 采购入库写库存批次（与库房入库同源） |
| `GbReportController` | — | 报表侧引用库存/核销 Service |

### 与新链路对接关系

| 新链路 | 复用方式 |
|--------|----------|
| `warehouse_stock_overview_path` | **`WarehouseStockOverviewTool`**：`GbDepartmentGoodsStockService` 汇总 + 批次列表聚合 + `queryReduceAllTypesTotalOnDailyRevenueDays` |
| `stock_query` | **`StockQueryTool`**：`queryGoodsStockCount` / `queryDepGoodsRestTotal` / 区间内入库汇总（须 **`disId`**：已由 `BusinessToolExecutionNode` 下发） |
| `stock_reduce_query` | **`StockReduceQueryTool`**：同上核销分型汇总 |

**缺口（后续迭代）**：与旧版 **`GbDepartmentGoodsStockQueryService`** 完全一致的「库存列表页字段」、订货阈值 **`GbDepartmentReorderReminderServiceImpl`** 精确预警规则、独立「长期未动销」SQL（当前 AI 侧用「早于统计月起始仍有剩余批次」作盘点启发式）。

**集团库存汇总（AI Run）**：集团管理账号问「库存怎么样」等开放式问句时，Planner 走 **`GROUP_WAREHOUSE_STOCK_OVERVIEW`**，`WarehouseStockOverviewTool` 在 **`groupWarehouseStockAggregation=true`** 下按 `distributerId` 解析门店根并逐店聚合；SSE **`answer_delta.data.warehouseOverview`** 会携带 **`scopeType`、`visibleStoreCount`、`coveredStores`、`dataMissingStores`** 等字段（见 **`DOMAIN_ORG_MODEL.md` §12**）。

---

## 文档维护

盘点更新时请同步：实际操作代码引用（类名变更）、以及在 `docs/TODO_MULTI_AGENT.md` 「旧版 AI 回答资产迁移」 checklist 勾选状态。
