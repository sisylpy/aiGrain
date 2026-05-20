# 阶段 1 总收口：语义主链验收摘要

> **状态**：阶段 1 语义层验收已通过（2026-05-19）；**D-CLEAN-V1-SEMANTIC-LEGACY-FINAL（2026-05-20）** 确认生产 **V2-only**（`semantic.query_parser.v2` + `semanticSlots` / `structuredIntentDetailWire`）。  
> **定位**：阶段 1 的**总边界 + 已通过 case + 架构原则 + 摘链清单 + 技术债 + 阶段 2 入口**；不替代各子矩阵的细节 expected 表。  
> **相关索引**：[`purchase-v2-semantic-followup-phase1-summary.md`](./purchase-v2-semantic-followup-phase1-summary.md)（1A）、[`business-phase1b-semantic-harness-matrix.md`](./business-phase1b-semantic-harness-matrix.md)（1B）、[`stock-reduce-phase1c-semantic-harness-matrix.md`](./stock-reduce-phase1c-semantic-harness-matrix.md)（1C）、[`d1x-v2-only-time-source-cleanup-inventory.md`](./d1x-v2-only-time-source-cleanup-inventory.md)、[`d1x-rankingtype-and-duplicate-responsibility-inventory.md`](./d1x-rankingtype-and-duplicate-responsibility-inventory.md)、[`AI_HARNESS_REPLAY_CASES.md`](../AI_HARNESS_REPLAY_CASES.md)。

---

## 1. 阶段 1 验收边界

阶段 1 只验 **语义主链**：从 V2 解析 JSON → 槽位合并 / 帧校验 → Resolver → `AiResolvedQueryContext` 摘要（Harness `RESOLVED_CONTEXT_ONLY` 或 Graph 上的语义探针）。

### 1.1 在范围内

| 维度 | 典型断言对象 |
|------|----------------|
| **intent / path** | `effectiveIntentCode`、`effectivePathCode` |
| **wire** | `structuredIntentDetail`、`canonicalStructuredIntentDetailWire`（Lexicon canonical） |
| **semanticSlots** | `queryObject`、`operation`、`metric`、`sourceFacet`、`anchorPolicy`、`detailWanted`、`structuredIntentDetailWire` |
| **time** | `timeAction`、`time.timeSource`、`effectiveTimeWindowSource`、`startDate` / `endDate` |
| **scope** | `requestedScopeType`、点名门店、`querySemanticEffectiveMentionedStoreNames`、多店 harness 探针 |
| **follow-up** | 多轮继承 / 域切换、`followUpResolution` 类探针（不含已摘链的 `TIME_SHIFT` 生产路径） |
| **anchor** | `anchorPolicy`、`resultAnchorsSummary`、Registry `matchedCapabilityId`（采购 1A） |
| **编排语义** | `orchestrationTaskMode` 等 v2 候选被 Resolver 采纳的摘要字段（**语义层**，非 Tool 执行结果） |

推荐 Harness 配置：`dryRunStage=RESOLVED_CONTEXT_ONLY`（1B / 1C）；固定 `frozenClockDate`；`strictStoreSqlMatch=false`（多店环境差异时）。

### 1.2 明确不在范围内

- 业务 **Tool** 返回行、payload 是否为空、行数是否「业务正确」
- 任意业务数据 **SQL**、`queryStoreIds` 展开是否与生产库一致（除 harness 探针级 scope 外）
- **`AnswerPlan`** 行集、`focusRows`、排序键、计划内聚合重算
- **`Composer`** 文案、前台展示、用户可见话术结构
- **前端** 消费协议与 UI 行为

阶段 2 起再进入「查什么参数 / 用什么 SQL」；阶段 3+ 再进入 AnswerPlan 事实选择与 Composer 表达（见 §6）。

---

## 2. 已完成 case（overallPass=true）

下列 case 已在阶段 1 收口回归中 **全部通过**（2026-05-19 记录）。

| 子阶段 | `caseId` | 模式 | 轮次 / 说明 |
|--------|----------|------|-------------|
| **采购 1A follow-up** | 专项 Harness case 族（非单一 `caseId`） | Resolver + Graph 探针 | 供货商排行 → 时间接力；商品排行 → 来源拆桶 / 单价 drilldown；营业额 →「那采购呢」域切换；渠道 overview → 商品明细；见 [`purchase-v2-semantic-followup-phase1-summary.md`](./purchase-v2-semantic-followup-phase1-summary.md) §2 |
| **采购单域 Graph** | **`PURCHASE_AGENT_GRAPH_CORE`** | `GRAPH_RUN`（语义 + 图探针） | 3 轮：集团采购 → 上个月 → AAA 单店；验 `PURCHASE_OVERVIEW` / `purchase_overview_path` 与采购语义不断链 |
| **经营 1B** | **`BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT`** | `RESOLVED_CONTEXT_ONLY` | 13 轮：经营概览 / 诊断 / 营业额 / 多店对比 / 时间继承 / 跨域接力（R08–R10）；矩阵见 1B 文档 |
| **出库 1C** | **`STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT`** | `RESOLVED_CONTEXT_ONLY` | 18 轮：出库子口径 wire、排行、多店、多轮继承、采购+出库双域风险（R14–R15）；矩阵见 1C 文档 |

**并行参考（阶段 1 语义主链，非本表「四类收口」必跑项）**：

- **`V2_SEMANTIC_MAINLINE_CORE_10`** — 十条真实问句，Resolver-only 主语义回归
- **`PURCHASE_AGENT_GRAPH_CORE`** 与 1A 专项 case 互补：Graph 验证采购单域 Tool 调度 + 语义探针

---

## 3. 已确立架构原则

阶段 1 实施与 Harness 验收均默认下列原则；后续阶段不得 silently 违背。

| # | 原则 | 说明 |
|---|------|------|
| 1 | **V2-only** | 生产仅 **`query_semantic_parser.v2.md`**（`semantic.query_parser.v2`）；**Historical removed**：`query_semantic_parser.v1.md`、`SEMANTIC_QUERY_PARSER_V1`、Resolver 内 legacy Normalizer / DishProfitGate |
| 2 | **semanticSlots / canonicalStructuredIntentDetailWire 优先** | 业务子口径以 `semanticSlots.structuredIntentDetailWire` → Lexicon canonical → `queryIntent.structuredIntentDetail` 为主链 |
| 3 | **当前轮显式语义 > previousTurn 继承** | 本轮 JSON 已给出的槽位 / wire 权威；继承仅补缺失 |
| 4 | **当前轮显式时间 > previousTurn 时间** | 时间由 V2 `time.timeType` / `timeAction` / `timeSource` + **`SemanticTimeContractCheck`** 落地；**Historical removed**：`AiQuerySemanticTimeLexicon`、`AiMultiTurnTimeWindowPolicy` |
| 5 | **业务域不解析月份** | 「上个月 / 本月」由 V2 结构化时间 + 多轮策略落地；不在采购 / 经营 / 出库 AnswerPlan 或 Tool 层各写一套 |
| 6 | **previousTurn 只能补缺失，不能覆盖当前轮明确语义** | 含 slots 空字段继承（`lastSemanticSlots` pick-prefer-current）；D-1X-C 已删除 wire→slot 反推与 previousTurn 补 wire |
| 7 | **metric.rankingType 只能 compat / debug** | 不得覆盖**本轮 LLM JSON 显式给出**的 `semanticSlots.structuredIntentDetailWire`（D-1X-D1：`currentTurnStructuredIntentDetailWire` 快照）；inherit 回填的 wire 不计入「本轮明确 slots」 |
| 8 | **Tool 查数，AnswerPlan 选事实，Composer 只表达** | 阶段 1 只验前几层语义；不在此阶段要求 Tool 行集或 Composer 文案正确 |

---

## 4. 已完成摘链（阶段 1 内）

| 项 | 摘要 | 参考 |
|----|------|------|
| **TIME_SHIFT 摘链** | 生产路径不再产出 `followUpType=TIME_SHIFT`；纯日历追问走 Lexicon + 多轮时间策略 | [`d1x-v2-only-time-source-cleanup-inventory.md`](./d1x-v2-only-time-source-cleanup-inventory.md) §4、D-1X-A |
| **Harness timeSource 双轨统一** | v2 `time.timeSource` 与 `effectiveTimeWindowSource` 摘要口径对齐，减少「解析 JSON vs Resolver 摘要」双轨漂移 | 同上 §5；1B / 1C 矩阵 time 列 |
| **Java V1 死入口摘链** | Resolver / Merge 不再走 V1-only 语义入口 | PR-C1；inventory §5 |
| **V1 文档 / prompt 引用清理** | v1 prompt 归档 DEPRECATED；对外 schema / 文档 V2-only | PR-C2；`semantic-output-schema.md` |
| **D-1X-D1：rankingType 不抢权** | 本轮 JSON 显式 `structuredIntentDetailWire` 存在且可 canonical 时，`metric.rankingType` 不得写 `queryIntent.structuredIntentDetail` 或覆盖 slots；**无本轮 wire 时**保留 store/revenue/compare / 双域 risk 等合法 fallback | [`d1x-rankingtype-and-duplicate-responsibility-inventory.md`](./d1x-rankingtype-and-duplicate-responsibility-inventory.md) §5 D1；`AiQuerySemanticParseResult#currentTurnStructuredIntentDetailWire` |

**D-1X-D1 回归（已通过）**：

- `PURCHASE_AGENT_GRAPH_CORE` — overallPass=true  
- `BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT` — overallPass=true  
- `STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT` — overallPass=true  

---

## 5. 仍保留技术债（阶段 2 前不阻塞语义主链）

| 编号 | 项 | 说明 |
|------|-----|------|
| **D-1X-D2** | broad overview guard 公共 policy 收敛 | 经营 / 营业额 broad 防 ranking 污染逻辑分散在 SlotMerge / MergeHelper；待抽统一 policy，**禁止** case 级中文关键词 if |
| **D-1X-D3** | AnswerPlan / Composer / Harness 层 rankingType 主口径收口 | **已完成（2026-05-20）**：`DishSalesAnswerPlanBuilder`、库房 Composer/Renderer、`AiHarnessResolvedContextSummarizer.rawStructuredIntentDetail` 不再以 rankingType 定 wire/planType |
| **D-1X-D4** | Harness / 文档同步 | inventory 清单与矩阵文档随 D2/D3 增量更新；探针命名与 `*AnyOf` 策略统一 |
| — | **purchaseSourceType vs sourceFacet** | 双字段并存、互校准与帧校验边界；采购域以 `sourceFacet` + frame 为主，metric 侧仍 compat |
| — | **stockReduceType vs structuredIntentDetailWire** | 出库 facet 与 canonical wire 多写口；1C 矩阵已文档化，代码层待 D2 收敛 |
| — | **validator / fallback / followUp 职责边界** | `CurrentSemanticFrameValidator`、FollowUp hydrator、MergeHelper fallback 三角职责待一张职责图 |
| — | **`replay-single-case.sh` 增强** | 支持只传 `caseId` 自动加载 `AiHarnessBuiltinCases` 内置 `messages`（今日仍需手动对齐 messages 或走服务端 caseId 默认） |

---

## 6. 下一阶段：阶段 2 — Tool Request / SQL 入参层

**目标**：证明系统**准备用什么参数去查**，而不是查出来的数据是否正确，也不是最终话术。

| 在范围内（阶段 2） | 仍不在范围内 |
|---------------------|--------------|
| Tool 请求 DTO / 入参组装（时间窗、scope、source、子口径 wire 映射到 API 字段） | 返回行数值是否正确、SQL 性能 |
| SQL 或 Mapper **参数绑定**快照（department_id 列表、日期闭区间、排序键意图） | AnswerPlan 行集业务含义、Composer 文案 |
| Harness 探针：`plannedToolArgs`、`sqlParam*`、scope 展开是否与 Resolver 一致 | 前端展示 |

**建议入口文档（待建）**：阶段 2 矩阵可沿 1A / 1B / 1C 域划分，复用相同 `caseId` 消息序，将 `dryRunStage` 扩展到「Tool 计划层」或轻量 Graph dry-run；具体 case 清单在阶段 2 启动时另文定义。

**阶段 1 → 阶段 2  handoff**：当前 Resolver 已稳定的 `effectiveIntentCode` / `effectivePathCode` / canonical wire / semanticSlots / time / scope 为阶段 2 的**唯一上游**；阶段 2 不得新增与 wire 平行的 rankingType 写口。

---

## 7. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-19 | 初版：阶段 1 总收口；四类 case overallPass；架构原则；摘链清单；D2–D4 技术债；阶段 2 入口。 |
