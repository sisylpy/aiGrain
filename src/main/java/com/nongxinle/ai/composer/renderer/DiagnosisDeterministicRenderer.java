package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.agent.business.BusinessDiagnosisAgentV1;
import com.nongxinle.ai.context.AiDepartmentScopeDTO;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.harness.followup.BusinessDiagnosisDrilldownMatrix;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.graph.business.CostInsightIntentConvergence;
import com.nongxinle.ai.security.AiAnswerBoundary;
import com.nongxinle.ai.security.AiRoleCodes;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic fallback prose for business diagnosis paths (harness {@link DiagnosisPlan}). Does not call the LLM.
 * 门店经营对比：只宣读 Plan 内已排序 evidence 与 {@link DiagnosisPlan#getStoreCompareConclusion()}，不在 Composer 内排序/算比率/判最高最低。
 */
@Component
public final class DiagnosisDeterministicRenderer {

    /** 与 {@link BusinessDiagnosisAgentV1} findingType 对齐（跨包仅在此处用字面量，避免访问包可见常量）。 */
    private static final String FT_PROFIT_QUALITY_RISK = "PROFIT_QUALITY_RISK";
    private static final String FT_PURCHASE_PRESSURE = "PURCHASE_PRESSURE";
    private static final String FT_COST_PRESSURE = "COST_PRESSURE";
    private static final String FT_STOCK_REDUCE_ABNORMAL = "STOCK_REDUCE_ABNORMAL";
    private static final String FT_LOW_DISH_MARGIN = "LOW_DISH_MARGIN";

    /** 门店账号单店口径下，Composer/LLM 偶发把「采购 327」写成「327.0万元」；金额字段本身为元，只对带小数的「X万元」做纠偏。 */
    private static final Pattern STORE_PRIORITY_DECIMAL_WAN_YUAN_CONFUSION =
            Pattern.compile("(\\d+\\.\\d+)万元");

    /** Structured store-priority sub-intent on business diagnosis path (same checks as composer routing). */
    public static boolean isBusinessDiagnosisStorePriorityTurn(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        if (isBusinessDiagnosisStoreRiskReasonsDrilldownTurn(state)) {
            return false;
        }
        AiResolvedQueryIntent qi = state.getResolvedQueryContext().getQueryIntent();
        return qi != null
                && AiQuerySemanticLexicon.isStorePriorityRankingStructuredDetail(qi.getStructuredIntentDetail());
    }

    /** D-13.2：承接上一轮 STORE 锚点的「原因 / 具体问题」追问（wire {@code store_risk_reasons_drilldown}）。 */
    public static boolean isBusinessDiagnosisStoreRiskReasonsDrilldownTurn(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        if (isBusinessDiagnosisDomainAttributionTurn(state) || isBusinessDiagnosisActionFollowupTurn(state)) {
            return false;
        }
        AiResolvedQueryIntent qi = state.getResolvedQueryContext().getQueryIntent();
        return qi != null
                && AiQuerySemanticLexicon.isStoreRiskReasonsDrilldownStructuredDetail(qi.getStructuredIntentDetail());
    }

    /** BD-E/F/G：诊断内子域归因确认（仅宣读 Plan / debug，不切单域 AnswerPlan）。 */
    public static boolean isBusinessDiagnosisDomainAttributionTurn(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        AiResolvedQueryIntent qi = state.getResolvedQueryContext().getQueryIntent();
        if (qi == null) {
            return false;
        }
        String wire = qi.getStructuredIntentDetail();
        return AiQuerySemanticLexicon.isStoreDomainAttributionPurchaseStructuredDetail(wire)
                || AiQuerySemanticLexicon.isStoreDomainAttributionStockReduceStructuredDetail(wire)
                || AiQuerySemanticLexicon.isStoreDomainAttributionDishProfitStructuredDetail(wire);
    }

    /** BD-K：改进行动追问（宣读 {@link DiagnosisPlan#getActionSuggestions()}）。 */
    public static boolean isBusinessDiagnosisActionFollowupTurn(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        AiResolvedQueryIntent qi = state.getResolvedQueryContext().getQueryIntent();
        return qi != null
                && AiQuerySemanticLexicon.isDiagnosisActionFollowupStructuredDetail(qi.getStructuredIntentDetail());
    }

    /** D-11：库房端 Scope（{@link AiResolvedOrgScope#SCOPE_WAREHOUSE}），禁止集团/多门店经营诊断话术边界。 */
    public static boolean isWarehouseOrgScope(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        AiResolvedOrgScope org = state.getResolvedQueryContext().getOrgScope();
        return org != null && AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(org.getScopeType());
    }

    private static String resolveWarehouseAnchorLabel(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return "本库房/所属门店";
        }
        AiResolvedOrgScope org = state.getResolvedQueryContext().getOrgScope();
        if (org == null) {
            return "本库房/所属门店";
        }
        if (org.getVisibleWarehouses() != null) {
            for (AiDepartmentScopeDTO w : org.getVisibleWarehouses()) {
                if (w != null && StringUtils.hasText(w.getDepartmentName())) {
                    return w.getDepartmentName().trim();
                }
            }
        }
        if (org.getVisibleStores() != null) {
            for (AiStoreScopeDTO st : org.getVisibleStores()) {
                if (st != null && StringUtils.hasText(st.getStoreName())) {
                    return st.getStoreName().trim();
                }
            }
        }
        if (StringUtils.hasText(org.getScopeName())) {
            return org.getScopeName().trim();
        }
        return "本库房/所属门店";
    }

    private static boolean warehouseFindingExcludedForWarehouseScope(String line) {
        String s = line.trim();
        if (s.contains("老板先看哪家店")) {
            return true;
        }
        if (s.contains("全部门店")) {
            return true;
        }
        if (s.contains("其它门店")) {
            return true;
        }
        if (s.contains("排名第一")) {
            return true;
        }
        if (s.contains("排名第") && s.contains("门店")) {
            return true;
        }
        if (s.contains("综合经营")) {
            return true;
        }
        if (s.contains("经营更好") || s.contains("经营优劣")) {
            return true;
        }
        if (s.contains("营业额")) {
            return true;
        }
        if (s.contains("菜品毛利")) {
            return true;
        }
        if (s.contains("门店") && s.contains("排序")) {
            return true;
        }
        return false;
    }

    public String renderHarnessDiagnosisPlan(DiagnosisPlan plan) {
        return renderHarnessDiagnosisPlan(null, plan);
    }

    /**
     * 经营诊断 Harness：若当前轮为门店经营对比意图且已生成 {@link DiagnosisPlan#getStoreCompareEvidence()}，优先输出确定性对比短文。
     */
    public String renderHarnessDiagnosisPlan(AiRunState state, DiagnosisPlan plan) {
        if (isStoreCompareEvidenceAnswerTurn(state, plan)) {
            return renderStoreCompareEvidenceAnswer(plan);
        }
        if (isDomainAttributionAnswerTurn(state, plan)) {
            return renderDomainAttributionAnswer(state, plan);
        }
        if (isActionFollowupAnswerTurn(state, plan)) {
            return renderActionFollowupAnswer(state, plan);
        }
        if (isStoreRiskReasonsDrilldownAnswerTurn(state, plan)) {
            return renderStoreRiskReasonsDrilldownAnswer(state, plan);
        }
        if (isStorePriorityRankingAnswerTurn(state, plan)) {
            return renderStorePriorityRankingAnswer(state, plan);
        }
        return shortDeterministicHarnessDiagnosisPlan(plan);
    }

    /**
     * 门店风险/优先级追问（{@code store_priority_ranking} 或同类口语）单独编排：首句点店名，再按域简述依据；不先宣读单一出库段落。
     */
    public static boolean isStoreRiskReasonsDrilldownAnswerTurn(AiRunState state, DiagnosisPlan plan) {
        if (plan == null) {
            return false;
        }
        if (isWarehouseOrgScope(state)) {
            return false;
        }
        if (isBusinessDiagnosisStoreRiskReasonsDrilldownTurn(state)) {
            return true;
        }
        Map<String, Object> dbg = plan.getDebug();
        return dbg != null
                && BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_RISK_REASONS.equals(
                        dbg.get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_QUESTION_TYPE));
    }

    public static boolean isStorePriorityRankingAnswerTurn(AiRunState state, DiagnosisPlan plan) {
        if (plan == null) {
            return false;
        }
        if (isWarehouseOrgScope(state)) {
            return false;
        }
        if (isStoreRiskReasonsDrilldownAnswerTurn(state, plan)) {
            return false;
        }
        if (isBusinessDiagnosisStorePriorityTurn(state)) {
            return true;
        }
        Map<String, Object> dbg = plan.getDebug();
        if (dbg != null
                && BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_PRIORITY_RANKING.equals(
                dbg.get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_QUESTION_TYPE))) {
            return true;
        }
        if (state != null
                && BusinessDiagnosisDrilldownMatrix.isStorePriorityHarnessTextFallback(state)
                && state.getDiagnosisPlan() != null) {
            return true;
        }
        return false;
    }

    public static boolean isDomainAttributionAnswerTurn(AiRunState state, DiagnosisPlan plan) {
        if (plan == null || isWarehouseOrgScope(state)) {
            return false;
        }
        if (isBusinessDiagnosisDomainAttributionTurn(state)) {
            return true;
        }
        Map<String, Object> dbg = plan.getDebug();
        if (dbg == null) {
            return false;
        }
        Object facet = dbg.get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_FACET);
        if (facet == null) {
            return false;
        }
        String f = facet.toString().trim();
        return BusinessDiagnosisDrilldownMatrix.FACET_PURCHASE.equals(f)
                || BusinessDiagnosisDrilldownMatrix.FACET_STOCK_REDUCE.equals(f)
                || BusinessDiagnosisDrilldownMatrix.FACET_DISH_PROFIT.equals(f);
    }

    public static boolean isActionFollowupAnswerTurn(AiRunState state, DiagnosisPlan plan) {
        if (plan == null || isWarehouseOrgScope(state)) {
            return false;
        }
        if (isBusinessDiagnosisActionFollowupTurn(state)) {
            return true;
        }
        Map<String, Object> dbg = plan.getDebug();
        return dbg != null
                && "ACTION_FOLLOWUP".equals(
                        str(dbg.get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_QUESTION_TYPE)));
    }

    private static String renderDomainAttributionAnswer(AiRunState state, DiagnosisPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("【门店子域归因·诊断内说明】\n");
        String store = resolveDrilldownAnchorStoreLabel(state, plan);
        String childLabel = domainAttributionChildLabel(plan);
        if (StringUtils.hasText(store)) {
            sb.append('「').append(store.trim()).append("」");
        }
        sb.append("是否与").append(childLabel).append("相关，依据诊断计划已有结论（未重算、未切单域专答）：\n\n");

        Object gap =
                plan.getDebug() != null
                        ? plan.getDebug().get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_KNOWN_GAP)
                        : null;
        if (gap != null && StringUtils.hasText(gap.toString())) {
            sb.append("- 能力边界：").append(gap.toString().trim()).append('\n');
        }

        @SuppressWarnings("unchecked")
        List<String> lines =
                plan.getDebug() != null
                        ? (List<String>)
                                plan.getDebug().get(BusinessDiagnosisDrilldownMatrix.DEBUG_DOMAIN_ATTRIBUTION_LINES)
                        : null;
        if (lines != null && !lines.isEmpty()) {
            for (String line : lines) {
                if (StringUtils.hasText(line)) {
                    sb.append("- ").append(line.trim()).append('\n');
                }
            }
        } else {
            sb.append("- 当前子域在已挂载证据中暂无匹配的风险条目；请结合上文门店优先级与全量诊断结论判断。\n");
        }
        return sb.toString().trim();
    }

    private static String domainAttributionChildLabel(DiagnosisPlan plan) {
        if (plan.getDebug() == null) {
            return "该子域";
        }
        Object cd = plan.getDebug().get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_CHILD_DOMAIN);
        if (cd == null) {
            cd = plan.getDebug().get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_FACET);
        }
        if (cd == null) {
            return "该子域";
        }
        return switch (cd.toString().trim()) {
            case BusinessDiagnosisDrilldownMatrix.CHILD_PURCHASE -> "采购";
            case BusinessDiagnosisDrilldownMatrix.CHILD_STOCK_REDUCE -> "出库/核销";
            case BusinessDiagnosisDrilldownMatrix.CHILD_DISH_PROFIT -> "菜品毛利";
            default -> cd.toString().trim();
        };
    }

    private static String renderActionFollowupAnswer(AiRunState state, DiagnosisPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("【改进行动建议】\n");
        String store = resolveDrilldownAnchorStoreLabel(state, plan);
        if (StringUtils.hasText(store)) {
            sb.append("针对门店「").append(store.trim()).append("」，");
        }
        if (plan.getTimeLabel() != null && !plan.getTimeLabel().isBlank()) {
            sb.append('「').append(plan.getTimeLabel().trim()).append("」范围内");
        }
        sb.append("可优先参考下列动作（均摘自诊断计划 actionSuggestions，未另算）：\n\n");

        List<Map<String, Object>> sug = plan.getActionSuggestions();
        if (sug == null || sug.isEmpty()) {
            sb.append("- 当前诊断计划未给出结构化改进行动；建议先完成门店风险排序与原因拆解后再定动作。\n");
        } else {
            int n = 0;
            for (Map<String, Object> row : sug) {
                if (row == null || n >= 6) {
                    break;
                }
                String a = str(row.get("action"));
                if (!a.isBlank()) {
                    sb.append("- ").append(a.trim()).append('\n');
                    n++;
                }
            }
        }
        return sb.toString().trim();
    }

    private static String renderStorePriorityRankingAnswer(AiRunState state, DiagnosisPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("【门店综合风险·优先关注】\n");

        boolean strictStoreRank = hasFindingType(plan, FT_PROFIT_QUALITY_RISK);
        String topStore = resolveStorePriorityTopStoreName(plan);
        boolean named = StringUtils.hasText(topStore);

        if (strictStoreRank && named) {
            if (plan.getTimeLabel() != null && !plan.getTimeLabel().isBlank()) {
                sb.append('「').append(plan.getTimeLabel().trim()).append("」范围内问题最大的门店是 ")
                        .append(topStore.trim()).append("。\n\n");
            } else {
                sb.append("本期问题最大的门店是 ").append(topStore.trim()).append("。\n\n");
            }
        } else if (named) {
            sb.append("当前数据不足以形成严格的门店风险排名；从已返回指标看，")
                    .append(topStore.trim()).append(" 更值得优先关注。\n\n");
        } else {
            sb.append(
                    "当前数据不足以形成严格的门店风险排名，且无法唯一确定门店级下钻目标；已挂载子域未对齐到可点名的门店级综合结论，建议结合门店排行明细再判断。\n\n");
        }

        sb.append("判断依据（均来自诊断计划与 AnswerPlan 已有聚合字段，未重算）：\n");
        sb.append("- 营业额表现：").append(storePriorityRevenueLine(plan)).append('\n');
        sb.append("- 采购金额或采购异常：").append(storePriorityPurchaseLine(plan)).append('\n');
        sb.append("- 出库/核销金额：").append(storePriorityStockLine(plan)).append('\n');
        sb.append("- 菜品毛利或低毛利菜品：").append(storePriorityDishLine(plan)).append('\n');
        sb.append("- 数据缺失或暂不可判：").append(storePriorityMissingLine(plan)).append('\n');

        List<Map<String, Object>> sug = plan.getActionSuggestions();
        if (sug != null && !sug.isEmpty()) {
            sb.append("\n可跟进动作（摘自诊断计划建议，按需取用）：\n");
            int n = 0;
            for (Map<String, Object> row : sug) {
                if (row == null || n >= 4) {
                    break;
                }
                String a = str(row.get("action"));
                if (!a.isBlank()) {
                    sb.append("- ").append(a.trim()).append('\n');
                    n++;
                }
            }
        }

        return sb.toString().trim();
    }

    private static String renderStoreRiskReasonsDrilldownAnswer(AiRunState state, DiagnosisPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("【门店综合风险·原因说明】\n");
        String anchorStore = resolveDrilldownAnchorStoreLabel(state, plan);
        boolean named = StringUtils.hasText(anchorStore);

        if (named) {
            sb.append("上文判断问题最大的门店是 ")
                    .append(anchorStore.trim())
                    .append("，主要问题集中在以下方面（均来自诊断计划与 AnswerPlan 已有聚合字段，未重算）：\n\n");
        } else {
            sb.append(
                    "上一轮未能锁定唯一的门店级下钻目标；当前数据不足以形成完整原因拆解，但从已返回指标看，需要优先结合下列维度核对门店风险（均来自诊断计划与 AnswerPlan 已有聚合字段，未重算）：\n\n");
        }

        sb.append("- 营业额表现：").append(storePriorityRevenueLine(plan)).append('\n');
        sb.append("- 采购金额或采购异常：").append(storePriorityPurchaseLine(plan)).append('\n');
        sb.append("- 出库/核销金额：").append(storePriorityStockLine(plan)).append('\n');
        sb.append("- 菜品毛利或低毛利菜品：").append(storePriorityDishLine(plan)).append('\n');
        sb.append("- 数据缺失或暂不可判：").append(storePriorityMissingLine(plan)).append('\n');

        List<Map<String, Object>> sug = plan.getActionSuggestions();
        if (sug != null && !sug.isEmpty()) {
            sb.append("\n可跟进动作（摘自诊断计划建议，按需取用）：\n");
            int n = 0;
            for (Map<String, Object> row : sug) {
                if (row == null || n >= 4) {
                    break;
                }
                String a = str(row.get("action"));
                if (!a.isBlank()) {
                    sb.append("- ").append(a.trim()).append('\n');
                    n++;
                }
            }
        }

        return sb.toString().trim();
    }

    private static String resolveDrilldownAnchorStoreLabel(AiRunState state, DiagnosisPlan plan) {
        if (state != null && state.getResolvedQueryContext() != null) {
            String fn = state.getResolvedQueryContext().getFollowUpTargetEntityName();
            if (StringUtils.hasText(fn)) {
                return fn.trim();
            }
        }
        return resolveStorePriorityTopStoreName(plan);
    }

    private static String resolveStorePriorityTopStoreName(DiagnosisPlan plan) {
        if (plan.getDebug() != null) {
            Object o = plan.getDebug().get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_TOP_STORE_NAME);
            if (o != null && StringUtils.hasText(o.toString())) {
                return o.toString().trim();
            }
        }
        String fallback = BusinessDiagnosisAgentV1.extractStoreNameForStorePriorityRanking(plan, null, null, null);
        return fallback == null ? "" : fallback.trim();
    }

    private static boolean hasFindingType(DiagnosisPlan plan, String findingType) {
        if (!StringUtils.hasText(findingType) || plan.getFocusFindings() == null) {
            return false;
        }
        for (Map<String, Object> f : plan.getFocusFindings()) {
            if (f != null && findingType.equals(str(f.get("findingType")))) {
                return true;
            }
        }
        return false;
    }

    private static String clipFindingDetail(Map<String, Object> f, int maxLen) {
        if (f == null) {
            return "";
        }
        String d = str(f.get("detail"));
        if (d.isEmpty()) {
            return str(f.get("title"));
        }
        if (d.length() <= maxLen) {
            return d;
        }
        return d.substring(0, maxLen).trim() + "…";
    }

    private static Map<String, Object> firstFinding(DiagnosisPlan plan, String findingType) {
        if (plan.getFocusFindings() == null) {
            return null;
        }
        for (Map<String, Object> f : plan.getFocusFindings()) {
            if (f != null && findingType.equals(str(f.get("findingType")))) {
                return f;
            }
        }
        return null;
    }

    private static boolean missingHas(DiagnosisPlan plan, String key) {
        if (plan.getMissingSections() == null || !StringUtils.hasText(key)) {
            return false;
        }
        for (String s : plan.getMissingSections()) {
            if (s != null && key.equalsIgnoreCase(s.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String storePriorityRevenueLine(DiagnosisPlan plan) {
        if (missingHas(plan, "revenue")) {
            return "营业额子域 AnswerPlan 未挂载，无法从营收侧补强门店级结论。";
        }
        Map<String, Object> pq = firstFinding(plan, FT_PROFIT_QUALITY_RISK);
        if (pq != null) {
            return "门店排行行显示该店营业额领先，并与下文采购/出库压力判断对齐（AnswerPlan 门店匹配行）。";
        }
        Map<String, Object> nm = firstFinding(plan, "NO_MAJOR_FINDING");
        if (nm != null && plan.getFocusFindings() != null && plan.getFocusFindings().size() == 1) {
            return str(nm.get("detail"));
        }
        return "未发现单独命中「营业额」阈值的告警摘要；如需对比可看营业额门店排行类 AnswerPlan。";
    }

    private static String storePriorityPurchaseLine(DiagnosisPlan plan) {
        if (missingHas(plan, "purchase")) {
            return "采购子域未挂载，对应门店采购压力无法在诊断中展开。";
        }
        Map<String, Object> pq = firstFinding(plan, FT_PROFIT_QUALITY_RISK);
        if (pq != null) {
            return "该店采购金额相对该店营业额偏高（AnswerPlan 门店匹配行，与「利润质量待关注」同源信号）。";
        }
        Map<String, Object> pur = firstFinding(plan, FT_PURCHASE_PRESSURE);
        if (pur != null) {
            return "集团/汇总口径：" + clipFindingDetail(pur, 120);
        }
        return "未发现命中当前规则的采购压力摘要，或风险主要体现在其它域。";
    }

    private static String storePriorityStockLine(DiagnosisPlan plan) {
        if (missingHas(plan, "stockReduce")) {
            return "出库/核销子域未挂载，无法在同类口径下补足门店流出判断。";
        }
        Map<String, Object> pq = firstFinding(plan, FT_PROFIT_QUALITY_RISK);
        if (pq != null) {
            return "该店出库/核销合计相对该店营业额比值偏高（同上，不单列宣读集团总出库段落）。";
        }
        Map<String, Object> cost = firstFinding(plan, FT_COST_PRESSURE);
        if (cost != null) {
            return "汇总口径：" + clipFindingDetail(cost, 120);
        }
        Map<String, Object> ab = firstFinding(plan, FT_STOCK_REDUCE_ABNORMAL);
        if (ab != null) {
            return clipFindingDetail(ab, 120);
        }
        return "未检出废弃/损耗/退货分项或汇总出库压力的规则命中。";
    }

    private static String storePriorityDishLine(DiagnosisPlan plan) {
        if (missingHas(plan, "dishProfit")) {
            return "菜品毛利子域未挂载，低毛利菜品无法在诊断中展开。";
        }
        Map<String, Object> dm = firstFinding(plan, FT_LOW_DISH_MARGIN);
        if (dm != null) {
            return clipFindingDetail(dm, 120);
        }
        return "未发现命中当前规则的低毛利代表性菜品摘要。";
    }

    private static String storePriorityMissingLine(DiagnosisPlan plan) {
        if (plan.getMissingSections() != null && !plan.getMissingSections().isEmpty()) {
            return "未挂载或缺口域：" + String.join("、", plan.getMissingSections()) + "。";
        }
        if (plan.getWarnings() == null || plan.getWarnings().isEmpty()) {
            return "暂无额外告警；若门店排行行不齐仍需回到后台核对权限与工具覆盖。";
        }
        return String.join("；", plan.getWarnings());
    }

    /**
     * 门店经营对比：canonical 为 {@link AiQuerySemanticLexicon#STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS}
     * 且 {@link DiagnosisPlan#getStoreCompareEvidence()} 非空（无 state 时可凭 plan.debug.storeCompareEvidenceWire 判定，与渲染分支一致）。
     */
    public static boolean isStoreCompareEvidenceAnswerTurn(AiRunState state, DiagnosisPlan plan) {
        if (plan == null) {
            return false;
        }
        List<Map<String, Object>> ev = plan.getStoreCompareEvidence();
        if (ev == null || ev.isEmpty()) {
            return false;
        }
        if (state != null
                && state.getResolvedQueryContext() != null
                && state.getResolvedQueryContext().getQueryIntent() != null) {
            String raw = state.getResolvedQueryContext().getQueryIntent().getStructuredIntentDetail();
            String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw);
            return AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS.equals(canon);
        }
        Map<String, Object> dbg = plan.getDebug();
        if (dbg != null) {
            Object w = dbg.get("storeCompareEvidenceWire");
            return AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS.equals(String.valueOf(w).trim());
        }
        return false;
    }

    /**
     * 替换 Planner 下发的「集团合并经营诊断」类 {@link AiRunState#getCostIntentConvergenceNote()}：
     * 仅用于门店经营对比证据分支的【意图说明】前缀。
     */
    public static String storeCompareIntentConvergencePrefix(DiagnosisPlan plan) {
        if (plan == null) {
            return "";
        }
        LinkedHashSet<String> nameSet = new LinkedHashSet<>();
        List<Map<String, Object>> ev = plan.getStoreCompareEvidence();
        if (ev != null) {
            for (Map<String, Object> row : ev) {
                if (row == null) {
                    continue;
                }
                String n = str(row.get("storeName"));
                if (!n.isEmpty()) {
                    nameSet.add(n);
                }
            }
        }
        if (!nameSet.isEmpty()) {
            String joined = String.join("、", nameSet);
            String timePart = plan.getTimeLabel() != null && !plan.getTimeLabel().isBlank()
                    ? plan.getTimeLabel().trim()
                    : "统计时间见上文【查询范围】";
            String body = "下面按你可查看的门店范围，对 " + joined + " 做经营表现对比（" + timePart
                    + "；含营业额、采购、出库/核销、菜品毛利可用证据）。";
            return AiAnswerBoundary.costIntentConvergencePrefix(body);
        }
        return AiAnswerBoundary.costIntentConvergencePrefix("下面按你可查看的门店范围做经营表现对比。");
    }

    /** 门店经营对比：只宣读 {@link DiagnosisPlan} 已排序的 storeCompareEvidence 与 storeCompareConclusion。 */
    private static String renderStoreCompareEvidenceAnswer(DiagnosisPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("【门店经营对比】\n");
        if (plan.getScopeLabel() != null && !plan.getScopeLabel().isBlank()) {
            sb.append("查询范围：").append(plan.getScopeLabel().trim()).append('\n');
        }
        if (plan.getTimeLabel() != null && !plan.getTimeLabel().isBlank()) {
            sb.append("统计时间：").append(plan.getTimeLabel().trim()).append('\n');
        }
        if ((plan.getScopeLabel() == null || plan.getScopeLabel().isBlank())
                && (plan.getTimeLabel() == null || plan.getTimeLabel().isBlank())) {
            sb.append("查询范围与时间：见本轮诊断计划配置。\n");
        }
        sb.append('\n');

        List<Map<String, Object>> rows = plan.getStoreCompareEvidence();
        if (rows == null) {
            rows = Collections.emptyList();
        }

        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String label = storeRowLabel(row);
            sb.append("【").append(label).append("】\n");
            Map<String, Object> dc = coverageOf(row);

            sb.append("- 营业额：").append(formatMoneyOrMissing(row.get("revenueAmount"), dc.get("revenueAvailable"))).append('\n');
            sb.append("- 采购金额：").append(formatMoneyOrMissing(row.get("purchaseAmount"), dc.get("purchaseAvailable")))
                    .append('\n');
            String ratioLine = str(row.get("purchaseToRevenueRatioLine"));
            if (ratioLine.isEmpty()) {
                ratioLine = "暂无法计算（诊断计划未提供比例行）";
            }
            sb.append("- 采购占营业额比例：").append(ratioLine).append('\n');

            boolean stockAvail = Boolean.TRUE.equals(dc.get("stockReduceAvailable"));
            Object stockAmt = row.get("stockReduceAmount");
            if (stockAvail && stockAmt != null && diagnosisFmtYuan(stockAmt) != null) {
                sb.append("- 门店级出库合计：约 ").append(diagnosisFmtYuan(stockAmt)).append(" 元\n");
            } else {
                sb.append("- 出库：本轮无可靠的门店级出库合计（或未返回该门店行）。\n");
            }

            String dpc = str(row.get("dishProfitCoverage"));
            if ("AGGREGATE_ONLY".equals(dpc)) {
                sb.append("- 菜品毛利：工具仅返回集团/范围汇总，无门店级拆分，不能用于门店对比。\n");
            } else {
                sb.append("- 菜品毛利：本轮无可用门店级拆分（").append(dpc.isEmpty() ? "NA" : dpc).append("），不能用于门店对比。\n");
            }
            sb.append('\n');
        }

        sb.append("【谨慎结论】\n");
        String conclusion = plan.getStoreCompareConclusion();
        if (conclusion != null && !conclusion.isBlank()) {
            sb.append(conclusion.trim());
        } else {
            sb.append("暂无门店对比谨慎结论（诊断计划未生成结论段）。");
        }
        return sb.toString().trim();
    }

    private static String storeRowLabel(Map<String, Object> row) {
        String name = str(row.get("storeName"));
        if (!name.isEmpty()) {
            return name;
        }
        Object id = row.get("storeDepartmentId");
        return id != null ? ("门店 " + id) : "未知门店";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> coverageOf(Map<String, Object> row) {
        Object dc = row.get("dataCoverage");
        if (dc instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Collections.emptyMap();
    }

    private static String formatMoneyOrMissing(Object amount, Object availableFlag) {
        if (!Boolean.TRUE.equals(availableFlag)) {
            return "本轮未成功覆盖或缺失";
        }
        String y = diagnosisFmtYuan(amount);
        if (y == null) {
            return "暂无";
        }
        return "约 " + y + " 元";
    }

    /**
     * D-11：{@code STORE} + 唯一可见门店时，修正终稿中与集团广角混用的措辞；
     * 并将「327.0万元」这类<strong>含小数且金额实为元</strong>的串改回「327.0元」（采购等字段口径为元）。
     */
    public static String applyStorePrioritySingleStoreScopeDisplayPatches(String prose, AiRunState state) {
        if (!StringUtils.hasText(prose) || state == null || state.getResolvedQueryContext() == null) {
            return prose;
        }
        AiResolvedOrgScope org = state.getResolvedQueryContext().getOrgScope();
        if (org == null || !AiResolvedOrgScope.SCOPE_STORE.equals(org.getScopeType())) {
            return prose;
        }
        List<AiStoreScopeDTO> vis = org.getVisibleStores();
        if (vis == null || vis.size() != 1) {
            return prose;
        }

        String out = prose;
        out = out.replace("基于集团视角", "基于当前可查看范围");
        out = out.replace("从集团视角", "从你当前可查看范围");
        out = out.replace("以集团视角", "以当前可查看范围");
        out = out.replace("在集团视角下", "在你当前门店权限范围内");
        out = out.replace("集团视角", "当前可查看范围");

        out = out.replace("集团口径出库/核销四类合计为 0", "本店口径出库/核销四类合计为 0");

        Matcher m = STORE_PRIORITY_DECIMAL_WAN_YUAN_CONFUSION.matcher(out);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + "元"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 新版 {@link DiagnosisPlan}（只读子域 AnswerPlan 聚合）：确定性短文，不调用 LLM、不心算。 */
    private static String shortDeterministicHarnessDiagnosisPlan(DiagnosisPlan plan) {
        if (plan == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【经营诊断·证据型】\n");

        if (plan.getScopeLabel() != null && !plan.getScopeLabel().isBlank()) {
            sb.append("组织范围：").append(plan.getScopeLabel().trim()).append('\n');
        }
        if (plan.getTimeLabel() != null && !plan.getTimeLabel().isBlank()) {
            sb.append("时间范围：").append(plan.getTimeLabel().trim()).append('\n');
        }
        if (plan.getRiskLevel() != null && !plan.getRiskLevel().isBlank()) {
            sb.append("综合风险级别：").append(plan.getRiskLevel().trim()).append('\n');
        }
        if (plan.getOverallJudgement() != null && !plan.getOverallJudgement().isBlank()) {
            sb.append("\n总体判断\n").append(plan.getOverallJudgement().trim()).append('\n');
        } else if (plan.getSummary() != null && !plan.getSummary().isBlank()) {
            sb.append("\n概要\n").append(plan.getSummary().trim()).append('\n');
        }

        appendStructuredFindingSection(sb, "重点问题（含证据来源与等级）", plan.getFocusFindings(), 24);

        List<Map<String, Object>> evid = plan.getEvidenceItems();
        if (evid != null && !evid.isEmpty()) {
            sb.append("\n关键证据摘录（来自 AnswerPlan 字段）：\n");
            int n = 0;
            for (Map<String, Object> row : evid) {
                if (row == null || n >= 14) {
                    break;
                }
                String flat = diagnosisPlanMapRowToReadableLine(row);
                if (flat != null && !flat.isBlank()) {
                    sb.append("- ").append(flat).append('\n');
                    n++;
                }
            }
        }

        appendStringListSection(sb, "缺失数据域", plan.getMissingSections(), 24);
        appendStringListSection(sb, "告警与说明", plan.getWarnings(), 20);

        List<Map<String, Object>> sug = plan.getActionSuggestions();
        if (sug != null && !sug.isEmpty()) {
            sb.append("\n建议动作：\n");
            int i = 0;
            for (Map<String, Object> row : sug) {
                if (row == null || i >= 16) {
                    break;
                }
                String flat = diagnosisPlanMapRowToReadableLine(row);
                if (flat != null && !flat.isBlank()) {
                    sb.append("- ").append(flat).append('\n');
                    i++;
                }
            }
        }

        appendDiagnosisPlanMapListSection(sb, "风险提示（汇总行）", plan.getRiskRows(), 16);

        List<Map<String, Object>> ev = plan.getEvidenceRows();
        if ((evid == null || evid.isEmpty()) && ev != null && !ev.isEmpty()) {
            sb.append("\n证据摘录（子计划 summary 切片）：\n");
            int n = 0;
            for (Map<String, Object> row : ev) {
                if (row == null || n >= 16) {
                    break;
                }
                Object label = row.get("label");
                if ("__planAttached".equals(String.valueOf(label))) {
                    Object src = row.get("sourcePlan");
                    Object ptype = row.get("planType");
                    if (src != null || ptype != null) {
                        sb.append("- 已挂载 ").append(src != null ? src : "子计划")
                                .append(ptype != null ? "（" + ptype + "）" : "")
                                .append("\n");
                        n++;
                    }
                    continue;
                }
                Object value = row.get("value");
                if (label != null && value != null) {
                    sb.append("- ").append(label).append("：").append(value).append("\n");
                    n++;
                } else {
                    String flat = diagnosisPlanMapRowToReadableLine(row);
                    if (flat != null && !flat.isBlank()) {
                        sb.append("- ").append(flat).append("\n");
                        n++;
                    }
                }
            }
        }

        String out = sb.toString().trim();
        return out.isEmpty() ? "经营诊断计划已生成，暂无摘录字段。" : out;
    }

    private static void appendStructuredFindingSection(
            StringBuilder sb, String title, List<Map<String, Object>> rows, int maxRows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        sb.append("\n").append(title).append("：\n");
        int n = 0;
        for (Map<String, Object> row : rows) {
            if (row == null || n >= maxRows) {
                break;
            }
            if (row.containsKey("findingType")) {
                String ft = str(row.get("findingType"));
                String sev = str(row.get("severity"));
                String src = str(row.get("evidenceSource"));
                String ttl = str(row.get("title"));
                String det = str(row.get("detail"));
                String act = str(row.get("suggestedAction"));
                if (ttl.isEmpty() && det.isEmpty()) {
                    continue;
                }
                sb.append("- ");
                if (!ttl.isEmpty()) {
                    sb.append(ttl);
                }
                if (!ft.isEmpty()) {
                    sb.append("（").append(ft).append("）");
                }
                if (!sev.isEmpty()) {
                    sb.append(" [").append(sev).append("]");
                }
                if (!src.isEmpty()) {
                    sb.append(" 来源:").append(src);
                }
                sb.append("\n");
                if (!det.isEmpty()) {
                    sb.append("  ").append(det).append("\n");
                }
                if (!act.isEmpty()) {
                    sb.append("  建议：").append(act).append("\n");
                }
                n++;
            } else {
                String line = diagnosisPlanMapRowToReadableLine(row);
                if (line != null && !line.isBlank()) {
                    sb.append("- ").append(line).append("\n");
                    n++;
                }
            }
        }
    }

    private static void appendStringListSection(StringBuilder sb, String title, List<String> lines, int max) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        sb.append("\n").append(title).append("：\n");
        int n = 0;
        for (String s : lines) {
            if (s == null || s.isBlank() || n >= max) {
                break;
            }
            sb.append("- ").append(s.trim()).append("\n");
            n++;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static void appendDiagnosisPlanMapListSection(
            StringBuilder sb, String title, List<Map<String, Object>> rows, int maxRows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        sb.append("\n").append(title).append("：\n");
        int n = 0;
        for (Map<String, Object> row : rows) {
            if (row == null || n >= maxRows) {
                break;
            }
            String line = diagnosisPlanMapRowToReadableLine(row);
            if (line != null && !line.isBlank()) {
                sb.append("- ").append(line).append("\n");
                n++;
            }
        }
    }

    /** 将 DiagnosisPlan 中一行 Map 压成可读一句（多键用分号连接）。 */
    private static String diagnosisPlanMapRowToReadableLine(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            parts.add(e.getKey() + "：" + e.getValue());
        }
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    /** Exposed for {@link DeterministicAnswerRenderer} dish-profit deterministic paths (same wording). */
    public static String lowestMarginDiagnosisFallbackLine(Map<String, Object> r0) {
        String name = DeterministicRendererSupport.nz(r0.get("dishName"));
        if (name.isEmpty()) {
            name = "该菜品";
        }
        StringBuilder sb = new StringBuilder(buildDiagnosisDishDragEvidenceSentenceStub(name, r0));
        sb.append("，建议核对出库、配方和售价。");
        return sb.toString();
    }

    /** 仅拼接非空的销售额/理论/实际/毛利率，禁止「xx为，」半截。 */
    public static String buildDiagnosisDishDragEvidenceSentenceStub(String dishName, Map<String, Object> row) {
        List<String> segs = new ArrayList<>();
        String rate = diagnosisFmtPercent(r0Get(row, "blendedGrossMarginRateOnListPrice"));
        if (diagnosisDetailPresent(rate)) {
            segs.add("毛利率约" + rate);
        }
        String rev = diagnosisFmtYuan(r0Get(row, "listPriceRevenue"));
        if (diagnosisDetailPresent(rev)) {
            segs.add("销售额" + rev + "元");
        }
        String theory = diagnosisFmtYuan(r0Get(row, "theoryCostAmount"));
        if (diagnosisDetailPresent(theory)) {
            segs.add("理论成本" + theory + "元");
        }
        String actual = diagnosisFmtYuan(r0Get(row, "actualCostAmount"));
        if (diagnosisDetailPresent(actual)) {
            segs.add("实际成本" + actual + "元");
        }
        String reason = DeterministicRendererSupport.nz(row.get("riskReason"));
        StringBuilder ev = new StringBuilder();
        ev.append("拖累毛利最明显的是").append(dishName);
        if (!segs.isEmpty()) {
            ev.append("，");
            ev.append(String.join("，", segs));
        }
        if (!reason.isEmpty()) {
            ev.append(segs.isEmpty() ? "，" : "，").append(reason);
        } else if (segs.isEmpty()) {
            ev.append("，本期单菜明细未完整返回，请核对后台行项目。");
        }
        return ev.toString();
    }

    public static Object r0Get(Map<String, Object> row, String key) {
        return row == null ? null : row.get(key);
    }

    public static boolean diagnosisDetailPresent(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String t = s.trim();
        return !"—".equals(t) && !"暂无".equals(t);
    }

    public static String diagnosisFmtPercent(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return String.format(Locale.CHINA, "%.2f%%", n.doubleValue());
        }
        String s = v.toString().trim();
        if (s.isEmpty() || "暂无".equals(s) || "—".equals(s) || s.contains("不适用")) {
            return null;
        }
        if (s.endsWith("%")) {
            return s;
        }
        try {
            return String.format(Locale.CHINA, "%.2f%%", Double.parseDouble(s.replace(",", "")));
        } catch (Exception e) {
            return s;
        }
    }

    public static String diagnosisFmtYuan(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            BigDecimal b = n instanceof BigDecimal bd ? bd : BigDecimal.valueOf(n.doubleValue());
            b = b.setScale(2, RoundingMode.HALF_UP);
            if (b.stripTrailingZeros().scale() <= 0) {
                return b.stripTrailingZeros().toPlainString();
            }
            return b.toPlainString();
        }
        String s = v.toString().trim();
        if (s.isEmpty() || "暂无".equals(s) || "—".equals(s) || s.contains("不适用")) {
            return null;
        }
        return s;
    }

}
