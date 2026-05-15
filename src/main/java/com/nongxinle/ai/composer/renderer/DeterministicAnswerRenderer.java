package com.nongxinle.ai.composer.renderer;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiBusinessOverviewResult;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.business.BusinessDiagnosisPlan;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.dto.cost.AiCostDiagnosisResult;
import com.nongxinle.ai.composer.summary.BusinessOverviewDeterministicSummaryBuilder;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic prose for Answer Composer LLM fallbacks: reads AnswerPlans, tool payloads,
 * {@link AiRunState#getResolvedQueryContext()} structured signals, diagnosis plans.<br>
 * Does not call the LLM. Copy-frozen wording from composer node.
 */
@Component
public final class DeterministicAnswerRenderer {

    private static final int MAX_FALLBACK_FINDINGS = 3;
    private static final int MAX_FALLBACK_RECOMMENDATIONS = 3;

    private final PurchaseDeterministicRenderer purchaseDeterministicRenderer;
    private final WarehouseDeterministicRenderer warehouseDeterministicRenderer;
    private final StockReduceDeterministicRenderer stockReduceDeterministicRenderer;
    private final DiagnosisDeterministicRenderer diagnosisDeterministicRenderer;
    private final DishProfitDeterministicRenderer dishProfitDeterministicRenderer;
    private final DishSalesDeterministicRenderer dishSalesDeterministicRenderer;

    @Autowired
    public DeterministicAnswerRenderer(PurchaseDeterministicRenderer purchaseDeterministicRenderer,
            WarehouseDeterministicRenderer warehouseDeterministicRenderer,
            StockReduceDeterministicRenderer stockReduceDeterministicRenderer,
            DiagnosisDeterministicRenderer diagnosisDeterministicRenderer,
            DishProfitDeterministicRenderer dishProfitDeterministicRenderer,
            DishSalesDeterministicRenderer dishSalesDeterministicRenderer) {
        this.purchaseDeterministicRenderer = purchaseDeterministicRenderer;
        this.warehouseDeterministicRenderer = warehouseDeterministicRenderer;
        this.stockReduceDeterministicRenderer = stockReduceDeterministicRenderer;
        this.diagnosisDeterministicRenderer = diagnosisDeterministicRenderer;
        this.dishProfitDeterministicRenderer = dishProfitDeterministicRenderer;
        this.dishSalesDeterministicRenderer = dishSalesDeterministicRenderer;
    }

    /**
     * Wiring without Spring (unit tests, scripts).
     */
    public static DeterministicAnswerRenderer createStandalone() {
        return new DeterministicAnswerRenderer(
                new PurchaseDeterministicRenderer(),
                new WarehouseDeterministicRenderer(),
                new StockReduceDeterministicRenderer(),
                new DiagnosisDeterministicRenderer(),
                new DishProfitDeterministicRenderer(),
                new DishSalesDeterministicRenderer());
    }

    private static final String GENERIC_CHAT_EMPTY_LLM_FALLBACK =
            "当前可用数据不足，暂时无法给出完整分析。";

    private static Object unwrapRevenueToolData(Object data) {
        if (data instanceof Map<?, ?> m) {
            return m;
        }
        if (data instanceof String s && !s.isBlank()) {
            try {
                Object parsed = JSON.parseObject(s);
                if (parsed instanceof Map<?, ?> pm) {
                    return pm;
                }
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    /**
     * AnswerPlan 不可读时，直接从 {@code revenue_query} 信封朗读总额/天数（与 Builder 数据源一致）。
     */
    private static String revenueOverviewDeterministicFallback(AiRunState state) {
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        Object env = state.getToolResults() == null ? null : state.getToolResults().get(AiBusinessToolIds.REVENUE_QUERY);
        if (!(env instanceof Map<?, ?>)) {
            return "当前未能读取营业额查询结果，请稍后重试或缩小门店与时间范围。";
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> envMap = (Map<String, Object>) env;
        if (!Boolean.TRUE.equals(envMap.get("success"))) {
            Object msg = envMap.get("message");
            return msg != null && !msg.toString().isBlank()
                    ? "查询营业额数据未成功：" + msg.toString().trim()
                    : "查询营业额数据未成功，请稍后重试。";
        }
        Object dataObj = unwrapRevenueToolData(envMap.get("data"));
        if (!(dataObj instanceof Map<?, ?>)) {
            return "营业额查询返回数据为空。";
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) dataObj;
        String total = DeterministicRendererSupport.plainNumericHint(inner.get("totalRevenue"));
        int days = DeterministicRendererSupport.intHint(inner.get("days"));
        String avg = DeterministicRendererSupport.plainNumericHint(inner.get("avgDailyRevenue"));
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        sb.append(tw.getDisplayTimeRange()).append("，营业额合计 ").append(total).append(" 元");
        if (days > 0) {
            sb.append("（录入营业额的自然日 ").append(days).append(" 天");
            if (DeterministicRendererSupport.parseDoubleLoose(inner.get("avgDailyRevenue")) > 0) {
                sb.append("，日均约 ").append(avg).append(" 元");
            }
            sb.append("）");
        }
        sb.append("。");
        return sb.toString();
    }

    private static String extractOverviewNumericHeadlinePreferAnswerPlan(AiRunState state,
            AiBusinessOverviewResult o) {
        if (BusinessOverviewDeterministicSummaryBuilder.hasAuthoritativeBusinessOverviewRevenuePlan(state)) {
            return DeterministicRendererSupport.nz(
                    BusinessOverviewDeterministicSummaryBuilder.businessOverviewResolvedRevenueParagraph(state)).trim();
        }
        return BusinessOverviewDeterministicSummaryBuilder.extractOverviewNumericHeadline(state, o);
    }

    private static String shortFallbackCost(AiCostDiagnosisResult d) {
        StringBuilder sb = new StringBuilder();
        if (d.getSummary() != null && !d.getSummary().isBlank()) {
            sb.append(d.getSummary().trim());
        } else {
            sb.append("已根据当前可查数据完成成本诊断初步判断。");
        }
        sb.append('\n');
        appendNumbered(sb, "重点发现", capCopy(d.getFindings(), MAX_FALLBACK_FINDINGS), MAX_FALLBACK_FINDINGS);
        appendNumbered(sb, "建议先做", capCopy(d.getRecommendations(), MAX_FALLBACK_RECOMMENDATIONS), MAX_FALLBACK_RECOMMENDATIONS);
        sb.append("\n下面的成本诊断卡片里有详细指标。");
        return sb.toString().trim();
    }

    private static String shortFallbackBusiness(AiRunState state, AiBusinessOverviewResult o) {
        StringBuilder sb = new StringBuilder();
        Map<String, Object> os = o.getOverviewScope();
        if (os != null && !os.isEmpty()) {
            Object pb = os.get("primaryBanner");
            Object cd = os.get("coverageDetail");
            if (pb != null && !pb.toString().isBlank()) {
                sb.append(pb.toString().trim());
            }
            if (cd != null && !cd.toString().isBlank()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(cd.toString().trim());
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
        }
        String cs = DeterministicRendererSupport.nz(o.getCoveredStoresBrief()).trim();
        if (!cs.isBlank()) {
            sb.append(cs).append('\n');
        }
        if (os != null && !os.isEmpty()) {
            Object dmb = os.get("dataMissingStoresBrief");
            if (dmb != null && !dmb.toString().isBlank()) {
                sb.append(dmb.toString().trim()).append('\n');
            }
        }
        sb.append(extractOverviewNumericHeadlinePreferAnswerPlan(state, o));
        sb.append('\n');
        String purchaseBrief = DeterministicRendererSupport.nz(
                BusinessOverviewDeterministicSummaryBuilder.businessOverviewPurchaseCoreSentence(state)).trim();
        if (state.isBusinessOverviewPath() && !purchaseBrief.isEmpty()) {
            sb.append(purchaseBrief).append('\n');
        }
        String ps = DeterministicRendererSupport.nz(o.getPriorityStoresBrief()).trim();
        if (!ps.isBlank()) {
            sb.append(ps).append('\n');
        }
        appendNumbered(sb, "当前重点", capCopy(o.getFindings(), MAX_FALLBACK_FINDINGS), MAX_FALLBACK_FINDINGS);
        appendNumbered(sb, "建议动作", capCopy(o.getRecommendations(), MAX_FALLBACK_RECOMMENDATIONS), MAX_FALLBACK_RECOMMENDATIONS);
        sb.append("\n完整指标详见下方经营概览卡片。");
        return sb.toString().trim();
    }

    private static void appendNumbered(StringBuilder sb, String title, List<String> lines, int max) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        sb.append('\n').append(title).append("：\n");
        int n = Math.min(lines.size(), max);
        for (int i = 0; i < n; i++) {
            sb.append(i + 1).append(". ").append(lines.get(i).trim()).append('\n');
        }
    }

    private static List<String> capCopy(List<String> list, int max) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        if (list.size() <= max) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>(list.subList(0, max));
    }

    public String renderCostFallback(AiCostDiagnosisResult d) {
        return shortFallbackCost(d);
    }

    public String renderHarnessDiagnosisPlan(DiagnosisPlan plan) {
        return diagnosisDeterministicRenderer.renderHarnessDiagnosisPlan(plan);
    }

    /** 与 {@link StubAnswerComposerNode} 一致传入 state，用于门店经营对比 canonical 意图门控。 */
    public String renderHarnessDiagnosisPlan(AiRunState state, DiagnosisPlan plan) {
        return diagnosisDeterministicRenderer.renderHarnessDiagnosisPlan(state, plan);
    }

    public String renderStorePriorityRanking(AiRunState state, BusinessDiagnosisPlan plan) {
        return diagnosisDeterministicRenderer.renderStorePriorityRanking(state, plan);
    }

    /** D-11：库房 Scope + 门店排序追问——库房边界短文，不调 LLM。 */
    public String renderWarehouseBoundedBusinessDiagnosisStorePriority(AiRunState state, BusinessDiagnosisPlan plan) {
        return diagnosisDeterministicRenderer.renderWarehouseBoundedBusinessDiagnosisStorePriority(state, plan);
    }

    /** D-11：部分岗位在诊断链路上仅输出采购/库存/核销侧摘要，禁止集团排行与营业额/毛利口径。 */
    public String renderPermissionDowngradedBusinessDiagnosis(AiRunState state, BusinessDiagnosisPlan plan) {
        return diagnosisDeterministicRenderer.renderPermissionDowngradedBusinessDiagnosis(state, plan);
    }

    public String renderBusinessDiagnosisFallback(AiRunState state, BusinessDiagnosisPlan plan) {
        return diagnosisDeterministicRenderer.renderBusinessDiagnosisFallback(state, plan);
    }

    public String renderDishProfitFallback(AiDishProfitOverviewResult r, AiRunState state) {
        return dishProfitDeterministicRenderer.renderDishProfitFallback(r, state);
    }

    public String renderBusinessOverviewFallback(AiRunState state, AiBusinessOverviewResult o) {
        return shortFallbackBusiness(state, o);
    }

    public String renderDishProfitAnswerPlanOneLiner(DishProfitAnswerPlan plan) {
        return dishProfitDeterministicRenderer.renderAnswerPlanOneLiner(plan);
    }

    /** 菜品销量/销售额排行 AnswerPlan：确定性宣读，不调 LLM。 */
    public String renderDishSalesAnswerPlan(DishSalesAnswerPlan plan) {
        return dishSalesDeterministicRenderer.render(plan);
    }

    public String renderPurchaseCostFallback(AiRunState state) {
        return purchaseDeterministicRenderer.renderPurchaseCostFallback(state);
    }

    public String renderWarehouseStockFallback(AiRunState state) {
        return warehouseDeterministicRenderer.renderWarehouseStockFallback(state);
    }

    public String renderRevenueEnvelopeFallback(AiRunState state) {
        return revenueOverviewDeterministicFallback(state);
    }

    public String renderStockReduceToolFallback(AiRunState state) {
        return stockReduceDeterministicRenderer.renderStockReduceToolFallback(state);
    }

    public String genericEmptyLlmFallback() {
        return GENERIC_CHAT_EMPTY_LLM_FALLBACK;
    }
}
