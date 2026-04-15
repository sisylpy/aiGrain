# 数据库迁移检查报告

## 当前问题
表 `gb_distributer_purchase_goods` 不存在于新数据库 `ai_marketing` 中。

## 分析结果

### 1. SQL 文件中的表 (all_tables.sql)
已包含 32 个表：
- gb_department
- gb_department_dis_goods
- gb_department_orders
- gb_department_user
- gb_distributer
- gb_distributer_father_goods
- gb_distributer_goods
- gb_distributer_module
- gb_distributer_pay
- gb_distributer_purchase_batch
- gb_distributer_standard
- gb_distributer_user
- nx_buy_user
- nx_department
- nx_department_bill
- nx_department_dis_goods
- nx_department_user
- nx_distributer
- nx_distributer_gb_distributer
- nx_distributer_goods
- nx_distributer_standard
- nx_distributer_user
- nx_goods
- nx_jrdh_supplier
- nx_jrdh_user
- nx_sell_user
- qy_gb_dis_corp_user
- sys_business_type
- sys_city_market
- sys_user

### 2. 代码中使用的表
根据 Mapper XML 文件分析，项目使用了以下表：

#### GbDistributerPurchaseGoodsMapper.xml
- `gb_distributer_purchase_goods` ⚠️ **缺失**
- `gb_distributer_goods` ✅
- `gb_distributer_father_goods` ✅
- `gb_department_orders` ✅
- `gb_department` ✅

#### GbDepartmentOrdersMapper.xml
- `gb_department_orders` ✅

### 3. 缺失的表

| 表名 | 状态 | 解决方案 |
|------|------|----------|
| gb_distributer_purchase_goods | ❌ 缺失 | 已创建 SQL 文件: `oldSql/gb_distributer_purchase_goods.sql` |

## 解决方案

### 方案 1: 执行已创建的 SQL 文件（推荐临时方案）
```sql
USE ai_marketing;
SOURCE /Users/lpy/Documents/javaWeb/kuangjia/aigrain/oldSql/gb_distributer_purchase_goods.sql;
```

### 方案 2: 从老项目数据库导出（推荐最终方案）
```bash
# 导出表结构
mysqldump -u root -p nongxinle gb_distributer_purchase_goods --no-data > gb_distributer_purchase_goods.sql

# 导出表数据（如果需要）
mysqldump -u root -p nongxinle gb_distributer_purchase_goods > gb_distributer_purchase_goods_with_data.sql
```

### 方案 3: 批量导出所有缺失表
建议直接从老项目数据库导出完整的数据库结构，然后导入到新数据库：

```bash
# 导出整个数据库结构
mysqldump -u root -p nongxinle --no-data > nongxinle_schema.sql

# 导入到新数据库
mysql -u root -p ai_marketing < nongxinle_schema.sql
```

## 建议

1. **短期**: 执行方案 1，先让项目跑起来
2. **长期**: 执行方案 3，确保所有表结构完全一致，避免后续出现类似问题

## 验证方法

执行以下 SQL 检查表是否存在：
```sql
USE ai_marketing;
SHOW TABLES LIKE 'gb_distributer_purchase_goods';
```
