package com.nongxinle.ai.graph.business.execution;

import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

/**
 * 采购执行层统一意图（P4-B）：contract-driven execution 主链（matchedContractId + semanticSlots + resultAnchors → executionIntentType / executionDetailWanted）。
 */
@Value
@Builder
public class PurchaseSemanticExecutionIntent {

    public static final String EXEC_NONE = "NONE";
    public static final String EXEC_GOODS_SOURCE_BREAKDOWN = "GOODS_SOURCE_BREAKDOWN";
    public static final String EXEC_GOODS_SUPPLIER_BREAKDOWN = "GOODS_SUPPLIER_BREAKDOWN";
    public static final String EXEC_GOODS_SUPPLIER_UNIT_PRICE = "GOODS_SUPPLIER_UNIT_PRICE";
    /** D-13.1：供货商排行锚 → 商品明细行。 */
    public static final String EXEC_SUPPLIER_ANCHOR_GOODS_LINES = "SUPPLIER_ANCHOR_GOODS_LINES";
    /** 渠道 overview 追问 GOODS_DETAIL（无 GOODS 实体锚）。 */
    public static final String EXEC_CHANNEL_GOODS_DETAIL = "CHANNEL_GOODS_DETAIL";
    /** 时间窗内采购原料/商品明细清单（无锚点；contract {@code purchase.period_goods_list}）。 */
    public static final String EXEC_PERIOD_GOODS_LIST = "PERIOD_GOODS_LIST";
    /** GOODS 锚原料采购经营分析（contract {@code purchase.goods_business_analysis.v1}）。 */
    public static final String EXEC_GOODS_BUSINESS_ANALYSIS = "GOODS_BUSINESS_ANALYSIS";

    public static PurchaseSemanticExecutionIntent none() {
        return PurchaseSemanticExecutionIntent.builder()
                .executionIntentType(EXEC_NONE)
                .anchorResolved(false)
                .build();
    }

    String matchedContractId;
    String wire;
    String queryObject;
    String operation;
    String detailWanted;
    String sourceFacet;
    String answerPlanType;
    String focusGoodsId;
    String focusGoodsName;
    Integer focusSupplierId;
    String anchorType;
    boolean anchorResolved;
    String executionIntentType;
    /** 写入 Tool {@code executionDetailWanted} 契约键（由 {@link #getToolDetailWantedKey()} 映射）。 */
    String toolDetailWantedKey;
    /** P2-J debug：{@code contract_entry} / {@code unresolved}；非 Matrix 推导成功标记。 */
    String resolutionSource;

    public boolean isActive() {
        return StringUtils.hasText(executionIntentType) && !EXEC_NONE.equals(executionIntentType);
    }

    public boolean requiresGoodsFocus() {
        return requiresGoodsFocusExecType(executionIntentType);
    }

    public static boolean requiresGoodsFocusExecType(String executionIntentType) {
        return EXEC_GOODS_SOURCE_BREAKDOWN.equals(executionIntentType)
                || EXEC_GOODS_SUPPLIER_BREAKDOWN.equals(executionIntentType)
                || EXEC_GOODS_SUPPLIER_UNIT_PRICE.equals(executionIntentType)
                || EXEC_GOODS_BUSINESS_ANALYSIS.equals(executionIntentType);
    }
}
