package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.graph.business.scope.BusinessScopeResolutionSupport;
import com.nongxinle.ai.harness.AiHarnessDataScopeFixtures;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.planner.DishProfitPlannerAgentAdapter;
import com.nongxinle.ai.planner.DishProfitPlannerExecutionContext;
import com.nongxinle.ai.planner.DishProfitPlannerReadRequest;
import com.nongxinle.ai.planner.DishProfitPlannerVisibleStore;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerExecutorTrace;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerRevenueExecutionContext;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.PlannerStepResult;
import com.nongxinle.ai.planner.PlannerStepStatus;
import com.nongxinle.ai.planner.PurchasePlannerAgentAdapter;
import com.nongxinle.ai.planner.PurchasePlannerExecutionContext;
import com.nongxinle.ai.planner.PurchasePlannerReadRequest;
import com.nongxinle.ai.planner.PurchasePlannerVisibleStore;
import com.nongxinle.ai.planner.RecommendationPlannerMockAgentAdapter;
import com.nongxinle.ai.planner.RevenuePlannerAgentAdapter;
import com.nongxinle.ai.planner.RevenuePlannerReadRequest;
import com.nongxinle.ai.planner.RevenuePlannerVisibleStore;
import com.nongxinle.ai.planner.StockReducePlannerAgentAdapter;
import com.nongxinle.ai.planner.StockReducePlannerExecutionContext;
import com.nongxinle.ai.planner.StockReducePlannerReadRequest;
import com.nongxinle.ai.planner.StockReducePlannerVisibleStore;
import com.nongxinle.ai.security.AiPermissions;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composite strict Harness（C-35 / C-48 / C-42）共用的 Hydrated 上下文物化。
 * 原单域 Adapter GraphCase 已摘除（P1-B Final）。
 */
public final class PlannerCompositeHarnessContext {

    public static final String DIAGNOSIS_ANSWER_PLAN_REF = "HARNESS_COMPOSITE_DIAGNOSIS_ANSWER_PLAN_REF";
    public static final String FINAL_ANSWER_PLAN_TYPE = "BUSINESS_DIAGNOSIS_COMPOSITE";

    private PlannerCompositeHarnessContext() {
    }

    public static final class RevenueStore {
        private RevenueStore() {}
        public static final String HARNESS_RESOLVED_CONTEXT_REF =
                "HARNESS_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_RESOLVED_CTX_REF";
        public static final String HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE =
                "HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE_HYDRATED";

        /** 本地 AAA 门店根部门（gb_department_id=1）；子部门 2、5 由现有 Tool 解析展开，不在此展开。 */
        public static final long HARNESS_STORE_DEPARTMENT_ID = 1L;

        /** 与 {@link AiHarnessReplayPlannerExecutorMock} 首轮 {@code SYNTHETIC_RUN_ID_BASE + 0} 对齐。 */
        private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_000L;

        private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
        private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);


        public static AiResolvedQueryContext buildHydratedResolvedQueryContext() {
            List<AiStoreScopeDTO> stores =
                    List.of(
                            AiStoreScopeDTO.builder()
                                    .storeDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                                    .storeName("AAA")
                                    .build());
            AiResolvedOrgScope org =
                    AiResolvedOrgScope.builder()
                            .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                            .currentStoreDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                            .requestDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                            .visibleStores(stores)
                            .build();
            AiResolvedTimeWindow tw =
                    AiResolvedTimeWindow.builder()
                            .startDate(HARNESS_TIME_START)
                            .endDate(HARNESS_TIME_END)
                            .timeLabel("2026-05-01..2026-05-14 (Harness hydrated)")
                            .build();
            return AiResolvedQueryContext.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .userId(1L)
                    .orgScope(org)
                    .timeWindow(tw)
                    .build();
        }

        public static RevenuePlannerReadRequest buildFullHarnessRevenueReadRequest() {
            return RevenuePlannerReadRequest.builder()
                    .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                    .timeStart(HARNESS_TIME_START)
                    .timeEnd(HARNESS_TIME_END)
                    .timeLabel("2026-05-01..2026-05-14 (Harness hydrated)")
                    .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                    .visibleStores(
                            List.of(
                                    RevenuePlannerVisibleStore.builder()
                                            .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                                            .displayLabel("AAA")
                                            .build()))
                    .queryDepartmentIds(List.of(HARNESS_STORE_DEPARTMENT_ID))
                    .targetStoreDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                    .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE)
                    .build();
        }

        public static AiRunState buildHydratedRunState(AiResolvedQueryContext rq) {
            return AiRunState.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .conversationId(0L)
                    .userId(1L)
                    .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                    .distributerId(null)
                    .resolvedQueryContext(rq)
                    .toolResults(new HashMap<>())
                    .build();
        }
    }

    public static final class RevenueGroup {
        private RevenueGroup() {}
        public static final String HARNESS_RESOLVED_CONTEXT_REF =
                "HARNESS_REVENUE_ADAPTER_GROUP_HYDRATED_RESOLVED_CTX_REF";
        public static final String HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE =
                "HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE_GROUP_HYDRATED";

        public static final long HARNESS_STORE_AAA_DEPARTMENT_ID = 1L;
        /** 汀兰餐厅（门店根 gb_department_id=3），与 C-43 设计一致。 */
        public static final long HARNESS_STORE_TINGLAN_DEPARTMENT_ID = 3L;

        private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
        private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);

        /** 与 STORE Hydrated 错开 runId，便于日志区分。 */
        private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_051L;


        public static AiResolvedQueryContext buildHydratedResolvedQueryContext() {
            List<AiStoreScopeDTO> stores =
                    List.of(
                            AiStoreScopeDTO.builder()
                                    .storeDepartmentId(HARNESS_STORE_AAA_DEPARTMENT_ID)
                                    .storeName("AAA")
                                    .build(),
                            AiStoreScopeDTO.builder()
                                    .storeDepartmentId(HARNESS_STORE_TINGLAN_DEPARTMENT_ID)
                                    .storeName("汀兰餐厅")
                                    .build());
            AiResolvedOrgScope org =
                    AiResolvedOrgScope.builder()
                            .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                            .currentStoreDepartmentId(null)
                            .requestDepartmentId(null)
                            .visibleStores(stores)
                            .build();
            AiResolvedTimeWindow tw =
                    AiResolvedTimeWindow.builder()
                            .startDate(HARNESS_TIME_START)
                            .endDate(HARNESS_TIME_END)
                            .timeLabel("2026-05-01..2026-05-14 (Harness GROUP hydrated)")
                            .build();
            AiResolvedDataScope dataScope = AiHarnessDataScopeFixtures.fromOrgScope(org);
            return AiResolvedQueryContext.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .userId(1L)
                    .orgScope(org)
                    .dataScope(dataScope)
                    .timeWindow(tw)
                    .build();
        }

        public static RevenuePlannerReadRequest buildFullHarnessRevenueReadRequest() {
            return RevenuePlannerReadRequest.builder()
                    .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                    .timeStart(HARNESS_TIME_START)
                    .timeEnd(HARNESS_TIME_END)
                    .timeLabel("2026-05-01..2026-05-14 (Harness GROUP hydrated)")
                    .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                    .visibleStores(
                            List.of(
                                    RevenuePlannerVisibleStore.builder()
                                            .departmentId(HARNESS_STORE_AAA_DEPARTMENT_ID)
                                            .displayLabel("AAA")
                                            .build(),
                                    RevenuePlannerVisibleStore.builder()
                                            .departmentId(HARNESS_STORE_TINGLAN_DEPARTMENT_ID)
                                            .displayLabel("汀兰餐厅")
                                            .build()))
                    .queryDepartmentIds(List.of(HARNESS_STORE_AAA_DEPARTMENT_ID, HARNESS_STORE_TINGLAN_DEPARTMENT_ID))
                    .targetStoreDepartmentId(null)
                    .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE)
                    .build();
        }

        public static AiRunState buildHydratedRunState(AiResolvedQueryContext rq) {
            return AiRunState.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .conversationId(0L)
                    .userId(1L)
                    .departmentId(null)
                    .distributerId(null)
                    .resolvedQueryContext(rq)
                    .toolResults(new HashMap<>())
                    .build();
        }
    }

    public static final class PurchaseStore {
        private PurchaseStore() {}
        public static final String HARNESS_RESOLVED_CONTEXT_REF =
                "HARNESS_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_RESOLVED_CTX_REF";
        public static final String HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE =
                "HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE_HYDRATED";

        /** 本地 AAA 门店根（gb_department_id=1）。 */
        public static final long HARNESS_STORE_DEPARTMENT_ID = 1L;

        /** Harness：与 {@link AiHarnessReplayPlannerExecutorMock} 首轮 {@code SYNTHETIC_RUN_ID_BASE + 0} 对齐。 */
        private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_000L;

        /** C-19：显式 disId，满足 Tool 非空校验（环境不一致时需换为库内真实分销商 ID）。 */
        public static final long HARNESS_PURCHASE_DISTRIBUTER_ID = 2L;

        private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
        private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);


        public static AiResolvedQueryContext buildHydratedResolvedQueryContext() {
            List<AiStoreScopeDTO> stores =
                    List.of(
                            AiStoreScopeDTO.builder()
                                    .storeDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                                    .storeName("AAA")
                                    .build());
            AiResolvedOrgScope org =
                    AiResolvedOrgScope.builder()
                            .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                            .currentStoreDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                            .requestDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                            .visibleStores(stores)
                            .build();
            AiResolvedTimeWindow tw =
                    AiResolvedTimeWindow.builder()
                            .startDate(HARNESS_TIME_START)
                            .endDate(HARNESS_TIME_END)
                            .timeLabel("2026-05-01..2026-05-14 (Harness purchase hydrated)")
                            .build();
            AiResolvedQueryIntent qi =
                    AiResolvedQueryIntent.builder()
                            .intentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW)
                            .pathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW)
                            .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY)
                            .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                            .build();
            return AiResolvedQueryContext.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .userId(1L)
                    .orgScope(org)
                    .timeWindow(tw)
                    .queryIntent(qi)
                    .effectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW)
                    .effectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW)
                    .build();
        }

        public static PurchasePlannerReadRequest buildFullHarnessPurchaseReadRequest() {
            return PurchasePlannerReadRequest.builder()
                    .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                    .timeStart(HARNESS_TIME_START)
                    .timeEnd(HARNESS_TIME_END)
                    .timeLabel("2026-05-01..2026-05-14 (Harness purchase hydrated)")
                    .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                    .visibleStores(
                            List.of(
                                    PurchasePlannerVisibleStore.builder()
                                            .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                                            .displayLabel("AAA")
                                            .build()))
                    .queryDepartmentIds(List.of(HARNESS_STORE_DEPARTMENT_ID))
                    .targetStoreDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                    .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                    .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY)
                    .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE)
                    .build();
        }

        public static AiRunState buildHydratedRunState(AiResolvedQueryContext rq) {
            return AiRunState.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .conversationId(0L)
                    .userId(1L)
                    .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                    .distributerId(HARNESS_PURCHASE_DISTRIBUTER_ID)
                    .resolvedQueryContext(rq)
                    .toolResults(new HashMap<>())
                    .groupPurchaseOverview(false)
                    .build();
        }
    }

    public static final class PurchaseGroup {
        private PurchaseGroup() {}
        public static final String HARNESS_RESOLVED_CONTEXT_REF =
                "HARNESS_PURCHASE_ADAPTER_GROUP_HYDRATED_RESOLVED_CTX_REF";
        public static final String HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE =
                "HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE_GROUP_HYDRATED";

        public static final long HARNESS_STORE_AAA_DEPARTMENT_ID = 1L;
        /** 汀兰餐厅（门店根 gb_department_id=3），与 C-43 / 营收 GROUP 一致。 */
        public static final long HARNESS_STORE_TINGLAN_DEPARTMENT_ID = 3L;

        /** 与 C-19 STORE Hydrated 文档一致的环境 disId。 */
        public static final long HARNESS_PURCHASE_DISTRIBUTER_ID =
                PlannerCompositeHarnessContext.PurchaseStore.HARNESS_PURCHASE_DISTRIBUTER_ID;

        private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
        private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);

        /** 与营收 GROUP（9_000_051）错开。 */
        private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_052L;


        public static AiResolvedQueryContext buildHydratedResolvedQueryContext() {
            List<AiStoreScopeDTO> stores =
                    List.of(
                            AiStoreScopeDTO.builder()
                                    .storeDepartmentId(HARNESS_STORE_AAA_DEPARTMENT_ID)
                                    .storeName("AAA")
                                    .build(),
                            AiStoreScopeDTO.builder()
                                    .storeDepartmentId(HARNESS_STORE_TINGLAN_DEPARTMENT_ID)
                                    .storeName("汀兰餐厅")
                                    .build());
            AiResolvedOrgScope org =
                    AiResolvedOrgScope.builder()
                            .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                            .currentStoreDepartmentId(null)
                            .requestDepartmentId(null)
                            .visibleStores(stores)
                            .build();
            AiResolvedTimeWindow tw =
                    AiResolvedTimeWindow.builder()
                            .startDate(HARNESS_TIME_START)
                            .endDate(HARNESS_TIME_END)
                            .timeLabel("2026-05-01..2026-05-14 (Harness purchase GROUP hydrated)")
                            .build();
            AiResolvedQueryIntent qi =
                    AiResolvedQueryIntent.builder()
                            .intentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW)
                            .pathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW)
                            .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY)
                            .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                            .build();
            AiResolvedDataScope dataScope = AiHarnessDataScopeFixtures.fromOrgScope(org);
            return AiResolvedQueryContext.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .userId(1L)
                    .orgScope(org)
                    .dataScope(dataScope)
                    .timeWindow(tw)
                    .queryIntent(qi)
                    .effectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW)
                    .effectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW)
                    .build();
        }

        public static PurchasePlannerReadRequest buildFullHarnessPurchaseReadRequest() {
            return PurchasePlannerReadRequest.builder()
                    .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                    .timeStart(HARNESS_TIME_START)
                    .timeEnd(HARNESS_TIME_END)
                    .timeLabel("2026-05-01..2026-05-14 (Harness purchase GROUP hydrated)")
                    .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                    .visibleStores(
                            List.of(
                                    PurchasePlannerVisibleStore.builder()
                                            .departmentId(HARNESS_STORE_AAA_DEPARTMENT_ID)
                                            .displayLabel("AAA")
                                            .build(),
                                    PurchasePlannerVisibleStore.builder()
                                            .departmentId(HARNESS_STORE_TINGLAN_DEPARTMENT_ID)
                                            .displayLabel("汀兰餐厅")
                                            .build()))
                    .queryDepartmentIds(List.of(HARNESS_STORE_AAA_DEPARTMENT_ID, HARNESS_STORE_TINGLAN_DEPARTMENT_ID))
                    .targetStoreDepartmentId(null)
                    .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                    .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY)
                    .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE)
                    .build();
        }

        public static AiRunState buildHydratedRunState(AiResolvedQueryContext rq) {
            return AiRunState.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .conversationId(0L)
                    .userId(1L)
                    .departmentId(null)
                    .distributerId(HARNESS_PURCHASE_DISTRIBUTER_ID)
                    .resolvedQueryContext(rq)
                    .toolResults(new HashMap<>())
                    .groupPurchaseOverview(true)
                    .build();
        }
    }

    public static final class StockReduceStore {
        private StockReduceStore() {}
        public static final String HARNESS_RESOLVED_CONTEXT_REF =
                "HARNESS_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_RESOLVED_CTX_REF";
        public static final String HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE =
                "HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE_HYDRATED";

        /** 单店 AAA（gb_department_id=1）。 */
        public static final long HARNESS_STORE_DEPARTMENT_ID = 1L;

        /** 与 {@link AiHarnessReplayPlannerExecutorMock} 首轮 {@code SYNTHETIC_RUN_ID_BASE + 0} 对齐。 */
        private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_000L;

        public static final long HARNESS_STOCK_REDUCE_DISTRIBUTER_ID = 2L;

        private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
        private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);


        public static AiResolvedQueryContext buildHydratedResolvedQueryContext() {
            List<AiStoreScopeDTO> stores =
                    List.of(
                            AiStoreScopeDTO.builder()
                                    .storeDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                                    .storeName("AAA")
                                    .build());
            AiResolvedOrgScope org =
                    AiResolvedOrgScope.builder()
                            .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                            .currentStoreDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                            .requestDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                            .visibleStores(stores)
                            .build();
            AiResolvedTimeWindow tw =
                    AiResolvedTimeWindow.builder()
                            .startDate(HARNESS_TIME_START)
                            .endDate(HARNESS_TIME_END)
                            .timeLabel("2026-05-01..2026-05-14 (Harness stock-reduce hydrated)")
                            .build();
            AiResolvedQueryIntent qi =
                    AiResolvedQueryIntent.builder()
                            .intentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY)
                            .pathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY)
                            .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY)
                            .build();
            AiResolvedDataScope dataScope =
                    AiResolvedDataScope.builder()
                            .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_STORE)
                            .queryStoreIds(List.of((int) HARNESS_STORE_DEPARTMENT_ID))
                            .expandedSqlDepartmentIds(List.of((int) HARNESS_STORE_DEPARTMENT_ID))
                            .storeRootDepartmentIds(List.of(HARNESS_STORE_DEPARTMENT_ID))
                            .visibleStoreIds(List.of(HARNESS_STORE_DEPARTMENT_ID))
                            .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_MODE_STORE)
                            .build();
            AiQuerySemanticParseResult semantic =
                    AiQuerySemanticParseResult.builder()
                            .metric(
                                    AiQuerySemanticParseResult.MetricPart.builder()
                                            .stockReduceType(StockReduceAnswerPlan.REDUCE_TYPE_ALL)
                                            .build())
                            .build();
            return AiResolvedQueryContext.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .userId(1L)
                    .orgScope(org)
                    .timeWindow(tw)
                    .dataScope(dataScope)
                    .queryIntent(qi)
                    .querySemanticParse(semantic)
                    .effectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY)
                    .effectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY)
                    .build();
        }

        public static StockReducePlannerReadRequest buildFullHarnessStockReduceReadRequest() {
            return StockReducePlannerReadRequest.builder()
                    .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                    .timeStart(HARNESS_TIME_START)
                    .timeEnd(HARNESS_TIME_END)
                    .timeLabel("2026-05-01..2026-05-14 (Harness stock-reduce hydrated)")
                    .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                    .visibleStores(
                            List.of(
                                    StockReducePlannerVisibleStore.builder()
                                            .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                                            .displayLabel("AAA")
                                            .build()))
                    .queryDepartmentIds(List.of(HARNESS_STORE_DEPARTMENT_ID))
                    .targetStoreDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                    .reduceType(StockReduceAnswerPlan.REDUCE_TYPE_ALL)
                    .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY)
                    .totalsBasis("CALENDAR_NATURAL_DAY")
                    .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE)
                    .build();
        }

        public static AiRunState buildHydratedRunState(AiResolvedQueryContext rq) {
            return AiRunState.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .conversationId(0L)
                    .userId(1L)
                    .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                    .distributerId(HARNESS_STOCK_REDUCE_DISTRIBUTER_ID)
                    .resolvedQueryContext(rq)
                    .toolResults(new HashMap<>())
                    .groupStockReduceQuery(false)
                    .build();
        }
    }

    public static final class StockReduceGroup {
        private StockReduceGroup() {}
        public static final String HARNESS_RESOLVED_CONTEXT_REF =
                "HARNESS_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_RESOLVED_CTX_REF";
        public static final String HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE =
                "HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE_GROUP_HYDRATED";

        public static final long HARNESS_STORE_AAA_DEPARTMENT_ID = 1L;
        /** 汀兰餐厅（门店根 gb_department_id=3），与 C-43 / 营收、采购 GROUP 一致。 */
        public static final long HARNESS_STORE_TINGLAN_DEPARTMENT_ID = 3L;

        public static final long HARNESS_STOCK_REDUCE_DISTRIBUTER_ID =
                PlannerCompositeHarnessContext.StockReduceStore.HARNESS_STOCK_REDUCE_DISTRIBUTER_ID;

        private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
        private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);

        /** 与采购 GROUP（9_000_052）错开。 */
        private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_053L;


        public static AiResolvedQueryContext buildHydratedResolvedQueryContext() {
            List<AiStoreScopeDTO> stores =
                    List.of(
                            AiStoreScopeDTO.builder()
                                    .storeDepartmentId(HARNESS_STORE_AAA_DEPARTMENT_ID)
                                    .storeName("AAA")
                                    .build(),
                            AiStoreScopeDTO.builder()
                                    .storeDepartmentId(HARNESS_STORE_TINGLAN_DEPARTMENT_ID)
                                    .storeName("汀兰餐厅")
                                    .build());
            AiResolvedOrgScope org =
                    AiResolvedOrgScope.builder()
                            .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                            .currentStoreDepartmentId(null)
                            .requestDepartmentId(null)
                            .visibleStores(stores)
                            .build();
            AiResolvedTimeWindow tw =
                    AiResolvedTimeWindow.builder()
                            .startDate(HARNESS_TIME_START)
                            .endDate(HARNESS_TIME_END)
                            .timeLabel("2026-05-01..2026-05-14 (Harness stock-reduce GROUP hydrated)")
                            .build();
            AiResolvedQueryIntent qi =
                    AiResolvedQueryIntent.builder()
                            .intentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY)
                            .pathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY)
                            .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY)
                            .build();
            AiResolvedDataScope dataScope = AiHarnessDataScopeFixtures.fromOrgScope(org);
            AiQuerySemanticParseResult semantic =
                    AiQuerySemanticParseResult.builder()
                            .metric(
                                    AiQuerySemanticParseResult.MetricPart.builder()
                                            .stockReduceType(StockReduceAnswerPlan.REDUCE_TYPE_ALL)
                                            .build())
                            .build();
            return AiResolvedQueryContext.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .userId(1L)
                    .orgScope(org)
                    .dataScope(dataScope)
                    .timeWindow(tw)
                    .queryIntent(qi)
                    .querySemanticParse(semantic)
                    .effectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY)
                    .effectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY)
                    .build();
        }

        public static StockReducePlannerReadRequest buildFullHarnessStockReduceReadRequest() {
            return StockReducePlannerReadRequest.builder()
                    .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                    .timeStart(HARNESS_TIME_START)
                    .timeEnd(HARNESS_TIME_END)
                    .timeLabel("2026-05-01..2026-05-14 (Harness stock-reduce GROUP hydrated)")
                    .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                    .visibleStores(
                            List.of(
                                    StockReducePlannerVisibleStore.builder()
                                            .departmentId(HARNESS_STORE_AAA_DEPARTMENT_ID)
                                            .displayLabel("AAA")
                                            .build(),
                                    StockReducePlannerVisibleStore.builder()
                                            .departmentId(HARNESS_STORE_TINGLAN_DEPARTMENT_ID)
                                            .displayLabel("汀兰餐厅")
                                            .build()))
                    .queryDepartmentIds(List.of(HARNESS_STORE_AAA_DEPARTMENT_ID, HARNESS_STORE_TINGLAN_DEPARTMENT_ID))
                    .targetStoreDepartmentId(null)
                    .reduceType(StockReduceAnswerPlan.REDUCE_TYPE_ALL)
                    .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY)
                    .totalsBasis("CALENDAR_NATURAL_DAY")
                    .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE)
                    .build();
        }

        public static AiRunState buildHydratedRunState(AiResolvedQueryContext rq) {
            return AiRunState.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .conversationId(0L)
                    .userId(1L)
                    .departmentId(null)
                    .distributerId(HARNESS_STOCK_REDUCE_DISTRIBUTER_ID)
                    .resolvedQueryContext(rq)
                    .toolResults(new HashMap<>())
                    .groupStockReduceQuery(true)
                    .build();
        }
    }

    public static final class DishProfitStore {
        private DishProfitStore() {}
        public static final String HARNESS_RESOLVED_CONTEXT_REF =
                "HARNESS_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_RESOLVED_CTX_REF";
        public static final String HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT =
                "HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT_HYDRATED";

        /** 单店 AAA（gb_department_id=1）。 */
        public static final long HARNESS_STORE_DEPARTMENT_ID = 1L;

        private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_000L;

        public static final long HARNESS_DISH_PROFIT_DISTRIBUTER_ID = 2L;

        private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
        private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);


        public static AiResolvedQueryContext buildHydratedResolvedQueryContext() {
            List<AiStoreScopeDTO> stores =
                    List.of(
                            AiStoreScopeDTO.builder()
                                    .storeDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                                    .storeName("AAA")
                                    .build());
            AiResolvedOrgScope org =
                    AiResolvedOrgScope.builder()
                            .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                            .currentStoreDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                            .requestDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                            .visibleStores(stores)
                            .build();
            AiResolvedTimeWindow tw =
                    AiResolvedTimeWindow.builder()
                            .startDate(HARNESS_TIME_START)
                            .endDate(HARNESS_TIME_END)
                            .timeLabel("2026-05-01..2026-05-14 (Harness dish-profit hydrated)")
                            .build();
            AiResolvedQueryIntent qi =
                    AiResolvedQueryIntent.builder()
                            .intentCode(AiResolvedQueryIntent.DISH_PROFIT)
                            .pathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                            .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW)
                            .build();
            AiResolvedDataScope dataScope =
                    AiResolvedDataScope.builder()
                            .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_STORE)
                            .queryStoreIds(List.of((int) HARNESS_STORE_DEPARTMENT_ID))
                            .expandedSqlDepartmentIds(List.of((int) HARNESS_STORE_DEPARTMENT_ID))
                            .storeRootDepartmentIds(List.of(HARNESS_STORE_DEPARTMENT_ID))
                            .visibleStoreIds(List.of(HARNESS_STORE_DEPARTMENT_ID))
                            .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_MODE_STORE)
                            .build();
            AiQuerySemanticParseResult semantic =
                    AiQuerySemanticParseResult.builder()
                            .metric(
                                    AiQuerySemanticParseResult.MetricPart.builder()
                                            .primaryMetric("DISH_PROFIT_OVERVIEW")
                                            .build())
                            .build();
            return AiResolvedQueryContext.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .userId(1L)
                    .orgScope(org)
                    .timeWindow(tw)
                    .dataScope(dataScope)
                    .queryIntent(qi)
                    .querySemanticParse(semantic)
                    .effectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT)
                    .effectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                    .mentionedDishName(null)
                    .dishProfitMetricType("OVERVIEW")
                    .harnessMultiStoreScopeDetected(false)
                    .harnessMultiStoreScopeApplied(false)
                    .harnessSingleStoreNarrowingBlocked(false)
                    .build();
        }

        public static DishProfitPlannerReadRequest buildFullHarnessDishProfitReadRequest() {
            return DishProfitPlannerReadRequest.builder()
                    .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                    .timeStart(HARNESS_TIME_START)
                    .timeEnd(HARNESS_TIME_END)
                    .timeLabel("2026-05-01..2026-05-14 (Harness dish-profit hydrated)")
                    .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                    .visibleStores(
                            List.of(
                                    DishProfitPlannerVisibleStore.builder()
                                            .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                                            .displayLabel("AAA")
                                            .build()))
                    .queryDepartmentIds(List.of(HARNESS_STORE_DEPARTMENT_ID))
                    .targetStoreDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                    .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW)
                    .mentionedDishName(null)
                    .dishProfitMetricType("OVERVIEW")
                    .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT)
                    .build();
        }

        public static AiRunState buildHydratedRunState(AiResolvedQueryContext rq) {
            return AiRunState.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .conversationId(0L)
                    .userId(1L)
                    .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                    .distributerId(HARNESS_DISH_PROFIT_DISTRIBUTER_ID)
                    .resolvedQueryContext(rq)
                    .toolResults(new HashMap<>())
                    .dishProfitPath(true)
                    .groupStockReduceQuery(false)
                    .groupPurchaseOverview(false)
                    .groupWarehouseStockOverview(false)
                    .build();
        }
    }

    public static final class DishProfitGroup {
        private DishProfitGroup() {}
        public static final String HARNESS_RESOLVED_CONTEXT_REF =
                "HARNESS_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_RESOLVED_CTX_REF";
        public static final String HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT =
                "HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT_GROUP_HYDRATED";

        public static final long HARNESS_STORE_AAA_DEPARTMENT_ID = 1L;
        /** 汀兰餐厅（门店根 gb_department_id=3），与 C-44/C-45/C-46 一致。 */
        public static final long HARNESS_STORE_TINGLAN_DEPARTMENT_ID = 3L;

        public static final long HARNESS_DISH_PROFIT_DISTRIBUTER_ID =
                PlannerCompositeHarnessContext.DishProfitStore.HARNESS_DISH_PROFIT_DISTRIBUTER_ID;

        private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
        private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);

        /** 与 C-46 StockReduce GROUP（9_000_053）错开。 */
        private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_054L;


        /** 与 C-29 相同 ROLE 权限快照，满足 {@link com.nongxinle.ai.security.AiPermissionGuard} 对菜品毛利 Tool 的校验。 */
        public static AiUserContext buildHarnessGroupManagerUserContext() {
            return AiUserContext.builder()
                    .userId(1L)
                    .roleCode(AiRoleCodes.GROUP_MANAGER)
                    .roleName("Harness GROUP_MANAGER")
                    .distributerId(HARNESS_DISH_PROFIT_DISTRIBUTER_ID)
                    .permissions(new ArrayList<>(AiRoleMapper.permissionsForAiRole(AiRoleCodes.GROUP_MANAGER)))
                    .build();
        }

        public static AiResolvedQueryContext buildHydratedResolvedQueryContext() {
            List<AiStoreScopeDTO> stores =
                    List.of(
                            AiStoreScopeDTO.builder()
                                    .storeDepartmentId(HARNESS_STORE_AAA_DEPARTMENT_ID)
                                    .storeName("AAA")
                                    .build(),
                            AiStoreScopeDTO.builder()
                                    .storeDepartmentId(HARNESS_STORE_TINGLAN_DEPARTMENT_ID)
                                    .storeName("汀兰餐厅")
                                    .build());
            AiResolvedOrgScope org =
                    AiResolvedOrgScope.builder()
                            .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                            .currentStoreDepartmentId(null)
                            .requestDepartmentId(null)
                            .visibleStores(stores)
                            .build();
            AiResolvedTimeWindow tw =
                    AiResolvedTimeWindow.builder()
                            .startDate(HARNESS_TIME_START)
                            .endDate(HARNESS_TIME_END)
                            .timeLabel("2026-05-01..2026-05-14 (Harness dish-profit GROUP hydrated)")
                            .build();
            AiResolvedQueryIntent qi =
                    AiResolvedQueryIntent.builder()
                            .intentCode(AiResolvedQueryIntent.DISH_PROFIT)
                            .pathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                            .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW)
                            .build();
            AiResolvedDataScope dataScope = AiHarnessDataScopeFixtures.fromOrgScope(org);
            AiQuerySemanticParseResult semantic =
                    AiQuerySemanticParseResult.builder()
                            .metric(
                                    AiQuerySemanticParseResult.MetricPart.builder()
                                            .primaryMetric("DISH_PROFIT_OVERVIEW")
                                            .build())
                            .build();
            return AiResolvedQueryContext.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .userId(1L)
                    .orgScope(org)
                    .timeWindow(tw)
                    .dataScope(dataScope)
                    .queryIntent(qi)
                    .querySemanticParse(semantic)
                    .effectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT)
                    .effectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                    .mentionedDishName(null)
                    .dishProfitMetricType("OVERVIEW")
                    .harnessMultiStoreScopeDetected(true)
                    .harnessMultiStoreScopeApplied(true)
                    .harnessSingleStoreNarrowingBlocked(false)
                    .build();
        }

        public static DishProfitPlannerReadRequest buildFullHarnessDishProfitReadRequest() {
            return DishProfitPlannerReadRequest.builder()
                    .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                    .timeStart(HARNESS_TIME_START)
                    .timeEnd(HARNESS_TIME_END)
                    .timeLabel("2026-05-01..2026-05-14 (Harness dish-profit GROUP hydrated)")
                    .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                    .visibleStores(
                            List.of(
                                    DishProfitPlannerVisibleStore.builder()
                                            .departmentId(HARNESS_STORE_AAA_DEPARTMENT_ID)
                                            .displayLabel("AAA")
                                            .build(),
                                    DishProfitPlannerVisibleStore.builder()
                                            .departmentId(HARNESS_STORE_TINGLAN_DEPARTMENT_ID)
                                            .displayLabel("汀兰餐厅")
                                            .build()))
                    .queryDepartmentIds(List.of(HARNESS_STORE_AAA_DEPARTMENT_ID, HARNESS_STORE_TINGLAN_DEPARTMENT_ID))
                    .targetStoreDepartmentId(null)
                    .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW)
                    .mentionedDishName(null)
                    .dishProfitMetricType("OVERVIEW")
                    .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT)
                    .build();
        }

        public static AiRunState buildHydratedRunState(AiResolvedQueryContext rq) {
            return AiRunState.builder()
                    .runId(HARNESS_SYNTHETIC_RUN_ID)
                    .conversationId(0L)
                    .userId(1L)
                    .departmentId(null)
                    .distributerId(HARNESS_DISH_PROFIT_DISTRIBUTER_ID)
                    .resolvedQueryContext(rq)
                    .toolResults(new HashMap<>())
                    .aiUserContext(buildHarnessGroupManagerUserContext())
                    .dishProfitPath(true)
                    .groupStockReduceQuery(false)
                    .groupPurchaseOverview(false)
                    .groupWarehouseStockOverview(false)
                    .build();
        }
    }
}
