package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import com.nongxinle.ai.semantic.routing.SemanticDomainRouteResult;
import com.nongxinle.ai.semantic.routing.SemanticDomainRouteType;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Step 2：根据 Router 结果选择单域 allowed 合同摘要（只读 Catalog；不合并八域大合同）。
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
        SemanticParserAllowedOutputContract parserContract =
                missing ? null : flattenActiveContracts(primary, active);

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

    static SemanticParserAllowedOutputContract flattenActiveContracts(
            String domain, List<SemanticCapabilityContract> active) {
        LinkedHashSet<String> wires = new LinkedHashSet<>();
        LinkedHashSet<String> queryObjects = new LinkedHashSet<>();
        LinkedHashSet<String> operations = new LinkedHashSet<>();
        LinkedHashSet<String> metrics = new LinkedHashSet<>();
        LinkedHashSet<String> sourceFacets = new LinkedHashSet<>();
        LinkedHashSet<String> detailWanted = new LinkedHashSet<>();
        LinkedHashSet<String> answerPlanTypes = new LinkedHashSet<>();

        for (SemanticCapabilityContract c : active) {
            if (c == null) {
                continue;
            }
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

        return SemanticParserAllowedOutputContract.builder()
                .selectedDomain(domain)
                .allowedWires(new ArrayList<>(wires))
                .allowedQueryObjects(new ArrayList<>(queryObjects))
                .allowedOperations(new ArrayList<>(operations))
                .allowedMetrics(new ArrayList<>(metrics))
                .allowedSourceFacets(new ArrayList<>(sourceFacets))
                .allowedDetailWanted(new ArrayList<>(detailWanted))
                .allowedAnswerPlanTypes(new ArrayList<>(answerPlanTypes))
                .build();
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
