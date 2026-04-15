# gb_department_goods_stock_reduce 表结构说明

## 表名
`gb_department_goods_stock_reduce`

## 新项目表结构（当前 aigrain 项目）

### 核心字段
| 字段名 | 类型 | 说明 |
|--------|------|------|
| gb_dgsr_id | INT | 主键 |
| gb_dgsr_dis_goods_id | INT | 关联的商品ID |
| gb_dgsr_distributer_id | INT | 分销商ID |
| gb_dgsr_type | INT | 类型（见下方说明） |
| gb_dgsr_subtotal | DECIMAL | 数量小计（只有一个） |

### type 字段取值说明
| type 值 | 含义 |
|---------|------|
| 1 | 生产/采购 (produce) |
| 2 | 损耗 (waste) |
| 3 | 损失 (loss) |
| 4 | 退货 (return) |

### 重要特性
新项目**只有一个 `gb_dgsr_subtotal` 字段**，通过 `gb_dgsr_type` 字段区分不同类别（1-4）。

## 旧项目表结构（对比参考）

旧项目使用多个独立字段：
- `gb_dgsr_produce_subtotal` - 生产小计
- `gb_dgsr_waste_subtotal` - 损耗小计
- `gb_dgsr_loss_subtotal` - 损失小计
- `gb_dgsr_return_subtotal` - 退货小计

## 查询方式差异

### 旧项目方式（错误示范）
```sql
SELECT gb_dgsr_produce_subtotal, gb_dgsr_waste_subtotal ...
```

### 新项目正确方式（使用 CASE WHEN）
```sql
SELECT 
    IFNULL(SUM(CASE WHEN gb_dgsr_type = 1 THEN gb_dgsr_subtotal ELSE 0 END), 0) AS produceTotal,
    IFNULL(SUM(CASE WHEN gb_dgsr_type = 2 THEN gb_dgsr_subtotal ELSE 0 END), 0) AS wasteTotal,
    IFNULL(SUM(CASE WHEN gb_dgsr_type = 3 THEN gb_dgsr_subtotal ELSE 0 END), 0) AS lossTotal,
    IFNULL(SUM(CASE WHEN gb_dgsr_type = 4 THEN gb_dgsr_subtotal ELSE 0 END), 0) AS returnTotal
FROM gb_department_goods_stock_reduce
WHERE ...
```

### 按 type 查询单个类别
```sql
SELECT ... FROM gb_department_goods_stock_reduce
WHERE gb_dgsr_type = #{type}  -- 1=produce, 2=waste, 3=loss, 4=return
```

## 注意事项

1. **不要混淆新旧项目的字段结构**，新项目没有 `gb_dgsr_produce_subtotal`、`gb_dgsr_waste_subtotal` 等独立字段。

2. **查询时要加 `gb_dgsr_type` 条件**，否则会查询所有类型的数据。

3. **Entity 类映射**：新项目 `GbDepartmentGoodsStockReduceEntity` 中只有一个 `subtotal` 字段，没有 `produceSubtotal`、`wasteSubtotal` 等字段。

4. **MyBatis 字段映射**：使用 MyBatis-Plus 时，实体类字段采用驼峰命名，数据库采用下划线命名，自动映射无需手动指定。

## 相关文件
- Entity: `GbDepartmentGoodsStockReduceEntity.java`
- Mapper: `GbDepartmentGoodsStockReduceMapper.xml`
- Service: `GbDepartmentGoodsStockReduceService.java`

---
*创建时间: 2026-04-15*
