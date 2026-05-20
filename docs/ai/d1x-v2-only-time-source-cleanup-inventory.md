# D-1X：V2-only 与统一时间 / timeSource 旧逻辑摘链清单

> **D-CLEAN-V1-SEMANTIC-LEGACY-FINAL（2026-05-20）**：生产语义 **V2-only**。  
> **Historical removed**：`query_semantic_parser.v1`、`AiQuerySemanticTimeLexicon.java`（`src/main` 无此类）。  
> **现网时间主链**：V2 `time` / `timeAction` → **`SemanticTimeContractCheck`** → Resolver 写 `timeWindow` + `effectiveTimeWindowSource`；合同失败 → 澄清。  
> **D-1X-D3-RANKINGTYPE-FINAL（2026-05-20）**：`metric.rankingType` 不再作主 wire；见 [`d1x-rankingtype-and-duplicate-responsibility-inventory.md`](./d1x-rankingtype-and-duplicate-responsibility-inventory.md) **现网契约**。  
> 下文 §2–§4 对 Lexicon「唯一扩展点」的描述为 **Historical inventory（实施前盘点）**，勿当作现网扩展指南。

本文档沉淀 **D-1X 梳理结论**（只读盘点，**不含实施**）。  
目标：在 **不扩大业务域时间 if/else**、不碰 Tool / SQL / AnswerPlan / Composer 的前提下，明确 **何处应删、何处应保留、何处分阶段摘链**。

**状态：** PR-C1（Java V1 摘链）与 **PR-C2（prompt / schema 文档 V2-only）** 已完成（2026-05-19）；Lexicon 源文件已在 D-CLEAN 删除。

**相关矩阵（已收口，勿回退）：**

- 阶段 1A：采购 V2 semantic follow-up — `docs/ai/purchase-v2-semantic-followup-phase1-summary.md`
- 阶段 1B：经营 semantic — `docs/ai/business-phase1b-semantic-harness-matrix.md`
- 阶段 1C：出库 semantic — `docs/ai/stock-reduce-phase1c-semantic-harness-matrix.md`（§8 收口口径）

---

## §1 统一时间主链路（生产）

用户自然语言时间 **不得** 在采购 / 出库 / 经营 / 菜品等业务域各自解析。生产主链路固定为：

```text
用户原话（normalizedUserMessage）
  → [L1] V2 LLM（query_semantic_parser.v2.md）  ← 唯一语义 prompt（Historical removed：v1）
         产出 timeAction + time.{ timeType, timeSource, needInheritFromPrevious, startDate, endDate }
  → [L2] AiQuerySemanticParseResultJsonParser.fromJsonObject
  → [L3] AiQuerySemanticLlmMergeHelper.mergeTentativeTime  ← 候选窗镜像（不读用户话术关键词）
  → [L4] SemanticTimeContractCheck.check  ← 结构自洽；PASS 采用 LLM startDate/endDate/timeSource
  → [L5] AiResolvedQueryContextResolver  ← FAIL → clarification；PASS → ctx.timeWindow + effectiveTimeWindowSource
  → [L6] 业务 Tool / AnswerPlan / Composer / Graph
         只读 ctx.timeWindow（startDate/endDate/timeLabel）与 effectiveTimeWindowSource
```

### 1.1 各层职责与关键类

| 层 | 类 / 文件 | 职责 | 主链路 |
|----|-----------|------|--------|
| L1 | `AiQuerySemanticLlmParser#parse(SemanticParserInput)` | 唯一语义解析入口（V2 prompt） | ✅ |
| L1 | `SemanticParserInputBuilder` / `SemanticParserPreviousTurn` | 上轮 timeLabel/startDate/endDate 喂给 LLM | ✅ |
| L2 | `AiQuerySemanticParseResultJsonParser` | 解析 JSON → `TimePart` + `timeAction` | ✅ |
| L3 | `AiQuerySemanticLlmMergeHelper#mergeTentativeTime` | 合并 V2 `time` 为候选窗（**不**读 TimeLexicon） | ✅ 候选 |
| L4 | `SemanticTimeContractCheck#check` | 校验 LLM `time` 块自洽；PASS 为权威落地 | ✅ |
| L5 | `AiResolvedQueryContextResolver#resolve` | 合同 PASS → 写 ctx；FAIL → 澄清 | ✅ |
| L6 | `BusinessTimeWindowNode` | 镜像 statStart/End，**不重算**用户话术 | ✅ 消费 |
| L6 | `AiTimeWindowTextFormatter` | ISO 窗 → 展示文案（「上个月」「本月至今」） | ✅ 消费 |
| — | ~~`AiQuerySemanticTimeLexicon`~~ | **Historical removed** | — |

### 1.2 Resolver 显式约定

`AiResolvedQueryContextResolver` 中 **`explicitTentative = null`**（注释：显式时间仅来自语义 LLM / 多轮合并，不再对用户话术做关键词解析）。  
**Historical（Pre-C1）**：曾约定口语信号经 TimeLexicon 进入 Merge；**现网** 仅 V2 LLM 结构化 `time` / `timeAction`，Resolver **不对**用户话术做关键词解析。

### 1.3 优先级（当前轮 > 继承 > 默认）

1. 当前轮 V2 显式 `time` / `timeSource= CURRENT_MESSAGE`（合同 PASS）
2. `previousTurn` 继承（LLM `timeSource=INHERITED_PREVIOUS` + 合同校验 dates 与 memory 一致）
3. 系统默认（LLM `timeSource=DEFAULT_MONTH_TO_DATE` + 合同 PASS）

业务域 **只读** `AiResolvedQueryContext.timeWindow`，不得重新解释「这个月 / 上个月」。

---

## §2 AiQuerySemanticTimeLexicon（Historical removed — 盘点归档）

> **现网**：`src/main` **无** `AiQuerySemanticTimeLexicon`、**无** `AiMultiTurnTimeWindowPolicy`；**禁止** 新建此类或恢复「用户话术 → timeType」Java 关键词路径。时间仅 V2 JSON + **`SemanticTimeContractCheck`**。

### 2.1 定义（Historical）

`com.nongxinle.ai.semantic.AiQuerySemanticTimeLexicon` 集中维护与 v2 prompt「明确时间词」对齐的口语片段，并提供：

| API | 用途 |
|-----|------|
| `explicitCurrentMonthMentioned` | 这个月 / 本月 / 当前月 / 本月至今 |
| `inferSemanticTimeTypeFromUtterance` | 口语 → `THIS_MONTH` / `LAST_MONTH` / `TODAY` / … |
| `explicitCalendarTimeMentioned` | 是否含可识别日历时间 |
| `explicitCalendarTimeOnlyUtterance` | 整句是否**仅**表达日历切换（如「那上个月呢」） |
| `explicitYTDOrYearRangeMentioned` | 今年 / YTD 等 |

### 2.2 当前调用点（生产 + 允许的 follow-up）

| 调用方 | 方法 | 说明 |
|--------|------|------|
| `AiQuerySemanticLlmMergeHelper` | `tryResolveUtteranceExplicitCalendarTime` | **最先**执行：口语优先于 inherit defer |
| 同上 | `shouldDeferSemanticThisMonthPlaceholderToPreviousTurn` | 无当前月口语时，拒信误标 OVERRIDE+THIS_MONTH |
| 同上 | `samePathScopeOrMetricOverrideInheritsPreviousCalendar` | 同 path 换 scope/metric 继承日历；YTD/当前月 bypass |
| `PurchaseFollowUpSlotSignals` | `shouldSkipObjectDrilldownForTimeOnly` | 纯日历句 → 跳过采购对象下钻（**不**复制 regex） |
| `AiHarnessResolvedContextSummarizer` | `deriveTimeOverrideReason` | **Harness debug only** |

### 2.3 扩展规则

- 新增「今天 / 本周 / 上季度 / 去年同期」等口语 → **只扩** `AiQuerySemanticTimeLexicon`（或经评审后同包 `TimeUtteranceSignals`）。
- **禁止**在 Agent / Tool / SQL / Composer / 各业务 Merge 中新增 `contains("这个月")` 类判断。
- 仓库 `src/main` 中文月份 contains 经盘点 **仅** 存在于 `AiQuerySemanticTimeLexicon`（2026-05-19）。

工程约束亦见：`docs/HARNESS_ORCHESTRATION_DECISION.md` §3.3、`docs/TODO_MULTI_AGENT.md`。

---

## §3 业务域不得解析「这个月 / 上个月」

### 3.1 允许

- 读取 `ctx.getTimeWindow().getStartDate()` / `getEndDate()` / `getTimeLabel()`
- 读取 `ctx.getEffectiveTimeWindowSource()` 写边界说明（如 `AnswerBoundaryNoteComposer`）
- 用 `AiTimeWindowTextFormatter` 将 **已落地 ISO 窗** 格式化为用户可见文案

### 3.2 禁止

- 在 `Purchase*` / `StockReduce*` / `Business*` / `Dish*` 模块对用户原话做月份 regex
- 在 SQL 或 Tool 入参层绕过 `resolvedQueryContext.orgScope` / `timeWindow` 自行解释自然语言时间
- 为通过某个 replay case 在业务域补 `if (msg.contains("上个月"))`

### 3.3 多轮时间争议时的排查顺序

1. V2 raw `timeAction` / `time.timeType` / `time.timeSource` / `needInheritFromPrevious` 是否正确
2. `SemanticTimeContractCheck` 是否因缺字段 / source 冲突 / 日期不一致 FAIL
3. Resolver 是否在合同 PASS 后直接采用 `normalizedTimeSource`（**禁止** Java `tentativeTime != null` 推导 `CURRENT_MESSAGE_EXPLICIT`）
4. Harness expectation 是否过度绑定 `effectiveTimeWindowSource`（见 §6）

---

## §4 TIME_SHIFT：已无生产者，只剩死分支 / 注释

### 4.1 背景

历史 Java keyword follow-up 曾使用 `followUpType = TIME_SHIFT`。  
当前主链路 follow-up 已统一为 **`AiFollowUpResolver.semanticStructuralBypassResolution`** → `followUpType = SEMANTIC_STRUCTURAL_MERGE`，**不再** 由 Java 关键字判定时间切换。

### 4.2 盘点：无生产者

全仓库 **无** `setFollowUpType("TIME_SHIFT")` 或等价赋值。  
`AiFollowUpResolver` 仅设置：

- `SEMANTIC_STRUCTURAL_MERGE`
- `NEED_SEMANTIC_CLARIFICATION`
- （Resolver 内个别 scope 扩展）`GROUP_SCOPE_EXPAND_FOLLOW_UP` 等

### 4.3 盘点：剩余消费者 / 引用

| 位置 | 性质 | D-1X 建议 |
|------|------|-----------|
| `PurchaseFollowUpSlotSignals#shouldSkipObjectDrilldownForTimeOnly` L175 | 若 `TIME_SHIFT` 则 skip 下钻 | **摘链**（阶段 D-1X-A）；保留 Lexicon 纯日历分支 |
| `AiResolvedQueryContextResolver#stabilizeDishProfitFollowUpStructuredIntent` L2464 | 允许 TIME_SHIFT 稳定菜品子意图 | **摘链或标 deprecated**（D-1X-A） |
| 同上注释 L2780 | 文档性提及 | 更新注释 |
| `AiFollowUpResolution` 字段注释 L22 | 枚举示例 | 更新文档 |
| Harness expectation | **无** `followUpTypeExpected` / TIME_SHIFT 断言 | 无需改 case |

纯时间追问的现行判定应走：**V2 `timeAction` + `AiQuerySemanticTimeLexicon.explicitCalendarTimeOnlyUtterance`**（采购门控已部分如此）。

---

## §5 V1 runtime / prompt / debug 摘链清单

### 5.1 原则

- **生产语义仅 V2**；V1 不得 fallback 抢 V2。
- **字段 / 枚举契约**以 **`semantic-output-schema.md`** 为唯一 prompt 侧说明；v2 prompt 只引用 schema + 本文档 §1 时间落地链，**不**再引用 v1.md 作 runtime 契约。

### 5.2 已摘链（PR-C1，2026-05-19）

| 项 | 位置 | 结果 |
|----|------|------|
| V1 解析入口 | `AiQuerySemanticLlmParser#parseUserQuestion` | **已删除** |
| V1 prompt 注册 | `AiPromptRegistry` / `AiPromptIds.SEMANTIC_QUERY_PARSER_V1` | **已注销** |
| V1 fallback 逻辑 | `AiResolvedQueryContextResolver#preferReadableSemantic(v2, v1)` | **已删除 v1 参数与分支** |
| V1 debug 字段 | `AiResolvedQueryContext.querySemanticV1` | **已删除** |
| V1 debug 开关 | `querySemanticV1DebugWhenV2Adopted` | **已删除** |
| Harness v1 摘要 | `AiHarnessResolvedContextSummarizer` `querySemanticV1` | **已删除** |

**验收（PR-C1）：** `BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT`、`STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT`、`PURCHASE_AGENT_GRAPH_CORE` overallPass=true。

### 5.3 文档 V2-only（PR-C2，2026-05-19）

| 项 | 说明 | 结果 |
|----|------|------|
| **`semantic-output-schema.md`** | V2 输出 JSON 字段 / 枚举 / D-13 `semanticSlots` / `time` 契约；替代 v1.md 作字段说明 | **新增** `src/main/resources/ai-prompts/semantic/semantic-output-schema.md` |
| **`query_semantic_parser.v2.md`** | 移除「与 v1 对齐 / 见 v1.md / v1 约定」等 runtime 误导表述；改指 schema + `AiResolvedTimeWindow` / canonical wire | **已更新** |
| **`query_semantic_parser.v1.md`** | **D-CLEAN-V1 已从 `src/main/resources/ai-prompts/semantic/` 删除**；契约见 v2 + `semantic-output-schema.md` | **已删除** |

**不在 PR-C2 范围：** `metric.rankingType` 降级（D-1X-D）；Harness timeSource reconcile（D-1X-B）；Java 代码。

### 5.4 主链路确认

`AiResolvedQueryContextResolver` 仅调用 `querySemanticLlmParser.parse(v2Input)`；**无** V1 参与 adopt/merge/finalize。

---

## §6 Harness reconcile 与生产 source 双轨问题

### 6.1 三套 timeSource 命名空间

| 命名空间 | 典型取值 | 写入方 |
|----------|----------|--------|
| v2 `time.timeSource` | `CURRENT_MESSAGE`, `INHERITED_PREVIOUS` | LLM |
| 生产 `effectiveTimeWindowSource` | `CURRENT_MESSAGE_EXPLICIT`, `INHERITED_PREVIOUS`, `DEFAULT_MONTH_TO_DATE` | `SemanticTimeContractCheck.Result#normalizedTimeSource`（经 Resolver 写入 ctx） |
| Harness 摘要 `effectiveTimeWindowSource` | 可能被 **改写** | `AiHarnessResolvedContextSummarizer#reconcileEffectiveTimeWindowSourceForHarness` |

### 6.2 生产链路（权威）

```text
SemanticTimeContractCheck.check(sem, previousTurn)
  → PASS: Result.toTimeWindow + normalizedTimeSource → ctx
  → FAIL: clarificationRequired → effectiveTimeWindowSource=UNRESOLVED
```

`AiFollowUpResolver.fillSources` 内亦有一套 preliminary time source（L104-112），但 Resolver 会以 **`SemanticTimeContractCheck`** 结果 **覆盖** followUp 上的 effective source。双写属于 **冗余**，D-1X-B 可摘链 fillSources 时间分支。

### 6.3 Harness reconcile 行为（非生产）

`reconcileEffectiveTimeWindowSourceForHarness`（Summarizer）在 **不改** `AiResolvedTimeWindow` 的前提下：

- 若 `semanticV2StructuredTimeInheritsPrevious(ctx)` 且日期与上一轮一致 → 强制 `INHERITED_PREVIOUS`
- 若显式窗但 declared 为 `DEFAULT_MONTH_TO_DATE` → 升为 `SEMANTIC_EXPLICIT`
- 若日期与 previousTurn 一致 → 升为 `INHERITED_PREVIOUS`

`semanticV2StructuredTimeInheritsPrevious` 依据：`querySemanticV2TimeAction == INHERIT_PREVIOUS` 或 `needInheritFromPrevious` 或 `time.timeSource == INHERITED_PREVIOUS`。

### 6.4 问题与 1C 收口关系

- **日期正确、source 标签不一致** 曾导致 1C replay 失败 → 1C 已通过 **放宽 Harness expectation（AnyOf）** 收口，**未**改 merge/policy（见 `stock-reduce-phase1c-semantic-harness-matrix.md` §8）。
- 根因是 **Harness 展示口径 ≠ 生产 ctx 口径**，而非出库 wire 错误。
- D-1X-B 目标：要么 reconcile **下沉合并** 到 Policy 单点，要么 reconcile **降级为纯 debug 字段**（如 `harnessEffectiveTimeWindowSourceReconciled`），**不再** 作为 comparator 默认 actual。

### 6.5 ctx 上的 canonical timeAction

Resolver 写入 `querySemanticV2TimeAction` = `AiQuerySemanticLlmMergeHelper.canonicalQuerySemanticV2TimeActionForHarness(...)`，与 Summarizer 内 `canonicalTimeActionForHarness` **逻辑相近**，存在重复维护风险 — D-1X-B 一并收敛。

---

## §7 metric.rankingType 与 semanticSlots 双通道风险

### 7.1 原则（架构硬性）

- **主语义依据：** `semanticSlots`、`canonicalStructuredIntentDetailWire`、`intent` / `path` / `operation` / `metric` / `sourceFacet` / `anchorPolicy`
- **`metric.rankingType`：** 解析 JSON 遗留 facet；**不得** 在 semanticSlots 已明确 wire 时覆盖子口径

采购域已在 `AiQuerySemanticSlotMerge` / `CurrentSemanticFrameValidator` 方向收口；出库 1C 亦明确 **`ALL` 不得覆盖排行 wire**（见 1C 矩阵 §3）。

### 7.2 rankingType 抢权（**D-1X-D3 已完成 — Historical**）

| 位置 | 状态 |
|------|------|
| Merge rankingType remap（`applyMetricStructuredWire` 等） | **Historical removed** |
| `DishSalesAnswerPlanBuilder` rankingType fallback | **D-1X-D3 已删** |
| Composer/Renderer rankingType fallback | **D-CLEAN-RENDERER-FALLBACK-FINAL 已删** |
| Harness `rawStructuredIntentDetail` | **D-1X-D3**：改读 slots / `currentTurnStructuredIntentDetailWire` |

### 7.3 与时间的交叉（仅观测）

`samePathScopeOrMetricOverrideInheritsPreviousCalendar` 在 **metricAction=OVERRIDE** 时可能继承日历 — 这是 **time merge** 规则，不是 rankingType 写时间；D-1X-D 不改此行为，除非 slots 已表达显式新 timeAction。

---

## §8 分阶段实施记录（Historical roadmap — 非待办）

> **现网**：**D-1X-C（V2-only）** 与 **D-1X-D（rankingType 降级）** 已完成。下文 **D-1X-A / B** 为可选后续；**勿** 恢复 TimeLexicon 或 v1 prompt。

### D-1X-A：TIME_SHIFT 摘链（可选 / 未单独 PR）

**范围：**

- 删除 / deprecated：`PurchaseFollowUpSlotSignals` 对 `TIME_SHIFT` 的分支
- 删除 / deprecated：`stabilizeDishProfitFollowUpStructuredIntent` 中 `TIME_SHIFT` 条件
- 更新 `AiFollowUpResolution` 注释与相关 doc 字符串

**不在范围：** 恢复 Java keyword follow-up；不改 mergeTentativeTime。

**验收：** grep 无生产路径依赖 `TIME_SHIFT`；采购纯日历追问靠 **V2 `time` / `timeAction`**（**Historical removed**：TimeLexicon）；1A purchase replay 不退化。

---

### D-1X-B：Harness timeSource 口径统一

**范围：**

- 明确 **生产权威**：`SemanticTimeContractCheck.Result#normalizedTimeSource` 即 ctx 与 comparator 默认 actual
- `reconcileEffectiveTimeWindowSourceForHarness`：**降级为 debug** 或合并进 Policy（二选一，PR 内定案）
- 收敛 `canonicalQuerySemanticV2TimeActionForHarness` 与 Summarizer 重复逻辑
- 老 harness case：逐步将硬绑 `setEffectiveTimeWindowSource(...)` 改为 **AnyOf + startDate/endDate 锚定**（参考 1B/1C）

**不在范围：** 为出库/采购/经营改 merge 规则；**不** 新增 Java 关键词时间解析（**Historical removed**：TimeLexicon）。

**验收：** 1B + 1C + 采购 core replay；日期断言稳定；source 断言与生产 ctx 一致或可 documented AnyOf。

---

### D-1X-C：V1 摘链 — **已完成（PR-C1 + PR-C2）**

**PR-C1（Java）** — 见 §5.2。

**PR-C2（文档 / prompt）** — 见 §5.3。

**不在范围（已完成阶段仍遵守）：** v1 fallback 重新启用；维护 v1 新业务规则；`metric.rankingType` 降级（→ D-1X-D）。

**后续验收（可选）：** grep 无生产调用 V1 parser（PR-C1 已满足）；v2 prompt 无 v1.md runtime 引用（PR-C2 已满足）。

---

### D-1X-D：metric.rankingType 降级 — **已完成（D-1X-D3-RANKINGTYPE-FINAL，2026-05-20）**

见 [`d1x-rankingtype-and-duplicate-responsibility-inventory.md`](./d1x-rankingtype-and-duplicate-responsibility-inventory.md) **现网契约**。

---

## §9 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-19 | 初稿：D-1X 梳理文档化。 |
| 2026-05-19 | **PR-C1** Java V1 摘链完成；1B/1C/采购 core replay PASS。 |
| 2026-05-19 | **PR-C2** 新增 `semantic-output-schema.md`；v2 prompt V2-only 引用；v1.md DEPRECATED 归档；inventory §5 / D-1X-C 收口。 |
