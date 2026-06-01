package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrixRow;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 采购域 Step 2 小合同只读导出（P1-A）。
 * <p>ACTIVE：Matrix goods-anchor 行 + 主流程已稳定 wire / PlanBuilder / Tool 链路。
 * KNOWN_GAP：Lexicon 已登记但主链未完整或未纳入本轮提升范围 — 仅 Catalog 观测。
 * <p>薄导出器：仅 Matrix / Lexicon 登记行 → {@link MatrixBackedContractExporterSupport} 结构化字段。
 * NL 见 {@code semantic_intake.v1.md}、{@code query_semantic_parser.v2.md}、Harness。
 * 治理见 {@code docs/ai/semantic-contract-exporter-governance.md}。
 */
public final class PurchaseSemanticCapabilityContractExporter implements SemanticCapabilityContractExporter {

    public static final String DOMAIN_CODE = "PURCHASE";

    public static final PurchaseSemanticCapabilityContractExporter INSTANCE =
            new PurchaseSemanticCapabilityContractExporter();

    private static final List<String> PURCHASE_TOOLS = List.of(AiBusinessToolIds.PURCHASE_OVERVIEW);

    private PurchaseSemanticCapabilityContractExporter() {
    }

    @Override
    public String domain() {
        return DOMAIN_CODE;
    }

    public static List<SemanticCapabilityContract> exportContracts() {
        return INSTANCE.exportAllContracts();
    }

    public static SemanticCapabilityContractExportSummary exportContractSummary() {
        return INSTANCE.exportSummary();
    }

    @Override
    public List<SemanticCapabilityContract> exportActiveContracts() {
        List<SemanticCapabilityContract> out = new ArrayList<>();
        for (PurchaseSemanticCapabilityMatrixRow row : PurchaseSemanticCapabilityMatrix.goodsAnchorRows()) {
            out.add(fromMatrixRow(row));
        }
        out.addAll(
                List.of(
                        activeContract(
                                "purchase.overview_summary",
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY,
                                null,
                                PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW,
                                "PURCHASE_ORDER",
                                Set.of("SUMMARY", "OVERVIEW"),
                                Set.of("PURCHASE_AMOUNT"),
                                AiQuerySemanticLexicon.SOURCE_ALL,
                                null,
                                false),
                        activeContract(
                                "purchase.self_overview",
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY,
                                null,
                                PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW,
                                "PURCHASE_ORDER",
                                Set.of("SUMMARY", "OVERVIEW"),
                                Set.of("PURCHASE_AMOUNT"),
                                AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE,
                                null,
                                false),
                        activeContract(
                                "purchase.supplier_overview",
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY,
                                null,
                                PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW,
                                "SUPPLIER",
                                Set.of("SUMMARY", "OVERVIEW"),
                                Set.of("PURCHASE_AMOUNT"),
                                AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE,
                                null,
                                false),
                        activeContract(
                                "purchase.goods_amount_ranking",
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING,
                                null,
                                PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING,
                                "GOODS",
                                Set.of("RANKING"),
                                Set.of("PURCHASE_AMOUNT"),
                                AiQuerySemanticLexicon.SOURCE_ALL,
                                null,
                                false),
                        activeContract(
                                "purchase.goods_count_ranking",
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING,
                                null,
                                PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING,
                                "GOODS",
                                Set.of("RANKING"),
                                Set.of("PURCHASE_COUNT"),
                                AiQuerySemanticLexicon.SOURCE_ALL,
                                null,
                                false),
                        activeContract(
                                "purchase.period_goods_list",
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PERIOD_GOODS_LIST,
                                null,
                                PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL,
                                "GOODS",
                                Set.of("DETAIL", "LIST"),
                                Set.of("PURCHASE_AMOUNT", "PURCHASE_QUANTITY"),
                                AiQuerySemanticLexicon.SOURCE_ALL,
                                null,
                                false),
                        activeContract(
                                "purchase.period_goods_list.self",
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PERIOD_GOODS_LIST,
                                null,
                                PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL,
                                "GOODS",
                                Set.of("DETAIL", "LIST"),
                                Set.of("PURCHASE_AMOUNT", "PURCHASE_QUANTITY"),
                                AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE,
                                null,
                                false),
                        activeContract(
                                "purchase.period_goods_list.supplier",
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PERIOD_GOODS_LIST,
                                null,
                                PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL,
                                "GOODS",
                                Set.of("DETAIL", "LIST"),
                                Set.of("PURCHASE_AMOUNT", "PURCHASE_QUANTITY"),
                                AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE,
                                null,
                                false),
                        activeContract(
                                "purchase.supplier_amount_ranking",
                                AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING,
                                null,
                                PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING,
                                "SUPPLIER",
                                Set.of("RANKING"),
                                Set.of("PURCHASE_AMOUNT"),
                                AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE,
                                null,
                                false),
                        activeContract(
                                "purchase.store_amount_ranking",
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING,
                                null,
                                PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING,
                                "STORE",
                                Set.of("RANKING"),
                                Set.of("PURCHASE_AMOUNT"),
                                AiQuerySemanticLexicon.SOURCE_ALL,
                                null,
                                false),
                        activeContract(
                                "purchase.anomaly.price",
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PRICE_ANOMALY,
                                null,
                                PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW,
                                "GOODS",
                                Set.of("ANOMALY", "DETAIL"),
                                Set.of("UNIT_PRICE", "PURCHASE_AMOUNT"),
                                AiQuerySemanticLexicon.SOURCE_ALL,
                                null,
                                false),
                        activeContract(
                                "purchase.anomaly.frequency",
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_FREQUENCY_ANOMALY,
                                null,
                                PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW,
                                "GOODS",
                                Set.of("ANOMALY", "DETAIL"),
                                Set.of("PURCHASE_COUNT"),
                                AiQuerySemanticLexicon.SOURCE_ALL,
                                null,
                                false),
                        activeContract(
                                "purchase.anomaly.quantity",
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_QUANTITY_ANOMALY,
                                null,
                                PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW,
                                "GOODS",
                                Set.of("ANOMALY", "DETAIL"),
                                Set.of("PURCHASE_QUANTITY"),
                                AiQuerySemanticLexicon.SOURCE_ALL,
                                null,
                                false),
                        activeContract(
                                "purchase.anomaly.amount_spike",
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE,
                                null,
                                PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW,
                                "GOODS",
                                Set.of("ANOMALY", "TREND"),
                                Set.of("PURCHASE_AMOUNT"),
                                AiQuerySemanticLexicon.SOURCE_ALL,
                                null,
                                false)));
        return List.copyOf(out);
    }

    @Override
    public List<SemanticCapabilityContract> exportPlannedContracts() {
        return List.of();
    }

    @Override
    public List<SemanticCapabilityContract> exportKnownGapContracts() {
        return List.of(
                gapContract(
                        "purchase.store_compare",
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_COMPARE,
                        null,
                        PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW,
                        "STORE",
                        Set.of("COMPARE", "OVERVIEW"),
                        Set.of("PURCHASE_AMOUNT"),
                        AiQuerySemanticLexicon.SOURCE_ALL,
                        null,
                        false,
                        "purchase_store_compare_not_in_p1"),
                gapContract(
                        "purchase.store_pair_amount_compare",
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_PAIR_AMOUNT_COMPARE,
                        null,
                        PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW,
                        "STORE",
                        Set.of("COMPARE", "RANKING"),
                        Set.of("PURCHASE_AMOUNT"),
                        AiQuerySemanticLexicon.SOURCE_ALL,
                        null,
                        false,
                        "purchase_store_pair_amount_compare_not_in_p1"),
                gapContract(
                        "purchase.risk.stock_reduce_mismatch",
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH,
                        null,
                        PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW,
                        "GOODS",
                        Set.of("ANOMALY", "COMPARE"),
                        Set.of("PURCHASE_AMOUNT", "PURCHASE_QUANTITY"),
                        AiQuerySemanticLexicon.SOURCE_ALL,
                        null,
                        false,
                        "purchase_stock_reduce_mismatch_missing_contract"));
    }

    @Override
    public SemanticCapabilityContractExportSummary exportSummary() {
        List<SemanticCapabilityContract> active = exportActiveContracts();
        List<SemanticCapabilityContract> planned = exportPlannedContracts();
        List<SemanticCapabilityContract> gaps = exportKnownGapContracts();
        List<String> gapMarkers = new ArrayList<>();
        for (SemanticCapabilityContract c : gaps) {
            if (c.getGapMarker() != null) {
                gapMarkers.add(c.getGapMarker());
            }
        }
        gapMarkers.add("goods_anchor_supplier_breakdown_missing_contract");
        int total = active.size() + planned.size() + gaps.size();
        return SemanticCapabilityContractExportSummary.builder()
                .domainCode(DOMAIN_CODE)
                .exportedContractCount(total)
                .activeContractCount(active.size())
                .plannedContractCount(planned.size())
                .knownGapContractCount(gaps.size())
                .exportedPurchaseContractCount(total)
                .activePurchaseContractCount(active.size())
                .plannedPurchaseContractCount(planned.size())
                .knownGapPurchaseContractCount(gaps.size())
                .knownGapMarkers(gapMarkers)
                .build();
    }

    private static SemanticCapabilityContract fromMatrixRow(PurchaseSemanticCapabilityMatrixRow row) {
        MatrixBackedContractExporterSupport.MatrixContractExportSpec.MatrixContractExportSpecBuilder b =
                MatrixBackedContractExporterSupport.MatrixContractExportSpec.builder()
                        .contractId(row.getCapabilityId())
                        .domain(DOMAIN_CODE)
                        .intentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW)
                        .pathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW)
                        .wire(row.getRequiredStructuredIntentDetailWire())
                        .sourceFacet(row.getRequiredSourceFacet())
                        .detailWanted(row.getRequiredDetailWanted())
                        .answerPlanType(row.getTargetPurchasePlanType())
                        .requiresAnchor(true)
                        .anchorType(row.getAnchorType())
                        .selectedTools(PURCHASE_TOOLS)
                        .status(SemanticCapabilityContractStatus.ACTIVE);
        if (row.getAllowedQueryObjects() != null) {
            b.queryObjects(new LinkedHashSet<>(row.getAllowedQueryObjects()));
        }
        if (row.getAllowedOperations() != null) {
            b.operations(new LinkedHashSet<>(row.getAllowedOperations()));
        }
        if (row.getAllowedMetricContains() != null) {
            b.metrics(new LinkedHashSet<>(row.getAllowedMetricContains()));
        }
        return MatrixBackedContractExporterSupport.build(b.build());
    }

    private static SemanticCapabilityContract activeContract(
            String contractId,
            String wire,
            String detailWanted,
            String answerPlanType,
            String queryObject,
            Set<String> operations,
            Set<String> metrics,
            String sourceFacet,
            String anchorType,
            boolean requiresAnchor) {
        return catalogContract(
                contractId,
                wire,
                detailWanted,
                answerPlanType,
                queryObject,
                operations,
                metrics,
                sourceFacet,
                anchorType,
                requiresAnchor,
                SemanticCapabilityContractStatus.ACTIVE,
                null);
    }

    private static SemanticCapabilityContract gapContract(
            String contractId,
            String wire,
            String detailWanted,
            String answerPlanType,
            String queryObject,
            Set<String> operations,
            Set<String> metrics,
            String sourceFacet,
            String anchorType,
            boolean requiresAnchor,
            String gapMarker) {
        return catalogContract(
                contractId,
                wire,
                detailWanted,
                answerPlanType,
                queryObject,
                operations,
                metrics,
                sourceFacet,
                anchorType,
                requiresAnchor,
                SemanticCapabilityContractStatus.KNOWN_GAP,
                gapMarker);
    }

    private static SemanticCapabilityContract catalogContract(
            String contractId,
            String wire,
            String detailWanted,
            String answerPlanType,
            String queryObject,
            Set<String> operations,
            Set<String> metrics,
            String sourceFacet,
            String anchorType,
            boolean requiresAnchor,
            SemanticCapabilityContractStatus status,
            String gapMarker) {
        return MatrixBackedContractExporterSupport.build(
                MatrixBackedContractExporterSupport.MatrixContractExportSpec.builder()
                        .contractId(contractId)
                        .domain(DOMAIN_CODE)
                        .intentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW)
                        .pathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW)
                        .wire(wire)
                        .queryObject(queryObject)
                        .operations(operations)
                        .metrics(metrics)
                        .sourceFacet(sourceFacet)
                        .detailWanted(detailWanted)
                        .answerPlanType(answerPlanType)
                        .requiresAnchor(requiresAnchor)
                        .anchorType(anchorType)
                        .selectedTools(PURCHASE_TOOLS)
                        .status(status)
                        .gapMarker(gapMarker)
                        .build());
    }
}
