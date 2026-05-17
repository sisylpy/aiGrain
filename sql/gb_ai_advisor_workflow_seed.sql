-- ============================================================================
-- Advisor / Workflow 第一批种子数据（可重复执行：按 code 幂等更新）
-- 需先执行 gb_ai_advisor_workflow_mvp.sql
--
-- GET /api/ai/advisors/{id}/conversation 依赖 gb_ai_conversation 上的顾问列：
-- 请先执行 sql/gb_ai_conversation_advisor_thread.sql（建议放在 gb_ai_conversation 表已存在，
-- 如 gb_ai_conversation_history_extensions.sql 之后）。
-- ============================================================================

INSERT INTO gb_ai_advisor (gb_ai_advisor_code, gb_ai_advisor_name, gb_ai_advisor_subtitle, gb_ai_advisor_description, gb_ai_advisor_sort_order, gb_ai_advisor_enabled)
VALUES
    ('ADV_BOSS', '老板经营顾问', '经营概览与复盘', '从营业额、门店对比等角度，帮助经营者快速把握经营态势与复盘要点。', 10, 1),
    ('ADV_PURCHASE', '采购分析顾问', '采购结构与排行', '聚焦采购金额、门店采购分布，辅助采购与成本管控决策。', 20, 1),
    ('ADV_STOCK', '库存管理顾问', '出库与现量', '围绕出库耗用与当前库存，支持门店库存健康度关注。', 30, 1),
    ('ADV_DISH_MARGIN', '菜品毛利顾问', '毛利与销量', '从菜品毛利排行、销量排行切入，优化菜品结构参考。', 40, 1),
    ('ADV_STORE_SUPERVISOR', '门店督导顾问', '多店对比与巡检', '侧重门店间营业额、采购、库存等横向对比，便于督导巡视。', 50, 1),
    ('ADV_REPORT', '报表整理顾问', '多主题汇总', '按需串联经营、采购、库存、菜品等主题，便于整理汇报材料（执行仍走 Harness）。', 60, 1)
ON DUPLICATE KEY UPDATE
    gb_ai_advisor_name = VALUES(gb_ai_advisor_name),
    gb_ai_advisor_subtitle = VALUES(gb_ai_advisor_subtitle),
    gb_ai_advisor_description = VALUES(gb_ai_advisor_description),
    gb_ai_advisor_sort_order = VALUES(gb_ai_advisor_sort_order),
    gb_ai_advisor_enabled = VALUES(gb_ai_advisor_enabled);

INSERT INTO gb_ai_workflow (gb_ai_workflow_code, gb_ai_workflow_name, gb_ai_workflow_description, gb_ai_workflow_category, gb_ai_workflow_sort_order, gb_ai_workflow_enabled)
VALUES
    ('WF_REVENUE_MONTH_REVIEW', '本月营业额复盘工作流', '对本月营业额进行复盘分析（执行由 Harness 完成）。', '经营', 10, 1),
    ('WF_REVENUE_STORE_RANK', '门店营业额排行工作流', '查看门店营业额排行（执行由 Harness 完成）。', '经营', 20, 1),
    ('WF_PURCHASE_AMOUNT', '采购金额分析工作流', '分析采购金额与结构（执行由 Harness 完成）。', '采购', 30, 1),
    ('WF_PURCHASE_STORE_RANK', '门店采购排行工作流', '门店维度采购排行（执行由 Harness 完成）。', '采购', 40, 1),
    ('WF_STOCK_CONSUMPTION', '出库耗用分析工作流', '出库与耗用分析（执行由 Harness 完成）。', '库存', 50, 1),
    ('WF_STOCK_ON_HAND', '库存现量检查工作流', '当前库存现量检查（执行由 Harness 完成）。', '库存', 60, 1),
    ('WF_DISH_MARGIN_RANK', '菜品毛利排行工作流', '菜品毛利排行（执行由 Harness 完成）。', '菜品', 70, 1),
    ('WF_DISH_SALES_RANK', '菜品销量排行工作流', '菜品销量排行（执行由 Harness 完成）。', '菜品', 80, 1)
ON DUPLICATE KEY UPDATE
    gb_ai_workflow_name = VALUES(gb_ai_workflow_name),
    gb_ai_workflow_description = VALUES(gb_ai_workflow_description),
    gb_ai_workflow_category = VALUES(gb_ai_workflow_category),
    gb_ai_workflow_sort_order = VALUES(gb_ai_workflow_sort_order),
    gb_ai_workflow_enabled = VALUES(gb_ai_workflow_enabled);

-- 绑定：先删该批顾问-工作流组合再插入，避免重复执行产生重复行（uk 已防重复，此段仅清理历史脏数据可选用）
-- 顾问 ↔ 工作流
INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order, gb_ai_aw_pinned, gb_ai_aw_is_default)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 10, 0, 1
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_BOSS' AND w.gb_ai_workflow_code = 'WF_REVENUE_MONTH_REVIEW'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order), gb_ai_aw_is_default = VALUES(gb_ai_aw_is_default);

INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 20
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_BOSS' AND w.gb_ai_workflow_code = 'WF_REVENUE_STORE_RANK'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order);

INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order, gb_ai_aw_is_default)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 10, 1
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_PURCHASE' AND w.gb_ai_workflow_code = 'WF_PURCHASE_AMOUNT'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order), gb_ai_aw_is_default = VALUES(gb_ai_aw_is_default);

INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 20
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_PURCHASE' AND w.gb_ai_workflow_code = 'WF_PURCHASE_STORE_RANK'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order);

INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order, gb_ai_aw_is_default)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 10, 1
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_STOCK' AND w.gb_ai_workflow_code = 'WF_STOCK_CONSUMPTION'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order), gb_ai_aw_is_default = VALUES(gb_ai_aw_is_default);

INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 20
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_STOCK' AND w.gb_ai_workflow_code = 'WF_STOCK_ON_HAND'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order);

INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order, gb_ai_aw_is_default)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 10, 1
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_DISH_MARGIN' AND w.gb_ai_workflow_code = 'WF_DISH_MARGIN_RANK'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order), gb_ai_aw_is_default = VALUES(gb_ai_aw_is_default);

INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 20
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_DISH_MARGIN' AND w.gb_ai_workflow_code = 'WF_DISH_SALES_RANK'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order);

INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 10
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_STORE_SUPERVISOR' AND w.gb_ai_workflow_code = 'WF_REVENUE_STORE_RANK'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order);

INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 20
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_STORE_SUPERVISOR' AND w.gb_ai_workflow_code = 'WF_PURCHASE_STORE_RANK'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order);

INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 30
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_STORE_SUPERVISOR' AND w.gb_ai_workflow_code = 'WF_STOCK_ON_HAND'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order);

INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 10
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_REPORT' AND w.gb_ai_workflow_code = 'WF_REVENUE_MONTH_REVIEW'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order);

INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 20
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_REPORT' AND w.gb_ai_workflow_code = 'WF_PURCHASE_AMOUNT'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order);

INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 30
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_REPORT' AND w.gb_ai_workflow_code = 'WF_STOCK_CONSUMPTION'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order);

INSERT INTO gb_ai_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id, gb_ai_aw_relation_type, gb_ai_aw_sort_order)
SELECT a.gb_ai_advisor_id, w.gb_ai_workflow_id, 'BOUND', 40
FROM gb_ai_advisor a, gb_ai_workflow w
WHERE a.gb_ai_advisor_code = 'ADV_REPORT' AND w.gb_ai_workflow_code = 'WF_DISH_MARGIN_RANK'
ON DUPLICATE KEY UPDATE gb_ai_aw_sort_order = VALUES(gb_ai_aw_sort_order);
