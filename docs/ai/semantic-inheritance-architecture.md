# Semantic Inheritance / Business Frame 架构

本文档固化多轮对话中 **previousTurn → 当前轮** 的语义继承规则，供 Cursor / Codex 及后续开发者在新窗口中直接查阅。**业务逻辑以代码为准；本文描述设计意图与 invariant。**

相关 Cursor 硬规则：`.cursor/rules/harness-java-boundary.md` §6.1–§6.2。

---

## 1. 问题背景

反复出现的故障模式：

- 上一轮业务 slots 在 Java 后处理阶段被 merge 进当前轮；
- 当前轮 LLM 已正确输出新业务 contract（如 `business_overview.summary`），却被上一轮 `dish_sales.count_ranking_high` 污染；
- time-only follow-up 只复制了 `selectedContractId` / `operation` / `metric`，漏掉 `sourceFacet`，触发 `selectedContractId_slot_mismatch`。

**根因：** 业务语义继承被做成**字段级拼装**（copy / coalesce），而非**完整 Business Frame 的原子继承或完全不继承**。

---

## 2. 三层继承模型

继承按层分离，顺序不可颠倒：

### 2.1 Context 继承

**可继承（安全上下文）：**

- `requestedScope`：scopeType、mentionedStoreName(s)、department、warehouse
- 权限 / 会话上下文（conversationId、runId 等）

**不可继承：** 任何业务 contract / wire / semanticSlots 业务字段。

**实现：** `SemanticSlotInheritanceApplier` 在 `INHERIT_CONTEXT_ONLY` 或 cross-family sovereign 时仅 merge scope。

### 2.2 Time 继承

| 场景 | 行为 |
|---|---|
| 当前轮有显式时间（V2 输出 `timeSource=CURRENT_MESSAGE_EXPLICIT` + startDate/endDate） | 使用当前轮时间 |
| 追问无显式新时间，结构化判定为继承追问 | `timeSource=INHERITED_PREVIOUS`，复制上一轮时间窗 |
| 首轮无显式时间 | 系统默认 `DEFAULT_MONTH_TO_DATE`（非 NL 解析） |

**禁止：** Java 读 `rawMessage` / `completedUserQuery` 识别「上个月、昨天」；禁止按 `timeType` 自动重算日期。

**实现：** `SemanticTimeContractCheck`；time-only follow-up 检测见 `StructuredTimeFollowUpSupport`（仅读 `timeAction` / `time.timeSource`）。

### 2.3 Business Frame 继承

**原则：要么整包继承，要么整包不继承。**

| Mode | 含义 |
|---|---|
| `INHERIT_NONE` | 不继承业务 frame（含**跨能力**显式实体追问、无 applicable policy） |
| `INHERIT_CONTEXT_ONLY` | 仅 Context；当前轮 sovereign 且跨 family 时使用 |
| `INHERIT_SAME_FAMILY_TIME_FOLLOWUP` | 同 contract family + structured time-only → 从 Catalog 派生完整 frame |
| `INHERIT_SAME_CAPABILITY_NAMED_ENTITY` | DISH_COST 三子能力内换菜名：Intake reason 与上一轮 contract 对齐 → 恢复上一轮 frame，仅替换菜名 |

**禁止：** 从 `previousTurn.lastSemanticSlots` 逐字段 copy；禁止 `coalesce(currentRaw, previousSlot)` 拼 Business Frame。

---

## 3. 主链组件

```
V2 Raw Parse
    → SemanticSlotInheritancePolicy.decide()
    → SemanticSlotInheritanceApplier.apply()
    → SemanticContractCompletionEngine.complete()
    → SemanticTimeContractCheck / mergeIntent
```

| 组件 | 职责 |
|---|---|
| `SemanticSlotInheritancePolicy` | 结构化门禁：sovereign / cross-family / time-only / explicit-entity |
| `SemanticSlotInheritanceApplier` | 执行 decision；time-only 时调用 Catalog 派生 |
| `CanonicalContractFrameSupport` | `previousContractId` → ACTIVE entry → 完整 canonical slots + orchestration |
| `SemanticContractCatalog` | CapabilityContractRegistry；ACTIVE contract lookup |
| `SemanticContractCompletionEngine` | contract-locked 补齐与 slot 校验 |
| `SemanticAdoptionPipeline` | 编排上述步骤；**不得**再 merge previous 业务 slots |

`AiQuerySemanticSlotMerge` **不是**继承主链：仅 wire 镜像、显式 dish anchor reconcile、turn memory 落库对齐。

---

## 4. current sovereign ACTIVE contract 优先

当当前轮 `selectedContractId` 命中 ACTIVE contract 且 `SemanticContractSovereigntySupport.hasSovereignActiveContract` 为 true 时：

- previousTurn **不得覆盖**当前轮任何业务语义字段；
- previousTurn 最多提供 Context（scope / store）。

**典型 sovereign 条件：**

- 跨 contract family（如 dish_sales → business_overview）；
- 当前轮非 time-only 弱选（全新业务问句）；
- 显式实体追问（当前轮带结构化菜名 + 相应 anchorPolicy）。

**示例（禁止）：**

| 上一轮 | 当前轮 | 错误 | 正确 |
|---|---|---|---|
| 销量高 | 昨天经营情况怎么样 | 继承 dish_sales | 保持 business_overview.summary |
| 今天买了什么 | 昨天经营情况怎么样 | 继承 purchase | 保持 business_overview.summary |

---

## 5. 同域 time-only follow-up：Catalog 派生完整 frame

### 5.1 触发条件（Policy 同时满足）

1. 无 current sovereign ACTIVE contract；
2. `StructuredTimeFollowUpSupport.isStructuredTimeOnlyFollowUp(current)`；
3. 当前轮与上一轮 **同一 contract family**（如均为 `dish_sales`、`menu`、`purchase`）；
4. 上一轮存在 stable business frame（contractId + wire）。

### 5.2 执行步骤（Applier）

1. 取 `previousContractId`（**唯一**来自 previousTurn 的业务输入）；
2. `SemanticContractCatalog.findActiveCapabilityContractById(previousContractId, domainHint)`；
3. `CanonicalContractFrameSupport.fromActiveContract(contract, anchorPolicy)`；
4. `applyBusinessFrameWhitelist(current, frame)` — 只替换 Business Frame 白名单字段；
5. 保留 current 的 `time`、`requestedScope`；清空 `mentionedDishName`（suppressPreviousDishAnchor）。

### 5.3 Business Frame 字段（须全部来自同一条 contract entry）

- `selectedContractId`
- `structuredIntentDetailWire` / `canonicalStructuredIntentDetailWire`
- `queryObject`, `operation`, `metric`, `sourceFacet`, `detailWanted`
- `answerPlanType`, `selectedTools`
- `intentCode`, `pathCode`（写入 parse / inheritance trace）
- `anchorPolicy`（time-only 通常为 `IGNORE_PREVIOUS_ANCHOR`）

### 5.4 Invariant

> 只要 `selectedContractId` 命中 ACTIVE contract，上述字段必须全部可追溯到**同一条** catalog entry。  
> **不允许：** contractId 来自 previous、sourceFacet 来自 current raw 的半旧半新状态。

---

## 6. resultAnchor 使用边界

`resultAnchors` / 排行 Top1 **不能**在 time-only follow-up 中自动变为 `mentionedDishName`。

| 场景 | mentionedDishName |
|---|---|
| 「上个月呢」「这个月呢」（仅改时间） | 必须 null |
| 「昨天经营怎么样」（新业务域） | 不得从 Top1 继承 |
| 「第一个菜呢」「核桃芽菜西芹呢」（显式实体追问） | 允许，走 `ExplicitEntityFollowUpSupport` |

**Turn memory 建议：** 区分 `lastMentionedDishName`（用户显式提到）与 `resultAnchors`（结果下钻），避免 V2 被 previousTurnSummary 误导。

---

## 7. 禁止补丁的位置

以下模块**禁止**新增 per-domain if/else、`contains`、alias、fallback：

- `AiQuerySemanticSlotMerge`
- `SemanticAdoptionPipeline` 内的 previousTurn 业务 merge
- `previousTurnSummary` / `lastSemanticSlots` merge
- `CurrentSemanticFrame` 域内 reconcile 业务语义

**正确修复路径：** Prompt / allowedContracts / Contract Catalog entry / Policy 结构化 predicate — 不是 Java 猜业务。

---

## 8. 旧 reconcile 方法废弃原因

以下方法已 `@Deprecated` 且为 **no-op**，主链迁移至 `SemanticSlotInheritancePolicy` + `SemanticSlotInheritanceApplier` + `CanonicalContractFrameSupport`：

- `reconcileDishSalesStructuredTimeFollowUpSlots`
- `reconcilePurchasePeriodGoodsStructuredTimeFollowUpSlots`
- `reconcileDishSalesExplicitDishFollowUpSlots`
- `reconcileDishCostStructuredFollowUpSlots`

**废弃原因：**

1. 每个业务域一套继承逻辑 → 补丁中心，互相污染；
2. 字段级 copy 无法保证 frame 完整性（如漏 `sourceFacet`）；
3. 与「selectedContractId 主权 + Catalog 派生」架构冲突。

**禁止恢复。** replay 稳定后可物理删除，避免新窗口误用。

---

## 9. 必跑回归 case

改继承逻辑后，至少跑：

**测试类：**

- `SemanticSlotInheritancePolicyTest`
- `SemanticSlotInheritanceApplierIntegrationTest`

**链路：** `Policy.decide → Applier.apply → SemanticContractCompletionEngine.complete`（无 violation）。

| # | 上一轮 | 当前轮 | 预期 |
|---|---|---|---|
| 1 | 菜单经营怎么样 | 上个月 | 完整 `menu.operation.overview.v1`，sourceFacet=OVERVIEW，无 mismatch |
| 2 | 销量高 | 上个月呢 | 完整 `dish_sales.count_ranking_high`，time=LAST_MONTH，dish=null |
| 3 | 今天买了什么 | 上个月呢 | 完整 `purchase.period_goods_list`，不降级 overview |
| 4 | 销量高 | 昨天经营情况怎么样 | `business_overview.summary`，不继承 dish_sales |
| 5 | 今天买了什么 | 昨天经营情况怎么样 | `business_overview.summary`，不继承 purchase |
| 6 | 销量高 | 核桃芽菜西芹呢 | 显式实体 → `dish_sales.single_dish`，不恢复 ranking |

---

## 10. 修改前检查清单

1. 问题属于哪一层？（语义 / Tool / AnswerPlan / Composer）
2. 是否 previousTurn 污染 current sovereign contract？
3. 是否跨 domain？是否仅 time-only follow-up？
4. 是否打算新增业务 if/else 或 contains？→ **停止，换 Policy/Catalog/Prompt**
5. Business Frame 是否从 Catalog 整包派生？
6. 上述 6 条回归是否通过？
