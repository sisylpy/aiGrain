# 订货习惯提醒接口（前端）

本文描述 **`GbDepartmentDisGoodsController#depReorderReminderPage`** 的请求与响应，供订货端小程序 / H5 联调。

设计要点：**以历史到货订单推断订货节奏（间隔天、习惯订货量）**；**库存与损耗类提示为辅**，放在 `aiAuxHints`。列表 **仅包含「今日建议关注订货」的商品**：与 `aiShouldRemindToday === "true"` 一致（先到习惯订货日 / 或单次订货场景下库存偏低）；SQL 仅筛「窗口内到货次数」候选，最终仍以该字段为准。

---

## 1. 基本信息

| 项目 | 说明 |
|------|------|
| 方法与路径 | `POST /gbdepartmentdisgoods/depReorderReminderPage` |
| Content-Type | `application/x-www-form-urlencoded` 或带同名参数的表单 POST（与项目现有 `@RequestParam` 用法一致） |
| 统一返回 | `R`：`code === 0` 成功；成功时含 **`page`**（分页对象） |

---

## 2. 请求参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `depId` | Integer | 是 | — | 部门 ID（`gb_department.gb_department_id`） |
| `page` | Integer | 是 | — | 页码，从 **1** 开始 |
| `limit` | Integer | 是 | — | 每页条数 |
| `windowDays` | Integer | 否 | **56** | 统计订货习惯的回溯天数（到货日在 \[today − windowDays + 1, today\]） |
| `minTimes` | Integer | 否 | **2** | 窗口内至少 **到货订货次数** 达到该值才会出现在候选列表（并按次数降序分页） |

---

## 3. 响应结构（成功）

顶层与普通分页接口一致，便于沿用 **`depGetDepGoodsGbPage`** 的解析逻辑：

```json
{
  "code": 0,
  "page": {
    "totalCount": 128,
    "totalPage": 7,
    "currPage": 1,
    "pageSize": 20,
    "windowDays": 56,
    "minTimes": 2,
    "list": []
  }
}
```

---

## 4. `page.list[]` 单条：`GbDepartmentDisGoodsEntity`

列表项在 **`depQueryDepGoodsWithOrderForAi` 同源结构**（含 `gbDistributerGoodsEntity`、库存汇总字段等）基础上，增加 **订货习惯与 AI 辅助字段**。

### 4.1 部门商品与库存（原有，节选）

| 字段 | 说明 |
|------|------|
| `gbDepartmentDisGoodsId` | 部门商品主键 |
| `gbDdgDepGoodsName` | 部门侧商品名 |
| `gbDdgStockTotalWeight` | **本接口返回前**会按 `gb_department_goods_stock.gb_dgs_rest_weight` **按部门商品汇总重写**，不再直接使用 `gb_department_dis_goods` 表内台账字段（后者可能与批次不一致或为负）。与 `aiCurrentStock` 一致。 |
| `gbDdgOrderStandard` | 订货规格单位（可与习惯量单位一并展示） |
| `gbDistributerGoodsEntity` | 批发商商品（含图等） |

### 4.2 订货习惯（新增 / 重组）

| 字段 | 类型 | 说明 |
|------|------|------|
| `aiHabitIntervalDays` | String | 推断的平均订货间隔（天，整数串）；无法算出时可为空串 |
| `aiNextHabitOrderDate` | String | 按「上次到货日 + 间隔」推算的下次习惯订货日，`yyyy-MM-dd`；无法算出时为空串 |
| `aiShouldRemindToday` | String | `"true"` / `"false"`：**习惯到货间隔已到** 或 **单次订货场景下库存低于约 2 天估算消耗**（见 `aiRemindLowStockBelowTwoDayUsage`）任一为真即为 true |
| `aiStockEstimateDailyUsage` | String | 仅当窗口内 **恰好 1 笔**已收货订单时可能有值：日均估算 = `(最近一单 gb_do_weight − 当前批次剩余汇总) / max(1, 距到货天数)` |
| `aiRemindLowStockBelowTwoDayUsage` | String | `"true"`：`库存 < 2 × aiStockEstimateDailyUsage`（且估算日均 > 0）时建议提醒 |
| `aiRemindReason` | String | `none` / `habit` / `stock_below_two_day_usage` / `habit,stock_below_two_day_usage` |
| `aiOrderQuantity` | String | 窗口内订单 **`gb_do_weight` 的中位数**（习惯参考订货量，两位小数） |
| `aiOrderStandard` | String | 规格展示，与部门订货规格对齐 |
| `aiDailyUsage` | String | 有习惯间隔：**「约每 X 天订一次货」**；否则单次订货可能为 **「单次订货：估算日均约 …」** |

第 **1** 页不足 `limit` 条时，会在主候选之外 **追加**「窗口内仅 1 笔到货且满足库存低于约 2 天估算消耗」的部门商品（`totalCount` 仍以「到货次数 ≥ minTimes」候选为准）。

### 4.3 库存 reduce 推算（与订货窗口日期一致）

数据来源：`gb_department_goods_stock_reduce`，与接口 **`windowDays`** 决定的 **`startDate`～`stopDate`**（含首尾）一致。类型与 `GbConstants.StockReduceType` 对齐：**生产 1、废弃(WASTE) 2、损失成本(LOSS) 3、退货 4**。

| 字段 | 说明 |
|------|------|
| `aiReduceProductionDailyAvg` | 窗口内 **type=1（生产）** 出库重量合计 ÷ **窗口天数**（首尾含当日），日均 |
| `aiReduceLossWasteDailyAvg` | 窗口内 **type=2（废弃）+ type=3（损失成本）** 出库重量合计 ÷ 窗口天数，日均 |
| `aiRecommendCoverDaysUsed` | 下面两个「缺口」所用的覆盖天数，当前固定为 **`"3"`**（与习惯间隔无关，仅作理性补货参考） |
| `aiRecommendGapWeightProductionOnly` | **主线**：`(生产日均 × 覆盖天数) − 当前库存`，不足则为 **0**；库存口径与 `gbDdgStockTotalWeight`（批次汇总）一致 |
| `aiRecommendGapWeightWithLossWaste` | **补充**：`(生产日均 + 废弃与损失日均) × 覆盖天数 − 当前库存`，不足则为 **0**；用于「若把废弃与损失算进消耗」时的备货参考 |
| `aiEstimateDepleteDateProductionOnly` | 假定按 **生产日均** 匀速消耗当前库存，线性推算的耗尽日 `yyyy-MM-dd`（速率过小则无值） |
| `aiEstimateDepleteDateWithLossWaste` | 假定按 **生产+废弃+损失** 合计日均匀速消耗，耗尽日（通常 **不晚于** 仅生产口径，便于提前备货） |

### 4.4 兼容展示（可为占位）

| 字段 | 说明 |
|------|------|
| `aiSafetyStock` | 固定 **`"-"`**（本接口不以安全库存公式为主） |
| `aiReorderPoint` | **`"-"`** |
| `aiTomorrowNeed` | **`"-"`** |
| `aiCurrentStock` | 与 `gbDdgStockTotalWeight` 一致，便于旧模板绑定 |
| `aiDaysSinceLastOrder` | 距**最后一次到货日**的天数 |
| `aiLastOrderDate` | 最后一次到货日 `yyyy-MM-dd` |
| `aiLastOrderQuantity` / `aiLastOrderUnit` | 最近一次订单的重量与规格（若无则用部门商品上订货字段兜底） |
| `aiAvailableDays` | 粗略「库存可支撑天数」：`gbDdgStockTotalWeight / 习惯单次订货量`（能算则一位小数） |

### 4.5 辅助提示 `aiAuxHints`

可为 **`null`** 或 **数组**；每项：

| 字段 | 说明 |
|------|------|
| `type` | `high_stock`：库存相对习惯单次订货量偏多；`high_loss_waste`：近期损耗+废弃占比偏高；`reduce_supplement`：窗口内损耗/损失日均 > 0 时提示可参考「含损耗损失」的缺口与耗尽日字段 |
| `message` | 可直接展示的短文案 |

---

## 5. 服务端规则摘要（便于前端理解边界）

1. **候选商品**：仅统计 **`gb_do_arrive_date`** 落在窗口内、且 **`gb_do_status = 4`（收货完成）** 的订单；按 `gb_do_dep_dis_goods_id` 分组，次数 ≥ `minTimes`，按次数 **降序** 分页。
2. **正在订货流程中的商品**：存在 **`gb_do_status < 4`** 且同部门的未完成收货订单时，该 **`gb_do_dep_dis_goods_id`** **不出现在本列表**（避免重复打扰）。
3. **间隔天**：对同一部门商品去重后的到货日序列，计算相邻日到货间隔的 **算术平均**，四舍五入为整数天。
4. **今日是否提醒**：`距上次到货天数 ≥ 习惯间隔天` **或**（窗口内 **仅一笔订单** 且 **`库存 < 2 × 估算日均消耗`**）。估算日均 = `(该单 gb_do_weight − 当前批次剩余合计) / max(1, 到货距今天数)`（隐含消耗近似）。**进入 `page.list`**：还须 **`aiShouldRemindToday === "true"`**（SQL 仅筛到货次数候选；例如当天刚到货且间隔未满、或未到下次习惯订货日时不会出现）。
5. **库存偏多**：`gbDdgStockTotalWeight` > 习惯单次订货量 × **2.5** → `high_stock`。
6. **损耗偏多**：同一窗口、同一部门 + `dis_goods_id` 下，`(loss + waste) / (produce + loss + waste)` 占比高于阈值且损耗类重量足够 → `high_loss_waste`（与 `GbConstants.StockReduceType`：生产 1、损耗 2、损失 3 一致）。
7. **第 1 页补充**：主候选（到货次数 ≥ `minTimes`）未填满 `limit` 时，再尝试加入「窗口内仅 1 笔到货」且触发「库存低于约 2 天估算消耗」提醒的商品（**不计入** `totalCount`）。
8. **库存过剩不提醒**：按「参考日均」估算库存可支撑天数（库存÷参考日均）。参考日均优先为「习惯单次订货量÷习惯间隔天」，否则用单次订货估算日均；若 **`aiReduceProductionDailyAvg`**（reduce 生产日均）可解析且大于 0，则与上述日均 **取较大值** 再参与计算（避免低估消耗）。若可支撑天数 ≥ max(8, 习惯间隔×4)，则**整行不进入提醒列表**（即使已到习惯订货日）。
9. **Reduce 补货参考**：生产日均主线建议量见 `aiRecommendGapWeightProductionOnly`；若存在损耗/损失出库，补充口径见 `aiRecommendGapWeightWithLossWaste` 与两条耗尽日（线性近似，实际波动以业务为准）。
10. **`totalCount`**：SQL 按到货次数统计的候选总数；列表另按 **`aiShouldRemindToday`** 过滤，故 **`totalCount` 与当前页实际条数可能不一致**，分页仅保证候选顺序与池大小。

---

## 6. 与其他接口的关系

- **列表形态**：与 **`POST .../depGetDepGoodsGbPage`** 相同顶层 **`page`**；若页面仅需「习惯提醒」subset，可只用本接口。
- **库存实盘**：若需批次级明细，仍用 **`GbDepartmentGoodsStockQueryService#queryDepGoodsBusiness`**（本接口只用部门商品上的汇总库存字段）。
