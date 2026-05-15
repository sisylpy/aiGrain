package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public final class PurchaseDeterministicRenderer {

    private static final Pattern PREV_TURN_PURCHASE_CARRY_PREFIX =
            Pattern.compile("^carry_po=(\\d+),carry_amt=([^|]+)\\|");

    private record PurchaseCarryHint(int orderCount, String amountToken) {
    }

    public String renderPurchaseCostFallback(AiRunState state) {
        return purchaseCostFallback(state);
    }

    private static boolean isBusinessOverviewToPurchaseConvergence(AiRunState state) {
        Map<String, String> ic = state.getIntentConvergence();
        return ic != null
                && "BUSINESS_OVERVIEW".equals(ic.get("from"))
                && "PURCHASE_OVERVIEW".equals(ic.get("to"));
    }

    private static String purchaseCostFallback(AiRunState state) {
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        Map<String, Object> overview = DeterministicRendererSupport.extractPurchaseOverviewPayloadForRenderer(state);
        Map<String, Object> stk = DeterministicRendererSupport.toolDataInnerMap(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
        boolean convergence = isBusinessOverviewToPurchaseConvergence(state);
        if (!overview.isEmpty()) {
            return purchaseOverviewStructuredFallback(state, overview, stk, convergence, tw);
        }
        Map<String, Object> p = DeterministicRendererSupport.toolDataInnerMap(state, AiBusinessToolIds.PURCHASE_QUERY);
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        if (convergence) {
            sb.append("说明：以下仅基于采购与库存权限汇总，不包含营业额、订单数、客单价、毛利或利润等经营指标。\n");
        } else {
            sb.append("（采购视角）已按权限汇总采购入库与核销/出库数据，不包含营业额与毛利率诊断。\n");
        }
        boolean purchaseHasRows = purchaseHasDataRows(p);
        if (!p.isEmpty() && purchaseHasRows) {
            sb.append(tw.getDisplayTimeRange()).append("，采购入库金额约 ")
                    .append(DeterministicRendererSupport.plainNumericHint(p.get("purchaseSubTotal")))
                    .append(" 元；采购明细行数 ")
                    .append(DeterministicRendererSupport.plainNumericHint(p.get("purchaseRowCount")))
                    .append("。\n");
        }
        appendPurchaseStockReduceParagraph(sb, stk, true, tw);
        if (!purchaseHasRows && stk.isEmpty()) {
            if (convergence) {
                sb.append("你当前账号可查看采购相关数据，但").append(tw.getDisplayTimeRange())
                        .append("暂未查询到采购记录；核销/出库侧亦无可用汇总。\n");
            } else {
                sb.append("当前可用数据不足，暂时无法给出完整分析；请核对本岗权限、所选门店与时间区间是否与录入一致。\n");
            }
        } else if (!purchaseHasRows && !stk.isEmpty() && convergence) {
            sb.append(tw.getDisplayTimeRange()).append("采购入库侧暂未查询到明细记录，可先结合上方核销/出库汇总排查是否与入库录入一致。\n");
        }
        sb.append("供应商价格与品类波动建议在采购或供货商模块导出核对。");
        return sb.toString().trim();
    }

    private static String purchaseOverviewStructuredFallback(AiRunState state, Map<String, Object> overview,
            Map<String, Object> stk, boolean convergence, AiTimeWindowTextFormatter.UserPhrases tw) {
        Object narrativeObj = overview.get("purchaseNarrativeMode");
        String narrative = narrativeObj != null ? narrativeObj.toString().trim() : "";
        if (narrative.isBlank()) {
            narrative = AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY;
        }
        Object focusObj = overview.get("purchaseSourceFocus");
        String purchaseFocus = focusObj != null ? focusObj.toString().trim() : "";
        boolean treatAsSupplierRanking = useSupplierRankingFocusedTemplate(state, narrative);
        if (treatAsSupplierRanking) {
            return purchaseSupplierRankingFallback(state, overview, tw);
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(narrative)) {
            return purchaseSourceAmountOnlyFallback(state, overview, purchaseFocus, tw);
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(narrative)) {
            return purchaseSourceGoodsNarrowFallback(state, overview, purchaseFocus, tw);
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY.equals(narrative)) {
            return purchaseSourceSummaryNarrowFallback(state, overview, purchaseFocus, tw);
        }
        return purchaseOverviewFullSummaryFallback(state, overview, stk, convergence, purchaseFocus, tw);
    }

    /** 是否走「仅供货商采购金额排行」短答（与 purchase_overview_summary 全量模板区分）。 */
    private static boolean useSupplierRankingFocusedTemplate(AiRunState state, String narrativeFromOverview) {
        if (AiQuerySemanticLexicon.isSupplierAmountRankingDetail(narrativeFromOverview)) {
            return true;
        }
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        var qi = state.getResolvedQueryContext().getQueryIntent();
        if (qi != null && AiQuerySemanticLexicon.isSupplierAmountRankingDetail(qi.getStructuredIntentDetail())) {
            return true;
        }
        return false;
    }

    private static PurchaseCarryHint tryParsePurchaseCarryFromPreviousTurn(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return null;
        }
        AiConversationTurnMemory prev = state.getResolvedQueryContext().getPreviousTurn();
        if (prev == null || prev.getLastToolSummary() == null) {
            return null;
        }
        Matcher m = PREV_TURN_PURCHASE_CARRY_PREFIX.matcher(prev.getLastToolSummary().trim());
        if (!m.find()) {
            return null;
        }
        try {
            int po = Integer.parseInt(m.group(1));
            String amtTok = m.group(2).trim();
            return new PurchaseCarryHint(po, amtTok);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 供货商渠道汇总为 0 时的说明正文（不含时间括号行）；若上一轮有 carry 则对比全口径结论。
     */
    private static void appendSupplierPurchaseZeroNarrativeBody(StringBuilder sb, AiRunState state,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        PurchaseCarryHint carry = tryParsePurchaseCarryFromPreviousTurn(state);
        String storeSubject = resolveSinglePurchaseStoreSubject(state);
        String timePhrase = resolvedTimeSubjectPhrase(tw);
        String leadInStoreTime = spacedStorePlusTimePhrase(storeSubject, timePhrase);
        String resultStoreTime = compactResultStoreTimePhrase(storeSubject, timePhrase);
        sb.append("沿用上文 ").append(leadInStoreTime).append("口径，本轮只看供货商采购。");
        sb.append("查询结果：").append(resultStoreTime).append("暂无供货商采购记录，供货商采购 0 笔、0 元。");
        if (carry != null && carry.orderCount() > 0) {
            String amtDisp = DeterministicRendererSupport.plainNumericHint(carry.amountToken());
            sb.append("结合上一轮").append(resultStoreTime).append("总采购 ").append(carry.orderCount()).append(" 笔、")
                    .append(amtDisp).append(" 元，可判断这些采购均为自采记录。");
        } else {
            sb.append("（未附带上一轮全口径对照数据时无法在答复中自动判断是否均为自采，请在系统中按入库来源拆分核对供货商/自采。）");
        }
    }

    /** 与用户可见【查询范围】前缀配合：阐明继承口径下的供货商筛选结论，避免笼统「暂无有效采购」。 */
    private static String supplierPurchaseFilteredZeroParagraph(AiRunState state,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        appendSupplierPurchaseZeroNarrativeBody(sb, state, tw);
        return sb.toString().trim();
    }

    private static String resolveSinglePurchaseStoreSubject(AiRunState state) {
        AiResolvedQueryContext ctx = state != null ? state.getResolvedQueryContext() : null;
        if (ctx == null || ctx.getOrgScope() == null || ctx.getOrgScope().getVisibleStores() == null
                || ctx.getOrgScope().getVisibleStores().size() != 1) {
            return "";
        }
        AiStoreScopeDTO s = ctx.getOrgScope().getVisibleStores().get(0);
        if (s == null || s.getStoreName() == null || s.getStoreName().isBlank()) {
            return "";
        }
        return s.getStoreName().trim();
    }

    private static String resolvedTimeSubjectPhrase(AiTimeWindowTextFormatter.UserPhrases tw) {
        String time = tw.getTimeSubjectText();
        if (time == null || time.isBlank()) {
            return "该统计区间";
        }
        return time.trim();
    }

    private static String spacedStorePlusTimePhrase(String store, String timePhrase) {
        if (store == null || store.isBlank()) {
            return timePhrase;
        }
        return store + " + " + timePhrase;
    }

    private static String compactResultStoreTimePhrase(String store, String timePhrase) {
        if (store == null || store.isBlank()) {
            return timePhrase;
        }
        return store + " " + timePhrase;
    }

    /** 自采/供货商「金额是多少」类：只报金额、笔数，至多 2 个金额最高单品；不追加「其中…」拆分与核销长段。 */
    private static String purchaseSourceAmountOnlyFallback(AiRunState state, Map<String, Object> overview,
            String purchaseFocus,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        int cnt = DeterministicRendererSupport.intHint(overview.get("purchaseOrderCount"));
        double amt = DeterministicRendererSupport.parseDoubleLoose(overview.get("totalPurchaseAmount"));
        boolean selfFocus = AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(purchaseFocus);
        boolean supFocus = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(purchaseFocus);
        if (cnt <= 0 && amt <= 0) {
            if (selfFocus) {
                return "当前范围内暂未查询到自采入库记录，请确认时间与门店范围内是否有自采入库数据。";
            }
            if (supFocus) {
                return supplierPurchaseFilteredZeroParagraph(state, tw);
            }
            return "当前范围内暂未查询到有效采购记录，请确认采购入库数据是否已录入。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        if (selfFocus) {
            sb.append(tw.getDisplayTimeRange()).append("，自采金额为")
                    .append(DeterministicRendererSupport.plainNumericHint(overview.get("totalPurchaseAmount")))
                    .append("元，共")
                    .append(cnt)
                    .append("笔自采入库");
        } else if (supFocus) {
            sb.append(tw.getDisplayTimeRange()).append("，供货商渠道采购金额为")
                    .append(DeterministicRendererSupport.plainNumericHint(overview.get("totalPurchaseAmount")))
                    .append("元，共")
                    .append(cnt)
                    .append("笔供货商采购入库");
        } else {
            sb.append(tw.getDisplayTimeRange()).append("，采购入库总金额为")
                    .append(DeterministicRendererSupport.plainNumericHint(overview.get("totalPurchaseAmount")))
                    .append("元，共")
                    .append(cnt)
                    .append("笔");
        }
        sb.append("。");
        List<String> tops = pickPurchaseAmountTopParts(overview, 2);
        if (!tops.isEmpty()) {
            if (selfFocus) {
                sb.append("自采金额最高的商品是").append(String.join("、", tops)).append("。");
            } else if (supFocus) {
                sb.append("供货商渠道采购金额最高的商品是").append(String.join("、", tops)).append("。");
            } else {
                sb.append("采购金额最高的商品是").append(String.join("、", tops)).append("。");
            }
        }
        return sb.toString();
    }

    /** 「自采了哪些商品」：笔数+金额 + 频次/金额 Top；不展开核销与门店覆盖长段。 */
    private static String purchaseSourceGoodsNarrowFallback(AiRunState state, Map<String, Object> overview,
            String purchaseFocus, AiTimeWindowTextFormatter.UserPhrases tw) {
        int cnt = DeterministicRendererSupport.intHint(overview.get("purchaseOrderCount"));
        double amt = DeterministicRendererSupport.parseDoubleLoose(overview.get("totalPurchaseAmount"));
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        sb.append("（采购视角）以下汇总采购入库商品情况，不包含营业额与毛利率诊断。\n");
        Object b = overview.get("queryScopeBanner");
        if (b != null && !b.toString().isBlank()) {
            sb.append(b.toString().trim()).append("\n");
        }
        appendPurchaseCountAmountLine(sb, state, overview, purchaseFocus, tw);
        if (cnt <= 0 && amt <= 0) {
            return sb.toString().trim();
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append("\n");
        }
        appendGoodsFrequencyTopSentence(sb, overview.get("goodsPurchaseFrequencyTop"), purchaseFocus);
        Object amtTop = overview.get("goodsPurchaseAmountTop");
        if (!(amtTop instanceof List<?>) || ((List<?>) amtTop).isEmpty()) {
            amtTop = overview.get("highAmountItems");
        }
        appendGoodsAmountTopSentence(sb, amtTop, purchaseFocus);
        return sb.toString().trim();
    }

    /** 「自采有多少」：笔数+金额 + 可 Top 商品；不展开完整概览。 */
    private static String purchaseSourceSummaryNarrowFallback(AiRunState state, Map<String, Object> overview,
            String purchaseFocus, AiTimeWindowTextFormatter.UserPhrases tw) {
        int cnt = DeterministicRendererSupport.intHint(overview.get("purchaseOrderCount"));
        double amt = DeterministicRendererSupport.parseDoubleLoose(overview.get("totalPurchaseAmount"));
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        sb.append("（采购视角）以下汇总采购入库情况，不包含营业额与毛利率诊断。\n");
        Object b = overview.get("queryScopeBanner");
        if (b != null && !b.toString().isBlank()) {
            sb.append(b.toString().trim()).append("\n");
        }
        appendPurchaseCountAmountLine(sb, state, overview, purchaseFocus, tw);
        if (cnt <= 0 && amt <= 0) {
            return sb.toString().trim();
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append("\n");
        }
        appendGoodsFrequencyTopSentence(sb, overview.get("goodsPurchaseFrequencyTop"), purchaseFocus);
        Object amtTop = overview.get("goodsPurchaseAmountTop");
        if (!(amtTop instanceof List<?>) || ((List<?>) amtTop).isEmpty()) {
            amtTop = overview.get("highAmountItems");
        }
        appendGoodsAmountTopSentence(sb, amtTop, purchaseFocus);
        return sb.toString().trim();
    }

    /** 供货商采购金额排行：只输出名次与户数，不包含全量采购/自采拆分/单品 Top/核销。 */
    private static String purchaseSupplierRankingFallback(AiRunState state, Map<String, Object> overview,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        StringBuilder sb = new StringBuilder();
        sb.append(tw != null ? tw.getDisplayTimeRange() : "统计周期")
                .append("，")
                .append(supplierRankingScopeLead(state, overview))
                .append("供货商采购金额排名如下：\n");
        Object topRaw = overview.get("topSuppliers");
        if (!(topRaw instanceof List<?> topList) || topList.isEmpty()) {
            int po = DeterministicRendererSupport.intHint(overview.get("purchaseOrderCount"));
            double amt = DeterministicRendererSupport.parseDoubleLoose(overview.get("totalPurchaseAmount"));
            if (po <= 0 && amt <= 0) {
                sb.append("当前范围内暂未查询到采购入库记录。");
            } else {
                sb.append("暂无真实供货商采购记录。");
                sb.append("本周期仍有采购入账，但未识别到挂靠供货商的入账行（常见于全部为自采或入库未登记供货商）；与上一轮若为「全自采」结论一致时也属正常。");
            }
            return sb.toString().trim();
        }
        int pos = 1;
        for (Object o : topList) {
            if (pos > 50) {
                break;
            }
            if (!(o instanceof Map<?, ?> row)) {
                continue;
            }
            Object nm = row.get("supplierName");
            if (nm == null || nm.toString().isBlank()) {
                continue;
            }
            int lines = supplierRankingLineCountHint(row);
            double rowAmt = DeterministicRendererSupport.parseDoubleLoose(row.get("totalPurchaseAmount"));
            sb.append("第")
                    .append(pos)
                    .append("名：")
                    .append(nm.toString().trim())
                    .append("，采购金额")
                    .append(rowAmt > 1e-9 ? DeterministicRendererSupport.plainNumericHint(row.get("totalPurchaseAmount")) : DeterministicRendererSupport.plainNumericHint(0))
                    .append("元，共")
                    .append(lines > 0 ? lines : Math.max(DeterministicRendererSupport.intHint(row.get("orderCount")), DeterministicRendererSupport.intHint(row.get("lineCount"))))
                    .append("笔。\n");
            pos++;
        }
        int counted = pos - 1;
        if (counted <= 0) {
            sb.append("暂无真实供货商采购记录。");
        } else {
            sb.append("\n当前口径下仅查询到")
                    .append(counted)
                    .append("家真实供货商采购记录。");
        }
        return sb.toString().trim();
    }

    private static int supplierRankingLineCountHint(Map<?, ?> row) {
        int a = DeterministicRendererSupport.intHint(row.get("purchaseLineCount"));
        if (a > 0) {
            return a;
        }
        return DeterministicRendererSupport.intHint(row.get("purchaseOrderCount"));
    }

    /** 接在时间及逗号后的范围提示，如「集团范围」。 */
    private static String supplierRankingScopeLead(AiRunState state, Map<String, Object> overview) {
        Object b = overview != null ? overview.get("queryScopeBanner") : null;
        String banner = b != null ? b.toString().trim() : "";
        if (banner.contains("集团")) {
            return "集团范围";
        }
        if (state != null && state.getResolvedQueryContext() != null) {
            var org = state.getResolvedQueryContext().getOrgScope();
            if (org != null) {
                if (AiResolvedOrgScope.SCOPE_GROUP.equals(org.getScopeType())) {
                    return "集团范围";
                }
                if (org.getVisibleStores() != null && org.getVisibleStores().size() == 1) {
                    return "当前门店范围";
                }
            }
        }
        if (!banner.isEmpty()) {
            return banner.replaceFirst("^【?查询范围】?[:：]?\\s*", "").trim();
        }
        return "当前查询范围";
    }

    private static void appendPurchaseCountAmountLine(StringBuilder sb, AiRunState state,
            Map<String, Object> overview, String purchaseFocus, AiTimeWindowTextFormatter.UserPhrases tw) {
        int cnt = DeterministicRendererSupport.intHint(overview.get("purchaseOrderCount"));
        double amt = DeterministicRendererSupport.parseDoubleLoose(overview.get("totalPurchaseAmount"));
        boolean selfFocus = AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(purchaseFocus);
        boolean supFocus = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(purchaseFocus);
        if (cnt <= 0 && amt <= 0) {
            if (selfFocus) {
                sb.append("当前范围内暂未查询到自采入库记录，请确认时间与门店范围内是否有自采入库数据。");
            } else if (supFocus) {
                if (state != null) {
                    appendSupplierPurchaseZeroNarrativeBody(sb, state, tw);
                } else {
                    sb.append(
                            "本轮按供货商采购渠道汇总：暂无供货商入库记录（0 笔、0 元）；未表示全口径采购为空，请核对时间与录入口径。");
                }
            } else {
                sb.append("当前范围内暂未查询到有效采购记录，请确认采购入库数据是否已录入。");
            }
            return;
        }
        String rangeLead = tw != null ? tw.getDisplayTimeRange() : "统计周期";
        if (selfFocus) {
            sb.append(rangeLead).append("，自采入库共")
                    .append(cnt)
                    .append("笔，自采金额")
                    .append(DeterministicRendererSupport.plainNumericHint(overview.get("totalPurchaseAmount")))
                    .append("元。");
        } else if (supFocus) {
            sb.append(rangeLead).append("，供货商渠道采购入库共")
                    .append(cnt)
                    .append("笔，金额")
                    .append(DeterministicRendererSupport.plainNumericHint(overview.get("totalPurchaseAmount")))
                    .append("元。");
        } else {
            sb.append(rangeLead).append("，采购入库共")
                    .append(cnt)
                    .append("笔，金额")
                    .append(DeterministicRendererSupport.plainNumericHint(overview.get("totalPurchaseAmount")))
                    .append("元。");
        }
    }

    private static List<String> pickPurchaseAmountTopParts(Map<String, Object> overview, int maxN) {
        if (maxN <= 0) {
            return Collections.emptyList();
        }
        Object amtTop = overview.get("goodsPurchaseAmountTop");
        if (!(amtTop instanceof List<?>) || ((List<?>) amtTop).isEmpty()) {
            amtTop = overview.get("highAmountItems");
        }
        if (!(amtTop instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> parts = new ArrayList<>();
        for (Object o : list) {
            if (parts.size() >= maxN) {
                break;
            }
            if (o instanceof Map<?, ?> row) {
                Object nm = row.get("goodsName");
                Object sub = row.get("purchaseSubtotal");
                if (nm != null && !nm.toString().isBlank() && sub != null
                        && DeterministicRendererSupport.parseDoubleLoose(sub) > 1e-9) {
                    parts.add(nm.toString().trim() + DeterministicRendererSupport.plainNumericHint(sub) + "元");
                }
            }
        }
        return parts;
    }

    private static String purchaseOverviewFullSummaryFallback(AiRunState state, Map<String, Object> overview,
            Map<String, Object> stk, boolean convergence, String purchaseFocus,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        if (convergence) {
            sb.append("说明：以下仅基于采购与库存权限汇总，不包含营业额、订单数、客单价、毛利或利润等经营指标。\n");
        } else {
            sb.append("（采购视角）已按权限汇总采购入库与核销/出库数据，不包含营业额与毛利率诊断。\n");
        }
        Object b = overview.get("queryScopeBanner");
        if (b != null && !b.toString().isBlank()) {
            sb.append(b.toString().trim()).append("\n");
        }
        Object scs = overview.get("storeCoverageSummary");
        if (scs != null && !scs.toString().isBlank()) {
            sb.append(scs.toString().trim()).append("\n");
        }
        int cnt = DeterministicRendererSupport.intHint(overview.get("purchaseOrderCount"));
        double amt = DeterministicRendererSupport.parseDoubleLoose(overview.get("totalPurchaseAmount"));
        boolean selfFocus = AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(purchaseFocus);
        boolean supFocus = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(purchaseFocus);
        if (cnt <= 0 && amt <= 0) {
            if (selfFocus) {
                sb.append("当前范围内暂未查询到自采入库记录，请确认时间与门店范围内是否有自采入库数据。\n");
            } else if (supFocus) {
                if (state != null) {
                    appendSupplierPurchaseZeroNarrativeBody(sb, state, tw);
                    sb.append("\n");
                } else {
                    sb.append(
                            "本轮按供货商采购渠道汇总：暂无供货商入库记录（0 笔、0 元）；未表示全口径采购为空，请核对时间与录入口径。\n");
                }
            } else {
                sb.append("当前范围内暂未查询到有效采购记录，请确认采购入库数据是否已录入。\n");
            }
        } else {
            String rangeLead = tw.getDisplayTimeRange();
            sb.append(rangeLead).append("，");
            if (selfFocus) {
                sb.append("自采入库共");
            } else if (supFocus) {
                sb.append("供货商渠道采购入库共");
            } else {
                sb.append("采购入库共");
            }
            sb.append(cnt)
                    .append("笔，")
                    .append(selfFocus ? "自采金额" : (supFocus ? "供货商渠道采购金额" : "总金额"))
                    .append(DeterministicRendererSupport.plainNumericHint(overview.get("totalPurchaseAmount")))
                    .append("元");
            boolean methodOk = Boolean.TRUE.equals(overview.get("purchaseMethodBreakdownSupported"));
            Object frag = overview.get("purchaseMethodSummaryFragment");
            Object methodNote = overview.get("purchaseMethodNote");
            if (methodOk && frag != null && !frag.toString().isBlank() && !selfFocus && !supFocus) {
                sb.append("其中").append(frag.toString().trim()).append("。");
            } else if (methodNote != null && !methodNote.toString().isBlank() && !selfFocus && !supFocus) {
                sb.append(" ").append(methodNote.toString().trim()).append("。");
            } else {
                sb.append("。");
            }
            appendGoodsFrequencyTopSentence(sb, overview.get("goodsPurchaseFrequencyTop"), purchaseFocus);
            Object amtTop = overview.get("goodsPurchaseAmountTop");
            if (!(amtTop instanceof List<?>) || ((List<?>) amtTop).isEmpty()) {
                amtTop = overview.get("highAmountItems");
            }
            appendGoodsAmountTopSentence(sb, amtTop, purchaseFocus);
            appendPrimarySuppliersSentence(sb, overview.get("topSuppliers"));

            appendBriefPurchaseList(sb, "价格波动较明显的商品", overview.get("priceChangeItems"), "goodsName", 3);
            appendBriefPurchaseList(sb, "有采购但无销售/无核销（待核对）", overview.get("purchaseWithoutSalesItems"),
                    "goodsName", 3);
        }
        DeterministicRendererSupport.appendWarehouseRecommendations(sb, overview.get("recommendations"));
        appendPurchaseStockReduceParagraph(sb, stk, true, tw);
        sb.append("供应商价格与品类波动建议在采购或供货商模块导出核对。");
        return sb.toString().trim();
    }

    private static void appendGoodsFrequencyTopSentence(StringBuilder sb, Object listObj, String purchaseSourceFocus) {
        if (!(listObj instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        int n = 0;
        for (Object o : list) {
            if (n >= 5) {
                break;
            }
            if (o instanceof Map<?, ?> row) {
                Object nm = row.get("goodsName");
                int times = DeterministicRendererSupport.intHint(row.get("purchaseTimes"));
                if (nm != null && !nm.toString().isBlank() && times > 0) {
                    parts.add(nm.toString().trim() + times + "次");
                    n++;
                }
            }
        }
        if (parts.isEmpty()) {
            return;
        }
        String head = "采购次数较多的是";
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(purchaseSourceFocus)) {
            head = "自采商品采购频次较高的是";
        } else if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(purchaseSourceFocus)) {
            head = "供货商采购商品采购频次较高的是";
        }
        sb.append(head).append(String.join("、", parts)).append("。\n");
    }

    private static void appendGoodsAmountTopSentence(StringBuilder sb, Object listObj, String purchaseSourceFocus) {
        if (!(listObj instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        int n = 0;
        for (Object o : list) {
            if (n >= 5) {
                break;
            }
            if (o instanceof Map<?, ?> row) {
                Object nm = row.get("goodsName");
                Object sub = row.get("purchaseSubtotal");
                if (nm != null && !nm.toString().isBlank() && sub != null
                        && DeterministicRendererSupport.parseDoubleLoose(sub) > 1e-9) {
                    parts.add(nm.toString().trim() + DeterministicRendererSupport.plainNumericHint(sub) + "元");
                    n++;
                }
            }
        }
        if (parts.isEmpty()) {
            return;
        }
        String head = "采购金额最高的是";
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(purchaseSourceFocus)) {
            head = "自采商品采购金额较高的是";
        } else if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(purchaseSourceFocus)) {
            head = "供货商采购商品金额较高的是";
        }
        sb.append(head).append(String.join("、", parts)).append("。\n");
    }

    private static void appendPrimarySuppliersSentence(StringBuilder sb, Object listObj) {
        appendPrimarySuppliersSentence(sb, listObj, 4);
    }

    private static void appendPrimarySuppliersSentence(StringBuilder sb, Object listObj, int maxSuppliers) {
        if (!(listObj instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        int cap = maxSuppliers > 0 ? maxSuppliers : 4;
        List<String> parts = new ArrayList<>();
        int n = 0;
        for (Object o : list) {
            if (n >= cap) {
                break;
            }
            if (o instanceof Map<?, ?> row) {
                Object nm = row.get("supplierName");
                Object am = row.get("totalPurchaseAmount");
                if (nm != null && !nm.toString().isBlank()) {
                    StringBuilder one = new StringBuilder(nm.toString().trim());
                    if (am != null && DeterministicRendererSupport.parseDoubleLoose(am) > 1e-9) {
                        one.append("（").append(DeterministicRendererSupport.plainNumericHint(am)).append("元）");
                    }
                    parts.add(one.toString());
                    n++;
                }
            }
        }
        if (parts.isEmpty()) {
            return;
        }
        sb.append("主要供货商为").append(String.join("、", parts)).append("。\n");
    }

    private static void appendPurchaseStockReduceParagraph(StringBuilder sb, Map<String, Object> stk,
            boolean closureHint, AiTimeWindowTextFormatter.UserPhrases tw) {
        if (stk == null || stk.isEmpty()) {
            return;
        }
        if (allPurchaseStockReduceMetricsZero(stk)) {
            String range = tw != null ? tw.getDisplayTimeRange() : "统计周期";
            sb.append(range).append("暂无核销/出库记录。\n");
            return;
        }
        sb.append("核销方面：生产耗用 ")
                .append(DeterministicRendererSupport.plainNumericHint(stk.get("productionTotal")))
                .append(" 元，出品 ")
                .append(DeterministicRendererSupport.plainNumericHint(stk.get("produceTotal")))
                .append(" 元，废弃 ")
                .append(DeterministicRendererSupport.plainNumericHint(stk.get("wasteTotal")))
                .append(" 元，损耗 ")
                .append(DeterministicRendererSupport.plainNumericHint(stk.get("lossTotal")))
                .append(" 元（亦称报损），退货 ")
                .append(DeterministicRendererSupport.plainNumericHint(stk.get("returnTotal")))
                .append(" 元");
        if (closureHint) {
            sb.append("。请结合入库核对链路是否闭合");
        }
        sb.append("。\n");
    }

    private static boolean allPurchaseStockReduceMetricsZero(Map<String, Object> stk) {
        if (stk == null || stk.isEmpty()) {
            return true;
        }
        String[] keys = {"productionTotal", "produceTotal", "wasteTotal", "lossTotal", "returnTotal"};
        for (String k : keys) {
            if (DeterministicRendererSupport.parseDoubleLoose(stk.get(k)) > 1e-9) {
                return false;
            }
        }
        return true;
    }

    private static void appendBriefPurchaseList(StringBuilder sb, String title, Object listObj, String nameKey, int max) {
        if (!(listObj instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        StringBuilder line = new StringBuilder();
        int n = 0;
        for (Object o : list) {
            if (n >= max) {
                break;
            }
            if (o instanceof Map<?, ?> row) {
                Object nm = row.get(nameKey);
                if (nm == null) {
                    nm = row.get("goodsName");
                }
                if (nm != null && !nm.toString().isBlank()) {
                    line.append(nm.toString().trim()).append("；");
                    n++;
                }
            }
        }
        if (n > 0) {
            sb.append(title).append("：").append(line).append("\n");
        }
    }

    private static boolean purchaseHasDataRows(Map<String, Object> p) {
        if (p == null || p.isEmpty()) {
            return false;
        }
        Object rc = p.get("purchaseRowCount");
        if (rc instanceof Number n) {
            return n.intValue() > 0;
        }
        try {
            return rc != null && Integer.parseInt(rc.toString().trim()) > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
