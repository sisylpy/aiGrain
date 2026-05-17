# 日营业额 / 营收链路：DailyRevenueAnswerPlan 与 Harness 契约

> **状态（2026-05-12 更新）**：**核心 Harness 已落地**——**`DailyRevenueAnswerPlan`**（下称 **营收 AnswerPlan**）由 **`DailyRevenueAnswerPlanBuilder`** 在 **`revenue_overview_path`** 下挂载至 **`AiRunState`**；**`StubAnswerComposerNode`** 在计划可用时 **优先宣读** **`focusRows` / `secondaryRows`**（不重算营业额、不重排行）；**`AiHarnessResolvedContextSummarizer`** / **`resolvedQueryContextSummary.revenueAnswerPlan*`** 与 SSE **`answer_delta.data.revenueAnswerPlan`** 供 Debug / Replay / 前台与 **`docs/API_INTEGRATION.md`** 对齐。  
> **前提**：口径不重新发明——以 **`RevenueQueryTool`** → **`GbAiDailyRevenueService#getStatsByDepartmentId`**（**`rawStats`**）及既有 Mapper 字段为准（见 **`docs/LEGACY_AI_ANSWER_ASSETS.md`**、**`docs/TODO_MULTI_AGENT.md`**）。  
> **边界**：本链路 **不替代** **`business_overview_path`** 经营概览四 Tool 编排；成本主线仍可 **`revenue_query`**；营收 AnswerPlan **仅服务** **`revenue_overview_path`** / **`REVENUE_OVERVIEW`** 意图下的直连营业额问答。  
> **经营诊断**：**`BusinessDiagnosisPlan`** 为更上层综合；可消费 Tool 事实，**不替代**本条 AnswerPlan 契约。

全局分层说明见：`docs/ai/harness-composer-architecture.md`。采购对照：`docs/ai/purchase-answer-plan.md`。出库对照：`docs/ai/stock-reduce-answer-plan.md`。

---

## 0. 定位与职责边界

### 0.1 DailyRevenueAnswerPlan 的定位

- **是什么**：服务端为本轮 Run 生成的 **结构化回答计划**（JSON 友好），锁定「营业额相关」用户可见结论的行级事实与 **`planType`**，供 Composer **只读宣读**、Harness **比对 Replay**、前台 **`planSource=revenueAnswerPlan`** 展示。
- **不是什么**：不是替代 **`PurchaseAnswerPlan`** / **`StockReduceAnswerPlan`** / **`DishProfitAnswerPlan`** / **`BusinessDiagnosisPlan`**；不在此链路内改写采购 / 出库 / 毛利 / 诊断 Builder 或 Composer 主路径。
- **path / intent**：解析层 **`effectivePathCode`** 为 **`revenue_overview_path`**，**`effectiveIntentCode`** 一般为 **`REVENUE_OVERVIEW`**；细分语义由 **`structuredIntentDetail`**（wire）与 AnswerPlan **`planType`** 表达。

### 0.2 Tool / Builder / Composer

| 层级 | 职责 | 典型类 |
|------|------|--------|
| **Tool** | 按 **`AiResolvedQueryContext`** 参数查库，返回 **`revenue_query`** 信封（**`data.rawStats`**、**`totalRevenue`**、**`storeRevenueRanking`** 等）；**不**负责回答话术、**不**决定 **`planType`** | **`RevenueQueryTool`** |
| **Builder** | **`BusinessToolExecutionNode`** 在 **`success`** 且 **`AiRunState.isRevenueOverviewPath()`** 时调用 **`DailyRevenueAnswerPlanBuilder.attachIfApplicable`**：解析信封、映射 **wire → planType**、填充 **`focusRows` / `secondaryRows` / `summary` / `debug`** | **`DailyRevenueAnswerPlanBuilder`** |
| **Composer** | **`revenueOverviewPath`** 且 **`revenueAnswerPlan`** 可用时，**优先**按 **`composeRevenueDeterministicFromAnswerPlan`**（及分支）宣读计划；**禁止**自算营业额 / 客单 / 重排门店或日排行 | **`StubAnswerComposerNode`** |

---

## 1. 目标架构（Harness 四层）

```text
AiResolvedQueryContext（timeWindow、dataScope、queryIntent、structuredIntentDetail、orgScope.visibleStores …）
    → RevenueQueryTool / 既有日营收查询入口（参数均可追溯到 Context；SQL / 聚合口径不变；本阶段不重写 Tool）
    → ToolResult（结构化事实：总额、渠道拆分、按日序列、按门店序列、订单/客单等）
    → DailyRevenueAnswerPlan（本轮 planType + revenueChannel + focusRows / secondaryRows + summary + debug）
    → Composer：仅朗读 AnswerPlan + 必要边界说明；禁止重算营业额、订单数、客单价、排行顺序
    → Debug / Replay：透出 revenueAnswerPlan* 与上下文字段
```

**Composer 不得**：自行汇总营业额、自行计算客单价（除非 Tool 已给出可供朗读的最终字段且 AnswerPlan 仅引用）、自行重排「哪一天最高/最低」、自行判断「哪个平台最高」、把 **`expandedSqlDepartmentIds`** 当作「门店列表」念给用户。

---

## 2. 命名与路由（实施前须与代码对表）

| 维度 | 建议值 | 说明 |
|------|--------|------|
| Java DTO | **`DailyRevenueAnswerPlan`**（备选：**`RevenueAnswerPlan`**） | 与「日营业额」数据基础一致；若仓库已有 **`RevenueAnswerPlan`** 类名占用则择优避让 |
| JSON / RunState 键名 | **`revenueAnswerPlan`** | 与 **`purchaseAnswerPlan`** / **`stockReduceAnswerPlan`** 并列形态一致 |
| **path**（Planner） | **`revenue_overview_path`** | 与 **`purchase_overview_path`** / **`stock_reduce_query_path`** 风格对齐 |
| **intent**（解析层 / Debug 展示） | **`REVENUE_OVERVIEW`** | 细分任务由 **`planType`** 表达，不必为每种问法单独 intent |

若实现时发现 **`REVENUE_OVERVIEW`** / **`revenue_overview_path`** 与历史枚举冲突，**以合并迁移方案为准**，但 **planType** 枚举语义仍以本文 §4 为准。

---

## 3. DailyRevenueAnswerPlan 建议字段

与采购 / 出库 AnswerPlan 一致思路：**稳定业务任务类型** + **服务端排好序的行** + **可序列化 debug**。

| 字段（建议） | 说明 |
|--------------|------|
| **`planType`** | 枚举字符串，见 §4（Fastjson 对外可能与 **`type`** 别名对齐——实施时与 **`PurchaseAnswerPlan`** 一致：**JSON 字段名为 `type`** 对应 Java **`planType`**） |
| **`scopeLabel`** | 本轮回答覆盖范围的人读标签（集团合并 / 单店 / N 家门店等），**非** SQL id 列表 |
| **`timeLabel`** | 与 **`AiResolvedTimeWindow`** / 用户话术一致 |
| **`revenueChannel`** | 可选： **`ALL`** / **`DINE_IN`** / **`TAKEOUT`** / **`PLATFORM`** / **`MIXED_BREAKDOWN`** 等；用于收窄「堂食-only」「外卖-only」与拆分展示 |
| **`summary`** | 极短摘要块（汇总金额、订单数、顾客数、客单价等——**数字须来自 Tool**；Composer **优先 `focusRows`**，`summary` 仅辅助） |
| **`focusRows`** | **核心事实行**（已排序、已选好「答哪一行」——如 Top1 平台、峰值日、峰值门店） |
| **`secondaryRows`** | 补充行（渠道拆分其它行、Top2+、日历序列摘要行等） |
| **`debug`** | `LinkedHashMap<String,Object>`：**`sortKey`**、**`sortDirection`**、候选数、引用 Tool 字段名、`structuredIntentDetail`、聚合层级说明等 |

**行（row）对象**：与 **`RevenueQueryTool`** /  dashboard **`stats`** 已返回字段对齐（日期、门店名、渠道标签、金额、订单数、顾客数、客单价），避免 Composer 再猜别名。

---

## 4. 建议 planType（业务任务类型）

下列为**任务枚举**，与自然语言问法多对一映射；若 **`AiQuerySemanticLexicon`** 需新增 wire，与 **`planType`** 对齐即可。

| planType | 含义（业务） | 典型问法 |
|----------|--------------|----------|
| **`REVENUE_OVERVIEW`** | 营业额 **总览**（默认全渠道合计或产品定义的「总营业额」） | 「这个月营业额多少」「上个月呢」 |
| **`REVENUE_DINE_IN_OVERVIEW`** | **堂食**营业额 | 「堂食营业额多少」 |
| **`REVENUE_TAKEOUT_OVERVIEW`** | **外卖渠道合计**营业额（日营业额表中 **`total_takeout_revenue`** 聚合；**不区分**美团 / 饿了么 / 抖音等具体平台） | 「外卖营业额多少」「外卖收入多少」「外卖平台收入多少」「哪个外卖平台金额最高」（见 §4.2） |
| **`REVENUE_PLATFORM_RANKING`** | **预留**：真实「按外卖平台分列排行」。**当前阶段**日营业额表 **无平台分列**，实现上 **禁止**产出真实排行；相关问法一律降级为 **`REVENUE_TAKEOUT_OVERVIEW`**（§4.2） | （暂无真实数据时可不向用户暴露该 planType） |
| **`REVENUE_ORDER_COUNT_OVERVIEW`** | **订单数** | 「订单数多少」 |
| **`REVENUE_CUSTOMER_COUNT_OVERVIEW`** | **顾客数**（若有口径区分就餐人数 vs 订单人数，以 Tool 字段为准并在 **`debug`** 标明） | 「顾客数多少」 |
| **`REVENUE_AVERAGE_ORDER_VALUE`** | **客单价** | 「客单价多少」 |
| **`REVENUE_DAILY_AMOUNT_RANKING`** | **按日**营业额 **峰值 / 谷底**（最高一天、最低一天）；口径见 **§4.3（采纳方案 B）** | 「哪天营业额最高」「哪天最低」 |
| **`REVENUE_STORE_AMOUNT_RANKING`** | **按门店**营业额 **排行** | 「哪个门店营业额最高」「哪个门店营业额最低」（门店优先于单日谷底语义） |
| **`REVENUE_CHANNEL_BREAKDOWN`** | **渠道拆分**（堂食 + 外卖、「分别多少」类）；**`structuredIntentDetail`**：**`revenue_channel_breakdown`** | 「堂食和外卖分别多少」「各占多少」「堂食外卖各是多少」 |

### 4.1 focusRows / secondaryRows 约定（实现口径）

下列为 **当前 Builder 产出形态**，Composer **只读**；若新增 **`planType`** 须同步更新本表与 **`DailyRevenueAnswerPlanBuilder`**。

| planType | focusRows（要点） | secondaryRows（要点） |
|----------|-------------------|------------------------|
| **`REVENUE_OVERVIEW`** | **`role=overview`**：**`totalRevenue`**、**`days`**、**`avgDailyRevenue`** | 渠道摘录等（见 **`appendChannelSecondaryFromRawStats`**） |
| **`REVENUE_DINE_IN_OVERVIEW`** | **`role=dine_in_total`**，**`revenueAmount`** | 视 Tool 信封 |
| **`REVENUE_TAKEOUT_OVERVIEW`** | **`role=takeout_total`**（外卖渠道合计），**`revenueAmount`** | 平台明细 **无** 时 Composer 说明「未区分具体平台」（§4.2） |
| **`REVENUE_CHANNEL_BREAKDOWN`** | **`role=channel_breakdown_total`**：**`totalRevenue`**、**`days`**（区间总盘子） | **`channel=DINE_IN` / `TAKEOUT`**，**`label`**（堂食/外卖），**`revenueAmount`**；可选 **`PLATFORM_FEE`** |
| **`REVENUE_ORDER_COUNT_OVERVIEW`** / **`REVENUE_CUSTOMER_COUNT_OVERVIEW`** / **`REVENUE_AVERAGE_ORDER_VALUE`** | 各类型主指标行（见 Builder） | 补充行或有 |
| **`REVENUE_DAILY_AMOUNT_RANKING`** | **`daily_rank_pick`**：**`revenueAmount`**，**`semantic`**（最高/最低日聚合） | **`daily_rank_other_bound`** 等 |
| **`REVENUE_STORE_AMOUNT_RANKING`** | **`store_rank_top`**：门店排行首行 | **`store_rank_rest`**：其余门店 |

金额字段 **`revenueAmount`**（及 **`summary`** 内镜像）须 **来自 Tool / `rawStats`**，Composer **禁止**心算改写。

### 4.2 外卖「平台」问法 vs 外卖渠道合计（当前阶段）

日营业额事实表当前 **仅有外卖渠道合计金额**（字段语义上与 Tool **`rawStats.total_takeout_revenue`** 等对表），**没有**美团、饿了么、抖音等 **分列明细**。

因此：

- 「外卖营业额多少」「外卖收入多少」「外卖平台收入多少」「哪个外卖平台金额最高」「哪个平台外卖收入最高」等，在自然语言里出现的「平台」一词 **统一理解为外卖渠道合计**，**不得**当成可做 Top-N 的真实 **`REVENUE_PLATFORM_RANKING`**。
- **`REVENUE_PLATFORM_RANKING`** 保留为 **未来扩展**（待数据源具备平台分列后再启用）；现行路由 / Builder / Composer 应将上述问法映射为 **`REVENUE_TAKEOUT_OVERVIEW`**，并在用户使用了「排行 / 哪个平台最高」类话术时，由 Composer 明确说明：**数据未区分具体外卖平台，仅统计外卖渠道合计**。
- **禁止**编造任何具体平台名称或平台间高低对比。

其它 **`failureReason`**（如历史上 **`missing_platform_ranking`**）仅代表「无法按平台分列」的诊断语义；产品回答口径仍以 **外卖渠道合计** 为准。

### 4.3 日排行「最高 / 最低」口径（采纳方案 B）

**方案 A（备选）**：拆成 **`REVENUE_DAILY_HIGHEST_AMOUNT`**、**`REVENUE_DAILY_LOWEST_AMOUNT`** 两个 **planType**。若后续实现偏好语义更显式，可迁移至方案 A，但须同步 **`AiQuerySemanticLexicon`** / Follow-up 继承与前台 **`planType`** 筛选。

**方案 B（本文采纳）**：仅保留单一 **`REVENUE_DAILY_AMOUNT_RANKING`**，用排序语义区分「最高一天」vs「最低一天」：

| 语义 | **`sortDirection`** | **`sortKey`**（规范名） |
|------|---------------------|-------------------------|
| 哪一天营业额 **最高** | **`DESC`** | **`revenueAmount`** |
| 哪一天营业额 **最低** | **`ASC`** | **`revenueAmount`** |

- **`focusRows`**：第一条必须为 AnswerPlan 认定的「答案日」（峰值或谷底）；**`secondaryRows`** 可携带邻近日或完整序列摘要（由 Builder 定义）。
- **Follow-up 硬约束**：后续追问（如门店收窄 **「AAA 呢？」**）必须 **继承上一轮 `sortDirection`**——上一轮问「最低」则仍为 **`ASC`**（谷底），**禁止**因默认排序或未解析短句而退回 **`DESC`**（峰值）。
- Debug 镜像：**`revenueAnswerPlanSortKey`** / **`revenueAnswerPlanSortDirection`** 必须与 **`plan.debug`** 一致，便于 Replay 比对。

---

## 5. Debug / Replay 字段（`AiHarnessResolvedContextSummarizer` 契约目标）

与 **`purchaseAnswerPlan*`** / **`stockReduceAnswerPlan*`** 并列，建议在 **`resolvedQueryContextSummary`** 中透出：

| 字段 | 说明 |
|------|------|
| **`revenueAnswerPlanPresent`** | `boolean`，是否存在可宣读计划 |
| **`revenueAnswerPlan`** | 完整计划对象（与 DTO / **`answer_delta.data`** 同源） |
| **`revenueAnswerPlanType`** | 等价于 **`planType`**（便于面板筛选） |
| **`revenueAnswerPlanFocusRows`** | 核心行列表（浅拷贝拉出，避免深嵌找不到） |
| **`revenueAnswerPlanSecondaryRows`** | 次要行列表 |
| **`revenueAnswerPlanDebug`** | **`plan.debug`** 的镜像或合并视图 |
| **`revenueAnswerPlanSortKey`** | 排行类：排序字段（如 `amount`、`orderCount`、`businessDate`） |
| **`revenueAnswerPlanSortDirection`** | `ASC` / `DESC` |

**可选镜像**：若团队选择在 **`harnessDebug` 根层**再铺一套扁平字段（与 **`resolvedQueryContextSummary`** 重复），须在 **`docs/API_INTEGRATION.md`** 明示；**默认仅保证 `resolvedQueryContextSummary` 内字段完备**。

### 5.1 Builder 失败诊断（禁止静默失败）

**`DailyRevenueAnswerPlanBuilder`** **不得**在无计划时静默返回；须在 **`resolvedQueryContextSummary`** 中给出可复盘诊断（推荐嵌套对象 **`revenueAnswerPlanAttachDebug`**，键名与 **`purchase*`** / **`stockReduce*`** attach 诊断风格对齐；亦可并入 **`revenueAnswerPlanDebug`** 仅当 **`revenueAnswerPlanPresent === false`** 时铺满下列字段）。

| 字段（建议） | 说明 |
|--------------|------|
| **`attachAttempted`** | `boolean`，是否执行了挂载 / 构建尝试 |
| **`sourceToolKey`** | 实际拿到的 Tool id / 快照键 |
| **`expectedToolKey`** | Builder 期望的 Tool（如 **`revenue_query`**） |
| **`toolResultKeys`** | 信封或 **`Map`** 顶层键列表（摘要即可） |
| **`hasRevenueToolResult`** | 是否识别到营收类 Tool 结果 |
| **`dataClass`** |  inner **`data`** 类型简述（防 envelope 错位） |
| **`foundDataPath`** | 解析到的数据路径（如 **`data.stats`**） |
| **`foundRevenueOverview`** | 是否找到总览块 |
| **`failureReason`** | 枚举短码（见下表） |
| **`failureDetail`** | 可读一行栈摘要或字段名（**不含**敏感 SQL） |

**`failureReason` 常见取值（示例）**：**`missing_tool_result`**、**`missing_or_invalid_tool_envelope`**、**`tool_envelope_unsuccessful`**、**`empty_inner_data`**、**`missing_revenue_overview`**、**`missing_platform_ranking`**、**`missing_daily_ranking`**、**`missing_store_ranking`**、**`build_exception`**。

此时 **`revenueAnswerPlanPresent`** 应为 **`false`**；**`revenueAnswerPlanType`** 可由面板显示为 **「未生成」**（见 §6.4）。

### 5.2 如何判断本轮走了 **`revenueAnswerPlan`**（Debug / Replay）

同时满足下列线索即可认定 **营收 Harness 主链路**（前台 **`planSource=revenueAnswerPlan`**，见 §6.4）：

1. **`effectivePathCode`**（或 **`resolvedQueryContextSummary.effectivePathCode`**）为 **`revenue_overview_path`**（或与实现对表的营收 path）。
2. **`revenueAnswerPlanPresent === true`** 且 **`revenueAnswerPlan.type`**（JSON **`type`**）为非空 **`planType`**；失败挂载时 **`present === false`** 且 **`plan.debug.failureReason`**（或 attach 诊断）有码。
3. SSE **`answer_delta.data`** / **`run_finished`** 中可与 **`resolvedQueryContextSummary.revenueAnswerPlan`** 同源；顶层 **`revenueAnswerPlan`** 见 **`docs/API_INTEGRATION.md`** 专节。
4. **`structuredIntentDetail`**（wire）与 **`plan.debug`** 中 **`structuredIntentDetailWire`** / **`resolvedPlanType`** 可对读（Replay 断言常用）。

---

## 6. 前台读取路径（强制）

以下路径与 **`docs/API_INTEGRATION.md`** **`revenueAnswerPlan`** 专节一致：**Summarizer / Composer** 已写入时，**`resolvedQueryContextSummary.revenueAnswerPlan*`** 与 **`answer_delta.data.revenueAnswerPlan`**（若该帧带出完整计划对象）同源；前端应按 **`revenueAnswerPlanPresent`** 区分「有计划 / 仅诊断」。

### 6.1 `GET /api/ai/runs/{runId}`（Harness Debug）

在 **`ai.harness.debug-context-enabled=true`** 且 **`harnessDebug.resolvedQueryContextPresent === true`** 时，优先读取：

- **`harnessDebug.resolvedQueryContextSummary.revenueAnswerPlan`**
- **`harnessDebug.resolvedQueryContextSummary.revenueAnswerPlanPresent`**
- **`harnessDebug.resolvedQueryContextSummary.revenueAnswerPlanType`**
- **`harnessDebug.resolvedQueryContextSummary.revenueAnswerPlanFocusRows`**
- **`harnessDebug.resolvedQueryContextSummary.revenueAnswerPlanSecondaryRows`**
- **`harnessDebug.resolvedQueryContextSummary.revenueAnswerPlanDebug`**
- **`harnessDebug.resolvedQueryContextSummary.revenueAnswerPlanSortKey`**（若有）
- **`harnessDebug.resolvedQueryContextSummary.revenueAnswerPlanSortDirection`**（若有）
- **`harnessDebug.resolvedQueryContextSummary.revenueAnswerPlanAttachDebug`**（§5.1；**`revenueAnswerPlanPresent === false`** 或诊断需要时）

若后端另有 **`harnessDebug.revenueAnswerPlan*`** 根层镜像，以前端 **`API_INTEGRATION`** 为准。

### 6.2 SSE **`answer_delta`**

- **`data.resolvedQueryContextSummary.revenueAnswerPlan`**
- **`data.resolvedQueryContextSummary.revenueAnswerPlanPresent`**
- **`data.resolvedQueryContextSummary.revenueAnswerPlanType`**
- **`data.resolvedQueryContextSummary.revenueAnswerPlanFocusRows`**
- **`data.resolvedQueryContextSummary.revenueAnswerPlanSecondaryRows`**
- **`data.resolvedQueryContextSummary.revenueAnswerPlanDebug`**
- （可选）**`data.resolvedQueryContextSummary.revenueAnswerPlanSortKey`** / **`revenueAnswerPlanSortDirection`**

与采购链路一致，计划在 **`Composer` 写入帧**可同时出现在 **`data.revenueAnswerPlan`**（及扁平 **`revenueAnswerPlanPresent`** 等）——**一旦实现，必须在 `docs/API_INTEGRATION.md` 列出**；本节要求前端 Debug **至少**支持 **`resolvedQueryContextSummary`** 嵌套路径。

### 6.3 SSE **`run_finished`**

优先 **`resolvedQueryContextSummary`** 下同路径：

- **`resolvedQueryContextSummary.revenueAnswerPlan`**
- **`resolvedQueryContextSummary.revenueAnswerPlanPresent`**
- **`resolvedQueryContextSummary.revenueAnswerPlanType`**
- **`resolvedQueryContextSummary.revenueAnswerPlanFocusRows`**
- **`resolvedQueryContextSummary.revenueAnswerPlanSecondaryRows`**
- **`resolvedQueryContextSummary.revenueAnswerPlanDebug`**

若 **`run_finished.data`** 顶层另有扁平字段，以 **`docs/API_INTEGRATION.md`** 为准。

### 6.4 前台 **`planSource`** 与「未生成」展示

当 **`path === revenue_overview_path`**（或与营收链路等价的有效 path）时，Run Debug 面板 **`planSource`** 必须为：

**`planSource = revenueAnswerPlan`**

（字符串取值与 JSON 对象键 **`revenueAnswerPlan`** 对齐，便于与采购 **`purchaseAnswerPlan`** / 出库 **`stockReduceAnswerPlan`** 面板共用同一套 **`planSource`** 枚举。）

| 状态 | Run Debug 展示约定 |
|------|---------------------|
| **已生成** | 照常展示 **`revenueAnswerPlanPresent === true`**、**`type`**（planType）、**`focusRows`**、**`secondaryRows`**、**`debug`**（及 §5.1 **`revenueAnswerPlanAttachDebug`** 仅在失败或诊断需要时） |
| **未生成** | 仍显示 **`planSource: revenueAnswerPlan`**，且至少：**`present: false`**；**`type`: 未生成**；**`focusRows`: 未生成**；**`secondaryRows`: 未生成**；**`debug`: 未生成**（或以 **`failureReason` / `failureDetail`**（§5.1）替代 **`debug`** 占位，二者勿互相顶替为「有计划」） |

**禁止 fallback**：当 **`path === revenue_overview_path`** 时，**不得**因 **`revenueAnswerPlan`** 缺失而把 Debug 主卡片切换为 **`purchaseAnswerPlan`**、**`stockReduceAnswerPlan`**、**`dishProfitAnswerPlan`** 的内容或 **`planSource`**。其它业务 AnswerPlan 若出现在同一 **`resolvedQueryContextSummary`**（合并视图 / 残留），**仅可作次要只读**，**不得**顶替营收 Debug 主摘要。

### 6.5 **`path` 专属 AnswerPlan 优先级（防串 Run / merged debug 残留）**

前台根据 **`effectivePathCode` / `path`** **硬绑定**主 **`planSource`** 与宣读优先级：

| **`path`** | 优先读取 / Debug **`planSource`** | **缺失计划时** |
|------------|-----------------------------------|----------------|
| **`revenue_overview_path`** | **`revenueAnswerPlan`** | **仅** §6.4「未生成」+ §5.1 诊断；**禁止**回退到其它业务 AnswerPlan |
| **`purchase_overview_path`**（及同源采购视角） | **`purchaseAnswerPlan`** | 按采购链路约定 |
| **`stock_reduce_query_path`** | **`stockReduceAnswerPlan`** | 按出库链路约定 |
| **`dish_profit_path`** | **`dishProfitAnswerPlan`**（或与实现对表键名） | 按毛利链路约定 |

**营收链路**：**`revenueAnswerPlan`** 不存在时，UI **只能**声明营收计划未生成并展示 **`failureReason`**（§5.1），**不可**用 **`purchaseAnswerPlan`** / **`stockReduceAnswerPlan`** / **`dishProfitAnswerPlan`** 的 **`focusRows`** 顶替展示或误以为本轮主 Plan。

---

## 7. 多轮验收问题（负责人前台执行）

### 7.1 验收序列与核对项

下列每一轮建议在 **Run Debug / Replay** 中核对：

**用户问题序列**

1. 这个月营业额多少？  
2. 上个月呢？  
3. 堂食营业额多少？  
4. 外卖营业额多少？  
4'. 堂食和外卖分别多少？（**`planType = REVENUE_CHANNEL_BREAKDOWN`**）  
5. 哪个外卖平台金额最高？（**`planType` 应为 `REVENUE_TAKEOUT_OVERVIEW`**，见 §4.2；**非**真实平台排行）  
6. 订单数多少？  
7. 顾客数多少？  
8. 客单价多少？  
9. 哪天营业额最高？  
10. 哪天营业额最低？  
11. AAA 呢？（或其它门店名收窄）  
12. 汀兰餐厅呢？  
13. 全部门店呢？（从收窄恢复集团 / 全量可见门店）

**每一轮检查项**

- **`intent`** 是否为 **`REVENUE_OVERVIEW`**（或与实现对表的营收 intent）
- **`path`** 是否为 **`revenue_overview_path`**（或与实现对表的营收 path）
- **`timeSource`** 是否正确  
- **`time` / 时间窗** 是否与问句一致  
- **`scopeType`** 是否正确  
- **`visibleStores`** 是否正确  
- **`queryScopeKind`** 是否正确  
- **`queryStoreIds`** 是否正确  
- **`expandedSqlDepartmentIds`** 是否 **仅** 作为 SQL 范围展开，**不** 当作门店列表展示  
- **`revenueAnswerPlanPresent`** 在成功链路是否为 **`true`**（失败挂载时核对 §5.1）  
- **`revenueAnswerPlan.type`**（planType）是否与问法匹配  
- **`focusRows`** 是否承载 **核心结论行**  
- **`secondaryRows`** 是否为 **合理补充**  
- **Follow-up**：核对 §7.2——**`planType` / `sortKey` / `sortDirection` / `rankingMetric`** 是否在收窄门店、恢复集团、切换时间时 **正确继承**，未被默认排序或未解析短句改写（尤其 **`REVENUE_DAILY_AMOUNT_RANKING`** 的 **`ASC`/`DESC`**）。

### 7.2 Revenue 模块 **follow-up** 继承规则（解析 + Builder + Debug）

下列规则适用于 **`path === revenue_overview_path`** 下的多轮短句（「上个月呢」「AAA 呢」「全部门店呢」等）。实现侧 **`AiFollowUpResolver`** / **`AiFollowUpHintSupport`** / **`AiConversationTurnMemory`** 须与会话记忆对齐，避免仅扩写文本却丢失排行语义。

#### 时间继承

- **上轮**：「这个月营业额多少？」→ **`planType = REVENUE_OVERVIEW`**  
- **下轮**：「上个月呢？」→ **须继承 `planType = REVENUE_OVERVIEW`**，仅更新时间窗（**`timeWindow`** / **`timeLabel`**）；**不得**切换到排行类或其它 **planType**。

#### 门店收窄（外卖「平台」问法语境，§4.2）

- **上轮**：「哪个外卖平台金额最高？」→ **`planType = REVENUE_TAKEOUT_OVERVIEW`**，**`focusRows`** 为外卖渠道合计（如 **`takeout_total` / `revenueAmount`**）；**`structuredIntentDetail` 对 harness 可展示为 `REVENUE_TAKEOUT_OVERVIEW`**（兼容历史 wire **`revenue_platform_ranking`** 时仍解析为同一 takeout 计划）。**禁止**输出平台名排行。  
- **下轮**：「AAA 呢？」→ **须继承 `planType = REVENUE_TAKEOUT_OVERVIEW`** 与同一外卖渠道合计语义；仅收窄 **`queryStoreIds` / `visibleStores`**。

#### 门店收窄（按日排行语境）

- **上轮**：「哪天营业额最低？」→ **`planType = REVENUE_DAILY_AMOUNT_RANKING`**（§4.3 方案 B），**`sortKey = revenueAmount`**，**`sortDirection = ASC`**  
- **下轮**：「AAA 呢？」→ **须继承同一 `planType`、`sortKey`、`sortDirection`**；仅收窄 **`queryStoreIds` / `visibleStores`**。**`rankingMetric`**（若有）保持不变。

#### 全部门店恢复（谷底 / 峰值语境）

- **上轮**：「哪天营业额最低？」→ **`planType = REVENUE_DAILY_AMOUNT_RANKING`**（§4.3 方案 B），**`sortKey = revenueAmount`**，**`sortDirection = ASC`**  
- **下轮**：「全部门店呢？」→ **须继承同一 **`planType`**、`sortKey`、`sortDirection`**；仅恢复集团 / 全量可见门店范围。**禁止**变为 **`DESC`**（峰值）。

#### 排行指标继承（总纲）

若上一轮已为：

- **`REVENUE_TAKEOUT_OVERVIEW`**（含原「外卖平台最高」类问法，§4.2）  
- **`REVENUE_STORE_AMOUNT_RANKING`**  
- **`REVENUE_DAILY_AMOUNT_RANKING`**（含 §4.3 **最低=`ASC`** / **最高=`DESC`**）  
- 或方案 A 迁移后的 **`REVENUE_DAILY_HIGHEST_AMOUNT`** / **`REVENUE_DAILY_LOWEST_AMOUNT`**

后续用户 **仅**说 **「AAA 呢」「汀兰餐厅呢」「全部门店呢」「这个月呢」「上个月呢」** 等 **未切换指标** 的短句时，**必须继承**：

- **`planType`**  
- **`sortKey`**  
- **`sortDirection`**  
- **`rankingMetric`**（若 Builder 使用该字段聚合「按金额 / 按订单 / 按顾客」等）

**仅当**用户 **明确**切换语义（如出现「最高」「最低」「订单数」「顾客数」「客单价」「堂食」「外卖」「平台」「营业额总览」等 **新区分词**）时，才允许 **重新路由**到对应 **`planType`**（并可重置 **`sortKey` / `sortDirection`**）。

---

## 8. 禁止项与冻结边界（营收 vs 其它 AnswerPlan）

营收 Harness **核心已交付**；下列约束防止与其它链路耦合漂移。**除非** 单独立项评审：

- **禁止**改动 **`PurchaseAnswerPlan`** / **`StockReduceAnswerPlan`** / **`DishProfitAnswerPlan`** 及其 Builder、挂载条件、专属 Composer 分支与契约文档主线（采购 / 出库 / 毛利链路各自冻结策略不变）。
- **禁止**以营收需求为由改写 **`purchase_overview_path`**、**`stock_reduce_query_path`**、**`dish_profit_path`** 的路由或 Tool 编排。
- **营收专属改动**应局限在 **`revenue_overview_path`** + **`DailyRevenueAnswerPlan*`** + **`RevenueQueryTool`**（仅缺陷 / 口径评审）+ **`StubAnswerComposerNode`** 中与 **`revenueOverviewPath`** / **`revenueAnswerPlan`** 绑定的宣读分支。
- **不要**改 **经营诊断**主链路（**`BusinessDiagnosisPlanBuilder`** / 诊断 Harness）——除非仅为读取 Tool 事实且不影响本条 AnswerPlan 契约。
- **不要**在无评审下改 **SQL** / Mapper **营业额口径**（缺陷修复单独评审）。
- **不要让 Composer** 在营收路径下自行汇总营业额、订单数、客单价或重排行（须宣读 AnswerPlan / Tool 已锁定字段）。
- **不要把 `expandedSqlDepartmentIds` 当门店列表展示**。

---

## 9. 交付状态与仓库单测

### 9.1 已落地（与采购 / 出库 Harness 对齐）

- **`DailyRevenueAnswerPlan`** + **`DailyRevenueAnswerPlanBuilder.attachIfApplicable`**；**`revenue_query`** 成功且 **`AiRunState.isRevenueOverviewPath()`** 时挂载 **`AiRunState.revenueAnswerPlan`**。
- **`AiHarnessResolvedContextSummarizer`** 透出 **`revenueAnswerPlan`**、**`revenueAnswerPlanPresent`**、**`revenueAnswerPlanType`**、**`revenueAnswerPlanFocusRows`**、**`revenueAnswerPlanSecondaryRows`**、**`revenueAnswerPlanDebug`**、排行类 **`revenueAnswerPlanSortKey` / `revenueAnswerPlanSortDirection`**；Debug **`planSource=revenueAnswerPlan`**（与实现对表）。
- **`StubAnswerComposerNode`**：营收 path 且计划可用时 **优先宣读 `focusRows` / `secondaryRows`**（详见 §0.2）。
- **`docs/API_INTEGRATION.md`**：**`answer_delta.data.revenueAnswerPlan`** 专节（本次补齐）。

### 9.2 当前已通过的单测（JUnit）

| 测试类 | 覆盖要点 |
|--------|----------|
| **`DailyRevenueAnswerPlanBuilderTest`** | 非营收 path 跳过挂载；总览信封 → **`REVENUE_OVERVIEW`**；缺 Tool → **`failureReason`**；外卖「平台」超极 → **`REVENUE_TAKEOUT_OVERVIEW`** + **`explainTakeoutChannelAggregateOnly`**；legacy **`STRUCTURED_REVENUE_PLATFORM_RANKING`** wire 同上；门店 vs 单日 **`resolvePlanType`** 区分；**`storeRevenueRanking`** → **`REVENUE_STORE_AMOUNT_RANKING`** 与 focus/secondary 切分 |
| **`StubAnswerComposerNodeTest`** | **`revenueCustomerCount_emptyFocusWithFailure_doesNotFallbackToOverview`**、**`revenueAverageOrderValue_emptyFocusWithFailure_doesNotFallbackToOverview`**（有计划失败语义时不错误回落总览/tool 误导字段） |

整机前台 §7 验收仍由负责人执行；仓库不要求 Agent 代跑 SSE。

---

## 10. 实施落地文件清单（与仓库一致）

| 层级 | 文件 |
|------|------|
| DTO | **`src/main/java/com/nongxinle/ai/dto/business/DailyRevenueAnswerPlan.java`** |
| 计划构建 | **`src/main/java/com/nongxinle/ai/graph/business/DailyRevenueAnswerPlanBuilder.java`** |
| 状态 | **`AiRunState.java`**（**`revenueAnswerPlan`**） |
| 挂载点 | **`BusinessToolExecutionNode.java`** |
| Tool | **`RevenueQueryTool.java`**（及日营收 Service / Mapper） |
| Debug | **`AiHarnessResolvedContextSummarizer.java`** |
| Composer | **`StubAnswerComposerNode.java`** |
| 语义 / 多轮 | **`AiQuerySemanticLexicon`**、**`AiFollowUpResolver`** / **`AiFollowUpHintSupport`** 等 |
| 单测 | **`DailyRevenueAnswerPlanBuilderTest.java`**、**`StubAnswerComposerNodeTest.java`**（营收相关方法见 §9.2） |

---

## 11. 已知限制（产品与技术）

1. **外卖平台维度**：日营业额事实当前 **仅有外卖渠道合计**（如 **`total_takeout_revenue`**），**无**美团 / 饿了么 / 抖音等分列；「哪个平台最高」类问法 **降级**为 **`REVENUE_TAKEOUT_OVERVIEW`**，Composer **须说明**未区分具体平台（见 §4.2）。**`REVENUE_PLATFORM_RANKING`** 仍为 **预留**，待数据源具备平台分列后再启用。
2. **单日营业额最高 / 最低**：**`REVENUE_DAILY_AMOUNT_RANKING`** 以金额与 **`sortDirection`** 为主；若 Tool **未**在 **`focusRows` / `debug`** 中给出可宣读的业务日期字段，**不得**编造具体「哪一天」——仅可答金额或说明数据未带来日期维度。
3. **门店排行性能**：当前 Java 侧多为 **按门店根逐个 rollup** 聚合并生成排行；门店数量很大时，**可优化为数据库 `GROUP BY` 聚合**（与 `RevenueQueryTool` / Mapper 演进单独立项）。
4. **多轮继承**：时间窗、门店收窄 / 恢复、日排行 **`ASC`/`DESC`** 等以解析层 + 会话记忆为准；若出现未继承个案，按 **Follow-up / Resolver** bug 修，而非在 Composer 内硬猜（见 §7.2）。

---

## 12. 交叉引用

- **TODO 节拍**：`docs/TODO_MULTI_AGENT.md` → **`revenue_overview_path`（DailyRevenueAnswerPlan）** 小节  
- **权限**：**`VIEW_REVENUE`**（见 **`TODO_MULTI_AGENT.md`** Tool → permission 表）
- **SSE / REST 契约**：`docs/API_INTEGRATION.md` → **`answer_delta.data.revenueAnswerPlan`**
