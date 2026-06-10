# Harness Java Boundary Rules：Java 不得猜业务语义

> **状态：Partial / Reference（非最高优先级规则）**
> 本文保留为历史整理和代码审查参考。当前最高优先级规则以 `.cursor/rules/harness-java-boundary.md`、`.cursor/rules/time-layer-inheritance.mdc`、`.cursor/rules/semantic-contract-exporter.mdc`、`docs/ai/semantic-inheritance-architecture.md`、`docs/ai/contract-entry-validation-p2-summary.md` 为准。
> 若本文与上述 Current Baseline 或当前运行代码冲突，**不得**按本文恢复旧逻辑；必须以 Current Baseline 为准并明确标出冲突。
>
> **Current 合同主权补充**：V2 在 `allowedContracts` 内选择 `selectedContractId`；Java 仅可在 V2 前做确定性实体存在性落地以缩小 `allowedContracts` 或澄清；V2 之后 Java 无权重新选择业务合同。Completion 成功后任何 support / repair / normalize / slot merge / scope / planner / tool / AnswerPlan / Composer 都不得修改 `selectedContractId`、canonical wire、`answerPlanType` 或 `selectedTools`。后置冲突只能澄清、失败或 known gap，不能通过再次 Completion / Validation 合法化 Java 后置切换合同。

## 1. 总原则

Java 不允许猜业务语义。

LLM + contract 负责判断用户想问什么。

Java 只能做：

- 合同校验
- 权限校验
- 范围校验
- Tool 入参构造
- 确定性查表
- AnswerPlan 举证
- Composer 输入准备
- 缺字段时澄清

Java 禁止做：

- `contains` 判断用户业务意图
- alias / 同义词兜底归一
- `semanticSlots → wire` 二次推导
- raw LLM `structuredIntentDetail` 直接进业务执行
- `metric.rankingType` 等旧字段抢主链
- fallback 自动猜 `intent/path/wire`
- 从中文句子截取业务实体，例如菜名、商品名、供货商名
- no-op / deprecated / legacy 壳长期保留

---

## 2. contract-locked 才能进入业务执行

所有 `AnswerPlanBuilder` / Tool Request Builder / Business Tool Executor 只能消费 contract-locked 后的确定性结果。

必须满足：

```java
SemanticContractCompletionEngine.isContractLockedParse(sem) == true
```

如果不是 contract-locked：

- 不允许从 raw `structuredIntentDetail`
- 不允许从 `currentTurnStructuredIntentDetailWire`
- 不允许从 `semanticSlots.structuredIntentDetailWire`
- 不允许从 `queryObject` / `operation` / `metric` 重新推 wire
- 应 early exit / need clarification / known gap

> 当前项目中的实践：`DishSalesAnswerPlanBuilder.resolveDishSalesWire()` 已严格遵循此规则 —— 非 contract-locked 直接返回 `raw=null, wire=null, rejectReason=non_contract_locked_parse`，不读取任何 raw LLM/slots 兜底字段。

---

## 3. Matrix 文件职责边界

Matrix 文件只能作为 **capability registry**（能力注册表）。

允许：

- 定义 ACTIVE / KNOWN_GAP matrix rows
- 提供 `wire → row` 查表
- 提供 `planType` / `knownGap` / `anchor` helper
- 提供 scope legacy helper（仅用于门店范围继承抑制等非语义判断）

禁止：

- 读取 `rawMessage` / `normalizedUserMessage` 推业务语义
- `contains` 判断用户意图
- 从 `semanticSlots` 反推 `structuredIntentDetailWire`
- 从中文句子截取实体
- `SUMMARY/OVERVIEW → ranking` 这类 Java 二次改语义
- `DETAIL + 关键词 → single_dish` 这类 Java 兜底

### 本轮清理经验（参考实现）

`DishSalesSemanticCapabilityMatrix` 已清理为纯 capability registry，以下旧模式视为禁止：

- ~~`inferMatrixWire`~~ — 已删除，禁止恢复
- ~~`utteranceRequests`~~ — 已删除，禁止恢复
- ~~`extractMentionedDishName` 从句子截实体~~ — 已标 `Scope legacy helper only`
- ~~`detectPriorRankingWireLeak`~~ — 已删除（no-op 壳）
- ~~`resolveMatrixRow` 内部传 `previousTurn` / `normalizedUserMessage`~~ — 已清理
- ~~`shouldSuppressStoreScopeInheritanceForTrend` 用 `contains("趋势")`~~ — 已标 `Scope legacy helper only`，后续迁到 Scope 层

保留的合法能力：

- `resolveMatrixRow(path, wire, sem, rq)` — 纯查表，不读 rawMessage
- `knownGapForResolvedRow(row)` — 返回已知 gap
- `planTypeEmitsDishSalesRankingResultAnchor(planType)` — anchor helper
- `isCrossDomainProfitStructuredWire(canon)` — 跨域过滤

---

## 4. AnswerPlanBuilder 职责边界

AnswerPlanBuilder 可以：

- 根据 contract-completed wire 查 Matrix Row
- 根据 Matrix Row 决定 `planType` / `metricType` / `sortKey`
- 从 Tool 结果中选择事实
- 排序、聚合、构造 AnswerPlan
- 生成 debug

AnswerPlanBuilder 禁止：

- 从 raw LLM wire 直接生成计划
- 从 `semanticSlots` raw wire 直接生成计划
- 自己根据 `queryObject` / `operation` / `metric` 推业务含义
- 自己根据中文关键词判断排行/单项详情/异常/趋势
- 为了让业务跑通而 fallback 到旧字段

**如果没有 contract-completed wire：宁可 AnswerPlan early exit，也不要偷偷兜底。**

### 本轮清理经验（参考实现）

`DishSalesAnswerPlanBuilder` 已清理完毕：

- ~~`structuredIntentDetailOrSlotsWireRaw()`~~ — 已删除（读取 raw LLM / currentTurn / raw slots 三个兜底源）
- ~~`prevStructuredWire()`~~ — 已删除（读取 `AiConversationTurnMemory`）
- ~~`detectPriorRankingWireLeak()` 调用~~ — 已摘除
- `resolveDishSalesWire()` — 重写为只从 contract-completed `sem.semanticSlots.structuredIntentDetailWire` 读取，且必须在 `isContractLockedParse` gate 之后
- `earlyReturnReason` — 精确反映真实拒绝原因（`non_contract_locked_parse` / `missing_contract_completed_wire` / `contract_wire_not_accepted_dish_sales_matrix`），不再统一写 `no_wire_from_structured_intent`

---

## 5. Prompt / Contract / Java 的分工

| 层 | 职责 | 禁止 |
|----|------|------|
| **Prompt** | 引导 LLM 在 `allowedContracts` 中选择 `selectedContractId`；输出合同要求的 `semanticSlots`；抽取实体如 `mentionedDishName`、`mentionedGoodsName`、`mentionedSupplierName` | 不做合同校验 |
| **Contract** | 约束 allowed contract；定义 `wire` / `queryObject` / `operation` / `metric` / `sourceFacet` / `answerPlanType`；定义 `requiresAnchor` / `anchorType` / `selectedTools` 等机器字段。**Current**：Exporter 不再写 `selectionHint` / examples | 不做具体查数 |
| **Java** | 校验 `selectedContractId` 是否存在；校验 slots 是否和合同一致；校验 anchor 是否完整；缺失则澄清，不猜 | 不猜业务语义 |

完整主链：

```
用户问题
→ LLM 语义解析
→ selectedContractId
→ ACTIVE contract entry
→ SemanticContractCompletionEngine
→ Tool 确定性查数
→ AnswerPlan 选择事实和口径
→ Composer 只负责表达
```

---

## 6. 看到这些关键词必须审计

以后代码中看到以下关键词，要主动判断是否是旧逻辑：

- `infer`
- `guess`
- `fallback`
- `legacy`
- `deprecated`
- `historical`
- `resolveStructuredIntentDetailWire`
- `inferMatrixWire`
- `reconcileSemanticSlots`
- `applyXXXFromSlots`
- `contains`
- `rawMessage`
- `normalizedUserMessage`
- `extractXXXFromQuestion`
- `metric.rankingType`
- `alias`
- `canonicalizeFromSlots`
- `no-op`

不是全部禁止，但必须确认：**它是在做确定性校验，还是在猜业务语义。**

如果是在猜业务语义：

1. 优先删除
2. 不能删就从主链摘掉
3. 暂留必须标 `LEGACY_ONLY`，并注释说明不能用于主链

---

## 7. 修改代码时的固定禁止项

以后 Cursor / WorkBuddy 改业务语义相关代码，必须遵守：

- 不允许 Java `contains` 推业务 `intent/path/wire`
- 不允许新增 alias
- 不允许 `slots → wire`
- 不允许 raw LLM wire 进 AnswerPlan
- 不允许保留两套新旧逻辑并行
- 不允许为了单个 case 通过恢复旧 fallback
- 不允许新增 no-op 壳方法
- 不允许用旧字段补 contract 没输出的内容
- 新功能替代旧功能后，旧逻辑必须删除或从主链摘掉

---

## 8. 验收方式

不要只看最终答案文本。必须按 6 阶段验收：

| 阶段 | 检查内容 |
|------|---------|
| **Phase 1：语义层** | `selectedContractId` / `matchedContractId` / `wire` / `queryObject` / `operation` / `metric` / entity 是否正确 |
| **Phase 2：Tool Request / SQL 入参层** | 时间、范围、门店、实体、metric 是否正确 |
| **Phase 3：Tool / SQL 数据层** | 真实数据是否正确 |
| **Phase 4：AnswerPlan 举证层** | AnswerPlan 是否选对事实、排行、口径 |
| **Phase 5：Composer 表达层** | 最终话术是否改事实、乱总结 |
| **Phase 6：前台真实 run 联调** | 页面展示、debug 字段、planSource 是否正确 |

---

## 9. 当前项目的强约束

- 这是新项目，没有真实用户，不需要为了兼容旧逻辑长期保留隐患
- 如果旧逻辑会误导 Cursor，宁可删掉
- 如果某个业务能力因为删除旧逻辑暂时跑不通，应暴露为 **known gap / clarification / early exit**，而不是恢复 Java 猜语义
- 每个 AnswerPlanBuilder 的 `earlyReturnReason` 必须准确反映真实原因，不允许统一兜底

---

## 10. 给 Cursor / WorkBuddy 的默认修改原则

如果你发现旧逻辑、deprecated、legacy fallback、no-op、raw slots 旁路，不要继续沿用。

1. 先判断它是否会影响主链语义
2. 如果会，优先删除或摘主链
3. 不要为了让单个 case 通过而加 `if/else`、`contains`、alias
4. 所有业务语义必须回到 **LLM + contract**
5. Java 只做确定性执行

当你不确定一个方法是做校验还是猜语义时，问自己一个问题：

> 这个方法的结果，能不能在 contract 里用 `wire` / `queryObject` / `operation` / `metric` 唯一确定？

- **能** → 合理，继续
- **不能** → 大概率是在猜语义，应删除或标记 LEGACY_ONLY

---

## 11. 单一主权与单一投影原则

修改 AnswerPlan / Card / Composer / Wire 链路时，**同一个业务决定只能有一个权威来源和一个决策入口**。下游只消费结果，不得重新判断、补挂、替换、修复或改变业务含义。

### 11.1 单一主权（SSOT）

| 决定 | 主权 |
|------|------|
| 语义能力 | `selectedContractId` → ACTIVE Contract |
| wire / AnswerPlanType / Tool / 执行模式 | Contract 派生（contract-locked） |
| 卡片类型 | 最终 `AnswerPlan.planType`（或该域独立 AnswerPlan 的 `planType`） |
| 时间 | Time Layer（见 `.cursor/rules/time-layer-inheritance.mdc`） |
| 组织 / 权限 | `AiResolvedQueryContext` scope / dataScope |

禁止 `contractId`、`wire`、`executionIntent`、raw LLM、关键词、已有 `cardType` 等**并行推导同一结果**。

### 11.2 单一决策入口

同一映射只允许一个统一入口，例如 `AnswerPlan.planType → CardType`。

采购域参考：`PurchaseAnswerPlanCardSupport`（映射 SSOT）→ `PurchaseAnswerPlanCardWireService.attachCardsIfApplicable`（唯一挂载）。

Service、Composer、`refreshAllCardPayloads` 等下游**只消费**，不得再次 PlanType / intent / wire / contract 判断来决定卡片。

### 11.3 禁止后置修复

禁止：reconcile 补挂、错卡替换、refresh 重选 CardType、Composer 从全量 Tool/cards 筛选实体、fallback 旧逻辑、单 case 补丁。

上游非法状态应在权威边界 **fail closed**（澄清 / known gap / noDataReason）。

允许：协议归一化（字段名、`cardPayload` 镜像、title）——非业务重决策。

### 11.4 Composer 边界

**只负责**：消费 AnswerPlan + 已生成 cards，生成自然语言（可按 `planType` 选短引导，不补挂/替换卡）。

**不负责**：选合同、执行模式、卡片类型、改事实、补挂/替换卡、从 Tool 原始结果拼答案。

### 11.5 接入与清理

- 新能力前先查现有 Contract / Tool / Plan / Card / Projection，避免重复类型或 Service
- 统一入口落地后，删除重复判断、旧 WireService、reconcile、fallback、Composer 业务 skip
- Harness 只读探针可留，**不得**参与挂载决策

### 11.6 修改汇报

说明：根因、唯一主权入口、修改范围、删除的重复逻辑（不必跑测试，除非用户明确要求）。

Cursor 规则全文同步见 `.cursor/rules/harness-java-boundary.md` §12。
