-- =============================================================================
-- 仅 SELECT：预览「若按父链回填 grand / great_grand」会得到什么（无任何 UPDATE）
--
-- 请先执行本文件全部查询，确认后再执行：nx_goods_update_grand_great_grand_from_chain.sql
--
-- 【业务口径】一级 = 库 level0 | 二级 = 库 level1 | 三级商品(SKU) = 库 level3
-- 【库中间层】nx_goods_level = 2 = 品名父（挂在二级下；SKU 的 father 指向它，不是口头「三级 SKU」）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- A) 品名父（nx_goods_level = 2）— 父须为「业务二级」= 库 level1
-- -----------------------------------------------------------------------------
SELECT
    L2.nx_goods_id,
    L2.nx_goods_name,
    L2.nx_goods_level,
    L2.nx_goods_father_id,
    L2.nx_goods_grand_id          AS current_grand_id,
    L2.nx_goods_great_grand_id    AS current_great_grand_id,
    L2.nx_goods_father_id         AS target_grand_id,
    P.nx_goods_father_id          AS target_great_grand_id,
    P.nx_goods_id                 AS join_p_id,
    P.nx_goods_level              AS join_p_level,
    CASE
        WHEN P.nx_goods_id IS NULL THEN 'SKIP_无业务二级父或父不是库level1'
        WHEN L2.nx_goods_father_id IS NULL THEN 'SKIP_品名父father为空'
        WHEN P.nx_goods_father_id IS NULL THEN 'SKIP_业务二级的father为空无法定一级'
        WHEN L2.nx_goods_grand_id <=> L2.nx_goods_father_id
         AND L2.nx_goods_great_grand_id <=> P.nx_goods_father_id THEN '已一致_可不必改'
        ELSE '将变更'
    END AS preview_status
FROM nongxinle.nx_goods AS L2
LEFT JOIN nongxinle.nx_goods AS P
    ON P.nx_goods_id = L2.nx_goods_father_id
   AND P.nx_goods_level = 1
WHERE L2.nx_goods_level = 2
ORDER BY preview_status, L2.nx_goods_id;

-- -----------------------------------------------------------------------------
-- B) 三级 SKU 商品（nx_goods_level = 3）— 经品名父 L2 再到业务二级 P
-- -----------------------------------------------------------------------------
SELECT
    c.nx_goods_id,
    c.nx_goods_name,
    c.nx_goods_standardname,
    c.nx_goods_level,
    c.nx_goods_father_id,
    c.nx_goods_grand_id           AS current_grand_id,
    c.nx_goods_great_grand_id     AS current_great_grand_id,
    L2.nx_goods_father_id         AS target_grand_id,
    P.nx_goods_father_id          AS target_great_grand_id,
    L2.nx_goods_id                AS join_l2_id,
    P.nx_goods_id                 AS join_p_id,
    CASE
        WHEN c.nx_goods_father_id IS NULL THEN 'SKIP_SKU父为空'
        WHEN L2.nx_goods_id IS NULL THEN 'SKIP_无品名父或不是level2'
        WHEN L2.nx_goods_father_id IS NULL THEN 'SKIP_品名父的father为空'
        WHEN P.nx_goods_id IS NULL THEN 'SKIP_业务二级不存在或不是库level1'
        WHEN P.nx_goods_father_id IS NULL THEN 'SKIP_业务二级的father为空无法定一级'
        WHEN c.nx_goods_grand_id <=> L2.nx_goods_father_id
         AND c.nx_goods_great_grand_id <=> P.nx_goods_father_id THEN '已一致_可不必改'
        ELSE '将变更'
    END AS preview_status
FROM nongxinle.nx_goods AS c
LEFT JOIN nongxinle.nx_goods AS L2
    ON L2.nx_goods_id = c.nx_goods_father_id
   AND L2.nx_goods_level = 2
LEFT JOIN nongxinle.nx_goods AS P
    ON P.nx_goods_id = L2.nx_goods_father_id
   AND P.nx_goods_level = 1
WHERE c.nx_goods_level = 3
ORDER BY preview_status, c.nx_goods_id;

-- -----------------------------------------------------------------------------
-- C) 汇总：各 preview_status 行数（先看这张再展开 A/B）
-- -----------------------------------------------------------------------------
SELECT preview_status, COUNT(*) AS cnt
FROM (
    SELECT
        CASE
            WHEN P.nx_goods_id IS NULL THEN 'SKIP_无业务二级父或父不是库level1'
            WHEN L2.nx_goods_father_id IS NULL THEN 'SKIP_品名父father为空'
            WHEN P.nx_goods_father_id IS NULL THEN 'SKIP_业务二级的father为空无法定一级'
            WHEN L2.nx_goods_grand_id <=> L2.nx_goods_father_id
             AND L2.nx_goods_great_grand_id <=> P.nx_goods_father_id THEN '已一致_可不必改'
            ELSE '将变更'
        END AS preview_status
    FROM nongxinle.nx_goods AS L2
    LEFT JOIN nongxinle.nx_goods AS P
        ON P.nx_goods_id = L2.nx_goods_father_id
       AND P.nx_goods_level = 1
    WHERE L2.nx_goods_level = 2
) t
GROUP BY preview_status
ORDER BY preview_status;

SELECT preview_status, COUNT(*) AS cnt
FROM (
    SELECT
        CASE
            WHEN c.nx_goods_father_id IS NULL THEN 'SKIP_SKU父为空'
            WHEN L2.nx_goods_id IS NULL THEN 'SKIP_无品名父或不是level2'
            WHEN L2.nx_goods_father_id IS NULL THEN 'SKIP_品名父的father为空'
            WHEN P.nx_goods_id IS NULL THEN 'SKIP_业务二级不存在或不是库level1'
            WHEN P.nx_goods_father_id IS NULL THEN 'SKIP_业务二级的father为空无法定一级'
            WHEN c.nx_goods_grand_id <=> L2.nx_goods_father_id
             AND c.nx_goods_great_grand_id <=> P.nx_goods_father_id THEN '已一致_可不必改'
            ELSE '将变更'
        END AS preview_status
    FROM nongxinle.nx_goods AS c
    LEFT JOIN nongxinle.nx_goods AS L2
        ON L2.nx_goods_id = c.nx_goods_father_id
       AND L2.nx_goods_level = 2
    LEFT JOIN nongxinle.nx_goods AS P
        ON P.nx_goods_id = L2.nx_goods_father_id
       AND P.nx_goods_level = 1
    WHERE c.nx_goods_level = 3
) t
GROUP BY preview_status
ORDER BY preview_status;
