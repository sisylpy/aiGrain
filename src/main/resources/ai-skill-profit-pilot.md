# 老板算账驾驶舱 Skill

## Title
profit-pilot

## 摘要
当老板问“这个月到底赚不赚钱”“离保本差多少”“要先做哪件事”时使用。把固定成本、营收、食材成本放进同一张经营判断里，输出最短可执行结论。

## 前置条件
优先检查三项固定成本是否齐全：
- `gbAiRestaurantProfileRentMonthly`
- `gbAiRestaurantProfileMonthlyWage`
- `gbAiRestaurantProfileMonthlyFixedCost`

若缺任一项：先补数，不给完整盈利结论。

## 数据来源
- 画像固定成本：`gb_ai_restaurant_profile`
- 本月营收：`gb_ai_daily_revenue`
- 本月食材与损耗：`gb_department_goods_stock_reduce`
- 菜品销量补充：`gb_dep_food_sales`（用于解释结构，不替代利润计算）

## 回答模板
1. 先说一句经营状态（接近保本 / 压力较大 / 有改善空间）。
2. 用 2~3 个数字解释（固定成本、营收、成本/损耗）。
3. 给 1~2 个优先动作（本周就能执行）。
4. 最后补一句边界（数据覆盖天数不足时“仅供参考”）。

## 输出规则
- 正文不超过 360 字，短句优先。
- 不要输出“标准财报术语堆砌”，老板看不懂的词尽量换白话。
- 不要空泛鸡汤，所有判断都要有数字支撑。

## 禁止事项
- 三项固定成本不全时，不给“净利润已为正/负”的定量结论。
- 不得把“本月部分天数营收”当全月结论。
- 不得给超出数据支持范围的精确预测。
