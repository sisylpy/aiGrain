-- =============================================================================
-- nx_goods：从「当前父节点」回填（含 UPDATE，执行前请备份）
-- 以 nx_goods_update_grand_great_grand_from_chain.sql 为准；本文件保留 audit SELECT 与同名 UPDATE 副本。
-- 仅 SELECT 排查见：nx_goods_query_grand_mismatch.sql
-- =============================================================================
--
-- 若 audit 里 sku_should_grand_id / sku_should_gg_id 全为空：
--   说明品名父（level=2）行上 nx_goods_grand_id、nx_goods_great_grand_id 未维护（NULL），
--   SKU 上仍是历史冗余值；不能先按「从 f 拷贝」去 UPDATE SKU，否则会把 SKU 也改成 NULL。
--
-- 【业务口径】一级 = 库 level0 | 二级 = 库 level1 | 三级商品(SKU) = 库 level3
-- 【库中间层】level=2 品名父（挂在二级下）。勿称「四级 SKU」，脚本内统一写「库 level3 = 三级 SKU」
--
-- 执行顺序（必须）：
--   1) UPDATE 品名父（库 level=2）
--   2) UPDATE 三级 SKU（库 level=3）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0) 诊断：品名父 grand/gg 为空的数量（SKU 审计里 should 为空的根因）
-- -----------------------------------------------------------------------------
SELECT COUNT(*) AS level2_missing_grand_or_gg
FROM nongxinle.nx_goods AS L2
WHERE L2.nx_goods_level = 2
  AND (L2.nx_goods_grand_id IS NULL OR L2.nx_goods_great_grand_id IS NULL);

-- 示例：看几条品名父（如你数据里的 10102…）
-- SELECT nx_goods_id, nx_goods_name, nx_goods_level, nx_goods_father_id,
--        nx_goods_grand_id, nx_goods_great_grand_id
-- FROM nongxinle.nx_goods WHERE nx_goods_id IN (10102, 10103, 10134);

-- -----------------------------------------------------------------------------
-- 1) 只查不改：四级 SKU —— 与「品名父上冗余列」不一致（父列为空时 should 列为 NULL）
-- -----------------------------------------------------------------------------
SELECT
    c.nx_goods_id,
    c.nx_goods_name,
    c.nx_goods_standardname,
    c.nx_goods_father_id,
    c.nx_goods_grand_id         AS sku_stored_grand_id,
    f.nx_goods_grand_id         AS sku_should_grand_from_f,
    c.nx_goods_great_grand_id   AS sku_stored_gg_id,
    f.nx_goods_great_grand_id   AS sku_should_gg_from_f,
    L2.nx_goods_father_id       AS chain_grand_from_tree,
    P.nx_goods_father_id        AS chain_gg_from_tree
FROM nongxinle.nx_goods AS c
INNER JOIN nongxinle.nx_goods AS f
    ON f.nx_goods_id = c.nx_goods_father_id
    AND f.nx_goods_level = 2
INNER JOIN nongxinle.nx_goods AS L2
    ON L2.nx_goods_id = c.nx_goods_father_id
    AND L2.nx_goods_level = 2
INNER JOIN nongxinle.nx_goods AS P
    ON P.nx_goods_id = L2.nx_goods_father_id
    AND P.nx_goods_level = 1
WHERE c.nx_goods_level = 3
  AND (
        NOT (c.nx_goods_grand_id <=> f.nx_goods_grand_id)
     OR NOT (c.nx_goods_great_grand_id <=> f.nx_goods_great_grand_id)
      );

-- -----------------------------------------------------------------------------
-- 2) 只查不改：三级品名父与当前二级父不一致
-- -----------------------------------------------------------------------------
SELECT
    L2.nx_goods_id,
    L2.nx_goods_name,
    L2.nx_goods_father_id,
    L2.nx_goods_grand_id        AS l2_stored_grand_id,
    L2.nx_goods_father_id       AS l2_should_grand_id,
    L2.nx_goods_great_grand_id  AS l2_stored_gg_id,
    P.nx_goods_father_id        AS l2_should_gg_id
FROM nongxinle.nx_goods AS L2
INNER JOIN nongxinle.nx_goods AS P
    ON P.nx_goods_id = L2.nx_goods_father_id
    AND P.nx_goods_level = 1
WHERE L2.nx_goods_level = 2
  AND (
        NOT (L2.nx_goods_grand_id <=> L2.nx_goods_father_id)
     OR NOT (L2.nx_goods_great_grand_id <=> P.nx_goods_father_id)
      );

-- -----------------------------------------------------------------------------
-- 3) 修复（安全版）：只允许从「父链」写入；推导值为 NULL 的行不更新以免再把列刷成 NULL
--     切勿再使用 SET c.grand_id = f.nx_goods_grand_id（品名父列为空时会清空 SKU）
-- -----------------------------------------------------------------------------

-- START TRANSACTION;

-- 3a 品名父
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

-- 3b SKU（必须用 L2/P 链；禁止从 f 的 grand 列拷贝）
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
