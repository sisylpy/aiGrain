# Semantic Capability Contract Exporter 全局治理

> **状态**：P0 + P2 + P3 + **P4（Java boundary hints 收口）** 已落地  
> **目标**：杜绝「第三套语义规则」——Java 合同导出层不得复制 `semantic_intake.v1.md` / `query_semantic_parser.v2.md` 的中文 Prompt。  
> **关联**：`.cursor/rules/semantic-contract-exporter.mdc`、`MatrixBackedContractExporterSupport`

---

## 1. 三层语义规则（只允许两套 NL）

| 层 | 职责 | 允许自然语言 |
|----|------|----------------|
| **Intake** | `semantic_intake.v1.md` → 粗域、`reason`、协议字段 | 是 |
| **V2 Parser** | `query_semantic_parser.v2.md` → wire / slots / `selectedContractId` | 是 |
| **Harness** | `AiHarnessBuiltinCases`、replay JSON → 回归问句与期望 | 是（测试数据） |
| **Matrix** | `*SemanticCapabilityMatrix` 行 → 结构化槽位 SSOT | 否（仅枚举字段） |
| **Contract Exporter** | Matrix → `SemanticCapabilityContract` 机器合同 | **否（禁止中文 hint / 问法 examples）** |
| **执行链** | Tool / SQL / AnswerPlan / Composer | 否 |

若在 Exporter（或 `DomainContractSelector` 内长段中文 `contractSelectionBoundaryHints`）补问法，会形成与 Prompt 并行的**第三套规则**，维护成本指数上升。

---

## 2. Exporter 职责（机器合同导出）

`SemanticCapabilityContractExporter` **只读**导出以下字段（来自 Matrix / Lexicon PLANNED 行）：

- `contractId`、`domain`、`intentCode`、`pathCode`
- `wire`、`queryObject`(s)、`operation`(s)、`metric`(s)
- `sourceFacet`、`detailWanted`、`answerPlanType`
- `requiresAnchor`、`anchorType`、`selectedTools`
- `status`、`gapMarker`

**禁止在 Exporter 中设置**（P0 起冻结，后续瘦身删除存量）：

- `description`、`selectionHint`、`negativeHint`
- `positiveExamples`、`negativeExamples`

混淆边界、老板口语、正反例 → **只**写入 Intake / V2 / Harness。

---

## 3. 域 Exporter 分级（2026-06 快照）

| 域 | Exporter | 行数约 | 分级 | 说明 |
|----|----------|--------|------|------|
| REVENUE | `RevenueSemanticCapabilityContractExporter` | 206 | **薄模板** | 仅 Matrix 映射，无 `SelectionMetadata` |
| BUSINESS_OVERVIEW | `BusinessOverviewSemanticCapabilityContractExporter` | 119 | **薄模板** | 仅 Matrix 映射；少量 row 特化（非 NL） |
| BUSINESS_DIAGNOSIS | `BusinessDiagnosisSemanticCapabilityContractExporter` | 203 | **薄模板** | Matrix + tool/contractId 映射（非 NL） |
| WAREHOUSE | `WarehouseSemanticCapabilityContractExporter` | ~200 | **薄模板（P0）** | 已改用 `MatrixBackedContractExporterSupport` |
| PURCHASE | `PurchaseSemanticCapabilityContractExporter` | ~320 | **薄模板（P2）** | 边界在 V2 §PURCHASE |
| STOCK_REDUCE | `StockReduceSemanticCapabilityContractExporter` | ~200 | **薄模板（P2）** | 边界在 V2 §STOCK_REDUCE |
| DISH_SALES | `DishSalesSemanticCapabilityContractExporter` | ~150 | **薄模板（P3）** | 边界在 V2 §DISH_SALES + Intake §26 |
| DISH_PROFIT | `DishProfitSemanticCapabilityContractExporter` | ~150 | **薄模板（P3）** | 边界在 V2 §DISH_PROFIT |
| DISH_COST | `DishCostAnalysisSemanticCapabilityContractExporter` | ~100 | **薄模板（P3）** | 边界在 V2 §DISH_COST + Intake §34a |
| MENU_OPERATION | `MenuOperationSemanticCapabilityContractExporter` | ~110 | **薄模板（P3）** | 边界在 V2 §MENU_OPERATION + Intake §27–30 |

**无 Exporter 的域**：无。`SemanticContractCatalog` 已注册 10 域，与 `DomainRoutingContractCatalog` 对齐。

---

## 4. 瘦身顺序（改代码阶段，本轮不批量执行）

按「依赖面 / 行数 / 与 V2 重复度」排序：

| 阶段 | 范围 | 动作 | 风险 |
|------|------|------|------|
| **P0** | 规则 + `MatrixBackedContractExporterSupport` + WAREHOUSE | 冻结新增 hint；WAREHOUSE 薄导出 | 低 |
| **P1** | REVENUE / BO / BD | 文档标注为模板；新域复制模板 | 低 |
| **P2** | STOCK_REDUCE、PURCHASE | ✅ 删除 `selectionMetadata`；边界迁入 V2 + Intake §21 指针 | 低（已落地） |
| **P3** | DISH_SALES、DISH_PROFIT、DISH_COST、MENU_OPERATION | ✅ 同上 | 低（已落地） |
| **P4** | `DomainContractSelector`、`SemanticParserInputBuilder`、`WarehouseInventoryShortageSemanticsSupport` | ✅ 删除 Java 中文 `contractSelectionBoundaryHints`；边界在 V2/Intake；库房风险保留 **allowedContracts 过滤**（结构化） | 低（已落地） |
| **P5** | `DomainRoutingContract.routeExample`、Intake `resolveClarificationQuestion` 用户话术 | Step 1 路由示例 / 澄清回复文案；非 Step 2 合同边界 | 低 |

**本轮明确不改**：Tool、SQL、AnswerPlan、Composer、Matrix 业务行、主链 Resolver。

---

## 5. 目标代码形态

```
*SemanticCapabilityMatrixRow
        │
        ▼
MatrixBackedContractExporterSupport.build(spec)   ← 只填结构化字段
        │
        ▼
SemanticCapabilityContract
        │
        ▼
SemanticContractCatalog → DomainContractSelector → Parser allowedOutputContract
```

各域 Exporter 仅保留：

- `domain()`、`exportActive/Planned/KnownGap`
- `contractIdForRow(row)`、`selectedToolsForRow(row)` 等 **结构化** 映射
- 调用 `MatrixBackedContractExporterSupport`（禁止 `applySelectionMetadata` 类方法）

**禁止**：按问法、`contractId` 大段中文 `switch`、`positiveExamples` 列表。

---

## 6. 与其它组件的边界

| 组件 | 是否放 NL | 备注 |
|------|-----------|------|
| `DomainRoutingContract.routeExample` | 少量域级 | Step 1 路由，不是 Step 2 合同 |
| `DomainContractSelector.contractSelectionBoundaryHints` | **恒 null** | 域边界见 V2 专节 + `knownGapContracts` |
| `SemanticParserInputBuilder` 合同 enrich | **已删除** | DISH_SALES 单店 / PURCHASE 清单时间追问见 V2 |
| `WarehouseInventoryShortageSemanticsSupport.filterContractSelection` | **仅过滤 allowed** | `warehouseInventorySemantics` + 单条 `inventory_risk_list`；NL 见 Intake §13a + V2 WAREHOUSE |
| `SemanticCapabilityContractMatcher` | 否 | 槽位/token 集合匹配，非用户原文 |
| `SemanticContractValidator` | 否 | 对照合同结构 |

---

## 7. PR / Review 检查清单

修改 `ai/semantic/contract/*Exporter*` 时：

- [ ] 未新增 `description` / `selectionHint` / `negativeHint` / `examples`
- [ ] 未为用户问句增加 `contains` / 中文 if/else
- [ ] 新合同行已写入对应 `*SemanticCapabilityMatrix` + V2 域章节 +（如需）Harness case
- [ ] 使用 `MatrixBackedContractExporterSupport.build` 或等价薄模板

---

## 8. P4 收口清单（2026-06）

| 位置 | 原 Java 中文边界 | 处置 |
|------|------------------|------|
| `DomainContractSelector.warehouseBoundaryHints` | WH-C vs 缺货/临期 | **删除**；V2 §WAREHOUSE + Intake §13/13a + `knownGapContracts` |
| `DomainContractSelector` STOCK_REDUCE 数量排行 | 金额 vs 数量 | **删除**；V2 §STOCK_REDUCE 商品排行表 |
| `SemanticParserInputBuilder` DISH_SALES 单店 | 单菜 vs 排行继承 | **删除**；V2 §DISH_SALES 单店硬规则 + `visibleStores` |
| `SemanticParserInputBuilder` PURCHASE 清单时间追问 | period_goods_list 继承 | **删除**；V2 **period_goods_list 仅改时间追问** + Intake Step 2 指针 |
| `WarehouseInventoryShortageSemanticsSupport.filterContractSelection` | Intake 风险中文 hint | **删除 hint**；**保留** `allowedContracts` 过滤（仅 `inventory_risk_list` 或 NEAR_EXPIRY 空 allowed） |

**未改**：Tool / SQL / AnswerPlan / Card / Composer / Time / Scope / Semantic Inheritance / Matrix / 主链 Resolver 执行顺序。

**仍含中文、非 Step-2 合同边界（P5 观测）**：`DomainRoutingContract.routeExample`（Step 1 路由示例）；`WarehouseInventoryShortageSemanticsSupport.resolveClarificationQuestion` 澄清回复话术；`toAllowedEntry` 仍透传已废弃 entry 级 hint 字段（Exporter 已不填）。

---

## 9. 参考

- 两段式合同设计：`docs/ai/semantic-allowed-output-contract-design.md`
- Harness 边界：`.cursor/rules/harness-java-boundary.md` §错误 7
- 硬规则：`.cursor/rules/semantic-contract-exporter.mdc`
