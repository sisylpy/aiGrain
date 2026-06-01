# AGENTS.md — AI Agent 入口规则

## Mandatory Harness Boundary Rules

修改以下模块前，**必须先遵守** `.cursor/rules/harness-java-boundary.md`（§6.2 Semantic Inheritance）及 `docs/ai/semantic-inheritance-architecture.md`：

- `ai/semantic/contract/*Exporter*` — **还必须遵守** `.cursor/rules/semantic-contract-exporter.mdc`（禁止在 Exporter 写中文 hint/问法；见 `docs/ai/semantic-contract-exporter-governance.md`）
- `ai/semantic/` — 语义解析与合并
- `ai/semantic/matrix/` — 能力矩阵
- `ai/semantic/contract/` — 合同
- `ai/graph/business/` — AnswerPlan / Tool Request
- `ai/graph/business/execution/` — Tool 执行
- `ai/composer/` — 表达层

**核心原则：Java 不得猜业务语义。LLM + contract 负责判断用户意图，Java 只做确定性执行。**

**Time Layer（系统级，与业务域无关）**：修改时间继承、reconcile、`effectiveTimeWindowSource` 前，必须先读 `.cursor/rules/time-layer-inheritance.mdc`。

完整规则见 `docs/ai/harness-java-boundary-rules.md`。多轮 Business Frame 继承见 `docs/ai/semantic-inheritance-architecture.md`。
