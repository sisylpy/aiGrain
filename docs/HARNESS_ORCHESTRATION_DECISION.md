# Harness OrchestrationDecision 设计说明

本文档定义 **OrchestrationDecision** 在当前餐饮集团经营分析 AI / 多智能体 Harness 中的含义、**第一来源（QuerySemanticParser v2 大模型输出）**、Java 侧仅做**执行边界与安全约束**的边界、**taskMode** 枚举、各模式下的执行形态与约束，以及与 **Replay**、**Guardrails**、**Human-in-the-loop** 的关系。

> **核心设计（已修正）**：**不要**设计 `OrchestrationDecisionService` 或任何 **Java if/else 语义层** 来判断用户问题应走 `DIRECT_LLM`、`DETERMINISTIC_WORKFLOW`、`ROUTED_AGENT`、`PLANNER_EXECUTOR`、`MULTI_AGENT` 还是 `HUMAN_IN_THE_LOOP`。**用户每一句话都先进入 QuerySemanticParser v2**，由大模型结合上下文理解短句、追问、省略表达，并在输出 JSON 的 **`orchestrationDecisionCandidate`** 对象中给出 `taskMode`、`selectedAgents`、`selectedTools`、布尔标志与澄清字段。Java **不**再根据用户原文或结构化字段去「猜」应走哪条路；若模型判断不清，应输出 **`taskMode=NEED_CLARIFICATION`**、`clarificationRequired=true` 与 `clarificationQuestion`，**本轮不调用业务 Tool**。  
> **权威细则**：**taskMode 与各模式选择规则**以 **`src/main/resources/ai-prompts/semantic/query_semantic_parser.v2.md`** 中「OrchestrationDecision：`orchestrationDecisionCandidate`」章节为准（本文对齐其语义）。

> **范围说明**：本文为设计说明；后续若需 Java 类承接与 trace 相关的结构化落盘/归一化，**不得**承担语义判断职责，命名上仅允许如 **OrchestrationDecisionTrace** / **OrchestrationDecisionCanonicalizer**（格式校验、缺省补全、枚举归一化等与**安全与契约**相关的非语义路由）。

---

## 1. OrchestrationDecision 是什么、在 Harness 中的位置

**OrchestrationDecision** 在本项目中指：**由 QuerySemanticParser v2 在单行 JSON 中输出的 `orchestrationDecisionCandidate` 对象**——描述「本轮如何编排」的结构化候选（任务模式、选人/选工具、是否需要计划器/多 Agent/审批/澄清等），经 **AiResolvedQueryContextResolver** 与既有 **intent / path / timeWindow / scope** 等合并进入 **ResolvedQueryContext**（或等价载体），再被 **MasterBusinessAgent** 与各执行分支消费。  
（**说明**：历史 parser 可能尚未解析该对象；**落地 Java 前**仍以 prompt 契约为准，由后续 JsonParser/Resolver 接入。）

**主链路：**

```
UserMessage
  → QuerySemanticParser v2
        输出 intent / time / requestedScope / metric …
             以及 orchestrationDecisionCandidate {
               taskMode, selectedAgents, selectedTools,
               plannerRequired, multiAgentRequired, approvalRequired,
               clarificationRequired, clarificationQuestion,
               confidence, reason
             }
        （以上为 OrchestrationDecision 的第一来源）
  → AiResolvedQueryContextResolver
        （时间继承、scope、权限解析等与既有设计对齐；合并 v2 编排字段）
  → MasterBusinessAgent
        （读取 v2 给出的 canonical taskMode / selectedAgents；负责调度）
  → DomainAgent / PlannerExecutor / MultiAgent / Human-in-the-loop
  → AnswerPlan
  → Composer / Renderer
  → SSE / 前台展示
```

### 1.1 位置约束

- **编排意图（走哪种 taskMode、选谁、是否澄清）的第一决策在 v2 LLM**；**不在** Resolver 之后单设一层「Java OrchestrationDecision 服务」再做语义分叉。
- **AiResolvedQueryContextResolver** **不能**绕过 v2；**不能**用 Java 重新推断「该走单 Agent 还是多 Agent」（仅可做契约合并、与时间/权限字段的一致性校验）。
- **MasterBusinessAgent** 只负责**按已解析上下文中的编排字段调度**；**不**重新理解用户原文以决定编排。

---

## 2. OrchestrationDecision 的第一来源：`orchestrationDecisionCandidate`（v2 契约）

v2 单行 JSON 中**必须**包含顶层键 **`orchestrationDecisionCandidate`**，值为对象。其字段如下（**与 prompt `query_semantic_parser.v2.md` 一致**）：

| 字段 | 说明 |
|------|------|
| `taskMode` | 本轮任务模式枚举值（见 §5）；不确定时必须为 `NEED_CLARIFICATION` |
| `selectedAgents` | 建议涉及的领域 Agent 标识列表（如 `RevenueAgent`、`PurchaseAgent` …） |
| `selectedTools` | 建议调用的业务 Tool ID 列表（须与 `intent`/路径语义一致；无把握可 `[]` 或 null，见 prompt） |
| `plannerRequired` | 是否需进入 PlannerExecutor 类多步执行 |
| `multiAgentRequired` | 是否需多 Agent 协同 |
| `approvalRequired` | LLM 判断本轮是否**可能**需人工审批（写/对外影响等） |
| `clarificationRequired` | 是否必须以追问结束本轮、**不调用业务 Tool** |
| `clarificationQuestion` | 给用户的追问文案；不需要澄清时为 `null` |
| `confidence` | 对 **taskMode / selectedAgents / selectedTools** 选择的置信度（0–1 或 0–100，与 prompt 约定一致） |
| `reason` | 简短可审计理由（不写大段复述用户原话） |

**与既有顶层字段的关系（过渡约定）**：v2 仍须输出 **`needClarification`**、**`clarificationQuestion`**（及四大 `*Action`、`intent`、`time` 等）以供当前解析链路使用；在未改 Java parser 之前，应保持 **`needClarification` 与 `orchestrationDecisionCandidate.clarificationRequired` 同真同假**，两处 **`clarificationQuestion` 一致**。顶层 **`confidence` / `reason`** 仍可表示整体语义解析；**编排专项**以 **`orchestrationDecisionCandidate` 内同名或专用字段** 为准写入 trace。

此外，v2 仍输出 **intent / time / requestedScope / metric** 等，由 Resolver 做时间继承与范围解析的最终合并；**`selectedAgents` / `selectedTools` 必须与 intent、path 语义一致**，不得割裂。

---

## 3. Java 侧职责：只做执行边界与安全约束（不做语义编排）

### 3.1 禁止：不新增 Java 语义判断层

Java **不做**下列基于「猜测用户意图」的分叉（**禁止使用 if/else 链**等方式决定）：

- 是普通 LLM、workflow、单 Agent、multiAgent、PlannerExecutor，还是是否需要追问；
- 「用户这句话到底什么意思」的补充推断（包括但不限于用户原文 contains/regex）。

若 v2 **未**给出合法的编排闭包（例如必填字段缺失、枚举非法），Java 应采**契约失败**路径：**fallback**（如统一降级为安全拒绝或触发一次「请重述」的系统响应，而非 Java 自拟 taskMode）、并**完整 trace**；**不允许**Java 顶替 v2「猜」意图。

### 3.2 允许：校验、兜底、追踪

Java **只做**包括但不限于：

| 类别 | 说明 |
|------|------|
| **存在性与契约** | 校验 `selectedAgent` 是否在注册表中存在；`selectedTool` 是否允许在当前环境启用 |
| **权限与能力** | 校验当前用户是否有权调用所选 Tool / Agent 所属能力 |
| **数据范围** | 校验调用参数始终在 `ResolvedQueryContext` 的 scope、时间窗、SQL 可视范围内 |
| **写操作与人审** | 见 **§8**（硬性兜底）与 **§9**（执行方式）；**即使 LLM 漏判 `approvalRequired`**，触及写类动作时执行层强制拦截 |
| **Fallback** | 契约或调用失败时的安全降级策略（不向用户编造业务数字） |
| **Trace / Replay** | 落盘 v2 编排字段、Java 校验结果、纠错与兜底分支，供 Replay 断言 |

Java **不**重新理解用户原文；**不**用业务代码替代 v2 选择 taskMode。

### 3.3 全局时间口语信号（`AiQuerySemanticTimeLexicon`，与 §3.1 边界）

**编排路由**（走何种 `taskMode`、选谁）仍以 **§3.1** 为准：**不得**用用户原文 `contains` / `regex` 在 Java 里「猜」意图。**以下仅为窄例外**：与 **`query_semantic_parser.v2.md`** 中「明确时间词」对齐的 **多轮时间窗合并 / 继承纠偏**（全局时间语义层），**不是**单业务 Agent 补丁。

| 约束 | 说明 |
|------|------|
| **统一入口** | 显式时间词（当前已实现：**这个月 / 本月 / 当前月** 等）的判断统一走 **`com.nongxinle.ai.semantic.AiQuerySemanticTimeLexicon`**（如 **`explicitCurrentMonthMentioned(normalizedUserMessage)`**），与 Resolver 归一后的本轮用户句配合使用。 |
| **禁止分散** | **不要**在 **DomainAgent**、**ToolExecutor**、**Composer**、**`AiResolvedQueryContextResolver` 主流程**（编排分叉）、**`MasterBusinessAgent`** 等路径上，为同类「口语时间锚」再写一套用户原文 **`contains` / `regex`**。 |
| **后续扩展** | 若需增加 **今天 / 本周 / 本月 / 今年 / 去年同期 / 上季度** 等口语信号，**集中**扩写 **`AiQuerySemanticTimeLexicon`**（或经评审后统一抽到同名 **`TimeUtteranceSignals`** 类）；**禁止**在业务模块复制短语表。 |
| **合并层消费方式** | **`AiQuerySemanticLlmMergeHelper`**（及同类时间合并代码）**只调用**上述词典的公开 API，**不**在内联逻辑里散写用户原文判断。 |

---

## 4. 判断不清楚：NEED_CLARIFICATION（不要让 Java 猜）

当 **v2 无法用当前上下文可靠完成编排**（歧义大、缺失关键维度、与用户可见 scope 冲突等），必须：

| 要求 | 说明 |
|------|------|
| `taskMode` | **`NEED_CLARIFICATION`** |
| `clarificationRequired` | **`true`** |
| `clarificationQuestion` | 给出明确、可用的追问 |
| **本轮执行** | **不**调用业务数据 Tool |
| **Java** | **不**根据其它字段拼凑「猜测」的最终 taskMode |

澄清回合结束后，用户在**下一轮**再次进入 v2；**编排决策仍必须由 v2 输出**，而非 Java 在多轮补丁里推导。

---

## 5. taskMode 枚举

**由 v2 在 `orchestrationDecisionCandidate.taskMode` 中输出；Java 仅校验合法性。** 取值与语义细则见 **`query_semantic_parser.v2.md`**。

| 取值 | 含义 |
|------|------|
| `DIRECT_LLM` | 知识/解释类，不查库、不调业务 Tool |
| `DETERMINISTIC_WORKFLOW` | 固定流程、固定口径、固定 Tool（与高确定性流水线对齐） |
| `ROUTED_AGENT` | **单一**领域 Agent（如营业额→RevenueAgent） |
| `PLANNER_EXECUTOR` | 多步可控分析，`plannerRequired` 通常为 true |
| `MULTI_AGENT` | 多领域汇总，`multiAgentRequired` 通常为 true |
| `HUMAN_IN_THE_LOOP` | 写操作或对外影响，需人审 |
| `NEED_CLARIFICATION` | 表达过短或缺关键口径，勿硬猜 |

`NEED_CLARIFICATION` **优先**：`clarificationRequired=true`，本轮不调业务 Tool；Executor 不得在未经新一轮 v2 的情况下强行调用 Tool。

---

## 6. 各 taskMode 说明（产品语义；决策权在 v2）

以下为每种模式的：**适用场景**、**餐饮业务例子**、**执行形态**、**禁止事项**、**失败处理**、**是否允许工具调用**、**是否允许写操作**。  
其中「谁决定进入该模式」答案统一为：**QuerySemanticParser v2**；Master / Domain Agent **不重判**。

### 6.1 DIRECT_LLM

| 维度 | 说明 |
|------|------|
| **适用场景** | 解释类、知识类、无需落库查询；不涉及本企业真实经营数字。 |
| **餐饮例子** | 「毛利率是什么意思？」「餐饮店为什么要看出库损耗？」 |
| **执行形态** | 通过 input guardrail 后走通用回复路径；**不**调用 Master 链路上的业务 Tool。 |
| **禁止事项** | 禁止编造本租户真实金额；DomainAgent **不**承担终答；Composer 不负责「算数改结论」。 |
| **失败处理** | 不满足纯知识边界 → v2 应倾向 `NEED_CLARIFICATION` 或 refusal，**非**Java 改写 taskMode 猜数据。 |
| **工具调用** | 默认**不允许**业务数据 Tool。 |
| **写操作** | **不允许**。写意图必须由 v2/HITL 路径表达，并由 Java approval guardrail **强制**。 |

### 6.2 DETERMINISTIC_WORKFLOW

| 维度 | 说明 |
|------|------|
| **适用场景** | 单指标、契约稳定、可走固定 Tool + AnswerPlan。 |
| **餐饮例子** | 「这个月营业额多少？」「这个月采购金额多少？」「这个月出库多少钱？」 |
| **执行形态** | v2 输出 `selectedTools`/workflow 语义；Executor 固定 Tool → AnswerPlan → Renderer；**不让「第二个 LLM」自由规划 SQL**。 |
| **禁止事项** | Java **不因**权限有 VIEW_* 擅自加 Tool（校验仅否决非法项，**不主动扩编排**）；参数须走公共 resolver。 |
| **失败处理** | Tool 错误 → trace + 安全兜底说明。 |
| **工具调用** | **允许**（只读、与 `selectedTools` 及权限一致）。 |
| **写操作** | **不允许**。 |

### 6.3 ROUTED_AGENT

| 维度 | 说明 |
|------|------|
| **适用场景** | 单一领域闭环。 |
| **餐饮例子** | 营业额→RevenueAgent；采购→PurchaseAgent；出库→StockReduceAgent；菜品毛利→DishProfitAgent（待接入）。 |
| **执行形态** | Master 按 v2 **`selectedAgents`（通常为单元素）** 调度；Agent 执行本领域 Tool/Builder。 |
| **禁止事项** | Master **不**重判单/多 Agent；DomainAgent **不**重判 `taskMode` / `selectedAgents`；**不**生成老板终答全文（终答 Composer）。 |
| **失败处理** | envelope 报错；fallback 不靠 Java 「换意图」。 |
| **工具调用** | **允许**（领域内只读）。 |
| **写操作** | 默认不允许；若存在写 Tool，须 HITL + Java **强制**。 |

### 6.4 PLANNER_EXECUTOR

| 维度 | 说明 |
|------|------|
| **适用场景** | 多步分析，步骤可被结构化约束。 |
| **餐饮例子** | 「帮我分析 AAA 门店这个月成本为什么偏高。」 |
| **执行形态** | v2/`plannerRequired` 触发结构化计划（见下）；Executor 逐步 Tool；Composer **仅表达**。 |
| **禁止事项** | 禁止仅自然语言计划；Composer 不重算不改结论；**禁止 Java 自动生成计划替代 v2**。 |
| **失败处理** | failureBranch/retryPolicy；不达标不伪造下游输入。 |
| **工具调用** | **允许**（多步只读）。 |
| **写操作** | 不允许，除非步骤显式提案且总体 HITL。 |

**结构化计划中每个 step 必须包含：** `stepId`、`stepName`、`input`、`toolToCall`、`expectedOutput`、`failureBranch`、`retryPolicy`、`acceptanceCriteria`、`traceKey`；且具备 **plan-level acceptance criteria**，供 Replay 断言。

### 6.5 MULTI_AGENT

| 维度 | 说明 |
|------|------|
| **适用场景** | 多领域均需贡献。 |
| **餐饮例子** | 「这个月经营得怎么样？」「AAA 和汀兰餐厅哪个经营情况好？」 |
| **执行形态** | v2 输出 **`multiAgentRequired`** 与 **`selectedAgents`**；Master 并行/拓扑调度；结构化合并 → Composer。 |
| **禁止事项** | Master **不**根据权限「自动加满 Agent」；**禁止** Java/regex 从原文组 Agent；Composer 不重算。 |
| **失败处理** | 部分 Agent 失败 → trace 明示；不跨域猜测补数。 |
| **工具调用** | **允许**。 |
| **写操作** | 不允许（跨域建议若涉写→HITL）。 |

### 6.6 HUMAN_IN_THE_LOOP

| 维度 | 说明 |
|------|------|
| **适用场景** | 写操作与高影响变更。 |
| **执行形态** | v2 可置 `approvalRequired=true`；**Java 硬性兜底**：见 §8。 |
| **禁止事项** | 未确认不得调真实写 Tool；全程 trace。 |
| **失败处理** | safe cancel。 |
| **工具调用** | 确认前仅允许预演/校验；确认后允许写工具（见 **§9** Human-in-the-loop 执行方式）。 |
| **写操作** | **确认后允许**。 |

---

## 7. 补充示例（与 v2 prompt 示例对齐）

- **DIRECT_LLM**：「毛利率是什么意思？」—— `orchestrationDecisionCandidate.taskMode=DIRECT_LLM`；勿答需 DB 的问题。  
- **DETERMINISTIC_WORKFLOW**：单指标固定 Tool、且产品明确走流水线封装时—— `DETERMINISTIC_WORKFLOW` + 与口径一致的 `selectedTools`。（**单笔营业额问句在产品示例中归为 ROUTED_AGENT**，见 prompt。）  
- **ROUTED_AGENT**：「这个月营业额多少？」→ `RevenueAgent`；「那采购呢？」→ `PurchaseAgent` 且 **`timeAction=INHERIT_PREVIOUS`**（与 prompt 一致）。路由由 **`selectedAgents`/`selectedTools`** 给出，**不靠 Java `if`**。  

更多句式与完整 JSON 样例见 **`query_semantic_parser.v2.md`**「输出示例」。

---

## 8. Human-in-the-loop：v2 预判 + Java 硬性兜底

- **语义侧**：LLM（v2）可先判断 **`approvalRequired`**，表达「可能需要人审」。  
- **执行侧硬性兜底**：以下情形**即使 LLM 漏判 `approvalRequired` 或误判为不需审批**，Java **必须**拦截并走人审/拒绝路径，不得直接执行：

  - **写操作**（任意落库、改状态）  
  - **退款**  
  - **调价**  
  - **删除**  
  - **发通知** / 对外推送  
  - **提交订单**  

以及与上述风险同级、产品明确列入审批清单的动作。

---

## 9. Human-in-the-loop 执行方式

1. 生成 **`proposedAction`**（结构化）。  
2. **暂停**真实写路径。  
3. **等待用户确认**。  
4. **确认后**才可调真实写 Tool。  
5. **拒绝 / 超时** → **safe cancel**。  
6. **审批全流程写入 trace**。

---

## 10. Guardrails 定义

| Guardrail | 职责 |
|-----------|------|
| **input guardrail** | v2/Master 前的安全输入边界；防越狱；与租户与权限一致。 |
| **tool guardrail** | 调用前校验 Tool 与白名单、`selectedTools`/注册表一致性、scope/时间窗；**写类强制命中审批规则**（§8）。 |
| **output guardrail** | 防敏感泄露；不把内部 trace 随意暴露给用户。 |
| **permission guardrail** | 无权则拒绝调用；**Java 不因「有权」而扩大 v2 未给出的 Tool/Agent**。 |
| **data scope guardrail** | 与 Resolver 输出的范围一致；禁止手写扩大 SQL 范围。 |
| **approval guardrail** | 写与人审链路；LLM + Java 双线约束（§8）。 |

---

## 11. MasterBusinessAgent 的职责（更新）

- 读取 **`ResolvedQueryContext` 中源自 v2 的 canonical `taskMode`、`selectedAgents`、`selectedTools`、布尔标志**。  
- **调度** DomainAgent / PlannerExecutor / MultiAgent / HITL 分支。  
- **不**根据用户原文重新判断 taskMode；**不**重新决定单 Agent 还是多 Agent。  
- **不**直接 SQL；**不**直接生成老板终答。  

---

## 12. DomainAgent 的职责（更新）

- 执行本领域任务（Tool/Builder、`AgentResultEnvelope` / AnswerPlan 片段）。  
- **不重判** `taskMode`；**不重判** `selectedAgents`。  
- **不**生成最终面向老板的自然语言回答（交由 Composer / Renderer）。  
- **不**手写公共时间/scope/权限/Tool 请求参数逻辑；走公共 resolver。  

---

## 13. 与 Replay 的关系

### 13.1 Trace

- v2 输出的 **`orchestrationDecisionCandidate` 全文**（含 `taskMode`、`selectedAgents`、`selectedTools`、布尔标志与 `confidence`、`reason`）应进入 **trace**。  
- Java **校验失败 / 兜底 / §8 强制审批**分支必须可回放。

### 13.2 Replay 断言范围

除最终答案外，建议断言：

| 维度 | 说明 |
|------|------|
| `taskMode` | 期望模式（含 `NEED_CLARIFICATION`） |
| `clarificationRequired` / `clarificationQuestion` | 追问回合 |
| `selectedAgents` | 集合/顺序策略 |
| `selectedTools` | 与实际调用一致或可解释偏差 |
| `plannerRequired` / `multiAgentRequired` | 与分支一致 |
| `approvalRequired` 与 Java **强制命中**结果 | HITL 合规 |
| `failureBranch` / plan-level acceptance（PlannerExecutor） | 同上 |

---

## 14. 后续落地阶段建议（已无 Java 语义 OrchestrationDecisionService）

建议顺序（**不含**承担语义分叉的 Java `OrchestrationDecisionService`）：

1. **`AiQuerySemanticParseResultJsonParser` / Resolver**：接入并归一 **`orchestrationDecisionCandidate`**（与顶层 `needClarification` 对齐策略）；prompt 已定稿于 **`query_semantic_parser.v2.md`**。  
2. **AiResolvedQueryContextResolver**：合并编排字段与时间/权限/scope；**不**新增语义 if/else 选路。  
3. **MasterBusinessAgent**：消费规范字段；对齐调度表。  
4. **DishProfitAgent** 最小闭环（与 Revenue / Purchase / StockReduce 对齐）。  
5. （可选）**OrchestrationDecisionTrace** / **OrchestrationDecisionCanonicalizer**：仅契约归一化、枚举校验、 Replay 对齐，**不承担**「替你决定 taskMode」。  
6. **BusinessOverview / Diagnosis MultiAgent**（✅ **v1 收口**：**`GRAPH_RUN`** Case **`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`**、**`BUSINESS_DIAGNOSIS_V1_CORE_3`**）；**Human-in-the-loop** 产品与 Java 强制执行链（**仍为设计 / 下一阶段**）。

---

## 15. 文档维护说明（本轮约束）

本篇若随迭代修订，仍为 **Markdown 设计与契约说明**；不替代 v2 Prompt/Schema 的单独文档。

如需代码变更，须在独立任务中进行；**不在**本条目中混入未评审的接口定义。

---

## 16. taskMode 实际落地状态（阶段收口 · 2026-05-13）

以下为 **产品与代码已观测到的契约状态**，**不弱化** §1～§5 的根本原则：**编排意图仍以 v2 `orchestrationDecisionCandidate` 为第一来源**；Java **不**用用户原文 **`contains`/正则** 抢语义。**差异说明**：为消除 **taskMode / multiAgentRequired 与实际 Master 调度** 的 **漂移**（历史：v2 偶发 **`ROUTED_AGENT`** 但 **BusinessOverview / Diagnosis** 仍为四域 **`MULTI_AGENT`** 形态），Resolver 对部分 **已定稿 path + 结构化 wire** 会做 **deterministic canonical 对齐**（**格式与执行契约**，**不改变**顶层 intent/path 语义来源）。对齐规则与 Replay 断言见 **`docs/AI_HARNESS_REPLAY_CASES.md`**、**`docs/ai/master-business-agent-design.md`**。

| **`taskMode` / 能力面** | 落地状态 | 备注 |
|-------------------------|----------|------|
| **`ROUTED_AGENT`** | ✅ **已落地** | **单域**专线（营业额 / 采购 / 核销 / 菜品毛利）由 v2 **`selectedAgents` / selectedTools + path** 表达；Master 路由子 Agent。**Replay**：**`V2_SEMANTIC_MAINLINE_CORE_10`**（Resolver 摘要）；单域 **`GRAPH_RUN`** 仍待按计划补齐（见 **`TODO_MULTI_AGENT.md`** §下一阶段 A–D）。 |
| **`MULTI_AGENT`** | ✅ **已落地** | **BusinessOverview**（**`business_overview_path`** 四域概览专线）与 **DiagnosisAgent v1**（**`business_diagnosis_path`**）稳定为 **`MULTI_AGENT`**，`multiAgentRequired` 等与执行一致。**Replay**：**`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`**、**`BUSINESS_DIAGNOSIS_V1_CORE_3`**（**`GRAPH_RUN`**）。 |
| **`PLANNER_EXECUTOR`** | ⏳ **待进入 v1** | **`plannerRequired` / plan-level acceptance**；未作为本阶段验收主线；下一阶段 **`TODO_MULTI_AGENT.md` §C**。 |
| **`HUMAN_IN_THE_LOOP`** | 📋 **仍为设计阶段** | §6.6 / §8～§9 描述 **v2 预判 + Java 硬性兜底 + 产品与执行链**；**尚无**端到端产品与 **DTO / 持久化契约**闭环；下一阶段 **`TODO_MULTI_AGENT.md` §D**（DTO 设计）。 |

**其它 `taskMode`（§5）**：**`NEED_CLARIFICATION`** / **`DIRECT_LLM`** / **`DETERMINISTIC_WORKFLOW`** 等仍以 v2 与 **`HARNESS_ORCHESTRATION_DECISION.md`** 既有章节为准。

---

## 附录：与现有原则的交叉引用

- **编排决策第一来源**：**QuerySemanticParser v2** 顶层 **`orchestrationDecisionCandidate`**（详见同目录 **`query_semantic_parser.v2.md`**）。  
- **Java**：校验、兜底、强制执行审批、trace；**不语义选路**。  
- **澄清**：**v2** 输出 `NEED_CLARIFICATION`；**不许**Java 猜测用户意图。  
- **MasterBusinessAgent**：只调度，不重解原文决定编排。  
- **DomainAgent**：不重判编排字段；不产生终答全文。  
- **Composer / Renderer**：只表达，不重算、不编造结论。  
- **Tool 参数**：**BusinessToolExecutionRequestResolver** 等公共层；Agent 内不手写时间/门店/SQL 范围。  
