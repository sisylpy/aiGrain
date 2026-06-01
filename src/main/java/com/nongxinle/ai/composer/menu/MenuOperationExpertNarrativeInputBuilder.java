package com.nongxinle.ai.composer.menu;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单专家 LLM 用户消息：只含 {@code menuFactPack} 统计事实，不含 Java 优化方案/四象限/动作结论。
 */
public final class MenuOperationExpertNarrativeInputBuilder {

    private MenuOperationExpertNarrativeInputBuilder() {}

    public static String buildUserMessage(AiRunState state, MenuOperationAnswerPlan plan) {
        return JSON.toJSONString(buildInputEnvelope(state, plan));
    }

    public static Map<String, Object> buildInputEnvelope(AiRunState state, MenuOperationAnswerPlan plan) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("task", "menu_action_recommendation_presentation");
        envelope.put("userQuestion", resolveUserQuestion(state));
        envelope.put("scopeLabel", plan.getScopeLabel());
        envelope.put("timeLabel", plan.getTimeLabel());
        envelope.put("scopeTimeSummary", composeScopeTimeSummary(plan));
        envelope.put("planType", plan.getPlanType());
        envelope.put("menuFactPack", MenuExpertNarrativeFactPackBuilder.build(state, plan));

        MenuOptimizationPlan optimization = plan.getMenuOptimizationPlan();
        Map<String, Object> limits =
                optimization != null && optimization.getCapabilityLimits() != null
                        ? optimization.getCapabilityLimits()
                        : Map.of();
        String boundaryZh = MenuOperationCapabilityLimitsTextSupport.composeBoundaryNotice(limits);
        if (StringUtils.hasText(boundaryZh)) {
            envelope.put("capabilityBoundaryZh", boundaryZh.trim());
        }

        envelope.put("outputContract", buildOutputContract());
        envelope.put("hardRules", buildHardRules());
        return envelope;
    }

    private static String composeScopeTimeSummary(MenuOperationAnswerPlan plan) {
        if (plan == null) {
            return "";
        }
        String scope = StringUtils.hasText(plan.getScopeLabel()) ? plan.getScopeLabel().trim() : "";
        String time = StringUtils.hasText(plan.getTimeLabel()) ? plan.getTimeLabel().trim() : "";
        if (scope.isEmpty()) {
            return time;
        }
        if (time.isEmpty()) {
            return scope;
        }
        return scope + " · " + time;
    }

    private static String resolveUserQuestion(AiRunState state) {
        if (state == null) {
            return "";
        }
        if (StringUtils.hasText(state.getNormalizedUserInput())) {
            return state.getNormalizedUserInput().trim();
        }
        if (StringUtils.hasText(state.getRawUserInput())) {
            return state.getRawUserInput().trim();
        }
        return "";
    }

    private static Map<String, Object> buildOutputContract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("format", "JSON object MenuExpertPresentationPlan");
        contract.put(
                "fields",
                List.of(
                        "mainSummary",
                        "keyFindings[]",
                        "focusSections[]",
                        "nextSteps[]",
                        "supportingEvidence[]",
                        "capabilityBoundaryZh"));
        contract.put(
                "focusSectionFields",
                List.of("sectionTitle", "sectionSummary", "dishes[]", "suggestedAction", "reason"));
        contract.put(
                "dishFields",
                List.of(
                        "dishName",
                        "blendedGrossMarginRateOnListPrice",
                        "actualProfitAmount",
                        "suggestedAction",
                        "reason"));
        contract.put("responseRule", "只输出 JSON，不要 Markdown，不要解释文字");
        return contract;
    }

    private static List<String> buildHardRules() {
        return List.of(
                "只能基于 menuFactPack.dishRows 与汇总指标组织展示，不得新增菜品",
                "focusSections 只选 2-4 个最值得老板关注的重点，不要枚举 menuFactPack 中的全部菜品",
                "dishes[] 中的 dishName 必须来自 menuFactPack.dishRows",
                "dishes[] 若输出具体数字（如毛利率、利润额），必须与 dishRows 一致；不必为每道菜都填写数字",
                "不得把毛利低说成亏损，除非 dishRows 中 actualProfitAmount 明确为负数",
                "不得引用最新采购价、外部市场价、连续周期趋势、跨店排名，除非 menuFactPack 已提供对应数据",
                "不得直接建议下架，除非 dishRows 中有明显滞销且利润偏弱的依据",
                "不得输出内部开发态字段、英文系统代号、四象限分类名或 toolResults",
                "nextSteps 须基于 menuFactPack 中的菜品、数据标签与能力边界自行归纳；允许老板化表达；禁止新增事实包外菜品、指标、能力或未经支持的动作",
                "mainSummary、keyFindings、focusSections.reason、nextSteps 使用老板能直接看懂的中文，不要出现 type123、本轮中位、相对引流档、明星菜、潜力菜等工程/算法口径；排名与贡献率可放入 supportingEvidence");
    }
}
