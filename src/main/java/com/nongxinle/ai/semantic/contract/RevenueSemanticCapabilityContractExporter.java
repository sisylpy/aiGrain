package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.semantic.matrix.RevenueSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.RevenueSemanticCapabilityMatrixRow;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 营业额域 Step 2 小合同只读导出。
 * <p>ACTIVE：Matrix {@link RevenueSemanticCapabilityMatrix#firstTurnRows()} 无 knownGap 行。
 * KNOWN_GAP / PLANNED：Matrix 缺口行 + Lexicon 已登记但未纳入 Matrix 首轮稳定 wire。
 */
public final class RevenueSemanticCapabilityContractExporter implements SemanticCapabilityContractExporter {

    public static final String DOMAIN_CODE = "REVENUE";

    public static final RevenueSemanticCapabilityContractExporter INSTANCE =
            new RevenueSemanticCapabilityContractExporter();

    private static final List<String> REVENUE_TOOLS = List.of(AiBusinessToolIds.REVENUE_QUERY);

    private RevenueSemanticCapabilityContractExporter() {
    }

    @Override
    public String domain() {
        return DOMAIN_CODE;
    }

    @Override
    public List<SemanticCapabilityContract> exportActiveContracts() {
        List<SemanticCapabilityContract> out = new ArrayList<>();
        for (RevenueSemanticCapabilityMatrixRow row : RevenueSemanticCapabilityMatrix.firstTurnRows()) {
            if (row.getKnownGapCode() == null) {
                out.add(fromMatrixRow(row, SemanticCapabilityContractStatus.ACTIVE, null));
            }
        }
        return List.copyOf(out);
    }

    @Override
    public List<SemanticCapabilityContract> exportPlannedContracts() {
        return List.of(
                plannedContract(
                        "revenue.dine_in_overview",
                        AiQuerySemanticLexicon.STRUCTURED_REVENUE_DINE_IN_OVERVIEW,
                        DailyRevenueAnswerPlan.TYPE_REVENUE_DINE_IN_OVERVIEW,
                        "ALL",
                        Set.of("SUMMARY"),
                        Set.of("REVENUE_AMOUNT"),
                        "dine_in_overview_not_in_matrix"),
                plannedContract(
                        "revenue.takeout_overview",
                        AiQuerySemanticLexicon.STRUCTURED_REVENUE_TAKEOUT_OVERVIEW,
                        DailyRevenueAnswerPlan.TYPE_REVENUE_TAKEOUT_OVERVIEW,
                        "ALL",
                        Set.of("SUMMARY"),
                        Set.of("REVENUE_AMOUNT"),
                        "takeout_overview_not_in_matrix"),
                plannedContract(
                        "revenue.platform_ranking",
                        AiQuerySemanticLexicon.STRUCTURED_REVENUE_PLATFORM_RANKING,
                        DailyRevenueAnswerPlan.TYPE_REVENUE_PLATFORM_RANKING,
                        "PLATFORM",
                        Set.of("RANKING"),
                        Set.of("REVENUE_AMOUNT"),
                        "platform_ranking_not_in_matrix"),
                plannedContract(
                        "revenue.order_count_overview",
                        AiQuerySemanticLexicon.STRUCTURED_REVENUE_ORDER_COUNT_OVERVIEW,
                        DailyRevenueAnswerPlan.TYPE_REVENUE_ORDER_COUNT_OVERVIEW,
                        "ALL",
                        Set.of("SUMMARY"),
                        Set.of("ORDER_COUNT"),
                        "order_count_overview_not_in_matrix"),
                plannedContract(
                        "revenue.customer_count_overview",
                        AiQuerySemanticLexicon.STRUCTURED_REVENUE_CUSTOMER_COUNT_OVERVIEW,
                        DailyRevenueAnswerPlan.TYPE_REVENUE_CUSTOMER_COUNT_OVERVIEW,
                        "ALL",
                        Set.of("SUMMARY"),
                        Set.of("CUSTOMER_COUNT"),
                        "customer_count_overview_not_in_matrix"),
                plannedContract(
                        "revenue.average_order_value",
                        AiQuerySemanticLexicon.STRUCTURED_REVENUE_AVERAGE_ORDER_VALUE,
                        DailyRevenueAnswerPlan.TYPE_REVENUE_AVERAGE_ORDER_VALUE,
                        "ALL",
                        Set.of("SUMMARY"),
                        Set.of("AVERAGE_ORDER_VALUE"),
                        "average_order_value_not_in_matrix"),
                plannedContract(
                        "revenue.channel_breakdown",
                        AiQuerySemanticLexicon.STRUCTURED_REVENUE_CHANNEL_BREAKDOWN,
                        DailyRevenueAnswerPlan.TYPE_REVENUE_CHANNEL_BREAKDOWN,
                        "ALL",
                        Set.of("BREAKDOWN"),
                        Set.of("REVENUE_AMOUNT"),
                        "channel_breakdown_not_in_matrix"));
    }

    @Override
    public List<SemanticCapabilityContract> exportKnownGapContracts() {
        List<SemanticCapabilityContract> out = new ArrayList<>();
        for (RevenueSemanticCapabilityMatrixRow row : RevenueSemanticCapabilityMatrix.firstTurnRows()) {
            if (row.getKnownGapCode() != null) {
                out.add(fromMatrixRow(row, SemanticCapabilityContractStatus.KNOWN_GAP, row.getKnownGapCode()));
            }
        }
        return List.copyOf(out);
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
        return SemanticCapabilityContractExportSummary.builder()
                .domainCode(DOMAIN_CODE)
                .exportedContractCount(active.size() + planned.size() + gaps.size())
                .activeContractCount(active.size())
                .plannedContractCount(planned.size())
                .knownGapContractCount(gaps.size())
                .knownGapMarkers(gapMarkers)
                .build();
    }

    private static SemanticCapabilityContract fromMatrixRow(
            RevenueSemanticCapabilityMatrixRow row,
            SemanticCapabilityContractStatus status,
            String gapMarker) {
        String contractId = contractIdForRow(row);
        return SemanticCapabilityContract.builder()
                .contractId(contractId)
                .domain(DOMAIN_CODE)
                .intentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW)
                .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                .wire(row.getStructuredIntentDetailWire())
                .queryObject(row.getQueryObject())
                .operation(row.getOperation())
                .metric(row.getMetric())
                .sourceFacet(null)
                .detailWanted(null)
                .answerPlanType(row.getTargetRevenuePlanType())
                .requiresAnchor(false)
                .anchorType(null)
                .selectedTools(REVENUE_TOOLS)
                .status(status)
                .gapMarker(gapMarker)
                .build();
    }

    private static String contractIdForRow(RevenueSemanticCapabilityMatrixRow row) {
        return switch (row.getRowId()) {
            case "RV-A" -> "revenue.overview";
            case "RV-B" -> "revenue.store_amount_ranking";
            case "RV-C" -> "revenue.single_store_overview";
            case "RV-D" -> "revenue.store_compare";
            case "RV-H" -> "revenue.period_compare";
            case "RV-I" -> "revenue.daily_amount_ranking";
            case "RV-J" -> "revenue.trend";
            default -> "revenue." + row.getRowId().toLowerCase();
        };
    }

    private static SemanticCapabilityContract plannedContract(
            String contractId,
            String wire,
            String answerPlanType,
            String queryObject,
            Set<String> operations,
            Set<String> metrics,
            String gapMarker) {
        return SemanticCapabilityContract.builder()
                .contractId(contractId)
                .domain(DOMAIN_CODE)
                .intentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW)
                .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                .wire(wire)
                .queryObjects(new LinkedHashSet<>(Set.of(queryObject)))
                .operations(operations)
                .metrics(metrics)
                .sourceFacet(null)
                .detailWanted(null)
                .answerPlanType(answerPlanType)
                .requiresAnchor(false)
                .anchorType(null)
                .selectedTools(REVENUE_TOOLS)
                .status(SemanticCapabilityContractStatus.PLANNED)
                .gapMarker(gapMarker)
                .build();
    }
}
