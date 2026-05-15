# PlannerExecutor v1 — Harness 架构设计

> **读者**：餐饮经营 AI Harness 工程师。  
> **目的**：在阶段 B（单域 / 固定多域链路已 Replay 验收）之后，引入 **PLANNER_EXECUTOR** 模式的设计契约：**可复现的多步读链路**、步骤状态、失败策略、 trace 与 Replay 字段，以及与现有 **ROUTED_AGENT** / **MULTI_AGENT** 的差异。  
> **范围**：本文档仅为 **v1 架构设计**；**不**约定具体 Java 类名落地位置以外的实现细节；实现须与 `docs/ai/harness-composer-architecture.md`、`docs/ai/master-business-agent-design.md` 分层思想一致。

**阶段里程碑（**C-65 → **D）**：**Composite / Shadow** 生产安全框架已收口后，下一阶段以 **老板常问业务能力**（intent、单域深挖、多轮体验）为主线；业务能力 **P0～P3** 与「框架够用即止」边界见 **`[next-business-capability-roadmap.md](./next-business-capability-roadmap.md)`**。交接摘要： **`[PROJECT_HANDOFF_D1.md](./PROJECT_HANDOFF_D1.md)`**。**C-66**（集中 metrics / Shadow dashboard / **Redis** 跨实例限流）**暂缓**。

---

## 1. PlannerExecutor 在 Harness 中的位置

### 1.1 流水线顺序（概念层）

```text
用户请求
  → AiResolvedQueryContextResolver（唯一解析入口，已冻结）
  → 【本设计：PlannerExecutor v1】（可选阶段）
  → MasterBusinessAgent / DomainAgent / Tool 执行（现有能力，主逻辑冻结）
  → AnswerPlan / DiagnosisPlan / RecommendationPlan 等聚合
  → Composer 输出自然语言（主模板冻结）
```

### 1.2 推荐方案：**Resolver 之后、Master 调度之前，由 Graph 显式编排「计划 + 执行」子图**

**推荐**：在 **`AiResolvedQueryContext` 已固化之后**、**进入 `MasterBusinessAgent` 的「单次域调度」之前**，由 **Graph 层**插入 **`PlannerExecutor` 编排**（概念上可为独立 Node / Subgraph）：

| 维度 | 说明 |
|------|------|
| **输入** | 只读 `ResolvedQueryContext` + 本轮 **模板化** Planner 配置（见 §7：无自由 LLM 规划） |
| **输出** | `PlannerExecutionPlan`（步骤列表）、逐步 `PlannerStepResult`、`PlannerExecutorTrace` |
| **与 Master 的关系** | **不**要求改写 Master 内部「四个 DomainAgent」的 **核心调度表**；Executor 将每一步 **映射为对既有 Agent / Tool 契约的调用**（与现行 Harness 相同的 Tool 入参来自 `ResolvedQueryContext` 的原则）。Master 在 **PLANNER_EXECUTOR** 路径上可被视作「能力提供者」：Executor 按步骤调用 Revenue / Purchase / StockReduce / DishProfit / Diagnosis 等 **已存在** 的执行入口，而非在 Master 内嵌套一个解释型循环。 |

**为何不用「完全旁路到另一条不含 Master 的图」作为主方案？**

- 复用 **权限、组织范围、Tool 参数推导** 等已通过阶段 B 的契约，避免重复实现一套并行调用栈。
- **Replay** 仍在统一 Graph Run 下挂载 `plannerExecutorTrace`，与现有 `GRAPH_RUN` 对齐。

**为何不把 Planner 塞进 `MasterBusinessAgent` 主逻辑？**

- 阶段 C 明确 **冻结 Master 主调度**；将「多步状态机 + 降级 + 验收」并入 Master 会快速膨胀、难以单独 Replay。
- Graph 外置编排更符合「Harness 工程化」：**解析 → 计划 → 执行 → 汇总** 分层清晰。

**小结**：**PlannerExecutor 位于 `ResolvedQueryContext` 之后；在 Graph 上处于 Master 的「上游编排」或「包一层再调 Master/子能力」的位置**，而不是替代 Resolver，也不是在 Composer 内规划。

---

## 2. 与现有模式的区别

| 模式 | 行为概要 | 典型适用 |
|------|----------|----------|
| **ROUTED_AGENT** | 解析后 **路由到单一** DomainAgent（或单路径 Tool 链），一次「意图 → 一个负责人」 | 「AAA 店本月营业额多少」 |
| **MULTI_AGENT** | **固定**多 Agent / 多 Tool 组合按预设图执行（如 Business Overview / Diagnosis 固定并行或顺序） | 固定多域仪表盘、诊断 v1 主线 |
| **PLANNER_EXECUTOR** | 将 **复杂问题拆成有序步骤**；**每步执行、验收**；失败按策略处理（快进失败 / 降级继续 / 澄清占位）；**最后汇总** 成可 Composer 化的计划与事实 | 「为什么成本偏高 + 要建议」类 **跨域因果 + 建议**（仍 v1 只读） |

**本质差异**：PLANNER_EXECUTOR 引入 **显式 `PlannerExecutionPlan`**、**逐步 `PlannerStepResult`** 与 **可追溯的 `PlannerExecutorTrace`**，使复杂度从「再多加一个 MULTI_AGENT 变种」退化为「**步骤表驱动** + **统一失败语义**」。

---

## 3. 核心结构

以下为核心 **DTO / 领域对象** 设计名（实现阶段再落到具体包路径）。

### 3.1 `PlannerExecutionPlan`

表示一次 Run 的 **可读、可序列化、可 Replay** 的执行计划（**非**自然语言）。

| 字段（概念） | 说明 |
|--------------|------|
| `planId` | 本次计划唯一标识（Run 内） |
| `templateId` / `planSource` | 来自 **模板注册表**（禁止自由 LLM 动态生成） |
| `steps` | `List<PlannerStep>`，有序 |
| `failurePolicy` | 见 §6 |
| `resolvedContextRef` | 指向本轮 `ResolvedQueryContext` 的稳定摘要或 hash（便于 Replay 对齐） |
| `humanLoop` | 仅接口占位：见 §8 |
| `meta` | 版本、构建时间、调试标签 |

### 3.2 `PlannerStep`

| 字段（概念） | 说明 |
|--------------|------|
| `stepId` | 计划内唯一 |
| `order` | 顺序 |
| `kind` | 例如 `TOOL_CALL` / `AGENT_RUN` / `AGGREGATE` / `COMPOSER_PREP` |
| `targetAgent` | 可选；对应 DomainAgent 名（与 `BusinessAgentNames` 等现有枚举对齐） |
| `targetToolIds` | 可选；Tool 列表 |
| `inputBinding` | 从 `ResolvedQueryContext` 与前序 `PlannerStepResult` **绑定** 的声明（非代码表达式） |
| `acceptance` | 验收条件声明（见业务示例） |
| `allowDegraded` | 本步失败是否允许标记为 `DEGRADED` 并继续（与全局 `failurePolicy` 求交） |
| `notes` | 模板注释，供 Debug |

### 3.3 `PlannerStepResult`

| 字段（概念） | 说明 |
|--------------|------|
| `stepId` | 对应 `PlannerStep` |
| `status` | 见 §4 |
| `startedAt` / `finishedAt` | 时间戳 |
| `usedAgents` | 实际触达的 Agent 名列表 |
| `usedTools` | 实际触达的 Tool Id |
| `payloadRef` | 结构化结果引用（如 AnswerPlan、DiagnosisPlan、Tool JSON） |
| `error` | 可选；分类代码 + 消息 |
| `degradedReason` | 可选 |

### 3.4 `PlannerExecutorTrace`

单次 Run 级 **审计与 Replay 总线**。

| 字段（概念） | 说明 |
|--------------|------|
| `plan` | `PlannerExecutionPlan` 快照 |
| `stepResults` | `List<PlannerStepResult>` |
| `finalStatus` | `SUCCESS` / `FAILED` / `DEGRADED`（整轮） |
| `appliedFailurePolicy` | 实际生效策略 |
| `clarificationRequested` | 布尔；ASK_CLARIFICATION 时占位 |
| `degradedSteps` | `stepId` 列表 |
| `rollup` | 指向下游的 `finalAnswerPlan` 或等价句柄 |

---

## 4. Step 状态

| 状态 | 含义 |
|------|------|
| **PENDING** | 已排入计划，尚未开始 |
| **RUNNING** | 执行中 |
| **SUCCESS** | 验收通过 |
| **FAILED** | 未通过且不采用降级 |
| **SKIPPED** | 因依赖 / 策略跳过（例如前步 FAIL_FAST） |
| **DEGRADED** | 验收部分满足或失败但被策略允许以劣化结果继续 |

---

## 5. 失败策略

策略在 **计划级** 声明；**步骤级** `allowDegraded` 与之求交。

| 策略 | 行为 |
|------|------|
| **FAIL_FAST** | 任一关键步 **FAILED** → 后续 **SKIPPED**，整轮 `FAILED` |
| **CONTINUE_WITH_DEGRADED** | 允许将满足 `allowDegraded` 的步骤标为 **DEGRADED** 并继续；整轮可 `DEGRADED` 收尾 |
| **ASK_CLARIFICATION** | 遇缺参 / 歧义时 **不** 真实弹人；在 trace 中置 `clarificationRequested=true`，并生成 **澄清占位** 结构（v1 不执行 Human-in-the-loop） |

---

## 6. 餐饮业务示例

**用户原文**：`帮我分析 AAA 这个月成本为什么偏高，并给我三条改进建议`

**前提**：`AiResolvedQueryContext` 已解析出门店/部门语义 **AAA**、**本月** `timeWindow`、意图归因为 **PLANNER_EXECUTOR** 模板（模板化，非模型现场编计划）。

### 6.1 步骤表（示例）

| 步骤 | 动作 | 调用 Agent / Tool | 输入 | 输出 | 验收条件 | 失败是否可降级 |
|------|------|-------------------|------|------|----------|----------------|
| 1 | 使用已解析上下文 | — | `ResolvedQueryContext` | 上下文快照写入 trace | `timeWindow`、`dataScope` 完整；AAA 可解析 | 否 |
| 2 | 查 AAA 本月营业额 | **RevenueAgent** + 营收 Tool（与 `DailyRevenueAnswerPlan` 同源能力） | `ResolvedQueryContext` | `DailyRevenueAnswerPlan` + Tool 事实 | Tool 成功；金额非空或与「无数据」显式一致 | 是（无营收则 DEGRADED，靠采购/毛利侧解释） |
| 3 | 查 AAA 本月采购 | **PurchaseAgent** + 采购概览 Tool | `ResolvedQueryContext` | `PurchaseAnswerPlan` + 采购结构化事实 | Tool 成功；可追溯字段齐全 | 是 |
| 4 | 查 AAA 本月出库/核销 | **StockReduceAgent** + 出库 Tool | `ResolvedQueryContext` | `StockReduceAnswerPlan` | Tool 成功；type 口径与现有链路一致 | 是 |
| 5 | 查 AAA 本月菜品毛利 | **DishProfitAgent** + 菜品毛利 Tool | `ResolvedQueryContext` + 可选焦点菜 | `DishProfitAnswerPlan` | 与 `buildInsight` 一致；禁止 Composer 现算 | 是（局部菜系缺失可 DEGRADED） |
| 6 | 汇总 **DiagnosisPlan** | **BusinessDiagnosis** 聚合（Builder，只读前序 AnswerPlan） | 步骤 2–5 的 AnswerPlan | `DiagnosisPlan`（`focusFindings` / `evidenceRows` 等） | 必须符合 **diagnosis-answer-plan**：不反查原始 tool dump 重算 | 是（部分域缺失时标 `DEGRADED` 并收窄结论） |
| 7 | 生成 **RecommendationPlan** | 模板化规则 + 结构化字段（**非**自由订货/改价） | `DiagnosisPlan` + 各域事实 | `RecommendationPlan`（建议条目 ≤3，含依据引用） | 每条建议绑定 **证据 stepId**；**无证据则标为弱建议或不输出** | 是 |
| 8 | **Composer** 输出最终回答 | **StubAnswerComposerNode**（主模板不改动） | `RecommendationPlan` + `DiagnosisPlan` + 必要 AnswerPlan | 用户可读中文终稿 | 不出现未提供数字；三条建议可核对 | 否（Compose 失败则整轮 FAILED） |

### 6.2 说明

- **Agent / Tool** 名称实现阶段与现有 `BusinessAgentNames`、`AiBusinessToolIds` 对齐即可。  
- **验收**强调：**数字只来自 Tool / Builder**，与现有 Harness 硬约束一致。  
- **降级**：2–5 任一步 DEGRADED 时，步骤 6 应在 `DiagnosisPlan.debug` 或等价字段中**声明缺口**，步骤 7 **不得**编造掩盖缺口。

---

## 7. v1 边界（明确不做）

| 项 | v1 |
|----|-----|
| 数据访问 | **仅读** |
| 写操作 | **不做**（不写库、不发指令） |
| 规划方式 | **不做**自由 LLM 动态规划；仅 **预注册模板 + 参数绑定** |
| Human-in-the-loop | **不做**真实人工流程执行；仅 **接口占位**（§8） |
| 通知店长 / 推送 | **不做** |
| 调价 / 退款 / 提交订单 / 删除数据 | **不做** |

---

## 8. Human-in-the-loop — 仅设计接口

以下为 **契约占位**，v1 **不执行**真实审批动作、不发通知。

### 8.1 `ProposedAction`

| 字段（概念） | 说明 |
|--------------|------|
| `actionType` | 枚举：`PRICE_CHANGE`、`REFUND`、`ORDER_SUBMIT`、`NOTIFY_MANAGER`、… |
| `payload` | 结构化参数（仅展示/审计） |
| `sourceStepId` | 来源步骤 |
| `riskLevel` | 可选 |

### 8.2 `ApprovalRequired`

| 字段（概念） | 说明 |
|--------------|------|
| `proposalId` | 唯一标识 |
| `summary` | 给人看的摘要 |
| `requiredRole` | 可选 |

### 8.3 `ApprovalStatus`

`PENDING` / `APPROVED` / `REJECTED` / `NOT_APPLICABLE`（v1 恒可为 `NOT_APPLICABLE`）

### 8.4 `ExecutionAudit`

| 字段（概念） | 说明 |
|--------------|------|
| `runId` | 关联 AI Run |
| `planId` | 关联计划 |
| `events` | 只读事件序列（步骤开始/结束、降级、澄清占位） |

---

## 9. Replay 应覆盖的字段

以下为 **GRAPH_RUN / PlannerExecutor** 相关 **最小推荐集合**（可与现有 `AiMultiStoreHarnessTrace` 等对齐扩展）。

| 字段路径 | 说明 |
|----------|------|
| `plan.steps` | 每步 `stepId` / `order` / `kind` / `targetAgent` / `targetToolIds` |
| `step.status` | 每步最终状态 §4 |
| `usedAgents` | 每步或汇总：实际 Agent |
| `usedTools` | 每步或汇总：实际 Tool |
| `degradedSteps` | `stepId` 列表 |
| `finalAnswerPlan` | 或 `RecommendationPlan` + 汇总 Diagnosis 句柄（与 Composer 输入一致） |
| `plannerExecutorTrace` | 完整 `PlannerExecutorTrace` 快照 |

**比较断言建议**：对 `SUCCESS`/`DEGRADED` 路径校验 **步骤数、每步状态、关键 Tool 是否被调用**；对 `finalAnswerPlan` 做结构化 diff 而非全文字符串。

---

## 10. 阶段性落地路线

| 阶段 | 内容 |
|------|------|
| **第一步** | **仅设计文档**（本文档） |
| **第二步** | 新增 **DTO / skeleton**（`PlannerExecutionPlan`、`PlannerStep`、`PlannerStepResult`、`PlannerExecutorTrace` 等空实现 / mapper） |
| **第三步** | 新增 **最小 Graph-backed case**（单个模板、2–3 步只读），不触生产默认路径 |
| **第四步** | **接 Replay**：填充 §9 字段；与现有 `GRAPH_RUN` 对齐 |
| **第五步** | 评估 **是否进入生产主链路**（Feature flag、灰度、与 ROUTED/MULTI_AGENT 共存策略） |

---

## 11. 阶段 C-2：已落地 skeleton（Java 包）

**包路径**：`com.nongxinle.ai.planner`（`src/main/java/com/nongxinle/ai/planner/`）

| 类 | 说明 |
|----|------|
| `PlannerStepStatus` | 步骤状态枚举（PENDING / RUNNING / SUCCESS / FAILED / SKIPPED / DEGRADED） |
| `PlannerFailureStrategy` | 失败策略枚举（FAIL_FAST / CONTINUE_WITH_DEGRADED / ASK_CLARIFICATION） |
| `PlannerStep` | 单步定义：`stepId`、`stepName`、`order`、`targetAgent`、`targetTool`、`inputSummary`、`expectedOutput`、`acceptanceCriteria`、可选步级 `failureStrategy`；**测试期 mock**：`mockExecutionStatus`（`PlannerStepMockExecutionStatus`）、`mockDegradedReason`、`mockErrorMessage` |
| `PlannerStepMockExecutionStatus` | 测试期 mock：`SUCCESS` / `SKIPPED` / `DEGRADED` / `FAILED`（由 `MockPlannerStepExecutor` 与 `PlannerFailureStrategy` 合成 `PlannerStepExecutionResponse` → `PlannerStepResult`） |
| `PlannerExecutionPlan` | 计划 DTO：`planId`、`planType`、`steps`、`failureStrategy`、`resolvedContextRef`、`finalAnswerPlanType`（抄入 trace） |
| `PlannerStepResult` | 单步结果：`status`、`errorMessage`、`degradedReason`、`usedAgents`、`usedTools` |
| `PlannerExecutorTrace` | Run 级 trace：`plan`、`stepResults`、`degradedSteps`、汇总 `usedAgents` / `usedTools`、`finalAnswerPlanType`、`appliedFailureStrategy`、`overallStatus`、`clarificationRequested` |
| `PlannerExecutorResult` | 执行出口：`trace`、`ok` |
| `PlannerExecutor` | **编排 + trace 汇总**；**不**直接依赖 Domain Agent。按 `order` 遍历；C-5：`PlannerExecutorExecutionMode#MOCK`（默认）每步委托 `MockPlannerStepExecutor` 读 `mock*` 字段；`ADAPTER` 委托注入的 `PlannerStepExecutor`。failure / degraded / skip 聚合规则同 C-4。 |
| `PlannerExecutorExecutionMode` | `MOCK` / `ADAPTER`（C-5） |
| `PlannerStepExecutionRequest` / `PlannerStepExecutionResponse` | 单步适配边界 DTO（C-5） |
| `PlannerStepExecutor` / `MockPlannerStepExecutor` | 单步执行端口与 mock 唯一实现（C-5） |
| `PlannerAdapterToolKeys` | 后续真实路由用键名常量，当前无调用（C-5） |

**单测**（不接 Graph）：`src/test/java/com/nongxinle/ai/planner/PlannerExecutorSkeletonTest.java`

**约束**：C-2 仅 DTO + mock Executor；生产主链路、Replay 期望、Resolver / Master / Composer 均未接入。

---

## 12. 阶段 C-3：Graph-backed mock case（Harness 调试摘要）

**四条域 Hydrated RealBridge（C-29 止）**：下列 caseId 均在 **curl / Harness Replay** 下曾跑通 **真实 Tool** + 诚实摘要 **`REAL_BRIDGE_HYDRATED_*_TOOL_OK`**（依环境 DB / 权限）：**`PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`**（**`revenue_query`**，STORE 单店）、**`PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`**（**`purchase_overview`**）、**`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`**（**`stock_reduce_query`**）、**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`**（**`dish_profit_analysis`**）。**均不经** Master 生产主 Graph；权威表见 **`dish-profit-planner-adapter-design.md` §7.9**。**C-30**：基于四域复用的 **组合型经营诊断 Composite Plan** — **仅设计**，见 **`business-diagnosis-composite-plan-design.md`** 与本文 **§27**。**C-31**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE`** — 六步 **全 MOCK** Harness 骨架（**`AiPlannerExecutorBusinessDiagnosisCompositeGraphCase`**），**`finalAnswerPlanType=BUSINESS_DIAGNOSIS_COMPOSITE`**；根 **`plannerCompositeHonesty=COMPOSITE_SKELETON_ONLY`**；**不接** RealBridge。**C-31.1**：前四步 **`targetTool`** = **`mock_*_hydrated_adapter`**，**`plannerExecutorTrace.usedTools`** / 逐步 **`stepResults[].usedTools`** **不**含 **`revenue_query` / `purchase_overview` / `stock_reduce_query` / `dish_profit_analysis`**（避免误读为真实 Tool 已执行）；上述生产 Tool id 仅作 **`inputSummary` / `acceptanceCriteria`** 文案中的未来接线说明。**C-32**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeRevenueGraphCase`** + **`CompositeBusinessDiagnosisRevenueHybridPlannerStepExecutor`**：**仅** **`step_revenue_hydrated`**（**`targetTool=revenue_query`**）经 Bean **`RevenuePlannerRealReadBridge`**；其余五步仍为 **`mock_*`**；根 **`plannerCompositeHonesty=COMPOSITE_REVENUE_REAL_ONLY`**；Replay 推断 **`PLANNER_EXECUTOR_REVENUE_ADAPTER`**（与 C-13 同系）；营收失败诚实 **`DEGRADED`**，**不假** SUCCESS；轮次 **`pass`** 当且仅当 **`overallStatus=SUCCESS`**。**C-33**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseGraphCase`** + **`CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor`**：**`step_revenue_hydrated`** / **`step_purchase_hydrated`**（**`revenue_query`** / **`purchase_overview`**）经双 Bean **`RevenuePlannerRealReadBridge`** + **`PurchasePlannerRealReadBridge`**；出库 / 菜品 / 诊断 / 建议仍为 **`mock_*`**；根 **`plannerCompositeHonesty=COMPOSITE_REVENUE_PURCHASE_REAL_ONLY`**；Replay 推断 **`PLANNER_EXECUTOR_REVENUE_ADAPTER`**；营收或采购失败诚实 **`DEGRADED`**，**不假** SUCCESS；轮次 **`pass`** 当且仅当 **`overallStatus=SUCCESS`**。**C-34**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_STOCK_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseStockGraphCase`** + **`CompositeBusinessDiagnosisRevenuePurchaseStockHybridPlannerStepExecutor`**：**`step_revenue_hydrated`** / **`step_purchase_hydrated`** / **`step_stock_reduce_hydrated`**（**`revenue_query`** / **`purchase_overview`** / **`stock_reduce_query`**）经三 Bean；菜品 / 诊断 / 建议 **`mock_*`**；根 **`plannerCompositeHonesty=COMPOSITE_REVENUE_PURCHASE_STOCK_REAL_ONLY`**；**`AiHarnessReplayService#resolveReplayMode`** 推断 **`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER`**（与 C-24 同系摘要）；任一前三域真实步失败诚实 **`DEGRADED`**，**不假** SUCCESS；轮次 **`pass`** 当且仅当 **`overallStatus=SUCCESS`**。**C-35**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase`** + **`CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor`**：四数据步（**`revenue_query`** / **`purchase_overview`** / **`stock_reduce_query`** / **`dish_profit_analysis`**）经四 Bean；**`step_diagnosis_compose`** / **`step_recommendation`** **`mock_*`**（**无** LLM / 无生产 Action）；根 **`plannerCompositeHonesty=COMPOSITE_ALL_DATA_REAL_DIAGNOSIS_MOCK`**；**`resolveReplayMode`** → **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER`**；任一四数据步失败诚实 **`DEGRADED`**；轮次 **`pass`** 当且仅当 **`overallStatus=SUCCESS`**。**C-36**：**[`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md)** — **`BusinessDiagnosisCompositeAnswerPlan`** **仅文档**（**`type=BUSINESS_DIAGNOSIS_COMPOSITE`**，四域 **`RevenueSummary`…`DishProfitSummary`**、**`diagnosisSignals`**、**`dataCoverage`**、**`degradedSteps`**、**`riskLevel`** 等）；**不接** LLM、**不**生成自然语言终稿；**C-37** 从 **`plannerExecutorTrace.stepResults`** + adapter **AnswerPlan** 映射，`step_diagnosis_compose` **先** **确定性**（**不**调 LLM），Composer **后续只读**本 DTO、**不**直接扫原始 **`toolResults`**。

**ReplayMode**：PlannerExecutor DB-free 短路在 `AiHarnessReplayService#replay` 入口对 **PlannerExecutor mock 系 caseId**（`AiHarnessBuiltinCases#isPlannerExecutorMockHarnessCase`，含 `…_PURCHASE_ADAPTER_*`（含 **REAL_BRIDGE**）、`…_REVENUE_ADAPTER_*`、**`…_STOCK_REDUCE_ADAPTER_*`（C-21：`CORE` / `FAKE_OK_CORE`，**C-22**：`…_REAL_BRIDGE_CORE`，**C-24**：`…_REAL_BRIDGE_HYDRATED_CORE`，**C-46**：`…_GROUP_HYDRATED_CORE`，**及 Composite C-34**：`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_STOCK_CORE`）**、**`…_DISH_PROFIT_ADAPTER_*`（C-26：`CORE` / `FAKE_OK_CORE`；**C-27**：`…_REAL_BRIDGE_CORE` 骨架不接 Tool；**C-29**：`…_REAL_BRIDGE_HYDRATED_CORE` 经 Bean **`DishProfitPlannerRealReadBridge`** → **`DishProfitQueryToolExecutor`** **已实装**；**C-47**：`…_GROUP_HYDRATED_CORE` **GROUP** 双店探测，摘要 **`…_DISH_PROFIT_GROUP_TOOL_*`**；**及 Composite C-35 / C-48**：`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE` 与 **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`）**、`PLANNER_EXECUTOR_MOCK_*`）调用 `AiHarnessReplayPlannerExecutorMock.replay(req, revenuePlannerRealReadBridge, purchasePlannerRealReadBridge, stockReducePlannerRealReadBridge, dishProfitPlannerRealReadBridge)`，**不建会话、不跑 Resolver、不跑生产 Graph**。mock 系默认推断 `PLANNER_EXECUTOR_MOCK`；采购 Adapter 系推断 `PLANNER_EXECUTOR_PURCHASE_ADAPTER`；营收 Adapter 系推断 `PLANNER_EXECUTOR_REVENUE_ADAPTER`；**出库/核销 Adapter 系（C-21/C-22/C-24/**C-46**；**含 C-34 Composite 三域真实**）推断 `PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER`**；**菜品毛利 Adapter 系（C-26/C-27/C-29/**C-47**；**含 C-35 / C-48 Composite 四数据域全真实 / GROUP 同构**）推断 `PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER`**。

**CaseId（C-17 采购 RealBridge 骨架）**：`PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE` — `AiPlannerExecutorPurchaseAdapterRealBridgeGraphCase` + `PurchasePlannerRealReadBridge`：默认计划**不**物化 `AiRunState`/`AiResolvedQueryContext`，首步诚实降级（如 `ADAPTER_NO_RUN_STATE:run_state_ref_not_hydrated`）；`plannerPurchaseAdapterHonesty=REAL_BRIDGE_HARNESS_INCOMPLETE_CONTEXT`。**C-17 不**调用 `PurchaseOverviewToolExecutor`/DB。

**CaseId（C-19 采购 Hydrated RealBridge，curl 已验收）**：`PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` — `AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase` + `PurchasePlannerRealReadBridge`：`buildPurchaseRequestContext` → `executePurchaseOverview` → `PurchaseAnswerPlanBuilder`；STORE **1/AAA**、`distributerId` 取环境真实 **`disId`**（文档占位 **2**）；**未**默认设置 `purchaseOverviewPath`。成功路径曾观测：**`overallStatus=SUCCESS`**、**`degradedSteps=[]`**、采购步 **SUCCESS**、**`usedTools` 含 `purchase_overview`**、**`plannerPurchaseAdapterHonesty=REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_OK`**。摘要降级：**`REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_DEGRADED`**；轮次 `pass` 当且仅当 `overallStatus=SUCCESS`。完整调用链、最小上下文、限制与 **StockReduce / DishProfit** 模板见 **`purchase-planner-adapter-design.md` §12** 与本文 **§24**。

**C-46（出库/核销-only GROUP，非 Composite）**：**`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE`** — **`AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase`**：**`scopeType=GROUP`**、可见门店根 **1+3**、**`groupStockReduceQuery=true`**（与 C-24 **`false`** 对照，见 **`StockReduceQueryToolExecutor#buildHarnessToolArgs`**）、**`StockReducePlannerRealReadBridge`** → **`stock_reduce_query`**；诚实 **`REAL_BRIDGE_HYDRATED_STOCK_REDUCE_GROUP_TOOL_OK` / `…_DEGRADED`**；**`resolveReplayMode`** 与 C-24 同属 **`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER`**。详见 **`business-diagnosis-composite-group-design.md` §13** 与本节 **§25**（C-46 段）。

**CaseId（C-21 出库/核销 Adapter 骨架）**：`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_CORE` — `AiPlannerExecutorStockReduceAdapterGraphCase`：第 1 步 `StockReducePlannerAgentAdapter`（Harness **无** `StockReducePlannerReadBridge`）→ `DEGRADED`，`degradedReason` 含 `ADAPTER_NO_REAL_CONTEXT`；第 2 步建议 mock → `SUCCESS`。整轮 `overallStatus=DEGRADED`，`degradedSteps` 含 `step_stock_reduce_adapter`；`plannerExecutorTrace.usedAgents`/`usedTools` **不含**出库步真实 usage（降级不报伪 usage）。Replay 摘要根级 **`plannerStockReduceAdapterHonesty=ADAPTER_NO_REAL_CONTEXT`**（诚实字段与采购对称）。

**CaseId（C-21 出库/核销 Fake ReadBridge）**：`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_FAKE_OK_CORE` — 计划注入完整 `StockReducePlannerReadRequest`（**STORE** 单店）+ `FakeStockReducePlannerReadBridge`；出库步 `SUCCESS`，整轮 `SUCCESS`，`degradedSteps=[]`；trace 中 `usedAgents`/`usedTools` 各含 **`stock_reduce_query`**（`BusinessAgentNames` 与 `AiBusinessToolIds` 与此同值）；**`plannerStockReduceAdapterHonesty=FAKE_READ_BRIDGE_OK`**（**非**真实 `StockReduceQueryToolExecutor`/DB）。

**CaseId（C-26 菜品毛利 Adapter 骨架）**：`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_CORE` — `AiPlannerExecutorDishProfitAdapterGraphCase`：第 1 步 `DishProfitPlannerAgentAdapter`（Harness **无** `DishProfitPlannerReadBridge`）→ `DEGRADED`，`degradedReason` 含 `ADAPTER_NO_REAL_CONTEXT`；第 2 步建议 mock → `SUCCESS`。整轮 `overallStatus=DEGRADED`，`degradedSteps` 含 **`step_dish_profit_adapter`**；`plannerExecutorTrace.usedAgents`/`usedTools` **不含**菜品毛利步真实 usage。**`plannerDishProfitAdapterHonesty=ADAPTER_NO_REAL_CONTEXT`**。

**CaseId（C-26 菜品毛利 Fake ReadBridge）**：`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE` — 计划注入完整 `DishProfitPlannerReadRequest`（**STORE** 单店）+ `FakeDishProfitPlannerReadBridge`；**`step_dish_profit_adapter`** → `SUCCESS`，整轮 `SUCCESS`，`degradedSteps=[]`；trace 中 `usedAgents`/`usedTools` 各含 **`dish_profit_analysis`**；**`plannerDishProfitAdapterHonesty=FAKE_READ_BRIDGE_OK`**（**非**真实 `DishProfitQueryToolExecutor`/DB；Fake 中 **`salesAmount`** 对齐标价收入汇总语义，**不**将份数排行伪装为销售额）。

**CaseId（C-27 菜品毛利 RealBridge 骨架）**：`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE` — `AiPlannerExecutorDishProfitAdapterRealBridgeGraphCase` + `DishProfitPlannerRealReadBridge`（Harness `new`）：计划含 `dishProfitExecutionContext` + `dishProfitReadRequest`，默认**不**物化 `AiRunState`/`AiResolvedQueryContext`，第 1 步（**`step_dish_profit_adapter_real`**）诚实降级（如 `ADAPTER_NO_RUN_STATE:run_state_ref_not_hydrated`）；**不**调用 `DishProfitQueryToolExecutor`；**`plannerDishProfitAdapterHonesty=REAL_BRIDGE_HARNESS_INCOMPLETE_CONTEXT`**（与出库 RealBridge 骨架摘要对称）。

**CaseId（C-28 设计 + C-29 菜品毛利 Hydrated RealBridge，curl 已验收）**：`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` — **`AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase`**；权威字段见 **`dish-profit-planner-adapter-design.md` §7**、§7.0、§7.4.1、§7.5、§7.7。物化最小 **`AiRunState` + `AiResolvedQueryContext`（STORE 单店 AAA，`departmentId=1`，**`dishProfitPath=true`**，`distributerId=2` 占位）+ `DishProfitPlannerExecutionContext`**；**`AiHarnessReplayService`** 注入 Bean **`DishProfitPlannerRealReadBridge`**；经 **`PlannerExecutor` → `DishProfitPlannerAgentAdapter` → `DishProfitPlannerRealReadBridge`** 走 **`buildDishProfitRequestContext` → `executeDishProfitAnalysis` → `DishProfitAnalysisTool` → `attachForAgentEnvelope(state, false)`**，复用真实 **`dish_profit_analysis`**。**curl 曾观测**：**`overallStatus=SUCCESS`**、**`degradedSteps=[]`**、**`step_dish_profit_adapter_hydrated`** **SUCCESS**、**`usedTools` 含 `dish_profit_analysis`**、**`plannerDishProfitAdapterHonesty=REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_OK`**。否则诚实 **`DEGRADED`** / **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_DEGRADED`**（**不**假 SUCCESS）。**当前限制**（STORE 基准；GROUP 见 **C-47**）见 **`dish-profit-planner-adapter-design.md` §7.7**。

**CaseId（C-47 菜品毛利 GROUP Hydrated，非 Composite）**：**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE`** — **`AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase`**：**`scopeType=GROUP`**、可见门店根 **1+3**、**`AiResolvedDataScope.fromOrgScope`**、**`AiRunState.departmentId=null`**、**`distributerId=2`**、**`dishProfitPath=true`**、**`aiUserContext=GROUP_MANAGER`**（**`shouldRouteGroupWideBusinessOverview`** 依赖非空 user，见 **`dish-profit-planner-adapter-design.md` §7.10**）；**`DishProfitPlannerRealReadBridge`** Bean → **`dish_profit_analysis`**；诚实 **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_GROUP_TOOL_OK` / `…_GROUP_TOOL_DEGRADED`**；**`resolveReplayMode`** 仍为 **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER`**。**不接** Composite / LLM / Master。详表 **`business-diagnosis-composite-group-design.md` §14**。

**CaseId（C-22 出库/核销 RealBridge 骨架）**：`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_CORE` — `AiPlannerExecutorStockReduceAdapterRealBridgeGraphCase` + `StockReducePlannerRealReadBridge`（Harness `new`）：计划含 `stockReduceExecutionContext` + `stockReduceReadRequest`，默认**不**物化 `AiRunState`/`AiResolvedQueryContext`，首步诚实降级（如 `ADAPTER_NO_RUN_STATE:run_state_ref_not_hydrated`）；**不**调用 `StockReduceQueryToolExecutor`；**`plannerStockReduceAdapterHonesty=REAL_BRIDGE_HARNESS_INCOMPLETE_CONTEXT`**（与采购 RealBridge 骨架摘要对称）。

**CaseId（C-24 出库/核销 Hydrated RealBridge，curl 已验收）**：`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` — `AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase`；经 **`AiHarnessReplayService`** 注入 **`StockReducePlannerRealReadBridge`** Bean；走 **`buildStockReduceRequestContext` → `executeStockReduceQuery` → `StockReduceQueryTool` → `StockReduceAnswerPlanBuilder#attachIfApplicable`**。**STORE** 单店、**`departmentId` / 可见门店根 = 1**（**AAA**）、**`distributerId`** 文档占位 **2**（环境可校准）。成功路径已观测：**`overallStatus=SUCCESS`**、**`degradedSteps=[]`**、出库步（`step_stock_reduce_adapter_hydrated`）**`SUCCESS`**、**`usedTools` 含 `stock_reduce_query`**、**`plannerStockReduceAdapterHonesty=REAL_BRIDGE_HYDRATED_STOCK_REDUCE_TOOL_OK`**；摘要降级：**`REAL_BRIDGE_HYDRATED_STOCK_REDUCE_TOOL_DEGRADED`**；轮次 `pass` 当且仅当 **`overallStatus=SUCCESS`**。完整调用链、最小必填字段、**当前限制**与 **DishProfit** 后续模板见 **`docs/ai/stock-reduce-planner-adapter-design.md` §7.3.0–§7.4** 与 **§9**。

**CaseId（C-16 采购 Adapter 骨架）**：`PLANNER_EXECUTOR_PURCHASE_ADAPTER_CORE` — `AiPlannerExecutorPurchaseAdapterGraphCase`：第 1 步 `PurchasePlannerAgentAdapter`（Harness **无** `PurchasePlannerReadBridge`）→ `DEGRADED`，`degradedReason` 含 `ADAPTER_NO_REAL_CONTEXT`；第 2 步建议 mock → `SUCCESS`。整轮 `overallStatus=DEGRADED`，`degradedSteps` 含 `step_purchase_adapter`，`plannerExecutorTrace.usedAgents`/`usedTools` **不含**采购（降级步不报伪 usage）。Replay 摘要根级 `plannerPurchaseAdapterHonesty=ADAPTER_NO_REAL_CONTEXT`。

**CaseId（C-16 采购 Fake ReadBridge）**：`PLANNER_EXECUTOR_PURCHASE_ADAPTER_FAKE_OK_CORE` — 计划注入完整 `PurchasePlannerReadRequest` + `FakePurchasePlannerReadBridge`；采购步 `SUCCESS`，整轮 `SUCCESS`，`degradedSteps=[]`；trace 中 `usedAgents`/`usedTools` 含 `purchase_overview`；`plannerPurchaseAdapterHonesty=FAKE_READ_BRIDGE_OK`（**非**真实 `PurchaseOverviewToolExecutor`/DB）。

**CaseId（C-7 营收 Adapter 半真实占位）**：`PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE` — `AiPlannerExecutorRevenueAdapterGraphCase#buildPlan`：第 1 步 `PlannerExecutorExecutionMode#ADAPTER` + `RevenuePlannerAgentAdapter`（Harness 未注入 `RevenuePlannerReadBridge` → 步结果为 `DEGRADED`，`degradedReason` 含 `ADAPTER_NO_REAL_CONTEXT`）；第 2 步 `RecommendationPlannerMockAgentAdapter` → `MockPlannerStepExecutor`（mock SUCCESS）。整轮 `CONTINUE_WITH_DEGRADED`，`overallStatus=DEGRADED`，`ok=true`。Replay 摘要根级额外含 `plannerRevenueAdapterHonesty` / `plannerRevenueAdapterNote`。详见 §16。

**CaseId（C-9 Fake ReadBridge SUCCESS 闭环）**：`PLANNER_EXECUTOR_REVENUE_ADAPTER_FAKE_OK_CORE` — 计划注入完整 `RevenuePlannerReadRequest` + `FakeRevenuePlannerReadBridge`；营收步 `SUCCESS`，`overallStatus=SUCCESS`，`degradedSteps=[]`；`plannerRevenueAdapterHonesty=FAKE_READ_BRIDGE_OK`（非真实 SQL/Tool）。详见 §18。

**CaseId（C-12 Real ReadBridge + `revenue_query`）**：`PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_CORE` — 注册 `RevenuePlannerAgentAdapter(RevenuePlannerRealReadBridge)`；默认计划不 Hydrate `AiRunState`/`AiResolvedQueryContext`，诚实降级；`plannerRevenueAdapterHonesty=REAL_BRIDGE_HARNESS_INCOMPLETE_CONTEXT`。详见 §21。

**C-44（营收-only，非 Composite）**：**`PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE`** — **`AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase`**：**`scopeType=GROUP`**、可见门店根 **1（AAA）** 与 **3（汀兰餐厅）**、`dataScope=AiResolvedDataScope.fromOrgScope`、**`RevenuePlannerRealReadBridge`** → **`revenue_query`**；诚实 **`REAL_BRIDGE_HYDRATED_REVENUE_GROUP_TOOL_OK` / `…_DEGRADED`**；**`resolveReplayMode`** 与 C-13 同属 **`PLANNER_EXECUTOR_REVENUE_ADAPTER`**。详见 **`business-diagnosis-composite-group-design.md` §11** 与本文 **§22.13**。

**CaseId（C-13 Hydrated RealBridge，C-14 收口）**：`PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` — 与 C-12 **共用** `RevenuePlannerRealReadBridge`；在计划中物化最小 `AiRunState` + `AiResolvedQueryContext`，使 Bridge 能走完整 `resolveRevenueToolRequest` → `executeRevenueQuery` → `RevenueQueryTool`（**仍不经** Master / 生产 Graph）。**curl Harness 已跑通整轮 SUCCESS**（STORE 单店、`departmentId`/门店根 **1 / AAA**，摘要 `plannerRevenueAdapterHonesty=REAL_BRIDGE_HYDRATED_REVENUE_TOOL_OK`）；实测、最小上下文、限制与多域模板见 **§22.8–22.11**。

**CaseId（核心，6 步全 SUCCESS）**：`PLANNER_EXECUTOR_MOCK_CORE`

**CaseId（失败降级）**：`PLANNER_EXECUTOR_MOCK_DEGRADED_CORE` — `AiPlannerExecutorMockGraphCase#buildDegradedPlan`：采购步 `mockExecutionStatus=FAILED`（非 DEGRADED），计划级 `failureStrategy=CONTINUE_WITH_DEGRADED`；执行后该步 `stepResult.status=DEGRADED`，`degradedReason` 来自 `mockErrorMessage`（或 `mockDegradedReason` 若设置）；`degradedSteps` 含 `step_purchase_mtd`，`overallStatus=DEGRADED`，`PlannerExecutorResult.ok=true`。

**CaseId（C-31 + C-31.1 经营诊断 Composite MOCK 骨架）**：`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE` — `AiPlannerExecutorBusinessDiagnosisCompositeGraphCase#buildPlan`：六步（`step_revenue_hydrated` … `step_recommendation`）全 MOCK SUCCESS；前四步 **`targetTool`** = **`mock_revenue_hydrated_adapter`** … **`mock_dish_profit_hydrated_adapter`**（**C-31.1**，trace **不** echo 生产 Tool id）；`finalAnswerPlanType=BUSINESS_DIAGNOSIS_COMPOSITE`；根摘要 `plannerCompositeHonesty` / `plannerCompositeNote`。**不接** Hydrated RealBridge。

**CaseId（C-32 经营诊断 Composite + 营收 Hydrated 单域真实）**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeRevenueGraphCase#buildPlan`**：计划级 **`failureStrategy=CONTINUE_WITH_DEGRADED`**，**`finalAnswerPlanType=BUSINESS_DIAGNOSIS_COMPOSITE`**；**`step_revenue_hydrated`** **`targetTool=revenue_query`**，经 **`PlannerExecutor(ADAPTER, CompositeBusinessDiagnosisRevenueHybridPlannerStepExecutor)`** 调 **`RevenuePlannerAgentAdapter(RevenuePlannerRealReadBridge)`**（与 **C-13** 同 Bridge）；**`step_purchase_hydrated` / `step_stock_reduce_hydrated` / `step_dish_profit_hydrated` / `step_diagnosis_compose` / `step_recommendation`** 仍为 **`mock_purchase_hydrated_adapter`** … **`mock_build_recommendation_plan`**（**`MockPlannerStepExecutor`**）。根 **`plannerCompositeHonesty=COMPOSITE_REVENUE_REAL_ONLY`**、**`plannerCompositeNote`**：`revenue real hydrated adapter invoked; purchase/stock/dish/diagnosis/recommendation remain mock`；**`harnessReplayMode`** 与 C-13 对齐为 **`PLANNER_EXECUTOR_REVENUE_ADAPTER`**。trace：**营收步 `usedTools` 含 `revenue_query`**；其余数据步仍为 **`mock_*`**。

**CaseId（C-33 经营诊断 Composite + 营收/采购 Hydrated 双域真实）**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseGraphCase#buildPlan`**：计划级 **`failureStrategy=CONTINUE_WITH_DEGRADED`**，**`finalAnswerPlanType=BUSINESS_DIAGNOSIS_COMPOSITE`**；**`step_revenue_hydrated`**、**`step_purchase_hydrated`** 分别 **`targetTool=revenue_query`**、**`purchase_overview`**，经 **`PlannerExecutor(ADAPTER, CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor)`** + **`PlannerAgentAdapterRegistry(Revenue…, Purchase…)`**（与 **C-13 / C-19** 同构 Hydrated 上下文）；**`step_stock_reduce_hydrated` / `step_dish_profit_hydrated` / `step_diagnosis_compose` / `step_recommendation`** 仍为 **`mock_stock_reduce_hydrated_adapter`** … **`mock_build_recommendation_plan`**。根 **`plannerCompositeHonesty=COMPOSITE_REVENUE_PURCHASE_REAL_ONLY`**、**`plannerCompositeNote`**：`revenue and purchase real hydrated adapters invoked; stock/dish/diagnosis/recommendation remain mock`；**`harnessReplayMode`**：**`PLANNER_EXECUTOR_REVENUE_ADAPTER`**；**须** **`RevenuePlannerRealReadBridge`** + **`PurchasePlannerRealReadBridge`**。trace：**营收 / 采购步 `usedTools`** 含 **`revenue_query`**、**`purchase_overview`**；后四步 **`mock_*`**。

**CaseId（C-34 经营诊断 Composite + 营收/采购/出库 Hydrated 三域真实）**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_STOCK_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseStockGraphCase#buildPlan`**：计划级 **`failureStrategy=CONTINUE_WITH_DEGRADED`**，**`finalAnswerPlanType=BUSINESS_DIAGNOSIS_COMPOSITE`**；**`step_revenue_hydrated`**、**`step_purchase_hydrated`**、**`step_stock_reduce_hydrated`** 分别 **`targetTool=revenue_query`**、**`purchase_overview`**、**`stock_reduce_query`**，经 **`PlannerExecutor(ADAPTER, CompositeBusinessDiagnosisRevenuePurchaseStockHybridPlannerStepExecutor)`** + **`PlannerAgentAdapterRegistry(Revenue…, Purchase…, StockReduce…)`**（三域与 **C-13 / C-19 / C-24** 同构 Hydrated 上下文）；**`step_dish_profit_hydrated` / `step_diagnosis_compose` / `step_recommendation`** 仍为 **`mock_dish_profit_hydrated_adapter`**、**`mock_diagnosis_compose`**、**`mock_build_recommendation_plan`**。根 **`plannerCompositeHonesty=COMPOSITE_REVENUE_PURCHASE_STOCK_REAL_ONLY`**、**`plannerCompositeNote`**：`revenue, purchase and stock_reduce real hydrated adapters invoked; dish/diagnosis/recommendation remain mock`；**`harnessReplayMode`**：**`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER`**；**须** **`RevenuePlannerRealReadBridge`** + **`PurchasePlannerRealReadBridge`** + **`StockReducePlannerRealReadBridge`**。trace：**营收 / 采购 / 出库步 `usedTools`** 含 **`revenue_query`**、**`purchase_overview`**、**`stock_reduce_query`**；后三步 **`mock_*`**。

**CaseId（C-35 经营诊断 Composite + 四数据域 Hydrated 全真实 + 诊断/建议 mock）**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase#buildPlan`**：计划级 **`failureStrategy=CONTINUE_WITH_DEGRADED`**，**`finalAnswerPlanType=BUSINESS_DIAGNOSIS_COMPOSITE`**；**`step_revenue_hydrated`** … **`step_dish_profit_hydrated`** 分别为 **`revenue_query`**、**`purchase_overview`**、**`stock_reduce_query`**、**`dish_profit_analysis`**，经 **`PlannerExecutor(ADAPTER, CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor)`** + **`PlannerAgentAdapterRegistry(Revenue…, Purchase…, StockReduce…, DishProfit…)`**；**`step_diagnosis_compose` / `step_recommendation`**：`targetTool` 仍为 **`mock_diagnosis_compose`** / **`mock_build_recommendation_plan`**（**诚实口径**；**非**生产 Tool）。**`step_diagnosis_compose`** 由 **`CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor`** 内 **`BusinessDiagnosisCompositeAnswerPlanBuilder`**（演进 **C-37～C-49**；**`BUILDER_VERSION=C-49`**：**C-49** 为文档/版本标记收口；**`debug.mappingNotes`** 仍 **`phase=C-38.2_zero_vs_missing`** / **`signalsPhase=C-39_minimal_deterministic`** / **`summaryPhase=C-40_deterministic_zh`**；四域 summary 来自 AnswerPlan + **`toolResults`**；**C-39** **`diagnosisSignals`**；**C-40** 确定性中文 **`summaryText`**）产出结构化 Plan（**不**经 **`MockPlannerStepExecutor.INSTANCE`**）；**`step_recommendation`** 仍 **`MockPlannerStepExecutor`**。根 **`plannerCompositeHonesty=COMPOSITE_ALL_DATA_REAL_DIAGNOSIS_MOCK`**、**`plannerCompositeNote`**：`revenue, purchase, stock_reduce and dish_profit real hydrated adapters invoked; diagnosis/recommendation remain mock`；**`harnessReplayMode`**：**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER`**；**须** 四 Bridge Bean。trace：前四步 **`usedTools`** 为四生产 Tool id；**`step_diagnosis_compose`** **`usedTools`** 仅 **`mock_diagnosis_compose`**；**`step_recommendation`** **`mock_build_recommendation_plan`**。**C-37 Harness 根摘要**（**`AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase#toHarnessSummary`**，可选）：**`businessDiagnosisAnswerPlanType`**、**`businessDiagnosisRiskLevel`**、**`businessDiagnosisDataCoverage`**、**`businessDiagnosisCompositeAnswerPlan`**、**`businessDiagnosisSummaryText`**、**`businessDiagnosisSuggestedNextQuestions`**（**C-38 / C-38.2**：**`summary`** 与 **`debug.mappingNotes`**；**C-39**：**`diagnosisSignals` / `riskLevel` / `keyFindings`** **§8.8**；**C-40**：**`summaryText`** **§8.9**）；**`PlannerStepResult`** 携带 **`businessDiagnosisCompositeAnswerPlan`**（嵌套 trace 的 **`stepResults` 列表仍不展开该对象**，避免膨胀）。

**CaseId（C-48 经营诊断 Composite + GROUP 四域 Hydrated 全真实 + 诊断确定性 + 建议 mock）**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase#buildPlan`**：与 **C-35** **同六步、同 Executor 族**（**`CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor`**）；**`scopeType=GROUP`**、双店 **AAA（`id=1`）/ 汀兰餐厅（`id=3`）** 由各 **`AiPlannerExecutor*AdapterGroupHydratedGraphCase`** **物化**（与 **C-44～C-47** 单域 **同构**；**不**复制 STORE Composite **`scopeLabel`**）。根 **`plannerCompositeHonesty=COMPOSITE_GROUP_ALL_DATA_REAL_DIAGNOSIS_DETERMINISTIC`**、**`plannerCompositeNote`**：**`group composite; four group hydrated adapters invoked; diagnosis deterministic; recommendation mock`**；**`harnessReplayMode`**：**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER`**（与 **ALL_REAL** 同 **`resolveReplayMode`** 分组）。**Harness 根摘要**另含 **`visibleStoreRootDepartmentIds`**（默认 **`[1,3]`** 或由 **`BusinessScopeResolutionSupport#extractVisibleStoreRootDepartmentIds`** 覆盖）。**`summaryText`**：**GROUP 口径**（**`BusinessDiagnosisCompositeAnswerPlanBuilder`** 对 **`scopeType=GROUP`** 保守措辞；见 **`business-diagnosis-composite-group-design.md` §7**、**`business-diagnosis-answer-plan-design.md` §8.11**）。

**示例用户原话**（仅作文档/单测输入；**不**用原文 contains/regex 路由）：

`帮我分析 AAA 这个月成本为什么偏高，并给我三条改进建议`

**固定计划**（`AiPlannerExecutorMockGraphCase#buildPlan`）：6 步顺序为 **revenue → purchase → stockReduce → dishProfit → diagnosis → recommendation**；每步 `mockExecutionStatus=SUCCESS`。`targetAgent` / `targetTool` 与现有 Harness 常量对齐（营收/采购/出库/毛利为 `BusinessAgentNames` + `AiBusinessToolIds`；诊断/建议为 `mock_*` 占位）。

**Mock 图节点**：`AiPlannerExecutorMockGraphNode#run(executor, harnessCaseId)` → `AiPlannerExecutorMockGraphCase#planForHarnessCase` → `PlannerExecutor#execute`。

**调试摘要**（`AiPlannerExecutorMockGraphCase#toHarnessSummary`）：根 Map 除 `plannerExecutorTrace` 外，为可读性增加与嵌套结构一致的浅表字段（便于对照，不改变语义）：

| 根字段 | 对应嵌套位置 |
|--------|----------------|
| `harnessMockGraphCaseId` | 请求的 Harness case（区分 CORE / DEGRADED） |
| `harnessPlanType` | `plannerExecutorTrace.plan.planType` |
| `harnessPlanFinalAnswerPlanType` | `plannerExecutorTrace.plan.finalAnswerPlanType`（与 trace 根级 `finalAnswerPlanType` 同源快照） |
| `harnessPlannerOverallStatus` | `plannerExecutorTrace.overallStatus` |
| `harnessPlannerDegradedSteps` | `plannerExecutorTrace.degradedSteps` |
| `plannerCompositeHonesty` | **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE`**：**`COMPOSITE_SKELETON_ONLY`**；**C-32** **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE`**：**`COMPOSITE_REVENUE_REAL_ONLY`**；**C-33** **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE`**：**`COMPOSITE_REVENUE_PURCHASE_REAL_ONLY`**；**C-34** **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_STOCK_CORE`**：**`COMPOSITE_REVENUE_PURCHASE_STOCK_REAL_ONLY`**；**C-35** **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`**：**`COMPOSITE_ALL_DATA_REAL_DIAGNOSIS_MOCK`**；**C-48** **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`**：**`COMPOSITE_GROUP_ALL_DATA_REAL_DIAGNOSIS_DETERMINISTIC`** |
| `plannerCompositeNote` | **C-31**：**`skeleton only; real hydrated adapters not invoked`**；**C-32**：**`revenue real hydrated adapter invoked; purchase/stock/dish/diagnosis/recommendation remain mock`**；**C-33**：**`revenue and purchase real hydrated adapters invoked; stock/dish/diagnosis/recommendation remain mock`**；**C-34**：**`revenue, purchase and stock_reduce real hydrated adapters invoked; dish/diagnosis/recommendation remain mock`**；**C-35**：**`revenue, purchase, stock_reduce and dish_profit real hydrated adapters invoked; diagnosis/recommendation remain mock`**；**C-48**：**`group composite; four group hydrated adapters invoked; diagnosis deterministic; recommendation mock`** |

`plannerExecutorTrace` 其下仍含：

- `plan`（内含 `planId`、`planType`、`steps` …）
- 根级 **`steps`**（与 `plan.steps` 相同引用，便于 Harness 面板直接读）
- `stepResults`
- 汇总 `usedAgents`、`usedTools`、`degradedSteps`
- `overallStatus`、`finalAnswerPlanType`、`appliedFailureStrategy`

**单测**：`src/test/java/com/nongxinle/ai/harness/replay/AiPlannerExecutorMockGraphCaseTest.java`（含 `AiHarnessReplayPlannerExecutorMock` DB-free 路径）。

---

## 13. 阶段 C-4：PlannerExecutor mock 语义可控（按 step）

- **目的**：去掉「偶数 SUCCESS / 奇数 SKIPPED」全局启发式；测试与 Harness 通过 `PlannerStep.mockExecutionStatus`（及可选 `mockDegradedReason` / `mockErrorMessage`）指定每步 mock 结果。
- **命名**：三字段均以 `mock` 为前缀，明确仅为 **mock / Harness**，与生产执行输入区分；`mockExecutionStatus` = 本步期望的合成结果类别；`mockDegradedReason` / `mockErrorMessage` = 降级文案与失败文案来源（见 `PlannerExecutor`）。
- **`null` 默认**：仅 **`PlannerExecutor` 当前 mock 实现** 在 `mockExecutionStatus == null` 时按 SUCCESS 处理，属 Harness 缺省，**不**表示生产「未声明即成功」。
- **Trace**：`plannerExecutorTrace.degradedSteps` 为 **步骤结果为 DEGRADED** 的 `stepId` 列表（含 `mockExecutionStatus=DEGRADED` 的步骤，以及 `FAILED` 且策略为 `CONTINUE_WITH_DEGRADED` 而降级的步骤）。
- **Harness 摘要**：根级 `harnessMockGraphCaseId` 与 §12 表列浅表字段；完整结构仍在 `plannerExecutorTrace`。
- **约束**：仍不接真实 Agent / Tool / SQL；不触生产 Master / Resolver / Composer 主模板。

---

## 14. 阶段 C-5：Step 执行适配层（设计与骨架）

### 14.1 为什么加 adapter

- **隔离**：`PlannerExecutor` 只负责排序、失败策略短路、汇总 `PlannerExecutorTrace`，**不**直接依赖具体 Domain Agent / Tool / SQL，避免编排层与实现层硬耦合。
- **演进**：真实执行通过 `PlannerStepExecutor` 注入；Mock / Harness 与未来将加入的 `RealPlannerStepExecutor`（命名示意）共用同一编排入口。
- **测试**：`ADAPTER` + `MockPlannerStepExecutor` 可单独验证「请求 → 响应 → `PlannerStepResult`」管线，无需启动 Graph。

### 14.2 执行模式 `PlannerExecutorExecutionMode`

| 模式 | 行为 |
|------|------|
| `MOCK` | 每步调用 `MockPlannerStepExecutor`（与 C-4 语义一致：读 `mock*` 字段）。**默认**无参 `new PlannerExecutor()` 即此模式，保证现有 Harness / Replay 不变。 |
| `ADAPTER` | 每步调用**注入**的 `PlannerStepExecutor`；构造时必须传入非 null 执行器。v1 仍可注入 `MockPlannerStepExecutor.INSTANCE` 以走适配路径而不接真实 Agent。 |

### 14.3 类型一览（`com.nongxinle.ai.planner`）

| 类型 | 角色 |
|------|------|
| `PlannerStepExecutionRequest` | 单步入参：当前 `PlannerStep`、合并后的 `effectiveFailureStrategy`、可选 `planId` / `planType`；C-6：`resolvedQueryContextRef`、`answerPlanRef`（编排注入，非用户原文）。 |
| `PlannerStepExecutionResponse` | 单步出参：`status`、`errorMessage`、`degradedReason`、`usedAgents`、`usedTools`。 |
| `PlannerStepExecutor` | 函数式接口：`execute(Request) → Response`。 |
| `MockPlannerStepExecutor` | C-5 唯一实现：合成 mock 响应，**无**真实调用。 |
| `PlannerAdapterToolKeys` | **仅常量 + 文档目的**：后续真实适配器路由表键名（见下表），**当前无任何业务调用**。 |

### 14.4 目标 Tool / Agent 适配键（设计对照，C-5 不实装）

与 `PlannerStep.targetTool` / Agent 命名对齐的约定键（`PlannerAdapterToolKeys`）；线上 Tool 常量以 `AiBusinessToolIds` 为准，差异在表中注明。

| 键常量 | 说明 | 与现有 Tool/Agent 备注 |
|--------|------|-------------------------|
| `revenue_overview` | 营收专线 | Agent 侧常见 `revenue_overview`；Tool id 多为 `revenue_query`，适配层需显式映射 |
| `purchase_overview` | 采购概览 | 与 `purchase_overview` 对齐 |
| `stock_reduce_query` | 出库/核销 | 与 `stock_reduce_query` 对齐 |
| `dish_profit_analysis` | 菜品毛利 | 与 `dish_profit_analysis` 对齐 |
| `business_diagnosis_v1` | 诊断聚合 | 占位 Agent/Tool 名，接 Master/子图前维持字符串契约 |
| `recommendation_planner_v1` | 建议计划 | 同上 |

**后续接真实 Agent**：实现 `PlannerStepExecutor`（或分层 Facade），内部按 `targetTool` / `PlannerAdapterToolKeys` 分派到现有 `Business*Agent`、Tool Bean 或 Graph 节点；`PlannerExecutor` 仅 `new PlannerExecutor(PlannerExecutorExecutionMode.ADAPTER, realExecutor)`。**禁止**在 `PlannerExecutor` 内 `import` Domain Agent 实现类。

### 14.5 本轮约束

- **不接**真实 Agent / Tool；不修改 Master / Resolver / Composer / SQL / 前台。
- 不新增用户原文 contains/regex 路由。

---

## 15. 阶段 C-6：`PlannerAgentAdapter` 与 Registry（no-op 骨架）

### 15.1 目的

- 在 **不接**真实 Agent / Tool / SQL 的前提下，固化「一步 → 某个业务适配器」的**边界与查找规则**，便于后续把 Domain 能力挂到 `PlannerExecutorExecutionMode#ADAPTER` 上。
- `PlannerExecutor` **仍不** `import` 任何 DomainAgent；只通过 `PlannerStepExecutor`（推荐 `PlannerAgentAdapterStepExecutor`）→ `PlannerAgentAdapterRegistry` → `PlannerAgentAdapter`。

### 15.2 类型

| 类型 | 说明 |
|------|------|
| `PlannerAgentAdapterRequest` | Adapter 专用入参：`PlannerStep`、策略、计划元数据、`resolvedQueryContextRef`、`answerPlanRef`。**不得**将用户聊天原文作为主要字段。 |
| `PlannerAgentAdapter` | `supports(targetAgent, targetTool)` + `invoke(request)` → `PlannerStepExecutionResponse`。 |
| `PlannerAgentAdapterRegistry` | 持有有序 `List<PlannerAgentAdapter>`，**首个** `supports==true` 执行；否则返回 `FAILED`。 |
| `NoopPlannerAgentAdapter` | `supports` 恒 false；永不 `invoke`；占位 Bean。 |
| `PlannerAgentAdapterStepExecutor` | 实现 `PlannerStepExecutor`：`PlannerStepExecutionRequest` → `PlannerAgentAdapterRequest` → `registry.invoke(...)`。 |

### 15.3 `targetAgent` / `targetTool` 映射规则

- 注册表从 `PlannerStep` 取出 `targetAgent`、`targetTool`，经 **trim** 与空串→`null` 规范化后传入 `supports`。
- **匹配顺序**：构造 `PlannerAgentAdapterRegistry` 时列表顺序即优先级；建议细粒度专用 adapter 在前，泛化/兜底在后（未来）。
- **键源**：与计划模板一致（参见 §14.4 `PlannerAdapterToolKeys` 及 `BusinessAgentNames` / `AiBusinessToolIds` 对照）；实现类可用常量相等判断，**禁止**对用户原文做 contains/regex 作为路由依据。

### 15.4 找不到 adapter

- `PlannerAgentAdapterRegistry#invoke` 在无匹配时返回 **`PlannerStepExecutionResponse`，`status=FAILED`**，`errorMessage` 前缀 `planner_agent_adapter_not_registered:`。
- **`PlannerExecutor`** 在汇总前对 **`FAILED` + `CONTINUE_WITH_DEGRADED`** 做与 mock 一致的**吸收**：将步骤落成 `DEGRADED`，`degradedReason` 取自 `errorMessage`；`FAIL_FAST` / `ASK_CLARIFICATION` 下保持 `FAILED` 并触发原有短路逻辑。

### 15.5 输入 / 输出契约

- **输入**：真实 adapter **必须**依赖 `resolvedQueryContextRef`（及后续扩展的结构化句柄 / AnswerPlan 引用），由上游 Resolver / Graph 注入到 `PlannerStepExecutionRequest`；`PlannerExecutor` 已将 `PlannerExecutionPlan#resolvedContextRef` 抄入 `PlannerStepExecutionRequest#resolvedQueryContextRef`。不得单独依赖用户原文完成权限或意图。
- **输出**：**必须**为结构化 `PlannerStepExecutionResponse`（与 C-5 一致），由 `PlannerExecutor` 转为 `PlannerStepResult`。

### 15.6 本轮约束

- 不接真实 Agent / Tool / SQL；不修改 Master / Resolver / Composer；不新增用户原文路由。

---

## 16. 阶段 C-7：`RevenuePlannerAgentAdapter`（单域只读、Harness 独立 Replay）

### 16.1 目的

- 在 **`PlannerExecutorExecutionMode#ADAPTER`** 下接 **唯一**真实域适配方向：**`revenue_overview` / `revenue_query`**（与 `RevenuePlannerAgentAdapter` 常量一致）。
- **不**接入 `MasterBusinessAgent` 生产主链路、**不**改 Resolver / Composer / SQL / 前台；仅 **`caseId=PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE`** 走 `AiHarnessReplayPlannerExecutorMock` 固定计划。

### 16.2 输入边界（禁止重解析用户原文）

- Adapter **仅**接受 `PlannerAgentAdapterRequest`：`PlannerStep`、`resolvedQueryContextRef`、`answerPlanRef`、计划元数据、`effectiveFailureStrategy`（由 `PlannerAgentAdapterStepExecutor#fromPlannerStepExecution` 从 `PlannerStepExecutionRequest` 拷贝）。
- Harness 计划中 `resolvedContextRef` 为占位句柄（`HARNESS_REVENUE_ADAPTER_RESOLVED_CTX_REF`）；**不**声称本轮已通过 Resolver 得到真实 `AiResolvedQueryContext` 实例。

### 16.3 输出与诚实标记（半真实）

- **未**注入 `RevenuePlannerReadBridge` 时：返回 `PlannerStepStatus#DEGRADED`，`degradedReason` 前缀 **`ADAPTER_NO_REAL_CONTEXT`**（例如 `read_bridge_null`），**不**伪造查库成功、**不**输出伪造成交额。
- 后续在 `RevenuePlannerReadBridge#readRevenue`（入参 `RevenuePlannerReadRequest`）内对接 `RevenueQueryToolExecutor` 等（TODO），仍须仅依赖结构化上下文，**禁止**解析用户聊天原文。出参见 §17 `RevenuePlannerReadResponse`。

### 16.4 Replay 与 trace

- **CaseId**：`PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE`。
- **ReplayMode 推断**：`AiHarnessReplayMode#PLANNER_EXECUTOR_REVENUE_ADAPTER`（短路路径与 mock case 相同，仅摘要中 `harnessReplayMode` 区分）。
- **Trace**：`plannerExecutorTrace.stepResults[0]` 为营收步 `DEGRADED` + 上述 `degradedReason`；第 2 步 mock 建议为 `SUCCESS`；`overallStatus=DEGRADED`。
- **摘要根字段**：除 §12 浅表字段外，`plannerRevenueAdapterHonesty`（`ADAPTER_NO_REAL_CONTEXT`）、`plannerRevenueAdapterNote`（Harness 无 readBridge 说明）。

### 16.5 本轮约束

- **只**接一个真实 Adapter 方向（revenue）；第二步仍为 mock。
- 不改测试目录/SQL/前台/已有业务 Replay 期望（除非契约断裂必要项）。

---

## 17. 阶段 C-8：`RevenuePlannerReadRequest` / `RevenuePlannerReadResponse`（桥接边界）

### 17.1 目的

- 为真实营收只读调用固化 **入参 / 出参 DTO**，与 `AiResolvedQueryContext` 解耦（本包仅用值对象 + 句柄字符串）。
- **本轮**：仍不接 Tool / 查库；`RevenuePlannerReadBridge#readRevenue` 无生产实现。

### 17.2 `RevenuePlannerReadRequest`（禁止 `userMessage`）

| 字段 | 说明 |
|------|------|
| `resolvedQueryContextRef` | 解析上下文快照 id / hash（adapter 必填校验） |
| `timeStart` / `timeEnd` | 区间起止（`LocalDate`）；与 `timeLabel` 二选一满足时间充分条件 |
| `timeLabel` | 可选标签；**或**与成对 `timeStart`/`timeEnd` 一起声明窗口 |
| `scopeType` | 如 `STORE` / `GROUP` 等字符串，与现网 scope 对齐 |
| `visibleStores` | `List<RevenuePlannerVisibleStore>`（`departmentId` + 可选 `displayLabel`） |
| `queryDepartmentIds` | 部门 id 列表 |
| `targetStoreDepartmentId` | 单店焦点部门 id |
| `answerPlanRef` | 可选 AnswerPlan 句柄 |

注入路径：`PlannerExecutionPlan#revenueReadRequest` → `PlannerExecutor` 抄入各步 `PlannerStepExecutionRequest#revenueReadRequest` → `PlannerAgentAdapterRequest#revenueReadRequest`；并与顶层 `resolvedQueryContextRef` / `answerPlanRef` **合并**（嵌套缺省时由 plan 级句柄补齐）。

### 17.3 `RevenuePlannerReadResponse`

| 字段 | 说明 |
|------|------|
| `status` | `RevenuePlannerReadStatus`：`OK` / `DEGRADED` / `FAILED` |
| `revenueAmount` | 汇总金额（`BigDecimal`，可 null） |
| `storeRows` | `List<RevenuePlannerStoreRevenueRow>` 分门店行 |
| `timeLabel` / `scopeLabel` | 结构化展示标签 |
| `errorCode` / `errorMessage` | 降级或失败时的业务码与说明 |

### 17.4 `RevenuePlannerAgentAdapter` 行为

- **校验**（无用户原文、`contains`、`regex`、SQL）：
  - `readBridge == null` → `ADAPTER_NO_REAL_CONTEXT:read_bridge_null:…`
  - `resolvedQueryContextRef` 空 → `ADAPTER_NO_REAL_CONTEXT:resolved_query_context_ref_missing:…`
  - 时间不充分（既无 `timeStart`+`timeEnd` 又无 `timeLabel`）→ **`ADAPTER_MISSING_TIME`**
  - 范围不充分（`scopeType` 空，或无可解析部门/可见门店/目标店 id）→ **`ADAPTER_MISSING_SCOPE`**
- **调用**：`readBridge.readRevenue(RevenuePlannerReadRequest)`。
- **映射**：`OK` → `PlannerStepStatus.SUCCESS`（`usedAgents` / `usedTools` 营收常量）；`FAILED` → `FAILED`；`DEGRADED` → `DEGRADED`（`degradedReason` 来自 `errorCode`/`errorMessage`）。

### 17.5 本轮约束

- 不接生产 Graph / Master / Resolver / Composer；不改 SQL。
- Harness `PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE` 仍默认不注入 bridge；若仅注入 bridge 而无 `revenueReadRequest`，将在 `ADAPTER_MISSING_TIME` / `ADAPTER_MISSING_SCOPE` 降级（诚实缺失上下文）。

---

## 18. 阶段 C-9：`FakeRevenuePlannerReadBridge`（Harness SUCCESS 闭环）

### 18.1 目的

- 验证 `PlannerExecutionPlan#revenueReadRequest` 经 `PlannerExecutor` 传入 `RevenuePlannerAgentAdapter` → `RevenuePlannerReadBridge#readRevenue`，并得到 `RevenuePlannerReadResponse`（`OK`）。
- **非**真实 SQL / Tool / 库；摘要必须标明 **`plannerRevenueAdapterHonesty=FAKE_READ_BRIDGE_OK`**（常量在 `FakeRevenuePlannerReadBridge`）。

### 18.2 CaseId

- `PLANNER_EXECUTOR_REVENUE_ADAPTER_FAKE_OK_CORE`：`AiPlannerExecutorRevenueAdapterFakeOkGraphCase#buildPlan`，`AiHarnessReplayPlannerExecutorMock` 注册 `RevenuePlannerAgentAdapter(FakeRevenuePlannerReadBridge.instance())`。

### 18.3 预期 trace（Replay）

- 营收步 `SUCCESS`；建议 mock 步 `SUCCESS`。
- `plannerExecutorTrace.overallStatus=SUCCESS`，`degradedSteps=[]`。
- 汇总 `usedAgents` 含 `revenue_overview`，`usedTools` 含 `revenue_query`（另含建议 mock 步的 agent/tool）。

### 18.4 与 C-7 关系

- `PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE` **不变**：仍无 Bridge，诚实 `ADAPTER_NO_REAL_CONTEXT`。

---

## 19. 阶段 C-10：真实 `RevenuePlannerReadBridge` 前置梳理（只读，不接生产主链路）

> **目的**：厘清 **现有** `revenue_overview` / `revenue_query` 的真实读路径与 DTO 形态，为后续 **最薄** `RevenuePlannerRealReadBridge`（或等价命名）提供接法；**本轮不写 SQL、不接 DB 新链路、不接 Graph/Master/Resolver/Composer、不复制旧查询逻辑、不从 userMessage 取参**。

### 19.1 找到哪些 revenue 相关类 / 方法（主干）

| 层级 | 类 / 方法 | 作用 |
|------|-----------|------|
| Tool | `RevenueQueryTool`（`AiBusinessToolIds.REVENUE_QUERY` / `revenue_query`） | 唯一对 `GbAiDailyRevenueService` 做 **真实营收读** 的 Tool：`getStatsByDepartmentId`、多店聚合 `getGroupIncomeAggregateForDepartmentIds`、门店根展开等。 |
| Service（DB） | `GbAiDailyRevenueService` | 营收统计 DAO 封装；**Planner 不应直连**（避免与 Tool 语义分叉）。 |
| Executor | `RevenueQueryToolExecutor#executeRevenueQuery` | 构造 `ToolRequest`（`buildRevenueQueryToolArgs` + `resolvedQueryContext`）、`AiPermissionGuard`、执行 `ToolRegistry`，**结果写入** `AiRunState#getToolResults().put(REVENUE_QUERY, unwrappedMap)`。 |
| Graph 节点 | `BusinessToolExecutionNode` | `REVENUE_QUERY` 分支可调用同一 `executeRevenueQuery`（存在「Master 已跑过则跳过」等短路，与 Planner 无关）。 |
| Agent | `RevenueAgent#execute` | `BusinessToolExecutionRequestResolver#resolveRevenueToolRequest(state, rq)` → `RevenueToolRequestResolution`，再调 `executeRevenueQuery`；成功后可 `DailyRevenueAnswerPlanBuilder.attachIfApplicable`。 |
| 请求解析 DTO | `RevenueToolRequestResolution` | `startDateIso` / `stopDateIso`、`departmentFatherIdForScopedTools`、`departmentFatherIdForBuildInsight`、可见根 / SQL 快照、`resolutionDebug`。 |
| AnswerPlan | `DailyRevenueAnswerPlanBuilder#attachIfApplicable` | 读 `toolResults.get(revenue_query)` 的 `success` / `data` / `rawStats` / `totalRevenue` 等，**门控** `AiRunState` 的 `isRevenueOverviewPath`、`isBusinessOverviewPath`、`isBusinessDiagnosisPath` 等；产出 `DailyRevenueAnswerPlan` 挂到 `state`。 |

**Fake / Harness**：`FakeRevenuePlannerReadBridge`、`PlannerExecutorTrace` 等与 **生产读** 无关。

### 19.2 现有真实 revenue 查询「有效入参」是什么

**两层：**

1. **Resolution 层（与生产一致）**  
   - **`AiRunState`**：`userId`、`departmentId`、`distributerId`、路径旗标（如 `revenueOverviewPath`）、`resolvedQueryContext`（若已挂）、`toolResults` 写入位等。  
   - **`AiResolvedQueryContext`**：时间窗、组织 / 数据 scope、可见门店、意图收窄等（经 Resolver 产出，**非** Adapter 内解析 userMessage）。

2. **Tool 线参（`RevenueQueryToolExecutor.buildRevenueQueryToolArgs`）**  
   - 必选语义：`ARG_DEPARTMENT_FATHER_ID`、`ARG_START_DATE`、`ARG_STOP_DATE`（或与 Tool 常量等价字段）。  
   - 视路径与场景：`ARG_GROUP_WIDE_OVERVIEW_HINT`、`ARG_RESOLVED_DEPARTMENT_IDS`、诊断 / business overview 下的部门锚点等。  
   - **`ToolRequest#resolvedQueryContext`**：门店排行等需 **上下文标签**（如店名），Tool 内使用。

**结论**：仅凭 Planner 端的 `RevenuePlannerReadRequest`（时间标签 + scope 摘要）**无法**复刻生产 `buildRevenueQueryToolArgs` 的全部分支；真实桥接需要 **已解析的 `AiResolvedQueryContext` + 已具备路径/租户字段的 `AiRunState`**（或由 Resolver 预计算的 `RevenueToolRequestResolution`），**不得**在 ReadBridge 内读 `userMessage` 或抄一段旧 SQL。

### 19.3 现有真实 revenue 输出是什么

| 产物 | 位置 / 类型 | 说明 |
|------|-------------|------|
| Tool 结果 Map | `AiRunState#getToolResults().get(revenue_query)` | `RevenueQueryTool` 返回经 Executor 解包后的 **Map**（含 `success`、`data`、`rawStats`、`totalRevenue`、`storeRevenueRanking` 等 key，以对齐 `DailyRevenueAnswerPlanBuilder` 读取方式为准）。 |
| 结构化计划 | `DailyRevenueAnswerPlan` | `attachIfApplicable` 在满足路径门控时写入 `state`；字段见 `DailyRevenueAnswerPlan`（`planType`、`scopeLabel`、`timeLabel`、`summary`、`focusRows` 等）。 |

Planner 桥接若以 **最薄** 为目标，**首选**从 **Tool 结果 Map** 映射到 `RevenuePlannerReadResponse`（`revenueAmount`、`storeRows`、`timeLabel`、`scopeLabel`）；`DailyRevenueAnswerPlan` 为可选增强（需同路径门控与二次构建，易与 Graph 耦合）。

### 19.4 `RevenuePlannerRealReadBridge` 推荐怎么接（最薄、可复用）

**依赖（优先级从高到低）：**

1. **`RevenueQueryToolExecutor#executeRevenueQuery`**：与 `RevenueAgent` / Graph **同一条** Tool 执行链，**不**复制 `RevenueQueryTool` 内逻辑，**不**直连 `GbAiDailyRevenueService`。  
2. **`BusinessToolExecutionRequestResolver#resolveRevenueToolRequest(AiRunState, AiResolvedQueryContext)`**：保证日期与部门锚点与生产 `RevenueAgent` 一致；若 Graph/会话侧已保证 `state` + `rq` 与生产等价，可评估是否省略重复 resolve（但首版建议 **保留 resolve** 降低分叉风险）。

**`RevenuePlannerReadRequest` → 现有查询入参：**

- **不**在 Bridge 内从 `RevenuePlannerReadRequest` 单独拼齐 Tool 参数。  
- **做法**：由 **调用方**（后续某层，非 C-10）在调用 Bridge 前将 Planner 计划与 **会话态** 对齐：保证 `AiRunState` 上已有与本次营收步一致的 `resolvedQueryContext`（或能从 ref 恢复），路径旗标与租户字段齐全；Bridge 接收 **`AiRunState` + `AiResolvedQueryContext`**（或封装为 `PlannerRevenueExecutionContext`），内部 `resolveRevenueToolRequest` → `executeRevenueQuery`。  
- `RevenuePlannerReadRequest` 仅用于 **与 Adapter 契约对齐** 时的缺口校验（如 Adapter 已做的 `ADAPTER_MISSING_TIME` / `ADAPTER_MISSING_SCOPE`），或作为 trace 旁路；**真实日期应以 `RevenueToolRequestResolution` 或 `rq.getTimeWindow()` 为准**。

**现有返回 → `RevenuePlannerReadResponse`：**

- 读 `executeRevenueQuery` 返回值或执行后 **`state.getToolResults().get(revenue_query)`**（以 Executor 实际写入为准）。  
- `OK`：`success == true`，映射 `totalRevenue` → `revenueAmount`，`storeRevenueRanking`（或等价结构）→ `storeRows`；`timeLabel` / `scopeLabel` 可从 Tool data、`rq` 或 resolution debug 择优填充。  
- `DEGRADED` / `FAILED`：`executeRevenueQuery == null`（如无权限）、`success == false`、resolution 缺日期/部门、`data` 不完整等；**禁止**把业务失败当 OK。不得新增 user 原文 **contains/regex** 分支。

**若现有能力无法「只凭 PlannerDTO」直接复用：**

- **原因**：生产 Tool 参数与路径强依赖 **`AiRunState` + `AiResolvedQueryContext` + Resolver 产物**，与 Planner 侧轻量 DTO 不同构。  
- **最小改造建议（后续迭代，非 C-10）**：在 Graph 外新增 **只读的 context 装配器**：从 `resolvedQueryContextRef` / session 恢复 `AiResolvedQueryContext`，补全 `AiRunState` 中营收路径所需字段，再调 Bridge；**或** 扩展 Bridge 入参显式要求 `PlannerRevenueExecutionContext`（仍禁止解析 userMessage）。  

### 19.5 C-10 本轮结论

- **代码**：**未改** Java / SQL / test / Master / Resolver / Composer；**仅**本文档增补。  
- **后续实现**：再接 `RevenuePlannerRealReadBridge` 时，以 **`RevenueQueryToolExecutor` + `resolveRevenueToolRequest`** 为唯一执行面，输出从 **`revenue_query` tool map** 映射到 `RevenuePlannerReadResponse`。

---

## 20. 阶段 C-11：`PlannerRevenueExecutionContext` + `RevenuePlannerRealReadBridge` 骨架

### 20.1 为什么需要 `PlannerRevenueExecutionContext`

- **`RevenuePlannerReadRequest`** 只表达 **计划级** 切片（时间标签、scope 类型、可见店列表摘要、`resolvedQueryContextRef` 等），供 Harness / Adapter 校验与 Fake Bridge；**不足以**单独驱动 `buildRevenueQueryToolArgs`（路径旗标、多店 hint、`ToolRequest#resolvedQueryContext` 等）。
- **真实执行**必须与生产一致地携带 **`AiRunState`**（租户、路径、`toolResults` 写入位、`resolvedQueryContext` 在 state 上的挂接等）与 **`AiResolvedQueryContext`**（解析层产出）；二者通过单一 DTO **固定输入边界**，避免 Bridge 从 `userMessage` 或零散字段「拼凑」意图。
- **`runStateRef` / `resolvedQueryContextRef` / `runId` / `conversationId`** 预留 Hydrate 与观测；C-11 **仅承认已物化对象**，仅有 ref 未加载时按 **缺失** 降级（见 20.4），与「不绕过 ResolvedQueryContext」一致。

### 20.2 `RevenuePlannerReadRequest` 与 `AiRunState` / `AiResolvedQueryContext` 的关系

| 对象 | 角色 |
|------|------|
| `RevenuePlannerReadRequest` | Planner / Adapter 契约；描述「这一步想读什么」的**摘要**，可与 `PlannerRevenueExecutionContext#plannerReadRequest` 并存。 |
| `AiResolvedQueryContext` | 解析层给出的 **权威** 时间窗、scope、可见门店等；**Tool 链路的输入真相来源之一**。 |
| `AiRunState` | 当次 run 的**可变执行面**（权限、path flags、`getToolResults()`）；**Tool 链路的输入真相来源之二**。 |

**约束**：真实 Bridge **不得**仅用 `RevenuePlannerReadRequest` 替代 `AiResolvedQueryContext`，也不得在 Bridge 内解析用户原文补全 context。

### 20.3 真实 Bridge 如何复用现有 revenue tool 链路（C-12+ 落地，C-11 仅声明）

- **依赖类**：`RevenueQueryToolExecutor`、`BusinessToolExecutionRequestResolver`（Spring 注入至 `RevenuePlannerRealReadBridge`）。
- **执行顺序**（与 `RevenueAgent` 对齐）：`resolveRevenueToolRequest(state, rq)` → `executeRevenueQuery(runId, state, …)` → 读取 `state.getToolResults().get(revenue_query)` → 映射 `RevenuePlannerReadResponse`。
- **C-11**：`readWithExecutionContext` **不调用** 上述方法，仅在上下文齐全时返回 `REVENUE_REAL_READ_BRIDGE_SKELETON` 降级说明，**不接生产主链路、不查库**。

### 20.4 缺少上下文时的降级码

| 情况 | `errorCode` | 说明 |
|------|-------------|------|
| `ctx == null` | `ADAPTER_NO_RUN_STATE` | 无执行上下文。 |
| 无物化 `AiRunState`（含仅有 `runStateRef`） | `ADAPTER_NO_RUN_STATE` | detail：`run_state_missing` / `run_state_ref_not_hydrated`。 |
| 无物化 `AiResolvedQueryContext`（含仅有 ref） | `ADAPTER_NO_RESOLVED_CONTEXT` | detail：`resolved_query_context_missing` / `resolved_query_context_ref_not_hydrated`。 |
| C-11 仅骨架（历史） | `REVENUE_REAL_READ_BRIDGE_SKELETON` | 已由 C-12 真实执行替代；勿依赖此码作为常态。 |

### 20.5 不新写 SQL 的原因（重申）

- 营收统计已封装在 **`RevenueQueryTool` → `GbAiDailyRevenueService`**；Planner 侧重复 SQL 会分叉口径、绕过权限与 Tool 观测。
- 复用 **`RevenueQueryToolExecutor`** 保证与 Graph / `RevenueAgent` **同一条** 执行与写入 `toolResults` 的语义。

### 20.6 C-11 本轮结论

- **代码**：新增 `PlannerRevenueExecutionContext`、`RevenuePlannerRealReadBridge`（`com.nongxinle.ai.planner`）；**未改** test / SQL / Master / Resolver / Composer / `RevenuePlannerReadBridge` 契约接口。
- **文档**：本节（§20）。

---

## 21. 阶段 C-12：`RevenuePlannerRealReadBridge` 接通 `revenue_query`（仍不接 Master）

### 21.1 行为

- 当 `PlannerRevenueExecutionContext` 物化 **`AiRunState` + `AiResolvedQueryContext`** 后：
  1. `BusinessToolExecutionRequestResolver#resolveRevenueToolRequest(state, rq)`
  2. `RevenueQueryToolExecutor#executeRevenueQuery(...)`（与 `RevenueAgent` 同序）
  3. 从 `state.getToolResults().get(revenue_query)` 读取 **信封 Map**（含 `success`、`data`、`message`），映射 `RevenuePlannerReadResponse`
- **不**写 SQL、**不**解析 `userMessage`、**不**绕过 `AiResolvedQueryContext`。`state.resolvedQueryContext` 若空则用入参 `rq` 回补，避免 ToolRequest 旁路。

### 21.2 Adapter 契约：`PlannerExecutionPlan#revenueExecutionContext` / `PlannerAgentAdapterRequest#revenueExecutionContext`

- `RevenuePlannerReadRequest` **仅**表达计划切片（时间、scope 等），**不**持有 `PlannerRevenueExecutionContext`（避免 ReadRequest ↔ ExecutionContext 双向引用）。
- `PlannerRevenueExecutionContext` 可持有 `plannerReadRequest`；编排将二者同挂于 `PlannerExecutionPlan`（及逐步 `PlannerStepExecutionRequest` / `PlannerAgentAdapterRequest`）。
- `RevenuePlannerAgentAdapter` 对 `RevenuePlannerRealReadBridge` 调用 `readWithExecutionContext`；若 **无** `revenueExecutionContext`，降级 `REVENUE_REAL_BRIDGE_NO_PLANNER_EXECUTION_CONTEXT`。
- 其他 `RevenuePlannerReadBridge`（如 Harness Fake）仍只使用 `readRevenue(RevenuePlannerReadRequest)`。
- `PlannerExecutor` 写入 `PlannerExecutorTrace` 的 plan 快照会对 `revenueExecutionContext` **脱敏**：不保留完整 `AiRunState` / `AiResolvedQueryContext` 对象，仅保留 ref 与小字段（执行路径仍使用完整上下文）。

### 21.3 结果映射与状态

| 条件 | `status` | `errorCode`（示例） |
|------|----------|---------------------|
| `executeRevenueQuery == null` | `FAILED` | `REVENUE_TOOL_PERMISSION_DENIED` |
| `ToolResult` 非 success | `FAILED` | `REVENUE_TOOL_EXECUTION_FAILED` |
| `toolResults` 缺或信封 `success!=true` | `DEGRADED` | `REVENUE_TOOL_*` |
| 信封 `success` 且无 `totalRevenue`/有效 `rawStats`/`storeRevenueRanking` | `DEGRADED` | `REVENUE_TOOL_OK_BUT_EMPTY_REVENUE_PAYLOAD` |
| 有有效数据 | `OK` | — |

`storeRevenueRanking` → `RevenuePlannerStoreRevenueRow`：`storeDepartmentId` → `departmentId`，`storeName` → `storeLabel`，`revenueAmount` → `amount`。

### 21.4 Harness：`PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_CORE`

- **`AiPlannerExecutorRevenueAdapterRealBridgeGraphCase`**：计划在 `revenueExecutionContext` 上挂不完整上下文（**故意** `runState==null`、`resolvedQueryContext==null`，`revenueReadRequest` 只为切片），使默认 curl 摘要诚实为 `ADAPTER_NO_RUN_STATE` / `run_state_ref_not_hydrated`；摘要字段 `plannerRevenueAdapterHonesty=REAL_BRIDGE_HARNESS_INCOMPLETE_CONTEXT`。
- **`AiHarnessReplayService`** 注入 `RevenuePlannerRealReadBridge`、`PurchasePlannerRealReadBridge`、**`StockReducePlannerRealReadBridge`** 与 **`DishProfitPlannerRealReadBridge`**；Planner mock 重放走 `AiHarnessReplayPlannerExecutorMock.replay(req, revenueBridge, purchaseBridge, stockReduceBridge, dishProfitBridge)`。营收 Real / Hydrated case 且 `revenueBridge==null`、或采购 Real / Hydrated case 且 `purchaseBridge==null`、或出库 Hydrated case 且 `stockReduceBridge==null`、或菜品毛利 Hydrated case 且 `dishProfitBridge==null` 时抛 `IllegalStateException`（须经由 `AiHarnessReplayService`）。

### 21.5 C-12 结论

- **仍不接** `MasterBusinessAgent` 生产主链路；**不**改 Resolver / Composer。
- **仍不**新增 SQL；查库仅经由既有 `RevenueQueryTool`。

---

## 22. 阶段 C-13 / C-14：`PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`

**状态（C-14 收口 + C-44 GROUP 切片）**：STORE Hydrated 已实现，且 **curl / Harness 重放已验证成功链路**（见 **§22.8**）。类 **`AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase`**；**C-44** 另增 **`AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase`**（**§22.13**）。注册点含 `AiHarnessBuiltinCases`、`isPlannerExecutorMockHarnessCase`、`AiHarnessReplayPlannerExecutorMock`、`AiHarnessReplayService#resolveReplayMode`（GROUP case 与 C-13 同属 **`PLANNER_EXECUTOR_REVENUE_ADAPTER`**）。Hydrated 轮次 `pass` / 响应 `overallPass` 当且仅当 `plannerExecutorTrace.overallStatus=SUCCESS`（诚实；DB 无数据或 payload 不足时多为 `DEGRADED`，摘要见 §22.0）。

### 22.0 实现摘要（C-14）

- **类**：`AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase`（单店 `STORE`，门店根 **`gb_department_id=1`（AAA）**；path 旗标全 false；`aiUserContext` 未设以走 Guard 兼容路径；**不**在 Harness 内手动展开子部门，交给 `ToolDepartmentResolutionSupport` / `RevenueQueryToolExecutor`）。
- **摘要**：工具与整轮皆 `SUCCESS` 时 `plannerRevenueAdapterHonesty=REAL_BRIDGE_HYDRATED_REVENUE_TOOL_OK`；否则 `REAL_BRIDGE_HYDRATED_REVENUE_TOOL_DEGRADED` + `plannerRevenueAdapterNote` 含步状态与 `degradedReason`/`errorMessage`。
- **trace**：仍由 `PlannerExecutor#sanitizePlanForTrace`（§21.2）避免在 trace 中保留完整 `AiRunState`/`AiResolvedQueryContext` 大对象。

### 22.1 目标与边界

- **目标**：在**独立** Harness caseId `PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` 中，构造**最小可用**的 `AiRunState`、`AiResolvedQueryContext`、`RevenuePlannerReadRequest`、`PlannerRevenueExecutionContext`（与 C-12 结构一致：ExecutionContext 可持有 ReadRequest，ReadRequest 不反向持有 ExecutionContext），使 `RevenuePlannerRealReadBridge#readWithExecutionContext` 能真实调用既有 `revenue_query` 链路（`BusinessToolExecutionRequestResolver#resolveRevenueToolRequest` → `RevenueQueryToolExecutor#executeRevenueQuery` → `RevenueQueryTool`）。
- **遵守**：不新写 SQL；不接 `MasterBusinessAgent`；不改 Resolver / Composer **主逻辑**；不新增用户原文 contains/regex；继续复用 **C-12** 的 `RevenuePlannerRealReadBridge`。
- **环境依赖**：`RevenueQueryTool` 调用 `GbAiDailyRevenueService` 等现有服务；**整体验收依赖运行环境 DB（或等价数据源）在选定部门 + 日期范围内有可读行**。设计不臆造「魔法默认」以绕过服务端空结果；若库中无数据，工具可能仍 `success=true` 但 payload 偏空，**Bridge 映射**可能落到 `REVENUE_TOOL_OK_BUT_EMPTY_REVENUE_PAYLOAD`（见 §21.3），与「营收步 SUCCESS」冲突——实现时应选用**已知有数据**的 harness 部门/日期，或在实现阶段单独记录观测，**不在本文乱补默认值**。

### 22.2 Harness 成功判据（验收）

对 `PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` 的**目标**摘要形态（**§22.8 已观测到**）：

- 营收步：`PlannerStepResult.status = SUCCESS`；
- `plannerExecutorTrace.overallStatus = SUCCESS`；
- `plannerExecutorTrace.degradedSteps = []`；
- `usedAgents` 含 `revenue_overview`；
- `usedTools` 含 `revenue_query`；
- `plannerRevenueAdapterHonesty = REAL_BRIDGE_HYDRATED_REVENUE_TOOL_OK`（仅 Hydrated case 摘要；与 C-12 的 `REAL_BRIDGE_HARNESS_INCOMPLETE_CONTEXT` 区分）。

### 22.3 `AiRunState`：**最小**推荐字段（结合现有代码路径）

以下按 **`RevenuePlannerRealReadBridge`** → **`resolveRevenueToolRequest`** → **`executeRevenueQuery`** → **`RevenueQueryTool`** / **`AiPermissionGuard`** 的**实际读取**归纳；**单行门店、非 group-wide** 为首次实现的首选场景（见 22.6）。

| 字段 | 是否纳入最小集 | 说明 |
|------|----------------|------|
| `runId` | **是** | `executeRevenueQuery` / `ToolRequest`；与 `PlannerRevenueExecutionContext.runId` 对齐且可解析为 `long`。 |
| `conversationId` | **建议** | 与平台会话模型一致；当前 `REVENUE_QUERY` 执行链未强依赖，但利于与 Run 元数据一致。 |
| `userId` | **是** | `ToolRequest.userId`；与权限锚点一致。 |
| `departmentId` | **是** | `resolveRevenueToolRequest` 中部门锚点链条的起点之一；宜与 orgScope 门店根一致。 |
| `distributerId` | **建议** | 传入 `executeRevenueQuery` 的 `dis`；单店场景常可为 null，若工具/服务侧需要组织维度再补。 |
| `resolvedQueryContext` | **是** | 与 `PlannerRevenueExecutionContext.resolvedQueryContext` **同一引用**；Bridge 会在 state 上回补（若为空）。 |
| `toolResults` | **是** | 可初始化为空 `Map`；执行后写入 `revenue_query` 信封。 |
| `statStartDate` / `statEndDate` | **二选一** | `resolveRevenueToolRequest` 在 `rq.timeWindow` 无起止时回退到 state 上这两字段（见 `BusinessToolExecutionRequestResolver#resolveStartDateIso` / `resolveEndDateIso`）。**推荐**优先在 `rq.timeWindow` 填齐起止，减少歧义。 |
| `revenueOverviewPath` / `businessOverviewPath` / `businessDiagnosisPath` | **否（首选）** | `RevenueQueryToolExecutor#buildRevenueQueryToolArgs` 仅在上述 path 为 true 时走 **group-wide** 分支（多店、ARG_GROUP_WIDE_OVERVIEW_HINT 等）。**Hydrated v1 建议路径旗标全 false**，走单店 `getStatsByDepartmentId(dept, start, stop)`，最小化对 `AiQueryScope` / 多店列表的依赖。 |
| `aiUserContext` | **可选** | `AiPermissionGuard#evaluateToolInvocation`：**若 `state.getAiUserContext() == null`，直接 `allow()`**（文档语：兼容仅构造 state 的路径）。若为非 null，则必须含 `VIEW_REVENUE` 等权限且通过 `requestWithinOrgScope`。**实现二选一**：null 快车道，或补齐最小 `AiUserContext`（不在本文档虚构具体权限集合以外的字段）。 |
| `scope` | **可选** | `ToolDepartmentResolutionSupport#resolveBuildInsightDepartmentFatherId` 在 `orgScope` 不够用且 `AiConversationScopeMode.STORE` 时可回退；**首选**让 `AiResolvedOrgScope` 单店语义完备，使不必依赖 `scope`。 |

未在上表列出的 path 旗标、AnswerPlan 句柄、Composer 字段等，**首版 Hydrated Harness 不需要**。

### 22.4 `AiResolvedQueryContext`：**最小**推荐子结构

`AiResolvedQueryContext` 顶层字段极多；**营收 resolve + 单店 tool** 实际主要触及：

| 子结构 / 语义 | 是否纳入最小集 | 说明 |
|---------------|----------------|------|
| `timeWindow.startDate` / `timeWindow.endDate` | **是** | 对应业务上的「区间起止」；与 `RevenuePlannerReadRequest` 的 `timeStart`/`timeEnd` 应对齐。**勿**仅依赖 `timeLabel` 而不验证 `effectiveTimeWindowForResolution` 是否真能物化起止（`AiResolvedTimeWindow.fromSemanticTimeType` 路径依赖结构化 label + `LocalDate.now()` + 可选 `previousTurn`）。 |
| `orgScope.scopeType` | **是** | 例如 `AiResolvedOrgScope.SCOPE_STORE`；影响 `resolveBuildInsightDepartmentFatherId` 是否优先取 `currentStoreDepartmentId` 或单列 `visibleStores`。 |
| `orgScope.visibleStores` | **是** | 至少一项 `AiStoreScopeDTO.storeDepartmentId`，供 `BusinessScopeResolutionSupport#extractVisibleStoreRootDepartmentIds` 与部门锚点回退（`firstVisibleStoreDepartmentId`）。用户描述的「visibleStores」在此落地。 |
| `orgScope.currentStoreDepartmentId` | **强烈建议** | `SCOPE_STORE` 时 `ToolDepartmentResolutionSupport` 优先取此字段作为门店根。 |
| `orgScope.requestDepartmentId` / `distributerId` | **按需** | 若开启非 null `aiUserContext` 且走 `requestWithinOrgScope`，需与 `state.departmentId` / `distributerId` 一致；否则可后续再收紧。 |
| `dataScope` | **首选可空** | `resolveRevenueToolRequest` 在 org 锚点不足时用 `firstDataScopeDepartmentRootAnchor(rq.getDataScope())` 回退；**单店 v1** 若 orgScope 已给出锚点，可为 null。若仍缺锚点，需按生产语义补 `visibleStoreRootIds` / `effectiveSqlDepartmentIds` 等**之一**（具体键名以 `AiResolvedDataScope` 为准），**禁止**为「凑跑通」随意塞无关 ID。 |
| `queryIntent` / `effectivePathCode` 等 | **首版可空** | `resolveRevenueToolRequest` 的营收路径**不读** intent；**除非**后续验证工具或权限侧隐性依赖（若发现，在实现阶段记入本文「缺口」表）。 |

**说明**：用户清单中的「scopeType、visibleStores、queryDepartmentIds」——前两者落在 **`orgScope`**；**`queryDepartmentIds` 并非 `AiResolvedQueryContext` 顶层字段**，Planner 侧的 `RevenuePlannerReadRequest.queryDepartmentIds` 仍供 Adapter **校验** 与展示切片；与 **`AiResolvedDataScope.queryRealDepartmentIds` / `expandedSqlDepartmentIds`** 是不同层对象。Hydrated 场景下应**对齐** Planner 切片与 `orgScope`/`dataScope`，但不要在文档中假造两套 ID 的换算规则。

### 22.5 `RevenuePlannerReadRequest` + `PlannerRevenueExecutionContext`

- 与 **C-12** 相同：`RevenuePlannerReadRequest` 仅承载解析 ref、时间、scope 摘要、门店列表等；**`PlannerRevenueExecutionContext`** 承载 `runState`、`resolvedQueryContext`、`runStateRef`、`resolvedQueryContextRef`、`userId`、`departmentId`、`distributerId`、`conversationId`、`runId`、`plannerReadRequest`。
- Hydrated case 与 C-12 的差异：`runState`、`resolvedQueryContext` **非 null** 且字段按 22.3、22.4 物化；`runState.resolvedQueryContext` 与 context 内对象一致。

### 22.6 Group-wide 与 path 旗标（显式列为「扩展」）

若未来要验证 **多店 / 集团宽表** 与 `ARG_GROUP_WIDE_OVERVIEW_HINT`：

- 需将 `revenueOverviewPath` 或 `businessOverviewPath` 或 `businessDiagnosisPath` 置为 true，并满足 `RevenueQueryToolExecutor#buildRevenueQueryToolArgs` 中 `shouldRouteGroupWideBusinessOverview` / `extractVisibleStoreRootDepartmentIds(...).size() > 1` 等条件；
- 往往额外依赖 `state.scope`、`state.aiUserContext` 角色码等。**C-13 v1 设计默认不做**，避免在未审计 `BusinessToolExecutionNode.shouldRouteGroupWideBusinessOverview` 全部分支前乱设默认值。

### 22.7 已知「还可能缺」的依赖（只列事实，不乱补）

实现阶段若单店最小集仍失败，按**先后**排查：

1. **`AiPermissionGuard`**：非 null `aiUserContext` 时缺少 `VIEW_REVENUE` 或 `requestWithinOrgScope` 失败 → `executeRevenueQuery` 返回 null → 营收步 `FAILED`。  
2. **`GbAiDailyRevenueService#getStatsByDepartmentId`**：部门/日期在 DB 无行 → 工具仍可能标记 mock/no_rows；Bridge 若判「无有效 payload」则 DEGRADED（§21.3）。  
3. **`ToolDepartmentResolutionSupport` + `AiScopeResolver`**：门店角色（采购/库管等）时可能对 `dept` 做归一；若 Hydrated 使用**非门店角色**路径可规避；若模拟门店角色，需按 `AiScopeResolver` 行为准备 ID。  
4. **`buildStoreRevenueRanking`**：依赖 Tool 内对 `resolvedQueryContext` 的进一步读取；若排行失败仅影响附加列表，不一定阻断 `totalRevenue`。**以运行日志为准**补充字段，不本文预填。

### 22.8 C-14 实测成功链路（curl / Harness，不扩展功能）

以下为本轮 **C-14 收口** 时真实重放观测（**STORE** + **RealBridge** + **真实 `revenue_query`**）：

| 观测项 | 值 |
|--------|-----|
| `caseId` | `PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` |
| Scope | `AiResolvedOrgScope#SCOPE_STORE`，**单店** |
| 门店根 / `departmentId` | **1**（文档语：**AAA** 门店根 `gb_department_id=1`；Harness 常量 `HARNESS_STORE_DEPARTMENT_ID`） |
| Bridge | 复用 **`RevenuePlannerRealReadBridge`**（与 C-12 同源），不经 `MasterBusinessAgent` |
| Tool | **`revenue_query`**（`RevenueQueryToolExecutor` → 既有 `RevenueQueryTool` / 服务，**无新 SQL**） |
| `overallStatus` | `SUCCESS` |
| `degradedSteps` | `[]` |
| 营收步 | `SUCCESS` |
| 诚实摘要 | `plannerRevenueAdapterHonesty = REAL_BRIDGE_HYDRATED_REVENUE_TOOL_OK` |

**与时间/数据**：Harness 固定窗口为代码内 `LocalDate`（如 2026-05-01..14）；**能否 SUCCESS 仍依赖该部门在该区间内 DB 有可读营业额**；无行或空 payload 时 Bridge 可按 §21.3 落到 `DEGRADED`，摘要为 `REAL_BRIDGE_HYDRATED_REVENUE_TOOL_DEGRADED`（不伪装 OK）。

### 22.9 C-14 最小成功上下文（Hydrated 实现实际填充）

以下为 **`AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase` 当前构造** 与 §22.3–22.5（字段设计展开）对照后的「跑通 rev」**最小可复用清单**（其它字段可按路径再收紧，但不作为首版 Harness 必选项）。

**`AiRunState`（必填级）**

| 字段 | Hydrated 当前值 / 说明 |
|------|-------------------------|
| `runId` | 与 `PlannerRevenueExecutionContext.runId` 可解析一致（Harness 与 `SYNTHETIC_RUN_ID_BASE+轮次` 对齐） |
| `conversationId` | 建议设置（Harness 用 `0L`） |
| `userId` | 与权限 / ToolRequest 锚点一致 |
| `departmentId` | **与门店根一致**（当前 **1**） |
| `distributerId` | 可为 `null`（单店首版） |
| `resolvedQueryContext` | **非 null**，与 `PlannerRevenueExecutionContext.resolvedQueryContext` **同一引用** |
| `toolResults` | **非 null**，初值空 `Map`，执行后写入 `revenue_query` 结果 |
| path 旗标 | **`revenueOverviewPath` / `businessOverviewPath` / `businessDiagnosisPath` 均为 false**（`AiRunState` 默认值即可） |

**`AiResolvedQueryContext`（必填级子结构）**

| 字段 / 子结构 | Hydrated 当前说明 |
|---------------|-------------------|
| `timeWindow.startDate` / `endDate` | 与 `RevenuePlannerReadRequest.timeStart` / `timeEnd` **对齐** |
| `timeWindow.timeLabel` | 建议填写，便于调试 |
| `orgScope.scopeType` | **`SCOPE_STORE`** |
| `orgScope.currentStoreDepartmentId` | 门店根（当前 **1**） |
| `orgScope.requestDepartmentId` | 与门店根对齐（当前 **1**） |
| `orgScope.visibleStores` | 至少一项：`storeDepartmentId` **1**，`storeName` **AAA** |
| `runId` / `userId`（顶层） | 与 `AiRunState` / 会话模型对齐（Harness 已填） |

**`PlannerRevenueExecutionContext`（必填级）**

| 字段 | 说明 |
|------|------|
| `runState` | **非 null**，且 `runState.getResolvedQueryContext()` 与下同引用 |
| `resolvedQueryContext` | **非 null** |
| `resolvedQueryContextRef` | 与计划 / ReadRequest 句柄一致 |
| `userId` / `departmentId` / `distributerId` | 与 `AiRunState` 对齐 |
| `conversationId` / `runId` | 字符串形式与 Run 元数据对齐 |
| `plannerReadRequest` | **非 null**，与计划 `revenueReadRequest` 一致 |

**`RevenuePlannerReadRequest`（必填级）**

| 字段 | Hydrated 当前说明 |
|------|-------------------|
| `resolvedQueryContextRef` | 与 `PlannerExecutionPlan.resolvedContextRef` / ExecutionContext ref 一致 |
| `timeStart` / `timeEnd` / `timeLabel` | 与 `AiResolvedQueryContext.timeWindow` 对齐 |
| `scopeType` | **`SCOPE_STORE`** |
| `visibleStores` | 至少一项：`departmentId` **1**，展示名 **AAA** |
| `queryDepartmentIds` | **`[1]`**（**不**在 Harness 内展开子部门 2、5） |
| `targetStoreDepartmentId` | **1** |
| `answerPlanRef` | Adapter / 计划后链路句柄 |

### 22.10 C-14 当前限制（收口声明）

- **只验证 STORE 单店**；**未**用本 case 系统性验证 **GROUP / 多门店**、`revenueOverviewPath` 等 **group-wide** 分支（见 §22.6）。
- **第二步 recommendation** 仍为 **`RecommendationPlannerMockAgentAdapter` + mock SUCCESS**，**不是**生产建议链路。
- **未接** `MasterBusinessAgent` **生产主图**；Hydrated 仍走 `AiHarnessReplayPlannerExecutorMock` 短路，**不跑** Resolver / 生产 Graph。
- **不新写 SQL**；营收数据依赖既有 `RevenueQueryTool` 及服务。
- **环境依赖**：其它机器 / 库若部门 **1** 或日期区间无数据，同一 caseId 可能为 `DEGRADED` 或 `overallPass=false`，属诚实失败而非文档契约断裂。

### 22.11 后续多域 RealBridge 模板（Purchase / Stock / Dish）

营收 C-14 收口后，**采购 / 出库 / 菜品毛利** 若做 **Planner 侧 Hydrated RealBridge Harness**，建议 **复用同一模板**（**不新写 SQL**，不接 Master 主链路作首版）：

| 拟议 Bridge（命名示意） | 应对齐的既有 Tool / 执行链（实现以代码为准） | 与 C-14 对齐的做法 |
|-------------------------|-----------------------------------------------|---------------------|
| `PurchasePlannerRealReadBridge` | 既有 **`purchase_overview`** / `PurchaseOverviewToolExecutor`（或等价入口） | 物化最小 `AiRunState` + `AiResolvedQueryContext` + **领域 ReadRequest** + **领域 ExecutionContext**；经 **现有** `BusinessToolExecutionRequestResolver`（或领域内部分辨）→ **现有** Tool Executor → **现有** Tool Bean |
| `StockReducePlannerRealReadBridge` | 既有 **`stock_reduce_query`** / `StockReduceQueryToolExecutor` | 同上 |
| `DishProfitPlannerRealReadBridge` | 既有 **`dish_profit_analysis`** / `DishProfitQueryToolExecutor`（或等价） | 同上 |

**原则**：

1. **Bridge 只调度**「resolve tool 请求 → 调用现有 Executor → 映射为 PlannerReadResponse」，**禁止**在 Bridge 内直连底层报表 Service（除非与现有 Tool 层已统一）。
2. **Hydrated Harness** 独立 `caseId`，结构对称 C-14：`PlannerExecutorExecutionMode.ADAPTER` + 对应 `*PlannerAgentAdapter` + **可选** mock 后续步。
3. **诚实摘要**：成功 / `DEGRADED` / 空 payload 原因与营收 `REAL_BRIDGE_HYDRATED_*` 对称命名，**不**伪装 Tool 成功。
4. **trace** 继续依赖 `sanitizePlanForTrace` 同类策略，**不**输出完整 `AiRunState` / `AiResolvedQueryContext`。

### 22.12 实现清单（✅ C-14 已核对 + curl 验收）

1. `AiHarnessBuiltinCases`：`PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`。  
2. `isPlannerExecutorMockHarnessCase`、`AiHarnessReplayPlannerExecutorMock`、`AiHarnessReplayService`：`HYDRATED` 与 `REAL_BRIDGE_CORE` 对称注入 `RevenuePlannerRealReadBridge`。  
3. `AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase`（门店根 **1 / AAA**；实测与最小字段见 **§22.8–22.9**）。  
4. **不改** `RevenuePlannerRealReadBridge` / SQL / Master。  
5. **C-44**：`PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE` + `AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase`（**§22.13**；**非** Composite）。

### 22.13 C-44：`PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE`（GROUP Hydrated）

- **目的**：在 **`scopeType=GROUP`**、**双可见门店根（1 / AAA，3 / 汀兰餐厅）** 下探测 **`RevenuePlannerRealReadBridge`** 是否真实执行 **`revenue_query`** 并得到 **多店/集团可读** payload（**`totalRevenue`**、**`storeRevenueRanking`** 等以 Tool 为准），**不**接 Composite / Master / LLM。  
- **上下文**：`AiResolvedOrgScope.SCOPE_GROUP`；`AiResolvedDataScope.fromOrgScope(org)`；`AiRunState.departmentId=null`；`runState.resolvedQueryContext` 与 `PlannerRevenueExecutionContext.resolvedQueryContext` **同一引用**；`RevenuePlannerReadRequest.targetStoreDepartmentId=null`，`queryDepartmentIds=[1,3]`。  
- **诚实摘要**：成功 **`plannerRevenueAdapterHonesty=REAL_BRIDGE_HYDRATED_REVENUE_GROUP_TOOL_OK`**（营收步 `SUCCESS` 且整轮 `SUCCESS`）；否则 **`…_GROUP_TOOL_DEGRADED`**（**不**用单店假成功顶替 GROUP）。  
- **Harness 根摘要观测**：`harnessRevenueGroupVisibleStoreRootDepartmentIds`、`harnessRevenueQueryEnvelopePresent`、`harnessRevenueQueryTotalRevenue`、`harnessRevenueQueryStoreRevenueRankingSize`、`harnessRevenueQueryRankingStoreDepartmentIds`（见 **`AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase#toHarnessSummary`**）。  
- **权威补文**：**[`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md) §11**。

---

## 23. 阶段 C-15 / C-18：Purchase Planner Adapter（梳理 + C-18 设计 + **C-19 curl 验收**）

**状态**：**C-16/C-17** 已落地 Planner Java + C-17 Harness；**C-19** **Hydrated RealBridge** 已 **curl 验收**（详见 **[`purchase-planner-adapter-design.md`](./purchase-planner-adapter-design.md) §12** 与本文 **§24**）。

- **详设**：[`docs/ai/purchase-planner-adapter-design.md`](./purchase-planner-adapter-design.md) — **`purchase_overview`** / **`purchase_query`**、`PurchaseAgent` + `PurchaseOverviewToolExecutor` + `PurchaseToolRequestContext` + `PurchaseAnswerPlan`、**STORE 单店 Hydrated（§11 字段权威；§12 C-19 curl 收口 + 限制 + 后续域模板）**、禁止项、caseId：
  - `PLANNER_EXECUTOR_PURCHASE_ADAPTER_CORE`
  - `PLANNER_EXECUTOR_PURCHASE_ADAPTER_FAKE_OK_CORE`
  - `PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE`
  - `PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`（**C-18 设计 + C-19 实装**，见 **§24**）
- **RealBridge 推荐接法**：与 `RevenuePlannerRealReadBridge` 对称 — `buildPurchaseRequestContext` → `executePurchaseOverview` →（成功）`PurchaseAnswerPlanBuilder.attachIfApplicable`；**禁止**解析 `userMessage`、**禁止** Bridge 内 SQL、**禁止**绕过 `AiResolvedQueryContext` / 既有 Tool。

---

## 24. 阶段 C-18 / C-19：`PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`（**C-19 curl 已验收**）

**状态（C-19 收口）**：Java 与 Replay 已落地；**curl Harness 已成功**走真实 **`purchase_overview`**（不经 Master）。**字段级权威**与 **curl 观测表 / 限制 / 后续 Adapter 模板** 以 **[`purchase-planner-adapter-design.md`](./purchase-planner-adapter-design.md) §11–§12** 为准；本文 **§24** 为架构文档侧索引与对称营收 **§22** 的「多域模板」锚点。

### 24.1 目标（caseId）

独立 **caseId** `PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`：在计划中物化最小 **`AiRunState` + `AiResolvedQueryContext` + `PurchasePlannerReadRequest` + `PurchasePlannerExecutionContext`**（**STORE 单店**，**不做 GROUP**），使 **`PurchasePlannerRealReadBridge#readWithExecutionContext`** 走：

**`BusinessToolExecutionRequestResolver#buildPurchaseRequestContext` → `PurchaseOverviewToolExecutor#executePurchaseOverview` → `PurchaseOverviewTool` → `PurchaseAnswerPlanBuilder`**。

### 24.2 完整调用链（从 PlannerExecutor 到 AnswerPlan）

与 **`purchase-planner-adapter-design.md` §12.2** 一致，顶层多一步编排：

```text
PlannerExecutor（ADAPTER + PlannerAgentAdapterStepExecutor）
  → PurchasePlannerAgentAdapter
       → PurchasePlannerRealReadBridge#readWithExecutionContext
            → BusinessToolExecutionRequestResolver#buildPurchaseRequestContext
            → PurchaseOverviewToolExecutor#executePurchaseOverview
            → PurchaseOverviewTool
            → PurchaseAnswerPlanBuilder#attachIfApplicable
```

本 Harness 计划 **第 2 步** 仍为 **`RecommendationPlannerMockAgentAdapter`**（mock），**不**代表生产建议链。

### 24.3 curl Harness 已观测成功（C-19）

`caseId = PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`，**STORE**、**`departmentId` / 门店根 1 / AAA**、**`purchaseSourceType = ALL`**、**`structuredIntentDetail = purchase_overview_summary`**、`groupPurchaseOverview = false`，且 DB / 权限 / `distributerId` 与环境一致时，曾观测：

| 字段 | 值 |
|------|-----|
| `overallStatus` | **SUCCESS** |
| `degradedSteps` | **`[]`** |
| 采购步 `step_purchase_adapter_hydrated` | **SUCCESS** |
| `usedTools` | 含 **`purchase_overview`** |
| `plannerPurchaseAdapterHonesty` | **`REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_OK`** |

### 24.3.1 C-45：`PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE`（GROUP Hydrated 探测）

独立 **caseId**，类 **`AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase`**：**`scopeType=GROUP`**，可见门店根 **1（AAA）**、**3（汀兰餐厅）**，**`AiResolvedDataScope.fromOrgScope`**；**`AiRunState.departmentId=null`**、**`distributerId=2`**（与 C-19 占位一致）、**`groupPurchaseOverview=true`**（生产 **`PurchaseOverviewToolExecutor#buildPurchaseOverviewToolArgs`** 据此走 **`ARG_GROUP_PURCHASE_AGGREGATION`** + 多店 **`ARG_RESOLVED_DEPARTMENT_IDS`**；C-19 STORE 单店为 **`false`**）；**`PurchasePlannerReadRequest`**：**`targetStoreDepartmentId=null`**，**`queryDepartmentIds=[1,3]`**，**`purchaseSourceType=ALL`**，**`structuredIntentDetail=purchase_overview_summary`**。Replay 推断 **`PLANNER_EXECUTOR_PURCHASE_ADAPTER`**；成功 **`plannerPurchaseAdapterHonesty=REAL_BRIDGE_HYDRATED_PURCHASE_GROUP_TOOL_OK`**；失败 **`…_GROUP_TOOL_DEGRADED`**（**不**单店假成功）。**不接** Composite / LLM / Master。观测字段见 **`business-diagnosis-composite-group-design.md`** C-45 表与 **`purchase-planner-adapter-design.md` §7.3**。

### 24.4 最小成功上下文（速查）

细表见 **purchase §11**；速查见 **purchase §12.3**。要点：**`AiRunState`** 含 `runId`、`conversationId`、`userId`、`departmentId`、`distributerId`、`resolvedQueryContext`（与 ExecutionContext 同引用）、`toolResults`（非 null）、`groupPurchaseOverview=false`；**`AiResolvedQueryContext`** 含对齐的 `timeWindow`、`orgScope`（STORE / 1 / AAA）、`queryIntent`（ALL + `purchase_overview_summary`）、根级 **`effectiveIntentCode` / `effectivePathCode`**；**`PurchasePlannerExecutionContext`** 与 **`PurchasePlannerReadRequest`** 按 §11.4–11.5 与计划 `purchaseReadRequest` 对齐。

**`purchaseOverviewPath`**：Hydrated case **默认 false**；若未来某 Tool 分支必须 `true`，**仅**在 `AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase#buildHydratedRunState` 设并双文档更新（见 purchase §11.8）。

### 24.5 验收契约（诚实）

- **成功**：采购步 **SUCCESS**、`overallStatus=SUCCESS`、`usedTools` 含 **`purchase_overview`**、**`plannerPurchaseAdapterHonesty = REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_OK`**。
- **失败**：**可控降级 / 步失败**，**不**未捕获异常，**不**假 SUCCESS；**`plannerPurchaseAdapterHonesty = REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_DEGRADED`** + note（对标营收 §22.0）。
- **trace**：**不**输出完整 `AiRunState` / `AiResolvedQueryContext`（与 §22 / `sanitizePlanForTrace` 同类策略）。

### 24.6 当前验证范围与明确未覆盖项

与 **purchase §12.4** 对齐简述：**C-19** **仅**验 STORE 单店；**C-45** 独立 **`PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE`** 验 GROUP + **`groupPurchaseOverview=true`**（见 **§24.3.1**）；**未**在单 case 内覆盖自采/供货商筛选、supplier 维度等；**recommendation** 仍为 mock；**未**接 Master 生产主链路；**不**新 SQL、**不**解析 `userMessage`。

### 24.7 后续 Adapter 模板（StockReduce / DishProfit）

对称 **采购 C-19** 与 **营收 §22**：

1. **`*PlannerRealReadBridge`**：`readWithExecutionContext` 风格，物化 **`AiRunState` + `AiResolvedQueryContext`**；**禁止** Bridge 内 SQL、禁止绕过既有 Tool。
2. **执行链**：**`BusinessToolExecutionRequestResolver`**（或域内 `build*RequestContext`）→ **`StockReduceQueryToolExecutor` / `DishProfitQueryToolExecutor`**（或等价命名）→ **现有 Tool** → **现有 `*AnswerPlanBuilder.attachIfApplicable`（或等价）**，仅从 **`toolResults`** 附着。
3. **Harness**：独立 **`…_REAL_BRIDGE_HYDRATED_CORE`**，诚实摘要 **`REAL_BRIDGE_HYDRATED_*_TOOL_OK` / `…_DEGRADED`**。
4. **详表**：见 **purchase-planner-adapter-design.md §12.5**；**出库/核销** 前置设计见 **[`stock-reduce-planner-adapter-design.md`](./stock-reduce-planner-adapter-design.md)**（**C-20**）。

### 24.8 实现清单（✅ C-19）

1. `AiHarnessBuiltinCases`：`PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`。  
2. `isPlannerExecutorMockHarnessCase`、`AiHarnessReplayPlannerExecutorMock`、`AiHarnessReplayService`：与采购 Real CORE 对称；Hydrated 轮次 `pass` 当且仅当 `overallStatus=SUCCESS`。  
3. `AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase`；`PurchasePlannerRealReadBridge` 接线 `buildPurchaseRequestContext` → `executePurchaseOverview` → `attachIfApplicable`。  
4. **trace**：`PlannerExecutor#sanitizePlanForTrace` 继续清空 `runState` / `resolvedQueryContext`。  
5. **C-45**：`PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE` + `AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase`（**§24.3.1**）。  
6. **C-46（出库）**：`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE` + `AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase`（本文 **§25** C-46 段、`stock-reduce-planner-adapter-design.md` §7.3.10）。

「还可能缺」的依赖见 **purchase-planner-adapter-design.md §11.8**。

---

## 25. 阶段 C-20 / **C-21** / **C-22** / **C-24（Hydrated RealBridge）** / **C-46（GROUP Hydrated 单域）**：StockReduce Planner Adapter

**C-20**：仅文档梳理 — **[`docs/ai/stock-reduce-planner-adapter-design.md`](./stock-reduce-planner-adapter-design.md)**（生产 `stock_reduce_query` 链路与语义）。

**C-21（已落地）**：`com.nongxinle.ai.planner` 下 **`StockReducePlannerReadStatus`**、**`StockReducePlannerVisibleStore`**、**`StockReducePlannerReadRequest`**、**`StockReducePlannerReadResponse`**、**`StockReducePlannerExecutionContext`**、**`StockReducePlannerReadBridge`**、**`FakeStockReducePlannerReadBridge`**、**`StockReducePlannerAgentAdapter`**；**`PlannerExecutionPlan` / `PlannerStepExecutionRequest` / `PlannerAgentAdapterRequest` / `PlannerExecutor`** 已透传 **`stockReduceReadRequest`** / **`stockReduceExecutionContext`**（trace 内 sanitization 对称采购）。**不**改 Master / Resolver / Composer。**Harness**：**`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_CORE`**、**`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_FAKE_OK_CORE`**；**`AiHarnessReplayMode.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER`**。

**C-22（已落地，骨架）**：**`StockReducePlannerRealReadBridge`** — Harness **`new`**（无依赖）时 **`readWithExecutionContext`** 在上下文齐全处仍 **`STOCK_REDUCE_REAL_READ_BRIDGE_SKELETON`**；缺 `AiRunState`/`AiResolvedQueryContext` 时 **`ADAPTER_NO_RUN_STATE`** / **`ADAPTER_NO_RESOLVED_CONTEXT`**。**Harness**：**`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_CORE`**（默认不 Hydrate → 可控 `DEGRADED`；摘要 **`plannerStockReduceAdapterHonesty=REAL_BRIDGE_HARNESS_INCOMPLETE_CONTEXT`**）。

**C-24（已落地，Hydrated；curl 已验收）**：**`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** — **`AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase`**；**`StockReducePlannerRealReadBridge`** 为 **`@Component`** 并由 **`AiHarnessReplayService`** 注入时走 PlannerExecutor → Adapter → Bridge → **`buildStockReduceRequestContext` → `executeStockReduceQuery` → `StockReduceQueryTool` → `StockReduceAnswerPlanBuilder#attachIfApplicable`**。**已观测**：**STORE**、**1/AAA**、**`overallStatus=SUCCESS`**、**`degradedSteps=[]`**、**`usedTools` 含 `stock_reduce_query`**、**`plannerStockReduceAdapterHonesty=REAL_BRIDGE_HYDRATED_STOCK_REDUCE_TOOL_OK`**。**§12** C-24 条目；**限制 / DishProfit 模板**见 **`stock-reduce-planner-adapter-design.md` §7.4**、**§9**。

**C-46（已落地，GROUP Hydrated 单域探测；非 Composite）**：**`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE`** — **`AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase`**：**`scopeType=GROUP`**、可见门店根 **1+3**、**`AiResolvedDataScope.fromOrgScope`**、**`AiRunState.departmentId=null`**、**`groupStockReduceQuery=true`**（与 C-24 **`false`** 对照；生产 **`StockReduceQueryToolExecutor#buildHarnessToolArgs`** 据此写 **`ARG_GROUP_STOCK_REDUCE_AGGREGATION`** + 多店 **`ARG_RESOLVED_DEPARTMENT_IDS`**）；**`StockReducePlannerReadRequest`**：**`targetStoreDepartmentId=null`**，**`queryDepartmentIds=[1,3]`**，**`totalsBasis=CALENDAR_NATURAL_DAY`**；**`StockReducePlannerRealReadBridge`** Bean → **`stock_reduce_query`**；诚实 **`REAL_BRIDGE_HYDRATED_STOCK_REDUCE_GROUP_TOOL_OK` / `…_GROUP_TOOL_DEGRADED`**；**`resolveReplayMode`** 仍为 **`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER`**。**不接** Composite / LLM / Master。详表 **`business-diagnosis-composite-group-design.md` §13**、**`stock-reduce-planner-adapter-design.md` §7.3.10**。

### 25.1 与生产对齐的 recommended 编排（C-24 **Spring Bean** 接 Executor/DB）

```text
PlannerExecutor（ADAPTER + PlannerAgentAdapterStepExecutor）
  → StockReducePlannerAgentAdapter
       → StockReducePlannerRealReadBridge#readWithExecutionContext（**Bean**；C-22 `new` 仍为骨架）
            → BusinessToolExecutionRequestResolver#buildStockReduceRequestContext
            → StockReduceQueryToolExecutor#executeStockReduceQuery
            → StockReduceQueryTool
            → StockReduceAnswerPlanBuilder#attachIfApplicable
```

### 25.2 Harness caseId

| caseId | 状态 |
|--------|------|
| `PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_CORE` | **C-21** 已注册 |
| `PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_FAKE_OK_CORE` | **C-21** 已注册 |
| `PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_CORE` | **C-22** 已注册（骨架；不调用 Tool） |
| `PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` | **C-24** 已注册（见 `stock-reduce-planner-adapter-design.md` §7.3） |
| `PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE` | **C-46** 已注册（见 §25 正文 C-46 段、`stock-reduce-planner-adapter-design.md` §7.3.10） |

### 25.3 详设入口

- **Hydrated 最小字段、curl 验收表、必填上下文**：**`stock-reduce-planner-adapter-design.md` §7.3.0**、**§7.3.7**。
- **当前限制（STORE-only、`stock_reduce_overview`、`CALENDAR_NATURAL_DAY`、未接 Master 等）**：**`stock-reduce-planner-adapter-design.md` §7.4**。
- **`DishProfitPlannerRealReadBridge` Hydrated（C-29）**：权威最小上下文见 **`dish-profit-planner-adapter-design.md` §7**；**`stock-reduce-planner-adapter-design.md` §9** 仍为跨域模板指针。
- **链路与语义表**、**DTO**、**禁止项**：见 **`stock-reduce-planner-adapter-design.md` 全文**。

---

## 26. 阶段 **C-25**（设计）+ **C-26～C-29**（骨架 / Hydrated）：DishProfit / 菜品毛利 Planner Adapter

**C-25**：**仅文档** — **[`dish-profit-planner-adapter-design.md`](./dish-profit-planner-adapter-design.md)** 梳理生产 **`dish_profit_analysis`** 链路（**`DishProfitAgent` → `buildDishProfitRequestContext` → `DishProfitQueryToolExecutor` → `DishProfitAnalysisTool` / `GbDepFoodBusinessInsightService#buildInsight` → `DishProfitAnswerPlanBuilder`**），设计 **`DishProfitPlannerReadRequest` / `ReadResponse` / `ExecutionContext` / `DishProfitPlannerAgentAdapter` / `DishProfitPlannerReadBridge`** 边界；**STORE 单店** Hydrated **设计范围**；**GROUP 多门店** 暂不纳入 v1。**禁止**：Adapter 解析 `userMessage`、Bridge 内新 SQL 或绕过 **`ResolvedQueryContext`** 与现有 **Tool / Executor / AnswerPlan** 链。

**C-27（RealBridge 骨架，已落地）**：**`DishProfitPlannerRealReadBridge`**（`readWithExecutionContext`；上下文缺失 → **`ADAPTER_NO_RUN_STATE` / `ADAPTER_NO_RESOLVED_CONTEXT`**；**`new`** 无 Bean → **`DISH_PROFIT_REAL_READ_BRIDGE_SKELETON`**）。**Harness**：**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE`**。**不**物化上下文时 **不**调用 **`DishProfitQueryToolExecutor`**。

**C-28 / C-29（Hydrated RealBridge）**：**C-28** 最小上下文设计见 **`dish-profit-planner-adapter-design.md` §7**。**C-29（已落地并已 curl 验收）**：**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`**；**`AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase`**；Spring Bean **`DishProfitPlannerRealReadBridge`** 经 **`PlannerExecutor` → `DishProfitPlannerAgentAdapter`** 走 **`executeDishProfitAnalysis`** → **`attachForAgentEnvelope(state, false)`**；摘要 **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_OK`** / **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_DEGRADED`**。与 **营收 / 采购 / 出库** 三条 Hydrated RealBridge 并列，见 **§12** 首段、**`dish-profit-planner-adapter-design.md` §7.9**。

**C-26（已落地，骨架）**：`com.nongxinle.ai.planner` 下 **`DishProfitPlannerReadStatus`**、**`DishProfitPlannerVisibleStore`**、**`DishProfitPlannerReadRequest`**、**`DishProfitPlannerReadResponse`**、**`DishProfitPlannerExecutionContext`**、**`DishProfitPlannerReadBridge`**、**`FakeDishProfitPlannerReadBridge`**、**`DishProfitPlannerAgentAdapter`**；**`PlannerExecutionPlan` / `PlannerStepExecutionRequest` / `PlannerAgentAdapterRequest` / `PlannerExecutor`** 已透传 **`dishProfitReadRequest`** / **`dishProfitExecutionContext`**（trace sanitize 对称出库）。**C-26/C-27 骨架路径** **不**接 **`DishProfitQueryToolExecutor`**；**C-29 Hydrated Bean** 接 Tool。**不**改 Master / Resolver / Composer。**Harness**：**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_CORE`**、**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE`**、**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE`**、**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`**；**C-47**：**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE`**（**`AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase`**）；**`AiHarnessReplayMode.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER`**。

### 26.1 Harness `caseId`

| caseId | 状态 |
|--------|------|
| `PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_CORE` | **C-26** 已注册（无 Bridge → `ADAPTER_NO_REAL_CONTEXT`） |
| `PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE` | **C-26** 已注册（`FakeDishProfitPlannerReadBridge`） |
| `PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE` | **C-27** 已注册（`DishProfitPlannerRealReadBridge` 骨架；默认不 Hydrate → 诚实降级；不调 Tool） |
| `PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` | **C-29** 已注册（**curl 已验收**；**`AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase`**；Bean **`DishProfitPlannerRealReadBridge`**；步 **`step_dish_profit_adapter_hydrated`**；真实 **`dish_profit_analysis`**；**`dishProfitPath=true`**；摘要 **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_OK`** / **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_DEGRADED`**；限制 **§7.7**） |
| `PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE` | **C-47** 已注册（**`AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase`**；**GROUP** 可见根 **1+3**；**`AiRunState.departmentId=null`**；**`aiUserContext=GROUP_MANAGER`**；诚实 **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_GROUP_TOOL_OK` / `…_GROUP_TOOL_DEGRADED`**；详 **§7.10**、**`business-diagnosis-composite-group-design.md` §14**） |

### 26.2 详设入口

- **生产链路、语义 wire、禁止项、Hydrated 最小上下文、curl 验收、当前限制**：**`dish-profit-planner-adapter-design.md` 全文**（**C-25** 权威；**§7** **C-28 设计 + C-29 实装与收口**，**§7.0 / §7.4.1 / §7.5 / §7.7 / §7.9 / §7.10（C-47 GROUP）**）。

---

## 27. 阶段 **C-30**（设计）+ **C-31**（Harness MOCK 骨架）+ **C-32～C-35**（Composite + Hydrated 渐进真实）+ **C-36**（Composite AnswerPlan）+ **C-42**（出库降级 Harness）+ **C-43**（GROUP 多店 **仅设计**）+ **C-62**（普通 Run **`SHADOW` 名单/限流**，composite §16）+ **C-63**（**`ShadowPolicy`** 接线，composite **§17 已编码**）+ **C-64**（**`SHADOW` 灰度上线策略**，composite **§18** + **[`business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md)**）+ **C-65**（**灰度观测与复盘清单**，composite **§19** + **[`business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md)**）：组合型经营诊断 **Composite Plan**

### 27.1 阶段收口（**C-50～C-65**）· 下一阶 **D-1**

- **已完成（本窗口）**：**C-50～C-65** 形成 **Composite 生产接入安全框架** — **Readonly Composer（C-50/C-51）**、**Gate（C-52 起）**、**`HARNESS_ONLY`（C-58）**、**`SHADOW` + `compositeShadow*`（C-60/C-61）**、**`ShadowPolicy`（C-63）**、**[`business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md)（C-64）**、**[`business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md)（C-65）**；**STORE / GROUP** Composite Harness 已跑通。  
- **明确暂缓**：**C-66** — 集中式 **metrics**、**dashboard**、**Redis 跨实例限流** 等 **不继续排入当前迭代**；记入 backlog，待有真实灰度需求再开窗。  
- **下一阶段**：自 **D-1** 起 **回到主业务能力建设**（问法、深挖、多轮、前台与老板问题清单等）— 见 **[`next-business-capability-roadmap.md`](./next-business-capability-roadmap.md)**；会话交接见 **[`PROJECT_HANDOFF_D1.md`](./PROJECT_HANDOFF_D1.md)**。

**C-30 状态**：**只写设计文档**（**[`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md)** 全文）。

**C-31 状态**：**已**实装 Harness **全 MOCK** 计划骨架（**不接**四条 Hydrated RealBridge、**不**调真实 Tool / LLM / SQL）。**C-31.1**：收紧 trace **`usedTools`** 口径（前四步 **`mock_*_hydrated_adapter`**），与生产 Tool 执行 **显式区分**。**C-32**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE`** — **仅**营收步 Hydrated RealBridge + 真实 **`revenue_query`**；采购 / 出库 / 菜品 / 诊断 / 建议仍 **mock**（**[`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md) §2.1**）。**C-33**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE`** — 营收 + 采购 Hydrated 真实（**`revenue_query`** + **`purchase_overview`**）；出库 / 菜品 / 诊断 / 建议 **mock**（**同文档 §2.2**）。**C-34**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_STOCK_CORE`** — 营收 + 采购 + 出库 Hydrated 真实（**`revenue_query`** + **`purchase_overview`** + **`stock_reduce_query`**）；菜品 / 诊断 / 建议 **mock**（**同文档 §2.3**；摘要 **`harnessReplayMode=PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER`**）。**C-35**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`** — 四数据域 Hydrated 真实（**STORE**）+ **诊断 / 建议** **`mock_*`**（**同文档 §2.4**；**`harnessReplayMode=PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER`**）。**C-48**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** — 与 **C-35** **同六步与同 `resolveReplayMode`**，**`scopeType=GROUP`**、四域上下文复用 **C-44～C-47**；**`plannerCompositeHonesty=COMPOSITE_GROUP_ALL_DATA_REAL_DIAGNOSIS_DETERMINISTIC`**（**同文档 §2.6**）。

**C-36 状态**：**仅设计文档** — **[`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md)**：**`BusinessDiagnosisCompositeAnswerPlan`** 字段（**`type=BUSINESS_DIAGNOSIS_COMPOSITE`**、**四域 summary**、**`diagnosisSignals`（五类 signal + `sourceStep`/`severity`/`reason`/`evidenceRefs`）**、**`dataCoverage`（每域 `success`/`realToolInvoked`/`stepId`/`usedTool`/`degradedReason`）**、**`riskLevel`/`summaryText`/`keyFindings`/`suggestedNextQuestions`/`degradedSteps`/`debug`**）；缺失域 **不编造**、**整体可生成但必须降级口径一致**；**不接** LLM / Master / Composer。**C-37**：Java 映射 + `step_diagnosis_compose` **确定性** + Composer **只读 AnswerPlan**（**[`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md) §8**）。**C-38**：**`BusinessDiagnosisCompositeAnswerPlanBuilder`** 四域 summary **从 AnswerPlan + `toolResults` 真值映射**；**`debug.mappingNotes`** 记录缺口（见 **同文档 §8.7**）。**C-38.2 / C-39**：summary **零值 vs 缺失** 诚实标注（**§8.7**）；**C-39** 最小 **`diagnosisSignals`** + **`riskLevel` 聚合**（**§8.8**，**不**调 LLM）。**C-40**：**`summaryText`** + Harness **`businessDiagnosisSummaryText`**（**§8.9**）。

**权威**：**[`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md)**；Composite **AnswerPlan** 权威：**[`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md)**。

**C-41 阶段总收口（仅文档）**：**C-30～C-40** 路线表、最终链路、**ALL_REAL_CORE** curl 观测、限制、原则与 **C-42～C-46** 见 **[`planner-executor-composite-c30-c40-summary.md`](./planner-executor-composite-c30-c40-summary.md)**。

**C-42**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeStockDegradedGraphCase#buildPlan`**；**`CompositeBusinessDiagnosisStockDegradedHarnessHybridPlannerStepExecutor`**（出库步固定 **DEGRADED**，不调 **`stock_reduce_query`**）；全 trace **`overallStatus=DEGRADED`**；**`plannerCompositeHonesty=COMPOSITE_STOCK_DEGRADED_DIAGNOSIS_DETERMINISTIC`**（详见 **`business-diagnosis-composite-plan-design.md` §2.5**）。

**C-43**：**GROUP 多门店** — **规格**（**`scopeType=GROUP`**、**`visibleStores`**、**`dataScope`**、**`summaryText`** 安全措辞、降级与 trace）— 权威 **[`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md)**。**C-48**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** — **已**注册 **`AiHarnessBuiltinCases`** + **`isPlannerExecutorMockHarnessCase`**；**`AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase#buildPlan`** + **`AiHarnessReplayPlannerExecutorMock`**；六步与 **C-35** 同构，四域 **GROUP Hydrated** 与 **C-44～C-47** 单域 **物化一致**；**`plannerCompositeHonesty=COMPOSITE_GROUP_ALL_DATA_REAL_DIAGNOSIS_DETERMINISTIC`**；**不**影响 **ALL_REAL**（STORE）。**AnswerPlan / `summaryText`**：**[`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md) §8.11**。

**C-50 / C-51**：**Composite Composer** — **C-50** 设计见 **[`business-diagnosis-composer-readonly-design.md`](./business-diagnosis-composer-readonly-design.md)**；**C-51** 实装 **`BusinessDiagnosisCompositeReadonlyComposer`**（**`COMPOSER_VERSION=C-51_READONLY_COMPOSER`**）与 **`BusinessDiagnosisCompositeComposeResult`**；**只读** **`BusinessDiagnosisCompositeAnswerPlan`**，**禁止** **重读** **`toolResults`**；Harness **`AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase`** / **`AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase`** 的 **`toHarnessSummary`** 另输出 **`businessDiagnosisFinalAnswerText`**、**`businessDiagnosisComposerVersion`**；**不接** Master / 前台 / LLM 主链路。

**C-52**：**生产入口 Composite Gate** — 设计权威 **[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md)**。**Resolver / BusinessDataPlanner 之后**、**四域 Tool 执行之前**、**Master 真正调用 Composite `PlannerExecutor` 之前**（概念序），判定是否进入 **Composite**；**只读**结构化上下文，**禁止**用户原文 **`contains`/`regex`**；**STORE / GROUP** 条件、**fallback**、**降级诚实**、**SSE 调试字段** 详见该文。

**C-53**：**Java skeleton（只判不断）** — **`com.nongxinle.ai.planner.BusinessDiagnosisCompositeProductionGate#evaluate`** + **`BusinessDiagnosisCompositeGateResult`** + **`BusinessDiagnosisCompositeGateReasonCode`**；入参 **`AiResolvedQueryContext`**、可选 **`AiRunState`**、**`boolean compositeProductionEnabled`（默认由调用方传 false）**；**未**从 **Master** 调用；**不**执行 Tool / **不**调 LLM / **不**改 **`AiRunState`**。规则与 **C-52.1 §3.3** 白名单一致，见 **production-gate-design** 文档 **§C-53**。

**C-54**：**Harness-only Gate replay** — **`AiHarnessReplayCompositeGate`** + **`AiHarnessReplayMode#BUSINESS_DIAGNOSIS_COMPOSITE_GATE`** + 八个 **`BUSINESS_DIAGNOSIS_COMPOSITE_GATE_*`** **`caseId`**（见 **`business-diagnosis-production-gate-design.md` §C-54**）。**仅**调用 **`evaluate`**；响应 **`harnessRootSummary`**（及首轮 **`resolvedQueryContextSummary` 镜像**）输出 **`gateAllowed` / `gateReasonCode`** 等；**不**执行 **`PlannerExecutor`** / Tool / Composer；**不**接 Master / 前台 / LLM。

**C-55**：**生产主链路 Gate 关闭态骨架（仅观测）** — **`AiRunService`** 在 Resolver 之后、**Business Graph / Tool** 之前调用 **`BusinessDiagnosisCompositeProductionGate.evaluate`**，将 **`BusinessDiagnosisCompositeGateResult`** 写入 **`AiRunState`** 及 **`AiHarnessResolvedContextSummarizer`** / SSE（**`compositeGate*`**）；**`ai.composite.businessDiagnosis.productionEnabled` 默认 false**（**`FEATURE_FLAG_DISABLED`**）；**不**改 **`MasterBusinessAgent` / DataPlanner** 路由、**不**执行 Composite **`PlannerExecutor`**、**不**变最终用户回答；详情 **§C-55**，**C-56** 配置化 / 灰度 / 生产接线。

**C-56.1**：**降低 Gate feature flag 测试成本（设计）** — 见 **`business-diagnosis-production-gate-design.md` §C-56.1**。

**C-56.2**：**Harness-only override（已实装）** — **`AiHarnessReplayRequest#compositeProductionGateProductionEnabledOverride`**；**仅** **`GRAPH_RUN`** → **`executeBusinessGraphSyncForHarness`**；**`effectiveEnabled = override != null ? override : ${ai.composite.businessDiagnosis.productionEnabled:false}`**；**`compositeGateProductionEnabledSource` / `compositeGateProductionEnabledEffective`** 与 **`gateDebug.productionEnabled*`** 可观测；**普通 `/api/ai/runs` 不受影响**。**C-58** 扩展：同路径可选 **`compositeBusinessDiagnosisExecutionMode=HARNESS_ONLY`** 才 **执行** Composite **`PlannerExecutor`**（详见 **C-58** 下条）。权威 **`business-diagnosis-production-gate-design.md` §C-56.2 / §C-58**。

**C-57**：**Gate `allowed=true` 后的生产 Composite 执行与灰度（设计）** — **`BusinessDiagnosisCompositePlanFactory`**（从 **`AiRunState` / `AiResolvedQueryContext` / GateResult** 物化 **`PlannerExecutionPlan`**，**非** Harness **`GraphCase`**）、**`BusinessDiagnosisCompositeExecutionService`**（**`BusinessDiagnosisCompositeExecutionMode`**：**`OFF` / `HARNESS_ONLY` / `SHADOW` / `PRIMARY`**）、**`BusinessDiagnosisCompositeExecutionResult`**、配置 **`ai.composite.businessDiagnosis.executionMode`**（默认 **`OFF`**；**C-58** Harness 另用 **请求字段**；**C-60 Spring `SHADOW`** 接 **`executeRun`**) 与 **`fallbackToLegacyOnFailure`**；**SHADOW** 与 legacy **四域双跑** 读放大及 **PRIMARY** **目标态去重** 见 **`business-diagnosis-production-composite-execution-design.md`**；**Gate** **§C-57**。

**C-58**：**Harness-only Composite 执行（已实装 HARNESS_ONLY）** — **`AiHarnessReplayRequest#compositeBusinessDiagnosisExecutionMode`**；**仅** **`GRAPH_RUN`**、**`AiRunService#executeBusinessGraphSyncForHarness`** 图成功后：**`BusinessDiagnosisCompositePlanFactory` → `PlannerExecutor(ADAPTER, CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor)` → 四域 **`*PlannerRealReadBridge` → `BusinessDiagnosisCompositeReadonlyComposer`**；写入 **`AiRunState#businessDiagnosisCompositeExecutionResult`**；**`AiHarnessResolvedContextSummarizer`** 输出 **`compositeExecution*`**；**不替换** **`finalAnswerText`**。**`PRIMARY`** **仍不接**。**已通过真实 GRAPH_RUN curl 观测**：**`compositeGate*`** / **`compositeExecution*`** / **`compositeFinalAnswerText`** 等均可用；**legacy `answerPreview`（主图终稿摘要）未被 Composite 替换**。判读时参见 **`business-diagnosis-production-composite-execution-design.md` §12.1～§12.2**：**勿以 **`overallPass`** 误判 C-58**。权威 **`business-diagnosis-production-composite-execution-design.md` §12**、**`business-diagnosis-production-gate-design.md` §C-58**。

**C-59**：**`SHADOW` 语义（设计）** — 普通 **`/api/ai/runs`**，**`productionEnabled=true`**，Spring **`executionMode=SHADOW`**，Gate **`allowed=true`** 时 **旁路**与 C-58 **同栈** Composite；**用户终稿仍为 legacy**；观测 **`composite*`**；**读放大** composite **§13.3**；**失败不反噬 legacy** composite **§13.5**。**权威**：composite **§13**、gate **§C-59**。**C-60（已实装）**：**`AiRunService#executeRun`** → **`maybeExecuteShadowCompositePlanner`**（**同步**，可后续异步）；**`summarizeCompositeGateAndExecutionOnly`** 入 **SSE**；**PRIMARY**：**`tryExecute` 入口早退** **`executed=false`**（不接 PlannerExecutor）。**权威**：composite **§14**、gate **§C-60**。**C-61（已实装）**：**`compositeShadow*`** — **`compositeShadowLatencyMs`**、**`compositeShadowLegacyAnswerPresent`**、**`compositeShadowCompositeAnswerPresent`**、**`compositeShadowComparedWithLegacy`**、**`compositeShadowFinalAnswerReplaced`**（恒 **`false`**）；**mergeCompositeShadowObservationFields**；全文 **composite §15**，**curl/SSE** 核对。

**C-62（§16：设计语义）**：补足 **「谁可以旁路、多频」**：**名单**（**`userId` / `distributerId` / **`departmentWhitelist`**、**可见门店 dept**、可选 **`scopeWhitelist`**）与 **配额**（Spring **`shadow.maxRunsPerMinute`** / **`shadow.maxRunsPerHour`** / **`shadow.cooldownSeconds`**；进程内 MVP）。**intent/path/ref** **仍只允许** **`BusinessDiagnosisCompositeProductionGate` 的输出**。**SKIP** ⇒ **不 `tryExecute`**；**legacy **`finalAnswerText` / `answerPreview`** 不变**；观测 **`compositeShadowSkipped`** 等。详见 composite **§16**、gate **§C-62（索引）**。

**C-63（§17：已编码；§17.2：手工验收已通过）**：**`ShadowPolicy` / `ShadowDecision`**，在 **`AiRunService#maybeExecuteShadowCompositePlanner`** 内 **`tryExecute` 之前**；仅读 **`AiRunState`** / **`AiResolvedQueryContext`** / 配置；**不读用户原文**；**不接 PRIMARY**；**默认 **`shadow.enabled=false`** ⇒ **SKIP**。Harness **`HARNESS_ONLY`** 不经由 **`ShadowPolicy`**。**观测**：**`POST /api/ai/runs` + `GET .../events`** 三轮（**shadow 关 / whitelist 命中 / whitelist 未命中**）与 **composite §17.2** 表一致；**`compositeShadowFinalAnswerReplaced=false`**；**whitelist 命中**样例旁路墙钟 **≈ 27s**（**`compositeShadowLatencyMs=27059`**），扩 **SHADOW** 灰度须关注 **性能与读放大**（composite **§13.3 / §16**）。**权威**：composite **§17**。

**C-64（仅文档）**：**`SHADOW` 灰度上线策略** — 谁可开、STORE/GROUP 分时、单场景、`maxRunsPerMinute` / `maxRunsPerHour` / **`cooldownSeconds`** 保守起步、必看 **`compositeShadowLatencyMs`** / **`compositeExecutionSuccess`** / **`compositeFallbackRequired`** / 错误码与 **`compositePlannerDegradedSteps`** / **`usedTools`** / **`compositeFinalAnswerText` 非空** / **legacy 终稿**，及 **立即关闸**（耗时、DB、失败率、Tool 放大、SSE/前台、GROUP 口径错、疑似替换 legacy）。gate **§C-64**。**权威**：[**`business-diagnosis-shadow-rollout-plan.md`**](./business-diagnosis-shadow-rollout-plan.md)。

**C-52.1**：**Gate 意图 / path / `structuredIntentDetail` 与现网常量对齐表** — 同上文档 **§3.3**（**`AiResolvedQueryIntent` / `AiQuerySemanticLexicon` / `AiResolvedQueryContext.orchestration*`**）；**无** `BUSINESS_OVERVIEW_COMPOSITE` 等占位 query intent；**`BUSINESS_DIAGNOSIS_COMPOSITE`** 仅为 **AnswerPlan `type`**（**`BusinessDiagnosisCompositeAnswerPlan`**），**非** **`intentCode`**。

**C-65（仅文档）**：**`SHADOW` 灰度观测与复盘清单** — 每 **`SHADOW`** 批次须记录的 **`userId` / `distributerId` / `departmentId` / `scopeType`**、**`compositeGate*`**、**`compositeExecuted`**、**`compositeExecution*`**、**`compositePlanner*`**、**`compositeShadow*`**、**`compositeFinalAnswerText` 非空**与 **legacy 正常**；**每日复盘表**（总量、Gate allowed、实际旁路次数、skipped、成功率、均值/**P95**、降级域、**Top errorCode**、scope 口径与 legacy）；**扩大灰度**（连续 24～72h 无 legacy 影响、成功率、P95、无 GROUP/STORE 口径错误、DB/Tool 压力）；**暂停灰度**（耗时、Tool 放大、降级率、GROUP→单店错、SSE/前台、疑似替换 legacy）。gate **§C-65**。composite **§19**。**权威**：[**`business-diagnosis-shadow-observation-checklist.md`**](./business-diagnosis-shadow-observation-checklist.md)。

**caseId**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE`** — **`AiHarnessBuiltinCases`** + **`isPlannerExecutorMockHarnessCase`**；**`AiPlannerExecutorBusinessDiagnosisCompositeGraphCase#buildPlan`**；**`AiPlannerExecutorMockGraphCase#planForHarnessCase`** 分支。**C-32**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeRevenueGraphCase#buildPlan`**；**`AiHarnessReplayPlannerExecutorMock`** 专用分支（须 **`RevenuePlannerRealReadBridge`** Bean）。**C-33**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseGraphCase#buildPlan`**；**`AiHarnessReplayPlannerExecutorMock`** 专用分支（须 **双** Bean：**`RevenuePlannerRealReadBridge`** + **`PurchasePlannerRealReadBridge`**）。**C-34**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_STOCK_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseStockGraphCase#buildPlan`**；**`AiHarnessReplayPlannerExecutorMock`** 专用分支（须 **三** Bean：营收 + 采购 + **`StockReducePlannerRealReadBridge`**）。**C-35**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase#buildPlan`**；**`AiHarnessReplayPlannerExecutorMock`** 专用分支（须 **四** Bean：+ **`DishProfitPlannerRealReadBridge`**）。**C-48**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase#buildPlan`**；**`AiHarnessReplayPlannerExecutorMock`** 专用分支（须与 **C-35** 相同 **四** Bean）；**`resolveReplayMode`** 与 **C-35** 同为 **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER`**。**C-42**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE`** — **`AiPlannerExecutorBusinessDiagnosisCompositeStockDegradedGraphCase#buildPlan`**；**`AiHarnessReplayPlannerExecutorMock`** 专用分支（**四** Bean；**`CompositeBusinessDiagnosisStockDegradedHarnessHybridPlannerStepExecutor`**；出库步 **不调** **`stock_reduce_query`**，trace **`overallStatus=DEGRADED`**）。

**目标（C-30 目标态）**：固定多步 **`PlannerExecutionPlan`**，在 **同一 STORE（`departmentId=1` / AAA）、同一 `timeWindow`、同一 `AiResolvedQueryContext`（同构）** 下复用四条已 **curl 验收** 的 **Hydrated RealBridge** — **`revenue_query`**、**`purchase_overview`**、**`stock_reduce_query`**、**`dish_profit_analysis`** — 再经 **`step_diagnosis_compose`（mock / skeleton，无 LLM）** 与 **`step_recommendation`（mock）** 聚合；**`finalAnswerPlanType` = `BUSINESS_DIAGNOSIS_COMPOSITE`**；trace 含逐步 status、**`usedAgents` / `usedTools` / `degradedSteps` / `overallStatus`**。

**C-31 当前**：六 **`stepId`** 为 **`step_revenue_hydrated`** … **`step_recommendation`**；**均为 `PlannerExecutor` MOCK SUCCESS**；**`failureStrategy` = `CONTINUE_WITH_DEGRADED`**；**`step_diagnosis_compose`** 带 **`answerPlanRef`** 占位；Replay 根 **`plannerCompositeHonesty` / `plannerCompositeNote`** 标明骨架诚实性。**C-31.1**：汇总 **`usedTools`** = **`mock_revenue_hydrated_adapter`**、**`mock_purchase_hydrated_adapter`**、**`mock_stock_reduce_hydrated_adapter`**、**`mock_dish_profit_hydrated_adapter`**、**`mock_diagnosis_compose`**、**`mock_build_recommendation_plan`**（均为 MOCK echo，**非**生产 Tool 执行凭证）。**诊断 / 建议步**：**`targetAgent`** 可与生产枚举对齐，但 **仅** Mock 执行器合成 **`usedTools`**（**`mock_diagnosis_compose`** / **`mock_build_recommendation_plan`**），**不**表示已跑 LLM 或真实业务 Action — 见各 GraphCase **`inputSummary` / `acceptanceCriteria`**。**C-32**：汇总 **`usedTools`** 含 **`revenue_query`**（营收步）+ 上述 **`mock_*`**（其余五步）；**`plannerCompositeHonesty=COMPOSITE_REVENUE_REAL_ONLY`**；营收失败路径诚实 **`DEGRADED`** / **`FAILED` 吸收为 `DEGRADED`**（**`CONTINUE_WITH_DEGRADED`**），**不假** SUCCESS。**C-33**：汇总 **`usedTools`** 含 **`revenue_query`**、**`purchase_overview`** + 后四步 **`mock_*`**；**`plannerCompositeHonesty=COMPOSITE_REVENUE_PURCHASE_REAL_ONLY`**；**双**真实步任一方失败同上诚实降级，**不假** SUCCESS。**C-34**：汇总 **`usedTools`** 含 **`revenue_query`**、**`purchase_overview`**、**`stock_reduce_query`** + 后三步 **`mock_*`**；**`plannerCompositeHonesty=COMPOSITE_REVENUE_PURCHASE_STOCK_REAL_ONLY`**；**三**真实步任一方失败同上诚实降级，**不假** SUCCESS。**C-35**：汇总 **`usedTools`** 含四生产 Tool id + **`mock_diagnosis_compose`**、**`mock_build_recommendation_plan`**；**`plannerCompositeHonesty=COMPOSITE_ALL_DATA_REAL_DIAGNOSIS_MOCK`**；任一台阶真实步失败同上诚实降级，**不假** SUCCESS。**C-48（GROUP + 四域真实）**：与 **C-35** **相同** **`usedTools`** 模式（四生产 Tool + **`mock_diagnosis_compose`** + **`mock_build_recommendation_plan`**）；**`plannerCompositeHonesty=COMPOSITE_GROUP_ALL_DATA_REAL_DIAGNOSIS_DETERMINISTIC`**；上下文 **GROUP** / 双店可见性 见 **`business-diagnosis-composite-group-design.md`**。**C-42（出库 Harness 降级）**：汇总 **`usedTools`** 含 **`revenue_query`**、**`purchase_overview`**、**`dish_profit_analysis`** + **`mock_diagnosis_compose`**、**`mock_build_recommendation_plan`**；**不**含 **`stock_reduce_query`**（出库步 **DEGRADED**、`usedTools` **空**）；**`plannerCompositeHonesty=COMPOSITE_STOCK_DEGRADED_DIAGNOSIS_DETERMINISTIC`**；全轮 **`overallStatus=DEGRADED`**。

**失败策略（设计）**：四数据步建议 **`CONTINUE_WITH_DEGRADED`**；诊断步在关键数据不足时 **`DEGRADED`**；**禁止假装缺失数据存在**。

**当前不做**：普通 Run **仍不**因 **`PRIMARY`** Spring 配置 **执行** Composite（**`PRIMARY` 不接**）。**`HARNESS_ONLY` 仅 Harness `GRAPH_RUN`**（**C-58**）。**C-60 `SHADOW`** **已**接 **`executeRun`**（**同步**旁路）；**C-63 **`ShadowPolicy`** 在 **`shadow.enabled=false`**（默认）或名单/限流 **SKIP** 时不调用 **`tryExecute`**，legacy 不受影响；**放行后**仍按 composite **§13.3** 读放大。**C-62～C-65**：**composite §16（设计）+ §17（`ShadowPolicy` 已编码）+ §18 **[`business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md)** + §19 **`business-diagnosis-shadow-observation-checklist.md`**（**C-65** 日复盘准入/暂停）。**C-66+**（metrics / dashboard / Redis 跨实例限流 / legacy **`toolResults` 只读复用**）— **本窗口暂缓**，见 rollout **§7** 仅为 **未来 TODO**。**C-57 **`PRIMARY`** **主终稿替换** **仍**未绑。**`C-48`** 的 GROUP Composite **PlannerExecutor harness case** **仍**独立于 **`AiPlannerExecutorBusinessDiagnosisComposite*GraphCase`**。**生产** **`BusinessDiagnosisCompositePlanFactory`** **不得**抄写 GraphCase **硬编码** 门店与时间。不做前台、不做 LLM 自由规划、不做真实 action、**不新 SQL**、不改 Resolver / Composer 主逻辑、**不**在 Composite 层新增用户原文 **contains/regex**（详见 **`business-diagnosis-composite-plan-design.md` §9**）。

---

## 附录 A — 本轮禁止项（与阶段 B 冻结一致）

```text
- 不改 Java 业务代码（本轮）
- 不改 SQL（本轮）
- 不改前台（本轮）
- 不改已有四个 DomainAgent
- 不改 MasterBusinessAgent 主逻辑
- 不改 Resolver（AiResolvedQueryContextResolver 主逻辑）
- 不改 Composer 主模板
- 不新增用户原文 contains / regex 判断（意图仍由解析层与模板路由）
- 不做真实通知 / 调价 / 退款 / 提交订单 / 删除数据
```

---

## 附录 B — 参考文档

- `docs/ai/harness-composer-architecture.md` — 分层与 AnswerPlan / DiagnosisPlan  
- `docs/ai/master-business-agent-design.md` — Master 与子 Agent 契约  
- `docs/ai/diagnosis-answer-plan.md` — DiagnosisPlan 聚合边界  
- [`docs/ai/purchase-planner-adapter-design.md`](./purchase-planner-adapter-design.md) — C-15 梳理 + **C-16/C-17** 采购 Planner 实装 + **C-18/C-19** Hydrated（**§11 字段权威；§12 C-19 curl 收口 + StockReduce/DishProfit 模板**）
- [`docs/ai/stock-reduce-planner-adapter-design.md`](./stock-reduce-planner-adapter-design.md) — **C-20～C-24** 出库/核销 Planner（**C-24** Hydrated RealBridge curl 已验收；§9 DishProfit 模板指针）
- [`docs/ai/dish-profit-planner-adapter-design.md`](./dish-profit-planner-adapter-design.md) — **C-25** 菜品毛利 Planner Adapter 生产梳理 + **C-26～C-27** Harness 骨架 + **C-28** §7 设计 + **C-29** Hydrated 实装与 **curl 收口**（**§7.0～§7.9**；**§26** 摘要）
- [`docs/ai/business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md) — **C-30** 组合型经营诊断 **Composite Plan** 设计 + **C-31** Harness MOCK 骨架 + **C-31.1** trace 口径 + **C-32～C-35** Hydrated 渐进真实（**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_*`**；**§27** 摘要）+ **C-42** / **C-43** 规格 + **C-48** GROUP Harness + **C-50 / C-52** Composer 只读 AnswerPlan + **生产入口 Gate**（外链）
- [`docs/ai/business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md) — **C-43** **GROUP** 规格 + **C-48** **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`**
- [`docs/ai/business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md) — **C-36～C-40** **`BusinessDiagnosisCompositeAnswerPlan`** + **C-43 / C-48 / C-49** §8.11 **GROUP** 口径（**`BUILDER_VERSION=C-49`**）+ **C-50** §8.12 + **C-52** §8.13
- [`docs/ai/business-diagnosis-composer-readonly-design.md`](./business-diagnosis-composer-readonly-design.md) — **C-50** 设计 + **C-51** **`BusinessDiagnosisCompositeReadonlyComposer`**（仅文档 + Java skeleton + Harness 摘要字段）+ **C-52** §10 Gate 指针
- [`docs/ai/business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md) — **C-52** Gate + … + **C-56.2** + **C-58**（HARNESS_ONLY 仅 Harness `GRAPH_RUN`）+ **C-59**（`SHADOW` 语义）+ **C-60**（`SHADOW` 普通 `executeRun`，§C-60）+ **§C-62～C-65**（`SHADOW` 灰度 + **C-64** 放量 + **C-65** 观测清单）
- [`docs/ai/business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md) — **C-57** PlanFactory / ExecutionService / executionMode + **C-58** §12 Harness + **C-59** §13（`SHADOW` 语义）+ **C-60** §14 + **C-61** §15（**`compositeShadow*`**）+ **C-62** §16（灰度设计）+ **C-63** §17（**`ShadowPolicy` 已编码**；**§17.2 三轮手工验收已通过**）+ **C-64** §18 + **C-65** §19（观测索引）；**PRIMARY** **未生效**
- [`docs/ai/business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md) — **C-64** **`SHADOW` 灰度上线策略**（仅文档）：范围、限流、指标、§5 关闸；**§6** → **C-65**；**§7** → **C-66+**（暂缓）
- [`docs/ai/business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md) — **C-65** **批次字段 / 日复盘表 / 扩灰与暂停**
- [`PROJECT_HANDOFF_D1.md`](./PROJECT_HANDOFF_D1.md) — **C-50～C-65** 收口交接；**D-1** 起主业务能力
- [`next-business-capability-roadmap.md`](./next-business-capability-roadmap.md) — **D-1** 业务能力 **P0～P3**

---

**四条 Hydrated RealBridge 均已 curl 验收（C-29）**：营收 §22、采购 §24、出库 §25、菜品毛利 **`dish-profit-planner-adapter-design.md` §7.0 / §7.9**；**不经** Master 主链路。

---

**文档版本**：v1 设计稿 + C-2…**C-29**（四条 Hydrated curl 已验收）+ **C-30**（**Composite Plan 设计**，**[`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md)**）+ **C-31**（**Composite MOCK 骨架** + **C-31.1** trace **`usedTools`** 口径）+ **C-32～C-35**（**Composite + Hydrated 渐进真实**，**§27**）+ **C-36～C-40**（**[`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md)**：**`BusinessDiagnosisCompositeAnswerPlan`**）+ **C-42**（出库降级 Harness）+ **C-43**（**GROUP** 规格 **`business-diagnosis-composite-group-design.md`**）+ **C-48**（**GROUP Composite Harness** **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`**）+ **C-49**（**GROUP curl 快照与限制**、**`BusinessDiagnosisCompositeAnswerPlanBuilder#BUILDER_VERSION=C-49`**，见 **`business-diagnosis-composite-group-design.md` §10**）+ **C-50 / C-51**（**Composite Readonly Composer** + Harness **`businessDiagnosisFinalAnswerText`**，**[`business-diagnosis-composer-readonly-design.md`](./business-diagnosis-composer-readonly-design.md)**）+ **C-52 / C-52.1**（**生产入口 Gate** + **§3.3 意图对齐表**，**[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md)**）+ **C-53 / C-54 / C-55 / C-56.1 / C-56.2**（Gate **Java**、Harness-only **replay**、主链路 **`compositeGate*`** **关闭态观测**、override **设计**、**C-56.2 GRAPH_RUN override 实装**）+ **C-57**（**[`business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md)**：Gate **`allowed`** 后 **生产 Composite 执行与灰度**）+ **C-58**（**Harness-only `HARNESS_ONLY`** **`PlannerExecutor`**）+ **C-59**（**`SHADOW` 语义与设计**，composite **§13**、gate **§C-59**）+ **C-60**（**`SHADOW` 普通 `executeRun` 最小接线已编码**，composite **§14**、gate **§C-60**；**`PRIMARY`** **未生效**）+ **C-61**（**`compositeShadow*`** 观测，composite **§15**）+ **C-62**（**`SHADOW` 名单/限流** 设计，composite **§16** / gate **§C-62**）+ **C-63**（**`ShadowPolicy` / `ShadowDecision` 接线**，composite **§17**；**§17.2 三轮 curl 观测已收口**）+ **C-64**（**[`business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md)**：**`SHADOW` 灰度上线策略**，composite **§18**）+ **C-65**（**[`business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md)**：**灰度观测与复盘清单**，composite **§19**）— **C-50～C-65 Composite 生产安全框架已收口**；**C-66 metrics/dashboard/Redis 限流暂缓**；**下一阶 D-1**：**[`next-business-capability-roadmap.md`](./next-business-capability-roadmap.md)**、**[`PROJECT_HANDOFF_D1.md`](./PROJECT_HANDOFF_D1.md)**（**C-19** 采购 Hydrated **§24**；**C-20～C-24** StockReduce **§25**；**C-25～C-29** DishProfit **§26**；**C-30～C-65 §27**）
**状态**：营收 / 采购 / 出库 / 菜品毛利 Hydrated **均已 curl 验收**（**§12**）；**C-31** **经营诊断 Composite** — **Harness 六步 MOCK**（**不接** RealBridge）；**C-32** — **仅** **`revenue_query`** 真实；**C-33** — **`revenue_query`** + **`purchase_overview`** 真实，后四步 **mock**；**C-34** — **`revenue_query`** + **`purchase_overview`** + **`stock_reduce_query`** 真实，后三步 **mock**；**C-35** — 四数据域 Tool **全**真实（STORE），**诊断 / 建议** **`mock_*`**；**C-48** — **GROUP** 四域 **同 C-35 真实 Tool 模式**，**诊断确定性**、**建议 mock**，诚实字段 **`COMPOSITE_GROUP_ALL_DATA_REAL_DIAGNOSIS_DETERMINISTIC`**；**C-37+** — Composite AnswerPlan **Builder 已实装**；C-17 未 Hydrate CORE **仍**诚实降级。  
