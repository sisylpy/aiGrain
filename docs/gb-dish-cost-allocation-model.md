# 菜品成本 / 配料分析：出库分摊模型说明（供业务核对）

本文描述 **`GbDishCostAnalysisServiceImpl`** 中「把本期某分销商商品（`disGoodsId`）的出库重量，摊到报表内各道菜、各配料行」的**数学规则**及**代码入口**，便于判断是否符合门店经营预期。

---

## 1. 适用范围

以下接口/报表共用同一套 **`allocateOutboundWeightForDishGood`** 分摊链（及 **`buildSumNeedByGoods`** 的 `sumNeed`）：

- `POST /gbDishCostAnalysis/report`（`reportKind=salesDish` 等配料行、瓶颈）
- `POST /gbDishCostAnalysis/ingredientAnalysis`（配料分析：`actualProduceUsage` / `actualUsage` 等）
- `POST /gbDishCostAnalysis/outboundIngredientAnalysis`（按商汇总下挂菜品）

出库重量来自部门商品库存扣减汇总（**type1=生产** 用于 `W_g`；配料分析里 **type2、type3** 另参与「按 share 摊到菜」见下文）。

---

## 2. 符号约定（对固定原料 `g`、固定统计区间）

| 符号 | 含义 | 数据来源（概念） |
|------|------|------------------|
| `W_g` | 本期该料 **type1（生产）** 出库重量合计 | `queryProductionReduceAggByDisGoods` → `reduceW.get(g)` |
| `W2_g`, `W3_g` | 本期该料 **type2（损耗）**、**type3（损失）** 出库重量合计 | 按类型汇总扣减 |
| `q_i` | 菜品 `i` 在本报表中的**实销份数** | 菜品销售头表汇总 |
| `dishU_{i,g}` | 菜品 `i` 对原料 `g` 的**合并单份配方用量**（同一菜多条配方同料则 `u` 相加） | `gb_distributer_food_goods` |
| `needThis_{i,g}` | 本菜 `i` 对料 `g` 的**理论总需求** | `needThis = q_i × dishU_{i,g}` |
| `sumNeed_g` | 报表内所有涉及菜对料 `g` 的理论总需求 | **`sumNeed_g = Σ_i (q_i × dishU_{i,g})`**，见 `buildSumNeedByGoods` |

销售子表 `t_{i,g}`、`sumT_g = Σ_i t_{i,g}` 在降级分支使用；主路径以 **`sumNeed`** 为准（与「按配方×销量」一致时，`sumT` 常与 `sumNeed` 相等）。

---

## 3. 主口径：按「理论总需求」比例分摊 type1（生产）出库

**条件**：`sumNeed_g > 0` 且 `W_g > 0`。

**公式**：

```text
alloc1_{i,g} = W_g × needThis_{i,g} / sumNeed_g
            = W_g × (q_i × dishU_{i,g}) / Σ_j (q_j × dishU_{j,g})
```

**含义**：全店（本报表范围内）该料若按配方把各菜「该吃多少」加总得到 `sumNeed_g`，则本期生产出库 `W_g` 按**各菜需求占比**分给每道菜；**不区分谁先卖、谁后卖**，只认需求结构。

**性质**（便于核对是否合理）：

1. **守恒**：`Σ_i alloc1_{i,g} = W_g`（各菜 `alloc1` 相加应等于该料 type1 出库总重；日志 `[ingredientTrace娃娃菜] GLOBAL_SUMMARY` 里 `sumPerDish_alloc1…` 与 `W1` 对齐校验）。
2. **短料等比压缩**：若 `W_g < sumNeed_g`，每道菜摊到的生产量都按同一比例变少；且满足 **`alloc1_{i,g} / dishU_{i,g} ≤ q_i`**（不会因分摊反推「可卖份数」超过实销）。代码注释称「老板短料逻辑」。
3. **同一 `g` 下多道菜**：**`alloc1_{i,g} / needThis_{i,g} = W_g / sumNeed_g`**，与菜 `i` 无关。因此**仅看「生产分摊 ÷ 本菜理论」的利用率时，各菜数值相同**——这是模型推论，不是实现 bug。

**代码位置**：

- 核心实现：`GbDishCostAnalysisServiceImpl.allocateOutboundWeightForDishGood`（分支 `1_N_W*need_div_sumNeed`）
- `sumNeed` 构建：`GbDishCostAnalysisServiceImpl.buildSumNeedByGoods`
- 调用处示例：`buildIngredientAnalysisDishRow`、`collectPerDishAllocs`、`buildSalesDishRow` 等

---

## 4. 降级顺序（当 `sumNeed_g` 无法使用）

在 **`sumNeed_g == 0` 或 `W_g == 0`** 时，按顺序尝试（见 `allocateOutboundWeightForDishGood` 源码与 `[dishCostAlloc]` 日志 `branch=`）：

1. **`2_Q`**：`Q_g > 0` 时 `alloc = W_g × q_i / Q_g`（`Q_g` 为配方含该料且 `u>0` 的各菜实销份数之和，**每菜只计一次**）。
2. **`3_T`**：`sumT_g > 0` 时 `alloc = W_g × t_{i,g} / sumT_g`（按销售子表用量结构分摊）。
3. **`4_S`**：`S_g > 0` 时 `alloc = W_g × dishU_{i,g} / S_g`（`S_g = Σ_i dishU_{i,g}`，仅配方单份用量占比）。
4. **`0_none`**：否则 `alloc = 0`。

---

## 5. type2 / type3 如何摊到「本菜本料」（配料分析）

在 **`ingredientAnalysis`** 等路径上，在得到 **`alloc1`（仅 type1）** 之后：

```text
share_{i,g} = alloc1_{i,g} / W_g     （当 W_g > 0；否则 share = 0）
alloc2_{i,g} = share_{i,g} × W2_g
alloc3_{i,g} = share_{i,g} × W3_g
actual123_{i,g} = alloc1_{i,g} + alloc2_{i,g} + alloc3_{i,g}
```

**含义**：**损耗、损失**在全店该料上的出库重量，按「本菜占到的 **type1 生产出库份额**」同一比例摊到各菜；**不是**按每道菜单独的真实损耗台账拆分。

**接口字段**（配料行）：

- `actualProduceUsage` ≈ `alloc1`
- `actualWasteUsage` / `actualLossUsage` ≈ `alloc2` / `alloc3`
- `actualUsage` ≈ `actual123`

当前 **`utilizationRate`** 约定为：**仅 `alloc1 ÷ 本菜配方理论`（做菜/制作口径，不含 type2、3 进分子）**，见 `data.disclaimerZh`。

---

## 6. 业务核对清单（判断「是否合理」时可自问）

1. **共料是否应按「需求占比」分？**  
   若认为应按**销量**、按**销售额**、或按**部门责任**分货，则与当前「`q×dishU` 需求占比」不一致，需改模型或另做一张分析表。

2. **是否接受「同一原料多道菜利用率相同」？**  
   在主分支下，**`alloc1/理论` 对同一 `g` 各菜相同**；若希望每道菜不同，必须有**能落到单菜的出库或消耗计量**（或改分摊规则），不能仅靠本模型。

3. **type2/3 与 type1 同 share 是否合理？**  
   这是「损失与生产绑定同一分配键」的简化；若损耗主要来自中央厨房而非与销量同结构，可能失真。

4. **`sumNeed` 与报表范围**  
   `sumNeed` 只含**进入本报表的菜品集合**；增删菜、改日期会改变分母，从而改变分摊。

---

## 7. 日志与字段辅助核对

- `[dishCostAlloc]`：`allocateOutboundWeightForDishGood` 每次分摊一行（`branch`、各参数、`allocW`）。
- `[ingredientTrace娃娃菜]`：针对配方名含「娃娃菜」的配料，输出 `PER_DISH` 与 `GLOBAL_SUMMARY`（出库、分摊、利用率等）。

---

## 8. 相关常量

扣减类型定义见 **`GbConstants.StockReduceType`**（type1 生产、type2 损耗、type3 损失等）。

---

## 9. FAQ：「每份 0.2÷0.3」与接口里 50% 不一致？

接口里（配料行）已显式给出：

- **`recipeUnitPerDish`**：单份配方用量（如大娃娃菜 **0.3 斤/份**）。
- **`produceAllocatedPerSoldPortion`**：**仅 type1** 摊到本菜后，再除以本菜实销份数 `q`，即 **`alloc1 ÷ q`**（斤/每实销一份）。
- **`utilizationRate`**：**`alloc1 ÷ theoryUsage`**，其中 `theoryUsage = q × recipeUnitPerDish`。  
  因此与 **「每份生产摊销 ÷ 每份配方」** 数学上**完全等价**：  
  `(alloc1/q) / recipeUnitPerDish = alloc1 / (q×recipeUnitPerDish)`。

**示例（与某次日志一致）**：烩菜 `q=5`，大娃娃菜 `recipeUnitPerDish=0.3`，`W1=3`，两菜对该料 `sumNeed=6`，本菜 `needThis=1.5`。

- `alloc1 = W1 × needThis / sumNeed = 3 × 1.5 / 6 = 0.75`（**整段期本菜该料生产摊销合计**，不是 1.0）。
- **`produceAllocatedPerSoldPortion = 0.75 / 5 = 0.15` 斤/份**（不是 0.2）。
- **`utilizationRate = 0.75 / 1.5 = 0.15 / 0.3 = 50%`**（不是 0.2÷0.3 的 66.67%）。

若要出现 **66.67%**，在 **`theoryUsage=1.5` 不变**的前提下，需要 **`alloc1=1.0`**（即 **0.2 斤/份 × 5**）。但在 **`W1=3` 且 sumNeed 按 1.5:4.5 分两菜** 时，烩菜按比例只能分到 **0.75**；若强行按「烩菜应吃 1.0」分，则另一菜或总账会与 **`Σ alloc1 = W1`** 矛盾，除非 **`W1` 或 sumNeed 口径**与系统不一致。

**干锅娃娃菜**：同一模型下本菜该料 **`theoryUsage = 3 × 1.5 = 4.5`**，**`alloc1 = 2.25`**（**每份 0.75 斤**生产摊销），**`utilizationRate = 2.25/4.5 = 50%`**。若您手头的「理论 1.5、实际 0.8」指的是**别的口径**（例如只看了单份配方、或另一时间段出库、或未含全部实销份数），请与接口里 **`theoryUsage`、`actualProduceUsage`、`produceAllocatedPerSoldPortion`** 对齐后再比。

---

*文档与 `GbDishCostAnalysisServiceImpl`（约 `allocateOutboundWeightForDishGood`、`buildSumNeedByGoods`）保持一致；若代码调整公式，请同步更新本文。*
