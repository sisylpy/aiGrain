# D-BD-DRILLDOWN-MATRIX-CONTRACT-P1 — 经营诊断内下钻矩阵契约

> **契约交叉引用**：wire 登记七步见 [`semantic-output-schema.md`](../../src/main/resources/ai-prompts/semantic/semantic-output-schema.md)；八域成熟度见 [`phase1-semantic-mainline-acceptance-summary.md`](./phase1-semantic-mainline-acceptance-summary.md) §4；Composer/fallback 见 [`harness-composer-architecture.md`](./harness-composer-architecture.md) §2.7。

> **目的**：把经营诊断 **门店下钻 + 子域归因确认** 收敛为 Harness Engineering 矩阵行，约束 LLM wire / Matrix / `DiagnosisPlan` debug / Composer 宣读分工。  
> **范围（P1）**：仅诊断内下钻；**不接** `BusinessDiagnosisCompositeAnswerPlan` 主链；**不扩** Composer Plan-first 原则；**不改** SQL。  
> **相关**：[follow-up-drilldown-matrix.md](./follow-up-drilldown-matrix.md)（D-13.2 STORE 已封版）、[result-anchor-protocol.md](./result-anchor-protocol.md)。

---

## 1. P1 边界

| 在 P1 | 不在 P1（P2+） |
|-------|----------------|
| BD-A 集团经营综述 | H 菜品销量排行 → `DishSales` Matrix |
| BD-B 门店综合风险排序 + STORE anchor | I 低毛利菜排行 → `DishProfit` Matrix |
| BD-C/D 门店原因说明（继承锚 / 显式店名） | J 采购排行 → `Purchase` Matrix |
| BD-E/F/G 子域归因（仍读 `DiagnosisPlan.focusFindings`） | Composite `finalAnswerText` |
| BD-K 改进行动（`actionSuggestions`） | `DiagnosisDeterministicRenderer` 内重算比例/排序 |

**planType**：全程 **`OVERALL_BUSINESS_DIAGNOSIS`**；矩阵维度写入 **`DiagnosisPlan.debug`**，不新增 planType 枚举。

---

## 2. Matrix 行表（P1）

| rowId | 用户问法（示意） | operation | wire | diagnosisFacet | childDomain | anchor |
|-------|------------------|-----------|------|----------------|-------------|--------|
| **BD-A** | 这个月帮我做一下经营诊断（Harness P1 入口；避免与 overview「经营得怎么样」混线） | SUMMARY | `business_diagnosis_summary` | SUMMARY | — | NONE |
| **BD-B** | 哪个门店问题最大？ | RANKING | `store_priority_ranking` | STORE_PRIORITY | — | **EMIT_STORE** |
| **BD-C** | 为什么？ | EXPLAIN | `store_risk_reasons_drilldown` | STORE_RISK_REASONS | — | CONSUME_STORE（消费上轮 STORE anchor；ResolvedContext 填 follow-up） |
| **BD-D** | AAA 为什么不好？ | EXPLAIN | `store_risk_reasons_drilldown` | STORE_RISK_REASONS | — | CONSUME_STORE + **用户原文**显式店名（非 semantic inherit） |
| **BD-E** | 是采购问题吗？ | EXPLAIN | `store_domain_attribution_purchase` | PURCHASE | PURCHASE | CONSUME_STORE |
| **BD-F** | 是出库问题吗？ | EXPLAIN | `store_domain_attribution_stock_reduce` | STOCK_REDUCE | STOCK_REDUCE | CONSUME_STORE |
| **BD-G** | 是毛利问题吗？ | EXPLAIN | `store_domain_attribution_dish_profit` | DISH_PROFIT | DISH_PROFIT | CONSUME_STORE |
| **BD-K** | 那怎么改？ | ADVISE | `diagnosis_action_followup` | ACTION | — | CONSUME_STORE |

实现类：`BusinessDiagnosisDrilldownMatrix` / `BusinessDiagnosisDrilldownMatrixRow`。

---

## 3. Semantic wire（新增 / 复用）

| wire | 状态 |
|------|------|
| `business_diagnosis_summary` | 复用 |
| `store_priority_ranking` | 复用 |
| `store_risk_reasons_drilldown` | 复用 |
| `store_domain_attribution_purchase` | **P1 新增** |
| `store_domain_attribution_stock_reduce` | **P1 新增** |
| `store_domain_attribution_dish_profit` | **P1 新增** |
| `diagnosis_action_followup` | **P1 新增** |

---

## 4. DiagnosisPlan debug 字段

| debug 键 | 含义 |
|----------|------|
| `diagnosisDrilldownMatrixRowId` | BD-A … BD-K |
| `diagnosisQuestionType` | 与 facet / 历史常量对齐（如 `STORE_PRIORITY_RANKING`） |
| `diagnosisFacet` | SUMMARY / STORE_PRIORITY / … / ACTION |
| `diagnosisChildDomain` | BD-E/F/G：`PURCHASE` / `STOCK_REDUCE` / `DISH_PROFIT` |
| `diagnosisKnownGap` | 子域 Plan 缺失或无可引用 finding |
| `diagnosisTargetStoreName` | 当前轮门店目标（与 `diagnosisTopStoreName` 同步） |
| `diagnosisDomainAttributionLines` | 子域归因宣读行（Composer 只读） |

Harness 摘要镜像：`diagnosisDrilldownMatrixRowId`、`diagnosisFacet`、`diagnosisChildDomain`、`diagnosisKnownGap`、`diagnosisTargetStoreName`（见 `AiHarnessAnswerPlanSummaryAppender`）。

---

## 5. 主链与 Composer

```
semantic wire → BusinessDiagnosisDrilldownMatrix.resolveRow
→ BusinessDiagnosisAgentV1.enrich (debug + anchor + domain lines)
→ DiagnosisDeterministicRenderer (宣读 Plan/debug，无 toolResults / 无重算)
```

- **BD-B**：`resultAnchors` 含 `STORE`，`sourcePlanType=STORE_PRIORITY_RANKING`。
- **BD-C**：消费 `followUpTargetEntityName` / 上轮 STORE 锚；**不**再产新 STORE 锚列表。
- **BD-D**：`mentionedStoreName` 或句内店名 → `diagnosisTargetStoreName`。
- **BD-E/F/G**：子域 AnswerPlan 缺失时写 `diagnosisKnownGap`，**禁止**假成功。
- **BD-K**：宣读 `actionSuggestions`；不恢复 LLM+Tool fallback。

**Harness 兜底**：无 wire 时，`userMessageLooksLikeStorePriorityRanking` 仅作 **BD-B 文本 fallback**（`BusinessDiagnosisDrilldownMatrix.isStorePriorityHarnessTextFallback`），**不得**优先于已解析 wire。

---

## 6. Harness Case

| CaseId | 轮次 | 覆盖行 |
|--------|------|--------|
| `BUSINESS_DIAGNOSIS_DRILLDOWN_MATRIX_P1` | 8 | BD-A … BD-K（见 `AiHarnessBuiltinCases.messagesBusinessDiagnosisDrilldownMatrixP1`） |

---

## 7. knownGap 约定

| 代码 | 含义 |
|------|------|
| `DIAGNOSIS_CHILD_DOMAIN_PLAN_MISSING_PURCHASE` | 采购 AnswerPlan 未挂载 |
| `DIAGNOSIS_CHILD_DOMAIN_PLAN_MISSING_STOCK_REDUCE` | 出库 AnswerPlan 未挂载 |
| `DIAGNOSIS_CHILD_DOMAIN_PLAN_MISSING_DISH_PROFIT` | 菜品毛利 AnswerPlan 未挂载 |
| `DIAGNOSIS_NO_FINDING_FOR_CHILD_DOMAIN` | Plan 在但无匹配 finding |
