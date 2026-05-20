package com.nongxinle.ai.harness;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Follow-up 目标实体 id 补齐（Harness 摘要层，不改业务语义）。
 */
final class AiHarnessFollowUpSummaryAppender {

    private AiHarnessFollowUpSummaryAppender() {
    }

    static void appendFollowUpFields(LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx) {
        AiFollowUpResolution fur = ctx.getFollowUpResolution();
        if (fur != null) {
            out.put("followUp", fur.isFollowUp());
            out.put("followUpType", AiHarnessSummaryUtils.blankToNull(fur.getFollowUpType()));
        } else {
            out.put("followUp", false);
            out.put("followUpType", null);
        }
        out.put("followUpAction", AiHarnessSummaryUtils.blankToNull(ctx.getFollowUpAction()));
        out.put("followUpTargetEntityType", AiHarnessSummaryUtils.blankToNull(ctx.getFollowUpTargetEntityType()));
        out.put("followUpTargetEntityName", AiHarnessSummaryUtils.blankToNull(ctx.getFollowUpTargetEntityName()));
        out.put("followUpTargetEntityId", AiHarnessSummaryUtils.blankToNull(ctx.getFollowUpTargetEntityId()));
        out.put("followUpDetailWanted", AiHarnessSummaryUtils.blankToNull(ctx.getFollowUpDetailWanted()));
        out.put("followUpSourcePlanType", AiHarnessSummaryUtils.blankToNull(ctx.getFollowUpSourcePlanType()));
        Map<String, Object> capDbg = ctx.getBusinessFollowUpCapabilityDebug();
        if (capDbg != null && !capDbg.isEmpty()) {
            out.put("matchedCapabilityId", capDbg.get("matchedCapabilityId"));
            out.put("followUpRegistryQueryMode", capDbg.get("followUpRegistryQueryMode"));
            out.put("framePlanType", capDbg.get("framePlanType"));
            out.put("framePurchaseSourceType", capDbg.get("framePurchaseSourceType"));
            out.put("slotDetailWanted", capDbg.get("slotDetailWanted"));
            out.put("businessFollowUpCapabilityDebug", new LinkedHashMap<>(capDbg));
        } else {
            out.put("matchedCapabilityId", null);
            out.put("followUpRegistryQueryMode", null);
            out.put("framePlanType", null);
            out.put("framePurchaseSourceType", null);
            out.put("slotDetailWanted", null);
            out.put("businessFollowUpCapabilityDebug", null);
        }
        Integer prevResultAnchorsCount = null;
        AiConversationTurnMemory prev = ctx.getPreviousTurn();
        if (prev != null) {
            LinkedHashMap<String, Object> p = new LinkedHashMap<>();
            p.put("lastIntentCode", AiHarnessSummaryUtils.blankToNull(prev.getLastIntentCode()));
            p.put("lastPathCode", AiHarnessSummaryUtils.blankToNull(prev.getLastPathCode()));
            p.put("lastStructuredIntentDetail", AiHarnessSummaryUtils.blankToNull(prev.getLastStructuredIntentDetail()));
            String prevSid = prev.getLastStructuredIntentDetail();
            if (StringUtils.hasText(prevSid)
                    && AiQuerySemanticLexicon.isStructuredStockReduceDetail(prevSid)) {
                String prevCode = AiQuerySemanticLexicon.toStructuredIntentDetailDebugCode(prevSid);
                if (prevCode != null) {
                    p.put("lastStockReduceType", prevCode);
                }
            }
            p.put("lastPurchaseSourceType", AiHarnessSummaryUtils.blankToNull(prev.getLastPurchaseSourceType()));
            p.put("lastStartDate", AiHarnessSummaryUtils.blankToNull(prev.getLastStartDate()));
            p.put("lastEndDate", AiHarnessSummaryUtils.blankToNull(prev.getLastEndDate()));
            p.put("lastTimeLabel", AiHarnessSummaryUtils.blankToNull(prev.getLastTimeLabel()));
            p.put("lastScopeType", AiHarnessSummaryUtils.blankToNull(prev.getLastScopeType()));
            p.put("lastMentionedDishName", AiHarnessSummaryUtils.blankToNull(prev.getLastMentionedDishName()));
            if (prev.getLastResultAnchors() != null && !prev.getLastResultAnchors().isEmpty()) {
                prevResultAnchorsCount = prev.getLastResultAnchors().size();
                p.put("resultAnchorsCount", prevResultAnchorsCount);
                LinkedHashSet<String> anchorTy = new LinkedHashSet<>();
                for (AiResultAnchor ax : prev.getLastResultAnchors()) {
                    if (ax != null && StringUtils.hasText(ax.getEntityType())) {
                        anchorTy.add(ax.getEntityType().trim());
                    }
                }
                if (!anchorTy.isEmpty()) {
                    p.put("resultAnchorTypes", new ArrayList<>(anchorTy));
                } else {
                    p.put("resultAnchorTypes", null);
                }
            } else {
                p.put("resultAnchorsCount", null);
                p.put("resultAnchorTypes", null);
            }
            out.put("previousTurnSummary", p);
        } else {
            out.put("previousTurnSummary", null);
        }
        out.put("resultAnchorsCount", prevResultAnchorsCount);
    }

    static void reconcileFollowUpTargetEntityIdForHarness(
            LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx, AiRunState state) {
        String entityType = AiHarnessSummaryUtils.blankToNull(
                AiHarnessSummaryUtils.stringifyHarnessDbg(out.get("followUpTargetEntityType")));
        if (entityType == null && ctx != null) {
            entityType = AiHarnessSummaryUtils.blankToNull(ctx.getFollowUpTargetEntityType());
        }
        if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(entityType)) {
            return;
        }
        if (StringUtils.hasText(AiHarnessSummaryUtils.stringifyHarnessDbg(out.get("followUpTargetEntityId")))) {
            return;
        }
        String resolved = resolveFollowUpTargetGoodsEntityIdForHarness(out, ctx, state);
        if (StringUtils.hasText(resolved)) {
            out.put("followUpTargetEntityId", resolved);
        }
    }

    private static String resolveFollowUpTargetGoodsEntityIdForHarness(
            LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx, AiRunState state) {
        if (ctx != null && StringUtils.hasText(ctx.getFollowUpTargetEntityId())) {
            return ctx.getFollowUpTargetEntityId().trim();
        }
        String fromDrilldown = AiHarnessSummaryUtils.harnessEntityIdString(out.get("purchaseGoodsDrilldownTargetGoodsId"));
        if (StringUtils.hasText(fromDrilldown)) {
            return fromDrilldown;
        }
        if (state != null && state.getPurchaseAnswerPlan() != null) {
            String fromPlan = uniqueGoodsAnchorEntityId(state.getPurchaseAnswerPlan().getResultAnchors());
            if (StringUtils.hasText(fromPlan)) {
                return fromPlan;
            }
        }
        AiConversationTurnMemory prev = ctx != null ? ctx.getPreviousTurn() : null;
        if (prev != null && prev.getLastResultAnchors() != null) {
            String fromPrev = uniqueGoodsAnchorEntityId(prev.getLastResultAnchors());
            if (StringUtils.hasText(fromPrev)) {
                return fromPrev;
            }
        }
        return null;
    }

    private static String uniqueGoodsAnchorEntityId(List<?> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return null;
        }
        String candidate = null;
        int goodsWithId = 0;
        for (Object o : anchors) {
            if (!(o instanceof AiResultAnchor ax)) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(
                    AiHarnessSummaryUtils.blankToNull(ax.getEntityType()))) {
                continue;
            }
            String eid = AiHarnessSummaryUtils.blankToNull(ax.getEntityId());
            if (!StringUtils.hasText(eid)) {
                continue;
            }
            goodsWithId++;
            candidate = eid.trim();
        }
        return goodsWithId == 1 ? candidate : null;
    }
}
