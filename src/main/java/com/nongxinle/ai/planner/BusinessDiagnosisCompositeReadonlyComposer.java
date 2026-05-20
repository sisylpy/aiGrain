package com.nongxinle.ai.planner;

import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlan;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeComposeResult;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeRiskLevel;
import com.nongxinle.ai.dto.business.BusinessDiagnosisDomainCoverage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * C-51：只读 {@link BusinessDiagnosisCompositeAnswerPlan} 的最小 Composer；不调 LLM、不读 toolResults、不改写 Builder 已物化事实。
 *
 * <p><b>旁路边界</b>：仅生成 BusinessDiagnosisComposite
 * {@link BusinessDiagnosisCompositeExecutionMode#SHADOW} /
 * {@link BusinessDiagnosisCompositeExecutionMode#HARNESS_ONLY} 旁路观测用的<strong>只读摘要</strong>；
 * <strong>不写</strong>、<strong>不替换</strong> {@link com.nongxinle.ai.core.AiRunState#getFinalAnswerText()}；
 * <strong>不属于</strong> Master Graph 主回答链；<strong>不负责</strong>生产用户正文。
 * {@link BusinessDiagnosisCompositeExecutionMode#PRIMARY} 为预留/未接生产主链。</p>
 *
 * @see com.nongxinle.ai.planner.BusinessDiagnosisCompositeAnswerPlanBuilder
 */
public final class BusinessDiagnosisCompositeReadonlyComposer {

    public static final String COMPOSER_VERSION = "C-51_READONLY_COMPOSER";

    /** Harness / 调试：建议追问条数上限（去重后截断）。 */
    private static final int MAX_SUGGESTED_QUESTIONS = 24;

    private BusinessDiagnosisCompositeReadonlyComposer() {
    }

    public static BusinessDiagnosisCompositeComposeResult compose(BusinessDiagnosisCompositeAnswerPlan plan) {
        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("composerVersion", COMPOSER_VERSION);

        if (plan == null) {
            debug.put("note", "null_plan");
            return BusinessDiagnosisCompositeComposeResult.builder()
                    .finalAnswerText("当前缺少经营诊断结构化结果，无法生成回答。")
                    .suggestedNextQuestions(List.of())
                    .answerPlanType(null)
                    .debug(debug)
                    .build();
        }

        List<String> questions = dedupeLimit(plan.getSuggestedNextQuestions());
        String finalText = buildFinalAnswerText(plan, debug);
        if (anyCoverageFailed(plan) && !textImpliesDataIncomplete(finalText)) {
            finalText =
                    finalText
                            + "\n\n说明：部分数据域未完整读取，请结合数据覆盖情况理解以下表述。";
        }

        return BusinessDiagnosisCompositeComposeResult.builder()
                .finalAnswerText(finalText)
                .suggestedNextQuestions(questions)
                .riskLevel(plan.getRiskLevel())
                .scopeLabel(plan.getScopeLabel())
                .timeLabel(plan.getTimeLabel())
                .answerPlanType(plan.getType())
                .debug(debug)
                .build();
    }

    private static String buildFinalAnswerText(BusinessDiagnosisCompositeAnswerPlan plan, Map<String, Object> debug) {
        String summary = plan.getSummaryText();
        if (summary != null && !summary.trim().isEmpty()) {
            debug.put("source", "summaryText");
            return summary.trim();
        }

        debug.put("source", "fallback_conservative");
        return buildFallbackText(plan);
    }

    /** 仅在 summaryText 为空时使用；不引入新数字；不输出「经营正常」类表述。 */
    private static String buildFallbackText(BusinessDiagnosisCompositeAnswerPlan plan) {
        String scope = blankToDefault(plan.getScopeLabel(), "组织范围未标注");
        String time = blankToDefault(plan.getTimeLabel(), "时间范围未标注");
        BusinessDiagnosisCompositeRiskLevel risk = plan.getRiskLevel();
        String riskLine =
                risk != null
                        ? ("风险档位：" + risk.name() + "。")
                        : "风险档位：未标注。";

        boolean incomplete = anyCoverageFailed(plan);
        StringBuilder sb = new StringBuilder();
        sb.append("范围：").append(scope).append("；时间：").append(time).append("\n");
        sb.append(riskLine);
        if (incomplete) {
            sb.append("部分数据域未完整读取，以下仅基于可用信息做概括，不提供确定性经营结论。\n");
        } else if (plan.getRiskLevel() == BusinessDiagnosisCompositeRiskLevel.INSUFFICIENT_DATA) {
            sb.append("当前数据不足以给出确定性经营结论。\n");
        }

        List<String> findings = plan.getKeyFindings();
        if (findings != null && !findings.isEmpty()) {
            sb.append("要点：\n");
            for (String f : findings) {
                if (f == null || f.isBlank()) {
                    continue;
                }
                sb.append("- ").append(f.trim()).append("\n");
            }
        } else {
            sb.append("（无可列要点；详情见结构化诊断计划。）\n");
        }

        // GROUP 安全：主语完全采用 Builder 下发的 scopeLabel，不在此改为单店口径。
        if (looksLikeGroupScopeLabel(scope)) {
            sb.append("\n（集团/多店口径：以上范围描述以结构化计划为准，未将汇总改写为单店表述。）");
        }

        String out = sb.toString().trim();
        if (!incomplete
                && !containsForbiddenOkPhrase(out)
                && plan.getRiskLevel() != BusinessDiagnosisCompositeRiskLevel.INSUFFICIENT_DATA) {
            out = out + "\n\n当前未触发需单独强调的结论性表述；追问建议见 suggestedNextQuestions（模板/澄清用，非生产智能建议）。";
        }
        return out;
    }

    private static boolean anyCoverageFailed(BusinessDiagnosisCompositeAnswerPlan plan) {
        List<BusinessDiagnosisDomainCoverage> cov = plan.getDataCoverage();
        if (cov == null) {
            return false;
        }
        for (BusinessDiagnosisDomainCoverage c : cov) {
            if (c != null && !c.isSuccess()) {
                return true;
            }
        }
        return false;
    }

    private static boolean textImpliesDataIncomplete(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains("未完整")
                || text.contains("不完整读取")
                || text.contains("数据不足")
                || text.contains("未读取")
                || text.contains("无法读取")
                || text.contains("不可用")
                || text.contains("缺失")
                || text.contains("未完整读取");
    }

    private static boolean looksLikeGroupScopeLabel(String scopeLabel) {
        if (scopeLabel == null || scopeLabel.isBlank()) {
            return false;
        }
        String s = scopeLabel.toLowerCase(Locale.ROOT);
        return s.contains("group")
                || scopeLabel.contains("集团")
                || scopeLabel.contains("当前可见门店")
                || scopeLabel.contains("可见门店")
                || scopeLabel.contains("多门店")
                || scopeLabel.contains("全部门店")
                || scopeLabel.contains("、")
                        && (scopeLabel.contains("餐厅") || scopeLabel.contains("店"));
    }

    private static boolean containsForbiddenOkPhrase(String text) {
        if (text == null) {
            return false;
        }
        return text.contains("经营正常")
                || text.contains("没问题")
                || text.contains("一切正常")
                || text.contains("无需担心");
    }

    private static String blankToDefault(String v, String def) {
        if (v == null || v.trim().isEmpty()) {
            return def;
        }
        return v.trim();
    }

    private static List<String> dedupeLimit(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String q : raw) {
            if (q == null) {
                continue;
            }
            String t = q.trim();
            if (t.isEmpty()) {
                continue;
            }
            String key = t.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                out.add(t);
                if (out.size() >= MAX_SUGGESTED_QUESTIONS) {
                    break;
                }
            }
        }
        return out;
    }
}
