# MasterBusinessAgent：生产级业务子 Agent 编排设计

> **读者**：接手餐饮集团经营分析 AI Harness / 多智能体编排的工程师。  
> **目的**：定义 **MasterBusinessAgent** 与子 Agent 的职责边界、核心数据结构、Trace/Replay 契约与分阶段落地路线；**不替代**既有语义解析与 Resolver，而是在 **`ResolvedQueryContext` 之后** 收敛「谁执行、按何顺序、失败怎么办」。  
> **关联文档**：分层契约见 `docs/ai/harness-composer-architecture.md`；Replay 必跑 Case 见 `docs/AI_HARNESS_REPLAY_CASES.md`（**核心回归**：**`V2_SEMANTIC_MAINLINE_CORE_10`** + **`GRAPH_RUN`**：**`BUSINESS_DIAGNOSIS_V1_CORE_3`**、**`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`**）。

---

## 1. 为什么要引入 MasterBusinessAgent

当前主链路已在工程上落实：**QuerySemanticParser v2 → Resolver → `AiResolvedQueryContext` → Planner / Tool → AnswerPlan → Composer / Renderer**。但当业务能力横向扩展（营业额、采购、出库、菜品毛利、诊断、门店对比等）时，若继续在单一 Planner 节点内堆叠分支，会出现：

1. **调度逻辑与领域逻辑耦合**：顺序、并行、超时、失败兜底与「查什么数」绑在一起，难以单独测试与回放断言。
2. **扩展成本高**：每新增一条业务竖切都要改同一坨编排代码，违背「子 Agent 自声明能力」的插件化预期。
3. **可观测性不足**：生产级 Agent 需要可追溯的 **dispatch → 子 Agent 入参/出参 → 状态机**，仅有最终 SSE 文本不足以定位「哪一步调度错了」。
4. **失败语义模糊**：Tool 空结果、权限拒绝、超时、LLM 结构化输出损坏等若只在末端表现为「一句话」，Harness 无法做 **PARTIAL_SUCCESS / DEGRADED** 等精细化管控与 Replay 断言。

**MasterBusinessAgent 的定位**：在 **不改变「语义由 v2、落地由 Java」** 的前提下，承担 **可调度、可扩展、可回放、可管控** 的编排层——把「本轮该跑哪些子 Agent、怎么跑、跑完如何聚合成 AnswerPlan」说清楚；**不**重复解析用户原文，**不**绕过权限与 SQL 范围，**不**直接生成面向老板的自然语言终稿。

---

## 2. 与现有组件的关系（数据流）

下列关系用「只读 / 写入职责」描述，避免后续实现时越权。

```text
UserMessage
  → QuerySemanticParser v2          （LLM：语义动作 / 结构化意图）
  → Resolver                         （Java：时间、门店范围、权限、effective* 确定性落地）
  → AiResolvedQueryContext           （唯一公共查询上下文；Tool 只按此查数）
  → MasterBusinessAgent              【本设计新增层：调度 + 聚合 + Trace】
       → RevenueAgent | PurchaseAgent | StockReduceAgent | DishProfitAgent | DiagnosisAgent | …
       → （各子 Agent 仅调领域内 Tool / AnswerPlanBuilder）
  → AgentResultEnvelope（可多份）    （结构化结果 + 状态 + 失败语义）
  → （聚合）AnswerPlan                （选数、排序、定型；规则在 Plan，不在 Composer）
  → Composer / Renderer              （仅表达：不重新计算、不重排、不编造数字）
  → SSE / 前台
```

| 组件 | 与 MasterBusinessAgent 的关系 |
|------|-------------------------------|
| **QuerySemanticParser v2** | Master **不得**重新理解用户原文；仅消费 Resolver 已写入 `ResolvedQueryContext` 的结构化字段。 |
| **Resolver / FollowUp / TimeWindow / OrgScope** | Master **不得**改写权限与时间窗语义；若需澄清，应返回 **NEED_CLARIFICATION** 类状态并由上层产品流程处理，而非自行解析自然语言。 |
| **Tool** | 仍只接收由 `ResolvedQueryContext` 确定性推导的参数；子 Agent **组织** Tool 调用，Tool **不**再解析用户话。 |
| **AnswerPlan** | Master **不**替代 AnswerPlan；负责触发 Builder、合并多个子结果的 Plan 片段（若有），保证 Composer 仍只面向 Plan + 事实数据表达。 |
| **Composer / Renderer** | Master **不**生成最终对用户话术；不向 Composer 注入「新业务判断」。 |

---

## 3. MasterBusinessAgent 职责边界

**必须做**

- 根据 `ResolvedQueryContext`（及可选 `BusinessAgentRequest` 元数据）生成 **`BusinessAgentDispatchPlan`**。
- 按 Plan 调用注册的 **`BusinessSubAgent`**（顺序 / 并行策略由 Plan 描述）。
- 聚合子 Agent 的 **`AgentResultEnvelope`**（成功、部分成功、跳过、失败等），形成进入 AnswerPlan 流水线所需的结构化输入。
- 产出完整 **`AgentTraceEnvelope`**（或可拆分的多段 trace），供 Debug / Replay / 运维检索。
- 执行 **`AgentFailurePolicy`**：超时、重试、降级、短路、权限失败的上报方式。

**禁止做**

- 解析用户原始字符串做意图补丁（**禁止** `contains` / 正则抢语义）。
- 绕过 v2 / Resolver 直接构造「自以为是的」上下文。
- 直连数据库或绕过既有 PermissionGuard / SQL 范围约束。
- 生成最终自然语言回答或对数字做二次计算与重排序（归属 AnswerPlan 或确定性 Renderer）。

---

## 4. 子 Agent（BusinessSubAgent）职责边界

**必须做**

- 只消费 **`ResolvedQueryContext`** 与 **`BusinessAgentRequest`** 中与自身相关的切片（由 Registry / DispatchPlan 约束）。
- 仅调用 **本领域** 已注册的 Tool、`AnswerPlanBuilder`、确定性 Renderer 辅助（若现有代码如此拆分）。
- 产出 **`AgentResultEnvelope`**（及可选中间 trace），内部可包含 **`AnswerPlan` 片段** 或 Builder 所需结构化 facts。

**禁止做**

- 读取用户原文并自行推断意图（语义已在 v2 + Resolver 落地）。
- 绕过权限或扩大 SQL 范围。
- 直接向通道写入「老板可见」的最终文案（应统一经 Composer / Renderer）。

---

## 5. BusinessAgentRequest 设计（草案）

**目的**：把「本轮 Run 中与编排相关的、但不属于语义歧义」的信息从各处收敛到一个显式请求对象，便于 Trace 与单元测试构造。

建议字段（实现阶段可增减，但需在 Trace 中可序列化摘要）：

| 字段 | 说明 |
|------|------|
| `runId` / `turnId` | 关联 Run / 多轮轮次。 |
| `resolvedQueryContext` | **必填**；只读引用 `AiResolvedQueryContext`。 |
| `workspaceMode` / `graphPhase` | 与现有 Run 状态机对齐的枚举（若已有则用现有类型）。 |
| `replayCaseId` | 可选；Harness Replay 时写入，便于 Trace 过滤。 |
| `callerHints` | **极简**、非语义：例如「仅诊断」「跳过缓存」类工程开关；**不得**承载自然语言意图补丁。 |
| `deadlineAt` / `maxWallClockMs` | 本轮编排上限（与全局 Run 超时协调）。 |

**原则**：Request 是「调度上下文」，不是第二个语义解析器。

---

## 6. BusinessAgentDispatchPlan 设计（草案）

**目的**：显式描述 **本轮调用哪些子 Agent、顺序、并行、必需性、超时、失败策略**，使 Replay 可断言调度链路。

建议结构：

| 元素 | 说明 |
|------|------|
| `planId` | UUID 或单调 ID，写入 Trace。 |
| `steps[]` | 有序步骤列表；每步绑定 `agentId`（注册键）。 |
| `steps[].executionMode` | `SEQUENTIAL` \| `PARALLEL_GROUP`（同组内并行）等。 |
| `steps[].required` | 失败是否导致整轮失败或触发降级。 |
| `steps[].timeoutMs` | 单步超时。 |
| `steps[].failurePolicyRef` | 引用命名策略或内联 **`AgentFailurePolicy`**。 |
| `steps[].inputProjection` | 从 `ResolvedQueryContext` **确定性投影**出子 Agent 所需字段子集（字段名级别，非 NLP）。 |
| `correlationTags` | 用于日志与 Replay 的标签（如 `intent=effectiveIntentCode`）。 |

**生成位置**：仅 **MasterBusinessAgent**（或其内部的 **DispatchPlanner** 纯组件）；生成逻辑应可单测，且不依赖用户原文字符串匹配。

---

## 7. BusinessSubAgent 接口设计（草案）

```text
interface BusinessSubAgent {
  /** 注册键，如 "revenue", "purchase". */
  String agentId();

  /** 声明支持的上下文特征（能力标签 / intent 集合 / path 集合等），供 Registry 索引。 */
  BusinessAgentCapability capability();

  /** 执行单步子任务；不得解析 raw user text。 */
  AgentResultEnvelope execute(BusinessAgentRequest request, BusinessAgentStepSpec stepSpec);
}
```

说明：

- `BusinessAgentStepSpec`：来自 `BusinessAgentDispatchPlan` 的单步定义（超时、required、`inputProjection` 解析结果等）。
- `capability()` 应返回 **结构化声明**（见 §8），避免 Master 内硬编码 `if (intent == …)`；Master 只负责 **选择算法**（例如基于 capability 评分），具体「支持或不支持」由子 Agent 声明。

---

## 8. BusinessAgentRegistry 设计（草案）

**职责**

- 启动时注册全部 `BusinessSubAgent` Bean（或 SPI）。
- 提供 `resolve(candidatePlan)` 所需的查询：`listByCapabilityTag`、`findByAgentId`。
- **可选**：校验 DispatchPlan 中 `agentId` 存在、必填 capability 是否覆盖。

**原则**

- Registry **不做业务判断**；不做语义解析。
- 与 Spring `Map<String, BusinessSubAgent>` 相比，增加 **capability 索引**与 **冲突检测**（两个 Agent 声明同一优先意图时的告警策略）。

---

## 9. AgentResultEnvelope 设计（草案）

**目的**：统一子 Agent 产出，便于聚合与 Replay 断言。

建议包含：

| 字段 | 说明 |
|------|------|
| `agentId` | 来源子 Agent。 |
| `status` | 见 §11 状态枚举。 |
| `answerPlanFragment` | 可选；指向本轮可合并的 AnswerPlan 片段或 Builder 输出句柄。 |
| `toolInvocationSummaries` | 可选；Tool 名、参数摘要、行数、耗时（**不含**大字段正文）。 |
| `failureType` | 超时 / 权限 / 数据为空 / LLM 结构化损坏 / 内部异常 等。 |
| `userVisibleMessageKey` | 可选；**枚举键**指向文案模板，而非自由生成长文。 |
| `durationMs` | 执行耗时。 |
| `extensions` | 允许携带 Replay 专用小字段（须文档化键名）。 |

聚合规则（Master）：多个 `PARTIAL_SUCCESS` 如何合成、`FAILED` 是否短路，由 **`AgentFailurePolicy`** + 产品约定驱动，**不写死**在 Composer。

---

## 10. AgentTraceEnvelope 设计（草案）

**目的**：生产级排障 + Harness Replay 扩展为「可断言调度链路」。

建议分段记录（可为一条总 envelope 或多条子记录）：

| 段落 | 内容 |
|------|------|
| `semanticSummary` | **结构化**：`effectiveIntentCode`、`effectivePathCode`、时间窗摘要、范围摘要（**非**用户原文全文必要时仅哈希）。 |
| `resolvedContextFingerprint` | `ResolvedQueryContext` 的稳定摘要哈希（字段清单需版本化）。 |
| `dispatchPlan` | `BusinessAgentDispatchPlan` 序列化或确定性摘要。 |
| `agentSpans[]` | 每个子 Agent：`input` 摘要、`output` 摘要、`status`、`durationMs`、`failureType`。 |
| `aggregation` | Master 聚合决策：合并顺序、是否降级、最终合成状态。 |

**隐私与安全**：Trace 默认不落用户全文；敏感字段脱敏与权限对齐现有 Run Trace 策略。

---

## 11. AgentFailurePolicy 设计（草案）

**目的**：统一失败与降级语义，避免各处 catch 后随意拼字符串。

建议策略维度：

| 维度 | 示例 |
|------|------|
| **超时** | 单步 cancel；标记 `DEGRADED`；是否继续后续步骤。 |
| **Tool 失败** | 可重试次数、退避；最终 `FAILED` 或 `PARTIAL_SUCCESS`。 |
| **空数据** | `NO_DATA`；是否仍生成 AnswerPlan「解释性片段」。 |
| **权限** | `PERMISSION_DENIED`；短路 Tool；不向 Composer 泄漏越权细节。 |
| **LLM 结构化输出异常** | 仅限允许使用 LLM 的子步骤（若有）；策略：`SKIPPED` / `DEGRADED`。 |
| **澄清 needed** | `NEED_CLARIFICATION`：不得由子 Agent 编造参数；应回到产品澄清流（若上层支持）。 |

**全局状态枚举（建议）**：`SUCCESS`、`NO_DATA`、`PARTIAL_SUCCESS`、`FAILED`、`SKIPPED`、`DEGRADED`、`PERMISSION_DENIED`、`NEED_CLARIFICATION`。

---

## 12. 分阶段落地方案

**状态（2026-05-13 更新）**：阶段 **B / C** 四条单域专线已交付；**BusinessOverview 四域 MultiAgent** 与 **DiagnosisAgent v1** 已收口，**`GRAPH_RUN`** Harness（**`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`**、**`BUSINESS_DIAGNOSIS_V1_CORE_3`**）已接入（见 **`AI_HARNESS_REPLAY_CASES.md`**）。**阶段 D**（Composer / Renderer「仅表达」整理）、**OrchestrationDecisionService** 独立服务、**PlannerExecutor v1**、**Human-in-the-loop** 见 **`docs/TODO_MULTI_AGENT.md`** / **`HARNESS_ORCHESTRATION_DECISION.md`**。下文阶段标签 **保留**为路线图锚点；**B1～C3 + 概览 / 诊断 MultiAgent** 已在当前仓库验收。

### 阶段 A：文档与接口草案

- 交付：本文档 +（可选）Java 草案。**已于后续迭代**：主链路接入 Master 后，阶段 A 的「零代码」表述不再描述当前主干；以 **`TODO_MULTI_AGENT`** 与本节下文 **「当前已接入：四条 DomainAgent + …」** 为准。
- 验收：团队对齐边界与数据结构；Replay 要断言的 Trace / Summarizer 字段持续迭代。

### 阶段 B：Revenue 一条链路（**已交付 · B1**）

- 接入 **RevenueAgent**，Master 调度 **`revenue_query`** 单步（narrow gate + fallback + legacy skip 约定）。
- Trace：`AiRunState#masterBusinessAgentDebug` + Summarizer 扁平字段。
- Replay：**`V2_SEMANTIC_MAINLINE_CORE_10`** 覆盖语义入口；**调度 Trace 扩展**见后续 Orchestration 里程碑。

### 阶段 C：Purchase / StockReduce / DishProfit（**已交付 · C1～C3**）

- 各竖切 **`BusinessSubAgent`**，由 Master + **`BusinessToolExecutionNode`** 调用；**禁止**用户原文 **`contains`/正则** 抢 Resolver（仍适用）。
- Dispatch 基于 **`ResolvedQueryContext`** 的确定性字段（intent / path / metric）。
- 并行组与 **`PARTIAL_SUCCESS`** 聚合：**后续** Orchestration / PlannerExecutor 扩展。

### 阶段 D：Composer / Renderer 整理

- 收敛「仅表达」路径：Composer 输入统一来自聚合后的 AnswerPlan + envelopes 摘要。
- **不**在阶段 D 引入新业务判断；若发现判断残留在 Composer，应回溯 AnswerPlan 或 Resolver。

---

## 当前已接入：四条 DomainAgent + BusinessOverview MultiAgent + DiagnosisAgent v1（阶段收口 · 2026）

### 四条单域 DomainAgent（生产最小闭环）

以下四条专线已由 **`MasterBusinessAgent`** 在主链路调度（典型入口：**`BusinessToolExecutionNode`**），并通过负责人 **真实 Run** 抽检；Harness **`V2_SEMANTIC_MAINLINE_CORE_10`** 覆盖 **语义入口 + Resolver 摘要**，**`GRAPH_RUN`** Case（见下 **「Graph-backed Replay」**）补强 **四域 MultiAgent / Diagnosis** 全链路。

| DomainAgent | `effectiveIntentCode`（典型） | `effectivePathCode` | 主 Tool id（`AiBusinessToolIds`） | AnswerPlan（Builder → RunState） | Fallback / legacy（概要） |
|-------------|------------------------------|---------------------|-----------------------------------|-----------------------------------|---------------------------|
| **RevenueAgent** | `REVENUE_OVERVIEW` | `revenue_overview_path` | **`revenue_query`** | **`DailyRevenueAnswerPlan`**（`DailyRevenueAnswerPlanBuilder`） | **`revenueFallback`**；成功 Master 路径可 **`legacyRevenueSkipped`** 并剥离 **`toolResults`** 中已执行 Tool；否则 legacy 全跑。 |
| **PurchaseAgent** | `PURCHASE_OVERVIEW` | `purchase_overview_path`（采购结构化支线以 Resolver 为准） | **`purchase_overview`** | **`PurchaseAnswerPlan`**（`PurchaseAnswerPlanBuilder`） | **`purchaseFallback`**、**`legacyPurchaseSkipped`**、**`purchaseToolExecutedByMasterPath`** 等（Summarizer 扁平键）。 |
| **StockReduceAgent** | `STOCK_REDUCE_QUERY` | `stock_reduce_query_path` | **`stock_reduce_query`** | **`StockReduceAnswerPlan`**（`StockReduceAnswerPlanBuilder`） | **`stockReduceFallback`**、**`legacyStockReduceSkipped`** 等。 |
| **DishProfitAgent** | `DISH_PROFIT` | `dish_profit_path` | **`dish_profit_analysis`** | **`DishProfitAnswerPlan`**（主线仍由 **`DishProfitAgentNode`** / 既有 Builder 挂载） | **`dishProfitFallback`**、**`masterDishProfitPathAllowsLegacySkip`**、**`dishProfitToolExecutedByMasterPath`**。**D-8**（`DISH_SALES_QUERY` / `dish_sales_query_path`）亦执行 **`dish_profit_analysis`**（**Historical removed**：`DishSalesQueryTool` 已删）。 |

### BusinessOverview MultiAgent（✅ v1 · 闭环）

- **意图 / path**：**`effectiveIntentCode=BUSINESS_OVERVIEW`**，**`business_overview_path`**；在用户问「本月经营状况如何」类等 **四域结构化概览** 时，Master 并行调度 **Revenue / Purchase / StockReduce / DishProfit**，消费四份 **`consumedAnswerPlans`**。  
- **编排契约**：**`orchestrationTaskMode=MULTI_AGENT`**（与 **v2 `orchestrationDecisionCandidate`** + **Resolver canonical 对齐** 一致，避免表面 `ROUTED_AGENT` 与实际四域执行漂移）。详见 **`docs/HARNESS_ORCHESTRATION_DECISION.md`** §taskMode 落地状态。  
- **回归**：**`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`**（**`GRAPH_RUN`**，三问法；见 **`AI_HARNESS_REPLAY_CASES.md`**）。

### DiagnosisAgent v1（✅ 闭环）

- **意图 / path**：**`effectiveIntentCode=BUSINESS_DIAGNOSIS`**，**`business_diagnosis_path`**；证据型「经营问诊 / 诊断」——四域 AnswerPlan **+** **`BusinessDiagnosisPlan`**（含 **`dataCompleteness`** 等契约字段）。  
- **编排契约**：**`orchestrationTaskMode=MULTI_AGENT`**；单店 / 双店 **scope / SQL 部门** 与时间窗继承由 Resolver + Harness 镜像一致化。  
- **回归**：**`BUSINESS_DIAGNOSIS_V1_CORE_3`**（**`GRAPH_RUN`**，三问法；见 **`AI_HARNESS_REPLAY_CASES.md`**）。

### Graph-backed Harness Replay（✅ 已接入）

- **接口**：**`POST /api/ai/harness/replay`**，`replayMode` 可显式 **`GRAPH_RUN`**；**`BUSINESS_DIAGNOSIS_V1_CORE_3`**、**`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`** 等 `caseId` 在服务端亦可 **默认 Graph**，与 **`AiRunService` 同步执行业务图 + Tool + Master + Summarizer** 对齐。  
- **断言**：以 **`resolvedQueryContextSummary`**（及扩展字段如 **`consumedAnswerPlans`**、**`businessDiagnosisPlan`**、**`answerPreview`**、多店 **queryStoreIds** 等）为主；**不比** Composer 终稿长文。  
- **文档**：Case 表、覆盖范围与历史问题保护见 **`docs/AI_HARNESS_REPLAY_CASES.md`** §**GRAPH_RUN 核心回归**。

**Debug**：**`GET /api/ai/runs/{runId}`** → **`harnessDebug.resolvedQueryContextSummary`** 合并 **`AiHarnessResolvedContextSummarizer`** 输出；REST 交叉引用 **`docs/API_INTEGRATION.md`**。

---

## 13. 禁止项（硬性）

下列事项在设计与后续落地中 **一律禁止**，除非单独开架构变更评审：

1. **禁止**不经评审 **大范围重写** 解析 / 编排 / Tool 主链路语义。**说明**：**四条专线 + BusinessOverview MultiAgent + DiagnosisAgent v1** 已于本阶段收口。此后改动 **`MasterBusinessAgent`**、**`BusinessToolExecutionNode`**、**`AiResolvedQueryContextResolver`**、FollowUp / TimeWindow / OrgScope、AnswerPlan 挂载、经营诊断 / 经营概览 path 相关 **v2 提示或 canonical 对齐** 等，须 **优先**：**`V2_SEMANTIC_MAINLINE_CORE_10`** + **`BUSINESS_DIAGNOSIS_V1_CORE_3`** + **`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`**（后两者 **`GRAPH_RUN`**，见 **`docs/AI_HARNESS_REPLAY_CASES.md`**）并保留负责人 **真实 Run** 抽检。  
2. **不改** SQL（除非独立 DDL / 性能评审）。  
3. **不改** Composer / Renderer 语义（阶段 D 之前默认冻结业务判断）。  
4. **不改**前台（除非独立产品评审）。  
5. **不新增**基于用户原文的 **`contains` / 正则** 语义规则抢在 v2 之前或替代 Resolver。

---

## 14. 与 Replay 的关系（影响说明）

- **`V2_SEMANTIC_MAINLINE_CORE_10`**（典型 **Resolver-focused**）：断言 **语义 + Harness 上下文摘要**，覆盖 **REVENUE / PURCHASE / STOCK_REDUCE / DISH_PROFIT / BUSINESS_OVERVIEW** 等多轮语义入口；**不**等价单轮完整 Graph Tool 深挖。  
- **`BUSINESS_DIAGNOSIS_V1_CORE_3`**、**`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`**（**`GRAPH_RUN`**）：与 **`AiRunService` 同步图**一致，断言 **MULTI_AGENT、四域 AnswerPlan、`answerPreview`、`businessDiagnosisPlan`（诊断）**、多店与时间继承等——**补齐** Master 编排与 Resolver **契约漂移**的历史盲区。详见 **`AI_HARNESS_REPLAY_CASES.md`** §**GRAPH_RUN 核心回归**。  
- **OrchestrationDecisionService**：独立编排决策微服务仍为 **`HARNESS_ORCHESTRATION_DECISION.md`** / **`TODO_MULTI_AGENT.md`** 下一阶段；当前 **`taskMode` 对齐**依托 **v2 `orchestrationDecisionCandidate`** + **Resolver merge + path 专用 canonical guard**。

---

## 15. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-13 | **阶段收口（Diagnosis / BusinessOverview MultiAgent + GRAPH_RUN）**：在 §12「分阶段」之后增补 **四条 DomainAgent + BusinessOverview MultiAgent（v1）+ DiagnosisAgent（v1）+ Graph-backed Replay**；§13～§14 与关联文档对齐；Replay 核心集加入 **`BUSINESS_DIAGNOSIS_V1_CORE_3`**、**`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`**。 |
| 2026-05-13 | **阶段收口**：补充 **「当前已接入的 DomainAgent」**（Revenue / Purchase / StockReduce / DishProfit；tool id、path、AnswerPlan、fallback）；更新 §12～§14 与 §13 禁止项第 1 条说明；与 **`TODO_MULTI_AGENT`**、**`AI_HARNESS_REPLAY_CASES`**、**`API_INTEGRATION`** 对齐。 |
| 2026-05-13 | 初版：MasterBusinessAgent / DispatchPlan / Envelope / Policy / 分阶段路线 |
