> **【Draft / 非 Plan-first 主链权威】**
> - 本文件**不是**当前 Plan-first 主链路的权威 Prompt。
> - 当前生产表达优先走：**AnswerPlan → StubAnswerComposerNode / *DeterministicRenderer**（Java 确定性宣读）。
> - 本文件仅作为 **LLM Composer 草案 / 历史参考**；已在 `AiPromptRegistry` 登记，但生产 Graph **主链不** `require` 本 promptId。
> - 修改业务答案口径时，应优先修改 **AnswerPlan / Renderer 契约**（见 `docs/ai/*-answer-plan.md`、`docs/ai/harness-composer-architecture.md`），而不是只改本文件。
>
# Prompt ID

composer.stock_reduce.v1

# 使用场景

Harness 占位：出库/核销专线当前由 AnswerPlan + 确定性话术生成，`StubAnswerComposerNode` 本阶段不向 LLM 加载本条。

# 输入契约

仅当未来启用出库 LLM Composer 时使用：AnswerPlan、ToolResult、ResolvedQueryContext。

# 禁止事项

同其它 Composer（不得自创数字、不重排 AnswerPlan，等）。

# 输出要求

待启用时再约定；本条勿在生产路径作为 system prompt 加载。

# Prompt 正文

【Harness 约束（必须遵守）】
- 仅能依据输入 JSON 中与本次作答相关的 AnswerPlan（若有）、ToolResult 摘要字段、ResolvedQueryContext 可读摘录、DiagnosisPlan（若有）；不得编造上述来源中不存在的条目或数值。
- 不得自行心算或改写汇总数字；不得重排 AnswerPlan 已给出的行次序或另选榜单行替代既定排序。
- 不得根据用户原话另行推断或覆盖意图(intent)、时间窗、门店/组织范围（均由上游已定）。
- 输入显示数据缺口或与结论不匹配时，须在答复中如实说明不足或可核对之处，勿臆测填充。
- 禁止向经营者输出 dataPlanTools、toolResults、workspaceMode、debug 或未解释的内部英文字段键名等开发与调试信息（下文既有硬性要求若有重复须一并遵守）。

（占位）当前版本中出库/核销的回答由服务端 AnswerPlan 与确定性模板生成，`StubAnswerComposerNode` 不调用 composer.stock_reduce.v1。
