package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.cost.AiCostDiagnosisResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.security.AiPermissionGuard;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CostDiagnosisAgentNode implements AgentNode {

    private final AiSseEventPublisher publisher;
    private final AiPermissionGuard permissionGuard;

    @Override
    public String name() {
        return "CostDiagnosisAgent";
    }

    @Override
    public boolean shouldRun(AiRunState state) {
        return state.isCostInsightPath() && state.getDataPlanTools() != null && !state.getDataPlanTools().isEmpty();
    }

    @Override
    public AiRunState run(AiRunState state) {
        long rid = state.getRunId();

        var perm = permissionGuard.evaluateCostDiagnosisAgent(state);
        if (!perm.isAllowed()) {
            AiPermissionDenied denial = perm.getDenial();
            if (denial != null) {
                state.getPermissionDenials().add(denial);
            }
            LinkedHashMap<String, Object> ex = new LinkedHashMap<>();
            ex.put("agent", "CostDiagnosisAgent");
            String msg = denial != null ? denial.getReason() : "成本诊断无权限执行";
            publisher.publishError(rid,
                    msg,
                    "agent permission denied",
                    "TOOL_PERMISSION_DENIED",
                    "BusinessError",
                    ex,
                    denial);
            publisher.publish(rid, "agent_finished", Map.of(
                    "agent", "CostDiagnosisAgent",
                    "skipped", true,
                    "displayText", "成本诊断因权限不足已跳过",
                    "permissionDenied", denial != null ? denial.asDataMap() : Map.of()
            ));
            return state;
        }

        publisher.publish(rid, "agent_started", Map.of(
                "agent", "CostDiagnosisAgent",
                "displayText", "正在进行成本结构化诊断…"
        ));

        Map<String, Object> revD = section(state, AiBusinessToolIds.REVENUE_QUERY);
        Map<String, Object> purD = section(state, AiBusinessToolIds.PURCHASE_QUERY);
        Map<String, Object> stkD = section(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
        Map<String, Object> dishD = section(state, AiBusinessToolIds.DISH_SALES_QUERY);
        Map<String, Object> marginD = section(state, AiBusinessToolIds.GROSS_MARGIN_CALCULATOR);

        BigDecimal revenue = nz(revD.get("totalRevenue"));
        int days = revD.get("days") instanceof Number ? ((Number) revD.get("days")).intValue() : 0;
        BigDecimal purchase = nz(purD.get("purchaseSubTotal"));
        BigDecimal production = nz(stkD.get("productionTotal"));
        BigDecimal produce = nz(stkD.get("produceTotal"));
        BigDecimal waste = nz(stkD.get("wasteTotal"));
        BigDecimal loss = nz(stkD.get("lossTotal"));
        BigDecimal listRev = nz(dishD.get("listPriceRevenueTotal"));
        BigDecimal marginPctNumeric = nz(marginD.get("estimatedGrossMarginPercent"));
        boolean marginReliable = !(Boolean.FALSE.equals(marginD.get("grossMarginReliable")));
        String marginDisplayed = resolveMarginDisplay(marginD, marginReliable, marginPctNumeric);

        boolean mockHeavy = envMock(state, AiBusinessToolIds.REVENUE_QUERY)
                || envMock(state, AiBusinessToolIds.STOCK_REDUCE_QUERY)
                || envMock(state, AiBusinessToolIds.PURCHASE_QUERY);

        BigDecimal outbound123 = waste.add(loss).add(produce);
        BigDecimal wasteLossShare = outbound123.signum() == 0 ? BigDecimal.ZERO
                : waste.add(loss).multiply(BigDecimal.valueOf(100)).divide(outbound123, 2, RoundingMode.HALF_UP);

        List<Map<String, Object>> metrics = new ArrayList<>();
        metrics.add(AiCostDiagnosisResult.metric("统计天数", days, "天"));
        metrics.add(AiCostDiagnosisResult.metric("区间营业额(日营收汇总)", revenue.stripTrailingZeros().toPlainString(), "元"));
        metrics.add(AiCostDiagnosisResult.metric("菜品标价收入(区间汇总)", listRev.stripTrailingZeros().toPlainString(), "元"));
        metrics.add(AiCostDiagnosisResult.metric("采购额(入库汇总)", purchase.stripTrailingZeros().toPlainString(), "元"));
        metrics.add(AiCostDiagnosisResult.metric("核销-生产相关合计", outbound123.stripTrailingZeros().toPlainString(), "元"));
        metrics.add(AiCostDiagnosisResult.metric("损耗+废弃占比(相对1+2+3)",
                wasteLossShare.stripTrailingZeros().toPlainString() + "%", null));
        metrics.add(AiCostDiagnosisResult.metric("估算毛利率%",
                marginDisplayed, marginReliable ? null : "口径不完整仅供参考"));

        List<String> findings = new ArrayList<>();
        boolean linkageGap = purchase.compareTo(BigDecimal.ZERO) > 0 && outbound123.signum() == 0;
        boolean haveRevenue = revenue.signum() > 0 || listRev.signum() > 0;

        if (state.getDepartmentId() == null || state.getDistributerId() == null) {
            findings.add("缺少 departmentId/distributerId，部分数据源可能无法对齐业务库。");
        }
        if (revenue.signum() == 0 && listRev.signum() == 0) {
            findings.add("区间内未汇总到营业额/标价收入，需确认是否有日营收录入或菜品售价/销量维护。");
        }
        if (linkageGap) {
            findings.add("有采购发生但核销侧汇总偏低，可能存在入库与门店核销链路断点。");
        }
        if (!marginReliable && haveRevenue) {
            findings.add("核销/出库/生产消耗数据不足，毛利率仅能标记为「暂不可准确计算」，不可直接解读为高毛利。");
        }
        if (wasteLossShare.compareTo(BigDecimal.valueOf(20)) > 0) {
            findings.add("损耗与废弃在出库结构中占比较高（粗估口径），值得关注。");
        }
        if (marginReliable && marginPctNumeric.compareTo(BigDecimal.ZERO) == 0 && revenue.add(listRev).signum() > 0 && outbound123.signum() > 0) {
            findings.add("在存在收入与核销的情况下估算毛利率仍为 0，请核对计算器口径与各 Tool 返回值。");
        }
        if (mockHeavy) {
            findings.add("部分查询返回 mock/空数据集，本节结论需在数据齐全后复检。");
        }

        List<String> recs = new ArrayList<>();
        if (linkageGap || (!marginReliable && haveRevenue)) {
            recs.add("建议优先核对入库、核销、出库链路是否连续，避免仅有采购却无核销分摊。");
        }
        recs.add("核对本区间是否有完整的日营业额与采购入库数据。");
        recs.add("关注高损耗废弃菜品与库存盘点，优先排查 Top 菜品。");
        if (wasteLossShare.compareTo(BigDecimal.valueOf(15)) > 0) {
            recs.add("建议结合出库明细按员工班次/档口拆解损耗原因。");
        }

        boolean dataIncompleteRisk =
                linkageGap || (!marginReliable && haveRevenue && nz(marginD.get("basisRevenue")).signum() > 0);

        boolean highRisk = wasteLossShare.compareTo(BigDecimal.valueOf(25)) > 0
                || (marginReliable && marginPctNumeric.compareTo(BigDecimal.valueOf(25)) < 0 && haveRevenue);
        boolean warningRisk = !highRisk && (wasteLossShare.compareTo(BigDecimal.valueOf(15)) > 0
                || (marginReliable && marginPctNumeric.compareTo(BigDecimal.valueOf(40)) < 0 && haveRevenue));

        String risk;
        if (highRisk) {
            risk = "high";
        } else if (dataIncompleteRisk) {
            risk = "data_incomplete";
        } else if (warningRisk) {
            risk = "warning";
        } else {
            risk = "ok";
        }

        boolean needMore = revenue.signum() == 0 && listRev.signum() == 0 || state.getDepartmentId() == null;

        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        String subj = tw.getTimeSubjectText();
        String summary;
        if (needMore) {
            summary = subj + "，成本数据不完整，暂无法给出可靠结论；请关注营业额/菜品侧是否已录入。";
        } else if ("high".equals(risk)) {
            summary = subj + "，成本压力偏大：出库损耗结构或毛利率预警，建议尽快核查采购与核销明细。";
        } else if ("data_incomplete".equals(risk)) {
            summary = subj + "，暂未发现明显损耗异常，但由于核销/生产消耗数据不足，成本判断还不完整。建议优先核对入库、核销、出库链路。";
        } else if ("warning".equals(risk)) {
            summary = subj + "，成本存在改进空间，采购与核销/损耗需重点关注。";
        } else {
            summary = subj + "，在现有数据口径下未触发明显损耗或毛利异常告警，仍可继续核对核销完整性。";
        }

        AiCostDiagnosisResult diag = AiCostDiagnosisResult.builder()
                .agentName("CostDiagnosisAgent")
                .summary(summary)
                .riskLevel(risk)
                .keyMetrics(metrics)
                .findings(findings)
                .recommendations(recs)
                .needMoreData(needMore)
                .questions(needMore
                        ? List.of("请告知需要分析的门店 departmentId（父部门）以及分销商 distributerId 是否已选对？")
                        : List.of())
                .build();

        state.setCostDiagnosisResult(diag);

        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "CostDiagnosisAgent",
                "displayText", "结构化诊断已完成",
                "riskLevel", risk,
                "needMoreData", needMore
        ));
        return state;
    }

    /** 优先使用毛利 Tool 的展示字段；不可靠时禁止把「空/0」渲染成可解读的毛利率数值。 */
    private static String resolveMarginDisplay(Map<String, Object> marginD, boolean marginReliable, BigDecimal marginPctNumeric) {
        if (!marginReliable) {
            Object disp = marginD.get("estimatedGrossMarginPercentDisplay");
            if (disp != null && !disp.toString().isBlank()) {
                return disp.toString().trim();
            }
            return "毛利率暂不可准确计算（核销/出库/生产消耗数据不足）";
        }
        return marginPctNumeric.stripTrailingZeros().toPlainString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map)) {
            return Map.of();
        }
        Object data = ((Map<String, Object>) env).get("data");
        if (!(data instanceof Map)) {
            return Map.of();
        }
        return (Map<String, Object>) data;
    }

    private static BigDecimal nz(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        try {
            return new BigDecimal(v.toString().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean envMock(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map)) {
            return false;
        }
        return Boolean.TRUE.equals(((Map<String, Object>) env).get("mock"));
    }
}
