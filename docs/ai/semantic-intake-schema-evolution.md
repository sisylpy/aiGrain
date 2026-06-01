# Semantic Intake Schema 演进 — `reason` 结构化 marker 技术债

> **状态**：已记录 · **短期允许** · **P3+ 实施**  
> **关联 Prompt**：`semantic.intake.v1`（`src/main/resources/ai-prompts/semantic/semantic_intake.v1.md`）  
> **边界原则**：Java 不得猜业务语义；结构化决策必须来自 LLM + contract + **显式 schema 字段**，而非解析自然语言或 debug 字符串。

---

## 1. 问题

当前 Semantic Intake v1 输出 schema 中，`reason` 字段承担了两类职责：

| 职责 | 示例 | 是否合适 |
|------|------|----------|
| **Debug / Harness 观测** | `dish_sales_quantity_short_phrase`、`low_confidence` | ✅ 长期保留 |
| **下游结构化决策 marker** | `_to_cost_ranking`、`dimension_switch_sales_to_cost_ranking`、`dish_actual_cost_ranking_high_explicit` | ⚠️ **过渡方案** |

裸维度切换（§38e–38g）与部分显式排行观测码暂存于 `reason` 中，Java 通过 **字符串 contains / prefix** 激活 `BareRankingDimensionSwitchPlan`、协议纠错、以及 `SemanticIntakeMultiDishRankingSupport` 的部分 guard。

这违反了 Intake 分层目标：**`reason` 应是人类可读的简短解释，不应是机器路由协议**。

---

## 2. 短期策略（当前生产，允许）

在 schema v2 落地前：

1. **`reason` 仅作为过渡 structured marker 容器** — 已有 wire token 约定（§38g）继续有效。
2. **Java 只读 `reason` 中的固定 wire token**（如 `_to_cost_ranking`），**禁止**读 `canonicalUserQuery` / `rawMessage` 推断业务。
3. **新增业务分支不得**再向 `reason` 叠加新的「可被 Java 解析」编码；若需结构化信号，先在本 doc 登记，排期 schema 字段。
4. Prompt 维护说明与 Harness replay 用例同步更新。

---

## 3. 目标 Schema（Intake v2 增量字段）

在现有顶层 JSON 上**新增**可选块（命名待定，以下为推荐）：

```json
{
  "questionMode": "SINGLE_QUESTION",
  "normalizationType": "REWRITE",
  "canonicalUserQuery": "汀兰餐厅本月成本最高的菜品有哪些",
  "isFollowUp": true,
  "usedPreviousContext": true,
  "primaryDomain": "DISH_PROFIT",
  "candidateDomains": ["DISH_PROFIT"],
  "routeType": "EXPLICIT",
  "confidence": 0.92,
  "needClarification": false,
  "clarificationQuestion": null,
  "reason": "用户在上轮销量排行后改问成本最高排行",
  "followUpIntent": {
    "kind": "RANKING_DIMENSION_SWITCH",
    "targetDomain": "DISH_PROFIT",
    "targetMetric": "ACTUAL_COST",
    "targetContractId": "dish_profit.ranking_high_actual_cost",
    "targetStructuredIntentDetailWire": "dish_actual_cost_ranking_high",
    "anchorPolicy": "IGNORE_PREVIOUS_ANCHOR"
  },
  "subQuestions": null
}
```

### 3.1 字段语义

| 字段 | 类型 | 说明 |
|------|------|------|
| `followUpIntent` | object \| null | 仅 `isFollowUp=true` 且存在结构化追问意图时填写；完整独立句为 `null` |
| `followUpIntent.kind` | enum | 如 `RANKING_DIMENSION_SWITCH`、`DISH_SWAP`、`TIME_ONLY`、`SCOPE_ONLY`、`NONE` |
| `followUpIntent.targetDomain` | string | 与 `SemanticIntakePrimaryDomain` 一致 |
| `followUpIntent.targetMetric` | string | 与 contract metric 对齐：`ACTUAL_COST` / `GROSS_MARGIN_RATE` / `SALES_COUNT` / `SALES_AMOUNT` 等 |
| `followUpIntent.targetContractId` | string \| null | 可选；Intake 若能确定则填 ACTIVE catalog id，否则 null 由 V2 在 allowed contracts 内选择 |
| `followUpIntent.targetStructuredIntentDetailWire` | string \| null | 与 lexicon wire 对齐，便于 Harness 断言 |
| `followUpIntent.anchorPolicy` | string | `IGNORE_PREVIOUS_ANCHOR` / `USE_PREVIOUS_ANCHOR`（仅显式指代或 requiresAnchor 单菜合同） |

`reason` 迁移后：**仅中文/英文自然语言短句**，供日志与 replay 面板阅读；**Java 不再 parse**。

### 3.2 与现有 wire token 对照

| 过渡 `reason` token | 目标 `followUpIntent` |
|---------------------|------------------------|
| `_to_cost_ranking` | `kind=RANKING_DIMENSION_SWITCH`, `targetMetric=ACTUAL_COST`, `targetDomain=DISH_PROFIT` |
| `_to_margin_ranking` | `targetMetric=GROSS_MARGIN_RATE`, `targetDomain=DISH_PROFIT` |
| `_to_sales_ranking` | `targetMetric=SALES_COUNT`, `targetDomain=DISH_SALES` |
| `_to_amount_ranking` | `targetMetric=SALES_AMOUNT`, `targetDomain=DISH_SALES` |
| `dish_actual_cost_ranking_high_explicit` | 完整显式句：`followUpIntent=null` 或 `kind=NONE`；`primaryDomain=DISH_PROFIT` 已足够，可选填 `targetContractId` |
| `named_dish_*` | `kind=DISH_SWAP` 或显式 `anchorPolicy=USE_PREVIOUS_ANCHOR` + 顶层/后续 V2 `mentionedDishName` |

---

## 4. Java 迁移清单（实施 schema v2 时）

### 4.1 读路径替换

| 当前读 `reason` | 目标读法 |
|-----------------|----------|
| `BareRankingDimensionSwitchSupport.hasDimensionSwitchReasonToken` | `followUpIntent.kind == RANKING_DIMENSION_SWITCH` + `targetMetric` |
| `BareRankingDimensionSwitchSupport.buildPlan` facet/domain/contract 推导 | `followUpIntent.targetDomain` / `targetContractId` / `targetMetric` |
| `LlmSemanticIntakeParser.collectDimensionSwitchReasonProtocolErrors` | 校验 `followUpIntent` 枚举与 `primaryDomain` 一致性 |
| `SemanticIntakeMultiDishRankingSupport.hasExplicitRankingReason` | `followUpIntent==null` + `primaryDomain` + canonical 结构化 reconcile（保留）或 `targetContractId` |

### 4.2 写路径 / 模型

- `LlmSemanticIntakeParsed` / `LlmSemanticIntakeJsonParser`：解析新字段
- `SemanticIntakeResult`：透传 `followUpIntent` 至 Resolver / Harness debug map
- `SemanticIntakeResult.toDebugMap()`：输出结构化 follow-up 块

### 4.3 删除项（迁移完成后）

- `reason` 上的 `_to_*_ranking` 协议校验与 contains 逻辑
- Prompt §38g「reason 必须含 wire token」硬规则（改为 `followUpIntent` 必填）
- `FACET_SOURCE_INTAKE_REASON_TOKEN` → `FACET_SOURCE_INTAKE_FOLLOW_UP_INTENT`

### 4.4 兼容期（建议）

1. **Phase A**：双写 — LLM 输出 `followUpIntent` + 保留 `reason` token；Java **优先**读 `followUpIntent`，fallback `reason` token。
2. **Phase B**：Prompt 仅要求 `followUpIntent`；`reason` 改为纯文本；Java 移除 fallback。
3. **Phase C**：Harness replay expected 断言迁移至 `followUpIntent` / `targetContractId`。

---

## 5. 验收标准

- [ ] 裸维度切换 replay（如「销量高 → 成本呢」）**不依赖** `reason.contains("_to_cost_ranking")` 即可激活 Plan。
- [ ] 完整显式排行（如「上个月的菜品成本排行」）**不依赖** `reason=dish_actual_cost_ranking_high_explicit` 字符串。
- [ ] `reason` 改为中文说明后，主链行为不变。
- [ ] Intake 协议纠错报错指向 **缺失/非法 `followUpIntent` 字段**，而非 reason token。
- [ ] AGENTS.md / harness-java-boundary 规则仍成立：Java 不猜 NL，只执行 schema + contract。

---

## 6. 相关代码索引

| 职责 | 类 |
|------|-----|
| Intake LLM 解析 + reason 协议校验 | `LlmSemanticIntakeParser` |
| 裸维度切换 Plan | `BareRankingDimensionSwitchSupport` |
| 显式多菜排行 reconcile / anchor suppress | `SemanticIntakeMultiDishRankingSupport` |
| rewrite anchor 注入边界 | `AiResolvedQueryContextResolver` |
| Intake Prompt | `semantic_intake.v1.md` |

---

## 7. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-06-01 | 首版：记录 `reason` marker 技术债与 Intake v2 `followUpIntent` 目标 schema |
