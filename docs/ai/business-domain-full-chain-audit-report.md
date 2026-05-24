# 6 域 Tool → Service → Mapper → XML → AnswerPlan 全链路审计报告

> **日期**：2026-05-24  
> **范围**：Revenue / Purchase / StockReduce / Warehouse / DishProfit / DishSales 六域全链路盘点  
> **目标**：确认每个域的 Tool 到 SQL（Mapper/XML）再到 AnswerPlan 的完整调用链，识别缺失/冗余/耦合

---

## 1. 汇总矩阵

| 域 | Tool | Service | Mapper | XML | AnswerPlan | 独立 Tool? | 独立 Mapper? |
|----|------|---------|--------|-----|------------|-----------|-------------|
| **REVENUE** | `RevenueQueryTool` | `GbAiDailyRevenueService` | `GbAiDailyRevenueMapper` | `GbAiDailyRevenueMapper.xml` | `DailyRevenueAnswerPlan` | ✅ | ✅ |
| **PURCHASE** | `PurchaseOverviewTool` | `GbDistributerPurchaseGoodsService` | `GbDistributerPurchaseGoodsMapper` + `GbDistributerPurchaseBatchMapper` | `GbDistributerPurchaseGoodsMapper.xml` + `GbDistributerPurchaseBatchMapper.xml` | `PurchaseAnswerPlan` | ✅ | ✅ |
| **STOCK_REDUCE** | `StockReduceQueryTool` | `GbDepartmentGoodsStockReduceService` | `GbDepartmentGoodsStockReduceMapper` | `GbDepartmentGoodsStockReduceMapper.xml` | `StockReduceAnswerPlan` | ✅ | ✅ |
| **WAREHOUSE** | `WarehouseStockOverviewTool` | `GbDepartmentGoodsStockService` + `GbDepartmentGoodsStockReduceService` | `GbDepartmentGoodsStockMapper` + `GbDepartmentMapper` | `GbDepartmentGoodsStockMapper.xml` + `GbDepartmentMapper.xml` | `WarehouseAnswerPlan` | ✅ | ⚠️ 复用多源 |
| **DISH_PROFIT** | `DishProfitAnalysisTool` | `GbDepFoodBusinessInsightService` | `GbDepFoodMapper` + `GbDepFoodSalesMapper` + 多间接 | `GbDepFoodMapper.xml` + `GbDepFoodSalesMapper.xml` | `DishProfitAnswerPlan` | ✅ | ⚠️ 聚合多源 |
| **DISH_SALES** | ❌ **无**（复用 DishProfit） | 同 DishProfit | 同 DishProfit | 同 DishProfit | `DishSalesAnswerPlan` | ❌ | ❌ |

---

## 2. REVENUE（营收域）

### 全链路

```text
RevenueQueryTool
  → GbAiDailyRevenueService (GbAiDailyRevenueServiceImpl extends ServiceImpl<GbAiDailyRevenueMapper>)
    → GbAiDailyRevenueMapper
      → GbAiDailyRevenueMapper.xml
        → gb_ai_daily_revenue 表
  → DailyRevenueAnswerPlanBuilder → DailyRevenueAnswerPlan
```

### 文件清单

| 层 | 文件 | 说明 |
|----|------|------|
| **Tool** | `ai/tool/business/RevenueQueryTool.java` | 直接注入 `GbAiDailyRevenueService` |
| **Tool Executor** | `ai/graph/business/RevenueQueryToolExecutor.java` | Tool 调用编排 |
| **Tool Request** | `ai/graph/business/toolrequest/RevenueToolRequestResolution.java` | 请求参数解析 |
| **Service** | `service/GbAiDailyRevenueService.java` | 接口 |
| **ServiceImpl** | `service/impl/GbAiDailyRevenueServiceImpl.java` | `extends ServiceImpl<GbAiDailyRevenueMapper, GbAiDailyRevenueEntity>` |
| **Mapper** | `mapper/GbAiDailyRevenueMapper.java` | 9 个自定义查询方法 |
| **XML** | `resources/mapper/GbAiDailyRevenueMapper.xml` | ~13KB，窗口聚合 + 多部门汇总 |
| **AnswerPlan** | `ai/dto/business/DailyRevenueAnswerPlan.java` | DTO |
| **AnswerPlan Builder** | `ai/graph/business/DailyRevenueAnswerPlanBuilder.java` | 静态 attachIfApplicable |
| **Contract Exporter** | `ai/semantic/contract/RevenueSemanticCapabilityContractExporter.java` | 7 ACTIVE + KNOWN_GAP/PLANNED |
| **Matrix** | `ai/semantic/matrix/RevenueSemanticCapabilityMatrix.java` | contract-locked guard 已覆盖 |
| **Agent** | `ai/agent/business/RevenueAgent.java` | BusinessAgent |
| **Planner** | `ai/planner/RevenuePlannerAgentAdapter.java` + 6 辅助类 | ReadBridge 架构 |

### 链路完整性：✅ 完整

- Tool 直接调用 Service，Service 基于 MyBatis-Plus `ServiceImpl` 使用 Mapper
- Mapper 方法覆盖：单店统计、多部门聚合、集团聚合、记账部门维度等
- XML 使用动态 SQL（`<if>` / `<foreach>`）支持灵活查询

---

## 3. PURCHASE（采购域）

### 全链路

```text
PurchaseOverviewTool
  → GbDistributerPurchaseGoodsService (GbDistributerPurchaseGoodsServiceImpl)
    → GbDistributerPurchaseGoodsMapper
      → GbDistributerPurchaseGoodsMapper.xml → gb_distributer_purchase_goods 表
  → PurchaseAnswerPlanBuilder → PurchaseAnswerPlan
```

### 文件清单

| 层 | 文件 | 说明 |
|----|------|------|
| **Tool** | `ai/tool/business/PurchaseOverviewTool.java` | 72KB 超大工具类，直接注入多个 Service |
| **Tool Executor** | `ai/graph/business/PurchaseOverviewToolExecutor.java` | 工具编排 |
| **Service (主)** | `service/GbDistributerPurchaseGoodsService.java` | 接口 |
| **ServiceImpl** | `service/impl/GbDistributerPurchaseGoodsServiceImpl.java` | `extends ServiceImpl<GbDistributerPurchaseGoodsMapper>` |
| **Mapper (主)** | `mapper/GbDistributerPurchaseGoodsMapper.java` | 20+ 自定义查询方法 |
| **XML (主)** | `resources/mapper/GbDistributerPurchaseGoodsMapper.xml` | ~77KB 超大型 XML |
| **Mapper (辅)** | `mapper/GbDistributerPurchaseBatchMapper.java` | 批次维度 |
| **XML (辅)** | `resources/mapper/GbDistributerPurchaseBatchMapper.xml` | ~20KB |
| **AnswerPlan** | `ai/dto/business/PurchaseAnswerPlan.java` | DTO，含 resultAnchors |
| **AnswerPlan Builder** | `ai/graph/business/PurchaseAnswerPlanBuilder.java` | 含 `PurchaseSemanticExecutionIntentResolver` |
| **Contract Exporter** | `ai/semantic/contract/PurchaseSemanticCapabilityContractExporter.java` | 13 ACTIVE + KNOWN_GAP |
| **Matrix** | `ai/semantic/matrix/PurchaseSemanticCapabilityMatrix.java` | contract-locked guard |
| **Agent** | `ai/agent/business/PurchaseAgent.java` | BusinessAgent |
| **Planner** | `ai/planner/PurchasePlannerAgentAdapter.java` | ReadBridge 架构 |
| **Execution** | `ai/graph/business/execution/PurchaseSemanticExecution*.java` | 3 个 Execution 类 |
| **Frame Validator** | `ai/semantic/frame/PurchaseCurrentSemanticFrameValidator.java` | 17KB 复杂校验 |

### 补充 Service（Tool 直接注入）

| Service | 用途 |
|---------|------|
| `GbDistributerPurchaseGoodsService` | 主采购数据 |
| `GbAiDailyRevenueService` | 营收侧关联（成本链） |
| `GbDistributerGoodsService` (间接) | 商品信息 |

### 链路完整性：✅ 完整（但 Tool 过重）

- **Tool 72KB** 是最大问题：大量 SQL 调用直接在 Tool 内编排
- Mapper XML 77KB 复杂度高
- AnswerPlan Builder 带 ExecutionIntent 解析，耦合语义层

---

## 4. STOCK_REDUCE（出库域）

### 全链路

```text
StockReduceQueryTool
  → GbDepartmentGoodsStockReduceService (ServiceImpl)
    → GbDepartmentGoodsStockReduceMapper
      → GbDepartmentGoodsStockReduceMapper.xml → gb_department_goods_stock_reduce 表
  → StockReduceAnswerPlanBuilder → StockReduceAnswerPlan
```

### 文件清单

| 层 | 文件 | 说明 |
|----|------|------|
| **Tool** | `ai/tool/business/StockReduceQueryTool.java` | 双路径：harnessCalendar + legacyEmbeddedCost |
| **Tool Executor** | `ai/graph/business/StockReduceQueryToolExecutor.java` | 编排 |
| **Service** | `service/GbDepartmentGoodsStockReduceService.java` | 接口 |
| **ServiceImpl** | `service/impl/GbDepartmentGoodsStockReduceServiceImpl.java` | `extends ServiceImpl<GbDepartmentGoodsStockReduceMapper>` |
| **Mapper** | `mapper/GbDepartmentGoodsStockReduceMapper.java` | 15+ 自定义方法 |
| **XML** | `resources/mapper/GbDepartmentGoodsStockReduceMapper.xml` | ~42KB |
| **AnswerPlan** | `ai/dto/business/StockReduceAnswerPlan.java` | DTO |
| **AnswerPlan Builder** | `ai/graph/business/StockReduceAnswerPlanBuilder.java` | 静态 attachIfApplicable |
| **Contract Exporter** | `ai/semantic/contract/StockReduceSemanticCapabilityContractExporter.java` | 7 ACTIVE + KNOWN_GAP/PLANNED |
| **Matrix** | `ai/semantic/matrix/StockReduceSemanticCapabilityMatrix.java` | contract-locked guard |
| **Agent** | `ai/agent/business/StockReduceAgent.java` | BusinessAgent |
| **Planner** | `ai/planner/StockReducePlannerAgentAdapter.java` | ReadBridge 架构 |
| **Frame Validator** | `ai/semantic/frame/StockReduceCurrentSemanticFrameValidator.java` | 2.5KB |

### 链路完整性：✅ 完整

- 独立 Tool/Service/Mapper/XML，边界清晰
- 双路径设计（harness vs legacy）有明确注释
- Mapper 覆盖：按 type 统计、多门店聚合、成本分页、商品排行等

---

## 5. WAREHOUSE（库房域）

### 全链路

```text
WarehouseStockOverviewTool
  → GbDepartmentGoodsStockService (库存快照) + GbDepartmentGoodsStockReduceService (进出核算)
    → GbDepartmentGoodsStockMapper + GbDepartmentMapper
      → GbDepartmentGoodsStockMapper.xml → gb_department_goods_stock 表
      → GbDepartmentMapper.xml → gb_department 表
  → WarehouseAnswerPlanBuilder → WarehouseAnswerPlan
```

### 文件清单

| 层 | 文件 | 说明 |
|----|------|------|
| **Tool** | `ai/tool/business/WarehouseStockOverviewTool.java` | 54KB，直接注入多个 Service + Mapper |
| **Tool Executor** | `ai/graph/business/WarehouseStockOverviewToolExecutor.java` | 编排 |
| **Service (主)** | `service/GbDepartmentGoodsStockService.java` | 库存快照 |
| **ServiceImpl** | `service/impl/GbDepartmentGoodsStockServiceImpl.java` | `extends ServiceImpl<GbDepartmentGoodsStockMapper>` |
| **Mapper (主)** | `mapper/GbDepartmentGoodsStockMapper.java` | 库存查询 |
| **XML (主)** | `resources/mapper/GbDepartmentGoodsStockMapper.xml` | ~35KB |
| **Mapper (辅)** | `mapper/GbDepartmentMapper.java` | 部门信息 |
| **XML (辅)** | `resources/mapper/GbDepartmentMapper.xml` | ~16KB |
| **AnswerPlan** | `ai/dto/business/WarehouseAnswerPlan.java` | DTO |
| **AnswerPlan Builder** | `ai/graph/business/WarehouseAnswerPlanBuilder.java` | 含 Matrix 引用 |
| **Contract Exporter** | `ai/semantic/contract/WarehouseSemanticCapabilityContractExporter.java` | 5 ACTIVE + KNOWN_GAP |
| **Matrix** | `ai/semantic/matrix/WarehouseSemanticCapabilityMatrix.java` | contract-locked guard |
| **Agent** | `ai/agent/business/WarehouseStockAgent.java` | BusinessAgent |

### 链路完整性：⚠️ 无独立 Mapper

- **无专属 Warehouse 表或 Mapper**：完全复用 `GbDepartmentGoodsStock`（库存快照）和 `GbDepartmentGoodsStockReduce`（进出核算），在业务语义上用"库存剩余=入库(采购)−核销(出库)"表达库房状态
- Tool 直接引用 `GbDepartmentMapper`（跳过 Service 层），不一致
- 54KB Tool 过重，包含库存预警、低库存/高积压/批次等逻辑

---

## 6. DISH_PROFIT（菜品利润域）

### 全链路

```text
DishProfitAnalysisTool
  → GbDepFoodBusinessInsightService
    → GbDepFoodMapper + GbDepFoodSalesMapper + GbDepartmentMapper 
      + GbDepartmentGoodsStockReduceService → GbDepartmentGoodsStockReduceMapper (间接)
      + GbDishCostAnalysisService (成本分析)
    → 多 XML
  → DishProfitAnswerPlanBuilder → DishProfitAnswerPlan (via DishProfitAgentNode)
```

### 文件清单

| 层 | 文件 | 说明 |
|----|------|------|
| **Tool** | `ai/tool/business/DishProfitAnalysisTool.java` | 21KB，直接注入 `GbDepFoodBusinessInsightService` |
| **Tool Executor** | `ai/graph/business/DishProfitQueryToolExecutor.java` | 编排 |
| **Service (核心)** | `service/GbDepFoodBusinessInsightService.java` | 接口 |
| **ServiceImpl** | `service/impl/GbDepFoodBusinessInsightServiceImpl.java` | 聚合多数据源 |
| **Mapper** | `mapper/GbDepFoodMapper.java` | 菜品基础信息 |
| **XML** | `resources/mapper/GbDepFoodMapper.xml` | ~3KB |
| **Mapper** | `mapper/GbDepFoodSalesMapper.java` | 菜品销售 |
| **XML** | `resources/mapper/GbDepFoodSalesMapper.xml` | ~0.4KB |
| **AnswerPlan** | `ai/dto/business/DishProfitAnswerPlan.java` | DTO |
| **AnswerPlan Builder** | `ai/graph/business/DishProfitAnswerPlanBuilder.java` | 委托 `DishProfitAgentNode.computeOverviewAndAttachPlans` |
| **AgentNode** | `ai/graph/business/DishProfitAgentNode.java` | **117KB 超级节点**，含业务计算 + Overview 推导 |
| **Contract Exporter** | `ai/semantic/contract/DishProfitSemanticCapabilityContractExporter.java` | 4 ACTIVE + KNOWN_GAP |
| **Matrix** | `ai/semantic/matrix/DishProfitSemanticCapabilityMatrix.java` | 34KB |
| **Agent** | `ai/agent/business/DishProfitAgent.java` | BusinessAgent |
| **Planner** | `ai/planner/DishProfitPlannerAgentAdapter.java` | ReadBridge 架构 |
| **Composer** | `ai/composer/renderer/DishProfitDeterministicRenderer.java` | 26KB 渲染层 |

### 链路完整性：⚠️ 无独立 Mapper，聚合多源

- DishProfit **没有自己的数据库表或 Mapper**，数据完全由 `GbDepFoodBusinessInsightService` 聚合以下来源计算得到：
  - `GbDepFoodMapper` — 菜品定义
  - `GbDepFoodSalesMapper` — 菜品销售明细
  - `GbDepartmentGoodsStockReduceMapper` — 出库成本（间接通过 `GbDepartmentGoodsStockReduceService`）
  - `GbDishCostAnalysisService` — 成本分析（间接通过 `GbDishCostAnalysisServiceImpl`, 183KB）
  - `GbDepartmentService` / `GbDepartmentMapper` — 门店信息
- `DishProfitAgentNode.java` 117KB 是最大的单文件，工具编排 + AnswerPlan + Overview + Rendering 高度耦合
- AnswerPlan 通过 `DishProfitAgentNode.computeOverviewAndAttachPlans()` 生成，非独立 Builder 模式

### 关键依赖链（间接）

```text
GbDepFoodBusinessInsightService
  ├── GbDepFoodService → GbDepFoodMapper (菜品)
  ├── GbDepFoodSalesService → GbDepFoodSalesMapper (销售)
  ├── GbDistributerFoodService (分销商菜品)
  ├── GbDepartmentGoodsStockReduceService → GbDepartmentGoodsStockReduceMapper (出库成本)
  ├── GbDishCostAnalysisService → 成本分析聚合
  ├── GbDepartmentService → GbDepartmentMapper (部门/门店)
  └── GbDepartmentGoodsStockReduceSupport (工具类)
```

---

## 7. DISH_SALES（菜品销售域）

### 全链路

```text
(无独立 Tool)
→ 复用 DishProfitAnalysisTool (AiBusinessToolIds.DISH_PROFIT_ANALYSIS)
  → 同 DishProfit 全链
→ DishSalesAnswerPlanBuilder → DishSalesAnswerPlan
```

### 文件清单

| 层 | 文件 | 说明 |
|----|------|------|
| ~~Tool~~ | ❌ 不存在 | 代码注释：`Historical removed：独立 dish_sales_query Tool（DishSalesQueryTool）已删除` |
| ~~Service~~ | ❌ 不独立 | 复用 DishProfit 的 `GbDepFoodBusinessInsightService` |
| ~~Mapper~~ | ❌ 不独立 | 同 DishProfit |
| ~~XML~~ | ❌ 不独立 | 同 DishProfit |
| **AnswerPlan** | `ai/dto/business/DishSalesAnswerPlan.java` | DTO |
| **AnswerPlan Builder** | `ai/graph/business/DishSalesAnswerPlanBuilder.java` | 从 `dish_profit_analysis` 快照提取 dishRows |
| **Contract Exporter** | `ai/semantic/contract/DishSalesSemanticCapabilityContractExporter.java` | 7 ACTIVE + KNOWN_GAP |
| **Matrix** | `ai/semantic/matrix/DishSalesSemanticCapabilityMatrix.java` | 40KB |
| **Composer** | `ai/composer/renderer/DishSalesDeterministicRenderer.java` | 5KB 渲染层 |

### 链路完整性：❌ 无独立 Tool/Service/Mapper

- **DishSales 完全寄生在 DishProfit 上**：
  - 共用同一个 Tool：`DishProfitAnalysisTool`
  - 共用同一个 Service 链
  - AnswerPlan 从 DishProfit 结果中提取 `dishRows` 字段
  - 唯一的独立性体现在 `DishSalesAnswerPlanBuilder` 对结果的重新解释
- `DishSalesDeterministicRenderer` 相比 DishProfit 的 Renderer 极为轻量（5KB vs 26KB）

---

## 8. 问题汇总

### 8.1 缺失独立 Tool（1 域）

| 域 | 状态 | 影响 |
|----|------|------|
| **DISH_SALES** | 无独立 Tool | 完全依赖 DishProfitAnalysisTool，语义解析后仍走同一数据源。无法独立演进/优化。 |

### 8.2 缺失独立 Mapper（3 域）

| 域 | 状态 | 复用情况 |
|----|------|----------|
| **WAREHOUSE** | 无专属 Mapper | 复用 `GbDepartmentGoodsStockMapper` + `GbDepartmentMapper` |
| **DISH_PROFIT** | 无专属 Mapper | 通过 Service 聚合 6+ 数据源 |
| **DISH_SALES** | 无专属 Mapper | 寄生在 DishProfit 上 |

### 8.3 Tool 过重（2 域）

| 域 | 文件 | 大小 | 问题 |
|----|------|------|------|
| **PURCHASE** | `PurchaseOverviewTool.java` | 72KB | SQL 编排直接写在 Tool 中 |
| **WAREHOUSE** | `WarehouseStockOverviewTool.java` | 54KB | 库存预警逻辑混杂 |

### 8.4 超大 AgentNode（1 域）

| 域 | 文件 | 大小 | 问题 |
|----|------|------|------|
| **DISH_PROFIT** | `DishProfitAgentNode.java` | 117KB | 工编排 + AnswerPlan + Overview + Rendering 耦合 |

### 8.5 Mapper 调用跳过 Service 层（1 域）

| 域 | 问题 |
|----|------|
| **WAREHOUSE** | `WarehouseStockOverviewTool` 直接注入 `GbDepartmentMapper`（跳过 Service） |

---

## 9. 各域链路评分

| 域 | Tool | Service | Mapper | XML | AnswerPlan | 总分 | 评级 |
|----|------|---------|--------|-----|------------|------|------|
| **REVENUE** | ✅ | ✅ | ✅ | ✅ | ✅ | 5/5 | 🟢 完整 |
| **PURCHASE** | ⚠️ | ✅ | ✅ | ✅ | ✅ | 4/5 | 🟡 过重 |
| **STOCK_REDUCE** | ✅ | ✅ | ✅ | ✅ | ✅ | 5/5 | 🟢 完整 |
| **WAREHOUSE** | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ✅ | 2/5 | 🟠 借壳 |
| **DISH_PROFIT** | ✅ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | 2/5 | 🟠 聚合 |
| **DISH_SALES** | ❌ | ❌ | ❌ | ❌ | ⚠️ | 1/5 | 🔴 寄生 |

### 评分说明

| 层 | ✅ | ⚠️ | ❌ |
|----|-----|------|-----|
| Tool | 有独立 Tool | 有但过重/越界 | 无独立 Tool |
| Service | 有独立 Service | 有但聚合多源/无专属逻辑 | 无独立 Service |
| Mapper | 有独立 Mapper | 复用其他 Mapper | 无专属 Mapper |
| XML | 有独立 XML | 复用其他 XML | 无专属 XML |
| AnswerPlan | Builder + DTO 完整 | 有但耦合/寄生 | 无 |

---

## 10. 改进建议

### P3 建议（按优先级）

1. **DISH_SALES 独立化**：提供专属 Tool（如 `DishSalesQueryTool`），降低对 DishProfit 的寄生耦合
2. **PurchaseOverviewTool 拆解**：将 72KB Tool 中的 SQL 查询逻辑下沉到 Service 层
3. **WarehouseStockOverviewTool 拆解**：提取库存预警为独立 Service；统一通过 Service 调用 Mapper
4. **DishProfitAgentNode 拆分**：将 AnswerPlan 构建与 Rendering 逻辑从 117KB 节点中分离

### 架构原则（长期）

- **Tool 层**：只做参数解析 + 结果组装，SQL/业务逻辑归 Service
- **Service 层**：每个域应有独立 Service，可调用其他域 Service 但不应跳过
- **Mapper 层**：每个域应有独立 Mapper（至少逻辑边界独立）
- **AnswerPlan 层**：Builder 与 Tool/SQL 完全解耦，仅消费 Tool 快照

---

*本文档为 2026-05-24 代码快照审计；后续以源码实际变更为准。*
