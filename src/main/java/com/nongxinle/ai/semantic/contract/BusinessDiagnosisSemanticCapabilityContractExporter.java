package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.agent.business.BusinessDiagnosisAgentV1;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.semantic.matrix.BusinessDiagnosisSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.BusinessDiagnosisSemanticCapabilityMatrixRow;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;

/**
 * 经营诊断域 Step 2 小合同只读导出。
 * <p>ACTIVE：Matrix 行无 knownGapCode；KNOWN_GAP：子域归因等 Plan 依赖未稳定行。
 */
public final class BusinessDiagnosisSemanticCapabilityContractExporter
        implements SemanticCapabilityContractExporter {

    public static final String DOMAIN_CODE = "BUSINESS_DIAGNOSIS";

    public static final BusinessDiagnosisSemanticCapabilityContractExporter INSTANCE =
            new BusinessDiagnosisSemanticCapabilityContractExporter();

    private static final List<String> DIAGNOSIS_TOOLS = AiBusinessToolIds.DEFAULT_BUSINESS_DIAGNOSIS_TOOLS;

    private static final List<BusinessDiagnosisSemanticCapabilityMatrixRow> ALL_ROWS =
            List.of(
                    BusinessDiagnosisSemanticCapabilityMatrix.SUMMARY,
                    BusinessDiagnosisSemanticCapabilityMatrix.STORE_PRIORITY_RANKING,
                    BusinessDiagnosisSemanticCapabilityMatrix.STORE_RISK_REASONS_INHERITED,
                    BusinessDiagnosisSemanticCapabilityMatrix.STORE_RISK_REASONS_NAMED,
                    BusinessDiagnosisSemanticCapabilityMatrix.STORE_DOMAIN_PURCHASE,
                    BusinessDiagnosisSemanticCapabilityMatrix.STORE_DOMAIN_STOCK_REDUCE,
                    BusinessDiagnosisSemanticCapabilityMatrix.STORE_DOMAIN_DISH_PROFIT,
                    BusinessDiagnosisSemanticCapabilityMatrix.STORE_COMPARE_DIAGNOSIS,
                    BusinessDiagnosisSemanticCapabilityMatrix.ACTION_SUGGESTION);

    private BusinessDiagnosisSemanticCapabilityContractExporter() {
    }

    @Override
    public String domain() {
        return DOMAIN_CODE;
    }

    @Override
    public List<SemanticCapabilityContract> exportActiveContracts() {
        List<SemanticCapabilityContract> out = new ArrayList<>();
        for (BusinessDiagnosisSemanticCapabilityMatrixRow row : ALL_ROWS) {
            if (row.getKnownGapCode() == null) {
                out.add(fromMatrixRow(row, SemanticCapabilityContractStatus.ACTIVE, null));
            }
        }
        return List.copyOf(out);
    }

    @Override
    public List<SemanticCapabilityContract> exportPlannedContracts() {
        return List.of();
    }

    @Override
    public List<SemanticCapabilityContract> exportKnownGapContracts() {
        List<SemanticCapabilityContract> out = new ArrayList<>();
        for (BusinessDiagnosisSemanticCapabilityMatrixRow row : ALL_ROWS) {
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
            BusinessDiagnosisSemanticCapabilityMatrixRow row,
            SemanticCapabilityContractStatus status,
            String gapMarker) {
        SemanticCapabilityContract.SemanticCapabilityContractBuilder b =
                SemanticCapabilityContract.builder()
                .contractId(contractIdForRow(row))
                .domain(DOMAIN_CODE)
                .intentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS)
                .pathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS)
                .wire(row.getStructuredIntentDetailWire())
                .metric(diagnosisMetric(row));
        if (BusinessDiagnosisSemanticCapabilityMatrix.SUMMARY.equals(row)) {
            b.queryObject("GROUP")
                    .queryObject("STORE")
                    .queryObject("BUSINESS")
                    .operation("SUMMARY")
                    .operation("DIAGNOSIS")
                    .operation("OVERVIEW");
        } else if (BusinessDiagnosisSemanticCapabilityMatrix.STORE_COMPARE_DIAGNOSIS.equals(row)) {
            b.queryObject("STORE")
                    .queryObject("BUSINESS")
                    .operation("COMPARE");
        } else {
            b.queryObject(row.getQueryObject()).operation(row.getOperation());
        }
        return b
                .sourceFacet(null)
                .detailWanted(null)
                .answerPlanType(answerPlanTypeForRow(row))
                .requiresAnchor(requiresStoreAnchor(row))
                .anchorType(requiresStoreAnchor(row) ? "STORE" : null)
                .selectedTools(plannerToolsForRow(row))
                .status(status)
                .gapMarker(gapMarker)
                .build();
    }

    private static String diagnosisMetric(BusinessDiagnosisSemanticCapabilityMatrixRow row) {
        if (row.getChildDomain() != null) {
            return row.getChildDomain();
        }
        if (BusinessDiagnosisSemanticCapabilityMatrix.FACET_SUMMARY.equals(row.getDiagnosisFacet())) {
            return "BUSINESS_STATUS";
        }
        if (BusinessDiagnosisSemanticCapabilityMatrix.FACET_STORE_PRIORITY.equals(row.getDiagnosisFacet())) {
            return "BUSINESS_STATUS";
        }
        if (BusinessDiagnosisSemanticCapabilityMatrix.FACET_STORE_RISK_REASONS.equals(row.getDiagnosisFacet())) {
            return "BUSINESS_STATUS";
        }
        if (BusinessDiagnosisSemanticCapabilityMatrix.FACET_ACTION.equals(row.getDiagnosisFacet())) {
            return "BUSINESS_STATUS";
        }
        return "BUSINESS_STATUS";
    }

    private static String answerPlanTypeForRow(BusinessDiagnosisSemanticCapabilityMatrixRow row) {
        if (BusinessDiagnosisSemanticCapabilityMatrix.STORE_PRIORITY_RANKING.equals(row)) {
            return BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_PRIORITY_RANKING;
        }
        if (BusinessDiagnosisSemanticCapabilityMatrix.STORE_RISK_REASONS_INHERITED.equals(row)
                || BusinessDiagnosisSemanticCapabilityMatrix.STORE_RISK_REASONS_NAMED.equals(row)) {
            return BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_RISK_REASONS;
        }
        if (StringUtils.hasText(row.getDiagnosisQuestionType())) {
            return row.getDiagnosisQuestionType();
        }
        return DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS;
    }

    private static boolean requiresStoreAnchor(BusinessDiagnosisSemanticCapabilityMatrixRow row) {
        return BusinessDiagnosisSemanticCapabilityMatrix.STORE_RISK_REASONS_INHERITED.equals(row)
                || BusinessDiagnosisSemanticCapabilityMatrix.STORE_DOMAIN_PURCHASE.equals(row)
                || BusinessDiagnosisSemanticCapabilityMatrix.STORE_DOMAIN_STOCK_REDUCE.equals(row)
                || BusinessDiagnosisSemanticCapabilityMatrix.STORE_DOMAIN_DISH_PROFIT.equals(row)
                || BusinessDiagnosisSemanticCapabilityMatrix.ACTION_SUGGESTION.equals(row);
    }

    private static List<String> plannerToolsForRow(BusinessDiagnosisSemanticCapabilityMatrixRow row) {
        if (row.getChildDomain() != null) {
            return switch (row.getChildDomain()) {
                case BusinessDiagnosisSemanticCapabilityMatrix.CHILD_PURCHASE ->
                        List.of(AiBusinessToolIds.PURCHASE_OVERVIEW);
                case BusinessDiagnosisSemanticCapabilityMatrix.CHILD_STOCK_REDUCE ->
                        List.of(AiBusinessToolIds.STOCK_REDUCE_QUERY);
                case BusinessDiagnosisSemanticCapabilityMatrix.CHILD_DISH_PROFIT ->
                        List.of(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
                default -> DIAGNOSIS_TOOLS;
            };
        }
        return BusinessDiagnosisSemanticCapabilityMatrix.plannerToolsForWire(
                row.getStructuredIntentDetailWire());
    }

    private static String contractIdForRow(BusinessDiagnosisSemanticCapabilityMatrixRow row) {
        return switch (row.getRowId()) {
            case "BD-A" -> "business_diagnosis.summary";
            case "BD-B" -> "business_diagnosis.store_priority_ranking";
            case "BD-C" -> "business_diagnosis.store_risk_reasons_inherited";
            case "BD-D" -> "business_diagnosis.store_risk_reasons_named";
            case "BD-E" -> "business_diagnosis.store_domain_purchase";
            case "BD-F" -> "business_diagnosis.store_domain_stock_reduce";
            case "BD-G" -> "business_diagnosis.store_domain_dish_profit";
            case "BD-H" -> "business_diagnosis.store_compare";
            case "BD-K" -> "business_diagnosis.action_followup";
            default -> "business_diagnosis." + row.getRowId().toLowerCase();
        };
    }
}
