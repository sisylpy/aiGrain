# Result Anchor Protocol（结果锚点协议）

与 **D-13 Business Follow-up Action Protocol** 配套：把上一轮「结构化回答里可被追问的实体」落成 **`AiResultAnchor`**，供下一轮 Resolver / 语义输入继承对象与时间窗，而不靠 LLM 臆造 ID 或写 SQL。

---

## 1. 设计原则

| 原则 | 说明 |
|------|------|
| 域无关 | `entityType` 使用通用枚举（SUPPLIER / STORE / DISH / GOODS），不绑定采购表名 |
| 不重算 | Anchor 只从 **AnswerPlan 已填充的行 / Overview 切片** 拷贝，禁止为锚点单独跑查询改口径 |
| 可序列化 | 锚点需可进入 TurnMemory；当前实现见下文「持久化」 |
| 可摘要 | 提供给语义解析的 **`resultAnchorsSummary`** 为人类可读短文本，不含敏感或未授权 ID |

---

## 2. 数据结构（`AiResultAnchor`）

| 字段 | 类型 | 说明 |
|------|------|------|
| `entityType` | String | `SUPPLIER` · `STORE` · `DISH` · `GOODS`（常量见 `AiResultAnchor`） |
| `entityId` | String | 可选；业务侧 ID，可为 null |
| `entityName` | String | 展示名；**为空时当前采购排行路径不生成锚点** |
| `rank` | Integer | 排行位次；单锚点且无 rank 时可与「仅一条锚点」规则组合使用 |
| `sourcePlanType` | String | 产出锚点的计划类型，如 `PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING` |
| `metric` | String | 可选；指标语义，如金额汇总口径描述 |
| `amount` | String | 可选；展示用数值字符串 |
| `extraJson` | String | 可选；扩展 JSON |

---

## 3. 谁负责产出（AnswerPlan）

- **采购 · 供货商金额排行**：`PurchaseAnswerPlanBuilder` 在 `planType == PURCHASE_SUPPLIER_AMOUNT_RANKING` 时，从 **`focusRows` 首行**，否则 **`overview.topSuppliers` 首行** 提取 `supplierId` / `supplierName` / `amount` / `rank`，构造 `entityType=SUPPLIER` 的锚点列表。
- **Debug**：`PurchaseAnswerPlan.debug` 可携带 **`resultAnchorsCount`**（与业务 SQL 无关的计数观测）。
- 其他域（门店排行、菜品、商品）后续按同一契约扩展，不在此文档绑定具体列名。

---

## 4. TurnMemory 如何保存

- **Java 对象**：`AiConversationTurnMemory.lastResultAnchors`，在 `fromCompletedState(AiRunState)` 从 `state.getPurchaseAnswerPlan().getResultAnchors()` 写入。
- **持久化（本轮不设独立列）**：实体层将锚点 JSON 置于 **`gb_ai_ctm_tool_summary`（tool_summary）前缀 `nx_ctm_ra_json=`**，与 Harness 多店摘要前缀可共存；加载时解析还原。
- **若未来拆列**：可增加 `gb_ai_ctm_result_anchors_json TEXT`；协议字段仍以 `AiResultAnchor` 为准。

---

## 5. 语义 / Resolver 输入

- **`SemanticParserPreviousTurn.resultAnchorsSummary`**：由 `SemanticParserInputBuilder` 根据 `lastResultAnchors` 生成简短可读摘要，便于 LLM / 合并层识别「上一轮 Top 供货商是谁」。
- 详见 [`semantic-allowed-output-contract-design.md`](./semantic-allowed-output-contract-design.md)：`semanticSlots.detailWanted` + `anchorPolicy` + `SemanticExecutionIntent` 如何 jointly 触发路由。

---

## 6. Harness 摘要建议观测

由 `AiHarnessResolvedContextSummarizer` 等摊平输出时，建议关注：

- `previousTurnSummary.resultAnchorsCount`：上一轮记忆中带有的锚点条数。
- 顶层 `resultAnchorsCount`：与 `previousTurnSummary` 对齐的快捷字段（实现以代码为准）。
- `purchaseAnswerPlanResultAnchorsCount`：当前轮若已物化 `PurchaseAnswerPlan`，其 `resultAnchors` 条数。

---

## 7. Replay 验证要点

- **有锚点**：完成「供货商排行」轮次后，下一轮解析预览或 TurnMemory 还原中应能观察到 **非空锚点**（或等价摘要）；具体键名以 Harness 摘要为准。
- **无语义断裂**：下钻轮次不应仍在 wire 层表现为「仅排行」而计划类型仍为 `PURCHASE_SUPPLIER_AMOUNT_RANKING`（见 follow-up 文档中的采购下钻用例）。
