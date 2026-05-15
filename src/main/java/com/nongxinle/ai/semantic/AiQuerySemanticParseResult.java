package com.nongxinle.ai.semantic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Harness 入口「用户语义 LLM 解析」结果：仅限自然语言语义，不含任何需权限/组织树展开的数据库 ID。
 * <p>
 * SQL 范围 ID（如 queryStoreIds、expandedSqlDepartmentIds 等）必须由
 * {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} /
 * {@link com.nongxinle.ai.context.AiResolvedOrgScope} /
 * {@link com.nongxinle.ai.context.AiResolvedDataScope} 生成。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AiQuerySemanticParseResult {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimePart {
        private String timeType;
        private String startDate;
        private String endDate;
        private String timeSource;
        private Boolean needInheritFromPrevious;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestedScopePart {
        private String requestedScopeType;
        private String mentionedStoreName;
        /**
         * 用户一次点到多家门店（多店对比/并列问营收），口述名列表；不包含任何数据库 ID。
         */
        private List<String> mentionedStoreNames;
        private String mentionedDepartmentName;
        private String mentionedWarehouseName;
        private String scopeSource;
        private Boolean needInheritFromPrevious;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricPart {
        private String primaryMetric;
        private String rankingType;
        private String purchaseSourceType;
        private String stockReduceType;
    }

    /**
     * LLM 输出的编排候选（仅解析 JSON；Java 不据此猜测用户原文）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrchestrationDecisionCandidatePart {
        private String taskMode;
        /** 模型建议的子 Agent / 能力名（字符串列表，观测用） */
        private List<String> selectedAgents;
        private List<String> selectedTools;
        private Boolean plannerRequired;
        private Boolean multiAgentRequired;
        private Boolean approvalRequired;
        private Boolean clarificationRequired;
        private String clarificationQuestion;
        private Double confidence;
        private String reason;
    }

    private String intent;
    /** DISH_PROFIT/BUSINESS_DIAGNOSIS 等：用户在原文中点到的一道菜名称（口述，非 ID）。 */
    private String mentionedDishName;
    private Double confidence;
    /** 是否与上一轮同一对话主线（语义判断，不设 ID） */
    private Boolean followUp;
    /** NEW / INHERIT_PREVIOUS / OVERRIDE；缺省则由 Java 仅按既有 intent/time 字段合并 */
    private String intentAction;
    private String timeAction;
    private String scopeAction;
    private String metricAction;

    private TimePart time;
    private RequestedScopePart requestedScope;
    private MetricPart metric;

    private OrchestrationDecisionCandidatePart orchestrationDecisionCandidate;

    private Boolean needClarification;
    private String clarificationQuestion;
    private String reason;

    /** 模型原始 JSON 摘录（截断），仅用于排障；不落业务逻辑。 */
    private String rawJsonDigest;

    /**
     * 观测：语义解析 LLM 原始输出截断（当前仅 v2 {@link AiQuerySemanticLlmParser#parse} 填充）；不参与 merge / 路由。
     */
    private String observationLlmRawText;
    /**
     * 观测：JSON 未产出、无法抽取对象或解析失败时的简短原因码；不参与 merge。
     */
    private String observationJsonParseError;

    /**
     * Harness：本轮若实际走了语义解析 LLM，则为对应 {@link com.nongxinle.ai.prompt.AiPromptRegistry} promptId；
     * 开关关闭或未调用时为 null。
     */
    private String promptRegistryId;

    /** 未调用 LLM / 关闭开关 / 占位网关 / 解析失败。 */
    @Builder.Default
    private boolean parseMissing = true;

    /**
     * 解析未失败且置信度达阈（缺省置信度视为 0）；用于决定是否允许短路 Java keyword follow-up、以及语义合并门禁。
     */
    public boolean isStructuralConfidenceOk(double minConfidence) {
        if (parseMissing) {
            return false;
        }
        double c = confidence != null ? confidence : 0d;
        return c >= minConfidence;
    }

    /**
     * 是否允许跳过 {@link com.nongxinle.ai.conversation.AiFollowUpResolver} 的 Java 规则追问入口：
     * 置信度过关且至少有一项结构化路由信号（intent、各 action、或语义 followUp 标记）。
     */
    public boolean bypassesJavaKeywordFollowUpGate(double minConfidence) {
        if (!isStructuralConfidenceOk(minConfidence)) {
            return false;
        }
        if (Boolean.TRUE.equals(followUp)) {
            return true;
        }
        if (StringUtils.hasText(intentAction)
                || StringUtils.hasText(timeAction)
                || StringUtils.hasText(scopeAction)
                || StringUtils.hasText(metricAction)) {
            return true;
        }
        return StringUtils.hasText(intent);
    }

    /** 参与语义 scope/门店收窄等：置信度过关且有意向或口述范围/多轮动作信号。 */
    public boolean isUsableForMerge(double minConfidence) {
        if (!isStructuralConfidenceOk(minConfidence)) {
            return false;
        }
        if (StringUtils.hasText(intent)) {
            return true;
        }
        RequestedScopePart rs = requestedScope;
        if (rs != null && StringUtils.hasText(rs.getRequestedScopeType())) {
            return true;
        }
        if (!effectiveMentionedStoreNames().isEmpty()) {
            return true;
        }
        if (Boolean.TRUE.equals(followUp)) {
            return true;
        }
        if (StringUtils.hasText(intentAction)
                || StringUtils.hasText(timeAction)
                || StringUtils.hasText(scopeAction)
                || StringUtils.hasText(metricAction)) {
            return true;
        }
        return false;
    }

    /**
     * 合并 {@code requestedScope.mentionedStoreNames}（优先）与单字段 {@code mentionedStoreName}，去空白、去重、保序。
     */
    public List<String> effectiveMentionedStoreNames() {
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        RequestedScopePart rs = requestedScope;
        if (rs != null) {
            if (rs.getMentionedStoreNames() != null) {
                for (String n : rs.getMentionedStoreNames()) {
                    String t = sanitizeMentionedStoreNameToken(n);
                    if (t != null) {
                        dedup.add(t);
                    }
                }
            }
            String one = sanitizeMentionedStoreNameToken(rs.getMentionedStoreName());
            if (one != null) {
                dedup.add(one);
            }
        }
        return new ArrayList<>(dedup);
    }

    /** 口述店名：排除 null、空白、字面量 {@code "null"}（JSON/LLM 噪点）；不解析用户原文。 */
    public static String sanitizeMentionedStoreNameToken(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (!StringUtils.hasText(t)) {
            return null;
        }
        if ("null".equalsIgnoreCase(t)) {
            return null;
        }
        return t;
    }
}
