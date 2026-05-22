package com.nongxinle.ai.semantic.contract;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;

/** 只读 Catalog 聚合统计（Harness / debug；不驱动 Validator / Parser）。 */
@Value
@Builder
public class SemanticContractCatalogSummary {

    int domainRoutingContractCount;
    int capabilityContractCount;
    int purchaseCapabilityContractCount;
    int activePurchaseCapabilityContractCount;
    int plannedPurchaseCapabilityContractCount;
    int knownGapPurchaseCapabilityContractCount;

    @Singular("domainWithRoutingContract")
    List<String> domainsWithRoutingContract;

    @Singular("domainWithCapabilityContract")
    List<String> domainsWithCapabilityContract;

    @Singular("knownGapMarker")
    List<String> knownGapMarkers;

    /** Catalog 不登记 compat alias；非法 wire 仍由运行时 Lexicon 处理，Catalog 侧恒为 true。 */
    @Builder.Default
    boolean unsupportedAliasNotRegistered = true;

    /** 按 domain 的 ACTIVE capability 计数。 */
    Map<String, Integer> activeCapabilityCountByDomain;

    Map<String, Integer> capabilityContractCountByDomain;
    Map<String, Integer> plannedCapabilityCountByDomain;
    Map<String, Integer> knownGapCapabilityCountByDomain;

    /** 有 RoutingContract 但尚无 CapabilityContract exporter 的域。 */
    List<String> domainsMissingCapabilityContract;
}
