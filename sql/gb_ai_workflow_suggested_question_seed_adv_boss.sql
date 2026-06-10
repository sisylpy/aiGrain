-- ============================================================================
-- ADV_BOSS（老板经营顾问）推荐问句 + topic 归类种子
-- 依赖：gb_ai_workflow_suggested_question.sql、gb_ai_advisor_workflow_seed.sql
-- question_code 全局唯一，域前缀 bo_
-- 幂等：先按文案回填 topic；INSERT … ON DUPLICATE KEY UPDATE（uk question_code）
-- 不新增 Harness 能力：仅组织既有 workflow / contract；问句点击仍只填 text
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 已有问句（仅缺 topic）：按文案回填，不新增同行
-- ---------------------------------------------------------------------------
UPDATE gb_ai_workflow_suggested_question
SET gb_ai_wsq_topic_id = 'business_overview',
    gb_ai_wsq_topic_title = '经营概览',
    gb_ai_wsq_topic_description = '先看整体经营情况，承接经营汇报与四域概览',
    gb_ai_wsq_topic_sort = 1
WHERE gb_ai_wsq_question_text IN (
    '今天经营情况怎么样？',
    '这周经营情况怎么样？',
    '这个月经营情况怎么样？',
    '这个月经营得怎么样？',
    '这个月经营怎么样？'
);

UPDATE gb_ai_workflow_suggested_question
SET gb_ai_wsq_topic_id = 'menu_operation',
    gb_ai_wsq_topic_title = '菜单经营',
    gb_ai_wsq_topic_description = '看菜单结构、销量与利润，而非单菜孤立查看',
    gb_ai_wsq_topic_sort = 2
WHERE gb_ai_wsq_question_text IN (
    '菜单整体经营情况怎么样？',
    '哪些菜卖得好，利润也比较稳定？',
    '哪些菜销量不错，但利润空间偏低？'
);

UPDATE gb_ai_workflow_suggested_question
SET gb_ai_wsq_topic_id = 'dish_performance',
    gb_ai_wsq_topic_title = '菜品表现',
    gb_ai_wsq_topic_description = '看具体菜品销量、毛利与成本变化',
    gb_ai_wsq_topic_sort = 3
WHERE gb_ai_wsq_question_text IN (
    '最近哪些菜表现比较好？',
    '哪些菜最近销量变化比较明显？',
    '哪些菜的成本变化需要关注？'
);

UPDATE gb_ai_workflow_suggested_question
SET gb_ai_wsq_topic_id = 'stock_ingredient',
    gb_ai_wsq_topic_title = '库存与原料',
    gb_ai_wsq_topic_description = '看库存监督诊断、账面库存金额排行与用量变化',
    gb_ai_wsq_topic_sort = 4
WHERE gb_ai_wsq_question_text IN (
    '库存核查情况怎么样？',
    '哪些商品账面库存金额较低？',
    '哪些原料用量变化比较明显？'
);

-- ---------------------------------------------------------------------------
-- 2) topic 1 — 经营概览（BUSINESS_OVERVIEW · business_overview.summary）
-- workflow：WF_REVENUE_MONTH_REVIEW（四域概览入口；不新造 workflow）
-- ---------------------------------------------------------------------------
INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'business_overview', '经营概览', '先看整体经营情况，承接经营汇报与四域概览', 1,
       'bo_overview_today', '今天经营情况怎么样？',
       1, 'ACTIVE', 'BOTH', 1,
       'BUSINESS_OVERVIEW', 'business_overview.summary'
FROM gb_ai_workflow w
WHERE w.gb_ai_workflow_code = 'WF_REVENUE_MONTH_REVIEW'
  AND NOT EXISTS (
      SELECT 1 FROM gb_ai_workflow_suggested_question sq
      WHERE sq.gb_ai_wsq_question_text = '今天经营情况怎么样？'
  )
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_topic_id = VALUES(gb_ai_wsq_topic_id),
    gb_ai_wsq_topic_title = VALUES(gb_ai_wsq_topic_title),
    gb_ai_wsq_topic_description = VALUES(gb_ai_wsq_topic_description),
    gb_ai_wsq_topic_sort = VALUES(gb_ai_wsq_topic_sort),
    gb_ai_wsq_question_text = VALUES(gb_ai_wsq_question_text),
    gb_ai_wsq_enabled = VALUES(gb_ai_wsq_enabled),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status),
    gb_ai_wsq_scene = VALUES(gb_ai_wsq_scene),
    gb_ai_wsq_sort = VALUES(gb_ai_wsq_sort),
    gb_ai_wsq_intent_hint = VALUES(gb_ai_wsq_intent_hint),
    gb_ai_wsq_contract_hint = VALUES(gb_ai_wsq_contract_hint);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'business_overview', '经营概览', '先看整体经营情况，承接经营汇报与四域概览', 1,
       'bo_overview_this_week', '这周经营情况怎么样？',
       1, 'ACTIVE', 'BOTH', 2,
       'BUSINESS_OVERVIEW', 'business_overview.summary'
FROM gb_ai_workflow w
WHERE w.gb_ai_workflow_code = 'WF_REVENUE_MONTH_REVIEW'
  AND NOT EXISTS (
      SELECT 1 FROM gb_ai_workflow_suggested_question sq
      WHERE sq.gb_ai_wsq_question_text = '这周经营情况怎么样？'
  )
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_topic_id = VALUES(gb_ai_wsq_topic_id),
    gb_ai_wsq_topic_title = VALUES(gb_ai_wsq_topic_title),
    gb_ai_wsq_topic_description = VALUES(gb_ai_wsq_topic_description),
    gb_ai_wsq_topic_sort = VALUES(gb_ai_wsq_topic_sort),
    gb_ai_wsq_question_text = VALUES(gb_ai_wsq_question_text),
    gb_ai_wsq_enabled = VALUES(gb_ai_wsq_enabled),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status),
    gb_ai_wsq_scene = VALUES(gb_ai_wsq_scene),
    gb_ai_wsq_sort = VALUES(gb_ai_wsq_sort),
    gb_ai_wsq_intent_hint = VALUES(gb_ai_wsq_intent_hint),
    gb_ai_wsq_contract_hint = VALUES(gb_ai_wsq_contract_hint);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'business_overview', '经营概览', '先看整体经营情况，承接经营汇报与四域概览', 1,
       'bo_overview_this_month', '这个月经营情况怎么样？',
       1, 'ACTIVE', 'BOTH', 3,
       'BUSINESS_OVERVIEW', 'business_overview.summary'
FROM gb_ai_workflow w
WHERE w.gb_ai_workflow_code = 'WF_REVENUE_MONTH_REVIEW'
  AND NOT EXISTS (
      SELECT 1 FROM gb_ai_workflow_suggested_question sq
      WHERE sq.gb_ai_wsq_question_text IN (
          '这个月经营情况怎么样？',
          '这个月经营得怎么样？',
          '这个月经营怎么样？'
      )
  )
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_topic_id = VALUES(gb_ai_wsq_topic_id),
    gb_ai_wsq_topic_title = VALUES(gb_ai_wsq_topic_title),
    gb_ai_wsq_topic_description = VALUES(gb_ai_wsq_topic_description),
    gb_ai_wsq_topic_sort = VALUES(gb_ai_wsq_topic_sort),
    gb_ai_wsq_question_text = VALUES(gb_ai_wsq_question_text),
    gb_ai_wsq_enabled = VALUES(gb_ai_wsq_enabled),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status),
    gb_ai_wsq_scene = VALUES(gb_ai_wsq_scene),
    gb_ai_wsq_sort = VALUES(gb_ai_wsq_sort),
    gb_ai_wsq_intent_hint = VALUES(gb_ai_wsq_intent_hint),
    gb_ai_wsq_contract_hint = VALUES(gb_ai_wsq_contract_hint);

-- ---------------------------------------------------------------------------
-- 3) topic 2 — 菜单经营（MENU_OPERATION · P1 已交付 workflow）
-- ---------------------------------------------------------------------------
INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'menu_operation', '菜单经营', '看菜单结构、销量与利润，而非单菜孤立查看', 2,
       'bo_menu_overview', '菜单整体经营情况怎么样？',
       1, 'ACTIVE', 'BOTH', 1,
       'MENU_OPERATION', 'menu.operation.overview.v1'
FROM gb_ai_workflow w
WHERE w.gb_ai_workflow_code = 'WF_MENU_OPERATION_OVERVIEW'
  AND NOT EXISTS (
      SELECT 1 FROM gb_ai_workflow_suggested_question sq
      WHERE sq.gb_ai_wsq_question_text = '菜单整体经营情况怎么样？'
  )
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_topic_id = VALUES(gb_ai_wsq_topic_id),
    gb_ai_wsq_topic_title = VALUES(gb_ai_wsq_topic_title),
    gb_ai_wsq_topic_description = VALUES(gb_ai_wsq_topic_description),
    gb_ai_wsq_topic_sort = VALUES(gb_ai_wsq_topic_sort),
    gb_ai_wsq_question_text = VALUES(gb_ai_wsq_question_text),
    gb_ai_wsq_enabled = VALUES(gb_ai_wsq_enabled),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status),
    gb_ai_wsq_scene = VALUES(gb_ai_wsq_scene),
    gb_ai_wsq_sort = VALUES(gb_ai_wsq_sort),
    gb_ai_wsq_intent_hint = VALUES(gb_ai_wsq_intent_hint),
    gb_ai_wsq_contract_hint = VALUES(gb_ai_wsq_contract_hint);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'menu_operation', '菜单经营', '看菜单结构、销量与利润，而非单菜孤立查看', 2,
       'bo_menu_stable_sales_margin', '哪些菜卖得好，利润也比较稳定？',
       1, 'ACTIVE', 'BOTH', 2,
       'MENU_OPERATION', 'menu.operation.overview.v1'
FROM gb_ai_workflow w
WHERE w.gb_ai_workflow_code = 'WF_MENU_OPERATION_OVERVIEW'
  AND NOT EXISTS (
      SELECT 1 FROM gb_ai_workflow_suggested_question sq
      WHERE sq.gb_ai_wsq_question_text = '哪些菜卖得好，利润也比较稳定？'
  )
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_topic_id = VALUES(gb_ai_wsq_topic_id),
    gb_ai_wsq_topic_title = VALUES(gb_ai_wsq_topic_title),
    gb_ai_wsq_topic_description = VALUES(gb_ai_wsq_topic_description),
    gb_ai_wsq_topic_sort = VALUES(gb_ai_wsq_topic_sort),
    gb_ai_wsq_question_text = VALUES(gb_ai_wsq_question_text),
    gb_ai_wsq_enabled = VALUES(gb_ai_wsq_enabled),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status),
    gb_ai_wsq_scene = VALUES(gb_ai_wsq_scene),
    gb_ai_wsq_sort = VALUES(gb_ai_wsq_sort),
    gb_ai_wsq_intent_hint = VALUES(gb_ai_wsq_intent_hint),
    gb_ai_wsq_contract_hint = VALUES(gb_ai_wsq_contract_hint);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'menu_operation', '菜单经营', '看菜单结构、销量与利润，而非单菜孤立查看', 2,
       'bo_menu_high_sales_low_margin', '哪些菜销量不错，但利润空间偏低？',
       1, 'ACTIVE', 'BOTH', 3,
       'MENU_OPERATION', 'menu.dish.high_sales_low_profit.v1'
FROM gb_ai_workflow w
WHERE w.gb_ai_workflow_code = 'WF_MENU_HIGH_SALES_LOW_MARGIN'
  AND NOT EXISTS (
      SELECT 1 FROM gb_ai_workflow_suggested_question sq
      WHERE sq.gb_ai_wsq_question_text = '哪些菜销量不错，但利润空间偏低？'
  )
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_topic_id = VALUES(gb_ai_wsq_topic_id),
    gb_ai_wsq_topic_title = VALUES(gb_ai_wsq_topic_title),
    gb_ai_wsq_topic_description = VALUES(gb_ai_wsq_topic_description),
    gb_ai_wsq_topic_sort = VALUES(gb_ai_wsq_topic_sort),
    gb_ai_wsq_question_text = VALUES(gb_ai_wsq_question_text),
    gb_ai_wsq_enabled = VALUES(gb_ai_wsq_enabled),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status),
    gb_ai_wsq_scene = VALUES(gb_ai_wsq_scene),
    gb_ai_wsq_sort = VALUES(gb_ai_wsq_sort),
    gb_ai_wsq_intent_hint = VALUES(gb_ai_wsq_intent_hint),
    gb_ai_wsq_contract_hint = VALUES(gb_ai_wsq_contract_hint);

-- ---------------------------------------------------------------------------
-- 4) topic 3 — 菜品表现（DISH_SALES / DISH_PROFIT）
-- ---------------------------------------------------------------------------
INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'dish_performance', '菜品表现', '看具体菜品销量、毛利与成本变化', 3,
       'bo_dish_top_performance', '最近哪些菜表现比较好？',
       1, 'ACTIVE', 'BOTH', 1,
       'DISH_SALES_QUERY', 'dish_sales.count_ranking_high'
FROM gb_ai_workflow w
WHERE w.gb_ai_workflow_code = 'WF_DISH_SALES_RANK'
  AND NOT EXISTS (
      SELECT 1 FROM gb_ai_workflow_suggested_question sq
      WHERE sq.gb_ai_wsq_question_text = '最近哪些菜表现比较好？'
  )
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_topic_id = VALUES(gb_ai_wsq_topic_id),
    gb_ai_wsq_topic_title = VALUES(gb_ai_wsq_topic_title),
    gb_ai_wsq_topic_description = VALUES(gb_ai_wsq_topic_description),
    gb_ai_wsq_topic_sort = VALUES(gb_ai_wsq_topic_sort),
    gb_ai_wsq_question_text = VALUES(gb_ai_wsq_question_text),
    gb_ai_wsq_enabled = VALUES(gb_ai_wsq_enabled),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status),
    gb_ai_wsq_scene = VALUES(gb_ai_wsq_scene),
    gb_ai_wsq_sort = VALUES(gb_ai_wsq_sort),
    gb_ai_wsq_intent_hint = VALUES(gb_ai_wsq_intent_hint),
    gb_ai_wsq_contract_hint = VALUES(gb_ai_wsq_contract_hint);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'dish_performance', '菜品表现', '看具体菜品销量、毛利与成本变化', 3,
       'bo_dish_sales_change', '哪些菜最近销量变化比较明显？',
       1, 'COMING_SOON', 'BOTH', 2,
       'DISH_SALES_QUERY', 'dish_sales.trend'
FROM gb_ai_workflow w
WHERE w.gb_ai_workflow_code = 'WF_DISH_SALES_RANK'
  AND NOT EXISTS (
      SELECT 1 FROM gb_ai_workflow_suggested_question sq
      WHERE sq.gb_ai_wsq_question_text = '哪些菜最近销量变化比较明显？'
  )
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_topic_id = VALUES(gb_ai_wsq_topic_id),
    gb_ai_wsq_topic_title = VALUES(gb_ai_wsq_topic_title),
    gb_ai_wsq_topic_description = VALUES(gb_ai_wsq_topic_description),
    gb_ai_wsq_topic_sort = VALUES(gb_ai_wsq_topic_sort),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status),
    gb_ai_wsq_intent_hint = VALUES(gb_ai_wsq_intent_hint),
    gb_ai_wsq_contract_hint = VALUES(gb_ai_wsq_contract_hint);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'dish_performance', '菜品表现', '看具体菜品销量、毛利与成本变化', 3,
       'bo_dish_cost_change', '哪些菜的成本变化需要关注？',
       1, 'COMING_SOON', 'BOTH', 3,
       'DISH_PROFIT', 'dish_profit.ranking_max_cost_gap'
FROM gb_ai_workflow w
WHERE w.gb_ai_workflow_code = 'WF_DISH_MARGIN_RANK'
  AND NOT EXISTS (
      SELECT 1 FROM gb_ai_workflow_suggested_question sq
      WHERE sq.gb_ai_wsq_question_text = '哪些菜的成本变化需要关注？'
  )
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_topic_id = VALUES(gb_ai_wsq_topic_id),
    gb_ai_wsq_topic_title = VALUES(gb_ai_wsq_topic_title),
    gb_ai_wsq_topic_description = VALUES(gb_ai_wsq_topic_description),
    gb_ai_wsq_topic_sort = VALUES(gb_ai_wsq_topic_sort),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status),
    gb_ai_wsq_intent_hint = VALUES(gb_ai_wsq_intent_hint),
    gb_ai_wsq_contract_hint = VALUES(gb_ai_wsq_contract_hint);

-- ---------------------------------------------------------------------------
-- 5) topic 4 — 库存与原料（WH-I 监督 / WAREHOUSE 排行 / STOCK_REDUCE）
-- ---------------------------------------------------------------------------
INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'stock_ingredient', '库存与原料', '看库存监督诊断、账面库存金额排行与用量变化', 4,
       'bo_stock_reconciliation', '库存核查情况怎么样？',
       1, 'ACTIVE', 'BOTH', 1,
       'WAREHOUSE_STOCK_OVERVIEW', 'warehouse.inventory_supervision.v1'
FROM gb_ai_workflow w
WHERE w.gb_ai_workflow_code = 'WF_STOCK_ON_HAND'
  AND NOT EXISTS (
      SELECT 1 FROM gb_ai_workflow_suggested_question sq
      WHERE sq.gb_ai_wsq_question_text = '库存核查情况怎么样？'
  )
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_topic_id = VALUES(gb_ai_wsq_topic_id),
    gb_ai_wsq_topic_title = VALUES(gb_ai_wsq_topic_title),
    gb_ai_wsq_topic_description = VALUES(gb_ai_wsq_topic_description),
    gb_ai_wsq_topic_sort = VALUES(gb_ai_wsq_topic_sort),
    gb_ai_wsq_question_text = VALUES(gb_ai_wsq_question_text),
    gb_ai_wsq_enabled = VALUES(gb_ai_wsq_enabled),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status),
    gb_ai_wsq_scene = VALUES(gb_ai_wsq_scene),
    gb_ai_wsq_sort = VALUES(gb_ai_wsq_sort),
    gb_ai_wsq_intent_hint = VALUES(gb_ai_wsq_intent_hint),
    gb_ai_wsq_contract_hint = VALUES(gb_ai_wsq_contract_hint);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'stock_ingredient', '库存与原料', '看库存监督诊断、账面库存金额排行与用量变化', 4,
       'bo_stock_low_ingredient', '哪些商品账面库存金额较低？',
       1, 'ACTIVE', 'BOTH', 2,
       'WAREHOUSE_STOCK_OVERVIEW', 'warehouse.goods_amount_ranking_low'
FROM gb_ai_workflow w
WHERE w.gb_ai_workflow_code = 'WF_STOCK_ON_HAND'
  AND NOT EXISTS (
      SELECT 1 FROM gb_ai_workflow_suggested_question sq
      WHERE sq.gb_ai_wsq_question_text = '哪些商品账面库存金额较低？'
  )
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_topic_id = VALUES(gb_ai_wsq_topic_id),
    gb_ai_wsq_topic_title = VALUES(gb_ai_wsq_topic_title),
    gb_ai_wsq_topic_description = VALUES(gb_ai_wsq_topic_description),
    gb_ai_wsq_topic_sort = VALUES(gb_ai_wsq_topic_sort),
    gb_ai_wsq_question_text = VALUES(gb_ai_wsq_question_text),
    gb_ai_wsq_enabled = VALUES(gb_ai_wsq_enabled),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status),
    gb_ai_wsq_scene = VALUES(gb_ai_wsq_scene),
    gb_ai_wsq_sort = VALUES(gb_ai_wsq_sort),
    gb_ai_wsq_intent_hint = VALUES(gb_ai_wsq_intent_hint),
    gb_ai_wsq_contract_hint = VALUES(gb_ai_wsq_contract_hint);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'stock_ingredient', '库存与原料', '看库存监督诊断、账面库存金额排行与用量变化', 4,
       'bo_ingredient_usage_change', '哪些原料用量变化比较明显？',
       1, 'COMING_SOON', 'BOTH', 3,
       'STOCK_REDUCE_QUERY', NULL
FROM gb_ai_workflow w
WHERE w.gb_ai_workflow_code = 'WF_STOCK_CONSUMPTION'
  AND NOT EXISTS (
      SELECT 1 FROM gb_ai_workflow_suggested_question sq
      WHERE sq.gb_ai_wsq_question_text = '哪些原料用量变化比较明显？'
  )
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_topic_id = VALUES(gb_ai_wsq_topic_id),
    gb_ai_wsq_topic_title = VALUES(gb_ai_wsq_topic_title),
    gb_ai_wsq_topic_description = VALUES(gb_ai_wsq_topic_description),
    gb_ai_wsq_topic_sort = VALUES(gb_ai_wsq_topic_sort),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status),
    gb_ai_wsq_intent_hint = VALUES(gb_ai_wsq_intent_hint),
    gb_ai_wsq_contract_hint = VALUES(gb_ai_wsq_contract_hint);

-- 启用 WH-I 库存监督推荐问句（历史曾绑 purchase.risk / COMING_SOON）
UPDATE gb_ai_workflow_suggested_question sq
INNER JOIN gb_ai_workflow w ON w.gb_ai_workflow_code = 'WF_STOCK_ON_HAND'
SET sq.gb_ai_wsq_workflow_id = w.gb_ai_workflow_id,
    sq.gb_ai_wsq_workflow_code = w.gb_ai_workflow_code,
    sq.gb_ai_wsq_topic_description = '看库存监督诊断、账面库存金额排行与用量变化',
    sq.gb_ai_wsq_enabled = 1,
    sq.gb_ai_wsq_status = 'ACTIVE',
    sq.gb_ai_wsq_intent_hint = 'WAREHOUSE_STOCK_OVERVIEW',
    sq.gb_ai_wsq_contract_hint = 'warehouse.inventory_supervision.v1'
WHERE sq.gb_ai_wsq_question_code = 'bo_stock_reconciliation'
   OR sq.gb_ai_wsq_question_text = '库存核查情况怎么样？';

-- 清理历史误导文案：「库存偏少」不得绑定 WH-C 金额低排行
UPDATE gb_ai_workflow_suggested_question
SET gb_ai_wsq_question_text = '哪些商品账面库存金额较低？',
    gb_ai_wsq_topic_description = '看库存监督诊断、账面库存金额排行与用量变化',
    gb_ai_wsq_intent_hint = 'WAREHOUSE_STOCK_OVERVIEW',
    gb_ai_wsq_contract_hint = 'warehouse.goods_amount_ranking_low'
WHERE gb_ai_wsq_question_text IN (
    '哪些常用原料库存偏少？',
    '哪些常用原料库存偏少',
    '常用原料库存偏少有哪些？'
);
