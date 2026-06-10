# Semantic Query Parser — V2 输出 JSON 契约

**用途：** 生产语义解析唯一 prompt 为 **`query_semantic_parser.v2.md`**（`semantic.query_parser.v2`）。本文定义 **LLM 须输出的 JSON 字段名、嵌套结构与枚举**，供 **`AiQuerySemanticParseResultJsonParser`** 解析。

**非 runtime 说明：**
- 不存在 v1 单字符串 user 入口；v2 未收养时走 clarification / frame validation，**不回退** v1 parser。
- LLM 输出的 **`time`** 块经 **`SemanticTimeContractCheck.reconcileTimePartForContract`** 仅在缺 **`startDate`/`endDate`** 时做有限补齐（`DEFAULT_MONTH_TO_DATE` / `INHERITED_PREVIOUS`），再 **`check`** 结构自洽；**PASS** 后写入 **`AiResolvedTimeWindow`** 与 **`effectiveTimeWindowSource`**；**FAIL** 进入 Resolver 澄清。Java **不**据 `timeType` 重算日期、**不**读 `time.reason` 或用户原文时间词。**Historical removed**：`AiMultiTurnTimeWindowPolicy#finalizeTimeWindow`。
- 当 `allowedContracts` 非空时，**`semanticSlots.selectedContractId` 是业务合同主键**。Completion 成功后，canonical wire / `answerPlanType` / `selectedTools` / execution path 统一来自该 `selectedContractId` 对应的同一条 ACTIVE contract entry。LLM 输出的 **`structuredIntentDetailWire`** 仅为 raw/debug 观测；**`metric.rankingType`** 为 **deprecated / compat / debug** 观测字段，二者均不得参与服务端主 wire、path 或 AnswerPlan（**D-1X-D3-RANKINGTYPE-FINAL** 已收口）。

**域内业务规则（采购矩阵、库存/出库、编排等）以 v2 prompt 专节为准；本文仅列通用字段与 D-13 槽位形状。**

---

## 契约治理 · Wire / semanticSlots 登记规则

下列规则适用于 **v2 Prompt、`semantic-output-schema.md`、Java 执行链、Matrix、AnswerPlan、Composer** 的协同维护；避免「文档写了、代码没接」或「Prompt 发明 wire、Java 不认识」的漂移。

### 主语义依据

| 层级 | 权威 |
|------|------|
| **主语义（contract-locked）** | **`semanticSlots.selectedContractId` → ACTIVE contract entry**；entry 拥有 canonical wire / `answerPlanType` / `selectedTools` / execution path 主权 |
| **槽位校验视图** | `queryObject` / `operation` / `metric` / `sourceFacet` / `anchorPolicy` / `detailWanted` 必须与所选同一条 entry 对齐 |
| **raw / debug / transitional only** | LLM **`structuredIntentDetailWire`**、`orchestrationDecisionCandidate.selectedTools`、`reason` marker、**`metric.rankingType`**、部分 **`metric.stockReduceType`** / **`metric.purchaseSourceType`**：**不得**参与服务端 wire 推断、path 路由、Tool 选择或 AnswerPlan 主判断（见 [`docs/ai/contract-entry-validation-p2-summary.md`](../../../../docs/ai/contract-entry-validation-p2-summary.md)） |

**后置主权边界**：Java 可以在 V2 前做确定性实体存在性落地，用于缩小 `allowedContracts` 或触发澄清；V2 之后 Java 没有重新选择业务合同的权力。Completion 成功后，任何 support / repair / normalize / slot merge / scope / planner / tool / AnswerPlan / Composer 都不得修改 `selectedContractId`、canonical wire、`answerPlanType` 或 `selectedTools`。后置发现冲突只能澄清、失败或 known gap。

### Prompt 不得发明未登记 wire

- **`query_semantic_parser.v2.md`** 与 LLM 输出中的 **`structuredIntentDetailWire` / `structuredIntentDetail`** 应使用本文档 / 各域 **`domain capability matrix / answer-plan docs`** 已列出的 canonical wire；但服务端执行不信任 LLM wire，contract-locked 后只使用 `selectedContractId` 对应 ACTIVE entry 的 wire。
- **禁止**输出 Java Merge / Matrix / AnswerPlan **未登记** 的蛇形 wire；若产品需要新口径，先走下方登记清单，**再**改 Prompt 专节。

### 新 wire 进入生产前同步清单（7 步）

新增或变更一条 **生产 wire** 时，须在同一变更集或连续 PR 内对齐：

| # | 工件 | 说明 |
|---|------|------|
| 1 | **`AiQuerySemanticLexicon.java`** | 增加 `STRUCTURED_*` 常量；必要时补 canonical 别名映射 |
| 2 | **`semantic-output-schema.md`** | 域内白名单 / 枚举表增补（本文） |
| 3 | **对应 `docs/ai/domain capability matrix / answer-plan docs`** | 矩阵行：首轮 / 追问、`knownGap` 标注 |
| 4 | **对应 `*SemanticCapabilityMatrix.java`** | 可解析、可挂 AnswerPlan；`MATRIX_WIRE_MISSING` 行为明确 |
| 5 | **对应 `*AnswerPlan` / `*AnswerPlanBuilder`** | `planType` 与 wire 映射 |
| 6 | **Composer / `*DeterministicRenderer` / `StubAnswerComposerNode`** | 有 Plan 须有**专用宣读分支**；禁止仅 generic fallback |
| 7 | **Harness** | 新增或更新 replay case；若暂不实现须写 **`knownGap`** 与文档 **Planned/Gap**，**不得**让 Prompt 当作已支持能力输出 |

### Planned / Gap 与 Prompt 的关系

- 若 **schema 或 v2 专节已写**、但 **Matrix 行标 `knownGap` 或 Java 未挂 Plan**，该 wire 在 Prompt 中应标注为 **Planned/Gap** 或 **勿作为默认输出**，避免模型稳定产出「假闭环」JSON。
- Harness **strict** 失败（如 `MATRIX_WIRE_MISSING`）优于生产环境 silent 降级到错误话术。

### 相关索引（勿新建独立治理文件）

- 主链与契约索引：[`docs/ai/semantic-allowed-output-contract-design.md`](../../../../docs/ai/semantic-allowed-output-contract-design.md)
- Plan-first / fallback：[`docs/ai/harness-composer-architecture.md`](../../../../docs/ai/harness-composer-architecture.md) §2.7
- Strict 模式：[`docs/ai/semantic-contract-strict-mode-plan.md`](../../../../docs/ai/semantic-contract-strict-mode-plan.md)

---

## 顶层字段（必须输出）

| 字段 | 类型 | 说明 |
|------|------|------|
| `isFollowUp` | boolean | 接续上一轮同一话题的简短追问为 true |
| `intentAction` | NEW \| INHERIT_PREVIOUS \| OVERRIDE | 业务主线相对上一轮 |
| `timeAction` | NEW \| INHERIT_PREVIOUS \| OVERRIDE | 时间窗相对上一轮 |
| `scopeAction` | NEW \| INHERIT_PREVIOUS \| OVERRIDE | 组织/可见范围相对上一轮 |
| `metricAction` | NEW \| INHERIT_PREVIOUS \| OVERRIDE | 指标子口径相对上一轮 |
| `intent` | enum | 见下节 |
| `domain` | string \| null | 业务域标签；采购填 **PURCHASE** |
| `confidence` | number | 0.0～1.0 |
| `time` | object | 见「time 对象」 |
| `requestedScope` | object | 见「requestedScope 对象」 |
| `metric` | object | 见「metric 对象」 |
| `semanticSlots` | object \| null | 见「D-13 semanticSlots」；采购排行/总览等场景**必填** |
| `mentionedDishName` | string \| null | 用户口述单道菜名 |
| `needClarification` | boolean | 信息不足时为 true |
| `clarificationQuestion` | string \| null | |
| `reason` | string \| null | 不给 ID |
| `orchestrationDecisionCandidate` | object | v2 编排；键见 v2 prompt「OrchestrationDecision」专节 |
| `stockSnapshot` | object \| null | cover-days / WH-K bundle：**必填**；见下节 |
| `salesBaselineWindow` | object \| null | cover-days / WH-K bundle：**必填**；见下节 |

布尔为小写 `true`/`false`；日期为 **yyyy-MM-dd**。

### stockSnapshot 对象（cover-days / WH-K bundle）

| 键 | 说明 |
|----|------|
| `asOfDate` | 库存快照日 `yyyy-MM-dd`；「现在/当前/现量/还有多少库存」只写此处 |

### salesBaselineWindow 对象（cover-days / WH-K bundle）

| 键 | 说明 |
|----|------|
| `action` | `DEFAULT` \| `EXPLICIT` |
| `source` | `DEFAULT_LAST_7_DAYS` \| `USER_EXPLICIT_TIME_WINDOW` |
| `startDate`, `endDate` | 销量基线起止日；DEFAULT 为 anchor 往前 6 天～anchor（ROLLING_7） |
| `timeType` | 如 `ROLLING_7`、`LAST_MONTH`、`CUSTOM` |
| `reason` | 可选观测说明 |

**WH-K bundle**：选定 `warehouse.goods_anchor_inventory_bundle.v1` 时**必须**输出 `stockSnapshot` + `salesBaselineWindow`（即使用户只问裸库存）；Java **不**从 `time` 推导。

### intent 枚举（节选）

`BUSINESS_OVERVIEW`, `REVENUE_OVERVIEW`, `PURCHASE_OVERVIEW`, `WAREHOUSE_STOCK_OVERVIEW`, `STOCK_REDUCE_QUERY`, `DISH_PROFIT`, `DISH_SALES_QUERY`, `DISH_COST_ANALYSIS`, `MENU_OPERATION`, `COST_DIAGNOSIS`, `BUSINESS_DIAGNOSIS`, …

域分工与互斥规则见 **v2 prompt** 各域专节（DISH_PROFIT vs COST_DIAGNOSIS、**MENU_OPERATION vs BUSINESS_OVERVIEW vs DISH_PROFIT/DISH_SALES**、库存现量 vs 出库核销、双域诊断等）。

---

## time 对象

| 键 | 说明 |
|----|------|
| `timeType` | 见下节「timeType 体系」；**须与 `startDate`/`endDate` 结构一致**，否则 `TIME_TYPE_DATE_MISMATCH` |
| `startDate`, `endDate` | **每轮必填** ISO 日期；由 LLM 从 **`currentUserMessage` 中的时间表达** 换算（Java 不解析用户原文） |
| `timeSource` | **`CURRENT_MESSAGE_EXPLICIT`** \| **`INHERITED_PREVIOUS`** \| **`DEFAULT_MONTH_TO_DATE`**（兼容别名 `CURRENT_MESSAGE` → 显式） |
| `needInheritFromPrevious` | true 表示声明沿用上一轮时间（须与 `timeSource=INHERITED_PREVIOUS` 一致） |
| `reason` | 简短说明（Harness 观测）；**须与 `timeSource` 一致，禁止自相矛盾** |

### timeSource / timeAction 选用（硬规则）

| 场景 | `timeSource` | 顶层 `timeAction` | 典型 `timeType` / 区间 |
|------|--------------|-------------------|------------------------|
| `currentUserMessage` **含明确时间词**（今天/昨天/本月/上个月/上上个月/本季度/上个季度/近7天/具体自然月如5月四月/起止日期/局部周段/最近/去年同期等） | **`CURRENT_MESSAGE_EXPLICIT`** | **`NEW` 或 `OVERRIDE`** | 与话术一致的 `timeType` + 对应 `startDate`/`endDate`（见「timeType 体系」） |
| 本句**未提时间**，且有 `previousTurn` 可继承 | **`INHERITED_PREVIOUS`** | **`INHERIT_PREVIOUS`** | 与上一轮相同的起止日；`needInheritFromPrevious=true` |
| 本句**未提时间**，且**无可继承**（首轮） | **`DEFAULT_MONTH_TO_DATE`** | **`NEW`** | `THIS_MONTH`：月初 1 日～`today`；`needInheritFromPrevious=false` |

**本句同时含时间词与业务实体**（如「这个月烩菜卖得怎么样」「AAA 这个月哪个菜卖得最好」）：时间仍走第一行；**不得**因存在 `previousTurn`、跨域或 `intentAction`/`scopeAction=INHERIT_PREVIOUS` 而令 **`timeAction=INHERIT_PREVIOUS`**。

**`time.reason` 须与 `timeSource` 一致（观测字段，但禁止自相矛盾）：**

| `timeSource` | 允许的 `time.reason` 语义 |
|--------------|---------------------------|
| `DEFAULT_MONTH_TO_DATE` | 未指定时间；默认本月至今；default_month_to_date |
| `CURRENT_MESSAGE_EXPLICIT` | 本句明确时间词 / 指定区间（如「本句指定上个月」） |
| `INHERITED_PREVIOUS` | 本句未再提时间；沿用上一轮区间 |

**仅改时间的多轮接力（如「上个月呢」「上个季度呢」→ Intake 规范化为「上个月{对象}…」「上个季度{对象}…」）：**

- 视为 **`CURRENT_MESSAGE_EXPLICIT`** + **`timeAction=OVERRIDE`**（或 `NEW`），**不得** `INHERIT_PREVIOUS`。
- `timeType` 须与本句时间词一致（「上个月」→ `LAST_MONTH` / `PREVIOUS_MONTH`，完整自然月起止日；「上个季度」→ `LAST_QUARTER`，完整自然季起止日）。
- 业务槽位/合同/菜名可沿 `previousTurn` 继承，**时间窗必须按本句重算**，不得复制上一轮日期。

**季度 timeType 与起止日（须与输入 `today` 对齐，服务端 Java 按自然季边界校验）：**

| 用户话术 | `timeType` | `startDate` | `endDate` |
|----------|------------|-------------|-----------|
| 这个季度/本季度 | `THIS_QUARTER` | 当季 1 日 | `today`（非季末） |
| 上个季度/上季度 | `LAST_QUARTER` | 上季 1 日 | 上季末日 |

自然季：Q1=1–3月，Q2=4–6月，Q3=7–9月，Q4=10–12月。示例（`today=2026-05-26`）：`THIS_QUARTER` → `2026-04-01`～`2026-05-26`；`LAST_QUARTER` → `2026-01-01`～`2026-03-31`。

**禁止：** 把「上个季度」写成 rolling 3 个月或复制 Intake 中错误的「YYYY年M月～YYYY年M月」区间；`THIS_QUARTER` 的 `endDate` 不得为季末未来日。

### timeType 体系（与 `SemanticTimeContractCheck` 对齐）

V2 **必须**自算 `startDate`/`endDate` 并选对 `timeType`。Java **只**做结构校验，**不**据 `timeType` 重算日期；`timeType` 与起止日不一致 → **`TIME_TYPE_DATE_MISMATCH`** → 澄清，不查数。

**三层分类（选用顺序）**

| 层 | 何时用 | 正式 `timeType` |
|----|--------|-----------------|
| **锚定相对型** | 话术相对 **`today`** 的固定日历/滚动窗口 | `TODAY`, `YESTERDAY`, `THIS_MONTH`, `LAST_MONTH`, `THIS_QUARTER`, `LAST_QUARTER`, `ROLLING_7`, `LAST_YEAR`, `YEAR_TO_DATE`, `LAST_YEAR_SAME_PERIOD`, `THIS_WEEK` |
| **自由区间型** | 点名自然月、局部周/段、起止日、上上个月、继承的非标准区间等**一切非锚定相对型** | **`CUSTOM` 唯一**（起止 = V2 换算的精确区间） |

**`CUSTOM` 是唯一自由区间 token（硬规则）**

- 生产 JSON **`timeType` 只允许**上表锚定相对型 + **`CUSTOM`**；**禁止**输出 `CUSTOM_RANGE`、`EXPLICIT_MONTH` 或其它自造标签。
- 文档中「局部区间 / 自定义范围」仅作**语义说明**；模型输出仍写 **`CUSTOM`** + 对应 `startDate`/`endDate`。
- Java / display：`AiResolvedTimeWindow.CUSTOM` 为 canonical；`normalizeSemanticTimeTypeLabel` 不归一 `CUSTOM_RANGE`；前端对 `CUSTOM` 回退展示 `startDate～endDate`。

**用户话术 → `timeType` + 起止日（`anchor` = 输入 `today`）**

| 用户话术 | `timeType` | `startDate` / `endDate` 规则 |
|----------|------------|------------------------------|
| 今天/今日 | `TODAY` | `startDate=endDate=anchor` |
| 昨天/昨日 | `YESTERDAY` | `startDate=endDate=anchor-1天` |
| 本月/这个月/当月 | `THIS_MONTH` | 当月 1 日～`anchor`（**不是**月末；`endDate` **不得**晚于 `anchor`） |
| 上个月/上月 | `LAST_MONTH` 或 `PREVIOUS_MONTH` | **完整**上一自然月 1 日～末日（相对 `anchor`）；**禁止**用 `anchor-29`～`anchor` 滚动窗冒充 |
| 最近一个月/按一个月（cover-days 销量基线） | `CUSTOM` | 滚动 30 天：`endDate=anchor`，`startDate=anchor-29天`（含首尾 30 天）；**禁止**标 `LAST_MONTH` |
| 上上个月/前两个月 | `CUSTOM` | **完整**上上个自然月 1 日～末日；**禁止**用 `LAST_MONTH` |
| 5月/四月/2025年3月 等**点名月** | `CUSTOM` | 该月 1 日～该月最后一天；**禁止** `THIS_MONTH`/`LAST_MONTH`（除非与 anchor 相对月同义） |
| 本季度/这个季度 | `THIS_QUARTER` | 当季 1 日～`anchor` |
| 上个季度/上季度 | `LAST_QUARTER` | 上一**完整**自然季 |
| 近7天/最近一周 | `ROLLING_7` | `startDate=anchor-6天`，`endDate=anchor`（含今日共 7 天） |
| 最近/近来/近期（无其它锚点） | `ROLLING_7` 或 `CUSTOM` | 近 7 天或近 30 天等**单一**滚动窗；须在 `time.reason` 点明 |
| 今年至今/今年以来 | `YEAR_TO_DATE` | 当年 1 月 1 日～`anchor` |
| 去年/上一年（整年） | `LAST_YEAR` | 上一日历年 1/1～12/31 |
| 去年同期 | `LAST_YEAR_SAME_PERIOD` | 与当前区间同长度、对齐到去年（由 V2 换算） |
| 上个月最后一周/某月第2周/3号到15号 | `CUSTOM` | 精确起止日；**禁止** `LAST_MONTH`/`THIS_MONTH`（它们表示**整月**） |
| 具体起止日（2026-04-10～2026-04-20） | `CUSTOM` | 与用户区间一致 |
| 本句未提时间，继承上一轮 | 与上一轮 `timeType` 相同或 `CUSTOM` | **`startDate`/`endDate` 必须与 `previousTurn` 完全一致** |

**Java 硬边界（违反 → `TIME_TYPE_DATE_MISMATCH`）**

以下 `timeType` 除起止日自洽外，还须满足固定日历/滚动边界（`anchor` = `today`）：

| `timeType` | 校验要点 |
|------------|----------|
| `THIS_MONTH` | 当月 1 日起；`start`/`end` 同月且等于 `anchor` 所在月；`endDate` ≤ `anchor` |
| `LAST_MONTH` / `PREVIOUS_MONTH` | 等于 `anchor` 的**上一完整自然月** |
| `THIS_QUARTER` | 当季 1 日起；季与 `anchor` 同季；`endDate` ≤ `anchor` |
| `LAST_QUARTER` | 等于 `anchor` 的**上一完整自然季** |
| `ROLLING_7` | `endDate=anchor` 且 `startDate=anchor-6天`（含首尾 7 天） |

**滚动 N 天（含首尾，全系统硬公式）**：`endDate=anchor`；`startDate=anchor-(N-1)天`；`windowDays=N`。例 N=30、anchor=2026-06-08 → 2026-05-10～2026-06-08（不是 2026-05-09～2026-06-08）。
| `TODAY` / `YESTERDAY` | 单日且等于 `anchor` / `anchor-1` |
| `LAST_YEAR` | 上一日历年整年 |

**自由区间型（仅要求 `startDate` ≤ `endDate`）**：**仅 `CUSTOM`**。`LAST_YEAR_SAME_PERIOD`、`YEAR_TO_DATE`、`THIS_WEEK` 等为锚定相对型，须自洽起止日。继承上一轮的非整月/非整季区间 → **`CUSTOM`**（或沿用上一轮已有 `timeType`），**禁止**标 `LAST_MONTH`/`THIS_MONTH`。

**别名（归一化后等价）**：`CURRENT_MONTH` → `THIS_MONTH`；`PREVIOUS_MONTH` → `LAST_MONTH`。

**维护者正例（`today=2026-06-02`）**

- 「5月采购多少」→ `timeType=CUSTOM`，`2026-05-01`～`2026-05-31`，`CURRENT_MESSAGE_EXPLICIT`（**不是** `THIS_MONTH`）。
- 「上个月最后一周出库」→ `timeType=CUSTOM`，`2026-05-25`～`2026-05-31`，`CURRENT_MESSAGE_EXPLICIT`（**不是** `LAST_MONTH` 整月）。
- 「这个月销售额」→ `timeType=THIS_MONTH`，`2026-06-01`～`2026-06-02`，`CURRENT_MESSAGE_EXPLICIT`（**不是** `2026-06-30`）。
- 继承上一轮 `2026-05-16`～`2026-05-25` → `timeType=CUSTOM`，`INHERITED_PREVIOUS`，起止与上一轮**完全一致**。

**禁止：**

- 用户**未说任何时间词**时输出 **`CURRENT_MESSAGE_EXPLICIT`**（首轮「{菜名}成本怎么样」等须用 **`DEFAULT_MONTH_TO_DATE`**）。
- 用「默认今天 / 未指定时间 / 默认本月至今」兜底却标 **`CURRENT_MESSAGE_EXPLICIT`**（应使用 **`DEFAULT_MONTH_TO_DATE`** 或 **`INHERITED_PREVIOUS`**）。
- **`time.reason`** 写「未指定时间 / 默认本月至今」等与 **`CURRENT_MESSAGE_EXPLICIT`** 同时出现。
- **`CURRENT_MESSAGE_EXPLICIT`** 与 **`timeAction=INHERIT_PREVIOUS`** 同时出现。
- 本句含新时间词（本月/这个月/上个月等）却 **`timeAction=INHERIT_PREVIOUS`** 或 **`timeSource=INHERITED_PREVIOUS`**（即使跨域、即使 `intentAction`/`scopeAction` 为 `INHERIT_PREVIOUS`）。
- 本句含新时间词却 **`timeType`/`startDate`/`endDate` 仍等于上一轮**。
- 点名自然月（`5月`/`四月`）误用 **`THIS_MONTH`/`LAST_MONTH`**（与 anchor 相对月不一致时）。
- 局部周/段（「上个月最后一周」）误用 **`LAST_MONTH`/`THIS_MONTH`**（整月标签 vs 局部起止 → **`TIME_TYPE_DATE_MISMATCH`**）。
- **`THIS_MONTH`** 的 **`endDate` 晚于 `today`**（用户说「本月/这个月」时常见错误）。
- 输出 **`CUSTOM_RANGE` / `EXPLICIT_MONTH`** 等非登记 `timeType`（自由区间**统一 `CUSTOM`**）。

**服务端：** LLM JSON 经 **`reconcileTimePartForContract`** 仅在缺起止日时补齐（`DEFAULT_MONTH_TO_DATE` / `INHERITED_PREVIOUS`），再 **`check`**；**PASS** 写入 **`AiResolvedTimeWindow`**；**FAIL**（`timeSource`/`timeType`/日期/`timeAction` 结构性矛盾，如 `TIME_TYPE_DATE_MISMATCH`）→ **`needSemanticClarification`**，不查数。显式时间（`CURRENT_MESSAGE_EXPLICIT`）的 **`startDate`/`endDate` 必须由 V2 给出**，Java 不据 `timeType` 重算。

---

## requestedScope 对象

| 键 | 说明 |
|----|------|
| `requestedScopeType` | GROUP, STORE, REGION, DEPARTMENT, WAREHOUSE, PURCHASER, USER |
| `mentionedStoreName` | 单店口述名 |
| `mentionedStoreNames` | 多店对比时 string 数组；**单店也应用数组**（如 `["AAA"]`） |
| `mentionedDepartmentName`, `mentionedWarehouseName` | 短语，无 ID |
| `scopeSource` | 如 CURRENT_MESSAGE, INHERITED_PREVIOUS |
| `needInheritFromPrevious` | boolean |

**门店 scope 与 store 合同（硬规则）**：当选中 `dish_sales.store_count_ranking`、`dish_sales.store_single_dish`、`revenue.single_store_overview` 等**须点名门店**的 ACTIVE 合同时，`currentUserMessage` 含门店口述名 → **`mentionedStoreNames` 或 `mentionedStoreName` 必填**，`requestedScopeType=STORE`，`scopeSource=CURRENT_MESSAGE`。禁止只选 store 合同却不输出门店槽位。

---

## metric 对象

| 键 | 说明 |
|----|------|
| `primaryMetric` | 如 revenue, purchase, business_status, profit_margin |
| `rankingType` | **deprecated / debug**：蛇形 wire 字面量；与 Lexicon STRUCTURED_* **可对齐作观测**；**服务端主链不读此字段推断 wire** |
| `purchaseSourceType` | ALL, SELF_PURCHASE, SUPPLIER_PURCHASE 等 |
| `stockReduceType` | 出库子类型 |

**`rankingType` 与 `semanticSlots.structuredIntentDetailWire`：** 主语义 **必须**由 **`semanticSlots.selectedContractId` + 同 entry 槽位**表达；`structuredIntentDetailWire` 和 `rankingType` 仅 LLM/Harness **debug**，服务端 **不**以其补 wire。详见 v2 采购专节。

---

## D-13 semanticSlots（采购等结构化域）

顶层键 **`semanticSlots`**（勿省略键名）。与 **`previousTurn.semanticSlots`** 同形。

| 键 | 枚举 / 说明 |
|----|-------------|
| `queryObject` | GOODS, SUPPLIER, STORE, DISH, ORDER, UNKNOWN |
| `operation` | SUMMARY, OVERVIEW, RANKING, BREAKDOWN, DETAIL, TREND, COMPARE, DIAGNOSIS |
| `metric` | PURCHASE_AMOUNT, PURCHASE_COUNT, PURCHASE_QUANTITY, UNIT_PRICE, UNKNOWN |
| `sourceFacet` | ALL, SELF_PURCHASE, SUPPLIER_PURCHASE, UNKNOWN |
| `anchorPolicy` | USE_PREVIOUS_ANCHOR, IGNORE_PREVIOUS_ANCHOR, REQUIRE_CLARIFICATION |
| `detailWanted` | 追问明细：**SOURCE_BREAKDOWN**、**SUPPLIER_BREAKDOWN**、GOODS_DETAIL、GOODS_UNIT_PRICE、**SUPPLIER_UNIT_PRICE** 等；须与所选 contract entry 一致 |
| `structuredIntentDetailWire` | **可选 debug**：canonical 蛇形 wire；服务端 contract-locked 后以 `selectedContractId` 对应 entry 的 `wire` 为准，LLM 输出不一致不阻断主链 |
| `selectedContractId` | **P4-J2**：当 Step2 提供 `allowedContracts` 时必填；从 `allowedContracts[].contractId` 精确选取 |
| `answerPlanType` | 可选；须与所选 contract entry 一致 |
| `mentionedDishName` | string \| null；**requiresAnchor=DISH** 的单菜合同须填用户口述菜名（与顶层 `mentionedDishName` 二选一或并存） |
| `requestedTargetGrossMarginRate` | string \| null；用户**明确口述**目标毛利率百分比（如 `55` 表示 55%）；仅 `dish.profit.prescription.v1` 等定价处方合同使用；**禁止** Java 从原文 regex 解析 |
| `expiryRiskFilter` | string \| null；仅 `warehouse.near_expiry`：`NEAR_EXPIRY` / `EXPIRED` / `DUE_TODAY` / `ALL_RISK`；与 Intake 同值搬运 |
| `capabilitySpecificity` | `EXPLICIT` \| `UNSPECIFIED` \| null；多子合同族（如采购异常）须输出；**UNSPECIFIED** 时禁止选具体细分合同 |

**capabilitySpecificity（多子合同族硬规则）**

| 值 | 含义 | 与 `selectedContractId` |
|----|------|-------------------------|
| `EXPLICIT` | 用户已明确子 capability（如单价异常、次数异常、金额突增） | 须选对应 ACTIVE 细分合同 |
| `UNSPECIFIED` | 用户仅泛问（如「采购有没有异常」） | **禁止**输出任一细分 `purchase.anomaly.*`；须顶层 `needClarification=true` + `clarificationQuestion` |
| `null` | 非多子合同族问法（排行/概况/清单等） | 按 entry 常规选择；**不**强制填写 |

**采购异常**：泛问 → `capabilitySpecificity=UNSPECIFIED`；明确子类型 → `EXPLICIT` + 对应 `purchase.anomaly.*`。

**DISH 锚点 / `mentionedDishName`（硬规则）**

| 场景 | 要求 |
|------|------|
| 所选 contract **`requiresAnchor=true` 且 `anchorType=DISH`** | **`mentionedDishName` 必填**（顶层和/或 `semanticSlots`，至少一处） |
| **`currentUserMessage` 含具体菜名** | 必须写入 `mentionedDishName`；**禁止**留空后依赖 Java 猜菜名 |
| 本句已含菜名 | **`anchorPolicy` 应为 `IGNORE_PREVIOUS_ANCHOR`**（或当前句锚点）；**禁止**无上轮 DISH 锚点时仍 `USE_PREVIOUS_ANCHOR` |
| 本句无菜名、承接上一轮同一道菜 | 可 `USE_PREVIOUS_ANCHOR` + 继承 `previousTurn.mentionedDishName` / resultAnchors |

示例：`currentUserMessage=烩菜卖得怎么样` → `selectedContractId=dish_sales.single_dish`，`mentionedDishName=烩菜`，`anchorPolicy=IGNORE_PREVIOUS_ANCHOR`。

**服务端协议搬运（非业务推断）**：若 LLM 将 `selectedContractId` / 槽位字段误放在 **JSON 顶层**（与 `semanticSlots` 同级），Parser 会 deterministic 搬入 `semanticSlots`；`semanticSlots` 内已有值优先。顶层 `mentionedDishName` 保留，并复制到 slots（若 slots 缺失）。

**库房现量（contract-first，`primaryDomain=WAREHOUSE`）：**

- **`allowedContracts`（ACTIVE，可选为 `selectedContractId`）**：`warehouse.overview`、`warehouse.inventory_supervision.v1`、`warehouse.goods_amount_ranking_high`、`warehouse.goods_amount_ranking_low`、`warehouse.store_amount_ranking`、`warehouse.single_store_overview`、`warehouse.inventory_risk_list`、`warehouse.near_expiry`、`warehouse.goods_supported_dish_cover.v1`、`warehouse.goods_stock_batch_detail.v1`、`warehouse.goods_anchor_inventory_bundle.v1`（wire 见各 entry）。
- **`warehouse.inventory_supervision.v1`**：wire=`warehouse_inventory_supervision`；Intake `SUPERVISION_QUERY` 时**必选**；老板入口「库存怎么样/有没有问题/有没有风险」；**禁止**用于「库存金额多少/有多少种」等数值概览。
- **`warehouse.overview`**：wire=`warehouse_stock_overview`；**数值/统计概览**（金额、种类、整体快照汇总）；**禁止**用于 Intake `SUPERVISION_QUERY` 的监督入口问法。
- **`warehouse.goods_amount_ranking_low`**：wire=`goods_stock_amount_ranking_low`，**仅**账面剩余库存**金额**升序排行；**禁止**用于库存偏少/报警/缺货/临期/监督入口问法。
- **`knownGapContracts`（禁止选为 selectedContractId）**：`warehouse.out_of_stock`。
- **`plannedContracts`（禁止选为 selectedContractId）**：`warehouse.stock_replenishment_needed`、`warehouse.stock_overstock_risk`、`warehouse.store_stock_item_count_ranking`、`warehouse.warehouse_stock_item_count_ranking`。

**Lexicon 历史 wire（勿与 ACTIVE 混用；以 `allowedContracts` 为准）：**
`warehouse_stock_overview`、`warehouse_stock_amount_ranking`、`goods_stock_amount_ranking_low`、`store_stock_amount_ranking` 等 — **禁止**在此 path 下输出出库域 `stock_reduce_*` wire。

**禁止**将 **`COMPARE_STORE`** 作为最终 `intent` 输出（已废弃；服务端 `mapLlmIntent` 不路由）。多店对比须直接输出上表业务域 + 完整 **`semanticSlots`**（见 v2「双店/多店对比」专节）。

**MenuOperation wire 白名单（`MENU_OPERATION` / `menu_operation_path`）：**
`menu_operation_overview`, `menu_dish_high_sales_low_profit`, `menu_action_recommendation` — **禁止**在此 path 下输出 `dish_profit_*` / `dish_sales_*` wire；execution wire 以 **`selectedContractId` 对应 entry** 为准。

**菜品毛利 wire 白名单（`DISH_PROFIT` / `dish_profit_path`）：**
`dish_profit_ranking_low_margin`, `dish_profit_ranking_high_margin`, `dish_profit_ranking_high_profit_amount`, `dish_profit_ranking_low_profit_amount`, `dish_gross_margin_query`, `dish_ingredient_cost_breakdown`, `dish_actual_cost_ranking_high`, `dish_actual_cost_ranking_low`, `dish_theoretical_cost_ranking_high`, `dish_theoretical_cost_ranking_low`, `dish_gap_ranking_max`, `dish_theoretical_cost`, `dish_actual_outbound_cost`, `dish_cost_gap`, `dish_low_profit_reason` — **禁止**在此 path 下填采购/出库 wire。

**菜品成本 wire 白名单（`DISH_COST_ANALYSIS` / `dish_cost_analysis_path`）：**
`dish_cost_analysis`（合同 `dish_cost.single_dish_analysis`：单菜成本/配料/实际 vs 理论成本）；`dish_profit_prescription`（合同 `dish.profit.prescription.v1`：单菜定价/毛利处方/配方优化/建议售价）；`dish_ingredient_cover_days`（合同 `dish.ingredient_cover_days.v1`：单菜配料可支撑天数）。三合同**互斥**，由 `selectedContractId` 决定，**禁止** alias 到 `menu.dish.single_analysis.v1`。

**canonical wire 白名单** 与 **`AiQuerySemanticLexicon`** / v2 prompt「structuredIntentDetailWire 白名单」一致。

**采购槽位完整性：** 须给出 queryObject / operation / metric / sourceFacet / anchorPolicy；追问还须 detailWanted + wire。**禁止**仅用 `metric.rankingType` 代替 slots。

**菜品毛利槽位完整性（Phase 1 矩阵）：** 排行 / 单菜 / DISH 锚原料构成须输出完整 **`semanticSlots`** + **`structuredIntentDetailWire`**；**`metric.rankingType` 仅 debug**，服务端 **不以之写 wire**。缺 wire → **`MATRIX_WIRE_MISSING`**，strict harness 失败。

**双域采购↔出库风险 vs 采购商品明细（勿混 wire）：**

| 场景 | `intent` | `structuredIntentDetailWire` |
|------|----------|------------------------------|
| 采购多但出库少 / 买得多没怎么用 / **最近采购多但出库少** | `BUSINESS_DIAGNOSIS` | `purchase_stock_reduce_mismatch` |
| 采购了但没有核销 / 采购后长期未核销 | `BUSINESS_DIAGNOSIS` | `purchase_slow_moving_risk`（或对照句式用 `purchase_stock_reduce_mismatch`） |
| **退货金额/退货多少/退库**（出库 type4，非采购） | `STOCK_REDUCE_QUERY` | `return`（合同 `stock_reduce.return_overview`） |
| 商品**出库金额**排行（明确金额） | `STOCK_REDUCE_QUERY` | `goods_outbound_ranking` |
| 商品出库**数量/次数/用得最多/出库最多**（无金额词，P1） | `STOCK_REDUCE_QUERY` | **须** `needClarification`；对照 `knownGapContracts.stock_reduce.goods_count_ranking`；**禁止** `goods_amount_ranking` |
| 泛化**出库有没有异常**（P1） | `STOCK_REDUCE_QUERY` | **须** `needClarification`（禁止 `waste`/`overview` 凑合） |
| 供货商渠道**定了什么货** / 商品行明细 / 来源拆桶 | `PURCHASE_OVERVIEW` | `purchase_source_goods_query`（须 `detailWanted=GOODS_DETAIL` 等） |
| 点名单一原料**采购经营分析**（来源+量价+库存+销量匹配） | `PURCHASE_OVERVIEW` | `purchase_goods_business_analysis`（合同 `purchase.goods_business_analysis.v1`）；`sourceFacet=ALL`（默认） |
| 同上且问句**明确供货商/供应商/配送商**采购 | `PURCHASE_OVERVIEW` | 同上；**`sourceFacet=SUPPLIER_PURCHASE`**（禁止 `ALL`） |
| 同上且问句**明确自采** | `PURCHASE_OVERVIEW` | 同上；**`sourceFacet=SELF_PURCHASE`** |

**禁止**把「最近采购了但没有核销的商品有哪些？」落成 **`purchase_source_goods_query`**。

**仅改时间的接力：** 当本句只含明确新时间词，须从 **`previousTurn.semanticSlots`** 逐字段继承，**禁止** `semanticSlots: null` 或 `{}`。

---

## orchestrationDecisionCandidate.selectedTools（现网 Tool 白名单）

| `intent` / 有效路径语义 | `selectedTools`（仅此表内 id） |
|-------------------------|--------------------------------|
| `REVENUE_OVERVIEW` / `revenue_overview_path` | `["revenue_query"]` |
| `PURCHASE_OVERVIEW` / `purchase_overview_path` | `["purchase_overview"]`（默认）；contract-locked **`purchase.goods_business_analysis.v1`** → `["purchase_goods_business_analysis"]` |
| `WAREHOUSE_STOCK_OVERVIEW` / `warehouse_stock_overview_path` | `["warehouse_stock_overview"]` |
| `STOCK_REDUCE_QUERY` / `stock_reduce_query_path` | `["stock_reduce_query"]` |
| `DISH_PROFIT` / `dish_profit_path` | `["dish_profit_analysis"]` |
| `DISH_SALES_QUERY` / `dish_sales_query_path`（语义 wire，非 Tool id） | 排行：`["dish_profit_analysis"]`；单菜合同 `dish_sales.single_dish` / `dish_sales.store_single_dish`：`["dish_sales_analysis_card"]` |
| `DISH_COST_ANALYSIS` / `dish_cost_analysis_path` | 成本卡 `dish_cost.single_dish_analysis`：`["dish_cost_analysis"]`；利润处方 `dish.profit.prescription.v1`：`["dish_profit_analysis","dish_cost_analysis"]`（顺序固定；服务端按 contract 取交集） |
| `MENU_OPERATION` / `menu_operation_path` | `["dish_profit_analysis"]` |
| `COST_DIAGNOSIS` / `cost_diagnosis_path` | `revenue_query`, `purchase_overview`, `stock_reduce_query`, `dish_profit_analysis`（四 Tool；毛利由服务端推导） |
| `BUSINESS_OVERVIEW` MULTI 四域 | 同上四 Tool |
| 采购+出库双域 `BUSINESS_DIAGNOSIS` 风险 | `purchase_overview`, `stock_reduce_query` |

**Historical removed（禁止输出）**：`purchase_query`, `stock_query`, `dish_sales_query`, `gross_margin_calculator`, `business_overview_query`, `purchase_anomaly_query` 等未注册 Tool id。

---

## 禁止出现在输出中的键

`queryStoreIds`, `queryRealDepartmentIds`, `expandedSqlDepartmentIds`, `storeToDepartmentIds`, `queryDistributerId`, `distributerId`, `departmentIds`，及任意数值型部门/门店 ID、SQL。

**禁止回显 User 输入结构**：`allowedOutputContract`, `allowedContracts`, `visibleStores`, `previousTurn`, `semanticRoute`, `currentUserMessage`, `today`（这些是 Parser **输入**键，不是 V2 **输出** schema）。

---

## 归档说明

历史单串 user 形态曾见 **`query_semantic_parser.v1.md`**（**D-CLEAN-V1 已从生产 prompt 目录删除**；Git 历史可检索，勿作字段契约来源）。
