package com.nongxinle.ai.composer.purchase;

import com.nongxinle.ai.dto.business.PurchaseGoodsBusinessAnalysisAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseGoodsBusinessJudgmentSignal;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** Composer 短预览：只读 AnswerPlan，不写经营结论长文。 */
public final class PurchaseGoodsBusinessAnalysisCardCompanionAnswerPreviewSupport {

    private PurchaseGoodsBusinessAnalysisCardCompanionAnswerPreviewSupport() {}

    public static boolean shouldUseShortPreview(PurchaseGoodsBusinessAnalysisAnswerPlan plan) {
        return plan != null
                && PurchaseGoodsBusinessAnalysisAnswerPlan.TYPE.equals(plan.getPlanType())
                && !PurchaseGoodsBusinessAnalysisAnswerPlan.STATUS_FAILED.equals(plan.getStatus());
    }

    public static String composeCardCompanionHint(PurchaseGoodsBusinessAnalysisAnswerPlan plan) {
        if (plan == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(plan.getGoodsName())) {
            sb.append("原料：").append(plan.getGoodsName().trim()).append("\n");
        }
        if (StringUtils.hasText(plan.getPurchaseTimeLabel())) {
            sb.append("采购周期：").append(plan.getPurchaseTimeLabel().trim()).append("\n");
        }
        if (StringUtils.hasText(plan.getInventorySnapshotLabel())) {
            sb.append("库存快照：").append(plan.getInventorySnapshotLabel().trim()).append("\n");
        }
        if (StringUtils.hasText(plan.getSalesBaselineLabel())) {
            sb.append("销量基线：").append(plan.getSalesBaselineLabel().trim()).append("\n");
        }
        Map<String, Object> vol = plan.getPurchaseVolumeSection();
        if (vol != null && !vol.isEmpty()) {
            sb.append("周期采购量：")
                    .append(nullToDash(vol.get("totalPurchaseQuantity")))
                    .append("，次数：")
                    .append(nullToDash(vol.get("totalPurchaseLineCount")))
                    .append("\n");
        }
        Map<String, Object> inv = plan.getInventorySection();
        if (inv != null) {
            sb.append("当前库存：")
                    .append(nullToDash(inv.get("currentStockQty")))
                    .append(StringUtils.hasText(str(inv.get("stockUnit"))) ? str(inv.get("stockUnit")) : "")
                    .append("，最短支撑：")
                    .append(nullToDash(inv.get("firstImpactedCoverDays")))
                    .append(" 天\n");
        }
        List<PurchaseGoodsBusinessJudgmentSignal> signals = plan.getJudgmentSignals();
        if (signals != null && !signals.isEmpty()) {
            sb.append("信号：");
            for (int i = 0; i < signals.size(); i++) {
                if (i > 0) {
                    sb.append("；");
                }
                PurchaseGoodsBusinessJudgmentSignal s = signals.get(i);
                sb.append(s.getCode() == null ? "?" : s.getCode());
            }
        }
        return sb.toString().trim();
    }

    private static String nullToDash(Object o) {
        return o == null || !StringUtils.hasText(o.toString()) ? "—" : o.toString().trim();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
