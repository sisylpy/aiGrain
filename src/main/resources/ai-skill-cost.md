# 餐厅成本分析 Skill

## Title
restaurant-cost-analyst

## 摘要
当用户询问成本、支出、费用、利润、房租、工资、损耗、食材费用等财务相关问题时使用此技能。需要从数据库查询餐厅的固定成本和变动成本数据进行分析。

## ⚠️ 核心前置条件

**必须先获取3个基本固定成本数据，缺一不可：**

| 数据项 | 字段名 | 说明 |
|-------|--------|------|
| 月租金 | `gbAiRestaurantProfileRentMonthly` | 每月房租支出 |
| 月工资 | `gbAiRestaurantProfileMonthlyWage` | 每月员工工资总额 |
| 月固定成本 | `gbAiRestaurantProfileMonthlyFixedCost` | 其他固定成本（水电、物业等） |

**如果这3个数据中任何一个缺失：**
1. ❌ 不要查询库存消耗、食材成本等变动成本数据
2. ❌ 不要进行任何成本分析或利润计算
3. ✅ 必须先引导用户补充这3项数据
4. ✅ 提示语参考："钱多多老师需要先了解你的固定成本情况，才能帮你分析成本。请问：1）你的月租金是多少？2）每月工资支出大概多少？3）还有其他固定开支吗？"

## 数据库表结构

### 1. 餐厅画像表 (gb_ai_restaurant_profile)
- `gbAiRestaurantProfileDepartmentId` - 部门ID（门店）
- `gbAiRestaurantProfileRentMonthly` - 月租金 ⭐ 必填
- `gbAiRestaurantProfileMonthlyWage` - 月工资 ⭐ 必填
- `gbAiRestaurantProfileMonthlyFixedCost` - 月固定成本 ⭐ 必填

### 2. 日营收表 (gb_ai_daily_revenue)
- `gbAiDailyRevenueDepartmentId` - 部门ID
- `gbAiDailyRevenueRecordDate` - 记录日期
- `gbAiDailyRevenueDineInRevenue` - 堂食营业额
- `gbAiDailyRevenueTakeoutRevenue` - 外卖营业额
- `gbAiDailyRevenuePlatformFee` - 平台抽成

### 3. 库存消耗日报表 (gb_department_goods_stock_reduce_daily)
- `gbDgsrdGbDepartmentId` - 部门ID
- `gbDgsrdDate` - 日期
- `gbDgsrdCostSubtotal` - 成本金额
- `gbDgsrdWasteSubtotal` - 废弃金额
- `gbDgsrdLossSubtotal` - 损耗金额
- `gbDgsrdReturnSubtotal` - 退货金额

## 查询时间
- 默认查询本月数据
- 时间格式：`YYYY-MM-DD`

## 成本分类

### 固定成本
- 月租金 (Rent)
- 月工资 (Wages)
- 月固定成本 (Fixed Costs)

### 变动成本
- 食材成本 (Material Cost)
- 废弃损耗 (Waste)
- 损耗金额 (Loss)
- 退货金额 (Return)

### 营收数据
- 堂食营业额 (Dine-in Revenue)
- 外卖营业额 (Takeout Revenue)
- 平台抽成 (Platform Fee)

## ⚡ 数据提取规则

**当用户提到任何数字数据时，必须提取并返回JSON：**

```json
{
  "hasData": true,
  "needsConfirm": false,
  "updates": [
    {"field": "gb_ai_restaurant_profile_rent_monthly", "value": 8000.00, "displayName": "月租金"},
    {"field": "gb_ai_restaurant_profile_monthly_wage", "value": 15000.00, "displayName": "月工资"},
    {"field": "gb_ai_restaurant_profile_monthly_fixed_cost", "value": 800.00, "displayName": "月固定成本"}
  ],
  "summary": "提取到月租金8000元、月工资15000元、月固定成本800元"
}
```

**重要说明：**
1. 如果用户提供了3个基本固定成本数据，要同时提取并返回JSON
2. 只有当3个数据都提取到且 `needsConfirm=false` 时，系统才会保存数据
3. 如果需要确认（如数据冲突），设置 `needsConfirm=true`
4. 不要在回复中说"已保存"，因为保存由后台自动完成
5. 如果用户没有提供任何新数据，`hasData` 设为 `false`
