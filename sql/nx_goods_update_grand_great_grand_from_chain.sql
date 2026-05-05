-- =============================================================================
-- UPDATE：按父链回填 nx_goods_grand_id / nx_goods_great_grand_id
--
-- 逻辑已与 nx_goods_query_grand_mismatch.sql、nx_goods_preview_recover_grand_great_grand.sql 对齐。
-- 建议：先 START TRANSACTION，跑完后用同文件末尾 SELECT 抽查，再 COMMIT。
--
-- 【业务口径】一级 = 库 level0 | 二级 = 库 level1 | 三级 SKU = 库 level3
-- 【品名父】 nx_goods_level = 2
--
-- 顺序：① 先品名父 level=2，② 再三级 SKU level=3
--
-- 【先测两条】② 默认带 AND c.nx_goods_id IN (100002, 100003)，请改成你库里的两个 SKU id；
--           全表执行时把该行注释掉或删掉。① 可先注释整段不跑，只验证 SKU；要联测品名父再打开①并按需加 IN。
-- =============================================================================

-- START TRANSACTION;

-- ① 品名父（level=2）：全表跑时再打开；先测两条 SKU 请保持下面整段注释
/*
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
-- 可选：只测两条品名父时在上面的 WHERE 末尾补一行（去掉分号再添）：
--   AND L2.nx_goods_id IN (10102, 10103)
*/

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
      )
-- 【测试两条】全表跑请注释掉下一行
  AND c.nx_goods_id IN (100002, 100003)
;

-- 抽查（应无行或与 preview 中 SKIP 一致）：
-- SELECT nx_goods_id, nx_goods_level, nx_goods_father_id, nx_goods_grand_id, nx_goods_great_grand_id
-- FROM nongxinle.nx_goods
-- WHERE nx_goods_level IN (2, 3)
--   AND (nx_goods_grand_id IS NULL OR nx_goods_great_grand_id IS NULL);

-- COMMIT;
