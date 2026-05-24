# Phase 3 数据层收口说明

> **日期**：2026-05-24  
> **状态**：收口完成，六域数据层审计通过  
> **关联**：[`business-domain-full-chain-audit-report.md`](./business-domain-full-chain-audit-report.md)、[`contract-entry-validation-p2-summary.md`](./contract-entry-validation-p2-summary.md)

---

## 1. 阶段目标

Phase 3 是 Tool / SQL / 数据层验收，只验证一条链：

```
Tool Request args → Tool / Service / Mapper / SQL → Tool payload → AnswerPlan 消费字段
```

**在范围内**：

| 维度 | 验证内容 |
|------|----------|
| Tool Request 入参 | 时间窗、部门范围、source focus、narrative mode 是否与 Contract Entry 对齐 |
| Tool 执行 | 哪个 Tool 被调用、Service/Mapper/SQL 链路是否透明可追溯 |
| SQL / Mapper / 表 | 核心表、WHERE 条件、GROUP BY、时间/范围字段是否正确 |
| Tool payload | 字段名与语义是否一致、是否有歧义字段命名 |
| AnswerPlan 消费字段 | Builder 是否只消费 Tool payload 真实字段、是否有字段误用 |

**不在范围内**：

- 语义解析 / Prompt / LLM 输出质量
- Composer 话术 / `finalAnswerText`
- 前端展示 / 协议
- 大文件重构 / 代码风格
- 性能优化
- 新增 TODO / Feature

---

## 2. 当前主链

生产环境实际数据流（与 P2 主链一致）：

```text
用户问句
  → SemanticIntake LLM（semantic_intake.v1）
  → DomainContractSelector（Java：按 intakePrimaryDomain 注入单域 allowedOutputContract）
  → query_semantic_parser.v2（LLM：selectedContractId + semanticSlots）
  → SemanticAdoptionPipeline
      · SemanticContractCompletionEngine.complete()
      · contractEntryValidated = true
  → ContractExecutionMappingSupport（resolve intent/path/tools）
  → AiResolvedQueryContext（effectivePathCode + effectiveIntentCode + timeWindow + orgScope + dataScope）
  → BusinessDataPlannerNode（dataPlanTools + path flags）
  → BusinessToolExecutionNode
      · BusinessToolExecutionRequestResolver（构建 *ToolRequestContext）
      · *ToolExecutor.build*ToolArgs（args + resolvedQueryContext）
  → Tool.execute（Tool / Service / Mapper / SQL）
  → Tool payload（保存到 state.toolResults）
  → AnswerPlan Builder（消费 Tool payload 字段）
  → Composer（只宣读 AnswerPlan，不改事实）
```

**P3 审计聚焦的是从 `BusinessDataPlannerNode` 到 `AnswerPlan` 这一段**，即 Tool 被调度后、SQL 到底查了哪些表/字段、Tool payload 结构、AnswerPlan 消费了哪些字段。

---

## 3. 六域结论总表

| 业务域 | 结论 | P3_BLOCKER | 核心表 | 金额/数量主字段 | 时间字段 | 范围字段 | 备注 |
|--------|------|-----------|--------|----------------|----------|----------|------|
| **Revenue** | ✅ OK | 无 | `gb_ai_daily_revenue` | `dine_in_revenue` + `takeout_revenue` | `record_date` | `department_id` | 门店营收/实收口径，Revenue 可作为样板域 |
| **StockReduce** | ✅ OK | 无 | `gb_department_goods_stock_reduce` | `gb_dgsr_subtotal`（类型：1=生产耗用,2=废弃,3=损耗/报损,4=退货） | `gb_dgsr_date` | `gb_dgsr_department_id` | 出库/核销/耗用域，与 Warehouse 库存现量区分 |
| **Purchase** | ✅ OK | 无 | `gb_distributer_purchase_goods` | `gb_DPG_buy_subtotal`（来源：ALL/SELF_PURCHASE/SUPPLIER_PURCHASE） | `gb_DPG_stock_finish_date` | `gb_DPG_purchase_department_id` / `gb_DPG_distributer_id` | 单商品采购概览是功能缺口，非 SQL blocker |
| **Warehouse** | ✅ OK | 无 | `gb_department_goods_stock` | `gb_dgs_rest_subtotal`（库存金额）、`gb_dgs_rest_weight`（库存数量/重量） | 无时间过滤（库存现量快照） | `gb_dgs_gb_department_father_id` | reduce 表仅作核销辅助，不作为库存余额主口径 |
| **DishProfit** | ✅ OK | 无（P3I 已修复） | 多表聚合（`gb_dep_food` + `gb_dep_food_sales` + `gb_department_goods_stock_reduce`） | `actualCostTotalAmount123`（type1+2+3）、`blendedGrossMarginRateOnListPrice` | `gbDfsFullDate` / `gb_dgsr_date` | `gbDfsDepId` / `gb_dgsr_department_id` | 原 `actualCostAmount` 实际是 type1 生产出库成本，P3I 修复后优先使用 type1+2+3 |
| **DishSales** | ✅ OK | 无 | 复用 DishProfit 快照（`gb_dep_food_sales` + `gb_dep_food`） | `soldPortionsTotal`（销量）、`listPriceRevenue`（标价销售额） | `gbDfsFullDate` | `gbDfsDepId IN scopeDepIds` | 无独立 Tool，寄生 DishProfit；销售额为菜品标价收入，不等于 Revenue 营业额 |

---

## 4. P3_BLOCKER 收口

**当前 6 域已无未关闭 P3_BLOCKER。**

唯一曾出现的 blocker：

| 编号 | 域 | 问题 | 处理 |
|------|-----|------|------|
| DP-BLOCK-1 | DishProfit | `actualCostAmount` 实际是 type1 生产出库成本，对外说"实际成本"有误导 | **P3I 已修复**：AnswerPlan/Renderer 对外"实际成本"优先使用 type1+2+3（`actualCostTotalAmount123` / `totalActualCostAmount123`），type1 字段现显式为 `productionActualCostAmount`；**P3I-R 复核通过** |

---

## 5. P3_CLEANUP 清单

以下为各域记录的结构清理项。这些项**当前不阻塞数据口径正确性**，不改代码。

### 5.1 Revenue

| 编号 | 描述 |
|------|------|
| REV-CLEAN-1 | `total_coupon_amount` 命名误导（可能是优惠券 + 其他合计） |
| REV-CLEAN-2 | 可能存在 N+1 / Map key 风格问题 |

### 5.2 StockReduce

| 编号 | 描述 |
|------|------|
| SR-CLEAN-1 | legacy / harness 双路径口径需后续统一 |
| SR-CLEAN-2 | SQL 层 ROUND 可后续优化 |

### 5.3 Purchase

| 编号 | 描述 |
|------|------|
| PU-CLEAN-1 | `PurchaseOverviewTool` 过大（72KB） |
| PU-CLEAN-2 | `GbDistributerPurchaseGoodsMapper.xml` 过大（~77KB） |
| PU-CLEAN-3 | 单商品采购概览当前不是稳定主流程 |

### 5.4 Warehouse

| 编号 | 描述 |
|------|------|
| WH-CLEAN-1 | 无独立 Mapper / Service 边界较弱 |
| WH-CLEAN-2 | `WarehouseStockOverviewTool` 偏重（54KB） |
| WH-CLEAN-3 | 复用库存 / reduce Mapper，但当前数据口径 OK |

### 5.5 DishProfit

| 编号 | 描述 |
|------|------|
| DP-CLEAN-1 | `DishProfitAgentNode` 过大（117KB） |
| DP-CLEAN-2 | `GbDepFoodBusinessInsightService` 聚合过重（6+ 数据源） |
| DP-CLEAN-3 | `diffCostAmount` 在非 GAP 次要对照片中仍可能是 type1 差异，当前不阻塞 |

### 5.6 DishSales

| 编号 | 描述 |
|------|------|
| DS-CLEAN-1 | 无独立 Tool / Service / Mapper，寄生 `DishProfitAnalysisTool` |
| DS-CLEAN-2 | AnswerPlan 行里可能携带毛利字段（`grossMarginRate` / `actualCostAmount` / `theoryCostAmount`），但排序只用销量/销售额 |
| DS-CLEAN-3 | Renderer Top3 可能附带毛利率，边界不够纯 |
| DS-CLEAN-4 | `selectedTools` 仍是 `dish_profit_analysis`，语义上不干净 |

---

## 6. P3_KNOWN_GAP 清单

以下为各域已知功能缺口。这些项**不属于数据层口径错误**，是 P1 主流程故意未支持的功能。

### 6.1 Revenue

| 编号 | 描述 |
|------|------|
| REV-GAP-1 | 门店对比（`store_compare`） |
| REV-GAP-2 | 周期对比（`period_compare`） |
| REV-GAP-3 | 每日排行（`daily_amount_ranking`） |
| REV-GAP-4 | 趋势（`trend`） |
| REV-GAP-5 | 堂食/外卖/平台/客单等 PLANNED 细分 |

### 6.2 StockReduce

| 编号 | 描述 |
|------|------|
| SR-GAP-1 | 商品废弃排行（`goods_waste_ranking`） |
| SR-GAP-2 | 商品数量排行 + 产出量等 PLANNED 行 |

### 6.3 Purchase

| 编号 | 描述 |
|------|------|
| PU-GAP-1 | 单商品采购概览 / 商品锚点查询需后续 contract entry + `disGoodsId` 支持 |
| PU-GAP-2 | 门店采购金额排行（`store_amount_ranking`） |
| PU-GAP-3 | 采购-出库不匹配风险（`stock_reduce_mismatch`） |

### 6.4 Warehouse

| 编号 | 描述 |
|------|------|
| WH-GAP-1 | 缺货预警（`out_of_stock`） |
| WH-GAP-2 | 临期预警（`near_expiry`） |

### 6.5 DishProfit

| 编号 | 描述 |
|------|------|
| DP-GAP-1 | 复杂原料构成（`ingredient_cost_breakdown`） |
| DP-GAP-2 | 成本差异原因（`low_profit_reason`） |
| DP-GAP-3 | 实际成本/理论成本扩展分析（`theoretical_cost` / `actual_outbound_cost` / `cost_gap`） |
| DP-GAP-4 | 跨域归因（菜锚 follow-up `dish_anchor_ingredient_breakdown`） |

### 6.6 DishSales

| 编号 | 描述 |
|------|------|
| DS-GAP-1 | 单菜过滤使用 `contains` 子串匹配，可能匹配多个菜（如"芹菜"匹配多菜），但不丢数据 |
| DS-GAP-2 | 不支持折后/实收菜品销售额，当前只有标价销售额 |
| DS-GAP-3 | 退菜/取消/赠送没有独立过滤（仅排除 `amount ≤ 0` 行） |
| DS-GAP-4 | 趋势（`trend`）不在 P1 主流程 |
| DS-GAP-5 | 跨域毛利追问（`cross_domain_profit`）不接手，引导走 DishProfit 专线 |

### 6.7 组合域（不在本阶段收口）

| 域 | 说明 |
|----|------|
| **BusinessOverview** | 四域概览汇总，数据来自各单域 Tool 聚合，进入 Phase 4/5 时再看 AnswerPlan 证据组织 |
| **BusinessDiagnosis** | 问题/风险/建议/门店原因，依赖多域 Tool 快照，进入 Phase 4/5 时再看 |

---

## 7. 进入 Phase 4 的条件

Phase 3 当前可以收口，因为六个单域的数据层没有未关闭 blocker。

### Phase 4 目标：AnswerPlan 举证层

Phase 4 要证明：

1. **AnswerPlan 是否只消费 Tool payload 中真实字段**——Builder 不应从 `rawLLMField`、`metric.rankingType`、用户原文 contains 等非 Tool 产出拼字段
2. **排行是否使用 Tool 已查出的排序/指标**——AnswerPlan 不应自己对 Tool payload 做二次排序或重新计算指标
3. **AnswerPlan 是否没有重新发明事实**——Plan 中的数值必须可追溯到 Tool payload 中某一行
4. **AnswerPlan `focusRows` / `secondaryRows` 是否选对**——首轮主答行集与被答集区分正确
5. **Composer 是否只宣读 AnswerPlan，不改事实**——Composer 不应从 `toolResults` 直接拼事实，不应绕过 AnswerPlan

### Phase 3 → Phase 4 交接

| 从 Phase 3 带出 | 说明 |
|-----------------|------|
| 六域结论表 | Phase 4 按域验收 AnswerPlan 时对照核心字段 |
| Tool payload 字段清单 | 与 AnswerPlan DTO 字段逐一对账 |
| CLEANUP 清单 | 不阻塞 Phase 4，但记录已知结构债 |
| KNOWN_GAP 清单 | Phase 4 不用处理，记录即可 |

---

## 8. 禁止回退

以下行为在后续阶段禁止：

| 禁止 | 原因 |
|------|------|
| 恢复 Java 通过用户原文 `contains` 猜业务语义 | P2 已收口 contract-locked 主链 |
| 让 Tool Request 读取 `rawLLMField` 覆盖 `ResolvedQueryContext` | 覆盖会破坏 contract 一致性 |
| 让 Composer 从 `toolResults` 直接拼事实 | 应走 AnswerPlan 中间层 |
| 把 DishSales 立刻拆独立 Tool 打断 Phase 4 | 当前寄生口径正确，独立化是 P3+/P4 架构债 |
| 因为文件大就判数据层 blocker | 文件大小是 CLEANUP 项，不是数据口径问题 |
| 把 P3_CLEANUP 当作 P3_BLOCKER | 已是明确分类，不应倒回 |

---

## 9. 关键代码索引

| 职责 | 类 |
|------|-----|
| 总调度与 dataPlanTools | `BusinessDataPlannerNode` |
| Tool Request 解析 | `BusinessToolExecutionRequestResolver` |
| 时间窗/范围解析 | `*ToolRequestResolution` 各域实现 |
| Tool 执行入口 | `*ToolExecutor.build*ToolArgs` + `Tool.execute` |
| AnswerPlan 构建 | `*AnswerPlanBuilder` 各域实现 |
| Contract Entry → execution 映射 | `ContractExecutionMappingSupport` |
| 六域全链路报告 | `business-domain-full-chain-audit-report.md` |
| P2 语义主链收口 | `contract-entry-validation-p2-summary.md` |

---

*本文档为 Phase 3 收口快照；后续以各域 AnswerPlan 与 Composer 源码实际状态为准。*
