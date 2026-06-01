package com.nongxinle.ai.semantic.contract;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Set;

/**
 * Matrix 行 → {@link SemanticCapabilityContract} 的统一薄导出支持。
 * <p>只填充结构化机器字段；不设置 description / selectionHint / negativeHint / examples。
 * <p>NL 路由规则见 {@code semantic_intake.v1.md}、{@code query_semantic_parser.v2.md} 与 Harness。
 * 治理说明见 {@code docs/ai/semantic-contract-exporter-governance.md}。
 */
public final class MatrixBackedContractExporterSupport {

    private MatrixBackedContractExporterSupport() {}

    /**
     * 从 Matrix 导出规格构建合同。调用方不得再链式设置已废弃的 NL hint 字段。
     */
    public static SemanticCapabilityContract build(MatrixContractExportSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec required");
        }
        SemanticCapabilityContract.SemanticCapabilityContractBuilder b =
                SemanticCapabilityContract.builder()
                        .contractId(spec.contractId)
                        .domain(spec.domain)
                        .intentCode(spec.intentCode)
                        .pathCode(spec.pathCode)
                        .wire(spec.wire)
                        .sourceFacet(spec.sourceFacet)
                        .detailWanted(spec.detailWanted)
                        .answerPlanType(spec.answerPlanType)
                        .requiresAnchor(spec.requiresAnchor)
                        .anchorType(spec.anchorType)
                        .status(spec.status)
                        .gapMarker(spec.gapMarker);
        if (spec.queryObjects != null) {
            spec.queryObjects.forEach(b::queryObject);
        }
        if (spec.operations != null) {
            spec.operations.forEach(b::operation);
        }
        if (spec.metrics != null) {
            spec.metrics.forEach(b::metric);
        }
        if (spec.selectedTools != null) {
            b.selectedTools(spec.selectedTools);
        }
        return b.build();
    }

    /**
     * 单条 Matrix 合同导出规格（结构化字段 only）。
     */
    @Value
    @Builder
    public static class MatrixContractExportSpec {
        String contractId;
        String domain;
        String intentCode;
        String pathCode;
        String wire;
        @Singular("queryObject")
        Set<String> queryObjects;
        @Singular("operation")
        Set<String> operations;
        @Singular("metric")
        Set<String> metrics;
        String sourceFacet;
        String detailWanted;
        String answerPlanType;
        boolean requiresAnchor;
        String anchorType;
        List<String> selectedTools;
        SemanticCapabilityContractStatus status;
        String gapMarker;
    }
}
