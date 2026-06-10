# AGENTS.md — AI Agent 入口规则

## Mandatory Harness Boundary Rules

**当前最高优先级文档入口（Current Baseline）**：

1. `.cursor/rules/harness-java-boundary.md`
2. `.cursor/rules/time-layer-inheritance.mdc`
3. `.cursor/rules/semantic-contract-exporter.mdc`
4. `docs/ai/semantic-inheritance-architecture.md`
5. `docs/ai/contract-entry-validation-p2-summary.md`
6. `docs/AI_MAINLINE_INDEX.md`

若其它 Markdown 与上述入口冲突，**不得自行恢复旧逻辑**；必须按当前代码事实与 Current Baseline 标明冲突后再修改。

修改以下模块前，**必须先遵守** `.cursor/rules/harness-java-boundary.md`（§6.2 Semantic Inheritance）及 `docs/ai/semantic-inheritance-architecture.md`：

- `ai/semantic/contract/*Exporter*` — **还必须遵守** `.cursor/rules/semantic-contract-exporter.mdc`（禁止在 Exporter 写中文 hint/问法；见 `docs/ai/semantic-contract-exporter-governance.md`）
- `ai/semantic/` — 语义解析与合并
- `ai/semantic/matrix/` — 能力矩阵
- `ai/semantic/contract/` — 合同
- `ai/graph/business/` — AnswerPlan / Tool Request
- `ai/graph/business/execution/` — Tool 执行
- `ai/composer/` — 表达层
- `ai/platform/*CardWireService` — 卡片投影挂载（须遵守单一主权与单一投影，见 harness 规则 §11/§12）

**核心原则：Java 不得猜业务语义。LLM + contract 负责判断用户意图，Java 只做确定性执行。**

**合同主权硬规则（Current）**：

- V2 只在单域 `allowedContracts` 内选择 `semanticSlots.selectedContractId`。
- Java 可以在 V2 前做确定性实体存在性落地，用于缩小 `allowedContracts` 或触发澄清；V2 之后 Java 没有重新选择业务合同的权力。
- `SemanticContractCompletionEngine.complete()` 成功后，任何 support / repair / normalize / slot merge / scope / planner 层都不得修改 `selectedContractId`、canonical wire、`answerPlanType` 或 `selectedTools`。
- 后置发现合同、实体或槽位冲突时，只能澄清、失败或 known gap；即使重新经过 Completion / Validation，也不能用来合法化 Java 后置切换合同。
- canonical wire、`answerPlanType`、`selectedTools`、execution path 必须统一来自同一条 ACTIVE contract entry。
- LLM wire、orchestration `selectedTools`、`reason` marker 只能作为 raw/debug 或过渡观测字段，不参与主链执行。
- Time、Scope、Business Contract 主权相互独立。

**Time Layer（系统级，与业务域无关）**：修改时间继承、reconcile、`effectiveTimeWindowSource` 前，必须先读 `.cursor/rules/time-layer-inheritance.mdc`。

完整规则以 `.cursor/rules/harness-java-boundary.md` 为准；`docs/ai/harness-java-boundary-rules.md` 仅为 Partial / Reference。多轮 Business Frame 继承见 `docs/ai/semantic-inheritance-architecture.md`。
