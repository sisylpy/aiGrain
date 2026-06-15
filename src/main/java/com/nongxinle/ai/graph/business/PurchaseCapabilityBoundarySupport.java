package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.CapabilitySpecificitySupport;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import com.nongxinle.ai.semantic.contract.SemanticContractCatalog;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrame;
import com.nongxinle.ai.semantic.frame.SemanticFrameValidationResult;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 采购公共能力边界：指标 / 门店 Scope / 异常族 / KNOWN_GAP 合同的结构化门禁（不读 rawMessage）。
 */
public final class PurchaseCapabilityBoundarySupport {

    private static final String Q_STORE_SCOPE =
            "当前权限范围内可见门店不足以做跨店排行或对比，请确认门店范围或改用单店采购概览。";
    private static final String Q_STORE_COMPARE_GAP =
            "多门店采购对比能力尚未开放，请改为单店查询或门店采购金额排行（需至少两家可见门店）。";
    private static final String Q_ANOMALY_GENERIC =
            "采购异常需明确类型（单价波动、采购次数异常、采购数量异常或金额突增），请补充具体异常口径。";
    private static final String Q_METRIC_CONTRACT =
            "当前采购指标与所选能力合同不一致：采购数量(PURCHASE_QUANTITY)、采购次数(PURCHASE_COUNT)、采购金额(PURCHASE_AMOUNT) 不可混用。";

    private static final Set<String> STORE_RANKING_WIRES =
            Set.of(
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING,
                    AiQuerySemanticLexicon.STRUCTURED_STORE_PRIORITY_RANKING);

    private static final Set<String> STORE_COMPARE_GAP_WIRES =
            Set.of(
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_COMPARE,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_PAIR_AMOUNT_COMPARE);

    private PurchaseCapabilityBoundarySupport() {}

    /**
     * 采购 frame 能力边界（在 contract gate 之后调用）。
     *
     * @return null 表示通过
     */
    public static SemanticFrameValidationResult validateCapabilityBoundary(
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult rawParse,
            DomainContractSelectionResult contractSelection,
            AiResolvedOrgScope orgScope) {
        if (frame == null) {
            return null;
        }
        SemanticFrameValidationResult gapContract = validateKnownGapContractSelection(rawParse);
        if (gapContract != null) {
            return gapContract;
        }
        SemanticFrameValidationResult metric = validateMetricContractAlignment(frame, rawParse);
        if (metric != null) {
            return metric;
        }
        SemanticFrameValidationResult specificity = validateCapabilitySpecificity(frame, rawParse);
        if (specificity != null) {
            return specificity;
        }
        SemanticFrameValidationResult anomaly = validateAnomalyCapability(frame);
        if (anomaly != null) {
            return anomaly;
        }
        SemanticFrameValidationResult store = validateStoreCapability(frame, rawParse, orgScope);
        if (store != null) {
            return store;
        }
        return null;
    }

    public static boolean isStoreRankingPlanType(String planType) {
        return PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING.equals(planType);
    }

    public static int countEligibleCrossStoreRankingStores(AiResolvedOrgScope orgScope) {
        if (orgScope == null || orgScope.getVisibleStores() == null) {
            return 0;
        }
        int n = 0;
        for (AiStoreScopeDTO row : orgScope.getVisibleStores()) {
            if (row != null && row.getStoreDepartmentId() != null && row.getStoreDepartmentId() > 0) {
                n++;
            }
        }
        return n;
    }

    private static SemanticFrameValidationResult validateKnownGapContractSelection(
            AiQuerySemanticParseResult rawParse) {
        if (rawParse == null || !SemanticContractCompletionEngine.isContractLockedParse(rawParse)) {
            return null;
        }
        String contractId = SemanticContractCompletionEngine.extractSelectedContractId(rawParse);
        if (!StringUtils.hasText(contractId)) {
            return null;
        }
        SemanticCapabilityContract contract = findKnownGapPurchaseContract(contractId.trim());
        if (contract != null) {
            if (isStoreCompareGapContract(contract)) {
                return SemanticFrameValidationResult.clarify(
                        Q_STORE_COMPARE_GAP, List.of("PURCHASE_KNOWN_GAP_CONTRACT:" + contractId.trim()));
            }
            return SemanticFrameValidationResult.clarify(
                    Q_STORE_COMPARE_GAP,
                    List.of("PURCHASE_KNOWN_GAP_CONTRACT:" + contractId.trim()));
        }
        return null;
    }

    private static boolean isStoreCompareGapContract(SemanticCapabilityContract contract) {
        if (contract == null || contract.getWire() == null) {
            return false;
        }
        return STORE_COMPARE_GAP_WIRES.contains(contract.getWire().trim());
    }

    private static SemanticFrameValidationResult validateMetricContractAlignment(
            CurrentSemanticFrame frame, AiQuerySemanticParseResult rawParse) {
        if (frame == null || rawParse == null) {
            return null;
        }
        String metric = normalizeToken(frame.getMetric());
        if (metric == null) {
            return null;
        }
        String contractId = SemanticContractCompletionEngine.extractSelectedContractId(rawParse);
        if (!StringUtils.hasText(contractId)) {
            return validateMetricVsWire(frame, metric);
        }
        String id = contractId.trim();
        if ("purchase.goods_count_ranking".equals(id) && metric.contains("PURCHASE_QUANTITY")) {
            return SemanticFrameValidationResult.clarify(
                    Q_METRIC_CONTRACT, List.of("PURCHASE_COUNT_CONTRACT_WITH_QUANTITY_METRIC"));
        }
        if ("purchase.goods_quantity_ranking".equals(id) && metric.contains("PURCHASE_COUNT")) {
            return SemanticFrameValidationResult.clarify(
                    Q_METRIC_CONTRACT, List.of("PURCHASE_QUANTITY_CONTRACT_WITH_COUNT_METRIC"));
        }
        if ("purchase.goods_amount_ranking".equals(id)
                && (metric.contains("PURCHASE_COUNT") || metric.contains("PURCHASE_QUANTITY"))) {
            return SemanticFrameValidationResult.clarify(
                    Q_METRIC_CONTRACT, List.of("PURCHASE_AMOUNT_CONTRACT_WITH_NON_AMOUNT_METRIC"));
        }
        return validateMetricVsWire(frame, metric);
    }

    private static SemanticFrameValidationResult validateMetricVsWire(
            CurrentSemanticFrame frame, String metric) {
        String wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                frame.getStructuredIntentDetailWire());
        if (wire == null) {
            return null;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING.equals(wire)
                && metric.contains("PURCHASE_QUANTITY")) {
            return SemanticFrameValidationResult.clarify(
                    Q_METRIC_CONTRACT, List.of("GOODS_COUNT_RANKING_WIRE_WITH_QUANTITY_METRIC"));
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_QUANTITY_RANKING.equals(wire)
                && metric.contains("PURCHASE_COUNT")) {
            return SemanticFrameValidationResult.clarify(
                    Q_METRIC_CONTRACT, List.of("GOODS_QUANTITY_RANKING_WIRE_WITH_COUNT_METRIC"));
        }
        return null;
    }

    /**
     * {@code capabilitySpecificity} 与采购异常合同一致性（只读 V2 结构化字段）。
     * 细分合同 / 异常 detection wire / 异常专属 metric 已对齐时视为 structurally explicit，不要求
     * {@code capabilitySpecificity=EXPLICIT} 字面量。
     */
    private static SemanticFrameValidationResult validateCapabilitySpecificity(
            CurrentSemanticFrame frame, AiQuerySemanticParseResult rawParse) {
        if (rawParse == null) {
            return null;
        }
        String specificity = CapabilitySpecificitySupport.extract(rawParse);
        String contractId =
                rawParse.getSemanticSlots() != null
                        ? rawParse.getSemanticSlots().getSelectedContractId()
                        : null;
        boolean anomalyContract =
                CapabilitySpecificitySupport.isPurchaseAnomalyContractId(contractId);
        String wire =
                frame != null
                        ? frame.getStructuredIntentDetailWire()
                        : rawParse.getSemanticSlots() != null
                                ? rawParse.getSemanticSlots().getStructuredIntentDetailWire()
                                : null;
        String metric =
                frame != null
                        ? frame.getMetric()
                        : rawParse.getSemanticSlots() != null
                                ? rawParse.getSemanticSlots().getMetric()
                                : null;
        boolean structurallyExplicit =
                CapabilitySpecificitySupport.isPurchaseAnomalyStructurallyExplicit(
                        contractId, wire, metric);

        if (CapabilitySpecificitySupport.UNSPECIFIED.equals(specificity)) {
            if (anomalyContract && !structurallyExplicit) {
                return SemanticFrameValidationResult.clarify(
                        Q_ANOMALY_GENERIC,
                        List.of("PURCHASE_ANOMALY_UNSPECIFIED_WITH_SPECIFIC_CONTRACT"));
            }
            return null;
        }

        if (anomalyContract
                && !CapabilitySpecificitySupport.EXPLICIT.equals(specificity)
                && !structurallyExplicit) {
            return SemanticFrameValidationResult.clarify(
                    Q_ANOMALY_GENERIC,
                    List.of("PURCHASE_ANOMALY_CONTRACT_WITHOUT_EXPLICIT_SPECIFICITY"));
        }

        return null;
    }

    /**
     * 泛化异常门禁：仅拦截语义层显式输出的未指定异常 wire（{@code purchase_goods_anomaly}）。
     * 合法 {@code purchase.anomaly.*} 合同须 {@code capabilitySpecificity=EXPLICIT} 或结构化 explicit
     * （见 {@link #validateCapabilitySpecificity}）。
     */
    private static SemanticFrameValidationResult validateAnomalyCapability(CurrentSemanticFrame frame) {
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        frame.getStructuredIntentDetailWire());
        if (wire == null) {
            return null;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_ANOMALY.equals(wire)) {
            return SemanticFrameValidationResult.clarify(
                    Q_ANOMALY_GENERIC, List.of("PURCHASE_GENERIC_ANOMALY_WIRE_UNSUPPORTED"));
        }
        return null;
    }

    private static SemanticFrameValidationResult validateStoreCapability(
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult rawParse,
            AiResolvedOrgScope orgScope) {
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        frame.getStructuredIntentDetailWire());
        String op = normalizeToken(frame.getOperation());
        String qo = normalizeToken(frame.getQueryObject());

        if (wire != null && STORE_COMPARE_GAP_WIRES.contains(wire)) {
            return SemanticFrameValidationResult.clarify(
                    Q_STORE_COMPARE_GAP, List.of("PURCHASE_STORE_COMPARE_GAP_WIRE"));
        }

        if ("STORE".equals(qo) && "COMPARE".equals(op)) {
            if (wire == null || !STORE_RANKING_WIRES.contains(wire)) {
                return SemanticFrameValidationResult.clarify(
                        Q_STORE_COMPARE_GAP, List.of("PURCHASE_STORE_COMPARE_OPERATION_UNSUPPORTED"));
            }
            return SemanticFrameValidationResult.clarify(
                    Q_STORE_COMPARE_GAP, List.of("PURCHASE_STORE_COMPARE_MISROUTED_TO_RANKING"));
        }

        boolean storeRankingIntent =
                (wire != null && STORE_RANKING_WIRES.contains(wire))
                        || ("STORE".equals(qo) && "RANKING".equals(op));
        if (!storeRankingIntent) {
            return null;
        }

        int eligible = countEligibleCrossStoreRankingStores(orgScope);
        if (eligible < 2) {
            return SemanticFrameValidationResult.clarify(
                    Q_STORE_SCOPE, List.of("PURCHASE_STORE_RANKING_INSUFFICIENT_VISIBLE_STORES"));
        }
        return null;
    }

    private static SemanticCapabilityContract findKnownGapPurchaseContract(String contractId) {
        if (!StringUtils.hasText(contractId)) {
            return null;
        }
        for (SemanticCapabilityContract c : SemanticContractCatalog.listKnownGaps("PURCHASE")) {
            if (c != null && contractId.equals(c.getContractId())) {
                return c;
            }
        }
        return null;
    }

    private static String normalizeToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (AiQuerySemanticSlotMerge.UNKNOWN.equalsIgnoreCase(t)) {
            return AiQuerySemanticSlotMerge.UNKNOWN;
        }
        return t.isEmpty() ? null : t;
    }
}
