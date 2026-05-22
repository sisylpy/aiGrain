# Business Diagnosis Composite — 生产入口 Gate

> **读者**：架构 / Graph / Master 对接工程师。  
> **现网**：**`com.nongxinle.ai.planner.BusinessDiagnosisCompositeProductionGate`** 在 **`AiRunService#startRun`** 评估并写入 **`businessDiagnosisCompositeGateResult`**（**`allowed` / `reasonCode`**）；**只读**结构化 **`AiResolvedQueryContext`** / **`AiRunState`**，**禁止**用户原文 **contains/regex**。  
> **意图映射表（§3.3）**：与 **`AiResolvedQueryIntent`**、**`pathCode`**、**`AiQuerySemanticLexicon` wire** 对齐 — 路由 SSOT。  
> **后继执行**：Gate **`allowed=true`** 后由 **`BusinessDiagnosisCompositeExecutionService`** 按 **`HARNESS_ONLY` / `SHADOW`** 旁路 Composite（见 **[`business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md)**）。  
> **灰度运营**：**[`business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md)**、**[`business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md)**（**`ShadowPolicy`** 已实装；**C-66** 集中 dashboard 待做）。

## 1. Harness 已具备能力（本轮基线）

| 能力 | 说明 |
|------|------|
| **STORE Composite** | caseId **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`**：四域真实 Tool（**`revenue_query`** / **`purchase_overview`** / **`stock_reduce_query`** / **`dish_profit_analysis`**）+ **`BusinessDiagnosisCompositeAnswerPlan`** + **`BusinessDiagnosisCompositeReadonlyComposer`**（**`businessDiagnosisFinalAnswerText`**、**`businessDiagnosisComposerVersion=C-51_READONLY_COMPOSER`**）。 |
| **GROUP Composite** | caseId **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`**：同上四域真实 Tool + GROUP 上下文 + AnswerPlan + Readonly Composer；**`summaryText` / `finalAnswerText`** 须保持 **GROUP 口径**（见 **[`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md)**）。 |
| **降级诚实** | **C-42**（**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE`**）：出库降级时 **`dataCoverage`** **诚实**、**`riskLevel=INSUFFICIENT_DATA`**、**不写无来源 0**、正文含**数据未完整读取**语义。 |
| **建议步** | **`step_recommendation`** 仍为 **mock**；生产 Gate **不得**将 mock 建议当作真实 Action。 |

---

## 2. Gate 在流水线中的位置（概念）

**推荐顺序**（与 **[`planner-executor-v1-design.md`](./planner-executor-v1-design.md)** §1 一致，在 **Master 调度 Composite 之前**插入判断）：

```text
用户请求
  → AiResolvedQueryContextResolver（唯一解析入口，冻结）
  → BusinessDataPlanner / 意图结构化（现有能力；产出 queryIntent、path、scope、time）
  → 【C-52：CompositeProductionGate — 仅设计】布尔判定 + 原因码
  → 若通过：PlannerExecutor（Composite 六步）→ AnswerPlan → Readonly Composer
  → 若未通过：现有单域 Agent / Tool / 经营概览路径（不强行 Composite）
  → （远期）MasterBusinessAgent：仅当产品要求时，在 Gate 之后、Composite 调用前包一层编排
```

**硬约束**：

- Gate **位于** **Resolver / Planner 已产出结构化上下文之后**，**四域 Tool 真实执行之前**。  
- **本轮不实现** Gate 类；**不修改** `MasterBusinessAgent` 源码；设计假定未来 **Master 若调 Composite，必须先过 Gate**。

---

## 3. 允许 / 禁止进入 Composite 的问题类型

**重要**：生产 Gate **禁止**依赖**用户原文** **`contains` / `regex`** 做判定；**只允许**读取 **`AiResolvedQueryContext`**（及等价物）上已存在的 **结构化字段**（如 **`queryIntent`**、**pathCode / graph path**、**finalAnswerPlanType 候选**、**模板 id**）。下表 **「用户问法」** 仅为 **产品语义说明**，实现时须 **映射到 Resolver / Planner 已定义的枚举或路由键**。

### 3.1 允许 / 禁止（产品语义 → 结构化映射）

**权威表**：**§3.3 C-52.1**（**仅**使用现网 **`AiResolvedQueryIntent` / `AiQuerySemanticLexicon` / `AiResolvedQueryContext`** 字段与 wire；**无**占位 intent 名）。

下表为 **产品说法** 与 **§3.3 行** 的对应关系（实现 **不得** 从用户原文反推）。

| 产品语义（示例） | §3.3 对应 |
|------------------|-----------|
| 经营诊断 / 「哪里有问题」 / 综合因果诊断 | **`PATH_BUSINESS_DIAGNOSIS`** + **允许** 的 `structuredIntentDetail` 见 §3.3 |
| 经营概览且 **四域专线**（营收 + 采购 + 出库 + 菜品毛利，对齐 Harness **`BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS`**） | **`PATH_BUSINESS_OVERVIEW`** + §3.3 **四域 orchestration** 条件 |
| 「全部门店经营情况」、多店综合对比 | **`PATH_*`** 允许行 + **§4 GROUP** + **`business_store_status_compare`** 等 **wire**（见 §3.3） |
| 单纯营业额 / 采购 / 出库 / 菜品 / 排行 / 点菜深挖 | §3.3 **禁止** 行 → **fallback** 列 |

### 3.2 禁止（摘要）

| 类型 | 说明 |
|------|------|
| **单纯单域** | 仅营业额 / 仅采购 / 仅出库 / **仅**某菜毛利 —— 走 **ROUTED_AGENT** 或单域 Planner |
| **排行类单域** | Top-N 营收/采购/菜品等 **专项** —— 非 Composite |
| **指定菜品深挖** | 单品 drill —— 非本 Composite v1 |
| **Action** | 通知、调价、退款、下单、删除等 —— **硬拒绝** Composite |
| **上下文不明** | **权限不清楚**、**时间 / 组织范围无法解析** —— **澄清或 fallback**，**不**进 Composite |
| **半缺上下文硬跑** | 见 §7 |

### 3.3 C-52.1 Gate 结构化意图映射表（权威 — 现网常量）

> **范围**：本表 **只**引用仓库内 **已存在** 的字符串常量/wire；**不**新增 `AiResolvedQueryIntent` 枚举值。  
> **与 Composite AnswerPlan 类型无关**：**`BusinessDiagnosisCompositeAnswerPlan.TYPE_BUSINESS_DIAGNOSIS_COMPOSITE`**（`"BUSINESS_DIAGNOSIS_COMPOSITE"`）仅表示 **PlannerExecutor 产物 / DTO `type`**，**不是** `AiResolvedQueryIntent.intentCode` — Gate **不得**要求用户意图层出现该字符串。

#### 3.3.1 代码常量索引（对照用）

| 类别 | 类 / 资源 | 现网常量（摘录） |
|------|-----------|------------------|
| **intentCode** | **`com.nongxinle.ai.context.AiResolvedQueryIntent`** | `BUSINESS_OVERVIEW`=`"BUSINESS_OVERVIEW"`、`BUSINESS_DIAGNOSIS`=`"BUSINESS_DIAGNOSIS"`、`REVENUE_OVERVIEW`=`"REVENUE_OVERVIEW"`、`PURCHASE_OVERVIEW`=`"PURCHASE_OVERVIEW"`、`STOCK_REDUCE_QUERY`=`"STOCK_REDUCE_QUERY"`、`DISH_PROFIT`=`"DISH_PROFIT"`、`COST_DIAGNOSIS`=`"COST_DIAGNOSIS"`、`WAREHOUSE_STOCK_OVERVIEW`=`"WAREHOUSE_STOCK_OVERVIEW"` |
| **pathCode** | 同上 | `PATH_BUSINESS_OVERVIEW`=`"business_overview_path"`、`PATH_BUSINESS_DIAGNOSIS`=`"business_diagnosis_path"`、`PATH_REVENUE_OVERVIEW`=`"revenue_overview_path"`、`PATH_PURCHASE_OVERVIEW`=`"purchase_overview_path"`、`PATH_STOCK_REDUCE_QUERY`=`"stock_reduce_query_path"`、`PATH_DISH_PROFIT`=`"dish_profit_path"`、`PATH_COST_DIAGNOSIS`=`"cost_diagnosis_path"`、`PATH_WAREHOUSE_STOCK`=`"warehouse_stock_overview_path"` |
| **structuredIntentDetail（wire）** | **`com.nongxinle.ai.conversation.AiQuerySemanticLexicon`** | 经营诊断汇总：`STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY`=`"business_diagnosis_summary"`；成本压力/多店对比诊断：`STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS`=`"business_cost_pressure_diagnosis"`、`STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS`=`"business_store_status_compare_diagnosis"`；四域概览表面：`STRUCTURED_BUSINESS_OVERVIEW_SUMMARY`=`"business_overview_summary"`、`STRUCTURED_BUSINESS_OVERVIEW_STATUS`=`"business_overview_status"`、`STRUCTURED_BUSINESS_STORE_STATUS_COMPARE`=`"business_store_status_compare"`；**排行/深挖** 等见 **§3.3.4** |
| **有效路由** | **`AiResolvedQueryContext`** | 判定以 **`effectiveIntentCode` / `effectivePathCode`** 为主（与 **`queryIntent.intentCode` / `queryIntent.pathCode`** 在合并追问后应对齐）；来源见 **`effectiveIntentSource`**（观测） |
| **四域 Tool 顺序（设计对齐）** | **`AiBusinessToolIds.BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS`** | `revenue_query` → `purchase_overview` → `stock_reduce_query` → `dish_profit_analysis`（与 Composite Harness **C-35 / C-48** 一致） |
| **经营诊断 DataPlanner 链（现有主链路）** | **`BusinessDataPlannerNode#applyBusinessDiagnosisBranch`** | 默认顺序：**`purchase_overview`** → **`stock_reduce_query`** → **`dish_profit_analysis`** →（有 **`VIEW_REVENUE`** 时）**`revenue_query`** — **与 Composite 步序不同**，但 **域集合**可用于 **「是否四域综合意图」** 的语义对照 |
| **多 Agent / 四域概览开关** | **`AiResolvedQueryContext`** | `orchestrationTaskMode`（如 **`"MULTI_AGENT"`**）、`orchestrationMultiAgentRequired`（`Boolean`）；**`BusinessDataPlannerNode#resolvedContextOrchestrationMultiAgentOverview`**：`MULTI_AGENT` **忽略大小写** 或 **`Boolean.TRUE.equals(orchestrationMultiAgentRequired)`** |
| **四域概览 structured 判定** | **`AiQuerySemanticLexicon#isStructuredBusinessOverviewFourDomainOrchestrationSurface`** | 当且仅当 canonical **`structuredIntentDetail`** ∈ {`business_overview_summary`, `business_overview_status`, `business_store_status_compare`} |
| **澄清 / 禁止入户** | **`AiResolvedQueryContext`** | `needSemanticClarification`、`orchestrationClarificationRequired`；**`AiRunState#isNeedClarification`**（DataPlanner 早退）；**任一成立 → 不得**进入 Composite |

#### 3.3.2 Gate **禁止**依赖的用户原文规则（重申）

以下写法 **违反** C-52 / C-52.1，**禁止**出现在 Gate 实现中：

| 禁止模式 | 示例 |
|----------|------|
| **contains** | `message.contains("经营")` |
| **regex** | 匹配「哪里有问题」等中文关键词 |
| **关键词硬路由** | 任意 **未**经 **`AiQuerySemanticParseResult` / Resolver** 物化到 **`structuredIntentDetail` / path / intent** 的字符串判断 |

**允许**：仅读取 **`AiResolvedQueryContext`**、**`AiRunState`** 上已由 **Resolver / Semantic / `BusinessDataPlannerNode`** 写入的字段。

#### 3.3.3 主映射表（intent / path / structured → Composite）

**列说明**：**intentCode** / **pathCode** 指 **`queryIntent`** 上典型值；**effectiveIntentCode** / **effectivePathCode** 须与之一致方可 routing（追问合并后）。**canonical `structuredIntentDetail`** = **`AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(...)`** 的输出。

| intentCode | pathCode | structuredIntentDetail（wire，canonical） | effectiveIntentCode | effectivePathCode | 是否允许进入 Composite | 原因 | fallback 目标 |
|------------|----------|----------------------------------------|---------------------|-------------------|------------------------|------|----------------|
| `BUSINESS_DIAGNOSIS` | `business_diagnosis_path` | `business_diagnosis_summary` | `BUSINESS_DIAGNOSIS` | `business_diagnosis_path` | **允许** | 现网 **经营诊断** 主口径；**`BusinessDataPlannerNode#syncResolvedQueryContextToBusinessDiagnosis`** 在 **structured 为空**时默认写入该 wire | 现有 **`MasterBusinessAgent` / `BusinessDataPlannerNode` 经营诊断**；**C-53** 并联 **PlannerExecutor Composite** 时与之 **二选一或后置** 由产品定 |
| `BUSINESS_DIAGNOSIS` | `business_diagnosis_path` | `business_cost_pressure_diagnosis` | 同左 | 同左 | **允许** | **`AiQuerySemanticLexicon`** 注释：走 **BUSINESS_DIAGNOSIS** 的证据型诊断 | 同上 |
| `BUSINESS_DIAGNOSIS` | `business_diagnosis_path` | `business_store_status_compare_diagnosis` | 同左 | 同左 | **允许** | 多店对比诊断 wire | 同上；**GROUP** 须叠加 **§4.2** |
| `BUSINESS_OVERVIEW` | `business_overview_path` | `business_overview_summary` | `BUSINESS_OVERVIEW` | `business_overview_path` | **允许当且仅当** §3.3.3a **四域 orchestration** | 四域专线 **MULTI_AGENT** 或与 **`isStructuredBusinessOverviewFourDomainOrchestrationSurface`** 一致 | 不满足 orchestration → **空 plan**（classic 六工具已删） |
| `BUSINESS_OVERVIEW` | `business_overview_path` | `business_overview_status` | 同左 | 同左 | 同上 | 同上 | 同上 |
| `BUSINESS_OVERVIEW` | `business_overview_path` | `business_store_status_compare` | 同左 | 同左 | 同上 | **多店经营综合对比** wire；仍须 §3.3.3a | 同上 |
| `BUSINESS_OVERVIEW` | `business_overview_path` | （**非** §3.3.3a，且 **非** 允许 wire） | 同左 | 同左 | **禁止** | 非 MULTI 经营概览 → **空 plan**（classic 已删，见 legacy doc） | **空 plan** + 降级提示 |
| `REVENUE_OVERVIEW` | `revenue_overview_path` | *（任意/空）* | `REVENUE_OVERVIEW` | `revenue_overview_path` | **禁止** | 单域 **日线/营收**；**`BusinessAgentNames.REVENUE_OVERVIEW`** 对齐 **`revenue_query`** | **`RevenueAgent` / 日营收** 专线 |
| `PURCHASE_OVERVIEW` | `purchase_overview_path` | * | `PURCHASE_OVERVIEW` | `purchase_overview_path` | **禁止** | 单域 **采购概览**；Tool **`purchase_overview`** | **`PurchaseAgent` / 采购概览** |
| `STOCK_REDUCE_QUERY` | `stock_reduce_query_path` | * | `STOCK_REDUCE_QUERY` | `stock_reduce_query_path` | **禁止** | 独立 **出库/核销**；Tool **`stock_reduce_query`** | **`StockReduceAgent` / 出库** |
| `DISH_PROFIT` | `dish_profit_path` | * | `DISH_PROFIT` | `dish_profit_path` | **禁止** | 单域 **菜品毛利**；Tool **`dish_profit_analysis`** | **`DishProfitAgent`** |
| `COST_DIAGNOSIS` | `cost_diagnosis_path` | * | `COST_DIAGNOSIS` | `cost_diagnosis_path` | **禁止** | **成本诊断** 与 **Composite 经营诊断六步** 不同编排 | **`applyCostIntentBranch`** / 成本洞察链 |
| `WAREHOUSE_STOCK_OVERVIEW` | `warehouse_stock_overview_path` | * | `WAREHOUSE_STOCK_OVERVIEW` | `warehouse_stock_overview_path` | **禁止** | **库房库存概览** 专线 | **`WarehouseStockOverview` / `warehouse_stock_overview`** |
| *（任意）* | *（任意）* | *（任意）* | * | * | **禁止** | **`needSemanticClarification`** 或 **`orchestrationClarificationRequired`** 或 **`AiRunState#isNeedClarification`** | **澄清问答**；不跑 Composite |
| *（允许 intent/path）* | * | * | * | * | **禁止** | **`resolvedQueryContext.mentionedDishName`** **非空**（**指定菜品深挖**） | 现有 **收窄菜品** 专线（**`ARG_DISH_NAME_FOCUS_HINT`** 等） |

##### 3.3.3a 「四域 orchestration」判定（**BUSINESS_OVERVIEW** 专用）

**满足其一**即可与上表「**允许**」行对齐：

1. **`AiResolvedQueryContext.orchestrationTaskMode`** **非空**且 **`trim()` 后 `equalsIgnoreCase("MULTI_AGENT")`**（与 **`BusinessDataPlannerNode#resolvedContextOrchestrationMultiAgentOverview`** 一致）；**或**  
2. **`Boolean.TRUE.equals(AiResolvedQueryContext.orchestrationMultiAgentRequired)`**；**或**  
3. **`AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(structuredIntentDetail)`** 为 **true**。

#### 3.3.4 **`structuredIntentDetail`：排行 / 深挖类 wire（禁止 Composite）**

以下 wire 属 **单域排行、查漏或细分**，**不得**因 **path 误判** 进入 **Composite**（若 **effectivePath** 落在 **单域 path**，以下表 **fallback**；若在 **BUSINESS_OVERVIEW** 上 **仅**含排行 wire 而无 §3.3.3a，**禁止 Composite**）：

| 域 | **`AiQuerySemanticLexicon` 常量** = wire |
|----|-------------------------------------------|
| **采购排行** | `STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING`=`"purchase_goods_amount_ranking"`、`STRUCTURED_PURCHASE_GOODS_COUNT_RANKING`、`STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING`、`STRUCTURED_SUPPLIER_AMOUNT_RANKING` |
| **营收排行/拆分** | `STRUCTURED_REVENUE_PLATFORM_RANKING`、`STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING`、`STRUCTURED_REVENUE_STORE_AMOUNT_RANKING`、`STRUCTURED_REVENUE_CHANNEL_BREAKDOWN` 等（**非** §3.3.3 允许的三类 overview wire） |
| **出库排行** | `STRUCTURED_GOODS_OUTBOUND_RANKING`、`STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING`、`STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING` |
| **菜品排行 / 成本排行** | **`AiQuerySemanticLexicon#isDishProfitRankingStructuredDetail`** 为 true 的 wire（如 `dish_profit_ranking_low_margin`、`dish_sales_ranking` 等） |
| **门店排行** | `STRUCTURED_STORE_PRIORITY_RANKING`=`"store_priority_ranking"`（及别名归入 **`STORE_RISK_RANKING`**→`STORE_PRIORITY_RANKING` 的 canonical） |
| **单品明细/原因（非排行）** | **`isSingleDishMetricOrReasonStructuredDetail`** 为 true → **禁止 Composite**（**指定菜品深挖**） |

**判定**：对 **unknown** wire：**禁止** 默认放行 Composite；**fallback** 现有 **DataPlanner** 已选链路或 **澄清**。

#### 3.3.5 现网 **无** `COMPOSITE` 专用 `intentCode` 时的 **C-53 最小接入**（不编造枚举）

| 情况 | 处理 |
|------|------|
| **C-53 实现 Gate** | **白名单**仅限 **§3.3.3 表中「允许」行** 的 **`effectiveIntentCode` + `effectivePathCode` + structured/orchestration** 组合；**feature flag 默认关闭** |
| **若产品要求 Composite 而 structured 覆盖不足** | **先** 提 **独立设计**：在 **语义解析 / Resolver** 侧增加 **新 `intentCode`/`pathCode`/`structuredIntentDetail` wire`**（**C-52.1 不编造**）；**再** 接主链路 |
| **禁止** | 在无新结构化意图的情况下 **强行** 把 **生产全量** 导入 **PlannerExecutor Composite** |

---

## 4. STORE / GROUP 进入条件

### 4.1 STORE

| 条件 | 说明 |
|------|------|
| **`orgScope.scopeType`** | **`STORE`**（或项目等价枚举） |
| **门店锚点** | **`currentStoreDepartmentId`** 或 **`requestDepartmentId`** **可解析且非歧义** |
| **`visibleStores`** | **至少**能 **唯一定位当前店**（或单元素列表与锚点一致） |
| **`timeWindow`** | **`startDate` / `endDate`**（或等价）**完整、合法** |
| **`dataScope`** | 与单域 Hydrated 契约一致，可构造 **单店** IN / 过滤 |

### 4.2 GROUP

| 条件 | 说明 |
|------|------|
| **`scopeType`** | **`GROUP`** |
| **`visibleStores`** | **至少 2 个**有效门店根（`storeDepartmentId` 等 **稳定主键**） |
| **`dataScope`** | 能表达 **多店 IN / GROUP 聚合**（与 **[`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md)** §3 一致） |
| **输出口径** | AnswerPlan / Composer **`scopeLabel` / `summaryText` / `finalAnswerText`** **必须**保持 **GROUP 口径**；**禁止** GROUP Gate 失败后 **静默 fallback 单店** 仍宣称集团 |

---

## 5. 必须满足的上下文前置条件（Gate 输入清单）

Gate **只读**下列 **结构化输入**（概念字段名以 **`AiResolvedQueryContext`** / **`AiRunState`** 现网为准）：

| 字段区 | 要求 |
|--------|------|
| **`userId`** | 已鉴权 |
| **`departmentId` / `orgScope`** | 与 **STORE/GROUP** 规则一致 |
| **`distributerId`** | 若采购/菜品等 Tool **要求**，须非歧义 |
| **`timeWindow`** | **`startDate` / `endDate`** 完整 |
| **`scopeType`** | **STORE** 或 **GROUP** |
| **`visibleStores`** | 满足 §4 |
| **`queryIntent` / `effectiveIntentCode` / `effectivePathCode`** | **已**命中 **§3.3**「允许」行且 **未**触发 **§3.3** 全局禁止条件 |
| **权限** | 若存在权限上下文，**预设**须能通过各域 Tool 的权限检查；**不确定** → **不**进 Composite |
| **`toolResults` 初始化** | **`AiRunState`** 已进入本轮 Run、**`toolResults` Map 可写**（空 Map 即可），**不**要求预填 |

**禁止**：Gate **不**读取 **`toolResults`** 内 **payload** 做准入（准入只看 **解析后的上下文**）。

---

## 6. 不满足 Gate 时的 fallback

| 策略 | 行为 |
|------|------|
| **单域路由** | 回退 **现有** `ROUTED_AGENT` / 单域 **PlannerExecutor** / 单 Tool 链 |
| **澄清** | 缺时间、缺门店、缺权限 → **返回结构化澄清**（**不**跑 Composite） |
| **经营概览** | 若产品定义 **「轻量概览」** 与 **「四域 Composite」** 分离 → 走 **原有概览路径** |

**禁止**：

- **半缺上下文硬跑** Composite。  
- **GROUP 失败 fallback STORE** 仍对外称 **「全部门店」**。  
- **Tool 失败伪装 SUCCESS**（与现有 **DEGRADED** 诚实一致）。  

---

## 7. 执行中失败 / 降级（与 AnswerPlan 对齐）

| 情况 | 行为 |
|------|------|
| **任一真实 Tool 步失败 / DEGRADED** | 保留 **`PlannerExecutorTrace`** 降级语义；**`dataCoverage`** 对应域 **`success=false`**；**`riskLevel`** 按 **[`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md)** §8.8（含 **`INSUFFICIENT_DATA`**）。 |
| **Readonly Composer** | **`finalAnswerText`** **必须**保留 **「数据未完整读取」** 类语义（C-51 已与 Builder 对齐）。 |
| **话术** | **禁止** **「经营正常 / 没问题」** 与 **无依据** 确定性结论。 |

---

## 8. Composer 输出接入现有回答链路（设计）

| 产出 | 消费方（概念） |
|------|----------------|
| **`businessDiagnosisFinalAnswerText`**（或 **`ComposeResult.finalAnswerText`**） | **最终回答正文**（SSE / 会话消息体） |
| **`suggestedNextQuestions`** | **可选** UI 追问 chip；**不**标为「AI 智能推荐」 |
| **`riskLevel` / `dataCoverage`** | **调试面板**、运营后台、内部 Run 审计 |
| **`plannerExecutorTrace`** | **Run 级调试**（steps、`usedTools`、`degradedSteps`） |
| **`debug.mappingNotes`**（AnswerPlan 内） | **仅**开发/审计；**不**对用户展示 |

**不接 LLM 润色** 前：正文 **以 Readonly Composer 输出为准**；未来若接 LLM，须遵守 **[`business-diagnosis-composer-readonly-design.md`](./business-diagnosis-composer-readonly-design.md)** §8，**不得改事实**。

---

## 9. SSE / 前台调试字段建议（可选 payload）

**建议**在 **调试 / 内测** SSE 或管理端附加（**非**强制对用户默认展示全部）：

| 字段 | 说明 |
|------|------|
| **`finalAnswerPlanType`** | e.g. **`BUSINESS_DIAGNOSIS_COMPOSITE`** |
| **`composerVersion`** | **`C-51_READONLY_COMPOSER`** |
| **`plannerCompositeHonesty`** | Harness 诚实字段 |
| **`riskLevel`** | 枚举名 |
| **`dataCoverage`** | 四域列表 |
| **`usedTools`** | trace 汇总 |
| **`degradedSteps`** | stepId 列表 |
| **`scopeLabel` / `timeLabel`** | 与 AnswerPlan 一致 |
| **`scopeType` / `visibleStores`** | 组织上下文 |
| **`businessDiagnosisFinalAnswerText`** | Composer 终稿 |

**不展示给用户**：**`debug.mappingNotes`**、原始 **`toolResults`** 大 JSON。

---

## C-53 Java skeleton（已落地，未接 Master）

| 类 | 说明 |
|----|------|
| **`com.nongxinle.ai.planner.BusinessDiagnosisCompositeProductionGate`** | **静态** **`evaluate(AiResolvedQueryContext resolvedQueryContext, AiRunState runState, boolean compositeProductionEnabled)`**；**不**读用户原文、**不**执行 Tool / **不**创建 **`PlannerExecutionPlan`**、**不**调 Composer / LLM、**不**修改 **`AiRunState`** |
| **`com.nongxinle.ai.planner.BusinessDiagnosisCompositeGateResult`** | **`allowed`**、**`reasonCode`**、**`reason`**、**`scopeType`**、**`finalAnswerPlanType`**（仅 **`allowed`** 时为 **`BusinessDiagnosisCompositeAnswerPlan#TYPE_BUSINESS_DIAGNOSIS_COMPOSITE`**）、**`recommendedCaseKind`**（**`STORE` / `GROUP` / `NONE`**）、**`debug`** |
| **`com.nongxinle.ai.planner.BusinessDiagnosisCompositeGateReasonCode`** | 见 **§C-53.1** |

### C-53.1 `BusinessDiagnosisCompositeGateReasonCode`（与 Java 枚举一致）

| 枚举常量 | 含义 |
|----------|------|
| **`FEATURE_FLAG_DISABLED`** | **`compositeProductionEnabled==false`**（**默认应由调用方传入**） |
| **`ALLOWED_STORE`** | 通过；**`AiResolvedOrgScope.SCOPE_STORE`** |
| **`ALLOWED_GROUP`** | 通过；**`SCOPE_GROUP`** |
| **`CLARIFICATION_REQUIRED`** | **`needSemanticClarification` / `orchestrationClarificationRequired` / `runState.needClarification`** |
| **`MISSING_RESOLVED_CONTEXT`** | **`resolvedQueryContext==null`** |
| **`MISSING_TIME_WINDOW`** | **`timeWindow` 缺 `startDate`/`endDate`** |
| **`UNSUPPORTED_SCOPE`** | **`orgScope`/`scopeType` 缺失或非 STORE/GROUP** |
| **`STORE_SCOPE_MISSING_ANCHOR`** | STORE：**无** `currentStoreDepartmentId`/`requestDepartmentId` 有效锚点，或 **`visibleStores`** 无法匹配 |
| **`GROUP_SCOPE_INSUFFICIENT_VISIBLE_STORES`** | GROUP：有效 **`visibleStores` 门店根 \< 2** |
| **`INTENT_PATH_NOT_WHITELISTED`** | 非 §3.3.3 **允许 A/B** |
| **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`** | 单域 **intent/path**（营收/采购/出库/菜品/成本/库房） |
| **`NAMED_DISH_DEEP_DIVE_NOT_COMPOSITE`** | **`mentionedDishName` 非空** |
| **`RANKING_OR_DEEP_DIVE_NOT_COMPOSITE`** | **`structuredIntentDetail`** 命中 **§3.3.4** / **`AiQuerySemanticLexicon`** 辅助方法 |

**feature flag**：**`evaluate(..., false)`** 时 **恒** **`FEATURE_FLAG_DISABLED`**；**C-55** 主链路使用 **`ai.composite.businessDiagnosis.productionEnabled`**（**默认 false**）；命名微调与完全配置化见 **C-56**。

---

## C-54 Harness-only replay（Gate 决策验证）

| 项 | 说明 |
|----|------|
| **入口** | **`AiHarnessReplayService`**：**`caseId`** 为下表之一时 **优先**短路至 **`AiHarnessReplayCompositeGate.replay`**（**早于** **`PlannerExecutor`** mock） |
| **`AiHarnessReplayMode`** | **`BUSINESS_DIAGNOSIS_COMPOSITE_GATE`**（可选用 **`replayMode`** 显式指定） |
| **禁止** | **不**跑 Resolver 生产图、**不**调用 **`PlannerExecutor`**、**不**执行四域 Tool、**不**调用 Composer / LLM、**不**接 Master |
| **唯一业务调用** | **`BusinessDiagnosisCompositeProductionGate.evaluate(resolvedQueryContext, runState, compositeProductionEnabled)`** |
| **消息体** | **`messages`** 仅 Harness 外壳（首条可为占位）；**不参与** Gate 构造 |
| **根摘要** | 响应 **`harnessRootSummary`** 与首轮 **`rounds[0].resolvedQueryContextSummary`** 均含：`harnessReplayMode`、`gateCaseId`、`gateAllowed`、`gateReasonCode`、`gateReason`、`gateScopeType`、`gateRecommendedCaseKind`、`gateFinalAnswerPlanType`、`gateDebug` |
| **`overallPass`** | 各 case 内建 **期望** `reasonCode` / `allowed`（及放行时的 **`finalAnswerPlanType`** / **`recommendedCaseKind`**）自检 |

### C-54.1 `caseId` 与预期 `gateReasonCode`

| `caseId` | `compositeProductionEnabled` | 预期 **`gateAllowed`** | 预期 **`gateReasonCode`** |
|----------|------------------------------|------------------------|---------------------------|
| **`BUSINESS_DIAGNOSIS_COMPOSITE_GATE_STORE_ALLOWED`** | **true** | **true** | **`ALLOWED_STORE`** |
| **`BUSINESS_DIAGNOSIS_COMPOSITE_GATE_GROUP_ALLOWED`** | **true** | **true** | **`ALLOWED_GROUP`** |
| **`BUSINESS_DIAGNOSIS_COMPOSITE_GATE_FEATURE_DISABLED`** | **false** | **false** | **`FEATURE_FLAG_DISABLED`** |
| **`BUSINESS_DIAGNOSIS_COMPOSITE_GATE_DOMAIN_REVENUE_BLOCKED`** | **true** | **false** | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`** |
| **`BUSINESS_DIAGNOSIS_COMPOSITE_GATE_NAMED_DISH_BLOCKED`** | **true** | **false** | **`NAMED_DISH_DEEP_DIVE_NOT_COMPOSITE`** |
| **`BUSINESS_DIAGNOSIS_COMPOSITE_GATE_RANKING_BLOCKED`** | **true** | **false** | **`RANKING_OR_DEEP_DIVE_NOT_COMPOSITE`** |
| **`BUSINESS_DIAGNOSIS_COMPOSITE_GATE_MISSING_TIME`** | **true** | **false** | **`MISSING_TIME_WINDOW`** |
| **`BUSINESS_DIAGNOSIS_COMPOSITE_GATE_GROUP_INSUFFICIENT_STORES`** | **true** | **false** | **`GROUP_SCOPE_INSUFFICIENT_VISIBLE_STORES`** |

---

## C-55 生产主链路关闭态骨架（仅观测）

| 项 | 说明 |
|----|------|
| **目标** | 真机主链路在 **`AiResolvedQueryContext`** 已生成后、**Graph / `BusinessDataPlanner` / Tool 执行前** 调用 **`BusinessDiagnosisCompositeProductionGate.evaluate(resolvedQueryContext, runState, compositeProductionEnabled)`**，**只**把结果写入 **`AiRunState#businessDiagnosisCompositeGateResult`** 与 Harness / GET-run 摘要上的 **`compositeGate*`** 调试字段。 |
| **禁止** | **不**根据 Gate **`allowed`** 改 **`MasterBusinessAgent` / DataPlanner** 路由；**不**执行 Composite **`PlannerExecutor`**；**不**改最终回答、单域 Tool 计划、SQL、Adapter、Resolver 主逻辑、Composer 输出；**不**调 LLM；**不**接前台新 UI。 |
| **feature flag** | **`ai.composite.businessDiagnosis.productionEnabled`**，**默认 `false`**（未配置等同 false）。为 false 时 **`reasonCode`** 恒 **`FEATURE_FLAG_DISABLED`**。 |
| **调用点** | **`AiRunService#startRun`** / **`executeBusinessGraphSyncForHarness`**：在 **`newRunStateFromResolved`** 之后、异步 Graph / trace 启动前调用 **`recordCompositeProductionGateObservation`**。 |
| **调试可见性** | **`AiHarnessResolvedContextSummarizer`**：`resolvedQueryContextSummary` 与（**`ai.harness.debug-context-enabled=true`** 时）SSE **`run_*`** 信封顶层摊平 **`compositeGate*`**（含 **C-56.2** **`compositeGateProductionEnabledSource` / `compositeGateProductionEnabledEffective`**）；**`compositeGateDebug`** 内另有 **`productionEnabledSource`**（**CONFIG** / **HARNESS_OVERRIDE**）、**`productionEnabledEffective`**；**INFO** 日志 **`[AiRunService] compositeProductionGate`**。 |
| **后继** | **C-56**：配置化 / 灰度、以及与 **`PlannerExecutor`** 生产接线的 **Master / Graph** 路由（本阶段 **不做**）。**C-56.1**（下）仅解决 **flag=true** 的**低成本的观测验收**，**不接** Composite **执行**。 |

---

## C-56.1 降低 feature flag 测试成本（设计）

**背景**：**C-54** 已在 **Harness-only Gate** 八用例下验证 **`evaluate`**；**C-55** 已在真机 **`ai.composite.businessDiagnosis.productionEnabled` 默认 false** 下验证 **`FEATURE_FLAG_DISABLED`** 且**不伤主链路**。要把配置改为 **`true`** 以在主链路摘要里看到 **`ALLOWED_STORE` / `ALLOWED_GROUP`** 等，往往需要**重启**，每轮手工验证成本高。

### 方案 A：仅文档与流程约定

| 项 | 说明 |
|----|------|
| **做法** | **C-56** 正式收口时**允许只做一次**「改配置 + 重启」的**回归**；日常开发**不**把「配置 true」当作每轮必测项；**C-57** 前可再**集中**开配置测一轮。 |
| **优点** | **零**代码；无新增攻击面。 |
| **缺点** | 需要验证「**真实 Graph + Resolver 已物化的结构化上下文**」与 **flag=true** 组合时，仍依赖环境配置和重启。 |

### 方案 B：Harness-only 覆盖（**推荐**）

| 项 | 说明 |
|----|------|
| **目标** | 在 **不**改 **`/api/ai/runs`** 公网契约、**不**让终端用户请求体带开关的前提下，**仅**在 **`POST /api/ai/harness/replay`**（及同源 Harness 路径）允许**可选**覆盖「传入 **`evaluate` 的布尔第三个参数」**，从而**零重启**验证：**真实主链路图跑完前的观测态** + **`compositeProductionEnabled=true`** 时的 **`compositeGate*`** / **`gateReasonCode`**。 |
| **硬约束** | **不**执行 Composite **`PlannerExecutor`**；**不**改 **`MasterBusinessAgent` / DataPlanner / Tool / 最终回答**；**不**改 **SQL / Resolver / Composer**；**不**调 **LLM**；**不**接前台；**禁止**在 **`AiRunCreateRequest`** 或普通 **Run 创建 API** 上新增可写 production flag 的字段。 |
| **推荐字段** | 在 **`AiHarnessReplayRequest`** 增加可选 **`Boolean compositeProductionGateProductionEnabledOverride`**（包装类型，`null` = **不覆盖**，仍用 Spring **`${ai.composite.businessDiagnosis.productionEnabled:false}`**）。 |
| **接线（最小实现建议）** | 1）**`AiRunService#executeBusinessGraphSyncForHarness`** 增加最后一参 **`Boolean compositeProductionGateProductionEnabledOverride`**（或等价 `Optional<Boolean>`），**仅** Harness 入口传入。 2）**`recordCompositeProductionGateObservation`** 调整为：effectiveEnabled = **override != null ? override.booleanValue() :** 注入的 **productionEnabled**。 3）**`AiRunService#startRun`** **始终**传 **`null` 覆盖**（或调用不设 override 的重载），行为与 **C-55** 完全一致。 4）**`AiHarnessReplayService`** 在 **`GRAPH_RUN`** 分支组装完 **`runReq` 后**，调用 **`executeBusinessGraphSyncForHarness(..., req.getCompositeProductionGateProductionEnabledOverride())`**。 |
| **与 C-54 差分** | **C-54** **`AiHarnessReplayCompositeGate`** 已能 **`evaluate(..., true/false)`**，但**不**跑 **Business Graph**；**C-56.1** 面向 **`GRAPH_RUN`**：**同一套「真 Resolver + 真 DataPlanner + 真 Tool」链路**，仅**覆盖 Gate 观测用第三个参数**。 |
| **验收** | 普通 **`startRun`**：**未配置**时 **`compositeGateReasonCode=FEATURE_FLAG_DISABLED`**。**Harness replay + `GRAPH_RUN` + override=true + 白名单对话构造`**：摘要/日志可见 **`ALLOWED_STORE` / `ALLOWED_GROUP`**（取决于用例与结构化上下文）；**仍无 Composite PlannerExecutor**。 |
| **安全** | Override **不得**从 **`/api/ai/runs`** 流入；仅 Harness  replay 请求体；若 Harness 接口本身需鉴权，保持现有网关策略。 |

**结论**：**优先采用方案 B** 作为 **C-56.1** 实施蓝本；方案 A 保留为**无代码环境下的流程兜底**。

---

## C-56.2 Harness-only override（**已实装**）

| 项 | 说明 |
|----|------|
| **请求字段** | **`AiHarnessReplayRequest#compositeProductionGateProductionEnabledOverride`**：`Boolean`；**`null`** → 第三个参数 = **`${ai.composite.businessDiagnosis.productionEnabled:false}`**；**`true` / `false`** → 该轮 **Harness** 强制传入 **`evaluate(..., true/false)`**。 |
| **生效路径** | **仅** **`POST /api/ai/harness/replay`** 且 **`replayMode=GRAPH_RUN`**：**`AiHarnessReplayService`** → **`AiRunService#executeBusinessGraphSyncForHarness(..., override)`**。**C-54** Gate-only、**PlannerExecutor** mock 短路 **不**读该字段。 |
| **普通 Run** | **`AiRunService#startRun`** **仅**调用 **`recordCompositeProductionGateObservation(state, null)`**；**`AiRunCreateRequest`** **无**此字段；**`/api/ai/runs`** **不受影响**。 |
| **effective** | **`boolean effectiveEnabled = (override != null) ? override : compositeBusinessDiagnosisProductionEnabled`**。 |
| **调试** | **`gateResult.debug`** 含 **`productionEnabledSource`**（**`CONFIG` | `HARNESS_OVERRIDE`**）、**`productionEnabledEffective`**（布尔）。摘要顶层另付 **`compositeGateProductionEnabledSource`**、**`compositeGateProductionEnabledEffective`**（与 **`AiHarnessResolvedContextSummarizer`** / SSE 调试信封对齐）。 |
| **边界** | **override=true** 且 **Gate allowed** 时：**仅当** **`compositeBusinessDiagnosisExecutionMode=HARNESS_ONLY`**（**C-58**）才执行 Composite **`PlannerExecutor`**；**不改变** **`finalAnswerText`**。**`SHADOW`**（**C-60**，**普通 Run** **`AiRunService#executeRun`**）由 **`ai.composite.businessDiagnosis.executionMode=SHADOW`** 触发，**不**由本 Harness 字段触发；**Harness** 与 **普通 Run** **入口分离**。**不改**图路由、Tool、Composer 主链路。 |

---

## C-58 Harness-only Composite 执行（**已实装**）

在与 **C-56.2** 相同的 **`GRAPH_RUN` → `executeBusinessGraphSyncForHarness`** 路径上，**可选** **`AiHarnessReplayRequest#compositeBusinessDiagnosisExecutionMode`**：

| 取值 | 行为 |
|------|------|
| **`HARNESS_ONLY`** | Graph **成功结束且未 cancel** 后调用 **`BusinessDiagnosisCompositeExecutionService#tryExecute`**（**还须** **`gate != null && gate.allowed`**，否则结构化 **`executed=false`**）。产物写入 **`AiRunState#businessDiagnosisCompositeExecutionResult`**；**`AiHarnessResolvedContextSummarizer`** 输出 **`compositeExecution*`** / **`compositeFinalAnswerText`** 等。**不替换** **`finalAnswerText`**。 |
| **`OFF` / `null` / 未识别** | **不调** Composite **`PlannerExecutor`**（与 **C-56.2-only** 行为一致：`compositeGate*` 仍可观测）。 |
| **`SHADOW`（Harness 请求体） / `PRIMARY`（Harness 请求体）** | **`HARNESS_ONLY` 之外的 Harness 取值** **不执行** Composite（与 **`OFF`** 等价于 **`maybeExecuteHarnessCompositePlanner`**：**清空** **`businessDiagnosisCompositeExecutionResult`**）。**`SHADOW` 的生产旁路** 见 **§C-60** — **Spring** **`executionMode=SHADOW`** + **`AiRunService#executeRun`**。**`PRIMARY`**：**保留枚举**；**不接**（**C-60+**）。 |

**Gate 组合**：典型验收 **`compositeProductionGateProductionEnabledOverride=true`** **或** 配置 **`ai.composite.businessDiagnosis.productionEnabled=true`**，使 **`evaluate`** **`allowed`** 后 **`HARNESS_ONLY`** 才进入 **PlannerExecutor**；参见 **`business-diagnosis-production-composite-execution-design.md` §12**。

---

## C-59 **`SHADOW`** 语义（设计）与 **C-60** **普通 Run 接线（已实装）**

### C-59 设计边界

**目标**：在 **`/api/ai/runs`** **普通 Run** 上，当 **`productionEnabled=true`**、**Spring `executionMode=SHADOW`**、**Gate `allowed=true`** 时，**旁路**执行与 C-58 **相同**的 Composite **`PlannerExecutor` + Readonly Composer**；**用户终稿仍用 legacy**，**不替换** **`finalAnswerText` / `answerPreview`**。

**权威**（与普通 Run 触发条件、**四域读放大**、观测键、`compositeShadow*` 预留、**SHADOW 失败禁止反噬 legacy**）：**[`business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md) §13～§14**。

**与 C-58 关系**：**C-58** = Harness **`GRAPH_RUN` + 请求 `HARNESS_ONLY`**（**`executeBusinessGraphSyncForHarness`**）；**C-60** = **Spring `SHADOW`**（**`executeRun`**，legacy 图后 **`maybeExecuteShadowCompositePlanner`**）。**入口分离**，**不**在同一路径上对同一轮 **双重** Harness+Spring Composite（Harness 不调 **`maybeExecuteShadowCompositePlanner`**）。

### C-60 最小实装摘要

**挂载**：**`AiRunService#executeRun`** — **`graphRunner.runBusinessGraph` 完成之后**、**未 cancel**。  
**SSE / debug**：**`summarizeCompositeGateAndExecutionOnly`**：`compositeGate*` 与 **`compositeExecution*`**（**独立于** **`ai.harness.debug-context-enabled`**）。  
**PRIMARY**：Spring **`PRIMARY`** **`tryExecute` 不执行** PlannerExecutor。  

**详见**：composite **`§14`**。

---

## C-62 **`SHADOW` 灰度白名单与限流（§16 设计）** · C-63 **最小实现（已实装）**

**问题**：若 **`productionEnabled=true`** 且 **`executionMode=SHADOW`** 且 **Gate `allowed=true`** **对所有租户无差别**放行，则在 **C-60/C-61** 普通 **`executeRun`** 上会对 **每笔**合格请求 **再跑一整轮**四域 Hydrated Composite（**读放大 ×2**、**墙钟加长**、**DB / SSE** 承压），与设计 **§13.3**「双跑代价」不一致。

**权威（白名单维度、每分钟/每小时 cap、冷却、配置键、`compositeShadowSkipped` 等、fallback、`ShadowPolicy` / `ShadowDecision`、`maybeExecuteShadowCompositePlanner` 前判定）**：**[`business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md) §16～§17**。

**与本 Gate 文档边界**：**`BusinessDiagnosisCompositeProductionGate` 仍必须先 `allowed=true`**；Shadow 闸门 **只做** 租户 / 配额层的 **OR / 限流**，**不得** 替代 **intent / path / ref** 的结构化判定。**不接 `PRIMARY`**。**不**改写 **`finalAnswerText` / `answerPreview`**。**禁止** 以 Harness **`caseId`** 作为普通 Run 放行依据。

**C-63 手工验收（已通过）**：普通 **`POST /api/ai/runs`** + **`GET .../events`** 下 **`ShadowPolicy`** 三轮（**`shadow.enabled=false`** / **whitelist 命中** / **whitelist 未命中**）观测与 **composite §17.2** 表一致；**`compositeGateAllowed=true`**、**`compositeGateReasonCode=ALLOWED_GROUP`** 贯穿样例；**SHADOW** 下 **`compositeShadowFinalAnswerReplaced=false`**；**whitelist 命中**样例 **`compositeShadowLatencyMs` ≈ 27s**，扩灰度须关注 **性能与读放大**（见 composite **§13.3 / §16 / §17.2**）。

---

## C-64 **`SHADOW` 灰度上线策略**

**问题**：「**Gate `allowed` + ShadowPolicy 放行**」在技术上 **可**触发 **旁路 Composite**，但 **C-63 基线样例旁路 ~27s** + **§13.3** 读放大，**不设运营/SRE 清单**易造成 **无意间扩面打库或拖慢 SSE**。

**权威（完整清单）**：**[`business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md)**；**composite** **§18** 摘要索引。

**要点（与本文边界）**：**Gate** **仍只**判 **intent/path/ref/scope**；**`ShadowPolicy`** **只**做 **灰度闸**（**默认关**、白名单、限流）。**C-64** 约定：**内部 user 白名单起步**、**STORE / GROUP 分时放量**、**单业务场景**、**`maxRunsPerMinute` / `maxRunsPerHour` / `cooldownSeconds` 初期极保守**（例 **每小时 3～5 次**）、**须观察** SSE **`composite*`** 摘要与 **DB / Tool 日志**、**「立即关闸」任一条命中则 `shadow.enabled=false`**（**先关后查**）。**不接 PRIMARY**；**不替换 **legacy **`finalAnswerText` / `answerPreview`**。**C-65（字段 / 日复盘 / 扩灰准入 / 暂停操作化）**见 **[`business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md)**。**C-66+**（日志聚合 metrics、dashboard、跨实例限流、只读复用 legacy **`toolResults`**、PRIMARY）见 rollout **§7**。

---

## C-65 **`SHADOW` 灰度观测与复盘清单**

**问题**：**C-64** 已约定受众与关闸总则，但若 **批次间**不按同一套 **`composite*`** 抽样与日复盘口径汇总，易出现 **误判**放量或漏看 **legacy 牵连**。

**权威（全文）**：**[`business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md)**；**composite** **§19** 摘要索引。

**要点（仅索引）**：**§1** 每请求须存档字段 — **`userId` / `distributerId` / `departmentId` / `scopeType`**、**`compositeGateAllowed` / `compositeGateReasonCode`**、**`compositeExecuted`**、**`compositeExecutionSuccess`**、**`compositeFallbackRequired`**、**`compositeExecutionErrorCode` / `compositeExecutionErrorMessage`**、**`compositePlannerOverallStatus` / `compositePlannerDegradedSteps`**、**`compositeShadowLatencyMs`**、**`compositeShadowSkipped` / `compositeShadowSkipReason`**、**`compositeShadowWhitelistMatched` / `compositeShadowThrottleHit`**、**`compositeFinalAnswerText` 非空**、**legacy 终稿正常**；**§2** 每日复盘表；**§3** 扩大灰度；**§4** 暂停灰度；**§5** **C-66+**。**判据**须基于 **结构化 Gate / Resolver / `composite*`**，**禁止**用户原文 **`contains`/regex** 路由。

---

## C-57 Gate `allowed=true` 后的安全执行与灰度（**仅设计**）

**目标**：在 **`BusinessDiagnosisCompositeProductionGate.evaluate` 返回 `allowed=true`**（且 **`effectiveIntent` / `timeWindow` / `visibleStores`** 等已由 Resolver 物化）的前提下，定义 **如何、在何种配置下** 调用 **生产级** Composite **`PlannerExecutor`**，并区分 **HARNESS_ONLY / SHADOW / PRIMARY** 三阶段；**禁止**将 **`AiPlannerExecutorBusinessDiagnosisComposite*GraphCase`** 作为 **`/api/ai/runs`** 主入口。

**权威**：**[`business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md)**（**`BusinessDiagnosisCompositePlanFactory` / `BusinessDiagnosisCompositeExecutionService` / `BusinessDiagnosisCompositeExecutionResult` / `BusinessDiagnosisCompositeExecutionMode`**、配置键、**STORE/GROUP**、`fallback`、**四域 Tool 重复执行** 分期策略、**C-58** 最小切片、**C-59 `SHADOW` §13**、**C-60 §14**、**C-62～C-65**：**§16～§17 Shadow 灰度** + **`business-diagnosis-shadow-rollout-plan.md`（§18）** + **`business-diagnosis-shadow-observation-checklist.md`（§19）**）。

**与本 Gate 文档关系**：**C-53** 仍 **「只判不断」**；**C-57** 新增 **「allowed 且 mode 允许时的执行编排」**。**`ai.composite.businessDiagnosis.productionEnabled`** **仍为**观测与 Gate 第三参总闸；**`executionMode`** 与 **`fallbackToLegacyOnFailure`** **为 C-57 新增建议配置**（**C-58** 落地）。

---

## 10. C-53 闭环 Master 前 checklist

- [x] **Gate 类名**：**`BusinessDiagnosisCompositeProductionGate`**（**`com.nongxinle.ai.planner`**）。  
- [x] **只读入参**：**`AiResolvedQueryContext` + 可选 `AiRunState`**；**无**用户消息入参。  
- [x] **C-52.1** 白名单 / 禁止 / 澄清 / 排行深挖 **`reasonCode`**。  
- [x] **主链路观测（关闭态）**：**C-55** **`AiRunService`** 写入 **`businessDiagnosisCompositeGateResult`** + **`compositeGate*`** 摘要；**默认 flag false**。  
- [x] **Harness Composite（C-58）**：**`GRAPH_RUN`** + **`compositeBusinessDiagnosisExecutionMode=HARNESS_ONLY`** **`BusinessDiagnosisCompositeExecutionService`**；**不因** Harness **GraphCase** 直连；**普通 Run 不传、不执行**。  
- [x] **`SHADOW` 普通 Run（C-60）**：**`AiRunService#executeRun`** legacy 图后 **`maybeExecuteShadowCompositePlanner`**（**`productionEnabled=true`**、**`executionMode=SHADOW`**、**`gate.allowed`**）；composite **§14**。  
- [x] **`SHADOW` 灰度（C-62 设计 → C-63 编码）**：在 **`maybeExecuteShadowCompositePlanner`** 内、**`tryExecute` 之前**调用 **`ShadowPolicy.evaluate`** — **默认 **`shadow.enabled=false`** 时 **不旁路 Composite**（**`compositeShadowSkipped=true`**）；**不改变** **`finalAnswerText`**；Harness **`HARNESS_ONLY`** **不经过 **`ShadowPolicy`**；composite **§16～§17**。
- [ ] **`PRIMARY` 生产**：**C-57** 设计已定；**C-60+** **`PRIMARY`** 与普通 **`startRun`** 终稿替换 / 去重。  
- [ ] **观测**：日志 / trace 落 **`reasonCode`**（C-55 已 **INFO** + **`resolvedQueryContextSummary`**）。  
- [ ] **配置绑定**：**`compositeProductionEnabled`** ← 配置中心（**C-56**；**C-55** 已 **`ai.composite.businessDiagnosis.productionEnabled`**，默认 false）。

---

## 11. 当前不做（C-52 / C-53 骨架冻结）

| 不做项 | 说明 |
|--------|------|
| **接生产主链路** | **C-55**：**仅观测** — **`AiRunService`** 记录 Gate；**仍不**因 Gate 改 Master / Composite 执行 |
| **接前台** | 仅 §9 **字段建议** |
| **LLM** | **不**用于 Gate、**不**用于 v1 诊断事实 |
| **Action / 复杂推荐** | **不**在 Composite v1 |
| **SQL / Tool / Adapter / Resolver / Master / Composer 改动** | **禁止** |
| **多轮记忆** | **不**纳入 C-52 Gate |
| **用户原文 contains/regex** | **禁止** 在 Gate **或** Composite 层 **新增** |
| **编造 query 层 `COMPOSITE` intent** | **C-52.1**：仅 **§3.3** 白名单；**`BUSINESS_DIAGNOSIS_COMPOSITE`** **仅** AnswerPlan **type** |

---

## 12. 参考索引

| 文档 | 用途 |
|------|------|
| [`planner-executor-v1-design.md`](./planner-executor-v1-design.md) | PlannerExecutor 位置、§27 Composite |
| [`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md) | caseId、六步、诚实性 |
| [`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md) | GROUP 口径与降级 |
| [`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md) | AnswerPlan、`dataCoverage`、`riskLevel` |
| [`business-diagnosis-composer-readonly-design.md`](./business-diagnosis-composer-readonly-design.md) | C-50/C-51 Composer |
| [`business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md) | **C-57** … **C-63** §17 … **C-64** §18 **`business-diagnosis-shadow-rollout-plan.md`**；**C-65** §19 **`business-diagnosis-shadow-observation-checklist.md`**；**PRIMARY** **未生效** |
| [`business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md) | **C-64**：**`SHADOW` 灰度** — 状态、受众、限流、指标、§5 关闸；**§6** → **C-65**；**§7** → **C-66+** |
| [`business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md) | **C-65**：**批次字段 / 日复盘表 / 扩灰与暂停操作化** |

**文档版本**：**C-52**（设计）+ **C-52.1**（§3.3 意图表）+ **C-53**（Gate Java skeleton）+ **C-54**（**`AiHarnessReplayCompositeGate`** Harness-only replay）+ **C-55**（生产主链路 **关闭态** Gate **观测**）+ **C-56.1**（Harness override **设计**）+ **C-56.2**（**GRAPH_RUN** **`compositeProductionGateProductionEnabledOverride` 已实装**）+ **C-57**（**生产 Composite 执行与灰度** 设计）+ **C-58**（**HARNESS_ONLY** Composite **`PlannerExecutor`** **仅 Harness `GRAPH_RUN`**）+ **C-59**（**`SHADOW`** **语义**，§C-59 → composite §13）+ **C-60**（**`SHADOW`** **普通 **`executeRun`** 旁路**，§C-60 → composite §14；**不改变** **`/api/ai/runs` 用户终稿**）+ **§C-62**（composite §16：**灰度设计**）+ **§C-63**：**composite §17：** **`ShadowPolicy` / SSE `compositeShadowSkipped*`** **已编码**；**C-63 三轮手工验收已通过**（composite **§17.2**）+ **§C-64**（**[`business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md)**：**`SHADOW` 灰度上线策略**）+ **§C-65**（**[`business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md)**：**观测与复盘清单**）。
