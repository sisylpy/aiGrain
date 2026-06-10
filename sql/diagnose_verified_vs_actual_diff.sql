-- ============================================================
-- 诊断：verifiedTotalAmount(1116.11) vs actualCostTotal(1107.94)
-- 差异来源分析
-- 参数：disId=10, depFatherId=48, 2026-06-01 ~ 2026-06-09
--
-- 正确列名（来自 GbDepartmentGoodsStockReduceEntity / Mapper）：
--   gb_dgsr_gb_dis_goods_id         商品ID
--   gb_dgsr_gb_distributer_id       批发商ID
--   gb_dgsr_gb_department_father_id 父部门ID
--   gb_dgsr_date                    日期 (不是 gb_dgsr_full_date)
--   gb_dgsr_weight                  重量
--   gb_dgsr_subtotal                金额
--   gb_dgsr_type                    类型:1=生产,2=废弃,3=损耗,4=退货,6=员工餐
-- ============================================================

-- Step 1: 找出所有有 type1 出库的商品及其关联的菜（配方反查）
-- 这些是 outboundIngredientAnalysis 扩展出来的菜
SELECT 
    dfg.gb_dfg_food_id AS foodId,
    df.gb_df_food_name AS foodName,
    dfg.gb_dfg_dis_goods_id AS goodsId,
    dfg.gb_dfg_goods_name AS goodsName,
    dfg.gb_dfg_goods_amount AS recipeAmount,
    dfg.gb_dfg_status AS status
FROM gb_distributer_food_goods dfg
JOIN gb_distributer_food df ON df.gb_distributer_food_id = dfg.gb_dfg_food_id
WHERE dfg.gb_dfg_dis_goods_id IN (
    -- 所有有 type1+2+3 出库的商品
    SELECT DISTINCT gb_dgsr_gb_dis_goods_id
    FROM gb_department_goods_stock_reduce
    WHERE gb_dgsr_gb_distributer_id = 10
      AND gb_dgsr_type IN (1, 2, 3)
      AND gb_dgsr_date >= '2026-06-01'
      AND gb_dgsr_date <= '2026-06-09'
      AND gb_dgsr_gb_department_father_id = 48
)
AND dfg.gb_dfg_dis_id = 10
ORDER BY foodId, goodsId;


-- Step 2: 查看这些菜在区间内是否有销售/消费记录
-- consumptionQty > 0 的会被核销, = 0 的会计入 nonVerifiedDishCount
SELECT 
    fs.gb_dfs_food_id AS foodId,
    dfs_food.foodName,
    SUM(CASE 
        WHEN fs.gb_dfs_type IN (1, 3, 5) THEN COALESCE(fs.gb_dfs_amount, 0) 
        ELSE 0 
    END) AS salesQty,
    SUM(COALESCE(fs.gb_dfs_amount, 0)) AS consumptionQty,
    SUM(COALESCE(fs.gb_dfs_subtotal, 0)) AS revenue
FROM gb_dep_food_sales fs
JOIN (
    SELECT gb_distributer_food_id AS foodId, gb_df_food_name AS foodName
    FROM gb_distributer_food
) dfs_food ON dfs_food.foodId = fs.gb_dfs_food_id
WHERE fs.gb_dfs_distributer_id = 10
  AND fs.gb_dfs_dep_father_id = 48
  AND fs.gb_dfs_full_date >= '2026-06-01'
  AND fs.gb_dfs_full_date <= '2026-06-09'
GROUP BY fs.gb_dfs_food_id
ORDER BY consumptionQty ASC;


-- Step 3: 对比两个接口的菜范围差异
-- 初始范围：有 sales 的菜 (ingredientAnalysis 用的)
-- 扩展范围：有出库的商品的配方所关联的菜 (outboundIngredientAnalysis 扩展的)
-- 差异菜 = 扩展范围中有、但初始范围中没有的菜

-- 3a: 初始范围菜（有 sales 记录）
DROP TEMPORARY TABLE IF EXISTS tmp_initial_foods;
CREATE TEMPORARY TABLE tmp_initial_foods AS
SELECT DISTINCT gb_dfs_food_id AS foodId
FROM gb_dep_food_sales
WHERE gb_dfs_distributer_id = 10
  AND gb_dfs_dep_father_id = 48
  AND gb_dfs_full_date >= '2026-06-01'
  AND gb_dfs_full_date <= '2026-06-09';

-- 3b: 扩展范围菜（有出库商品的配方反查）
DROP TEMPORARY TABLE IF EXISTS tmp_expanded_foods;
CREATE TEMPORARY TABLE tmp_expanded_foods AS
SELECT DISTINCT dfg.gb_dfg_food_id AS foodId
FROM gb_distributer_food_goods dfg
WHERE dfg.gb_dfg_dis_goods_id IN (
    SELECT DISTINCT gb_dgsr_gb_dis_goods_id
    FROM gb_department_goods_stock_reduce
    WHERE gb_dgsr_gb_distributer_id = 10
      AND gb_dgsr_type IN (1, 2, 3)
      AND gb_dgsr_date >= '2026-06-01'
      AND gb_dgsr_date <= '2026-06-09'
      AND gb_dgsr_gb_department_father_id = 48
)
AND dfg.gb_dfg_dis_id = 10
AND dfg.gb_dfg_status != 0;

-- 3c: 只在扩展范围、不在初始范围的「差异菜」
SELECT 
    ef.foodId,
    df.gb_df_food_name AS foodName,
    '仅扩展范围，无销售' AS diffReason
FROM tmp_expanded_foods ef
LEFT JOIN tmp_initial_foods inf ON ef.foodId = inf.foodId
JOIN gb_distributer_food df ON df.gb_distributer_food_id = ef.foodId
WHERE inf.foodId IS NULL
UNION ALL
-- 3d: 只在初始范围、不在扩展范围的菜（理论上不应该有）
SELECT 
    inf.foodId,
    df.gb_df_food_name AS foodName,
    '仅初始范围' AS diffReason
FROM tmp_initial_foods inf
LEFT JOIN tmp_expanded_foods ef ON inf.foodId = ef.foodId
JOIN gb_distributer_food df ON df.gb_distributer_food_id = inf.foodId
WHERE ef.foodId IS NULL;


-- Step 4: 按商品维度查看出库和核销分解
-- 这直接看出哪些商品的 outbound123 ≠ verified + nonVerified
SELECT 
    gsr.goodsId,
    dg.gb_dg_goods_name AS goodsName,
    gsr.type1Amount,
    gsr.type2Amount,
    gsr.type3Amount,
    gsr.type6Amount,
    gsr.type1 + gsr.type2 + gsr.type3 AS outbound123,
    COALESCE(lastSale.lastSalesDate, '无销售') AS lastSalesDate,
    CASE 
        WHEN COALESCE(lastSale.lastSalesDate, '') = '' THEN '无关联销售菜'
        WHEN gsr.outboundAfterLastSales > 0 THEN CONCAT('宽限期后出库: ', ROUND(gsr.outboundAfterLastSales, 2))
        ELSE '全部可核销'
    END AS graceNote
FROM (
    SELECT 
        gb_dgsr_gb_dis_goods_id AS goodsId,
        SUM(CASE WHEN gb_dgsr_type = 1 THEN COALESCE(gb_dgsr_subtotal, 0) ELSE 0 END) AS type1Amount,
        SUM(CASE WHEN gb_dgsr_type = 2 THEN COALESCE(gb_dgsr_subtotal, 0) ELSE 0 END) AS type2Amount,
        SUM(CASE WHEN gb_dgsr_type = 3 THEN COALESCE(gb_dgsr_subtotal, 0) ELSE 0 END) AS type3Amount,
        SUM(CASE WHEN gb_dgsr_type = 6 THEN COALESCE(gb_dgsr_subtotal, 0) ELSE 0 END) AS type6Amount,
        -- 按商品计算宽限期后出库金额（简化版，不精确按菜区分）
        SUM(CASE 
            WHEN gb_dgsr_type IN (1,2,3) 
            THEN COALESCE(gb_dgsr_subtotal, 0) 
            ELSE 0 
        END) AS outboundAfterLastSales
    FROM gb_department_goods_stock_reduce
    WHERE gb_dgsr_gb_distributer_id = 10
      AND gb_dgsr_gb_department_father_id = 48
      AND gb_dgsr_date >= '2026-06-01'
      AND gb_dgsr_date <= '2026-06-09'
    GROUP BY gb_dgsr_gb_dis_goods_id
) gsr
LEFT JOIN gb_distributer_goods dg ON dg.gb_distributer_goods_id = gsr.goodsId
LEFT JOIN (
    -- 每个商品的关联菜中，最晚的销售日期
    SELECT 
        dfg.gb_dfg_dis_goods_id AS goodsId,
        MAX(fs.gb_dfs_full_date) AS lastSalesDate
    FROM gb_distributer_food_goods dfg
    JOIN gb_dep_food_sales fs ON fs.gb_dfs_food_id = dfg.gb_dfg_food_id
        AND fs.gb_dfs_distributer_id = 10
        AND fs.gb_dfs_dep_father_id = 48
        AND fs.gb_dfs_full_date >= '2026-06-01'
        AND fs.gb_dfs_full_date <= '2026-06-09'
    WHERE dfg.gb_dfg_dis_id = 10
      AND dfg.gb_dfg_status != 0
    GROUP BY dfg.gb_dfg_dis_goods_id
) lastSale ON lastSale.goodsId = gsr.goodsId
WHERE gsr.type1 + gsr.type2 + gsr.type3 > 0
ORDER BY outbound123 DESC;


-- Step 5: 汇总对比
SELECT 
    'subtotalProduceType1(原始type1出库)' AS label,
    ROUND(SUM(CASE WHEN gb_dgsr_type = 1 THEN COALESCE(gb_dgsr_subtotal, 0) ELSE 0 END), 2) AS amount
FROM gb_department_goods_stock_reduce
WHERE gb_dgsr_gb_distributer_id = 10
  AND gb_dgsr_gb_department_father_id = 48
  AND gb_dgsr_date >= '2026-06-01'
  AND gb_dgsr_date <= '2026-06-09'

UNION ALL

SELECT 
    'outbound123(type1+2+3)' AS label,
    ROUND(SUM(CASE WHEN gb_dgsr_type IN (1,2,3) THEN COALESCE(gb_dgsr_subtotal, 0) ELSE 0 END), 2)
FROM gb_department_goods_stock_reduce
WHERE gb_dgsr_gb_distributer_id = 10
  AND gb_dgsr_gb_department_father_id = 48
  AND gb_dgsr_date >= '2026-06-01'
  AND gb_dgsr_date <= '2026-06-09'

UNION ALL

SELECT 
    'totalOutbound123(withType6)' AS label,
    ROUND(SUM(CASE WHEN gb_dgsr_type IN (1,2,3,6) THEN COALESCE(gb_dgsr_subtotal, 0) ELSE 0 END), 2)
FROM gb_department_goods_stock_reduce
WHERE gb_dgsr_gb_distributer_id = 10
  AND gb_dgsr_gb_department_father_id = 48
  AND gb_dgsr_date >= '2026-06-01'
  AND gb_dgsr_date <= '2026-06-09';


-- Step 6: 差异菜及其分摊成本详情
-- 这些菜只存在于扩展范围，它们实际分摊到了多少成本
SELECT 
    ef.foodId,
    df.gb_df_food_name AS foodName,
    dfg.gb_dfg_dis_goods_id AS goodsId,
    dfg.gb_dfg_goods_name AS goodsName,
    gsr.type1Outbound,
    gsr.type1Weight,
    '该菜无销售，分摊的成本计入nonVerified(非核销)' AS note
FROM tmp_expanded_foods ef
LEFT JOIN tmp_initial_foods inf ON ef.foodId = inf.foodId
JOIN gb_distributer_food df ON df.gb_distributer_food_id = ef.foodId
JOIN gb_distributer_food_goods dfg ON dfg.gb_dfg_food_id = ef.foodId AND dfg.gb_dfg_status != 0
LEFT JOIN (
    SELECT 
        gb_dgsr_gb_dis_goods_id AS goodsId,
        SUM(COALESCE(gb_dgsr_subtotal, 0)) AS type1Outbound,
        SUM(COALESCE(gb_dgsr_weight, 0)) AS type1Weight
    FROM gb_department_goods_stock_reduce
    WHERE gb_dgsr_gb_distributer_id = 10
      AND gb_dgsr_type = 1
      AND gb_dgsr_gb_department_father_id = 48
      AND gb_dgsr_date >= '2026-06-01'
      AND gb_dgsr_date <= '2026-06-09'
    GROUP BY gb_dgsr_gb_dis_goods_id
) gsr ON gsr.goodsId = dfg.gb_dfg_dis_goods_id
WHERE inf.foodId IS NULL  -- 只在扩展范围
AND dfg.gb_dfg_dis_id = 10
ORDER BY ef.foodId;

-- 清理临时表
DROP TEMPORARY TABLE IF EXISTS tmp_initial_foods;
DROP TEMPORARY TABLE IF EXISTS tmp_expanded_foods;
