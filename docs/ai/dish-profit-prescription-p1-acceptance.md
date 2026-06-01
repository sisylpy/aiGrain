# 单菜利润处方卡（DISH_PROFIT_PRESCRIPTION）— 后端 P1 验收记录

**状态**：后端 P1 主链路 **已通过**（2026-05-27，`userId=3` + `scopeMode=GROUP` full probe）。  
**菜单顾问 P1 四卡汇总**：**`docs/ai/menu-operation-p1-card-summary.md`**  
**范围**：不含 P2/P3（最新采购价、外部市场价、跨店排名等见下文「P1 能力边界」）。

---

## 探针参数（与 D-11 权限一致）

| 参数 | 值 | 说明 |
|------|-----|------|
| `userId` | `3` | GROUP 视角店长/经理 fixture；**勿**用 `userId=1`（D-11 为 `WAREHOUSE_MANAGER`，会拒 `dish_profit_analysis`） |
| `scopeMode` | `GROUP` | |
| `distributerId` | `2` | |
| `replayMode` | `GRAPH_RUN` | 或 `POST /api/ai/runs` 全链 |
| `frozenClockDate` | `2026-05-13` | |
| `strictStoreSqlMatch` | `false` | |

Harness：`POST /api/ai/harness/replay`，`ai.harness.replay-enabled=true`。

---

## 已通过问句（3 条）

### 1. 处方主问句

**User**：`香煎青鱼价格和配方怎么优化？`

| 探针 | 预期 | 实测 |
|------|------|------|
| `semanticContractValidation.matchedContractId` | `dish.profit.prescription.v1` | ✅ |
| `structuredIntentDetailWire` | `dish_profit_prescription` | ✅ |
| `dishProfitPrescriptionAnswerPlanStatus` | `SUCCESS` | ✅ |
| `cards[0].cardType` | `DISH_PROFIT_PRESCRIPTION_CARD` | ✅ |
| `usedTools` | 含 `dish_profit_analysis` + `dish_cost_analysis` | ✅ |

### 2. 目标毛利率槽位透传

**User**：`香煎青鱼按55%目标毛利率应该卖多少钱？`

| 探针 | 预期 | 实测 |
|------|------|------|
| 合同 / wire | 同 `dish.profit.prescription.v1` / `dish_profit_prescription` | ✅ |
| `requestedTargetGrossMarginRate` | `55.0`（摘要 / 解析链） | ✅ |
| `suggestedPrice.targetGrossMarginRate` | 与请求或标准目标一致 | ✅ |
| `cards[0].cardType` | `DISH_PROFIT_PRESCRIPTION_CARD` | ✅ |

### 3. 旧成本路径未回归

**User**：`香煎青鱼成本怎么样？`

| 探针 | 预期 | 实测 |
|------|------|------|
| `matchedContractId` | `dish_cost.single_dish_analysis`（或矩阵等价 id） | ✅ |
| wire | `dish_cost_analysis` | ✅ |
| `cards[0].cardType` | `DISH_COST_ANALYSIS_CARD` | ✅ |
| 处方 Plan | **不应**出现 `dishProfitPrescriptionAnswerPlanPresent=true` | ✅ |

---

## P1 能力边界（产品 / 前端须知）

当前版本 ** intentionally 不提供**：

- **最新采购价**（配料单价为出库均价口径，`unitPriceSource=OUTBOUND_TYPE1_AVG`）
- **外部市场比价 / 基准价**
- **跨门店菜品排名**（菜单内排名基于当前 scope 返回的 `dishRows`，非全集团跨店榜）

上述限制在 AnswerPlan `knownGaps`（Harness debug）与 `capabilityLimits`（卡片 payload）中有结构化标记；**用户可见 `answerPreview` 仅输出中文说明**，不暴露英文 gap code（见下节）。

---

## answerPreview 与 knownGap

- **Composer 主链**：`StubAnswerComposerNode` → `DishProfitPrescriptionDeterministicRenderer`。
- **规则**：`knownGaps` 英文 code **仅** 用于 Harness / Plan debug；正文将 P1 边界译为「当前版本暂不提供最新采购价、外部市场比价和跨门店菜品排名…」。
- **Harness debug**（非 answerPreview）：`harnessDebug.dishProfitPrescriptionKnownGaps` — 前台 Debug 面板可折叠展示，**勿**拼进聊天气泡。
- **单测**：`DishProfitPrescriptionDeterministicRendererTest#render_doesNotExposeKnownGapCodesInUserText`。

---

## 前端下一步

小程序 / Web 需识别并渲染 **`DISH_PROFIT_PRESCRIPTION_CARD`**。字段契约见 **`docs/api/frontend-api-contract.md` §7.14**。

---

## 相关实现（只读索引）

| 层 | 类 |
|----|-----|
| 合同 / 矩阵 | `DishCostAnalysisSemanticCapabilityMatrix`、`DishCostAnalysisSemanticCapabilityContractExporter` |
| AnswerPlan | `DishProfitPrescriptionAnswerPlanBuilder`、`DishProfitPrescriptionAgentNode` |
| 卡片投影 | `DishProfitPrescriptionAnswerPlanCardSupport` |
| Run 出口 | `AiCardPayloadWireSupport`、`AiRunService` |
| 宣读 | `DishProfitPrescriptionDeterministicRenderer` |
