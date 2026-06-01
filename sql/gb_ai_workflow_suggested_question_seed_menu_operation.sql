-- ============================================================================
-- MenuOperation 推荐问句种子（依赖 gb_ai_workflow_suggested_question.sql + workflow seed）
-- question_code 全局唯一，域前缀 mo_
-- 幂等：ON DUPLICATE KEY UPDATE（uk question_code）
-- ============================================================================

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'dish_profitability', '菜品赚钱能力', '看菜单整体是否赚钱，以及高销量低利润菜品', 1,
       'mo_profit_menu_overview', '这个月菜单经营怎么样？',
       1, 'ACTIVE', 'BOTH', 1,
       'MENU_OPERATION', 'menu.operation.overview.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_OPERATION_OVERVIEW'
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
       'dish_profitability', '菜品赚钱能力', '看菜单整体是否赚钱，以及高销量低利润菜品', 1,
       'mo_profit_high_sales_low_margin', '哪些畅销菜毛利偏低？',
       1, 'ACTIVE', 'BOTH', 2,
       'MENU_OPERATION', 'menu.dish.high_sales_low_profit.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_HIGH_SALES_LOW_MARGIN'
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_question_text = VALUES(gb_ai_wsq_question_text),
    gb_ai_wsq_enabled = VALUES(gb_ai_wsq_enabled),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status),
    gb_ai_wsq_sort = VALUES(gb_ai_wsq_sort),
    gb_ai_wsq_contract_hint = VALUES(gb_ai_wsq_contract_hint);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'dish_profitability', '菜品赚钱能力', '看菜单整体是否赚钱，以及高销量低利润菜品', 1,
       'mo_profit_top_margin_dishes', '哪些菜毛利率最高？',
       1, 'COMING_SOON', 'BOTH', 3,
       'MENU_OPERATION', 'menu.dish.profit_ranking.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_PROFIT_RANKING'
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status),
    gb_ai_wsq_contract_hint = VALUES(gb_ai_wsq_contract_hint);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'dish_profitability', '菜品赚钱能力', '看菜单整体是否赚钱，以及高销量低利润菜品', 1,
       'mo_profit_top_profit_dishes', '哪些菜实际利润贡献最高？',
       1, 'COMING_SOON', 'BOTH', 4,
       'MENU_OPERATION', 'menu.dish.profit_ranking.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_PROFIT_RANKING'
ON DUPLICATE KEY UPDATE
    gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id),
    gb_ai_wsq_workflow_code = VALUES(gb_ai_wsq_workflow_code),
    gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'dish_cost_anomaly', '菜品成本异常', '找出拖后腿、拖累菜单利润的风险菜品', 2,
       'mo_cost_drag_dishes', '哪些菜在拖后腿？',
       1, 'ACTIVE', 'BOTH', 1,
       'MENU_OPERATION', 'menu.operation.overview.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_PROFIT_DRAG_DISHES'
ON DUPLICATE KEY UPDATE gb_ai_wsq_status = VALUES(gb_ai_wsq_status), gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'dish_cost_anomaly', '菜品成本异常', '找出拖后腿、拖累菜单利润的风险菜品', 2,
       'mo_cost_hurt_menu_profit', '哪些菜拖累菜单利润？',
       1, 'ACTIVE', 'BOTH', 2,
       'MENU_OPERATION', 'menu.operation.overview.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_PROFIT_DRAG_DISHES'
ON DUPLICATE KEY UPDATE gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'dish_cost_anomaly', '菜品成本异常', '找出拖后腿、拖累菜单利润的风险菜品', 2,
       'mo_cost_abnormal_high', '哪些菜实际成本偏高？',
       1, 'COMING_SOON', 'BOTH', 3,
       'MENU_OPERATION', 'menu.dish.single_analysis.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_COST_ABNORMAL_ANALYSIS'
ON DUPLICATE KEY UPDATE gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id), gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'dish_cost_anomaly', '菜品成本异常', '找出拖后腿、拖累菜单利润的风险菜品', 2,
       'mo_cost_recipe_review', '哪些菜需要复核配方成本？',
       1, 'COMING_SOON', 'BOTH', 4,
       'DISH_COST', 'dish.cost.analysis.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_COST_ABNORMAL_ANALYSIS'
ON DUPLICATE KEY UPDATE gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id), gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'dish_pricing_advice', '菜品定价建议', '具体定价建议能力即将支持；当前请从「菜品赚钱能力」看高销量低利润菜', 3,
       'mo_price_should_raise', '哪些菜该考虑涨价？',
       1, 'COMING_SOON', 'BOTH', 1,
       'MENU_OPERATION', 'menu.dish.pricing_advice.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_PRICING_ADVICE'
ON DUPLICATE KEY UPDATE gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id), gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'dish_pricing_advice', '菜品定价建议', '具体定价建议能力即将支持；当前请从「菜品赚钱能力」看高销量低利润菜', 3,
       'mo_price_too_low', '有没有定价偏低的畅销菜？',
       1, 'COMING_SOON', 'BOTH', 2,
       'MENU_OPERATION', 'menu.dish.pricing_advice.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_PRICING_ADVICE'
ON DUPLICATE KEY UPDATE gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'dish_pricing_advice', '菜品定价建议', '具体定价建议能力即将支持；当前请从「菜品赚钱能力」看高销量低利润菜', 3,
       'mo_price_high_sales_low_margin', '哪些菜卖得多但利润低？',
       1, 'COMING_SOON', 'BOTH', 3,
       'MENU_OPERATION', 'menu.dish.pricing_advice.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_PRICING_ADVICE'
ON DUPLICATE KEY UPDATE gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'dish_pricing_advice', '菜品定价建议', '具体定价建议能力即将支持；当前请从「菜品赚钱能力」看高销量低利润菜', 3,
       'mo_price_single_dish', '帮我看看某道菜定价是否合理',
       1, 'COMING_SOON', 'BOTH', 4,
       'MENU_OPERATION', 'menu.dish.single_analysis.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_SINGLE_DISH_ANALYSIS'
ON DUPLICATE KEY UPDATE gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id), gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'menu_structure', '菜单结构优化', '菜单结构是否健康，哪些菜需要调整', 4,
       'mo_structure_optimize', '菜单怎么优化？',
       1, 'ACTIVE', 'BOTH', 1,
       'MENU_OPERATION', 'menu.action.recommendation.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_ADJUSTMENT_SUGGESTION'
ON DUPLICATE KEY UPDATE gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'menu_structure', '菜单结构优化', '菜单结构是否健康，哪些菜需要调整', 4,
       'mo_structure_health', '这个月菜单结构健康吗？',
       1, 'ACTIVE', 'BOTH', 2,
       'MENU_OPERATION', 'menu.operation.overview.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_OPERATION_OVERVIEW'
ON DUPLICATE KEY UPDATE gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'menu_structure', '菜单结构优化', '菜单结构是否健康，哪些菜需要调整', 4,
       'mo_structure_need_adjust', '有哪些菜需要调整？',
       1, 'ACTIVE', 'BOTH', 3,
       'MENU_OPERATION', 'menu.action.recommendation.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_ADJUSTMENT_SUGGESTION'
ON DUPLICATE KEY UPDATE gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'menu_structure', '菜单结构优化', '菜单结构是否健康，哪些菜需要调整', 4,
       'mo_structure_slow_moving', '有没有滞销菜？',
       1, 'COMING_SOON', 'BOTH', 4,
       'MENU_OPERATION', 'menu.dish.sales_ranking.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_SALES_RANKING'
ON DUPLICATE KEY UPDATE gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id), gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'menu_lifecycle', '保留 / 主推 / 下架', '基于当前数据，看主推候选与需优先处理的风险菜', 5,
       'mo_lifecycle_keep_promote', '哪些菜适合继续主推？',
       1, 'ACTIVE', 'BOTH', 1,
       'MENU_OPERATION', 'menu.action.recommendation.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_OPERATION_OVERVIEW'
ON DUPLICATE KEY UPDATE gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'menu_lifecycle', '保留 / 主推 / 下架', '基于当前数据，看主推候选与需优先处理的风险菜', 5,
       'mo_lifecycle_consider_drop', '哪些菜该考虑下架？',
       1, 'COMING_SOON', 'BOTH', 2,
       'MENU_OPERATION', 'menu.action.recommendation.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_DISH_LIFECYCLE'
ON DUPLICATE KEY UPDATE gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id), gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'menu_lifecycle', '保留 / 主推 / 下架', '基于当前数据，看主推候选与需优先处理的风险菜', 5,
       'mo_lifecycle_risk_dishes', '哪些菜风险最高需要先处理？',
       1, 'ACTIVE', 'BOTH', 3,
       'MENU_OPERATION', 'menu.action.recommendation.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_OPERATION_OVERVIEW'
ON DUPLICATE KEY UPDATE gb_ai_wsq_status = VALUES(gb_ai_wsq_status);

INSERT INTO gb_ai_workflow_suggested_question (
    gb_ai_wsq_workflow_id, gb_ai_wsq_workflow_code,
    gb_ai_wsq_topic_id, gb_ai_wsq_topic_title, gb_ai_wsq_topic_description, gb_ai_wsq_topic_sort,
    gb_ai_wsq_question_code, gb_ai_wsq_question_text,
    gb_ai_wsq_enabled, gb_ai_wsq_status, gb_ai_wsq_scene, gb_ai_wsq_sort,
    gb_ai_wsq_intent_hint, gb_ai_wsq_contract_hint)
SELECT w.gb_ai_workflow_id, w.gb_ai_workflow_code,
       'menu_lifecycle', '保留 / 主推 / 下架', '基于当前数据，看主推候选与需优先处理的风险菜', 5,
       'mo_lifecycle_opportunity', '哪些菜还有增长空间值得加推？',
       1, 'COMING_SOON', 'BOTH', 4,
       'MENU_OPERATION', 'menu.operation.overview.v1'
FROM gb_ai_workflow w WHERE w.gb_ai_workflow_code = 'WF_MENU_DISH_LIFECYCLE'
ON DUPLICATE KEY UPDATE gb_ai_wsq_workflow_id = VALUES(gb_ai_wsq_workflow_id), gb_ai_wsq_status = VALUES(gb_ai_wsq_status);
