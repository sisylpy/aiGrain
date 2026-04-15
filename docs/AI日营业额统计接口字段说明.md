# AI日营业额统计接口字段说明

## 接口信息
- **接口路径**: `GET /ai/daily-revenue/stats/{departmentId}`
- **Controller**: `GbAiDailyRevenueController`
- **DTO类**: `GbAiDailyRevenueStatsDTO`
- **访问地址**: http://localhost:8090/api/swagger-ui/index.html

## 字段分类说明

### 1. 基础统计字段
| 字段名 | 类型 | 单位 | 业务说明 |
|--------|------|------|----------|
| days | Integer | 天 | 统计期间的总天数 |
| avgDailyRevenue | BigDecimal | 元 | 统计期间内每日的平均营业额 |
| totalRevenue | BigDecimal | 元 | 统计期间内的营业额总和 |
| avgOrderCount | BigDecimal | 单/天 | 统计期间内每日的平均订单数量 |
| avgPerCustomer | BigDecimal | 元/人 | 平均每位顾客的消费金额（客单价） |
| totalCouponAmount | BigDecimal | 元 | 统计期间内使用的优惠券总金额 |
| totalRefundAmount | BigDecimal | 元 | 统计期间内的退款总金额 |
| maxDailyRevenue | BigDecimal | 元 | 统计期间内最高的单日营业额 |
| minDailyRevenue | BigDecimal | 元 | 统计期间内最低的单日营业额 |

### 2. 固定开支字段
| 字段名 | 类型 | 单位 | 业务说明 |
|--------|------|------|----------|
| avgFixedCost | BigDecimal | 元/天 | 每日的固定成本（工资+租金） |
| monthlyWage | BigDecimal | 元/月 | 餐厅每月的工资总额 |
| monthlyRent | BigDecimal | 元/月 | 餐厅每月的租金 |
| avgNetRevenue | BigDecimal | 元/天 | 日均营业额扣除优惠券后的净收入 |

### 3. 外卖相关统计字段
| 字段名 | 类型 | 单位 | 业务说明 |
|--------|------|------|----------|
| totalTakeoutRevenue | BigDecimal | 元 | 统计期间内外卖的总营业额 |
| avgTakeoutRevenue | BigDecimal | 元/天 | 统计期间内每日的平均外卖营业额 |
| totalTakeoutNet | BigDecimal | 元 | **外卖净收入** = 外卖营业额 - 平台抽成 |
| avgTakeoutNet | BigDecimal | 元/天 | 每日的外卖营业额扣除平台抽成后的净收入 |

### 4. 成本支出字段（从部门商品库存减少表）
| 字段名 | 类型 | 单位 | 业务说明 | 对应type值 |
|--------|------|------|----------|------------|
| produceCost | BigDecimal | 元 | 原材料采购、加工等生产环节的成本 | 1 |
| wasteCost | BigDecimal | 元 | 原材料过期、变质等损耗成本（废气） | 2 |
| lossCost | BigDecimal | 元 | 原材料丢失、损坏等损失成本 | 3 |
| returnCost | BigDecimal | 元 | 原材料退货产生的成本 | 4 |
| productionCost | BigDecimal | 元 | **制作成本** = 生产成本 + 损耗成本 + 损失成本 | 计算值 |
| totalCost | BigDecimal | 元 | **总成本** = 制作成本 + 退货成本 | 计算值 |

### 5. 利润与毛利率字段
| 字段名 | 类型 | 单位 | 业务说明 | 计算公式 |
|--------|------|------|----------|----------|
| grossProfitMargin | BigDecimal | % | 毛利率数值（0-100） | (净收入 - 总成本) / 净收入 × 100% |
| grossProfitMarginPercent | String | 字符串 | 毛利率（带%符号） | grossProfitMargin + "%" |
| breakEvenPoint | BigDecimal | 元/天 | 盈亏平衡点（每日需达到的最低营业额） | 等于日均固定成本 |
| profitAmount | BigDecimal | 元/天 | 原有利润（不考虑成本） | 日均净收入 - 日均固定成本 |
| profitAfterCost | BigDecimal | 元/天 | **考虑成本后的实际利润** | 日均净收入 - (总成本/天数) - 日均固定成本 |
| actualProfit | BigDecimal | 元/天 | 实际利润（同profitAfterCost） | 与profitAfterCost相同 |

### 6. 盈亏状态字段
| 字段名 | 类型 | 可能值 | 业务说明 |
|--------|------|--------|----------|
| status | String | profit/breakeven/loss | 盈亏状态代码 |
| statusDesc | String | 盈利中/保本/亏损 | 盈亏状态描述 |

## 关键业务逻辑说明

### 1. 外卖净收入计算
```
外卖净收入 = 外卖营业额 - 平台抽成
```
- **外卖营业额**: 用户支付给外卖平台的总金额
- **平台抽成**: 外卖平台抽取的服务费、佣金等
- **实际到手金额**: 外卖营业额扣除平台抽成后的金额

### 2. 制作成本构成
```
制作成本 = 生产成本 + 损耗成本 + 损失成本
```
- **生产成本 (type=1)**: 原材料采购、加工等直接成本
- **损耗成本 (type=2)**: 原材料过期、变质、废气等损耗
- **损失成本 (type=3)**: 原材料丢失、损坏、被盗等损失

### 3. 总成本计算
```
总成本 = 制作成本 + 退货成本
```
- **退货成本 (type=4)**: 原材料退货产生的成本（运费、处理费等）

### 4. 毛利率计算
```
毛利率 = (总净收入 - 总成本) / 总净收入 × 100%
```
- **总净收入**: 总营业额扣除优惠券后的金额
- **分母**: 使用净收入而不是毛收入，更准确反映实际盈利情况

### 5. 盈亏状态判断
```
if (profitAfterCost > 0) → 盈利
else if (profitAfterCost = 0) → 保本
else → 亏损
```
- **考虑所有成本后的利润**: 使用`profitAfterCost`而非`profitAmount`
- **更真实的经营状况**: 考虑了原材料成本等实际支出

## 接口响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "stats": {
      "days": 30,
      "avgDailyRevenue": 8500.00,
      "totalRevenue": 255000.00,
      "avgOrderCount": 120.50,
      "avgPerCustomer": 70.50,
      "totalCouponAmount": 5000.00,
      "totalRefundAmount": 1500.00,
      "maxDailyRevenue": 12000.00,
      "minDailyRevenue": 6500.00,
      "avgFixedCost": 2000.00,
      "monthlyWage": 45000.00,
      "monthlyRent": 15000.00,
      "avgNetRevenue": 8300.00,
      "totalTakeoutRevenue": 90000.00,
      "avgTakeoutRevenue": 3000.00,
      "totalTakeoutNet": 81000.00,
      "avgTakeoutNet": 2700.00,
      "produceCost": 15000.00,
      "wasteCost": 3000.00,
      "lossCost": 1000.00,
      "returnCost": 2000.00,
      "productionCost": 19000.00,
      "totalCost": 21000.00,
      "grossProfitMargin": 25.50,
      "grossProfitMarginPercent": "25.50%",
      "breakEvenPoint": 2000.00,
      "profitAmount": 6300.00,
      "profitAfterCost": 4600.00,
      "actualProfit": 4600.00,
      "status": "profit",
      "statusDesc": "盈利中"
    },
    "profile": {
      "gbAiRestaurantProfileId": 1,
      "gbAiRestaurantProfileRestaurantName": "测试餐厅",
      "gbAiRestaurantProfileRentMonthly": 15000.00,
      "gbAiRestaurantProfileMonthlyWage": 45000.00,
      "gbAiRestaurantProfileMonthlyFixedCost": 60000.00,
      "...": "其他字段"
    }
  }
}
```

## 相关数据表说明

### 1. gb_ai_daily_revenue 表
- 存储日营业额基础数据
- 包含堂食、外卖营业额、平台抽成等字段

### 2. gb_department_goods_stock_reduce 表
- 存储部门商品库存减少记录
- 通过`gb_dgsr_type`字段区分不同类型的成本：
  - 1: 生产成本 (produce)
  - 2: 损耗成本 (waste, 废气等)
  - 3: 损失成本 (loss)
  - 4: 退货成本 (return)

### 3. gb_ai_restaurant_profile 表
- 存储餐厅画像信息
- 包含固定开支数据（工资、租金等）

---

*文档更新日期: 2026-04-15*
*最后修改: AI助手*