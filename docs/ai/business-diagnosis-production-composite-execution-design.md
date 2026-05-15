# Business Diagnosis Composite — **C-57～C-65：生产执行、`SHADOW`、观测与灰度策略（设计 + §17 MVP 占位 + §18～§19 灰度 / 观测索引）— 本阶段已收口，C-66 暂缓，D-1 起主业务能力**

> **读者**：Graph / AiRunService / PlannerExecutor 接线工程师、SRE / 产品经理（灰度策略）。  
> **阶段**：**C-57** — **设计**：Gate **`allowed=true`** 之后的执行与灰度。**C-58** — **已实装**：Harness **`GRAPH_RUN`** + **`compositeBusinessDiagnosisExecutionMode=HARNESS_ONLY`**。**C-59** — **设计**：**`SHADOW`** 语义（**§13**）。**C-60** — **已实装**：普通 **`/api/ai/runs`** + Spring **`executionMode=SHADOW`** 旁路 Composite（**§14**）。**C-61** — **已实装**：**`compositeShadow*`** 耗时/对比观测（**§15**）。**C-62** — **仅文档**：白名单与限流策略（**§16**）。**C-63** — **已实装**：**`ShadowPolicy` / `ShadowDecision`** 最小接线（**§17**，**`ai.composite.businessDiagnosis.shadow.*`**，默认 **`shadow.enabled=false`** 不旁路 Composite）；**§17.2** — **三轮手工验收已通过**。**C-64** — **仅文档**：**`SHADOW` 灰度上线策略** — **§18** 索引 **[`business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md)**。**C-65** — **仅文档**：**灰度观测与复盘清单** — **§19** 索引 **[`business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md)**。  
> **前置**：**C-52～C-56.2** — 权威 **[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md)**。  
> **横向**：Composite 六步、AnswerPlan Builder、Readonly Composer — **[`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md)**、**[`business-diagnosis-composer-readonly-design.md`](./business-diagnosis-composer-readonly-design.md)**。  
> **阶段收口（C-50～C-65）**：**C-58 `HARNESS_ONLY`**（Harness **`GRAPH_RUN`**）**已验证**。**C-60 / C-61** 普通 Run **`SHADOW`** **已接入**，**`compositeShadow*`** **可观测**。**C-63 `ShadowPolicy`** **已支持** 白名单、**`scopeWhitelist`**、**`maxRunsPerMinute` / `maxRunsPerHour`**、**`cooldownSeconds`**。**C-64 / C-65** **[`business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md)**（灰度策略）与 **[`business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md)**（观测清单）**文档已完备**。**当前**不接 **`PRIMARY`**，**不替换** **`finalAnswerText` / `answerPreview`**。**C-66** 集中式 **metrics / dashboard** **先不做**（与 **rollout §7** 暂缓一致）。**下一阶**：**D-1** 主业务能力 — **[`next-business-capability-roadmap.md`](./next-business-capability-roadmap.md)**、**[`PROJECT_HANDOFF_D1.md`](./PROJECT_HANDOFF_D1.md)**。  
> **后继**：**C-66+** — **日志聚合 metrics / dashboard / 跨实例限流 / legacy `toolResults` 只读复用 / `PRIMARY`** / **异步 SHADOW** — **当前窗口不排**；待有真实灰度或产品决策再开（详见 **`business-diagnosis-shadow-rollout-plan.md` §7**）。

---

## 1. C-57 约束（与设计边界）
以下与项目「重要原则」一致，**本节与后续编码阶段均须遵守**：

| 约束 | 说明 |
|------|------|
| **不写死 AAA / 汀兰 / departmentId / distributerId / runId** | 生产 **`BusinessDiagnosisCompositePlanFactory`** 必须 **仅从** **`AiResolvedQueryContext` / `AiRunState`** 物化 **`timeWindow`、组织、`visibleStores`、`dataScope`、权限上下文** |
| **不得把 Harness `GraphCase` 塞进生产主链路** | **禁止** **`AiPlannerExecutorBusinessDiagnosisComposite*GraphCase`** 直接或等价短路成为 **`/api/ai/runs`** 唯一入口；该类 **仅 Harness Replay 参考实现** |
| **Composite 层不解析用户原文** | PlanFactory **禁止** **`contains` / `regex`** 于用户消息；路由仅依赖 **`GateResult`** + **`AiResolvedQueryContext`** 已存在字段 |
| **Gate 已通过仍须再判运行模式** | **`allowed=true`** 仅表示「**技术上可执行 Composite**」；是否 **真执行**、是否 **对用户可见**，由 **`executionMode`** + **`productionEnabled`** + **`fallbackToLegacyOnFailure`** 组合决定 |
| **GROUP 口径** | **`recommendedCaseKind`** / **`AiResolvedOrgScope`** 必须为 **GROUP** 时方可走 **GROUP 计划绑定**（多店 IN、`summaryText` / `scopeLabel` GROUP 语义）；**禁止** GROUP Gate 失败后以单店上下文冒充「全部门店」 |
| **降级诚实** | **DEGRADED / 部分 Tool 失败** 必须反映在 **`BusinessDiagnosisCompositeAnswerPlan`** 的 **`dataCoverage`、`riskLevel`、`summaryText`** 与 **`PlannerExecutorTrace`** 中 |
| **不新增 SQL / 不改四域 Tool 主逻辑 / 不改 Resolver 主逻辑** | **C-57～C-60** **默认**：仅 **编排侧**接线；**C-59 `SHADOW`** **本轮**仍 **只**文档。**AnswerPlan Builder** 与 Hydrated Adapter **复用既有契约** |

---

## 2. 推荐生产执行架构（概念）

Gate（**已实现** **`BusinessDiagnosisCompositeProductionGate.evaluate`**）与 **Composite 真实执行** 之间插入 **薄编排层**：**Plan → Execute → Compose（可选对用户暴露）**。推荐职责拆分如下：

```text
Resolver / Planner（既有）
  → BusinessDiagnosisCompositeProductionGate.evaluate  （C-53；C-55 已观测写入）
  → BusinessDiagnosisCompositeExecutionService.tryExecute(...)
       ├─ 若不应执行 Composite：返回 executed=false（主链路照旧）
       ├─ 若 SHADOW：异步或同线程末尾「旁路」跑 Composite → 写入 trace 字段 → 不改变用户可见回答
       ├─ 若 PRIMARY 且成功：Orchestration 可选用 Composite.finalAnswerText 替换对外回答（见 §8）
       └─ 任一路径失败且 fallback=true：fallbackRequired=true，主链路仅用旧 Answer
  └─ 【内部】BusinessDiagnosisCompositePlanFactory.buildPlan(...)
       → PlannerExecutionPlan（六步模板 + 绑定真实 contextRef）
       → 既有 PlannerExecutor + Hybrid Step Executors + AnswerPlan Builder + Readonly Composer
```

**类职责（命名建议，C-58 落地）**

| 类 | 职责 |
|----|------|
| **`BusinessDiagnosisCompositeExecutionMode`** | 枚举：**`OFF`**、**`HARNESS_ONLY`**、**`SHADOW`**、**`PRIMARY`**（与配置 **`ai.composite.businessDiagnosis.executionMode`** 绑定；大小写约定在 C-58 收口） |
| **`BusinessDiagnosisCompositePlanFactory`** | 输入：**`AiRunState`**、**`AiResolvedQueryContext`**、**`BusinessDiagnosisCompositeGateResult`（须 allowed）**；输出：**`PlannerExecutionPlan` 快照**（**`planId` / `templateId` / `steps` / `failurePolicy` / `resolvedContextRef` / `meta`**）。**从 context 绑定** Hydrated 各域 **`…ExecutionContext` / `…ReadRequest`**，**不**复制 Harness `caseId` 硬编码门店 |
| **`BusinessDiagnosisCompositeExecutionService`** | 输入：同上 + **`executionMode`** + 配置 **`fallbackToLegacyOnFailure`**；**调用** PlanFactory → **PlannerExecutor** →（成功）**`BusinessDiagnosisCompositeReadonlyComposer`**；输出：**`BusinessDiagnosisCompositeExecutionResult`**（见 §5） |
| **`BusinessDiagnosisCompositeExecutionResult`** | 单次尝试的 **结构化结果 + 可观测性**（见 §5） |

**与 Harness 关系**：**`POST /api/ai/harness/replay`** 下若产品需要「全图 + Composite」，可 **单独**在 Harness 路径调用 **同一** `ExecutionService`，但 **入参 `executionMode` 强制视为 `HARNESS_ONLY` 或等价内部标志**，**不**依赖普通 Run 的 **PRIMARY** 配置。

---

## 3. 配置键（建议）

| Key | 类型 | 默认 | 说明 |
|-----|------|------|------|
| **`ai.composite.businessDiagnosis.productionEnabled`** | `boolean` | **`false`** | **总闸**：为 **false** 时，`Gate.evaluate(..., false)` **恒** **`FEATURE_FLAG_DISABLED`**（与 **C-55** 一致）。**PRIMARY/SHADOW 打开前**须为 **true** 且 Gate **`allowed`** 方有意义 |
| **`ai.composite.businessDiagnosis.executionMode`** | `enum` 字符串 | **`OFF`**（或 **`HARNESS_ONLY`** 由团队择一默认为「不向用户暴露 Composite」） | **`OFF`**：生产 **不调度** **`BusinessDiagnosisCompositeExecutionService`**（除显式 Harness 可调）。**`HARNESS_ONLY`**：仅 Harness **GRAPH_RUN** 或等价 **override** 路径执行 Composite（见 §4）。**`SHADOW`**：普通 Run **后台**跑 Composite。**`PRIMARY`**：允许用 Composite **`finalAnswerText`** 替换用户可见输出（§4.3） |
| **`ai.composite.businessDiagnosis.fallbackToLegacyOnFailure`** | `boolean` | **`true`** | Composite **抛错 / Planner FAILED 且无安全部分结果 / Composer 缺失**时，**不**放空回答；回到 **legacy 链路已产生** 的答案（见 §8） |

**配置组合语义（规范性）**

- **`productionEnabled=false`**：**不**应以 **PRIMARY** 对外替换回答；Gate 观测可保留（**C-55**）。
- **`executionMode=OFF`**：即使 Gate **`allowed=true`**，**`ExecutionService` 返回 `executed=false`**（除非 **Harness-only** 代码路径 **显式**传入「强制执行」内部标志——该标志 **禁止**从 **`AiRunCreateRequest`** 透出）。
- **`fallbackToLegacyOnFailure=false`**（**不推荐默认可在生产打开**）：仅当产品有「宁可失败不可用旧答」时使用；须在运维文档标明风险。

---

## 4. 三阶段 `executionMode`（HARNESS_ONLY / SHADOW / PRIMARY）

### 4.1 A. `HARNESS_ONLY`

| 维度 | 设计 |
|------|------|
| **触发** | **仅** **`POST /api/ai/harness/replay`** **且** **`replayMode=GRAPH_RUN`**（或文档约定的 **等价 Harness 入口**），**或** 内测 **admin** 工具链；**普通** **`/api/ai/runs`** **不调用** Composite **ExecutionService**（或调用即 **no-op** **`executed=false`**） |
| **Gate** | 可用 **C-56.2** **`compositeProductionGateProductionEnabledOverride`** 配合 **`evaluate`**；与 **是否执行 Composite** 解耦：执行决策由 **Harness 请求** + **`executionMode=HARNESS_ONLY`** + **显式「本 run 允许跑 Composite」** 组合 |
| **用户可见** | **不**改变公网 Run 回答契约（若无单独 Harness 响应体，则 **仅** 内部 trace） |
| **重复查库** | **允许** — 范围仅限 ** Harness / 实验室**（见 §9） |

### 4.2 B. `SHADOW`

| 维度 | 设计 |
|------|------|
| **触发** | **`productionEnabled=true`** 且 **`executionMode=SHADOW`**（**须显式配置**，见 §13.3）且 Gate **`allowed=true`** |
| **主链路** | **继续跑**现有 **`business_overview_path` / 经营诊断** 等 — **用户仍看到 legacy 终稿** |
| **Composite** | **同一次 Run** 内旁路执行 **`BusinessDiagnosisCompositeExecutionService`**（与 C-58 **同一栈**：PlanFactory → PlannerExecutor → Readonly Composer）；**不替换**用户可见 **`finalAnswerText` / `answerPreview`**；写入 **`AiRunState` / debug / SSE** 可观测字段（**`compositeExecution*`** 族，见 §13.4）。**调度相对 legacy 的先后/线程策略** 留给 **C-60**（§14）。 |
| **记录内容** | 至少：**`PlannerExecutorTrace`（或摘要）**、**`BusinessDiagnosisCompositeAnswerPlan`（可截断）**、**`composeResult.finalAnswerText`**（旁路预览）、**模式、成功、`fallbackRequired`、`errorCode`** |
| **重复查库** | **会** — 与 §9、**§13.3** 一致：**四域读放大**；C-59 **只定**小流量灰度与观测，**不默认大流量**。 |

**延展约定**：**§13** 为 **C-59** 权威展开（与 **`HARNESS_ONLY` 对比、`PRIMARY` 不接、fallback 禁止反噬 legacy、本阶段不做清单**）。

### 4.3 C. `PRIMARY`

| 维度 | 设计 |
|------|------|
| **触发** | **`productionEnabled=true`** 且 **`executionMode=PRIMARY`** 且 Gate **`allowed=true`** |
| **行为** | **优先**仅用 **Composite 路径** 产出 **对用户可见** **`finalAnswerText`**（经 **Readonly Composer**）；**legacy 链路**作为 **fallback**（**`fallbackToLegacyOnFailure=true`**） |
| **目标态去重** | **理想**：Gate **`allowed`** 后主图 **直接进入** **`ExecutionService`** 驱动的 Composite，**跳过** legacy 四套 Tool；失败再 **兜底**跑一次 legacy **或** 使用 **已缓存**的 legacy 片段（产品决策）。**C-57 不落地方案**：**`PRIMARY`** 去重与主链路接线见 **§14（C-60 建议）** 与 **§9 方案 4**（**非** C-59 **SHADOW** 范围） |
| **诚实** | **`success=false`** 或 **`fallbackRequired=true`** 时 **必须**仍有 **可读** fallback 文本（**不假成功**） |

### 4.4 `OFF`（显式关闭）

**不调度** **`BusinessDiagnosisCompositeExecutionService`**；与 **`productionEnabled=false`** 的差别：`OFF` 可在 **flag true**（例如仅限观测 Gate）下仍 **禁止任何** Composite CPU/DB **消耗**。

---

## 5. `BusinessDiagnosisCompositeExecutionResult`（字段契约）

单次 **`ExecutionService.tryExecute`**（或 **`execute`**）**建议输出**：

| 字段 | 类型（概念） | 说明 |
|------|----------------|------|
| **`executed`** | `boolean` | **本 Run 本轮是否实际进入了 PlannerExecutor（含半途失败）**；`HARNESS_ONLY` 下普通 Run **恒 false** |
| **`mode`** | `BusinessDiagnosisCompositeExecutionMode` | **实际生效**模式（可能与配置不同若内部降级：如 PRIMARY 临时退 SHADOW — **不推荐隐式**，若做须写入 **`fallbackReason`**） |
| **`success`** | `boolean` | Planner + Builder + Composer **整条链路是否达到「可对外使用 Composite 文本」标准** |
| **`fallbackRequired`** | `boolean` | **`PRIMARY`**：**须**走 legacy 对外回答；**`SHADOW`**：仅表示 **Composite 旁路未交付**（用户答 **仍**为 legacy，含义见 **§13.5**），**不得**反写主链路失败 |
| **`fallbackReason`** | `String`（枚举化 **errorCode** 优先） | 如 **`COMPOSITE_EXECUTION_EXCEPTION`**、**`PLANNER_FAILED`**、**`COMPOSER_MISSING`**、**`GATE_NOT_ALLOWED`**（若误调）、**`MODE_OFF`** |
| **`answerPlan`** | `BusinessDiagnosisCompositeAnswerPlan` | 可能 **null**（未执行或早期失败） |
| **`composeResult`** | `BusinessDiagnosisCompositeComposeResult` | **只读 Composer** 输出；**null** 若未执行 compose |
| **`plannerTrace`** | `PlannerExecutorTrace` | **可截断**挂 Run；供 Debug / Replay |
| **`errorCode`** | `String` | 稳定机读码 |
| **`errorMessage`** | `String` | 供日志；**不全量**对用户展示 |

---

## 6. STORE / GROUP 如何选择（不写死 Harness 常量）

**唯一来源**：**`BusinessDiagnosisCompositeGateResult`** + **`AiResolvedQueryContext`** / **`AiRunState`**。

| **`gateResult.recommendedCaseKind` / scope** | PlanFactory 行为 |
|----------------------------------------------|------------------|
| **`STORE`** | 绑定 **单店** Hydrated **`…ExecutionContext`**：锚定 **`currentStoreDepartmentId` / `requestDepartmentId`** 与 **`visibleStores`** 一致；四域 **`dataScope`** 与单域 Hydrated **生产契约**一致 |
| **`GROUP`** | 绑定 **`scopeType=GROUP`**、**`visibleStores` ≥ 2**、多店 **`dataScope`**（**[`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md)**）；**AnswerPlan** 的 **`summaryText`** 须 **GROUP 口径** |
| **`NONE` / Gate 未 allowed** | **PlanFactory 不得被调用**；**ExecutionService** 入口第一层返回 **`executed=false`** |

**禁止**：用 Harness 的 **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** 默认 **`[1,3]`** 作为生产默认；生产 **仅能** echo **运行时** **`visibleStores`**。

---

## 7. Fallback 策略（与 Gate fallback 的差异）

| 层级 | 条件 | 行为 |
|------|------|------|
| **Gate 层** | **`allowed=false`** | **不**构造 Plan；走 **existing** 单域 / 概览 / 澄清（**C-52**） |
| **Execution 层** | **`allowed=true`** 但 **`executionMode=OFF` / `HARNESS_ONLY`（在非 Harness 调用）`** | **`executed=false`**；全流程等同 **当前 C-56.2** 前文 |
| **Execution 层** | **Planner **`FAILED`** 且 **`failurePolicy`** 不允许继续** | **`fallbackToLegacyOnFailure`** → **`fallbackRequired=true`**；用户答 = **legacy** |
| **Execution 层** | **Composer 异常** | 同上 |
| **Execution 层** | **PRIMARY 且 Composite `success=true`** | 用户答 = **`composeResult.finalAnswerText`** |
| **SHADOW** | Composite 成败 | **用户答**仍为 **legacy**；**仅**旁路字段记录。**Composite 失败** **不得**导致主链路失败或清空终稿（§13.5） |

**禁止**：**fallback** 时把 **STORE** legacy 正文 **包装成** GROUP 话术；禁止 **空字符串** **冒充**成功。

---

## 8. 为什么不能复用 Harness `GraphCase`（生产）

| 原因 | 说明 |
|------|------|
| **耦合 caseId 与固定物化** | **`AiPlannerExecutorBusinessDiagnosisComposite*GraphCase`** 为 **Replay 稳定**服务，往往 **硬编码** **caseId、顺序、summary 字段、部分 department 列表**；生产 **runId / 门店 / 时间** 每轮变化 |
| **入口契约不同** | Harness 经 **`AiHarnessReplayService`** / **`planForHarnessCase`**；生产须经 **`AiRunService`** / **Graph** 且与 **Gate、SSE、会话** 一致 |
| **阻碍 PRIMARY 去重** | GraphCase **假设** 「本 run **仅**为 Composite」，生产 PRIMARY 须 **或与** legacy **编排互斥**，**不能**隐含「总是六步全款」在未评估 flag 的情况下执行 |
| **可测试≠可运维** | GraphCase **变更是 Harness 兼容性**问题；PlanFactory **变更是产品与 SLA**问题，应 **分库分支**演进 |

**可复用的不是类，而是语义**：**同一个** **`PlannerExecutor` + Hybrid Executors + `BusinessDiagnosisCompositeAnswerPlanBuilder` + Readonly Composer** — **Production 另有「计划绑定层」**（PlanFactory）。

---

## 9. 重复执行四域 Tool 的问题与控制策略

**现状**：**`business_overview_path`**（**MULTI_AGENT** 四域 orchestration）**已会**顺序/组合执行 **`revenue_query`、`purchase_overview`、`stock_reduce_query`、`dish_profit_analysis`**（或与 DataPlanner **步序略有不同**，但 **域集合重叠**）。

**若在 SHADOW 并行**：**legacy + Composite** ⇒ **同一窗口、同一门店集合下重复四套只读查询**，数据库与链路 **负载约 ×2**（读放大）。

**分层策略（C-57 只定原则，实现延到 C-58～C-60）**

| 阶段 | 策略 |
|------|------|
| **HARNESS_ONLY** | **允许重复**；吞吐 **可控**在使用面 |
| **SHADOW** | **允许暂时重复**，**必须**：**极小流量**、**可配置门店白名单/user 白名单**（**可选 C-58**）；监控 **p95** 与 DB **QPS** |
| **PRIMARY（目标）** | **Gate allowed 后主路径应以 Composite 为唯一数据源**，**legacy 仅在 fallback** 跑；从根源 **去掉**双跑（**去重落地见 §14 `C-60` 建议**，**非** C-59） |
| **远期（独立设计）** | 复用 **`AiRunState.toolResults`** 与各域 **AnswerPlan**，直接作为 **`step_diagnosis_compose`** 的输入，跳过前四 Hydrated Adapter 的重复调用 — **须在「RunState 隔离 / 快照不变性」上单独评审**，**不在 C-57 设计细节展开** |

---

## 10. C-58 最小实现建议（**部分已落地 — 见 §12**）

1. **`BusinessDiagnosisCompositeExecutionMode`**：枚举 + Spring **`@ConfigurationProperties`** 或与现有 **`ai.composite.businessDiagnosis.*`** 同前缀绑定。  
2. **`BusinessDiagnosisCompositePlanFactory`**：  
   - **方法** **`buildPlan(AiRunState, AiResolvedQueryContext, BusinessDiagnosisCompositeGateResult)`**  
   - **断言** **`gateResult.isAllowed()`** && **`recommendedCaseKind` ∈ {STORE, GROUP}**  
   - **内部** 复用 **与单域 Hydrated 相同** 的 **`…ExecutionContext` 工厂方法**（从 **context** 抽取，**新**写 **薄层**从 **GraphCase 抄流程不抄常量**）  
   - 输出 **`PlannerExecutionPlan`**：**`templateId=BUSINESS_DIAGNOSIS_COMPOSITE_PRODUCTION_v1`**（新常量，**非** Harness caseId）  
3. **`BusinessDiagnosisCompositeExecutionService`**：  
   - **`tryExecute(...)`** 首行：若 **`executionMode` 与入口不匹配**（普通 Run + `HARNESS_ONLY`）⇒ **`ExecutionResult.notExecuted`**  
   - 调 **`PlannerExecutor.execute`**（与 Harness **共享** Executor Bean）  
   - 成功后调 **`BusinessDiagnosisCompositeReadonlyComposer.compose(answerPlan)`**  
   - **异常**捕获 ⇒ **`fallbackRequired=true`**，**不**清空 legacy  
4. **挂载点（HARNESS_ONLY 已接）**：**`GRAPH_RUN`** 下 **`executeBusinessGraphSyncForHarness`** 图成功后写入 **`AiRunState#businessDiagnosisCompositeExecutionResult`**；**不接** **`SHADOW` / `PRIMARY` / 普通 `startRun`**（**`SHADOW`** 设计见 **§13**，**C-60** 接线见 **§14**）。**`PRIMARY`** **宜**主路径去重并最终 **跳过** legacy 四域 — **属 **`C-60+`** / 独立切片**。  
5. **观测**：**`AiRunState`** 新增 **`businessDiagnosisCompositeExecutionResult`** **摘要**或与 **`compositeGate*`** **并列**，供 **curl / GET run** JSON 回放（**不**强依赖 **`src/test`**）。

---

## 11. C-57 小结（交付检查）

- [x] **三阶段** **`HARNESS_ONLY` / `SHADOW` / `PRIMARY`** + **`OFF`** 定义清晰  
- [x] **PlanFactory / ExecutionService / ExecutionResult / ExecutionMode** 职责与字段表  
- [x] **配置键** 与 **Gate `productionEnabled`** 关系  
- [x] **不复用 GraphCase** 的理由与 **可复用执行栈** 的边界  
- [x] **STORE/GROUP** 来自 **Gate + context**  
- [x] **Fallback** 分层  
- [x] **四域 Tool 双倍执行** 风险与分期策略  
- [x] **C-58 最小实现切片**  
- [x] **C-59 **`SHADOW`** 仅文档** — **§13**（普通 Run 旁路语义、读放大、观测键、fallback **禁止反噬** legacy）  

---

## 12. C-58 已实装（**HARNESS_ONLY** + Harness **`GRAPH_RUN`**）

| 项 | 说明 |
|----|------|
| **触发** | **`AiHarnessReplayRequest#compositeBusinessDiagnosisExecutionMode`** = **`HARNESS_ONLY`**（**`null` / 空白 / 未识别 → `OFF`**）；**仅**与 **`replayMode=GRAPH_RUN`**、`executeBusinessGraphSyncForHarness` 同路径。**普通 `/api/ai/runs` 不传、不执行。** |
| **Gate** | 仍 **`recordCompositeProductionGateObservation`**（**C-55/C-56.2**）；可 **`compositeProductionGateProductionEnabledOverride=true`** 使 **`evaluate`** 放行。**`tryExecute`** 在非 **`HARNESS_ONLY`**、**`gate` 空、或 !**`allowed`** 时 **`executed=false`**。 |
| **类** | **`BusinessDiagnosisCompositeExecutionMode`**、**`BusinessDiagnosisCompositePlanFactory`**（**非** Harness GraphCase）、**`BusinessDiagnosisCompositeExecutionService`**、**`BusinessDiagnosisCompositeExecutionResult`** |
| **执行栈** | **`PlannerExecutor` + `CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor`** + **`Revenue/Purchase/StockReduce/DishProfit` `*PlannerRealReadBridge`** + **`BusinessDiagnosisCompositeReadonlyComposer`** |
| **终稿** | **不替换** **`AiRunState#finalAnswerText`**；Harness 摘要见 **`compositeFinalAnswerText`** 等键。 |
| **成功口径** | Planner **`overallStatus=SUCCESS`**；Composer 正文 **非空**；**不把 DEGRADED 当成功**。 |

### 12.1 C-58 **curl / Harness** 实测观测（**GRAPH_RUN**，已收口）

以下为 **`POST /api/ai/harness/replay`**、**`replayMode=GRAPH_RUN`**、**`compositeProductionGateProductionEnabledOverride=true`**、**`compositeBusinessDiagnosisExecutionMode=HARNESS_ONLY`**、与 Gate 允许的问句在同一环境下的 **第一轮 `resolvedQueryContextSummary`（或等价摊平摘要）** 上已核对过的关键键（示例值以一次成功跑通为准；环境数据不同仅影响正文措辞，不改变字段语义）。

| 键 | 说明 |
|----|------|
| **`compositeGateAllowed`** | **`true`** |
| **`compositeGateReasonCode`** | 示例 **`ALLOWED_GROUP`**（以 Gate 与环境为准） |
| **`compositeExecutionMode`** | **`HARNESS_ONLY`** |
| **`compositeExecuted`** | **`true`** |
| **`compositeExecutionSuccess`** | **`true`** |
| **`compositeFallbackRequired`** | **`false`** |
| **`compositePlannerOverallStatus`** | **`SUCCESS`** |
| **`compositeFinalAnswerText`** | **非空**（Harness 专用 Composite 终稿预览） |
| **`compositeComposerVersion`** | **`C-51_READONLY_COMPOSER`** |
| **`compositeAnswerPlanType`** | **`BUSINESS_DIAGNOSIS_COMPOSITE`** |
| **`answerPreview` / legacy 图终稿摘要** | **仍存在** — **未被** **`compositeFinalAnswerText`** **替换**。 |

### 12.2 **`overallPass` 与 C-58 验收**（勿混淆）

- Harness 响应根的 **`overallPass`** 由各轮 **`resolvedQueryContextSummary`** 与 **内置 / 传入 `expectations`** 的 **`AiHarnessExpectationComparator`** 比对决定。
- 若 **`caseId`** 仍为 **`V2_SEMANTIC_MAINLINE_CORE_10`**，服务端会套上 **该 case 自带的 10 轮完整预期**；当请求 **`messages`** 只有 **少量轮次**（例如仅一条「这个月经营得怎么样？」）时，**`AiHarnessExpectationComparator`** 仍可能拿 **后续轮次的旧预期**（如 **`dish_profit`** 相关字段）对照 **当前第一轮摘要**，出现 **不匹配** → **`round.pass=false`** → **`overallPass=false`**。**这不表示 C-58 `HARNESS_ONLY` 或 Composite `PlannerExecutor` 执行失败**；根本原因是 **复用旧 case 的预期与本轮 message 切片不一致**（典型为 **菜品毛利专线**的旧 expectation）。
- **C-58 验收判据**：以 **`compositeGate*`**、**`compositeExecution*`** 及 **`compositeFinalAnswerText`** 是否符合 §12 / §12.1 为准。**不要**把 **`overallPass`** 当作 **`HARNESS_ONLY` Composite** 成功与否的依据，除非你为本请求提供了与当前轮次一致的 `expectations`，或选用的 **`caseId`** 不会在短 message 链路下套用与摘要字段不符的内置预期。

---

## 13. C-59 **`SHADOW`** 模式（**仅设计**，**本阶段不实现**）

> **背景**：**C-58** 已在 Harness **`GRAPH_RUN` + `HARNESS_ONLY`** 下验收：Gate **`allowed`** 时可旁路跑 Composite，**`compositeFinalAnswerText`** 可观测，**不替换** legacy **`answerPreview`**；Harness **`overallPass`** 受旧 case expectation 误伤，**不**代表 Composite 失败（§12.2）。  
> **C-59** 将 **同一「只观测、不换终稿」** 思想 **迁到普通 `/api/ai/runs`**，**仍不接 `PRIMARY`、不替换用户回答、不复用 legacy toolResults、不加 LLM`**。

### 13.1 定义（何时跑、用户看到什么）

| 项 | 约定 |
|----|------|
| **入口** | **普通** **`POST /api/ai/runs`**（**`startRun` / 主 Business Graph**），**非** Harness **`GRAPH_RUN`** |
| **前置** | **`ai.composite.businessDiagnosis.productionEnabled=true`** **且** **`ai.composite.businessDiagnosis.executionMode=SHADOW`**（名称以 **C-57 §3** 为准）**且** **`BusinessDiagnosisCompositeProductionGate.evaluate` → `allowed=true`** |
| **主链路** | **完全沿用** 现有 **MULTI_AGENT / 经营概览四域** legacy 编排；**用户最终回答** = legacy 产出的 **`finalAnswerText`**（及现有 **`answerPreview` 等摘要契约**） |
| **Composite** | **旁路**调用与 C-58 **相同**的 **`BusinessDiagnosisCompositeExecutionService`**（PlanFactory + **`PlannerExecutor`** + Readonly Composer）；**仅**把结果写入 **`AiRunState`**、**调试结构**、以及 **SSE / GET-run 摘要** 中 **`composite*`** 可观测键 |
| **对用户** | **不因**开启 SHADOW **改变**前台文案、**不**把 Composite 正文 **覆盖** legacy |

### 13.2 与 **`HARNESS_ONLY`**、**`PRIMARY`** 的区别

| 模式 | 谁可以触发 | 用户可见终稿 | Composite 失败是否影响用户 |
|------|------------|--------------|----------------------------|
| **`HARNESS_ONLY`** | **仅** Harness **`replayMode=GRAPH_RUN`** + 请求字段 **`HARNESS_ONLY`** | **Legacy**（主图跑完后的既有摘要）；Harness 另给 **`compositeFinalAnswerText`** | **否** |
| **`SHADOW`（C-59 设计 / C-60 实装）** | **普通 Run** + Spring **`executionMode=SHADOW`** | **始终 legacy** | **否**（§13.5） |
| **`PRIMARY`（本阶段不做）** | 未来配置 + Gate **`allowed`** | **可**替换为 Composite **`finalAnswerText`**（产品定） | **可**经 **`fallbackToLegacyOnFailure`** 回退；**不属于 C-59** |

### 13.3 四域读放大、默认与灰度

- **现状**：**`business_overview_path`** 下 **MULTI_AGENT** 旧链路 **已会**跑 **四域 Tool**（营收 / 采购 / 出库 / 菜品毛利，集合与 Composite 真实步 **重叠**）。
- **SHADOW 再跑 Composite** ⇒ **又一次** 四套 Hydrated Adapter **`→` Tool**，**只读放大约 ×2**（同窗口、同 scope 下 **重复查库**）。
- **C-59 设计结论**：SHADOW **只用于** **小流量灰度、压测受控环境、可观测对比**；**禁止**作为 **默认全量**策略。
- **配置默认**：延续 **C-57 §3** — **`executionMode` 默认 `OFF`**。**`SHADOW`** **必须显式打开**。**C-60**：**未**接 **distributerId 白名单 / Run 级超时 / 限流**；与 **§13.3 / §14** 一致，**仅最小灰度接线**。

### 13.4 SHADOW 建议输出字段（摘要 / SSE）

与 C-58 已存在的 **`compositeGate*`**、**`compositeExecution*`** **同一命名空间** 扩展（**C-60**：**`summarizeCompositeGateAndExecutionOnly`**；**C-61**：**`compositeShadow*`** — **§15**）：

| 键（建议） | 说明 |
|------------|------|
| **`compositeExecutionMode`** | **`SHADOW`** |
| **`compositeExecuted`** | 是否进入 **`tryExecute`**（含失败路径） |
| **`compositeExecutionSuccess`** | Composite **是否达到可展示旁路正文** 的成功口径（与 C-58 一致：Planner + Builder + Composer **诚实**） |
| **`compositeFallbackRequired`** | **旁路语境**下：Composite 是否 **须视为未交付**（**不**表示用户答回退 — 用户答 **始终** legacy） |
| **`compositePlannerOverallStatus`** | Planner 根 **`overallStatus`** 摘要 |
| **`compositePlannerDegradedSteps`** | 降级步列表（可截断） |
| **`compositeFinalAnswerText`** | **旁路** Composite 只读 Composer 产出（**不换**用户主终稿） |
| **`compositeAnswerPlanType`** | 如 **`BUSINESS_DIAGNOSIS_COMPOSITE`** |
| **`compositeComposerVersion`** | 如 **`C-51_READONLY_COMPOSER`** |
| **`compositeShadowComparedWithLegacy`** | **C-61**：**`true`** 当 **`finalAnswerText`** 与 Composite **`finalAnswerText`** **均非空**（便于判断两侧是否都有可对比正文）；**不**读用户原文 |
| **`compositeShadowLatencyMs`** | **C-61**：旁路 **`tryExecute`** **墙钟毫秒**（`nanoTime` 换算） |
| **`compositeShadowLegacyAnswerPresent`** | **C-61**：**`AiRunState#finalAnswerText`** 非空 |
| **`compositeShadowCompositeAnswerPresent`** | **C-61**：Composer **`finalAnswerText`** 非空 |
| **`compositeShadowFinalAnswerReplaced`** | **C-61**：**恒 `false`**（SHADOW **契约**：未把 Composite 写进用户主终稿） |

### 13.5 SHADOW 下 **fallback** 口径（**禁止反噬 legacy**）

- Composite **失败 / 抛错 / Planner `FAILED` / Composer 不可用**：**只**记录 **`compositeExecutionSuccess=false`**、**`compositeFallbackRequired=true`**（或等价）、**`composite*ErrorCode` / `fallbackReason`**（机读码稳定）；**可选**截断 **`errorMessage`** 入 debug。
- **禁止**：因 Composite 失败 **修改** legacy 图 **终态**（**不得**把 Composite 异常 **冒泡**为用户 500、**不得**清空 **已生成**的 **`finalAnswerText`**、**不得**把 SHADOW 降级 **伪装**成 legacy **失败**）。
- **用户回答路径** 与 **C-55/C-58** 一致：**SHADOW 不改变**「主链路成功 / 失败」的 **既有语义**；Composite 仅为 **附加观测**。

### 13.6 **不做 / 延后**（C-60 **`SHADOW` 之后仍有效）

| 不做 / 延后 | 说明 |
|-------------|------|
| **`PRIMARY`** | **不接** Spring **`PRIMARY`**：**`BusinessDiagnosisCompositeExecutionService`** 对 **`PRIMARY`** **`tryExecute` ⇒ `executed=false`** |
| **替换主终稿** | **`finalAnswerText` / `answerPreview`** **仅 legacy** |
| **复用 legacy `toolResults`** | **仍未**减少双跑；**异步 SHADOW / 采样 / PRIMARY 去重** — **远期** |
| **LLM** | **不加** |
| **前台** | **不改** |
| **用户原文 **`contains`/`regex`**（含 Gate / Composite 层新增）** | **禁止** |
| **`SHADOW` 异步** | **当前同步**：`executeRun` 内 legacy 图后 **`maybeExecuteShadowCompositePlanner`**；**第二期**异步化 |
| **`SHADOW` 白名单 / 限流** | **权威设计 §16（C-62）** / **§17（C-63 建议实现）**；**C-61 前**：仅 **`productionEnabled`** + **`executionMode`** + **`gate`**（无灰度闸） |

---

## 14. **C-60：`SHADOW` 最小接线（已实现）**

**目标**：普通 **`POST /api/ai/runs`**（**`AiRunService.executeRun`**）在 **`legacy` Business Graph 跑完、`runStatus`/`finalAnswerText` 等主链路已定**（**不 cancel**）、**Gate 已 evaluate** 之后，**旁路 **`BusinessDiagnosisCompositeExecutionService.tryExecute(..., mode=SHADOW)`**。**用户终稿仍为 legacy**。失败 **不入终稿 / 不把 run 标失败**。**不调用 LLM**（Composite 仍为 **Hydrated PlannerExecutor + readonly Composer**）。

### 14.1 配置（Spring，`application*.yml` / `*.properties`）

| 属性 | 说明 | **C-60 默认** |
|------|------|---------------|
| **`ai.composite.businessDiagnosis.productionEnabled`** | **`true`** 才允许生产旁路 / Harness 生产态 | **`false`**（既有） |
| **`ai.composite.businessDiagnosis.executionMode`** | **`OFF` \| `HARNESS_ONLY` \| `SHADOW` \| `PRIMARY`** | **`OFF`** |
| **`ai.composite.businessDiagnosis.fallbackToLegacyOnFailure`** | **旁路**失败侧 **仅**影响 **`composite*`** 观测 | 既有默认 |

- **`HARNESS_ONLY`**：**仅** Harness **`GRAPH_RUN`** + 请求体 **`compositeBusinessDiagnosisExecutionMode=HARNESS_ONLY`**（**C-58**，**不变**）。
- **`SHADOW`**：**仅** **普通 Run** + 上表 **`executionMode=SHADOW`**；**与 Harness 请求字段无关**。
- **`PRIMARY`**：**不执行** Composite（**`tryExecute` 不跑 planner**）；**留给后续**。

### 14.2 执行条件（**全部**满足）

1. **`productionEnabled=true`**
2. **`executionMode=SHADOW`**（**Spring** 注入字符串 → **`BusinessDiagnosisCompositeExecutionMode`**）
3. **`state.businessDiagnosisCompositeGateResult != null` 且 `allowed=true`**
4. **`run` 未 cancel**
5. **调用路径**：**仅** **`AiRunService#executeRun`**（普通 **`POST /api/ai/runs`** 异步执行）。**Harness `GRAPH_RUN`** 使用 **`executeBusinessGraphSyncForHarness`**，**只走 C-58** **`maybeExecuteHarnessCompositePlanner`**，**不调用**本节 **`maybeExecuteShadowCompositePlanner`**（**无双路径叠加**）。

### 14.3 挂载点

- **`AiRunService.executeRun`**：**`graphRunner.runBusinessGraph(...)` 返回之后**、**`run_finished` 等收尾之前**，**`maybeExecuteShadowCompositePlanner(endedState)`**。
- **禁止**在 **legacy 图前**跑 Composite。**当前实现为同步**：读放大 §13.3；后续可异步。

### 14.4 失败与 **`AiRunState`**

- 异常：**catch** → **`compositeExecuted=true`**，**`compositeExecutionSuccess=false`**，**`compositeFallbackRequired=true`**，**`compositeExecutionErrorCode=COMPOSITE_SHADOW_EXCEPTION`**，**`compositeExecutionErrorMessage`**（截断）；**C-61** **`compositeShadow*`** 仍写入（**`compositeShadowLatencyMs`**、**`compositeShadowFinalAnswerReplaced=false`** 等）。
- **`BusinessDiagnosisCompositeExecutionResult`** 写入 **`AiRunState.businessDiagnosisCompositeExecutionResult`**（与 C-58 **同载体**；C-61 增 **`compositeShadow*`** 字段）。

### 14.5 SSE / 调试信封

**`compositeGate*`**：**既有 Gate 评测**。**`compositeExecution*`** 与 **`compositeShadow*`**（C-61）：**`summarizeCompositeGateAndExecutionOnly(state)`**，在 **`run_started` / `answer_delta`（若已有 harness summary 则追加）/`run_finished`** 合并。**不依赖** **`ai.harness.debug-context-enabled`** **即可暴露 **`compositeGate*`**、**`compositeExecution*`**。**详见 §15**。

### 14.6 验收（普通 curl，非 Harness）

**配置**： **`productionEnabled=true`**，**`executionMode=SHADOW`**。**问**：「这个月经营得怎么样？」  

**预期**：**用户正文 = legacy**；**`answer_delta.text`** = legacy；**`compositeGateAllowed=true`**；**`compositeExecutionMode=SHADOW`**；**`compositeExecuted=true`**；成功则 **`compositeExecutionSuccess=true`** 且 **`compositeFinalAnswerText` 仅 debug/SSE**；失败 **不影响 legacy / run 成功态**。**C-61**：另见 **`§15`** **`compositeShadow*`** 验收。

---

## 15. **C-61：`SHADOW` 观测收口（已实现）**

**目标**：在 **C-60** 旁路已跑通的前提下，增加 **`compositeShadow*`**，便于判断 **legacy 与 Composite 是否均有正文**、**旁路耗时**、**是否违反「未替换终稿」契约**。**不读用户原文**、**不调 LLM**、**不改** **`finalAnswerText`**。

### 15.1 字段语义

| SSE / 摘要键 | 类型 | 何时有值 |
|--------------|------|----------|
| **`compositeShadowLatencyMs`** | 数 | **`compositeExecutionMode=SHADOW`** 且 **`compositeExecuted=true`** |
| **`compositeShadowLegacyAnswerPresent`** | 布尔 | 同上；**`AiRunState#finalAnswerText`** 去空白后非空 |
| **`compositeShadowCompositeAnswerPresent`** | 布尔 | 同上；Composer **`finalAnswerText`** 去空白后非空 |
| **`compositeShadowComparedWithLegacy`** | 布尔 | 同上；**`true`** 当 **legacy 与 composite 两侧均非空**（表示可并行对比，**非**深度 diff） |
| **`compositeShadowFinalAnswerReplaced`** | 布尔 | 同上；**恒 `false`**（SHADOW **契约**） |
| **`compositeShadowSkipped`** | 布尔 | **C-63**：**`compositeShadowSkipped=true`** 时；**`compositeExecuted=false`** |
| **`compositeShadowSkipReason`** | 字符串 | **C-63**：机读跳过原因（见 **§16.5**） |
| **`compositeShadowThrottleHit`** | 布尔 | **C-63**：限流/冷却 **SKIP** |
| **`compositeShadowWhitelistMatched`** | 布尔 | **C-63**：名单维通过（节流 **SKIP** 时多为 **`true`**） |

**非 SHADOW**：上列键 **`null`**。**SHADOW 且 C-63 SKIP**：**`compositeShadowSkipped=true`**，**§15 C-61 耗时行** 等为 **`null`**（**`AiHarnessResolvedContextSummarizer`** 分支）。

### 15.2 实现要点

- **`AiRunService#maybeExecuteShadowCompositePlanner`**：**C-63 **`ShadowPolicy.evaluate`** 先于 **`tryExecute`**；**allow** 后 **`tryExecute`** **前后** **`nanoTime`**。**skip** ⇒ **`buildShadowSkippedObservation`**。
- **`AiHarnessResolvedContextSummarizer#mergeCompositeShadowObservationFields`**：摊平 **SKIP** 与 **C-61 executed** 两套分支。

### 15.3 curl / SSE 验收（普通 Run，非 Harness）

**配置（C-61 全量旁路路径）**：**`ai.composite.businessDiagnosis.productionEnabled=true`**，**`ai.composite.businessDiagnosis.executionMode=SHADOW`**，Gate **`allowed=true`**，且 **`ai.composite.businessDiagnosis.shadow.enabled=true`** + 至少一维名单或 **`scopeWhitelist`** 配置且通过（见 **§16～§17**）。**仅** **`shadow.enabled=false`**（默认）⇒ **SKIP**，见下 **C-63 SKIP**。

**订阅 SSE**（或查看合并了同类键的 **GET run 调试体**），在 **`run_finished`** 或 **`answer_delta.data`** 上核对：

1. **`answer_delta.text`**（及持久化 **`finalAnswerText`**）= **legacy**，**未**被 **`compositeFinalAnswerText`** 覆盖。
2. **`compositeExecutionMode`** = **`SHADOW`**，**`compositeExecuted`** = **`true`**。
3. **`compositeShadowFinalAnswerReplaced`** = **`false`**。
4. **`compositeShadowLatencyMs`** ≥ **0**（整数毫秒）。
5. **`compositeShadowLegacyAnswerPresent`** = **`true`** 当 legacy 本轮确有正文。
6. **`compositeShadowCompositeAnswerPresent`** = **`true`** 当旁路 Composer 产出非空正文。
7. **`compositeShadowComparedWithLegacy`** = **`true`** **当且仅当** 上两条均为 **`true`**。

**C-63 SKIP**（**`compositeShadowSkipped=true`**）：**`compositeExecuted=false`**；**不**调 **`tryExecute`**；**`compositeShadowSkipReason`** 机读（如 **`SHADOW_GRAY_DISABLED`**）；**`compositeShadowLatencyMs`** 等为 **`null`**；**`answer_delta.text`**（及 **`finalAnswerText`**）仍为 **legacy**。

**Composite 失败**：**`compositeExecutionSuccess=false`** 时仍应有 **`compositeShadowLatencyMs`**；**`compositeShadowCompositeAnswerPresent`** 多为 **`false`**；**`compositeShadowFinalAnswerReplaced`** 仍为 **`false`**；**legacy 不受影响**。

> **§16～§17**：**放行后**仍为 **C-61**；**`ShadowPolicy` SKIP** **`tryExecute`**（**`shadow.enabled=false`** / 名单 / **`scopeWhitelist`** / 限流），则依赖 **`compositeExecuted=true`** 的 **§15.3 条目 4～7** **不填充**（见 **§16.5**：**`compositeShadowSkipped`** 等）。**已记录样例观测**：**§17.2**。

---

## 16. **C-62：`SHADOW` 灰度白名单与限流（§16 设计；C-63 已编码实现）**

**目标**：控制 **§13.3** 读放大。**实现**：**§17/C-63 **`ShadowPolicy`**。

### 16.1 为什么不能直接「全量开 SHADOW」

| 风险 | 说明 |
|------|------|
| **四域 Tool 读放大** | Legacy **经营诊断主链路已在同窗口跑出四域 reads**（或子集）；**SHADOW Composite 再跑一次** Hydrated PlannerExecutor ⇒ **至多再 ×1 四套只读链路** ⇒ **聚合 QPS ~2×**，热点表压力上升。 |
| **耗时** | 旁路 **`tryExecute`** 目前 **同步**于 **`executeRun`**（§14）；**P95/P99 RUN** 与用户感知的 **`run_finished` / SSE 尾包延迟**可被 **composite 墙钟**（§15 **`compositeShadowLatencyMs`**）**拉长**。 |
| **DB 压力** | 四域 **`revenue_query` / `purchase_overview` / `stock_reduce_query` / `dish_profit_analysis`** 重复聚合；高峰期易与 **online 读写**争抢连接与 IO。 |
| **SSE / 观测体积** | 旁路 **`compositeFinalAnswerText`** 与 **`composite*`** 增大信封；虽 **不接 PRIMARY**、不换正文，仍可加重 **链路带宽与前端解析**。 |

**结论**：**`executionMode=SHADOW`** **不得**等价于默认全用户开启；须与 **显式 **`shadow.*` 配置闸门**、**名单**、**限流**组合使用（见 **§16.3～§16.7**）。

### 16.2 Gate 与灰度闸门的关系（两段式放行）

判定的 **逻辑顺序建议**（**均不读用户原文**；仅 **`AiResolvedQueryContext`** / **`AiRunState`** 已有字段）：

1. **`BusinessDiagnosisCompositeProductionGate`**：**`allowed`**（**intent/path/ref** **仅经 Gate** ✓）。
2. **生产总闸**：**`productionEnabled`** + **`executionMode=SHADOW`** + **未 cancel**（**C-60**）。
3. **`shadow` 灰度子闸（C-63 已实装）**：**默认 **`ai.composite.businessDiagnosis.shadow.enabled=false`** ⇒ **不**调用 **`tryExecute`**（**`compositeShadowSkipped=true`**，**`skipReason=SHADOW_GRAY_DISABLED`**）。**`enabled=true`** 时再应用 **§16.3～§16.4**；**user/distributer/department/scope** 四类名单 **全空** ⇒ **SKIP（`WHITELIST_NO_MATCH`）**。
4. **白名单**：见 **§16.3**。
5. **限流 / 冷却**：见 **§16.4**。
6. **`BusinessDiagnosisCompositeExecutionService.tryExecute`**。

**任一灰度闸门拒绝**：不调用 **`BusinessDiagnosisCompositeExecutionService#tryExecute`**；在观测中置 **`compositeShadowSkipped=true`**（**§16.5**）；不改变 **`finalAnswerText`**。

### 16.3 白名单维度

| 维度 | 数据源（示例） | 说明 |
|------|----------------|------|
| **`userId`** | **`AiRunState#getUserId()`**（或与登录态对齐的等价 long） | 运营 / 调试账号粒度；可为空 ⇒ 若 **仅用 user 名单**且无匹配则 skip。 |
| **`distributerId`** | **`AiResolvedQueryContext`/org、`AiRunState` 挂载** | 集团租户；可与 **STORE** **并列 OR**。**禁止** Harness **replay caseId**。 |
| **`departmentId`**（**/ storeId**） | **单店** **`AiRunState#getDepartmentId()`** 或与 **`departmentId`** 对齐的 **`visibleStores`** 根门店 | GROUP 会话下 **`departmentId` 常为 null**：须用 **GROUP 策略**或其它稳定 **storeRootId**集合；设计中 **whitelist 单列 `departmentWhitelist`** ⇒ **只对「能稳定解析到 STORE 粒度」的请求生效**。 |
| **`scopeType`（配置 `scopeWhitelist`）** | **`AiResolvedOrgScope#getScopeType()`** | **非空 **`` `ai.composite.businessDiagnosis.shadow.scopeWhitelist` ``**（逗号 **`STORE`/`GROUP`**）时：须 **AND** 命中；**与 id 维 OR 组合**见 **`ShadowPolicy`**。 |
| **intent / path** | **Gate 已写入 `AiResolved*` / `AiRunState` 的结构化结论** | Shadow 闸内 **禁止**用用户原文做 **`contains` / regex**。若需再裁剪（例如仅某 path），**只能**读取 **Gate 已输出的** `effectiveIntent` / `path` 等字段的子集；**不得**绕过 **`BusinessDiagnosisCompositeProductionGate`**。 |

**匹配语义**：**`userWhitelist` ∪ `distributerWhitelist` ∪ `departmentWhitelist`** **OR**（已配置名单维上 **至少一维命中**）；**`scopeWhitelist` 非空** 时 **AND** **`orgScope.scopeType`** 命中。**全空**（含 scope）：**`shadow.enabled=true`** 时 **SKIP**（**`WHITELIST_NO_MATCH`**）。

### 16.4 限流策略（全局 + 租户/用户）

| 策略 | 建议 | SKIP 时对 legacy |
|------|------|-------------------|
| **每分钟最大 SHADOW 次数** | 例：**`shadow.maxRunsPerMinute`** — 进程内令牌桶或滑动计数（**C-63 MVP**）；跨实例需 Redis 等——**第二期**。 |
| **每小时最大（可选）** | 例：**`shadow.maxRunsPerHour`**（配置名可扩展）— 防短时误配后继续打库。 |
| **冷却时间** | 例：**`shadow.cooldownSeconds`**：**同一 **`userId`** **或 **`distributerId`** 在一次旁路（成功或失败后是否计费由实现约定）之后的静默窗。 |
| **超限 / 冷却命中** | 不调用 **`tryExecute`**；legacy **照常**。 |

### 16.5 观测字段建议（C-62 / C-63）

与 **§15 **`compositeShadow*`**并行；**SKIP** **`tryExecute`** 时仍可观测：

| 键 | 说明 |
|----|------|
| **`compositeShadowSkipped`** | **`true`**：**灰度闸门**跳过旁路 Composite（名单/限流/策略关闭等）。 |
| **`compositeShadowSkipReason`** | 机读码：**`SHADOW_GRAY_DISABLED`**、**`WHITELIST_NO_MATCH`**、**`SCOPE_NOT_ALLOWED`**、**`THROTTLE_GLOBAL_MINUTE`** / **`THROTTLE_GLOBAL_HOUR`**、**`THROTTLE_USER_COOLDOWN`**、**`THROTTLE_DISTRIBUTER_COOLDOWN`**。 |
| **`compositeShadowThrottleHit`** | **`true`**：**限流/冷却命中**（与 **`SkipReason`** 可冗余，便于告警）。 |
| **`compositeShadowWhitelistMatched`** | **`true`**：**白名单 OR 语义命中**。**SKIP** **因限流**：可为 **`false`** **或 **`true`**（若设计为「已通过名单才进入限流」则 **限流 SKIP** 时为 **`true`**）。 |

### 16.6 配置建议（`application*.properties`/`yml`）

| 配置键 | 默认（C-62 契约） | 说明 |
|--------|-------------------|------|
| **`ai.composite.businessDiagnosis.shadow.enabled`** | **`false`** | **`false`**：普通 Run **不**旁路 **`tryExecute`**（**`compositeShadowSkipped=true`**，**§16.5**）。**`true`**：必选 **§16.3～§16.4** 规则（名单全空 ⇒ **SKIP**）。 |
| **`ai.composite.businessDiagnosis.shadow.userWhitelist`** | **空** | 逗号分隔 **`userId`** 列表。**与 distributer / department OR**。 |
| **`ai.composite.businessDiagnosis.shadow.distributerWhitelist`** | **空** | 同上 **`distributerId`**。**GROUP/STORE **均可带 **distributer**。 |
| **`ai.composite.businessDiagnosis.shadow.departmentWhitelist`** | **空** | 单门店 **id**。 |
| **`ai.composite.businessDiagnosis.shadow.maxRunsPerMinute`** | **`0`** 或 **`-1`** 表示**不启用**该项 | 全域 **SHADOW `tryExecute` 次数**/分钟。 |
| **`ai.composite.businessDiagnosis.shadow.maxRunsPerHour`** | **`0`** 或 **`-1`** **不启用**（**可选**） | **小时**封顶；防误配长尾打库。**C-63 MVP 可延后**。 |
| **`ai.composite.businessDiagnosis.shadow.cooldownSeconds`** | **`0`** **不启用** | **每 user / distributer** 冷却；与 **§16.4** 一致。 |

文档化 **格式**（List / CSV）Spring 对齐方式由 **C-63** PR 选型；本节只定 **语义**。

### 16.7 Fallback 边界（与 legacy）

| 场景 | 旁路 Composite | **`finalAnswerText` / `answerPreview`** |
|------|----------------|------------------------------------------|
| **白名单未命中** | 不调用 **`tryExecute`** | **Legacy 照常** |
| **限流或冷却命中** | **同上** | **同上** |
| **`shadow.enabled=false`**（默认） | **不调用 **`tryExecute`**（**`compositeShadowSkipped`**） | **Legacy 照常** |
| **`tryExecute` 内 Planner/Composer 失败** | **§14.4**：写失败观测 | **Legacy 不受影响** |

**措辞澄清（C-63）**：**`shadow.enabled=false`**（默认）时 **普通 SHADOW Run 不旁路 Composite**（**§16.2** 第 3 步）；**`enabled=true`** 时叠加名单 + 进程内限流。**运维硬关旁路**：亦可置 **`productionEnabled=false`** 或 **`executionMode≠SHADOW`**。**不接 PRIMARY**；**不替换终稿**；**Harness **`caseId`** 不作放行依据**。

---

## 17. **C-63：最小实现（已实装）**

1. **`ShadowDecision`**：`allowed`、`skipped`、`skipReason`、`whitelistMatched`、`throttleHit`、`debug`（**`com.nongxinle.ai.planner.ShadowDecision`**）。
2. **`ShadowPolicy`**（**`com.nongxinle.ai.planner.ShadowPolicy`**，`@Component`）：**`evaluate(AiRunState, AiResolvedQueryContext)`** → **`ShadowDecision`**；不读用户原文；**Harness 不经此闸**。
3. **`AiRunService#maybeExecuteShadowCompositePlanner`**：在 **`tryExecute`** **前**调用 **`ShadowPolicy`**；**skip** ⇒ **`BusinessDiagnosisCompositeExecutionResult`**（**`compositeShadowSkipped`** 等）**不写 **`finalAnswerText`**。
4. **默认 **`shadow.enabled=false`**：旁路 Composite **关闭**。
5. **限流 MVP**：进程内 **分钟 / 小时** 计数 + **`userId` / `distributerId`** **冷却 Map**（**`cooldownSeconds`**）；**占位成功即计数**（与 **`tryExecute`** 原子段同锁内）。

### 17.1 **C-63.1**：本地验收脚本（**`scripts/c63-shadow-verify.sh`**）

**用途**：不换 Java、只轮换 **`application.properties`** 中 **`ai.composite.businessDiagnosis.shadow.*`**（并临时写入 **`productionEnabled=true`** / **`executionMode=SHADOW`** 若缺失，以便进入旁路分支），每轮提示 **重启后端** 后 **`POST /api/ai/runs`** → 解析 **`runId`** → **`GET /api/ai/runs/{runId}/events`**，将 create 响应与 SSE 文本落到 **桌面**固定文件名；**`EXIT`/`INT`/`TERM` 与本脚本正常结束**时均从启动时备份还原 **`src/main/resources/application.properties`**。

```bash
chmod +x scripts/c63-shadow-verify.sh
# 后端默认 http://localhost:8090 且 server.servlet.context-path=/api
BASE_URL=http://localhost:8090/api scripts/c63-shadow-verify.sh
```

**环境变量**：**`BASE_URL`**（默认同上）、**`C63_EVENTS_MAX_TIME`**（SSE **`curl --max-time`** 秒，默认 **240**）。

**输出**（**`~/Desktop/`**）：**`c63-shadow-disabled-{create.json,events.txt}`**、**`c63-shadow-whitelist-hit-*`**、**`c63-shadow-whitelist-miss-*`**。**备份**：**`scripts/.c63-shadow-verify.application.properties.backup.<时间戳>`**（可自行删除）。

### 17.2 **C-63 三轮手工验收（已通过）**

**入口**：**`POST /api/ai/runs`**（例：`userId=1`、`departmentId=1`、`distributerId=2`、`scopeMode=GROUP`、问句「这个月经营得怎么样？」），**`GET /api/ai/runs/{runId}/events`**（或 **`run_finished` / `answer_delta`** 合并摘要）；**`productionEnabled=true`**、**`executionMode=SHADOW`**；灰度三轮分别轮换 **`ai.composite.businessDiagnosis.shadow.*`**（可用 **§17.1** 脚本）。**结论**：观测与 **`ShadowPolicy`** 设计一致，**C-63 验收通过**。

**契约（本轮再次确认）**：**SHADOW** **不替换**用户主终稿；**`compositeShadowFinalAnswerReplaced=false`**（命中白名单且 **`compositeExecuted=true`** 时仍为 **`false`**）。**`compositeFinalAnswerText`** 仅 **debug / SSE** 载体，**不得**覆盖 **`finalAnswerText` / `answerPreview`**。

**性能与读放大（告警）**：**whitelist 命中**、**全旁路**下本次样例 **`compositeShadowLatencyMs` ≈ 27059ms（约 27s）**；后续 **SHADOW 灰度扩面**须与 **§13.3 / §16** 一致，关注 **墙钟**、**四域 Tool 重复读**、**DB / SSE 体积**（限流与名单仍必备）。

| 轮次 | 配置要点 | 摘要键观测（样例） |
|------|----------|-------------------|
| **1 · shadow 关** | **`shadow.enabled=false`** | **`compositeGateAllowed=true`**，**`compositeGateReasonCode=ALLOWED_GROUP`**，**`compositeExecutionMode=SHADOW`**，**`compositeExecuted=false`**，**`compositeShadowSkipped=true`**，**`compositeShadowSkipReason=SHADOW_GRAY_DISABLED`** |
| **2 · whitelist 命中** | **`shadow.enabled=true`**，**`userWhitelist=1`**，**`scopeWhitelist=GROUP`**（与请求 **GROUP** 一致） | **`compositeGateAllowed=true`**，**`compositeGateReasonCode=ALLOWED_GROUP`**，**`compositeExecutionMode=SHADOW`**，**`compositeExecuted=true`**，**`compositeExecutionSuccess=true`**，**`compositeFallbackRequired=false`**，**`compositeShadowSkipped=false`**，**`compositeShadowWhitelistMatched=true`**，**`compositeShadowThrottleHit=false`**，**`compositeShadowFinalAnswerReplaced=false`**，**`compositeShadowLatencyMs=27059`**，**`compositeFinalAnswerText` 非空** |
| **3 · whitelist 未命中** | **`shadow.enabled=true`**，**`userWhitelist=999999`**，**`scopeWhitelist=GROUP`** | **`compositeGateAllowed=true`**，**`compositeGateReasonCode=ALLOWED_GROUP`**，**`compositeExecutionMode=SHADOW`**，**`compositeExecuted=false`**，**`compositeShadowSkipped=true`**，**`compositeShadowSkipReason=WHITELIST_NO_MATCH`**，**`compositeShadowWhitelistMatched=false`**，**`compositeShadowThrottleHit=false`** |

---

## 18. **C-64：`SHADOW` 灰度上线策略（仅文档；索引）**

在 **C-63**（**`ShadowPolicy` 接线** + **三轮验收**）基线上，约定 **何时开放旁路、受众、限流、观测与立即关闸**。本文件 **§13.3 / §16～§17** 为 **机制与配置语义**；**运营与 SRE 放行清单** 见独立权威：

- **[`business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md)** — **C-64**：**当前状态（Gate + ShadowPolicy + 默认关 + 不换终稿）**、**灰度范围**、**限流**、**必看指标**、**立即关闭条件（§5）**；**§6** → **C-65** 清单；**§7** → **C-66+  backlog**。

**Gate / Planner 总索引**：**[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md) §C-64～§C-65**；**[`planner-executor-v1-design.md`](./planner-executor-v1-design.md) §27**。

---

## 19. **C-65：`SHADOW` 灰度观测与复盘清单（仅文档；索引）**

在 **C-64**「谁 / 限流 / 关闸总则」之上，**操作化** **每请求须记录字段**、**每日复盘表**、**扩大灰度准入** 与 **暂停判据**（**只依赖** **Gate + Resolver 物化上下文 + `composite*`**）。**权威**：

- **[`business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md)**

**文档版本**：**C-63 §17 已编码** + **§17.2 手工验收已收口** + **C-64 §18** + **C-65 §19** — **本阶段 Composite 生产接入安全框架已收口**；**C-66 暂缓**；**D-1** 见 **[`PROJECT_HANDOFF_D1.md`](./PROJECT_HANDOFF_D1.md)**。
