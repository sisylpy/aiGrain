package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteResult;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteType;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Step 2：根据 Intake route 结果选择单域 allowed 合同摘要（只读 Catalog；不合并八域大合同）。
 */
public final class DomainContractSelector {

    private DomainContractSelector() {
    }

    public static DomainContractSelectionResult select(SemanticDomainRouteResult route) {
        if (route == null) {
            return skipped(null, "route_null");
        }
        String primary = blank(route.getPrimaryDomain());
        if (!StringUtils.hasText(primary)) {
            if (route.getRouteType() == SemanticDomainRouteType.AMBIGUOUS
                    && route.getCandidateDomains() != null
                    && !route.getCandidateDomains().isEmpty()) {
                return skipped(null, "ambiguous_no_primary");
            }
            return skipped(null, "no_primary_domain");
        }

        List<SemanticCapabilityContract> all = SemanticContractCatalog.listCapabilityContracts(primary);
        List<SemanticCapabilityContract> active = SemanticContractCatalog.listActiveCapabilityContracts(primary);
        List<SemanticCapabilityContract> gaps = SemanticContractCatalog.listKnownGaps(primary);

        boolean missing = active.isEmpty();
        List<SemanticCapabilityContract> planned = SemanticContractCatalog.listPlannedCapabilityContracts(primary);
        SemanticParserAllowedOutputContract parserContract =
                missing ? null : flattenParserContracts(primary, active, gaps, planned);

        return DomainContractSelectionResult.builder()
                .selectedDomain(primary)
                .selectedCapabilityContractCount(all.size())
                .selectedActiveContractCount(active.size())
                .selectedKnownGapCount(gaps.size())
                .capabilityContractMissing(missing)
                .parserAllowedOutputContract(parserContract)
                .build();
    }

    private static DomainContractSelectionResult skipped(String selectedDomain, String reason) {
        return DomainContractSelectionResult.builder()
                .selectedDomain(selectedDomain)
                .contractSelectionSkippedReason(reason)
                .capabilityContractMissing(true)
                .build();
    }

    static SemanticParserAllowedOutputContract flattenParserContracts(
            String domain,
            List<SemanticCapabilityContract> active,
            List<SemanticCapabilityContract> knownGaps,
            List<SemanticCapabilityContract> planned) {
        LinkedHashSet<String> wires = new LinkedHashSet<>();
        LinkedHashSet<String> queryObjects = new LinkedHashSet<>();
        LinkedHashSet<String> operations = new LinkedHashSet<>();
        LinkedHashSet<String> metrics = new LinkedHashSet<>();
        LinkedHashSet<String> sourceFacets = new LinkedHashSet<>();
        LinkedHashSet<String> detailWanted = new LinkedHashSet<>();
        LinkedHashSet<String> answerPlanTypes = new LinkedHashSet<>();
        List<SemanticParserAllowedOutputContract.AllowedContractEntry> entries = new ArrayList<>();
        List<SemanticParserAllowedOutputContract.AllowedContractEntry> gapEntries = new ArrayList<>();

        for (SemanticCapabilityContract c : active) {
            if (c == null || c.getStatus() != SemanticCapabilityContractStatus.ACTIVE) {
                continue;
            }
            entries.add(toAllowedEntry(c, SemanticCapabilityContractStatus.ACTIVE.name()));
            addIfText(wires, c.getWire());
            addIfText(answerPlanTypes, c.getAnswerPlanType());
            addIfText(sourceFacets, c.getSourceFacet());
            addIfText(detailWanted, c.getDetailWanted());
            if (c.getQueryObjects() != null) {
                c.getQueryObjects().forEach(v -> addIfText(queryObjects, v));
            }
            if (c.getOperations() != null) {
                c.getOperations().forEach(v -> addIfText(operations, v));
            }
            if (c.getMetrics() != null) {
                c.getMetrics().forEach(v -> addIfText(metrics, v));
            }
        }
        for (SemanticCapabilityContract c : knownGaps) {
            if (c != null) {
                gapEntries.add(toAllowedEntry(c, SemanticCapabilityContractStatus.KNOWN_GAP.name()));
            }
        }
        for (SemanticCapabilityContract c : planned) {
            if (c != null) {
                gapEntries.add(toAllowedEntry(c, SemanticCapabilityContractStatus.PLANNED.name()));
            }
        }

        return SemanticParserAllowedOutputContract.builder()
                .selectedDomain(domain)
                .allowedContracts(entries)
                .knownGapContracts(gapEntries.isEmpty() ? null : gapEntries)
                .contractSelectionBoundaryHints(null)
                .allowedWires(new ArrayList<>(wires))
                .allowedQueryObjects(new ArrayList<>(queryObjects))
                .allowedOperations(new ArrayList<>(operations))
                .allowedMetrics(new ArrayList<>(metrics))
                .allowedSourceFacets(new ArrayList<>(sourceFacets))
                .allowedDetailWanted(new ArrayList<>(detailWanted))
                .allowedAnswerPlanTypes(new ArrayList<>(answerPlanTypes))
                .build();
    }

    private static SemanticParserAllowedOutputContract.AllowedContractEntry toAllowedEntry(
            SemanticCapabilityContract c, String capabilityStatus) {
        List<String> qos = c.getQueryObjects() != null ? new ArrayList<>(c.getQueryObjects()) : List.of();
        List<String> ops = c.getOperations() != null ? new ArrayList<>(c.getOperations()) : List.of();
        List<String> mets = c.getMetrics() != null ? new ArrayList<>(c.getMetrics()) : List.of();
        SemanticParserAllowedOutputContract.AllowedContractEntry.AllowedContractEntryBuilder b =
                SemanticParserAllowedOutputContract.AllowedContractEntry.builder()
                        .contractId(c.getContractId())
                        .wire(c.getWire())
                        .queryObjects(qos.isEmpty() ? null : qos)
                        .operations(ops.isEmpty() ? null : ops)
                        .metrics(mets.isEmpty() ? null : mets)
                        .sourceFacet(c.getSourceFacet())
                        .detailWanted(c.getDetailWanted())
                        .answerPlanType(c.getAnswerPlanType())
                        .requiresAnchor(c.isRequiresAnchor())
                        .anchorType(c.getAnchorType())
                        .intentCode(c.getIntentCode())
                        .pathCode(c.getPathCode())
                        .selectedTools(
                                c.getSelectedTools() != null && !c.getSelectedTools().isEmpty()
                                        ? new ArrayList<>(c.getSelectedTools())
                                        : null);
        if (qos.size() == 1) {
            b.queryObject(qos.get(0));
        }
        if (ops.size() == 1) {
            b.operation(ops.get(0));
        }
        if (mets.size() == 1) {
            b.metric(mets.get(0));
        }
        b.description(c.getDescription())
                .selectionHint(c.getSelectionHint())
                .negativeHint(c.getNegativeHint());
        if (c.getPositiveExamples() != null && !c.getPositiveExamples().isEmpty()) {
            b.positiveExamples(new ArrayList<>(c.getPositiveExamples()));
        }
        if (c.getNegativeExamples() != null && !c.getNegativeExamples().isEmpty()) {
            b.negativeExamples(new ArrayList<>(c.getNegativeExamples()));
        }
        if (StringUtils.hasText(capabilityStatus)) {
            b.capabilityStatus(capabilityStatus);
        }
        if (StringUtils.hasText(c.getGapMarker())) {
            b.gapMarker(c.getGapMarker());
        }
        return b.build();
    }

    private static void addIfText(LinkedHashSet<String> set, String value) {
        if (StringUtils.hasText(value)) {
            set.add(value.trim());
        }
    }

    private static String blank(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
