package com.nongxinle.ai.identity;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 供 static execution 链读取 Spring {@link BusinessEntityIdentityResolver}。 */
@Component
@RequiredArgsConstructor
public class BusinessEntityIdentityBridge {

    private static BusinessEntityIdentityResolver resolver;

    private final BusinessEntityIdentityResolver identityResolver;

    @PostConstruct
    void register() {
        resolver = identityResolver;
    }

    public static ResolvedEntityIdentity resolveGoods(AiResolvedQueryContext ctx) {
        if (resolver == null) {
            return ResolvedEntityIdentity.skipped(EntityIdentityType.GOODS);
        }
        return resolver.resolveGoods(ctx);
    }

    public static ResolvedEntityIdentity resolveGoods(AiResolvedQueryContext ctx, Integer distributerIdHint) {
        if (resolver == null) {
            return ResolvedEntityIdentity.skipped(EntityIdentityType.GOODS);
        }
        return resolver.resolveGoods(ctx, distributerIdHint);
    }

    public static void appendGoodsIdentityHarnessDebug(
            AiResolvedQueryContext ctx, Map<String, Object> debug) {
        if (debug == null) {
            return;
        }
        ResolvedEntityIdentity identity = resolveGoods(ctx);
        appendGoodsIdentityHarnessDebug(identity, debug);
    }

    public static void appendGoodsIdentityHarnessDebug(
            ResolvedEntityIdentity identity, Map<String, Object> debug) {
        if (debug == null || identity == null) {
            return;
        }
        if (identity.getUserMentionedName() != null) {
            debug.put("userMentionedGoodsName", identity.getUserMentionedName());
        }
        if (identity.getResolvedCanonicalName() != null) {
            debug.put("resolvedCanonicalGoodsName", identity.getResolvedCanonicalName());
        }
        if (identity.getResolvedEntityId() != null) {
            debug.put("resolvedDisGoodsId", identity.getResolvedEntityId());
        }
        if (identity.getResolutionStatus() != null) {
            debug.put("entityIdentityResolutionStatus", identity.getResolutionStatus().name());
        }
        if (identity.getResolutionSource() != null) {
            debug.put("entityIdentityResolutionSource", identity.getResolutionSource().name());
        }
        if (identity.getAnchorPolicyApplied() != null) {
            debug.put("entityIdentityAnchorPolicyApplied", identity.getAnchorPolicyApplied());
        }
        if (identity.getClarificationMessage() != null) {
            debug.put("entityIdentityClarificationMessage", identity.getClarificationMessage());
        }
        if (identity.getCandidates() != null && !identity.getCandidates().isEmpty()) {
            debug.put("entityIdentityCandidates", identity.getCandidates());
        }
        if (identity.getDebugTrace() != null && !identity.getDebugTrace().isEmpty()) {
            debug.putAll(identity.getDebugTrace());
        }
    }
}
