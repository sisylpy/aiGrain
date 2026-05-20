package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.agent.business.BusinessDiagnosisAgentV1;
import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.harness.followup.DishProfitDrilldownMatrix;
import com.nongxinle.ai.harness.followup.DishSalesDrilldownMatrix;
import com.nongxinle.ai.harness.followup.RevenueDrilldownMatrix;
import com.nongxinle.ai.harness.followup.StockReduceDrilldownMatrix;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置 Replay 断言：与 {@code docs/AI_HARNESS_REPLAY_CASES.md} Case 1 对齐。
 */
public final class AiHarnessBuiltinCases {

    /** 文档 Case 1：采购金额多轮追问（集团 admin=0）。 */
    public static final String PURCHASE_MULTITURN_1 = "PURCHASE_MULTITURN_1";

    /**
     * 多门店「公共范围层」回归：两行店名触发 GROUP 对比，防误收成单 STORE。
     * <p>
     * 消息依次为：营业额 / 采购金额 / 出库金额。第 2、3 轮仅强约束语义店名 +
     * {@code visibleStoreRootIds} 基数与路径；完整 AnswerPlan 不在此测。
     * </p>
     * {@code visibleStoreRootIds}/{@code expandedSqlDepartmentIds}/{@code queryStoreIds} 占位与文档对齐；
     * 环境与树不一致时请 {@link AiHarnessReplayRequest#strictStoreSqlMatch} = false，仅断言店名与非单店计数。
     */
    public static final String MULTI_STORE_PUBLIC_SCOPE_BLOCK3 = "MULTI_STORE_PUBLIC_SCOPE_BLOCK3";

    /**
     * 五条链路口径回放：单月至今默认时间 + 双排门店营业额/毛利/采购/出库须保持 GROUP≥2。
     */
    public static final String MULTI_STORE_GLOBAL_LINKS_CONFIRM_5 = "MULTI_STORE_GLOBAL_LINKS_CONFIRM_5";

    /**
     * 语义锚点：`frozenClockDate` 对应的 LocalDate；
     * 「本月至今」为该月 1 日～锚点；「上个月」为其上一自然月闭合区间。
     */
    public record LocalDateAnchor(LocalDate frozenClock) {

        public static LocalDateAnchor frozenClock(LocalDate today) {
            return new LocalDateAnchor(today);
        }

        public String monthStartInclusive() {
            return frozenClock.withDayOfMonth(1).toString();
        }

        public String monthToDateInclusive() {
            return frozenClock.toString();
        }

        public String previousMonthFirstDay() {
            YearMonth ym = YearMonth.from(frozenClock).minusMonths(1);
            return ym.atDay(1).toString();
        }

        public String previousMonthLastDay() {
            YearMonth ym = YearMonth.from(frozenClock).minusMonths(1);
            return ym.atEndOfMonth().toString();
        }
    }

    /**
     * Case 1 预期链。
     * <ul>
     * <li>{@code visibleStoreRootIds}：集团 [1,3]、AAA→[1]、汀兰→[3] — 占位；环境与文档不一致时请将 {@link AiHarnessReplayRequest#strictStoreSqlMatch} = false。</li>
     * <li>第 6 轮校验收货渠道 {@code SUPPLIER_PURCHASE}；第 7 轮供货商排行 {@code structuredIntentDetail=supplier_amount_ranking}。</li>
     * </ul>
     */
    public static List<AiHarnessReplayExpectedRound> expectationsPurchaseMultiturn1(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        r1.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r1.setEffectivePathCode("purchase_overview_path");
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(a.monthStartInclusive());
        r1.setEndDate(a.monthToDateInclusive());
        r1.setScopeType("GROUP");
        r1.getVisibleStoreRootIds().add(1L);
        r1.getVisibleStoreRootIds().add(3L);
        r1.setCheckPurchaseSourceType(Boolean.TRUE);
        r1.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL);
        list.add(r1);

        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        r2.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r2.setEffectivePathCode("purchase_overview_path");
        r2.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("CURRENT_MESSAGE_EXPLICIT", "SEMANTIC_EXPLICIT", "INHERITED_PREVIOUS"));
        r2.setStartDate(p0);
        r2.setEndDate(p1);
        r2.setScopeType("GROUP");
        r2.getVisibleStoreRootIds().add(1L);
        r2.getVisibleStoreRootIds().add(3L);
        r2.setCheckPurchaseSourceType(Boolean.TRUE);
        r2.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL);
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        r3.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r3.setEffectivePathCode("purchase_overview_path");
        r3.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("INHERITED_PREVIOUS", "SEMANTIC_EXPLICIT", "CURRENT_MESSAGE_EXPLICIT", "DEFAULT_MONTH_TO_DATE"));
        r3.setStartDate(p0);
        r3.setEndDate(p1);
        r3.setScopeType("STORE");
        r3.getVisibleStoreRootIds().add(1L);
        r3.setCheckPurchaseSourceType(Boolean.TRUE);
        r3.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL);
        r3.setMentionedStore("AAA");
        list.add(r3);

        AiHarnessReplayExpectedRound r4 = new AiHarnessReplayExpectedRound();
        r4.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r4.setEffectivePathCode("purchase_overview_path");
        r4.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("INHERITED_PREVIOUS", "SEMANTIC_EXPLICIT", "CURRENT_MESSAGE_EXPLICIT", "DEFAULT_MONTH_TO_DATE"));
        r4.setStartDate(p0);
        r4.setEndDate(p1);
        r4.setScopeType("STORE");
        r4.getVisibleStoreRootIds().add(1L);
        r4.setCheckPurchaseSourceType(Boolean.TRUE);
        r4.getPurchaseSourceTypeAnyOf().add(AiQuerySemanticLexicon.SOURCE_ALL);
        r4.getPurchaseSourceTypeAnyOf().add(AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE);
        r4.setMentionedStore("AAA");
        list.add(r4);

        AiHarnessReplayExpectedRound r5 = new AiHarnessReplayExpectedRound();
        r5.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r5.setEffectivePathCode("purchase_overview_path");
        r5.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("INHERITED_PREVIOUS", "SEMANTIC_EXPLICIT", "CURRENT_MESSAGE_EXPLICIT", "DEFAULT_MONTH_TO_DATE"));
        r5.setStartDate(p0);
        r5.setEndDate(p1);
        r5.setScopeType("STORE");
        r5.getVisibleStoreRootIds().add(3L);
        r5.setCheckPurchaseSourceType(Boolean.TRUE);
        r5.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE);
        r5.setMentionedStore("汀兰餐厅");
        list.add(r5);

        AiHarnessReplayExpectedRound r6 = new AiHarnessReplayExpectedRound();
        r6.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r6.setEffectivePathCode("purchase_overview_path");
        r6.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("INHERITED_PREVIOUS", "SEMANTIC_EXPLICIT", "CURRENT_MESSAGE_EXPLICIT", "DEFAULT_MONTH_TO_DATE"));
        r6.setStartDate(p0);
        r6.setEndDate(p1);
        r6.setScopeType("STORE");
        r6.getVisibleStoreRootIds().add(3L);
        r6.setCheckPurchaseSourceType(Boolean.TRUE);
        r6.setPurchaseSourceType("SUPPLIER_PURCHASE");
        r6.setMentionedStore("汀兰餐厅");
        list.add(r6);

        AiHarnessReplayExpectedRound r7 = new AiHarnessReplayExpectedRound();
        r7.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r7.setEffectivePathCode("purchase_overview_path");
        r7.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("INHERITED_PREVIOUS", "SEMANTIC_EXPLICIT", "CURRENT_MESSAGE_EXPLICIT", "DEFAULT_MONTH_TO_DATE"));
        r7.setStartDate(p0);
        r7.setEndDate(p1);
        r7.setScopeType("STORE");
        r7.getVisibleStoreRootIds().add(3L);
        r7.setMentionedStore("汀兰餐厅");
        r7.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING);
        r7.setCheckPurchaseSourceType(Boolean.TRUE);
        r7.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        list.add(r7);

        return list;
    }

    /**
     * Case {@link #MULTI_STORE_PUBLIC_SCOPE_BLOCK3}。
     *
     * <p>占位 ID：root 门店 AAA=1、汀兰=3；SQL 扩展树示例 [1,2,5,3,4]，与文档一致。</p>
     */
    public static List<AiHarnessReplayExpectedRound> expectationsMultiStorePublicScopeBlock3(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        r1.setEffectiveTimeWindowSource("DEFAULT_MONTH_TO_DATE");
        r1.setStartDate(a.monthStartInclusive());
        r1.setEndDate(a.monthToDateInclusive());
        r1.setScopeType("GROUP");
        r1.getVisibleStoreRootIds().add(1L);
        r1.getVisibleStoreRootIds().add(3L);
        r1.getEffectiveSqlDepartmentIds().addAll(List.of(1L, 2L, 5L, 3L, 4L));
        r1.getQueryStoreIds().add(1);
        r1.getQueryStoreIds().add(3);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING);
        r1.setRevenueAnswerPlanPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING);
        r1.getQuerySemanticEffectiveMentionedStoreNames().add("AAA");
        r1.getQuerySemanticEffectiveMentionedStoreNames().add("汀兰餐厅");
        r1.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        r1.setMultiStoreScopeAppliedExpected(Boolean.TRUE);
        r1.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        r1.getMultiStoreMatchedStoresExpected().add("AAA");
        r1.getMultiStoreMatchedStoresExpected().add("汀兰餐厅");
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r2.setEffectiveTimeWindowSource("DEFAULT_MONTH_TO_DATE");
        r2.setStartDate(a.monthStartInclusive());
        r2.setEndDate(a.monthToDateInclusive());
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.getQueryStoreIds().add(1);
        r2.getQueryStoreIds().add(3);
        r2.getQuerySemanticEffectiveMentionedStoreNames().add("AAA");
        r2.getQuerySemanticEffectiveMentionedStoreNames().add("汀兰餐厅");
        r2.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        r2.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        r3.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r3.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r3.setEffectiveTimeWindowSource("DEFAULT_MONTH_TO_DATE");
        r3.setStartDate(a.monthStartInclusive());
        r3.setEndDate(a.monthToDateInclusive());
        r3.setScopeType("GROUP");
        r3.setVisibleStoreRootCountMin(2);
        r3.getQueryStoreIds().add(1);
        r3.getQueryStoreIds().add(3);
        r3.getQuerySemanticEffectiveMentionedStoreNames().add("AAA");
        r3.getQuerySemanticEffectiveMentionedStoreNames().add("汀兰餐厅");
        r3.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        r3.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        list.add(r3);

        return list;
    }

    /** Case {@link #MULTI_STORE_GLOBAL_LINKS_CONFIRM_5} — 与实际消息顺序一致，见 Replay 请求的 {@code messages}。 */
    public static List<AiHarnessReplayExpectedRound> expectationsMultiStoreGlobalLinksConfirm5(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound s1 = new AiHarnessReplayExpectedRound();
        s1.setEffectiveIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        s1.setEffectivePathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        s1.setEffectiveTimeWindowSource("DEFAULT_MONTH_TO_DATE");
        s1.setStartDate(a.monthStartInclusive());
        s1.setEndDate(a.monthToDateInclusive());
        s1.setScopeType("GROUP");
        s1.setVisibleStoreRootCountMin(2);
        s1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING);
        s1.setRevenueAnswerPlanPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING);
        s1.setMultiStoreScopeDetectedExpected(Boolean.FALSE);
        s1.setMultiStoreScopeAppliedExpected(Boolean.FALSE);
        s1.setSingleStoreNarrowingBlockedExpected(Boolean.FALSE);
        list.add(s1);

        AiHarnessReplayExpectedRound s2 = new AiHarnessReplayExpectedRound();
        s2.setEffectiveIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        s2.setEffectivePathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        s2.setEffectiveTimeWindowSource("DEFAULT_MONTH_TO_DATE");
        s2.setStartDate(a.monthStartInclusive());
        s2.setEndDate(a.monthToDateInclusive());
        s2.setScopeType("GROUP");
        s2.setVisibleStoreRootCountMin(2);
        s2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING);
        s2.setRevenueAnswerPlanPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING);
        s2.getQuerySemanticEffectiveMentionedStoreNames().add("AAA");
        s2.getQuerySemanticEffectiveMentionedStoreNames().add("汀兰餐厅");
        s2.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        s2.setMultiStoreScopeAppliedExpected(Boolean.TRUE);
        s2.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        s2.getMultiStoreMatchedStoresExpected().add("AAA");
        s2.getMultiStoreMatchedStoresExpected().add("汀兰餐厅");
        list.add(s2);

        AiHarnessReplayExpectedRound s3 = new AiHarnessReplayExpectedRound();
        s3.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        s3.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        s3.setEffectiveTimeWindowSource("DEFAULT_MONTH_TO_DATE");
        s3.setStartDate(a.monthStartInclusive());
        s3.setEndDate(a.monthToDateInclusive());
        s3.setScopeType("GROUP");
        s3.setVisibleStoreRootCountMin(2);
        s3.getQuerySemanticEffectiveMentionedStoreNames().add("AAA");
        s3.getQuerySemanticEffectiveMentionedStoreNames().add("汀兰餐厅");
        s3.setMentionedDishNameMustBeAbsent(Boolean.TRUE);
        s3.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        s3.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        list.add(s3);

        AiHarnessReplayExpectedRound s4 = new AiHarnessReplayExpectedRound();
        s4.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        s4.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        s4.setEffectiveTimeWindowSource("DEFAULT_MONTH_TO_DATE");
        s4.setStartDate(a.monthStartInclusive());
        s4.setEndDate(a.monthToDateInclusive());
        s4.setScopeType("GROUP");
        s4.setVisibleStoreRootCountMin(2);
        s4.getQuerySemanticEffectiveMentionedStoreNames().add("AAA");
        s4.getQuerySemanticEffectiveMentionedStoreNames().add("汀兰餐厅");
        s4.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        s4.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        list.add(s4);

        AiHarnessReplayExpectedRound s5 = new AiHarnessReplayExpectedRound();
        s5.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        s5.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        s5.setEffectiveTimeWindowSource("DEFAULT_MONTH_TO_DATE");
        s5.setStartDate(a.monthStartInclusive());
        s5.setEndDate(a.monthToDateInclusive());
        s5.setScopeType("GROUP");
        s5.setVisibleStoreRootCountMin(2);
        s5.getQuerySemanticEffectiveMentionedStoreNames().add("AAA");
        s5.getQuerySemanticEffectiveMentionedStoreNames().add("汀兰餐厅");
        s5.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        s5.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        list.add(s5);

        return list;
    }

    /**
     * v2 主语义 10 轮：菜品毛利 / 单菜追问 / 多店营收·采购·出库 / 经营概览与时间·范围继承（真实问句固化）。
     * <p>
     * 建议 {@code frozenClockDate=2026-05-13}、{@code strictStoreSqlMatch=false}（店名用 {@code querySemanticEffectiveMentionedStoreNames}，
     * ID 随环境漂移时用占位根 ID 不强校验）。
     * </p>
     */
    public static final String V2_SEMANTIC_MAINLINE_CORE_10 = "V2_SEMANTIC_MAINLINE_CORE_10";

    /**
     * 专项：上一轮「毛利率最低排行」承接时间与 scope，第二轮点名单菜问毛利；
     * 须脱离 {@code dish_profit_ranking_low_margin}，wire 升为 {@code dish_gross_margin_query}，
     * v2 metric 采纳为 {@code OVERRIDE}；与 {@link #expectationsV2SemanticMainlineCore10} 前两轮等价。
     */
    public static final String DISH_PROFIT_RANKING_TO_NAMED_DISH_FOLLOWUP_2 =
            "DISH_PROFIT_RANKING_TO_NAMED_DISH_FOLLOWUP_2";

    /**
     * D-13.3A：低毛利排行 DISH anchor →「哪些原料拖累毛利」追问 → {@code dish_ingredient_cost_breakdown} 协议闭环（无真实原料明细）。
     */
    public static final String DISH_LOW_MARGIN_DRILLDOWN_INGREDIENT_COST_2 =
            "DISH_LOW_MARGIN_DRILLDOWN_INGREDIENT_COST_2";

    /**
     * DiagnosisAgent v1：集团「本月问题」→ 单店（AAA）成本偏高 → 双店（AAA / 汀兰）并排原因；仅 Resolver Replay + 契约探针。
     */
    public static final String BUSINESS_DIAGNOSIS_V1_CORE_3 = "BUSINESS_DIAGNOSIS_V1_CORE_3";

    /**
     * BusinessOverview MultiAgent 四域固化：本月概览 →「那上个月呢」→ 双店经营对比；{@code GRAPH_RUN} 默认（或与
     * {@link #BUSINESS_DIAGNOSIS_V1_CORE_3} 同享 {@link AiHarnessReplayService} 默认图跑）。
     */
    public static final String BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3 = "BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3";

    /**
     * 经营类阶段 1B：语义矩阵 R01–R10，仅 {@link AiHarnessReplayDryRunStage#RESOLVED_CONTEXT_ONLY}（服务端对本
     * {@code caseId} 默认设置 dry-run；亦可显式传入）。消息顺序见 {@link #messagesBusinessSemantic1bResolvedContext()}；
     * 字段契约见 {@code docs/ai/business-phase1b-semantic-harness-matrix.md}。
     */
    public static final String BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT = "BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT";

    /**
     * 出库类阶段 1C：语义矩阵 R01–R15（含 R11–R13 多轮），仅
     * {@link AiHarnessReplayDryRunStage#RESOLVED_CONTEXT_ONLY}；消息见 {@link #messagesStockReduceSemantic1cResolvedContext()}；
     * 契约见 {@code docs/ai/stock-reduce-phase1c-semantic-harness-matrix.md}。
     */
    public static final String STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT =
            "STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT";

    /** 营业额单域 {@link AiHarnessReplayMode#GRAPH_RUN}：本月 → 上个月 → AAA 本月。 */
    public static final String REVENUE_AGENT_GRAPH_CORE = "REVENUE_AGENT_GRAPH_CORE";

    /** 采购单域 {@link AiHarnessReplayMode#GRAPH_RUN}。 */
    public static final String PURCHASE_AGENT_GRAPH_CORE = "PURCHASE_AGENT_GRAPH_CORE";

    /**
     * 阶段 2A 最小：{@link AiHarnessReplayDryRunStage#TOOL_REQUEST_ONLY}，采购金额 3 轮（不验 Tool 行集 / AnswerPlan）。
     */
    public static final String PURCHASE_TOOL_REQUEST_2A_MIN = "PURCHASE_TOOL_REQUEST_2A_MIN";

    /**
     * 阶段 2A 完整 4 轮：在 {@link #PURCHASE_TOOL_REQUEST_2A_MIN} 基础上追加供货商排行 Tool Request。
     * 无锚商品明细（如「定了什么东西？」）不在此 case：{@code TOOL_REQUEST_ONLY} 不执行 Tool，无法承接 R4 的 supplier anchor；
     * 见 {@link #PURCHASE_SUPPLIER_CHANNEL_OVERVIEW_GOODS_DETAIL_2} 或阶段 3 / {@code GRAPH_RUN}。
     */
    public static final String PURCHASE_TOOL_REQUEST_2A_CORE = "PURCHASE_TOOL_REQUEST_2A_CORE";

    /**
     * D-13：供货商金额排行 → 「上个月呢」→ Top 供货商商品/单价明细追问（默认 {@code GRAPH_RUN}）。
     * 预期链见 {@link #expectationsPurchaseSupplierRankingDrilldownGoodsUnitPrice3}；文档见 {@code docs/ai/follow-up-action-protocol.md}。
     */
    public static final String PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3 =
            "PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3";

    /**
     * D-13.4 第一阶段：商品采购金额 Top1 → 供应商/单价追问（协议 + Anchor + Resolver；默认 {@code GRAPH_RUN}）。
     * 预期见 {@link #expectationsPurchaseGoodsRankingDrilldownSupplierUnitPrice2}。
     */
    public static final String PURCHASE_GOODS_RANKING_DRILLDOWN_SUPPLIER_UNIT_PRICE_2 =
            "PURCHASE_GOODS_RANKING_DRILLDOWN_SUPPLIER_UNIT_PRICE_2";

    /**
     * D-13 Phase1：商品金额排行锚之后，用户以「供货商供货的商品里哪个采购金额最大」发起**新的**商品排行，
     * 来源限定为供货商渠道；须保持 {@code purchase_goods_amount_ranking}，不得误升 {@code supplier_amount_ranking}。
     */
    public static final String PURCHASE_SUPPLIER_FACET_GOODS_AMOUNT_RANKING_IGNORE_ANCHOR_2 =
            "PURCHASE_SUPPLIER_FACET_GOODS_AMOUNT_RANKING_IGNORE_ANCHOR_2";

    /**
     * Phase2-A：商品金额 Top1（ALL）→ 自采/供货商采购拆桶（legacy 行桶，ALL）；{@link AiHarnessReplayMode#GRAPH_RUN}。
     */
    public static final String PURCHASE_GOODS_RANKING_SOURCE_BREAKDOWN_2 = "PURCHASE_GOODS_RANKING_SOURCE_BREAKDOWN_2";

    /**
     * GOODS 锚四轮下钻矩阵（Harness 严格预期）：排行 → 谁供的 → 各供货商采购量 → 最高单价。
     * 问句见 {@link #messagesDrilldownPurchaseMatrixP1()}；预期见
     * {@link #expectationsDrilldownPurchaseMatrixP1(LocalDateAnchor)}。
     */
    public static final String DRILLDOWN_PURCHASE_MATRIX_P1 = "DRILLDOWN_PURCHASE_MATRIX_P1";

    /**
     * DISH 锚四轮下钻矩阵（Harness 严格预期）：低毛利排行 → 原料构成 → 高毛利排行 → 点名单菜毛利。
     * 问句见 {@link #messagesDishProfitMatrixP1()}；预期见 {@link #expectationsDishProfitMatrixP1(LocalDateAnchor)}。
     * <p><strong>当前主验收（P1-B）</strong>：与 Composite strict case 并列的 GRAPH 矩阵门禁。</p>
     */
    public static final String DISH_PROFIT_MATRIX_P1 = "DISH_PROFIT_MATRIX_P1";

    /**
     * Phase2-A：供货商 facet 商品金额 Top1 → 总额拆桶追问；Round2 须 {@code SOURCE_ALL}（勿锁 {@code SUPPLIER_PURCHASE}）。
     */
    public static final String PURCHASE_SUPPLIER_FACET_GOODS_RANKING_SOURCE_BREAKDOWN_2 =
            "PURCHASE_SUPPLIER_FACET_GOODS_RANKING_SOURCE_BREAKDOWN_2";

    /**
     * 供货商渠道上月订货 overview →「定了什么东西」商品明细（前台追问链）；默认 {@link AiHarnessReplayMode#GRAPH_RUN}。
     * 预期见 {@link #expectationsPurchaseSupplierChannelOverviewGoodsDetail2(LocalDateAnchor)}。
     */
    public static final String PURCHASE_SUPPLIER_CHANNEL_OVERVIEW_GOODS_DETAIL_2 =
            "PURCHASE_SUPPLIER_CHANNEL_OVERVIEW_GOODS_DETAIL_2";

    /**
     * 供货商金额排行锚（上月）→「在供货商订了多少钱」金额汇总追问；须走 {@code PURCHASE_SUPPLIER_OVERVIEW} /
     * {@code purchase_source_amount_query}，不得误登记为商品明细下钻。
     */
    public static final String PURCHASE_SUPPLIER_ANCHOR_THEN_SOURCE_AMOUNT_SUMMARY_2 =
            "PURCHASE_SUPPLIER_ANCHOR_THEN_SOURCE_AMOUNT_SUMMARY_2";

    /**
     * D-13.2：本月经营诊断 → 门店优先级 → STORE 原因追问；默认 {@link AiHarnessReplayMode#GRAPH_RUN}，预期见
     * {@link #expectationsBusinessStorePriorityDrilldownReasons3(LocalDateAnchor)}。
     */
    public static final String BUSINESS_STORE_PRIORITY_DRILLDOWN_REASONS_3 =
            "BUSINESS_STORE_PRIORITY_DRILLDOWN_REASONS_3";

    /**
     * 经营诊断内下钻 Matrix P1（GRAPH）：BD-A 综述 → BD-B 门店优先级 → BD-C/D 原因 → BD-E/F/G 子域归因 → BD-K 改进行动。
     * 问句见 {@link #messagesBusinessDiagnosisDrilldownMatrixP1()}；
     * 预期见 {@link #expectationsBusinessDiagnosisDrilldownMatrixP1(LocalDateAnchor)}。
     */
    public static final String BUSINESS_DIAGNOSIS_DRILLDOWN_MATRIX_P1 =
            "BUSINESS_DIAGNOSIS_DRILLDOWN_MATRIX_P1";

    /** 出库核销单域 {@link AiHarnessReplayMode#GRAPH_RUN}。 */
    public static final String STOCK_REDUCE_AGENT_GRAPH_CORE = "STOCK_REDUCE_AGENT_GRAPH_CORE";

    /**
     * 出库本域 Matrix P1（GRAPH 严格验收）：总览 / 门店排行 / 子口径 / 商品排行 / 门店追问 / facet 切换。
     * 问句见 {@link #messagesStockReduceMatrixP1()}；预期见 {@link #expectationsStockReduceMatrixP1(LocalDateAnchor)}。
     */
    public static final String STOCK_REDUCE_MATRIX_P1 = "STOCK_REDUCE_MATRIX_P1";

    /**
     * 营业额本域 Matrix P1（GRAPH 严格验收）：总览 / 门店排行 / 单店 / 对比 / 时间追问 / 环比·日峰·趋势 knownGap。
     * 问句见 {@link #messagesRevenueMatrixP1()}；预期见 {@link #expectationsRevenueMatrixP1(LocalDateAnchor)}。
     */
    public static final String REVENUE_MATRIX_P1 = "REVENUE_MATRIX_P1";

    /**
     * 库房库存现量本域 Matrix P1（GRAPH 严格验收）：总览 / 商品·门店排行 / 单店 / 缺货·临期 knownGap / 追问。
     * 问句见 {@link #messagesWarehouseMatrixP1()}；预期见 {@link #expectationsWarehouseMatrixP1(LocalDateAnchor)}。
     */
    public static final String WAREHOUSE_MATRIX_P1 = "WAREHOUSE_MATRIX_P1";

    /**
     * 菜品销量本域 Matrix P1（GRAPH 严格验收）：排行 / 单菜 / 门店 / 时间追问 / 跨域·趋势 knownGap。
     * 问句见 {@link #messagesDishSalesMatrixP1()}；预期见 {@link #expectationsDishSalesMatrixP1(LocalDateAnchor)}。
     */
    public static final String DISH_SALES_MATRIX_P1 = "DISH_SALES_MATRIX_P1";

    /** 菜品毛利单域 {@link AiHarnessReplayMode#GRAPH_RUN}：低毛利排行 → 点名菜毛利 → 本月最高毛利菜。 */
    public static final String DISH_PROFIT_AGENT_GRAPH_CORE = "DISH_PROFIT_AGENT_GRAPH_CORE";

    /**
     * PlannerExecutor 独立 mock：固定多步计划 + {@link AiHarnessReplayMode#PLANNER_EXECUTOR_MOCK}，
     * 不接生产 Graph / Master / SQL（见 {@link AiHarnessReplayPlannerExecutorMock}）。
     */
    public static final String PLANNER_EXECUTOR_MOCK_CORE = "PLANNER_EXECUTOR_MOCK_CORE";

    /**
     * PlannerExecutor mock：采购步 mock 失败 + {@code CONTINUE_WITH_DEGRADED}，整轮 {@code overallStatus=DEGRADED}。
     */
    public static final String PLANNER_EXECUTOR_MOCK_DEGRADED_CORE = "PLANNER_EXECUTOR_MOCK_DEGRADED_CORE";

    // P1-B Final Removed：四域单域 Adapter 演进轴（CORE / REAL_BRIDGE / FAKE_OK / HYDRATED / GROUP_HYDRATED）及
    // C-31 全 MOCK Composite（PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE）。Hydrated 物化见
    // {@link PlannerCompositeHarnessContext}；主验收仅 Composite strict（C-35 / C-48 / C-42）。

    // C-32～C-34 历史分步 Composite case 已退役（P1-A）；请用 ALL_REAL / GROUP / STOCK_DEGRADED strict case。

    /**
     * PlannerExecutor：经营诊断 Composite — 四数据域（营收 / 采购 / 出库 / 菜品毛利）均接
     * {@link com.nongxinle.ai.planner.RevenuePlannerRealReadBridge} /
     * {@link com.nongxinle.ai.planner.PurchasePlannerRealReadBridge} /
     * {@link com.nongxinle.ai.planner.StockReducePlannerRealReadBridge} /
     * {@link com.nongxinle.ai.planner.DishProfitPlannerRealReadBridge}（Hydrated）；诊断 / 建议仍为 mock（C-35）。
     *
     * @see com.nongxinle.ai.harness.replay.AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase
     */
    public static final String PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE =
            "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE";

    /**
     * PlannerExecutor：经营诊断 Composite — **GROUP** 四数据域 Hydrated（与 C-44～C-47 同构）+ 确定性诊断 compose +
     * mock 建议（**C-48**）；**不**接 Master / LLM。
     *
     * @see com.nongxinle.ai.harness.replay.AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase
     */
    public static final String PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE =
            "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE";

    /**
     * PlannerExecutor：经营诊断 Composite — 与 ALL_REAL 同六步，但出库步 Harness 固定 DEGRADED（不调
     * {@code stock_reduce_query}）；营收 / 采购 / 菜品仍 Hydrated 真实；诊断确定性；建议 mock（C-42）。
     *
     * @see com.nongxinle.ai.harness.replay.AiPlannerExecutorBusinessDiagnosisCompositeStockDegradedGraphCase
     */
    public static final String PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE =
            "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE";

    // ── C-54：Composite Production Gate — Harness-only（{@link com.nongxinle.ai.harness.replay.AiHarnessReplayCompositeGate}） ──

    public static final String BUSINESS_DIAGNOSIS_COMPOSITE_GATE_STORE_ALLOWED =
            "BUSINESS_DIAGNOSIS_COMPOSITE_GATE_STORE_ALLOWED";
    public static final String BUSINESS_DIAGNOSIS_COMPOSITE_GATE_GROUP_ALLOWED =
            "BUSINESS_DIAGNOSIS_COMPOSITE_GATE_GROUP_ALLOWED";
    public static final String BUSINESS_DIAGNOSIS_COMPOSITE_GATE_FEATURE_DISABLED =
            "BUSINESS_DIAGNOSIS_COMPOSITE_GATE_FEATURE_DISABLED";
    public static final String BUSINESS_DIAGNOSIS_COMPOSITE_GATE_DOMAIN_REVENUE_BLOCKED =
            "BUSINESS_DIAGNOSIS_COMPOSITE_GATE_DOMAIN_REVENUE_BLOCKED";
    public static final String BUSINESS_DIAGNOSIS_COMPOSITE_GATE_NAMED_DISH_BLOCKED =
            "BUSINESS_DIAGNOSIS_COMPOSITE_GATE_NAMED_DISH_BLOCKED";
    public static final String BUSINESS_DIAGNOSIS_COMPOSITE_GATE_RANKING_BLOCKED =
            "BUSINESS_DIAGNOSIS_COMPOSITE_GATE_RANKING_BLOCKED";
    public static final String BUSINESS_DIAGNOSIS_COMPOSITE_GATE_MISSING_TIME =
            "BUSINESS_DIAGNOSIS_COMPOSITE_GATE_MISSING_TIME";
    public static final String BUSINESS_DIAGNOSIS_COMPOSITE_GATE_GROUP_INSUFFICIENT_STORES =
            "BUSINESS_DIAGNOSIS_COMPOSITE_GATE_GROUP_INSUFFICIENT_STORES";

    /** C-54：{@link com.nongxinle.ai.harness.replay.AiHarnessReplayCompositeGate} 短路（非 PlannerExecutor）。 */
    public static boolean isCompositeGateHarnessCase(String caseId) {
        if (caseId == null) {
            return false;
        }
        String t = caseId.trim();
        return BUSINESS_DIAGNOSIS_COMPOSITE_GATE_STORE_ALLOWED.equals(t)
                || BUSINESS_DIAGNOSIS_COMPOSITE_GATE_GROUP_ALLOWED.equals(t)
                || BUSINESS_DIAGNOSIS_COMPOSITE_GATE_FEATURE_DISABLED.equals(t)
                || BUSINESS_DIAGNOSIS_COMPOSITE_GATE_DOMAIN_REVENUE_BLOCKED.equals(t)
                || BUSINESS_DIAGNOSIS_COMPOSITE_GATE_NAMED_DISH_BLOCKED.equals(t)
                || BUSINESS_DIAGNOSIS_COMPOSITE_GATE_RANKING_BLOCKED.equals(t)
                || BUSINESS_DIAGNOSIS_COMPOSITE_GATE_MISSING_TIME.equals(t)
                || BUSINESS_DIAGNOSIS_COMPOSITE_GATE_GROUP_INSUFFICIENT_STORES.equals(t);
    }

    /**
     * DB-free PlannerExecutor Replay caseIds（短路 {@link AiHarnessReplayPlannerExecutorMock}）。
     * <p><strong>当前主验收（P1-B）</strong>：Composite strict —
     * {@link #PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE}、
     * {@link #PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE}、
     * {@link #PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE}；
     * GRAPH 矩阵 {@link #DISH_PROFIT_MATRIX_P1}（走 {@link AiHarnessReplayService} GRAPH 路径，非本方法唯一入口）。</p>
     * <p>P1-B Final 已摘除单域 Adapter 全系列 case 与 C-31 全 MOCK Composite。</p>
     */
    public static boolean isPlannerExecutorMockHarnessCase(String caseId) {
        if (caseId == null) {
            return false;
        }
        String t = caseId.trim();
        return PLANNER_EXECUTOR_MOCK_CORE.equals(t)
                || PLANNER_EXECUTOR_MOCK_DEGRADED_CORE.equals(t)
                || PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE.equals(t)
                || PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE.equals(t)
                || PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE.equals(t);
    }

    private static void applyV2SemanticHarnessDefaults(AiHarnessReplayExpectedRound r) {
        r.setSemanticAdoptedFromExpected("v2");
        r.setSemanticFallbackUsedExpected(Boolean.FALSE);
        r.setQuerySemanticV2ParseMissingExpected(Boolean.FALSE);
        r.getForbiddenSubstringsInSummaryJson()
                .addAll(List.of("v2_no_routable_path", "Placeholder", "empty_llm_response"));
        r.setEnforceQuerySemanticV2ScopeKeyAbsence(Boolean.TRUE);
    }

    /** 防止旧 AiBusinessOverviewResult fallback 话术与统计缺失提示回退至最终 answerPreview（GRAPH 摘要对齐）。 */
    private static void addBusinessOverviewReplayLegacyFallbackAnswerPreviewGuards(AiHarnessReplayExpectedRound r) {
        r.getAnswerPreviewMustNotContainAnyOf()
                .addAll(List.of(
                        "经营看板未返回有效统计",
                        "组织或分销商上下文不完整",
                        "营业额与菜品标价收入均未汇总到"));
    }

    /** 单域 GRAPH_RUN 核心：禁止回落 MultiAgent / 诊断话术；摘要 JSON 不含典型 Tool 参数失败片段。 */
    private static void applySingleDomainGraphCoreDefaults(AiHarnessReplayExpectedRound r) {
        applyV2SemanticHarnessDefaults(r);
        r.getEffectiveIntentCodeNoneOf().add(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        r.getEffectiveIntentCodeNoneOf().add(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r.getForbiddenSubstringsInSummaryJson()
                .addAll(List.of(
                        "missing departmentFatherId",
                        "missing startDate",
                        "missing stopDate",
                        "missing disId",
                        "validation 参数失败"));
        r.getAnswerPreviewMustNotContainAnyOf().add("经营概览·四域汇总");
        r.getAnswerPreviewMustNotContainAnyOf().add("经营诊断·证据型");
    }

    public static List<AiHarnessReplayExpectedRound> expectationsV2SemanticMainlineCore10(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(p0);
        r1.setEndDate(p1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN);
        r1.setDishProfitMetricType("RANKING_LOW_MARGIN");
        r1.setHarnessReplayPlanSource("dishProfitAnswerPlan");
        r1.setHarnessReplayDishProfitAnswerPlanType(DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN);
        r1.setHarnessReplayDishProfitAnswerPlanSortDirection("ASC");
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        r2.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r2.setStartDate(p0);
        r2.setEndDate(p1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setMentionedDishName("核桃芽菜西芹");
        r2.setQuerySemanticV2TimeActionExpected("INHERIT_PREVIOUS");
        r2.setQuerySemanticV2MetricActionExpected("OVERRIDE");
        r2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY);
        r2.setDishProfitMetricType("GROSS_MARGIN");
        r2.setHarnessReplayPlanSource("dishProfitAnswerPlan");
        r2.setHarnessReplayDishProfitAnswerPlanType(DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE);
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r3);
        r3.setEffectiveIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        r3.setEffectivePathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        r3.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r3.setStartDate(p0);
        r3.setEndDate(p1);
        r3.setQuerySemanticV2TimeActionExpected("INHERIT_PREVIOUS");
        r3.setScopeType("GROUP");
        r3.setVisibleStoreRootCountMin(2);
        r3.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING);
        r3.setRevenueAnswerPlanPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING);
        r3.getQuerySemanticEffectiveMentionedStoreNames().add("AAA");
        r3.getQuerySemanticEffectiveMentionedStoreNames().add("汀兰餐厅");
        r3.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        r3.setMultiStoreScopeAppliedExpected(Boolean.TRUE);
        r3.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        r3.getMultiStoreMatchedStoresExpected().add("AAA");
        r3.getMultiStoreMatchedStoresExpected().add("汀兰餐厅");
        r3.setHarnessReplayPlanSource("revenueAnswerPlan");
        r3.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        r3.setHarnessReplayRevenueAnswerPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING);
        list.add(r3);

        AiHarnessReplayExpectedRound r4 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r4);
        r4.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r4.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r4.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r4.setStartDate(p0);
        r4.setEndDate(p1);
        r4.setQuerySemanticV2TimeActionExpected("INHERIT_PREVIOUS");
        r4.setScopeType("GROUP");
        r4.setVisibleStoreRootCountMin(2);
        r4.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING);
        r4.getQuerySemanticEffectiveMentionedStoreNames().add("AAA");
        r4.getQuerySemanticEffectiveMentionedStoreNames().add("汀兰餐厅");
        r4.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        r4.setMultiStoreScopeAppliedExpected(Boolean.TRUE);
        r4.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        r4.getMultiStoreMatchedStoresExpected().add("AAA");
        r4.getMultiStoreMatchedStoresExpected().add("汀兰餐厅");
        r4.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r4.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r4.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING);
        list.add(r4);

        AiHarnessReplayExpectedRound r5 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r5);
        r5.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r5.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r5.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r5.setStartDate(p0);
        r5.setEndDate(p1);
        r5.setQuerySemanticV2TimeActionExpected("INHERIT_PREVIOUS");
        r5.setScopeType("GROUP");
        r5.setVisibleStoreRootCountMin(2);
        r5.getQuerySemanticEffectiveMentionedStoreNames().add("AAA");
        r5.getQuerySemanticEffectiveMentionedStoreNames().add("汀兰餐厅");
        r5.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        r5.setMultiStoreScopeAppliedExpected(Boolean.TRUE);
        r5.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        r5.getMultiStoreMatchedStoresExpected().add("AAA");
        r5.getMultiStoreMatchedStoresExpected().add("汀兰餐厅");
        r5.setHarnessReplayPlanSource("stockReduceAnswerPlan");
        r5.setHarnessReplayStockReduceAnswerPlanType(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING);
        r5.setHarnessReplayStockReduceReduceType("RANKING");
        r5.getPurchaseSourceTypeNoneOf().add("OUTBOUND");
        list.add(r5);

        AiHarnessReplayExpectedRound r6 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r6);
        r6.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r6.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r6.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r6.setStartDate(p0);
        r6.setEndDate(p1);
        r6.setQuerySemanticV2TimeActionExpected("INHERIT_PREVIOUS");
        r6.setScopeType("GROUP");
        r6.setVisibleStoreRootCountMin(2);
        r6.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING);
        r6.getQuerySemanticEffectiveMentionedStoreNames().add("AAA");
        r6.getQuerySemanticEffectiveMentionedStoreNames().add("汀兰餐厅");
        r6.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        r6.setMultiStoreScopeAppliedExpected(Boolean.TRUE);
        r6.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        r6.getMultiStoreMatchedStoresExpected().add("AAA");
        r6.getMultiStoreMatchedStoresExpected().add("汀兰餐厅");
        r6.setHarnessReplayPlanSource("stockReduceAnswerPlan");
        r6.setHarnessReplayStockReduceAnswerPlanType(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING);
        r6.setHarnessReplayStockReduceAnswerPlanSortDirection("DESC");
        list.add(r6);

        AiHarnessReplayExpectedRound r7 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r7);
        r7.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        r7.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW);
        r7.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r7.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r7.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r7.setStartDate(m0);
        r7.setEndDate(m1);
        r7.setScopeType("GROUP");
        r7.setVisibleStoreRootCountMin(2);
        r7.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_STATUS);
        r7.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        r7.setHarnessReplayRevenueAnswerPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW);
        r7.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r7.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW);
        list.add(r7);

        AiHarnessReplayExpectedRound r8 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r8);
        r8.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        r8.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW);
        r8.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r8.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r8.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r8.getEffectiveTimeWindowSourceNoneOf().add("DEFAULT_MONTH_TO_DATE");
        r8.setStartDate(p0);
        r8.setEndDate(p1);
        r8.setScopeType("GROUP");
        r8.setVisibleStoreRootCountMin(2);
        r8.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        r8.setHarnessReplayRevenueAnswerPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW);
        r8.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r8.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW);
        list.add(r8);

        AiHarnessReplayExpectedRound r9 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r9);
        r9.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        r9.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW);
        r9.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r9.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r9.setStartDate(p0);
        r9.setEndDate(p1);
        r9.setScopeType("GROUP");
        r9.setVisibleStoreRootCountMin(2);
        r9.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_STATUS_COMPARE);
        r9.getQuerySemanticEffectiveMentionedStoreNames().add("AAA");
        r9.getQuerySemanticEffectiveMentionedStoreNames().add("汀兰餐厅");
        r9.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        r9.setMultiStoreScopeAppliedExpected(Boolean.TRUE);
        r9.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        r9.getMultiStoreMatchedStoresExpected().add("AAA");
        r9.getMultiStoreMatchedStoresExpected().add("汀兰餐厅");
        r9.getEffectiveIntentCodeNoneOf().add(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        r9.getQuerySemanticV2TimeActionNoneOf().add("OVERRIDE");
        r9.getQuerySemanticV2TimeTypeNoneOf().add("CURRENT_MONTH");
        r9.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        r9.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        list.add(r9);

        AiHarnessReplayExpectedRound r10 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r10);
        r10.setEffectiveIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        r10.setEffectivePathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        r10.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r10.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r10.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r10.setStartDate(m0);
        r10.setEndDate(m1);
        r10.setScopeType("GROUP");
        r10.setVisibleStoreRootCountMin(2);
        r10.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY);
        r10.setHarnessReplayPlanSource("revenueAnswerPlan");
        r10.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        r10.setHarnessReplayRevenueAnswerPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW);
        list.add(r10);

        return list;
    }

    /**
     * {@link #DISH_PROFIT_RANKING_TO_NAMED_DISH_FOLLOWUP_2}：消息顺序 —
     * 「上个月哪个菜毛利率最低？」→「核桃芽菜西芹毛利怎么样？」；
     * 日期按 {@link LocalDateAnchor#previousMonthFirstDay()} / {@link LocalDateAnchor#previousMonthLastDay()}。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsDishProfitRankingToNamedDishFollowup2(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound q1 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(q1);
        q1.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        q1.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        q1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        q1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        q1.setStartDate(p0);
        q1.setEndDate(p1);
        q1.setScopeType("GROUP");
        q1.setVisibleStoreRootCountMin(2);
        q1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN);
        q1.setDishProfitMetricType("RANKING_LOW_MARGIN");
        q1.setHarnessReplayPlanSource("dishProfitAnswerPlan");
        q1.setHarnessReplayDishProfitAnswerPlanType(DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN);
        q1.setHarnessReplayDishProfitAnswerPlanSortDirection("ASC");
        list.add(q1);

        AiHarnessReplayExpectedRound q2 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(q2);
        q2.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        q2.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        q2.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        q2.setStartDate(p0);
        q2.setEndDate(p1);
        q2.setScopeType("GROUP");
        q2.setVisibleStoreRootCountMin(2);
        q2.setMentionedDishName("核桃芽菜西芹");
        q2.setQuerySemanticV2TimeActionExpected("INHERIT_PREVIOUS");
        q2.setQuerySemanticV2MetricActionExpected("OVERRIDE");
        q2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY);
        q2.setDishProfitMetricType("GROSS_MARGIN");
        q2.setHarnessReplayPlanSource("dishProfitAnswerPlan");
        q2.setHarnessReplayDishProfitAnswerPlanType(DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE);
        list.add(q2);

        return list;
    }

    /**
     * {@link #DISH_LOW_MARGIN_DRILLDOWN_INGREDIENT_COST_2}：「上个月哪个菜毛利率最低？」→「具体是哪些原料拖累了毛利？」；
     * 与 {@link #DISH_PROFIT_AGENT_GRAPH_CORE} 相同为 {@code GRAPH_RUN} 默认。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsDishLowMarginDrilldownIngredientCost2(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(p0);
        r1.setEndDate(p1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN);
        r1.setDishProfitMetricType("RANKING_LOW_MARGIN");
        r1.setHarnessReplayPlanSource("dishProfitAnswerPlan");
        r1.setHarnessReplayDishProfitAnswerPlanType(DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN);
        r1.setHarnessReplayDishProfitAnswerPlanSortDirection("ASC");
        r1.setDishProfitAnswerPlanPresentExpected(Boolean.TRUE);
        r1.setDishProfitAnswerPlanHumanTypeExpected("低毛利排行");
        r1.setDishProfitAnswerPlanResultAnchorsCountMin(1);
        r1.getDishProfitAnswerPlanResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_DISH);
        r1.getUsedToolsMustContain().add(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        r1.setMasterDishProfitToolResultSuccessExpected(Boolean.TRUE);
        r1.getConsumedAnswerPlansMustContain().add("DishProfitAnswerPlan");
        r1.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r1.getAnswerPreviewContainsAnyOf().addAll(List.of("毛利", "菜品"));
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        r2.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r2.setStartDate(p0);
        r2.setEndDate(p1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setQuerySemanticV2TimeActionExpected("INHERIT_PREVIOUS");
        r2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN);
        r2.setHarnessReplayPlanSource("dishProfitAnswerPlan");
        r2.setHarnessReplayDishProfitAnswerPlanType(DishProfitAnswerPlan.TYPE_DISH_INGREDIENT_COST_BREAKDOWN);
        r2.setDishProfitAnswerPlanPresentExpected(Boolean.TRUE);
        r2.setDishProfitAnswerPlanHumanTypeExpected("原料成本构成");
        r2.getFollowUpActionAnyOf().addAll(List.of("DETAIL_DRILLDOWN", "OBJECT_DRILLDOWN"));
        r2.setFollowUpTargetEntityTypeExpected(AiResultAnchor.ENTITY_TYPE_DISH);
        r2.setFollowUpTargetEntityNameMustBeNonBlank(Boolean.TRUE);
        r2.getFollowUpDetailWantedAnyOf().addAll(List.of("INGREDIENT_COST_BREAKDOWN", "DISH_COST_COMPONENTS"));
        r2.setFollowUpSourcePlanTypeExpected(DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN);
        r2.setPreviousTurnSummaryResultAnchorsCountMin(1);
        r2.getPreviousTurnSummaryResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_DISH);
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.DISH_INGREDIENT_COST_BREAKDOWN);
        r2.setMasterDishProfitToolResultSuccessExpected(null);
        r2.setDishIngredientCostBreakdownToolSuccessExpected(Boolean.TRUE);
        r2.setIngredientBreakdownAvailableExpected(Boolean.TRUE);
        r2.setIngredientRowsCountMin(1);
        r2.getIngredientRowFieldsMustContain()
                .addAll(List.of(
                        "recipeQuantityPerDish",
                        "recipeUnit",
                        "unitCost",
                        "costPerDish",
                        "totalCost",
                        "costRatio"));
        r2.getConsumedAnswerPlansMustContain().add("DishProfitAnswerPlan");
        r2.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        list.add(r2);

        return list;
    }

    /**
     * {@link #BUSINESS_DIAGNOSIS_V1_CORE_3}：与 {@code docs/AI_HARNESS_REPLAY_CASES.md} 对齐；日期锚同 {@link LocalDateAnchor}。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsBusinessDiagnosisV1Core3(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r1.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(m0);
        r1.setEndDate(m1);
        r1.setScopeType("GROUP");
        r1.setBusinessOverviewMultiAgentBatchCompletedExpected(Boolean.TRUE);
        r1.setBusinessOverviewAllExpectedDomainsAttemptedExpected(Boolean.TRUE);
        r1.setBusinessOverviewMultiAgentAnyDomainSuccessExpected(Boolean.TRUE);
        r1.getConsumedAnswerPlansMustContain()
                .addAll(List.of(
                        "DailyRevenueAnswerPlan",
                        "PurchaseAnswerPlan",
                        "StockReduceAnswerPlan",
                        "DishProfitAnswerPlan"));
        r1.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r1.getAnswerPreviewContainsAnyOf().add("经营诊断");
        r1.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r2.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r2.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r2.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r2.setStartDate(m0);
        r2.setEndDate(m1);
        r2.setScopeType("STORE");
        r2.setQueryScopeKindExpected(AiResolvedDataScope.QUERY_SCOPE_KIND_STORE);
        r2.setQueryScopeModeExpected(AiResolvedDataScope.QUERY_SCOPE_MODE_STORE);
        r2.getQueryStoreIdsMustContain().add(1);
        r2.setMentionedStore("AAA");
        r2.setBusinessOverviewMultiAgentBatchCompletedExpected(Boolean.TRUE);
        r2.setBusinessOverviewAllExpectedDomainsAttemptedExpected(Boolean.TRUE);
        r2.setBusinessOverviewMultiAgentAnyDomainSuccessExpected(Boolean.TRUE);
        r2.getConsumedAnswerPlansMustContain()
                .addAll(List.of(
                        "DailyRevenueAnswerPlan",
                        "PurchaseAnswerPlan",
                        "StockReduceAnswerPlan",
                        "DishProfitAnswerPlan"));
        r2.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r2.getAnswerPreviewContainsAnyOf().add("经营诊断");
        r2.setBusinessDiagnosisDataCompletenessRevenueExpected("OK");
        r2.setResolvedEffectiveSqlDepartmentIdsNonEmpty(Boolean.TRUE);
        r2.getResolvedVisibleStoreRootIdsMustContain().add(1L);
        r2.getResolvedEffectiveSqlDepartmentIdsMustContain().add(1L);
        r2.getForbiddenSubstringsInSummaryJson()
                .addAll(List.of(
                        "missing departmentFatherId",
                        "missing startDate",
                        "missing stopDate",
                        "missing disId",
                        "validation 参数失败"));
        r2.getSummaryActionItemsForbiddenSubstrings().add("先补全日营业额或营收数据");
        r2.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r3);
        r3.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r3.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r3.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r3.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r3.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r3.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r3.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r3.setStartDate(m0);
        r3.setEndDate(m1);
        r3.setScopeType("GROUP");
        r3.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        r3.setMultiStoreScopeAppliedExpected(Boolean.TRUE);
        r3.getQueryStoreIdsMustContain().add(1);
        r3.getQueryStoreIdsMustContain().add(3);
        r3.getScopeLabelMustContainSubstrings().addAll(List.of("AAA", "汀兰餐厅"));
        r3.setBusinessOverviewMultiAgentBatchCompletedExpected(Boolean.TRUE);
        r3.setBusinessOverviewAllExpectedDomainsAttemptedExpected(Boolean.TRUE);
        r3.setBusinessOverviewMultiAgentAnyDomainSuccessExpected(Boolean.TRUE);
        r3.getConsumedAnswerPlansMustContain()
                .addAll(List.of(
                        "DailyRevenueAnswerPlan",
                        "PurchaseAnswerPlan",
                        "StockReduceAnswerPlan",
                        "DishProfitAnswerPlan"));
        r3.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r3.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS);
        r3.setHarnessReplayPlanSource("diagnosisPlan");
        r3.setDiagnosisPlanExistsExpected(Boolean.TRUE);
        r3.setBusinessDiagnosisPlanExistsExpected(Boolean.TRUE);
        r3.setHarnessReplayStoreCompareEvidenceRowsLenExpected(2);
        r3.setBusinessStoreCompareTop1StoreNameExpected("AAA");
        r3.setBusinessStoreCompareTop2StoreNameExpected("汀兰餐厅");
        r3.setFinalAnswerTextBlankExpected(Boolean.FALSE);
        r3.getAnswerPreviewMustNotContainAnyOf().add("经营概览·四域汇总");
        r3.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        list.add(r3);

        return list;
    }

    /**
     * {@link #BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3}：四域 MultiAgent「本月怎么样」→「那上个月」→ 双店对比；须通过
     * {@link AiHarnessReplayMode#GRAPH_RUN} 固化编排与 Composer 链路。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsBusinessOverviewMultiAgentCore3(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW);
        r1.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.setStartDate(m0);
        r1.setEndDate(m1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_STATUS);
        r1.setBusinessOverviewMultiAgentBatchCompletedExpected(Boolean.TRUE);
        r1.setBusinessOverviewAllExpectedDomainsAttemptedExpected(Boolean.TRUE);
        r1.setBusinessOverviewMultiAgentAnyDomainSuccessExpected(Boolean.TRUE);
        r1.getBusinessOverviewSuccessfulDomainsMustContain()
                .addAll(List.of("revenue", "purchase", "stockReduce", "dishProfit"));
        r1.getConsumedAnswerPlansMustContain()
                .addAll(List.of(
                        "DailyRevenueAnswerPlan",
                        "PurchaseAnswerPlan",
                        "StockReduceAnswerPlan",
                        "DishProfitAnswerPlan"));
        r1.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r1.getAnswerPreviewContainsAnyOf().addAll(List.of("经营概览", "经营概览·四域汇总"));
        addBusinessOverviewReplayLegacyFallbackAnswerPreviewGuards(r1);
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW);
        r2.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r2.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("INHERITED_PREVIOUS", "CURRENT_MESSAGE_EXPLICIT", "SEMANTIC_EXPLICIT"));
        r2.getEffectiveTimeWindowSourceNoneOf().add("DEFAULT_MONTH_TO_DATE");
        r2.setStartDate(p0);
        r2.setEndDate(p1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setBusinessOverviewMultiAgentBatchCompletedExpected(Boolean.TRUE);
        r2.setBusinessOverviewAllExpectedDomainsAttemptedExpected(Boolean.TRUE);
        r2.setBusinessOverviewMultiAgentAnyDomainSuccessExpected(Boolean.TRUE);
        r2.getBusinessOverviewSuccessfulDomainsMustContain()
                .addAll(List.of("revenue", "purchase", "stockReduce", "dishProfit"));
        r2.getConsumedAnswerPlansMustContain()
                .addAll(List.of(
                        "DailyRevenueAnswerPlan",
                        "PurchaseAnswerPlan",
                        "StockReduceAnswerPlan",
                        "DishProfitAnswerPlan"));
        r2.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r2.getAnswerPreviewContainsAnyOf().add("经营概览");
        addBusinessOverviewReplayLegacyFallbackAnswerPreviewGuards(r2);
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r3);
        r3.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        r3.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW);
        r3.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r3.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r3.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r3.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r3.getEffectiveTimeWindowSourceNoneOf().add("DEFAULT_MONTH_TO_DATE");
        r3.setStartDate(p0);
        r3.setEndDate(p1);
        r3.setScopeType("GROUP");
        r3.setVisibleStoreRootCountMin(2);
        r3.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        r3.setMultiStoreScopeAppliedExpected(Boolean.TRUE);
        r3.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        r3.getMultiStoreMatchedStoresExpected().addAll(List.of("AAA", "汀兰餐厅"));
        r3.getQueryStoreIdsMustContain().add(1);
        r3.getQueryStoreIdsMustContain().add(3);
        r3.getScopeLabelMustContainSubstrings().addAll(List.of("AAA", "汀兰餐厅"));
        r3.setBusinessOverviewMultiAgentBatchCompletedExpected(Boolean.TRUE);
        r3.setBusinessOverviewAllExpectedDomainsAttemptedExpected(Boolean.TRUE);
        r3.setBusinessOverviewMultiAgentAnyDomainSuccessExpected(Boolean.TRUE);
        r3.getBusinessOverviewSuccessfulDomainsMustContain()
                .addAll(List.of("revenue", "purchase", "stockReduce", "dishProfit"));
        r3.getConsumedAnswerPlansMustContain()
                .addAll(List.of(
                        "DailyRevenueAnswerPlan",
                        "PurchaseAnswerPlan",
                        "StockReduceAnswerPlan",
                        "DishProfitAnswerPlan"));
        r3.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r3.getAnswerPreviewContainsAnyOf().add("经营概览");
        r3.getAnswerPreviewMustNotContainAnyOf().add("经营诊断·证据型");
        addBusinessOverviewReplayLegacyFallbackAnswerPreviewGuards(r3);
        list.add(r3);

        return list;
    }

    /**
     * 与 {@link #STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT} 对齐：R01–R10 单轮 + R11–R13 各 2 轮 + R14–R15 单轮（共 18 轮）。
     */
    public static List<String> messagesStockReduceSemantic1cResolvedContext() {
        return List.of(
                "这个月出库情况怎么样？",
                "这个月核销金额多少？",
                "这个月出品耗用多少？",
                "这个月废弃金额多少？",
                "这个月损失金额多少？",
                "这个月退货金额多少？",
                "哪些商品出库金额最高？",
                "哪些商品出库次数最多？",
                "AAA 和汀兰餐厅哪个出库金额高？",
                "哪个门店出库金额最高？",
                "这个月出库情况怎么样？",
                "那上个月呢？",
                "这个月经营得怎么样？",
                "那出库呢？",
                "这个月采购怎么样？",
                "那核销呢？",
                "最近采购多但出库少的商品有哪些？",
                "最近采购了但没有核销的商品有哪些？");
    }

    /**
     * {@link #STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT}：R01–R15 解析层断言（intent / path / wire / 时间 / 范围 / v2 timeAction）；
     * 不比 Tool / AnswerPlan / Composer。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsStockReduceSemantic1cResolvedContext(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        // R01
        AiHarnessReplayExpectedRound r01 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r01);
        r01.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r01.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r01.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
        r01.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
        r01.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("DEFAULT_MONTH_TO_DATE", "SEMANTIC_EXPLICIT", "CURRENT_MESSAGE_EXPLICIT"));
        r01.setStartDate(m0);
        r01.setEndDate(m1);
        r01.setScopeType("GROUP");
        r01.setVisibleStoreRootCountMin(2);
        list.add(r01);

        // R02
        AiHarnessReplayExpectedRound r02 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r02);
        r02.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r02.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r02.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PRODUCE_CONSUME);
        r02.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_PRODUCE_CONSUME);
        r02.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r02.setStartDate(m0);
        r02.setEndDate(m1);
        r02.setScopeType("GROUP");
        r02.setVisibleStoreRootCountMin(2);
        list.add(r02);

        // R03
        AiHarnessReplayExpectedRound r03 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r03);
        r03.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r03.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r03.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PRODUCE_OUTPUT);
        r03.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_PRODUCE_OUTPUT);
        r03.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r03.setStartDate(m0);
        r03.setEndDate(m1);
        r03.setScopeType("GROUP");
        r03.setVisibleStoreRootCountMin(2);
        list.add(r03);

        // R04
        AiHarnessReplayExpectedRound r04 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r04);
        r04.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r04.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r04.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_WASTE);
        r04.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_WASTE);
        r04.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r04.setStartDate(m0);
        r04.setEndDate(m1);
        r04.setScopeType("GROUP");
        r04.setVisibleStoreRootCountMin(2);
        list.add(r04);

        // R05
        AiHarnessReplayExpectedRound r05 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r05);
        r05.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r05.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r05.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_LOSS);
        r05.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_LOSS);
        r05.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r05.setStartDate(m0);
        r05.setEndDate(m1);
        r05.setScopeType("GROUP");
        r05.setVisibleStoreRootCountMin(2);
        list.add(r05);

        // R06
        AiHarnessReplayExpectedRound r06 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r06);
        r06.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r06.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r06.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_RETURN);
        r06.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_RETURN);
        r06.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r06.setStartDate(m0);
        r06.setEndDate(m1);
        r06.setScopeType("GROUP");
        r06.setVisibleStoreRootCountMin(2);
        list.add(r06);

        // R07
        AiHarnessReplayExpectedRound r07 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r07);
        r07.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r07.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r07.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING);
        r07.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING);
        r07.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r07.setStartDate(m0);
        r07.setEndDate(m1);
        r07.setScopeType("GROUP");
        r07.setVisibleStoreRootCountMin(2);
        list.add(r07);

        // R08
        AiHarnessReplayExpectedRound r08 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r08);
        r08.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r08.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r08.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING);
        r08.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING);
        r08.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r08.setStartDate(m0);
        r08.setEndDate(m1);
        r08.setScopeType("GROUP");
        r08.setVisibleStoreRootCountMin(2);
        list.add(r08);

        // R09
        AiHarnessReplayExpectedRound r09 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r09);
        r09.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r09.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r09.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING);
        r09.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING);
        r09.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r09.setStartDate(m0);
        r09.setEndDate(m1);
        r09.setScopeType("GROUP");
        r09.setVisibleStoreRootCountMin(2);
        r09.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        r09.setMultiStoreScopeAppliedExpected(Boolean.TRUE);
        r09.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        r09.getMultiStoreMatchedStoresExpected().addAll(List.of("AAA", "汀兰餐厅"));
        r09.getQuerySemanticEffectiveMentionedStoreNames().addAll(List.of("AAA", "汀兰餐厅"));
        list.add(r09);

        // R10
        AiHarnessReplayExpectedRound r10 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r10);
        r10.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r10.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r10.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING);
        r10.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING);
        r10.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r10.setStartDate(m0);
        r10.setEndDate(m1);
        r10.setScopeType("GROUP");
        r10.setVisibleStoreRootCountMin(2);
        list.add(r10);

        // R11 round 1
        AiHarnessReplayExpectedRound r11a = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r11a);
        r11a.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r11a.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r11a.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
        r11a.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
        r11a.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r11a.setStartDate(m0);
        r11a.setEndDate(m1);
        r11a.setScopeType("GROUP");
        r11a.setVisibleStoreRootCountMin(2);
        list.add(r11a);

        // R11 round 2
        AiHarnessReplayExpectedRound r11b = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r11b);
        r11b.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r11b.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r11b.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
        r11b.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
        r11b.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "INHERITED_PREVIOUS",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "DEFAULT_MONTH_TO_DATE"));
        r11b.getQuerySemanticV2TimeActionAnyOf().addAll(List.of("OVERRIDE", "INHERIT_PREVIOUS"));
        r11b.setStartDate(p0);
        r11b.setEndDate(p1);
        r11b.setScopeType("GROUP");
        r11b.setVisibleStoreRootCountMin(2);
        list.add(r11b);

        // R12 round 1
        AiHarnessReplayExpectedRound r12a = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r12a);
        r12a.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        r12a.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW);
        r12a.getStructuredIntentDetailAnyOf()
                .addAll(List.of(
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY,
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_STATUS,
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_STATUS_COMPARE,
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS));
        r12a.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("DEFAULT_MONTH_TO_DATE", "SEMANTIC_EXPLICIT", "CURRENT_MESSAGE_EXPLICIT"));
        r12a.setStartDate(m0);
        r12a.setEndDate(m1);
        r12a.setScopeType("GROUP");
        r12a.setVisibleStoreRootCountMin(2);
        list.add(r12a);

        // R12 round 2
        AiHarnessReplayExpectedRound r12b = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r12b);
        r12b.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r12b.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r12b.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
        r12b.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
        r12b.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "INHERITED_PREVIOUS",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "DEFAULT_MONTH_TO_DATE"));
        r12b.getQuerySemanticV2TimeActionAnyOf().addAll(List.of("INHERIT_PREVIOUS", "OVERRIDE"));
        r12b.setStartDate(m0);
        r12b.setEndDate(m1);
        r12b.setScopeType("GROUP");
        r12b.setVisibleStoreRootCountMin(2);
        list.add(r12b);

        // R13 round 1
        AiHarnessReplayExpectedRound r13a = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r13a);
        r13a.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r13a.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r13a.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY);
        r13a.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY);
        r13a.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r13a.setStartDate(m0);
        r13a.setEndDate(m1);
        r13a.setScopeType("GROUP");
        r13a.setVisibleStoreRootCountMin(2);
        list.add(r13a);

        // R13 round 2
        AiHarnessReplayExpectedRound r13b = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r13b);
        r13b.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r13b.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r13b.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PRODUCE_CONSUME);
        r13b.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_PRODUCE_CONSUME);
        r13b.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "INHERITED_PREVIOUS",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "DEFAULT_MONTH_TO_DATE"));
        r13b.getQuerySemanticV2TimeActionAnyOf().addAll(List.of("INHERIT_PREVIOUS", "OVERRIDE"));
        r13b.setStartDate(m0);
        r13b.setEndDate(m1);
        r13b.setScopeType("GROUP");
        r13b.setVisibleStoreRootCountMin(2);
        list.add(r13b);

        // R14
        AiHarnessReplayExpectedRound r14 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r14);
        r14.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r14.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r14.getEffectiveIntentCodeNoneOf().add(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r14.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH);
        r14.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH);
        r14.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r14.setStartDate(m0);
        r14.setEndDate(m1);
        r14.setScopeType("GROUP");
        r14.setVisibleStoreRootCountMin(2);
        list.add(r14);

        // R15 — LLM 可能在 slow_moving vs mismatch 间波动
        AiHarnessReplayExpectedRound r15 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r15);
        r15.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r15.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r15.getEffectiveIntentCodeNoneOf().add(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r15.getStructuredIntentDetailAnyOf()
                .addAll(List.of(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SLOW_MOVING_RISK,
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH));
        r15.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r15.setStartDate(m0);
        r15.setEndDate(m1);
        r15.setScopeType("GROUP");
        r15.setVisibleStoreRootCountMin(2);
        list.add(r15);

        return list;
    }

    /**
     * 与 {@link #BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT} 对齐的 13 条用户问句（R01–R07 单轮 + R08–R10 各 2 轮）。
     */
    public static List<String> messagesBusinessSemantic1bResolvedContext() {
        return List.of(
                "这个月经营得怎么样？",
                "这个月整体有什么风险？",
                "哪个门店最需要关注？",
                "这个月营业额怎么样？",
                "哪个门店营业额最高？",
                "AAA 和汀兰餐厅哪个经营情况好？",
                "AAA 和汀兰餐厅哪个营业额高？",
                "这个月经营得怎么样？",
                "那上个月呢？",
                "这个月经营得怎么样？",
                "那采购呢？",
                "这个月经营得怎么样？",
                "那出库呢？");
    }

    /**
     * 内置 {@code caseId} 若绑定固定问句顺序，返回<b>可修改副本</b>；否则 {@code null}。
     * 供 {@link AiHarnessReplayService} 在请求体未带 {@code messages} 时补全。
     */
    public static List<String> builtinMessagesForCaseIdOrNull(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            return null;
        }
        String c = caseId.trim();
        if (BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT.equals(c)) {
            return new ArrayList<>(messagesBusinessSemantic1bResolvedContext());
        }
        if (STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT.equals(c)) {
            return new ArrayList<>(messagesStockReduceSemantic1cResolvedContext());
        }
        if (PURCHASE_TOOL_REQUEST_2A_MIN.equals(c)) {
            return new ArrayList<>(messagesPurchaseToolRequest2aMin());
        }
        if (PURCHASE_TOOL_REQUEST_2A_CORE.equals(c)) {
            return new ArrayList<>(messagesPurchaseToolRequest2aCore());
        }
        if (DRILLDOWN_PURCHASE_MATRIX_P1.equals(c)) {
            return new ArrayList<>(messagesDrilldownPurchaseMatrixP1());
        }
        if (DISH_PROFIT_MATRIX_P1.equals(c)) {
            return new ArrayList<>(messagesDishProfitMatrixP1());
        }
        if (STOCK_REDUCE_MATRIX_P1.equals(c)) {
            return new ArrayList<>(messagesStockReduceMatrixP1());
        }
        if (REVENUE_MATRIX_P1.equals(c)) {
            return new ArrayList<>(messagesRevenueMatrixP1());
        }
        if (WAREHOUSE_MATRIX_P1.equals(c)) {
            return new ArrayList<>(messagesWarehouseMatrixP1());
        }
        if (DISH_SALES_MATRIX_P1.equals(c)) {
            return new ArrayList<>(messagesDishSalesMatrixP1());
        }
        if (BUSINESS_DIAGNOSIS_DRILLDOWN_MATRIX_P1.equals(c)) {
            return new ArrayList<>(messagesBusinessDiagnosisDrilldownMatrixP1());
        }
        return null;
    }

    /** {@link #BUSINESS_DIAGNOSIS_DRILLDOWN_MATRIX_P1} 八轮问句（Matrix P1 契约 BD-A…BD-K）。 */
    public static List<String> messagesBusinessDiagnosisDrilldownMatrixP1() {
        return List.of(
                "这个月帮我做一下经营诊断",
                "哪个门店问题最大？",
                "为什么？",
                "AAA 为什么不好？",
                "是采购问题吗？",
                "是出库问题吗？",
                "是毛利问题吗？",
                "那怎么改？");
    }

    /** {@link #REVENUE_MATRIX_P1} 十轮问句（Matrix P1 契约 A–J）。 */
    public static List<String> messagesRevenueMatrixP1() {
        return List.of(
                "这个月营业额多少？",
                "哪个门店营业额最高？",
                "AAA 门店这个月营业额多少？",
                "AAA 和汀兰餐厅哪个营业额高？",
                "上个月营业额多少？",
                "那上个月呢？",
                "那哪个门店最高？",
                "本月和上月比怎么样？",
                "哪天营业额最高？",
                "营业额趋势怎么样？");
    }

    /** {@link #DISH_SALES_MATRIX_P1} 十轮问句（Matrix P1 契约 A–J）。 */
    public static List<String> messagesDishSalesMatrixP1() {
        return List.of(
                "这个月哪个菜卖得最好？",
                "哪个菜销量最高？",
                "哪个菜销量最低？",
                "核桃芽菜西芹这个月卖了多少份？",
                "AAA 门店哪个菜卖得最多？",
                "AAA 门店核桃芽菜西芹卖了多少？",
                "那上个月呢？",
                "那哪个菜最高？",
                "那毛利呢？",
                "菜品销量趋势怎么样？");
    }

    /** {@link #WAREHOUSE_MATRIX_P1} 九轮问句（Matrix P1 契约 A–I）。 */
    public static List<String> messagesWarehouseMatrixP1() {
        return List.of(
                "现在库存怎么样？",
                "哪个商品库存最多？",
                "哪个商品库存最少？",
                "哪个门店库存最多？",
                "AAA 门店库存怎么样？",
                "有没有缺货？",
                "有没有临期？",
                "那哪个商品最多？",
                "那 AAA 呢？");
    }

    /** {@link #STOCK_REDUCE_MATRIX_P1} 十一轮问句（Matrix P1 契约 A–K）。 */
    public static List<String> messagesStockReduceMatrixP1() {
        return List.of(
                "本月出库金额多少？",
                "哪个门店出库金额最高？",
                "生产耗用金额多少？",
                "废弃金额多少？",
                "损失金额多少？",
                "退货金额多少？",
                "哪个商品废弃最多？",
                "AAA 门店出库情况怎么样？",
                "那废弃呢？",
                "那损失呢？",
                "那哪个商品废弃最多？");
    }

    /** {@link #DISH_PROFIT_MATRIX_P1} 四轮问句（与 dish-profit-drilldown-matrix 契约一致）。 */
    public static List<String> messagesDishProfitMatrixP1() {
        return List.of(
                "上个月哪个菜毛利率最低？",
                "这个菜的成本构成是什么？",
                "上个月哪个菜毛利率最高？",
                "核桃芽菜西芹毛利怎么样？");
    }

    /** {@link #DRILLDOWN_PURCHASE_MATRIX_P1} 四轮问句（与 matrix 契约 §2 / prompt 9d 一致）。 */
    public static List<String> messagesDrilldownPurchaseMatrixP1() {
        return List.of(
                "这个月采购最多的商品是什么？",
                "第一名是谁供的？",
                "这个商品每个供货商分别采购了多少？",
                "哪个供货商单价最高？");
    }

    public static List<String> messagesPurchaseToolRequest2aMin() {
        return List.of("这个月采购金额多少？", "上个月呢？", "AAA 这个月采购金额多少？");
    }

    public static List<String> messagesPurchaseToolRequest2aCore() {
        return List.of(
                "这个月采购金额多少？",
                "上个月呢？",
                "AAA 这个月采购金额多少？",
                "哪个供货商金额最高？");
    }

    /**
     * {@link #BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT}：经营类 1B 矩阵；仅解析层断言（intent / path / wire / 时间 /
     * 范围 / v2 动作等），不比 Tool / AnswerPlan 行集 / Composer。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsBusinessSemantic1bResolvedContext(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        // R01
        AiHarnessReplayExpectedRound r01 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r01);
        r01.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        r01.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW);
        r01.getStructuredIntentDetailAnyOf()
                .addAll(List.of(
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY,
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_STATUS));
        r01.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("DEFAULT_MONTH_TO_DATE", "SEMANTIC_EXPLICIT", "CURRENT_MESSAGE_EXPLICIT"));
        r01.setStartDate(m0);
        r01.setEndDate(m1);
        r01.setScopeType("GROUP");
        r01.setVisibleStoreRootCountMin(2);
        list.add(r01);

        // R02
        AiHarnessReplayExpectedRound r02 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r02);
        r02.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r02.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r02.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY);
        r02.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY);
        r02.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r02.setStartDate(m0);
        r02.setEndDate(m1);
        r02.setScopeType("GROUP");
        r02.setVisibleStoreRootCountMin(2);
        list.add(r02);

        // R03
        AiHarnessReplayExpectedRound r03 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r03);
        r03.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r03.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r03.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_PRIORITY_RANKING);
        r03.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_STORE_PRIORITY_RANKING);
        r03.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r03.setStartDate(m0);
        r03.setEndDate(m1);
        r03.setScopeType("GROUP");
        r03.setVisibleStoreRootCountMin(2);
        list.add(r03);

        // R04
        AiHarnessReplayExpectedRound r04 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r04);
        r04.setEffectiveIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        r04.setEffectivePathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        r04.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY);
        r04.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY);
        r04.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r04.setStartDate(m0);
        r04.setEndDate(m1);
        r04.setScopeType("GROUP");
        r04.setVisibleStoreRootCountMin(2);
        list.add(r04);

        // R05
        AiHarnessReplayExpectedRound r05 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r05);
        r05.setEffectiveIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        r05.setEffectivePathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        r05.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING);
        r05.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING);
        r05.setRevenueAnswerPlanPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING);
        r05.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r05.setStartDate(m0);
        r05.setEndDate(m1);
        r05.setScopeType("GROUP");
        r05.setVisibleStoreRootCountMin(2);
        list.add(r05);

        // R06 — path / intent 按 merge 分叉 AnyOf
        AiHarnessReplayExpectedRound r06 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r06);
        r06.getEffectiveIntentCodeAnyOf()
                .addAll(List.of(AiResolvedQueryIntent.BUSINESS_OVERVIEW, AiResolvedQueryIntent.BUSINESS_DIAGNOSIS));
        r06.getEffectivePathCodeAnyOf()
                .addAll(List.of(
                        AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW, AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS));
        r06.getStructuredIntentDetailAnyOf()
                .addAll(List.of(
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_STATUS_COMPARE,
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS));
        r06.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r06.setStartDate(m0);
        r06.setEndDate(m1);
        r06.setScopeType("GROUP");
        r06.setVisibleStoreRootCountMin(2);
        r06.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        r06.setMultiStoreScopeAppliedExpected(Boolean.TRUE);
        r06.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        r06.getMultiStoreMatchedStoresExpected().addAll(List.of("AAA", "汀兰餐厅"));
        r06.getQuerySemanticEffectiveMentionedStoreNames().addAll(List.of("AAA", "汀兰餐厅"));
        list.add(r06);

        // R07
        AiHarnessReplayExpectedRound r07 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r07);
        r07.setEffectiveIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        r07.setEffectivePathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        r07.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING);
        r07.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING);
        r07.setRevenueAnswerPlanPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING);
        r07.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r07.setStartDate(m0);
        r07.setEndDate(m1);
        r07.setScopeType("GROUP");
        r07.setVisibleStoreRootCountMin(2);
        r07.setMultiStoreScopeDetectedExpected(Boolean.TRUE);
        r07.setMultiStoreScopeAppliedExpected(Boolean.TRUE);
        r07.setSingleStoreNarrowingBlockedExpected(Boolean.TRUE);
        r07.getMultiStoreMatchedStoresExpected().addAll(List.of("AAA", "汀兰餐厅"));
        r07.getQuerySemanticEffectiveMentionedStoreNames().addAll(List.of("AAA", "汀兰餐厅"));
        list.add(r07);

        // R08 round 1
        AiHarnessReplayExpectedRound r08a = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r08a);
        r08a.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        r08a.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW);
        r08a.getStructuredIntentDetailAnyOf()
                .addAll(List.of(
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY,
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_STATUS));
        r08a.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "DEFAULT_MONTH_TO_DATE",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "INHERITED_PREVIOUS"));
        r08a.setStartDate(m0);
        r08a.setEndDate(m1);
        r08a.setScopeType("GROUP");
        r08a.setVisibleStoreRootCountMin(2);
        list.add(r08a);

        // R08 round 2
        AiHarnessReplayExpectedRound r08b = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r08b);
        r08b.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        r08b.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW);
        r08b.getStructuredIntentDetailAnyOf()
                .addAll(List.of(
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY,
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_STATUS));
        r08b.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "INHERITED_PREVIOUS",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "SEMANTIC_EXPLICIT",
                        "DEFAULT_MONTH_TO_DATE"));
        r08b.setStartDate(p0);
        r08b.setEndDate(p1);
        r08b.setScopeType("GROUP");
        r08b.setVisibleStoreRootCountMin(2);
        r08b.getQuerySemanticV2TimeActionAnyOf().addAll(List.of("NEW", "OVERRIDE"));
        r08b.getEffectiveIntentCodeNoneOf().add(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        list.add(r08b);

        // R09 round 1
        AiHarnessReplayExpectedRound r09a = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r09a);
        r09a.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        r09a.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW);
        r09a.getStructuredIntentDetailAnyOf()
                .addAll(List.of(
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY,
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_STATUS));
        r09a.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("DEFAULT_MONTH_TO_DATE", "SEMANTIC_EXPLICIT", "CURRENT_MESSAGE_EXPLICIT"));
        r09a.setStartDate(m0);
        r09a.setEndDate(m1);
        r09a.setScopeType("GROUP");
        r09a.setVisibleStoreRootCountMin(2);
        list.add(r09a);

        // R09 round 2
        AiHarnessReplayExpectedRound r09b = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r09b);
        r09b.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r09b.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r09b.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY);
        r09b.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY);
        r09b.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "INHERITED_PREVIOUS",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "DEFAULT_MONTH_TO_DATE"));
        r09b.setQuerySemanticV2TimeActionExpected("INHERIT_PREVIOUS");
        r09b.setStartDate(m0);
        r09b.setEndDate(m1);
        r09b.setScopeType("GROUP");
        r09b.setVisibleStoreRootCountMin(2);
        list.add(r09b);

        // R10 round 1
        AiHarnessReplayExpectedRound r10a = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r10a);
        r10a.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        r10a.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW);
        r10a.getStructuredIntentDetailAnyOf()
                .addAll(List.of(
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY,
                        AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_STATUS));
        r10a.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("DEFAULT_MONTH_TO_DATE", "SEMANTIC_EXPLICIT", "CURRENT_MESSAGE_EXPLICIT"));
        r10a.setStartDate(m0);
        r10a.setEndDate(m1);
        r10a.setScopeType("GROUP");
        r10a.setVisibleStoreRootCountMin(2);
        list.add(r10a);

        // R10 round 2
        AiHarnessReplayExpectedRound r10b = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r10b);
        r10b.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r10b.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r10b.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
        r10b.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
        r10b.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of(
                        "INHERITED_PREVIOUS",
                        "SEMANTIC_EXPLICIT",
                        "CURRENT_MESSAGE_EXPLICIT",
                        "DEFAULT_MONTH_TO_DATE"));
        r10b.setQuerySemanticV2TimeActionExpected("INHERIT_PREVIOUS");
        r10b.setStartDate(m0);
        r10b.setEndDate(m1);
        r10b.setScopeType("GROUP");
        r10b.setVisibleStoreRootCountMin(2);
        list.add(r10b);

        return list;
    }

    public static List<AiHarnessReplayExpectedRound> expectationsRevenueAgentGraphCore(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(m0);
        r1.setEndDate(m1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setRevenueAnswerPlanPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW);
        r1.setHarnessReplayPlanSource("revenueAnswerPlan");
        r1.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        r1.setHarnessReplayRevenueAnswerPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW);
        r1.getUsedToolsMustContain().add(AiBusinessToolIds.REVENUE_QUERY);
        r1.setMasterRevenueToolResultSuccessExpected(Boolean.TRUE);
        r1.getConsumedAnswerPlansMustContain().add("DailyRevenueAnswerPlan");
        r1.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r1.getAnswerPreviewContainsAnyOf().addAll(List.of("营业额", "营收"));
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        r2.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("CURRENT_MESSAGE_EXPLICIT", "SEMANTIC_EXPLICIT", "INHERITED_PREVIOUS"));
        r2.setStartDate(p0);
        r2.setEndDate(p1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setRevenueAnswerPlanPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW);
        r2.setHarnessReplayPlanSource("revenueAnswerPlan");
        r2.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        r2.setHarnessReplayRevenueAnswerPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW);
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.REVENUE_QUERY);
        r2.setMasterRevenueToolResultSuccessExpected(Boolean.TRUE);
        r2.getConsumedAnswerPlansMustContain().add("DailyRevenueAnswerPlan");
        r2.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r2.getAnswerPreviewContainsAnyOf().addAll(List.of("营业额", "营收"));
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r3);
        r3.setEffectiveIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        r3.setEffectivePathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        r3.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r3.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r3.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r3.setStartDate(m0);
        r3.setEndDate(m1);
        r3.setScopeType("STORE");
        r3.getQueryStoreIdsMustContain().add(1);
        r3.setMentionedStore("AAA");
        r3.setRevenueAnswerPlanPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW);
        r3.setHarnessReplayPlanSource("revenueAnswerPlan");
        r3.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        r3.setHarnessReplayRevenueAnswerPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW);
        r3.getUsedToolsMustContain().add(AiBusinessToolIds.REVENUE_QUERY);
        r3.setMasterRevenueToolResultSuccessExpected(Boolean.TRUE);
        r3.getConsumedAnswerPlansMustContain().add("DailyRevenueAnswerPlan");
        r3.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r3.getAnswerPreviewContainsAnyOf().addAll(List.of("营业额", "营收"));
        list.add(r3);

        return list;
    }

    public static List<AiHarnessReplayExpectedRound> expectationsPurchaseAgentGraphCore(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(m0);
        r1.setEndDate(m1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setCheckPurchaseSourceType(Boolean.TRUE);
        r1.setPurchaseSourceType("ALL");
        r1.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r1.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r1.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW);
        r1.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r1.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r1.getConsumedAnswerPlansMustContain().add("PurchaseAnswerPlan");
        r1.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r1.getAnswerPreviewContainsAnyOf().add("采购");
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r2.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("CURRENT_MESSAGE_EXPLICIT", "SEMANTIC_EXPLICIT", "INHERITED_PREVIOUS"));
        r2.setStartDate(p0);
        r2.setEndDate(p1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setCheckPurchaseSourceType(Boolean.TRUE);
        r2.setPurchaseSourceType("ALL");
        r2.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r2.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r2.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW);
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r2.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r2.getConsumedAnswerPlansMustContain().add("PurchaseAnswerPlan");
        r2.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r2.getAnswerPreviewContainsAnyOf().add("采购");
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r3);
        r3.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r3.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r3.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r3.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r3.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r3.setStartDate(m0);
        r3.setEndDate(m1);
        r3.setScopeType("STORE");
        r3.getQueryStoreIdsMustContain().add(1);
        r3.setMentionedStore("AAA");
        r3.setCheckPurchaseSourceType(Boolean.TRUE);
        r3.setPurchaseSourceType("ALL");
        r3.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r3.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r3.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW);
        r3.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r3.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r3.getConsumedAnswerPlansMustContain().add("PurchaseAnswerPlan");
        r3.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r3.getAnswerPreviewContainsAnyOf().add("采购");
        list.add(r3);

        return list;
    }

    public static List<AiHarnessReplayExpectedRound> expectationsPurchaseToolRequest2aMin(LocalDateAnchor anchor) {
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();
        addPurchaseToolRequest2aOverviewRounds(list, anchor);
        return list;
    }

    public static List<AiHarnessReplayExpectedRound> expectationsPurchaseToolRequest2aCore(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<Long> groupPurchaseSql = List.of(1L, 2L, 5L, 3L, 4L);
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();
        addPurchaseToolRequest2aOverviewRounds(list, anchor);

        String supplierRanking = AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING;
        List<Long> storeAaaPurchaseSql = List.of(1L, 2L, 5L);
        AiHarnessReplayExpectedPlannedToolArgs r4pt = purchaseOverviewPlannedToolArgs(
                m0, m1, "STORE", storeAaaPurchaseSql, supplierRanking, null);
        r4pt.setCanonicalStructuredIntentDetailWire(supplierRanking);
        AiHarnessReplayExpectedRound r4 = new AiHarnessReplayExpectedRound();
        r4.setExpectedPlannedToolArgs(r4pt);
        applyPurchaseToolRequestHarnessProbes(r4);
        list.add(r4);

        return list;
    }

    private static void addPurchaseToolRequest2aOverviewRounds(
            List<AiHarnessReplayExpectedRound> list, LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<Long> groupPurchaseSql = List.of(1L, 2L, 5L, 3L, 4L);
        List<Long> storeAaaPurchaseSql = List.of(1L, 2L, 5L);
        String narrative = AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY;
        String sourceAll = AiQuerySemanticLexicon.SOURCE_ALL;

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        r1.setExpectedPlannedToolArgs(purchaseOverviewPlannedToolArgs(
                m0, m1, "GROUP", groupPurchaseSql, narrative, sourceAll));
        applyPurchaseToolRequestHarnessProbes(r1);
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        r2.setExpectedPlannedToolArgs(purchaseOverviewPlannedToolArgs(
                p0, p1, "GROUP", groupPurchaseSql, narrative, sourceAll));
        applyPurchaseToolRequestHarnessProbes(r2);
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        r3.setExpectedPlannedToolArgs(purchaseOverviewPlannedToolArgs(
                m0, m1, "STORE", storeAaaPurchaseSql, narrative, sourceAll));
        applyPurchaseToolRequestHarnessProbes(r3);
        list.add(r3);
    }

    private static void applyPurchaseToolRequestHarnessProbes(AiHarnessReplayExpectedRound round) {
        round.setToolExecuteSkippedExpected(Boolean.TRUE);
        round.setPurchaseAnswerPlanPresentExpected(Boolean.FALSE);
    }

    private static AiHarnessReplayExpectedPlannedToolArgs purchaseOverviewPlannedToolArgs(
            String startDate,
            String endDate,
            String scopeType,
            List<Long> purchaseSqlMustContain,
            String narrativeMode,
            String sourceFocus) {
        AiHarnessReplayExpectedPlannedToolArgs pt = new AiHarnessReplayExpectedPlannedToolArgs();
        pt.setToolId(AiBusinessToolIds.PURCHASE_OVERVIEW);
        pt.setStartDate(startDate);
        pt.setEndDate(endDate);
        pt.setScopeType(scopeType);
        if (purchaseSqlMustContain != null) {
            pt.getPurchaseSqlDepartmentIdsMustContain().addAll(purchaseSqlMustContain);
        }
        pt.setArgsPurchaseNarrativeMode(narrativeMode);
        pt.setArgsPurchaseSourceFocus(sourceFocus);
        return pt;
    }

    /**
     * 供货商排行 → 上个月 → 商品/单价下钻：与采购 {@link AiHarnessBuiltinCases#PURCHASE_AGENT_GRAPH_CORE} 同属单域 Graph，
     * 专测 {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} 锚点承接与 {@code purchase_source_goods_query} 路由。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsPurchaseSupplierRankingDrilldownGoodsUnitPrice3(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(m0);
        r1.setEndDate(m1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING);
        r1.setCheckPurchaseSourceType(Boolean.TRUE);
        r1.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        r1.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r1.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r1.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING);
        r1.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r1.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r1.getConsumedAnswerPlansMustContain().add("PurchaseAnswerPlan");
        r1.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r1.getAnswerPreviewContainsAnyOf().add("采购");
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r2.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("CURRENT_MESSAGE_EXPLICIT", "SEMANTIC_EXPLICIT", "INHERITED_PREVIOUS"));
        r2.setStartDate(p0);
        r2.setEndDate(p1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING);
        r2.setCheckPurchaseSourceType(Boolean.TRUE);
        r2.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        r2.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r2.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r2.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING);
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r2.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r2.getConsumedAnswerPlansMustContain().add("PurchaseAnswerPlan");
        r2.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r2.getAnswerPreviewContainsAnyOf().add("采购");
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r3);
        r3.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r3.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r3.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r3.setStartDate(p0);
        r3.setEndDate(p1);
        r3.setScopeType("GROUP");
        r3.setVisibleStoreRootCountMin(2);
        r3.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY);
        r3.setCheckPurchaseSourceType(Boolean.TRUE);
        r3.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        r3.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r3.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r3.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL);
        r3.setFollowUpActionExpected("OBJECT_DRILLDOWN");
        r3.setFollowUpTargetEntityTypeExpected("SUPPLIER");
        r3.setFollowUpTargetEntityNameMustBeNonBlank(Boolean.TRUE);
        r3.setFollowUpDetailWantedExpected("GOODS_UNIT_PRICE");
        r3.setFollowUpSourcePlanTypeExpected(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING);
        r3.setMatchedCapabilityIdExpected("purchase.supplier_anchor.goods_detail");
        r3.setFollowUpRegistryQueryModeExpected("supplier_anchor_goods_detail");
        r3.setSlotDetailWantedExpected("GOODS_UNIT_PRICE");
        r3.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r3.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r3.getConsumedAnswerPlansMustContain().add("PurchaseAnswerPlan");
        r3.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r3.getAnswerPreviewContainsAnyOf()
                .addAll(List.of("供货商口径查询", "采购的商品如下"));
        list.add(r3);

        return list;
    }

    /**
     * D-13.4：商品金额排行 Top1 GOODS 锚 →「哪些供应商、单价」追问；
     * 与 {@link #PURCHASE_AGENT_GRAPH_CORE} 同属单域 Graph，验收锚点协议与 {@code purchase_source_goods_query} 路由。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsPurchaseGoodsRankingDrilldownSupplierUnitPrice2(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(m0);
        r1.setEndDate(m1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING);
        r1.setCheckPurchaseSourceType(Boolean.TRUE);
        r1.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL);
        r1.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r1.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r1.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING);
        r1.setPurchaseAnswerPlanResultAnchorsCountMin(1);
        r1.getPurchaseAnswerPlanResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_GOODS);
        r1.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r1.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r1.getConsumedAnswerPlansMustContain().add("PurchaseAnswerPlan");
        r1.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r1.getAnswerPreviewContainsAnyOf().add("采购");
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r2.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r2.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r2.setStartDate(m0);
        r2.setEndDate(m1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY);
        r2.setCheckPurchaseSourceType(Boolean.TRUE);
        r2.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        r2.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r2.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r2.setFollowUpActionExpected("OBJECT_DRILLDOWN");
        r2.setFollowUpTargetEntityTypeExpected(AiResultAnchor.ENTITY_TYPE_GOODS);
        r2.setFollowUpTargetEntityNameMustBeNonBlank(Boolean.TRUE);
        r2.setFollowUpDetailWantedExpected("SUPPLIER_UNIT_PRICE");
        r2.setFollowUpSourcePlanTypeExpected(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING);
        r2.setMatchedCapabilityIdExpected("purchase.goods_anchor.supplier_unit_price");
        r2.setFollowUpRegistryQueryModeExpected("goods_anchor_supplier_unit_price");
        r2.setSlotDetailWantedExpected("SUPPLIER_UNIT_PRICE");
        r2.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL);
        r2.setPreviousTurnSummaryResultAnchorsCountMin(1);
        r2.getPreviousTurnSummaryResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_GOODS);
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r2.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r2.getConsumedAnswerPlansMustContain().add("PurchaseAnswerPlan");
        r2.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r2.getAnswerPreviewContainsAnyOf().add("采购");
        r2.setPurchaseSupplierGoodsDetailRowsOrNoDataOkExpected(Boolean.TRUE);
        list.add(r2);

        return list;
    }

    /**
     * Phase2-A：{@link #PURCHASE_GOODS_RANKING_SOURCE_BREAKDOWN_2} — 商品金额 Top1 → 来源拆桶。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsPurchaseGoodsRankingSourceBreakdown2(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(m0);
        r1.setEndDate(m1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING);
        r1.setCheckPurchaseSourceType(Boolean.TRUE);
        r1.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL);
        r1.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r1.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r1.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING);
        r1.setPurchaseAnswerPlanResultAnchorsCountMin(1);
        r1.getPurchaseAnswerPlanResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_GOODS);
        r1.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r1.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r2.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r2.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r2.setStartDate(m0);
        r2.setEndDate(m1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY);
        r2.setCheckPurchaseSourceType(Boolean.TRUE);
        r2.getPurchaseSourceTypeAnyOf().add(AiQuerySemanticLexicon.SOURCE_ALL);
        r2.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r2.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r2.setFollowUpActionExpected("OBJECT_DRILLDOWN");
        r2.setFollowUpTargetEntityTypeExpected(AiResultAnchor.ENTITY_TYPE_GOODS);
        r2.setFollowUpTargetEntityNameMustBeNonBlank(Boolean.TRUE);
        r2.setFollowUpTargetEntityIdMustBeNonBlank(Boolean.TRUE);
        r2.setFollowUpDetailWantedExpected("SOURCE_BREAKDOWN");
        r2.setFollowUpSourcePlanTypeExpected(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING);
        r2.setMatchedCapabilityIdExpected("purchase.goods_anchor.source_breakdown");
        r2.setFollowUpRegistryQueryModeExpected("goods_source_breakdown");
        r2.setSlotDetailWantedExpected("SOURCE_BREAKDOWN");
        r2.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN);
        r2.setPreviousTurnSummaryResultAnchorsCountMin(1);
        r2.getPreviousTurnSummaryResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_GOODS);
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r2.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r2.getPurchaseAnswerPlanFocusOrSecondaryRowsJsonMustContainSubstrings()
                .addAll(List.of("selfPurchaseAmount", "supplierPurchaseAmount", "disGoodsId"));
        list.add(r2);

        return list;
    }

    /**
     * Phase2-A：{@link #PURCHASE_SUPPLIER_FACET_GOODS_RANKING_SOURCE_BREAKDOWN_2} — 供货商 facet 商品 Top1 → 拆桶（Round2 ALL）。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsPurchaseSupplierFacetGoodsRankingSourceBreakdown2(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(m0);
        r1.setEndDate(m1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING);
        r1.setCheckPurchaseSourceType(Boolean.TRUE);
        r1.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        r1.setSemanticSlotQueryObject("GOODS");
        r1.setSemanticSlotOperation("RANKING");
        r1.setSemanticSlotMetric("PURCHASE_AMOUNT");
        r1.setSemanticSlotSourceFacet("SUPPLIER_PURCHASE");
        r1.setSemanticSlotAnchorPolicy("IGNORE_PREVIOUS_ANCHOR");
        r1.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r1.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r1.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING);
        r1.setPurchaseAnswerPlanResultAnchorsCountMin(1);
        r1.getPurchaseAnswerPlanResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_GOODS);
        r1.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r1.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r2.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r2.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r2.setStartDate(m0);
        r2.setEndDate(m1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY);
        r2.setCheckPurchaseSourceType(Boolean.TRUE);
        r2.getPurchaseSourceTypeAnyOf().add(AiQuerySemanticLexicon.SOURCE_ALL);
        r2.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r2.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r2.setFollowUpActionExpected("OBJECT_DRILLDOWN");
        r2.setFollowUpTargetEntityTypeExpected(AiResultAnchor.ENTITY_TYPE_GOODS);
        r2.setFollowUpTargetEntityNameMustBeNonBlank(Boolean.TRUE);
        r2.setFollowUpTargetEntityIdMustBeNonBlank(Boolean.TRUE);
        r2.setFollowUpDetailWantedExpected("SOURCE_BREAKDOWN");
        r2.setFollowUpSourcePlanTypeExpected(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING);
        r2.setMatchedCapabilityIdExpected("purchase.goods_anchor.source_breakdown");
        r2.setFollowUpRegistryQueryModeExpected("goods_source_breakdown");
        r2.setSlotDetailWantedExpected("SOURCE_BREAKDOWN");
        r2.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN);
        r2.setPreviousTurnSummaryResultAnchorsCountMin(1);
        r2.getPreviousTurnSummaryResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_GOODS);
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r2.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r2.getPurchaseAnswerPlanFocusOrSecondaryRowsJsonMustContainSubstrings()
                .addAll(List.of("totalPurchaseAmount", "selfPurchaseAmount", "supplierPurchaseAmount", "disGoodsId"));
        list.add(r2);

        return list;
    }

    /**
     * 见 {@link #PURCHASE_SUPPLIER_FACET_GOODS_AMOUNT_RANKING_IGNORE_ANCHOR_2}。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsPurchaseSupplierFacetGoodsAmountRankingIgnoreAnchor2(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(m0);
        r1.setEndDate(m1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING);
        r1.setCheckPurchaseSourceType(Boolean.TRUE);
        r1.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL);
        r1.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r1.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r1.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING);
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r2.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r2.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r2.setStartDate(m0);
        r2.setEndDate(m1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING);
        r2.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING);
        r2.setCheckPurchaseSourceType(Boolean.TRUE);
        r2.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        r2.setSemanticSlotQueryObject("GOODS");
        r2.setSemanticSlotOperation("RANKING");
        r2.setSemanticSlotMetric("PURCHASE_AMOUNT");
        r2.setSemanticSlotSourceFacet("SUPPLIER_PURCHASE");
        r2.setSemanticSlotAnchorPolicy("IGNORE_PREVIOUS_ANCHOR");
        r2.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r2.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r2.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING);
        list.add(r2);

        return list;
    }

    /**
     * 供货商渠道：上月订货金额 overview → 商品明细追问；与 {@link #PURCHASE_AGENT_GRAPH_CORE} 同属单域 Graph，
     * 覆盖 {@code purchase.supplier_channel.goods_detail} / {@code supplier_channel_goods_detail} 登记与承接。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsPurchaseSupplierChannelOverviewGoodsDetail2(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r1.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("CURRENT_MESSAGE_EXPLICIT", "SEMANTIC_EXPLICIT", "INHERITED_PREVIOUS"));
        r1.setStartDate(p0);
        r1.setEndDate(p1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY);
        r1.setCheckPurchaseSourceType(Boolean.TRUE);
        r1.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        r1.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r1.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r1.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW);
        r1.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r1.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r1.getConsumedAnswerPlansMustContain().add("PurchaseAnswerPlan");
        r1.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r1.getAnswerPreviewContainsAnyOf().add("采购");
        r1.getPurchaseAnswerPlanFocusOrSecondaryRowsJsonMustContainSubstrings()
                .addAll(List.of("totalPurchaseAmount", "68", "purchaseOrderCount", "3"));
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r2.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r2.setStartDate(p0);
        r2.setEndDate(p1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY);
        r2.setCheckPurchaseSourceType(Boolean.TRUE);
        r2.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        r2.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r2.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r2.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL);
        r2.setFollowUpActionExpected("OBJECT_DRILLDOWN");
        r2.setFollowUpDetailWantedExpected("GOODS_DETAIL");
        r2.setFollowUpSourcePlanTypeExpected(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW);
        r2.setMatchedCapabilityIdExpected("purchase.supplier_channel.goods_detail");
        r2.setFollowUpRegistryQueryModeExpected("supplier_channel_goods_detail");
        r2.setFramePlanTypeExpected(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW);
        r2.setFramePurchaseSourceTypeExpected(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        r2.setSlotDetailWantedExpected("GOODS_DETAIL");
        r2.setPurchaseSupplierGoodsDetailRowsCountMin(3);
        r2.getPurchaseAnswerPlanFocusOrSecondaryRowsJsonMustContainSubstrings()
                .addAll(List.of("goodsName", "去皮核桃仁", "三元原味酸奶", "红豆"));
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r2.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r2.getConsumedAnswerPlansMustContain().add("PurchaseAnswerPlan");
        r2.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r2.getAnswerPreviewContainsAnyOf()
                .addAll(List.of("去皮核桃仁", "三元原味酸奶", "红豆", "商品", "采购"));
        list.add(r2);

        return list;
    }

    /**
     * 见 {@link #PURCHASE_SUPPLIER_ANCHOR_THEN_SOURCE_AMOUNT_SUMMARY_2}。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsPurchaseSupplierAnchorThenSourceAmountSummary2(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(p0);
        r1.setEndDate(p1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING);
        r1.setCheckPurchaseSourceType(Boolean.TRUE);
        r1.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        r1.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r1.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r1.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING);
        r1.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r1.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r1.getConsumedAnswerPlansMustContain().add("PurchaseAnswerPlan");
        r1.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r1.getAnswerPreviewContainsAnyOf().add("采购");
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r2.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r2.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r2.setStartDate(p0);
        r2.setEndDate(p1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY);
        r2.setCheckPurchaseSourceType(Boolean.TRUE);
        r2.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        r2.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r2.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r2.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW);
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r2.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r2.getConsumedAnswerPlansMustContain().add("PurchaseAnswerPlan");
        r2.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r2.getAnswerPreviewContainsAnyOf().add("采购");
        list.add(r2);

        return list;
    }

    /**
     * {@link #BUSINESS_STORE_PRIORITY_DRILLDOWN_REASONS_3}：本月经营怎么样 → 门店优先级 → STORE 原因追问。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsBusinessStorePriorityDrilldownReasons3(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r1);
        r1.getEffectiveIntentCodeAnyOf()
                .addAll(List.of(
                        AiResolvedQueryIntent.BUSINESS_OVERVIEW,
                        AiResolvedQueryIntent.BUSINESS_DIAGNOSIS));
        r1.getEffectivePathCodeAnyOf()
                .addAll(List.of(
                        AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW,
                        AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS));
        r1.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(m0);
        r1.setEndDate(m1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setBusinessOverviewMultiAgentBatchCompletedExpected(Boolean.TRUE);
        r1.setBusinessOverviewAllExpectedDomainsAttemptedExpected(Boolean.TRUE);
        r1.setBusinessOverviewMultiAgentAnyDomainSuccessExpected(Boolean.TRUE);
        r1.getConsumedAnswerPlansMustContain()
                .addAll(List.of(
                        "DailyRevenueAnswerPlan",
                        "PurchaseAnswerPlan",
                        "StockReduceAnswerPlan",
                        "DishProfitAnswerPlan"));
        r1.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r1.getAnswerPreviewContainsAnyOf().addAll(List.of("经营诊断", "经营概览", "概览"));
        r1.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r2.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r2.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r2.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r2.setStartDate(m0);
        r2.setEndDate(m1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_PRIORITY_RANKING);
        r2.setDiagnosisPlanExistsExpected(Boolean.TRUE);
        r2.setDiagnosisQuestionTypeExpected(BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_PRIORITY_RANKING);
        r2.setDiagnosisPlanResultAnchorsCountMin(1);
        r2.getDiagnosisPlanResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_STORE);
        r2.setBusinessOverviewMultiAgentBatchCompletedExpected(Boolean.TRUE);
        r2.setBusinessOverviewAllExpectedDomainsAttemptedExpected(Boolean.TRUE);
        r2.setBusinessOverviewMultiAgentAnyDomainSuccessExpected(Boolean.TRUE);
        r2.getConsumedAnswerPlansMustContain()
                .addAll(List.of(
                        "DailyRevenueAnswerPlan",
                        "PurchaseAnswerPlan",
                        "StockReduceAnswerPlan",
                        "DishProfitAnswerPlan"));
        r2.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r2.getAnswerPreviewContainsAnyOf().addAll(List.of("门店综合风险", "问题最大的门店"));
        r2.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        r2.setHarnessReplayPlanSource("diagnosisPlan");
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r3);
        // Round 3 follow-up 可能走 v1 承接链或 semantic fallback；业务断言已覆盖 resolved / followUp / diagnosis。
        r3.setSemanticAdoptedFromExpected(null);
        r3.setSemanticFallbackUsedExpected(null);
        r3.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r3.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r3.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r3.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r3.setStartDate(m0);
        r3.setEndDate(m1);
        r3.setScopeType("GROUP");
        r3.setVisibleStoreRootCountMin(2);
        r3.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_RISK_REASONS_DRILLDOWN);
        r3.setEffectiveIntentSource("INHERITED_PREVIOUS");
        r3.getFollowUpActionAnyOf().addAll(List.of("OBJECT_DRILLDOWN", "DETAIL_DRILLDOWN"));
        r3.setFollowUpTargetEntityTypeExpected(AiResultAnchor.ENTITY_TYPE_STORE);
        r3.setFollowUpTargetEntityNameMustBeNonBlank(Boolean.TRUE);
        r3.setFollowUpDetailWantedExpected("STORE_RISK_REASONS");
        r3.setFollowUpSourcePlanTypeExpected(DiagnosisPlan.ANCHOR_SOURCE_STORE_PRIORITY_RANKING);
        r3.setDiagnosisQuestionTypeExpected(BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_RISK_REASONS);
        r3.setPreviousTurnSummaryResultAnchorsCountMin(1);
        r3.getPreviousTurnSummaryResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_STORE);
        r3.setDiagnosisPlanExistsExpected(Boolean.TRUE);
        r3.setBusinessOverviewMultiAgentBatchCompletedExpected(Boolean.TRUE);
        r3.setBusinessOverviewAllExpectedDomainsAttemptedExpected(Boolean.TRUE);
        r3.setBusinessOverviewMultiAgentAnyDomainSuccessExpected(Boolean.TRUE);
        r3.getConsumedAnswerPlansMustContain()
                .addAll(List.of(
                        "DailyRevenueAnswerPlan",
                        "PurchaseAnswerPlan",
                        "StockReduceAnswerPlan",
                        "DishProfitAnswerPlan"));
        r3.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r3.getAnswerPreviewContainsAnyOf()
                .addAll(List.of("上文判断问题最大的门店是", "营业额表现", "出库/核销金额"));
        r3.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        r3.setHarnessReplayPlanSource("diagnosisPlan");
        list.add(r3);

        return list;
    }

    /**
     * {@link #BUSINESS_DIAGNOSIS_DRILLDOWN_MATRIX_P1}：八轮 Matrix P1（BD-A…BD-K）。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsBusinessDiagnosisDrilldownMatrixP1(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r1.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(m0);
        r1.setEndDate(m1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY);
        r1.setDiagnosisDrilldownMatrixRowIdExpected("BD-A");
        r1.setDiagnosisFacetExpected("SUMMARY");
        r1.setBusinessOverviewMultiAgentBatchCompletedExpected(Boolean.TRUE);
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r2.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r2.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r2.setStartDate(m0);
        r2.setEndDate(m1);
        r2.setScopeType("GROUP");
        r2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_PRIORITY_RANKING);
        r2.setDiagnosisDrilldownMatrixRowIdExpected("BD-B");
        r2.setDiagnosisFacetExpected("STORE_PRIORITY");
        r2.setDiagnosisQuestionTypeExpected(BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_PRIORITY_RANKING);
        r2.setDiagnosisPlanExistsExpected(Boolean.TRUE);
        r2.setDiagnosisPlanResultAnchorsCountMin(1);
        r2.getDiagnosisPlanResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_STORE);
        r2.setHarnessReplayPlanSource("diagnosisPlan");
        r2.getAnswerPreviewContainsAnyOf().addAll(List.of("门店综合风险", "问题最大的门店"));
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r3);
        r3.setSemanticAdoptedFromExpected(null);
        r3.setSemanticFallbackUsedExpected(null);
        r3.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r3.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r3.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r3.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r3.setStartDate(m0);
        r3.setEndDate(m1);
        r3.setScopeType("GROUP");
        r3.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_RISK_REASONS_DRILLDOWN);
        r3.setDiagnosisDrilldownMatrixRowIdExpected("BD-C");
        r3.setDiagnosisFacetExpected("STORE_RISK_REASONS");
        r3.setDiagnosisQuestionTypeExpected(BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_RISK_REASONS);
        r3.getFollowUpActionAnyOf().addAll(List.of("OBJECT_DRILLDOWN", "DETAIL_DRILLDOWN"));
        r3.setFollowUpTargetEntityTypeExpected(AiResultAnchor.ENTITY_TYPE_STORE);
        r3.setFollowUpTargetEntityNameMustBeNonBlank(Boolean.TRUE);
        r3.setPreviousTurnSummaryResultAnchorsCountMin(1);
        r3.getPreviousTurnSummaryResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_STORE);
        r3.setDiagnosisPlanExistsExpected(Boolean.TRUE);
        r3.setHarnessReplayPlanSource("diagnosisPlan");
        r3.getAnswerPreviewContainsAnyOf().addAll(List.of("上文判断问题最大的门店是", "营业额表现"));
        list.add(r3);

        AiHarnessReplayExpectedRound r4 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r4);
        r4.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r4.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r4.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r4.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r4.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r4.setStartDate(m0);
        r4.setEndDate(m1);
        r4.setScopeType("STORE");
        r4.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_RISK_REASONS_DRILLDOWN);
        r4.setDiagnosisDrilldownMatrixRowIdExpected("BD-D");
        r4.setDiagnosisFacetExpected("STORE_RISK_REASONS");
        r4.setDiagnosisQuestionTypeExpected(BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_RISK_REASONS);
        r4.setDiagnosisTargetStoreNameMustContain("AAA");
        r4.setDiagnosisPlanExistsExpected(Boolean.TRUE);
        r4.setHarnessReplayPlanSource("diagnosisPlan");
        list.add(r4);

        AiHarnessReplayExpectedRound r5 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r5);
        r5.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r5.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r5.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r5.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r5.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r5.setStartDate(m0);
        r5.setEndDate(m1);
        r5.setScopeType("STORE");
        r5.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_PURCHASE);
        r5.setDiagnosisDrilldownMatrixRowIdExpected("BD-E");
        r5.setDiagnosisFacetExpected("PURCHASE");
        r5.setDiagnosisChildDomainExpected("PURCHASE");
        r5.setDiagnosisPlanExistsExpected(Boolean.TRUE);
        r5.setHarnessReplayPlanSource("diagnosisPlan");
        r5.getAnswerPreviewContainsAnyOf().add("采购");
        list.add(r5);

        AiHarnessReplayExpectedRound r6 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r6);
        r6.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r6.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r6.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r6.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r6.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r6.setStartDate(m0);
        r6.setEndDate(m1);
        r6.setScopeType("STORE");
        r6.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_STOCK_REDUCE);
        r6.setDiagnosisDrilldownMatrixRowIdExpected("BD-F");
        r6.setDiagnosisFacetExpected("STOCK_REDUCE");
        r6.setDiagnosisChildDomainExpected("STOCK_REDUCE");
        r6.setDiagnosisPlanExistsExpected(Boolean.TRUE);
        r6.setHarnessReplayPlanSource("diagnosisPlan");
        r6.getAnswerPreviewContainsAnyOf().addAll(List.of("出库", "核销"));
        list.add(r6);

        AiHarnessReplayExpectedRound r7 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r7);
        r7.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r7.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r7.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r7.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r7.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r7.setStartDate(m0);
        r7.setEndDate(m1);
        r7.setScopeType("STORE");
        r7.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_DISH_PROFIT);
        r7.setDiagnosisDrilldownMatrixRowIdExpected("BD-G");
        r7.setDiagnosisFacetExpected("DISH_PROFIT");
        r7.setDiagnosisChildDomainExpected("DISH_PROFIT");
        r7.setDiagnosisPlanExistsExpected(Boolean.TRUE);
        r7.setHarnessReplayPlanSource("diagnosisPlan");
        r7.getAnswerPreviewContainsAnyOf().addAll(List.of("毛利", "菜品"));
        list.add(r7);

        AiHarnessReplayExpectedRound r8 = new AiHarnessReplayExpectedRound();
        applyV2SemanticHarnessDefaults(r8);
        r8.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        r8.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        r8.setOrchestrationTaskModeExpected("MULTI_AGENT");
        r8.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r8.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r8.setStartDate(m0);
        r8.setEndDate(m1);
        r8.setScopeType("STORE");
        r8.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DIAGNOSIS_ACTION_FOLLOWUP);
        r8.setDiagnosisDrilldownMatrixRowIdExpected("BD-K");
        r8.setDiagnosisFacetExpected("ACTION");
        r8.setDiagnosisQuestionTypeExpected("ACTION_FOLLOWUP");
        r8.setDiagnosisPlanExistsExpected(Boolean.TRUE);
        r8.setHarnessReplayPlanSource("diagnosisPlan");
        r8.getAnswerPreviewContainsAnyOf().addAll(List.of("改进行动", "动作"));
        list.add(r8);

        return list;
    }

    public static List<AiHarnessReplayExpectedRound> expectationsStockReduceAgentGraphCore(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(m0);
        r1.setEndDate(m1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setHarnessReplayPlanSource("stockReduceAnswerPlan");
        r1.setHarnessReplayStockReduceAnswerPlanType(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW);
        r1.setHarnessReplayStockReduceReduceType(StockReduceAnswerPlan.REDUCE_TYPE_ALL);
        r1.getUsedToolsMustContain().add(AiBusinessToolIds.STOCK_REDUCE_QUERY);
        r1.setMasterStockReduceToolResultSuccessExpected(Boolean.TRUE);
        r1.getConsumedAnswerPlansMustContain().add("StockReduceAnswerPlan");
        r1.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r1.getAnswerPreviewContainsAnyOf().addAll(List.of("出库", "核销"));
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r2.getEffectiveTimeWindowSourceAnyOf()
                .addAll(List.of("CURRENT_MESSAGE_EXPLICIT", "SEMANTIC_EXPLICIT", "INHERITED_PREVIOUS"));
        r2.setStartDate(p0);
        r2.setEndDate(p1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setHarnessReplayPlanSource("stockReduceAnswerPlan");
        r2.setHarnessReplayStockReduceAnswerPlanType(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW);
        r2.setHarnessReplayStockReduceReduceType(StockReduceAnswerPlan.REDUCE_TYPE_ALL);
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.STOCK_REDUCE_QUERY);
        r2.setMasterStockReduceToolResultSuccessExpected(Boolean.TRUE);
        r2.getConsumedAnswerPlansMustContain().add("StockReduceAnswerPlan");
        r2.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r2.getAnswerPreviewContainsAnyOf().addAll(List.of("出库", "核销"));
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r3);
        r3.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r3.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        r3.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r3.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r3.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r3.setStartDate(m0);
        r3.setEndDate(m1);
        r3.setScopeType("STORE");
        r3.getQueryStoreIdsMustContain().add(1);
        r3.setMentionedStore("AAA");
        r3.setHarnessReplayPlanSource("stockReduceAnswerPlan");
        r3.setHarnessReplayStockReduceAnswerPlanType(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW);
        r3.setHarnessReplayStockReduceReduceType(StockReduceAnswerPlan.REDUCE_TYPE_ALL);
        r3.getUsedToolsMustContain().add(AiBusinessToolIds.STOCK_REDUCE_QUERY);
        r3.setMasterStockReduceToolResultSuccessExpected(Boolean.TRUE);
        r3.getConsumedAnswerPlansMustContain().add("StockReduceAnswerPlan");
        r3.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r3.getAnswerPreviewContainsAnyOf().addAll(List.of("出库", "核销"));
        list.add(r3);

        return list;
    }

    public static List<AiHarnessReplayExpectedRound> expectationsDishProfitAgentGraphCore(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(p0);
        r1.setEndDate(p1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN);
        r1.setDishProfitMetricType("RANKING_LOW_MARGIN");
        r1.setHarnessReplayPlanSource("dishProfitAnswerPlan");
        r1.setHarnessReplayDishProfitAnswerPlanType(DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN);
        r1.setHarnessReplayDishProfitAnswerPlanSortDirection("ASC");
        r1.getUsedToolsMustContain().add(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        r1.setMasterDishProfitToolResultSuccessExpected(Boolean.TRUE);
        r1.getConsumedAnswerPlansMustContain().add("DishProfitAnswerPlan");
        r1.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r1.getAnswerPreviewContainsAnyOf().addAll(List.of("毛利", "菜品"));
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        r2.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r2.setStartDate(p0);
        r2.setEndDate(p1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setMentionedDishName("核桃芽菜西芹");
        r2.setQuerySemanticV2TimeActionExpected("INHERIT_PREVIOUS");
        r2.setQuerySemanticV2MetricActionExpected("OVERRIDE");
        r2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY);
        r2.setDishProfitMetricType("GROSS_MARGIN");
        r2.setHarnessReplayPlanSource("dishProfitAnswerPlan");
        r2.setHarnessReplayDishProfitAnswerPlanType(DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE);
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        r2.setMasterDishProfitToolResultSuccessExpected(Boolean.TRUE);
        r2.getConsumedAnswerPlansMustContain().add("DishProfitAnswerPlan");
        r2.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r2.getAnswerPreviewContainsAnyOf().addAll(List.of("毛利", "菜品"));
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r3);
        r3.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        r3.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        r3.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r3.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r3.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r3.setStartDate(m0);
        r3.setEndDate(m1);
        r3.setScopeType("GROUP");
        r3.setVisibleStoreRootCountMin(2);
        r3.setQuerySemanticV2MetricActionExpected("OVERRIDE");
        r3.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN);
        r3.setDishProfitMetricType("RANKING_HIGH_MARGIN");
        r3.setMentionedDishNameMustBeAbsent(Boolean.TRUE);
        r3.setHarnessReplayPlanSource("dishProfitAnswerPlan");
        r3.setHarnessReplayDishProfitAnswerPlanType(DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN);
        r3.setHarnessReplayDishProfitAnswerPlanSortDirection("DESC");
        r3.getUsedToolsMustContain().add(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        r3.setMasterDishProfitToolResultSuccessExpected(Boolean.TRUE);
        r3.getConsumedAnswerPlansMustContain().add("DishProfitAnswerPlan");
        r3.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        r3.getAnswerPreviewContainsAnyOf().addAll(List.of("毛利", "菜品"));
        list.add(r3);

        return list;
    }

    /**
     * {@link #DISH_PROFIT_MATRIX_P1}：DISH 锚 4 轮下钻矩阵严格验收（低毛利 → 原料构成 → 高毛利 → 点名单菜）。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsDishProfitMatrixP1(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(p0);
        r1.setEndDate(p1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN);
        r1.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN);
        r1.setDishProfitMetricType("RANKING_LOW_MARGIN");
        r1.setHarnessReplayPlanSource("dishProfitAnswerPlan");
        r1.setHarnessReplayDishProfitAnswerPlanType(DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN);
        r1.setHarnessReplayDishProfitAnswerPlanSortDirection("ASC");
        r1.setDishProfitAnswerPlanPresentExpected(Boolean.TRUE);
        r1.setDishProfitAnswerPlanResultAnchorsCountMin(1);
        r1.getDishProfitAnswerPlanResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_DISH);
        r1.getUsedToolsMustContain().add(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        r1.setMasterDishProfitToolResultSuccessExpected(Boolean.TRUE);
        r1.getConsumedAnswerPlansMustContain().add("DishProfitAnswerPlan");
        r1.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        r2.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r2.setStartDate(p0);
        r2.setEndDate(p1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setQuerySemanticV2TimeActionExpected("INHERIT_PREVIOUS");
        r2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN);
        r2.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN);
        r2.setHarnessReplayPlanSource("dishProfitAnswerPlan");
        r2.setHarnessReplayDishProfitAnswerPlanType(DishProfitAnswerPlan.TYPE_DISH_INGREDIENT_COST_BREAKDOWN);
        r2.setDishProfitAnswerPlanPresentExpected(Boolean.TRUE);
        r2.setMentionedDishName("核桃芽菜西芹");
        r2.setMatchedCapabilityIdExpected(DishProfitDrilldownMatrix.CAPABILITY_DISH_ANCHOR_INGREDIENT_BREAKDOWN);
        r2.setFollowUpRegistryQueryModeExpected("dish_anchor_ingredient_breakdown");
        r2.setFollowUpActionExpected("OBJECT_DRILLDOWN");
        r2.setFollowUpTargetEntityTypeExpected(AiResultAnchor.ENTITY_TYPE_DISH);
        r2.setFollowUpTargetEntityNameMustBeNonBlank(Boolean.TRUE);
        r2.setFollowUpDetailWantedExpected(DishProfitDrilldownMatrix.DETAIL_WANTED_INGREDIENT_COST_BREAKDOWN);
        r2.setFollowUpSourcePlanTypeExpected(DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN);
        r2.setPreviousTurnSummaryResultAnchorsCountMin(1);
        r2.getPreviousTurnSummaryResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_DISH);
        r2.setNeedSemanticClarificationExpected(Boolean.FALSE);
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.DISH_INGREDIENT_COST_BREAKDOWN);
        r2.setDishIngredientCostBreakdownToolSuccessExpected(Boolean.TRUE);
        r2.getConsumedAnswerPlansMustContain().add("DishProfitAnswerPlan");
        r2.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r3);
        r3.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        r3.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        r3.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r3.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r3.setStartDate(p0);
        r3.setEndDate(p1);
        r3.setScopeType("GROUP");
        r3.setVisibleStoreRootCountMin(2);
        r3.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN);
        r3.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN);
        r3.setDishProfitMetricType("RANKING_HIGH_MARGIN");
        r3.setMentionedDishNameMustBeAbsent(Boolean.TRUE);
        r3.setHarnessReplayPlanSource("dishProfitAnswerPlan");
        r3.setHarnessReplayDishProfitAnswerPlanType(DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN);
        r3.setHarnessReplayDishProfitAnswerPlanSortDirection("DESC");
        r3.setDishProfitAnswerPlanPresentExpected(Boolean.TRUE);
        r3.setDishProfitAnswerPlanResultAnchorsCountMin(1);
        r3.getDishProfitAnswerPlanResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_DISH);
        r3.getUsedToolsMustContain().add(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        r3.setMasterDishProfitToolResultSuccessExpected(Boolean.TRUE);
        r3.getConsumedAnswerPlansMustContain().add("DishProfitAnswerPlan");
        r3.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        list.add(r3);

        AiHarnessReplayExpectedRound r4 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r4);
        r4.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        r4.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        r4.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r4.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r4.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r4.setStartDate(p0);
        r4.setEndDate(p1);
        r4.setScopeType("GROUP");
        r4.setVisibleStoreRootCountMin(2);
        r4.setMentionedDishName("核桃芽菜西芹");
        r4.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY);
        r4.setCanonicalStructuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY);
        r4.setDishProfitMetricType("GROSS_MARGIN");
        r4.setHarnessReplayPlanSource("dishProfitAnswerPlan");
        r4.setHarnessReplayDishProfitAnswerPlanType(DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE);
        r4.setDishProfitAnswerPlanPresentExpected(Boolean.TRUE);
        r4.setNeedSemanticClarificationExpected(Boolean.FALSE);
        r4.getUsedToolsMustContain().add(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        r4.setMasterDishProfitToolResultSuccessExpected(Boolean.TRUE);
        r4.getConsumedAnswerPlansMustContain().add("DishProfitAnswerPlan");
        r4.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        list.add(r4);

        return list;
    }

    /**
     * {@link #STOCK_REDUCE_MATRIX_P1}：出库本域 Matrix P1 严格验收（A–K）。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsStockReduceMatrixP1(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        list.add(stockReduceMatrixRound(
                m0,
                m1,
                "GROUP",
                2,
                null,
                StockReduceDrilldownMatrix.OVERVIEW,
                List.of("出库", "核销"),
                null,
                true));

        list.add(stockReduceMatrixRound(
                m0,
                m1,
                "GROUP",
                2,
                null,
                StockReduceDrilldownMatrix.STORE_AMOUNT_RANKING,
                List.of("门店", "出库"),
                null,
                true));

        list.add(stockReduceMatrixRound(
                m0,
                m1,
                "GROUP",
                2,
                null,
                StockReduceDrilldownMatrix.PRODUCTION_OVERVIEW,
                List.of("生产", "耗用"),
                null,
                true));

        list.add(stockReduceMatrixRound(
                m0,
                m1,
                "GROUP",
                2,
                null,
                StockReduceDrilldownMatrix.WASTE_OVERVIEW,
                List.of("废弃"),
                null,
                true));

        list.add(stockReduceMatrixRound(
                m0,
                m1,
                "GROUP",
                2,
                null,
                StockReduceDrilldownMatrix.LOSS_OVERVIEW,
                List.of("损失", "报损", "损耗"),
                null,
                true));

        list.add(stockReduceMatrixRound(
                m0,
                m1,
                "GROUP",
                2,
                null,
                StockReduceDrilldownMatrix.RETURN_OVERVIEW,
                List.of("退货"),
                null,
                true));

        list.add(stockReduceMatrixRound(
                m0,
                m1,
                "GROUP",
                2,
                null,
                StockReduceDrilldownMatrix.GOODS_WASTE_AMOUNT_RANKING,
                List.of("商品", "废弃"),
                StockReduceDrilldownMatrix.KNOWN_GAP_GOODS_WASTE_TYPE2_SQL_NOT_FILTERED,
                true));

        list.add(stockReduceMatrixRound(
                m0,
                m1,
                "STORE",
                null,
                "AAA",
                StockReduceDrilldownMatrix.OVERVIEW,
                List.of("出库", "核销"),
                null,
                true));

        list.add(stockReduceMatrixRound(
                m0,
                m1,
                "STORE",
                null,
                "AAA",
                StockReduceDrilldownMatrix.FACET_SWITCH_WASTE,
                List.of("废弃"),
                null,
                true));

        list.add(stockReduceMatrixRound(
                m0,
                m1,
                "STORE",
                null,
                "AAA",
                StockReduceDrilldownMatrix.FACET_SWITCH_LOSS,
                List.of("损失", "报损", "损耗"),
                null,
                true));

        list.add(stockReduceMatrixRound(
                m0,
                m1,
                "STORE",
                null,
                "AAA",
                StockReduceDrilldownMatrix.GOODS_WASTE_AMOUNT_RANKING,
                List.of("商品", "废弃"),
                StockReduceDrilldownMatrix.KNOWN_GAP_GOODS_WASTE_TYPE2_SQL_NOT_FILTERED,
                true));

        return list;
    }

    /**
     * {@link #REVENUE_MATRIX_P1}：营业额本域 Matrix P1 严格验收（A–J）。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsRevenueMatrixP1(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        list.add(revenueMatrixRound(
                m0, m1, "GROUP", 2, null,
                RevenueDrilldownMatrix.OVERVIEW,
                List.of("营业额", "营收"),
                null,
                false));

        list.add(revenueMatrixRound(
                m0, m1, "GROUP", 2, null,
                RevenueDrilldownMatrix.STORE_AMOUNT_RANKING,
                List.of("门店", "营业额"),
                null,
                false));

        list.add(revenueMatrixRound(
                m0, m1, "STORE", null, "AAA",
                RevenueDrilldownMatrix.SINGLE_STORE_OVERVIEW,
                List.of("营业额", "营收"),
                null,
                false));

        list.add(revenueMatrixRound(
                m0, m1, "GROUP", 2, null,
                RevenueDrilldownMatrix.STORE_COMPARE,
                List.of("营业额", "汀兰", "AAA"),
                RevenueDrilldownMatrix.KNOWN_GAP_STORE_COMPARE_NOT_PAIRWISE,
                false));

        list.add(revenueMatrixRound(
                p0, p1, "GROUP", 2, null,
                RevenueDrilldownMatrix.OVERVIEW,
                List.of("营业额", "营收"),
                null,
                false));

        list.add(revenueMatrixRound(
                p0, p1, "GROUP", 2, null,
                RevenueDrilldownMatrix.TIME_FOLLOWUP_PREV_MONTH,
                List.of("营业额", "上月"),
                null,
                false));

        list.add(revenueMatrixRound(
                p0, p1, "GROUP", 2, null,
                RevenueDrilldownMatrix.RANKING_FOLLOWUP_STORE_TOP,
                List.of("门店", "最高"),
                null,
                true));

        list.add(revenueMatrixRound(
                m0, m1, "GROUP", 2, null,
                RevenueDrilldownMatrix.PERIOD_COMPARE,
                List.of("比", "上月", "本月"),
                RevenueDrilldownMatrix.KNOWN_GAP_PERIOD_COMPARE_NOT_IMPLEMENTED,
                false));

        list.add(revenueMatrixRound(
                m0, m1, "GROUP", 2, null,
                RevenueDrilldownMatrix.DAILY_AMOUNT_RANKING,
                List.of("哪天", "最高"),
                RevenueDrilldownMatrix.KNOWN_GAP_DAILY_RANKING_CALENDAR_DATE_MISSING,
                false));

        list.add(revenueMatrixRound(
                m0, m1, "GROUP", 2, null,
                RevenueDrilldownMatrix.TREND,
                List.of("趋势"),
                RevenueDrilldownMatrix.KNOWN_GAP_TREND_SERIES_NOT_IMPLEMENTED,
                false));

        return list;
    }

    /**
     * {@link #WAREHOUSE_MATRIX_P1}：库房库存现量本域 Matrix P1 严格验收（A–I）。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsWarehouseMatrixP1(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        list.add(warehouseMatrixRound(
                m0, m1, "GROUP", 2, null,
                com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrix.OVERVIEW,
                List.of("库存"),
                null,
                true));

        list.add(warehouseMatrixRound(
                m0, m1, "GROUP", 2, null,
                com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrix.GOODS_AMOUNT_RANKING_HIGH,
                List.of("商品", "库存"),
                null,
                true));

        list.add(warehouseMatrixRound(
                m0, m1, "GROUP", 2, null,
                com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrix.GOODS_AMOUNT_RANKING_LOW,
                List.of("商品", "库存"),
                null,
                true));

        list.add(warehouseMatrixRound(
                m0, m1, "GROUP", 2, null,
                com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrix.STORE_AMOUNT_RANKING,
                List.of("门店", "库存"),
                null,
                true));

        list.add(warehouseMatrixRound(
                m0, m1, "STORE", null, "AAA",
                com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrix.SINGLE_STORE_OVERVIEW,
                List.of("库存"),
                null,
                true));

        list.add(warehouseMatrixRound(
                m0, m1, "GROUP", 2, null,
                com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrix.OUT_OF_STOCK,
                List.of("缺货"),
                com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrix.KNOWN_GAP_OUT_OF_STOCK_STRICT_NOT_SUPPORTED,
                true));

        list.add(warehouseMatrixRound(
                m0, m1, "GROUP", 2, null,
                com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrix.NEAR_EXPIRY,
                List.of("临期"),
                com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrix.KNOWN_GAP_NEAR_EXPIRY_NOT_IN_TOOL,
                true));

        list.add(warehouseMatrixRound(
                m0, m1, "GROUP", 2, null,
                com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrix.GOODS_RANKING_FOLLOWUP_HIGH,
                List.of("商品", "最多"),
                null,
                true));

        list.add(warehouseMatrixRound(
                m0, m1, "STORE", null, "AAA",
                com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrix.STORE_FOLLOWUP_AAA,
                List.of("库存", "AAA"),
                null,
                true));

        return list;
    }

    /**
     * {@link #DISH_SALES_MATRIX_P1}：菜品销量本域 Matrix P1 严格验收（A–J）。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsDishSalesMatrixP1(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        list.add(dishSalesMatrixRound(
                m0, m1, "GROUP", 2, null, null,
                DishSalesDrilldownMatrix.COUNT_RANKING_HIGH_A,
                List.of("销量", "最好"),
                null,
                false));

        list.add(dishSalesMatrixRound(
                m0, m1, "GROUP", 2, null, null,
                DishSalesDrilldownMatrix.COUNT_RANKING_HIGH_A,
                List.of("销量", "最高"),
                null,
                true));

        list.add(dishSalesMatrixRound(
                m0, m1, "GROUP", 2, null, null,
                DishSalesDrilldownMatrix.COUNT_RANKING_LOW,
                List.of("销量", "最低"),
                null,
                true));

        list.add(dishSalesMatrixRound(
                m0, m1, "GROUP", 2, null, "核桃芽菜西芹",
                DishSalesDrilldownMatrix.SINGLE_DISH,
                List.of("核桃芽菜西芹", "份"),
                null,
                false));

        list.add(dishSalesMatrixRound(
                m0, m1, "STORE", null, "AAA", null,
                DishSalesDrilldownMatrix.STORE_COUNT_RANKING,
                List.of("菜", "最多"),
                null,
                true));

        list.add(dishSalesMatrixRound(
                m0, m1, "STORE", null, "AAA", "核桃芽菜西芹",
                DishSalesDrilldownMatrix.STORE_SINGLE_DISH,
                List.of("核桃芽菜西芹"),
                null,
                false));

        list.add(dishSalesMatrixRound(
                p0, p1, "STORE", null, "AAA", "核桃芽菜西芹",
                DishSalesDrilldownMatrix.TIME_FOLLOWUP_PREV_MONTH,
                List.of("上月"),
                null,
                false));

        list.add(dishSalesMatrixRound(
                p0, p1, "STORE", null, "AAA", null,
                DishSalesDrilldownMatrix.RANKING_FOLLOWUP_HIGH,
                List.of("最高"),
                null,
                true));

        list.add(dishSalesMatrixRound(
                p0, p1, "STORE", null, "AAA", null,
                DishSalesDrilldownMatrix.CROSS_DOMAIN_PROFIT,
                List.of("毛利"),
                DishSalesDrilldownMatrix.KNOWN_GAP_CROSS_DOMAIN_DISH_PROFIT_NOT_IN_P1,
                true));

        list.add(dishSalesMatrixRound(
                m0, m1, "GROUP", 2, null, null,
                DishSalesDrilldownMatrix.TREND,
                List.of("趋势"),
                DishSalesDrilldownMatrix.KNOWN_GAP_TREND_SERIES_NOT_IMPLEMENTED,
                false));

        return list;
    }

    private static AiHarnessReplayExpectedRound dishSalesMatrixRound(
            String startDate,
            String endDate,
            String scopeType,
            Integer visibleStoreRootCountMin,
            String mentionedStore,
            String mentionedDish,
            com.nongxinle.ai.harness.followup.DishSalesDrilldownMatrixRow matrixRow,
            List<String> answerPreviewContainsAnyOf,
            String knownGapExpected,
            boolean inheritTime) {
        AiHarnessReplayExpectedRound r = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r);
        r.setEffectiveIntentCode(AiResolvedQueryIntent.DISH_SALES_QUERY);
        r.setEffectivePathCode(AiResolvedQueryIntent.PATH_DISH_SALES_QUERY);
        if (inheritTime) {
            r.getEffectiveTimeWindowSourceAnyOf()
                    .addAll(
                            List.of(
                                    "CURRENT_MESSAGE_EXPLICIT",
                                    "DEFAULT_MONTH_TO_DATE",
                                    "SEMANTIC_EXPLICIT",
                                    "INHERITED_PREVIOUS"));
        } else {
            r.getEffectiveTimeWindowSourceAnyOf()
                    .addAll(
                            List.of(
                                    "CURRENT_MESSAGE_EXPLICIT",
                                    "DEFAULT_MONTH_TO_DATE",
                                    "SEMANTIC_EXPLICIT"));
        }
        r.setStartDate(startDate);
        r.setEndDate(endDate);
        r.setScopeType(scopeType);
        if (visibleStoreRootCountMin != null) {
            r.setVisibleStoreRootCountMin(visibleStoreRootCountMin);
        }
        if (mentionedStore != null) {
            r.setMentionedStore(mentionedStore);
            r.getQueryStoreIdsMustContain().add(1);
        }
        if (mentionedDish != null) {
            r.setMentionedDishName(mentionedDish);
        }
        r.setStructuredIntentDetail(matrixRow.getStructuredIntentDetailWire());
        r.setCanonicalStructuredIntentDetailWire(matrixRow.getStructuredIntentDetailWire());
        r.setDishSalesMatrixRowIdExpected(matrixRow.getRowId());
        r.setHarnessReplayPlanSource("dishSalesAnswerPlan");
        r.setHarnessReplayDishSalesAnswerPlanType(matrixRow.getTargetDishSalesPlanType());
        if (knownGapExpected != null) {
            r.setDishSalesKnownGapExpected(knownGapExpected);
        } else {
            r.setDishSalesKnownGapMustBeAbsent(Boolean.TRUE);
        }
        r.getUsedToolsMustContain().add(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        r.getConsumedAnswerPlansMustContain().add("DishSalesAnswerPlan");
        r.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        if (DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH.equals(matrixRow.getTargetDishSalesPlanType())) {
            r.getAnswerPreviewMustNotContainAnyOf().add("Top3");
        }
        if (answerPreviewContainsAnyOf != null) {
            r.getAnswerPreviewContainsAnyOf().addAll(answerPreviewContainsAnyOf);
        }
        return r;
    }

    private static AiHarnessReplayExpectedRound warehouseMatrixRound(
            String startDate,
            String endDate,
            String scopeType,
            Integer visibleStoreRootCountMin,
            String mentionedStore,
            com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrixRow matrixRow,
            List<String> answerPreviewContainsAnyOf,
            String knownGapExpected,
            boolean inheritTime) {
        AiHarnessReplayExpectedRound r = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r);
        r.setEffectiveIntentCode(AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW);
        r.setEffectivePathCode(AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK);
        if (inheritTime) {
            r.getEffectiveTimeWindowSourceAnyOf()
                    .addAll(
                            List.of(
                                    "CURRENT_MESSAGE_EXPLICIT",
                                    "DEFAULT_MONTH_TO_DATE",
                                    "SEMANTIC_EXPLICIT",
                                    "INHERITED_PREVIOUS"));
        } else {
            r.getEffectiveTimeWindowSourceAnyOf()
                    .addAll(
                            List.of(
                                    "CURRENT_MESSAGE_EXPLICIT",
                                    "DEFAULT_MONTH_TO_DATE",
                                    "SEMANTIC_EXPLICIT"));
        }
        r.setStartDate(startDate);
        r.setEndDate(endDate);
        r.setScopeType(scopeType);
        if (visibleStoreRootCountMin != null) {
            r.setVisibleStoreRootCountMin(visibleStoreRootCountMin);
        }
        if (mentionedStore != null) {
            r.setMentionedStore(mentionedStore);
            r.getQueryStoreIdsMustContain().add(1);
        }
        r.setStructuredIntentDetail(matrixRow.getStructuredIntentDetailWire());
        r.setCanonicalStructuredIntentDetailWire(matrixRow.getStructuredIntentDetailWire());
        r.setWarehouseMatrixRowIdExpected(matrixRow.getRowId());
        r.setHarnessReplayPlanSource("warehouseAnswerPlan");
        r.setHarnessReplayWarehouseAnswerPlanType(matrixRow.getTargetWarehousePlanType());
        if (knownGapExpected != null) {
            r.setWarehouseKnownGapExpected(knownGapExpected);
        } else {
            r.setWarehouseKnownGapMustBeAbsent(Boolean.TRUE);
        }
        r.getUsedToolsMustContain().add(AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
        r.getConsumedAnswerPlansMustContain().add("WarehouseAnswerPlan");
        r.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        if (answerPreviewContainsAnyOf != null) {
            r.getAnswerPreviewContainsAnyOf().addAll(answerPreviewContainsAnyOf);
        }
        return r;
    }

    private static AiHarnessReplayExpectedRound revenueMatrixRound(
            String startDate,
            String endDate,
            String scopeType,
            Integer visibleStoreRootCountMin,
            String mentionedStore,
            com.nongxinle.ai.harness.followup.RevenueDrilldownMatrixRow matrixRow,
            List<String> answerPreviewContainsAnyOf,
            String knownGapExpected,
            boolean inheritTime) {
        AiHarnessReplayExpectedRound r = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r);
        r.setEffectiveIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        r.setEffectivePathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        if (inheritTime) {
            r.getEffectiveTimeWindowSourceAnyOf()
                    .addAll(
                            List.of(
                                    "CURRENT_MESSAGE_EXPLICIT",
                                    "DEFAULT_MONTH_TO_DATE",
                                    "SEMANTIC_EXPLICIT",
                                    "INHERITED_PREVIOUS"));
        } else {
            r.getEffectiveTimeWindowSourceAnyOf()
                    .addAll(
                            List.of(
                                    "CURRENT_MESSAGE_EXPLICIT",
                                    "DEFAULT_MONTH_TO_DATE",
                                    "SEMANTIC_EXPLICIT"));
        }
        r.setStartDate(startDate);
        r.setEndDate(endDate);
        r.setScopeType(scopeType);
        if (visibleStoreRootCountMin != null) {
            r.setVisibleStoreRootCountMin(visibleStoreRootCountMin);
        }
        if (mentionedStore != null) {
            r.setMentionedStore(mentionedStore);
            r.getQueryStoreIdsMustContain().add(1);
        }
        r.setStructuredIntentDetail(matrixRow.getStructuredIntentDetailWire());
        r.setCanonicalStructuredIntentDetailWire(matrixRow.getStructuredIntentDetailWire());
        r.setRevenueMatrixRowIdExpected(matrixRow.getRowId());
        r.setHarnessReplayPlanSource("revenueAnswerPlan");
        r.setHarnessReplayRevenueAnswerPlanProbePresent(Boolean.TRUE);
        r.setHarnessReplayRevenueAnswerPlanType(matrixRow.getTargetRevenuePlanType());
        r.setRevenueAnswerPlanPlanType(matrixRow.getTargetRevenuePlanType());
        if (knownGapExpected != null) {
            r.setRevenueKnownGapExpected(knownGapExpected);
        } else {
            r.setRevenueKnownGapMustBeAbsent(Boolean.TRUE);
        }
        r.getUsedToolsMustContain().add(AiBusinessToolIds.REVENUE_QUERY);
        r.setMasterRevenueToolResultSuccessExpected(Boolean.TRUE);
        r.getConsumedAnswerPlansMustContain().add("DailyRevenueAnswerPlan");
        r.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        if (answerPreviewContainsAnyOf != null) {
            r.getAnswerPreviewContainsAnyOf().addAll(answerPreviewContainsAnyOf);
        }
        return r;
    }

    private static AiHarnessReplayExpectedRound stockReduceMatrixRound(
            String startDate,
            String endDate,
            String scopeType,
            Integer visibleStoreRootCountMin,
            String mentionedStore,
            com.nongxinle.ai.harness.followup.StockReduceDrilldownMatrixRow matrixRow,
            List<String> answerPreviewContainsAnyOf,
            String knownGapExpected,
            boolean inheritTime) {
        AiHarnessReplayExpectedRound r = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r);
        r.setEffectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        r.setEffectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        if (inheritTime) {
            r.getEffectiveTimeWindowSourceAnyOf()
                    .addAll(
                            List.of(
                                    "CURRENT_MESSAGE_EXPLICIT",
                                    "DEFAULT_MONTH_TO_DATE",
                                    "SEMANTIC_EXPLICIT",
                                    "INHERITED_PREVIOUS"));
        } else {
            r.getEffectiveTimeWindowSourceAnyOf()
                    .addAll(
                            List.of(
                                    "CURRENT_MESSAGE_EXPLICIT",
                                    "DEFAULT_MONTH_TO_DATE",
                                    "SEMANTIC_EXPLICIT"));
        }
        r.setStartDate(startDate);
        r.setEndDate(endDate);
        r.setScopeType(scopeType);
        if (visibleStoreRootCountMin != null) {
            r.setVisibleStoreRootCountMin(visibleStoreRootCountMin);
        }
        if (mentionedStore != null) {
            r.setMentionedStore(mentionedStore);
            r.getQueryStoreIdsMustContain().add(1);
        }
        r.setStructuredIntentDetail(matrixRow.getStructuredIntentDetailWire());
        r.setCanonicalStructuredIntentDetailWire(matrixRow.getStructuredIntentDetailWire());
        r.setStockReduceMatrixRowIdExpected(matrixRow.getRowId());
        r.setHarnessReplayPlanSource("stockReduceAnswerPlan");
        r.setHarnessReplayStockReduceAnswerPlanType(matrixRow.getTargetStockReducePlanType());
        r.setHarnessReplayStockReduceReduceType(matrixRow.getReduceTypeLabel());
        if (knownGapExpected != null) {
            r.setStockReduceKnownGapExpected(knownGapExpected);
        } else {
            r.setStockReduceKnownGapMustBeAbsent(Boolean.TRUE);
        }
        r.getUsedToolsMustContain().add(AiBusinessToolIds.STOCK_REDUCE_QUERY);
        r.setMasterStockReduceToolResultSuccessExpected(Boolean.TRUE);
        r.getConsumedAnswerPlansMustContain().add("StockReduceAnswerPlan");
        r.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        if (answerPreviewContainsAnyOf != null) {
            r.getAnswerPreviewContainsAnyOf().addAll(answerPreviewContainsAnyOf);
        }
        return r;
    }

    /**
     * {@link #DRILLDOWN_PURCHASE_MATRIX_P1}：GOODS 锚 4 轮下钻矩阵严格验收（非 PROBE-only）。
     */
    public static List<AiHarnessReplayExpectedRound> expectationsDrilldownPurchaseMatrixP1(
            LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        String m0 = a.monthStartInclusive();
        String m1 = a.monthToDateInclusive();
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r1);
        r1.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r1.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r1.setStartDate(m0);
        r1.setEndDate(m1);
        r1.setScopeType("GROUP");
        r1.setVisibleStoreRootCountMin(2);
        r1.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING);
        r1.setCheckPurchaseSourceType(Boolean.TRUE);
        r1.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL);
        r1.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r1.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r1.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING);
        r1.setPurchaseAnswerPlanResultAnchorsCountMin(1);
        r1.getPurchaseAnswerPlanResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_GOODS);
        r1.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r1.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r1.getConsumedAnswerPlansMustContain().add("PurchaseAnswerPlan");
        r1.setMissingAnswerPlansMustBeEmpty(Boolean.TRUE);
        list.add(r1);

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r2);
        r2.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r2.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r2.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r2.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r2.setStartDate(m0);
        r2.setEndDate(m1);
        r2.setScopeType("GROUP");
        r2.setVisibleStoreRootCountMin(2);
        r2.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY);
        r2.setCheckPurchaseSourceType(Boolean.TRUE);
        r2.getPurchaseSourceTypeAnyOf().add(AiQuerySemanticLexicon.SOURCE_ALL);
        r2.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r2.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r2.setFollowUpActionExpected("OBJECT_DRILLDOWN");
        r2.setFollowUpTargetEntityTypeExpected(AiResultAnchor.ENTITY_TYPE_GOODS);
        r2.setFollowUpTargetEntityNameMustBeNonBlank(Boolean.TRUE);
        r2.setFollowUpTargetEntityIdMustBeNonBlank(Boolean.TRUE);
        r2.setFollowUpDetailWantedExpected("SOURCE_BREAKDOWN");
        r2.setFollowUpSourcePlanTypeExpected(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING);
        r2.setMatchedCapabilityIdExpected("purchase.goods_anchor.source_breakdown");
        r2.setFollowUpRegistryQueryModeExpected("goods_source_breakdown");
        r2.setSlotDetailWantedExpected("SOURCE_BREAKDOWN");
        r2.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN);
        r2.setPreviousTurnSummaryResultAnchorsCountMin(1);
        r2.getPreviousTurnSummaryResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_GOODS);
        r2.setNeedSemanticClarificationExpected(Boolean.FALSE);
        r2.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r2.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        r2.getPurchaseAnswerPlanFocusOrSecondaryRowsJsonMustContainSubstrings()
                .addAll(List.of("selfPurchaseAmount", "supplierPurchaseAmount", "disGoodsId"));
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r3);
        r3.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r3.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r3.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r3.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r3.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r3.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r3.setStartDate(m0);
        r3.setEndDate(m1);
        r3.setScopeType("GROUP");
        r3.setVisibleStoreRootCountMin(2);
        r3.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY);
        r3.setCheckPurchaseSourceType(Boolean.TRUE);
        r3.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        r3.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r3.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r3.setFollowUpActionExpected("OBJECT_DRILLDOWN");
        r3.setFollowUpTargetEntityTypeExpected(AiResultAnchor.ENTITY_TYPE_GOODS);
        r3.setFollowUpTargetEntityNameMustBeNonBlank(Boolean.TRUE);
        r3.setFollowUpTargetEntityIdMustBeNonBlank(Boolean.TRUE);
        r3.setFollowUpDetailWantedExpected("SUPPLIER_BREAKDOWN");
        r3.setFollowUpSourcePlanTypeExpected(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN);
        r3.setMatchedCapabilityIdExpected("purchase.goods_anchor.supplier_breakdown");
        r3.setFollowUpRegistryQueryModeExpected("goods_anchor_supplier_breakdown");
        r3.setSlotDetailWantedExpected("SUPPLIER_BREAKDOWN");
        r3.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL);
        r3.setPreviousTurnSummaryResultAnchorsCountMin(1);
        r3.getPreviousTurnSummaryResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_GOODS);
        r3.setPurchaseAnswerPlanResultAnchorsCountMin(1);
        r3.getPurchaseAnswerPlanResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_GOODS);
        r3.setNeedSemanticClarificationExpected(Boolean.FALSE);
        r3.setPurchaseSupplierGoodsDetailRowsOrNoDataOkExpected(Boolean.TRUE);
        r3.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r3.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        list.add(r3);

        AiHarnessReplayExpectedRound r4 = new AiHarnessReplayExpectedRound();
        applySingleDomainGraphCoreDefaults(r4);
        r4.setEffectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        r4.setEffectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        r4.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r4.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r4.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r4.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r4.setStartDate(m0);
        r4.setEndDate(m1);
        r4.setScopeType("GROUP");
        r4.setVisibleStoreRootCountMin(2);
        r4.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY);
        r4.setCheckPurchaseSourceType(Boolean.TRUE);
        r4.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        r4.setHarnessReplayPlanSource("purchaseAnswerPlan");
        r4.setHarnessReplayPurchaseAnswerPlanProbePresent(Boolean.TRUE);
        r4.setFollowUpActionExpected("OBJECT_DRILLDOWN");
        r4.setFollowUpTargetEntityTypeExpected(AiResultAnchor.ENTITY_TYPE_GOODS);
        r4.setFollowUpTargetEntityNameMustBeNonBlank(Boolean.TRUE);
        r4.setFollowUpDetailWantedExpected("SUPPLIER_UNIT_PRICE");
        r4.setFollowUpSourcePlanTypeExpected(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL);
        r4.setMatchedCapabilityIdExpected("purchase.goods_anchor.supplier_unit_price");
        r4.setFollowUpRegistryQueryModeExpected("goods_anchor_supplier_unit_price");
        r4.setSlotDetailWantedExpected("SUPPLIER_UNIT_PRICE");
        r4.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL);
        r4.setPreviousTurnSummaryResultAnchorsCountMin(1);
        r4.getPreviousTurnSummaryResultAnchorTypesMustContain().add(AiResultAnchor.ENTITY_TYPE_GOODS);
        r4.setNeedSemanticClarificationExpected(Boolean.FALSE);
        r4.setPurchaseSupplierGoodsDetailRowsOrNoDataOkExpected(Boolean.TRUE);
        r4.getUsedToolsMustContain().add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        r4.setMasterPurchaseToolResultSuccessExpected(Boolean.TRUE);
        list.add(r4);

        return list;
    }

    private AiHarnessBuiltinCases() {
    }
}
