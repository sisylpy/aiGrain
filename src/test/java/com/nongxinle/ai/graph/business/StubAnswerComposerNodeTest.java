package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.composer.renderer.DeterministicAnswerRenderer;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.core.AiWorkspaceMode;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.dto.cost.AiCostDiagnosisResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StubAnswerComposerNodeTest {

    @Mock
    private AiSseEventPublisher publisher;

    @Test
    void cost_blankLlm_shortFallbackDoesNotEmitKeyMetricsBlock() {
        StubAnswerComposerNode node = new StubAnswerComposerNode(publisher, DeterministicAnswerRenderer.createStandalone());

        AiCostDiagnosisResult diagnosis = AiCostDiagnosisResult.builder()
                .summary("本月成本判断还不完整。")
                .riskLevel("data_incomplete")
                .keyMetrics(List.of(
                        AiCostDiagnosisResult.metric("测试指标仅应在卡片展示", "999", "元")
                ))
                .findings(List.of("采购有数据", "核销不足", "出库链不连续"))
                .recommendations(List.of("核对入库核销", "补营业额", "排损耗菜"))
                .needMoreData(true)
                .build();

        AiRunState state = AiRunState.builder()
                .runId(1L)
                .normalizedUserInput("本月成本怎么样？")
                .costDiagnosisResult(diagnosis)
                .build();

        node.run(state);

        String text = state.getFinalAnswerText();
        assertThat(text).doesNotContain("关键指标");
        assertThat(text).doesNotContain("999");
        assertThat(text).doesNotContain("测试指标仅应在卡片展示");
        assertThat(text).contains("成本诊断卡片");
        assertThat(text).contains("本月成本判断还不完整");
    }

    @Test
    void fallbackStripRemovesTechnicalLines_genericNoPlanDoesNotEchoInternals() {
        StubAnswerComposerNode node = new StubAnswerComposerNode(publisher, DeterministicAnswerRenderer.createStandalone());
        AiRunState state = AiRunState.builder()
                .runId(4L)
                .normalizedUserInput("随便闲聊")
                .statStartDate("2026-05-01")
                .statEndDate("2026-05-31")
                .workspaceMode(com.nongxinle.ai.core.AiWorkspaceMode.BUSINESS_CHAT)
                .build();
        node.run(state);
        assertThat(state.getFinalAnswerText()).doesNotContain("dataPlanTools");
        assertThat(state.getFinalAnswerText()).doesNotContain("toolResults");
        assertThat(state.getFinalAnswerText()).doesNotContain("系统尚未执行任何数据查询工具");
    }

    @Test
    void dishProfit_blankLlm_answerPlanLowestMargin_usesFocusRowNotToolSummary() {
        StubAnswerComposerNode node = new StubAnswerComposerNode(publisher, DeterministicAnswerRenderer.createStandalone());

        Map<String, Object> focus = new LinkedHashMap<>();
        focus.put("dishName", "PlanLowMargin");
        focus.put("blendedGrossMarginRateOnListPrice", new BigDecimal("5.5"));
        focus.put("listPriceRevenue", "1000");
        focus.put("theoryCostAmount", "400");
        focus.put("actualCostAmount", "500");

        DishProfitAnswerPlan plan = DishProfitAnswerPlan.builder()
                .planType(DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN)
                .focusRows(List.of(focus))
                .build();

        AiDishProfitOverviewResult dp = AiDishProfitOverviewResult.builder()
                .summary("最低毛利的是 OtherDish，这条 summary 与 AnswerPlan 故意不一致。")
                .queryScopeBanner("汀兰餐厅 · 上月")
                .build();

        AiRunState state = AiRunState.builder()
                .runId(20L)
                .dishProfitPath(true)
                .normalizedUserInput("哪个菜品毛利最低？")
                .dishProfitOverviewResult(dp)
                .dishProfitAnswerPlan(plan)
                .build();

        node.run(state);

        String text = state.getFinalAnswerText();
        assertThat(text).contains("PlanLowMargin");
        assertThat(text).contains("汀兰餐厅");
        assertThat(text).doesNotContain("OtherDish");
        assertThat(text).doesNotContain("故意不一致");
    }

    @Test
    void dishProfit_blankLlm_answerPlanHighestActualCost_usesFocusRowNotToolSummary() {
        StubAnswerComposerNode node = new StubAnswerComposerNode(publisher, DeterministicAnswerRenderer.createStandalone());

        Map<String, Object> focus = new LinkedHashMap<>();
        focus.put("dishName", "PlanHighCost");
        focus.put("actualCostAmount", new BigDecimal("888.5"));
        focus.put("theoryCostAmount", "100");
        focus.put("blendedGrossMarginRateOnListPrice", new BigDecimal("12"));

        DishProfitAnswerPlan plan = DishProfitAnswerPlan.builder()
                .planType(DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST)
                .focusRows(List.of(focus))
                .build();

        AiDishProfitOverviewResult dp = AiDishProfitOverviewResult.builder()
                .summary("实际成本最高的是 WrongDish。")
                .queryScopeBanner("集团 · 本月")
                .build();

        AiRunState state = AiRunState.builder()
                .runId(21L)
                .dishProfitPath(true)
                .normalizedUserInput("哪个菜品实际成本最高？")
                .dishProfitOverviewResult(dp)
                .dishProfitAnswerPlan(plan)
                .build();

        node.run(state);

        String text = state.getFinalAnswerText();
        assertThat(text).contains("PlanHighCost");
        assertThat(text).contains("集团");
        assertThat(text).doesNotContain("WrongDish");
    }

    @Test
    void dishProfit_blankLlm_answerPlanActualOutbound_prefersFocusRowOverDeterministicSummaryIntent() {
        StubAnswerComposerNode node = new StubAnswerComposerNode(publisher, DeterministicAnswerRenderer.createStandalone());

        Map<String, Object> focus = new LinkedHashMap<>();
        focus.put("dishName", "PlanOutbound");
        focus.put("actualCostTotalAmount123", new BigDecimal("600"));
        focus.put("theoryCostAmount", "200");

        DishProfitAnswerPlan plan = DishProfitAnswerPlan.builder()
                .planType(DishProfitAnswerPlan.TYPE_DISH_ACTUAL_OUTBOUND_COST)
                .focusRows(List.of(focus))
                .build();

        AiDishProfitOverviewResult dp = AiDishProfitOverviewResult.builder()
                .summary("出库成本摘要应被忽略。")
                .queryScopeBanner("门店A")
                .build();

        AiRunState state = AiRunState.builder()
                .runId(22L)
                .dishProfitPath(true)
                .normalizedUserInput("哪个菜实际出库成本最高？")
                .dishProfitOverviewResult(dp)
                .dishProfitAnswerPlan(plan)
                .build();

        node.run(state);

        String text = state.getFinalAnswerText();
        assertThat(text).contains("PlanOutbound");
        assertThat(text).contains("600");
        assertThat(text).doesNotContain("摘要应被忽略");
    }

    @Test
    void dishProfit_blankLlm_answerPlanCostGap_usesFocusRowDiffNotToolSummary() {
        StubAnswerComposerNode node = new StubAnswerComposerNode(publisher, DeterministicAnswerRenderer.createStandalone());

        Map<String, Object> focus = new LinkedHashMap<>();
        focus.put("dishName", "PlanGapDish");
        focus.put("diffCostAmount", new BigDecimal("350.25"));
        focus.put("theoryCostAmount", "100.50");
        focus.put("actualCostAmount", "450.75");
        focus.put("blendedGrossMarginRateOnListPrice", new BigDecimal("15"));

        DishProfitAnswerPlan plan = DishProfitAnswerPlan.builder()
                .planType(DishProfitAnswerPlan.TYPE_DISH_COST_GAP)
                .focusRows(List.of(focus))
                .build();

        AiDishProfitOverviewResult dp = AiDishProfitOverviewResult.builder()
                .summary("差异最大的是 WrongGap 菜，成本差额约 999.99 元（故意与 AnswerPlan 不一致）。")
                .queryScopeBanner("区域 · 本季度")
                .build();

        AiRunState state = AiRunState.builder()
                .runId(23L)
                .dishProfitPath(true)
                .normalizedUserInput("哪个菜品理论和实际差异最大？")
                .dishProfitOverviewResult(dp)
                .dishProfitAnswerPlan(plan)
                .build();

        node.run(state);

        String text = state.getFinalAnswerText();
        assertThat(text).contains("PlanGapDish");
        assertThat(text).contains("350.25");
        assertThat(text).contains("区域");
        assertThat(text).doesNotContain("WrongGap");
        assertThat(text).doesNotContain("999.99");
        assertThat(text).doesNotContain("故意与 AnswerPlan 不一致");
    }

    @Test
    void dishProfit_blankLlm_answerPlanProfitReason_usesFocusRowMetricsNotToolSummary() {
        StubAnswerComposerNode node = new StubAnswerComposerNode(publisher, DeterministicAnswerRenderer.createStandalone());

        Map<String, Object> focus = new LinkedHashMap<>();
        focus.put("dishName", "PlanReasonDish");
        focus.put("blendedGrossMarginRateOnListPrice", new BigDecimal("8.25"));
        focus.put("theoryCostAmount", new BigDecimal("120.50"));
        focus.put("actualCostAmount", new BigDecimal("200.75"));
        focus.put("listPriceRevenue", new BigDecimal("900"));

        DishProfitAnswerPlan plan = DishProfitAnswerPlan.builder()
                .planType(DishProfitAnswerPlan.TYPE_DISH_PROFIT_REASON)
                .focusRows(List.of(focus))
                .build();

        AiDishProfitOverviewResult dp = AiDishProfitOverviewResult.builder()
                .summary("摘要菜 SummaryWrong 毛利率 77.77%，理论成本 11 元，实际成本 22 元（与计划不一致）。")
                .queryScopeBanner("汀兰 · 本周")
                .build();

        AiRunState state = AiRunState.builder()
                .runId(24L)
                .dishProfitPath(true)
                .normalizedUserInput("核桃芽菜西芹为什么毛利低？")
                .dishProfitOverviewResult(dp)
                .dishProfitAnswerPlan(plan)
                .build();

        node.run(state);

        String text = state.getFinalAnswerText();
        assertThat(text).contains("PlanReasonDish");
        assertThat(text).contains("8.25%");
        assertThat(text).contains("120.5");
        assertThat(text).contains("200.75");
        assertThat(text).contains("900");
        assertThat(text).contains("汀兰");
        assertThat(text).doesNotContain("SummaryWrong");
        assertThat(text).doesNotContain("77.77%");
        assertThat(text).doesNotContain("与计划不一致");
    }

    @Test
    void purchase_blankLlm_answerPlanSupplierRanking_usesFocusRowNotPoisonedToolOverview() {
        StubAnswerComposerNode node = new StubAnswerComposerNode(publisher, DeterministicAnswerRenderer.createStandalone());

        Map<String, Object> wrongSupplier = new LinkedHashMap<>();
        wrongSupplier.put("supplierId", -1);
        wrongSupplier.put("supplierName", "供货商ID-1");
        wrongSupplier.put("totalPurchaseAmount", 9999);
        wrongSupplier.put("purchaseLineCount", 99);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("purchaseNarrativeMode", "supplier_amount_ranking");
        overview.put("topSuppliers", List.of(wrongSupplier));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("purchaseOverview", overview);
        Map<String, Object> toolEnv = new LinkedHashMap<>();
        toolEnv.put("data", data);

        Map<String, Object> toolResults = new LinkedHashMap<>();
        toolResults.put(AiBusinessToolIds.PURCHASE_OVERVIEW, toolEnv);

        Map<String, Object> focus = new LinkedHashMap<>();
        focus.put("supplierId", 2);
        focus.put("supplierName", "金调料99的222");
        focus.put("totalPurchaseAmount", 68);
        focus.put("purchaseLineCount", 3);

        PurchaseAnswerPlan plan = PurchaseAnswerPlan.builder()
                .planType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING)
                .focusRows(List.of(focus))
                .build();

        AiRunState state = AiRunState.builder()
                .runId(40L)
                .purchaseCostInsightPath(true)
                .normalizedUserInput("哪个供货商采购金额最高？")
                .statStartDate("2026-05-01")
                .statEndDate("2026-05-31")
                .toolResults(toolResults)
                .purchaseAnswerPlan(plan)
                .build();

        node.run(state);

        String text = state.getFinalAnswerText();
        assertThat(text).contains("金调料99的222");
        assertThat(text).contains("68元");
        assertThat(text).contains("共3笔");
        assertThat(text).doesNotContain("9999");
        assertThat(text).doesNotContain("99笔");
        assertThat(text).doesNotContain("供货商ID");
        assertThat(text).doesNotContain("-1");
    }

    @Test
    void purchase_goodsSourceBreakdown_answerPlan_rendersSelfAndSupplierAmounts() {
        StubAnswerComposerNode node = new StubAnswerComposerNode(publisher, DeterministicAnswerRenderer.createStandalone());

        Map<String, Object> focus = new LinkedHashMap<>();
        focus.put("disGoodsId", 54);
        focus.put("goodsName", "海天5度白醋");
        focus.put("totalPurchaseAmount", "2970");
        focus.put("selfPurchaseAmount", "2970");
        focus.put("supplierPurchaseAmount", "0");
        focus.put("selfPurchaseLineCount", 3);
        focus.put("supplierPurchaseLineCount", 0);

        PurchaseAnswerPlan plan = PurchaseAnswerPlan.builder()
                .planType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN)
                .focusRows(List.of(focus))
                .build();

        AiRunState state = AiRunState.builder()
                .runId(41L)
                .purchaseOverviewPath(true)
                .normalizedUserInput("第一名是谁供的？")
                .statStartDate("2026-05-01")
                .statEndDate("2026-05-31")
                .purchaseAnswerPlan(plan)
                .resolvedQueryContext(
                        AiResolvedQueryContext.builder()
                                .effectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW)
                                .effectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW)
                                .build())
                .build();

        node.run(state);

        String text = state.getFinalAnswerText();
        assertThat(text).contains("海天5度白醋");
        assertThat(text).contains("2970");
        assertThat(text).contains("自采");
        assertThat(text).contains("供货商订货");
        assertThat(text).doesNotContain("采购分析计划暂未生成");
    }

    @Test
    void revenueCustomerCount_emptyFocusWithFailure_doesNotFallbackToOverview() {
        StubAnswerComposerNode node = new StubAnswerComposerNode(publisher, DeterministicAnswerRenderer.createStandalone());

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("totalRevenue", 9999);
        inner.put("days", 5);
        Map<String, Object> toolEnv = new LinkedHashMap<>();
        toolEnv.put("success", true);
        toolEnv.put("data", inner);
        Map<String, Object> toolResults = new LinkedHashMap<>();
        toolResults.put(AiBusinessToolIds.REVENUE_QUERY, toolEnv);

        Map<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("failureReason", "missing_revenue_overview");
        DailyRevenueAnswerPlan plan = DailyRevenueAnswerPlan.builder()
                .planType(DailyRevenueAnswerPlan.TYPE_REVENUE_CUSTOMER_COUNT_OVERVIEW)
                .focusRows(List.of())
                .secondaryRows(List.of())
                .debug(dbg)
                .build();

        AiRunState state = AiRunState.builder()
                .runId(801L)
                .revenueOverviewPath(true)
                .normalizedUserInput("顾客数多少？")
                .statStartDate("2026-04-01")
                .statEndDate("2026-04-30")
                .toolResults(toolResults)
                .revenueAnswerPlan(plan)
                .build();

        node.run(state);

        String text = state.getFinalAnswerText();
        assertThat(text).matches("(?s).*(顾客数|顾客数字段).*");
        assertThat(text).doesNotContain("9999");
        assertThat(text).doesNotContain("营业额合计 9999");
    }

    @Test
    void revenueAverageOrderValue_emptyFocusWithFailure_doesNotFallbackToOverview() {
        StubAnswerComposerNode node = new StubAnswerComposerNode(publisher, DeterministicAnswerRenderer.createStandalone());

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("totalRevenue", 9999);
        inner.put("days", 3);
        Map<String, Object> toolEnv = new LinkedHashMap<>();
        toolEnv.put("success", true);
        toolEnv.put("data", inner);
        Map<String, Object> toolResults = new LinkedHashMap<>();
        toolResults.put(AiBusinessToolIds.REVENUE_QUERY, toolEnv);

        Map<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("failureReason", "missing_average_order_value");
        DailyRevenueAnswerPlan plan = DailyRevenueAnswerPlan.builder()
                .planType(DailyRevenueAnswerPlan.TYPE_REVENUE_AVERAGE_ORDER_VALUE)
                .focusRows(List.of())
                .secondaryRows(List.of())
                .debug(dbg)
                .build();

        AiRunState state = AiRunState.builder()
                .runId(802L)
                .revenueOverviewPath(true)
                .normalizedUserInput("客单价多少？")
                .statStartDate("2026-04-01")
                .statEndDate("2026-04-30")
                .toolResults(toolResults)
                .revenueAnswerPlan(plan)
                .build();

        node.run(state);

        String text = state.getFinalAnswerText();
        assertThat(text).contains("客单价");
        assertThat(text).doesNotContain("9999");
        assertThat(text).doesNotContain("营业额合计 9999");
    }

    @Test
    void stockReduce_blankLlm_goodsAmountRanking_usesAnswerPlanNotPoisonedToolPayload() {
        StubAnswerComposerNode node = new StubAnswerComposerNode(publisher, DeterministicAnswerRenderer.createStandalone());

        Map<String, Object> wrongRow = new LinkedHashMap<>();
        wrongRow.put("name", "错误商品");
        wrongRow.put("amount", 9999);

        Map<String, Object> toolData = new LinkedHashMap<>();
        toolData.put("produceTotal", BigDecimal.ZERO);
        toolData.put("wasteTotal", BigDecimal.ZERO);
        toolData.put("lossTotal", BigDecimal.ZERO);
        toolData.put("returnTotal", BigDecimal.ZERO);
        toolData.put("grandTotalFourTypes", BigDecimal.ZERO);
        toolData.put("totalsBasis", "CALENDAR_NATURAL_DAY");
        toolData.put("topGoodsOutboundBySubtotal", List.of(wrongRow));

        Map<String, Object> toolEnv = new LinkedHashMap<>();
        toolEnv.put("success", true);
        toolEnv.put("data", toolData);
        Map<String, Object> toolResults = new LinkedHashMap<>();
        toolResults.put(AiBusinessToolIds.STOCK_REDUCE_QUERY, toolEnv);

        LinkedHashMap<String, Object> focus = new LinkedHashMap<>();
        focus.put("name", "青鱼");
        focus.put("amount", 280);

        StockReduceAnswerPlan plan = StockReduceAnswerPlan.builder()
                .planType(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_AMOUNT_RANKING)
                .focusRows(List.of(focus))
                .secondaryRows(List.of())
                .build();

        AiRunState state = AiRunState.builder()
                .runId(501L)
                .stockReduceQueryPath(true)
                .normalizedUserInput("哪个商品出库金额最高？")
                .statStartDate("2026-05-01")
                .statEndDate("2026-05-31")
                .toolResults(toolResults)
                .stockReduceAnswerPlan(plan)
                .build();

        node.run(state);

        String text = state.getFinalAnswerText();
        assertThat(text).contains("青鱼");
        assertThat(text).contains("280");
        assertThat(text).doesNotContain("错误商品");
        assertThat(text).doesNotContain("9999");
    }

    @Test
    void stockReduce_blankLlm_lossOverview_usesAnswerPlanNotWastePoisonFromTool() {
        StubAnswerComposerNode node = new StubAnswerComposerNode(publisher, DeterministicAnswerRenderer.createStandalone());

        Map<String, Object> toolData = new LinkedHashMap<>();
        toolData.put("produceTotal", BigDecimal.ZERO);
        toolData.put("wasteTotal", new BigDecimal("9999"));
        toolData.put("lossTotal", BigDecimal.ZERO);
        toolData.put("returnTotal", BigDecimal.ZERO);
        toolData.put("grandTotalFourTypes", new BigDecimal("9999"));
        toolData.put("totalsBasis", "CALENDAR_NATURAL_DAY");

        Map<String, Object> toolEnv = new LinkedHashMap<>();
        toolEnv.put("success", true);
        toolEnv.put("data", toolData);
        Map<String, Object> toolResults = new LinkedHashMap<>();
        toolResults.put(AiBusinessToolIds.STOCK_REDUCE_QUERY, toolEnv);

        AiResolvedQueryIntent qi = AiResolvedQueryIntent.builder()
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_WASTE)
                .build();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder()
                .queryIntent(qi)
                .build();

        LinkedHashMap<String, Object> focus = new LinkedHashMap<>();
        focus.put("label", "损耗/报损");
        focus.put("amount", 17);

        StockReduceAnswerPlan plan = StockReduceAnswerPlan.builder()
                .planType(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_LOSS_OVERVIEW)
                .reduceType(StockReduceAnswerPlan.REDUCE_TYPE_TYPE3)
                .focusRows(List.of(focus))
                .build();

        AiRunState state = AiRunState.builder()
                .runId(502L)
                .stockReduceQueryPath(true)
                .normalizedUserInput("损耗多少钱")
                .resolvedQueryContext(rq)
                .statStartDate("2026-05-01")
                .statEndDate("2026-05-31")
                .toolResults(toolResults)
                .stockReduceAnswerPlan(plan)
                .build();

        node.run(state);

        String text = state.getFinalAnswerText();
        assertThat(text).contains("17");
        assertThat(text).contains("损耗");
        assertThat(text).doesNotContain("9999");
        assertThat(text).doesNotContain("废弃（type2）");
    }

}