-- =============================================================================
-- （与 nx_goods_update_grand_great_grand_from_chain.sql 内容一致，保留旧文件名便于书签）
-- UPDATE：按父链回填 nx_goods_grand_id / nx_goods_great_grand_id
--
-- 逻辑已与 nx_goods_query_grand_mismatch.sql、nx_goods_preview_recover_grand_great_grand.sql 对齐。
-- 建议：先 START TRANSACTION，跑完后抽查，再 COMMIT。
--
-- 【业务口径】一级 = 库 level0 | 二级 = 库 level1 | 三级 SKU = 库 level3
-- 【品名父】 nx_goods_level = 2
--
-- 顺序：① 先品名父 level=2，② 再三级 SKU level=3
-- =============================================================================

-- START TRANSACTION;

-- ① 品名父（level=2）
UPDATE nongxinle.nx_goods AS L2
INNER JOIN nongxinle.nx_goods AS P
    ON P.nx_goods_id = L2.nx_goods_father_id
   AND P.nx_goods_level = 1
SET
    L2.nx_goods_grand_id       = L2.nx_goods_father_id,
    L2.nx_goods_great_grand_id = P.nx_goods_father_id
WHERE L2.nx_goods_level = 2
  AND L2.nx_goods_father_id IS NOT NULL
  AND P.nx_goods_father_id IS NOT NULL
  AND (
        NOT (L2.nx_goods_grand_id <=> L2.nx_goods_father_id)
     OR NOT (L2.nx_goods_great_grand_id <=> P.nx_goods_father_id)
      );

-- ② 三级 SKU（level=3）
UPDATE nongxinle.nx_goods AS c
INNER JOIN nongxinle.nx_goods AS L2
    ON L2.nx_goods_id = c.nx_goods_father_id
   AND L2.nx_goods_level = 2
INNER JOIN nongxinle.nx_goods AS P
    ON P.nx_goods_id = L2.nx_goods_father_id
   AND P.nx_goods_level = 1
SET
    c.nx_goods_grand_id        = L2.nx_goods_father_id,
    c.nx_goods_great_grand_id  = P.nx_goods_father_id
WHERE c.nx_goods_level = 3
  AND c.nx_goods_father_id IS NOT NULL
  AND L2.nx_goods_father_id IS NOT NULL
  AND P.nx_goods_father_id IS NOT NULL
  AND (
        NOT (c.nx_goods_grand_id <=> L2.nx_goods_father_id)
     OR NOT (c.nx_goods_great_grand_id <=> P.nx_goods_father_id)
      );

-- COMMIT;
