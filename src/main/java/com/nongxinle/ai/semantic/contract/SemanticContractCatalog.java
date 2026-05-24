package com.nongxinle.ai.semantic.contract;

import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 两段式语义合同只读聚合 Catalog（P1 skeleton）。
 * <p>汇总 Step 1 {@link DomainRoutingContract} 与 Step 2 {@link SemanticCapabilityContract}；
 * 当前不能被主链 Validator / Parser 消费。
 */
@UtilityClass
public final class SemanticContractCatalog {

    private static final List<SemanticCapabilityContractExporter> CAPABILITY_EXPORTERS =
            List.of(
                    PurchaseSemanticCapabilityContractExporter.INSTANCE,
                    RevenueSemanticCapabilityContractExporter.INSTANCE,
                    StockReduceSemanticCapabilityContractExporter.INSTANCE,
                    WarehouseSemanticCapabilityContractExporter.INSTANCE,
                    DishSalesSemanticCapabilityContractExporter.INSTANCE,
                    DishProfitSemanticCapabilityContractExporter.INSTANCE,
                    BusinessDiagnosisSemanticCapabilityContractExporter.INSTANCE,
                    BusinessOverviewSemanticCapabilityContractExporter.INSTANCE);

    public static List<DomainRoutingContract> listDomainRoutingContracts() {
        return DomainRoutingContractCatalog.listDomainRoutingContracts();
    }

    public static List<SemanticCapabilityContract> listCapabilityContracts(String domain) {
        SemanticCapabilityContractExporter exporter = exporterFor(domain);
        return exporter == null ? List.of() : exporter.exportAllContracts();
    }

    public static List<SemanticCapabilityContract> listActiveCapabilityContracts(String domain) {
        SemanticCapabilityContractExporter exporter = exporterFor(domain);
        return exporter == null ? List.of() : exporter.exportActiveContracts();
    }

    public static List<SemanticCapabilityContract> listKnownGaps(String domain) {
        SemanticCapabilityContractExporter exporter = exporterFor(domain);
        return exporter == null ? List.of() : exporter.exportKnownGapContracts();
    }

    /**
     * 按 {@code contractId} 查找 ACTIVE 能力合同；优先 {@code domainHint} 域内，再跨域只读扫描。
     * <p>用于 contract-locked execution mapping，不做自然语言路由。
     */
    public static SemanticCapabilityContract findActiveCapabilityContractById(
            String contractId, String domainHint) {
        if (!StringUtils.hasText(contractId)) {
            return null;
        }
        String id = contractId.trim();
        if (StringUtils.hasText(domainHint)) {
            SemanticCapabilityContract inDomain = findActiveInDomain(id, domainHint.trim());
            if (inDomain != null) {
                return inDomain;
            }
        }
        for (SemanticCapabilityContractExporter exporter : CAPABILITY_EXPORTERS) {
            SemanticCapabilityContract found = findActiveInExporter(id, exporter);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static SemanticCapabilityContract findActiveInDomain(String contractId, String domain) {
        SemanticCapabilityContractExporter exporter = exporterFor(domain);
        return exporter == null ? null : findActiveInExporter(contractId, exporter);
    }

    private static SemanticCapabilityContract findActiveInExporter(
            String contractId, SemanticCapabilityContractExporter exporter) {
        if (exporter == null || !StringUtils.hasText(contractId)) {
            return null;
        }
        for (SemanticCapabilityContract c : exporter.exportActiveContracts()) {
            if (c != null
                    && contractId.equals(c.getContractId())
                    && c.getStatus() == SemanticCapabilityContractStatus.ACTIVE) {
                return c;
            }
        }
        return null;
    }

    public static SemanticContractCatalogSummary summarize() {
        List<DomainRoutingContract> routing = listDomainRoutingContracts();
        List<String> routingDomains =
                routing.stream().map(DomainRoutingContract::getDomainCode).toList();

        int capabilityTotal = 0;
        List<String> capabilityDomains = new ArrayList<>();
        Map<String, Integer> countByDomain = new LinkedHashMap<>();
        Map<String, Integer> activeByDomain = new LinkedHashMap<>();
        Map<String, Integer> plannedByDomain = new LinkedHashMap<>();
        Map<String, Integer> knownGapByDomain = new LinkedHashMap<>();
        Set<String> gapMarkers = new LinkedHashSet<>();

        for (SemanticCapabilityContractExporter exporter : CAPABILITY_EXPORTERS) {
            String domain = exporter.domain();
            capabilityDomains.add(domain);
            List<SemanticCapabilityContract> all = exporter.exportAllContracts();
            List<SemanticCapabilityContract> active = exporter.exportActiveContracts();
            List<SemanticCapabilityContract> planned = exporter.exportPlannedContracts();
            List<SemanticCapabilityContract> gaps = exporter.exportKnownGapContracts();
            capabilityTotal += all.size();
            countByDomain.put(domain, all.size());
            activeByDomain.put(domain, active.size());
            plannedByDomain.put(domain, planned.size());
            knownGapByDomain.put(domain, gaps.size());
            SemanticCapabilityContractExportSummary summary = exporter.exportSummary();
            if (summary.getKnownGapMarkers() != null) {
                gapMarkers.addAll(summary.getKnownGapMarkers());
            }
        }

        List<String> missingCapability = new ArrayList<>();
        for (String routingDomain : routingDomains) {
            if (!capabilityDomains.contains(routingDomain)) {
                missingCapability.add(routingDomain);
            }
        }

        SemanticCapabilityContractExportSummary purchaseSummary =
                PurchaseSemanticCapabilityContractExporter.exportContractSummary();

        return SemanticContractCatalogSummary.builder()
                .domainRoutingContractCount(routing.size())
                .capabilityContractCount(capabilityTotal)
                .purchaseCapabilityContractCount(purchaseSummary.getExportedPurchaseContractCount())
                .activePurchaseCapabilityContractCount(purchaseSummary.getActivePurchaseContractCount())
                .plannedPurchaseCapabilityContractCount(purchaseSummary.getPlannedPurchaseContractCount())
                .knownGapPurchaseCapabilityContractCount(purchaseSummary.getKnownGapPurchaseContractCount())
                .domainsWithRoutingContract(routingDomains)
                .domainsWithCapabilityContract(capabilityDomains)
                .knownGapMarkers(new ArrayList<>(gapMarkers))
                .unsupportedAliasNotRegistered(true)
                .activeCapabilityCountByDomain(activeByDomain)
                .capabilityContractCountByDomain(countByDomain)
                .plannedCapabilityCountByDomain(plannedByDomain)
                .knownGapCapabilityCountByDomain(knownGapByDomain)
                .domainsMissingCapabilityContract(missingCapability)
                .build();
    }

    /** 只读 debug map，供 Harness / 日志观测。 */
    public static Map<String, Object> dump() {
        SemanticContractCatalogSummary s = summarize();
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("domainRoutingContractCount", s.getDomainRoutingContractCount());
        out.put("totalCapabilityContractCount", s.getCapabilityContractCount());
        out.put("capabilityContractCount", s.getCapabilityContractCount());
        out.put("purchaseCapabilityContractCount", s.getPurchaseCapabilityContractCount());
        out.put("activePurchaseCapabilityContractCount", s.getActivePurchaseCapabilityContractCount());
        out.put("plannedPurchaseCapabilityContractCount", s.getPlannedPurchaseCapabilityContractCount());
        out.put("knownGapPurchaseCapabilityContractCount", s.getKnownGapPurchaseCapabilityContractCount());
        out.put("domainsWithRoutingContract", s.getDomainsWithRoutingContract());
        out.put("domainsWithCapabilityContract", s.getDomainsWithCapabilityContract());
        out.put("domainsMissingCapabilityContract", s.getDomainsMissingCapabilityContract());
        out.put("knownGapContractMarkers", s.getKnownGapMarkers());
        out.put("knownGapPurchaseContractMarkers", s.getKnownGapMarkers());
        out.put("unsupportedAliasNotRegistered", s.isUnsupportedAliasNotRegistered());
        out.put("activeCapabilityCountByDomain", s.getActiveCapabilityCountByDomain());
        out.put("capabilityContractCountByDomain", s.getCapabilityContractCountByDomain());
        out.put("plannedCapabilityCountByDomain", s.getPlannedCapabilityCountByDomain());
        out.put("knownGapCapabilityCountByDomain", s.getKnownGapCapabilityCountByDomain());
        return out;
    }

    private static SemanticCapabilityContractExporter exporterFor(String domain) {
        if (domain == null) {
            return null;
        }
        String code = domain.trim().toUpperCase(Locale.ROOT);
        for (SemanticCapabilityContractExporter exporter : CAPABILITY_EXPORTERS) {
            if (code.equals(exporter.domain())) {
                return exporter;
            }
        }
        return null;
    }
}
