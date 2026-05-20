# Business Capability Registry（业务能力注册表）— Phase 1

> **Capability ≠ Domain**：业务能力描述「在什么上下文里能做什么下钻/承接」，与单个 Agent 或单条 SQL 无绑定。  
> **Permission / DataScope ≠ Capability**：权限与可见门店/分销户范围由 `AiPermissionGuard`、`AiResolvedDataScope` 等在 Tool 调用前解决；Registry **只**断言结构化路由与追问槽位是否允许某条能力成立，不做鉴权判决。

---

## 1. 概念

| 名称 | 含义 |
|------|------|
| **BusinessContextFrame** | 从上一轮 `AiConversationTurnMemory` + 解析结果抽象出的「静态语境」：如上一轮采购 `AnswerPlan` 等价类型、wire、`purchaseSourceType`、path 等。 |
| **BusinessFollowUpSlot** | 本轮追问槽：是否 follow-up、规范化话术、`slotDetailWanted`（如 `GOODS_DETAIL`、`GOODS_UNIT_PRICE`）、语义快照引用等。 |
| **BusinessCapability** | 一条可注册能力：**能力 ID** + 匹配谓词 + **目标采购计划类型**（Phase 1 仅采购）。 |
| **BusinessCapabilityRegistry** | 有序注册表：按优先级对 `(frame, slot)` 做 **首个匹配胜出**。 |
| **BusinessDrilldownRequest** | 命中能力后的结构化请求：**matchedCapabilityId**、**queryMode**、目标 `planType` 等（Phase 1 主要服务 Harness / Debug / 意图补全）。 |
| **BusinessDrilldownRequestAssembler** | 从 `AiResolvedQueryContext` 输入构建 frame/slot，调 Registry，可选改写 `queryIntent`（与旧 `resolveFollowUp*` **并存**，不删除旧方法）。 |

---

## 2. 前台路由与诊断的职责边界

- **BUSINESS_OVERVIEW / BUSINESS_DIAGNOSIS** 等「编排面」可以 **跨域调用** Purchase / Revenue / StockReduce / DishProfit 等 **能力**；Registry 描述的是 **能力**，不是「只能挂在 `purchase_overview_path` 上」。
- Phase 1 **实现范围**仅限 **采购路径**上的试点能力；其它域仅保留文档契约，不在代码中注册。

---

## 3. unsupported / clarification / permissionDenied

| 情况 | 归宿 |
|------|------|
| **unsupported** | `BusinessFollowUpUnsupportedReason` + Debug 中 `matchedCapabilityId=null`，可选 `unsupportedReasonCode`；**不**强行改写意图。 |
| **clarification** | 仍由 `needSemanticClarification` / `AiFollowUpResolver` 等现有链路处理；Registry **不参与**编造澄清话术。 |
| **permissionDenied** | `AiPermissionGuard` / `TOOL_PERMISSION_DENIED`；Registry **不**短路权限，也不在 Registry 内缓存许可结果。 |

---

## 4. Phase 1 已注册能力（采购）

见代码 `BusinessCapabilityRegistry.registerPhase1PurchaseCapabilities()`，能力 ID：

- `purchase.supplier_anchor.goods_detail`
- `purchase.goods_anchor.supplier_unit_price`
- `purchase.supplier_channel.goods_detail`
- `purchase.self_channel.goods_detail`

---

## 5. 与既有 D-13 协议的关系

- `followUpAction` / `followUpDetailWanted` 等字段仍见 `docs/ai/follow-up-action-protocol.md`。  
- Registry 命中后，Debug 层追加 **扁平键**：`matchedCapabilityId`、`followUpRegistryQueryMode`、`framePlanType`、`slotDetailWanted` 等（见 `AiHarnessResolvedContextSummarizer`）。
- **阶段 1A 收口（采购 V2 / follow-up / Registry 已通过链路与遗留项）**：[`purchase-v2-semantic-followup-phase1-summary.md`](./purchase-v2-semantic-followup-phase1-summary.md)。
- **阶段 1B（经营类 / 经营概览 / 经营诊断）语义层最小矩阵**：仅 Harness **`RESOLVED_CONTEXT_ONLY`** 验收说明——[`business-phase1b-semantic-harness-matrix.md`](./business-phase1b-semantic-harness-matrix.md)（**不**要求经营类 `matchedCapabilityId`）；内置回放 **`caseId`** **`BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT`**（见 `AiHarnessBuiltinCases`）。
- **阶段 1C（出库 / 核销语义层）最小矩阵**：[`stock-reduce-phase1c-semantic-harness-matrix.md`](./stock-reduce-phase1c-semantic-harness-matrix.md)；内置回放 **`caseId`** **`STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT`**。
