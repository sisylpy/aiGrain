# Mapper Inventory and SQL Ownership

> 本文档由 MAPPER-INVENTORY-AND-CONSOLIDATION-P1 盘点生成，并于 MAPPER-INVENTORY-DOC-P1 固化。
>
> **最后更新**: 2026-05-24
>
> **重要规则**: Cursor / WorkBuddy 新增 AI Tool 前，**必须先查本文档**，确认目标表已有 Mapper 和 SQL 口径。

---

## 1. 背景和原则

### 1.1 背景

当前项目存在两套 Mapper 历史：

1. **老微信小程序 / 管理端 Mapper**：`GbDepartment*`、`GbDistributer*`、`GbDepFood*`、`Nx*` 系列，承载微信小程序下单、采购管理、库存管理、报表查询等业务。
2. **AI Harness Mapper**：`GbAi*` 系列，由 Cursor 为 AI Tool / AnswerPlan / Agent 新建，承载营业额查询、采购概览、出库核销、菜品毛利分析、经营诊断等 AI 经营分析场景。

两套 Mapper 共存导致以下风险：

- SQL 口径分裂：同一指标（营业额、采购金额、出库金额等）存在两套 SQL，时间字段、门店范围、金额口径可能不一致。
- AI Tool 和老业务各查各的：后续 Cursor 不知道该复用哪套 Mapper。
- 重复 Mapper：同一张表被两套体系查询，甚至遗留 Dao 体系仍在运行。

### 1.2 原则

1. **Mapper 层不能像语义 legacy 一样直接删除**：删除 Mapper 前必须查 Controller / Service / Tool / XML 调用方。
2. **AI Tool 新增 SQL 前必须先查本文档**：确认目标表已有 Mapper 和 SQL 口径。
3. **业务表已有 Mapper 时，不能随手新建新的 AiXxxMapper**：优先复用现有 Mapper，可在其中新增 AI 专用方法。
4. **如果 AI 口径和老业务口径不同，必须记录时间字段、范围字段、金额/数量口径**：不能混用，必须文档化。

---

## 2. Mapper 总览

| 维度 | 数量 |
|---|---|
| Java Mapper 接口 | 52 |
| MyBatis XML 文件 | 34 |
| 遗留 Dao XML | 1 |
| `GbAi*` AI/Harness 专属 Mapper | 21 |
| 仅 BaseMapper CRUD 的 Mapper（无自定义 XML） | 18 |
| 有自定义 XML 的业务 Mapper | 16 |

---

## 3. AI/Harness 专属 Mapper

以下 Mapper 接口名以 `GbAi` 开头，仅服务于 AI Harness 体系。

| # | Mapper | XML | 主要表 | 有 XML | 用途 |
|---|---|---|---|---|---|
| 1 | `GbAiDailyRevenueMapper` | ✅ GbAiDailyRevenueMapper.xml | `gb_ai_daily_revenue` | ✅ (10 方法) | AI 日营业额查询，AI 经营分析营业额唯一数据源 |
| 2 | `GbAiConversationHistoryMapper` | ✅ GbAiConversationHistoryMapper.xml | `gb_ai_conversation_history` | ✅ (3+ 方法) | AI 对话历史记录 |
| 3 | `GbAiConversationMapper` | ✅ GbAiConversationMapper.xml | `gb_ai_conversation` | ✅ (2 方法) | AI 会话管理 |
| 4 | `GbAiConversationTurnMemoryMapper` | ✅ GbAiConversationTurnMemoryMapper.xml | `gb_ai_conversation_turn_memory` | ✅ (1 方法) | AI 会话轮次记忆 |
| 5 | `GbAiCouponPlanMapper` | ✅ GbAiCouponPlanMapper.xml | `gb_ai_coupon_plan` | ✅ (2 方法) | AI 优惠方案 |
| 6 | `GbAiKnowledgeMapper` | ✅ GbAiKnowledgeMapper.xml | `gb_ai_knowledge` | ✅ (3+ 方法) | AI 知识库 |
| 7 | `GbAiMessageMapper` | ✅ GbAiMessageMapper.xml | `gb_ai_message` | ✅ (3+ 方法) | AI 消息 |
| 8 | `GbAiRestaurantProfileMapper` | ✅ GbAiRestaurantProfileMapper.xml | `gb_ai_restaurant_profile` | ✅ (3 方法) | AI 餐厅画像 |
| 9 | `GbAiWorkflowMapper` | ✅ GbAiWorkflowMapper.xml | `gb_ai_workflow` | ✅ (2 方法) | AI 工作流定义 |
| 10 | `GbAiWorkflowRunMapper` | ✅ GbAiWorkflowRunMapper.xml | `gb_ai_workflow_run` | ✅ (3 方法) | AI 工作流运行实例 |
| 11 | `GbAiAdvisorMapper` | ❌ 无 | `gb_ai_advisor` | ❌ | AI 顾问，仅 BaseMapper CRUD |
| 12 | `GbAiAdvisorWorkflowMapper` | ❌ 无 | `gb_ai_advisor_workflow` | ❌ | AI 顾问工作流，仅 BaseMapper CRUD |
| 13 | `GbAiAgentRunMapper` | ❌ 无 | `gb_ai_agent_run` | ❌ | AI Agent 运行记录，仅 BaseMapper CRUD |
| 14 | `GbAiAgentStepMapper` | ❌ 无 | `gb_ai_agent_step` | ❌ | AI Agent 步骤记录，仅 BaseMapper CRUD |
| 15 | `GbAiConversationNotebookMapper` | ❌ 无 | `gb_ai_conversation_notebook` | ❌ | AI 会话笔记本关联，仅 BaseMapper CRUD |
| 16 | `GbAiConversationPinMapper` | ❌ 无 | `gb_ai_conversation_pin` | ❌ | AI 会话置顶关联，仅 BaseMapper CRUD |
| 17 | `GbAiConversationTagMapper` | ❌ 无 | `gb_ai_conversation_tag` | ❌ | AI 会话标签关联，仅 BaseMapper CRUD |
| 18 | `GbAiNotebookMapper` | ❌ 无 | `gb_ai_notebook` | ❌ | AI 笔记本，仅 BaseMapper CRUD |
| 19 | `GbAiTagMapper` | ❌ 无 | `gb_ai_tag` | ❌ | AI 标签，仅 BaseMapper CRUD |
| 20 | `GbAiWorkNoteMapper` | ✅ GbAiWorkNoteMapper.xml | `gb_ai_work_note` | ✅ (极小 XML) | AI 工作笔记 |
| 21 | `GbAiWorkPinMapper` | ✅ GbAiWorkPinMapper.xml | `gb_ai_work_pin` | ✅ (极小 XML) | AI 工作置顶 |

> **标记**: `ai-harness-active`

---

## 4. 老业务 Mapper

以下 Mapper 承载老微信小程序 / 管理端业务。

| # | Mapper | 主要表 | 业务域 | 标记 |
|---|---|---|---|---|
| 1 | `GbDepartmentOrdersMapper` | `gb_department_orders` | 营业额/订单 | old-business-active |
| 2 | `GbDepartmentGoodsStockReduceMapper` | `gb_department_goods_stock_reduce` | 出库/核销 | old-business-active |
| 3 | `GbDepartmentGoodsStockMapper` | `gb_department_goods_stock` | 库存 | old-business-active |
| 4 | `GbDepartmentGoodsDailyMapper` | `gb_department_goods_daily` | 库存日报 | old-business-active |
| 5 | `GbDepartmentDisGoodsMapper` | `gb_department_dis_goods` | 门店商品 | old-business-active |
| 6 | `GbDepartmentMapper` | `gb_department` | 部门/门店 | old-business-active |
| 7 | `GbDepartmentUserMapper` | `gb_department_user` | 门店用户 | old-business-active |
| 8 | `GbDistributerPurchaseGoodsMapper` | `gb_distributer_purchase_goods` | 采购 | old-business-active |
| 9 | `GbDistributerPurchaseBatchMapper` | `gb_distributer_purchase_batch` | 采购批次 | old-business-active |
| 10 | `GbDistributerFatherGoodsMapper` | `gb_distributer_father_goods` | 商品分类 | old-business-active |
| 11 | `GbDistributerFoodMapper` | `gb_distributer_food` | 菜品 | old-business-active |
| 12 | `GbDistributerFoodGoodsMapper` | `gb_distributer_food_goods` | 菜品配料 | old-business-active |
| 13 | `GbDistributerGoodsMapper` | `gb_distributer_goods` | 分销商品 | old-business-active |
| 14 | `GbDistributerPayListMapper` | `gb_distributer_pay_list` | 付款清单 | old-business-active |
| 15 | `GbDistributerStandardMapper` | `gb_distributer_standard` | 规格 | old-business-active |
| 16 | `GbDistributerSupplierPaymentMapper` | `gb_distributer_supplier_payment` | 供货商付款 | old-business-active |
| 17 | `GbDistributerAliasMapper` | `gb_distributer_alias` | 分销别名 | old-business-active |
| 18 | `GbDistributerMapper` | `gb_distributer` | 分销商 | old-business-active |
| 19 | `GbDistributerModuleMapper` | `gb_distributer_module` | 分销商模块 | old-business-active |
| 20 | `GbDistributerPayMapper` | `gb_distributer_pay` | 付款 | old-business-active |
| 21 | `GbDistributerUserMapper` | `gb_distributer_user` | 分销用户 | old-business-active |
| 22 | `GbDepFoodMapper` | `gb_dep_food` | 门店菜品 | old-business-active |
| 23 | `GbDepFoodSalesMapper` | `gb_dep_food_sales` | 菜品销售 | old-business-active |
| 24 | `GbDepFoodGoodsSalesMapper` | `gb_dep_food_goods_sales` | 菜品配料销售 | old-business-unknown |
| 25 | `GbReportMapper` | `gb_report` | 报表 | old-business-unknown |
| 26 | `NxGoodsMapper` | `nx_goods` | 农信商品 | old-business-active |
| 27 | `NxAliasMapper` | `nx_alias` | 农信别名 | old-business-active |
| 28 | `NxJrdhSupplierMapper` | `nx_jrdh_supplier` | 供货商 | old-business-active |
| 29 | `NxJrdhUserMapper` | `nx_jrdh_user` | 农信用户 | old-business-active |
| 30 | `NxStandardMapper` | `nx_standard` | 规格 | old-business-active |
| 31 | `SysCityMarketMapper` | `sys_city_market` | 城市市场 | old-business-unknown |

> **说明**: `old-business-active` 表示老业务明确有调用；`old-business-unknown` 表示可能老业务使用但调用链不清，需人工确认。

### 4.1 AI Harness 主链也在使用的老业务 Mapper（共享 Mapper）

以下老业务 Mapper 同时被 AI Harness 主链调用，属于**共享 Mapper**，修改时必须同时考虑老业务和 AI 口径：

| Mapper | 老业务域 | AI Harness 使用场景 |
|---|---|---|
| `GbDistributerPurchaseGoodsMapper` | 采购管理（小程序） | PurchaseOverviewTool 采购概览 |
| `GbDepartmentGoodsStockReduceMapper` | 出库/核销（小程序） | StockReduceQueryTool 出库统计 / DishProfitAnalysisTool 成本分摊 / WarehouseStockOverviewTool 出库汇总 |
| `GbDepartmentGoodsStockMapper` | 库存管理（小程序） | WarehouseStockOverviewTool 库存概览 |
| `GbDepartmentMapper` | 门店管理（小程序） | DishProfitAnalysisTool 子部门展开 / WarehouseStockOverviewTool 门店名查询 |
| `GbDepFoodMapper` | 门店菜品管理 | DishProfitAnalysisTool 门店菜品查询 |
| `GbDepFoodSalesMapper` | 菜品销售报表 | DishProfitAnalysisTool 菜品销量查询 |
| `GbDistributerFoodMapper` | 分销菜品管理 | DishProfitAnalysisTool 分销菜品查询 |

---

## 5. AI Harness Tool → Service → Mapper → SQL 表

### 5.1 Revenue（营业额）

```
RevenueQueryTool
  └→ GbAiDailyRevenueService
       └→ GbAiDailyRevenueMapper
            ├─ selectStatsByDepartmentId
            ├─ selectGroupIncomeAggregateForDepartmentIds
            └─ selectRevenueWindowAggregateForDepartmentIds
                 ↓
            查询表: gb_ai_daily_revenue
            时间字段: gb_ai_daily_revenue_date
            门店范围字段: department_id（记账部门）
            金额口径: total_revenue = 堂食 + 外卖（毛额）
```

### 5.2 Purchase（采购）

```
PurchaseOverviewTool
  ├→ GbDistributerPurchaseGoodsService (baseMapper)
  │    └→ GbDistributerPurchaseGoodsMapper
  │         ├─ queryGbPurchaseGoodsCount
  │         ├─ queryGbPurchaseGoodsBuySubtotalSum
  │         ├─ queryGbPurchaseGoodsAggByLegacyPurchaseMethod
  │         ├─ queryGbPurchaseGoodsTopTimesMerged
  │         ├─ queryGbPurchaseGoodsTopSubtotalMerged
  │         ├─ queryGbPurchaseGoodsTopPriceFluctuation
  │         ├─ queryGbPurchaseSupplierSpendTop
  │         ├─ sumPurchaseSubtotalGroupedByPurDepartmentId
  │         ├─ queryGbPurchaseSupplierAggRowsForFocusedDisGoods
  │         └─ queryGbPurchaseGoodsAggRowsForFocusedSupplier
  │              ↓
  │         查询表: gb_distributer_purchase_goods + gb_distributer_purchase_batch
  │         时间字段: gb_DPG_stock_finish_date（AI 侧 useStockFinishDate=true）
  │         门店范围字段: gb_DPG_purchase_department_id / purDepIds
  │         金额口径: gb_DPG_buy_subtotal，排除退货 typeNotEqual
  │
  └→ GbAiDailyRevenueService (expandStoreRoots)
       └→ GbAiDailyRevenueMapper (子部门展开)
```

### 5.3 StockReduce（出库/核销）

```
StockReduceQueryTool
  └→ GbDepartmentGoodsStockReduceService
       └→ GbDepartmentGoodsStockReduceMapper
            └─ queryReduceAllTypesTotalOnDailyRevenueDays
            └─ queryReduceAllTypesTotalForRetailDepartmentFathers
            └─ queryReduceAllTypesTotalGroupedByDepartmentFather
                 ↓
            查询表: gb_department_goods_stock_reduce + gb_ai_daily_revenue
            时间字段: gb_dgsr_date（限定 gb_ai_daily_revenue 有上传记录的日期）
            门店范围字段: department_father_id / matchDailyRevenueDepartmentId
            金额口径: 四类(produce/waste/loss/return) 的 gb_dgsr_subtotal
            特别说明: AI 方法限定仅统计日营业额上传记录日期的核销
```

### 5.4 Warehouse（库存）

```
WarehouseStockOverviewTool
  ├→ GbDepartmentGoodsStockService
  │    └→ GbDepartmentGoodsStockMapper
  │         ├─ queryGoodsStockCount
  │         ├─ queryDepGoodsRestTotal
  │         ├─ queryDepGoodsRestWeightTotal
  │         ├─ queryDepGoodsSubtotal
  │         ├─ queryDepStockWeightTotal
  │         └─ queryGoodsStockListForMendianPeriod
  │              ↓
  │         查询表: gb_department_goods_stock
  │
  ├→ GbDepartmentGoodsStockReduceService
  │    └→ GbDepartmentGoodsStockReduceMapper
  │         └─ queryReduceAllTypesTotalOnDailyRevenueDays（出库汇总）
  │
  └→ GbDepartmentMapper
       └─ selectBatchIds（门店名查询）
```

### 5.5 DishProfit（菜品毛利）

```
DishProfitAnalysisTool
  └→ GbDepFoodBusinessInsightService
       ├→ GbDepartmentMapper（查子部门）
       ├→ GbDepFoodService → GbDepFoodMapper（查门店菜品）
       ├→ GbDepFoodSalesService → GbDepFoodSalesMapper（查菜品销量）
       ├→ GbDishCostAnalysisService
       │    └→ GbDepartmentGoodsStockReduceMapper（查出库分摊）
       └→ GbDistributerFoodService → GbDistributerFoodMapper（查分销菜品）
            ↓
       时间字段: gb_dep_food_sales_date
       门店范围字段: department_id / scopeDepartmentIdsAllowFilter
       金额口径: listPriceRevenue（挂牌营收）/ theoryCostAmount（理论成本）/ actualCostAmount（实际成本）
```

### 5.6 BusinessDiagnosis / BusinessOverview（经营诊断）

不新增专属 Mapper，复用 Revenue / Purchase / StockReduce / DishProfit / Warehouse 的 Tool 与 Mapper。

调用路径：
```
BusinessToolExecutionNode
  └→ BusinessToolExecutionRequestResolver
       └→ ToolRequestContractExecutionParamSupport
            └→ 按 contract 分发到上述各 Tool
                 └→ 各 Tool 调用对应 Mapper
```

---

## 6. SQL 口径冲突风险

### E1 营业额 🔴 高风险

| 维度 | 老业务口径 | AI Harness 口径 |
|---|---|---|
| Mapper | `GbDepartmentOrdersMapper` | `GbAiDailyRevenueMapper` |
| 查询表 | `gb_department_orders`（订单明细表） | `gb_ai_daily_revenue`（日汇总表） |
| 金额含义 | 订单表原额 `gb_do_order_total` 等 | 日汇总 `total_revenue`（堂食+外卖毛额） |
| 风险 | 订单明细表 vs 日汇总表，金额可能不一致。日汇总表可能缺少退款/平台费拆分。 |
| 结论 | **保留双口径**。老业务继续查 `gb_department_orders`，AI 经营分析统一走 `gb_ai_daily_revenue`。两套口径不能混用。 |

### E2 出库/核销 🔴 高风险

| 维度 | 老业务口径 | AI Harness 口径 |
|---|---|---|
| Mapper | `GbDepartmentGoodsStockReduceMapper` 老方法 | 同 Mapper AI 新增方法 |
| SQL | `queryReduceAllTypesTotal` 等，查全日期范围 | `queryReduceAllTypesTotalOnDailyRevenueDays`，仅统计有日营业额上传记录的日期 |
| 风险 | 老业务查全日期范围出库，AI 只查有日营业额记录的日期的出库，金额可能不同。 |
| 结论 | **必须文档化，不要混用**。AI 经营分析统一走 `queryReduceAllTypesTotalOnDailyRevenueDays`，老业务走老方法。 |

### E3 采购 🟡 中风险

| 维度 | 老业务口径 | AI Harness 口径 |
|---|---|---|
| Mapper | `GbDistributerPurchaseGoodsMapper`（同一 Mapper） | 同左 |
| 时间字段 | 可能用 `gb_DPG_date`（入库日期） | `useStockFinishDate=true`，用 `gb_DPG_stock_finish_date`（入库完成日期） |
| 退货处理 | 未明确排除退货 | `typeNotEqual=5`（排除退货） |
| 门店范围 | 小程序入参 | `purDepIds`（集团展开到子部门） |
| 风险 | 入库日期 vs 入库完成日期不同；退货处理不一致。 |
| 结论 | **AI 采购口径必须固定说明**：时间用 `stock_finish_date`，排除退货，门店展开到子部门。 |

### E4 菜品毛利 🟡 中风险

| 维度 | 老业务口径 | AI Harness 口径 |
|---|---|---|
| Service | `GbDishCostAnalysisServiceImpl`（183KB，管理端报表） | `GbDepFoodBusinessInsightServiceImpl`（45KB，AI Insight） |
| 分摊逻辑 | 独立分摊计算 | 调用 `GbDishCostAnalysisService` 底层 + 自有聚合/门店范围过滤层 |
| 门店范围 | 管理端报表维度 | `scopeDepartmentIdsAllowFilter`，可能不同 |
| 风险 | 门店范围过滤和聚合层可能不一致，集团/单店汇总可能差异。 |
| 结论 | **后续单独做 DishProfit 口径审计**，确认两套 Service 的分摊逻辑是否一致。 |

### E5 门店范围 🟡 中风险

| 维度 | 老业务口径 | AI Harness 口径 |
|---|---|---|
| 门店范围字段 | `departmentFatherId`（门店根） | `resolvedDepartmentIds` / `effectiveSqlDepartmentIds`（可能展开到子部门） |
| 风险 | 范围粒度不同：门店根 vs 子部门再汇总。 |
| 结论 | **AI Tool 必须明确查询范围字段**。门店展开逻辑文档化。 |

---

## 7. 重复和历史遗留

### 7.1 GbDistributerFatherGoodsDao vs GbDistributerFatherGoodsMapper

| 维度 | Dao | Mapper |
|---|---|---|
| 类型 | 遗留 Dao 体系 | MyBatis Mapper 体系 |
| Java 路径 | `com/nongxinle/dao/GbDistributerFatherGoodsDao.java` | `com/nongxinle/mapper/GbDistributerFatherGoodsMapper.java` |
| XML 路径 | `com/nongxinle/dao/GbDistributerFatherGoodsDao.xml` (40.85KB) | `mapper/GbDistributerFatherGoodsMapper.xml` (42.37KB) |
| 查询表 | `gb_distributer_father_goods` | `gb_distributer_father_goods` |
| 标记 | **duplicate-candidate** | |

**说明**: 同一张表 `gb_distributer_father_goods` 存在两套查询体系。不允许直接删除 Dao，必须先确认无调用方。后续治理时统一到 Mapper 体系。

### 7.2 BaseMapper-only Mapper（仅 CRUD，无自定义 XML）

以下 18 个 Mapper 接口只有 BaseMapper CRUD，没有自定义 XML 方法：

| # | Mapper | 主要表 | 用途 |
|---|---|---|---|
| 1 | `GbAiAdvisorMapper` | `gb_ai_advisor` | AI 顾问 |
| 2 | `GbAiAdvisorWorkflowMapper` | `gb_ai_advisor_workflow` | AI 顾问工作流 |
| 3 | `GbAiAgentRunMapper` | `gb_ai_agent_run` | AI Agent 运行 |
| 4 | `GbAiAgentStepMapper` | `gb_ai_agent_step` | AI Agent 步骤 |
| 5 | `GbAiConversationNotebookMapper` | `gb_ai_conversation_notebook` | AI 会话笔记本关联 |
| 6 | `GbAiConversationPinMapper` | `gb_ai_conversation_pin` | AI 会话置顶关联 |
| 7 | `GbAiConversationTagMapper` | `gb_ai_conversation_tag` | AI 会话标签关联 |
| 8 | `GbAiNotebookMapper` | `gb_ai_notebook` | AI 笔记本 |
| 9 | `GbAiTagMapper` | `gb_ai_tag` | AI 标签 |
| 10 | `GbDepartmentUserMapper` | `gb_department_user` | 门店用户 |
| 11 | `GbDistributerMapper` | `gb_distributer` | 分销商 |
| 12 | `GbDistributerModuleMapper` | `gb_distributer_module` | 分销商模块 |
| 13 | `GbDistributerPayMapper` | `gb_distributer_pay` | 付款 |
| 14 | `GbDistributerUserMapper` | `gb_distributer_user` | 分销用户 |
| 15 | `GbDepFoodGoodsSalesMapper` | `gb_dep_food_goods_sales` | 菜品配料销售 |
| 16 | `GbReportMapper` | `gb_report` | 报表 |
| 17 | `NxJrdhUserMapper` | `nx_jrdh_user` | 农信用户 |
| 18 | `SysCityMarketMapper` | `sys_city_market` | 城市市场 |

**说明**: 仅 BaseMapper 不等于废弃。如果有 Service 注入使用，不能删除。需逐个确认是否有 Service 注入。

### 7.3 delete-candidate-zero-callers

当前暂不确认真正零调用方的 Mapper，需要后续专项扫描（运行时依赖分析 + IDE 引用检查）。

---

## 8. 后续治理规则

### 规则 1: 新增 AI Tool 前必须查本文档

Cursor / WorkBuddy 新增 AI Tool 前，**必须先查本文档**，确认：
- 目标表是否已有 Mapper
- 已有 Mapper 的 SQL 口径是什么
- 是否可以直接复用现有 Mapper 方法

### 规则 2: 优先复用现有 Mapper

如果业务表已有 Mapper，**优先复用现有 Mapper**。不允许为普通业务表随手新建 `AiXxxMapper`。

### 规则 3: 新增 AI 专用 SQL 方法必须文档化

允许在现有 Mapper 增加 AI 专用 SQL 方法，但必须写清楚：
- 业务域
- 查询表
- 时间字段
- 门店范围字段
- 金额/数量口径
- 是否区别于老业务口径（如区别，必须在本文档第 6 章"SQL 口径冲突风险"中记录）

### 规则 4: GbAi* Mapper 只用于 gb_ai_* 专属表

`GbAi*` Mapper 只用于 `gb_ai_*` 专属表（如 `gb_ai_daily_revenue`、`gb_ai_conversation` 等），不用于普通业务表。

### 规则 5: 不允许为普通业务表新建 AiXxxMapper

普通业务表（如 `gb_department_orders`、`gb_distributer_purchase_goods`）已有 Mapper 时，不允许再新建 `GbAiXxxMapper` 查询同一张表。

### 规则 6: AI 口径与老业务口径不同时必须保留双口径并文档化

如果 AI 口径和老业务口径不同（如时间字段、门店范围、金额计算方式不同），必须保留双口径并在本文档第 6 章记录，**不能混用**。

### 规则 7: 删除 Mapper 前必须确认无调用方

删除 Mapper 前必须确认无 Controller / Service / Tool / XML 调用方。不能仅凭文件名或"看起来没用"判断。

### 规则 8: Mapper 合并按业务域逐步做

Mapper 合并按业务域逐步做，**不做一次性大迁移**。优先级见第 9 章。

### 规则 9: SQL 口径变更必须同步更新本文档

任何 SQL 口径变更（时间字段、门店范围、金额计算方式）必须同步更新本文档。

---

## 9. 后续治理计划

| 优先级 | 业务域 | 行动 | 说明 |
|---|---|---|---|
| **P0** | **Revenue（营业额）** | 文档化 Revenue 口径差异，不改 SQL | 确认 `gb_ai_daily_revenue` vs `gb_department_orders` 差异，AI 经营分析统一走 `gb_ai_daily_revenue` |
| **P0** | **StockReduce（出库）** | 文档化 StockReduce 口径差异，不改 SQL | 确认老方法全日期 vs AI 方法限定日营业额上传日期的差异 |
| **P1** | **Purchase（采购）** | Purchase 入参口径文档化 | `useStockFinishDate`、`typeNotEqual`、`purDepIds` 等差异文档化 |
| **P1** | **DishProfit（菜品毛利）** | DishProfit 口径专项审计 | `GbDepFoodBusinessInsightServiceImpl` vs `GbDishCostAnalysisServiceImpl` 分摊逻辑一致性审计 |
| **P2** | **Warehouse / Stock（库存）** | 基本保持现状 | 已统一到 `GbDepartmentGoodsStockMapper`，无重复 |
| **P2** | **BusinessDiagnosis（经营诊断）** | 不新增专属 Mapper | 复用 Revenue / Purchase / StockReduce / DishProfit / Warehouse 的 Tool 与 Mapper |
| **P3** | **GbDistributerFatherGoodsDao 遗留** | 遗留 Dao 调用方专项审计 | 确认无直接调用后，统一到 `GbDistributerFatherGoodsMapper` |
| **P3** | **BaseMapper-only Mapper** | 调用方专项审计 | 逐个确认是否有 Service 注入使用，确认无调用方后标记 `delete-candidate-zero-callers` |

---

## 附录: Mapper 接口与 XML 对应关系速查

| Mapper 接口 | XML 文件 | XML 大小 | 有自定义方法 |
|---|---|---|---|
| `GbAiDailyRevenueMapper` | GbAiDailyRevenueMapper.xml | ~13KB | ✅ 10 |
| `GbAiConversationHistoryMapper` | GbAiConversationHistoryMapper.xml | — | ✅ 3+ |
| `GbAiConversationMapper` | GbAiConversationMapper.xml | — | ✅ 2 |
| `GbAiConversationTurnMemoryMapper` | GbAiConversationTurnMemoryMapper.xml | — | ✅ 1 |
| `GbAiCouponPlanMapper` | GbAiCouponPlanMapper.xml | — | ✅ 2 |
| `GbAiKnowledgeMapper` | GbAiKnowledgeMapper.xml | — | ✅ 3+ |
| `GbAiMessageMapper` | GbAiMessageMapper.xml | — | ✅ 3+ |
| `GbAiRestaurantProfileMapper` | GbAiRestaurantProfileMapper.xml | — | ✅ 3 |
| `GbAiWorkflowMapper` | GbAiWorkflowMapper.xml | — | ✅ 2 |
| `GbAiWorkflowRunMapper` | GbAiWorkflowRunMapper.xml | — | ✅ 3 |
| `GbAiWorkNoteMapper` | GbAiWorkNoteMapper.xml | 极小 | ❌ |
| `GbAiWorkPinMapper` | GbAiWorkPinMapper.xml | 极小 | ❌ |
| `GbDepartmentOrdersMapper` | GbDepartmentOrdersMapper.xml | 37.66KB | ✅ |
| `GbDepartmentGoodsStockReduceMapper` | GbDepartmentGoodsStockReduceMapper.xml | 41.79KB | ✅ |
| `GbDepartmentGoodsStockMapper` | GbDepartmentGoodsStockMapper.xml | 35.43KB | ✅ |
| `GbDepartmentGoodsDailyMapper` | GbDepartmentGoodsDailyMapper.xml | 31.23KB | ✅ |
| `GbDepartmentDisGoodsMapper` | GbDepartmentDisGoodsMapper.xml | 63.61KB | ✅ |
| `GbDepartmentMapper` | GbDepartmentMapper.xml | 15.8KB | ✅ |
| `GbDistributerPurchaseGoodsMapper` | GbDistributerPurchaseGoodsMapper.xml | 77.14KB | ✅ |
| `GbDistributerPurchaseBatchMapper` | GbDistributerPurchaseBatchMapper.xml | 20.51KB | ✅ |
| `GbDistributerFatherGoodsMapper` | GbDistributerFatherGoodsMapper.xml | 42.37KB | ✅ |
| `GbDistributerFoodMapper` | GbDistributerFoodMapper.xml | 9.7KB | ✅ |
| `GbDistributerFoodGoodsMapper` | GbDistributerFoodGoodsMapper.xml | 3.53KB | ✅ |
| `GbDistributerGoodsMapper` | GbDistributerGoodsMapper.xml | 3.07KB | ✅ |
| `GbDistributerPayListMapper` | GbDistributerPayListMapper.xml | 1.77KB | ✅ |
| `GbDistributerStandardMapper` | GbDistributerStandardMapper.xml | 2.98KB | ✅ |
| `GbDistributerSupplierPaymentMapper` | GbDistributerSupplierPaymentMapper.xml | 1.93KB | ✅ |
| `GbDistributerAliasMapper` | GbDistributerAliasMapper.xml | 420B | ❌ |
| `GbDepFoodMapper` | GbDepFoodMapper.xml | 2.96KB | ✅ |
| `GbDepFoodSalesMapper` | GbDepFoodSalesMapper.xml | 410B | ❌ |
| `NxGoodsMapper` | NxGoodsMapper.xml | 54.36KB | ✅ |
| `NxAliasMapper` | NxAliasMapper.xml | 2.3KB | ✅ |
| `NxJrdhSupplierMapper` | NxJrdhSupplierMapper.xml | 3.05KB | ✅ |
| `NxStandardMapper` | NxStandardMapper.xml | 1.32KB | ✅ |

**遗留 Dao（非 Mapper 体系）**:

| Dao | XML | XML 大小 | 查询表 |
|---|---|---|---|
| `GbDistributerFatherGoodsDao` | com/nongxinle/dao/GbDistributerFatherGoodsDao.xml | 40.85KB | `gb_distributer_father_goods` |
