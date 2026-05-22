# 出库 / 核销链路：StockReduceAnswerPlan 与 Harness 契约

> **前提**：金额、分型（type1～type4）、Top 商品等**不重新发明口径**——以现有 **`StockReduceQueryTool`**、`GbDepartmentGoodsStockReduceService` / Mapper、**`GbConstants.StockReduceType`** 为准；**交付阶段不改 SQL、不重写 Tool**（后续仅缺陷修复时例外，须单独评审）。  
> **目标**：把 **`stock_reduce_query_path`**（`STOCK_REDUCE_QUERY`）从「Tool JSON + Composer / 旧 summary 即兴拼答」收敛为 **`StockReduceAnswerPlan` → Composer 优先宣读计划**，与 **`PurchaseAnswerPlan`** / **`DishProfitAnswerPlan`** 同一工程范式；**Debug / Replay** 可核对计划与上下文。  
> **阶段状态（2026-05-12）**：**前台验收已通过**；本链路 **阶段冻结**——**后续只做 bugfix / 小补丁**，**不做** 路由、计划构建或 Composer 层级的架构级大改（除非产品重新立项）。

全局分层说明见：`docs/ai/harness-composer-architecture.md`。采购对照见：`docs/ai/purchase-answer-plan.md`。

---

## 0. 前台验收结论（可冻结）

下列项由 **负责人前台 Debug / 联调** 勾选完成（仓库内 Agent **不要求** 代跑前台）。

| 项 | 状态 |
|----|------|
| **`StockReduceAnswerPlan` 已生成**并挂入 Run / 摘要 | ✅ |
| **前台 Debug `planSource`** 正确显示 **`stockReduceAnswerPlan`** | ✅ |
| **`StubAnswerComposerNode`** 在出库路径下 **优先读取 `StockReduceAnswerPlan`**（确定性宣读；旧 Tool summary 仅兜底） | ✅ |
| **`STOCK_REDUCE_OVERVIEW`** | ✅ |
| **`STOCK_REDUCE_PRODUCTION_OVERVIEW`** | ✅ |
| **`STOCK_REDUCE_OUTPUT_OVERVIEW`** | ✅ |
| **`STOCK_REDUCE_WASTE_OVERVIEW`** | ✅ |
| **`STOCK_REDUCE_LOSS_OVERVIEW`** | ✅ |
| **`STOCK_REDUCE_RETURN_OVERVIEW`** | ✅ |
| **`STOCK_REDUCE_GOODS_AMOUNT_RANKING`**（商品出库 **金额** 排行） | ✅ |
| **`STOCK_REDUCE_GOODS_COUNT_RANKING`**（商品出库 **次数** 排行，`sortKey=outboundTimes`） | ✅ |
| **type2 废弃 / type3 损耗** 问法与计划 **不混淆** | ✅ |
| **商品次数排行多轮 follow-up**：收窄门店（如 **AAA**）、恢复 **全部门店/集团**、收窄时间等 **继承** **`outboundTimes` / COUNT 计划类型**，不因短句无「次数」而退回金额排行 | ✅ |
| **Harness / Debug** 可核对 `stockReduceAnswerPlan*`、`focusRows`、`debug.sortKey` 等 | ✅ |

**后续约定**：出库 / 核销 **AnswerPlan + Composer** 收口交付完成后，**仅 bugfix**；扩需求走新立项。

**Renderer 清理（非 classic 经营概览）**：`StockReduceDeterministicRenderer` 与 `DeterministicAnswerRenderer.renderStockReduceToolFallback` 已移除；无计划兜底改为 `StubAnswerComposerNode.composeStockReduceNoPlanFallback`。索引见 `docs/AI_MAINLINE_INDEX.md`。

---

## 1. 从现状到目标架构

### 1.1 主数据与分型（真值）

| 资源 | 说明 |
|------|------|
| 表 `gb_department_goods_stock_reduce` | 出库 / 核销流水 |
| type1 | 生产耗用 / 正常制作菜品消耗（**PRODUCTION**） |
| type2 | **废弃** / 过保鲜期废弃（**WASTE**）— **不要**与 type3 混淆 |
| type3 | **损耗** / 丢失、破损、自然损耗（**LOSS**）— **不要**与 type2 混淆 |
| type4 | 退货（**RETURN**） |

**合计口径**：用户问「出库多少钱」「核销多少钱」时，**全口径合计**通常为 **type1 + type2 + type3 + type4**；若用户只问某一类，AnswerPlan 的 **`reduceType`** / **`planType`** 必须收窄到对应 type，**不得**把废弃说成损耗或反之。

**「出品」与「生产耗用」**：库表侧 **没有** 与 type1 并列的独立「出品」类型。旧版库房表述里「核销侧出品」与 **type1 金额**同源。若产品上「生产耗用」与「出品」需两行数字，须**另定**业务拆分规则；本版 AnswerPlan 可先以 **同一 type1 事实行** 配不同 **`scopeLabel` / `summary` 标签**区分表述，**禁止** Composer 侧凭空拆出第二套金额。

### 1.2 当前 Harness 锚点（已实现，文档阶段不改动）

| 环节 | 代码 / 配置 | 说明 |
|------|-------------|------|
| 意图 | `AiResolvedQueryIntent.STOCK_REDUCE_QUERY` | Debug 中 `intent` |
| 路径 | `AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY`（**`stock_reduce_query_path`**） | Planner / FollowUp 与成本主线分流 |
| 工具 | `AiBusinessToolIds.STOCK_REDUCE_QUERY`（`stock_reduce_query`） | **`StockReduceQueryTool`** |
| 规划 / 执行 | `BusinessDataPlannerNode`、`BusinessToolExecutionNode` | 含 `ARG_STOCK_REDUCE_HARNESS_PATH`、`ARG_GROUP_STOCK_REDUCE_AGGREGATION`、`ARG_STOCK_REDUCE_NARRATIVE_MODE` 等 |
| 结构化 wire | `AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY`（`stock_reduce_overview`） | Debug 可映射为 **`STOCK_REDUCE_OVERVIEW`** 等 |

### 1.3 目标架构（与采购 / 菜品毛利对齐）

```text
AiResolvedQueryContext（timeWindow、dataScope、queryIntent、structuredIntentDetail、orgScope.visibleStores …）
    → StockReduceQueryTool（参数均可追溯到 Context；SQL / 聚合口径不变）
    → ToolResult（现有 stock reduce 载荷保留作事实层）
    → StockReduceAnswerPlan（本轮 planType + reduceType + focusRows / secondaryRows + summary + debug）
    → Composer：仅朗读 AnswerPlan + 必要边界说明；禁止重算、重排、重选 Top、混淆 type2/type3
    → Debug / Replay：透出 stockReduceAnswerPlan* 与上下文字段
```

**Composer 不得**：自行汇总出库金额、自行按金额/次数重排商品、把 **type2** 口述成损耗、把 **type3** 口述成废弃、把 **`expandedSqlDepartmentIds`** 当作「门店列表」念给用户。

---

## 2. StockReduceAnswerPlan 建议结构

与 `PurchaseAnswerPlan` 一致思路：**稳定业务任务类型** + **服务端排好序的行** + **可序列化 debug**。

| 字段（建议） | 说明 |
|--------------|------|
| `planType` | 枚举字符串，见 §3（Java 侧可实现为 `TYPE_*` 常量） |
| `scopeLabel` | 本轮回答覆盖范围的人读标签（如集团合并 / 单店 / 可见门店数） |
| `timeLabel` | 本轮时间窗人读标签（与 `AiResolvedTimeWindow` 一致） |
| `reduceType` | 可选：`ALL` / `TYPE1` / `TYPE2` / `TYPE3` / `TYPE4` / `TYPE1_TO_4_BREAKDOWN` 等；用于收窄问答口径与验收 |
| `summary` | 极短摘要句（**可由计划构建器生成**；Composer **优先行数据**，summary 仅辅助） |
| `focusRows` | **核心事实行**（已排序、已选好「答哪一行」） |
| `secondaryRows` | 补充行（拆分项、次要高亮、排行 2～N 等） |
| `debug` | `LinkedHashMap<String, Object>`：排序键、候选数、引用 Tool 字段名、`structuredIntentDetail` 等 |

**行（row）对象**：建议与 Tool 已返回字段对齐（商品 ID、名称、金额、次数、type 分型标签等），避免 Composer 再查别名。

---

## 3. 建议支持的 planType

下列为**业务任务类型**，与 `purchaseAnswerPlan.type` / `dishProfitAnswerPlan.planType` 同一风格；若实现时已有 Debug 枚举对齐需求，可与 `AiQuerySemanticLexicon` / `AiHarnessResolvedContextSummarizer` 协调命名，**语义**以下表为准。

| planType | 含义（业务） | 典型问法 |
|----------|--------------|----------|
| `STOCK_REDUCE_OVERVIEW` | 出库 / 核销**总览**（全类型合计或默认全口径） | 「这个月出库多少钱」「核销多少钱」 |
| `STOCK_REDUCE_PRODUCTION_OVERVIEW` | **生产耗用**（type1） | 「生产耗用了多少」 |
| `STOCK_REDUCE_OUTPUT_OVERVIEW` | **出品**侧表述（与 type1 **同源数据**，标签区分；见 §1.1） | 「出品用了多少」 |
| `STOCK_REDUCE_WASTE_OVERVIEW` | **废弃**（type2） | 「废弃多少钱」 |
| `STOCK_REDUCE_LOSS_OVERVIEW` | **损耗**（type3） | 「损耗多少钱」「报损多少钱」 |
| `STOCK_REDUCE_RETURN_OVERVIEW` | **退货**（type4） | 「退货多少钱」 |
| `STOCK_REDUCE_TYPE_BREAKDOWN` | **四分型拆分**（type1～4 各多少） | 「生产/废弃/损耗/退货分别是多少」 |
| `STOCK_REDUCE_GOODS_AMOUNT_RANKING` | 商品 **金额** Top / 最高 | 「哪个商品出库金额最高」 |
| `STOCK_REDUCE_GOODS_COUNT_RANKING` | 商品 **次数** Top / 最多 | 「哪个商品出库次数最多」 |

**说明**：`STOCK_REDUCE_OUTPUT_OVERVIEW` 与 `STOCK_REDUCE_PRODUCTION_OVERVIEW` 在**数据层**均应对齐 **type1**；差异在 **计划类型与文案维度**，由 AnswerPlan 与 Composer 宣读分支区分，**不**在 Composer 内重新从 raw 行算 type1。

---

## 4. Debug / Replay 必须透出的字段

与采购 **`purchaseAnswerPlan*`** 并列，建议在 `AiHarnessResolvedContextSummarizer`（及 Replay  Summary）中透出：

| 字段 | 说明 |
|------|------|
| `stockReduceAnswerPlanPresent` | `boolean`，是否存在可宣读计划 |
| `stockReduceAnswerPlan` | 完整计划对象（JSON 对象，与 DTO 同源） |
| `stockReduceAnswerPlanType` | 等价于 `planType`（便于面板筛选） |
| `stockReduceAnswerPlanFocusRows` | 核心行列表（可单独拉出，避免深嵌找不到） |
| `stockReduceAnswerPlanSecondaryRows` | 次要行列表 |
| `stockReduceAnswerPlanDebug` | 构建诊断（排序键、过滤原因等） |

**前端兼容**：若已有通用 AnswerPlan 渲染（嵌套 `answer_delta.data`），至少保证 **`stockReduceAnswerPlan` + `stockReduceAnswerPlanPresent`** 与采购字段形态一致，便于同一套 Debug UI 扩展。

**排序键（可选）**：若排行类与采购一致需要明示，可增设 `stockReduceAnswerPlanSortKey` / `stockReduceAnswerPlanSortDirection`（与 `purchaseAnswerPlanSortKey` 对齐）。

---

## 5. 多轮验收问题清单（负责人前台执行）

以下问句序列已在 **前台验收** 中覆盖核心场景（含排行类门店收窄/恢复集团、次数排行继承）；新机器仍可复用本清单做回归。

下列每一轮建议在 **Run Debug / Replay** 中核对：

**用户问题序列**

1. 这个月出库多少钱？  
2. 上个月呢？  
3. 生产耗用了多少？  
4. 出品用了多少？  
5. 废弃多少钱？  
6. 损耗多少钱？  
7. 退货多少钱？  
8. 哪个商品出库金额最高？  
9. 哪个商品出库次数最多？  
10. AAA 呢？（或其它门店名收窄）  
11. 汀兰餐厅呢？  
12. 全部门店呢？（从收窄恢复集团 / 全量可见门店）

**每一轮检查项**

- `intent` 是否为 **`STOCK_REDUCE_QUERY`**
- `path` 是否为 **`stock_reduce_query_path`**
- `timeSource` / 时间窗是否正确
- `time` / `timeLabel` 是否与问句一致
- `scopeType`、`visibleStores` 是否正确
- `queryScopeKind`、`queryStoreIds` 是否正确
- **`expandedSqlDepartmentIds`** 是否**仅**作为 SQL 范围展开，**不**当作「门店列表」对用户展示
- `stockReduceAnswerPlanPresent` 是否为 **`true`**（实施阶段目标）
- `stockReduceAnswerPlan.type`（planType）是否与问法匹配
- `focusRows` 是否承载**核心结论行**（金额/分型/Top1）
- `secondaryRows` 是否为**合理补充**（拆分、Top2+、边界说明引用行）
- type1 / type2 / type3 / type4 **无混淆**（尤其 5/6 与「废弃」「损耗」问法）

---

## 6. 协作边界（冻结期仍适用）

在 **出库链路已阶段冻结** 的前提下，**除非**单独立项或明确解除本链路冻结：

- **不要**改采购链路（`purchase_overview_path`、`PurchaseAnswerPlan`、`PurchaseOverviewTool` 等）
- **不要**改 **菜品毛利**链路（`dish_profit_path`、`DishProfitAnswerPlan`、`DishProfitAgentNode` 等）
- **不要**改 **经营诊断** 主链路（`BusinessDiagnosisPlanBuilder` / 诊断 Harness），仅复用其已引用的 Tool 事实时保持边界
- **不要**在无单独评审的情况下改 **SQL** / Mapper **口径**；**不要** **重写** **`StockReduceQueryTool`**
- **不要让 Composer** 自己计算**出库合计**、**自行重排** Top、或**把 type2/type3 说反**（主路径已以 AnswerPlan 为准）
- **不要把 `expandedSqlDepartmentIds` 当门店列表**对用户念读或展示

**出库本链路**：缺陷修复与安全小补丁可合入；**避免**路由/计划/Composer 层级的大重构。

---

## 7. 实施三步（均已交付，供回顾）

下列三步已在代码中落地并于 **2026-05-12** **前台验收通过**；本条仅作架构回顾，**非**待办。

### 第一步：StockReduceAnswerPlan 生成与 Debug 透出 ✅

- **`StockReduceAnswerPlan`** DTO + **`StockReduceAnswerPlanBuilder`**；工具成功后挂载 **`AiRunState`**。
- **`AiHarnessResolvedContextSummarizer`** 透出 §4 **`stockReduceAnswerPlan*`**；**`stockReduceAnswerPlanPresent`** 与计划内容可核对。

### 第二步：planType 与 rows ✅

- 总览 / 单分型 / 商品 **金额 / 次数** 排行等映射到正确 **`planType`**；**type2/type3** 不混淆。
- 「出品」与「生产耗用」在计划类型上区分表述，数据层对齐 type1（见 §1.1）。

### 第三步：Composer 收口 ✅

- **`StubAnswerComposerNode`** 在出库路径下 **`stockReduceAnswerPlan` 可用时优先宣读计划**（与采购 AnswerPlan 策略一致）。

**冻结后**：仅 **bugfix**；新能力另开需求。

---

## 8. 主要落地文件（回顾）

| 层级 | 文件 |
|------|------|
| DTO | `StockReduceAnswerPlan.java` |
| 计划构建 | `StockReduceAnswerPlanBuilder.java` |
| 状态 | `AiRunState.java`（`stockReduceAnswerPlan`） |
| 挂载点 | `BusinessToolExecutionNode` 等（`stock_reduce_query` 成功后 attach） |
| Debug | `AiHarnessResolvedContextSummarizer.java` |
| Composer | `StubAnswerComposerNode.java`（优先 AnswerPlan） |
| 多轮继承 | `AiQuerySemanticLexicon`（出库次数排行 wire）、`AiFollowUpResolver`（承载问句模板）等 |
| 契约文档 | `docs/API_INTEGRATION.md`（`answer_delta.data.stockReduceAnswerPlan*`） |
| 单测 | `StockReduceAnswerPlanBuilderTest.java` 等 |

**冻结期**：`StockReduceQueryTool` **不重写**；缺陷修复须保持既有 SQL/口径契约。

---

## 9. 交叉引用

- **TODO 节拍**：`docs/TODO_MULTI_AGENT.md` → **`stock_reduce_query_path`（StockReduceAnswerPlan）** 小节  
- **业务诊断**：出库事实仍可能被 **`BusinessDiagnosisPlanBuilder`** 引用；AnswerPlan **不**替代诊断层，只服务 **直连出库问句** 的用户可见回答链路
