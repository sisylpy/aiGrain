# D-12 — Harness Minimal Gates（长期最小回归门卫）

## Minimal Gates v1 验收结果

- **状态**：v1 已在本地验收通过，**7** 个内置 `caseId` **`overallPass=true`**（全部 **PASS**）：
  - `V2_SEMANTIC_MAINLINE_CORE_10`
  - `BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`
  - `BUSINESS_DIAGNOSIS_V1_CORE_3`
  - `REVENUE_AGENT_GRAPH_CORE`
  - `PURCHASE_AGENT_GRAPH_CORE`
  - `STOCK_REDUCE_AGENT_GRAPH_CORE`
  - `DISH_PROFIT_AGENT_GRAPH_CORE`
- **运行命令**（仓库根目录）：
  ```bash
  bash scripts/harness/run-minimal-gates.sh
  ```
- **默认请求 fixture（GROUP_MANAGER）**：`userId=3`、`distributerId=2`、`scopeMode=GROUP`、`strictStoreSqlMatch=false`、不传 `departmentId`；**`frozenClockDate`** 默认 **`2026-05-15`**（可用环境变量 **`FROZEN_CLOCK_DATE`** 覆盖）。
- **`summary.txt` 列**（制表符分隔，首行为 `#` 注释表头）：**`caseId`** / **`replayMode`** / **`overallPass`** / **`failedRounds`** / **`firstFailedField`** / **`status`**。其中 **`failedRounds`** 为各轮 **`rounds[]` 中 `pass=false`** 的条数；**`firstFailedField`** 为首轮失败断言的简短诊断（已截断、去换行/Tab，避免一行过长）。
- **D-11 Permission Boundary**：**尚未**固化为内置 Java `caseId`；当前仍为 **spot gate / 人工复核**（见下文 **Permission Boundary**）——与 Minimal Gates v1 的 **7** 个自动 PASS 用例 **分离**。权限角色 **Permission Spot Gates**（**D-13**）已在本地 **`v1` 验收通过**（四类 persona 全部 **`AUTO_PASS`**），作为 D-12 主链路门卫之外的 **权限边界补充门卫**——见 **`docs/D13_PERMISSION_SPOT_GATES.md`**，脚本 **`scripts/harness/run-permission-spot-gates.sh`**。

## 目标

固化 **长期最小回归门卫**，防止 **D-9（经营诊断）**、**D-10（多轮上下文 / Graph 探针）**、**D-11（权限边界）** 已验收主链在后续改动中被静默改坏。

- **范围**：语义解析、Resolver、FollowUp、时间窗、组织范围、单域 Graph、经营概览四域、经营诊断多 Agent、关键 Harness 摘要字段。
- **不替代**：生产 `POST /api/ai/runs` 全链路、PlannerExecutor 平行族、Composite Gate 单测、权限场景的完整自动化（见下文 **Permission Boundary**）。

## 一键脚本

- 脚本路径：**`scripts/harness/run-minimal-gates.sh`**（推荐 **`bash scripts/harness/run-minimal-gates.sh`**；亦可 `chmod +x` 后直接执行）
- 环境变量：**`BASE_URL`**（默认 `http://localhost:8090/api`）、**`FROZEN_CLOCK_DATE`**（默认 `2026-05-15`）、**`OUT_DIR`**（默认 `out/harness-gate-YYYYMMDD-HHMMSS/`）
- 默认请求：`BASE_URL` → **`POST`** `$BASE_URL/ai/harness/replay`（勿重复拼接 `/api`：若 `BASE_URL` 已含 context-path，脚本只追加 `/ai/harness/replay`）
- **Prerequisites**：本地 **`ai.harness.replay-enabled=true`**；数据库与 **`gb_department_user`** 与文档占位（AAA / 汀兰等）大致一致时，`overallPass` 更稳定。
- **HTTP 响应形态**：`POST /ai/harness/replay` 返回 **`R`** 包装，`AiHarnessReplayResponse` 在键 **`replay`** 下（**不是**根级 `overallPass`）。脚本的 `summary` 已按 **`replay` 优先、否则整包**解包；`pretty.json` 中可见 `"code": 0` 与同级的 **`"replay": { ... }`**。
- **`summary.txt`**：详情见上文 **Minimal Gates v1 验收结果**；若出现 **`probe_no_expectation`**（`exploreProbeReplay`），**勿**与常规 **`NEED_REVIEW`** 混淆。

## 最小门卫 case 表

下列 **7** 个 `caseId` 与内置 **`AiHarnessBuiltinCases`** 预期对齐；脚本按此顺序依次回放。

| # | `caseId` | 覆盖能力 | 默认 `replayMode` | 建议 `frozenClockDate` | 是否完整业务图（`GRAPH_RUN`） |
|---|-----------|----------|-------------------|-------------------------|------------------------------|
| 1 | `V2_SEMANTIC_MAINLINE_CORE_10` | v2 语义、多轮时间/范围继承、多店排行 wire、经营概览/对比语义入口、`harnessReplay*` 探针（**不跑** Tool） | **`RESOLVER_ONLY`**（省略即默认） | **`2026-05-15`**（脚本固定；可与文档其他示例互换） | 否 |
| 2 | `BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3` | 经营概览 **MULTI_AGENT**、四域 batch、`missingAnswerPlans`、双店对比继承 | **`GRAPH_RUN`**（省略即默认） | 同上 | 是 |
| 3 | `BUSINESS_DIAGNOSIS_V1_CORE_3` | 经营诊断 **MULTI_AGENT**、集团→单店→双店原因、四域消费、`businessDiagnosisPlan` 相关探针 | **`GRAPH_RUN`**（省略即默认） | 同上 | 是 |
| 4 | `REVENUE_AGENT_GRAPH_CORE` | 营业额单域、`revenue_query`、误收 **overview/diagnosis** 回归 | **`GRAPH_RUN`**（省略即默认） | 同上 | 是 |
| 5 | `PURCHASE_AGENT_GRAPH_CORE` | 采购单域、`purchase_overview` | **`GRAPH_RUN`**（省略即默认） | 同上 | 是 |
| 6 | `STOCK_REDUCE_AGENT_GRAPH_CORE` | 出库核销单域、`stock_reduce_query` | **`GRAPH_RUN`**（省略即默认） | 同上 | 是 |
| 7 | `DISH_PROFIT_AGENT_GRAPH_CORE` | 菜品毛利：低毛利排行 → 点名菜 → 本月高毛利排行（metric **OVERRIDE**） | **`GRAPH_RUN`**（省略即默认） | 同上 | 是 |

### 各 case 主要关注字段（摘要 / 轮末）

- **共用**：`effectiveIntentCode`、`effectivePathCode`、`structuredIntentDetailWire`（或 `structuredIntentDetail`）、`scopeType`、`visibleStores`、`startDate` / `endDate`、`effectiveTimeWindowSource`。
- **V2**：`semanticAdoptedFrom`、`querySemanticV2*`、`harnessReplayPlanSource`、`harnessReplay*AnswerPlanType`、多店标志 `multiStoreScopeApplied` 等（详见 **`docs/AI_HARNESS_REPLAY_CASES.md`** · Case V2）。
- **GRAPH_RUN（概览 / 诊断）**：`orchestrationTaskMode`、`consumedAnswerPlans` / `missingAnswerPlans`、`businessOverviewSuccessfulDomains`（概览）、`dataPlanTools` / `usedTools`、`permissionDenials`、`diagnosisPlanExists`、`businessDiagnosisPlanExists`、`businessDiagnosisPath`、`finalAnswerTextBlank`。
- **单域 GRAPH**：`usedTools` 含对应 tool id、`master*ToolResultSuccess`、对应 AnswerPlan 类型探针；**禁止** `effectiveIntentCode` 落 `BUSINESS_OVERVIEW` / `BUSINESS_DIAGNOSIS`（内置已对单域 case 做约束）。

## D-11 Permission Boundary Spot Gates（第一版）

**D-11** 权限边界 **尚未**固化为 **`AiHarnessBuiltinCases`** 中的内置 **`caseId`**；Minimal Gates v1 的 **7** 条为 **GROUP_MANAGER** 广角回归，**不**替代 D-11 角色矩阵。

权限 spot check 请使用 **`docs/ai/d11-permission-frozen-role-fixtures.md`** 的 **Frozen Role Fixture**：

| Persona | `userId` | `scopeMode` | `departmentId` | 说明 |
|---------|----------|-------------|----------------|------|
| GROUP_MANAGER（集团管理） | **3** | **GROUP** | （不传，避免门店锚点收窄） | 与 Minimal Gates 脚本默认一致 |
| STORE_PURCHASER（采购员 / 汀兰） | **2** | **STORE** | **3** | 汀兰餐厅 |
| WAREHOUSE_MANAGER（库房 / AAA） | **1** | **STORE** | **1** | AAA |
| STORE_MANAGER（AAA 店长） | **4** | **STORE** | **1** | AAA |

对权限场景：**可复制**同上 `caseId` 与 `messages`，仅替换 **`userId` / `departmentId` / `scopeMode` / `distributerId`**（集团需 `distributerId`；单店常需 `departmentId`）。**第一版不**将权限 spot 纳入脚本的自动 **PASS**：结论以 **`pretty.json`** 人工判断为准（无泄露、权限提示正确、禁词等）。

## PASS / NEED_REVIEW / FAIL 规则

| 场景 | 判定 |
|------|------|
| 内置 **7** case，`overallPass === true` | **PASS** |
| 内置 **7** case，`overallPass === false` | **FAIL**（脚本 **`exit 1`**） |
| `overallPass === null`（如 `PROBE` / `AD_HOC` / `ignoreExpectations`） | **NEED_REVIEW**；脚本**不一定** `exit 1` |
| **`PROBE` / `AD_HOC`** | **不得**当作自动通过 |
| **D-11 权限边界**（换 frozen role 的手动/自定义请求；**无**内置 `caseId`） | **NEED_REVIEW / 人工复核**：上传 **`pretty.json`** 判断（无泄露、权限提示正确、禁词等） |

## 关键字段清单（抽测 / 复盘）

用于从 **`resolvedQueryContextSummary`** 或探索型 **`probe`** 中快速扫读：

- **路由与结构化**：`effectiveIntentCode`、`effectivePathCode`、`structuredIntentDetailWire`
- **范围**：`scopeType`、`visibleStores`
- **工具与计划**：`dataPlanTools`、`usedTools`、`harnessReplayPlanSource`、`harnessReplay*AnswerPlanType`（如 `harnessReplayRevenueAnswerPlanType` 等）
- **诊断 / 对比**：`diagnosisPlanExists`、`businessDiagnosisPlanExists`、`harnessReplayStorePriorityRankingRowsLen`（及 Summarizer 摊平的同类键）、`harnessReplayStoreCompareEvidenceRowsLen` / `businessStoreCompareEvidenceRowsLen`
- **权限与产出**：`permissionDenials`、`finalAnswerTextBlank`

**禁止**：对 **`finalAnswerText`** 做**全文 diff**（数据与口径随环境变）。仅可做 **轻量**检查：是否空文本、是否含已知**禁串**、权限场景是否出现**权限提示**而非越权数值等。

## 相关文档

- 用例详解与请求示例：**`docs/AI_HARNESS_REPLAY_CASES.md`**
- D-11 Fixture：**`docs/ai/d11-permission-frozen-role-fixtures.md`**
