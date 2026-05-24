package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteResult;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组装 {@link SemanticParserInput}（v2 LLM 用户消息 JSON），仅含脱敏字段，不包含任何数据库 ID。
 * <p>
 * 由 Resolver 调用 {@link AiQuerySemanticLlmParser#parse(SemanticParserInput)}。
 * <b>缺口</b>：`AiConversationTurnMemory` 未持久化多店点名列表，{@link SemanticParserPreviousTurn#getMentionedStoreNames()}
 * 恒为 {@code null}，仅 {@code mentionedStoreName}（来自 {@code lastMentionedStore}，必要时辅以 {@code lastFocusedStoreName}）。
 */
public final class SemanticParserInputBuilder {

    private SemanticParserInputBuilder() {
    }

    /**
     * @param normalizedUserMessage 与 Resolver 侧一致的清洗后问句（可为空串，由 {@link AiQuerySemanticLlmParser#parse} 再门禁）
     * @param today                 须与 {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver#resolve(Long, com.nongxinle.ai.platform.dto.AiRunCreateRequest, com.nongxinle.ai.context.AiUserContext, LocalDate)}
     *                              的 {@code today} 或 Harness Replay 的 {@code frozenClockDate} 解析结果一致；不得为 null
     * @param previousTurn          可为 null（首轮）
     * @param orgScope              可为 null；非 null 时从 {@link AiResolvedOrgScope#getVisibleStores()} 仅取店名
     */
    /**
     * FollowUp rewrite 已补全问句时：仅保留 time/scope/锚点摘要供 v2 输入，去掉易污染 completed 问句的 path/wire/slots。
     */
    public static AiConversationTurnMemory reducePreviousTurnForFollowUpRewrite(AiConversationTurnMemory mem) {
        if (mem == null) {
            return null;
        }
        return AiConversationTurnMemory.builder()
                .conversationId(mem.getConversationId())
                .previousRunId(mem.getPreviousRunId())
                .lastStartDate(mem.getLastStartDate())
                .lastEndDate(mem.getLastEndDate())
                .lastTimeLabel(mem.getLastTimeLabel())
                .lastScopeType(mem.getLastScopeType())
                .lastVisibleStoreIds(mem.getLastVisibleStoreIds())
                .lastFocusedStoreId(mem.getLastFocusedStoreId())
                .lastFocusedStoreName(mem.getLastFocusedStoreName())
                .lastMentionedStore(mem.getLastMentionedStore())
                .lastMentionedDishName(mem.getLastMentionedDishName())
                .lastHarnessMultiStoreMatchedStores(mem.getLastHarnessMultiStoreMatchedStores())
                .lastResultAnchors(mem.getLastResultAnchors())
                .lastEffectiveScopeSource(mem.getLastEffectiveScopeSource())
                .lastEffectiveQuestion(mem.getLastEffectiveQuestion())
                .build();
    }

    public static SemanticParserInput build(
            String normalizedUserMessage,
            LocalDate today,
            AiConversationTurnMemory previousTurn,
            AiResolvedOrgScope orgScope) {
        return build(normalizedUserMessage, today, previousTurn, orgScope, null, null);
    }

    public static SemanticParserInput build(
            String normalizedUserMessage,
            LocalDate today,
            AiConversationTurnMemory previousTurn,
            AiResolvedOrgScope orgScope,
            SemanticDomainRouteResult domainRoute,
            DomainContractSelectionResult contractSelection) {
        if (today == null) {
            throw new IllegalArgumentException("today must not be null");
        }
        String msg = normalizedUserMessage == null ? "" : normalizedUserMessage.trim();
        SemanticParserInput.SemanticParserInputBuilder b =
                SemanticParserInput.builder()
                        .currentUserMessage(msg)
                        .today(today.toString())
                        .previousTurn(mapPreviousTurn(previousTurn))
                        .visibleStores(mapVisibleStores(orgScope));
        if (domainRoute != null) {
            b.semanticRoute(mapSemanticRoute(domainRoute));
        }
        if (contractSelection != null && contractSelection.getParserAllowedOutputContract() != null) {
            b.allowedOutputContract(contractSelection.getParserAllowedOutputContract());
        }
        return b.build();
    }

    /**
     * 双跑 / Harness 观测用：仅含 currentUserMessage、today、previousTurn 文本摘要、visibleStores（每项仅 storeName），无 ID。
     */
    public static Map<String, Object> toDebugPreview(SemanticParserInput input) {
        if (input == null) {
            return null;
        }
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("currentUserMessage", input.getCurrentUserMessage());
        root.put("today", input.getToday());
        SemanticParserPreviousTurn pt = input.getPreviousTurn();
        if (pt == null) {
            root.put("previousTurn", null);
        } else {
            LinkedHashMap<String, Object> p = new LinkedHashMap<>();
            p.put("intentCode", pt.getIntentCode());
            p.put("pathCode", pt.getPathCode());
            p.put("structuredIntentDetail", pt.getStructuredIntentDetail());
            p.put("purchaseSourceType", pt.getPurchaseSourceType());
            p.put("timeLabel", pt.getTimeLabel());
            p.put("startDate", pt.getStartDate());
            p.put("endDate", pt.getEndDate());
            p.put("scopeType", pt.getScopeType());
            p.put("mentionedStoreName", pt.getMentionedStoreName());
            p.put("mentionedStoreNames", pt.getMentionedStoreNames());
            p.put("mentionedDishName", pt.getMentionedDishName());
            if (pt.getSemanticSlots() != null) {
                LinkedHashMap<String, Object> ss = new LinkedHashMap<>();
                var ssv = pt.getSemanticSlots();
                ss.put("queryObject", blankDbg(ssv.getQueryObject()));
                ss.put("operation", blankDbg(ssv.getOperation()));
                ss.put("metric", blankDbg(ssv.getMetric()));
                ss.put("sourceFacet", blankDbg(ssv.getSourceFacet()));
                ss.put("anchorPolicy", blankDbg(ssv.getAnchorPolicy()));
                p.put("semanticSlots", ss);
            } else {
                p.put("semanticSlots", null);
            }
            p.put("resultAnchorsSummary", pt.getResultAnchorsSummary());
            root.put("previousTurn", p);
        }
        List<Map<String, String>> vis = new ArrayList<>();
        if (input.getVisibleStores() != null) {
            for (SemanticParserVisibleStore vs : input.getVisibleStores()) {
                if (vs == null || !StringUtils.hasText(vs.getStoreName())) {
                    continue;
                }
                LinkedHashMap<String, String> row = new LinkedHashMap<>();
                row.put("storeName", vs.getStoreName().trim());
                vis.add(row);
            }
        }
        root.put("visibleStores", vis);
        if (input.getSemanticRoute() != null) {
            LinkedHashMap<String, Object> route = new LinkedHashMap<>();
            SemanticParserIntakeRouteInput sr = input.getSemanticRoute();
            route.put("primaryDomain", sr.getPrimaryDomain());
            route.put("candidateDomains", sr.getCandidateDomains());
            route.put("routeType", sr.getRouteType());
            route.put("confidence", sr.getConfidence());
            root.put("semanticRoute", route);
        } else {
            root.put("semanticRoute", null);
        }
        if (input.getAllowedOutputContract() != null) {
            root.put("allowedOutputContract", mapAllowedOutputContract(input.getAllowedOutputContract()));
        } else {
            root.put("allowedOutputContract", null);
        }
        return root;
    }

    private static LinkedHashMap<String, Object> mapAllowedOutputContract(
            SemanticParserAllowedOutputContract contract) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("selectedDomain", contract.getSelectedDomain());
        if (contract.getAllowedContracts() != null) {
            List<LinkedHashMap<String, Object>> entries = new ArrayList<>();
            for (SemanticParserAllowedOutputContract.AllowedContractEntry e : contract.getAllowedContracts()) {
                if (e == null) {
                    continue;
                }
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("contractId", e.getContractId());
                row.put("wire", e.getWire());
                row.put("queryObject", e.getQueryObject());
                row.put("queryObjects", e.getQueryObjects());
                row.put("operation", e.getOperation());
                row.put("operations", e.getOperations());
                row.put("metric", e.getMetric());
                row.put("metrics", e.getMetrics());
                row.put("sourceFacet", e.getSourceFacet());
                row.put("detailWanted", e.getDetailWanted());
                row.put("answerPlanType", e.getAnswerPlanType());
                row.put("requiresAnchor", e.getRequiresAnchor());
                row.put("anchorType", e.getAnchorType());
                row.put("selectedTools", e.getSelectedTools());
                row.put("description", e.getDescription());
                row.put("examples", e.getExamples());
                entries.add(row);
            }
            m.put("allowedContracts", entries);
        }
        m.put("allowedWires", contract.getAllowedWires());
        m.put("allowedQueryObjects", contract.getAllowedQueryObjects());
        m.put("allowedOperations", contract.getAllowedOperations());
        m.put("allowedMetrics", contract.getAllowedMetrics());
        m.put("allowedSourceFacets", contract.getAllowedSourceFacets());
        m.put("allowedDetailWanted", contract.getAllowedDetailWanted());
        m.put("allowedAnswerPlanTypes", contract.getAllowedAnswerPlanTypes());
        return m;
    }

    private static SemanticParserIntakeRouteInput mapSemanticRoute(SemanticDomainRouteResult route) {
        return SemanticParserIntakeRouteInput.builder()
                .primaryDomain(route.getPrimaryDomain())
                .candidateDomains(route.getCandidateDomains())
                .routeType(route.getRouteType() != null ? route.getRouteType().name() : null)
                .confidence(route.getConfidence())
                .build();
    }

    private static SemanticParserPreviousTurn mapPreviousTurn(AiConversationTurnMemory mem) {
        if (mem == null) {
            return null;
        }
        String mentionedStore = trimToNull(mem.getLastMentionedStore());
        if (mentionedStore == null) {
            mentionedStore = trimToNull(mem.getLastFocusedStoreName());
        }
        return SemanticParserPreviousTurn.builder()
                .intentCode(trimToNull(mem.getLastIntentCode()))
                .pathCode(trimToNull(mem.getLastPathCode()))
                .structuredIntentDetail(trimToNull(mem.getLastStructuredIntentDetail()))
                .purchaseSourceType(trimToNull(mem.getLastPurchaseSourceType()))
                .timeLabel(trimToNull(mem.getLastTimeLabel()))
                .startDate(trimToNull(mem.getLastStartDate()))
                .endDate(trimToNull(mem.getLastEndDate()))
                .scopeType(trimToNull(mem.getLastScopeType()))
                .mentionedStoreName(mentionedStore)
                .mentionedStoreNames(null)
                .mentionedDishName(trimToNull(mem.getLastMentionedDishName()))
                .semanticSlots(mem.getLastSemanticSlots())
                .resultAnchorsSummary(summarizeResultAnchorsForSemanticParser(mem.getLastResultAnchors()))
                .build();
    }

    private static String blankDbg(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String summarizeResultAnchorsForSemanticParser(List<AiResultAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (AiResultAnchor a : anchors) {
            if (a == null) {
                continue;
            }
            String et = trimToNull(a.getEntityType());
            String nm = trimToNull(a.getEntityName());
            if (!StringUtils.hasText(nm)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            String id = trimToNull(a.getEntityId());
            sb.append(et != null ? et : "?")
                    .append("#")
                    .append(id != null ? id : "?")
                    .append(": ")
                    .append(nm);
            if (StringUtils.hasText(a.getSourcePlanType())) {
                sb.append(" [").append(a.getSourcePlanType().trim()).append("]");
            }
            Integer rk = a.getRank();
            if (rk != null) {
                sb.append(" (rank=").append(rk).append(')');
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static List<SemanticParserVisibleStore> mapVisibleStores(AiResolvedOrgScope orgScope) {
        List<SemanticParserVisibleStore> out = new ArrayList<>();
        if (orgScope == null || orgScope.getVisibleStores() == null) {
            return out;
        }
        for (AiStoreScopeDTO s : orgScope.getVisibleStores()) {
            if (s == null) {
                continue;
            }
            String name = s.getStoreName();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            out.add(SemanticParserVisibleStore.builder().storeName(name.trim()).build());
        }
        return out;
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
