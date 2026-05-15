package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 菜品销量/销售额排行：纯确定性正文（不调 LLM）；主旨为销量与销售额，毛利率仅作附列表述。
 */
@Component
public final class DishSalesDeterministicRenderer {

    public String render(DishSalesAnswerPlan plan) {
        if (plan == null) {
            return "当前未能读取菜品销售排行计划。";
        }
        StringBuilder sb = new StringBuilder();
        String scope = DeterministicRendererSupport.nz(plan.getScopeLabel()).trim();
        String time = DeterministicRendererSupport.nz(plan.getTimeLabel()).trim();
        if (StringUtils.hasText(scope)) {
            sb.append("查询范围：").append(scope).append('\n');
        }
        if (StringUtils.hasText(time)) {
            sb.append("统计时间：").append(time).append('\n');
        }

        List<Map<String, Object>> rows = plan.getRankingRows();
        if (rows == null || rows.isEmpty()) {
            sb.append("当前没有可用菜品销售明细。");
            appendLimitations(sb, plan);
            return sb.toString().trim();
        }

        Map<String, Object> top = rows.get(0);
        String name = dishName(top);
        String qty = DeterministicRendererSupport.plainNumericHint(top.get("soldPortionsTotal"));
        String rev = DeterministicRendererSupport.plainNumericHint(top.get("listPriceRevenue"));
        String pt = plan.getPlanType() == null ? "" : plan.getPlanType().trim();
        if (DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH.equals(pt)) {
            sb.append("销量最高的是 ")
                    .append(name)
                    .append("，销量 ")
                    .append(qty)
                    .append(" 份，销售额 ")
                    .append(rev)
                    .append(" 元。");
        } else if (DishSalesAnswerPlan.TYPE_DISH_SALES_AMOUNT_RANKING_HIGH.equals(pt)) {
            sb.append("销售额最高的是 ")
                    .append(name)
                    .append("，销售额 ")
                    .append(rev)
                    .append(" 元，销量 ")
                    .append(qty)
                    .append(" 份。");
        } else if (DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_LOW.equals(pt)) {
            sb.append("销售份数最少的是 ")
                    .append(name)
                    .append("，销量 ")
                    .append(qty)
                    .append(" 份，销售额 ")
                    .append(rev)
                    .append(" 元。");
        } else {
            sb.append("菜品销售排行首位：")
                    .append(name)
                    .append("（销量 ")
                    .append(qty)
                    .append(" 份，销售额 ")
                    .append(rev)
                    .append(" 元）。");
        }

        sb.append('\n');
        sb.append("销量/销售额排行 Top3：\n");
        int n = Math.min(3, rows.size());
        for (int i = 0; i < n; i++) {
            Map<String, Object> r = rows.get(i);
            if (r == null) {
                continue;
            }
            String dn = dishName(r);
            String q = DeterministicRendererSupport.plainNumericHint(r.get("soldPortionsTotal"));
            String rv = DeterministicRendererSupport.plainNumericHint(r.get("listPriceRevenue"));
            String gm = DeterministicRendererSupport.nz(r.get("grossMarginRate")).trim();
            sb.append(i + 1)
                    .append(". ")
                    .append(dn)
                    .append("｜销量 ")
                    .append(q)
                    .append(" 份｜销售额 ")
                    .append(rv)
                    .append(" 元");
            if (StringUtils.hasText(gm)) {
                sb.append("｜毛利率 ").append(gm);
            }
            sb.append('\n');
        }
        appendLimitations(sb, plan);
        return sb.toString().trim();
    }

    private static void appendLimitations(StringBuilder sb, DishSalesAnswerPlan plan) {
        List<String> lim = plan.getLimitations();
        if (lim == null || lim.isEmpty()) {
            return;
        }
        boolean any = false;
        for (String s : lim) {
            if (StringUtils.hasText(s)) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        sb.append('\n');
        for (String s : lim) {
            if (StringUtils.hasText(s)) {
                sb.append(s.trim()).append('\n');
            }
        }
    }

    private static String dishName(Map<String, Object> row) {
        if (row == null) {
            return "（未命名菜品）";
        }
        Object v = row.get("dishName");
        String s = v == null ? "" : v.toString().trim();
        return StringUtils.hasText(s) ? s : "（未命名菜品）";
    }
}
