package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
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
        r1.setStartDate(a.monthStartInclusive());
        r1.setEndDate(a.monthToDateInclusive());
        r1.setScopeType("GROUP");
        r1.getVisibleStoreRootIds().add(1L);
        r1.getVisibleStoreRootIds().add(3L);
        r1.setCheckPurchaseSourceType(Boolean.TRUE);
        r1.setPurchaseSourceType(null);
        list.add(r1);

        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        r2.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r2.setEffectivePathCode("purchase_overview_path");
        r2.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("TIME_SHIFT");
        r2.setStartDate(p0);
        r2.setEndDate(p1);
        r2.setScopeType("GROUP");
        r2.getVisibleStoreRootIds().add(1L);
        r2.getVisibleStoreRootIds().add(3L);
        r2.setCheckPurchaseSourceType(Boolean.TRUE);
        r2.setPurchaseSourceType(null);
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        r3.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r3.setEffectivePathCode("purchase_overview_path");
        r3.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r3.setStartDate(p0);
        r3.setEndDate(p1);
        r3.setScopeType("STORE");
        r3.getVisibleStoreRootIds().add(1L);
        r3.setCheckPurchaseSourceType(Boolean.TRUE);
        r3.setPurchaseSourceType(null);
        r3.setMentionedStore("AAA");
        list.add(r3);

        AiHarnessReplayExpectedRound r4 = new AiHarnessReplayExpectedRound();
        r4.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r4.setEffectivePathCode("purchase_overview_path");
        r4.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r4.setStartDate(p0);
        r4.setEndDate(p1);
        r4.setScopeType("STORE");
        r4.getVisibleStoreRootIds().add(1L);
        r4.setCheckPurchaseSourceType(Boolean.TRUE);
        r4.setPurchaseSourceType("SELF_PURCHASE");
        r4.setMentionedStore("AAA");
        list.add(r4);

        AiHarnessReplayExpectedRound r5 = new AiHarnessReplayExpectedRound();
        r5.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r5.setEffectivePathCode("purchase_overview_path");
        r5.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r5.setStartDate(p0);
        r5.setEndDate(p1);
        r5.setScopeType("STORE");
        r5.getVisibleStoreRootIds().add(3L);
        r5.setCheckPurchaseSourceType(Boolean.TRUE);
        r5.setPurchaseSourceType(null);
        r5.setMentionedStore("汀兰餐厅");
        list.add(r5);

        AiHarnessReplayExpectedRound r6 = new AiHarnessReplayExpectedRound();
        r6.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r6.setEffectivePathCode("purchase_overview_path");
        r6.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
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
        r7.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r7.setStartDate(p0);
        r7.setEndDate(p1);
        r7.setScopeType("STORE");
        r7.getVisibleStoreRootIds().add(3L);
        r7.setMentionedStore("汀兰餐厅");
        r7.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING);
        r7.setCheckPurchaseSourceType(Boolean.TRUE);
        r7.setPurchaseSourceType(null);
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
     * DiagnosisAgent v1：集团「本月问题」→ 单店（AAA）成本偏高 → 双店（AAA / 汀兰）并排原因；仅 Resolver Replay + 契约探针。
     */
    public static final String BUSINESS_DIAGNOSIS_V1_CORE_3 = "BUSINESS_DIAGNOSIS_V1_CORE_3";

    /**
     * BusinessOverview MultiAgent 四域固化：本月概览 →「那上个月呢」→ 双店经营对比；{@code GRAPH_RUN} 默认（或与
     * {@link #BUSINESS_DIAGNOSIS_V1_CORE_3} 同享 {@link AiHarnessReplayService} 默认图跑）。
     */
    public static final String BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3 = "BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3";

    /** 营业额单域 {@link AiHarnessReplayMode#GRAPH_RUN}：本月 → 上个月 → AAA 本月。 */
    public static final String REVENUE_AGENT_GRAPH_CORE = "REVENUE_AGENT_GRAPH_CORE";

    /** 采购单域 {@link AiHarnessReplayMode#GRAPH_RUN}。 */
    public static final String PURCHASE_AGENT_GRAPH_CORE = "PURCHASE_AGENT_GRAPH_CORE";

    /** 出库核销单域 {@link AiHarnessReplayMode#GRAPH_RUN}。 */
    public static final String STOCK_REDUCE_AGENT_GRAPH_CORE = "STOCK_REDUCE_AGENT_GRAPH_CORE";

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

    public static final String PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE = "PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.FakeRevenuePlannerReadBridge}：结构化 SUCCESS 闭环（Harness-only，非真实库）。
     */
    public static final String PLANNER_EXECUTOR_REVENUE_ADAPTER_FAKE_OK_CORE = "PLANNER_EXECUTOR_REVENUE_ADAPTER_FAKE_OK_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.RevenuePlannerRealReadBridge}：走真实
     * {@code revenue_query}；默认 Harness 计划不物化 {@code AiRunState}/{@code AiResolvedQueryContext}，摘要诚实降级。
     */
    public static final String PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_CORE =
            "PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.RevenuePlannerRealReadBridge}：单店物化
     * {@code AiRunState}/{@code AiResolvedQueryContext}，走真实 {@code revenue_query}（仍依赖环境 DB）。
     */
    public static final String PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE =
            "PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.RevenuePlannerRealReadBridge}：GROUP 双可见门店根
     * 物化 {@code AiRunState}/{@code AiResolvedQueryContext}，走真实 {@code revenue_query}（C-44 探测；不接 Composite）。
     *
     * @see com.nongxinle.ai.harness.replay.AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase
     */
    public static final String PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE =
            "PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.PurchasePlannerAgentAdapter}：无
     * {@link com.nongxinle.ai.planner.PurchasePlannerReadBridge} → 诚实降级（C-16）。
     */
    public static final String PLANNER_EXECUTOR_PURCHASE_ADAPTER_CORE = "PLANNER_EXECUTOR_PURCHASE_ADAPTER_CORE";

    /** PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.FakePurchasePlannerReadBridge}（Harness-only，非真实 Tool/DB）。 */
    public static final String PLANNER_EXECUTOR_PURCHASE_ADAPTER_FAKE_OK_CORE =
            "PLANNER_EXECUTOR_PURCHASE_ADAPTER_FAKE_OK_CORE";

    /** PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.PurchasePlannerRealReadBridge}（C-17：计划不物化上下文 → 诚实降级）。 */
    public static final String PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE =
            "PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.PurchasePlannerRealReadBridge}：
     * 物化最小 {@link com.nongxinle.ai.core.AiRunState} / {@link com.nongxinle.ai.context.AiResolvedQueryContext}，
     * 真实 {@code purchase_overview}（C-19）。
     */
    public static final String PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE =
            "PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.PurchasePlannerRealReadBridge}：GROUP 双可见门店根
     * 物化 {@code AiRunState}/{@code AiResolvedQueryContext}，走真实 {@code purchase_overview}（C-45 探测；不接 Composite）。
     *
     * @see com.nongxinle.ai.harness.replay.AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase
     */
    public static final String PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE =
            "PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.StockReducePlannerAgentAdapter}：无
     * {@link com.nongxinle.ai.planner.StockReducePlannerReadBridge} → 诚实降级（C-21）。
     */
    public static final String PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_CORE =
            "PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.FakeStockReducePlannerReadBridge}（Harness-only，非真实
     * Tool/DB）。
     */
    public static final String PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_FAKE_OK_CORE =
            "PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_FAKE_OK_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.StockReducePlannerRealReadBridge}（C-22：计划不物化
     * {@code AiRunState}/{@code AiResolvedQueryContext} → 诚实降级；不接 {@code StockReduceQueryToolExecutor}）。
     */
    public static final String PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_CORE =
            "PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.StockReducePlannerRealReadBridge}（Spring Bean）：
     * 物化最小 {@code AiRunState} / {@code AiResolvedQueryContext}，真实 {@code stock_reduce_query}（C-24）。
     */
    public static final String PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE =
            "PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.StockReducePlannerRealReadBridge}：GROUP 双可见门店根
     * 物化 {@code AiRunState}/{@code AiResolvedQueryContext}，走真实 {@code stock_reduce_query}（C-46 探测；不接 Composite）。
     *
     * @see com.nongxinle.ai.harness.replay.AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase
     */
    public static final String PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE =
            "PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.DishProfitPlannerAgentAdapter}：无
     * {@link com.nongxinle.ai.planner.DishProfitPlannerReadBridge} → 诚实降级（C-26）。
     */
    public static final String PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_CORE =
            "PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.FakeDishProfitPlannerReadBridge}（Harness-only，非真实
     * {@code DishProfitQueryToolExecutor}/DB）。
     */
    public static final String PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE =
            "PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.DishProfitPlannerRealReadBridge}（Harness {@code new}）：
     * 默认<strong>不</strong>物化 {@code AiRunState} / {@code AiResolvedQueryContext}，诚实降级（C-27 骨架）。
     */
    public static final String PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE =
            "PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.DishProfitPlannerRealReadBridge}（Spring Bean）：
     * 物化最小 {@code AiRunState} / {@code AiResolvedQueryContext}，真实 {@code dish_profit_analysis}（C-29）。
     */
    public static final String PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE =
            "PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE";

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.DishProfitPlannerRealReadBridge}：GROUP 双可见门店根
     * 物化 {@code AiRunState}/{@code AiResolvedQueryContext}，走真实 {@code dish_profit_analysis}（C-47 探测；非 Composite）。
     *
     * @see com.nongxinle.ai.harness.replay.AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase
     */
    public static final String PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE =
            "PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE";

    /**
     * PlannerExecutor MOCK：经营诊断<strong>组合</strong> Plan 骨架（C-31）— 六步全 mock，不接四条 Hydrated
     * RealBridge / 真实 Tool / LLM；{@code finalAnswerPlanType=BUSINESS_DIAGNOSIS_COMPOSITE}，
     * {@link com.nongxinle.ai.planner.PlannerFailureStrategy#CONTINUE_WITH_DEGRADED}。
     *
     * @see com.nongxinle.ai.harness.replay.AiPlannerExecutorBusinessDiagnosisCompositeGraphCase
     */
    public static final String PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE =
            "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE";

    /**
     * PlannerExecutor：经营诊断 Composite — <strong>仅</strong>营收步接
     * {@link com.nongxinle.ai.planner.RevenuePlannerRealReadBridge}（Hydrated，真实 {@code revenue_query}）；采购 /
     * 出库 / 菜品 / 诊断 / 建议仍为 mock（C-32）。
     *
     * @see com.nongxinle.ai.harness.replay.AiPlannerExecutorBusinessDiagnosisCompositeRevenueGraphCase
     */
    public static final String PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE =
            "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE";

    /**
     * PlannerExecutor：经营诊断 Composite — 营收 + 采购接
     * {@link com.nongxinle.ai.planner.RevenuePlannerRealReadBridge} /
     * {@link com.nongxinle.ai.planner.PurchasePlannerRealReadBridge}（Hydrated，真实 {@code revenue_query} /
     * {@code purchase_overview}）；出库 / 菜品 / 诊断 / 建议仍为 mock（C-33）。
     *
     * @see com.nongxinle.ai.harness.replay.AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseGraphCase
     */
    public static final String PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE =
            "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE";

    /**
     * PlannerExecutor：经营诊断 Composite — 营收 + 采购 + 出库接
     * {@link com.nongxinle.ai.planner.RevenuePlannerRealReadBridge} /
     * {@link com.nongxinle.ai.planner.PurchasePlannerRealReadBridge} /
     * {@link com.nongxinle.ai.planner.StockReducePlannerRealReadBridge}（Hydrated，真实 {@code revenue_query} /
     * {@code purchase_overview} / {@code stock_reduce_query}）；菜品 / 诊断 / 建议仍为 mock（C-34）。
     *
     * @see com.nongxinle.ai.harness.replay.AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseStockGraphCase
     */
    public static final String PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_STOCK_CORE =
            "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_STOCK_CORE";

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

    /** DB-free PlannerExecutor Replay caseIds（短路 {@link AiHarnessReplayPlannerExecutorMock}）。 */
    public static boolean isPlannerExecutorMockHarnessCase(String caseId) {
        if (caseId == null) {
            return false;
        }
        String t = caseId.trim();
        return PLANNER_EXECUTOR_MOCK_CORE.equals(t)
                || PLANNER_EXECUTOR_MOCK_DEGRADED_CORE.equals(t)
                || PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE.equals(t)
                || PLANNER_EXECUTOR_REVENUE_ADAPTER_FAKE_OK_CORE.equals(t)
                || PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_CORE.equals(t)
                || PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE.equals(t)
                || PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE.equals(t)
                || PLANNER_EXECUTOR_PURCHASE_ADAPTER_CORE.equals(t)
                || PLANNER_EXECUTOR_PURCHASE_ADAPTER_FAKE_OK_CORE.equals(t)
                || PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE.equals(t)
                || PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE.equals(t)
                || PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE.equals(t)
                || PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_CORE.equals(t)
                || PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_FAKE_OK_CORE.equals(t)
                || PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_CORE.equals(t)
                || PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE.equals(t)
                || PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE.equals(t)
                || PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_CORE.equals(t)
                || PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE.equals(t)
                || PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE.equals(t)
                || PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE.equals(t)
                || PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE.equals(t)
                || PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE.equals(t)
                || PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE.equals(t)
                || PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE.equals(t)
                || PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_STOCK_CORE.equals(t)
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
        r2.getEffectiveTimeWindowSourceAnyOf().add("INHERITED_PREVIOUS");
        r2.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("TIME_SHIFT");
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
        r2.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("TIME_SHIFT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
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
        r2.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("TIME_SHIFT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
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
        r2.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("TIME_SHIFT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("SEMANTIC_EXPLICIT");
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

    private AiHarnessBuiltinCases() {
    }
}
