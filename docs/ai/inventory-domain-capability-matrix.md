# D-6 Inventory / WarehouseStock — 库存域能力矩阵与语义边界

## 1. 背景

- **D-5 StockReduce Batch 1** 已收口：出库专线（`STOCK_REDUCE_QUERY` / `stock_reduce_query_path`）与多店出库排行、 goods outbound 等语义边界在主力链路上已单独治理。
- **当前进入 D-6**：以 **Inventory / WarehouseStock** 为专题，把**库存现量与结构**与 **出库/核销**、**采购+库存双域风险**分清，避免用户问「仓库还有多少」却走出库排行，或问「补货」却落到核销概览。
- **库存域原则**：回答应优先基于**账面库存快照与库存工具**；出库数据只应用来解释「区间内核销结构」，不能替代「现在还有多少」；采购侧风险 wire 不能泛化成「所有积压问题」的唯一出口。
- **治理边界（D-CLEAN）**：**`warehouse_stock_overview` / `WAREHOUSE_STOCK_OVERVIEW` / `warehouse_stock_overview_path` 为现网活跃库存域主链**，禁止与已删除的 **`business_overview_query` / `BusinessOverviewQueryTool`** 类比整链删除。
- **D-CLEAN-WAREHOUSE-P1B**：已删除 **`WarehouseDeterministicRenderer` / `AnswerComposerPayloadFactory`** 及库房 raw-tool Composer fallback；**`warehouseOverview` 正文仅来自 `warehouse_stock_overview` → `WarehouseAnswerPlan` → Composer Plan-first**（无 Plan 固定 no-plan）。契约见 `docs/ai/inventory-domain-capability-matrix.md`。
- **文档索引**：无独立 `warehouse-stock-overview.md`；现网契约以本文件、`docs/AI_MAINLINE_INDEX.md`、`docs/API_INTEGRATION.md` §`warehouseOverview` 为准。已删 **`stock_query`**：现网 **`warehouse_stock_overview`**。

---

## 2. 当前链路事实（只读梳理）

| 事实 | 说明 |
|------|------|
| Intent | 存在 **`WAREHOUSE_STOCK_OVERVIEW`**（`AiResolvedQueryIntent`）。 |
| Path | 存在 **`warehouse_stock_overview_path`**（`PATH_WAREHOUSE_STOCK`）。 |
| 无独立 inventory intent | **不存在** `INVENTORY_OVERVIEW` intent 或独立的 `inventory_*_path`；库存专线在服务端统一落在仓线命名上。 |
| 真实库存类工具 | **仅** **`warehouse_stock_overview`**（`WarehouseStockOverviewTool`）。**Historical removed（D-CLEAN-STOCK-QUERY-P2）**：**`stock_query`** / **`StockQueryTool`** 已删；语义 wire **`"STOCK_QUERY"`** 仍映射到本 Tool。 |
| Payload | `warehouse_stock_overview` 返回体中含 **`warehouseOverview`**，典型字段包括：`stockItemCount`、`stockBatchRowCount`、`totalStockAmount`、`totalStockWeight`、`inboundAmount`/`inboundWeight`、核销分型金额（如 `produceAmount`、`wasteAmount`、`lossAmount`、`returnAmount`、`stockReduceAmount` 等）、**`lowStockItems`**、**`overStockItems`**、**`inactiveStockItems` / `priorityStocktakeItems`**、`recommendations`；集团场景另有门店覆盖与缺失列表等。 |
| AnswerPlan | **不存在** `WarehouseStockAnswerPlan` / `InventoryAnswerPlan`；无与采购/出库同级的 attach Builder。 |
| 回答生成 | **`WarehouseAnswerPlan` + Composer Plan-first**（`StubAnswerComposerNode`）；无 Plan 固定 no-plan。**不**使用已删 `WarehouseDeterministicRenderer`。 |

**Planner 侧（事实摘要）**：当有效路径为 `warehouse_stock_overview_path` 且未被更高优先级分支抢占时，`BusinessDataPlannerNode` 走库存概览分支；在具备 `VIEW_STOCK` 时 `dataPlanTools` 通常为 **`["warehouse_stock_overview"]`**（`WarehouseStockIntentConvergence`）。  
**注意**：编排层 `orchestrationDecisionCandidate.selectedTools` 与上述计划可能不一致；以 **`effectivePathCode` + `dataPlanTools`** 为准解读主链。

**Lexicon 事实摘要**：存在 **`purchase_inventory_overstock_risk`**、出库与经营对比等 structured wire；**Phase 4B 起**，「Phase 4 落地设计」中的门店 / 仓库库存排行类 wire（`store_stock_amount_ranking`、`store_stock_item_count_ranking`、`warehouse_stock_amount_ranking`、`warehouse_stock_item_count_ranking`）已与 **`AiQuerySemanticLexicon`** 及主链对齐，验收见 **「Phase 4B 验收结果：库存排行闭环」**。D-6 其它建议 wire 见第 4 节表格。

---

## 3. 语义边界

### 3.1 `stock_reduce_query`（出库专线）

- **覆盖**：出库、核销、耗用、报损、退货出库、出品相关金额/次数/**商品出库排行**、多店**出库金额**对比（与现存 `goods_outbound_ranking`、`store_outbound_amount_ranking` 等对齐）。
- **不覆盖**：不等价于「仓库里现在还有多少货」「现货结余」。

### 3.2 `warehouse_stock_overview`（库存现量专线）

- **覆盖**：库存**现量**、仓库还有多少、现货、存量、结余；聚合视图内可带**简易**低/高库存与早入库仍有剩余等列表（工具内启发式，非 MRP）。
- **不覆盖**：不等于「区间内谁出库最多」排行问法的主答（除非用户明确改问出口出库）。

### 3.3 `purchase_inventory_overstock_risk`（采购 + 出库/库存 双域风险）

- **覆盖**：语义上绑定 **采购/进货** 与 **出库/耗用/积压/新鲜度** 等**对照或脱节**（例如买多了但没怎么用、采购多与出库少并存）。
- **不覆盖**：不能**单独**代表「纯看库存表就很高」且**完全无采购语境**时的唯一口径；那种场景应对齐 **纯库存侧** 表达（见下节建议 wire）。

### 3.4 `warehouse_stock_overstock_risk`（纯库存过高 / 积压 — **设计概念**）

- **设计意图**：仅用**库存快照与库存域规则**描述「积压/过高」（可与采购无关）。
- **当前状态**：作为**语义与 wire 设计**写入本文档；**未必已在 Lexicon/合并规则/工具中实现**。落地前不得与 `purchase_inventory_overstock_risk` 混用叙事。

---

## 4. 建议 canonical wire（设计稿，不要求一期全部实现）

下列 wire 名称为 **D-6 建议契约**，用于 **`semanticSlots.structuredIntentDetailWire` / `queryIntent.structuredIntentDetail`**、Planner 与 Composer 对齐。LLM 可输出 **`metric.rankingType`** 作 **debug/deprecated** 观测；**服务端主依据为 `semanticSlots` wire**（**D-1X-D3**）。实现可分期。

| Wire | 用途摘要 |
|------|-----------|
| `warehouse_stock_overview` | 与现工具/path 对齐；集团/单店库存聚合概览。 |
| `warehouse_stock_goods_query` | 单品/品名级库存穿透（与概览区分）。 |
| `warehouse_stock_low_risk` | 低库存/不足风险（启发式或未来接安全库存规则）。 |
| `warehouse_stock_replenishment_needed` | 「需要补货」清单语义锚点（仍受诚实降级约束）。 |
| `warehouse_stock_overstock_risk` | 纯库存侧「过高/积压」锚点。 |
| `warehouse_stock_inactive_risk` | 呆滞/长期有剩余/早入库仍有剩余等 **inactive** 类信号。 |
| `warehouse_stock_freshness_risk` | 临期/保鲜期（依赖批次与保质期数据后方可启用）。 |
| `store_stock_amount_ranking` | 多门店 **库存金额** 对比/排行（与 `business_store_status_compare` 经营对比区分）。**Phase 4 落地设计的唯一 canonical**；历史文档名 `store_inventory_amount_ranking` 作为别名归一到本 wire（见「Phase 4 落地设计」）。 |
| `store_stock_item_count_ranking` | 多门店 **库存 SKU 种数** 排行（与 Phase 4 payload `storeStockItemCountRanking` 对齐）。 |
| `warehouse_stock_amount_ranking` | **库房维**库存金额排行（数据模型稳定后启用；否则 Phase 4.2 / Phase 5，见「Phase 4 落地设计」）。 |
| `warehouse_stock_item_count_ranking` | **库房维**库存商品种数排行（同上）。 |

---

## 5. 能力矩阵

表中 **「当前是否具备」** 指截至 D-6 梳理时，**产品级可用性**（含工具+结构化+答复形态），不仅是「能否 LLM 编答案」。

### 5.1 库存现量 / 结余

| 维度 | 内容 |
|------|------|
| **老板问法** | 现在库存还有多少？仓库还有多少货？现货/结余？ |
| **期望 intent/path** | `WAREHOUSE_STOCK_OVERVIEW` / `warehouse_stock_overview_path` |
| **期望 structured wire** | `warehouse_stock_overview` |
| **需要 tool** | **`warehouse_stock_overview`**（唯一执行 Tool；Composer 只读 `warehouseOverview`） |
| **回答应包含字段** | `totalStockAmount`、`totalStockWeight`、`stockItemCount`、`stockBatchRowCount`、`summary`、时间/范围 banner |
| **当前是否具备** | **是**（主工具 + 确定性渲染可覆盖核心摘要）。 |
| **诚实降级规则** | 无实时盘点时明确为**账面**库存；数据缺失门店需在 `coveredStores`/`dataMissingStores` 或文案中体现。 |

### 5.2 单商品库存查询

| 维度 | 内容 |
|------|------|
| **老板问法** | 牛肉还剩多少？某某 SKU 库存？ |
| **期望 intent/path** | `WAREHOUSE_STOCK_OVERVIEW`（或未来细分为 goods 子意图，仍挂在仓线）。 |
| **期望 structured wire** | `warehouse_stock_goods_query`（建议） |
| **需要 tool** | 现状依赖 **`warehouse_stock_overview`** 是否在内部按品名过滤；若无专用过滤，需 **`warehouse_stock_goods_query` 工具或接口** 才算「具备」。 |
| **回答应包含字段** | 品名/规格、结余数量或金额、归属 scope、批次行数（若有） |
| **当前是否具备** | **部分/视实现而定**；需对照 `WarehouseStockOverviewTool` 是否稳定消费 `mentioned` 品名锚点。 |
| **诚实降级规则** | 无法唯一匹配品名时澄清或列候选；不挪用出库排行代替库存结余。 |

### 5.2b 商品账面库存金额低排行（WH-C / ACTIVE）

| 维度 | 内容 |
|------|------|
| **合同** | `warehouse.goods_amount_ranking_low` → wire `goods_stock_amount_ranking_low` |
| **真实口径** | 按商品聚合 **`gb_dgs_rest_subtotal` 剩余金额升序 Top10** |
| **不是** | 库存偏少、报警、缺货、临期、补货建议 |
| **老板推荐问法** | 应使用「哪些商品**账面库存金额较低**？」等带**金额**语义的话术；**禁止**推荐「库存偏少/快缺货」绑定本合同 |

### 5.3 库存不足 / 补货

| 维度 | 内容 |
|------|------|
| **老板问法** | 哪些商品库存不够？哪些需要补货？ |
| **期望 intent/path** | `WAREHOUSE_STOCK_OVERVIEW` + wire：`warehouse_stock_low_risk` / `warehouse_stock_replenishment_needed`（建议）。 |
| **期望 structured wire** | `warehouse_stock_low_risk`、`warehouse_stock_replenishment_needed` |
| **需要 tool** | `warehouse_stock_overview`（当前有 `lowStockItems` 列表）；未来可强化规则或独立 tool。 |
| **回答应包含字段** | 低库存清单、阈值说明、scope；**不应**编造补货量。 |
| **当前是否具备** | **部分**：有 **启发式 `lowStockItems`**；无安全库存则无「应订多少」。 |
| **诚实降级规则** | 无安全库存/再订货点时只给**风险提示与清单**，不给出准确补货量；不将问句误路由到 **`stock_reduce_query`**。 |

### 5.4 库存过高 / 积压

| 维度 | 内容 |
|------|------|
| **老板问法** | 哪些商品库存太多？可能积压？库存压力大？ |
| **期望 intent/path** | 纯库存：`WAREHOUSE_STOCK_OVERVIEW` + **`warehouse_stock_overstock_risk`**（建议）；若**明确采购+消耗脱节**：`BUSINESS_DIAGNOSIS` + `purchase_inventory_overstock_risk`。 |
| **期望 structured wire** | `warehouse_stock_overstock_risk` vs `purchase_inventory_overstock_risk` 二选一语义清晰。 |
| **需要 tool** | 现状 `warehouse_stock_overview` 含 **`overStockItems`**；双域诊断需 `purchase_overview` + `stock_reduce_query` 等。 |
| **回答应包含字段** | 高库存清单、与阈值的相对关系、是否涉及采购侧证据（双域时）。 |
| **当前是否具备** | **部分**：有高库存列表；**双域**可走现有诊断 wire；**纯库存积压」专用 wire 未与采购 wire 分拆完毕前易混淆。 |
| **诚实降级规则** | 无消耗速度/预测时不说「必然滞销」；**不用采购+出库风险 narrative 覆盖无采购语境的纯库存问法**。 |

### 5.5 呆滞 / 周转慢

| 维度 | 内容 |
|------|------|
| **老板问法** | 哪些货很久没动？呆滞库存？ |
| **期望 intent/path** | `WAREHOUSE_STOCK_OVERVIEW` + `warehouse_stock_inactive_risk`（建议）。 |
| **期望 structured wire** | `warehouse_stock_inactive_risk` |
| **需要 tool** | `warehouse_stock_overview` 中 **`inactiveStockItems` / `priorityStocktakeItems`** 等。 |
| **回答应包含字段** | 候选呆滞 SKU、早入库仍有剩余、建议盘点动作。 |
| **当前是否具备** | **部分**（依赖工具内启发式与数据覆盖）。 |
| **诚实降级规则** | 「周转慢」若需出库速率，需额外时间序列；否则只陈述 **inactive 规则命中结果**。 |

### 5.6 临期 / 新鲜度

| 维度 | 内容 |
|------|------|
| **老板问法** | 哪些快过期？生鲜库存风险？ |
| **期望 intent/path** | 未来：`WAREHOUSE_STOCK_OVERVIEW` 或 `BUSINESS_DIAGNOSIS` + `warehouse_stock_freshness_risk` / 与采购 freshness 的边界在需求中再定。 |
| **期望 structured wire** | `warehouse_stock_freshness_risk`（建议）；与 `purchase_freshness_risk` 区分维度。 |
| **需要 tool** | 需 **批次 + 保质期** 数据源；当前主线工具若无则不具备。 |
| **回答应包含字段** | 批次、到期日、建议处理。 |
| **当前是否具备** | **否**（或仅限非结构化 LLM，**不得冒充确定结论**）。 |
| **诚实降级规则** | 无批次/保鲜期则直接说明**数据不支持**，不编造临期清单。 |

### 5.7 门店 / 仓库库存金额对比

| 维度 | 内容 |
|------|------|
| **老板问法** | A 店和 B 店哪个库存金额高？各店库存排名？ |
| **期望 intent/path** | `WAREHOUSE_STOCK_OVERVIEW`（多店）+ `store_stock_amount_ranking`（建议 canonical）。 |
| **期望 structured wire** | `store_stock_amount_ranking`（`store_inventory_amount_ranking` 仅作别名归一） |
| **需要 tool** | 按店汇总的库存金额排行接口；**不等于** `business_store_status_compare`（经营对比）。 |
| **回答应包含字段** | 门店、库存金额、scope、缺失数据说明；排行场景依赖 `storeStockAmountRanking` / `storeStockItemCountRanking` 等（见 Phase 4B 验收）。 |
| **当前是否具备** | **Phase 4B：门店维库存金额 / 种数（SKU）排行与 GRAPH_RUN 验收通过**（见「Phase 4B 验收结果」）；误路由到经营对比的风险已在设计（六）（七）与收口链路中持续收敛。 |
| **诚实降级规则** | 无排行数据时不得编造排序；**禁止**用 `coveredStores` / `overStockItems` / `lowStockItems` 推断门店金额或种数排序（见「Phase 4 落地设计」**（二）**与 Phase 4B 边界）。 |

---

## 6. Phase 规划

| Phase | 主题 | 目标 |
|-------|------|------|
| **Phase 1** | 库存现量入口收口 | 问法锚定 `WAREHOUSE_STOCK_OVERVIEW` + `warehouse_stock_overview`；编排示例与观测字段与 `dataPlanTools` 一致；杜绝「现量问法」误选 `stock_reduce_query`。 |
| **Phase 2** | 库存不足 / 补货语义契约 | 引入或固化 `warehouse_stock_low_risk`、`warehouse_stock_replenishment_needed` 与 prompt/Lexicon/Merge 边界；补货回答强制诚实降级。 |
| **Phase 3** | 库存过高 / 积压语义契约 | 拆分 **`warehouse_stock_overstock_risk`** 与 **`purchase_inventory_overstock_risk`** 的触发条件与文案；避免「积压」默认进双域采购诊断。 |
| **Phase 4** | 门店库存排行优先 | **交付目标**：门店侧库存金额 / 商品种数 / 压力类排行的语义、payload 与答复闭环。仓库维度若数据模型不稳定，**放到 Phase 4.2 或 Phase 5**（见「Phase 4 落地设计」）。 |
| **Phase 5** | `WarehouseStockAnswerPlan` 评审 | 在 wire 与工具稳定后评审是否引入专用 AnswerPlan（字段、Composer、Gate、回放）。 |
| **Phase 6** | 临期 / 批次 / 保鲜期风险 | 数据就绪后落地 `warehouse_stock_freshness_risk` 或与采购 freshness 的联动策略。 |

---

## Phase 1 执行链路评审：库存现量入口

以下为 D-6 Phase 1 只读代码梳理结论，用于评审库存现量问法在主链上的落点（不涉及本次改码）。

1. **`WAREHOUSE_STOCK_OVERVIEW` / `warehouse_stock_overview_path` 主链路已存在。**  
   `BusinessDataPlannerNode` 在**无**语义澄清（`needSemanticClarification=false`）时，会根据 `effectivePathCode=warehouse_stock_overview_path` 进入库存分支，并通过 `applyInventoryOverviewQuestionBranch` 生成 **`dataPlanTools=[warehouse_stock_overview]`**（具备 `VIEW_STOCK` 时）。

2. **若 `needSemanticClarification=true`，Planner 不会正常进入库存分支**，会走澄清 / 清空 / 重置类分支。因此 PROBE 若出现「看起来是库存 intent/path 但没有跑库存工具」，**应先检查 `needSemanticClarification`**。

3. **主执行链路以 `dataPlanTools` 为准，不以 `orchestrationSelectedTools` 为准。**  
   此前 PROBE 中 intent/path 为库存，但 `orchestrationSelectedTools=stock_reduce_query`，属于语义 LLM **编排候选字段漂移**；只要 **`effectivePathCode` 仍为 `warehouse_stock_overview_path`**，Planner **仍应**生成 `warehouse_stock_overview`。

4. **`BusinessToolExecutionNode` 会执行 `dataPlanTools` 中的 `warehouse_stock_overview`**；`toolResults` 的 key 为 **`warehouse_stock_overview`**。

5. **`warehouseOverview` 的读取路径以实际 Renderer/Composer 提取逻辑为准。**  
   `toolResults` 顶层是 `unwrapData` 后的 Map；消费侧通常经 **`data.warehouseOverview`** 或兼容路径提取。后续排查以 **`DeterministicRendererSupport` / `extractWarehouseOverviewPayload`** 等的实际读取为准。

6. **`warehouse_stock_overview` 当前能支持库存总览；单商品库存查询仍为弱支持。**  
   「牛肉库存还剩多少？」在 Phase 1 **仍走 `warehouse_stock_overview` 全量概览**；若要**稳定**单商品库存回答，需要 Phase 2 设计 **`warehouse_stock_goods_query`** 或工具 args 过滤等能力。

7. **Phase 1 最小收口点（文档/评审层面）：**  
   **优先**在 prompt/schema 中约束：当 **`WAREHOUSE_STOCK_OVERVIEW` + `warehouse_stock_overview_path`** 时，`orchestrationDecisionCandidate.selectedTools` 与主链一致，**必须使用 `warehouse_stock_overview`，禁止写 `stock_reduce_query`。**  
   **暂不改** Planner、Tool、Composer、AnswerPlan（本轮范围）。

---

## Phase 2 执行链路评审：库存不足 / 补货

以下为 D-6 Phase 2 只读梳理与 PROBE 结论，用于评审「库存不足 / 补货」问法在主链上的落点与契约缺口（不涉及本轮改码）。

1. **Phase 2 主路由已经收口。** PROBE 显示下列问法均已稳定进入 **`WAREHOUSE_STOCK_OVERVIEW`** / **`warehouse_stock_overview_path`** / **`warehouse_stock_overview`**（`selectedTools = warehouse_stock_overview`）：哪些商品库存不够？哪些商品需要补货？哪些商品快没货了？库存低于安全线的有哪些？AAA 店哪些商品需要补货？

2. **当前细分 `structuredIntentDetail` 尚未收口：**
   - Lexicon 缺 **`warehouse_stock_low_risk`**、**`warehouse_stock_replenishment_needed`** 正式常量；
   - **`stock_below_safety`** 尚无 canonical 映射；
   - **`query_semantic_parser.v2`**（v1 已于 D-CLEAN-V1 删除）尚未强制上述问法输出统一的 **`structuredIntentDetail`**。

3. **`warehouse_stock_overview` payload 中已有 `lowStockItems`**，可支撑**概览级**低库存提示。字段包括：**`goodsName`**；**`goodsId`**（可选）；**`storeName`**（可选）；**`restWeightTotal`**；**`restAmountTotal`**；**`note`**。

4. **`lowStockItems` 为启发式低库存列表**，**不等于**真实安全库存线或再订货点（ROP）。

5. **诚实降级（答复边界）：**
   - 可以说「库存偏低 / 建议关注补货」；
   - **不能**严格说「低于安全库存线」；
   - **不能**计算精确建议补货量；
   - **不能**预测还能用几天。

6. **Renderer / Composer：**
   - **`WarehouseAnswerPlan` + Composer** 宣读 **`lowStockItems`**（Tool 信封字段）；
   - **`COMPOSER_WAREHOUSE_V1`** 可引用 **`warehouseOverview.lowStockItems`**。

7. **Phase 2 最小收口建议（文档/评审层面）：**
   - **先**在 prompt/schema 统一 wire；
   - 建议 wire：**`warehouse_stock_low_risk`**、**`warehouse_stock_replenishment_needed`**；
   - 再由 **`AiQuerySemanticLexicon`** 增加 canonical 映射；
   - **暂不改** Tool / SQL / Planner / Composer；
   - **暂不新增** **`WarehouseStockAnswerPlan`**。

---

## Phase 3 执行链路评审：库存过高 / 积压

以下为 D-6 Phase 3 只读梳理与 PROBE 结论，用于评审「纯库存过高 / 积压」问法在主链上的落点与契约缺口（不涉及本轮改码）。

1. **Phase 3 PROBE：纯库存过高类问法的主路由已进入库存域。** 前 5 条均已稳定进入 **`WAREHOUSE_STOCK_OVERVIEW`**、**`warehouse_stock_overview_path`**、**`warehouse_stock_overview`**（`selectedTools = warehouse_stock_overview`）：
   - 哪些商品库存太多？
   - 哪些商品库存积压？
   - 哪些商品库存压力大？
   - 哪些商品存货太多？
   - 哪些商品库存金额太高？

2. **当前细分 `structuredIntentDetail` 未收口：**
   - 「库存太多」误落到 **`warehouse_stock_low_risk`**；
   - 「库存积压 / 库存压力大 / 存货太多 / 库存金额太高」误落到 **`purchase_inventory_overstock_risk`**；
   - **`AiQuerySemanticLexicon`** 当前**没有** **`warehouse_stock_overstock_risk`** 正式常量；
   - **`query_semantic_parser.v2`** 也**未**要求纯库存过高问法输出 **`warehouse_stock_overstock_risk`**。

3. **混淆根因（梳理结论）：**
   - **v1/v2** 将「可能积压 / 库存量太大 / 库存压力大」写入 **`purchase_inventory_overstock_risk`**，易诱导 LLM 在**无采购语境**的纯库存问法上也输出采购双域风险 wire；
   - **Phase 2** 已强化 **`warehouse_stock_low_risk`**，但**缺少**与之对称的「库存偏高 / 过高」独立 wire，使「库存太多」等反面问法可能被误归到低库存风险枚举。

4. **语义边界：**
   - **纯库存过高 / 账面偏多**：用户话术**仅**含库存、存货、金额高、积压、压力大等，**没有**采购、进货、买、出库少、没怎么用等**双域对照** → 应走 **`WAREHOUSE_STOCK_OVERVIEW`** / **`warehouse_stock_overview_path`**；**建议 wire**：**`warehouse_stock_overstock_risk`**；工具：**`warehouse_stock_overview`**；数据侧重：**`overStockItems`**。
   - **采购 vs 出库脱节（双域风险）**：话术含采购/进货/买 **与** 出库/核销/没用/长期没出库等**对照** → 应走 **`BUSINESS_DIAGNOSIS`** 或既定双域风险链路；wire 可为 **`purchase_stock_reduce_mismatch`** / **`purchase_slow_moving_risk`** / **`purchase_inventory_overstock_risk`**（须在双域语义成立时使用）。

5. **`warehouse_stock_overview` payload 已有 `overStockItems`**，可支撑**概览级**库存偏高提示。常见字段：**`goodsName`**；**`goodsId`**（可选）；**`restAmountTotal`**；**`restWeightTotal`**；**`storeName` / scope 标签**（可选）；**`note`**（类似「剩余金额相对较高，建议优先消耗避免积压」）。**`overStockItems` 为启发式高库存列表**，不等于严格滞销、真实积压、MRP 过量或周转天数判断。

6. **Renderer / Composer：**
   - **`WarehouseAnswerPlan` + Composer** 宣读 **`overStockItems`**（积压偏高条目，标题「库存偏高 / 建议优先消耗」）；
   - 因此 Phase 3 **暂不需要先改** Renderer / Composer。

7. **Phase 3 最小收口建议（文档/评审层面）：**
   - **第一小步**：prompt/schema 新增纯库存过高 wire：**`warehouse_stock_overstock_risk`**，覆盖：库存太多、库存积压、库存压力大、存货太多、库存金额太高等（**无采购双域语境**）；
   - **第二小步**：**`AiQuerySemanticLexicon`** 增加 **`STRUCTURED_WAREHOUSE_STOCK_OVERSTOCK_RISK`** 及 canonical 映射；
   - **同时收紧** **`purchase_inventory_overstock_risk`**：仅用于「采购/进货/买」与「出库/核销/没用/长期没出库」等**双域语境**；
   - **暂不改** Tool / SQL / Planner / Composer；
   - **暂不新增** **`WarehouseStockAnswerPlan`**；
   - **暂不优先改 MergeHelper**，除非 prompt + Lexicon 后仍出现「仓线路径 + `purchase_*` 双域 wire」共存。

---

## Phase 4 落地设计：门店 / 仓库库存金额对比

本节为 **D-6 Phase 4 设计收敛**（基于只读代码与 prompt 梳理），**不等于**当前已实现。**交付重心**：**门店库存排行优先**；**仓库**侧「按物理库房」的金额/种数排行若与部门树 / `visibleWarehouses` 模型未完全对齐，归入 **Phase 4.2 或 Phase 5**，实现前须**诚实降级**。

### （一）当前事实（截至文档修订时的代码与 Lexicon）

| 项 | 结论 |
|----|------|
| **Lexicon** | **没有** 以下 wire 的正式 `STRUCTURED_*` 注册与 **`canonicalStructuredIntentDetailWire`** 归一：`store_inventory_amount_ranking`、`store_stock_amount_ranking`、`warehouse_stock_amount_ranking`、`warehouse_stock_item_count_ranking`、`warehouse_inventory_item_count_ranking` 等。LLM 若自造 snake case，多为**原样透传**。 |
| **Prompt v2** | Phase 4 问法须在 v2 填 **`semanticSlots` + `structuredIntentDetailWire`**（及顶层 `intent` / `path`）；**`metric.rankingType`** 可 **debug** 输出，**非**服务端主 wire 来源。 |
| ~~**`AiQuerySemanticV2CompareStoreNormalizer`**~~ | **Historical removed（D-CLEAN-V1）**。多店对比改由 V2 **`semanticSlots.structuredIntentDetailWire`**（如 `business_store_status_compare`、`store_stock_amount_ranking`、`store_outbound_amount_ranking`）+ `mapLlmIntent`；**禁止**再引入 Java `COMPARE_STORE` 关键词 Normalizer。 |
| **`BusinessDataPlannerNode`** | 只要 **`effectivePathCode`** 为 **`warehouse_stock_overview_path`** 且进入库存分支，在具备 **`VIEW_STOCK`** 时 **`dataPlanTools`** 通常为 **`["warehouse_stock_overview"]`**（仅此 Tool）。若路径被Resolver 判成 **`business_overview_path`** 等，**不会**走该库存 Planner 分支。 |
| **`WarehouseStockOverviewTool`** | 集团聚合在内存中按门店根逐店计算各店 **`totalStockAmount`** 等，但 **循环结束后 `one.clear()`**，**不对外**输出**按门店/按仓库**的排行数组；顶层 **`stockItemCount` / `totalStockAmount`** 为**合并**结果。 |
| **`coveredStores` / `dataMissingStores`** | 仅 **`departmentId`/`name`、`hasData`**（及归一后的 `storeDepartmentId`/`storeName`），**没有**单店金额、种数。 |
| **`overStockItems` / `lowStockItems`** | **商品级**启发式列表（可带 `storeName`），**不是**门店/仓库汇总排行，**不能**用来回答「哪个门店库存金额最高」「哪个店种类最多」。 |

### （二）禁止用现有字段「凑答案」

在 **`warehouse_stock_overview`** payload **尚未提供** **`storeStockAmountRanking` / `storeStockItemCountRanking`**（及本设计中的 **`dataCoverage`**）等 **per-store** 结构化排行前：

- **不得**用 **`coveredStores`** 推出「谁家金额高」（无金额字段）。
- **不得**用 **`overStockItems` / `lowStockItems`** 的商品条数或局部金额**推断**门店总库存金额或门店间排序。
- 对「哪个门店库存金额最高？」「哪个门店库存商品种类最多？」「哪个门店库存压力最大？」等，须 **诚实降级**：说明当前返回体**仅有集团合并汇总 + 商品级告警列表**，**无**可信的门店（或仓库）排行字段，**不能**断言谁第一。

### （三）建议 canonical wire（设计稿）

以下命名供 **`AiQuerySemanticLexicon` canonical、`structuredIntentDetail` / slots wire、Merge 归一**对齐：

| Canonical wire | 含义 |
|----------------|------|
| **`store_stock_amount_ranking`** | 门店维度：**库存剩余金额**排行或双店对比（与高库存商品列表区分）。**建议作为门店库存金额的唯一 canonical**；文档/历史中的 **`store_inventory_amount_ranking`** **不再单独保留为第二套名字**，实现上作为 **alias → `store_stock_amount_ranking`**。 |
| **`store_stock_item_count_ranking`** | 门店维度：**仍有账面剩余的 SKU 种数**（或业务定义的 `stockItemCount`）排行。 |
| **`warehouse_stock_amount_ranking`** | **物理库房/仓维度**：库存剩余金额排行（仅当系统能稳定解析「仓」与部门/门店关系时启用）。 |
| **`warehouse_stock_item_count_ranking`** | **物理库房维度**：库存商品种数排行（同上）。 |

**说明**：不推荐同时维护 **`store_inventory_amount_ranking`** 与 **`store_stock_amount_ranking`** 两个并列 canonical；**统一为 `store_stock_amount_ranking`**，另一名称仅作别名归一入口。

**「库存压力最大」**：可映射为 **同一 wire + 答复策略**（例如综合 `totalStockAmount`、启发式 `overStockItemCount`、可选周转代理字段），或在 Phase 4 后期拆 **独立 wire**；须在 AnswerPlan 中固定口径，避免与 **`warehouse_stock_overstock_risk`**（商品级）混淆。

### （四）`warehouse_stock_overview` payload 扩展（设计稿）

在 **`warehouseOverview`** 内 **新增**（字段名可按实现微调，语义保持）：

**1）`storeStockAmountRanking`**（按 **`totalStockAmount` 降序**，同序可再按 `storeName`）

```json
[
  {
    "rank": 1,
    "storeDepartmentId": 0,
    "storeName": "",
    "totalStockAmount": 0.0,
    "stockItemCount": 0,
    "stockBatchRowCount": 0,
    "lowStockItemCount": 0,
    "overStockItemCount": 0,
    "dataAvailable": true
  }
]
```

**2）`storeStockItemCountRanking`**  
与上表**同一元素结构**，排序键为 **`stockItemCount` 降序**（必要时并列 `totalStockAmount` 作次序键）。

**3）`warehouseStockAmountRanking` / `warehouseStockItemCountRanking`**  
若当前聚合循环仅保证 **门店根（`father_id=0`）** 维度、**不能**稳定产出「物理仓库」级一行一总额，则：

- 字段可 **占位不写** 或 **返回空数组**；
- 文档与答复统一标注 **Phase 4.2 或 Phase 5**；
- 用户问「哪个仓库…」时 **诚实降级**（例如：当前数据为门店/合并口径，不按独立库房排行）。

**4）`dataCoverage` / `limitations`（建议）**  
与排行配套的**可读元数据**，例如：参与汇总的门店数、`coveredStores`/`dataMissingStores` 与排行列表的**对齐说明**、缺数门店是否进入排行（`dataAvailable=false` 或单列入 `limitations`）。**目标**是让用户理解「谁有数据、谁被排除」，**不是**替代排行金额本身。

**现有字段保留**：`coveredStores`、`dataMissingStores`、`lowStockItems`、`overStockItems` 等**语义不变**；排行数组为**增量事实源**，供 AnswerPlan / Renderer 使用，避免再用覆盖列表「猜」金额。

### （五）推荐实现顺序

| 顺序 | 项 | 说明 |
|:----:|----|------|
| **A** | **Prompt + Lexicon** | 定义 Phase 4 问法 → **`WAREHOUSE_STOCK_OVERVIEW`** / **`warehouse_stock_overview_path`** / **`warehouse_stock_overview`**；注册 **`store_stock_amount_ranking`** 等 canonical 与别名归一。 |
| **B** | **V2 prompt + `semanticSlots`**（非 Java Normalizer） | 双店库存对比须在 LLM 输出 **`WAREHOUSE_STOCK_OVERVIEW`** + wire `store_stock_amount_ranking` / `store_stock_item_count_ranking`；勿回落已删 CompareStore Normalizer。 |
| **C** | **`WarehouseStockOverviewTool`** | 在集团（及需要的单店）路径下**填充**本节**（四）**排行数组与 **`dataCoverage`**；不在此步强行做未建模的仓库维。 |
| **D** | **Inventory / Warehouse `AnswerPlan`** | 新增或扩展现有 plan，承载排行行、口径、边界（**计划内算清排序与展示行**，Composer **不重算**）。 |
| **E** | **Composer Plan-first** | 宣读 `WarehouseAnswerPlan`；无 Plan 固定 no-plan。 |

### （六）验收问法（GRAPH_RUN / Harness）

| 问法 | 期望（实现 Phase 4 后） |
|------|-------------------------|
| AAA 和汀兰餐厅哪个库存金额高？ | **`WAREHOUSE_STOCK_OVERVIEW`**，双店体现在 scope + 排行或对比行；**非** `business_store_status_compare`。 |
| 哪个门店库存金额最高？ | 依赖 **`storeStockAmountRanking`**（或 plan 等价物），**无数据则降级**。 |
| 哪个门店库存商品种类最多？ | 依赖 **`storeStockItemCountRanking`**。 |
| 哪个门店库存压力最大？ | 依赖约定压力口径（plan 内定义）；无统一指标前须降级或单列启发式依据。 |
| 哪个仓库库存金额最高？ | 若**无**稳定库房维排行 → **诚实降级**，指向 Phase 4.2 / Phase 5。 |
| 哪个仓库库存商品种类最多？ | 同上。 |

### （七）交付范围声明

- **Phase 4 主交付目标**：**门店库存排行**（金额、种数、压力类问法在数据与 plan 允许范围内）。
- **仓库维度**（按仓的金额/种数 Top）：仅在 **部门/库房模型与聚合链路**评审通过后记为 **Phase 4.2**；否则并入 **Phase 5**，避免半稳定 payload 误导用户。

### Phase 4B 验收结果：库存排行闭环

**结论**：D-6 Phase 4B「门店 / 仓库库存金额对比 / 排行」已在 **GRAPH_RUN** 侧完成验收；以下为事实记录（以当时验收与主链为准）。

#### 1. 已通过链路

- 用户问题  
  → **`effectiveIntentCode` = `WAREHOUSE_STOCK_OVERVIEW`**  
  → **`effectivePathCode` = `warehouse_stock_overview_path`**  
  → **`structuredIntentDetailWire`** 可命中：
  - `store_stock_amount_ranking`
  - `store_stock_item_count_ranking`
  - `warehouse_stock_amount_ranking`
  - `warehouse_stock_item_count_ranking`
- **`dataPlanTools` = `["warehouse_stock_overview"]`**
- **`WarehouseStockOverviewTool`** 在 **`warehouseOverview`** 中输出：
  - `storeStockAmountRanking`
  - `storeStockItemCountRanking`
  - `warehouseStockRankingDegradedNote`
- **Composer** 按排行 wire 宣读 **`WarehouseAnswerPlan`** 排行字段（查询范围、统计时间、首句结论、Top3 等）；**不**恢复已删 Renderer fallback。
- **`StubAnswerComposerNode`** 在库存排行 wire 下 **deterministic takeover**，**不再**让 **`COMPOSER_WAREHOUSE_V1`** 覆盖排行答案。
- **`finalAnswerText`** 可直接回答排行问题（排行 path 上避免与「总览 + 收敛/边界头」重复啰嗦，以当期实现为准）。

#### 2. GRAPH_RUN 验收问法结果

下列问法均已跑通：

- AAA 和汀兰餐厅哪个库存金额高？
- 哪个门店库存金额最高？
- 哪个门店库存最多？
- 哪个门店库存商品种类最多？
- 哪个门店库存 SKU 数最多？
- 哪个门店库存压力最大？
- 哪些商品库存压力大？
- 哪个仓库库存金额最高？
- 哪个仓库库存商品种类最多？

#### 3. 验收结论

- **门店库存金额排行**：通过。
- **门店库存商品种类 / SKU 排行**：通过。
- **「库存最多」「库存压力最大」**在门店对比语境下按 **库存金额排行** 处理：通过。
- **「哪些商品库存压力大？」**仍走 **`warehouse_stock_overstock_risk`**，**不被**门店排行 takeover：通过。
- **仓库维问法**：当前**不伪造**仓库级排行；使用 **`warehouseStockRankingDegradedNote`** 明确降级，并展示 **门店维参考排行**：通过。

#### 4. 当前边界

- Phase 4B **只正式支持「门店维」排行**。
- **`warehouse_stock_amount_ranking` / `warehouse_stock_item_count_ranking`**：语义可识别 + **诚实降级**；**不代表**已有真实仓库级排行数据。
- **`coveredStores`**、**`lowStockItems`**、**`overStockItems`** **不能**作为门店（或仓库）排行依据；与「Phase 4 落地设计」**（二）** 一致。
- **真正仓库级排行**：**Phase 4.2** 或 **Phase 5**。

#### 5. 禁止事项（文档维护）

本节为 **文档验收记录**；**不要求**藉此轮文档修订去改 Java、Prompt、SQL、Tool、Composer、Renderer、Resolver、MergeHelper、Planner、test。后续能力演进以单独变更为准。

---

## Phase 4 执行链路评审（PROBE 历史快照）

以下为早期 PROBE 现象记录，**设计与收口顺序以本节之上「Phase 4 落地设计」为准**。

- 「AAA 和汀兰餐厅哪个库存金额高？」曾误走 **`BUSINESS_OVERVIEW`** / **`business_store_status_compare`**，编排侧曾出现 **`stock_reduce_query`** 漂移。
- 「哪个门店库存最多？」曾进入 **`WAREHOUSE_STOCK_OVERVIEW`** 但缺 **`structuredIntentDetail`**。
- 部分问法曾出现 LLM 自造 **`store_stock_amount_ranking`**、**`warehouse_stock_quantity_ranking`** 等未在 Lexicon 注册的字符串。

---

## 7. 诚实降级原则（全局）

1. **无安全库存 / 再订货点**：不能给出**准确补货量**或「订多少合适」的确定性结论。  
2. **无消耗速度 / 预测**：不能断言「几天后断货」或精确周转天数（除非定义简化公式并披露假设）。  
3. **无实时盘点**：默认库存为**账面库存**，需在答复中可被用户理解。  
4. **无批次与保鲜期**：不能输出「快过期」清单或临期排名。  
5. **无门店库存排行结构 / 无可用排行行**：不能断言**哪家门店库存金额最高**；须诚实降级（Phase 4B 已提供 `storeStockAmountRanking` 等字段时，以结构化数据为准，不得再用 `coveredStores` / 商品列表凑排序）。  
6. **不用出库数据代替库存现量**：排行与核销金额的回答不能替代「现在还剩多少」。  
7. **不用采购+出库双域风险代替纯库存积压**：无采购语境时，叙事应对齐 **库存侧** wire（`warehouse_stock_overstock_risk` 等），避免误用 `purchase_inventory_overstock_risk`。

---

## 文档维护

- **本文档仅描述设计与事实边界**；具体实现以代码与 prompt 变更为准，变更时应同步修订本矩阵「当前是否具备」列。  
- **关联**：D-5 出库专线设计、经营路由 D-2、`query_semantic_parser.v2` 库存与双域章节、`BusinessDataPlannerNode`、`WarehouseStockOverviewTool`。
