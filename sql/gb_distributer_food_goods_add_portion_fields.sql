-- 菜品原料表添加每份用量 & 包装换算字段
ALTER TABLE gb_distributer_food_goods
    ADD COLUMN gb_dfg_portion_amount DECIMAL(18, 6) NULL COMMENT '每份用量（用户录入的数），如 50（表示 50g/份）',
    ADD COLUMN gb_dfg_portion_unit  VARCHAR(16)       NULL COMMENT '每份用量单位（最小单位），如 g/ml/个/张/只/斤',
    ADD COLUMN gb_dfg_pack_qty_in_min DECIMAL(18, 4)  NULL COMMENT '1 个采购包装 = 多少最小单位，如 3000（1袋=3000g）';
