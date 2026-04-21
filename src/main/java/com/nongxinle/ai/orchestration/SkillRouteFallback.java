package com.nongxinle.ai.orchestration;

import cn.hutool.core.util.StrUtil;

import java.util.Locale;

/**
 * LLM 路由为主：仅在结构化解析失败、或 skills 为空 / none 时，用关键词规则推断技能文件。
 */
public final class SkillRouteFallback {

    private SkillRouteFallback() {
    }

    /**
     * 营收类问题中是否需要附带「按菜销量」事实块（gb_dep_food_sales）。
     */
    public static boolean shouldAttachDishSalesFacts(String userMessage) {
        if (StrUtil.isBlank(userMessage)) {
            return false;
        }
        String m = userMessage.toLowerCase(Locale.ROOT);
        if (m.contains("菜") && (m.contains("销") || m.contains("卖") || m.contains("量") || m.contains("多"))) {
            return true;
        }
        if (m.contains("热销") || m.contains("爆款")) {
            return true;
        }
        if (m.contains("什么菜") || m.contains("哪道菜")) {
            return true;
        }
        return m.contains("菜品") && (m.contains("最多") || m.contains("最好"));
    }

    public static SkillSelectionResult apply(String userMessage, SkillSelectionResult fromLlm) {
        boolean empty = isNoneOrEmptySkills(fromLlm.skillsCsv());
        boolean needFallback = !fromLlm.llmStructuredOk() || empty;
        if (!needFallback) {
            return fromLlm.withRouteSource(ChatRouteSource.LLM);
        }
        String inferred = inferSkillsCsv(userMessage);
        boolean broad = fromLlm.broadQuestion() || inferBroadQuestionFallback(userMessage);
        return new SkillSelectionResult(
                inferred,
                fromLlm.costFacet(),
                broad,
                fromLlm.confidence(),
                fromLlm.llmStructuredOk(),
                ChatRouteSource.RULE_FALLBACK
        );
    }

    private static boolean isNoneOrEmptySkills(String csv) {
        if (StrUtil.isBlank(csv)) {
            return true;
        }
        String t = csv.trim().toLowerCase(Locale.ROOT);
        if ("none".equals(t) || "null".equalsIgnoreCase(t)) {
            return true;
        }
        // 模型若吐出非法文件名（如 [].md），视为未选技能，走规则兜底
        return !t.contains("ai-skill-");
    }

    /**
     * 是否附带「本月采购」事实块（gb_distributer_purchase_goods.gb_DPG_buy_subtotal，按入库完成日筛）。
     */
    public static boolean shouldAttachPurchaseFacts(String userMessage, String costFacet) {
        if ("procurement".equalsIgnoreCase(StrUtil.trimToEmpty(costFacet))) {
            return true;
        }
        if (StrUtil.isBlank(userMessage)) {
            return false;
        }
        String u = userMessage.toLowerCase(Locale.ROOT);
        if (u.contains("自采") && !(u.contains("采购") || u.contains("进货"))) {
            // 仅问自采时有专用块，不重复拉全量采购
            return false;
        }
        return u.contains("采购") || u.contains("进货");
    }

    /**
     * 本月自采金额：gb_distributer_purchase_goods，gb_DPG_purchase_type = PurchaseOrderType.SELF_PURCHASE（1）。
     */
    public static boolean shouldAttachSelfPurchaseFacts(String userMessage) {
        if (StrUtil.isBlank(userMessage)) {
            return false;
        }
        return userMessage.toLowerCase(Locale.ROOT).contains("自采");
    }

    /**
     * 是否附带「供货商未结账款」事实块（gb_distributer_purchase_batch，status=3 未结账 vs 4 已结账）。
     */
    public static boolean shouldAttachSupplierUnsettledFacts(String userMessage, String costFacet) {
        if ("supplier".equalsIgnoreCase(StrUtil.trimToEmpty(costFacet))) {
            return true;
        }
        if (StrUtil.isBlank(userMessage)) {
            return false;
        }
        String u = userMessage.toLowerCase(Locale.ROOT);
        boolean supplierCtx = u.contains("供货") || u.contains("供应商");
        boolean debtCtx = u.contains("未结") || u.contains("应付") || u.contains("欠款") || u.contains("挂账")
                || (u.contains("结账") && u.contains("未"));
        return supplierCtx && debtCtx;
    }

    /**
     * 是否附带「当前库存账面汇总」（由用户问法触发）。
     */
    public static boolean shouldAttachInventoryFacts(String userMessage) {
        if (StrUtil.isBlank(userMessage)) {
            return false;
        }
        String u = userMessage.toLowerCase(Locale.ROOT);
        if (u.contains("库存") || u.contains("存货") || u.contains("备货")) {
            return true;
        }
        return u.contains("剩") && (u.contains("货") || u.contains("料"));
    }

    /**
     * 与 {@code GbAiChatServiceImpl#inferBroadQuestionFallback} 语义对齐的轻量推断（供 LLM 解析与规则兜底共用）。
     */
    public static boolean inferBroadQuestionFallback(String userMessage) {
        if (StrUtil.isBlank(userMessage) || userMessage.length() > 40) {
            return false;
        }
        boolean topicLoose = userMessage.contains("营业") || userMessage.contains("营收") || userMessage.contains("客流")
                || userMessage.contains("生意") || userMessage.contains("经营") || userMessage.contains("营销")
                || userMessage.contains("成本") || userMessage.contains("利润");
        if (!topicLoose) {
            return false;
        }
        return userMessage.contains("怎么") || userMessage.contains("如何") || userMessage.contains("怎样")
                || (userMessage.contains("建议") && userMessage.length() <= 24);
    }

    private static String inferSkillsCsv(String userMessage) {
        if (StrUtil.isBlank(userMessage)) {
            return "none";
        }
        String u = userMessage.toLowerCase(Locale.ROOT);

        boolean inventory = u.contains("库存") || u.contains("存货") || u.contains("备货")
                || (u.contains("剩") && (u.contains("货") || u.contains("料")));
        if (inventory) {
            return "ai-skill-cost.md";
        }

        boolean supplierUnsettled = shouldAttachSupplierUnsettledFacts(userMessage, null);
        boolean dishCost = isDishCostIntent(u);
        boolean procurement = isProcurementIntent(u) || supplierUnsettled;
        boolean profitPilot = isProfitPilotIntent(u);

        boolean cost = u.contains("成本") || u.contains("损耗") || u.contains("废弃") || u.contains("毛利")
                || u.contains("食材") || u.contains("利润") && (u.contains("薄") || u.contains("低"))
                || u.contains("支出") || u.contains("费用") && u.contains("控")
                || u.contains("采购") || u.contains("进货") || u.contains("自采")
                || supplierUnsettled;
        boolean revenue = u.contains("营业") || u.contains("营收") || u.contains("客流") || u.contains("促销")
                || u.contains("营销") || u.contains("生意") || shouldAttachDishSalesFacts(userMessage);
        boolean extractor = (u.contains("租金") || u.contains("工资") || u.contains("月薪"))
                && (u.contains("记录") || u.contains("登记") || u.contains("帮我记") || u.contains("填"));

        if (dishCost) {
            return "ai-skill-dish-cost-diagnosis.md";
        }
        if (procurement) {
            return "ai-skill-procurement-structure.md";
        }
        if (profitPilot) {
            return "ai-skill-profit-pilot.md";
        }
        if (cost) {
            return "ai-skill-cost.md";
        }
        if (extractor && (u.contains("租金") || u.contains("工资"))) {
            return "ai-skill-data-extractor.md";
        }
        if (revenue) {
            return "ai-skill-revenue-boost.md";
        }
        if (extractor) {
            return "ai-skill-data-extractor.md";
        }
        return "none";
    }

    private static boolean isDishCostIntent(String u) {
        if (u == null) {
            return false;
        }
        boolean dishWord = u.contains("菜") || u.contains("菜品");
        boolean costWord = u.contains("成本") || u.contains("毛利") || u.contains("利润")
                || u.contains("配料") || u.contains("原料") || u.contains("出库");
        if (dishWord && costWord) {
            return true;
        }
        return u.contains("瓶颈原料") || u.contains("卡脖子")
                || (u.contains("哪道菜") && (u.contains("亏") || u.contains("不赚钱")));
    }

    private static boolean isProcurementIntent(String u) {
        if (u == null) {
            return false;
        }
        return u.contains("采购") || u.contains("进货") || u.contains("供应商") || u.contains("供货商")
                || u.contains("应付") || u.contains("未结") || u.contains("挂账")
                || u.contains("结账") || u.contains("自采");
    }

    private static boolean isProfitPilotIntent(String u) {
        if (u == null) {
            return false;
        }
        return u.contains("算账") || u.contains("账本") || u.contains("保本") || u.contains("回本")
                || u.contains("赚不赚钱") || u.contains("盈利") || u.contains("利润盘")
                || (u.contains("这个月") && u.contains("能不能赚"));
    }
}
