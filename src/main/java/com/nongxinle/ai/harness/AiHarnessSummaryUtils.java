package com.nongxinle.ai.harness;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.alibaba.fastjson2.JSON;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Harness 摘要层共享工具（blank/null 规范化、列表拷贝、工具信封探测等）。
 */
final class AiHarnessSummaryUtils {

    private AiHarnessSummaryUtils() {
    }

    static String blankToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    static String stringifyHarnessDbg(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    static String harnessEntityIdString(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.toString();
        }
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    static Map<String, Object> jsonDeepCopyMap(Map<String, Object> in) {
        if (in == null || in.isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(JSON.toJSONString(in), Map.class);
        } catch (Exception ex) {
            return new LinkedHashMap<>(in);
        }
    }

    /**
     * Harness 观测用：保留 V2 输入上下文，省略 {@code allowedOutputContract.allowedContracts} 全量条目
     * （与 {@code domainContractSelection} / {@code semanticContractValidation} 重复且体积大）。
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> compactQuerySemanticV2InputPreview(Map<String, Object> in) {
        Map<String, Object> copy = jsonDeepCopyMap(in);
        if (copy == null) {
            return null;
        }
        Object contractObj = copy.get("allowedOutputContract");
        if (!(contractObj instanceof Map<?, ?> contractRaw)) {
            return copy;
        }
        LinkedHashMap<String, Object> compactContract = new LinkedHashMap<>((Map<String, Object>) contractRaw);
        Object entries = compactContract.remove("allowedContracts");
        List<String> contractIds = new ArrayList<>();
        if (entries instanceof List<?> list) {
            for (Object row : list) {
                if (!(row instanceof Map<?, ?> m)) {
                    continue;
                }
                Object id = m.get("contractId");
                if (id != null && StringUtils.hasText(id.toString())) {
                    contractIds.add(id.toString().trim());
                }
            }
        }
        compactContract.put("allowedContractIds", contractIds.isEmpty() ? null : contractIds);
        compactContract.put("allowedContractCount", contractIds.size());
        copy.put("allowedOutputContract", compactContract);
        return copy;
    }

    static List<Long> longList(List<Long> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(in);
    }

    static List<Integer> intList(List<Integer> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(in);
    }

    static List<String> emptyToNullCopy(List<String> in) {
        if (in == null || in.isEmpty()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (String s : in) {
            String t = AiQuerySemanticParseResult.sanitizeMentionedStoreNameToken(s);
            if (t != null) {
                out.add(t);
            }
        }
        return out.isEmpty() ? null : out;
    }

    static List<String> stringListFromDebugList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<String> acc = new ArrayList<>();
        for (Object x : list) {
            if (x == null) {
                continue;
            }
            String s = x.toString().trim();
            if (StringUtils.hasText(s)) {
                acc.add(s);
            }
        }
        return acc.isEmpty() ? null : acc;
    }

    static String plainOrEmpty(String s) {
        return s == null ? "" : s;
    }

    static boolean harnessNonBlankish(Object v) {
        return v != null && StringUtils.hasText(v.toString().trim());
    }

    static boolean harnessTimeExplicitForSummary(AiResolvedQueryContext ctx, AiResolvedTimeWindow tw) {
        return tw != null && tw.isExplicitTimeMentioned();
    }

    static boolean harnessToolEnvelopeSuccess(AiRunState state, String toolKey) {
        if (state.getToolResults() == null || toolKey == null) {
            return false;
        }
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map<?, ?> map)) {
            return false;
        }
        return Boolean.TRUE.equals(map.get("success"));
    }

    /**
     * 经营概览 MULTI_AGENT：usedTools 优先列出四专线中 success=true 的工具；尚无成功时仍可回退到已编排的四域工具 id，
     * 避免 harness 上出现旧默认链路的「计划即 used」误判。
     */
    static List<String> resolveHarnessUsedTools(AiRunState state,
            AiResolvedQueryContext rq,
            List<String> allPlanned) {
        if (allPlanned == null) {
            return null;
        }
        if (state == null || !state.isBusinessOverviewPath()) {
            return new ArrayList<>(allPlanned);
        }
        String tm = rq != null ? rq.getOrchestrationTaskMode() : null;
        boolean multi = (tm != null && "MULTI_AGENT".equalsIgnoreCase(tm.trim()))
                || Boolean.TRUE.equals(rq != null ? rq.getOrchestrationMultiAgentRequired() : null);
        if (!multi) {
            return new ArrayList<>(allPlanned);
        }
        List<String> orderedSuccess = new ArrayList<>();
        for (String domainId : AiBusinessToolIds.BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS) {
            if (allPlanned.contains(domainId) && harnessToolEnvelopeSuccess(state, domainId)) {
                orderedSuccess.add(domainId);
            }
        }
        if (!orderedSuccess.isEmpty()) {
            return orderedSuccess;
        }
        List<String> plannedDomainOnly = new ArrayList<>();
        for (String domainId : AiBusinessToolIds.BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS) {
            if (allPlanned.contains(domainId)) {
                plannedDomainOnly.add(domainId);
            }
        }
        return plannedDomainOnly.isEmpty() ? new ArrayList<>(allPlanned) : plannedDomainOnly;
    }
}
