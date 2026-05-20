# Classic business overview chain — removal record (P1A–P1F-F2)

**Status:** Fully removed from production Graph (2026-05). **BUILD SUCCESS** verified after P1F-F2 (`mvn -DskipTests compile`).

Do **not** reintroduce classic routing, Composer branches, DTO mount, or SSE `data.businessOverview`. The only live **经营概览** Graph path is **MULTI_AGENT 四域** (below).

---

## Phase removal map

| Phase | Removed surface |
|-------|-----------------|
| **P1A** | Composer isolated fallback on `state.getBusinessOverviewResult()` / classic structured card before AnswerPlan |
| **P1B** | Planner emit of **`DEFAULT_BUSINESS_OVERVIEW_TOOLS`** six-tool classic plan for non-MULTI `business_overview_path` |
| **P1C** | **`MasterBusinessAgent.tryOrchestrateClassicBusinessOverview`**, **`ClassicBusinessOverviewToolRunner`**, **`BusinessToolExecutionNode.runClassicBusinessOverviewToolChain`** |
| **P1D** | Outcome Review legacy aggregate; Composer **`BUSINESS_OVERVIEW_CLASSIC_V1`** branch; **`BusinessOverviewAgent`**; **`BusinessOverviewAgentNode`**; prompt id **`COMPOSER_BUSINESS_OVERVIEW_V1`** |
| **P1E** | Constants / state flags: **`DEFAULT_BUSINESS_OVERVIEW_TOOLS`**, **`PLAN_TYPE_BUSINESS_OVERVIEW_CLASSIC_V1`**, **`classicOverviewResult`**, **`classicBusinessOverviewMasterPath`**, **`BusinessAgentNames.BUSINESS_OVERVIEW`** |
| **P1F-F1** | Dead Composer/renderer helpers: **`DeterministicAnswerRenderer.renderBusinessOverviewFallback`**, **`shortFallbackBusiness`**, **`extractOverviewNumericHeadlinePreferAnswerPlan`**; **`AnswerComposerPayloadFactory.buildBusinessOverviewPayload`** (+ preview/cap helpers); classic-only methods on **`BusinessOverviewDeterministicSummaryBuilder`** (**`composeRevenueDeterministicFromAnswerPlan`** retained for REVENUE / MULTI) |
| **P1F-F2** | API/debug tail: **`AiRunState.businessOverviewResult`**; **`AiRunService` → `answer_delta.data.businessOverview` / `businessOverviewWarning`**; DTO **`AiBusinessOverviewResult`**; **`agent_finished.hasBusinessOverview`**; **`StubAnswerComposerNodeTest`** classic fallback cases |

---

## Deleted source files (classic Graph chain)

| File (representative path) | Phase |
|----------------------------|-------|
| `src/main/java/.../agent/business/BusinessOverviewAgent.java` | P1D |
| `src/main/java/.../graph/business/BusinessOverviewAgentNode.java` | P1D |
| `src/main/java/.../agent/business/ClassicBusinessOverviewToolRunner.java` (or equivalent) | P1C |
| `src/main/java/.../dto/business/AiBusinessOverviewResult.java` | P1F-F2 |
| `GroupManagerBusinessOverviewAnswerFormatRegressionTest.java` | P1D |
| `BusinessOverviewAgentGroupScopeSmokeTest.java` | P1D |
| `src/main/java/.../tool/business/BusinessOverviewQueryTool.java` | **D-CLEAN-BOV-TOOL-DELETE** |
| `BusinessOverviewQueryToolGroupSnapshotIsolationTest.java` | **D-CLEAN-BOV-TOOL-DELETE** |

**Not classic overview (separate legacy track):** **`StockReduceDeterministicRenderer`** — see [stock-reduce-deterministic-renderer-removed.md](stock-reduce-deterministic-renderer-removed.md) (出库 `StockReduceAnswerPlan` Composer 收口，非 P1A–P1F).

---

## Deleted fields, constants, prompts, API & debug keys

### State / DTO / SSE

- **`AiRunState.businessOverviewResult`**
- **`AiBusinessOverviewResult`** (entire DTO)
- **`answer_delta.data.businessOverview`**
- **`answer_delta.data.businessOverviewWarning`**

### Constants / plan types / agents

- **`DEFAULT_BUSINESS_OVERVIEW_TOOLS`**
- **`PLAN_TYPE_BUSINESS_OVERVIEW_CLASSIC_V1`**
- **`BusinessAgentNames.BUSINESS_OVERVIEW`**
- **`classicOverviewResult`**, **`classicBusinessOverviewMasterPath`** (Master debug / state)

### Prompt / Composer ids

- **`COMPOSER_BUSINESS_OVERVIEW_V1`**
- Composer branch **`BUSINESS_OVERVIEW_CLASSIC_V1`**

### Composer / renderer methods (P1F-F1)

- **`renderBusinessOverviewFallback`**, **`buildBusinessOverviewPayload`**, classic-only **`BusinessOverviewDeterministicSummaryBuilder`** helpers

### Debug keys (classic only)

- **`agent_finished.hasBusinessOverview`**

---

## Active replacement — MULTI_AGENT 经营概览主链

**Intent / path**

```
effectiveIntentCode = BUSINESS_OVERVIEW
  → business_overview_path = true
  → Resolver: orchestrationTaskMode = MULTI_AGENT
     (or orchestrationMultiAgentRequired)
```

**Planner → execution**

```
BusinessDataPlannerNode
  → dataPlanTools = permission-filtered subset of
     BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS:
       revenue_query
       purchase_overview
       stock_reduce_query
       dish_profit_analysis
  → MasterBusinessAgent multi batch (four domain agents)
  → BusinessToolExecutionNode (domain tool runs)
```

**AnswerPlan → Composer → frontend**

```
MasterBusinessAgent.assembleBusinessOverviewAnswerPlan
  → AiRunState.businessOverviewAnswerPlan
     planType = BUSINESS_OVERVIEW_MULTI_AGENT_V1
  → StubAnswerComposerNode deterministic four-domain Markdown
  → AiRunState.finalAnswerText
  → SSE answer_delta.data.text   ← frontend source of truth
```

Parallel diagnose path may use **`PLAN_TYPE_BUSINESS_DIAGNOSIS_MULTI_AGENT_V1`** (same AnswerPlan shape; different planType / debug source).

**Not MULTI `business_overview_path`**

- When **`orchestrationTaskMode ≠ MULTI_AGENT`** and **`orchestrationMultiAgentRequired`** is false:
  - Planner emits **empty `dataPlanTools`**
  - Debug: **`businessOverviewClassicPlanSuppressed: true`**
  - **No silent fallback** to classic six-tool chain
  - Composer does **not** synthesize classic overview card

---

## Retained (not classic Graph mainline)

| Artifact | Why kept |
|----------|----------|
| **`BusinessOverviewAnswerPlan`** + **`PLAN_TYPE_BUSINESS_OVERVIEW_MULTI_AGENT_V1`** | Live MULTI Composer input |
| **`PLAN_TYPE_BUSINESS_DIAGNOSIS_MULTI_AGENT_V1`** | Diagnosis composite reuses four-domain merge |
| **`AiRunState.businessOverviewAnswerPlan`** | State mount for MULTI / diagnosis merge |
| **`businessOverviewAgentResults`**, **`businessOverviewEnvelopeSummary`** | Master / BTEN MULTI debug |
| **`businessOverviewWarningsSnapshot`** | `DiagnosisPlanBuilder` MULTI debug (from AnswerPlan warnings) |
| **`hasBusinessOverviewMultiAgentAnswerPlan`**, **`businessOverviewMultiAgent*`** harness / Composer debug | Active MULTI probes |
| **`businessOverviewPath`**, **`businessOverviewClassicPlanSuppressed`** | Routing flag + explicit non-MULTI suppression marker |
| **`BusinessOverviewDeterministicSummaryBuilder.composeRevenueDeterministicFromAnswerPlan`** | REVENUE + MULTI revenue section |
| **`AiOverviewStoreIssueItem`** | Group overview / dish profit / tool payloads (unrelated to deleted DTO) |
| **`AiBusinessToolIds.BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS`** | Canonical four-domain tool list |

---

## Doc keywords: allowed vs forbidden in new code

### Allowed in docs only (must label Historical / removed / P1x)

`BusinessOverviewAgent`, `BusinessOverviewAgentNode`, `DEFAULT_BUSINESS_OVERVIEW_TOOLS`, `AiBusinessOverviewResult`, `businessOverviewResult`, `data.businessOverview`, `COMPOSER_BUSINESS_OVERVIEW_V1`, `BUSINESS_OVERVIEW_CLASSIC_V1`, classic six-tool sequence, harness **negative** assertions (“must not contain old AiBusinessOverviewResult fallback”).

### Forbidden in new `src/main` / `src/test` production paths

Reintroducing any of:

- **`AiBusinessOverviewResult`**, **`getBusinessOverviewResult`**, **`setBusinessOverviewResult`**
- **`data.businessOverview`**, **`businessOverviewWarning`** serialization
- **`renderBusinessOverviewFallback`**, **`buildBusinessOverviewPayload`**
- **`DEFAULT_BUSINESS_OVERVIEW_TOOLS`**, **`PLAN_TYPE_BUSINESS_OVERVIEW_CLASSIC_V1`**
- **`BusinessOverviewAgent`**, **`BusinessOverviewAgentNode`**, **`BusinessAgentNames.BUSINESS_OVERVIEW`**
- **`tryOrchestrateClassicBusinessOverview`**, **`runClassicBusinessOverviewToolChain`**
- Silent classic fallback when MULTI is off (empty plan is the only non-MULTI behavior)

---

## Obsolete doc pointers

Older docs mentioning classic Planner six-tool plans, **`BusinessOverviewAgentNode`**, or **`COMPOSER_BUSINESS_OVERVIEW_V1`** describe **removed** behavior unless marked **Historical**. Canonical live integration: **`docs/API_INTEGRATION.md`**, **`docs/PERMISSION_MODEL.md` §7**, **`docs/ai/business-overview-diagnosis-domain-capability-matrix.md`**.

---

## Removed Tool (D-CLEAN-BOV-TOOL-DELETE)

| Removed | Notes |
|---------|--------|
| **`BusinessOverviewQueryTool`** (`business_overview_query`) | No Planner emit; **`AiBusinessToolIds.BUSINESS_OVERVIEW_QUERY`** removed；**`AiPermissionGuard`** 权限行已删 |
| **`GrossMarginCalculatorTool`** | **Historical removed（D-CLEAN-GROSS-MARGIN-P2B）**；毛利见 **`CostMarginDerivation`** + **`CostDiagnosisAgentNode`** |

现网 **`business_overview_path`** → **MULTI_AGENT** → **`revenue_query` + `purchase_overview` + `stock_reduce_query` + `dish_profit_analysis`**（见 **`BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS`**）。

---

## Next phase (outside this cleanup)

**Do not continue deleting 经营概览 MULTI assets.** Classic Graph + legacy overview Tool removal is complete.

Suggested follow-up: **audit the next duplicate / legacy routing chain** (e.g. cost vs purchase convergence, diagnosis composite vs single-domain paths) using the same phase pattern — audit → Graph detach → dead code → API tail → final doc.

---

## Related docs

- [API_INTEGRATION.md](../API_INTEGRATION.md) — MULTI active + `data.businessOverview` Historical removed
- [PERMISSION_MODEL.md](../PERMISSION_MODEL.md) — `business_overview_query` **Historical removed**
- [TODO_MULTI_AGENT.md](../TODO_MULTI_AGENT.md) — delivery checklist (Historical sections retained)
- [LEGACY_AI_ANSWER_ASSETS.md](../LEGACY_AI_ANSWER_ASSETS.md) — Tool vs Graph distinction
