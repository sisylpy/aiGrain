package com.nongxinle.ai.composer.warehouse;

import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import com.nongxinle.ai.inventory.WarehouseInventorySupervisionSupport;

import java.util.List;
import java.util.Map;

/**
 * 库存监督卡：Composer 仅输出短导语，分桶明细在 Card payload。
 */
public final class WarehouseInventorySupervisionCardCompanionAnswerPreviewSupport {

    private WarehouseInventorySupervisionCardCompanionAnswerPreviewSupport() {}

    public static boolean shouldUseShortPreview(WarehouseAnswerPlan plan) {
        return plan != null
                && WarehouseAnswerPlan.TYPE_WAREHOUSE_INVENTORY_SUPERVISION.equals(plan.getPlanType());
    }

    public static String composeCardCompanionHint(WarehouseAnswerPlan plan) {
        if (plan == null) {
            return "";
        }
        String scope = plan.getScopeLabel() == null ? "当前范围" : plan.getScopeLabel().trim();
        Map<String, Object> summary = plan.getSummary();
        Object narrative = summary == null ? null : summary.get("narrative");
        if (narrative != null && !narrative.toString().isBlank()) {
            return scope + "：" + narrative.toString().trim() + "；详情见下方卡片。";
        }
        int urgentToday = sectionCount(plan, WarehouseInventorySupervisionSupport.SECTION_URGENT_TODAY);
        int urgentTomorrow = sectionCount(plan, WarehouseInventorySupervisionSupport.SECTION_URGENT_TOMORROW);
        int expiry = sectionCount(plan, WarehouseInventorySupervisionSupport.SECTION_EXPIRY);
        int overstock = sectionCount(plan, WarehouseInventorySupervisionSupport.SECTION_OVERSTOCK);
        if (urgentToday == 0 && urgentTomorrow == 0 && expiry == 0 && overstock == 0) {
            return scope + "：当前库存整体平稳，暂无急需采购、临期或明显积压提醒；详情见下方卡片。";
        }
        StringBuilder sb = new StringBuilder(scope).append("：");
        if (urgentToday > 0) {
            sb.append("今天急需采购 ").append(urgentToday).append(" 项；");
        }
        if (urgentTomorrow > 0) {
            sb.append("明天急需 ").append(urgentTomorrow).append(" 项；");
        }
        if (expiry > 0) {
            sb.append("临期/过期 ").append(expiry).append(" 个批次；");
        }
        if (overstock > 0) {
            sb.append("积压/慢动销 ").append(overstock).append(" 项；");
        }
        sb.append("详情见下方卡片。");
        return sb.toString();
    }

    private static int sectionCount(WarehouseAnswerPlan plan, String sectionId) {
        List<Map<String, Object>> sections = plan.getSections();
        if (sections == null) {
            return 0;
        }
        for (Map<String, Object> section : sections) {
            if (section == null) {
                continue;
            }
            Object id = section.get("sectionId");
            if (sectionId.equals(id == null ? "" : id.toString())) {
                Object c = section.get("rowCount");
                if (c instanceof Number n) {
                    return n.intValue();
                }
                Object rows = section.get("rows");
                return rows instanceof List<?> list ? list.size() : 0;
            }
        }
        return 0;
    }
}
