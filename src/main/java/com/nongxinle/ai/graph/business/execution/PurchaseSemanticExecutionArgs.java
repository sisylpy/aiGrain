package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.identity.BusinessEntityIdentityGoodsProjection;
import com.nongxinle.ai.identity.ResolvedEntityIdentity;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.frame.ContractLockedSemanticFrame;
import com.nongxinle.ai.semantic.frame.PurchaseLockedSemanticFrameSupport;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 采购 Tool execution args：只读 LockedFrame + ResolvedEntityIdentity + RequiresAnchor gate。
 */
public final class PurchaseSemanticExecutionArgs {

    private PurchaseSemanticExecutionArgs() {}

    public static void applyToToolArgs(Map<String, Object> m, AiResolvedQueryContext ctx) {
        if (m == null || ctx == null) {
            return;
        }
        if (!SemanticContractCompletionEngine.isContractLockedParse(ctx.getQuerySemanticParse())) {
            return;
        }
        ContractLockedSemanticFrame frame = PurchaseLockedSemanticFrameSupport.lockedFrame(ctx);
        if (frame == null) {
            return;
        }
        PurchaseSemanticExecutionIntent projected = PurchaseSemanticExecutionIntentResolver.project(ctx);
        if (!projected.isActive()) {
            return;
        }
        String execType = projected.getExecutionIntentType();
        m.put(AiBusinessToolIds.ARG_PURCHASE_EXECUTION_INTENT_TYPE, execType);
        putExecutionDetailWanted(m, projected.getToolDetailWantedKey());

        if (!projected.requiresGoodsFocus()) {
            applyNonGoodsFocus(m, projected);
            return;
        }

        RequiresAnchorExecutionGateSupport.Decision gate = RequiresAnchorExecutionGateSupport.evaluate(ctx);
        if (gate.blocksToolExecution()) {
            markAnchorIdentityBlocked(m, gate, projected);
            return;
        }

        ResolvedEntityIdentity identity = ctx.getResolvedGoodsIdentity();
        Integer disGoodsId = BusinessEntityIdentityGoodsProjection.executionDisGoodsId(identity);
        String goodsName = BusinessEntityIdentityGoodsProjection.executionGoodsNameHint(identity);
        if (disGoodsId == null && !StringUtils.hasText(goodsName)) {
            markAnchorIdentityBlocked(
                    m,
                    RequiresAnchorExecutionGateSupport.Decision.builder()
                            .outcome(RequiresAnchorExecutionGateSupport.Outcome.BLOCK)
                            .blockReason(RequiresAnchorExecutionGateSupport.BLOCK_GOODS_IDENTITY_MISSING)
                            .build(),
                    projected);
            return;
        }
        if (disGoodsId != null) {
            m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_DIS_GOODS_ID, disGoodsId);
        }
        if (StringUtils.hasText(goodsName)) {
            m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_GOODS_NAME, goodsName.trim());
        }
        m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_ENTITY_TYPE, AiResultAnchor.ENTITY_TYPE_GOODS);
    }

    /** 主链读 {@link AiBusinessToolIds#ARG_PURCHASE_EXECUTION_DETAIL_WANTED}。 */
    public static String readExecutionDetailWanted(Map<String, Object> args) {
        if (args == null) {
            return null;
        }
        Object primary = args.get(AiBusinessToolIds.ARG_PURCHASE_EXECUTION_DETAIL_WANTED);
        if (primary != null && StringUtils.hasText(primary.toString())) {
            return primary.toString().trim();
        }
        return null;
    }

    public static boolean isAnchorIdentityBlocked(Map<String, Object> args) {
        return args != null && Boolean.TRUE.equals(args.get(AiBusinessToolIds.ARG_PURCHASE_ANCHOR_IDENTITY_BLOCKED));
    }

    public static boolean requiresGoodsFocusExecution(Map<String, Object> args) {
        if (args == null) {
            return false;
        }
        String execType = str(args.get(AiBusinessToolIds.ARG_PURCHASE_EXECUTION_INTENT_TYPE));
        return PurchaseSemanticExecutionIntent.requiresGoodsFocusExecType(execType);
    }

    public static boolean canApplyGoodsFocus(PurchaseSemanticExecutionIntent intent) {
        if (intent == null || !intent.requiresGoodsFocus()) {
            return false;
        }
        return parsePositiveInt(intent.getFocusGoodsId()) != null
                || StringUtils.hasText(intent.getFocusGoodsName());
    }

    private static void applyNonGoodsFocus(Map<String, Object> m, PurchaseSemanticExecutionIntent projected) {
        String execType = projected.getExecutionIntentType();
        if (PurchaseSemanticExecutionIntent.EXEC_SUPPLIER_ANCHOR_GOODS_LINES.equals(execType)) {
            if (projected.getFocusSupplierId() == null) {
                return;
            }
            m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_SUPPLIER_ID, projected.getFocusSupplierId());
            m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_ENTITY_TYPE, AiResultAnchor.ENTITY_TYPE_SUPPLIER);
            putExecutionDetailWanted(m, "GOODS_UNIT_PRICE");
            return;
        }
        if (PurchaseSemanticExecutionIntent.EXEC_CHANNEL_GOODS_DETAIL.equals(execType)) {
            putExecutionDetailWanted(m, "GOODS_DETAIL");
            return;
        }
        if (PurchaseSemanticExecutionIntent.EXEC_PERIOD_GOODS_LIST.equals(execType)) {
            putExecutionDetailWanted(m, "PERIOD_GOODS_LIST");
        }
    }

    private static void markAnchorIdentityBlocked(
            Map<String, Object> m,
            RequiresAnchorExecutionGateSupport.Decision gate,
            PurchaseSemanticExecutionIntent projected) {
        m.put(AiBusinessToolIds.ARG_PURCHASE_ANCHOR_IDENTITY_BLOCKED, Boolean.TRUE);
        if (gate != null && StringUtils.hasText(gate.getBlockReason())) {
            m.put(AiBusinessToolIds.ARG_PURCHASE_ANCHOR_IDENTITY_BLOCK_REASON, gate.getBlockReason().trim());
        }
        m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_ENTITY_TYPE, AiResultAnchor.ENTITY_TYPE_GOODS);
        if (gate != null && StringUtils.hasText(gate.getRequestedEntityName())) {
            m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_GOODS_NAME, gate.getRequestedEntityName().trim());
        } else if (StringUtils.hasText(projected.getFocusGoodsName())) {
            m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_GOODS_NAME, projected.getFocusGoodsName().trim());
        }
        putExecutionDetailWanted(m, projected.getToolDetailWantedKey());
    }

    private static void putExecutionDetailWanted(Map<String, Object> m, String detailWanted) {
        if (!StringUtils.hasText(detailWanted)) {
            return;
        }
        String dw = detailWanted.trim();
        m.put(AiBusinessToolIds.ARG_PURCHASE_EXECUTION_DETAIL_WANTED, dw);
    }

    private static Integer parsePositiveInt(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }
}
