package com.nongxinle.ai.identity;

import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import com.nongxinle.ai.semantic.contract.SemanticContractCatalog;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.frame.ContractLockedSemanticFrame;
import com.nongxinle.ai.semantic.frame.PurchaseLockedSemanticFrameSupport;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 跨域 Entity Anchor 主权：当前轮显式实体优先于任何 previous / rewrite anchor。
 * 只读 V2 / LockedFrame / anchorPolicy 结构化字段，不读 raw message。
 */
public final class EntityAnchorSovereigntySupport {

    private EntityAnchorSovereigntySupport() {}

    public static String normalizeAnchorPolicy(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    public static String anchorPolicyFromParse(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return null;
        }
        ContractLockedSemanticFrame frame = sem.getContractLockedFrame();
        if (frame != null) {
            String fromLocked = PurchaseLockedSemanticFrameSupport.anchorPolicy(frame);
            if (StringUtils.hasText(fromLocked)) {
                return normalizeAnchorPolicy(fromLocked);
            }
        }
        if (sem.getSemanticSlots() == null) {
            return null;
        }
        return normalizeAnchorPolicy(sem.getSemanticSlots().getAnchorPolicy());
    }

    public static boolean isIgnorePreviousAnchor(String anchorPolicy) {
        return AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS.equals(normalizeAnchorPolicy(anchorPolicy));
    }

    public static boolean isUsePreviousAnchor(String anchorPolicy) {
        return AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(normalizeAnchorPolicy(anchorPolicy));
    }

    public static String resolveCurrentTurnGoodsName(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return null;
        }
        ContractLockedSemanticFrame frame = sem.getContractLockedFrame();
        if (frame != null && frame.getEntitySlots() != null) {
            String fromEntity = trimOrNull(frame.getEntitySlots().getMentionedGoodsName());
            if (fromEntity != null) {
                return fromEntity;
            }
        }
        return trimOrNull(sem.effectiveMentionedGoodsName());
    }

    public static String resolveCurrentTurnDishName(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return null;
        }
        ContractLockedSemanticFrame frame = sem.getContractLockedFrame();
        if (frame != null && frame.getEntitySlots() != null) {
            String fromEntity = trimOrNull(frame.getEntitySlots().getMentionedDishName());
            if (fromEntity != null) {
                return fromEntity;
            }
        }
        return trimOrNull(sem.effectiveMentionedDishName());
    }

    public static boolean hasCurrentTurnExplicitGoodsName(AiQuerySemanticParseResult sem) {
        return StringUtils.hasText(resolveCurrentTurnGoodsName(sem));
    }

    public static boolean hasCurrentTurnExplicitDishName(AiQuerySemanticParseResult sem) {
        return StringUtils.hasText(resolveCurrentTurnDishName(sem));
    }

    /**
     * 历史 anchor（previous / rewrite / turn memory）是否允许进入 identity 候选。
     * 当前轮有显式实体，或 policy 非 USE_PREVIOUS → 禁止。
     */
    public static boolean shouldAllowHistoricalAnchorSources(String anchorPolicy, boolean hasExplicitEntity) {
        if (hasExplicitEntity) {
            return false;
        }
        return isUsePreviousAnchor(anchorPolicy);
    }

    /**
     * rewriteUsedAnchors 是否允许写入 ResolvedContext（Context 层第一道过滤）。
     */
    public static boolean shouldAcceptRewriteUsedAnchors(
            AiQuerySemanticParseResult sem, SemanticCapabilityContract contract) {
        if (sem == null || contract == null || !contract.isRequiresAnchor()) {
            return false;
        }
        String anchorType = normalizeEntityType(contract.getAnchorType());
        if (!StringUtils.hasText(anchorType) || "NONE".equals(anchorType)) {
            return false;
        }
        String anchorPolicy = anchorPolicyFromParse(sem);
        if (!isUsePreviousAnchor(anchorPolicy) || isIgnorePreviousAnchor(anchorPolicy)) {
            return false;
        }
        if (AiResultAnchor.ENTITY_TYPE_GOODS.equals(anchorType)
                && hasCurrentTurnExplicitGoodsName(sem)) {
            return false;
        }
        if (AiResultAnchor.ENTITY_TYPE_DISH.equals(anchorType)
                && hasCurrentTurnExplicitDishName(sem)) {
            return false;
        }
        return true;
    }

    /**
     * Context 构建层：是否将 rewrite provenance 投影进 {@link AiResolvedQueryContext}。
     * 当前轮显式实体或 {@code IGNORE_PREVIOUS_ANCHOR} 时整组 rewrite 字段置空。
     */
    public static boolean shouldProjectRewriteAnchorsIntoContext(AiQuerySemanticParseResult sem) {
        if (sem == null || !SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            return false;
        }
        ContractLockedSemanticFrame frame = sem.getContractLockedFrame();
        String contractId = PurchaseLockedSemanticFrameSupport.selectedContractId(frame);
        if (!StringUtils.hasText(contractId)) {
            return false;
        }
        String domain =
                StringUtils.hasText(sem.getSemanticDomain()) ? sem.getSemanticDomain().trim() : null;
        SemanticCapabilityContract contract =
                SemanticContractCatalog.findActiveCapabilityContractById(contractId.trim(), domain);
        return shouldAcceptRewriteUsedAnchors(sem, contract);
    }

    /**
     * 基于 identity repository 返回的 canonical 名称做精确相等（非 contains / 模糊）。
     */
    public static boolean canonicalEntityNamesMatch(String requestedName, String canonicalName) {
        if (!StringUtils.hasText(requestedName) || !StringUtils.hasText(canonicalName)) {
            return false;
        }
        return requestedName.trim().equals(canonicalName.trim());
    }

    public static boolean identityUsedHistoricalAnchorWithExplicitNameConflict(ResolvedEntityIdentity identity) {
        if (identity == null || !StringUtils.hasText(identity.getUserMentionedName())) {
            return false;
        }
        if (identity.getResolutionSource() == null
                || !identity.getResolutionSource().isHistoricalAnchorSource()) {
            return false;
        }
        if (!StringUtils.hasText(identity.getResolvedCanonicalName())) {
            return false;
        }
        return !canonicalEntityNamesMatch(
                identity.getUserMentionedName(), identity.getResolvedCanonicalName());
    }

    private static String normalizeEntityType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimOrNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }
}
