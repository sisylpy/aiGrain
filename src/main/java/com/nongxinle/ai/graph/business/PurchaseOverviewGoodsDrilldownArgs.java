package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * D-13：{@code purchase_source_goods_query} 下追问时，向 {@code purchase_overview} 注入聚焦参数。
 * <p>两条独立协议：
 * <ul>
 *   <li>D-13.4：GOODS 锚 + {@code detailWanted=SUPPLIER_UNIT_PRICE} → 按商品查各供应商行；</li>
 *   <li>Phase2-B：GOODS 锚 + {@code detailWanted=SUPPLIER_BREAKDOWN} → 同上供应商聚合行（Tool 仍用 SUPPLIER_UNIT_PRICE 键）；</li>
 *   <li>D-13.1：SUPPLIER 锚 + {@code detailWanted=GOODS_UNIT_PRICE} → 按供应商查各商品行。</li>
 *   <li>Phase2-A：GOODS 锚 + {@code detailWanted=SOURCE_BREAKDOWN} + {@code SOURCE_ALL} → 按采购记录行 legacy 桶拆自采/供货商/其它；</li>
 * </ul>
 * 无上一锚点所需 ID 时不写键，避免误过滤。
 */
public final class PurchaseOverviewGoodsDrilldownArgs {

    private PurchaseOverviewGoodsDrilldownArgs() {
    }

    public static void putIntoToolArgsIfApplicable(Map<String, Object> m, AiResolvedQueryContext purCtx) {
        if (m == null || purCtx == null) {
            return;
        }
        AiResolvedQueryIntent qi = purCtx.getQueryIntent();
        if (qi == null) {
            return;
        }
        String wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(qi.getStructuredIntentDetail());
        if (!AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(wire)) {
            return;
        }
        if (tryPutGoodsSourceBreakdownDrilldown(m, purCtx, qi)) {
            return;
        }
        if (tryPutGoodsAnchoredSupplierBreakdownDrilldown(m, purCtx)) {
            return;
        }
        String pstRaw = qi.getPurchaseSourceType();
        if (pstRaw == null || pstRaw.isBlank()) {
            return;
        }
        String pstTrim = pstRaw.trim();
        boolean supplierPst = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equalsIgnoreCase(pstTrim);
        boolean selfPst = AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equalsIgnoreCase(pstTrim);
        if (!supplierPst && !selfPst) {
            return;
        }
        if (!purCtx.isFollowUp()) {
            return;
        }
        if (supplierPst) {
            if (tryPutGoodsAnchoredSupplierUnitPriceDrilldown(m, purCtx)) {
                return;
            }
            tryPutSupplierAnchoredGoodsLinesDrilldown(m, purCtx);
            return;
        }
    }

    /** Phase2-A：商品金额排行 GOODS 锚追问「自采/供货商」拆桶（ALL 口径，不按商品主数据钉死来源）。 */
    private static boolean tryPutGoodsSourceBreakdownDrilldown(
            Map<String, Object> m, AiResolvedQueryContext purCtx, AiResolvedQueryIntent qi) {
        String wanted = purCtx.getFollowUpDetailWanted();
        if (wanted == null || !"SOURCE_BREAKDOWN".equalsIgnoreCase(wanted.trim())) {
            return false;
        }
        if (!purCtx.isFollowUp()) {
            return false;
        }
        String pstRaw = qi.getPurchaseSourceType();
        if (pstRaw == null
                || pstRaw.isBlank()
                || !AiQuerySemanticLexicon.SOURCE_ALL.equalsIgnoreCase(pstRaw.trim())) {
            return false;
        }
        Integer disGoodsId = parsePositiveInt(purCtx.getFollowUpTargetEntityId());
        String goodsName = purCtx.getFollowUpTargetEntityName();
        if (goodsName != null) {
            goodsName = goodsName.trim();
        }
        if (disGoodsId == null) {
            AiConversationTurnMemory prev = purCtx.getPreviousTurn();
            if (prev != null && prev.getLastResultAnchors() != null) {
                for (AiResultAnchor a : prev.getLastResultAnchors()) {
                    if (a == null) {
                        continue;
                    }
                    if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(nullToEmpty(a.getEntityType()))) {
                        continue;
                    }
                    disGoodsId = parsePositiveInt(a.getEntityId());
                    if (!StringUtils.hasText(goodsName)
                            && a.getEntityName() != null
                            && !a.getEntityName().isBlank()) {
                        goodsName = a.getEntityName().trim();
                    }
                    if (disGoodsId != null) {
                        break;
                    }
                }
            }
        }
        boolean stableAnchor = disGoodsId != null || StringUtils.hasText(goodsName);
        if (!stableAnchor) {
            return false;
        }
        if (disGoodsId != null) {
            m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_DIS_GOODS_ID, disGoodsId);
        }
        if (StringUtils.hasText(goodsName)) {
            m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_GOODS_NAME, goodsName);
        }
        m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_ENTITY_TYPE, AiResultAnchor.ENTITY_TYPE_GOODS);
        m.put(AiBusinessToolIds.ARG_PURCHASE_FOLLOW_UP_DETAIL_WANTED, "SOURCE_BREAKDOWN");
        return true;
    }

    /**
     * 商品锚 + {@code SUPPLIER_BREAKDOWN}：注入与 D-13.4 相同的供应商聚合 drilldown 参数（Tool 契约键为 SUPPLIER_UNIT_PRICE）。
     */
    private static boolean tryPutGoodsAnchoredSupplierBreakdownDrilldown(
            Map<String, Object> m, AiResolvedQueryContext purCtx) {
        String wanted = purCtx.getFollowUpDetailWanted();
        if (wanted == null || !AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equalsIgnoreCase(wanted.trim())) {
            return false;
        }
        if (!purCtx.isFollowUp()) {
            return false;
        }
        return putGoodsAnchoredSupplierLinesDrilldownFocus(m, purCtx, "SUPPLIER_UNIT_PRICE");
    }

    private static boolean tryPutGoodsAnchoredSupplierUnitPriceDrilldown(
            Map<String, Object> m, AiResolvedQueryContext purCtx) {
        String wanted = purCtx.getFollowUpDetailWanted();
        if (wanted == null || !"SUPPLIER_UNIT_PRICE".equalsIgnoreCase(wanted.trim())) {
            return false;
        }

        return putGoodsAnchoredSupplierLinesDrilldownFocus(m, purCtx, "SUPPLIER_UNIT_PRICE");
    }

    private static boolean putGoodsAnchoredSupplierLinesDrilldownFocus(
            Map<String, Object> m, AiResolvedQueryContext purCtx, String toolDetailWantedKey) {
        Integer disGoodsId = null;
        String goodsName = null;

        AiConversationTurnMemory prev = purCtx.getPreviousTurn();
        if (prev != null && prev.getLastResultAnchors() != null) {
            for (AiResultAnchor a : prev.getLastResultAnchors()) {
                if (a == null) {
                    continue;
                }
                if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(nullToEmpty(a.getEntityType()))) {
                    continue;
                }
                disGoodsId = parsePositiveInt(a.getEntityId());
                if (a.getEntityName() != null && !a.getEntityName().isBlank()) {
                    goodsName = a.getEntityName().trim();
                }
                break;
            }
        }
        if (goodsName == null || goodsName.isBlank()) {
            if (purCtx.getFollowUpTargetEntityName() != null && !purCtx.getFollowUpTargetEntityName().isBlank()) {
                goodsName = purCtx.getFollowUpTargetEntityName().trim();
            }
        }
        if (disGoodsId == null) {
            disGoodsId = parsePositiveInt(purCtx.getFollowUpTargetEntityId());
        }

        boolean hasAnchor = disGoodsId != null || (goodsName != null && !goodsName.isBlank());
        if (!hasAnchor) {
            return false;
        }

        if (disGoodsId != null) {
            m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_DIS_GOODS_ID, disGoodsId);
        }
        if (goodsName != null && !goodsName.isBlank()) {
            m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_GOODS_NAME, goodsName);
        }
        m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_ENTITY_TYPE, AiResultAnchor.ENTITY_TYPE_GOODS);
        m.put(AiBusinessToolIds.ARG_PURCHASE_FOLLOW_UP_DETAIL_WANTED, toolDetailWantedKey);
        return true;
    }

    /**
     * D-13.1：供货商金额排行 Top1 锚 → 商品明细行（与 Resolver {@code GOODS_UNIT_PRICE} / OBJECT_DRILLDOWN 对齐）。
     */
    private static void tryPutSupplierAnchoredGoodsLinesDrilldown(
            Map<String, Object> m, AiResolvedQueryContext purCtx) {
        String wanted = purCtx.getFollowUpDetailWanted();
        if (wanted == null || !"GOODS_UNIT_PRICE".equalsIgnoreCase(wanted.trim())) {
            return;
        }

        Integer supplierId = null;
        AiConversationTurnMemory prev = purCtx.getPreviousTurn();
        List<AiResultAnchor> anchors = prev == null ? null : prev.getLastResultAnchors();
        if (anchors != null) {
            for (AiResultAnchor a : anchors) {
                if (a == null) {
                    continue;
                }
                if (!AiResultAnchor.ENTITY_TYPE_SUPPLIER.equalsIgnoreCase(nullToEmpty(a.getEntityType()))) {
                    continue;
                }
                if (!PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(
                        nullToEmpty(a.getSourcePlanType()))) {
                    continue;
                }
                Integer rk = a.getRank();
                boolean rankOne = rk != null && rk == 1;
                boolean singleUnranked = rk == null && anchors.size() == 1;
                if (!(rankOne || singleUnranked)) {
                    continue;
                }
                supplierId = parsePositiveInt(a.getEntityId());
                if (supplierId != null) {
                    break;
                }
            }
        }
        if (supplierId == null) {
            return;
        }
        m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_SUPPLIER_ID, supplierId);
        m.put(AiBusinessToolIds.ARG_PURCHASE_FOCUS_ENTITY_TYPE, AiResultAnchor.ENTITY_TYPE_SUPPLIER);
        m.put(AiBusinessToolIds.ARG_PURCHASE_FOLLOW_UP_DETAIL_WANTED, "GOODS_UNIT_PRICE");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Integer parsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? Integer.valueOf(v) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
