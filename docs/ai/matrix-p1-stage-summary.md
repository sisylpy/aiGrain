# Matrix P1 阶段收口说明（Composer Plan-first）

> **用途**：新窗口接手时快速了解本轮 Matrix P1 / Composer Plan-first 的完成度、待验收项、knownGap 与禁止恢复项。  
> **范围**：文档收口 only；**不**代表最新一次本地 replay 已通过（见 §4）。

**相关契约**：

| 域 | 契约文档 | Harness caseId |
|----|----------|----------------|
| 菜品毛利 | `dish-profit-drilldown-matrix-contract.md` | `DISH_PROFIT_MATRIX_P1` |
| 营业额 | `revenue-drilldown-matrix-contract.md` | `REVENUE_MATRIX_P1` |
| 出库 | `stock-reduce-drilldown-matrix-contract.md` | `STOCK_REDUCE_MATRIX_P1` |
| 库房 | `warehouse-drilldown-matrix-contract.md` | `WAREHOUSE_MATRIX_P1` |
| 菜品销量 | `dish-sales-drilldown-matrix-contract.md` | `DISH_SALES_MATRIX_P1` |

**架构总览**：`harness-composer-architecture.md`  
**已删资产索引**：`LEGACY_AI_ANSWER_ASSETS.md`、`docs/legacy-reference/*-removed.md`

---

## 1. 当前已完成的 Matrix P1

| 域 | caseId | 实现状态 | 验收状态 |
|----|--------|----------|----------|
| **DishProfit** | `DISH_PROFIT_MATRIX_P1` | 矩阵 + Plan-first Composer 已落地 | **已 strict 通过**（历史记录；复验可用 `replay-single-case.sh`） |
| **Revenue** | `REVENUE_MATRIX_P1` | 矩阵 + Plan-first 已落地 | **已单项通过** |
| **StockReduce** | `STOCK_REDUCE_MATRIX_P1` | 矩阵 + Plan-first 已落地 | **已实现，待数据库稳定后单项验收** |
| **Warehouse** | `WAREHOUSE_MATRIX_P1` | 矩阵 + Plan-first 已落地 | **已实现，待数据库稳定后单项验收** |
| **DishSales** | `DISH_SALES_MATRIX_P1` | 矩阵 + V2 收养兜底 + Plan-first 已落地 | **已实现；因 DB 连接中断暂缓本地 replay 验收** |

**说明**：

- 「已实现」= 代码、Harness 预期、契约文档、replay 脚本已齐，**不等于**最近一次环境跑通。
- **Purchase** 本轮以追问矩阵 / Planner 适配为主，**无**独立 `*_MATRIX_P1` case；Composer 已 Plan-first（见 §2）。
- **Diagnosis（经营诊断）** 走 `DiagnosisAnswerPlan` + Composite strict（C-35 / C-48 / C-42），非本表 Matrix P1 十轮，但 Composer 同样 Plan-first。

---

## 2. Composer Plan-first 收口状态

### 2.1 统一原则

各业务域 **均已不再** 由 Composer 从 `toolResults` / raw Tool envelope **拼业务事实**（排行 Top3、金额汇总、毛利率心算等）。

| 路径 | 行为 |
|------|------|
| **有 AnswerPlan** | Composer **只宣读** Plan 内已算字段、`focusRows`、`limitations`、`knownGap` 宣读段 |
| **无 AnswerPlan** | 各域 **固定 no-plan** 文案（`StubAnswerComposerNode.compose*NoPlanFallback` 等） |
| **禁止** | 恢复 **LLM + Tool fallback** 拼正文；恢复已删 `*DeterministicRenderer` 从 tool 拼事实 |

### 2.2 按域（现网 Tool → Plan → Composer）

| 域 | AnswerPlan | 现网 Tool（示例） | 无 Plan |
|----|------------|-------------------|---------|
| Purchase | `PurchaseAnswerPlan` | `purchase_overview` 等 | no-plan |
| Revenue | `RevenueAnswerPlan` | `revenue_query` | no-plan |
| StockReduce | `StockReduceAnswerPlan` | `stock_reduce_query` | `composeStockReduceNoPlanFallback` |
| DishProfit | `DishProfitAnswerPlan` | `dish_profit_analysis` | `composeDishProfitNoPlanFallback` |
| DishSales | `DishSalesAnswerPlan` | `dish_profit_analysis`（销量口径） | no-plan |
| Warehouse | `WarehouseAnswerPlan` | `warehouse_stock_overview` | no-plan |
| Diagnosis | `DiagnosisAnswerPlan` | Composite 多域物化 | 诊断专用宣读 / gap |

### 2.3 仍保留的确定性 Renderer

`DeterministicAnswerRenderer` 及 **仍存在的** 域 Renderer（如营收/毛利/采购/诊断部分路径）仅 **宣读 AnswerPlan + canonical wire**，与 Composer 同边界。  
**已删除且禁止恢复**：`StockReduceDeterministicRenderer`、`WarehouseDeterministicRenderer`、`PurchaseDeterministicRenderer`（见 §7）。

---

## 3. 各 Matrix 的 replay 脚本

统一依赖：`scripts/harness/replay-harness-common.sh`（footer 输出 **`caseId` / `overallPass` / `failureCount`**）。

**推荐调用方式**（无需 `chmod +x`）：

```bash
bash scripts/harness/replay-<domain>-matrix-p1.sh
# 可选：API_BASE=http://localhost:8090/api REPLAY_OUT_DIR=.../out/...
```

| 域 | 脚本 | caseId | 默认输出目录 |
|----|------|--------|--------------|
| Revenue | `scripts/harness/replay-revenue-matrix-p1.sh` | `REVENUE_MATRIX_P1` | `out/replay-revenue-matrix-p1/` |
| StockReduce | `scripts/harness/replay-stock-reduce-matrix-p1.sh` | `STOCK_REDUCE_MATRIX_P1` | `out/replay-stock-reduce-matrix-p1/` |
| Warehouse | `scripts/harness/replay-warehouse-matrix-p1.sh` | `WAREHOUSE_MATRIX_P1` | `out/replay-warehouse-matrix-p1/` |
| DishSales | `scripts/harness/replay-dish-sales-matrix-p1.sh` | `DISH_SALES_MATRIX_P1` | `out/replay-dish-sales-matrix-p1/` |
| **DishProfit** | `scripts/harness/replay-single-case.sh` | `DISH_PROFIT_MATRIX_P1` | `out/replay-single-case/`（默认） |

**DishProfit 示例**：

```bash
bash scripts/harness/replay-single-case.sh DISH_PROFIT_MATRIX_P1 \
  "第 1 轮问句" "第 2 轮" "第 3 轮" "第 4 轮"
```

问句顺序以 `AiHarnessBuiltinCases.messagesDishProfitMatrixP1()` 与 `dish-profit-drilldown-matrix-contract.md` 为准。

**其它相关脚本（非 Matrix P1 十轮，仅供参考）**：

- `scripts/harness/replay-dish-followup-core.sh` — 含 `DISH_PROFIT_AGENT_GRAPH_CORE` 等追问 core，**不是** `DISH_PROFIT_MATRIX_P1` 矩阵严格集。
- `scripts/harness/run-local-replay-regression-bundle.sh` — 回归 bundle，含多域 core case。

**前置**：服务已启动；`ai.harness.replay-enabled=true`；建议安装 `jq`。

---

## 4. 暂缓验收原因

### 4.1 DishSales Matrix P1

- 最近一轮本地 replay 出现 **HTTP 500**，根因为 **MySQL `Communications link failure`**（连接不稳定 / 断链），**不是** 业务路由或 Matrix 语义错误。
- V2 语义 LLM 偶发 **SSL 握手失败** 时，会 `parseMissing`；代码层已加 **Matrix 收养兜底**（门店+单菜 / 集团单菜 / 排行追问 / 首轮问句 pin 等），**不应**因环境 LLM/DB 问题回滚 DishSales 实现。
- **后续**：数据库与后端连接稳定后，**单独**重跑：

  ```bash
  bash scripts/harness/replay-dish-sales-matrix-p1.sh
  ```

- **明确禁止**：因本次 HTTP 500 / DB 故障 **回滚** DishSales Matrix 或 Composer Plan-first 相关提交。

### 4.2 StockReduce / Warehouse Matrix P1

- 实现与 Harness 预期已就绪；与 DishSales 类似，依赖 **稳定 DB + 全链路 Graph**。
- DB 稳定后分别执行：

  ```bash
  bash scripts/harness/replay-stock-reduce-matrix-p1.sh
  bash scripts/harness/replay-warehouse-matrix-p1.sh
  ```

### 4.3 运行时类加载注意

若修改 `DishSalesDrilldownMatrix` 等后 **未全量重编**，可能出现 `NoSuchMethodError`（新旧 class 混载）。**必须停服 + 全量编译后再启动**，不要仅热替换单个 class。

---

## 5. DishSales 后续待验重点

Harness：`DISH_SALES_MATRIX_P1`（10 轮 A–J）。契约：`dish-sales-drilldown-matrix-contract.md`。

| 轮次 | 矩阵行 | 验收要点 |
|------|--------|----------|
| **R4** | **DS-D** | 集团单菜：`dish_sales_single_dish` / `DISH_SALES_SINGLE_DISH`；`mentionedDishName`（如 核桃芽菜西芹）；**不**落门店 scope |
| **R6** | **DS-F** | 门店+单菜：`dish_sales_store_single_dish`；`scope=STORE`、`mentionedStore=AAA`；**即使库内无该菜**，语义层也须 `DISH_SALES_QUERY` + `dish_sales_query_path`（无数据由 Tool/Plan 表达） |
| **R8** | **DS-H** | 排行追问：`dish_sales_count_ranking_high`；继承上轮 dish_sales path；V2 parse 失败时 Matrix 收养 |
| **R9** | **DS-I** | 跨域「那毛利呢」：留在 `dish_sales_query_path`；须暴露 **knownGap** `DISH_SALES_CROSS_DOMAIN_DISH_PROFIT_NOT_IN_P1`；**不**切 `dish_profit_path` |

**全局断言**：

- `consumedAnswerPlans` **只含** `DishSalesAnswerPlan`
- **不得出现** `DishProfitAnswerPlan`、`AGGREGATED_DISH_PORTFOLIO_FALLBACK` 或 portfolio 级毛利兜底
- `dishSalesKnownGap`：R9/R10 等轮须 **有值**（能力边界），preview **不得**假装已实现

**语义层收养（LLM 不可用时）**：`DishSalesDrilldownMatrix` + `AiQuerySemanticLlmMergeHelper` + `AiResolvedQueryContextResolver.trySemanticAdoption`；**非**全局 `contains` 抢权。

---

## 6. knownGap 总表

**定义**：`knownGap` 表示 **P1 已约定语义/wire、但执行链或 Tool/SQL 尚未完整实现** 的能力边界。Harness 要求 gap **被宣读或摘要暴露**，**不是** `overallPass` 假成功。

### 6.1 Revenue

| knownGap code | 场景 | 说明 |
|---------------|------|------|
| `REVENUE_STORE_COMPARE_NOT_PAIRWISE_ONLY_RANKING` | RV-D 两店对比 | 仅门店排行，无 pairwise compare 专链 |
| `REVENUE_PERIOD_COMPARE_MO_M_NOT_IMPLEMENTED` | RV-H 本月和上月比 | 无 period_compare plan / 双窗 SQL |
| `REVENUE_DAILY_RANKING_ARGMAX_DATE_MISSING` | RV-I 哪天最高 | 无日历日 argmax |
| `REVENUE_TREND_SERIES_NOT_IMPLEMENTED` | RV-J 趋势 | 无日序列 trend plan |

### 6.2 StockReduce

| knownGap code | 场景 | 说明 |
|---------------|------|------|
| `GOODS_WASTE_RANKING_TYPE2_SQL_NOT_FILTERED` | SR-GW / K | 语义要废弃商品排行，harness SQL 未按 type2 严格过滤 |

### 6.3 Warehouse

| knownGap code | 场景 | 说明 |
|---------------|------|------|
| `WAREHOUSE_OUT_OF_STOCK_STRICT_NOT_SUPPORTED` | WH-F 缺货 | `lowStockItems` 为启发式，非严格缺货 |
| `WAREHOUSE_NEAR_EXPIRY_NOT_IN_TOOL` | WH-G 临期 | Tool 无保质期/临期专链 |

### 6.4 DishSales

| knownGap code | 场景 | 说明 |
|---------------|------|------|
| `DISH_SALES_CROSS_DOMAIN_DISH_PROFIT_NOT_IN_P1` | DS-I 那毛利呢 | 销量域内跨域毛利，不假装 DishSales 已算毛利 |
| `DISH_SALES_TREND_SERIES_NOT_IMPLEMENTED` | DS-J 趋势 | 无日序列销量 trend plan |

### 6.5 DishProfit / Purchase

- **DishProfit Matrix P1**：以四轮下钻 **strict** 为主；窄口径 Plan 宣读已收口，**无** 本表级 P1 knownGap 十轮集（见毛利契约）。
- **Purchase**：追问矩阵 knownGap 见 `purchase-drilldown-matrix-contract.md`（本轮未列入上表五域 Matrix P1 脚本书）。

---

## 7. 禁止恢复清单

以下资产 **已删除** 或 **已断开 wiring**。**禁止** 以「恢复 fallback / 先跑通 Harness」为由加回。

### 7.1 已删类

| 类 | 说明 |
|----|------|
| `PurchaseDeterministicRenderer` | 采购 raw-tool 拼正文 |
| `StockReduceDeterministicRenderer` | 出库 tool envelope 拼正文 |
| `WarehouseDeterministicRenderer` | 库房 tool 拼 Top3/概览 |
| `AnswerComposerPayloadFactory` | 从多域 tool 拼 Composer payload |

索引：`docs/legacy-reference/stock-reduce-deterministic-renderer-removed.md`、`LEGACY_AI_ANSWER_ASSETS.md`。

### 7.2 已删 / 禁止恢复的 DeterministicAnswerRenderer 方法

| 方法 | 域 |
|------|-----|
| `renderPurchaseCostFallback` | 采购 |
| `renderDishProfitFallback` | 毛利（改用 Plan 宣读 / no-plan） |
| `renderRevenueEnvelopeFallback` | 营收 |
| `renderStockReduceToolFallback` | 出库 |
| `renderWarehouseStockFallback` | 库房 |

### 7.3 禁止的模式

- **LLM + Tool fallback**：Composer 在 Plan 缺失时 **不得** 回退为读 `toolResults` 让 LLM 编业务数字/排行。
- **`scripts/gen_deterministic_renderer.py`**：禁止重新生成上述 Renderer / fallback 方法（文件头已列禁止项）。
- **Classic business overview** 主链与 `business_overview_query` 等已删 Tool（见 `legacy-reference/`）。

### 7.4 仍允许（非 fallback）

- `AiPromptIds.COMPOSER_*_V1`：LLM **宣读 AnswerPlan** 的系统提示注册 id，**不是** raw-tool fallback。
- 仍保留的 `*DeterministicRenderer`（营收/毛利等）：仅 **Plan-first 宣读**，不拼 tool 事实。

---

## 8. 下一阶段建议（仅计划，未实现）

| 方向 | 说明 |
|------|------|
| **跨域追问 Matrix P2** | 如销量→毛利、营收→采购等跨域 wire 与 knownGap 矩阵化；避免 ad-hoc pin |
| **经营诊断下钻 Matrix** | Diagnosis 多轮下钻与 Composite step 的 Harness 矩阵化（与 C-35/C-48 对齐） |
| **Prompt Registry legacy id 瘦身** | 梳理 `COMPOSER_*_V1` 与已删路径的注册项，文档化「仅 LLM 宣读 Plan」 |
| **WarehouseAnswerPlan 增强** | 严格缺货、临期、排行字段与 Tool 能力对齐（消 knownGap F/G） |
| **KnownGap → SQL/Tool 补齐** | 按 §6 表逐项：Revenue period/daily/trend、StockReduce type2 过滤、Warehouse 临期等 |

**不在 P2 默认范围**：恢复 §7 任一 fallback；扩大全局 `contains` 语义抢权。

---

## 变更记录（文档）

| 日期 | 说明 |
|------|------|
| 2026-05-20 | 初版：Matrix P1 阶段收口、暂缓验收、knownGap、禁止恢复、脚本索引 |
