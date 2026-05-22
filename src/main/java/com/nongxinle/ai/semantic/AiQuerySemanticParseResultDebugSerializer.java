package com.nongxinle.ai.semantic;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link AiQuerySemanticParseResult} 压成 Harness / 双跑 debug 可下发的 Map（无数据库 ID）。
 * 与 v1 摘要对齐，并补充四大 action、{@code isFollowUp}、{@code mentionedDishName}。
 */
public final class AiQuerySemanticParseResultDebugSerializer {

    private AiQuerySemanticParseResultDebugSerializer() {
    }

    public static Map<String, Object> toSafeMap(AiQuerySemanticParseResult r) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        if (r == null) {
            m.put("parseMissing", true);
            return m;
        }
        m.put("parseMissing", r.isParseMissing());
        m.put("isFollowUp", r.getFollowUp());
        m.put("intentAction", blankToNull(r.getIntentAction()));
        m.put("timeAction", blankToNull(r.getTimeAction()));
        m.put("scopeAction", blankToNull(r.getScopeAction()));
        m.put("metricAction", blankToNull(r.getMetricAction()));
        m.put("intent", blankToNull(r.getIntent()));
        m.put("domain", blankToNull(r.getSemanticDomain()));
        m.put("mentionedDishName", blankToNull(r.getMentionedDishName()));
        m.put("confidence", r.getConfidence());
        if (r.getTime() != null) {
            LinkedHashMap<String, Object> t = new LinkedHashMap<>();
            t.put("timeType", blankToNull(r.getTime().getTimeType()));
            t.put("startDate", blankToNull(r.getTime().getStartDate()));
            t.put("endDate", blankToNull(r.getTime().getEndDate()));
            t.put("timeSource", blankToNull(r.getTime().getTimeSource()));
            t.put("needInheritFromPrevious", r.getTime().getNeedInheritFromPrevious());
            t.put("reason", blankToNull(r.getTime().getReason()));
            m.put("time", t);
        } else {
            m.put("time", null);
        }
        if (r.getRequestedScope() != null) {
            LinkedHashMap<String, Object> rs = new LinkedHashMap<>();
            rs.put("requestedScopeType", blankToNull(r.getRequestedScope().getRequestedScopeType()));
            rs.put(
                    "mentionedStoreName",
                    AiQuerySemanticParseResult.sanitizeMentionedStoreNameToken(
                            r.getRequestedScope().getMentionedStoreName()));
            rs.put("mentionedStoreNames", emptyToNullCopy(r.getRequestedScope().getMentionedStoreNames()));
            rs.put("mentionedDepartmentName", blankToNull(r.getRequestedScope().getMentionedDepartmentName()));
            rs.put("mentionedWarehouseName", blankToNull(r.getRequestedScope().getMentionedWarehouseName()));
            rs.put("scopeSource", blankToNull(r.getRequestedScope().getScopeSource()));
            rs.put("needInheritFromPrevious", r.getRequestedScope().getNeedInheritFromPrevious());
            m.put("requestedScope", rs);
        } else {
            m.put("requestedScope", null);
        }
        if (r.getMetric() != null) {
            LinkedHashMap<String, Object> met = new LinkedHashMap<>();
            met.put("primaryMetric", blankToNull(r.getMetric().getPrimaryMetric()));
            met.put("rankingType", blankToNull(r.getMetric().getRankingType()));
            met.put("purchaseSourceType", blankToNull(r.getMetric().getPurchaseSourceType()));
            met.put("stockReduceType", blankToNull(r.getMetric().getStockReduceType()));
            m.put("metric", met);
        } else {
            m.put("metric", null);
        }
        if (r.getSemanticSlots() != null) {
            AiQuerySemanticParseResult.SemanticSlotsPart ss = r.getSemanticSlots();
            LinkedHashMap<String, Object> slot = new LinkedHashMap<>();
            slot.put("queryObject", blankToNull(ss.getQueryObject()));
            slot.put("operation", blankToNull(ss.getOperation()));
            slot.put("metric", blankToNull(ss.getMetric()));
            slot.put("sourceFacet", blankToNull(ss.getSourceFacet()));
            slot.put("anchorPolicy", blankToNull(ss.getAnchorPolicy()));
            slot.put("detailWanted", blankToNull(ss.getDetailWanted()));
            slot.put("structuredIntentDetailWire", blankToNull(ss.getStructuredIntentDetailWire()));
            slot.put("answerPlanType", blankToNull(ss.getAnswerPlanType()));
            m.put("semanticSlots", slot);
        } else {
            m.put("semanticSlots", null);
        }
        m.put("needClarification", r.getNeedClarification());
        m.put("clarificationQuestion", blankToNull(r.getClarificationQuestion()));
        m.put("reason", blankToNull(r.getReason()));
        m.put("multiTurnInheritanceTrace", r.getMultiTurnInheritanceTrace());
        if (r.getTime() != null) {
            m.put(
                    "inheritedTime",
                    SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS.equals(
                            blankToNull(r.getTime().getTimeSource())));
        }
        if (r.getRequestedScope() != null) {
            String ss = blankToNull(r.getRequestedScope().getScopeSource());
            m.put(
                    "inheritedScope",
                    "INHERITED_PREVIOUS".equals(ss)
                            || Boolean.TRUE.equals(r.getRequestedScope().getNeedInheritFromPrevious()));
        }
        if (r.getOrchestrationDecisionCandidate() != null) {
            var od = r.getOrchestrationDecisionCandidate();
            LinkedHashMap<String, Object> oc = new LinkedHashMap<>();
            oc.put("taskMode", blankToNull(od.getTaskMode()));
            oc.put("selectedAgents", emptyToNullCopy(od.getSelectedAgents()));
            oc.put("selectedTools", emptyToNullCopy(od.getSelectedTools()));
            oc.put("plannerRequired", od.getPlannerRequired());
            oc.put("multiAgentRequired", od.getMultiAgentRequired());
            oc.put("approvalRequired", od.getApprovalRequired());
            oc.put("clarificationRequired", od.getClarificationRequired());
            oc.put("clarificationQuestion", blankToNull(od.getClarificationQuestion()));
            oc.put("confidence", od.getConfidence());
            oc.put("reason", blankToNull(od.getReason()));
            m.put("orchestrationDecisionCandidate", oc);
        } else {
            m.put("orchestrationDecisionCandidate", null);
        }
        m.put("mentionedStoreNames", emptyToNullCopy(r.effectiveMentionedStoreNames()));
        m.put("purchaseSemanticFramePrimaryMerge", r.getPurchaseSemanticFramePrimaryMerge());
        m.put("promptRegistryId", blankToNull(r.getPromptRegistryId()));
        return m;
    }

    private static String blankToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private static List<String> emptyToNullCopy(List<String> in) {
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
}
