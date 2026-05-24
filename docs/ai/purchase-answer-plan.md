# 采购链路：PurchaseAnswerPlan 与 Harness 契约

> **前提**：采购金额、自采/供货商拆分、Top 商品与供货商、门店合并范围等**不重新发明口径**——以 `PurchaseMethodLegacyAggRow` / `GbDistributerPurchaseGoodsMapper.xml`（`purGoodsWhereLegacyPurchaseMethodFocus`、`queryGbPurchaseGoodsAggByLegacyPurchaseMethod`）、以及当前 Harness 的 **`PurchaseOverviewTool`** 为准。  
> **目标**：把采购问答从「Tool JSON + Composer/兜底 if-else 即兴拼答」收敛为 **`PurchaseAnswerPlan` → Composer 只读计划**，与 **`DishProfitAnswerPlan`** 同一工程范式；Debug / Replay 可核对计划与上下文。  
> **本轮**：仅本文档；**不要**改 Java、不要动 SQL、不要扩散经营诊断/菜品毛利/出库链路。

全局分层说明见：`docs/ai/harness-composer-architecture.md`。菜品毛利对照见：`docs/ai/dish-profit-answer-plan.md`。

---

## 1. 从现状到目标架构

### 1.1 当前 Harness 采购链路（已实现）

| 环节 | 代码 / 配置 | 说明 |
|------|-------------|------|
| 意图 | `AiResolvedQueryIntent.PURCHASE_OVERVIEW`、`PATH_PURCHASE_OVERVIEW`（`purchase_overview_path`） | 与经营概览分流；集团门店合并走 `groupPurchaseOverview` |
| 规划 | `BusinessDataPlannerNode` | `purchase_overview_path`、`STORE_PURCHASE_OVERVIEW` / `GROUP_PURCHASE_OVERVIEW`、`dataPlanTools` 含 `PURCHASE_OVERVIEW` |
| 执行 | `BusinessToolExecutionNode` | 组装 `disId`、日期、`ARG_GROUP_PURCHASE_AGGREGATION`、`ARG_VISIBLE_STORES`、`ARG_PURCHASE_SOURCE_FOCUS`、`ARG_PURCHASE_NARRATIVE_MODE`、`ARG_QUERY_SCOPE_BANNER` 等 |
| 工具 | `PurchaseOverviewTool` | 与旧版一致的 legacy 桶：`supplier_channel` / `self_strict` / `other`；供货商 Top 经 `filterRealSupplierSpendTopRows` |
| 合成 | `StubAnswerComposerNode` | `PURCHASE_COMPOSER_SYSTEM` + `purchaseOverview` JSON；多路 **fallback**（`purchaseOverviewStructuredFallback`、`purchaseSupplierRankingFallback` 等） |

### 1.2 目标架构（与菜品毛利对齐）

```text
SemanticIntake.canonicalUserQuery + intakePrimaryDomain
    → semantic.query_parser.v2（单域 allowedContracts 内选 selectedContractId + semanticSlots）
    → Contract Validator / AiResolvedQueryContext
    → PurchaseOverviewTool（参数均可追溯到 Context；SQL 口径不变）
    → ToolResult（现有 purchaseOverview 载荷保留作事实层）
    → PurchaseAnswerPlan（以 selectedContractId / queryObject / operation / metric / sourceFacet / anchorPolicy 为主语义依据）
    → Composer：仅朗读 AnswerPlan + 必要边界说明；禁止重算、重排、重选供货商
    → Debug / Replay：透出 semanticSlots、selectedContractId、purchaseAnswerPlan* 与上下文字段
```

**主语义依据（目标路径）**：`semanticSlots.selectedContractId` 及同 entry 对齐的 `queryObject`、`operation`、`metric`、`sourceFacet`、`anchorPolicy`。AnswerPlan 类型与选行逻辑应**优先**由上述槽位 + Matrix entry 推导，而非由 Composer 或 fallback 即兴判断。

**兼容 / 派生字段（非目标主路径）**：`queryIntent.structuredIntentDetail`（wire）、`purchaseSourceType` 可保留作 debug、迁移映射或派生输入；**不得**再作为唯一主语义来源抢权。

**Fallback（历史迁移项）**：`StubAnswerComposerNode` 内采购 **if/else fallback**（`purchaseOverviewStructuredFallback`、`purchaseSupplierRankingFallback` 等）为**历史迁移**路径；**不是**目标主路径。新问法应走 Intake → v2 合同选择 → AnswerPlan → Composer；fallback 仅在没有 Plan / 旧链路兼容时保留，**不得**扩展为新业务规则入口。

**Composer 不得**：重新排序商品或供货商、重新计算金额、把自采占位当真实供应商、把「供货商采购总额」与「供货商金额排行」混为一谈。

---

## 2. 采购口径真值（必须复用）

### 2.1 统计与表述约定

- **金额** `gb_DPG_buy_subtotal`，日期 **`gb_DPG_stock_finish_date`（入库完成日）**，状态 `gb_DPG_status > 2`，排除 `gb_DPG_purchase_type = 9`（与现有统计接口一致）。
- **自采 vs 供货商**：强调 **`gb_DPG_purchase_nx_supplier_id`** 与入库批次 `gb_dgs_nx_supplier_id` 同语义：**-1 = 自采**；正整数 = 供货商 ID；勿只对用户念 `type=5`/`type=1`。
- 与库存减少 `type=1` **不是**同一口径（禁止把出库成本表里的 type=1 说成采购额）。

### 2.2 当前 Tool 内注释与 DTO（权威拆分桶）

`PurchaseMethodLegacyAggRow`（与 Mapper `queryGbPurchaseGoodsAggByLegacyPurchaseMethod` 一致）：

| `methodBucket` | 含义（后台） | SQL 归类规则（摘要） |
|----------------|--------------|----------------------|
| `supplier_channel` | 供货商采购 | `purchase_type = 5`，或 `purchase_type = 1` 且 **`gb_DPG_purchase_nx_supplier_id` 非空且 ≠ -1** |
| `self_strict` | 自采 | `purchase_type = 1` 且 **`nx_supplier_id` 为 null 或 -1** |
| `other` | 其它方式 | 其余 `purchase_type` |

聚焦过滤（`legacyPurchaseMethodFocus`）见 `GbDistributerPurchaseGoodsMapper.xml` → `purGoodsWhereLegacyPurchaseMethodFocus`：

- **自采**：`type = 1` 且 `(nx IS NULL OR nx = -1)`。
- **供货商**：`type = 5` 或 `(type = 1` 且 `nx` 非空且 `≠ -1)`。

与用户在需求稿中写的「type=5 + 正整数 nx」**口径一致**；**自采**在 legacy 桶里同时覆盖 **`type=1` 且 nx 占位**，文档与实现以此为准（旧数据中「仅 type=1」但无供货商 ID 仍计自采）。

### 2.3 `PurchaseOverviewTool`

- 采购方式汇总：`resolvePurchaseMethodSection` ↔ 旧版 `appendPurchaseSupplyMixSummary` 同一维度。
- SQL 条件：与 `queryGbPurchaseGoodsCount` **同一 join/筛选**（含 `typeNotEqual` 退货排除等）。
- **供货商 Top**：`filterRealSupplierSpendTopRows` / `isRealSupplierSpendTopRow`——排除 `supplierId` null/`≤0`、名称为「自采」、名称含「供货商ID -1」「供货商ID-1」等占位展示。
- **排行商品**：`queryGbPurchaseGoodsTopTimesMerged`、`queryGbPurchaseGoodsTopSubtotalMerged`——服务端已排序；映射为 `goodsPurchaseFrequencyTop`、`goodsPurchaseAmountTop`（当前各取 Top 5）。

### 2.4 结构化 wire 与 AnswerPlan 类型（兼容层）

`AiQuerySemanticLexicon` 已有 wire → Debug 面板大写枚举映射（见 `purchaseStructuredDetailWireToDebugEnumName`）。在 **SemanticIntake + v2 合同选择** 主链下，这些 wire **从** `selectedContractId` / `semanticSlots` **派生或兼容映射**，**不是**目标主语义入口。

| wire（`structuredIntentDetail`，兼容/派生） | 典型场景 |
|-----------------------------------|----------|
| `purchase_overview_summary` | 采购总览、全口径摘要 |
| `purchase_source_summary` | 来源拆分 + 商品 Top 等「窄答」 |
| `purchase_source_amount_query` | 主要问金额/笔数 |
| `purchase_source_goods_query` | 主要问商品频次/金额排行 |
| `supplier_amount_ranking` | 供货商金额排行 |

**第一版 `PurchaseAnswerPlan.type`（建议）** — 优先由 **`semanticSlots.selectedContractId`** 及 `queryObject` / `operation` / `metric` / `sourceFacet` 映射；下表 wire / `purchaseSourceType` 列为**兼容对照**：

| PurchaseAnswerPlan.type | 主语义（目标） | 兼容 wire / purchaseSourceType |
|-------------------------|----------------|--------------------------------|
| `PURCHASE_OVERVIEW` | contract entry：总览类 | `purchase_overview_summary` / `ALL` |
| `PURCHASE_SELF_OVERVIEW` | entry + `sourceFacet` 自采 | `purchase_source_*` + `SELF_PURCHASE` |
| `PURCHASE_SUPPLIER_OVERVIEW` | entry + 供货商口径 | `purchase_source_*` + `SUPPLIER_PURCHASE` |
| `PURCHASE_GOODS_AMOUNT_RANKING` | entry：商品金额排行 | `purchase_source_goods_query` |
| `PURCHASE_GOODS_COUNT_RANKING` | entry：商品次数排行 | `purchase_source_goods_query` |
| `PURCHASE_SUPPLIER_AMOUNT_RANKING` | entry：供货商金额排行 | `supplier_amount_ranking` |
| `PURCHASE_STORE_COMPARISON` | entry：门店对比 | `purchase_overview_summary` + 集团 `coveredStores` |

---

## 3. 采购业务口径（写入 AnswerPlan 前必须统一）

### 3.1 采购总额（默认）

用户问「采购多少钱」：**采购入库总金额**，字段 **`gb_DPG_buy_subtotal`**，日期 **`gb_DPG_stock_finish_date`（入库完成日）**，与旧版「本月采购数据」块一致；**不是**库存核销出库金额。

### 3.2 自采

与 **`PurchaseMethodLegacyAggRow` / `purGoodsWhereLegacyPurchaseMethodFocus`** 一致：

- `gb_DPG_purchase_type = 1` 且 **`gb_DPG_purchase_nx_supplier_id` 为 null 或 -1** → **自采**（`self_strict`）。
- 旧版叙述中「type=1 且 nx 为正」归入供货商通道，不计入自采桶。

### 3.3 供货商订货（供货商采购）

- **`gb_DPG_purchase_type = 5`**，或 **`type = 1` 且 `gb_DPG_purchase_nx_supplier_id` 非空且 ≠ -1** → **`supplier_channel`**（与 Mapper CASE 一致）。
- 展示名称：现有 SQL/Service 已关联供应商实体（如 `NxJrdhSupplierEntity`）；AnswerPlan 只携带 Tool 已解析的 `supplierId` / `supplierName`。

### 3.4 供货商排行（过滤规则）

必须在 **服务端**（Tool 或 AnswerPlan 构建层）排除，Composer **不得**再过滤：

- `supplierId = -1` 或 **null**
- 自采占位、名称「自采」
- 名称含 **「供货商ID -1」** / **「供货商ID-1」**（与 `PurchaseOverviewTool#isRealSupplierSpendTopRow` 对齐）
- 其它「名称空且属自采占位」的行（若 Mapper 仍返回，在本层剔除）

若过滤后 **无真实供货商行**：`focusRows` 为空，标准提示文案（给用户）：

> 「当前口径下暂未查询到真实供货商采购记录；本期采购主要为自采。」

**不得**向用户输出 `supplierId=-1` 或占位供货商排行。

### 3.5 商品排行

采购金额 Top、采购次数 Top **必须在服务端排序**；AnswerPlan 写入 **`focusRows` / `secondaryRows`** 时带 **`debug.sortKey` / `sortDirection`**。Composer **禁止**重排。

### 3.6 「供货商采购总额」vs「供货商金额排行」

- **总额**：`summary` 内 `supplierPurchaseAmount`（或分拆字段）+ 可能仅 1 行叙述。
- **排行**：`focusRows` 为多供货商；二者语义分离，禁止用总额字段冒充 Top1。

---

## 4. 接入 `AiResolvedQueryContext`（Tool 参数来源）

下列字段为采购 Tool / AnswerPlan 的**推荐数据源**（与 `AiResolvedDataScope` 设计一致）。**目标主链**优先读 **`semanticSlots`**（含 `selectedContractId`、`queryObject`、`operation`、`metric`、`sourceFacet`、`anchorPolicy`）；`structuredIntentDetail` / `purchaseSourceType` 为**兼容或派生**字段。

| 字段 | 用途 |
|------|------|
| **`semanticSlots`**（**目标主语义**） | `selectedContractId`、`queryObject`、`operation`、`metric`、`sourceFacet`、`anchorPolicy` → AnswerPlan 类型与 Tool 叙事模式 |
| `timeWindow`（及 ISO 起止） | 查询时间窗 |
| `dataScope.queryScopeKind` | 本轮主范围类型：`STORE` / `DEPARTMENT` / `DISTRIBUTER` |
| `dataScope.queryStoreIds` | 按**门店**查询时的门店 **root id** 列表 |
| `dataScope.queryRealDepartmentIds` | 明确按**部门**时的真实部门 id |
| `dataScope.queryDistributerId` | 机构 id，单值 |
| `dataScope.expandedSqlDepartmentIds` | 仅供 SQL `department_id IN (...)`，**不得**当作对用户展示的门店列表 |
| `orgScope.visibleStores`（→ Tool `ARG_VISIBLE_STORES`） | **展示用门店**、集团合并锚点（与 `PurchaseOverviewTool` 注释一致） |
| 点名门店（多轮追问） | `mentionedStore`（`AiHarnessResolvedContextSummarizer#resolveMentionedStore`，与 Harness Replay 期望对齐） |
| `queryIntent.purchaseSourceType` | **兼容/派生**：`SELF_PURCHASE` / `SUPPLIER_PURCHASE` / `ALL` |
| `queryIntent.structuredIntentDetail` | **兼容/派生**：wire → 旧 AnswerPlan 映射；新链路应由 contract entry 推导 |

**禁止**：再把 **`queryDepartmentIds`** 当作对外主字段；**禁止**把 **`expandedSqlDepartmentIds`** 直接展示为「门店列表」。展示门店必须以 **`visibleStores` / `queryStoreIds`**（及 banner）为准。

---

## 5. PurchaseAnswerPlan 建议 JSON 形态

与 `dishProfitAnswerPlan` 类似，建议挂在 Run 状态 / `answer_delta.data` 下键名 **`purchaseAnswerPlan`**（实现阶段与 `AiRunState` 字段对齐）。

共性字段：

| 键 | 说明 |
|----|------|
| `type` | 本文 §2.4 枚举 |
| `scopeLabel` | 人类可读范围，来自 `queryScopeBanner` / 组织展示，**非** ID 列表 |
| `timeLabel` | 与 `AiTimeWindowTextFormatter` / 用户话术一致 |
| `purchaseSourceType` | `SELF_PURCHASE` / `SUPPLIER_PURCHASE` / `ALL` |
| `summary` | 总金额、总笔数、自采/供货商金额等（与 Tool 一致） |
| `focusRows` | 主答行（已排序） |
| `secondaryRows` | 次要对比行（如排行 2～5 名） |
| `debug` | `sortKey`、`sortDirection`、`excludedSupplierIds`、数据来源、`legacyPurchaseMethodFocus` 等 |

### 5.1 供货商金额排行示例

```json
{
  "type": "PURCHASE_SUPPLIER_AMOUNT_RANKING",
  "scopeLabel": "汀兰餐厅",
  "timeLabel": "上个月",
  "purchaseSourceType": "SUPPLIER_PURCHASE",
  "summary": {
    "totalAmount": 68,
    "totalCount": 3,
    "selfPurchaseAmount": 0,
    "supplierPurchaseAmount": 68
  },
  "focusRows": [
    {
      "supplierId": 123,
      "supplierName": "金调料99的222",
      "purchaseAmount": 68,
      "purchaseCount": 3
    }
  ],
  "secondaryRows": [],
  "debug": {
    "sortKey": "purchaseAmount",
    "sortDirection": "DESC",
    "excludedSupplierIds": [-1],
    "source": "PurchaseOverviewTool"
  }
}
```

### 5.2 商品采购金额排行示例

```json
{
  "type": "PURCHASE_GOODS_AMOUNT_RANKING",
  "scopeLabel": "全部门店",
  "timeLabel": "上个月",
  "purchaseSourceType": "ALL",
  "summary": {
    "totalAmount": 1491,
    "totalCount": 34
  },
  "focusRows": [
    {
      "goodsId": 1,
      "goodsName": "青鱼",
      "purchaseAmount": 400,
      "purchaseCount": 1
    }
  ],
  "secondaryRows": [
    {
      "goodsName": "去皮核桃仁",
      "purchaseAmount": 220
    }
  ],
  "debug": {
    "sortKey": "purchaseAmount",
    "sortDirection": "DESC"
  }
}
```

（若当前 `GbDistributerGoodsEntity` 映射未带 `goodsId`，第一版可仅要求 `goodsName` + 金额/次数与 Tool 一致，避免为 AnswerPlan 单独扩 SQL。）

---

## 6. Composer 规则（有 `purchaseAnswerPlan` 时）

1. **只读** `purchaseAnswerPlan` + 允许的边界文案（如无数据提示）。  
2. **历史 fallback**（`purchaseOverviewStructuredFallback`、`purchaseSupplierRankingFallback` 等）为**迁移项**：仅在 **`purchaseAnswerPlan` 缺失**时使用；**不是**目标主路径，**不得**为新问法扩展 fallback 分支。  
3. **不**重新计算采购金额、笔数、占比。  
4. **不**重新排序商品或供货商。  
5. **不**挑选「另一个」供应商或商品作为主答。  
6. **不**把自采占位、`supplierId=-1`、占位名称当作真实供货商。  
7. **`focusRows` 为空**且类型为供货商排行时：使用 §3.4 标准提示，**禁止**编造供货商。  
8. 对用户：**勿**念 `type=5`、`gb_DPG_*`、内部枚举代号；可用「供货商采购/自采」等口语。

---

## 7. Debug / Replay 透出字段（后续 Java 改造清单）

建议在 Harness Summary / Replay 中展示（与现有 `AiHarnessResolvedContextSummarizer` 扩充对齐）：

| 分类 | 字段 |
|------|------|
| SemanticIntake | `canonicalUserQuery`、`intakeStatus`、`questionMode`、`intakePrimaryDomain` |
| v2 合同 | `selectedContractId`、`semanticSlots`、`matchedContractId`、`contractValidation` |
| AnswerPlan | `purchaseAnswerPlanPresent`、`purchaseAnswerPlan.type`、`purchaseAnswerPlan.summary`、`purchaseAnswerPlan.focusRows`、`purchaseAnswerPlan.secondaryRows`、`purchaseAnswerPlan.debug` |
| 意图（兼容） | `purchaseSourceType`、`structuredIntentDetail`（wire + 调试用大写名）、`effectiveIntentCode` / `effectivePathCode` |
| 范围 | `queryScopeKind`、`queryStoreIds`、`queryRealDepartmentIds`、`queryDistributerId`、`expandedSqlDepartmentIds`、`visibleStores`（或等价摘要）、`mentionedStore`（若有） |
| 工具 | `usedToolId`（如 `PURCHASE_OVERVIEW`） |

说明：`AiHarnessResolvedContextSummarizer` 已对 `purchaseSourceType`、`purchaseSqlDepartmentIds` 等有部分透出；AnswerPlan 落地后增量挂载上述键。

---

## 8. 第一批回归测试问题（多轮 + 边界）

### 8.1 采购总览多轮

1. 这个月采购多少钱？  
2. 上个月呢？  
3. AAA 呢？  
4. 汀兰餐厅呢？  
5. 全部门店呢？

### 8.2 采购来源

6. 自采购呢？  
7. 供货商订货呢？

### 8.3 排行

8. 哪个商品采购金额最高？  
9. 哪个商品采购次数最多？  
10. 哪个供货商采购金额最高？  
11. 供货商采购金额排名是什么？

### 8.4 供应商空数据

12. AAA 上个月哪个供货商采购金额最高？  

**预期**：若 AAA **仅有自采**，应答 **没有真实供货商采购记录**，语义与 §3.4 一致；**不得**输出 `supplierId=-1` 或占位供货商名称。

---

## 9. 迁移步骤（建议小步 PR）

1. **DTO**：新增 `PurchaseAnswerPlan`（或与现有 `DishProfitAnswerPlan` 并列的 record），字段与 §5 对齐。  
2. **构建层**：在 `PurchaseOverviewTool` 执行完成之后（或紧邻的 Node）根据 **`semanticSlots.selectedContractId`** 及槽位、Tool 结果构建 `PurchaseAnswerPlan`（排序只做一次）；`structuredIntentDetail` / `purchaseSourceType` 仅作兼容映射。  
3. **RunState**：`AiRunState` 增加 `purchaseAnswerPlan`；SSE / `answer_delta` 透出。  
4. **Composer**：`StubAnswerComposerNode` 优先读 `purchaseAnswerPlan`；收紧 `PURCHASE_COMPOSER_SYSTEM` 指令，与 `DISH_PROFIT` 一样声明「禁止重排」。  
5. **Harness**：`AiHarnessResolvedContextSummarizer`、`AiHarnessExpectationComparator`（若有）扩展 §7 字段。  
6. **测试**：补 Harness / 单测（对齐 `PurchaseOverviewToolVisibleStoresScopeTest` 风格）；跑通 §8 清单。

---

## 10. 本轮明确不做

- 不继续深挖 **经营诊断** / `BusinessDiagnosisPlan`。  
- 不改 **菜品毛利** AnswerPlan 链路。  
- 不改 **出库** 链路。  
- **不重写** 采购 SQL / Mapper。  
- 不大范围重构 `BusinessDataPlannerNode`；新增 AnswerPlan 构建应尽量局部、可测试。

---

## 11. 参考路径索引

| 资源 | 路径 |
|------|------|
| 主链与 Tool 索引 | `docs/AI_MAINLINE_INDEX.md` |
| Legacy 采购 Mapper | `src/main/resources/mapper/GbDistributerPurchaseGoodsMapper.xml` |
| 采购方式桶 DTO | `src/main/java/com/nongxinle/dto/PurchaseMethodLegacyAggRow.java` |
| Harness Tool | `src/main/java/com/nongxinle/ai/tool/business/PurchaseOverviewTool.java` |
| 工具入参组装 | `src/main/java/com/nongxinle/ai/graph/business/BusinessToolExecutionNode.java` |
| 规划 | `src/main/java/com/nongxinle/ai/graph/business/BusinessDataPlannerNode.java` |
| Composer | `src/main/java/com/nongxinle/ai/graph/business/StubAnswerComposerNode.java` |
| 结构化 wire | `src/main/java/com/nongxinle/ai/conversation/AiQuerySemanticLexicon.java` |
| 公共上下文 | `src/main/java/com/nongxinle/ai/context/AiResolvedQueryContext.java`、`AiResolvedDataScope.java` |
| Harness Summary | `src/main/java/com/nongxinle/ai/harness/AiHarnessResolvedContextSummarizer.java` |
| 可见门店范围测试 | `src/test/java/com/nongxinle/ai/tool/business/PurchaseOverviewToolVisibleStoresScopeTest.java` |
