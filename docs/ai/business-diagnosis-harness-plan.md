# 经营诊断 Harness：阶段三设计说明（文档先行）

> **状态**：设计基线已采纳（v0.2）；Java 最小闭环实现中。  
> **读者**：接手餐饮 AI Harness 的工程师。  
> **定位**：经营诊断是**上层编排**，独立于单表 / 单链路工具；**不要**混进 `PURCHASE_OVERVIEW`、`STOCK_REDUCE_QUERY`、`DISH_PROFIT` 的 path 语义里。

> **2026-05-12 补充（AnswerPlan 优先）**：阶段一 **Harness 数据平面** 以 **`docs/ai/diagnosis-answer-plan.md`** 为准——**DiagnosisPlan / DiagnosisPlanBuilder 必须消费已落地的** **`PurchaseAnswerPlan`**、**`StockReduceAnswerPlan`**、**`DishProfitAnswerPlan`**、**`DailyRevenueAnswerPlan`**，**禁止**让 Composer 或诊断层绕过这些计划从原始 Tool 重算、重排。本文下述 **Tool 列表** 仍描述「子能力来源」，**事实引用** 应理解为 **各域 AnswerPlan 已选事实**，而非重复解析 Tool JSON。仓库内 **`BusinessDiagnosisPlan`** 若与 `diagnosis-answer-plan.md` 字段名不一致，以 **新文档** 为产品契约方向，后续 PR 收敛 DTO。

---

## 1. 目标与边界

### 1.1 要解决的用户问题（第一版）

用自然语言回答「跨工具、偏管理视角」的经营问题，例如：

- 某店 / 全部门店本周期经营是否正常、有何风险。
- 哪个门店最值得优先关注。
- 成本是否偏高（需结合采购、出库、毛利等**已有**口径）。
- 哪个菜拖累毛利（依赖菜品毛利 Tool 的排行与权威字段）。
- 老板今天应优先看哪几件事（**短清单**，非完整经营报告）。

### 1.2 第一版交付形态（四块）

不做复杂报告、不做「大而全」菜单工程。每条 Run 结构化输出固定为四块：

1. **总体结论**：本周期经营是否正常；数据是否足以支撑结论。
2. **主要风险**：采购 / 出库 / 毛利 / 数据链路断点等（**数字与判定依据来自 Tool + 规则层**）。
3. **重点对象**：需关注的门店、菜品、成本项（**对象列表由规则 + Tool 结果选出**）。
4. **建议动作**：先查什么、先调什么、哪些数据要补齐（**可经规则生成条目，Composer 只润色顺序与语气**）。

### 1.3 本轮明确不做

- 不深抠菜品毛利单个问法、不重写 `buildInsight` / 采购 / 出库 SQL。
- 不让 Composer 心算毛利率、理论/实际成本、差额、利用率等（与 `docs/ai/dish-profit-answer-plan.md`、`harness-composer-architecture.md` 一致）。
- 不把经营诊断逻辑塞进已通过验收的采购 / 出库 / 菜品毛利 path 的「主流程」里硬分支。

---

## 2. 建议 Intent / Path

| 项 | 建议值 |
|----|--------|
| Intent | `BUSINESS_DIAGNOSIS` |
| Path | `business_diagnosis_path` |

若代码库中已存在同名或近义 intent/path，**优先复用**并在实现时只做编排层挂载；本文档以「独立上层 path」为默认假设。

---

## 3. 依赖的已有 Tool（只组合，不重写）

经营诊断 Orchestrator **调用**下列稳定能力，入参均须从 `AiResolvedQueryContext` 推导（见第 6 节）：

| Tool（逻辑名 / 与现网对齐） | 用途（诊断侧） |
|-----------------------------|----------------|
| 采购概览（`PURCHASE_OVERVIEW` / `purchase_overview` 链路） | 周期采购金额、结构异常、供货商侧信号（遵守既有口径，如自采占位 `supplierId=-1/null` 等） |
| 出库 / 核销（`STOCK_REDUCE_QUERY` / `stock_reduce_query`） | 出库金额、type1–type4 结构、异常占比（**不把 type2 叫损耗、不把 type3 叫废弃**） |
| 菜品毛利（`DishProfitAnalysisTool` / `dish_profit_path`） | 毛利 overview、低毛利菜、高实际成本菜、成本差等（**排行与字段以 `buildInsight` / AnswerPlan 载荷为准**） |
| 日营业额（可选） | `dataCompleteness.revenue` 可为 `MISSING`；第一版允许无日营业额仍出诊断，但需在结论中标明收入侧未接入 |

**原则**：诊断层增加的是**调用顺序、并行策略、规则汇总、DiagnosisPlan 组装**；不复制 SQL、不复算核心指标。

---

## 4. DiagnosisPlan 契约（Tool / 规则 → Composer）

DiagnosisPlan 是**经营诊断**侧的中间层，角色类比菜品毛利的 **AnswerPlan**：服务端完成选对象、风险分级、完整性标记；Composer **只**根据 DiagnosisPlan + 各 Tool 已返回事实组织自然语言。

### 4.1 建议 JSON 形状（与实现语言无关）

```json
{
  "type": "BUSINESS_DIAGNOSIS",
  "scopeLabel": "汀兰餐厅",
  "timeLabel": "上个月",
  "riskLevel": "WARN",
  "overallSummary": {
    "normalized": true,
    "dataSufficient": true,
    "headline": "服务端可给的短句或码，Composer 可改写语气"
  },
  "mainFindings": [],
  "riskItems": [],
  "focusTargets": {
    "stores": [],
    "dishes": [],
    "costCategories": []
  },
  "actionItems": [],
  "sourceTools": [
    "purchase_overview",
    "stock_reduce_query",
    "dish_profit_analysis"
  ],
  "usedTools": [
    "purchase_overview",
    "stock_reduce_query",
    "dish_profit_analysis"
  ],
  "dataCompleteness": {
    "purchase": "OK",
    "stockReduce": "OK",
    "dishProfit": "OK",
    "revenue": "MISSING"
  },
  "debugRef": {
    "purchaseSnapshotId": null,
    "stockReduceSnapshotId": null,
    "dishProfitAnswerPlanType": null
  },
  "sourceResultSummary": {
    "purchase": {
      "totalAmount": 1491,
      "selfPurchaseAmount": 1423,
      "supplierPurchaseAmount": 68,
      "riskSignals": []
    },
    "stockReduce": {
      "totalAmount": 1042.7,
      "produceAmount": 1025.7,
      "wasteAmount": 0,
      "lossAmount": 17,
      "returnAmount": 0,
      "riskSignals": []
    },
    "dishProfit": {
      "salesAmount": 2931,
      "actualCostAmount": 1025.7,
      "grossMarginRate": 65.01,
      "lowestMarginDish": "核桃芽菜西芹",
      "riskSignals": []
    }
  }
}
```

**说明**：

- `sourceResultSummary`：**仅摘要**，便于 Composer 与人类阅读；**不**替代原始 ToolResult / 卡片。数值须来自对应 Tool 或下游 Agent（如菜品透视 summary），禁止在 Composer 中重新计算。
- `mainFindings`：面向「总体结论」的条目列表（每条应有 `code` / `severity` / `evidence` 引用，避免 Composer 编数字）。
- `riskItems`：面向「主要风险」；可与 `riskLevel`（如 `INFO` / `WARN` / `HIGH`）对齐。建议**每条**统一为以下结构（便于前台卡片与 Replay）：

```json
{
  "level": "WARN",
  "domain": "DISH_PROFIT",
  "title": "核桃芽菜西芹毛利偏低",
  "evidence": "综合毛利率 53.28%，低于本轮其他菜品",
  "suggestion": "优先核对售价与配方成本"
}
```

字段含义：`level`（与 `riskLevel` 档位可一致或更细）、`domain`（`PURCHASE` / `STOCK_REDUCE` / `DISH_PROFIT` / `DATA_CHAIN` / `REVENUE` 等）、`title` 短标题、`evidence` 须有 Tool/摘要依据、`suggestion` 可执行动作。
- `focusTargets`：与第 1.2 节「重点对象」对应；门店 / 菜品 ID 或名称应来自 Tool，**禁止** Composer 现场猜店名。
- `actionItems`：面向「建议动作」；优先由规则生成可执行短句，Composer 不做新业务判断。
- `sourceTools` vs `usedTools`：若实现上需区分「计划调用」与「实际调用（某工具失败跳过）」，可保留两字段；否则合并为一亦可。
- `debugRef`：可选，用于 Replay 关联子 Tool 的 summary / AnswerPlan 类型，**具体键名实现时再定**。

上述字段为**契约初稿**；落地时可平铺为 Java DTO（如 `BusinessDiagnosisPlan`），但语义应与本文一致。

### 4.2 `riskLevel` 与 `dataCompleteness` 枚举（建议）

| 字段 | 建议枚举 |
|------|-----------|
| `riskLevel` | `INFO` / `WARN` / `HIGH`（或 `OK` / `WARN` / `CRITICAL`，全项目统一一种即可） |
| `dataCompleteness.*` | `OK` / `PARTIAL` / `MISSING` / `FAILED`（`FAILED` 表示调用异常，区别于「无数据」） |

---

## 5. 规则层 vs Composer 分工

### 5.1 必须由规则 / 服务端完成的判断

- **范围与时间**：严格来自 `AiResolvedQueryContext`（时间窗、`queryScopeKind`、门店 / 部门 / 经销主体字段），**不**回退到废弃的混合 `queryDepartmentIds` 语义。
- **SQL 范围**：仅使用 `expandedSqlDepartmentIds`（或 `AiResolvedDataScope` 同源 API）作为 `department_id IN (...)` 依据；**不**把展开 ID 当作「门店列表」展示给用户。
- **工具选择与并行**：根据用户问法解析子任务（如「哪个门店」需门店对比 → 触发多店聚合策略）；失败重试 / 降级策略在服务端记录到 Debug。
- **异常与完整性**：各 Tool 是否成功、是否空结果、是否部分时间无数据 → 写入 `dataCompleteness` 与 `riskItems`。
- **跨工具对比的信号**（第一版可用**简单阈值 + 环比**，阈值配置化）例如：
  - 采购金额相对历史同窗偏差超过配置比例 → `riskItems` 一条。
  - 出库 type2/type3 占比超阈值 → 一条（文案模版由规则带证据字段）。
  - 菜品毛利：引用 `blendedGrossMarginRateOnListPrice`、`diffCostAmount` 等**已有字段**，由规则标注「偏低 / 偏高 / 差异大」，**禁止** Composer 重算比率。
- **排行与 Top-N**：「哪个菜拖累毛利」等必须沿用菜品毛利 **`DISH_LOWEST_MARGIN` / 等价 AnswerPlan + focusRows** 的结果，诊断层只做引用与汇总。

### 5.2 交给 Composer 的内容

- 将 **DiagnosisPlan 四块结构**译为连贯中文段落顺序。
- 根据 `scopeLabel`、`timeLabel` 生成礼貌的首尾句。
- **不得**：自行计算毛利率、成本差、利用率；不得捏造未在 Tool / DiagnosisPlan 出现的店名、菜名、金额。
- **不得**：重新排序 Tool 已给出的榜单或替换 focus 菜品。

---

## 6. 必须接入的 `AiResolvedQueryContext`

经营诊断全链路**只读**统一解析结果，至少要覆盖并在 Debug 中展示：

| 字段/概念 | 用途 |
|-----------|------|
| `timeWindow` | 诊断周期；与 `timeSource` 一并展示 |
| `scopeType` / `visibleStores` | 展示「用户可见范围」与多店对比语义 |
| `queryScopeKind` | `STORE` / `DEPARTMENT` / `DISTRIBUTER` |
| `queryStoreIds` | 按门店查询时的 root id 列表 |
| `queryRealDepartmentIds` | 按真实部门查询时的部门 id |
| `queryDistributerId` | 按组织 / 经销主体时的单值 |
| `expandedSqlDepartmentIds` | **仅** SQL 展开，与业务展示口径解耦 |

**禁止**：把 `expandedSqlDepartmentIds` 当作门店列表对外界解释；**禁止**恢复 `queryDepartmentIds` 作为业务语义主字段。

---

## 7. Run Debug / Replay 必备字段

建议在现有 Harness Debug 面板中，对 `business_diagnosis_path` **至少**增加或对齐展示：

| 展示项 | 说明 |
|--------|------|
| `intent` | 含 `BUSINESS_DIAGNOSIS` |
| `path` | `business_diagnosis_path` |
| `timeSource` | 时间解析来源 |
| `time` / `timeWindow` | 起止与粒度 |
| `scopeType` | 与 dataScope 一致 |
| `visibleStores` | 用户可见门店信息 |
| `queryScopeKind` | 主查询维度 |
| `queryStoreIds` | 门店 root |
| `queryRealDepartmentIds` | 部门 id |
| `queryDistributerId` | 单值主体 |
| `expandedSqlDepartmentIds` | SQL 展开 ID |
| `usedTools` | 实际调用的工具列表 |
| `diagnosisPlan` | 完整 DiagnosisPlan（或序列化摘要） |
| `riskLevel` | 汇总风险等级 |
| `dataCompleteness` | 各域数据完整性 |

（若与现有 `AiRunState` / Summarizer 字段名略有不一致，以**对齐 `AiHarnessResolvedContextSummarizer` 与 Run DTO** 为准，但语义上须全覆盖上表。）

---

## 8. 第一批测试问题（验收问法）

实现 Java 后建议用下列问题做回归（解析 → 多 Tool → DiagnosisPlan → Composer）：

1. 上个月汀兰餐厅经营有什么问题？  
2. 这个月全部门店经营情况怎么样？  
3. 上个月哪个门店最需要关注？  
4. 这个月成本是不是太高？  
5. 哪个菜拖累毛利？  
6. 老板今天应该先看哪三件事？  

**期望**：每条均可 Replay 看到 intent/path、范围字段、`usedTools`、`diagnosisPlan`、`riskLevel`、`dataCompleteness`；Composer 输出中关键数字可追溯到某一 Tool 返回值字段。

---

## 9. 冻结模块（本轮及诊断第一版请勿大改）

以下模块已通过阶段验收或属核心算法边界，**经营诊断不应倒逼重写**：

- 采购 Harness：`PURCHASE_OVERVIEW` / `purchase_overview_path` 及底层 SQL。
- 出库 Harness：`STOCK_REDUCE_QUERY` / `stock_reduce_query_path` 及 type1–type4 口径。
- 菜品毛利：`GbDepFoodBusinessInsightService#buildInsight`、`GbDishCostAnalysisService`、`DishProfitAnalysisTool`、`DishProfitAgentNode` 及现有 AnswerPlan 类型（`DISH_PROFIT_OVERVIEW`、`DISH_LOWEST_MARGIN` 等）。
- Composer 心算禁令所覆盖的全部指标字段（见菜品毛利 AnswerPlan 文档）。

诊断层**允许**：新增 Orchestrator、DiagnosisPlan DTO、规则配置、Debug 字段、**新** Path/Intent 注册与 Composer **新规约**（只解释 DiagnosisPlan）。

---

## 10. 与现有文档的关系

| 文档 | 关系 |
|------|------|
| **`docs/ai/diagnosis-answer-plan.md`** | **阶段一**：DiagnosisPlan 字段、`focusFindings` / `evidenceRows`、Debug、Composer 边界；**AnswerPlan 优先** |
| `docs/ai/harness-composer-architecture.md` | 分层模型、Context 字段、Composer 边界 |
| `docs/ai/dish-profit-answer-plan.md` | 菜品侧 AnswerPlan 与权威字段；诊断中「拖累毛利」等须引用此契约 |
| `docs/ai/purchase-answer-plan.md` / `stock-reduce-answer-plan.md` / `revenue-answer-plan.md` | 各域 planType 与冻结说明；诊断只读其输出 |
| `docs/ai/dish-profit-legacy-review.md`（若存在） | 旧版算法与字段溯源 |

---

## 11. Review 检查清单（给审阅者）

- [ ] DiagnosisPlan 是否足以让 Composer **不计算、不选榜**？  
- [ ] 是否所有 Tool 入参都可从 **非** `queryDepartmentIds` 的 Context 字段导出？  
- [ ] Debug 是否覆盖「范围主语义 vs SQL 展开」两套字段？  
- [ ] 第一版四块输出是否在 JSON 中有对应锚点（或明确由 `mainFindings` / `riskItems` / `focusTargets` / `actionItems` 映射）？  
- [ ] 日营业额缺失时，是否仍能给 **降级** 结论而非胡编收入？  
- [ ] 是否明确写清 **冻结模块**，避免 PR 评审范围蔓延？

---

**文档版本**：v0.2（Review 通过：补充 `sourceResultSummary`、统一 `riskItems` 结构；Java 最小闭环对齐本文契约。）
