# AI 查询语义词典（公共层 v1）

> **【历史口语附录 · 非 wire 权威源】**
> - 本文件为**历史口语附录 / 采购早期参考**，便于对照用户说法与数据库口径。
> - **不是**当前 `structuredIntentDetailWire` / canonical wire 的权威源。
> - 当前 wire 权威以 **`src/main/resources/ai-prompts/semantic/semantic-output-schema.md`**、**`AiQuerySemanticLexicon.java`**、各 **`*-drilldown-matrix-contract.md`** 及生产 Prompt **`query_semantic_parser.v2.md`** 为准。

本文件与 **`AiFollowUpResolver` / `AiResolvedQueryContext`** 配套：统一**用户口语 → domain / path / 结构化意图 / 数据库口径**，避免各 Agent 重复猜词。

扩展计划见 **`docs/TODO_MULTI_AGENT.md`**。

---

## 采购域（purchase）

### 1. 自采 / 自采购 / 自己买的 / 市场买的

| 维度 | 说明 |
|------|------|
| **用户可能说法** | 自采、自采购、自己买的、市场买的、菜场买的 |
| **归属** | 采购概览链路（`purchase_overview_path`），非经营/菜品/库存主链 |
| **intentCode（工具前）** | `PURCHASE_OVERVIEW` |
| **pathCode** | `purchase_overview_path` |
| **structuredIntentDetail** | `purchase_source_summary` |
| **purchaseSourceType** | `SELF_PURCHASE` |
| **旧版参考** | `GbAiChatServiceImpl#appendPurchaseSupplyMixSummary`：`GbConstants.PurchaseOrderType.SELF_PURCHASE`（type=1）且 `gb_DPG_purchase_nx_supplier_id` 为 **null 或 -1** 归入自采桶；与 type=5 / type=1+nx 正 的「供货商侧」区分见旧方法注释 |
| **数据库判断（口径摘要）** | 采购商品行：`gb_DPG_purchase_type`；自采严格桶：`type=1` 且 (`nx_supplier_id` IS NULL OR `nx_supplier_id` = -1)；**禁止**把 `nx=-1` 暴露为用户可见「供货商 ID」 |
| **用户可见怎么说** | 「自采」「门店自采」等；只说笔数与金额 |
| **禁止** | 「供货商 ID -1」「nx=-1」「purchase_type=1」等内部字段 |

---

### 2. 供货商采购 / 供应商采购

| 维度 | 说明 |
|------|------|
| **用户可能说法** | 供货商采购、供应商采购、配送供货、订货（在采购语境下） |
| **归属** | `purchase_overview_path` |
| **structuredIntentDetail** | `purchase_source_summary` |
| **purchaseSourceType** | `SUPPLIER_PURCHASE` |
| **旧版参考** | `appendPurchaseSupplyMixSummary`：type=5（`DELIVERY_SUPPLIER`）；type=1 且 `gb_DPG_purchase_nx_supplier_id` 为正 → 供货商维度入库 |
| **数据库判断** | type=5 或 (type=1 且 nx 正整数) |
| **用户可见** | 「供货商采购」「供货商维度入库」 |
| **禁止** | 在「纯自采」结论下忽略 type=1+nx 正 的供货商行（旧版已强调） |

---

### 3. 采购入库

| 维度 | 说明 |
|------|------|
| **用户可能说法** | 采购入库、进货入库、入库完成了多少 |
| **归属** | `purchase_overview_path` |
| **purchaseSourceType** | `ALL` 或未限定 |
| **数据库** | 与 `queryGbPurchaseGoodsCount` 同 join：`gb_DPG_status` > 2、`gb_DPG_stock_finish_date`、排除退货类型等（见 Mapper） |

---

### 4. 供货商排行追问（多轮）

| 维度 | 说明 |
|------|------|
| **用户可能说法** | 哪个供货商/供应商金额最高、谁家供货最多、供货排行/排名、供货商采购金额排名、哪个供应商采购最多 |
| **followUpType** | `SUPPLIER_RANKING` |
| **structuredIntentDetail** | `supplier_amount_ranking` |
| **规则** | 上一轮须为 `purchase_overview_path`；继承时间与组织范围；仍走 `PurchaseOverviewTool` |

---

### 5. 核销 / 出库 / 生产耗用 / 报损 / 损耗 / 退货（占位）

| 维度 | 说明 |
|------|------|
| **说明** | v1 仅收录表达与归属边界，**不要求**本轮改库存/核销 SQL |
| **核销 / 出库 / 生产耗用** | 多与 `stock_reduce_query`、成本/库存域相关；用户追问「采购之后有没有核销」可走 `purchase_overview_path` + 核销工具组合（现有 Planner 编排），词典后续细化 |
| **报损 / 损耗 / 退货** | 退货采购类型常为 `RETURN(9)`，与入库统计排除条件一致；报损/损耗多属出库分型，见库存减少表 |
| **禁止** | 向用户展示内部 `type` 数字且无业务译名 |

---

## 多轮追问（规则层、无 LLM）

与时间 / 范围继承规则见 **`AiFollowUpResolver`** 与 **`AI_AGENT_DEVELOPMENT_GUIDE.md`** 后续条款。
