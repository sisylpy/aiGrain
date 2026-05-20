# D-13.1 — Supplier Drilldown 封版说明

**状态：正式封版（Frozen）**  

本版本仅覆盖 **供货商采购金额排行 → 继承对象与时间的商品明细（含单价诉求）**，不包含门店 / 菜品 / 其它实体下钻（归入 **D-13.2+** 规划）。

## 封版范围

- **主链路**：`resultAnchors` → TurnMemory → Resolver → `PurchaseAnswerPlan` 类型与结构化 wire 对齐。
- **Replay caseId**：`PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3`
- **固定三轮话术**：
  1. 这个月哪个供应商供货金额最高  
  2. 上个月呢  
  3. 采购了哪些商品？单价分别是多少？

## 第三轮验收锚（Harness）

与内置预期 `AiHarnessBuiltinCases.expectationsPurchaseSupplierRankingDrilldownGoodsUnitPrice3` 一致，摘要层至少包含：

| 观测 | 期望值（概念） |
|------|----------------|
| `effectiveTimeWindowSource` | `INHERITED_PREVIOUS`（承接上一轮「上个月」窗） |
| `purchaseSourceType` | `SUPPLIER_PURCHASE` |
| `structuredIntentDetailWire` | `purchase_source_goods_query` |
| `purchaseAnswerPlanType` / `harnessReplayPurchaseAnswerPlanType` | `PURCHASE_SUPPLIER_GOODS_DETAIL` |
| `followUpAction` | `OBJECT_DRILLDOWN` |
| `followUpTargetEntityType` | `SUPPLIER` |
| `followUpTargetEntityName` | 非空（Top 供货商名） |
| `followUpDetailWanted` | `GOODS_UNIT_PRICE` |
| `previousTurnSummary.resultAnchorsCount` | `1`（与锚点条数一致） |

详细 Resolver / Planner 规则见 **[follow-up-action-protocol.md](../follow-up-action-protocol.md)**；锚点产出见 **[result-anchor-protocol.md](../result-anchor-protocol.md)**。

## 不做事项（本版本）

- 不扩展 **STORE / DISH / GOODS** 等非供货商锚点的下钻主链（留待 **D-13.2** 等）。
- 不把本链路做成配置后台或 Skill 商店；仅文档 + Replay + 一键 probe 脚本工程化。

## 一键 probe

```bash
./scripts/harness/probe-supplier-drilldown.sh
```

输出 JSON 默认写入桌面；脚本打印各轮关键字段与 `overallPass`。

---

## D-13 里程碑（跨子版本）

| 子版本 | 锚点 | 状态 | 主要文档 / CaseId |
|--------|------|------|-------------------|
| **D-13.1** | `SUPPLIER` | 已封版 | 本文档；`PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3` |
| **D-13.2** | `STORE` | 已封版 | **[follow-up-action-protocol.md](../follow-up-action-protocol.md)** §10；`BUSINESS_STORE_PRIORITY_DRILLDOWN_REASONS_3` |

**全量 Harness 回归**：`scripts/harness/run-local-replay-regression-bundle.sh` 已纳入 D-13.1 case、D-13.2 case 及营收 / 采购 / 出库 / 菜品毛利 / 经营概览 / 经营诊断 / v2 主链等内置用例；**验收**各 case `overallPass=true`，`PROBE_STORY_7_MULTITURN`（`ignoreExpectations`）`overallPass=null` 属预期。

**明确不继续扩展 D-13.3**（本阶段里程碑收口）。
