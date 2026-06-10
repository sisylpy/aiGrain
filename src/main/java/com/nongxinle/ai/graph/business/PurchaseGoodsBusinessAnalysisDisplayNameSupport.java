package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.graph.business.execution.EffectiveGoodsAnchor;
import com.nongxinle.ai.graph.business.execution.EffectiveGoodsAnchorSupport;
import org.springframework.util.StringUtils;

import java.util.Map;

/** 采购经营分析卡展示商品名：对齐当前轮 semantic / Tool payload，禁止继承上一轮 anchor 名。 */
public final class PurchaseGoodsBusinessAnalysisDisplayNameSupport {

    private PurchaseGoodsBusinessAnalysisDisplayNameSupport() {}

    /**
     * 卡片 title / {@code payload.goodsName} 权威展示名：
     * 1. 当前轮 {@code semanticSlots.mentionedGoodsName}
     * 2. Tool payload {@code inventoryCover.goodsName}（disGoodsId 映射）
     * 3. Tool payload 顶层 {@code goodsName}
     * 4. {@code purchaseSourceBreakdown.goodsName}
     * 5. 仅当 anchor 来源为当前轮时采用 anchor
     */
    public static String resolveDisplayGoodsName(
            AiResolvedQueryContext rq, Map<String, Object> core, Map<String, Object> cover) {
        String fromSemantic = resolveCurrentTurnMentionedGoodsName(rq);
        if (StringUtils.hasText(fromSemantic)) {
            return fromSemantic.trim();
        }
        String fromCover = str(cover == null ? null : cover.get("goodsName"));
        if (StringUtils.hasText(fromCover)) {
            return fromCover;
        }
        String fromCore = str(core == null ? null : core.get("goodsName"));
        if (StringUtils.hasText(fromCore)) {
            return fromCore;
        }
        String fromSource = str(sourceGoodsName(core));
        if (StringUtils.hasText(fromSource)) {
            return fromSource;
        }
        EffectiveGoodsAnchor anchor = EffectiveGoodsAnchorSupport.resolve(rq);
        if (anchor != null
                && GoodsEntityDisplayNameSupport.isCurrentTurnGoodsAnchorSource(anchor.getSource())
                && anchor.hasGoodsName()) {
            return anchor.getGoodsName().trim();
        }
        return null;
    }

    /** 从 AnswerPlan 投影卡片时二次对齐，避免 plan 构建与 wire 之间残留旧名。 */
    public static String resolveDisplayGoodsNameForPlan(AiResolvedQueryContext rq, String planGoodsName) {
        String fromSemantic = resolveCurrentTurnMentionedGoodsName(rq);
        if (StringUtils.hasText(fromSemantic)) {
            return fromSemantic.trim();
        }
        if (StringUtils.hasText(planGoodsName)) {
            return planGoodsName.trim();
        }
        EffectiveGoodsAnchor anchor = EffectiveGoodsAnchorSupport.resolve(rq);
        if (anchor != null
                && GoodsEntityDisplayNameSupport.isCurrentTurnGoodsAnchorSource(anchor.getSource())
                && anchor.hasGoodsName()) {
            return anchor.getGoodsName().trim();
        }
        return null;
    }

    public static Integer resolveDisplayDisGoodsId(
            AiResolvedQueryContext rq, Map<String, Object> core, Map<String, Object> cover) {
        Integer fromCore = parseIntLoose(core == null ? null : core.get("disGoodsId"));
        if (fromCore != null) {
            return fromCore;
        }
        Integer fromCover = parseIntLoose(cover == null ? null : cover.get("disGoodsId"));
        if (fromCover != null) {
            return fromCover;
        }
        EffectiveGoodsAnchor anchor = EffectiveGoodsAnchorSupport.resolve(rq);
        if (anchor != null
                && GoodsEntityDisplayNameSupport.isCurrentTurnGoodsAnchorSource(anchor.getSource())
                && anchor.hasDisGoodsId()) {
            return anchor.getDisGoodsId();
        }
        return null;
    }

    private static String resolveCurrentTurnMentionedGoodsName(AiResolvedQueryContext rq) {
        return GoodsEntityDisplayNameSupport.currentTurnExplicitGoodsName(rq);
    }

    @SuppressWarnings("unchecked")
    private static String sourceGoodsName(Map<String, Object> core) {
        if (core == null || !(core.get("purchaseSourceBreakdown") instanceof Map<?, ?> m)) {
            return null;
        }
        return str(((Map<String, Object>) m).get("goodsName"));
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
