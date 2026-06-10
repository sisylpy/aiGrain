package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.identity.BusinessEntityExistenceLookup;
import com.nongxinle.ai.identity.BusinessEntityIdentityBridge;
import com.nongxinle.ai.identity.EntityAnchorSovereigntySupport;
import com.nongxinle.ai.identity.EntityIdentityResolutionSource;
import com.nongxinle.ai.identity.EntityIdentityResolutionStatus;
import com.nongxinle.ai.identity.EntityIdentityType;
import com.nongxinle.ai.identity.ResolvedEntityIdentity;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import com.nongxinle.ai.semantic.contract.SemanticContractCatalog;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.frame.ContractLockedSemanticFrame;
import com.nongxinle.ai.semantic.frame.PurchaseLockedSemanticFrameSupport;
import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

/**
 * requiresAnchor=true 的 ACTIVE contract：实体 identity 未 OK 时 fail-closed，禁止 Tool 以 overview/ranking 退化执行。
 * <p>只读 {@link ContractLockedSemanticFrame} + Catalog entry + {@link ResolvedEntityIdentity}；不做 NL 推断。
 */
public final class RequiresAnchorExecutionGateSupport {

    public static final String BLOCK_GOODS_IDENTITY_NOT_FOUND = "GOODS_IDENTITY_NOT_FOUND";
    public static final String BLOCK_GOODS_IDENTITY_AMBIGUOUS = "GOODS_IDENTITY_AMBIGUOUS";
    public static final String BLOCK_GOODS_IDENTITY_UNRESOLVED = "GOODS_IDENTITY_UNRESOLVED";
    public static final String BLOCK_GOODS_IDENTITY_MISSING = "GOODS_IDENTITY_MISSING";
    public static final String BLOCK_GOODS_NAME_ID_CONFLICT = "GOODS_NAME_ID_CONFLICT";
    public static final String BLOCK_UNSUPPORTED_ANCHOR_TYPE = "UNSUPPORTED_ANCHOR_TYPE";

    private RequiresAnchorExecutionGateSupport() {}

    public enum Outcome {
        ALLOW,
        BLOCK
    }

    @Value
    @Builder
    public static class Decision {
        Outcome outcome;
        String contractId;
        String anchorType;
        String blockReason;
        String clarificationMessage;
        String requestedEntityName;
        EntityIdentityResolutionStatus identityStatus;
        EntityIdentityType entityIdentityType;

        public boolean blocksToolExecution() {
            return outcome == Outcome.BLOCK;
        }
    }

    public static Decision evaluate(AiResolvedQueryContext ctx) {
        if (ctx == null
                || !SemanticContractCompletionEngine.isContractLockedParse(ctx.getQuerySemanticParse())) {
            return allow(null, null);
        }
        ContractLockedSemanticFrame frame = PurchaseLockedSemanticFrameSupport.lockedFrame(ctx);
        String contractId = PurchaseLockedSemanticFrameSupport.selectedContractId(frame);
        if (!StringUtils.hasText(contractId)) {
            return allow(null, null);
        }
        String domain = domainHint(ctx);
        SemanticCapabilityContract contract =
                SemanticContractCatalog.findActiveCapabilityContractById(contractId.trim(), domain);
        if (contract == null || !contract.isRequiresAnchor()) {
            return allow(contractId, contract != null ? contract.getAnchorType() : null);
        }
        String anchorType = normalizeAnchorType(contract.getAnchorType());
        if (!StringUtils.hasText(anchorType) || "NONE".equals(anchorType)) {
            return allow(contractId, anchorType);
        }
        if (AiResultAnchor.ENTITY_TYPE_GOODS.equals(anchorType)) {
            return evaluateGoods(ctx, contractId, anchorType);
        }
        return Decision.builder()
                .outcome(Outcome.BLOCK)
                .contractId(contractId)
                .anchorType(anchorType)
                .blockReason(BLOCK_UNSUPPORTED_ANCHOR_TYPE)
                .clarificationMessage("当前问题需要定位具体业务对象，但系统暂不支持该锚点类型的自动解析。")
                .build();
    }

    public static boolean blocksToolExecution(AiResolvedQueryContext ctx) {
        return evaluate(ctx).blocksToolExecution();
    }

    private static Decision evaluateGoods(
            AiResolvedQueryContext ctx, String contractId, String anchorType) {
        ResolvedEntityIdentity identity = ctx.getResolvedGoodsIdentity();
        if (identity == null) {
            identity = BusinessEntityIdentityBridge.resolveGoods(ctx);
        }
        if (identity == null || identity.getResolutionStatus() == EntityIdentityResolutionStatus.SKIPPED) {
            return Decision.builder()
                    .outcome(Outcome.BLOCK)
                    .contractId(contractId)
                    .anchorType(anchorType)
                    .blockReason(BLOCK_GOODS_IDENTITY_MISSING)
                    .clarificationMessage(BusinessEntityExistenceLookup.CLARIFICATION_GOODS_NOT_FOUND)
                    .entityIdentityType(EntityIdentityType.GOODS)
                    .build();
        }
        if (identity.getResolutionSource() == EntityIdentityResolutionSource.CURRENT_NAME_ID_CONFLICT
                || EntityAnchorSovereigntySupport.identityUsedHistoricalAnchorWithExplicitNameConflict(
                        identity)) {
            String requestedName =
                    firstNonBlank(identity.getUserMentionedName(), identity.getResolvedCanonicalName());
            return Decision.builder()
                    .outcome(Outcome.BLOCK)
                    .contractId(contractId)
                    .anchorType(anchorType)
                    .blockReason(BLOCK_GOODS_NAME_ID_CONFLICT)
                    .clarificationMessage(BusinessEntityExistenceLookup.CLARIFICATION_GOODS_NOT_FOUND)
                    .requestedEntityName(requestedName)
                    .identityStatus(EntityIdentityResolutionStatus.NOT_FOUND)
                    .entityIdentityType(EntityIdentityType.GOODS)
                    .build();
        }
        if (identity.isExecutable()) {
            return allow(contractId, anchorType);
        }
        String requestedName = firstNonBlank(identity.getUserMentionedName(), identity.getResolvedCanonicalName());
        return switch (identity.getResolutionStatus()) {
            case NOT_FOUND -> Decision.builder()
                    .outcome(Outcome.BLOCK)
                    .contractId(contractId)
                    .anchorType(anchorType)
                    .blockReason(BLOCK_GOODS_IDENTITY_NOT_FOUND)
                    .clarificationMessage(
                            StringUtils.hasText(identity.getClarificationMessage())
                                    ? identity.getClarificationMessage().trim()
                                    : BusinessEntityExistenceLookup.CLARIFICATION_GOODS_NOT_FOUND)
                    .requestedEntityName(requestedName)
                    .identityStatus(EntityIdentityResolutionStatus.NOT_FOUND)
                    .entityIdentityType(EntityIdentityType.GOODS)
                    .build();
            case NEED_CLARIFICATION -> Decision.builder()
                    .outcome(Outcome.BLOCK)
                    .contractId(contractId)
                    .anchorType(anchorType)
                    .blockReason(BLOCK_GOODS_IDENTITY_AMBIGUOUS)
                    .clarificationMessage(
                            StringUtils.hasText(identity.getClarificationMessage())
                                    ? identity.getClarificationMessage().trim()
                                    : "找到多个同名库存原料，请说更完整的原料名。")
                    .requestedEntityName(requestedName)
                    .identityStatus(EntityIdentityResolutionStatus.NEED_CLARIFICATION)
                    .entityIdentityType(EntityIdentityType.GOODS)
                    .build();
            case UNRESOLVED -> Decision.builder()
                    .outcome(Outcome.BLOCK)
                    .contractId(contractId)
                    .anchorType(anchorType)
                    .blockReason(BLOCK_GOODS_IDENTITY_UNRESOLVED)
                    .clarificationMessage(BusinessEntityExistenceLookup.CLARIFICATION_GOODS_NOT_FOUND)
                    .requestedEntityName(requestedName)
                    .identityStatus(EntityIdentityResolutionStatus.UNRESOLVED)
                    .entityIdentityType(EntityIdentityType.GOODS)
                    .build();
            default -> allow(contractId, anchorType);
        };
    }

    private static Decision allow(String contractId, String anchorType) {
        return Decision.builder()
                .outcome(Outcome.ALLOW)
                .contractId(contractId)
                .anchorType(anchorType)
                .build();
    }

    private static String domainHint(AiResolvedQueryContext ctx) {
        if (ctx.getQuerySemanticParse() != null
                && StringUtils.hasText(ctx.getQuerySemanticParse().getSemanticDomain())) {
            return ctx.getQuerySemanticParse().getSemanticDomain().trim();
        }
        if (ctx.getQuerySemanticParse() != null && ctx.getQuerySemanticParse().getContractCompletionTrace() != null) {
            Object domain = ctx.getQuerySemanticParse().getContractCompletionTrace().get("domain");
            if (domain instanceof String s && StringUtils.hasText(s)) {
                return s.trim();
            }
        }
        return null;
    }

    private static String normalizeAnchorType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase();
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        if (StringUtils.hasText(b)) {
            return b.trim();
        }
        return null;
    }
}
