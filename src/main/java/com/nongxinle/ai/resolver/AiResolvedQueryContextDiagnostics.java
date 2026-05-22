package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolver;
import com.nongxinle.ai.followup.AiFollowUpHintSupport;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.SemanticParseFallbackPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Resolver 诊断：V2 未采纳原因、澄清句、pipeline 日志（不改 reason 字符串与触发条件）。
 */
@Slf4j
@Component
public class AiResolvedQueryContextDiagnostics {

    public String explainV2NonAdoption(AiQuerySemanticParseResult v2, double querySemanticMinConfidence) {
        if (v2 == null) {
            return "v2_null";
        }
        if (SemanticParseFallbackPolicy.needSemanticParseClarification(v2, querySemanticMinConfidence)) {
            if (v2.isParseMissing()) {
                String err = v2.getObservationJsonParseError();
                return StringUtils.hasText(err) ? "v2_parse_missing:" + err : "v2_parse_missing";
            }
            if (!v2.isStructuralConfidenceOk(querySemanticMinConfidence)) {
                return "v2_low_confidence";
            }
            if (Boolean.TRUE.equals(v2.getNeedClarification())) {
                return "v2_need_clarification";
            }
            return "v2_unreliable";
        }
        return "v2_no_routable_path";
    }

    public static String resolveSemanticClarificationQuestion(AiQuerySemanticParseResult semanticLlm) {
        if (semanticLlm != null
                && Boolean.TRUE.equals(semanticLlm.getNeedClarification())
                && StringUtils.hasText(semanticLlm.getClarificationQuestion())) {
            return semanticLlm.getClarificationQuestion().trim();
        }
        return SemanticParseFallbackPolicy.clarificationQuestion();
    }

    public void logResolveStart(
            Long runId,
            Long convId,
            Long uid,
            String normalized,
            AiConversationTurnMemory previousTurn) {
        if (!log.isInfoEnabled()) {
            return;
        }
        log.info(
                "[AiFollowUpContext] resolve start runId={} conversationId={} userId={} messageSnippet={} "
                        + "previousTurnLoaded={} prevPathCode={}",
                runId,
                convId,
                uid,
                normalized.length() > 80 ? normalized.substring(0, 80) + "…" : normalized,
                previousTurn != null,
                previousTurn != null ? previousTurn.getLastPathCode() : null);
    }

    public void logIntentResolutionDiagnostics(
            Long runId,
            Long conversationId,
            String rawMessage,
            AiConversationTurnMemory previousTurn,
            AiResolvedQueryIntent currentIntentProbe,
            com.nongxinle.ai.conversation.AiFollowUpResolution followUp,
            AiResolvedQueryContext ctx) {
        if (!log.isInfoEnabled()) {
            return;
        }
        var fur = followUp;
        var probe = currentIntentProbe != null ? currentIntentProbe : AiResolvedQueryIntent.builder().build();
        log.info(
                "[AiFollowUpContext] intentRouting runId={} conversationId={} rawMessageSnippet={} "
                        + "currentIntentCode={} currentPathCode={} previousTurn.intentCode={} previousTurn.pathCode={} "
                        + "followUp={} inheritIntent={} effectiveIntentCode={} effectivePathCode={} effectiveIntentSource={}",
                runId,
                conversationId,
                rawMessage == null ? "" : (rawMessage.length() > 120 ? rawMessage.substring(0, 120) + "…" : rawMessage),
                probe.getIntentCode(),
                probe.getPathCode(),
                previousTurn != null ? previousTurn.getLastIntentCode() : null,
                previousTurn != null ? previousTurn.getLastPathCode() : null,
                fur != null && fur.isFollowUp(),
                fur != null && fur.isInheritIntent(),
                ctx.getEffectiveIntentCode(),
                ctx.getEffectivePathCode(),
                ctx.getEffectiveIntentSource());
    }

    public void logFollowUpDiagnostics(
            Long runId,
            Long conversationId,
            AiConversationTurnMemory previousTurn,
            com.nongxinle.ai.conversation.AiFollowUpResolution followUp,
            AiResolvedQueryContext ctx) {
        if (!log.isInfoEnabled()) {
            return;
        }
        var tw = ctx.getTimeWindow();
        var fur = followUp;
        var ds = ctx.getDataScope();
        boolean storeFu = fur != null && "STORE_SCOPE_FOLLOW_UP".equals(fur.getFollowUpType());
        log.info(
                "[AiFollowUpContext] runId={} conversationId={} previousTurnPresent={} prevIntentCode={} prevPathCode={} "
                        + "prevTimeWindow={}..{} prevTimeLabel={} "
                        + "followUp={} followUpType={} inheritIntent={} inheritTimeWindow={} inheritOrgScope={} "
                        + "timeLabel={} startDate={} endDate={} "
                        + "effectiveIntentCode={} effectivePathCode={} effectiveTimeWindowSource={} effectiveScopeSource={} "
                        + "effectiveIntentSource={}",
                runId,
                conversationId,
                previousTurn != null,
                previousTurn != null ? previousTurn.getLastIntentCode() : null,
                previousTurn != null ? previousTurn.getLastPathCode() : null,
                previousTurn != null ? previousTurn.getLastStartDate() : null,
                previousTurn != null ? previousTurn.getLastEndDate() : null,
                previousTurn != null ? previousTurn.getLastTimeLabel() : null,
                fur != null && fur.isFollowUp(),
                fur != null ? fur.getFollowUpType() : null,
                fur != null && fur.isInheritIntent(),
                fur != null && fur.isInheritTimeWindow(),
                fur != null && fur.isInheritOrgScope(),
                tw != null ? tw.getTimeLabel() : null,
                tw != null ? tw.getStartDate() : null,
                tw != null ? tw.getEndDate() : null,
                ctx.getEffectiveIntentCode(),
                ctx.getEffectivePathCode(),
                ctx.getEffectiveTimeWindowSource(),
                ctx.getEffectiveScopeSource(),
                ctx.getEffectiveIntentSource());
        if (storeFu && fur != null) {
            log.info(
                    "[AiFollowUpContext] STORE_SCOPE_FOLLOW_UP runId={} conversationId={} "
                            + "currentMentionedStoreName={} matchedStoreDepartmentId={} "
                            + "inheritIntent=true inheritPath=true inheritTimeWindow=true overrideScope=true "
                            + "effectiveScopeSource={} expandedSqlDepartmentIds={}",
                    runId,
                    conversationId,
                    fur.getStoreScopeFollowUpMentionedName(),
                    fur.getStoreScopeFollowUpMatchedStoreRootId(),
                    ctx.getEffectiveScopeSource(),
                    ds != null ? ds.getEffectiveSqlDepartmentIds() : null);
        }
    }

    public void logResolvedContextPipeline(
            Long runId,
            Long conversationId,
            String rawMessage,
            AiConversationTurnMemory previousTurn,
            AiResolvedOrgScope permissionBaselineOrg,
            AiResolvedQueryIntent mergedIntentStemForLog,
            boolean currentExplicitTimeMentioned,
            com.nongxinle.ai.conversation.AiFollowUpResolution followUp,
            AiResolvedQueryContext ctx) {
        if (!log.isInfoEnabled()) {
            return;
        }
        var cur = mergedIntentStemForLog;
        var tw = ctx.getTimeWindow();
        var effOrg = ctx.getOrgScope();
        var qi = ctx.getQueryIntent();
        String prevTw = null;
        String prevStores = null;
        if (previousTurn != null) {
            prevTw = (previousTurn.getLastStartDate() != null ? previousTurn.getLastStartDate() : "")
                    + ".."
                    + (previousTurn.getLastEndDate() != null ? previousTurn.getLastEndDate() : "")
                    + "|label="
                    + previousTurn.getLastTimeLabel();
            if (previousTurn.getLastVisibleStoreIds() != null) {
                prevStores = previousTurn.getLastVisibleStoreIds().toString();
            }
        }
        String effStores = effOrg != null && effOrg.getVisibleStores() != null
                ? effOrg.getVisibleStores().stream()
                        .filter(Objects::nonNull)
                        .map(s -> s.getStoreDepartmentId() + ":" + (s.getStoreName() != null ? s.getStoreName() : ""))
                        .collect(Collectors.joining(","))
                : null;
        boolean currentExplicitStore =
                semanticDeclaresStoreFocusForLogging(ctx.getQuerySemanticParse(), permissionBaselineOrg);
        String rm = rawMessage == null ? "" : rawMessage;
        if (rm.length() > 2000) {
            rm = rm.substring(0, 2000) + "…(truncated)";
        }
        log.info(
                "[AiResolvedContext] pipeline runId={} conversationId={} rawMessage={} "
                        + "previousIntentCode={} previousPathCode={} "
                        + "previousStructuredIntentDetail={} previousPurchaseSourceType={} "
                        + "previousScopeType={} previousVisibleStores={} "
                        + "previousTimeWindow={} "
                        + "currentIntentCode={} currentPathCode={} currentStructuredIntentDetail={} currentPurchaseSourceType={} "
                        + "currentExplicitTimeMentioned={} currentExplicitStoreMentioned={} "
                        + "currentDeclaresDomainPath={} "
                        + "effectiveIntentCode={} effectivePathCode={} "
                        + "effectiveTimeWindow={}..{} effectiveTimeLabel={} "
                        + "effectiveScopeType={} effectiveVisibleStores={} "
                        + "effectivePurchaseSourceType={} effectiveStructuredIntentDetail={} "
                        + "effectiveIntentSource={} effectiveTimeWindowSource={} effectiveScopeSource={} "
                        + "mentionedStore={} matchedStoreDepartmentId={}",
                runId,
                conversationId,
                rm,
                previousTurn != null ? previousTurn.getLastIntentCode() : null,
                previousTurn != null ? previousTurn.getLastPathCode() : null,
                previousTurn != null ? previousTurn.getLastStructuredIntentDetail() : null,
                previousTurn != null ? previousTurn.getLastPurchaseSourceType() : null,
                previousTurn != null ? previousTurn.getLastScopeType() : null,
                prevStores,
                prevTw,
                cur != null ? cur.getIntentCode() : null,
                cur != null ? cur.getPathCode() : null,
                cur != null ? cur.getStructuredIntentDetail() : null,
                cur != null ? cur.getPurchaseSourceType() : null,
                currentExplicitTimeMentioned,
                currentExplicitStore,
                AiFollowUpHintSupport.currentMessageDeclaresDomainPath(rawMessage),
                ctx.getEffectiveIntentCode(),
                ctx.getEffectivePathCode(),
                tw != null ? tw.getStartDate() : null,
                tw != null ? tw.getEndDate() : null,
                tw != null ? tw.getTimeLabel() : null,
                effOrg != null ? effOrg.getScopeType() : null,
                effStores,
                qi != null ? qi.getPurchaseSourceType() : null,
                qi != null ? qi.getStructuredIntentDetail() : null,
                ctx.getEffectiveIntentSource(),
                ctx.getEffectiveTimeWindowSource(),
                ctx.getEffectiveScopeSource(),
                followUp != null ? followUp.getStoreScopeFollowUpMentionedName() : null,
                followUp != null ? followUp.getStoreScopeFollowUpMatchedStoreRootId() : null);
    }

    public void logExplicitStoreMentionNarrowing(
            Long runId,
            Long convId,
            AiResolvedOrgScope before,
            AiResolvedOrgScope after) {
        if (!log.isInfoEnabled()) {
            return;
        }
        log.info(
                "[AiResolvedQueryContext] explicitStoreMentionNarrowing runId={} conversationId={} "
                        + "beforeScopeType={} afterScopeType={} afterVisibleStoreIds={}",
                runId,
                convId,
                before != null ? before.getScopeType() : null,
                after != null ? after.getScopeType() : null,
                after != null && after.getVisibleStores() != null
                        ? after.getVisibleStores().stream()
                                .map(s -> s != null ? s.getStoreDepartmentId() : null)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList())
                        : null);
    }

    public void logTimeContractMissingOnNonClarificationPath(Long runId, Long convId) {
        log.warn(
                "[AiResolvedQueryContext] time contract missing on non-clarification path runId={} conversationId={}",
                runId,
                convId);
    }

    private static boolean semanticDeclaresStoreFocusForLogging(
            AiQuerySemanticParseResult sem, AiResolvedOrgScope groupLikeOrg) {
        if (sem == null || sem.isParseMissing() || groupLikeOrg == null
                || !AiResolvedOrgScope.SCOPE_GROUP.equals(groupLikeOrg.getScopeType())) {
            return false;
        }
        if (!sem.effectiveMentionedStoreNames().isEmpty()) {
            return true;
        }
        AiQuerySemanticParseResult.RequestedScopePart rs = sem.getRequestedScope();
        return rs != null && StringUtils.hasText(rs.getMentionedStoreName());
    }
}
