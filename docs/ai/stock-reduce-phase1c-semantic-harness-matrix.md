# 出库 / 核销 / 废弃 / 损失 — 阶段 1C 语义层 Harness 矩阵（RESOLVED_CONTEXT_ONLY）

本文档定义 **阶段 1C** 的验收范围与最小用例矩阵，仅针对 **ResolvedContext 语义层**，不涉及业务取数、SQL、Tool、`StockReduceAnswerPlan`、`Composer` 或前台展示。

---

## §1 阶段 1C 验收边界

**本阶段只验 ResolvedContext 及相关语义摘要字段，不验业务取数。**

允许断言（Harness / 解析快照中与路由、时间、范围、多轮一致的部分）：

- `effectiveIntentCode`
- `effectivePathCode`
- `structuredIntentDetail` / `canonicalStructuredIntentDetailWire`（以归一后的 **canonical wire** 为优先比对口径；人类可读 debug code 可与 Lexicon `toStructuredIntentDetailDebugCode` 对齐）
- Harness 摘要中的 **`stockReduceType` debug 字段**（与结构化子口径侧写对齐时的枚举名，见 §6）
- `timeAction`、`timeSource`（来自语义解析 JSON 的 `timeAction` / `time.timeSource`；若 Harness 仅镜像 `effectiveTimeWindowSource`，在 case 中写清断言对象）
- 范围与门店：`requestedScope`、`scopeAction`、`scopeSource`、可见门店列表 / 并排范围来源（如 `harnessMultiStoreScope*`、继承标记）

允许覆盖的多轮与跨域：

- **多轮继承**：上一轮时间窗 / 门店范围 / 对比形态在追问中的承接
- **跨域接力**：经营 → 出库、采购 → 核销等 **intent/path 切换** 下的语义一致性

**本阶段明确不验：**

- Tool 行、`selectedTools` / Agent 编排执行结果
- SQL、`department_id` 等数据范围展开结果
- `StockReduceAnswerPlan`、`focusRows`、排序键
- Composer 文案、前台展示

---

## §2 出库语义现状（单矩阵前提）

### 2.1 单域出库统一路由

单域「出库 / 核销流水 / 子类金额 / 出库排行」在合并与归一后统一为：

- `effectiveIntentCode` = `STOCK_REDUCE_QUERY`
- `effectivePathCode` = `stock_reduce_query_path`

LLM 别名 `STOCK_OUT`、`WRITE_OFF` 服务端会归一到上述 intent/path。

### 2.2 子口径 canonical wire（structuredIntentDetail）

| wire | 含义 |
|------|------|
| `stock_reduce_overview` | 出库总览 / 未指定子类 |
| `produce_consume` | 生产耗用；v2 常写作 `TYPE1`，服务端 canonical 归一 |
| `produce_output` | 出品耗用（与生产耗用区分由解析层给出） |
| `waste` | 废弃；v2 常写作 `TYPE2` |
| `loss` | 损失 / 报损；v2 常写作 `TYPE3` |
| `return` | 退货；v2 常写作 `TYPE4` |
| `goods_outbound_ranking` | 商品出库金额排行 |
| `goods_outbound_count_ranking` | 商品出库次数排行 |
| `store_outbound_amount_ranking` | 门店出库金额对比 / 排行 |

别名示例：`goods_outbound_amount_ranking` → `goods_outbound_ranking`；`stock_reduce_store_amount_ranking` → `store_outbound_amount_ranking`；`type1`–`type4` → 上表对应子口径。

### 2.3 采购 + 出库 / 库存风险（非单域出库）

当问句构成 **采购与出库/耗用脱节或双域风险**（文档与 v2 专节约定）时，**不走** `stock_reduce_query_path`，而应：

- `effectiveIntentCode` = `BUSINESS_DIAGNOSIS`
- `effectivePathCode` = `business_diagnosis_path`
- `canonicalStructuredIntentDetailWire` 落在采购+出库风险类 wire 之一，例如：
  - `purchase_stock_reduce_mismatch`
  - `purchase_slow_moving_risk`
  - `purchase_inventory_overstock_risk`
  - `purchase_freshness_risk`

此类用例 **不得** 期望为 `STOCK_REDUCE_QUERY`。

### 2.4 库存现量（不纳入本单域出库矩阵）

问「还剩多少 / 现货 / 结余 / 门店库存谁高」等 **库存现量** 应为：

- `WAREHOUSE_STOCK_OVERVIEW` + `warehouse_stock_overview_path`

**不纳入** 本节 R01–R13 单域出库矩阵；若出现误路由到出库专线，应作为 **缺陷** 记录，不在 1C 矩阵内当作期望通过。

---

## §3 `stockReduceType` 与 wire 的边界

### 3.1 定义分工

- **`metric.stockReduceType`（解析 JSON）**：出库 **类型 facet**，约定含 `ALL`、`TYPE1`–`TYPE4`，以及 v1 允许的 snake（如 `produce_output`）；与采购 `purchaseSourceType` 无关。
- **`canonicalStructuredIntentDetailWire`**：当前问题的 **子口径 wire**（总览 / 子类 / 排行）。

二者经合并层作用后，**最终子口径以 `queryIntent.structuredIntentDetail` 经 canonical 后的结果为准**。

### 3.2 合并层行为约定（验收依据，不在 1C 改代码）

- **`ALL`**：不得把字面 `ALL` 当作最终 wire；应落到 **`stock_reduce_overview`**（与「全部类型总览」一致）。
- **排行 wire**：`goods_outbound_ranking`、`goods_outbound_count_ranking`、`store_outbound_amount_ranking` **不得**被 `stockReduceType` 覆盖。
- **其余 wire**：若 `metric.stockReduceType` 与已有 wire **冲突**，阶段 1C **先通过用例暴露**，不扩大服务端修改范围。

### 3.3 阶段 1C 策略

冲突类行为以 **R01–R15 实际跑 harness 结果** 为准写入「观测 / 后续」；本矩阵文档只锁定 **期望语义**，不测 SQL/Tool。

---

## §4 建议 `caseId` 与命名

- **套件 / 前缀**：`STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT`
- **用例**：`STOCK_REDUCE_SEMANTIC_1C_R01` … `STOCK_REDUCE_SEMANTIC_1C_R15`（与下表一一对应）

**已注册**：内置 **`caseId`** **`STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT`**（`AiHarnessBuiltinCases#messagesStockReduceSemantic1cResolvedContext()` / `expectationsStockReduceSemantic1cResolvedContext()`）。请求体可省略 **`messages`**（由服务端补全）；**`dryRunStage` 未传**时默认 **`RESOLVED_CONTEXT_ONLY`**。详见 **`docs/AI_HARNESS_REPLAY_CASES.md`**。

实现 harness 时可采用单 JSON 多轮 `rounds[]`，每轮只断言 ResolvedContext 相关字段。

---

## §5 最小矩阵 R01–R15

**通用说明**：下列「期望」均以 **effective** intent/path 与 **canonical** structured wire 为主；多轮行在第二轮验收。

| ID | 场景摘要 | 期望（effectiveIntent / effectivePath / canonicalWire） | 备注 |
|----|----------|----------------------------------------------------------|------|
| **R01** | 本月出库总览 | `STOCK_REDUCE_QUERY` / `stock_reduce_query_path` / `stock_reduce_overview` | `metric.stockReduceType` 可为 null 或 ALL |
| **R02** | 本月核销金额多少 | 同上 / `produce_consume` | 允许解析为 `TYPE1` 再 canonical 到 `produce_consume`；**起止日期已对当前月**时，`effectiveTimeWindowSource` 可为 `INHERITED_PREVIOUS`（见 §8） |
| **R03** | 本月出品耗用多少 | 同上 / `produce_output` | |
| **R04** | 本月废弃金额多少 | 同上 / `waste` | 允许 `TYPE2` canonical |
| **R05** | 本月损失/报损金额多少 | 同上 / `loss` | 允许 `TYPE3` canonical |
| **R06** | 本月退货金额多少 | 同上 / `return` | 允许 `TYPE4` canonical |
| **R07** | 哪些商品出库金额最高 | 同上 / `goods_outbound_ranking` | **不应**因 ALL 或 stockReduceType 覆盖排行语义 |
| **R08** | 哪些商品出库次数最多 | 同上 / `goods_outbound_count_ranking` | |
| **R09** | AAA 和汀兰餐厅哪个出库金额高 | 同上 / `store_outbound_amount_ranking` | 要求 **≥2 店** 被识别或继承并排范围；具体字段以 scope / harness 多店标记为准 |
| **R10** | 哪个门店出库金额最高 | 同上 / `store_outbound_amount_ranking` | 主断言 **`structuredIntentDetailWire`**（`semanticSlots`）；LLM 可输出 **`metric.rankingType`** 作 **debug/deprecated**，**服务端不**以其写 wire |
| **R11** | 时间继承 | 第一轮：同 R01；第二轮：「那上个月呢？」 | 第二轮仍为 `STOCK_REDUCE_QUERY` / `stock_reduce_query_path`；**时间**切到上月（`timeType` 或起止日期对上 LAST_MONTH）；`querySemanticV2TimeAction` **AnyOf**：`OVERRIDE` \| `INHERIT_PREVIOUS` |
| **R12** | 经营 → 出库 | 第一轮：本月经营；第二轮：「那出库呢？」 | 第一轮 wire **AnyOf**：`business_overview_summary` \| `business_overview_status` \| `business_store_status_compare` \| `business_store_status_compare_diagnosis`（多店上下文，见 §8）；第二轮：`STOCK_REDUCE_QUERY` / `stock_reduce_overview`；**时间、scope 可继承** |
| **R13** | 采购 → 核销 | 第一轮：本月采购；第二轮：「那核销呢？」 | 第二轮：`STOCK_REDUCE_QUERY` / `produce_consume`；**不应**仍停留在采购 wire；`querySemanticV2TimeAction` **AnyOf**：`INHERIT_PREVIOUS` \| `OVERRIDE`（不硬绑继承） |
| **R14** | 采购多、出库少 | 「最近采购多但出库少的商品有哪些？」 | `BUSINESS_DIAGNOSIS` / `business_diagnosis_path` / `purchase_stock_reduce_mismatch`（**明确不是** `stock_reduce_query_path`） |
| **R15** | 采购了但没有核销 | 「最近采购了但没有核销的商品有哪些？」 | `BUSINESS_DIAGNOSIS` / `business_diagnosis_path` / **`purchase_slow_moving_risk`**（与 v1/v2「采购后未核销」专节一致；若线上解析偶发落到 `purchase_stock_reduce_mismatch`，阶段 1C 可记 **AnyOf** 二选一并标注为待收紧） |

---

## §6 风险与后续

1. **`stockReduceType` 与 wire 双通道**：解析层 `metric.stockReduceType`、slots wire、合并层覆盖顺序可能导致 **facet 与子口径 wire 不一致**；1C 用 R02/R07 类用例暴露即可。
2. **Harness `stockReduceType` debug**：摘要里的 `stockReduceType` 常与 **`structuredIntentDetail` 的 debug 枚举名（sidCode）** 对齐，在出库 path 下便于比对；**不一定等于** 原始 LLM `metric.stockReduceType` 字符串。断言时需约定比 **`metric.stockReduceType`**（parse 快照）还是比 **摘要顶栏 `stockReduceType`**。
3. **R10 / R09**：门店出库排行以 **`semanticSlots.structuredIntentDetailWire`** 为主断言；`metric.rankingType` 仅 debug（**D-1X-D3**）。
4. **阶段 1C 不处理**：SQL、Tool 选路、`StockReduceAnswerPlan` 行、Composer。
5. **D-1X 收口（Historical 参考）**：时间源 V2-only（**D-CLEAN-V1**）；`metric.rankingType` 主 wire 已收口（**D-1X-D3**）。`effectiveTimeWindowSource` 与 v2 `time.timeSource` Harness 对齐见 **D-1X-B**（可选）。

---

## §8 阶段 1C 收口口径（Harness expectation 放宽，不改业务逻辑）

**原则：1C 只验出库及相关跨域接力的 ResolvedContext 语义层**（intent / path / canonical wire / 时间窗 / 范围 / 多轮标记），不验 Tool、SQL、AnswerPlan、Composer。

1. **最终日期正确优先于 `effectiveTimeWindowSource` debug 口径**  
   若 `startDate` / `endDate` 已与问句时间一致（如 R02 当前月、R11 第二轮上月），Harness **不得**仅因 `effectiveTimeWindowSource` 为 `INHERITED_PREVIOUS` 等非「显式本句」标记而判失败。公共层 `timeSource` 与 `effectiveTimeWindowSource` Harness 对齐属 **D-1X-B（可选）**；**不在** 1C 扩大修 MergeHelper / Resolver。

2. **多店上下文下的经营轮（R12 第一轮）**  
   「这个月经营得怎么样」在集团 / 多店可见范围下，允许落在：
   - `business_overview_summary`
   - `business_overview_status`
   - `business_store_status_compare`
   - `business_store_status_compare_diagnosis`  
   以 **structuredIntentDetail AnyOf** 验收；**不在 1C 改经营 merge / 业务逻辑**。

3. **跨域追问的 `timeAction`（R11 / R13 等）**  
   显式时间切换（如「那上个月呢」）或域切换追问（如「那核销呢」）时，v2 `timeAction` 可在合理枚举间波动；Harness 用 **AnyOf** 而非单值硬绑，只要落地时间窗与 intent/path/wire 正确。

4. **仍属 1C 缺陷（不得用 expectation 掩盖）**  
   canonical wire 错误、intent/path 误路由、日期窗明显错误、采购 wire 未切换至出库/诊断等 **语义错误** 仍应失败。

---

## §7 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-19 | 初稿：阶段 1C RESOLVED_CONTEXT_ONLY 出库语义矩阵与 R01–R15 期望；不含 Java / prompt / test 修改。 |
| 2026-05-19 | 注册内置 Harness **`STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT`**（18 轮，R11–R13 为 2 轮）。 |
| 2026-05-19 | **1C 收口**：Harness expectation 放宽（R02 `timeSource`；R12 经营 wire AnyOf；R13 `timeAction` AnyOf）；新增 §8 收口口径；公共 timeSource 归 D-1X。 |
