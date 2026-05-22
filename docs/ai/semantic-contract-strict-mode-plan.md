# Semantic Contract Strict Mode Plan

> P2.6 前置设计：**strict 仍未开启**；主链 `SemanticContractValidator` 保持 **observe-only**。  
> 本文描述 P3/P4 切换 strict 时的边界、拦截条件与澄清策略。

---

## 1. 何时启用 strict

| 阶段 | 开关 | 行为 |
|------|------|------|
| P2 / P2.5 / P2.6（当前） | `semantic.contract.strict.enabled=false`（默认） | Validator **observe-only**；违例只写 Harness debug，不阻断 adoption |
| **P3（当前）** | 同上 + `SemanticContractStrictDecision` 已接入 Resolver / Harness | observe 写 `semanticContractStrictDecision`；`enabled=true` 时 enforce clarification |
| P3 试点 | 域级 flag 或配置 `semantic.contract.strict.domains=PURCHASE` | 仅试点域 enforce；其它域仍 observe（**未实现**） |
| P4 全域 | `semantic.contract.strict.enabled=true` + P4 blocker 清理完成 | Router + ContractSelector + Validator 同 snapshot enforce |

**启用前提（checklist）**

1. 目标域 `DomainContractSelector` 已注入 ACTIVE `allowedWires`（Runtime switched = yes）
2. 该域 capability exporter 已登记，且 `domainsMissingCapabilityContract` 为空
3. Harness 观测：`semanticContractValidation.modelContractViolation` 误报率可接受
4. v2 / DomainSemanticParser 能按 entry 输出完整槽位（无 systematic MISSING_REQUIRED_SLOT）
5. `SemanticContractClarificationQuestionFactory` 已覆盖全部 enforce 违例码

---

## 1.1 合同外 wire（现网行为）

**位置**：[`AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire`](../src/main/java/com/nongxinle/ai/conversation/AiQuerySemanticLexicon.java)（仅 case 归一）、[`SemanticContractValidator`](../src/main/java/com/nongxinle/ai/semantic/contract/SemanticContractValidator.java)

- 合同外 / 未登记 wire：**不** Java silent 映射；observe 记录 `UNSUPPORTED_WIRE`；strict=true → clarification。
- Matrix 合同帧补全：**仅** registered wire + 合同内槽位形状；**非** semantic wire alias 表。

---

## 1.2 Strict blocker Catalog（ACTIVE only）

**说明**：Catalog **只**登记当前仍阻塞 strict enforce 的 **ACTIVE** blocker；已删除历史项（Lexicon alias、rankingType infer、旧 payload 双写等）**不再**进入 catalog。合同外 wire 由 **`SemanticContractValidator`** / **`SemanticContractStrictDecision`** 输出违例（`UNSUPPORTED_WIRE` 等），**不**通过历史 alias / fallback 修正。

运行时 debug：`semanticContractStrictBlockers`（Harness）/ `SemanticContractStrictDecision.activeStrictBlockers`。

| id | 位置 | 说明 |
|----|------|------|
| `matrix.contract_frame_canonicalize` | Purchase/Warehouse `canonicalize*ContractFrame` | 合同内槽位补全（registered wire 下补 `detailWanted` 等） |
| `slot_merge.wire_reconcile` | `AiQuerySemanticSlotMerge.reconcile*SemanticSlots` | Merge 按 Matrix 形状覆盖 LLM wire / sourceFacet |
| `prompt.metric_ranking_type_field` | `query_semantic_parser.v2.md` / `semantic-output-schema.md` | schema 仍含 `metric.rankingType` **debug** 字段；主链 wire 仅 `semanticSlots` |
| `debug.replay_legacy_wire_fields` | Harness replay / `semanticMetricNormalized*` debug | Replay 仍输出 `metric.rankingType` 等观测字段 |

实现：[`SemanticContractStrictBlockerCatalog.java`](../src/main/java/com/nongxinle/ai/semantic/contract/SemanticContractStrictBlockerCatalog.java)

### P4-B — Purchase contract-driven execution（**DONE**）

| 项 | 现网 |
|----|------|
| PlanType / execution | `PurchaseSemanticExecutionIntentResolver` → `matchedContractId` + `semanticSlots` + `resultAnchors` → `answerPlanType` |
| Tool focus args | `PurchaseSemanticExecutionArgs` → `executionDetailWanted` / `executionIntentType` / `focusEntity*` |
| 锚读取 | `previousTurn.lastResultAnchors` + `rewriteInheritedAnchorName` |

类：`PurchaseSemanticExecutionIntent`、`PurchaseSemanticExecutionIntentResolver`、`PurchaseSemanticExecutionArgs`（`com.nongxinle.ai.graph.business.execution`）。

### P4-C — Lexicon alias/compat 主链摘除（**DONE**）

**位置**：[`AiQuerySemanticLexicon`](../src/main/java/com/nongxinle/ai/conversation/AiQuerySemanticLexicon.java)（薄桥接）、[`AiSemanticWireConstants`](../src/main/java/com/nongxinle/ai/conversation/AiSemanticWireConstants.java)

**已完成**：

- 删除 Lexicon 内 alias/compat switch（含 `warehouse_overview`→`warehouse_stock_overview`、`dish_sales_ranking`→`dish_sales_count_ranking_high`、`revenue_overview`→`revenue_overview_summary`、`store_risk_ranking`→`store_priority_ranking`、`type1`/`all`/rankingType 族等）。
- `canonicalStructuredIntentDetailWire` 仅 `normalizeWireCase`；不再调用 `RevenueSemanticCapabilityMatrix.canonicalWireSupplement` / `DishProfitSemanticCapabilityMatrix.canonicalWireSupplement`。
- 经营诊断 wire 重命名：`store_risk_reason_explanation`、`diagnosis_action_suggestion`（Matrix BD-C/D/K）。
- Matrix `inferWireFromMetric*Compat` **已删除**（P4-E）；wire 缺失时走 Validator `UNSUPPORTED_WIRE` / `MISSING_REQUIRED_SLOT`。

**strict 行为（alias 字面量）**

- observe：非 registered wire 原样进入 Validator → `UNSUPPORTED_WIRE`（若不在 allowedWires）。
- strict=true：澄清；不 silent 映射到 registered wire。

### P4-D — Payload / execution 命名收口（**DONE**，P4-E 收尾）

**原则**：execution 由 **contract + semanticSlots + resultAnchors** 驱动；Strict 前合同外 wire 不得被 Lexicon / Matrix / SlotMerge **静默改写**。

| 扫描类 | 现网主链 |
|--------|----------|
| Purchase / Supplier anchor payload | 仅 `purchaseGoodsAnchor*` / `purchaseSupplierAnchorExecution*` |
| Tool execution args | 仅 `executionDetailWanted` / `executionIntentType` |
| Matrix wire 推断 | 仅 `semanticSlots.structuredIntentDetailWire` + contract；**无** `metric.rankingType` 补 wire |
| BD Composer / Frame | `*ExplanationTurn` / `*SuggestionTurn`；`*AnchorExecutionFramePlan*` |
| Harness replay | `executionIntentType`、`executionDetailWanted`、`diagnosisReasonExplanationMatrixRowId` |

**P4-E 已完成**：删 Matrix rankingType infer、Purchase execution fallback、Historical payload / arg 常量。

---

## 2. Strict 只拦截的问题

Strict enforce **仅**拦截下列合同层问题；**不**替代 Matrix contract frame completion、SlotMerge 合法 reconcile、业务 PlanBuilder。

| # | 条件 | 违例码 | 说明 |
|---|------|--------|------|
| a | Router `UNKNOWN` / `AMBIGUOUS` 且无 `primaryDomain` | `ROUTE_UNKNOWN` / `ROUTE_AMBIGUOUS` | Step 1 无法选域 → 不注入 Parser 合同 |
| b | `selectedDomain` 无 capability exporter / ACTIVE 合同为空 | `NO_CAPABILITY_CONTRACT` | Catalog 缺口；不应 silent fallback |
| c | LLM 输出 wire ∉ ACTIVE `allowedWires` | `UNSUPPORTED_WIRE` | 禁止 Java 猜测、禁止 compat 归一合同外 wire |
| d | wire ∈ allowedWires，但槽位组合不匹配任何 ACTIVE entry | `UNSUPPORTED_SLOT_COMBO` | 使用 `SemanticContractSlotView` 统一视图匹配 |
| e | 匹配 entry 时缺必填槽（queryObject / operation / metric / sourceFacet / detailWanted） | `MISSING_REQUIRED_SLOT` | 不 silent 补槽 |
| f | entry `requiresAnchor=true` 但无 STORE 点名 / `USE_PREVIOUS_ANCHOR` 等证据 | `ANCHOR_CONTRACT_MISMATCH` | 不猜测锚对象 |

**不拦截（仍走现有业务链）**

- Matrix wire reconcile、Purchase canonicalize、SlotMerge 合法突变
- Tool / SQL / AnswerPlan / Composer 执行失败
- KNOWN_GAP / PLANNED entry 被选中（可选：`PLANNED_CAPABILITY_SELECTED`）

---

## 3. Strict 下如何返回 needClarification

```
Rewrite → Router → DomainContractSelector
  → [strict] 若 routeType ∈ {UNKNOWN, AMBIGUOUS} 且无 primaryDomain
       → needClarification=true, clarificationQuestion=factory(ROUTE_*)
  → v2 / DomainSemanticParser
  → SemanticContractValidator.enforce(parse, selection)
       → 若 violation
            → needClarification=true
            → clarificationQuestion=factory(violationCode, …)
            → **不**进入 trySemanticAdoption / PlanBuilder
```

**澄清问题来源**

- **必须**由 `SemanticContractClarificationQuestionFactory` 根据 `SemanticContractViolationCode` 生成
- 输入：`violationCode`、`selectedDomain`、`unsupportedWire`、`missingSlots`、`candidateDomains`
- **禁止**：Java 读用户原话猜意图、禁止新增 compat 映射、禁止把合同外 wire 自动归一后放行

---

## 4. Observe-only 与 Enforce 开关边界

| 组件 | observe-only（当前） | enforce（P3+） |
|------|---------------------|----------------|
| `SemanticDomainRouter` | 始终运行；AMBIGUOUS 仍注入 candidateDomains | strict 时 AMBIGUOUS/UNKNOWN 可前置拦截 |
| `DomainContractSelector` | 有 primaryDomain 才注入 allowedOutputContract | 无 ACTIVE 合同时 `NO_CAPABILITY_CONTRACT` |
| `SemanticContractValidator` | `observe()` 写 debug，**不**改 parse | `enforce()` 违例 → needClarification，阻断 adoption |
| `SemanticContractClarificationQuestionFactory` | 可单测 / Harness 预览 | enforce 路径唯一澄清文案来源 |
| Lexicon silent wire 映射 | **已删**（P4-C/E）；observe 下合同外 wire → `UNSUPPORTED_WIRE` | strict 时禁止任何 silent 归一 |

**配置建议（P3 未实现，仅预留）**

```properties
# 默认 false — 当前生产行为（P3 已接入决策层，默认仍 observe-only）
semantic.contract.strict.enabled=false

# P3 试点：仅 Purchase enforce（未实现）
# semantic.contract.strict.domains=PURCHASE

# P4 全域（须先完成 §1.2 blocker 清理）
# semantic.contract.strict.enabled=true
```

---

## 5. 违例码 → 澄清示例

| 违例码 | 示例澄清 |
|--------|----------|
| `UNSUPPORTED_WIRE` | 这个问题当前系统还没有登记为可查询能力，请确认你想查的是采购金额排行、采购总览，还是商品供货明细？ |
| `UNSUPPORTED_SLOT_COMBO` | 我识别到你想查采购，但查询对象、指标或口径不完整，请确认你想按商品、供货商还是门店查看？ |
| `MISSING_REQUIRED_SLOT` | 为了准确查询，还需要确认：detailWanted。请补充后再试。 |
| `ANCHOR_CONTRACT_MISMATCH` | 这个问题需要指定具体对象（例如某商品、某门店或上一轮结果），请补充你想查的是哪一个。 |
| `ROUTE_AMBIGUOUS` | 这个问题可能涉及多个业务域，请确认你想查采购、出库、库存、营业额还是菜品？ |
| `ROUTE_UNKNOWN` | 我还不能确定你想查哪类业务，请说明是想看采购、出库、库存、营业额、菜品还是经营诊断。 |
| `NO_CAPABILITY_CONTRACT` | 当前还没有为「菜品销量」登记可查询能力合同，请换一种问法或选择其他业务域。 |

实现类：[`SemanticContractClarificationQuestionFactory.java`](../src/main/java/com/nongxinle/ai/semantic/contract/SemanticContractClarificationQuestionFactory.java)

---

## 6. 与主设计文档关系

- 能力合同定义：[`semantic-allowed-output-contract-design.md`](./semantic-allowed-output-contract-design.md)
- P2.6 状态：7 域 routing + 7 域 capability exporter 齐全；strict **未**开启
- P3：Purchase（或配置域）strict 小流量 + DomainSemanticParser
- P4：全域 strict + Lexicon alias / rankingType infer 已删；`SemanticContractStrictBlockerCatalog` **只**登记当前仍阻塞 strict enforce 的 **ACTIVE** blocker，已删除历史项**不再**进入 catalog

---

## 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-22 | P2.6 初稿：strict 拦截边界、澄清工厂、observe/enforce 开关 |
| 2026-05-22 | P2.8 文档：登记 Lexicon Historical non-contract wire fallback 为 P3/P4 strict blocker |
| 2026-05-22 | **P3**：SemanticContractStrictDecision + strict.enabled 开关 + Clarification 接入 Resolver/Harness；StrictBlockerCatalog |
