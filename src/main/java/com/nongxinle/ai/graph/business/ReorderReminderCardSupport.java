package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.service.GbDepartmentReorderReminderService;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Card 层调用 {@link GbDepartmentReorderReminderService}，不走 LLM / goods-add。 */
final class ReorderReminderCardSupport {

    private static final String SOURCE = "gbDepartmentReorderReminderService";
    private static final int PAGE = 1;
    private static final int LIMIT = 200;
    private static final int WINDOW_DAYS = 56;
    private static final int MIN_TIMES = 2;
    private static final String HABIT_NOT_PERIOD_AUDIT_WARNING =
            "订货提醒按当前状态生成，不代表该周期历史订货审计。";

    private ReorderReminderCardSupport() {}

    static Map<String, Object> build(
            AiRunState state,
            BusinessStatusCardBuildRequest req,
            GbDepartmentReorderReminderService reorderReminderService) {
        Map<String, Object> payload = new LinkedHashMap<>();
        BusinessStatusCardShellSupport.putRangeFields(payload, req);

        Integer depId = resolveOrderingDepId(state);
        if (depId == null) {
            payload.put("status", BusinessStatusCardShellSupport.STATUS_SKIPPED);
            payload.put("emptyReason", "当前范围无法唯一确定订货部门，暂无法生成订货提醒");
            payload.put("shouldOrderItems", List.of());
            payload.put("notOrderedItems", List.of());
            payload.put("checkSummary", "暂无订货提醒。");
            payload.put("warnings", List.of());
            return shell(req, payload);
        }

        if (reorderReminderService == null) {
            payload.put("status", BusinessStatusCardShellSupport.STATUS_EMPTY);
            payload.put("emptyReason", "订货提醒服务不可用");
            payload.put("shouldOrderItems", List.of());
            payload.put("notOrderedItems", List.of());
            payload.put("checkSummary", "暂无订货提醒。");
            return shell(req, payload);
        }

        Map<String, Object> root =
                reorderReminderService.depReorderReminderPage(
                        depId, PAGE, LIMIT, WINDOW_DAYS, MIN_TIMES);
        List<Map<String, Object>> items = extractReminderItems(root);

        List<String> warnings = new ArrayList<>();
        if (shouldShowHabitWarning(req)) {
            warnings.add(HABIT_NOT_PERIOD_AUDIT_WARNING);
        }

        payload.put("status", items.isEmpty() ? BusinessStatusCardShellSupport.STATUS_EMPTY : BusinessStatusCardShellSupport.STATUS_OK);
        payload.put("orderingDepId", depId);
        payload.put("shouldOrderItems", items);
        payload.put("notOrderedItems", items);
        payload.put("normalOrderedItems", List.of());
        payload.put("warnings", warnings);
        payload.put("checkSummary", buildCheckSummary(items));
        if (items.isEmpty()) {
            payload.put("emptyReason", "当前没有需要提醒订货的商品");
        }

        return shell(req, payload);
    }

    private static Map<String, Object> shell(BusinessStatusCardBuildRequest req, Map<String, Object> payload) {
        return BusinessStatusCardShellSupport.buildCard(
                BusinessStatusCardTypes.REORDER_REMINDER_CARD,
                BusinessStatusCardShellSupport.titled(req.getReportLabel(), "·订货"),
                "按订货习惯提示应关注补货",
                BusinessStatusCardShellSupport.CHART_TABLE,
                payload,
                SOURCE);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractReminderItems(Map<String, Object> root) {
        if (root == null || root.isEmpty()) {
            return List.of();
        }
        Object pageObj = root.get("page");
        if (!(pageObj instanceof Map<?, ?> pageRaw)) {
            return List.of();
        }
        Object listObj = pageRaw.get("list");
        if (!(listObj instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof GbDepartmentDisGoodsEntity entity)) {
                continue;
            }
            Map<String, Object> item = mapEntity(entity);
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }

    private static Map<String, Object> mapEntity(GbDepartmentDisGoodsEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("depDisGoodsId", entity.getGbDepartmentDisGoodsId());
        item.put("goodsName", firstNonBlank(entity.getGbDdgDepGoodsName(), entity.getGbDdgShowStandardName()));
        item.put("suggestQty", firstNonBlank(entity.getAiOrderQuantity(), entity.getGbDdgOrderQuantity()));
        item.put("recommendGapQty", entity.getAiRecommendGapWeightProductionOnly());
        item.put("unit", entity.getGbDdgShowStandardName());
        item.put("habitIntervalDays", entity.getAiHabitIntervalDays());
        item.put("nextHabitOrderDate", entity.getAiNextHabitOrderDate());
        item.put("remindReason", entity.getAiRemindReason());
        item.put("lastOrderDate", entity.getGbDdgOrderDate());
        item.put("orderStatusLabel", "未订");
        item.put("shouldRemindToday", entity.getAiShouldRemindToday());
        return item;
    }

    static Integer resolveOrderingDepId(AiRunState state) {
        if (state == null) {
            return null;
        }
        if (state.getDepartmentId() != null && state.getDepartmentId() > 0) {
            return state.getDepartmentId().intValue();
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null || rq.getOrgScope() == null) {
            return null;
        }
        AiResolvedOrgScope org = rq.getOrgScope();
        List<AiStoreScopeDTO> stores = org.getVisibleStores();
        if (stores == null || stores.size() != 1) {
            return null;
        }
        AiStoreScopeDTO only = stores.get(0);
        if (only == null || only.getStoreDepartmentId() == null || only.getStoreDepartmentId() <= 0) {
            return null;
        }
        return only.getStoreDepartmentId().intValue();
    }

    private static boolean shouldShowHabitWarning(BusinessStatusCardBuildRequest req) {
        if (req == null) {
            return false;
        }
        if (isMultiDayRange(req)) {
            return true;
        }
        if (!StringUtils.hasText(req.getTimeLabel())) {
            return false;
        }
        return !AiResolvedTimeWindow.TODAY.equals(
                AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(req.getTimeLabel()));
    }

    private static boolean isMultiDayRange(BusinessStatusCardBuildRequest req) {
        if (req == null || !StringUtils.hasText(req.getStartDate()) || !StringUtils.hasText(req.getEndDate())) {
            return false;
        }
        return !req.getStartDate().trim().equals(req.getEndDate().trim());
    }

    private static String buildCheckSummary(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return "当前没有需要提醒订货的商品。";
        }
        return "当前应关注订货 " + items.size() + " 项，详见下方列表。";
    }

    private static String firstNonBlank(Object... values) {
        for (Object v : values) {
            if (v == null) {
                continue;
            }
            String s = v.toString().trim();
            if (StringUtils.hasText(s)) {
                return s;
            }
        }
        return null;
    }
}
