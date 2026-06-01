package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.DishProfitPrescriptionAnswerPlan;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.cost.AiCostDiagnosisResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic prose for Answer Composer LLM fallbacks: reads AnswerPlans,
 * {@link AiRunState#getResolvedQueryContext()} structured signals, diagnosis plans.<br>
 * Does not call the LLM. Copy-frozen wording from composer node.
 */
@Component
public final class DeterministicAnswerRenderer {

    private static final int MAX_FALLBACK_FINDINGS = 3;
    private static final int MAX_FALLBACK_RECOMMENDATIONS = 3;

    private final DiagnosisDeterministicRenderer diagnosisDeterministicRenderer;
    private final DishProfitDeterministicRenderer dishProfitDeterministicRenderer;
    private final DishSalesDeterministicRenderer dishSalesDeterministicRenderer;
    private final MenuOperationDeterministicRenderer menuOperationDeterministicRenderer;
    private final DishProfitPrescriptionDeterministicRenderer dishProfitPrescriptionDeterministicRenderer;

    @Autowired
    public DeterministicAnswerRenderer(DiagnosisDeterministicRenderer diagnosisDeterministicRenderer,
            DishProfitDeterministicRenderer dishProfitDeterministicRenderer,
            DishSalesDeterministicRenderer dishSalesDeterministicRenderer,
            MenuOperationDeterministicRenderer menuOperationDeterministicRenderer,
            DishProfitPrescriptionDeterministicRenderer dishProfitPrescriptionDeterministicRenderer) {
        this.diagnosisDeterministicRenderer = diagnosisDeterministicRenderer;
        this.dishProfitDeterministicRenderer = dishProfitDeterministicRenderer;
        this.dishSalesDeterministicRenderer = dishSalesDeterministicRenderer;
        this.menuOperationDeterministicRenderer = menuOperationDeterministicRenderer;
        this.dishProfitPrescriptionDeterministicRenderer = dishProfitPrescriptionDeterministicRenderer;
    }

    /**
     * Wiring without Spring (unit tests, scripts).
     */
    public static DeterministicAnswerRenderer createStandalone() {
        return new DeterministicAnswerRenderer(
                new DiagnosisDeterministicRenderer(),
                new DishProfitDeterministicRenderer(),
                new DishSalesDeterministicRenderer(),
                new MenuOperationDeterministicRenderer(),
                new DishProfitPrescriptionDeterministicRenderer());
    }

    private static final String GENERIC_NON_BUSINESS_PLAN_FALLBACK =
            "当前问题未匹配到可用的业务分析计划，请换个问法或缩小范围后重试。";

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

    /** 与 {@link com.nongxinle.ai.graph.business.StubAnswerComposerNode} 一致传入 state，用于门店经营对比 canonical 意图门控。 */
    public String renderHarnessDiagnosisPlan(AiRunState state, DiagnosisPlan plan) {
        return diagnosisDeterministicRenderer.renderHarnessDiagnosisPlan(state, plan);
    }

    public String renderDishProfitAnswerPlanOneLiner(DishProfitAnswerPlan plan) {
        return dishProfitDeterministicRenderer.renderAnswerPlanOneLiner(plan);
    }

    /** 菜品销量/销售额排行 AnswerPlan：确定性宣读，不调 LLM。 */
    public String renderDishSalesAnswerPlan(DishSalesAnswerPlan plan) {
        return dishSalesDeterministicRenderer.render(plan);
    }

    /** 菜单经营顾问 AnswerPlan：确定性宣读，只读 Plan。 */
    public String renderMenuOperationAnswerPlan(MenuOperationAnswerPlan plan) {
        return menuOperationDeterministicRenderer.render(plan);
    }

    /** 单菜利润处方 AnswerPlan：确定性宣读，只读 Plan。 */
    public String renderDishProfitPrescriptionAnswerPlan(DishProfitPrescriptionAnswerPlan plan) {
        return dishProfitPrescriptionDeterministicRenderer.render(plan);
    }

    public String genericNonBusinessPlanFallback() {
        return GENERIC_NON_BUSINESS_PLAN_FALLBACK;
    }
}
