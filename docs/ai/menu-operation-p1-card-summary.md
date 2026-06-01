# 菜单顾问 P1 — 卡片与能力汇总

**状态**：P1 后端主链路已完成（2026-05）。  
**用途**：前端联调、新窗口接手、Cursor 后续开发的**单一入口文档**。  
**详细方法论**：`docs/ai/menu-expert-playbook.md`  
**设计背景（含未落地能力）**：`docs/ai/menu-operation-agent-p1-design.md`（其中 MO-D/E/F 等矩阵行 **未** 纳入 P1）

> **Historical**：早期文档与 `docs/修改卡片.md` 曾以根级 `cardPayload` / `cardPayload.data` 为主协议，并以 `actions[]` 列表作为菜单建议主展示。**P1 起正式协议为 `cards[]`**；`menu.action.recommendation.v1` 主卡为**菜单优化方案**结构，非 action list。

---

## 1. P1 已完成的 4 类卡片

| # | `cardType` | 展示名 | 合同 / wire | AnswerPlan `planType` | 典型问法 |
|---|------------|--------|-------------|------------------------|----------|
| 1 | `MENU_PORTFOLIO_QUADRANT_CARD` | 菜单结构四象限 | `menu.operation.overview.v1` · `menu_operation_overview` | `MENU_OPERATION_OVERVIEW` | 这个月菜单经营怎么样？ |
| 2 | `MENU_HIGH_SALES_LOW_MARGIN_CARD` | 畅销低利菜 | `menu.dish.high_sales_low_profit.v1` · `menu_dish_high_sales_low_profit` | `MENU_DISH_HIGH_SALES_LOW_PROFIT` | 哪些畅销菜毛利偏低？ |
| 3 | `MENU_ACTION_RECOMMENDATION_CARD` | 菜单优化方案 | `menu.action.recommendation.v1` · `menu_action_recommendation` | `MENU_ACTION_RECOMMENDATION` | 菜单怎么优化？ |
| 4 | `DISH_PROFIT_PRESCRIPTION_CARD` | 单菜价格与配方诊断 | `dish.profit.prescription.v1` · `dish_profit_prescription` | （独立 Plan，非 MenuOperation） | 见 §1.4 |

**共用 Tool**：菜单顾问三条（1–3）仅消费 **`dish_profit_analysis`** 快照；单菜处方（4）另加 **`dish_cost_analysis`**。

**Path**：`menu_operation_path`（1–3）；单菜处方走 `dish_cost_analysis_path`（成本链 + 处方合同）。

---

### 1.1 `MENU_PORTFOLIO_QUADRANT_CARD`

**壳字段**

| 字段 | 典型值 |
|------|--------|
| `title` | 菜单结构四象限 |
| `chartType` | `PIE` |
| `source.answerPlan` | `menuOperationAnswerPlan` |
| `source.dataRef` | `menuPortfolioClassification` |

**`payload` 主要字段**（`MenuPortfolioClassification` 序列化）

| 字段 | 说明 |
|------|------|
| `totalDishCount` | 本轮分析菜品数 |
| `salesMetricName` / `profitMetricName` | 分层维度字段名（如 `soldPortionsTotal` / `actualProfitAmount`） |
| `salesHighThreshold` / `profitHighThreshold` | 中位数阈值（展示用） |
| `thresholdMethod` | 如 `median` |
| `categories[]` | 四象限分类列表 |
| `categories[].categoryCode` | `STAR` / `TRAFFIC` / `POTENTIAL` / `ELIMINATE` |
| `categories[].categoryName` | 相对明星档 / 引流档 / 潜力档 / 淘汰档 |
| `categories[].count` / `ratio` / `summary` / `recommendedAction` | 数量、占比、摘要、建议动作文案 |
| `categories[].dishes[]` | 可选代表菜：`dishId`, `dishName`, `salesCount`, `salesAmount`, `blendedGrossMarginRateOnListPrice`, `actualProfitAmount`, `reason`, `evidenceRefId` |

**说明**：四象限为**当前 scope 内相对分层**，非绝对行业标准。样本过少时 Harness debug 可能有 `MENU_PORTFOLIO_CLASSIFICATION_SMALL_SAMPLE`；**用户可见文案**由 Renderer 译为中文，勿展示英文 code。

**overview 副卡（Historical 兼容）**：问「这个月菜单经营怎么样？」时，除四象限主卡外，若有可执行行动，可能**追加**一张 `MENU_ACTION_RECOMMENDATION_CARD` 副卡，payload 为旧形 `actions[]`（见 §1.3 副卡形态）。前端应以 `cardType` + payload 键是否存在 `optimizationSummary` 区分主/副形态。

---

### 1.2 `MENU_HIGH_SALES_LOW_MARGIN_CARD`

**壳字段**

| 字段 | 典型值 |
|------|--------|
| `title` | 畅销低利菜 |
| `chartType` | `TABLE` |
| `source.answerPlan` | `menuOperationAnswerPlan` |
| `source.dataRef` | `riskDishes` |

**`payload` 主要字段**

| 字段 | 说明 |
|------|------|
| `status` | 有数据时省略或业务态；无数据时为 `EMPTY` |
| `totalRiskDishCount` | 风险菜数量 |
| `summary` | 一句中文摘要 |
| `dishes[]` | 畅销低利菜行 |
| `dishes[].dishId` / `dishName` | 菜品 |
| `dishes[].salesCount` / `salesAmount` | 销量 / 销售额 |
| `dishes[].blendedGrossMarginRateOnListPrice` | 综合毛利率 |
| `dishes[].actualProfitAmount` | 实际利润 |
| `dishes[].actualCostTotalAmount123` | 实际成本（type123） |
| `dishes[].riskReason` | 风险原因（中文） |
| `dishes[].recommendedAction` | 建议动作（中文，如「压降成本」） |
| `dishes[].evidenceRefId` | 证据引用 |

---

### 1.3 `MENU_ACTION_RECOMMENDATION_CARD`

**壳字段（主链 · `menu.action.recommendation.v1`）**

| 字段 | 典型值 |
|------|--------|
| `title` | 菜单优化方案 |
| `chartType` | `PLAN` |
| `source.answerPlan` | `menuOperationAnswerPlan` |
| `source.dataRef` | `menuOptimizationPlan` |

**`payload` 主要字段（优化方案 · 主展示）**

| 字段 | 说明 |
|------|------|
| `status` | `ACTIVE` / `EMPTY` |
| `optimizationSummary` | 一句话：本月菜单优化重点 |
| `priorityGroups[]` | 按优先级分组 |
| `priorityGroups[].groupCode` | `PRIORITY_HANDLE` / `STABLE_PROMOTE` / `INCREASE_EXPOSURE` / `WATCH_ADJUST` |
| `priorityGroups[].groupName` | 优先处理 / 稳定主推 / 增加曝光 / 观察调整 |
| `priorityGroups[].priority` | 1–4 |
| `priorityGroups[].reason` / `suggestedAction` | 分组说明与建议动作 |
| `priorityGroups[].dishes[]` | 组内菜品（见下） |
| `costReviewDishes[]` | 需复核成本/定价的菜（引流档等） |
| `protectDishes[]` | 稳定主推（明星档） |
| `promotionDishes[]` | 适合加强曝光（潜力档） |
| `watchListDishes[]` | 观察调整（淘汰档） |
| `nextSteps[]` | 2–3 条可执行动作（中文） |
| `evidenceRows[]` | 证据行：`evidenceId`, `displayLabel`, `value`, `unit` |
| `capabilityLimits` | P1 边界机器标记（值为 `NOT_IN_P1`）— **勿原样展示** |
| `summary` | 通常与 `optimizationSummary` 相同 |

**菜品行（各 bucket / `priorityGroups[].dishes[]`）**

`dishId`, `dishName`, `quadrantCode`, `quadrantName`, `soldPortionsTotal`, `listPriceRevenue`, `blendedGrossMarginRateOnListPrice`, `actualProfitAmount`, `suggestedActionLabel`, `reason`, `evidenceRefId`

**副卡形态（Historical · overview / high_sales 追加）**

当 payload **不含** `optimizationSummary`、而含 `actions[]` 时，为旧形行动清单副卡：

| 字段 | 说明 |
|------|------|
| `totalActionCount` | 行动条数 |
| `summary` | 行动汇总句 |
| `actions[]` | `actionType`, `actionName`, `dishId`, `dishName`, `priority`, `reason`, 销量/毛利/利润, `evidenceRefId` |

**前端建议**：主问「菜单怎么优化？」只渲染优化方案形态；收到 overview 双卡时，副卡可折叠或简化为「补充行动」区块，**不要**把 `actions[]` 当作主方案 UI。

**`answerPreview`**：范围/时间 → 优化方案摘要 → 分组菜品 → `nextSteps`；**不含**长篇经营概览指标。详见 `MenuOperationDeterministicRenderer`。

---

### 1.4 `DISH_PROFIT_PRESCRIPTION_CARD`

**归属**：单菜利润处方（`DishProfitPrescriptionAnswerPlan`），与 MenuOperation 三条并列，同属「菜单/菜品经营顾问」产品面。

**典型问法（P1 已验收）**

- 香煎青鱼价格和配方怎么优化？
- 香煎青鱼价格合适吗？
- 香煎青鱼为什么毛利不高？
- 香煎青鱼按 55% 目标毛利率应该卖多少钱？

**字段契约**：见 **`docs/api/frontend-api-contract.md` §7.14** 与 **`docs/ai/dish-profit-prescription-p1-acceptance.md`**。

**展示重点**：当前售价、实际成本、理论成本、目标毛利率、建议售价、配料复核、`recommendedActions[]`（中文 `actionName` / `reasonZh`）。

---

## 2. 统一 `cards[]` 协议

所有结构化卡片经 **`AiCardPayloadWireSupport.refreshAllCardPayloads`** 写入 Run 状态，并在以下位置出现**相同结构**：

| 出口 | 路径 |
|------|------|
| SSE | `run_finished.data.cards[]`、`answer_delta.data.cards[]` |
| GET Run | `/api/ai/runs/{runId}` 根级 `cards[]` |
| 历史消息 | `/api/ai/conversations/{id}/messages` → assistant 消息 `cards[]` |

**单卡元素（stable）**

```json
{
  "cardType": "MENU_ACTION_RECOMMENDATION_CARD",
  "title": "菜单优化方案",
  "subtitle": "…",
  "chartType": "PLAN",
  "payload": { },
  "source": {
    "answerPlan": "menuOperationAnswerPlan",
    "dataRef": "menuOptimizationPlan"
  }
}
```

**Deprecated · 勿扩展**

| 字段 | 说明 |
|------|------|
| 根级 / 消息 `cardPayload` | `{ "cardType", "data" }`，`data` 镜像 `cards[0].payload`；**不单独落库**，不新增字段 |
| Harness 仅 `cardPayload`、无 `cards[]` | Historical 调试形态 |

---

## 3. 历史消息与持久化

- 助手消息卡片快照：`gb_ai_message.gb_ai_message_cards_json`（JSON 数组，与 Run 结束时 `cards[]` 一致）。
- **优先读取** `message.cards[]`；`cardPayload` 仅为读旧客户端时的兼容投影。
- Run 完成时由 `AiRunService` 写入；若 `cards_json` 为空，进程内 Session 可能对**极旧消息** hydrate 兜底，**不保证**所有历史都有卡。
- 2026-05 之前或未走 AnswerPlan 投影的消息：**可能没有卡片**，仅 `content` / `answerPreview` 文本。

---

## 4. 前端责任边界

| 要做 | 不要做 |
|------|--------|
| 按 `cardType` 选组件，**只读** `payload` 渲染 | 在前端重算四象限、主推/降本/下架分类 |
| 缺字段隐藏行，不猜补 | 从 `answerPreview` 正则解析业务数字 |
| `capabilityLimits` / knownGap **映射为中文**说明 | 展示 `NOT_IN_P1`、`MENU_*_NOT_IN_P1` 等英文 code |
| 多卡时按数组顺序渲染（overview 可能 2 张） | 假设每轮只有一张卡 |
| 配料 `reviewFlags` 等内部枚举映射图标/文案 | 原样输出 `USAGE_ABNORMAL` 等给用户 |

**问句入口**：`GET /api/ai/advisors/{id}?scene=MINIAPP` → `questionTopics[].questions[].text`（**Historical 已删**：`GET /api/ai/advisors/{id}/suggested-questions`）。

---

## 5. 后端责任边界

```
用户问句 → LLM 语义 + Contract/Matrix（wire）
         → Tool 查事实（dish_profit_analysis 等）
         → AnswerPlan Builder 确定性业务判断
         → CardSupport 投影 cards[]
         → Renderer 只读 AnswerPlan → answerPreview（中文）
         → AiCardPayloadWireSupport → SSE / GET / 持久化
```

| 层 | 职责 |
|----|------|
| Tool | 事实快照；菜单 P1 **不**在 Tool 内拼最终卡片 |
| AnswerPlan Builder | 四象限、风险菜、优化方案、行动优先级 |
| CardSupport | `MenuOperationAnswerPlanCardSupport` / `DishProfitPrescriptionAnswerPlanCardSupport` |
| Renderer | `MenuOperationDeterministicRenderer` / `DishProfitPrescriptionDeterministicRenderer` |
| LLM Composer | **未**接入 `menu-expert-runtime-prompt.md`；**不**自由判断菜单经营结论 |

**Harness 边界**：Java 不得用 `contains` / alias / remap 猜业务语义（见 `docs/ai/harness-java-boundary-rules.md`）。

---

## 6. P1 能力边界

### 6.1 支持

- 菜单四象限（相对销量 × 实际利润分层）
- 畅销低利菜识别与建议动作
- 菜单优化方案（优先级分组 + nextSteps）
- 单菜价格与配方诊断（处方卡）
- 建议售价（用户给定目标毛利率时）
- 理论成本 vs 实际成本对比
- 配料复核建议（出库均价口径）

### 6.2 不支持（展示统一中文说明）

| 能力 | 结构化标记（Debug / payload，勿直出给用户） |
|------|-----------------------------------------------|
| 最新采购价 | `capabilityLimits.latestPurchasePrice` = `NOT_IN_P1` |
| 外部市场价 | `externalMarketBenchmark` |
| 连续多周期趋势 | `multiPeriodTrend` |
| 跨门店单菜排名 | `crossStoreDishRank`（菜单内 rank 仅限当前 scope） |
| 套餐点单组合分析 | `comboOrderAnalysis` |
| 自动修改配方克数 | 无；仅「配料复核」建议 |

---

## 7. 联调探针（菜单三条 + 处方）

| 问句 | `matchedContractId` | wire | `menuOperationAnswerPlanType` 或处方 | 期望 `cards[0].cardType` |
|------|---------------------|------|----------------------------------------|---------------------------|
| 这个月菜单经营怎么样？ | `menu.operation.overview.v1` | `menu_operation_overview` | `MENU_OPERATION_OVERVIEW` | `MENU_PORTFOLIO_QUADRANT_CARD` |
| 哪些畅销菜毛利偏低？ | `menu.dish.high_sales_low_profit.v1` | `menu_dish_high_sales_low_profit` | `MENU_DISH_HIGH_SALES_LOW_PROFIT` | `MENU_HIGH_SALES_LOW_MARGIN_CARD` |
| 菜单怎么优化？ | `menu.action.recommendation.v1` | `menu_action_recommendation` | `MENU_ACTION_RECOMMENDATION` | `MENU_ACTION_RECOMMENDATION_CARD`（payload 含 `optimizationSummary`） |
| 香煎青鱼价格和配方怎么优化？ | `dish.profit.prescription.v1` | `dish_profit_prescription` | 处方 Plan `SUCCESS` | `DISH_PROFIT_PRESCRIPTION_CARD` |

**权限探针**：`userId=3`, `scopeMode=GROUP`（与 Harness D-11 一致）。

---

## 8. 相关文档索引

| 文档 | 用途 |
|------|------|
| `docs/api/frontend-api-contract.md` §7.14–§7.15 | 前端字段契约 |
| `docs/ai/menu-expert-playbook.md` | 方法论与优化方案设计原则 |
| `docs/ai/dish-profit-prescription-p1-acceptance.md` | 处方卡验收 |
| `docs/AI_HARNESS_REPLAY_CASES.md` | Replay 断言 |
| `docs/修改卡片.md` | 卡片链路实现 checklist（部分用语已 Historical，见文首） |
| `docs/ai/menu-operation-agent-p1-design.md` | 初版设计（**含 P2+ 未实现矩阵行**） |

---

## 9. 前端对接重点（ checklist ）

1. 聊天页 / `storeAiChat`：**以 `cards[]` 为主**，`cardType` switch 至少覆盖上表 4 种。
2. `MENU_ACTION_RECOMMENDATION_CARD`：判断 `payload.optimizationSummary` → 方案卡 UI；否则 fallback `actions[]` 副卡。
3. overview 可能返回 **2 张卡**（四象限 + 行动副卡）。
4. `capabilityLimits`：固定 footer 中文，例如「当前版本暂不提供最新采购价、外部市场比价、连续多周期趋势…」。
5. 历史消息：读 `cards_json`；无则仅展示 `content`，勿报错。
6. 处方卡与菜单卡 **勿混用同一组件**；处方详见 §7.14。
