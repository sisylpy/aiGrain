package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.graph.business.execution.EffectiveGoodsAnchor;
import com.nongxinle.ai.graph.business.execution.EffectiveGoodsAnchorSupport;
import com.nongxinle.ai.semantic.contract.SemanticContractAnchorInheritanceSupport;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * GOODS 卡片 / AnswerPlan 展示名 SSOT：当前轮显式实体优先；Tool payload 次之；
 * 上一轮 anchor 仅在本轮无显式商品且 {@code USE_PREVIOUS_ANCHOR} 时采用。
 */
public final class GoodsEntityDisplayNameSupport {

    private static final String SOURCE_PREVIOUS = "previousTurn.resultAnchor";

    private GoodsEntityDisplayNameSupport() {}

    public static boolean hasCurrentTurnExplicitGoodsMention(AiResolvedQueryContext rq) {
        return StringUtils.hasText(currentTurnExplicitGoodsName(rq));
    }

    public static String currentTurnExplicitGoodsName(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQuerySemanticParse() == null) {
            return null;
        }
        String mention = rq.getQuerySemanticParse().effectiveMentionedGoodsName();
        return StringUtils.hasText(mention) ? mention.trim() : null;
    }

    public static boolean allowsPreviousGoodsAnchor(AiResolvedQueryContext rq) {
        if (hasCurrentTurnExplicitGoodsMention(rq)) {
            return false;
        }
        if (rq == null || rq.getQuerySemanticParse() == null) {
            return false;
        }
        return SemanticContractAnchorInheritanceSupport.isUsePreviousAnchorPolicy(
                rq.getQuerySemanticParse());
    }

    /** AnswerPlan 构建：semantic → tool {@code goodsName} → 允许范围内的 anchor。 */
    public static String resolveDisplayGoodsName(AiResolvedQueryContext rq, Map<String, Object> toolCore) {
        String fromSemantic = currentTurnExplicitGoodsName(rq);
        if (StringUtils.hasText(fromSemantic)) {
            return fromSemantic;
        }
        String fromTool = str(toolCore == null ? null : toolCore.get("goodsName"));
        if (StringUtils.hasText(fromTool)) {
            return fromTool;
        }
        return resolveGoodsNameFromAnchorIfAllowed(rq);
    }

    public static Integer resolveDisplayDisGoodsId(AiResolvedQueryContext rq, Map<String, Object> toolCore) {
        Integer fromTool = parseIntLoose(toolCore == null ? null : toolCore.get("disGoodsId"));
        if (fromTool != null) {
            return fromTool;
        }
        return resolveDisGoodsIdFromAnchorIfAllowed(rq);
    }

    /** Card wire：semantic → plan 字段 → 允许范围内的 anchor。 */
    public static String resolveDisplayGoodsNameForPlan(AiResolvedQueryContext rq, String planGoodsName) {
        String fromSemantic = currentTurnExplicitGoodsName(rq);
        if (StringUtils.hasText(fromSemantic)) {
            return fromSemantic;
        }
        if (StringUtils.hasText(planGoodsName)) {
            return planGoodsName.trim();
        }
        return resolveGoodsNameFromAnchorIfAllowed(rq);
    }

    public static Integer resolveDisplayDisGoodsIdForPlan(AiResolvedQueryContext rq, Integer planDisGoodsId) {
        if (planDisGoodsId != null && planDisGoodsId > 0) {
            return planDisGoodsId;
        }
        return resolveDisGoodsIdFromAnchorIfAllowed(rq);
    }

    static boolean isCurrentTurnGoodsAnchorSource(String source) {
        if (!StringUtils.hasText(source)) {
            return false;
        }
        return source.startsWith("currentTurn")
                || "rewriteInheritedAnchor".equals(source)
                || "identityResolver".equals(source);
    }

    private static String resolveGoodsNameFromAnchorIfAllowed(AiResolvedQueryContext rq) {
        EffectiveGoodsAnchor anchor = EffectiveGoodsAnchorSupport.resolve(rq);
        if (!anchor.hasGoodsName()) {
            return null;
        }
        if (isCurrentTurnGoodsAnchorSource(anchor.getSource())) {
            return anchor.getGoodsName().trim();
        }
        if (SOURCE_PREVIOUS.equals(anchor.getSource()) && allowsPreviousGoodsAnchor(rq)) {
            return anchor.getGoodsName().trim();
        }
        return null;
    }

    private static Integer resolveDisGoodsIdFromAnchorIfAllowed(AiResolvedQueryContext rq) {
        EffectiveGoodsAnchor anchor = EffectiveGoodsAnchorSupport.resolve(rq);
        if (!anchor.hasDisGoodsId()) {
            return null;
        }
        if (isCurrentTurnGoodsAnchorSource(anchor.getSource())) {
            return anchor.getDisGoodsId();
        }
        if (SOURCE_PREVIOUS.equals(anchor.getSource()) && allowsPreviousGoodsAnchor(rq)) {
            return anchor.getDisGoodsId();
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static Integer parseIntLoose(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            int v = n.intValue();
            return v > 0 ? v : null;
        }
        try {
            int v = Integer.parseInt(o.toString().trim());
            return v > 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }
}
