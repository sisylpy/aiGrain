【Harness Engineering 修改前硬原则｜Cursor / Codex 必须先读】

这是餐饮 AI / Harness Engineering 数据查询系统。当前目标不是让某个 case 勉强通过，而是维护长期稳定的合同链路。

如果你改代码前没有先判断“问题属于哪一层”，本次修改很可能是错的。

---

## 0. Cursor 最容易犯的错误｜先看这里

你过去最容易犯这些错误，以后发现苗头必须立刻停止并汇报：

### 错误 1：为了一个 case 写 Java 关键词规则

禁止写：

* rawMessage.contains(...)
* normalizedMessage.contains(...)
* completedUserQuery.contains(...)
* Pattern.compile("上个月|昨天|成本|采购|经营|利润|营收|库存|菜品...")
* List.of("上个月", "昨天", "今天", "成本", "采购", "经营"...)
* if/else 判断业务域、时间、菜名、指标、意图
* alias / fallback / heuristic 修 case

Java 不负责理解中文自然语言。
LLM / Semantic Intake / V2 负责理解自然语言。
Java 只读结构化字段。

### 错误 2：让 LLM wire 或 Matrix wire 抢主链

禁止让这些字段决定执行：

* LLM 输出的 semanticSlots.structuredIntentDetailWire
* rawStructuredIntentDetail
* metric.rankingType
* Matrix scope-remapped wire
* debug observed wire

selectedContractId 一旦命中 ACTIVE contract entry，主链 wire 必须来自 contract.getWire()。

### 错误 3：把 scope 当成语义

GROUP / STORE / PURCHASER 只能影响权限范围、SQL 范围。
scope 不允许影响：

* selectedContractId
* canonicalStructuredIntentDetailWire
* answerPlanType
* selectedTools
* execution path

### 错误 4：把 Java 改成时间解释器

**完整 Time Layer 规则见 `.cursor/rules/time-layer-inheritance.mdc`（alwaysApply，每个窗口必读）。**

时间处理不要复杂化。

Java 只允许做三件事：

1. 首轮无显式时间 → DEFAULT_MONTH_TO_DATE。
2. 上下文延续且无显式新时间 → INHERITED_PREVIOUS（与业务域 / selectedContractId 无关）。
3. 校验 V2 输出的 timeSource / timeAction / startDate / endDate 是否自洽。

禁止：

* Java 根据 timeType=LAST_MONTH 自动计算上个月日期。
* Java 根据 PREVIOUS_MONTH / LAST_MONTH 推 startDate/endDate。
* Java 根据 time.reason 里的「未指定」「默认本月至今」等中文解释校正 timeSource。
* Java 根据 rawMessage / completedUserQuery / normalizedMessage 识别“上个月、昨天、本月”。
* 在各业务域、各合同、各 Card 内单独写时间继承 if/else。

显式时间必须由 V2 直接输出 startDate/endDate。
如果 V2 输出错，应该修 Prompt / schema / time contract，或者 contract 拦截，不要 Java 自动解释。

### 错误 5：看到 Tool / AnswerPlan 现象就回头改语义主链

不要因为卡片错、AnswerPlan 空、Tool 查不到，就直接改 wire、selectedContractId、anchor 或 SQL。
必须先判断问题层级。

### 错误 6：保留 deprecated / no-op / 兼容壳，给下个窗口埋坑

如果旧类、旧字段、旧方法已经没有主链职责，并且名称会误导后续 Agent 继续加规则，优先删除或摘主链。
不要保留类似 HintSupport、Fallback、Legacy、Noop 壳，让下个 Cursor 继续往里加 if/else。

### 错误 7：在 Contract Exporter 里写 Prompt（第三套语义规则）

`*SemanticCapabilityContractExporter` 只能做 **Matrix → 机器合同** 只读导出（`contractId` / `wire` / slots / `selectedTools` / `status` / `gapMarker`）。

禁止：

* 在 Exporter 里新增或扩充 `description`、`selectionHint`、`negativeHint`、`positiveExamples`、`negativeExamples`
* 为某个自然语言问句在 Exporter 的 `switch` 里写中文说明
* 在 Exporter 用 `contains` / 问法 if/else 选合同

老板问法、混淆边界、正反例 → **只**改 `semantic_intake.v1.md`、`query_semantic_parser.v2.md`、Harness case。

完整治理见 `.cursor/rules/semantic-contract-exporter.mdc` 与 `docs/ai/semantic-contract-exporter-governance.md`。

### 错误 8：多处重复决策 / 下游补挂替换卡片

禁止在多个节点对**同一业务决定**重复判断或后置修复，例如：

* PlanType + executionIntent + contractId + wire + 已有 cards 分别推导 CardType
* Composer / refresh 阶段 `reconcile` 漏挂补挂、错卡替换
* Service 层 duplicate early-return / suppress，与投影层各写一套
* 从 Tool 全量结果或 cards 内容事后筛选目标实体

**完整规则见 §12（单一主权与单一投影）。** 业务映射只允许一个 SSOT + 一个执行入口；下游只消费，不修正业务含义。

---

## 1. 最核心原则

1. Java 不允许猜业务语义。
2. LLM 负责理解自然语言，输出结构化语义。
3. Java 只负责合同校验、权限范围、上下文继承、Tool 参数拼装和确定性执行。
4. 如果结构化字段不足，应澄清或 known gap，不能用 Java 关键词补救。
5. 不允许为了单个测试 case 写局部补丁。
6. 不允许改动已经稳定的前期主流程，除非先汇报原因并获得确认。

---

## 2. Contract-owned Wire 硬原则

1. LLM 不允许自定义业务 wire。

2. V2 只在单域 `allowedContracts` 内选择 `semanticSlots.selectedContractId`；`selectedContractId` 是语义合同主键。

3. selectedContractId 一旦命中 ACTIVE Capability Contract entry：

    * canonicalStructuredIntentDetailWire 必须来自 contract.getWire()
    * answerPlanType 必须来自 contract entry
    * selectedTools 必须来自 contract entry
    * execution path / tool 元数据必须来自 contract entry

4. LLM 输出的 semanticSlots.structuredIntentDetailWire 只能作为 raw/debug 观测字段。

5. 不允许因为 LLM wire 与 contract wire 不一致，就 UNSUPPORTED_CONTRACT。

6. slot merge / repair / canonicalize / matrix 不允许重新推导或覆盖主链 wire。

7. Tool / AnswerPlan / Composer 只能读取 contract-locked frame，不能读取 raw LLM wire。

8. Java 可以在 V2 前做确定性实体存在性落地，用于缩小 `allowedContracts` 或触发澄清；**V2 之后 Java 没有重新选择业务合同的权力**。

9. `SemanticContractCompletionEngine.complete()` 成功后，任何 support / repair / normalize / slot merge / scope / planner / tool / AnswerPlan / Composer 层都不得修改：

    * `selectedContractId`
    * canonical wire / `structuredIntentDetailWire`
    * `answerPlanType`
    * `selectedTools`
    * execution path / tool metadata

10. 后置发现合同、实体或槽位冲突时，只能澄清、失败或 known gap；即使重新经过 Completion / Validation，也不能用来合法化 Java 后置切换合同。

11. Time、Scope、Business Contract 主权相互独立：时间继承不由合同切换决定，scope 不决定合同/wire/tools/path，合同不改写时间或权限范围。

发现以下情况必须主动指出或清理：

* structuredIntentDetailWire 参与主链 hard blocker
* selectedContractId 已命中合同后仍使用 LLM wire
* applyContractToParse 里 LLM wire 优先于 contract wire
* Completion 成功后 support 再调用 `applyActiveContractById` 或等价方法切换合同
* CurrentSemanticFrame 主 wire 来自 raw LLM
* Matrix / SlotMerge / Canonicalize 根据 scope 或 slots 改写主链 wire
* metric.rankingType / rawStructuredIntentDetail / 旧 wire 字段抢主链
* prompt 要求 LLM 自己配对 contractId 和 wire，并且 Java 信任这个配对

---

## 3. Java 不猜语义｜禁止关键词规则

严禁新增或恢复以下模式：

* rawMessage.contains(...)
* completedUserQuery.contains(...)
* normalizedMessage.contains(...)
* Pattern.compile(...)
* List.of("上个月", "昨天", "今天", "成本", "采购", "经营"...)
* alias 兼容
* fallback 猜测
* heuristic 修 case
* if/else 判断业务域、时间、菜名、指标、意图

Java 可以判断结构化字段，例如：

* selectedContractId
* semanticSlots.queryObject / operation / metric / sourceFacet
* semanticSlots.mentionedDishName
* anchorPolicy
* timeSource / timeAction
* previousTurn.lastSemanticSlots
* previousTurn.resultAnchors
* resolvedQueryContext.scope
* visibleStores / queryStoreIds / expandedSqlDepartmentIds

Java 不可以根据中文原文推断业务语义。

---

## 4. 时间主权原则｜不要再改复杂

时间问题只能按下面边界处理。

### 4.1 显式时间

例如用户说“今天、昨天、上个月、这个月、去年同期”等。

这类显式时间必须由 V2 直接输出：

* timeSource
* timeAction
* timeType
* startDate
* endDate

Java 只校验，不解释。

如果 V2 输出：

* timeType=LAST_MONTH
* startDate/endDate 却是本月至今

Java 应判为 TIME_TYPE_DATE_MISMATCH，不能自动重算上个月日期。

### 4.2 首轮无显式时间

首轮用户没有说时间，例如：

* 烩菜成本怎么样
* 椒麻鸡销售怎么样

Java 可以给系统默认：

* timeSource=DEFAULT_MONTH_TO_DATE
* timeAction=NEW
* startDate=本月 1 日
* endDate=today

这是系统默认，不是 Java 理解自然语言。

### 4.3 追问无显式新时间

用户没有说新时间，只是继续追问，例如：

* 那这个菜呢
* 那采购呢
* 那另一个店呢

如果结构化字段表明是继承追问，Java 可以继承上一轮时间：

* timeSource=INHERITED_PREVIOUS
* timeAction=INHERIT_PREVIOUS
* startDate/endDate 复制上一轮

### 4.4 禁止事项

禁止：

* Java 根据 timeType 自动推日期。
* Java 根据 LAST_MONTH / PREVIOUS_MONTH 计算日期。
* Java 读取 time.reason 中文解释来校正 timeSource。
* Java 读取 rawMessage / completedUserQuery / normalizedMessage 识别“上个月”。
* SemanticTimeContractCheck 变成时间解析器。

SemanticTimeContractCheck 只能做：

* 缺失字段检查
* timeSource/timeAction 合法性检查
* timeType/date 一致性检查
* 默认时间补齐
* 继承时间补齐

---

## 5. 修改边界｜不要破坏已稳定主流程

已经稳定的主链包括：

1. selectedContractId → ACTIVE contract entry → contract-owned wire
2. Java 不猜业务语义，不写 rawMessage / contains / regex / 中文词表
3. 时间处理只做：默认、继承、校验；显式时间由 V2 给 startDate/endDate
4. scope 只影响权限和查询范围，不影响 selectedContractId / wire / answerPlanType / selectedTools
5. Tool / SQL / AnswerPlan / Composer 各司其职，不跨层修问题
6. Dish anchor 使用统一 effectiveDishAnchor，当前实体优先，只有 USE_PREVIOUS_ANCHOR 才继承上一轮

如果你发现必须改动前期稳定流程，先不要改，先汇报：

1. 为什么必须改旧流程
2. 不改会影响什么
3. 会不会破坏已通过的 GROUP / STORE / DishSales / DishCost 链路
4. 有没有更小的结构化修复方案

---

## 6. Anchor / 多轮追问原则

1. 当前轮显式实体永远优先。
2. 当前轮有 mentionedDishName 时，必须覆盖上一轮菜品。
3. 只有当前轮没有菜品，且 anchorPolicy=USE_PREVIOUS_ANCHOR 时，才允许继承上一轮 DISH anchor。
4. anchorPolicy=IGNORE_PREVIOUS_ANCHOR 时，禁止继承 previous dishName / foodId。
5. Tool Request、Adapter、AnswerPlan、Card、Composer 必须使用同一个 effectiveDishAnchor。
6. 不允许某一层自己重新从 previousTurn 取菜品或 foodId。
7. 当前轮显式新菜名时，previous foodId 必须清空，避免用上一轮实体劫持查询。
8. 时间追问处理必须依赖结构化 timeAction / timeSource / anchorPolicy / previousTurn，不允许 Java 识别“上个月”三个字。

## 6.1 Semantic Slot Merge / 上下文继承硬边界

`AiQuerySemanticSlotMerge`、`CurrentSemanticFrame`、`previousTurnSummary`、`lastSemanticSlots` 等上下文合并逻辑，只能做结构化上下文补齐，不能成为业务语义修复中心。

### 6.1.1 当前轮合同主权优先

如果当前轮 V2 / Contract Selection 已经输出了明确的 `selectedContractId`，并且该 `selectedContractId` 命中 ACTIVE Capability Contract，则当前轮合同拥有最高主权。

此时 previousTurn 不允许覆盖当前轮以下字段：

* selectedContractId
* structuredIntentDetailWire / canonicalStructuredIntentDetailWire
* queryObject
* operation
* metric
* sourceFacet
* answerPlanType
* selectedTools
* executionIntentType
* pathCode / intentCode

上一轮只能作为上下文辅助，不能改写当前轮业务语义。

### 6.1.2 跨 domain 禁止继承业务语义槽位

如果上一轮 domain 与当前轮 domain 不一致，例如：

* 上一轮 DISH_SALES，当前轮 BUSINESS_OVERVIEW
* 上一轮 PURCHASE，当前轮 WAREHOUSE
* 上一轮 DISH_PROFIT，当前轮 MENU_OPERATION

则 previousTurn 只能继承安全上下文：

* scope
* store / department 范围
* conversationId / runId
* 必要的权限上下文

禁止继承业务语义槽位：

* selectedContractId
* wire
* queryObject
* operation
* metric
* answerPlanType
* mentionedDishName / foodId
* rankingType
* detailWanted
* sourceFacet

跨 domain 继承业务语义，一律视为主链污染。

### 6.1.3 只有同域 time-only follow-up 才允许继承业务合同

只有同时满足以下条件，才允许从上一轮继承业务合同：

1. 当前轮没有新的明确业务合同；
2. 当前轮被结构化识别为 time-only follow-up；
3. 当前轮没有新的 queryObject / operation / metric；
4. 当前轮与上一轮属于同一 contract family / domain；
5. 上一轮合同是可继承合同；
6. 当前轮只是替换时间，例如“上个月呢 / 这个月呢 / 昨天呢”。

示例：

允许：

* 上一轮：“销量高”
* 当前轮：“上个月呢”
* 继承 dish_sales.count_ranking_high，只替换时间。

禁止：

* 上一轮：“销量高”
* 当前轮：“昨天经营情况怎么样？”
* 当前轮已经是 BUSINESS_OVERVIEW，不允许继承 dish_sales.count_ranking_high。

### 6.1.4 resultAnchor 不能自动升级为下一轮主语

排行结果里的 Top1 / TopN anchor 只能作为可点击下钻或显式追问依据。

禁止在以下场景自动把 Top1 变成 mentionedDishName：

* 用户只说“上个月呢”
* 用户只说“这个月呢”
* 用户只改时间
* 用户问新的业务域，例如“昨天经营怎么样”

只有用户明确表达实体追问时，才允许使用 resultAnchor，例如：

* “第一个菜呢”
* “这个菜呢”
* “核桃芽菜西芹呢”
* “第一名利润怎么样”

### 6.1.5 SlotMerge 禁止成为业务 if/else 补丁中心

禁止在 `AiQuerySemanticSlotMerge` 或类似 merge/reconcile 文件里不断新增业务 case 分支，例如：

* if 上一轮是 DISH_SALES，当前轮是 BUSINESS_OVERVIEW，则重置某字段
* if 上一轮是 PURCHASE，当前轮是 WAREHOUSE，则改某 wire
* if 问句像某个业务，就补某个 contract
* if 某个 case 失败，就特殊清空某个 slot

这类写法会让情况无限增长，必须禁止。

正确做法是抽象成通用门禁：

* 当前轮合同优先；
* 跨 domain 不继承业务 slots；
* 同域 time-only 才继承业务合同；
* resultAnchor 只有显式实体追问才使用；
* previousTurn 不允许覆盖当前轮 ACTIVE contract。

### 6.1.6 修改 SlotMerge 前必须先汇报

凡是准备修改以下文件或同类逻辑：

* AiQuerySemanticSlotMerge
* CurrentSemanticFrame repair / merge
* previousTurnSummary merge
* lastSemanticSlots merge
* semantic slot canonicalize / reconcile
* anchor inherit / repair

必须先汇报：

1. 当前问题属于哪一层；
2. 当前轮 V2 raw 输出是什么；
3. 当前轮 contract-locked 输出是什么；
4. 是否发生 previousTurn 污染当前轮；
5. 是否跨 domain；
6. 是否只是 time-only follow-up；
7. 是否准备新增业务 if/else；
8. 为什么不能通过 Prompt / Contract / Intake / 通用继承规则解决。

未汇报前，不允许直接在 SlotMerge 里加新分支。

## 6.2 Semantic Inheritance / Business Frame 继承硬规则

**完整架构说明见：** [`docs/ai/semantic-inheritance-architecture.md`](../../docs/ai/semantic-inheritance-architecture.md)

多轮继承必须按三层分离，**禁止字段级拼装 Business Frame**：

| 层 | 允许 | 禁止 |
|---|---|---|
| **Context** | scope / store / department / permission | 业务 contract / wire / slots |
| **Time** | 追问无显式新时间时继承上一轮；有显式时间用当前轮 | Java 读 rawMessage 识别「上个月」 |
| **Business Frame** | 同域 time-only follow-up：整包继承或整包不继承 | 从 previousTurn / current raw 零散 copy / coalesce |

### 6.2.1 主链类职责（不得写业务 if/else 补丁）

| 类 | 职责 | 禁止 |
|---|---|---|
| `SemanticSlotInheritancePolicy` | 结构化决策：INHERIT_NONE / INHERIT_CONTEXT_ONLY / INHERIT_SAME_FAMILY_TIME_FOLLOWUP | per-domain case 分支 |
| `SemanticSlotInheritanceApplier` | 按 decision 写入；time-only 时从 Catalog 派生完整 frame | 从 `lastSemanticSlots` 字段级 copy；`coalesce(cur, prev)` 业务槽位 |
| `CanonicalContractFrameSupport` | `previousContractId` → ACTIVE entry → 完整 canonical frame | 从 memory slots 拼装 |
| `SemanticAdoptionPipeline` | 编排 Policy → Applier → ContractCompletion | 在此或下游再 merge previous 业务 slots |
| `AiQuerySemanticSlotMerge` | wire 镜像、anchor 结构化 reconcile、memory 落库对齐 | 恢复 `reconcile*FollowUpSlots`；业务域 if/else |

### 6.2.2 current sovereign ACTIVE contract 绝对优先

当前轮 `selectedContractId` 命中 ACTIVE Capability Contract 且具备主权（跨 family、非 time-only 弱选、显式实体追问等）时：

* **previousTurn 不得覆盖**任何业务语义字段（见 §6.1.1 列表）。
* 典型禁止：上一轮「销量高」+ 当前轮「昨天经营情况怎么样」→ 不得继承 `dish_sales.count_ranking_high`。
* previousTurn **最多**继承 Context（scope / store）。

### 6.2.3 同域 time-only follow-up：完整 Business Frame，不是零散 slot

仅当 `SemanticSlotInheritancePolicy` 判定为 `INHERIT_SAME_FAMILY_TIME_FOLLOWUP` 时：

1. previousTurn **只提供** `previousContractId`（lookup key）。
2. 用 `SemanticContractCatalog` / CapabilityContractRegistry 查 **ACTIVE contract entry**。
3. 经 `CanonicalContractFrameSupport` 派生**完整** canonical frame，至少包括：
   `selectedContractId`、`structuredIntentDetailWire`、`queryObject`、`operation`、`metric`、`sourceFacet`、`detailWanted`、`answerPlanType`、`selectedTools`、`intentCode` / `pathCode`、`anchorPolicy`。
4. **保留**当前轮 `time` / `requestedScope`；**禁止**用 previous 覆盖。
5. **禁止**半旧半新：`selectedContractId` 来自 previous、但 `sourceFacet` / `detailWanted` 残留 current raw。

**Invariant：** 只要 `selectedContractId` 命中 ACTIVE contract，上述字段必须全部来自**同一条** contract entry。

### 6.2.4 resultAnchor 边界

* time-only follow-up（「上个月呢」）→ `mentionedDishName` 必须为空；排行 Top1 **不得**自动升级为主语。
* 仅 V2 结构化显式实体追问（菜名 + `IGNORE_PREVIOUS` / `OVERRIDE` 等）才可用 previous `resultAnchor`。

### 6.2.5 禁止补丁的位置（硬性）

以下位置**禁止**新增 per-domain / per-case 业务 if/else、`contains`、alias：

* `AiQuerySemanticSlotMerge` 及已 `@Deprecated` 的 `reconcile*FollowUpSlots`（**禁止恢复**）
* `SemanticAdoptionPipeline` 内的 previousTurn 业务 merge
* `previousTurnSummary` / `lastSemanticSlots` merge 逻辑
* `CurrentSemanticFrame` 中按域 reconcile 业务语义

正确修复路径：Prompt / Contract Catalog / Intake / Policy+Applier+Catalog 派生；不是 Java 猜语义。

### 6.2.6 必跑回归（改继承逻辑后）

单元 + 集成（`SemanticSlotInheritancePolicyTest`、`SemanticSlotInheritanceApplierIntegrationTest`）至少覆盖：

1. 菜单经营 → 上个月：完整 `menu.operation.overview.v1`，`sourceFacet` 正确，无 slot mismatch。
2. 销量高 → 上个月呢：完整 `dish_sales.count_ranking_high`，`mentionedDishName` 为空。
3. 今天买了什么 → 上个月呢：完整 `purchase.period_goods_list`，不降级 overview。
4. 销量高 → 昨天经营情况怎么样：保持 `business_overview.summary`，不继承 dish_sales。
5. 今天买了什么 → 昨天经营情况怎么样：保持 `business_overview.summary`，不继承 purchase。
6. 销量高 → 核桃芽菜西芹呢：显式实体追问走单菜路径，不恢复 ranking 整包。

链路：`Policy.decide → Applier.apply → SemanticContractCompletionEngine.complete`。

---

## 7. Scope / 权限范围原则

1. GROUP / STORE / PURCHASER 等 scope 只能影响权限和查询范围。

2. scope 可以影响：

    * visibleStores
    * queryStoreIds
    * queryDepartmentIds
    * expandedSqlDepartmentIds
    * SQL where 范围

3. scope 不允许影响：

    * selectedContractId
    * canonicalStructuredIntentDetailWire
    * answerPlanType
    * selectedTools
    * execution path

4. Matrix 如果保留 scope observed row / wire，只能作为 debug-only，不能驱动主链。

---

## 8. Debug / Harness 字段原则

1. Debug 字段不能伪装成主链字段。
2. 主链字段必须清楚表示 contract-owned。
3. Matrix / scope / observed 信息必须标记为 observed / debugOnly / scopeRemapped。
4. 不允许保留容易误导的旧字段，例如看起来像主链 wire 但实际来自 Matrix observed 的字段。
5. 如果 summary、SSE、runDebug 里存在旧字段污染，要一并清理输出路径。

---

## 9. 修改前必须判断问题层级

动代码前先判断问题属于哪一层：

* 阶段 0：LLM / Semantic Intake 基础设施失败
* 阶段 1：语义解析 / 合同选择 / anchor / time
* 阶段 2：Tool Request / SQL 入参
* 阶段 3：Tool / SQL 数据
* 阶段 4：AnswerPlan 举证
* 阶段 5：Composer 表达
* 阶段 6：前端展示 / SSE / Run Debug

不要跨阶段混改。

禁止：

* 不要因为 AnswerPlan 或 Tool 现象，回头改 wire。
* 不要因为语义问题，改 SQL。
* 不要因为 debug 字段误导，改主链业务。
* 不要因为一个 case 失败，就动已通过的 GROUP / STORE / DishSales / DishCost 主链。

---

## 10. 修改后必须汇报

每次完成后只汇报：

1. 本次问题属于哪一层。
2. 改动是否遵守 Java 不猜语义。
3. 是否新增 rawMessage / normalizedMessage / completedUserQuery 判断。
4. 是否新增 contains / regex / Pattern / 中文词表。
5. wire 的唯一主权来源是否仍是 selectedContractId → ACTIVE contract entry。
6. 时间是否仍遵守：显式时间由 V2 给日期，Java 只做默认、继承、校验。
7. 当前轮实体覆盖、上一轮 anchor 继承的边界是否清楚。
8. STORE / GROUP scope 是否只影响范围字段。
9. 是否删除或改名了误导性 debug 字段。
10. 是否发现其他同类隐患；如果发现，必须主动指出。

---

## 11. 禁止事项总表

禁止：

* 不写 rawMessage.contains
* 不写 normalizedMessage.contains
* 不写 completedUserQuery.contains
* 不写中文业务词表
* 不写 Pattern 正则判断业务语义
* 不写 alias
* 不新增 fallback
* 不让 Java 猜业务域、时间、菜名、指标、意图
* 不让 Java 根据 timeType 自动算日期
* 不让 Java 根据 time.reason 中文文本校正协议字段
* 不让 LLM wire 抢主链
* 不让 Matrix / SlotMerge / Repair / Canonicalize 改写主链 wire
* 不保留两套 wire 主权
* 不为了单个 case 打补丁
* 不跑测试；测试由用户本地执行

如果必须处理语义不明确的情况，优先返回 clarification / known gap，而不是 Java 猜。

如果当前问题需要动已稳定主链，请先停止修改并汇报，不要直接改。

---

## 12. 单一主权与单一投影原则

修改 AnswerPlan / Card / Composer / Wire 链路时，**同一个业务决定只能有一个权威来源和一个决策入口**。下游节点只消费结果，不得重新判断、补挂、替换、修复或改变业务含义。

### 12.1 单一主权（SSOT）

每类业务信息必须明确唯一主权字段或对象：

| 决定 | 主权 |
|------|------|
| 语义能力 | `selectedContractId` 命中的 ACTIVE Contract |
| wire / AnswerPlanType / Tool / 执行模式 | Contract 派生（contract-locked frame） |
| 卡片类型 | 最终 `AnswerPlan.planType`（或该域独立 AnswerPlan 的 `planType`） |
| 时间 | Time Layer（`timeSource` / `effectiveTimeWindowSource` 等，见 time-layer 规则） |
| 组织 / 权限范围 | `AiResolvedQueryContext` 中 scope / dataScope |

禁止多个字段**并行抢占同一决定**，例如同时读取 `contractId`、`wire`、`executionIntent`、raw LLM 字段、关键词、已有 `cardType`，再分别推导同一 CardType 或执行路径。

### 12.2 单一决策入口

同一业务映射只允许**一个**统一入口，例如：

`AnswerPlan.planType → CardType`（采购域参考：`PurchaseAnswerPlanCardSupport` → `PurchaseAnswerPlanCardWireService.attachCardsIfApplicable`）

后续 Service、Composer、`AiCardPayloadWireSupport.refresh` 等**只能消费**该决策结果，不得再次做 PlanType / executionIntent / wire / contract 判断来决定卡片。

禁止在多个位置重复添加：PlanType 判断、executionIntent 判断、wire 判断、contract 判断、early-return、suppress 条件、卡片替换逻辑。

### 12.3 禁止后置修复

禁止用以下方式掩盖上游设计问题：

* 漏挂后补挂（reconcile）
* 错卡生成后替换
* refresh 阶段重新选择卡片类型
* Composer 根据 cards / Tool 原始结果重新改业务类型或从全量数据筛选实体
* fallback 到另一套旧逻辑
* 为单个 case 增加 if/else、`contains`、alias 或关键词判断

上游产生非法状态时，应在**权威边界 fail closed**（澄清、known gap、明确 noDataReason），不得由下游偷偷修正业务含义。

**允许**：协议归一化（字段名、deprecated `cardPayload` 镜像、title 补齐）——这是 wire 格式，不是业务重决策。

### 12.4 Composer 职责边界

Composer **只负责表达**已确定的事实：

* 消费 AnswerPlan
* 消费已生成的 cards
* 生成自然语言说明（可据 `planType` 选择短引导话术，**不得**据此补挂或替换卡片）

Composer **不负责**：选合同、决定执行模式、决定卡片类型、修改事实、补挂/替换卡片、从 Tool 原始结果重新拼业务答案。

### 12.5 新能力接入

新增能力前先检查现有 Contract / Tool / AnswerPlan / CardType / Projection / Composer，确认是**映射缺失**还是系统确实缺少正式能力。

不得在未梳理链路前直接新增重复 Contract、PlanType、CardType 或 WireService。

### 12.6 旧逻辑清理

新统一入口替代旧逻辑后，必须从主链**删除或摘掉**：

* 重复判断
* 旧投影入口
* 后置 reconcile
* fallback / 兼容分支
* 已失效 helper
* Composer 中的业务跳过逻辑

不能长期保留两套决策链路作为「保险」。

Harness 只读探针（mirror debug）可保留，但**不得**参与挂载或互斥决策。

### 12.7 修改流程

1. 定位当前业务决定的真正主权入口
2. 检查整条链路是否存在重复决策
3. 在统一入口解决问题
4. 删除被替代的旧逻辑
5. 检查同类能力是否存在相同隐患
6. 不针对单个测试问法写补丁
7. 汇报：根因、唯一主权入口、修改范围、删除的重复逻辑
