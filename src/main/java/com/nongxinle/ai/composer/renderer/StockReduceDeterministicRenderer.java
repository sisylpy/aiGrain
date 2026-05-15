package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public final class StockReduceDeterministicRenderer {

    public String renderStockReduceToolFallback(AiRunState state) {
        return stockReduceQueryDeterministicFallback(state);
    }

    private static String stockReduceQueryDeterministicFallback(AiRunState state) {
        Map<String, Object> d = DeterministicRendererSupport.toolDataInnerMap(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
        if (d == null || d.isEmpty()) {
            return "暂时没有拿到出库/核销汇总数据，请确认统计周期与门店权限后重试。";
        }
        boolean mock = Boolean.TRUE.equals(DeterministicRendererSupport.toolEnvelope(state, AiBusinessToolIds.STOCK_REDUCE_QUERY).get("mock"));
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        String timeLine = DeterministicRendererSupport.nz(tw.getTimeSubjectText());
        String basisNote = "CALENDAR_NATURAL_DAY".equals(String.valueOf(d.get("totalsBasis")))
                ? "（自然日历日四类金额合计；不按「仅日营业额日」过滤）"
                : "（与同段成本工具一致：仅日营业额日核销口径）";

        List<String> storeNames = new ArrayList<>();
        AiResolvedQueryContext ctx = state != null ? state.getResolvedQueryContext() : null;
        if (ctx != null && ctx.getOrgScope() != null && ctx.getOrgScope().getVisibleStores() != null) {
            for (AiStoreScopeDTO s : ctx.getOrgScope().getVisibleStores()) {
                if (s != null && s.getStoreName() != null && !s.getStoreName().isBlank()) {
                    storeNames.add(s.getStoreName().trim());
                }
            }
        }
        boolean groupAgg = Boolean.TRUE.equals(d.get("groupStockReduceAggregation"));
        String scopeLine = storeNames.isEmpty()
                ? (groupAgg ? "范围为集团你可查看门店集合。" : "范围为当前账号可见门店。")
                : ("门店：" + String.join("、", storeNames) + "。");

        String wireDetail =
                ctx != null && ctx.getQueryIntent() != null ? DeterministicRendererSupport.nz(ctx.getQueryIntent().getStructuredIntentDetail()) : "";

        String pAmt = DeterministicRendererSupport.plainNumericHint(d.get("produceTotal"));
        String wAmt = DeterministicRendererSupport.plainNumericHint(d.get("wasteTotal"));
        String lAmt = DeterministicRendererSupport.plainNumericHint(d.get("lossTotal"));
        String rAmt = DeterministicRendererSupport.plainNumericHint(d.get("returnTotal"));
        String gAmt = DeterministicRendererSupport.plainNumericHint(d.get("grandTotalFourTypes"));

        if (AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(wireDetail)) {
            Object rawTop = d.get("topGoodsOutboundBySubtotal");
            StringBuilder ranks = new StringBuilder();
            if (rawTop instanceof List<?> list) {
                int idx = 0;
                for (Object rowObj : list) {
                    if (!(rowObj instanceof Map<?, ?> row)) {
                        continue;
                    }
                    if (idx >= 5) {
                        break;
                    }
                    if (ranks.length() > 0) {
                        ranks.append(' ');
                    }
                    ranks.append(idx + 1).append(')').append(DeterministicRendererSupport.nz(row.get("name"))).append(" ")
                            .append(DeterministicRendererSupport.plainNumericHint(row.get("amount"))).append(" 元.");
                    idx++;
                }
            }
            String mockNote = mock ? "（提示：当前结果为占位或数据源不足，请稍后重试。）" : "";
            return String.format("%s在%s%s，%s出库金额最高的商品：%s%s",
                    mock ? "[数据待完善] " : "",
                    timeLine.isBlank() ? "该时段" : timeLine,
                    basisNote,
                    scopeLine,
                    ranks.length() > 0 ? ranks.toString() : "暂未查询到明细。",
                    mockNote);
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PRODUCE_CONSUME.equals(wireDetail)) {
            return String.format("在%s%s，%s生产耗用（type1）出库金额合计约 %s 元。分项：废弃 %s 元、损耗 %s 元、退货 %s 元%s",
                    timeLine.isBlank() ? "该时段" : timeLine, basisNote, scopeLine,
                    pAmt, wAmt, lAmt, rAmt, mock ? " [mock]" : "");
        }
        if (AiQuerySemanticLexicon.STRUCTURED_WASTE.equals(wireDetail)) {
            return String.format("在%s%s，%s废弃（type2）出库金额约 %s 元。", timeLine.isBlank() ? "该时段" : timeLine,
                    basisNote, scopeLine, wAmt) + (mock ? " [mock]" : "");
        }
        if (AiQuerySemanticLexicon.STRUCTURED_LOSS.equals(wireDetail)) {
            return String.format("在%s%s，%s损耗（type3，口语常称报损）出库金额约 %s 元。", timeLine.isBlank() ? "该时段" : timeLine,
                    basisNote, scopeLine, lAmt) + (mock ? " [mock]" : "");
        }
        if (AiQuerySemanticLexicon.STRUCTURED_RETURN.equals(wireDetail)) {
            return String.format("在%s%s，%s退货出库（type4）金额约 %s 元。", timeLine.isBlank() ? "该时段" : timeLine,
                    basisNote, scopeLine, rAmt) + (mock ? " [mock]" : "");
        }

        String head = mock ? "[占位/不完整数据] " : "";
        return head + String.format(
                "%s%s，%s出库/核销金额合计（四类之和）约 %s 元，其中生产耗用 %s 元、废弃 %s 元、损耗 %s 元、退货 %s 元。",
                timeLine.isBlank() ? "该时段" : timeLine, basisNote, scopeLine,
                gAmt, pAmt, wAmt, lAmt, rAmt);
    }
}
