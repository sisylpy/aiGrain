-- ============================================================
-- 根因诊断：verifiedTotalAmount(1116.11) vs actualCostTotal(1107.94) 
-- 八块多差异来源：outboundIngredientAnalysis 用 consumptionQty 分摊，ingredientAnalysis 用 salesQty 分摊
-- 参数：disId=10, depFatherId=48, 2026-06-01 ~ 2026-06-09
--
-- gb_dep_food_sales 正确列名（来自 GbDepFoodSalesEntity）：
--   gb_dfs_amount          份数（不是 gb_dfs_count）
--   gb_dfs_distributer_id  批发商ID（不是 gb_dfs_dis_id）
--   gb_dfs_type            类型:1正常 2折扣 3会员 4赠送 5员工餐
--   gb_dfs_subtotal        金额
--   gb_dfs_food_id         菜品ID
--   gb_dfs_dep_father_id   父部门ID
--   gb_dfs_full_date       日期
-- ============================================================

-- Step 1: 找出 consumptionQty > salesQty 的菜（有员工餐/赠送消费但不计入销售）
-- consumptionQty 包含 type 1-5（含 type4赠送, type5员工餐），salesQty只含 type 1,3（操作型销售）
SELECT 
    fs.gb_dfs_food_id AS foodId,
    df.gb_df_food_name AS foodName,
    -- 销售份数（仅 type 1+3 操作型销售，不含赠送和员工餐）
    SUM(CASE WHEN fs.gb_dfs_type IN (1, 3) THEN COALESCE(fs.gb_dfs_amount, 0) ELSE 0 END) AS salesQty,
    -- 总消费份数（type 1-5）
    SUM(COALESCE(fs.gb_dfs_amount, 0)) AS consumptionQty,
    -- 差额
    SUM(COALESCE(fs.gb_dfs_amount, 0)) 
    - SUM(CASE WHEN fs.gb_dfs_type IN (1, 3) THEN COALESCE(fs.gb_dfs_amount, 0) ELSE 0 END) AS extraQty,
    -- 销售金额
    SUM(CASE WHEN fs.gb_dfs_type IN (1,3) THEN COALESCE(fs.gb_dfs_subtotal, 0) ELSE 0 END) AS salesSubtotal,
    -- 各类型明细
    SUM(CASE WHEN fs.gb_dfs_type = 1 THEN COALESCE(fs.gb_dfs_amount, 0) ELSE 0 END) AS type1,
    SUM(CASE WHEN fs.gb_dfs_type = 2 THEN COALESCE(fs.gb_dfs_amount, 0) ELSE 0 END) AS type2,
    SUM(CASE WHEN fs.gb_dfs_type = 3 THEN COALESCE(fs.gb_dfs_amount, 0) ELSE 0 END) AS type3,
    SUM(CASE WHEN fs.gb_dfs_type = 4 THEN COALESCE(fs.gb_dfs_amount, 0) ELSE 0 END) AS type4,
    SUM(CASE WHEN fs.gb_dfs_type = 5 THEN COALESCE(fs.gb_dfs_amount, 0) ELSE 0 END) AS type5
FROM gb_dep_food_sales fs
JOIN gb_distributer_food df ON df.gb_distributer_food_id = fs.gb_dfs_food_id
WHERE fs.gb_dfs_distributer_id = 10
  AND fs.gb_dfs_dep_father_id = 48
  AND fs.gb_dfs_full_date >= '2026-06-01'
  AND fs.gb_dfs_full_date <= '2026-06-09'
GROUP BY fs.gb_dfs_food_id
HAVING extraQty > 0
ORDER BY extraQty DESC;


-- Step 2: 这些「consumptionQty > salesQty」的菜，它们的配方和出库分摊差异
-- consumptionQty 更大 → needThis_consumption > needThis_sales → 分摊更多出库 → verified > actual
WITH diff_dishes AS (
    SELECT 
        fs.gb_dfs_food_id AS foodId,
        SUM(COALESCE(fs.gb_dfs_amount, 0)) AS consumptionQty,
        SUM(CASE WHEN fs.gb_dfs_type IN (1, 3) THEN COALESCE(fs.gb_dfs_amount, 0) ELSE 0 END) AS salesQty
    FROM gb_dep_food_sales fs
    WHERE fs.gb_dfs_distributer_id = 10
      AND fs.gb_dfs_dep_father_id = 48
      AND fs.gb_dfs_full_date >= '2026-06-01'
      AND fs.gb_dfs_full_date <= '2026-06-09'
    GROUP BY fs.gb_dfs_food_id
    HAVING consumptionQty > salesQty
)
SELECT 
    dd.foodId,
    df.gb_df_food_name AS foodName,
    dd.salesQty,
    dd.consumptionQty,
    ROUND((dd.consumptionQty - dd.salesQty), 2) AS extraQty,
    dfg.gb_dfg_dis_goods_id AS goodsId,
    dfg.gb_dfg_goods_name AS goodsName,
    ROUND(COALESCE(dfg.gb_dfg_goods_amount, 0), 4) AS recipeUnit,
    -- 用 salesQty 算的理论需求
    ROUND(dd.salesQty * COALESCE(dfg.gb_dfg_goods_amount, 0), 4) AS needThis_bySales,
    -- 用 consumptionQty 算的理论需求
    ROUND(dd.consumptionQty * COALESCE(dfg.gb_dfg_goods_amount, 0), 4) AS needThis_byConsumption,
    -- 该商品 type1 出库金额
    ROUND(COALESCE(t1.type1Amount, 0), 2) AS type1OutboundAmount,
    ROUND(COALESCE(t1.type1Weight, 0), 2) AS type1OutboundWeight
FROM diff_dishes dd
JOIN gb_distributer_food df ON df.gb_distributer_food_id = dd.foodId
JOIN gb_distributer_food_goods dfg ON dfg.gb_dfg_food_id = dd.foodId 
    AND dfg.gb_dfg_dis_id = 10
    AND dfg.gb_dfg_status != 0
LEFT JOIN (
    SELECT 
        gb_dgsr_gb_dis_goods_id AS goodsId,
        SUM(COALESCE(gb_dgsr_subtotal, 0)) AS type1Amount,
        SUM(COALESCE(gb_dgsr_weight, 0)) AS type1Weight
    FROM gb_department_goods_stock_reduce
    WHERE gb_dgsr_gb_distributer_id = 10
      AND gb_dgsr_type = 1
      AND gb_dgsr_gb_department_father_id = 48
      AND gb_dgsr_date >= '2026-06-01'
      AND gb_dgsr_date <= '2026-06-09'
    GROUP BY gb_dgsr_gb_dis_goods_id
) t1 ON t1.goodsId = dfg.gb_dfg_dis_goods_id
ORDER BY dd.extraQty DESC, dd.foodId, goodsId;


-- Step 3: 汇总对比 - 理论差异vs实际差异
-- 这里有局限：SQL无法精确复现Java的加权分摊逻辑(alloc1 = W * needThis / sumNeed)
-- 但可以看出哪些菜 consumptionQty 更大，方向一致
SELECT 
    'salesQty-based (ingredientAnalysis)' AS method,
    COUNT(DISTINCT fs.gb_dfs_food_id) AS dishCount,
    SUM(CASE WHEN fs.gb_dfs_type IN (1,3) THEN COALESCE(fs.gb_dfs_amount, 0) ELSE 0 END) AS totalQty
FROM gb_dep_food_sales fs
WHERE fs.gb_dfs_distributer_id = 10
  AND fs.gb_dfs_dep_father_id = 48
  AND fs.gb_dfs_full_date >= '2026-06-01'
  AND fs.gb_dfs_full_date <= '2026-06-09'
  AND fs.gb_dfs_type IN (1,3)

UNION ALL

SELECT 
    'consumptionQty-based (outboundIngredientAnalysis)' AS method,
    COUNT(DISTINCT fs.gb_dfs_food_id),
    SUM(COALESCE(fs.gb_dfs_amount, 0))
FROM gb_dep_food_sales fs
WHERE fs.gb_dfs_distributer_id = 10
  AND fs.gb_dfs_dep_father_id = 48
  AND fs.gb_dfs_full_date >= '2026-06-01'
  AND fs.gb_dfs_full_date <= '2026-06-09'
  AND fs.gb_dfs_type IN (1,2,3,4,5);
