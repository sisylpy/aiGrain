package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.context.AiDepartmentScopeDTO;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.BusinessDiagnosisPlan;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic fallback prose for business diagnosis paths (store priority, harness {@link DiagnosisPlan},
 * {@link BusinessDiagnosisPlan} summaries). Does not call the LLM.
 */
@Component
public final class DiagnosisDeterministicRenderer {

    /** 门店账号单店口径下，Composer/LLM 偶发把「采购 327」写成「327.0万元」；金额字段本身为元，只对带小数的「X万元」做纠偏。 */
    private static final Pattern STORE_PRIORITY_DECIMAL_WAN_YUAN_CONFUSION =
            Pattern.compile("(\\d+\\.\\d+)万元");

    /** Structured store-priority sub-intent on business diagnosis path (same checks as composer routing). */
    public static boolean isBusinessDiagnosisStorePriorityTurn(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        AiResolvedQueryIntent qi = state.getResolvedQueryContext().getQueryIntent();
        return qi != null
                && AiQuerySemanticLexicon.isStorePriorityRankingStructuredDetail(qi.getStructuredIntentDetail());
    }

    /** D-11：库房端 Scope（{@link AiResolvedOrgScope#SCOPE_WAREHOUSE}），禁止集团/多门店经营诊断话术边界。 */
    public static boolean isWarehouseOrgScope(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        AiResolvedOrgScope org = state.getResolvedQueryContext().getOrgScope();
        return org != null && AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(org.getScopeType());
    }

    /**
     * 经营诊断 store_priority_ranking：确定性短文；{@link AiRunState} 非空时在店长单店 STORE Scope 下做显示层纠偏
     * （集团视角话术 / 小数万元误标注），不改 Plan 数据来源。
     */
    public String renderStorePriorityRanking(AiRunState state, BusinessDiagnosisPlan plan) {
        return applyStorePrioritySingleStoreScopeDisplayPatches(shortFallbackStorePriorityRanking(plan), state);
    }

    /**
     * D-11：库房 Scope 下用户对「哪家店优先」类追问——只给库房证据链摘要，不调 LLM、不做多门店经营排名。
     */
    public String renderWarehouseBoundedBusinessDiagnosisStorePriority(AiRunState state, BusinessDiagnosisPlan plan) {
        String anchor = resolveWarehouseAnchorLabel(state);
        StringBuilder sb = new StringBuilder();
        sb.append("【库房视角说明】\n");
        sb.append("当前账号为库房端，只能查看 ").append(anchor).append(" 权限范围内的库存、出库/核销与采购入库相关数据。");
        sb.append("不能做全集团或多门店「哪家店综合经营问题最大」的排名，也不讨论营业额与菜品毛利。\n\n");
        sb.append("【库房侧风险摘要】\n");

        boolean any = appendWarehousePurchaseStockEvidence(sb, plan);
        any |= appendRiskItemsForDomains(sb, plan, "PURCHASE", "STOCK_REDUCE");
        any |= appendMainFindingsFiltered(sb, plan, 4, false);

        if (!any) {
            sb.append("- 本轮在库房权限内可用的结构化采购/出库摘要较少；建议核对盘点差异、报损与入库单据是否在统计周期内完整同步。\n");
        }
        sb.append("\n若需对比多家门店的经营表现或营业额排名，请在具备相应权限的岗位下提问。");
        return sb.toString().trim();
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
        return shortDeterministicHarnessDiagnosisPlan(plan);
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

    /** 门店经营对比（仅引用 tool 信封已落地的 storeCompareEvidence；不心算、不补全缺失出库/门店毛利）。 */
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

        List<Map<String, Object>> rows = new ArrayList<>(plan.getStoreCompareEvidence());
        rows.removeIf(Objects::isNull);
        rows.sort(Comparator.comparing(
                        DiagnosisDeterministicRenderer::safeRevenueAmount,
                        Comparator.nullsFirst(Double::compareTo))
                .reversed()
                .thenComparing(DiagnosisDeterministicRenderer::storeRowLabel));

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
            sb.append("- 采购占营业额比例：").append(formatPurchaseToRevenueRatioLine(row, dc)).append('\n');

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
        sb.append(buildStoreCompareCautiousConclusion(rows));
        return sb.toString().trim();
    }

    private static Double safeRevenueAmount(Map<String, Object> row) {
        return parseDoubleLoose(row == null ? null : row.get("revenueAmount"));
    }

    private static Double parseDoubleLoose(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        String s = v.toString().trim().replace(",", "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
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

    private static String formatPurchaseToRevenueRatioLine(Map<String, Object> row, Map<String, Object> dc) {
        boolean revOk = Boolean.TRUE.equals(dc.get("revenueAvailable"));
        boolean purOk = Boolean.TRUE.equals(dc.get("purchaseAvailable"));
        Double rev = parseDoubleLoose(row.get("revenueAmount"));
        Double pur = parseDoubleLoose(row.get("purchaseAmount"));
        if (!revOk || !purOk || rev == null || pur == null || rev <= 0) {
            return "暂无法计算（营业额或采购缺失，或营业额≤0）";
        }
        double pct = pur / rev * 100.0;
        return String.format(Locale.CHINA, "约 %.1f%%", pct);
    }

    private static String buildStoreCompareCautiousConclusion(List<Map<String, Object>> rows) {
        String bestRevName = null;
        Double bestRev = null;
        final class RatioPick {
            String name;
            double ratio;
        }
        List<RatioPick> ratioPicks = new ArrayList<>();
        boolean anyStockStoreLevel = false;

        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Map<String, Object> dc = coverageOf(row);
            Double rev = parseDoubleLoose(row.get("revenueAmount"));
            if (Boolean.TRUE.equals(dc.get("revenueAvailable")) && rev != null) {
                if (bestRev == null || rev > bestRev) {
                    bestRev = rev;
                    bestRevName = storeRowLabel(row);
                }
            }
            Double pur = parseDoubleLoose(row.get("purchaseAmount"));
            if (Boolean.TRUE.equals(dc.get("revenueAvailable"))
                    && Boolean.TRUE.equals(dc.get("purchaseAvailable"))
                    && rev != null
                    && rev > 0
                    && pur != null) {
                RatioPick p = new RatioPick();
                p.name = storeRowLabel(row);
                p.ratio = pur / rev;
                ratioPicks.add(p);
            }
            if (Boolean.TRUE.equals(dc.get("stockReduceAvailable"))) {
                anyStockStoreLevel = true;
            }
        }

        StringBuilder c = new StringBuilder();
        if (bestRevName != null && bestRev != null) {
            String y = diagnosisFmtYuan(bestRev);
            c.append("从营业额看，").append(bestRevName).append("更高（约 ").append(y != null ? y : bestRev).append(" 元）。");
        }

        if (ratioPicks.size() >= 2) {
            RatioPick lowest = ratioPicks.get(0);
            for (RatioPick p : ratioPicks) {
                if (p.ratio < lowest.ratio) {
                    lowest = p;
                }
            }
            if (c.length() > 0) {
                c.append(" ");
            }
            c.append("从采购占营业额比例看，").append(lowest.name).append("占比更低（约 ")
                    .append(String.format(Locale.CHINA, "%.1f%%", lowest.ratio * 100.0))
                    .append("），相对采购压力更小。");
        } else if (ratioPicks.size() == 1) {
            RatioPick only = ratioPicks.get(0);
            if (c.length() > 0) {
                c.append(" ");
            }
            c.append(only.name).append(" 的采购占营业额比例约 ")
                    .append(String.format(Locale.CHINA, "%.1f%%", only.ratio * 100.0))
                    .append("（仅单店可算，不做门店间优劣排序）。");
        }

        if (c.length() > 0) {
            c.append('\n');
        }
        if (!anyStockStoreLevel) {
            c.append("本轮缺少可靠的门店级出库合计，无法把出库压力纳入门店对比。\n");
        } else {
            c.append("出库虽有个别门店级合计，仍建议结合完整出库明细再做判断。\n");
        }
        c.append("菜品毛利仅集团/范围汇总，没有门店级拆分，不能用于门店对比。\n");
        c.append("因此不宜仅凭营业额判断哪家「经营更好」，也不宜在缺少完备门店级证据时得出完整经营优劣定论。");
        return c.toString().trim();
    }

    public String renderBusinessDiagnosisFallback(AiRunState state, BusinessDiagnosisPlan plan) {
        return shortFallbackBusinessDiagnosis(state, plan);
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

    private static String shortFallbackStorePriorityRanking(BusinessDiagnosisPlan plan) {
        if (plan == null || plan.getStorePriorityRanking() == null) {
            return "当前未能生成门店优先级排序，请稍后重试或核对可见门店范围。";
        }
        List<BusinessDiagnosisPlan.StorePriorityFocus> fs = plan.getStorePriorityRanking().getFocusStores();
        if (fs == null || fs.isEmpty()) {
            return "当前未能生成门店优先级排序，请稍后重试或核对可见门店范围。";
        }
        BusinessDiagnosisPlan.StorePriorityFocus first = fs.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("今天建议先处理 ").append(DeterministicRendererSupport.nz(first.getStoreName())).append("。\n\n");
        sb.append("原因是：").append(DeterministicRendererSupport.nz(first.getReason())).append("\n\n");
        if (fs.size() > 1) {
            BusinessDiagnosisPlan.StorePriorityFocus second = fs.get(1);
            sb.append(DeterministicRendererSupport.nz(second.getStoreName())).append(" 排在后面：")
                    .append(DeterministicRendererSupport.nz(second.getReason())).append("\n\n");
        }
        sb.append("今天先做三件事：\n");
        int k = 0;
        for (BusinessDiagnosisPlan.StorePriorityFocus f : fs) {
            if (f == null) {
                continue;
            }
            String su = f.getSuggestion();
            if (su == null || su.isBlank()) {
                continue;
            }
            k++;
            sb.append(k).append(". ").append(su.trim()).append("\n");
            if (k >= 3) {
                break;
            }
        }
        if (k == 0 && first.getSuggestion() != null && !first.getSuggestion().isBlank()) {
            sb.append("1. ").append(first.getSuggestion().trim());
        }
        return sb.toString().trim();
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

    private static String shortFallbackBusinessDiagnosis(AiRunState state, BusinessDiagnosisPlan plan) {
        if (state != null && isBusinessDiagnosisStorePriorityTurn(state) && plan != null
                && plan.getStorePriorityRanking() != null
                && plan.getStorePriorityRanking().getFocusStores() != null
                && !plan.getStorePriorityRanking().getFocusStores().isEmpty()) {
            return applyStorePrioritySingleStoreScopeDisplayPatches(shortFallbackStorePriorityRanking(plan), state);
        }
        DishProfitAnswerPlan ap = state != null ? state.getDishProfitAnswerPlan() : null;
        if (ap != null && DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN.equals(ap.getPlanType())
                && ap.getFocusRows() != null && !ap.getFocusRows().isEmpty()) {
            Map<String, Object> r0 = ap.getFocusRows().get(0);
            if (r0 != null) {
                return lowestMarginDiagnosisFallbackLine(r0);
            }
        }
        return shortFallbackBusinessDiagnosisFromPlanOnly(plan);
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

    /** 与 BusinessDiagnosisPlanBuilder 一致：仅拼接非空的销售额/理论/实际/毛利率，禁止「xx为，」半截。 */
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

    private static String shortFallbackBusinessDiagnosisFromPlanOnly(BusinessDiagnosisPlan plan) {
        if (plan == null) {
            return "本轮经营诊断计划暂未生成，请稍后重试。";
        }
        StringBuilder sb = new StringBuilder();
        if (plan.getOverallSummary() != null
                && plan.getOverallSummary().getHeadline() != null
                && !plan.getOverallSummary().getHeadline().isBlank()) {
            sb.append(plan.getOverallSummary().getHeadline().trim());
        }
        if (plan.getMainFindings() != null && !plan.getMainFindings().isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("主要发现：");
            int i = 0;
            for (String f : plan.getMainFindings()) {
                if (f == null || f.isBlank()) {
                    continue;
                }
                if (i >= 5) {
                    break;
                }
                sb.append(i == 0 ? "" : "；").append(f.trim());
                i++;
            }
        }
        if (plan.getRiskItems() != null && !plan.getRiskItems().isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("风险：");
            int j = 0;
            for (BusinessDiagnosisPlan.DiagnosisRiskItem ri : plan.getRiskItems()) {
                if (ri == null || (ri.getTitle() == null || ri.getTitle().isBlank())) {
                    continue;
                }
                if (j >= 5) {
                    break;
                }
                sb.append(j == 0 ? "" : "；").append(ri.getTitle().trim());
                j++;
            }
        }
        if (plan.getActionItems() != null && !plan.getActionItems().isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("建议：");
            int k = 0;
            for (String a : plan.getActionItems()) {
                if (a == null || a.isBlank()) {
                    continue;
                }
                if (k >= 5) {
                    break;
                }
                sb.append(k == 0 ? "" : "；").append(a.trim());
                k++;
            }
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? "经营诊断结果已就绪，详情见调试计划。" : s;
    }

    /**
     * D-11：库房 / 采购 / 配送等在诊断链路上的权限降级正文（不调 LLM，不包含营业额与菜品毛利结论）。
     */
    public String renderPermissionDowngradedBusinessDiagnosis(AiRunState state, BusinessDiagnosisPlan plan) {
        if (plan == null) {
            return "";
        }
        AiUserContext ctx = state != null ? state.getAiUserContext() : null;
        String rc = ctx != null ? ctx.getRoleCode() : null;
        if (isWarehouseOrgScope(state)
                || AiRoleCodes.WAREHOUSE_MANAGER.equals(rc)
                || AiRoleCodes.REGION_WAREHOUSE.equals(rc)) {
            return renderWarehouseDowngradedBusinessDiagnosis(state, plan);
        }
        if (CostInsightIntentConvergence.isProcurementCostConvergenceRole(rc)) {
            return renderPurchaserDowngradedBusinessDiagnosis(plan);
        }
        if (AiRoleCodes.DELIVERY_SUPPLIER.equals(rc) || AiRoleCodes.DELIVERY_DRIVER.equals(rc)) {
            return renderDeliveryDowngradedBusinessDiagnosis(plan);
        }
        return renderPurchaserDowngradedBusinessDiagnosis(plan);
    }

    private static String renderWarehouseDowngradedBusinessDiagnosis(AiRunState state, BusinessDiagnosisPlan plan) {
        String anchor = resolveWarehouseAnchorLabel(state);
        StringBuilder sb = new StringBuilder();
        sb.append("【权限降级·库房视角】\n");
        sb.append("当前账号不包含营业额或菜品毛利权限；只能查看 ").append(anchor).append(" 权限范围内的库存、出库/核销与采购入库侧结构化摘要。\n");
        sb.append("以下不作为全集团或多门店综合经营排名依据。\n\n");
        sb.append("【库房侧风险摘要】\n");

        boolean any = appendWarehousePurchaseStockEvidence(sb, plan);
        any |= appendRiskItemsForDomains(sb, plan, "PURCHASE", "STOCK_REDUCE");
        any |= appendMainFindingsFiltered(sb, plan, 4, true);

        if (!any) {
            sb.append("- 本轮在库房权限内可用的结构化采购/出库摘要较少；建议核对盘点差异、报损与入库单据是否在统计周期内完整同步。\n");
        }
        sb.append("\n若需营业额或菜品毛利视角，请在具备相应权限的岗位下提问。");
        return sb.toString().trim();
    }

    private static String renderPurchaserDowngradedBusinessDiagnosis(BusinessDiagnosisPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("【权限降级·采购视角】\n");
        sb.append("当前账号不包含营业额或菜品毛利结构化结论；以下为采购与出库/核销范围内的摘要（非全集团经营排名）。\n\n");
        sb.append("【采购侧风险摘要】\n");
        boolean any = appendWarehousePurchaseStockEvidence(sb, plan);
        any |= appendRiskItemsForDomains(sb, plan, "PURCHASE", "STOCK_REDUCE");
        any |= appendMainFindingsFiltered(sb, plan, 5, true);
        if (!any) {
            sb.append("- 本轮在采购权限内可用的结构化摘要较少；建议核对入库单据与核销分型是否在统计周期内完整同步。\n");
        }
        sb.append("\n如需营业额或菜品毛利视角，请在具备相应权限的岗位下提问。");
        return sb.toString().trim();
    }

    private static String renderDeliveryDowngradedBusinessDiagnosis(BusinessDiagnosisPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("【权限降级·配送侧视角】\n");
        sb.append("当前账号不包含营业额或菜品毛利分析权限；以下仅摘要配送侧可见的采购与库存流转相关信号（非综合经营诊断）。\n\n");
        sb.append("【配送侧可见摘要】\n");
        boolean any = appendWarehousePurchaseStockEvidence(sb, plan);
        any |= appendRiskItemsForDomains(sb, plan, "PURCHASE", "STOCK_REDUCE");
        any |= appendMainFindingsFiltered(sb, plan, 5, true);
        if (!any) {
            sb.append("- 本轮在权限内可用的结构化摘要较少；建议核对出库/核销与入库同步是否完整。\n");
        }
        sb.append("\n如需营业额或菜品毛利视角，请在具备相应权限的岗位下提问。");
        return sb.toString().trim();
    }

    private static boolean appendWarehousePurchaseStockEvidence(StringBuilder sb, BusinessDiagnosisPlan plan) {
        boolean any = false;
        BusinessDiagnosisPlan.SourceResultSummary srs = plan.getSourceResultSummary();
        if (srs != null && srs.getPurchase() != null && srs.getPurchase().getTotalAmount() != null) {
            sb.append("- 采购入库合计约 ")
                    .append(String.format(Locale.CHINA, "%.0f", srs.getPurchase().getTotalAmount()))
                    .append(" 元（来自采购概览工具）\n");
            any = true;
        }
        if (srs != null && srs.getStockReduce() != null && srs.getStockReduce().getTotalAmount() != null) {
            sb.append("- 出库/核销四类合计约 ")
                    .append(String.format(Locale.CHINA, "%.1f", srs.getStockReduce().getTotalAmount()))
                    .append(" 元（来自核销/出库汇总工具）\n");
            any = true;
        }
        return any;
    }

    private static boolean appendRiskItemsForDomains(StringBuilder sb, BusinessDiagnosisPlan plan, String... domains) {
        if (plan.getRiskItems() == null || domains == null || domains.length == 0) {
            return false;
        }
        boolean any = false;
        for (BusinessDiagnosisPlan.DiagnosisRiskItem ri : plan.getRiskItems()) {
            if (ri == null || ri.getDomain() == null) {
                continue;
            }
            String dom = ri.getDomain().trim();
            boolean match = false;
            for (String d : domains) {
                if (d != null && d.equalsIgnoreCase(dom)) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                continue;
            }
            if (!StringUtils.hasText(ri.getTitle())) {
                continue;
            }
            if (permissionDowngradeDiagnosisLineExcluded(ri.getTitle())
                    || (StringUtils.hasText(ri.getEvidence()) && permissionDowngradeDiagnosisLineExcluded(ri.getEvidence()))) {
                continue;
            }
            sb.append("- ").append(ri.getTitle().trim());
            if (StringUtils.hasText(ri.getEvidence())) {
                sb.append("（").append(ri.getEvidence().trim()).append("）");
            }
            sb.append("\n");
            any = true;
        }
        return any;
    }

    private static boolean appendMainFindingsFiltered(StringBuilder sb, BusinessDiagnosisPlan plan, int maxLines,
            boolean permissionDowngradeExtraFilter) {
        if (plan.getMainFindings() == null) {
            return false;
        }
        boolean any = false;
        int added = 0;
        for (String f : plan.getMainFindings()) {
            if (f == null || f.isBlank()) {
                continue;
            }
            if (warehouseFindingExcludedForWarehouseScope(f)) {
                continue;
            }
            if (permissionDowngradeExtraFilter && permissionDowngradeDiagnosisLineExcluded(f)) {
                continue;
            }
            sb.append("- ").append(f.trim()).append("\n");
            any = true;
            if (++added >= maxLines) {
                break;
            }
        }
        return any;
    }

    /** 降级渲染：剔除集团排行、营业额与菜品毛利口径等句式（与库房追问过滤对齐并稍加扩展）。 */
    private static boolean permissionDowngradeDiagnosisLineExcluded(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        if (warehouseFindingExcludedForWarehouseScope(raw)) {
            return true;
        }
        String s = raw.trim();
        return s.contains("全集团") || s.contains("集团排名") || s.contains("综合经营诊断")
                || s.contains("毛利率") || s.contains("毛利偏低") || s.contains("拖累毛利")
                || s.contains("销售额") || s.contains("营业额占比") || s.contains("菜品销售额");
    }
}
