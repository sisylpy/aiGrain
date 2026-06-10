package com.nongxinle.ai.identity;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.graph.business.execution.EffectiveGoodsAnchor;
import org.springframework.util.StringUtils;

/** 将 {@link ResolvedEntityIdentity} 投影为 legacy {@link EffectiveGoodsAnchor}（展示层只读，PR1 不破坏 DisplayNameSupport）。 */
public final class BusinessEntityIdentityGoodsProjection {

    private static final String SOURCE_CURRENT_TURN = "currentTurn.mentionedGoodsName";
    private static final String SOURCE_PREVIOUS = "previousTurn.resultAnchor";
    private static final String SOURCE_REWRITE = "rewriteInheritedAnchor";

    private BusinessEntityIdentityGoodsProjection() {}

    public static EffectiveGoodsAnchor toEffectiveGoodsAnchor(AiResolvedQueryContext ctx) {
        ResolvedEntityIdentity identity = BusinessEntityIdentityBridge.resolveGoods(ctx);
        return toEffectiveGoodsAnchor(identity);
    }

    public static EffectiveGoodsAnchor toEffectiveGoodsAnchor(ResolvedEntityIdentity identity) {
        if (identity == null || identity.getResolutionStatus() == EntityIdentityResolutionStatus.SKIPPED) {
            return EffectiveGoodsAnchor.empty();
        }
        if (identity.getResolutionStatus() != EntityIdentityResolutionStatus.OK) {
            return EffectiveGoodsAnchor.empty();
        }
        String source = mapSource(identity);
        String goodsName = displayGoodsName(identity);
        return EffectiveGoodsAnchor.builder()
                .goodsName(goodsName)
                .disGoodsId(identity.getResolvedEntityId())
                .source(source)
                .build();
    }

    /** Tool / SQL focus hint：canonical 优先；无 canonical 时用 user mention。 */
    public static String executionGoodsNameHint(ResolvedEntityIdentity identity) {
        if (identity == null || identity.getResolutionStatus() != EntityIdentityResolutionStatus.OK) {
            return null;
        }
        if (StringUtils.hasText(identity.getResolvedCanonicalName())) {
            return identity.getResolvedCanonicalName().trim();
        }
        return trimOrNull(identity.getUserMentionedName());
    }

    public static Integer executionDisGoodsId(ResolvedEntityIdentity identity) {
        if (identity == null || identity.getResolutionStatus() != EntityIdentityResolutionStatus.OK) {
            return null;
        }
        Integer id = identity.getResolvedEntityId();
        return id != null && id > 0 ? id : null;
    }

    private static String displayGoodsName(ResolvedEntityIdentity identity) {
        if (StringUtils.hasText(identity.getUserMentionedName())) {
            return identity.getUserMentionedName().trim();
        }
        if (StringUtils.hasText(identity.getResolvedCanonicalName())) {
            return identity.getResolvedCanonicalName().trim();
        }
        return null;
    }

    private static String mapSource(ResolvedEntityIdentity identity) {
        if (identity.getResolutionSource() == EntityIdentityResolutionSource.INHERITED_PREVIOUS_ANCHOR) {
            return SOURCE_PREVIOUS;
        }
        if (identity.getResolutionSource() == EntityIdentityResolutionSource.REWRITE_INHERITED_ANCHOR) {
            return SOURCE_REWRITE;
        }
        if (StringUtils.hasText(identity.getUserMentionedName())) {
            return SOURCE_CURRENT_TURN;
        }
        if (identity.getResolutionSource() == EntityIdentityResolutionSource.CURRENT_STRUCTURED_ID) {
            return SOURCE_CURRENT_TURN;
        }
        return "identityResolver";
    }

    private static String trimOrNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }
}
