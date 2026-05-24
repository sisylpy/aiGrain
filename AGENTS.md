# AGENTS.md — AI Agent 入口规则

## Mandatory Harness Boundary Rules

修改以下模块前，**必须先遵守** `.cursor/rules/harness-java-boundary.mdc`：

- `ai/semantic/` — 语义解析与合并
- `ai/semantic/matrix/` — 能力矩阵
- `ai/semantic/contract/` — 合同
- `ai/graph/business/` — AnswerPlan / Tool Request
- `ai/graph/business/execution/` — Tool 执行
- `ai/composer/` — 表达层

**核心原则：Java 不得猜业务语义。LLM + contract 负责判断用户意图，Java 只做确定性执行。**

完整规则见 `docs/ai/harness-java-boundary-rules.md`。
