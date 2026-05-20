# Follow-up Frame Composer（语境帧 + 槽位合成）

> **Frame Composer** 在本阶段指：**从现有 Resolver 产物合成 `BusinessContextFrame` + `BusinessFollowUpSlot`**，再交给 `BusinessCapabilityRegistry`；**不**新增独立 LLM 调用，**不**替换 Composer 节点。

---

## 1. Frame（语境帧）

**BusinessContextFrame** 回答：「上一轮在采购语义下 **表现为哪种 AnswerPlan 等价形态**？」

- 主要来源：`AiConversationTurnMemory` 的 `lastPathCode`、`lastStructuredIntentDetail`、`lastPurchaseSourceType`，经与 `PurchaseAnswerPlanBuilder.resolvePlanType` **一致** 的推导得到 `framePlanType`。
- 携带 `previousResultAnchors` 的只读快照（供锚点类能力匹配）。

**不是**完整业务会话 state 镜像；不包含权限、不包含 SQL 参数。

---

## 2. Slot（追问槽）

**BusinessFollowUpSlot** 回答：「本轮用户在追问维度上 **想要什么明细**？」

- Phase 1：`slotDetailWanted` 取自 **语义信号优先**，其次为 **集中在单类** `PurchaseFollowUpSlotSignals` 内的规则（避免在 Resolver 各处继续堆零散的「单句 if」）。
- `followUp` 为 false 时，通常不产生下钻能力匹配。

---

## 3. Composer 边界

- **不**修改 `StubAnswerComposerNode` / 其它 Composer：Frame Composer **仅**影响解析层观测、可选 `queryIntent` 补全、以及 Tool 入参中已存在的键。
- 最终用户可见话术仍由现有 Composer 消费 `PurchaseAnswerPlan` 等。

---

## 4. Debug 契约（Harness）

解析完成后，在 `AiResolvedQueryContext.businessFollowUpCapabilityDebug` 中建议包含：

- `matchedCapabilityId`
- `followUpRegistryQueryMode`（与 `BusinessCapabilityMatch#getQueryMode` 对齐）
- `framePlanType`
- `framePurchaseSourceType`
- `slotDetailWanted`

与 `docs/ai/follow-up-action-protocol.md` 中的 `followUp*` 字段 **互补**：旧锚点链路照写；Registry 命中时两者应 **语义一致**，便于排查「协议双写」问题。

---

## 5. 演进

后续 Phase 可将 `BusinessDrilldownRequest` 下沉为唯一意图突变入口，并逐步让旧 `resolveFollowUp*` 委托 Registry；Phase 1 **不**做删除或大规模替换。
