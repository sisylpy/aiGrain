package com.nongxinle.ai.harness;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.graph.business.execution.PurchaseSemanticExecutionIntent;
import com.nongxinle.ai.graph.business.execution.PurchaseSemanticExecutionIntentResolver;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractValidationDebug;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 锚 execution / 语义帧 debug 摘要（Harness 层，不改业务语义）。
 * P4-F：主输出 {@code executionIntentType} / {@code executionDetailWanted} / {@code anchorPolicy} / focus 实体键。
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

        AiQuerySemanticParseResult.SemanticSlotsPart slots = semanticSlots(ctx);
        out.put("anchorPolicy", slots == null ? null : AiHarnessSummaryUtils.blankToNull(slots.getAnchorPolicy()));
        out.put(
                "semanticSlotsDetailWanted",
                slots == null ? null : AiHarnessSummaryUtils.blankToNull(slots.getDetailWanted()));

        PurchaseSemanticExecutionIntent exec = PurchaseSemanticExecutionIntentResolver.resolve(ctx);
        out.put("executionIntentType", AiHarnessSummaryUtils.blankToNull(exec.getExecutionIntentType()));
        out.put("executionDetailWanted", AiHarnessSummaryUtils.blankToNull(exec.getDetailWanted()));
        out.put("focusEntityType", AiHarnessSummaryUtils.blankToNull(exec.getAnchorType()));
        out.put("focusEntityName", AiHarnessSummaryUtils.blankToNull(exec.getFocusGoodsName()));
        out.put("focusEntityId", AiHarnessSummaryUtils.blankToNull(exec.getFocusGoodsId()));
        if (exec.getFocusSupplierId() != null) {
            out.put("focusSupplierId", exec.getFocusSupplierId());
        }

        SemanticContractValidationDebug validation = ctx.getSemanticContractValidation();
        out.put(
                "matchedContractId",
                validation == null
                        ? AiHarnessSummaryUtils.blankToNull(exec.getMatchedContractId())
                        : AiHarnessSummaryUtils.blankToNull(validation.getMatchedContractId()));
        out.put("contractExecutionQueryMode", resolveContractExecutionQueryMode(exec));
        out.put("slotDetailWanted", slots == null ? null : AiHarnessSummaryUtils.blankToNull(slots.getDetailWanted()));
        out.put("framePlanType", null);
        out.put("framePurchaseSourceType", null);

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

    static void reconcileFocusGoodsEntityIdForHarness(
            LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx, AiRunState state) {
        String entityType = AiHarnessSummaryUtils.blankToNull(
                AiHarnessSummaryUtils.stringifyHarnessDbg(out.get("focusEntityType")));
        if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(entityType)) {
            return;
        }
        if (StringUtils.hasText(AiHarnessSummaryUtils.stringifyHarnessDbg(out.get("focusEntityId")))) {
            return;
        }
        String resolved = resolveFocusGoodsEntityIdForHarness(out, ctx, state);
        if (StringUtils.hasText(resolved)) {
            out.put("focusEntityId", resolved);
        }
    }

    private static AiQuerySemanticParseResult.SemanticSlotsPart semanticSlots(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getQuerySemanticParse() == null) {
            return null;
        }
        return ctx.getQuerySemanticParse().getSemanticSlots();
    }

    private static String resolveContractExecutionQueryMode(PurchaseSemanticExecutionIntent exec) {
        if (exec == null || !StringUtils.hasText(exec.getExecutionIntentType())) {
            return null;
        }
        return switch (exec.getExecutionIntentType()) {
            case PurchaseSemanticExecutionIntent.EXEC_GOODS_SOURCE_BREAKDOWN -> "goods_source_breakdown";
            case PurchaseSemanticExecutionIntent.EXEC_GOODS_SUPPLIER_BREAKDOWN -> "goods_supplier_breakdown";
            case PurchaseSemanticExecutionIntent.EXEC_GOODS_SUPPLIER_UNIT_PRICE -> "goods_anchor_supplier_unit_price";
            case PurchaseSemanticExecutionIntent.EXEC_SUPPLIER_ANCHOR_GOODS_LINES -> "supplier_anchor_goods_lines";
            case PurchaseSemanticExecutionIntent.EXEC_CHANNEL_GOODS_DETAIL -> "supplier_channel_goods_detail";
            default -> exec.getExecutionIntentType();
        };
    }

    private static String resolveFocusGoodsEntityIdForHarness(
            LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx, AiRunState state) {
        PurchaseSemanticExecutionIntent exec = PurchaseSemanticExecutionIntentResolver.resolve(ctx);
        String fromExec = AiHarnessSummaryUtils.blankToNull(exec.getFocusGoodsId());
        if (StringUtils.hasText(fromExec)) {
            return fromExec;
        }
        String fromExecution =
                AiHarnessSummaryUtils.harnessEntityIdString(out.get("purchaseGoodsAnchorExecutionTargetGoodsId"));
        if (StringUtils.hasText(fromExecution)) {
            return fromExecution;
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
