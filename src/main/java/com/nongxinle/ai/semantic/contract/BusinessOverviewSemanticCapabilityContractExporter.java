package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.semantic.matrix.BusinessOverviewSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.BusinessOverviewSemanticCapabilityMatrixRow;

import java.util.ArrayList;
import java.util.List;

/**
 * 经营概览域 Step 2 小合同只读导出（P2E）。
 * <p>ACTIVE：{@link BusinessOverviewSemanticCapabilityMatrix#firstTurnRows()} 无 knownGap 行。
 */
public final class BusinessOverviewSemanticCapabilityContractExporter
        implements SemanticCapabilityContractExporter {

    public static final String DOMAIN_CODE = "BUSINESS_OVERVIEW";

    public static final BusinessOverviewSemanticCapabilityContractExporter INSTANCE =
            new BusinessOverviewSemanticCapabilityContractExporter();

    private BusinessOverviewSemanticCapabilityContractExporter() {}

    @Override
    public String domain() {
        return DOMAIN_CODE;
    }

    @Override
    public List<SemanticCapabilityContract> exportActiveContracts() {
        List<SemanticCapabilityContract> out = new ArrayList<>();
        for (BusinessOverviewSemanticCapabilityMatrixRow row :
                BusinessOverviewSemanticCapabilityMatrix.firstTurnRows()) {
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
        for (BusinessOverviewSemanticCapabilityMatrixRow row :
                BusinessOverviewSemanticCapabilityMatrix.firstTurnRows()) {
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
            BusinessOverviewSemanticCapabilityMatrixRow row,
            SemanticCapabilityContractStatus status,
            String gapMarker) {
        SemanticCapabilityContract.SemanticCapabilityContractBuilder b =
                SemanticCapabilityContract.builder()
                        .contractId(contractIdForRow(row))
                        .domain(DOMAIN_CODE)
                        .intentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW)
                        .pathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW)
                        .wire(row.getStructuredIntentDetailWire())
                        .metric(row.getMetric())
                        .sourceFacet(null)
                        .detailWanted(null)
                        .answerPlanType(row.getTargetOverviewPlanType())
                        .requiresAnchor(false)
                        .anchorType(null)
                        .selectedTools(BusinessOverviewSemanticCapabilityMatrix.defaultFourDomainPlannerTools())
                        .status(status)
                        .gapMarker(gapMarker);
        if (BusinessOverviewSemanticCapabilityMatrix.STORE_STATUS_COMPARE.equals(row)) {
            b.queryObject(row.getQueryObject()).operation(row.getOperation());
        } else {
            b.queryObject("BUSINESS")
                    .queryObject("STORE")
                    .queryObject("GROUP")
                    .operation("SUMMARY")
                    .operation("OVERVIEW");
        }
        return b.build();
    }

    private static String contractIdForRow(BusinessOverviewSemanticCapabilityMatrixRow row) {
        return switch (row.getRowId()) {
            case "BO-A" -> "business_overview.summary";
            case "BO-B" -> "business_overview.status";
            case "BO-C" -> "business_overview.store_status_compare";
            default -> "business_overview." + row.getRowId().toLowerCase();
        };
    }
}
