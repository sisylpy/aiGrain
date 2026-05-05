-- 核对用；确认后可执行：nx_goods_update_grand_great_grand_from_chain.sql
--
-- 【业务口径】一级 = 库 level0 | 二级 = 库 level1 | 三级商品(SKU) = 库 level3
-- 【库中间层】level=2 品名父（非口头「三级 SKU」）
-- -----------------------------------------------------------------------------

-- ① 三级 SKU（库 nx_goods_level = 3）
--    grand_id       应 = 品名父.nx_goods_father_id（业务二级 = 库 level1）
--    great_grand_id 应 = 该二级行的 nx_goods_father_id（一级 = 库 level0）
SELECT
    c.nx_goods_id,
    c.nx_goods_name,
    c.nx_goods_standardname,
    c.nx_goods_father_id,
    c.nx_goods_grand_id         AS stored_grand_id,
    L2.nx_goods_father_id       AS expected_grand_id,
    c.nx_goods_great_grand_id   AS stored_great_grand_id,
    P.nx_goods_father_id        AS expected_great_grand_id
FROM nongxinle.nx_goods AS c
INNER JOIN nongxinle.nx_goods AS L2
    ON L2.nx_goods_id = c.nx_goods_father_id
   AND L2.nx_goods_level = 2
LEFT JOIN nongxinle.nx_goods AS P
    ON P.nx_goods_id = L2.nx_goods_father_id
   AND P.nx_goods_level = 1
WHERE c.nx_goods_level = 3
  AND NOT (c.nx_goods_grand_id <=> L2.nx_goods_father_id);

-- ② 品名父（库 nx_goods_level = 2）
--    grand_id 应 = 自己的 nx_goods_father_id（所挂业务二级 id）
SELECT
    nx_goods_id,
    nx_goods_name,
    nx_goods_father_id,
    nx_goods_grand_id         AS stored_grand_id,
    nx_goods_father_id       AS expected_grand_id
FROM nongxinle.nx_goods
WHERE nx_goods_level = 2
  AND NOT (nx_goods_grand_id <=> nx_goods_father_id);
