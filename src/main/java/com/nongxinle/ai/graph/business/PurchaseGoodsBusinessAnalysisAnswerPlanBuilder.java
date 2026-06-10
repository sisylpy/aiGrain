package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.PurchaseGoodsBusinessAnalysisAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseGoodsBusinessJudgmentSignal;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.identity.BusinessEntityIdentityOutcomeSupport;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public final class PurchaseGoodsBusinessAnalysisAnswerPlanBuilder {

    private static final double VOLUME_OVER_RATIO = 1.3d;
    private static final double VOLUME_UNDER_RATIO = 0.7d;
    private static final double LOW_COVER_DAYS = 3.0d;
    private static final double HIGH_COVER_DAYS = 30.0d;

    private PurchaseGoodsBusinessAnalysisAnswerPlanBuilder() {}

    public static void attachIfApplicable(AiRunState state) {
        if (state == null) {
            return;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (!ToolRequestContractExecutionParamSupport.isPurchaseGoodsBusinessAnalysisContract(rq)) {
            return;
        }
        state.setPurchaseGoodsBusinessAnalysisAnswerPlan(null);

        Map<String, Object> core = extractCorePayload(state);
        if (core == null || core.isEmpty()) {
            log.warn("[PurchaseGoodsBusinessAnalysis] empty payload runId={}", state.getRunId());
            return;
        }
        if ("FAILED".equalsIgnoreCase(str(core.get("status")))) {
            attachFailed(state, core, str(core.get("failureReason")));
            return;
        }

        try {
            PurchaseGoodsBusinessAnalysisAnswerPlan plan = buildPlan(state, rq, core);
            state.setPurchaseGoodsBusinessAnalysisAnswerPlan(plan);
            log.info(
                    "[PurchaseGoodsBusinessAnalysis] runId={} goods={} status={} signals={}",
                    state.getRunId(),
                    plan.getGoodsName(),
                    plan.getStatus(),
                    plan.getJudgmentSignals() == null ? 0 : plan.getJudgmentSignals().size());
        } catch (Exception ex) {
            log.warn("[PurchaseGoodsBusinessAnalysis] attach failed runId={}", state.getRunId(), ex);
            attachFailed(state, core, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractCorePayload(AiRunState state) {
        Object raw =
                state.getToolResults() == null
                        ? null
                        : state.getToolResults().get(AiBusinessToolIds.PURCHASE_GOODS_BUSINESS_ANALYSIS);
        if (!(raw instanceof Map<?, ?> env)) {
            return null;
        }
        Object data = env.get("data");
        if (!(data instanceof Map<?, ?> dm)) {
            return null;
        }
        Object core = dm.get(PurchaseGoodsBusinessAnalysisSupport.PAYLOAD_KEY);
        if (core instanceof Map<?, ?> cm) {
            return (Map<String, Object>) cm;
        }
        return null;
    }

    private static PurchaseGoodsBusinessAnalysisAnswerPlan buildPlan(
            AiRunState state, AiResolvedQueryContext rq, Map<String, Object> core) {
        @SuppressWarnings("unchecked")
        Map<String, Object> source =
                core.get("purchaseSourceBreakdown") instanceof Map<?, ?> m
                        ? (Map<String, Object>) m
                        : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> price =
                core.get("priceSection") instanceof Map<?, ?> pm ? (Map<String, Object>) pm : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> cover =
                core.get("inventoryCover") instanceof Map<?, ?> im ? (Map<String, Object>) im : Map.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dishRows =
                cover.get("dishRows") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();

        LinkedHashMap<String, Object> purchaseVolume = new LinkedHashMap<>();
        purchaseVolume.put("totalPurchaseAmount", source.get("totalPurchaseAmount"));
        purchaseVolume.put("totalPurchaseQuantity", source.get("totalPurchaseQuantity"));
        purchaseVolume.put("totalPurchaseLineCount", source.get("totalPurchaseLineCount"));

        BigDecimal theoryQty = computeTheoreticalConsumptionQty(dishRows);
        purchaseVolume.put("theoreticalConsumptionQty", formatQty(theoryQty));
        BigDecimal purchaseQty = parseDecimal(source.get("totalPurchaseQuantity"));
        purchaseVolume.put("purchaseToTheoryRatio", computeRatio(purchaseQty, theoryQty));

        LinkedHashMap<String, Object> purchaseSourceSection = new LinkedHashMap<>(source);
        String requestedSourceFacet = resolveRequestedSourceFacet(rq);
        purchaseSourceSection.put("requestedSourceFacet", requestedSourceFacet);
        String dominant = resolveDominantSource(source, requestedSourceFacet);
        purchaseSourceSection.put("dominantPurchaseSource", dominant);

        LinkedHashMap<String, Object> inventorySection = new LinkedHashMap<>();
        inventorySection.put("currentStockQty", cover.get("currentStockQty"));
        inventorySection.put("stockUnit", cover.get("stockUnit"));
        inventorySection.put("stockAsOfDate", cover.get("stockAsOfDate"));
        inventorySection.put("firstImpactedDishName", cover.get("firstImpactedDishName"));
        inventorySection.put("firstImpactedCoverDays", cover.get("firstImpactedCoverDays"));

        LinkedHashMap<String, Object> salesMatchSection = new LinkedHashMap<>();
        salesMatchSection.put("linkedDishCount", cover.get("linkedDishCount"));
        DishIngredientCoverSalesBaseline baseline =
                DishIngredientCoverSalesBaseline.fromWireMap(cover.get("salesBaseline"));
        if (baseline != null) {
            salesMatchSection.put("salesBaselineStartDate", baseline.getStartDateIso());
            salesMatchSection.put("salesBaselineStopDate", baseline.getStopDateIso());
        }

        List<String> knownGaps = new ArrayList<>();
        knownGaps.add("near_expiry_not_in_v1");
        knownGaps.add("frequency_anomaly_not_in_v1");
        knownGaps.add("quantity_anomaly_not_in_v1");
        knownGaps.add("amount_spike_not_in_v1");
        knownGaps.add("purchase_stock_reduce_mismatch_not_in_p1");
        if ("no_linked_dish_for_goods".equals(str(cover.get("knownGap")))) {
            knownGaps.add("no_linked_dish_for_goods");
        }

        List<PurchaseGoodsBusinessJudgmentSignal> signals =
                buildJudgmentSignals(dominant, requestedSourceFacet, source, price, purchaseQty, theoryQty, cover, dishRows);

        String status =
                dishRows.isEmpty() && "no_linked_dish_for_goods".equals(str(cover.get("knownGap")))
                        ? PurchaseGoodsBusinessAnalysisAnswerPlan.STATUS_PARTIAL
                        : PurchaseGoodsBusinessAnalysisAnswerPlan.STATUS_SUCCESS;

        String displayGoodsName =
                PurchaseGoodsBusinessAnalysisDisplayNameSupport.resolveDisplayGoodsName(rq, core, cover);
        Integer displayDisGoodsId =
                PurchaseGoodsBusinessAnalysisDisplayNameSupport.resolveDisplayDisGoodsId(rq, core, cover);

        return PurchaseGoodsBusinessAnalysisAnswerPlan.builder()
                .planType(PurchaseGoodsBusinessAnalysisAnswerPlan.TYPE)
                .contractId(PurchaseGoodsBusinessAnalysisAnswerPlan.CONTRACT_ID)
                .status(status)
                .disGoodsId(displayDisGoodsId)
                .goodsName(displayGoodsName)
                .scopeLabel(resolveScopeLabel(rq))
                .purchaseTimeLabel(str(core.get("purchaseTimeLabel")))
                .inventorySnapshotLabel(str(core.get("inventorySnapshotLabel")))
                .salesBaselineLabel(str(core.get("salesBaselineLabel")))
                .dominantPurchaseSource(dominant)
                .purchaseSourceSection(purchaseSourceSection)
                .purchaseVolumeSection(purchaseVolume)
                .priceSection(new LinkedHashMap<>(price))
                .inventorySection(inventorySection)
                .salesMatchSection(salesMatchSection)
                .dishRows(dishRows)
                .judgmentSignals(signals)
                .knownGaps(knownGaps)
                .build();
    }

    private static List<PurchaseGoodsBusinessJudgmentSignal> buildJudgmentSignals(
            String dominant,
            String requestedSourceFacet,
            Map<String, Object> source,
            Map<String, Object> price,
            BigDecimal purchaseQty,
            BigDecimal theoryQty,
            Map<String, Object> cover,
            List<Map<String, Object>> dishRows) {
        List<PurchaseGoodsBusinessJudgmentSignal> signals = new ArrayList<>();

        BigDecimal totalAmt = parseDecimal(source.get("totalPurchaseAmount"));
        if (totalAmt == null || totalAmt.compareTo(BigDecimal.ZERO) <= 0) {
            signals.add(signal("NO_PURCHASE_IN_PERIOD", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_WARN, "purchaseVolumeSection"));
        } else {
            appendPurchaseSourceJudgmentSignals(signals, dominant, requestedSourceFacet, source);
        }

        String priceTrend = str(price.get("priceTrend"));
        if ("UP".equals(priceTrend)) {
            signals.add(signal("PURCHASE_PRICE_UP", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_WARN, "priceSection"));
        } else if ("DOWN".equals(priceTrend)) {
            signals.add(signal("PURCHASE_PRICE_DOWN", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_INFO, "priceSection"));
        } else if ("FLAT".equals(priceTrend)) {
            signals.add(signal("PURCHASE_PRICE_STABLE", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_INFO, "priceSection"));
        }

        Double ratio = computeRatio(purchaseQty, theoryQty);
        if (ratio != null) {
            if (ratio > VOLUME_OVER_RATIO) {
                signals.add(signal("PURCHASE_VOLUME_ABOVE_THEORY", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_WARN, "purchaseVolumeSection"));
            } else if (ratio < VOLUME_UNDER_RATIO) {
                signals.add(signal("PURCHASE_VOLUME_BELOW_THEORY", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_INFO, "purchaseVolumeSection"));
            } else {
                signals.add(signal("PURCHASE_VOLUME_ALIGNED_WITH_THEORY", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_INFO, "purchaseVolumeSection"));
            }
        }

        if (dishRows.isEmpty()) {
            signals.add(signal("NO_LINKED_DISH", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_WARN, "salesMatchSection"));
        }

        Double coverDays = parseDoubleLoose(cover.get("firstImpactedCoverDays"));
        if (coverDays != null) {
            if (coverDays < LOW_COVER_DAYS) {
                signals.add(signal("LOW_STOCK_COVER_DAYS", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_WARN, "inventorySection"));
            } else if (coverDays > HIGH_COVER_DAYS) {
                signals.add(signal("HIGH_STOCK_COVER_DAYS", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_INFO, "inventorySection"));
            }
        }

        BigDecimal stock = parseDecimal(cover.get("currentStockQty"));
        if (stock == null || stock.compareTo(BigDecimal.ZERO) <= 0) {
            signals.add(signal("ZERO_OR_MISSING_STOCK", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_WARN, "inventorySection"));
        }

        return signals;
    }

    private static PurchaseGoodsBusinessJudgmentSignal signal(
            String code, String severity, String evidenceRef) {
        return PurchaseGoodsBusinessJudgmentSignal.builder()
                .code(code)
                .severity(severity)
                .evidenceRefs(List.of(evidenceRef))
                .build();
    }

    private static void appendPurchaseSourceJudgmentSignals(
            List<PurchaseGoodsBusinessJudgmentSignal> signals,
            String dominant,
            String requestedSourceFacet,
            Map<String, Object> source) {
        if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(requestedSourceFacet)) {
            if (hasPositiveAmount(source.get("totalPurchaseAmount"))) {
                signals.add(signal("PURCHASE_SOURCE_SUPPLIER_ONLY", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_INFO, "purchaseSourceSection"));
            } else {
                signals.add(signal("NO_SUPPLIER_PURCHASE_IN_PERIOD", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_WARN, "purchaseSourceSection"));
            }
            return;
        }
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(requestedSourceFacet)) {
            if (hasPositiveAmount(source.get("totalPurchaseAmount"))) {
                signals.add(signal("PURCHASE_SOURCE_SELF_ONLY", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_INFO, "purchaseSourceSection"));
            } else {
                signals.add(signal("NO_SELF_PURCHASE_IN_PERIOD", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_WARN, "purchaseSourceSection"));
            }
            return;
        }
        switch (dominant) {
            case "MIXED" -> signals.add(signal("PURCHASE_SOURCE_MIXED", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_INFO, "purchaseSourceSection"));
            case "SELF" -> signals.add(signal("PURCHASE_SOURCE_SELF_ONLY", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_INFO, "purchaseSourceSection"));
            case "SELF_WITH_OTHER" -> signals.add(signal("PURCHASE_SOURCE_SELF_WITH_OTHER", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_INFO, "purchaseSourceSection"));
            case "SUPPLIER" -> signals.add(signal("PURCHASE_SOURCE_SUPPLIER_ONLY", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_INFO, "purchaseSourceSection"));
            case "SUPPLIER_WITH_OTHER" -> signals.add(signal("PURCHASE_SOURCE_SUPPLIER_WITH_OTHER", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_INFO, "purchaseSourceSection"));
            case "OTHER" -> signals.add(signal("PURCHASE_SOURCE_OTHER_ONLY", PurchaseGoodsBusinessJudgmentSignal.SEVERITY_INFO, "purchaseSourceSection"));
            default -> { }
        }
    }

    private static String resolveRequestedSourceFacet(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQuerySemanticParse() == null) {
            return AiQuerySemanticLexicon.SOURCE_ALL;
        }
        var slots = rq.getQuerySemanticParse().getSemanticSlots();
        if (slots != null && StringUtils.hasText(slots.getSourceFacet())) {
            return slots.getSourceFacet().trim().toUpperCase();
        }
        String focus = ToolRequestContractExecutionParamSupport.resolvePurchaseSourceFocus(rq);
        if (StringUtils.hasText(focus)) {
            return focus.trim().toUpperCase();
        }
        return AiQuerySemanticLexicon.SOURCE_ALL;
    }

    private static String resolveDominantSource(Map<String, Object> source, String requestedSourceFacet) {
        if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(requestedSourceFacet)) {
            return hasPositiveAmount(source.get("totalPurchaseAmount")) ? "SUPPLIER" : "NONE";
        }
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(requestedSourceFacet)) {
            return hasPositiveAmount(source.get("totalPurchaseAmount")) ? "SELF" : "NONE";
        }
        return resolveDominantSourceAll(source);
    }

    private static String resolveDominantSourceAll(Map<String, Object> source) {
        BigDecimal self = parseDecimal(source.get("selfPurchaseAmount"));
        BigDecimal sup = parseDecimal(source.get("supplierPurchaseAmount"));
        BigDecimal other = parseDecimal(source.get("otherPurchaseAmount"));
        boolean hasSelf = hasPositiveAmount(self);
        boolean hasSup = hasPositiveAmount(sup);
        boolean hasOther = hasPositiveAmount(other);
        if (hasSelf && hasSup) {
            return "MIXED";
        }
        if (hasSelf && hasOther) {
            return "SELF_WITH_OTHER";
        }
        if (hasSelf) {
            return "SELF";
        }
        if (hasSup && hasOther) {
            return "SUPPLIER_WITH_OTHER";
        }
        if (hasSup) {
            return "SUPPLIER";
        }
        if (hasOther) {
            return "OTHER";
        }
        return "NONE";
    }

    private static BigDecimal computeTheoreticalConsumptionQty(List<Map<String, Object>> dishRows) {
        BigDecimal total = BigDecimal.ZERO;
        boolean any = false;
        for (Map<String, Object> row : dishRows) {
            if (row == null) {
                continue;
            }
            BigDecimal recipe = parseDecimal(row.get("recipeUnitPerDish"));
            BigDecimal sales = parseDecimal(row.get("salesPortionsInBaseline"));
            if (recipe != null && sales != null && recipe.compareTo(BigDecimal.ZERO) > 0 && sales.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(recipe.multiply(sales));
                any = true;
            }
        }
        return any ? total.setScale(1, RoundingMode.HALF_UP) : null;
    }

    private static Double computeRatio(BigDecimal purchaseQty, BigDecimal theoryQty) {
        if (purchaseQty == null || theoryQty == null || theoryQty.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return purchaseQty.divide(theoryQty, 4, RoundingMode.HALF_UP).doubleValue();
    }

    private static void attachFailed(AiRunState state, Map<String, Object> core, String reason) {
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        @SuppressWarnings("unchecked")
        Map<String, Object> cover =
                core.get("inventoryCover") instanceof Map<?, ?> im
                        ? (Map<String, Object>) im
                        : Map.of();
        PurchaseGoodsBusinessAnalysisAnswerPlan plan =
                PurchaseGoodsBusinessAnalysisAnswerPlan.builder()
                        .planType(PurchaseGoodsBusinessAnalysisAnswerPlan.TYPE)
                        .contractId(PurchaseGoodsBusinessAnalysisAnswerPlan.CONTRACT_ID)
                        .status(PurchaseGoodsBusinessAnalysisAnswerPlan.STATUS_FAILED)
                        .goodsName(
                                PurchaseGoodsBusinessAnalysisDisplayNameSupport.resolveDisplayGoodsName(
                                        rq, core, cover))
                        .disGoodsId(
                                PurchaseGoodsBusinessAnalysisDisplayNameSupport.resolveDisplayDisGoodsId(
                                        rq, core, cover))
                        .debug(new LinkedHashMap<>(Map.of("failureReason", reason == null ? "unknown" : reason)))
                        .build();
        state.setPurchaseGoodsBusinessAnalysisAnswerPlan(plan);
        if ("goods_identity_not_found".equals(reason) || "goods_identity_ambiguous".equals(reason)) {
            state.setNeedClarification(true);
            String msg = BusinessEntityIdentityOutcomeSupport.goodsIdentityUserFacingFailureMessage(rq);
            if (StringUtils.hasText(msg)) {
                state.setClarificationQuestion(msg);
            }
        }
    }

    private static String resolveScopeLabel(AiResolvedQueryContext rq) {
        if (rq != null && StringUtils.hasText(rq.getQueryScopeBanner())) {
            return rq.getQueryScopeBanner().trim();
        }
        return "";
    }

    private static boolean hasPositiveAmount(Object raw) {
        BigDecimal v = parseDecimal(raw);
        return v != null && v.compareTo(BigDecimal.ZERO) > 0;
    }

    private static BigDecimal parseDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Double parseDoubleLoose(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatQty(BigDecimal v) {
        return v == null ? null : v.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
