# Harness Engineering：解析、工具、计划与 Composer

> **读者**：接手餐饮 AI 多智能体与菜品毛利 Harness 的工程师。  
> **目的**：说明「用工程化框架约束 AI」的分层模型——何为硬约束、何处可交给模型；**AnswerPlan** 与 **Composer** 的职责边界；如何复用旧单板能力而**不**继续堆 `if/else`。  
> **范围**：架构与文档契约；**不**在本文件内重写菜品成本算法或采购/出库业务细节（参见 `dish-profit-legacy-review.md`、`ai-skill-dish-cost-diagnosis.md`）。

---

## 1. Harness Engineering 思想

目标不是把经营问题写成巨型 `if/else`，而是：

```text
让「理解 → 查数 → 规划回答 → 说人话」每一层都可复现、可调试、可回放、可评估。
```

每一轮 Run 至少在 Debug / Replay 中能看清：

| 维度 | 说明 |
|------|------|
| 用户问了什么 | `originalQuestion` / `normalizedQuestion`、多轮追问合并结果 |
| intent / path | `AiResolvedQueryContext.effectiveIntentCode` / `effectivePathCode` |
| 时间 | `AiResolvedTimeWindow`（来源：`effectiveTimeWindowSource`） |
| 范围 | `AiResolvedDataScope`：**门店 / 部门 / 经销主体** 与 SQL 展开分离 |
| Tool 参数 | 必须可由 `ResolvedQueryContext` 推导、落盘可追溯 |
| Tool 返回 | 结构化事实（数字、列表、行），**不写漂亮话** |
| 回答计划 | **AnswerPlan**：本轮要完成的少数稳定业务任务类型 |
| 自然语言 | **Composer**：只根据 AnswerPlan + Tool 数据组织话术 |

代码入口参考：`AiRunService`、`AiResolvedQueryContextResolver`、`MasterBusinessAgent`、`BusinessToolExecutionNode`、`StubAnswerComposerNode`、`AiHarnessResolvedContextSummarizer`。Master 与子 Agent 契约见 **`docs/ai/master-business-agent-design.md`**（**「当前已接入的 DomainAgent」**）。

---

## 2. 分层契约（必须遵守）

### 2.1 ResolvedQueryContext（唯一公共查询上下文）

`AiResolvedQueryContext` 是单次 Run 的**Harness 入口**：用户身份、组织范围、时间窗、意图与数据口径。后续 Agent / Tool 应**只读**本对象，避免重复从请求体各自解析范围。

只读路径典型包括：`orgScope`、`timeWindow`、`queryIntent`、`dataScope`、`effectiveIntentCode` / `effectivePathCode`、`mentionedDishName`、`dishProfitMetricType`（由结构化意图推导）等。

类内文档：`AiResolvedQueryContext.java`。

### 2.2 范围模型：主语义 vs SQL 展开（硬约束）

**禁止**再使用「混合数组」同时表示门店 root 与子部门语义（历史上 `queryDepartmentIds` 混入多种 ID 的问题）。

主查询维度（互斥，由 `queryScopeKind` 指定）：

| kind | 主字段 | 说明 |
|------|--------|------|
| `STORE` | `queryStoreIds` | 门店 **rootId** 列表，不含子部门 |
| `DEPARTMENT` | `queryRealDepartmentIds` | **仅**用户明确点到部门时的真实部门 ID |
| `DISTRIBUTER` | `queryDistributerId` | **单值**组织/经销主体 |

业务表 `department_id IN (...)` 使用 **`expandedSqlDepartmentIds`**（或与 `AiResolvedDataScope#getEffectiveSqlDepartmentIds()` 同源的值），与「按店/按部门/按主体」的**用户语义**解耦。

辅助结构：`storeToDepartmentIds`（门店 → 直属子部门，**说明用**，不作主查询数组）。

**Debug / Replay**：展示 `queryScopeKind`、`queryStoreIds`、`queryRealDepartmentIds`、`queryDistributerId`、`expandedSqlDepartmentIds`；**不要**把已废弃的混合 `queryDepartmentIds` 当作主查询语义字段对外展示。若 SQL 内部需要命名，可用 `expandedSqlDepartmentIds`、`departmentIdsAllowFilter`、`internalSqlDepartmentIds` 等**实现侧**名称，但文档与面板以 Harness 字段为准。

详细说明见：`AiResolvedDataScope.java`、`AiHarnessResolvedContextSummarizer` 中的 `departmentScopeModelNote`。

### 2.3 Tool Args（必须来自 ResolvedQueryContext）

原则：**Tool 参数不得与解析层漂移**。例如菜品毛利工具应与 `AiResolvedDataScope` 及 `timeWindow` 对齐（`DishProfitAnalysisTool` 使用 `ARG_QUERY_SCOPE_KIND`、`ARG_QUERY_STORE_IDS`、`ARG_RESOLVED_DEPARTMENT_IDS`、日期、`ARG_DISH_PROFIT_STRUCTURED_DETAIL`、`ARG_DISH_NAME_FOCUS_HINT` 等）。

采购、出库 / 核销 **StockReduceAnswerPlan** 链路等**已通过阶段验收的链路，无需求变更时不要大改**（仅 bugfix；见 `docs/ai/purchase-answer-plan.md`、`docs/ai/stock-reduce-answer-plan.md`）。**日营业额 / 营收** **DailyRevenueAnswerPlan**（**`revenue_overview_path`**）见 **`docs/ai/revenue-answer-plan.md`**——**核心 Harness 已落地**（Builder + **`revenueAnswerPlan*`** Summarizer + **`StubAnswerComposerNode`** 优先宣读）；已知限制（外卖无平台分列、单日日期依赖 Tool 等）见该文档 §11。营收改动 **不得** 扰动已冻结的采购 / 出库 / **`DishProfitAnswerPlan`** 主线。

### 2.4 Tool Result（结构化事实）

- 输出：**可序列化的事实**（金额、份数、行列表、`buildInsight` 衍生结构等）。
- **不**承担：长篇解释、修辞、「建议」的完整撰写（可由 AnswerPlan 指定是否引入建议类段落，但数字仍须来自 Tool）。

菜品毛利权威数字来自 **`GbDepFoodBusinessInsightService#buildInsight`** 及既有成本服务，**不重写算法**（见 `docs/ai/dish-profit-legacy-review.md`）。

### 2.5 AnswerPlan（本轮「怎么答」的稳定类型）

**AnswerPlan** 是一小组**业务任务类型**枚举（例如菜品毛利的 `DISH_PROFIT_OVERVIEW`、`DISH_LOWEST_MARGIN` 等），不是话术模板、不是每个同义词一条分支。

- **归一**：多种自然语言问法映射到**同一个** AnswerPlan 类型（例如「哪道菜最不赚钱」「哪个拖后腿」「毛利最低」→ `DISH_LOWEST_MARGIN`）。
- **选数**：排行类计划在**服务端**根据 `dishRows`（或等价列表）**按规则排序/截断**，得到 `focusDishes`、`metrics` 等**已计算好的展示载荷**，而不是让 Composer 自己排序或心算。
- **可追溯**：Replay 应能展示「本 AnswerPlan 类型 + 选中行 ID/菜名 + 引用的字段名」。

详细枚举与迁移步骤见：**`docs/ai/dish-profit-answer-plan.md`**（本文不重复展开）。

### 2.5b DiagnosisPlan（经营诊断：只聚合子 AnswerPlan）

**经营诊断**问法在上层增加 **DiagnosisPlan**（见 **`docs/ai/diagnosis-answer-plan.md`**）：**DiagnosisPlanBuilder** **只读** 本轮已产生的 **`PurchaseAnswerPlan`**、**`StockReduceAnswerPlan`**、**`DishProfitAnswerPlan`**、**`DailyRevenueAnswerPlan`**，组装 `summary` / `focusFindings` / `evidenceRows` / `riskRows` / `actionSuggestions` / `debug`；**不得**回退为从原始 `toolResults` 重算、重排后再交给 Composer。**DiagnosisComposer** 与单域 Composer 边界相同：只表达、不心算。

阶段一可仅文档与字段定稿；**四条主链路代码冻结**，诊断模块不得反向污染采购 / 出库 / 毛利 / 营收 Builder 与主 Tool。

### 2.6 Composer（只负责自然语言）

**Composer**（如 `StubAnswerComposerNode` 中各 `*_COMPOSER_SYSTEM`）职责：

- 消费：**AnswerPlan** + **Tool 结构化结果**（及必要的 `queryScopeBanner`、`summary` 等）；**经营诊断路径**以 **DiagnosisPlan** 为主输入（见 **`diagnosis-answer-plan.md`**），**不**再直接扫多域原始 Tool。
- 产出：用户可读中文，遵守产品约束（不写 JSON、不出现内部字段名等）。

**Composer 不能做的事（硬约束）**：

- **不得**自行计算毛利率、成本差额、利用率等；**必须**使用 Tool / `buildInsight` / `buildReport` 已给出的字段或已格式化的可读串。
- **不得**编造 Tool 未返回的数字或排行。
- **不得**把 type2 叫「损耗」、把 type3 叫「废弃」；退货 type4 为单独口径（与出库链路文档一致）。

菜品毛利 Composer 现有系统提示：`DISH_PROFIT_COMPOSER_SYSTEM`（`StubAnswerComposerNode`）。后续若引入 AnswerPlan，应在提示中明确「仅展开 AnswerPlan 指定焦点与 Tool 中对应字段」。

---

## 3. 哪些必须工程硬约束 vs 哪些可以让 AI 判断

### 3.1 建议硬约束（代码 / Schema / 单一路径）

| 类别 | 示例 |
|------|------|
| 范围与时间 | `queryScopeKind` + 门店/部门/主体字段与 `expandedSqlDepartmentIds`；`timeWindow` 起止与继承规则 |
| 路由 | `intent` / `path` 与权限、Tool 选择 |
| Tool 入参完整性 | 缺参则失败或降级，且日志可追踪 |
| 金额与毛利率 | 一切财务比率、汇总：**以服务端字段为准** |
| 出库分型措辞 | type1～type4 中文标签统一 |
| Debug 摘要 | `AiHarnessResolvedContextSummarizer` 输出字段稳定，便于 Replay 比对 |

### 3.2 可以让模型辅助（须在边界内）

| 类别 | 示例 |
|------|------|
| 自然语言理解 | 将多样问法**建议**映射到 `structuredIntentDetail` / AnswerPlan（最终可由规则校验或二次模型） |
| 语气与结构 | 在 AnswerPlan 与 Tool 数据锁定后，组织段落、过渡句、「详细见卡片」类提示 |
| 空数据时的解释 | 在 Tool 已返回「无行/不完整」前提下，生成**不编造数字**的说明 |

**错误模式**：为每个同义词写 `message.contains("...")`。**正确模式**：归一到 `structuredIntentDetail` 或 AnswerPlan 类型 + 统一排序/选行逻辑。

---

## 4. 如何复用旧单智能体能力

旧单板的核心价值在**数据与口径**，不在聊天模板：

| 复用方式 | 说明 |
|----------|------|
| 直接调用服务 | `GbDepFoodBusinessInsightService#buildInsight`、`GbDishCostAnalysisService` 报表与分摊逻辑 |
| Mapper / SQL | `GbDepartmentGoodsStockReduceMapper.xml` 等；不改口径前提下复用 |
| 字段与文档 | `ai-skill-dish-cost-diagnosis.md`、`docs/gb-dish-cost-analysis-frontend.md`、`dish-profit-legacy-review.md` |
| Graph 接表 | `DishProfitAnalysisTool` 已接 `buildInsight`；DTO 如 `AiDishProfitOverviewResult` / `AiDishProfitDishBrief` |

**不接**：把旧聊天里的硬编码月份、混合 `queryDepartmentIds` 展示、无限 `if` 问法分支原样搬进新图。

---

## 5. 如何避免继续堆 if/else

1. **归一**：用户话 → `structuredIntentDetail`（wire）→ **AnswerPlan 类型**（少量枚举）。  
2. **选行**：排行/原因类在 Java 或统一的小型「选数策略」中完成（对 `dishRows` 排序、`mentionedDishName` 过滤），输出**窄化后的结构化 payload**。  
3. **生成**：Composer 只读 payload + AnswerPlan，**禁止**再按字符串判断「最低毛利」类问法。  
4. **迁移**：`DishProfitAgentNode` 中 `isLowMarginRankingQuestion()` 等**仅作迁移参考**，新需求优先加 **AnswerPlan + structuredIntentDetail**，而非新方法。

---

## 6. 相关文件索引

| 主题 | 路径 |
|------|------|
| 解析上下文 | `AiResolvedQueryContext.java`、`AiResolvedDataScope.java`、`AiResolvedQueryContextResolver.java` |
| Tool 执行与参数 | `BusinessToolExecutionNode.java`、`DishProfitAnalysisTool.java`、`StockReduceQueryTool.java` |
| Composer | `StubAnswerComposerNode.java` |
| Harness Debug | `AiHarnessResolvedContextSummarizer.java` |
| 菜品毛利 legacy | `docs/ai/dish-profit-legacy-review.md`、`GbDepFoodBusinessInsightServiceImpl`、`GbDishCostAnalysisServiceImpl` |
| Agent 节点（迁移中） | `DishProfitAgentNode.java` |
| Skill 约束 | `src/main/resources/ai-skill-dish-cost-diagnosis.md` |
| AnswerPlan 专项 | **`docs/ai/dish-profit-answer-plan.md`**、**`docs/ai/purchase-answer-plan.md`**、**`docs/ai/stock-reduce-answer-plan.md`**（出库 / 核销 **`stock_reduce_query_path`**）、**`docs/ai/revenue-answer-plan.md`**（日营业额 / 营收 **`revenue_overview_path`**，核心 Harness 已落地）、**`docs/ai/diagnosis-answer-plan.md`**（**DiagnosisPlan**，阶段一文档定稿） |
| Master / 子 Agent 编排（设计与运行时） | **`docs/ai/master-business-agent-design.md`**（**四条专线已接入 Master**，见 **「当前已接入的 DomainAgent」**；Replay **`V2_SEMANTIC_MAINLINE_CORE_10`** 见 **`docs/AI_HARNESS_REPLAY_CASES.md`**） |

---

## 7. 文档维护

- 本文件描述 **跨域 Harness 架构**；菜品毛利 AnswerPlan 枚举与字段表以 `dish-profit-answer-plan.md` 为准；采购以 `purchase-answer-plan.md` 为准；**出库 / 核销**（**`STOCK_REDUCE_QUERY` / `stock_reduce_query_path`**）以 **`stock-reduce-answer-plan.md`** 为准（**2026-05-12** 起 **前台验收通过、链路冻结**，后续仅 bugfix）；**日营业额 / 营收** **`DailyRevenueAnswerPlan`** 以 **`revenue-answer-plan.md`** 为准（**核心已交付**，Summarizer / **`docs/API_INTEGRATION.md`** **`revenueAnswerPlan`** 专节已对齐）。**经营诊断 DiagnosisPlan** 以 **`diagnosis-answer-plan.md`** 为准（**阶段一文档定稿**，实现待阶段二）。**MasterBusinessAgent**：四条单领域专线（营收 / 采购 / 出库 / 菜品毛利）已于 **2026-05-13** 在主链路落地（**`docs/ai/master-business-agent-design.md`**）；变更语义解析或 Master / **`BusinessToolExecutionNode`** 时 **优先** 跑 **`docs/AI_HARNESS_REPLAY_CASES.md`** · **`V2_SEMANTIC_MAINLINE_CORE_10`**。  
- 若新增链路（非采购/出库 базовые），应在本文件增补一层「Tool → AnswerPlan → Composer」的约定，避免各 Agent 私自分叉。  
- **OrchestrationDecisionService** / 多 Agent 经营概览 / 诊断编排：见 **`docs/TODO_MULTI_AGENT.md`** 下一阶段与 **`docs/HARNESS_ORCHESTRATION_DECISION.md`**。

---

*版本：与仓库当前 Harness 代码与 `dish-profit-legacy-review.md` 对齐；大改解析或 Composer 时请同步更新本节。*
