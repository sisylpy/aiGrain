package com.nongxinle.ai.conversation;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.followup.FollowUpIntentResolveService;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 采购域口语 → 结构化语义（第一版）。详细说明见 {@code docs/AI_QUERY_SEMANTIC_LEXICON.md}。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AiQuerySemanticLexicon {

    /** 全量采购概览：采购怎么样、拆分、Top、核销等（无来源聚焦或「自采/供货商」整体情况）。 */
    public static final String STRUCTURED_PURCHASE_OVERVIEW_SUMMARY = "purchase_overview_summary";

    public static final String STRUCTURED_PURCHASE_SOURCE_SUMMARY = "purchase_source_summary";

    /** 只问自采/供货商采购「金额」：简答，不输出完整采购报告。 */
    public static final String STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY = "purchase_source_amount_query";

    /** 问「哪些商品」：可输出频次与金额 Top，仍非全量经营式报告。 */
    public static final String STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY = "purchase_source_goods_query";

    /** 供货商/供应商采购金额或笔数排行（走采购概览 Tool；排行 SQL 仅统计真实 {@code nx_supplier_id>0}）。 */
    public static final String STRUCTURED_SUPPLIER_AMOUNT_RANKING = "supplier_amount_ranking";

    /**
     * 同 {@link #STRUCTURED_SUPPLIER_AMOUNT_RANKING}（旧常量名，兼容 Harness / 日志）。
     */
    public static final String STRUCTURED_SUPPLIER_RANKING = STRUCTURED_SUPPLIER_AMOUNT_RANKING;

    public static final String SOURCE_SELF_PURCHASE = "SELF_PURCHASE";
    public static final String SOURCE_SUPPLIER_PURCHASE = "SUPPLIER_PURCHASE";
    public static final String SOURCE_ALL = "ALL";

    /**
     * 出库/核销总金额（类型1–4）；统计口径为基本自然日全集，与日营收日过滤不同。
     * <p>四类命名与库表一致：1 生产耗用；2 废弃/过保鲜期废弃；3 损耗/丢失破损自然损耗（口语常称报损）；4 退货。勿将 type2 泛称为「损耗」或将 type3 误标为「报损」独用名。</p>
     */
    public static final String STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY = "stock_reduce_overview";
    /** gb_dgsr_type=1，wire 值 {@code produce_consume}；制作成本/制作消耗等同源归本类。 */
    public static final String STRUCTURED_PRODUCE_CONSUME = "produce_consume";
    public static final String STRUCTURED_WASTE = "waste";
    public static final String STRUCTURED_LOSS = "loss";
    public static final String STRUCTURED_RETURN = "return";

    /** 商品出库金额（subtotal）排行；不按业务类型拆分。 */
    public static final String STRUCTURED_GOODS_OUTBOUND_RANKING = "goods_outbound_ranking";

    /** 菜品毛利：总览（默认）。 */
    public static final String STRUCTURED_DISH_PROFIT_OVERVIEW = "dish_profit_overview";

    public static final String STRUCTURED_DISH_THEORETICAL_COST = "dish_theoretical_cost";
    public static final String STRUCTURED_DISH_ACTUAL_OUTBOUND_COST = "dish_actual_outbound_cost";
    public static final String STRUCTURED_DISH_COST_GAP = "dish_cost_gap";
    /** 综合毛利率/问「毛利率多少」等（引用行内 blended，禁止心算）。 */
    public static final String STRUCTURED_DISH_GROSS_MARGIN_QUERY = "dish_gross_margin_query";

    public static final String STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN = "dish_profit_ranking_low_margin";
    /** 「某菜为什么毛利低」等原因解释；正文须用工具行内毛利率字段，禁止心算。 */
    public static final String STRUCTURED_DISH_LOW_PROFIT_REASON = "dish_low_profit_reason";
    public static final String STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH = "dish_actual_cost_ranking_high";
    public static final String STRUCTURED_DISH_GAP_RANKING_MAX = "dish_gap_ranking_max";
    public static final String STRUCTURED_DISH_SALES_RANKING = "dish_sales_ranking";
    public static final String STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN = "dish_ingredient_cost_breakdown";

    private static final Pattern DISH_NAME_BEFORE_COST_PHRASE = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,12})(?:的)?(理论成本|实际出库成本|实际成本|毛利率|毛利|差异|差多少)");

    /** 「本月/当月/这周…菜品」+ 紧跟成本词：正则误捕为点名菜，与真实菜名菜名区分。 */
    private static final Set<String> DISH_PROFIT_TIME_PLUS_DISH_BOILERPLATE = Set.of(
            "这个月菜品", "上个月菜品", "本月菜品", "当月菜品", "上月菜品",
            "这周菜品", "本周菜品", "今年菜品", "本季度菜品", "这季度菜品", "上季度菜品");

    /**
     * 根据用户消息补强 {@link AiResolvedQueryIntent} 的采购结构化字段（不单独改 path，path 由关键词/继承决定）。
     */
    public static void mergePurchaseCuesInto(AiResolvedQueryIntent intent, String rawMessage) {
        if (intent == null || rawMessage == null || rawMessage.isBlank()) {
            return;
        }
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(intent.getPathCode())) {
            return;
        }
        if (looksLikeSupplierRanking(rawMessage)) {
            intent.setStructuredIntentDetail(STRUCTURED_SUPPLIER_AMOUNT_RANKING);
            return;
        }
        String s = rawMessage.replace(" ", "").toLowerCase(Locale.ROOT);

        // 供货商/供应商类口径优先于自采，避免「不是自采」等句子里含「自采」子串被误标为自采
        if (matchesSupplierPurchaseCue(s)) {
            intent.setPurchaseSourceType(SOURCE_SUPPLIER_PURCHASE);
            intent.setStructuredIntentDetail(resolveSupplierPurchaseStructuredIntent(s));
            return;
        }
        if (matchesSelfPurchaseCue(s)) {
            intent.setPurchaseSourceType(SOURCE_SELF_PURCHASE);
            intent.setStructuredIntentDetail(resolveSelfPurchaseStructuredIntent(s));
        }
    }

    /**
     * 出库/核销细分意图；若非菜品/采购/经营等已锁定 path 则写入 structuredIntentDetail。
     */
    public static void mergeStockReduceCuesInto(AiResolvedQueryIntent intent, String rawMessage) {
        if (intent == null || rawMessage == null || rawMessage.isBlank()) {
            return;
        }
        String p = intent.getPathCode();
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(p)
                || AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(p)
                || AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(p)) {
            return;
        }
        if (looksLikeGoodsOutboundRanking(rawMessage)) {
            intent.setStructuredIntentDetail(STRUCTURED_GOODS_OUTBOUND_RANKING);
            return;
        }
        String s = rawMessage.replace(" ", "").toLowerCase(Locale.ROOT);
        if ((s.contains("进货") || s.contains("订货") || s.contains("供货商") || s.contains("供应商"))
                && !s.contains("出库") && !s.contains("核销")) {
            return;
        }
        if (matchesProduceConsumeCue(s)) {
            intent.setStructuredIntentDetail(STRUCTURED_PRODUCE_CONSUME);
            return;
        }
        if (matchesWasteCue(s)) {
            intent.setStructuredIntentDetail(STRUCTURED_WASTE);
            return;
        }
        if (matchesLossCue(s)) {
            intent.setStructuredIntentDetail(STRUCTURED_LOSS);
            return;
        }
        if (matchesReturnOutboundCue(s)) {
            intent.setStructuredIntentDetail(STRUCTURED_RETURN);
            return;
        }
    }

    /**
     * 「为什么毛利低 / 为啥不赚钱」等：原因追问，优先于总览与「毛利率多少」。
     */
    public static boolean looksLikeDishLowProfitReasonQuestion(String compactNoSpaces) {
        if (compactNoSpaces == null || compactNoSpaces.isBlank()) {
            return false;
        }
        String s = compactNoSpaces;
        boolean marginLow = s.contains("毛利低") || s.contains("利润低") || s.contains("不赚钱") || s.contains("亏本");
        boolean hasWhy = s.contains("为什么") || s.contains("为啥") || s.contains("怎么回事");
        if (hasWhy && marginLow) {
            return true;
        }
        if (hasWhy && s.contains("毛利") && (s.contains("这么低") || s.contains("那么低") || s.contains("偏低") || s.contains("较差"))) {
            return true;
        }
        if ((s.contains("为何") || s.contains("怎么")) && marginLow) {
            return true;
        }
        return false;
    }

    /**
     * 菜品毛利子意图；仅在 {@link AiResolvedQueryIntent#PATH_DISH_PROFIT} 下写入 {@code structuredIntentDetail}。
     */
    public static void mergeDishProfitCuesInto(AiResolvedQueryIntent intent, String rawMessage) {
        if (intent == null || rawMessage == null || rawMessage.isBlank()) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(intent.getPathCode())) {
            return;
        }
        String s = rawMessage.replace(" ", "");

        if (looksLikeDishLowProfitReasonQuestion(s)) {
            if (StringUtils.hasText(tryExtractDishNameForReasonQuestion(rawMessage))) {
                intent.setStructuredIntentDetail(STRUCTURED_DISH_LOW_PROFIT_REASON);
                return;
            }
        }

        if ((s.contains("菜品毛利") || s.contains("菜品利润")) && (s.contains("怎么样") || s.contains("如何") || s.contains("情况"))) {
            intent.setStructuredIntentDetail(STRUCTURED_DISH_PROFIT_OVERVIEW);
            return;
        }
        if (s.contains("全部门店") || s.contains("集团") && (s.contains("呢") || s.contains("吧"))) {
            intent.setStructuredIntentDetail(STRUCTURED_DISH_PROFIT_OVERVIEW);
            return;
        }

        if ((s.contains("哪个") || s.contains("哪些") || s.contains("哪家") || s.contains("哪道菜") || s.contains("什么菜"))
                && s.contains("毛利")
                && (s.contains("最低") || s.contains("最差") || s.contains("亏") || s.contains("最少"))) {
            intent.setStructuredIntentDetail(STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN);
            return;
        }
        if ((s.contains("哪个") || s.contains("哪些") || s.contains("哪道菜") || s.contains("什么菜"))
                && (s.contains("实际成本") || s.contains("出库成本"))
                && (s.contains("最高") || s.contains("最多") || s.contains("最大"))) {
            intent.setStructuredIntentDetail(STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH);
            return;
        }
        if ((s.contains("哪个") || s.contains("哪些"))
                && (s.contains("差异") || (s.contains("理论") && s.contains("实际")))
                && (s.contains("最大") || s.contains("最多") || s.contains("最差"))) {
            intent.setStructuredIntentDetail(STRUCTURED_DISH_GAP_RANKING_MAX);
            return;
        }
        if ((s.contains("销量") || s.contains("卖得")) && (s.contains("排行") || s.contains("排名") || s.contains("最高") || s.contains("最多"))) {
            intent.setStructuredIntentDetail(STRUCTURED_DISH_SALES_RANKING);
            return;
        }
        if (s.contains("配料") || s.contains("原料") && s.contains("成本") || s.contains("bom")) {
            intent.setStructuredIntentDetail(STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN);
            return;
        }
        if (s.contains("理论成本")) {
            intent.setStructuredIntentDetail(STRUCTURED_DISH_THEORETICAL_COST);
            return;
        }
        // 「出库成本多少」= type1 实际出库汇总（「实际出库成本」已含「出库成本」子串）
        if (s.contains("出库成本") && !s.contains("理论")) {
            intent.setStructuredIntentDetail(STRUCTURED_DISH_ACTUAL_OUTBOUND_COST);
            return;
        }
        if (s.contains("实际成本") && !s.contains("理论")) {
            intent.setStructuredIntentDetail(STRUCTURED_DISH_ACTUAL_OUTBOUND_COST);
            return;
        }
        // 与 stock_reduce 的「生产耗用」专线区分：仅在 dish_profit path 下将口语「制作成本」视为实际出库汇总口径
        if ((s.contains("制作成本") || s.contains("制作消耗") || s.contains("做菜成本")) && !s.contains("理论")) {
            intent.setStructuredIntentDetail(STRUCTURED_DISH_ACTUAL_OUTBOUND_COST);
            return;
        }
        if (s.contains("差异") || s.contains("差多少") || (s.contains("理论") && s.contains("实际") && s.contains("差"))) {
            intent.setStructuredIntentDetail(STRUCTURED_DISH_COST_GAP);
            return;
        }
        if (s.contains("毛利率") || (s.contains("毛利") && (s.contains("多少") || s.contains("几个点")))) {
            intent.setStructuredIntentDetail(STRUCTURED_DISH_GROSS_MARGIN_QUERY);
            return;
        }
    }

    /** 本条是否带有可解析的菜品毛利子意图（用于多轮「实际成本呢」类仅换口径）。 */
    public static boolean dishProfitStructuredIntentFromUtterance(String norm) {
        if (!StringUtils.hasText(norm)) {
            return false;
        }
        AiResolvedQueryIntent probe = AiResolvedQueryIntent.builder()
                .pathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                .build();
        mergeDishProfitCuesInto(probe, norm);
        return StringUtils.hasText(probe.getStructuredIntentDetail());
    }

    /**
     * 仅切换理论/实际/差异/毛利率口径的极短追问（继承上一轮时间与菜品主题）。
     */
    public static boolean looksLikeDishProfitMetricOnlyFollowUp(String norm) {
        if (!StringUtils.hasText(norm) || norm.length() > 36) {
            return false;
        }
        String s = norm.replace(" ", "");
        if (s.contains("门店") || s.contains("店") && s.contains("呢")) {
            return false;
        }
        return s.endsWith("呢") || s.endsWith("吗") || s.endsWith("吧")
                || s.equals("实际成本") || s.equals("出库成本") || s.equals("制作成本") || s.equals("理论成本")
                || s.equals("毛利率")
                || s.equals("差异");
    }

    public static boolean isStructuredDishProfitDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = structuredIntentDetail.trim();
        return STRUCTURED_DISH_PROFIT_OVERVIEW.equals(t) || isNonOverviewDishProfitStructuredDetail(t);
    }

    public static boolean isNonOverviewDishProfitStructuredDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = structuredIntentDetail.trim();
        if (STRUCTURED_DISH_PROFIT_OVERVIEW.equals(t)) {
            return false;
        }
        return STRUCTURED_DISH_THEORETICAL_COST.equals(t) || STRUCTURED_DISH_ACTUAL_OUTBOUND_COST.equals(t)
                || STRUCTURED_DISH_COST_GAP.equals(t) || STRUCTURED_DISH_GROSS_MARGIN_QUERY.equals(t)
                || STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(t) || STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH.equals(t)
                || STRUCTURED_DISH_LOW_PROFIT_REASON.equals(t)
                || STRUCTURED_DISH_GAP_RANKING_MAX.equals(t) || STRUCTURED_DISH_SALES_RANKING.equals(t)
                || STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN.equals(t);
    }

    public static boolean messageDeclaresExplicitDishProfitOverview(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        String s = stripForDishProfitHintToken(rawMessage);
        return (s.contains("菜品毛利") || s.contains("菜品利润")) && (s.contains("怎么样") || s.contains("总览"));
    }

    /**
     * 从用户话术中提取点名菜名（菜品毛利 path）；用于多轮继承与 Tool 收窄。
     */
    public static String extractDishNameHint(String rawMessage, AiResolvedQueryIntent qi) {
        if (rawMessage == null || qi == null || !AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(qi.getPathCode())) {
            return null;
        }
        String s = stripForDishProfitHintToken(rawMessage);
        Matcher m = DISH_NAME_BEFORE_COST_PHRASE.matcher(s);
        if (m.find()) {
            String name = stripForDishProfitHintToken(m.group(1));
            if (StringUtils.hasText(name) && !isGenericNonDishToken(name)
                    && !isDishProfitOverviewBoilerplateBeforeCostWord(name)) {
                return name.trim();
            }
        }
        Matcher mWhyLead = Pattern.compile("^([\\u4e00-\\u9fa5]{2,20})(?:为什么|为啥)").matcher(s);
        if (mWhyLead.find()) {
            String name = stripForDishProfitHintToken(mWhyLead.group(1));
            if (StringUtils.hasText(name) && !isGenericNonDishToken(name)
                    && !isDishProfitOverviewBoilerplateBeforeCostWord(name)) {
                return name.trim();
            }
        }
        Matcher mWhyMid = Pattern.compile("(?:为什么|为啥)([\\u4e00-\\u9fa5]{2,20})(?:的)?(?:毛利|利润)").matcher(s);
        if (mWhyMid.find()) {
            String name = stripForDishProfitHintToken(mWhyMid.group(1));
            if (StringUtils.hasText(name) && !isGenericNonDishToken(name)
                    && !isDishProfitOverviewBoilerplateBeforeCostWord(name)) {
                return name.trim();
            }
        }
        Matcher m2 = Pattern.compile("^([\\u4e00-\\u9fa5]{2,10})(?:怎么样|如何|呢)[？?!！。…]*$").matcher(s);
        if (m2.find()) {
            String name = stripForDishProfitHintToken(m2.group(1));
            if (StringUtils.hasText(name) && !isGenericNonDishToken(name)
                    && !isDishProfitOverviewBoilerplateBeforeCostWord(name)) {
                return name.trim();
            }
        }
        return null;
    }

    /**
     * 不依赖 path 已解析：用于在「为什么毛利低」句子里抢提菜名，以及会话修复。
     */
    public static String tryExtractDishNameForReasonQuestion(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return null;
        }
        AiResolvedQueryIntent probe = AiResolvedQueryIntent.builder()
                .pathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                .build();
        return extractDishNameHint(rawMessage, probe);
    }

    /** 去 BOM / 各类空白，避免误捕菜名与前缀判断失配。 */
    private static String stripForDishProfitHintToken(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.stripLeading().replaceFirst("^\uFEFF+", "").replaceAll("[\\s\\u3000]+", "");
    }

    private static boolean isGenericNonDishToken(String name) {
        if (name == null) {
            return true;
        }
        return name.equals("这个月") || name.equals("上个月") || name.equals("本月") || name.equals("菜品")
                || name.equals("门店") || name.equals("集团") || name.equals("全部门店");
    }

    /**
     * 「上个月/本月…菜品毛利怎么样」里，成本词前的 {2,12} 会误吞「上个月菜品」「这个月的菜品」等总览话术，不能当作点名菜。
     */
    private static boolean isDishProfitOverviewBoilerplateBeforeCostWord(String name) {
        if (name == null) {
            return true;
        }
        String n = stripForDishProfitHintToken(name);
        if (!n.endsWith("菜品")) {
            return false;
        }
        if (DISH_PROFIT_TIME_PLUS_DISH_BOILERPLATE.contains(n)) {
            return true;
        }
        if (n.startsWith("当月") || n.startsWith("这周") || n.startsWith("本周") || n.startsWith("今年")
                || n.startsWith("本季度") || n.startsWith("这季度") || n.startsWith("上季度")) {
            return true;
        }
        if (n.startsWith("上个月") || n.startsWith("这个月") || n.startsWith("本月")
                || n.startsWith("上月") || n.startsWith("下个月") || n.startsWith("下月")
                || n.contains("月的菜品")) {
            return true;
        }
        // 「近3月菜品」「近三月菜品」等总览话术
        if (n.matches("近\\d{1,2}个?月菜品") || n.matches("近[一二三四五六七八九十两]{1,3}月菜品")) {
            return true;
        }
        return false;
    }

    /**
     * Harness/Debug：由 wire 映射为可读 metric 枚举（非 wire）。
     */
    public static String dishProfitMetricTypeFromStructuredWire(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        String t = wire.trim();
        return switch (t) {
            case STRUCTURED_DISH_PROFIT_OVERVIEW -> "OVERVIEW";
            case STRUCTURED_DISH_THEORETICAL_COST -> "THEORETICAL_COST";
            case STRUCTURED_DISH_ACTUAL_OUTBOUND_COST -> "ACTUAL_OUTBOUND_COST";
            case STRUCTURED_DISH_COST_GAP -> "COST_GAP";
            case STRUCTURED_DISH_GROSS_MARGIN_QUERY -> "GROSS_MARGIN";
            case STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN -> "RANKING_LOW_MARGIN";
            case STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH -> "RANKING_HIGH_ACTUAL_COST";
            case STRUCTURED_DISH_GAP_RANKING_MAX -> "RANKING_MAX_GAP";
            case STRUCTURED_DISH_SALES_RANKING -> "RANKING_SALES";
            case STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN -> "INGREDIENT_BREAKDOWN";
            case STRUCTURED_DISH_LOW_PROFIT_REASON -> "LOW_PROFIT_REASON";
            default -> wireToScreamingSnake(t);
        };
    }

    public static boolean looksLikeGoodsOutboundRanking(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        String s = rawMessage.replace(" ", "");
        boolean outbound = s.contains("出库") || s.contains("核销");
        boolean goodsCue = s.contains("商品") || s.contains("货品") || s.contains("东西");
        if (!outbound && !goodsCue) {
            return false;
        }
        return s.contains("哪个") || s.contains("哪家") || s.contains("谁家")
                || s.contains("最高") || s.contains("最多") || s.contains("第一大")
                || s.contains("排行") || s.contains("排名");
    }

    public static boolean isStructuredStockReduceDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = structuredIntentDetail.trim();
        return STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY.equals(t)
                || STRUCTURED_PRODUCE_CONSUME.equals(t)
                || STRUCTURED_WASTE.equals(t)
                || STRUCTURED_LOSS.equals(t)
                || STRUCTURED_RETURN.equals(t)
                || STRUCTURED_GOODS_OUTBOUND_RANKING.equals(t);
    }

    /** 出库结构化子意图且非「四类总览」：多轮换店/换时间时须保留（排行、分项等）。 */
    public static boolean isNonOverviewStockReduceStructuredDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = structuredIntentDetail.trim();
        if (STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY.equals(t)) {
            return false;
        }
        return isStructuredStockReduceDetail(t);
    }

    /**
     * 用户明确要求改查「出库/核销四类总金额或总览」时，才应从排行/分项退回总览。
     */
    public static boolean messageDeclaresExplicitStockReduceOverview(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        String s = rawMessage.replace(" ", "");
        if (s.contains("出库总览") || s.contains("核销总览")) {
            return true;
        }
        if (s.contains("总出库") || s.contains("出库合计") || s.contains("核销合计")) {
            return s.contains("多少") || s.contains("钱") || s.contains("元") || s.contains("金额");
        }
        if (s.contains("四类") && (s.contains("合计") || s.contains("多少"))) {
            return true;
        }
        return (s.contains("出库") || s.contains("核销"))
                && (s.contains("一共") || s.contains("总共"))
                && (s.contains("多少") || s.contains("钱"));
    }

    private static boolean matchesProduceConsumeCue(String s) {
        return s.contains("生产耗用") || s.contains("生产成本") || s.contains("成本耗用")
                || s.contains("制作成本") || s.contains("制作消耗") || s.contains("做菜成本")
                || s.contains("菜品制作消耗") || s.contains("正常制作消耗")
                || (s.contains("出品") && !(s.contains("菜品") || s.contains("毛利") || s.contains("利润")));
    }

    /** type=2：废弃（过保鲜期/过期/变质等），勿与 type=3「损耗」混称。 */
    private static boolean matchesWasteCue(String s) {
        return s.contains("废弃") || s.contains("废弃成本") || s.contains("过期废弃") || s.contains("过保鲜期")
                || s.contains("变质废弃") || s.contains("保鲜期过了") || s.contains("waste");
    }

    /** type=3：损耗（丢失/破损/自然损耗/盘亏等），「报损」口语归本类。 */
    private static boolean matchesLossCue(String s) {
        return s.contains("损耗") || s.contains("损耗成本") || s.contains("丢失") || s.contains("破损")
                || s.contains("自然损耗") || s.contains("盘亏") || s.contains("报损") || s.contains("损失")
                || s.contains("loss");
    }

    private static boolean matchesReturnOutboundCue(String s) {
        if (!s.contains("退货") && !s.contains("退回")) {
            return false;
        }
        return s.contains("出库") || s.contains("核销")
                || s.contains("多少") || s.contains("金额") || s.contains("钱")
                || s.contains("呢") || s.contains("吧");
    }

    /**
     * 本句经 {@link #mergeStockReduceCuesInto} 是否落在结构化出库子意图（含排行）；用于与泛「成本诊断」分流。
     */
    public static boolean mapsToStructuredStockReduceDetailCue(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        AiResolvedQueryIntent probe = AiResolvedQueryIntent.builder().build();
        mergeStockReduceCuesInto(probe, rawMessage);
        String sid = probe.getStructuredIntentDetail();
        return sid != null && !sid.isBlank() && isStructuredStockReduceDetail(sid);
    }

    /**
     * 本句是否在词典层明确带了采购来源（用于多轮：有则不应盲继承上一轮的 purchaseSourceType）。
     */
    public static boolean messageDeclaresExplicitPurchaseSource(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        if (looksLikeSupplierRanking(rawMessage)) {
            return true;
        }
        String s = rawMessage.replace(" ", "").toLowerCase(Locale.ROOT);
        return matchesSupplierPurchaseCue(s) || matchesSelfPurchaseCue(s);
    }

    private static String resolveSelfPurchaseStructuredIntent(String s) {
        if (matchesPurchaseGoodsListCue(s)) {
            return STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY;
        }
        if (matchesPurchaseAmountCue(s)) {
            return STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY;
        }
        if (matchesBroadPurchaseSituationCue(s)) {
            return STRUCTURED_PURCHASE_OVERVIEW_SUMMARY;
        }
        if (matchesPurchaseCountCue(s)) {
            return STRUCTURED_PURCHASE_SOURCE_SUMMARY;
        }
        return STRUCTURED_PURCHASE_SOURCE_SUMMARY;
    }

    private static String resolveSupplierPurchaseStructuredIntent(String s) {
        if (matchesPurchaseGoodsListCue(s)) {
            return STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY;
        }
        if (matchesPurchaseAmountCue(s)) {
            return STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY;
        }
        if (matchesBroadPurchaseSituationCue(s)) {
            return STRUCTURED_PURCHASE_OVERVIEW_SUMMARY;
        }
        if (matchesPurchaseCountCue(s)) {
            return STRUCTURED_PURCHASE_SOURCE_SUMMARY;
        }
        return STRUCTURED_PURCHASE_SOURCE_SUMMARY;
    }

    private static boolean matchesPurchaseGoodsListCue(String s) {
        return s.contains("哪些商品") || s.contains("什么商品") || s.contains("啥商品")
                || s.contains("哪些货") || s.contains("什么货") || s.contains("买了啥") || s.contains("买的啥")
                || s.contains("采购哪些") || s.contains("采了哪些") || s.contains("进了哪些") || s.contains("进了什么");
    }

    private static boolean matchesPurchaseAmountCue(String s) {
        return s.contains("金额") || s.contains("多少钱") || s.contains("多少元") || s.contains("货款")
                || s.contains("价款") || s.contains("价钱");
    }

    private static boolean matchesBroadPurchaseSituationCue(String s) {
        return s.contains("怎么样") || s.contains("如何") || s.contains("好不好") || s.contains("行不行")
                || s.contains("概况") || s.contains("情况");
    }

    private static boolean matchesPurchaseCountCue(String s) {
        return s.contains("有多少") || s.contains("几笔") || s.contains("多少笔") || s.contains("笔数")
                || s.contains("几条");
    }

    /**
     * 是否为供货商/供应商金额（或笔数）排行子意图（兼容历史值 {@code supplier_ranking}）。
     */
    public static boolean isSupplierAmountRankingDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = structuredIntentDetail.trim();
        return STRUCTURED_SUPPLIER_AMOUNT_RANKING.equals(t) || "supplier_ranking".equals(t);
    }

    /**
     * 将 wire 值（如 {@code supplier_amount_ranking}）转为调试面板用大写枚举名（{@code SUPPLIER_AMOUNT_RANKING}）。
     * 未知 wire 则按分段转大写并用 {@code _} 连接。
     */
    public static String toStructuredIntentDetailDebugCode(String structuredIntentDetailWire) {
        if (structuredIntentDetailWire == null || structuredIntentDetailWire.isBlank()) {
            return null;
        }
        String w = structuredIntentDetailWire.trim();
        if (STRUCTURED_SUPPLIER_AMOUNT_RANKING.equals(w) || "supplier_ranking".equals(w)) {
            return "SUPPLIER_AMOUNT_RANKING";
        }
        if (STRUCTURED_PURCHASE_OVERVIEW_SUMMARY.equals(w)) {
            return "PURCHASE_OVERVIEW_SUMMARY";
        }
        if (STRUCTURED_PURCHASE_SOURCE_SUMMARY.equals(w)) {
            return "PURCHASE_SOURCE_SUMMARY";
        }
        if (STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(w)) {
            return "PURCHASE_SOURCE_AMOUNT_QUERY";
        }
        if (STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(w)) {
            return "PURCHASE_SOURCE_GOODS_QUERY";
        }
        if (STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY.equals(w)) {
            return "STOCK_REDUCE_OVERVIEW";
        }
        if (STRUCTURED_PRODUCE_CONSUME.equals(w)) {
            return "PRODUCE_CONSUME";
        }
        if (STRUCTURED_WASTE.equals(w)) {
            return "WASTE";
        }
        if (STRUCTURED_LOSS.equals(w)) {
            return "LOSS";
        }
        if (STRUCTURED_RETURN.equals(w)) {
            return "RETURN";
        }
        if (STRUCTURED_GOODS_OUTBOUND_RANKING.equals(w)) {
            return "GOODS_OUTBOUND_RANKING";
        }
        if (STRUCTURED_DISH_PROFIT_OVERVIEW.equals(w)) {
            return "DISH_PROFIT_OVERVIEW";
        }
        if (STRUCTURED_DISH_THEORETICAL_COST.equals(w)) {
            return "DISH_THEORETICAL_COST";
        }
        if (STRUCTURED_DISH_ACTUAL_OUTBOUND_COST.equals(w)) {
            return "DISH_ACTUAL_OUTBOUND_COST";
        }
        if (STRUCTURED_DISH_COST_GAP.equals(w)) {
            return "DISH_COST_GAP";
        }
        if (STRUCTURED_DISH_GROSS_MARGIN_QUERY.equals(w)) {
            return "DISH_GROSS_MARGIN_QUERY";
        }
        if (STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(w)) {
            return "DISH_LOW_PROFIT_RANKING";
        }
        if (STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH.equals(w)) {
            return "DISH_ACTUAL_COST_RANKING";
        }
        if (STRUCTURED_DISH_LOW_PROFIT_REASON.equals(w)) {
            return "DISH_LOW_PROFIT_REASON";
        }
        if (STRUCTURED_DISH_GAP_RANKING_MAX.equals(w)) {
            return "DISH_GAP_RANKING_MAX";
        }
        if (STRUCTURED_DISH_SALES_RANKING.equals(w)) {
            return "DISH_SALES_RANKING";
        }
        if (STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN.equals(w)) {
            return "DISH_INGREDIENT_COST_BREAKDOWN";
        }
        return wireToScreamingSnake(w);
    }

    private static String wireToScreamingSnake(String wire) {
        String[] parts = wire.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('_');
            }
            sb.append(p.toUpperCase(Locale.ROOT));
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /** 「哪个供货商最多」类追问；勿与 {@link #mergePurchaseCuesInto} 的「供应商采购」口径混淆。 */
    public static boolean looksLikeSupplierRanking(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        String s = rawMessage.replace(" ", "");
        boolean sup = s.contains("供货") || s.contains("供应");
        if (!sup) {
            return false;
        }
        return s.contains("哪个") || s.contains("谁家") || s.contains("哪一家")
                || s.contains("最多") || s.contains("最大") || s.contains("最高")
                || s.contains("金额最高") || s.contains("采购金额最高") || s.contains("订货金额")
                || s.contains("订货高") || s.contains("订货多")
                || s.contains("排行") || s.contains("排名") || s.contains("排序") || s.contains("榜单")
                || s.contains("第一")
                || s.contains("采购最多") || s.contains("金额排名") || s.contains("采购排名")
                || s.contains("供应商排行") || s.contains("供货商排行");
    }

    /**
     * 明确要「采购总览 / 单品或商品维度排行」，不应强行收敛为「仅供货商金额排行」话术（与{@link #looksLikeSupplierRanking} 搭配使用）。
     */
    public static boolean looksLikeExplicitPurchaseGeneralOverviewOrGoodsRankingOnly(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        String x = rawMessage.replace(" ", "");
        if (x.contains("采购总览")) {
            return true;
        }
        if (x.contains("单品") && (x.contains("排行") || x.contains("排名"))) {
            return true;
        }
        if ((x.contains("货品") || x.contains("商品")) && (x.contains("金额排行") || x.contains("金额排名"))) {
            return true;
        }
        if (x.contains("商品排行") || x.contains("商品排名")) {
            return true;
        }
        return false;
    }

    private static boolean matchesSelfPurchaseCue(String s) {
        if (s.contains("不是自采") || s.contains("非自采")) {
            return false;
        }
        return s.contains("自采购") || s.contains("自采") || s.contains("自己采购") || s.contains("自己买") || s.contains("自己买的")
                || s.contains("门店自采")
                || s.contains("市场买") || s.contains("市场买的") || s.contains("菜场买");
    }

    /**
     * 「订货」仅在出现供货商/供应商/批发商/配送商等锚点时才视为供货渠道口径，避免单独「订货」误判。
     */
    private static boolean matchesSupplierPurchaseCue(String s) {
        if (s.contains("不是自采") || s.contains("非自采")) {
            return true;
        }
        if (s.contains("供货商采购") || s.contains("供应商采购") || s.contains("向供货商采购") || s.contains("向供应商采购")) {
            return true;
        }
        if (s.contains("供货商订货") || s.contains("供应商订货")
                || s.contains("供货商送货") || s.contains("供应商送货")
                || s.contains("供货商那边") || s.contains("供应商那边")) {
            return true;
        }
        if (s.contains("批发商") && (s.contains("送来") || s.contains("送的") || s.contains("送货") || s.contains("订货"))) {
            return true;
        }
        if (s.contains("配送商")
                && (s.contains("送来") || s.contains("送的") || s.contains("送货") || s.contains("订货") || s.contains("采购"))) {
            return true;
        }
        boolean supplierWord = s.contains("供货商") || s.contains("供应商");
        if (supplierWord && (s.contains("采购") || s.contains("订货") || s.contains("送货"))) {
            return true;
        }
        if (s.contains("送货商") && (s.contains("采购") || s.contains("订货") || s.contains("送货"))) {
            return true;
        }
        return false;
    }

    /**
     * 上一轮为采购概览时，本轮极短追问可只点供货/自采渠道。
     *
     * @return {@code true} 若已进入短追问包络但未设置 {@code purchaseSourceType}（且仍为 blank），便于 Harness 记录未知语义
     */
    public static boolean augmentPurchaseOverviewSourceFromShortCue(
            AiResolvedQueryIntent merged, String rawMessage, AiConversationTurnMemory previousTurn) {
        if (merged == null || rawMessage == null || previousTurn == null) {
            return false;
        }
        if (!AiResolvedQueryIntent.PURCHASE_OVERVIEW.equals(previousTurn.getLastIntentCode())
                || !AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(previousTurn.getLastPathCode())) {
            return false;
        }
        if (!AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(merged.getPathCode())) {
            return false;
        }
        String norm = rawMessage.trim();
        if (FollowUpIntentResolveService.currentMessageDeclaresDomainPath(norm)) {
            return false;
        }
        if (looksLikeSupplierRanking(norm)) {
            return false;
        }
        String c = norm.replace(" ", "").toLowerCase(Locale.ROOT);
        if (!purchaseOverviewShortSourceFollowEnvelope(c)) {
            return false;
        }
        if (bareSupplierPurchaseChannelShorthand(norm, c)) {
            merged.setPurchaseSourceType(SOURCE_SUPPLIER_PURCHASE);
            merged.setStructuredIntentDetail(structuredIntentForOverviewShortCue(merged.getStructuredIntentDetail()));
            return false;
        }
        if (bareSelfPurchaseChannelShorthand(c)) {
            merged.setPurchaseSourceType(SOURCE_SELF_PURCHASE);
            merged.setStructuredIntentDetail(structuredIntentForOverviewShortCue(merged.getStructuredIntentDetail()));
            return false;
        }
        String pst = merged.getPurchaseSourceType();
        return pst == null || pst.isBlank();
    }

    /** 短语追问长度上限（与时间短句上限对齐）。 */
    private static final int PURCHASE_OVERVIEW_SHORT_SOURCE_LEN = 40;

    private static boolean purchaseOverviewShortSourceFollowEnvelope(String c) {
        if (c.length() > PURCHASE_OVERVIEW_SHORT_SOURCE_LEN) {
            return false;
        }
        return true;
    }

    /** 「供货商呢？」类：仅有渠道词与语气词，尚未被 {@link #matchesSupplierPurchaseCue} 覆盖。 */
    private static boolean bareSupplierPurchaseChannelShorthand(String normTrim, String compactLower) {
        if (matchesSupplierPurchaseCue(compactLower)) {
            return false;
        }
        if (looksLikeSupplierRanking(normTrim)) {
            return false;
        }
        if (!(compactLower.contains("供货商") || compactLower.contains("供应商")
                || compactLower.contains("送货商") || compactLower.contains("配送商"))) {
            return false;
        }
        String core = compactLower.replaceAll("[\\p{Z}\\s]+", "");
        core = core.replaceAll("[呢吗吧嘛啊呀噢哦咦诶嘿哇]+", "");
        core = core.replaceAll("[\\p{Punct}。？?！!.…]+$", "");
        return core.length() >= 2 && core.length() <= 12;
    }

    private static boolean bareSelfPurchaseChannelShorthand(String compactLower) {
        if (matchesSupplierPurchaseCue(compactLower)) {
            return false;
        }
        if (!matchesSelfPurchaseCue(compactLower)) {
            return false;
        }
        // 「自采购呢」已由 mergePurchaseCuesInto 命中时可不来此；冗余设置无害
        return bareSelfStemOnly(compactLower);
    }

    /**
     * 短句仅剩自采语义核（可加「呢」），避免过长业务句误判。
     */
    private static boolean bareSelfStemOnly(String c) {
        int len = c.replaceAll("[\\s呢吗吧嘛啊？?!！。…，,\\.]+", "").length();
        return len <= 12;
    }

    /** 追问未展开到「哪种商品」等业务时，落为金额简答以保持与口语「…呢？」一致的篇幅。 */
    private static String structuredIntentForOverviewShortCue(String current) {
        if (current != null && !current.isBlank()
                && (STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(current)
                || isSupplierAmountRankingDetail(current))) {
            return current;
        }
        if (current != null && STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(current)) {
            return current;
        }
        return STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY;
    }

    /** 是否限定为「仅采购概述」问法（配合 Planner 使用） */
    public static boolean looksPurchaseDomainShortQuestion(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        String x = normalizedMessage.replace(" ", "");
        boolean purchaseCue = x.contains("采购") || x.contains("进货") || x.contains("订货")
                || matchesSelfPurchaseCue(x.toLowerCase(Locale.ROOT))
                || x.contains("供货商") || x.contains("供应商");
        if (!purchaseCue) {
            return false;
        }
        if (x.contains("成本") || x.contains("毛利") || x.contains("菜品利润")) {
            return false;
        }
        return x.contains("多少") || x.contains("怎么样") || x.contains("如何") || x.contains("情况")
                || x.contains("笔") || x.contains("金额");
    }
}
