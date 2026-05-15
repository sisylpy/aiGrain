# 真实老板问法 → intent / path → Composite / 单域 边界设计（**D-2**）

> **读者**：语义解析、Resolver、`BusinessDataPlannerNode`、Composite Gate 对接工程师。  
> **阶段**：**D-2** — **仅文档**；对齐现网 **`AiResolvedQueryIntent`**、**pathCode**、**`AiQuerySemanticLexicon` wire**（canonical **`structuredIntentDetail`**）及 **[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md) §3.3**。  
> **不做什么**：不接 **PRIMARY**；不继续 **C-66** metrics/dashboard/Redis；**禁止**用户原文 **contains/regex** 路由；不把 **Harness GraphCase** 当生产主入口（见 §7）。

**交叉引用**：Gate 权威白名单 **[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md)** §3.3；业务能力优先级 **[`next-business-capability-roadmap.md`](./next-business-capability-roadmap.md)**；常量定义 **`AiResolvedQueryIntent`**、`AiQuerySemanticLexicon`。

---

## 1. TOP 真实问法清单（产品口径）

以下为 **本轮必须覆盖** 的 **exampleQuestion**；实现侧 **不得** 用字面匹配代替 **语义 LLM + Resolver** 输出的结构化字段。

| # | exampleQuestion |
|---|-----------------|
| 1 | 这个月经营得怎么样？ |
| 2 | 今天生意怎么样？ / 本月生意怎么样？ / 上月生意怎么样？ |
| 3 | 哪个门店经营最好？ |
| 4 | AAA 和汀兰哪个经营好？ |
| 5 | 成本是不是高了？ |
| 6 | 采购是不是太高？ |
| 7 | 哪个商品采购最多？ |
| 8 | 出库多少钱？ |
| 9 | 损耗多少？ / 报损多少？ / 退货多少？ |
| 10 | 哪个菜毛利最低？ |
| 11 | 核桃芽菜西芹毛利怎么样？ |
| 12 | 那上个月呢？ |
| 13 | 那 AAA 呢？ |
| 14 | 那采购呢？ |
| 15 | 那出库呢？ |
| 16 | 那菜品呢？ |

---

## 2. **`routeTarget` / `scopeType` / `timeStrategy` 枚举说明**

| 字段 | 取值 | 含义 |
|------|------|------|
| **routeTarget** | `COMPOSITE` | 满足 Gate **§3.3.3「允许」行** + scope/time 完整，可进 **PlannerExecutor Composite**（仍须 **`BusinessDiagnosisCompositeProductionGate.allowed=true`**；**SHADOW/HARNESS_ONLY** 按 **[`business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md)**）。 |
| | `REVENUE` / `PURCHASE` / `STOCK_REDUCE` / `DISH_PROFIT` | 单域 **`ROUTED_AGENT`** 口径，对齐现网 **intent**（**`REVENUE_OVERVIEW`** / **`PURCHASE_OVERVIEW`** / **`STOCK_REDUCE_QUERY`** / **`DISH_PROFIT`**）及对应 **Tool**。 |
| | `CLARIFY` | **`needSemanticClarification`** / **`orchestrationClarificationRequired`** / **`AiRunState#isNeedClarification`** 等 **早退澄清**；**不得**为进 Composite 硬猜。 |
| | `UNSUPPORTED` | **相对本表四域 + Composite** 之外的主链路（如 **`COST_DIAGNOSIS`**、**`WAREHOUSE_STOCK_OVERVIEW`**）、或 **产品未开放**能力；见 **§2.1**。 |
| **scopeType** | `STORE` / `GROUP` | 与 **`AiResolvedOrgScope` / `dataScope`** 对齐；GROUP 进店对比须 **§4.2**。 |
| | `INHERIT` | 多轮 **继承上一轮** **`queryStoreIds` / `visibleStores` / GROUP 集合**（由 **`AiFollowUpResolution`** 产出）。 |
| | `NEED_CLARIFY` | 追问 **未**携带足够 **门店/集团**锚点且无 **可继承**上下文。 |
| **timeStrategy** | `EXPLICIT` | 用户或本轮解析 **明确**起止/BY_DAY/BY_MONTH。 |
| | `DEFAULT_MONTH_TO_DATE` | 产品默认 **本月至今**（**锚点 today** 来自 Resolver；Harness Replay **固定日**）。 |
| | `INHERIT` | 继承 **`AiConversationTurnMemory`** / **`effectiveTimeWindowSource`**。 |
| | `NEED_CLARIFY` | 时间 **无法解析**或 **冲突**。 |

### 2.1 `routeTarget = UNSUPPORTED`（非 Composite、非四字面单域）

现网尚有 **`COST_DIAGNOSIS`**（**`cost_diagnosis_path`**）、**`WAREHOUSE_STOCK_OVERVIEW`**（**`warehouse_stock_overview_path`**）等；**Gate 明文禁止进 Composite**。本设计在映射表中 **不写新枚举** —— 此类行 **`routeTarget=UNSUPPORTED`**，在 **`reason`** 中写明 **实际 effectiveIntent**（如 **`COST_DIAGNOSIS`**），避免与 D-2 给定七值冲突。

---

## 3. 问法 → 结构化路由映射表（主表）

**约定**：

- **`expectedIntent` / `expectedPath`**：与 **`AiResolvedQueryIntent`** 常量一致；判定以 **`effectiveIntentCode` / `effectivePathCode`** 为准。  
- **`structuredIntentDetail`**：须 **canonical**（**`AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(...)`**）。  
- **`shouldUseComposite`**：与 Gate **允许行**一致；**true** 仅当 **`routeTarget=COMPOSITE`** 且 **未**触发 **§3.3 禁止行**（含 **`mentionedDishName` 非空**、**needClarification**、**非 §3.3.3a 的 BUSINESS_OVERVIEW** 等）。  
- **四域经营总览进 Composite**：**`BUSINESS_OVERVIEW`** 时 **必须** 同时满足 **[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md) §3.3.3a**（**`MULTI_AGENT`** / **`orchestrationMultiAgentRequired`** / **`isStructuredBusinessOverviewFourDomainOrchestrationSurface`**）。

| exampleQuestion | expectedIntent | expectedPath | structuredIntentDetail | scopeType | timeStrategy | routeTarget | shouldUseComposite | reason |
|-----------------|----------------|-------------|------------------------|-----------|--------------|-------------|-------------------|--------|
| 这个月经营得怎么样？ | `BUSINESS_OVERVIEW` | `business_overview_path` | `business_overview_summary` | `STORE` | `DEFAULT_MONTH_TO_DATE` | `COMPOSITE` | **true** iff §3.3.3a | **经营简报 / 四域总览**语义；无 §3.3.3a 则 **legacy 概览**、`shouldUseComposite=false`。 |
| 今天生意怎么样？ | `BUSINESS_OVERVIEW` | `business_overview_path` | `business_overview_status` | `STORE` | `EXPLICIT`（当日） | `COMPOSITE` | **true** iff §3.3.3a | **日粒度**由 **timeWindow** 表达；非四域 orchestration → 走 **DEFAULT_BUSINESS_OVERVIEW_TOOLS**。 |
| 本月生意怎么样？ | `BUSINESS_OVERVIEW` | `business_overview_path` | `business_overview_summary` | `STORE` | `DEFAULT_MONTH_TO_DATE` | `COMPOSITE` | **true** iff §3.3.3a | 同上月行；与「本月至今」默认一致。 |
| 上月生意怎么样？ | `BUSINESS_OVERVIEW` | `business_overview_path` | `business_overview_summary` | `STORE` | `EXPLICIT`（上月整月） | `COMPOSITE` | **true** iff §3.3.3a | **整月**须 **语义层**给出 **完整 timeWindow**。 |
| 哪个门店经营最好？ | **`REVENUE_OVERVIEW`** 或 `BUSINESS_OVERVIEW` | `revenue_overview_path` **或** `business_overview_path` | **`revenue_store_amount_ranking`** **或** `store_priority_ranking` **或**（若多域对比且 §3.3.3a）`business_store_status_compare` | `GROUP` | `EXPLICIT` 或 `DEFAULT_MONTH_TO_DATE` | **见 reason 列** | **false**（排行）／**true**（仅当 `business_store_status_compare` + §3.3.3a） | **§4 硬性**：**纯正向排行** → **`routeTarget=REVENUE`**，`shouldUseComposite=false`（Gate **§3.3.4**）。**「综合经营对比 + MULTI_AGENT + compare wire」** → **`routeTarget=COMPOSITE`**。语义层 **必须二选一**。 |
| AAA 和汀兰哪个经营好？ | `BUSINESS_DIAGNOSIS` 或 `BUSINESS_OVERVIEW` | `business_diagnosis_path` **或** `business_overview_path` | **`business_store_status_compare_diagnosis`** **或** `business_store_status_compare` | `GROUP` | `EXPLICIT` 或默认月 | `COMPOSITE` | **true**（诊断 wire）／**true** iff §3.3.3a（overview wire） | **双店对比**：诊断路径 **允许** Composite（**§3.3.3**）；overview 路径须 **§3.3.3a**。**visibleStores** 须 **双店解析稳定**。 |
| 成本是不是高了？ | `BUSINESS_DIAGNOSIS` | `business_diagnosis_path` | `business_cost_pressure_diagnosis` | `STORE` 或 `GROUP` | 视问法 | `COMPOSITE` | **true** | **四域证据型成本压力** → Gate **允许**。**不得**仅靠关键词。 |
| 成本是不是高了？（歧义/落单域） | `COST_DIAGNOSIS` | `cost_diagnosis_path` | *（成本专线 wire，现网 parser）* | `STORE` | 视问法 | `UNSUPPORTED` | **false** | **legacy 成本诊断**；Gate **禁止 Composite**（**§3.3.3**）。**reason** 标注 **`COST_DIAGNOSIS`**。 |
| 采购是不是太高？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `purchase_overview_summary` | `STORE` | `DEFAULT_MONTH_TO_DATE` | `PURCHASE` | **false** | **单域采购概览**；**非** Composite（**§3.3.3 禁止行**）。 |
| 哪个商品采购最多？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `purchase_goods_amount_ranking` | `STORE` 或 `GROUP` | 视问法 | `PURCHASE` | **false** | **采购排行** → **§3.3.4 禁止 Composite**。 |
| 出库多少钱？ | `STOCK_REDUCE_QUERY` | `stock_reduce_query_path` | `stock_reduce_overview` | `STORE` | 视问法 | `STOCK_REDUCE` | **false** | 单域 **`stock_reduce_query`**。 |
| 损耗多少？ | `STOCK_REDUCE_QUERY` | `stock_reduce_query_path` | `waste` | `STORE` | 视问法 | `STOCK_REDUCE` | **false** | wire **`waste`**（现网 Lexicon）。 |
| 报损多少？ | `STOCK_REDUCE_QUERY` | `stock_reduce_query_path` | `loss` | `STORE` | 视问法 | `STOCK_REDUCE` | **false** | wire **`loss`**。 |
| 退货多少？ | `STOCK_REDUCE_QUERY` | `stock_reduce_query_path` | `return` | `STORE` | 视问法 | `STOCK_REDUCE` | **false** | wire **`return`**。 |
| 哪个菜毛利最低？ | `DISH_PROFIT` | `dish_profit_path` | `dish_profit_ranking_low_margin` | `STORE` | 视问法 | `DISH_PROFIT` | **false** | **菜品排行** → **禁止 Composite**（**§3.3.4**）。 |
| 核桃芽菜西芹毛利怎么样？ | `DISH_PROFIT` | `dish_profit_path` | `dish_gross_margin_query` 或 `dish_profit_overview` | `STORE` | 视问法 | `DISH_PROFIT` | **false** | **`mentionedDishName` 非空** → Gate **全局禁止 Composite**（**§3.3.3** 末行）。 |
| 那上个月呢？ | *继承* | *继承* | *继承* | `INHERIT` | `INHERIT`（改为上月） | *继承* | *继承* | **§5**；**不得**因短句 **硬进 Composite** —— **须** `followUpResolution` 合并后 **effective\*** 与上一行一致。 |
| 那 AAA 呢？ | *继承* | *继承* | *继承* | `INHERIT`（改 scope） | `INHERIT` | *继承* | *继承* | **仅换店**；若 **无法解析 AAA** → `NEED_CLARIFY` + `CLARIFY`。 |
| 那采购呢？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `purchase_overview_summary` | `INHERIT` | `INHERIT` | `PURCHASE` | **false** | **域切换**：自 Composite/概览语境 **切** 采购单域（**§5**）。 |
| 那出库呢？ | `STOCK_REDUCE_QUERY` | `stock_reduce_query_path` | `stock_reduce_overview` | `INHERIT` | `INHERIT` | `STOCK_REDUCE` | **false** | 域切换 → 出库单域。 |
| 那菜品呢？ | `DISH_PROFIT` | `dish_profit_path` | `dish_profit_overview` | `INHERIT` | `INHERIT` | `DISH_PROFIT` | **false** | 域切换 → 菜品毛利单域（**非**排行时 **非** `mentionedDishName` 可 **overview**）。 |

**表注**：

- **「哪个门店经营最好？」** 产品上要 **强制语义二义性拆分**：**A)** 营业额/销量 **排行** → **单域 path + §3.3.4 wire**；**B)** **多域综合对比 + MULTI_AGENT** → **`business_store_status_compare` + §3.3.3a** → **COMPOSITE**。  
- **「今天生意怎么样？」** 若语义层 **未**能给 **合法日历窗** → **`timeStrategy=NEED_CLARIFY`**，`routeTarget=CLARIFY`。

---

## 4. Composite 进入规则（**复用 Gate，不另行发明**）

以下 **全部为 [`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md) §3 思路的 D-2 收口叙述**：

| 规则 | 说明 |
|------|------|
| **结构化唯一入口** | **禁止**用户原文 **contains/regex**；**只认** **`AiResolvedQueryContext` / `AiRunState`** 已物化字段（**effectiveIntent/path**、canonical **`structuredIntentDetail`**、`orchestration*`、`mentionedDishName`、澄清标志等）。 |
| **`BUSINESS_DIAGNOSIS`** | **允许**进 Composite **当且仅当**命中 **§3.3.3** 所列 **`business_diagnosis_summary` / `business_cost_pressure_diagnosis` / `business_store_status_compare_diagnosis`** 等 **允许行**，且 **无** **§3.3** 全局禁止条件。 |
| **`BUSINESS_OVERVIEW`** | **仅当**表达 **明确四域 / 多智能体经营总览**（**§3.3.3a** **四域 orchestration**）且 structured 属于 **`business_overview_summary` / `business_overview_status` / `business_store_status_compare`** 时 **允许** Composite；**否则** legacy 概览，**禁止** Composite。 |
| **单域问题** | **`REVENUE_OVERVIEW` / `PURCHASE_OVERVIEW` / `STOCK_REDUCE_QUERY` / `DISH_PROFIT`** 等 **§3.3.3 禁止行** —— **不得**进 Composite。 |
| **排行、指定菜品、商品深挖** | **§3.3.4** 所列 wire、**`mentionedDishName` 非空**、`isSingleDishMetricOrReasonStructuredDetail`、`isDishProfitRankingStructuredDetail` 等 —— **禁止** Composite。 |
| **澄清** | **`needSemanticClarification`** / **`orchestrationClarificationRequired`** / **`isNeedClarification`** → **不进** Composite（**§3.3.3**）。 |

---

## 5. 单域边界（**必须走单域**）

| 问法语义 | routeTarget | 现网对齐 |
|----------|-------------|-----------|
| 营收多少、哪店 **营业额**/营收 **排行榜**（非 §3.3.3a 综合对比） | `REVENUE` | **`REVENUE_OVERVIEW`** + **`revenue_query`** |
| 采购金额/是否偏高/自采/供货商/**商品采购排行** | `PURCHASE` | **`PURCHASE_OVERVIEW`** + **`purchase_overview`** |
| **出库**金额、**损耗/报损/退货**、**出库排行** | `STOCK_REDUCE` | **`STOCK_REDUCE_QUERY`** + **`stock_reduce_query`** |
| **菜品毛利**、指定菜、`mentionedDishName`、毛利 **最低**/成本异常排行 | `DISH_PROFIT` | **`DISH_PROFIT`** + **`dish_profit_analysis`** |

**与 §3 关系**：单域 **永远** **`shouldUseComposite=false`**（当前 Gate 定义下）。

---

## 6. 多轮追问规则

| 短句 | 继承 | 切换 | 结构化要求 |
|------|------|------|-------------|
| **「那上个月呢？」** | **业务域 intent/path/structured** 与 **scope** **不变** | **仅替换** **`timeWindow`**（上月） | **`timeStrategy=INHERIT`** 指 **effectiveTime** 重写；依赖 **`AiFollowUpResolution`** + memory。 |
| **「那 AAA 呢？」** | **业务域** 与 **时间** **不变** | **仅替换** **门店锚点 / `queryStoreIds` / GROUP 成员** | **`scopeType=INHERIT`** + 新 **`visibleStores`**；解析失败 → **`NEED_CLARIFY`**。 |
| **「那采购呢？」** | **时间 + scope** 继承（除非句内覆盖） | **intent → `PURCHASE_OVERVIEW`**，`structuredIntentDetail → purchase_overview_summary`**（默认） | **从 Composite/经营语境切到 Purchase 单域**；**effectivePathCode** **必须**切到 **`purchase_overview_path`**。 |
| **「那出库呢？」** | 同上 | **`STOCK_REDUCE_QUERY`** + **`stock_reduce_overview`** | 切 **`stock_reduce_query_path`**。 |
| **「那菜品呢？」** | 同上 | **`DISH_PROFIT`** + **`dish_profit_overview`**（无点菜名） | 切 **`dish_profit_path`**；**若**上轮留有 **`mentionedDishName`** 且本句 **未点名新菜**，按产品决定是否 **清空**/**保留** —— **须**单列 **D-3** 对齐 **Gate **`mentionedDishName`** 规则**。 |
| **不明确**（无继承、无意图） | — | — | **`CLARIFY`**；**禁止** 为凑 Composite **默认沿用 diagnosis**。 |

---

## 7. Harness / curl 验收建议（**D-3**，本轮不实现）

下列场景 **建议在 D-3** 增补 **replay / curl** 期望（与 **[`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md)**、**[`AI_HARNESS_REPLAY_CASES.md`](../AI_HARNESS_REPLAY_CASES.md)** 维护方式一致）。

| 场景 ID（建议） | 观测重点 |
|-----------------|----------|
| **D3-COMPOSITE-OVERVIEW-MTD** | **`BUSINESS_OVERVIEW` + §3.3.3a** → **`compositeGateAllowed`**、**`finalAnswerPlanType`**、四步 **`usedTools`** |
| **D3-COMPOSITE-DIAGNOSIS-COST** | **`business_cost_pressure_diagnosis`** |
| **D3-COMPOSITE-GROUP-COMPARE** | **`business_store_status_compare_diagnosis`**、**`scopeType=GROUP`**、**`visibleStores`** |
| **D3-SINGLE-PURCHASE-RANK** | **`purchase_goods_amount_ranking`** → **`compositeGateAllowed=false`**、**reasonCode** 含 **单域/排行** |
| **D3-SINGLE-DISH-NAMED** | **`mentionedDishName` 非空** → Gate **禁止** Composite |
| **D3-FOLLOWUP-TIME** | 继承 **effectiveIntent** + 仅 **`timeWindow`** 变 |
| **D3-FOLLOWUP-DOMAIN-SWITCH** | **「那采购呢？」** **`effectivePathCode`** 切换到 **`purchase_overview_path`** |

**重点观测字段**（与普通 Run / SHADOW 观测一致）：

- **`effectiveIntentCode` / `effectivePathCode`**（及 **`structuredIntentDetail` wire**）
- **`effectiveTimeWindowSource`**、**`timeWindow`**（**start/end**）
- **`orgScope.scopeType`**、**`visibleStores`**、**`queryStoreIds`**
- **`routeTarget`**（本设计 **表** — D-3 可 **日志/harnessPayload** mirror）
- **`compositeGateAllowed`**、**`compositeGateReasonCode`**（及 Gate **文档**所列 reason 家族）
- **`finalAnswerPlanType`**
- **`usedTools`**（Composite **六步 trace**）
- **`businessDiagnosisFinalAnswerText`**（若 Composite **Readonly Composer** 路径挂载）

---

## 8. 禁止项（**D-2 本轮**）

| 禁止 | 说明 |
|------|------|
| 改 Java（非必要） | 本文件 **不产生**编译修改；后续 **D-3+** **单列 PR**。 |
| 改 **`src/test/**`** | **否**。 |
| 新增 SQL | **否**。 |
| 接 **PRIMARY**；替换 **`finalAnswerText` / `answerPreview`** | **否**。 |
| **C-66** metrics / dashboard / **Redis** 跨实例限流 | **暂缓**。 |
| 用户原文 **contains/regex** 路由 | **否**。 |
| **Harness GraphCase** 当作 **`/api/ai/runs`** 唯一生产入口 | **否**。 |
| 未经许可改 **Composite / Gate / Composer** 源码 | **非 D-2 范围**。 |

---

## 9. 现网结构化能力 **缺口**（供 D-3+ 语义/Resolver backlog）

| 缺口 | 说明 |
|------|------|
| **「经营最好」双轨** | 须 **语义层显性**产出 **「排行 vs 综合对比」**，否则易 **误判 path**（**REVENUE** ranking vs **`business_store_status_compare`**）。 |
| **随访域切换句**（「那采购呢？」） | 依赖 **`AiFollowUpResolver`**：**强制**重写 **intent/path**，避免 **effective\*** **仍卡在 diagnosis/overview**。需在 **integration 测试/D-3 curl** **显式断言**。 |
| **`COST_DIAGNOSIS` vs `business_cost_pressure_diagnosis`** | 产品培训 **句式**应与 **结构化 wire**表一致；混淆时 **宁可 CLARIFY** 也不宜 **两套链路乱跳**。 |
| **损耗/退货/分拆** | Lexicon **已有** **`waste` / `loss` / `return`**；若 **Tool 输出**暂无字段级拆分，Composer **仅能诚实说明 dataCoverage**（**不改 SQL** 前提下）。 |

---

## 10. **D-2.2** — 主链路路由验收表（`/api/ai/runs` → Resolver → Gate → 终链路）

<a id="d22-main-route-acceptance-table"></a>

### 10.1 验收前提（必读）

| 要点 | 说明 |
|------|------|
| **入口** | 用户请求经 **`POST /ai/runs`** → **`AiRunService.startRun`**。 |
| **结构化来源** | **`AiResolvedQueryContextResolver`** 调用 **`AiQuerySemanticLlmParser`**（及 v2/v1 归一化链路），产出 **`AiResolvedQueryContext`**：其中 **`effectiveIntentCode` / `effectivePathCode`** 与 **`queryIntent.structuredIntentDetail`**（经 **`AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire`** canonical）为本验收表对齐字段。**用户原文不参与 Gate 分支**。 |
| **Gate** | **`BusinessDiagnosisCompositeProductionGate.evaluate`** **只读** **`AiResolvedQueryContext`** 与 **`AiRunState.needClarification`**；**不** `contains`/regex **`message`**；**不**改路由到 **`BusinessDataPlannerNode`**。**Composite 亦不直接读用户原文**——仅消费 Resolver 已物化字段。 |
| **`productionEnabled`** | 表中 **`expectedCompositeGateAllowed=true`** **均假设** **`ai.composite.businessDiagnosis.productionEnabled=true`**。若为 **false**：除 **MISSING_\*** **外**，一律 **`allowed=false`、`expectedGateReasonCode=FEATURE_FLAG_DISABLED`**（见 **`BusinessDiagnosisCompositeGateReasonCode`**）。 |

### 10.2 验收字段说明

| 列 | 含义 |
|------|------|
| **expectedStructuredIntentDetail** | **canonical wire**（与 Gate 判定一致）；多选一写 **`a \| b`**。 |
| **expectedScopeType** | **`AiResolvedOrgScope`** 上 **`STORE` / `GROUP`**；追问继承写 **`STORE（inherit）`** 或 **`GROUP（inherit）`**。 |
| **expectedCompositeGateAllowed** | **`BusinessDiagnosisCompositeGateResult.allowed`**。 |
| **expectedGateReasonCode** | **`BusinessDiagnosisCompositeGateReasonCode`** 枚举名；放行行为 **`ALLOWED_STORE` / `ALLOWED_GROUP`**。 |
| **finalRouteTarget** | **本条 Run 语义上主业务落点**：**`COMPOSITE`** 表示 Gate 放行且（在 SHADOW/HARNESS_ONLY/未来 PRIMARY 下）可走 PlannerExecutor Composite；其余为 **legacy 单域主链路 / 澄清 / 成本专线**。与实际 **SSE `finalAnswerText`** 数据源对应：**Composite 未做主回答时仍为 legacy**。 |

### 10.3 主验收表（16 类问法）

| exampleQuestion | expectedIntentCode | expectedPathCode | expectedStructuredIntentDetail | expectedScopeType | expectedCompositeGateAllowed | expectedGateReasonCode | finalRouteTarget | reason |
|-----------------|-------------------|------------------|-------------------------------|-------------------|-------------------------------|--------------------------|-----------------|--------|
| 这个月经营得怎么样？ | `BUSINESS_OVERVIEW` | `business_overview_path` | `business_overview_summary` | `STORE` | **true** | `ALLOWED_STORE` | **COMPOSITE** | 须 **`§3.3.3a` 成立**（`MULTI_AGENT` / `orchestrationMultiAgentRequired=true` / 四域 surface）；否则 **`INTENT_PATH_NOT_WHITELISTED`**，终链 **legacy BUSINESS_OVERVIEW**（非本表 COMPOSITE）。 |
| 今天生意怎么样？ / 本月生意怎么样？ / 上月生意怎么样？ | `BUSINESS_OVERVIEW` | `business_overview_path` | **`business_overview_status`**（今天）**/** **`business_overview_summary`**（本月 MTD）**/** **`business_overview_summary`** + **显式上月整窗**（上月） | `STORE` | **true** iff §3.3.3a | `ALLOWED_STORE` iff §3.3.3a | **COMPOSITE** iff §3.3.3a | 与时间解析绑定；解析失败 **`CLARIFICATION_REQUIRED`** 或 **`MISSING_TIME_WINDOW`** → **`CLARIFY`**。 |
| 哪个门店经营最好？ | **`REVENUE_OVERVIEW`** | **`revenue_overview_path`** | **`revenue_store_amount_ranking`** **或 **`store_priority_ranking`**（canonical 后等价 §3.3.4） | `GROUP` | **false** | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`** **或 **`RANKING_OR_DEEP_DIVE_NOT_COMPOSITE`** | **REVENUE** | **排行优先解释**；语义若落 **`business_store_status_compare` + §3.3.3a** → **允许 COMPOSITE**（见 §10.4）。 |
| AAA 和汀兰哪个经营好？ | **`BUSINESS_DIAGNOSIS`** **或 **`BUSINESS_OVERVIEW`**（二选一产品时定） | `business_diagnosis_path` **或 **`business_overview_path`** | **`business_store_status_compare_diagnosis`** **或 **`business_store_status_compare`** | `GROUP` | **true** | **`ALLOWED_GROUP`** | **COMPOSITE** | 诊断 wire **direct allowA**；overview wire **须 §3.3.3a**。**`GROUP`** 须 **≥2** `visibleStores` 根否则 **`GROUP_SCOPE_INSUFFICIENT_VISIBLE_STORES`** → **`CLARIFY`**（或 legacy，非 Composite）。 |
| 成本是不是高了？（四域证据型） | `BUSINESS_DIAGNOSIS` | `business_diagnosis_path` | **`business_cost_pressure_diagnosis`** | `STORE` 或 `GROUP` | **true** | **`ALLOWED_STORE`** 或 **`ALLOWED_GROUP`** | **COMPOSITE** | 与 **`COST_DIAGNOSIS` 专线**互斥产品线：与本表 **`COST_DIAGNOSIS` 成行**分列验收。 |
| 成本是不是高了？（落成本专线） | `COST_DIAGNOSIS` | `cost_diagnosis_path` | *（解析器定义的 structured，非上列三种 diagnosis overview wire）* | `STORE` | **false** | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`** | **COST_DIAGNOSIS** | Gate **§3.3.3 禁止单行** → **不进 Composite**。 |
| 采购是不是太高？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `purchase_overview_summary` | `STORE` | **false** | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`** | **PURCHASE** | 单域采购概览 → **legacy DataPlanner Purchase 链**。 |
| 哪个商品采购最多？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | **`purchase_goods_amount_ranking`** | `STORE` 或 `GROUP` | **false** | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`**（源码先判单域）；若 path 误判非采购则另行 | **PURCHASE** | 采购排行：**不进 Composite**；**`NAMED_DISH_...`** 不适用于本行。 |
| 出库多少钱？ | `STOCK_REDUCE_QUERY` | `stock_reduce_query_path` | **`stock_reduce_overview`** | `STORE` | **false** | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`** | **STOCK_REDUCE** | 单域 **`stock_reduce_query`**。 |
| 损耗多少？ / 报损多少？ / 退货多少？ | `STOCK_REDUCE_QUERY` | `stock_reduce_query_path` | **`waste`** / **`loss`** / **`return`**（三选一） | `STORE` | **false** | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`** 或 **`RANKING_…`**（若误标排行 wire） | **STOCK_REDUCE** | **non-overview stock_reduce structured** **若被判排行类**则可能 **`RANKING_OR_DEEP_DIVE_NOT_COMPOSITE`**；参见 Lexicon **`isNonOverviewStockReduceStructuredDetail`**。 |
| 哪个菜毛利最低？ | `DISH_PROFIT` | `dish_profit_path` | **`dish_profit_ranking_low_margin`** | `STORE` | **false** | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`**（`DISH_PROFIT` 先走单域拒 Composite） | **DISH_PROFIT** | **`RANKING_...`** 分支在源码中位于单域判定之后；**机读 reasonCode** 以 **`DOMAIN_SINGLE_...`** 为准。 |
| 核桃芽菜西芹毛利怎么样？ | `DISH_PROFIT` | `dish_profit_path` | **`dish_gross_margin_query`** **或 **`dish_profit_overview`** | `STORE` | **false** | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`** | **DISH_PROFIT** | **`mentionedDishName` 须由 Resolver 写入**仅供 Tool/Composer；**Gate**（`BusinessDiagnosisCompositeProductionGate`）对 **`DISH_PROFIT`+`dish_profit_path`** **先命中单域拦截**，一般不落到 **`NAMED_DISH_DEEP_DIVE_NOT_COMPOSITE`**。 |
| 那上个月呢？ | 继承上一轮 `effectiveIntentCode` | 继承 `effectivePathCode` | 继承 canonical structured | 继承 `scopeType` | 与上一轮同规则 | 同上一轮 | 同上一轮主域 | Resolver **重写 timeWindow**；无快照 → **`CLARIFICATION_REQUIRED`** → **`CLARIFY`**。 |
| 那 AAA 呢？ | 继承 intent/path/structured | 同左 | 同左 | STORE（重写锚店）或 GROUP | 再判锚点 | 锚点∉visible：**`STORE_SCOPE_MISSING_ANCHOR`** | 继承 domain | 仅 scope 切换。 |
| 那采购呢？ | **`PURCHASE_OVERVIEW`** | **`purchase_overview_path`** | **`purchase_overview_summary`** | **继承 scope** | **false** | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`** | **PURCHASE** | **域切换**；须语义层 **重写 effectivePath**，不可 **单靠**短句字面。 |
| 那出库呢？ | **`STOCK_REDUCE_QUERY`** | **`stock_reduce_query_path`** | **`stock_reduce_overview`** | **继承 scope** | **false** | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`** | **STOCK_REDUCE** | 同上。 |
| 那菜品呢？ | **`DISH_PROFIT`** | **`dish_profit_path`** | **`dish_profit_overview`** **或结构化随随访收敛**（如排行/点名则换 wire） | **继承 scope** | **通常为 false**（`DISH_PROFIT`+`dish_profit_path` 先走单域） | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`**（或与「哪个菜」「点名菜」对齐的 wire 时再判 **`RANKING_…`** / 其他） | **DISH_PROFIT** | Resolver **升格 path+structured** 后按 **本条表**前述菜品行规则重判；**不**可依用户短句字面单独改 Gate。 |

**表注**：「哪个门店经营最好？」若产品语义收口为 **`business_store_status_compare` + §3.3.3a + GROUP**，则 **`expectedCompositeGateAllowed=true`**、**`ALLOWED_GROUP`**、**`finalRouteTarget=COMPOSITE`**；与 **排行单列**分叉须在 **语义层**显性区隔（见 §10.4）。

### 10.4 语义缺口（验收表联动，**不引入代码**）

接续 **§9**，专供 **curl / Replay / 日志对表**时归因 **字段漂移**：

| 缺口 | 对验收的影响 |
|------|----------------|
| **§3.3.3a 四域 orchestration** 与 **`BUSINESS_OVERVIEW`** | 「生意怎么样」句常落成 overview wire **但未带 **`MULTI_AGENT`**/`orchestrationMultiAgentRequired`** → Gate **`INTENT_PATH_NOT_WHITELISTED`**，**legacy 终稿**；不等价于 Resolver 宕机。 |
| **门店最好：排行 vs GROUP 对比** | 排行 vs **`business_store_status_compare`** **二选一**；混 path/wire 时以 Gate **`isRankingOrDeepDiveStructuredDetail`** + **whitelist** 综合为准。 |
| **随访「那采购/出库/菜品」** | Resolver **必须**产出新 **`effectivePathCode`**；否则 Gate 仍按 **上一轮 path**，验收 **失败**。 |
| **成本句双轨** | **`business_cost_pressure_diagnosis`** vs **`COST_DIAGNOSIS`**：**验收分列两 expected**（表中已两行）。 |

### 10.5 **主路由与 Composite 接入策略评审表**（D-2.2 增补）

本节与 **[`main-routing-to-composite-integration-review.md`](./main-routing-to-composite-integration-review.md)**（调用链）、**[`main-business-routing-and-composite-integration-map.md`](./main-business-routing-and-composite-integration-map.md)**（Gate/SHADOW 挂接）对齐；与 **§10.3「逐句例」**互为补充：**本节按「问法类型」归类**，便于评审 **Composite 放行边界**与 **legacy 主链**分工。

#### 10.5.1 分层原则（须同时满足）

| 层 | 职责 | **禁止** |
|----|------|----------|
| **第一层** | **`AiResolvedQueryContextResolver`** + **`AiQuerySemanticLlmParser`**（v2→v1）产出 **`AiResolvedQueryContext`**：含 **`queryIntent`**、**`effectiveIntentCode`/`effectivePathCode`**、canonical **`structuredIntentDetail`**、时间/范围/ORCH 扁平字段等。 | 不得用 **`message` `contains`/regex** 顶替 **LLM+合并**作为主路由。 |
| **Gate（C-53）** | **`BusinessDiagnosisCompositeProductionGate.evaluate`** **只读**结构化结果 + **`needClarification`**；**不写** **`dataPlanTools`**、**不改** **`AiRunState` 路由位**。 | 不得读用户原文做分支；不得驱动 **`BusinessDataPlannerNode`** 选路。 |
| **Composite** | **PlannerExecutor + RealBridge**（**`BusinessDiagnosisCompositeExecutionService`**）仅在 **HARNESS_ONLY / SHADOW（及未来 PRIMARY）** 语义下运行；输入锚在 **`AiResolvedQueryContext`**。 | **不是**第二层「原文路由」引擎。 |

#### 10.5.2 执行观测与 PRIMARY（现行 vs 将来）

| 模式 | **`finalAnswerText`** | **说明** |
|------|------------------------|----------|
| **SHADOW（C-60～）** | **不替换** | **`maybeExecuteShadowCompositePlanner`** 在 **`runBusinessGraph` 完结后**旁路执行；遗留 **Composer** 产出仍为 **SSE 主文**；**`compositeShadow*`** 仅观测。详见集成图 §5 / 梳理文档 §5。 |
| **PRIMARY** | **当前不接** | **`tryExecute`** 入口 **仅放行** **`HARNESS_ONLY`｜`SHADOW`**；**PRIMARY** 需先扩 **Execution**，再在 **`AiRunService#executeRun` 末尾**与 SHADOW **同相位**决策是否替换正文。**禁止**为用例加 **`contains`/regex**。**去重风险**：PRIMARY 若继承现行 SHADOW「legacy 图已跑满四域 Tool + Composite 再跑 RealBridge」，易 **双倍 IO**；须在 **`BusinessDataPlannerNode`** 或 **`BusinessToolExecutionNode`** 基于 **结构化条件**裁剪计划（见梳理文档 §6.2）。 |

#### 10.5.3 问法类型 × 结构化 × Composite × **最终路线**

**约定**：（1）**`structuredIntentDetail` 特征**为 **canonical wire 族**简述，确切枚举以 **`AiQuerySemanticLexicon`** + Gate 白皮书对齐为准；（2）**Composite 允许**列指 **`productionEnabled=true`** 且 **非澄清、时间窗完整**前提下 **`GateResult.allowed`** 的典型值；（3）**Gate reason**：放行写 **`ALLOWED_*`**；拒绝写 **`BusinessDiagnosisCompositeGateReasonCode`** 典型枚举名；（4）**最终路线**七种之一：单域四类 / **LEGACY_MULTI_AGENT** / **COMPOSITE_CANDIDATE** / **CLARIFICATION**。

| 问法类型 | **典型** `effectiveIntentCode` | **典型** `effectivePathCode` | **`structuredIntentDetail` 特征**（简述） | **Composite 允许**（典型） | **典型 Gate reason** | **最终路线** |
|----------|-------------------------------|------------------------------|--------------------------------------------|----------------------------|-----------------------|---------------|
| **单域营收** | `REVENUE_OVERVIEW` | `revenue_overview_path` | 营业额/应收 **概览类** wire；非门店排行时用 summary 族 | **否** | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`**（单域门闸先于细排） | **REVENUE** |
| **单域采购** | `PURCHASE_OVERVIEW` | `purchase_overview_path` | 采购 overview/summary 族 | **否** | 同上 | **PURCHASE** |
| **单域出库** | `STOCK_REDUCE_QUERY` | `stock_reduce_query_path` | **`stock_reduce_overview`** / **`waste`** / **`loss`** / **`return`** 等 | **否** | 同上；误标排行时 **`RANKING_OR_DEEP_DIVE_NOT_COMPOSITE`** | **STOCK_REDUCE** |
| **单域菜品毛利** | `DISH_PROFIT` | `dish_profit_path` | overview / margin query / ranking low margin — **仍为单域路径** | **否**（典型：单域 Gate） | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`** | **DISH_PROFIT** |
| **经营概览** | `BUSINESS_OVERVIEW` | `business_overview_path` | **四域 orchestration surface**（Gate 白皮书 §3.3.3a）且 **`MULTI_AGENT`/`orchestrationMultiAgentRequired`** 由 Resolver 置位 | **是**（白名单成立） | **`ALLOWED_STORE`** / **`ALLOWED_GROUP`** | **COMPOSITE_CANDIDATE**：Gate 放行≠换主文；**SSE** 仍为 **legacy**。未满足 orchestration 时 → **LEGACY_MULTI_AGENT**（`DEFAULT_BUSINESS_OVERVIEW_TOOLS` 等）。 |
| **经营诊断** | `BUSINESS_DIAGNOSIS` | `business_diagnosis_path` | **`business_*_diagnosis`** / **`business_cost_pressure_diagnosis`** 等；Resolver **对齐** **`MULTI_AGENT`** | **是**（白名单+scope/time OK） | **`ALLOWED_*`** | **LEGACY_MULTI_AGENT**：**DataPlanner `applyBusinessDiagnosisBranch`** + **Tool** + **Master**（若 **`eligible`**）为主链；并行 **Composite** → **COMPOSITE_CANDIDATE**。**不换主文**（现行）。 |
| **排行** | 多为 **`REVENUE_OVERVIEW`** / **`PURCHASE_OVERVIEW`** / **`DISH_PROFIT`**（单域） | **`revenue_overview_path`** / **`purchase_overview_path`** / **`dish_profit_path`** | ranking 族 wire；Gate **`isRankingOrDeepDiveStructuredDetail`** | **否** | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`** 或 **`RANKING_OR_DEEP_DIVE_NOT_COMPOSITE`** | **REVENUE** / **PURCHASE** / **DISH_PROFIT**（依域） |
| **点名菜品** | `DISH_PROFIT` | `dish_profit_path` | **`dish_gross_margin_query`** 等；**`mentionedDishName`** Resolver 物化 | **否**（典型） | **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`**（与 §10.3 点名行一致） | **DISH_PROFIT** |
| **深挖追问**（时间·域·店继承） | **继承上一轮** **`effectiveIntentCode`** | **继承** **`effectivePathCode`**（**域切换句**须在 Resolver **重写 path**） | **继承 canonical** 或 **随域切换换新 wire** | **同继承句与上轮同类** | **`CLARIFICATION_REQUIRED`** **或** **`STORE_SCOPE_MISSING_ANCHOR`** **或** **`ALLOWED_*`** | **与继承域一致**（四类单域之一 / **LEGACY_MULTI_AGENT** / **COMPOSITE_CANDIDATE** **或** **CLARIFICATION**） |
| **时间缺失 /  unresolved** | 解析未完成 | — | **`needSemanticClarification`** **或** 时间 **`null`** | **否** | **`CLARIFICATION_REQUIRED`** **或** **`MISSING_TIME_WINDOW`** | **CLARIFICATION** |
| **范围缺失 / 不齐** | 可有草稿意图 | — | GROUP **可见店少于 2 家**、语义店不在 **visible** 集合 **等** | **否** | **`GROUP_SCOPE_INSUFFICIENT_VISIBLE_STORES`** **等** | **CLARIFICATION** **或** legacy 兜底答复（Composer/门禁，见实现） |
| **权限不足** | 仍可有 **intent/path** | 对应 **`*_path`** | **`dataPlanTools`** **权限裁剪**；Master **`eligible`** **假** | **随 intent**：单域常 **否**；Composite 亦非「权限」专用开关 | **`ALLOWED_*`**（仍过 Gate 但 **无 Tool**）**或** **`INTENT_PATH_NOT_WHITELISTED`** **等** | **LEGACY_MULTI_AGENT** 或 **单域四类之一**，**退化子链**；**非** **`CLARIFICATION`** **的泛称** |

**表注**：**COMPOSITE_CANDIDATE** ≡ §10.3 **`finalRouteTarget`** 中 **语义上的「可进 Composite（SHADOW/Harness/将来 PRIMARY）」**；**与用户可见终稿**：现行 **始终 legacy `finalAnswerText`**，直至 **PRIMARY** 落地。**`COST_DIAGNOSIS`**（成本专线）**不在七种「最终路线」内**：并行成本支线；**Gate** 典型 **`DOMAIN_SINGLE_INTENT_NOT_COMPOSITE`**。

---

## 11. 版本

| 版本 | 日期 | 说明 |
|------|------|------|
| **D-2 v1** | 2026-05-14 | 初版：TOP 问法映射、Composite/单域边界、随访、D-3 观测、禁止项与 **§9** 语义缺口。 |
| **D-2 v2** | 2026-05-14 | **§10** 主链路路由验收表（D-2.2）、§10.4；**不接代码**。 |
| **D-2 v3** | 2026-05-14 | **§10.5** 主路由与 Composite **接入策略评审表**：问法类型 × Gate × 七种最终路线；SHADOW **不换主文**、PRIMARY **策略与去重风险**。 |

---

**交叉索引**  
- **主链路时序 / Gate-SHADOW 挂接**：[`main-business-routing-and-composite-integration-map.md`](./main-business-routing-and-composite-integration-map.md)。  
- **Resolver→DataPlanner→Master→Composite 代码梳理**：[`main-routing-to-composite-integration-review.md`](./main-routing-to-composite-integration-review.md)。
