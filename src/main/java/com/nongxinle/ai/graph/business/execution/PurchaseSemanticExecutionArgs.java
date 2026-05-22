package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 采购 Tool execution args 读写：主链只读 {@link AiBusinessToolIds#ARG_PURCHASE_EXECUTION_DETAIL_WANTED}。
 */
public final class PurchaseSemanticExecutionArgs {

    private PurchaseSemanticExecutionArgs() {}

    public static void applyToToolArgs(Map<String, Object> m, com.nongxinle.ai.context.AiResolvedQueryContext ctx) {
        if (m == null || ctx == null) {
            return;
        }
        PurchaseSemanticExecutionIntent intent = PurchaseSemanticExecutionIntentResolver.resolve(ctx);
        if (!intent.isActive()) {
            return;
        }
        m.put(AiBusinessToolIds.ARG_PURCHASE_EXECUTION_INTENT_TYPE, intent.getExecutionIntentType());
        String exec = intent.getExecutionIntentType();
        if (PurchaseSemanticExecutionIntent.EXEC_GOODS_SOURCE_BREAKDOWN.equals(exec)
                || PurchaseSemanticExecutionIntent.EXEC_GOODS_SUPPLIER_BREAKDOWN.equals(exec)
                || PurchaseSemanticExecutionIntent.EXEC_GOODS_SUPPLIER_UNIT_PRICE.equals(exec)) {
            if (!intent.requiresGoodsFocus()) {
                return;
            }
            Integer disGoodsId = parsePositiveInt(intent.getFocusGoodsId());
            String goodsName = intent.getFocusGoodsName();
            if (disGoodsId == null && !StringUtils.hasText(goodsName)) {
                return;
            }
            if (disGoodsId != null) {
                m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_DIS_GOODS_ID, disGoodsId);
            }
            if (StringUtils.hasText(goodsName)) {
                m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_GOODS_NAME, goodsName.trim());
            }
            m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_ENTITY_TYPE, com.nongxinle.ai.dto.business.AiResultAnchor.ENTITY_TYPE_GOODS);
            putExecutionDetailWanted(m, intent.getToolDetailWantedKey());
            return;
        }
        if (PurchaseSemanticExecutionIntent.EXEC_SUPPLIER_ANCHOR_GOODS_LINES.equals(exec)) {
            if (intent.getFocusSupplierId() == null) {
                return;
            }
            m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_SUPPLIER_ID, intent.getFocusSupplierId());
            m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_ENTITY_TYPE, com.nongxinle.ai.dto.business.AiResultAnchor.ENTITY_TYPE_SUPPLIER);
            putExecutionDetailWanted(m, "GOODS_UNIT_PRICE");
            return;
        }
        if (PurchaseSemanticExecutionIntent.EXEC_CHANNEL_GOODS_DETAIL.equals(exec)) {
            putExecutionDetailWanted(m, "GOODS_DETAIL");
        }
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

    public static boolean canApplyGoodsFocus(PurchaseSemanticExecutionIntent intent) {
        if (intent == null || !intent.requiresGoodsFocus()) {
            return false;
        }
        return parsePositiveInt(intent.getFocusGoodsId()) != null
                || StringUtils.hasText(intent.getFocusGoodsName());
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
}
