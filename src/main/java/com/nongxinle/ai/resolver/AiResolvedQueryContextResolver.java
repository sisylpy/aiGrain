package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiDepartmentScopeDTO;
import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.conversation.AiConversationMemoryService;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.conversation.AiFollowUpResolver;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.followup.FollowUpIntentResolveService;
import com.nongxinle.ai.followup.FollowUpPathKind;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统一解析入口：装配 {@link AiResolvedQueryContext}（唯一新业务上下文入口）；规则解析 + 组织树下钻口径由本类集中处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiResolvedQueryContextResolver {

    private final GbDepartmentMapper gbDepartmentMapper;
    private final AiConversationMemoryService conversationMemoryService;
    private final AiFollowUpResolver followUpResolver;

    public AiResolvedQueryContext resolve(AiRunCreateRequest request, AiUserContext userContext) {
        return resolve(null, request, userContext, LocalDate.now());
    }

    public AiResolvedQueryContext resolve(Long runId, AiRunCreateRequest request, AiUserContext userContext) {
        return resolve(runId, request, userContext, LocalDate.now());
    }

    /**
     * @param today 语义解析「今天」锚点；Harness Replay 传入固定日以稳定断言，生产链路请使用 {@link #resolve(Long, AiRunCreateRequest, AiUserContext)}。
     */
    public AiResolvedQueryContext resolve(Long runId, AiRunCreateRequest request, AiUserContext userContext, LocalDate today) {
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(userContext, "userContext");
        String message = request != null ? request.getMessage() : null;
        String normalized = message == null ? "" : message.trim();
        Long reqDept = request != null ? request.getDepartmentId() : null;
        Long effectiveDept = reqDept != null ? reqDept : userContext.getDepartmentId();

        Long uid = userContext.getUserId() != null ? userContext.getUserId()
                : (request != null ? request.getUserId() : null);
        Long convId = request != null ? request.getConversationId() : null;
        AiConversationTurnMemory previousTurn =
                uid != null ? conversationMemoryService.load(uid, convId) : null;
        if (log.isInfoEnabled()) {
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
        if (convId != null && previousTurn == null
                && FollowUpIntentResolveService.isShortTemporalFollowUp(normalized)) {
            log.warn(
                    "[AiFollowUpContext] runId={} userId={} conversationId={}: short temporal follow-up but no "
                            + "AiConversationTurnMemory — ensure prior run in this window used the same conversationId "
                            + "and completed (memory is keyed by userId + conversationId only).",
                    runId, uid, convId);
        }

        AiResolvedOrgScope orgScope = resolveOrgScope(userContext, effectiveDept, request);
        AiResolvedTimeWindow tentativeTime = AiResolvedTimeWindow.tryParseExplicitFromUserMessage(message, today);
        AiResolvedQueryIntent keywordIntent = AiResolvedQueryIntent.fromUserMessage(message);

        AiFollowUpResolution followUp = followUpResolver.resolve(
                message, previousTurn, keywordIntent, tentativeTime, orgScope, request, today, runId);

        AiResolvedQueryIntent queryIntent = followUp.getMergedQueryIntent() != null
                ? followUp.getMergedQueryIntent() : keywordIntent;
        AiResolvedTimeWindow timeWindow = followUp.getMergedTimeWindow() != null
                ? followUp.getMergedTimeWindow() : tentativeTime;
        timeWindow = AiMultiTurnTimeWindowPolicy.finalizeTimeWindow(timeWindow, tentativeTime, previousTurn, today);
        String effectiveTimeSource = AiMultiTurnTimeWindowPolicy.resolveEffectiveTimeWindowSource(tentativeTime, timeWindow);
        if (followUp != null) {
            followUp.setMergedTimeWindow(timeWindow);
            followUp.setEffectiveTimeWindowSource(effectiveTimeSource);
        }
        AiResolvedOrgScope mergedOrg = followUp != null && followUp.getMergedOrgScope() != null
                ? followUp.getMergedOrgScope()
                : orgScope;

        var orgOutcome = AiMultiTurnOrgScopePolicy.applyInheritedEffectiveOrgScope(mergedOrg, previousTurn, message);
        mergedOrg = orgOutcome.org();
        if (orgOutcome.inheritedFromPreviousTurn()) {
            followUp.setEffectiveScopeSource("INHERITED_PREVIOUS");
        } else if (followUp != null && followUp.isInheritOrgScope()
                && !"STORE_SCOPE_FOLLOW_UP".equals(followUp.getFollowUpType())
                && !"GROUP_SCOPE_EXPAND_FOLLOW_UP".equals(followUp.getFollowUpType())
                && "INHERITED_PREVIOUS".equals(followUp.getEffectiveScopeSource())) {
            followUp.setEffectiveScopeSource("CURRENT_MESSAGE");
        }

        AiResolvedOrgScope beforeExplicitStore = mergedOrg;
        mergedOrg = AiFollowUpResolver.maybeNarrowGroupScopeToExplicitStoreMention(message, mergedOrg, request);
        if (mergedOrg != beforeExplicitStore) {
            followUp.setEffectiveScopeSource("CURRENT_MESSAGE_EXPLICIT_STORE");
        }
        AiResolvedOrgScope beforeDbName = mergedOrg;
        mergedOrg = narrowGroupOrgByDbStoreNameIfNeeded(message, mergedOrg, request);
        if (mergedOrg != beforeDbName) {
            followUp.setEffectiveScopeSource("CURRENT_MESSAGE_EXPLICIT_STORE");
        }
        if (mergedOrg != beforeExplicitStore && log.isInfoEnabled()) {
            log.info(
                    "[AiResolvedQueryContext] explicitStoreMentionNarrowing runId={} conversationId={} "
                            + "beforeScopeType={} afterScopeType={} afterVisibleStoreIds={}",
                    runId,
                    convId,
                    beforeExplicitStore != null ? beforeExplicitStore.getScopeType() : null,
                    mergedOrg != null ? mergedOrg.getScopeType() : null,
                    mergedOrg != null && mergedOrg.getVisibleStores() != null
                            ? mergedOrg.getVisibleStores().stream()
                                    .map(AiStoreScopeDTO::getStoreDepartmentId)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList())
                            : null);
        }

        reconcileSupplierRankingStructuredIntentFromUtterance(queryIntent, normalized);
        normalizeStockReduceStructuredRouting(queryIntent);
        normalizePurchaseStructuredRouting(queryIntent);
        alignFollowUpEffectiveRoutingWithQueryIntent(followUp, queryIntent);
        repairInheritedIntentPathAfterBroadScopeFollowUpLeak(
                normalized, previousTurn, followUp, queryIntent);
        repairDishProfitPathForLowProfitReasonQuestion(normalized, queryIntent, previousTurn);

        AiResolvedDataScope dataScope = buildDataScope(mergedOrg);

        String normQuestion = normalized;
        if (followUp.isNormalizedInputExpandedAtResolvePhase()
                && followUp.getExpandedNormalizedQuestion() != null
                && !followUp.getExpandedNormalizedQuestion().isBlank()) {
            normQuestion = followUp.getExpandedNormalizedQuestion().trim();
        }

        String banner = mergedOrg != null ? mergedOrg.getQueryScopeBanner() : null;
        String timeLabel = timeWindow != null ? timeWindow.getDisplayText() : null;
        String answerBoundaryNote = buildCombinedBoundaryNote(
                effectiveTimeSource, followUp != null ? followUp.getEffectiveScopeSource() : null,
                timeWindow, mergedOrg, previousTurn);

        String mentionedDishName = resolveMentionedDishName(
                normalized, queryIntent, previousTurn, mergedOrg, followUp);
        String dishProfitMetricType =
                AiQuerySemanticLexicon.dishProfitMetricTypeFromStructuredWire(
                        queryIntent != null ? queryIntent.getStructuredIntentDetail() : null);

        AiResolvedQueryContext built = AiResolvedQueryContext.builder()
                .runId(runId)
                .userId(uid)
                .userContext(userContext)
                .orgScope(mergedOrg)
                .timeWindow(timeWindow)
                .queryIntent(queryIntent)
                .dataScope(dataScope)
                .followUp(followUp.isFollowUp())
                .originalQuestion(message)
                .normalizedQuestion(normQuestion)
                .queryScopeBanner(banner)
                .timeWindowLabel(timeLabel)
                .answerBoundaryNote(answerBoundaryNote)
                .previousTurn(previousTurn)
                .followUpResolution(followUp)
                .effectiveIntentCode(followUp.getEffectiveIntentCode())
                .effectivePathCode(followUp.getEffectivePathCode())
                .effectiveTimeWindowSource(effectiveTimeSource)
                .effectiveScopeSource(followUp.getEffectiveScopeSource())
                .effectiveIntentSource(followUp.getEffectiveIntentSource())
                .mentionedDishName(mentionedDishName)
                .dishProfitMetricType(dishProfitMetricType)
                .build();
        logIntentResolutionDiagnostics(runId, convId, message, previousTurn, keywordIntent, followUp, built);
        logFollowUpDiagnostics(runId, convId, previousTurn, followUp, built);
        logResolvedContextPipeline(
                runId,
                convId,
                message,
                previousTurn,
                orgScope,
                keywordIntent,
                tentativeTime != null,
                followUp,
                built);
        return built;
    }

    private void logIntentResolutionDiagnostics(
            Long runId,
            Long conversationId,
            String rawMessage,
            AiConversationTurnMemory previousTurn,
            AiResolvedQueryIntent currentIntentProbe,
            AiFollowUpResolution followUp,
            AiResolvedQueryContext ctx) {
        if (!log.isInfoEnabled()) {
            return;
        }
        var fur = followUp;
        var probe = currentIntentProbe != null ? currentIntentProbe : AiResolvedQueryIntent.fromUserMessage(
                rawMessage != null ? rawMessage : "");
        log.info(
                "[AiFollowUpContext] intentRouting runId={} conversationId={} rawMessageSnippet={} "
                        + "currentIntentCode={} currentPathCode={} previousTurn.intentCode={} previousTurn.pathCode={} "
                        + "followUp={} inheritIntent={} effectiveIntentCode={} effectivePathCode={} effectiveIntentSource={}",
                runId,
                conversationId,
                rawMessage == null ? "" : (rawMessage.length() > 120 ? rawMessage.substring(0, 120) + "…" : rawMessage),
                probe != null ? probe.getIntentCode() : null,
                probe != null ? probe.getPathCode() : null,
                previousTurn != null ? previousTurn.getLastIntentCode() : null,
                previousTurn != null ? previousTurn.getLastPathCode() : null,
                fur != null && fur.isFollowUp(),
                fur != null && fur.isInheritIntent(),
                ctx.getEffectiveIntentCode(),
                ctx.getEffectivePathCode(),
                ctx.getEffectiveIntentSource());
    }

    private void logFollowUpDiagnostics(
            Long runId,
            Long conversationId,
            AiConversationTurnMemory previousTurn,
            AiFollowUpResolution followUp,
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

    private static String buildCombinedBoundaryNote(
            String effectiveTimeWindowSource,
            String effectiveScopeSource,
            AiResolvedTimeWindow tw,
            AiResolvedOrgScope org,
            AiConversationTurnMemory previousTurn) {
        boolean timeInh = "INHERITED_PREVIOUS".equals(effectiveTimeWindowSource);
        boolean scopeInh = "INHERITED_PREVIOUS".equals(effectiveScopeSource);
        List<String> hints = new ArrayList<>();
        if (scopeInh) {
            AiMultiTurnOrgScopePolicy.singleVisibleStoreName(org).ifPresent(hints::add);
        }
        if (timeInh && tw != null) {
            hints.add(AiMultiTurnTimeWindowPolicy.humanReadableTimeCarryover(tw));
        }
        if (!hints.isEmpty()) {
            return "按上文「" + String.join(" + ", hints) + "」口径查询；本句未指定新的时间和门店。若需调整请直接说明。";
        }
        return AiMultiTurnTimeWindowPolicy.buildAnswerBoundaryNote(
                effectiveTimeWindowSource, tw, previousTurn);
    }

    private void logResolvedContextPipeline(
            Long runId,
            Long conversationId,
            String rawMessage,
            AiConversationTurnMemory previousTurn,
            AiResolvedOrgScope permissionBaselineOrg,
            AiResolvedQueryIntent currentKeywordIntent,
            boolean currentExplicitTimeMentioned,
            AiFollowUpResolution followUp,
            AiResolvedQueryContext ctx) {
        if (!log.isInfoEnabled()) {
            return;
        }
        var cur = currentKeywordIntent;
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
        boolean currentExplicitStore = permissionBaselineOrg != null
                && AiMultiTurnOrgScopePolicy.hasExplicitUniqueStoreMention(rawMessage, permissionBaselineOrg);
        String rm = rawMessage == null ? "" : rawMessage;
        if (rm.length() > 2000) {
            rm = rm.substring(0, 2000) + "…(truncated)";
        }
        AiResolvedQueryIntent lexProbe = AiResolvedQueryIntent.builder().build();
        AiQuerySemanticLexicon.mergePurchaseCuesInto(lexProbe, rawMessage != null ? rawMessage.trim() : "");
        log.info(
                "[AiResolvedContext] pipeline runId={} conversationId={} rawMessage={} "
                        + "previousIntentCode={} previousPathCode={} "
                        + "previousStructuredIntentDetail={} previousPurchaseSourceType={} "
                        + "previousScopeType={} previousVisibleStores={} "
                        + "previousTimeWindow={} "
                        + "currentIntentCode={} currentPathCode={} currentStructuredIntentDetail={} currentPurchaseSourceType={} "
                        + "currentExplicitTimeMentioned={} currentExplicitStoreMentioned={} "
                        + "currentLexiconStructuredIntentDetail={} currentLexiconPurchaseSourceType={} currentLexiconExplicitPurchaseSource={} "
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
                lexProbe.getStructuredIntentDetail(),
                lexProbe.getPurchaseSourceType(),
                AiQuerySemanticLexicon.messageDeclaresExplicitPurchaseSource(rawMessage),
                FollowUpIntentResolveService.currentMessageDeclaresDomainPath(rawMessage),
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

    private AiResolvedDataScope buildDataScope(AiResolvedOrgScope org) {
        if (org == null) {
            return AiResolvedDataScope.builder()
                    .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_STORE)
                    .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_EMPTY)
                    .queryStoreIds(new ArrayList<>())
                    .queryRealDepartmentIds(new ArrayList<>())
                    .expandedSqlDepartmentIds(new ArrayList<>())
                    .storeToDepartmentIds(new LinkedHashMap<>())
                    .build();
        }
        if (AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(org.getScopeType())) {
            List<Long> whIds = org.getVisibleWarehouses() == null ? new ArrayList<>() : org.getVisibleWarehouses().stream()
                    .map(AiDepartmentScopeDTO::getDepartmentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(ArrayList::new));
            boolean allWh = !whIds.isEmpty();
            List<Integer> whInt = new ArrayList<>();
            for (Long id : whIds) {
                if (id != null && id > 0 && id <= Integer.MAX_VALUE) {
                    whInt.add(id.intValue());
                }
            }
            return AiResolvedDataScope.builder()
                    .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_DEPARTMENT)
                    .queryRealDepartmentIds(new ArrayList<>(whInt))
                    .queryStoreIds(new ArrayList<>())
                    .queryDistributerId(null)
                    .storeToDepartmentIds(new LinkedHashMap<>())
                    .expandedSqlDepartmentIds(new ArrayList<>(whInt))
                    .visibleStoreIds(new ArrayList<>())
                    .storeRootDepartmentIds(new ArrayList<>())
                    .targetStoreIds(new ArrayList<>())
                    .explicitChildDepartmentIds(new ArrayList<>())
                    .expandedChildDepartmentIds(new ArrayList<>())
                    .visibleWarehouseIds(new ArrayList<>(whIds))
                    .targetWarehouseIds(whIds)
                    .targetDepartmentIds(new ArrayList<>(whIds))
                    .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_WAREHOUSE_DEPARTMENT)
                    .allVisibleStores(false)
                    .allVisibleWarehouses(allWh)
                    .build();
        }

        List<Long> storeRoots = org.getVisibleStores() == null ? new ArrayList<>() : org.getVisibleStores().stream()
                .map(AiStoreScopeDTO::getStoreDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        LinkedHashMap<Integer, List<Integer>> rootToChildrenInt = new LinkedHashMap<>();
        List<Long> expandedChildren = new ArrayList<>();
        List<Integer> expandedSqlInt = new ArrayList<>();
        List<Integer> storeRootInts = new ArrayList<>();

        for (Long root : storeRoots) {
            if (root == null || root <= 0 || root > Integer.MAX_VALUE) {
                continue;
            }
            int ri = root.intValue();
            storeRootInts.add(ri);
            expandedSqlInt.add(ri);
            List<Integer> childIdsInt = new ArrayList<>();
            List<GbDepartmentEntity> subs = gbDepartmentMapper.querySubDepartments(ri);
            if (subs != null) {
                for (GbDepartmentEntity sub : subs) {
                    if (sub != null && sub.getGbDepartmentId() != null) {
                        long sid = sub.getGbDepartmentId().longValue();
                        if (sid > 0 && sid <= Integer.MAX_VALUE) {
                            int si = (int) sid;
                            expandedSqlInt.add(si);
                            childIdsInt.add(si);
                            expandedChildren.add(sid);
                        }
                    }
                }
            }
            rootToChildrenInt.put(ri, childIdsInt);
        }

        if (storeRoots.isEmpty() && org.getDistributerId() != null) {
            long dis = org.getDistributerId();
            if (dis > 0 && dis <= Integer.MAX_VALUE) {
                return AiResolvedDataScope.builder()
                        .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_DISTRIBUTER)
                        .queryStoreIds(new ArrayList<>())
                        .queryRealDepartmentIds(new ArrayList<>())
                        .queryDistributerId((int) dis)
                        .storeToDepartmentIds(new LinkedHashMap<>())
                        .expandedSqlDepartmentIds(new ArrayList<>())
                        .visibleStoreIds(new ArrayList<>())
                        .storeRootDepartmentIds(new ArrayList<>())
                        .targetStoreIds(new ArrayList<>())
                        .explicitChildDepartmentIds(new ArrayList<>())
                        .expandedChildDepartmentIds(new ArrayList<>())
                        .visibleWarehouseIds(new ArrayList<>())
                        .targetWarehouseIds(new ArrayList<>())
                        .targetDepartmentIds(new ArrayList<>())
                        .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_EMPTY)
                        .allVisibleStores(false)
                        .allVisibleWarehouses(false)
                        .build();
            }
        }

        boolean allStores = AiResolvedOrgScope.SCOPE_GROUP.equals(org.getScopeType());
        List<Long> rootsCopy = new ArrayList<>(storeRoots);
        return AiResolvedDataScope.builder()
                .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_STORE)
                .queryStoreIds(new ArrayList<>(storeRootInts))
                .queryRealDepartmentIds(new ArrayList<>())
                .queryDistributerId(null)
                .storeToDepartmentIds(rootToChildrenInt)
                .expandedSqlDepartmentIds(new ArrayList<>(expandedSqlInt))
                .visibleStoreIds(new ArrayList<>(rootsCopy))
                .storeRootDepartmentIds(new ArrayList<>(rootsCopy))
                .targetStoreIds(new ArrayList<>(rootsCopy))
                .explicitChildDepartmentIds(new ArrayList<>())
                .expandedChildDepartmentIds(expandedChildren)
                .visibleWarehouseIds(new ArrayList<>())
                .targetWarehouseIds(new ArrayList<>())
                .targetDepartmentIds(new ArrayList<>())
                .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_STORE_ROOTS_AND_DIRECT_CHILDREN)
                .allVisibleStores(allStores)
                .allVisibleWarehouses(false)
                .build();
    }

    /**
     * 供货商/供应商排行类问法：补上 {@code structuredIntentDetail}（与词典一致），避免仅依赖 Tool 默认 Top 却仍缺 Run/Harness 调试字段。
     */
    private static void reconcileSupplierRankingStructuredIntentFromUtterance(
            AiResolvedQueryIntent qi, String trimmedMessage) {
        if (!StringUtils.hasText(trimmedMessage) || qi == null) {
            return;
        }
        if (!AiQuerySemanticLexicon.looksLikeSupplierRanking(trimmedMessage)) {
            return;
        }
        if (AiQuerySemanticLexicon.looksLikeExplicitPurchaseGeneralOverviewOrGoodsRankingOnly(trimmedMessage)) {
            return;
        }
        String path = qi.getPathCode();
        if (path != null && !path.isBlank()
                && !AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(path)) {
            return;
        }
        String sid = qi.getStructuredIntentDetail();
        boolean upgradeSid = !StringUtils.hasText(sid)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY.equals(sid)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY.equals(sid);
        if (upgradeSid) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING);
        }
    }

    /**
     * 词典仅写出库结构化子意图、path 仍空时，补全 {@code stock_reduce_query_path}，避免追问仅带「损耗呢」时 effective* 断档。
     */
    private static void normalizeStockReduceStructuredRouting(AiResolvedQueryIntent qi) {
        if (qi == null) {
            return;
        }
        String sid = qi.getStructuredIntentDetail();
        boolean wants = AiQuerySemanticLexicon.isStructuredStockReduceDetail(sid);
        String path = qi.getPathCode();
        if (path != null && !path.isBlank()) {
            if (!AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(path)) {
                return;
            }
            if (!StringUtils.hasText(qi.getIntentCode())) {
                qi.setIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
            }
            return;
        }
        if (!wants) {
            return;
        }
        qi.setPathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        qi.setIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        if (!StringUtils.hasText(qi.getTopic())) {
            qi.setTopic("出库/核销查询");
        }
    }

    private static void normalizePurchaseStructuredRouting(AiResolvedQueryIntent qi) {
        if (qi == null) {
            return;
        }
        String sid = qi.getStructuredIntentDetail();
        boolean ranking = AiQuerySemanticLexicon.isSupplierAmountRankingDetail(sid);
        boolean needsPurchasePath = ranking
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY.equals(sid)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY.equals(sid)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(sid)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(sid);
        if (!needsPurchasePath && (qi.getPurchaseSourceType() == null || qi.getPurchaseSourceType().isBlank())) {
            return;
        }
        if (qi.getPathCode() != null && !qi.getPathCode().isBlank()) {
            return;
        }
        qi.setPathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        qi.setIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        if (ranking) {
            qi.setTopic("采购概览（供货商排行）");
        } else if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(sid)) {
            qi.setTopic("采购概览（来源金额）");
        } else if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(sid)) {
            qi.setTopic("采购概览（来源商品）");
        } else if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY.equals(sid)) {
            qi.setTopic("采购概览");
        } else {
            qi.setTopic("采购概览（来源聚焦）");
        }
    }

    /**
     * {@link #normalizePurchaseStructuredRouting} 在本类后段补全 intent/path（如仅含 {@code purchaseSourceType}），
     * 而 {@link com.nongxinle.ai.conversation.AiFollowUpResolver} 内 {@code fillSources} 已写过一轮 {@code effective*}，
     * 必须把二者对齐，否则 Harness / Replay 会看到 effectiveIntentCode 为空。
     */
    /**
     * 「全部门店呢」会先被误认为店名片语且无 DB 命中，{@link AiFollowUpResolver} 可能未 return，
     * 此处按「大范围重置用语」兜底补全 intent/path/effective*。
     */
    private void repairInheritedIntentPathAfterBroadScopeFollowUpLeak(
            String normalized,
            AiConversationTurnMemory previousTurn,
            AiFollowUpResolution followUp,
            AiResolvedQueryIntent queryIntent) {
        if (!StringUtils.hasText(normalized) || previousTurn == null || queryIntent == null || followUp == null) {
            return;
        }
        if (!StringUtils.hasText(previousTurn.getLastPathCode())) {
            return;
        }
        if (!AiMultiTurnOrgScopePolicy.messageDeclaresBroadGroupReset(normalized)) {
            return;
        }
        if (StringUtils.hasText(queryIntent.getPathCode()) && StringUtils.hasText(queryIntent.getIntentCode())) {
            return;
        }
        if (FollowUpIntentResolveService.currentMessageDeclaresDomainPath(normalized)) {
            return;
        }
        FollowUpPathKind lk = followUpPathKindFrom(previousTurn.getLastPathCode());
        if (lk == null || FollowUpIntentResolveService.pathTopicConflict(normalized, lk)) {
            return;
        }
        AiFollowUpResolver.overlayTurnMemoryOntoBlankIntent(previousTurn, normalized, queryIntent);
        normalizeStockReduceStructuredRouting(queryIntent);
        normalizePurchaseStructuredRouting(queryIntent);
        alignFollowUpEffectiveRoutingWithQueryIntent(followUp, queryIntent);
        if (!followUp.isFollowUp()) {
            followUp.setFollowUp(true);
            followUp.setFollowUpType("GROUP_SCOPE_EXPAND_FOLLOW_UP");
            followUp.setInheritIntent(true);
            followUp.setEffectiveIntentSource("INHERITED_PREVIOUS");
            followUp.setEffectiveScopeSource("CURRENT_MESSAGE_GROUP_EXPAND");
        }
    }

    private static FollowUpPathKind followUpPathKindFrom(String pathCode) {
        if (!StringUtils.hasText(pathCode)) {
            return null;
        }
        return switch (pathCode) {
            case AiResolvedQueryIntent.PATH_DISH_PROFIT -> FollowUpPathKind.DISH_PROFIT;
            case AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW -> FollowUpPathKind.BUSINESS_OVERVIEW;
            case AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK -> FollowUpPathKind.WAREHOUSE_STOCK;
            case AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW -> FollowUpPathKind.PURCHASE_OVERVIEW;
            case AiResolvedQueryIntent.PATH_COST_DIAGNOSIS -> FollowUpPathKind.COST_INSIGHT;
            case AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY -> FollowUpPathKind.STOCK_REDUCE_QUERY;
            default -> null;
        };
    }

    private static void alignFollowUpEffectiveRoutingWithQueryIntent(
            AiFollowUpResolution followUp, AiResolvedQueryIntent qi) {
        if (followUp == null || qi == null) {
            return;
        }
        if (StringUtils.hasText(qi.getIntentCode())) {
            followUp.setEffectiveIntentCode(qi.getIntentCode());
        }
        if (StringUtils.hasText(qi.getPathCode())) {
            followUp.setEffectivePathCode(qi.getPathCode());
        }
    }

    private AiResolvedOrgScope resolveOrgScope(AiUserContext ctx, Long requestDepartmentId, AiRunCreateRequest request) {
        Integer admin = ctx.getSourceAdminRole();
        if (admin == null) {
            return buildDepartmentLikeScope(ctx, requestDepartmentId, AiResolvedOrgScope.SCOPE_DEPARTMENT, request);
        }
        if (Objects.equals(admin, GbConstants.DepartmentUserRole.GROUP_MANAGER_APP)) {
            return buildGroupScope(ctx, requestDepartmentId, request);
        }
        if (Objects.equals(admin, GbConstants.DepartmentUserRole.STORE_MANAGER_APP)) {
            return buildStoreScope(ctx, requestDepartmentId, AiResolvedOrgScope.SCOPE_STORE, request);
        }
        if (Objects.equals(admin, GbConstants.DepartmentUserRole.STORE_PURCHASER_APP)) {
            return buildStoreScope(ctx, requestDepartmentId, AiResolvedOrgScope.SCOPE_PURCHASER, request);
        }
        if (Objects.equals(admin, GbConstants.DepartmentUserRole.WAREHOUSE_APP)) {
            return buildWarehouseScope(ctx, requestDepartmentId, request);
        }
        return buildDepartmentLikeScope(ctx, requestDepartmentId, AiResolvedOrgScope.SCOPE_DEPARTMENT, request);
    }

    /**
     * Run 请求体中的 {@code distributerId} 优先于用户表快照，避免集团账号挂靠部门与主体 ID 不一致时只展开一家门店。
     */
    static Long mergedDistributerId(AiRunCreateRequest request, AiUserContext ctx) {
        if (request != null && request.getDistributerId() != null) {
            return request.getDistributerId();
        }
        return ctx != null ? ctx.getDistributerId() : null;
    }

    private AiResolvedOrgScope buildGroupScope(AiUserContext ctx, Long requestDepartmentId, AiRunCreateRequest request) {
        Long dis = mergedDistributerId(request, ctx);
        var b = AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .distributerId(dis)
                .requestDepartmentId(requestDepartmentId)
                .currentDepartmentId(ctx.getDepartmentId())
                .visibleWarehouses(new ArrayList<>())
                .visibleDepartments(new ArrayList<>());

        if (dis == null) {
            b.scopeName("集团（未解析 distributerId）")
                    .visibleStores(new ArrayList<>())
                    .queryScopeBanner("集团范围：缺少 distributerId，暂无法展开下属门店")
                    .coverageDetail("请确保 gb_department_user 挂靠 distributerId、或请求体传入 distributerId。");
            return b.build();
        }
        int disPk;
        try {
            disPk = Math.toIntExact(dis);
        } catch (ArithmeticException ex) {
            b.scopeName("集团（distributerId 超出 int 范围）")
                    .visibleStores(new ArrayList<>())
                    .queryScopeBanner("集团范围：distributerId 无法用于部门表查询")
                    .coverageDetail("distributerId=" + dis + " 超出 MyBatis 映射 int。");
            return b.build();
        }

        List<Integer> storeIds = gbDepartmentMapper.selectStoreDepartmentIdsUnderDistributer(disPk);
        List<AiStoreScopeDTO> stores = new ArrayList<>(storeIds.size());
        for (Integer sid : storeIds) {
            GbDepartmentEntity row = sid != null ? gbDepartmentMapper.selectById(sid) : null;
            stores.add(AiStoreScopeDTO.builder()
                    .storeDepartmentId(sid != null ? sid.longValue() : null)
                    .storeName(row != null ? row.getGbDepartmentName() : null)
                    .build());
        }

        String banner = "集团范围：共识别 " + stores.size() + " 家门店（gb_department_father_id=0）";
        b.visibleStores(stores)
                .currentStoreDepartmentId(null)
                .scopeName("集团")
                .queryScopeBanner(banner)
                .coverageDetail("visibleStores 为权限内应可见门店根，非当日有营收门店。");
        return b.build();
    }

    private AiResolvedOrgScope buildStoreScope(AiUserContext ctx, Long requestDepartmentId, String scopeType,
                                               AiRunCreateRequest request) {
        Long dis = mergedDistributerId(request, ctx);
        NormalizedDept n = normalizeStoreAnchor(requestDepartmentId);
        List<AiStoreScopeDTO> stores = new ArrayList<>();
        if (n.storeDepartmentId() != null) {
            stores.add(AiStoreScopeDTO.builder()
                    .storeDepartmentId(n.storeDepartmentId())
                    .storeName(n.storeName())
                    .build());
        }
        String label = AiResolvedOrgScope.SCOPE_PURCHASER.equals(scopeType) ? "门店采购" : "门店";
        String banner = n.storeName() != null
                ? label + "：" + n.storeName()
                : label + "：部门 " + requestDepartmentId;
        return AiResolvedOrgScope.builder()
                .scopeType(scopeType)
                .distributerId(dis)
                .requestDepartmentId(requestDepartmentId)
                .currentStoreDepartmentId(n.storeDepartmentId())
                .currentDepartmentId(requestDepartmentId)
                .visibleStores(stores)
                .visibleWarehouses(new ArrayList<>())
                .visibleDepartments(new ArrayList<>())
                .scopeName(label)
                .queryScopeBanner(banner)
                .coverageDetail("单门店可见范围。")
                .build();
    }

    private AiResolvedOrgScope buildWarehouseScope(AiUserContext ctx, Long requestDepartmentId,
                                                   AiRunCreateRequest request) {
        Long dis = mergedDistributerId(request, ctx);
        Long deptId = requestDepartmentId != null ? requestDepartmentId : ctx.getDepartmentId();
        GbDepartmentEntity dep = departmentRow(deptId);
        List<AiDepartmentScopeDTO> wh = new ArrayList<>();
        Long father = null;
        if (dep != null) {
            Integer f = dep.getGbDepartmentFatherId();
            father = f != null ? f.longValue() : null;
            wh.add(AiDepartmentScopeDTO.builder()
                    .departmentId(dep.getGbDepartmentId() != null ? dep.getGbDepartmentId().longValue() : deptId)
                    .departmentName(dep.getGbDepartmentName())
                    .fatherId(father)
                    .build());
        } else if (deptId != null) {
            wh.add(AiDepartmentScopeDTO.builder()
                    .departmentId(deptId)
                    .departmentName(null)
                    .fatherId(null)
                    .build());
        }

        Long storeAnchor = (father != null && father > 0L) ? father : null;

        return AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_WAREHOUSE)
                .distributerId(dis)
                .requestDepartmentId(requestDepartmentId)
                .currentStoreDepartmentId(storeAnchor)
                .currentDepartmentId(deptId)
                .visibleStores(new ArrayList<>())
                .visibleWarehouses(wh)
                .visibleDepartments(new ArrayList<>(wh))
                .scopeName("库房")
                .queryScopeBanner(dep != null && dep.getGbDepartmentName() != null
                        ? "本库房：" + dep.getGbDepartmentName()
                        : "本库房/部门：" + deptId)
                .coverageDetail("库房视角：仅本人所在库房/部门，不展开集团全部门店库存。")
                .build();
    }

    private AiResolvedOrgScope buildDepartmentLikeScope(AiUserContext ctx, Long requestDepartmentId, String scopeType,
                                                        AiRunCreateRequest request) {
        Long dis = mergedDistributerId(request, ctx);
        NormalizedDept n = normalizeStoreAnchor(requestDepartmentId != null ? requestDepartmentId : ctx.getDepartmentId());
        List<AiStoreScopeDTO> stores = new ArrayList<>();
        if (n.storeDepartmentId() != null) {
            stores.add(AiStoreScopeDTO.builder()
                    .storeDepartmentId(n.storeDepartmentId())
                    .storeName(n.storeName())
                    .build());
        }
        return AiResolvedOrgScope.builder()
                .scopeType(scopeType)
                .distributerId(dis)
                .requestDepartmentId(requestDepartmentId)
                .currentStoreDepartmentId(n.storeDepartmentId())
                .currentDepartmentId(requestDepartmentId != null ? requestDepartmentId : ctx.getDepartmentId())
                .visibleStores(stores)
                .visibleWarehouses(new ArrayList<>())
                .visibleDepartments(new ArrayList<>())
                .scopeName("部门")
                .queryScopeBanner(n.storeName() != null ? "可见门店：" + n.storeName() : "组织范围：待解析")
                .coverageDetail("非 0/1/3/11 角色的兜底：按挂靠部门归一化门店锚点。")
                .build();
    }

    private NormalizedDept normalizeStoreAnchor(Long departmentId) {
        GbDepartmentEntity dep = departmentRow(departmentId);
        if (dep == null) {
            return new NormalizedDept(departmentId, null);
        }
        Integer father = dep.getGbDepartmentFatherId();
        if (father == null || father == 0) {
            long sid = dep.getGbDepartmentId() != null ? dep.getGbDepartmentId().longValue() : departmentId;
            return new NormalizedDept(sid, dep.getGbDepartmentName());
        }
        GbDepartmentEntity store = gbDepartmentMapper.selectById(father);
        long sid = father.longValue();
        return new NormalizedDept(sid, store != null ? store.getGbDepartmentName() : null);
    }

    private GbDepartmentEntity departmentRow(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        if (departmentId > Integer.MAX_VALUE || departmentId < Integer.MIN_VALUE) {
            return null;
        }
        return gbDepartmentMapper.selectById(departmentId.intValue());
    }

    /**
     * 当 {@link AiFollowUpResolver#maybeNarrowGroupScopeToExplicitStoreMention} 因 visibleStores 缺店名未命中时，
     * 用 distributer 下门店根表名与正文再做一次交集（仍限制在集团当前 visible 门店 id 内）。
     */
    private AiResolvedOrgScope narrowGroupOrgByDbStoreNameIfNeeded(
            String rawMessage,
            AiResolvedOrgScope mergedOrg,
            AiRunCreateRequest request) {
        if (mergedOrg == null || !AiResolvedOrgScope.SCOPE_GROUP.equals(mergedOrg.getScopeType())) {
            return mergedOrg;
        }
        List<AiStoreScopeDTO> vis = mergedOrg.getVisibleStores();
        if (vis == null || vis.size() <= 1) {
            return mergedOrg;
        }
        String norm = rawMessage == null ? "" : rawMessage.trim();
        if (!StringUtils.hasText(norm)) {
            return mergedOrg;
        }
        String work = FollowUpIntentResolveService.stripKnownTemporalPhrases(norm);
        if (!StringUtils.hasText(work)) {
            work = norm;
        }
        work = work.replace(" ", "");
        Long dis = mergedOrg.getDistributerId() != null
                ? mergedOrg.getDistributerId()
                : (request != null ? request.getDistributerId() : null);
        if (dis == null) {
            return mergedOrg;
        }
        int disPk;
        try {
            disPk = Math.toIntExact(dis);
        } catch (ArithmeticException ex) {
            return mergedOrg;
        }
        Set<Long> allowed = vis.stream()
                .filter(s -> s != null && s.getStoreDepartmentId() != null)
                .map(AiStoreScopeDTO::getStoreDepartmentId)
                .collect(Collectors.toSet());
        if (allowed.isEmpty()) {
            return mergedOrg;
        }
        List<Integer> ids = gbDepartmentMapper.selectStoreDepartmentIdsUnderDistributer(disPk);
        if (ids == null || ids.isEmpty()) {
            return mergedOrg;
        }
        List<AiStoreScopeDTO> candidates = new ArrayList<>();
        for (Integer id : ids) {
            if (id == null || id <= 0 || !allowed.contains(id.longValue())) {
                continue;
            }
            GbDepartmentEntity e = gbDepartmentMapper.selectById(id);
            if (e == null || e.getGbDepartmentFatherId() == null || e.getGbDepartmentFatherId() != 0) {
                continue;
            }
            candidates.add(AiStoreScopeDTO.builder()
                    .storeDepartmentId(e.getGbDepartmentId() != null ? e.getGbDepartmentId().longValue() : null)
                    .storeName(e.getGbDepartmentName())
                    .build());
        }
        if (candidates.size() < 2) {
            return mergedOrg;
        }
        Optional<AiStoreScopeDTO> hit =
                AiFollowUpResolver.uniquelyMentionedStoreFromVisibleList(work, candidates);
        if (hit.isEmpty()) {
            return mergedOrg;
        }
        log.info(
                "[AiResolvedQueryContext] explicitStoreInMessage dbIntersectionHit storeRootId={} storeName={}",
                hit.get().getStoreDepartmentId(),
                hit.get().getStoreName());
        return AiFollowUpResolver.copyOrgNarrowedToSingleStore(mergedOrg, hit.get());
    }

    private static String resolveMentionedDishName(
            String normalized,
            AiResolvedQueryIntent qi,
            AiConversationTurnMemory previousTurn,
            AiResolvedOrgScope mergedOrg,
            AiFollowUpResolution followUp) {
        if (qi == null || !AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(qi.getPathCode())) {
            return null;
        }
        String fromMsg = AiQuerySemanticLexicon.extractDishNameHint(normalized, qi);
        if (!StringUtils.hasText(fromMsg)) {
            fromMsg = AiQuerySemanticLexicon.tryExtractDishNameForReasonQuestion(normalized);
        }
        if (StringUtils.hasText(fromMsg)) {
            String dish = discardIfHintIsScopedStoreName(fromMsg.trim(), mergedOrg, followUp);
            if (StringUtils.hasText(dish)) {
                return dish;
            }
        }
        if (previousTurn != null && StringUtils.hasText(previousTurn.getLastMentionedDishName())) {
            return discardIfHintIsScopedStoreName(
                    previousTurn.getLastMentionedDishName().trim(), mergedOrg, followUp);
        }
        return null;
    }

    /**
     * 「某某店呢」会与点名菜的正则重叠，不能把当前可见门店名当作菜名传给毛利工具（否则明细被 filter 光）。
     */
    private static String discardIfHintIsScopedStoreName(
            String dishHint,
            AiResolvedOrgScope org,
            AiFollowUpResolution followUp) {
        if (!StringUtils.hasText(dishHint)) {
            return null;
        }
        if (equalsNormalizedStoreLabel(dishHint, followUp != null ? followUp.getStoreScopeFollowUpMentionedName() : null)) {
            return null;
        }
        if (org != null && org.getVisibleStores() != null) {
            for (AiStoreScopeDTO s : org.getVisibleStores()) {
                if (s != null && equalsNormalizedStoreLabel(dishHint, s.getStoreName())) {
                    return null;
                }
            }
        }
        return dishHint;
    }

    /**
     * 「某菜为什么毛利低」类句在关键词层常未落到 PATH_DISH_PROFIT；在上下文合并后补 path+structured，便于 Debug 与 Tool 焦点。
     */
    private static void repairDishProfitPathForLowProfitReasonQuestion(
            String normalized,
            AiResolvedQueryIntent qi,
            AiConversationTurnMemory previousTurn) {
        if (qi == null || normalized == null || normalized.isBlank()) {
            return;
        }
        String compact = normalized.replace(" ", "");
        if (!AiQuerySemanticLexicon.looksLikeDishLowProfitReasonQuestion(compact)) {
            return;
        }
        boolean prevDish = previousTurn != null
                && AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(previousTurn.getLastPathCode());
        String standalone = AiQuerySemanticLexicon.tryExtractDishNameForReasonQuestion(normalized);
        boolean prevMentioned = previousTurn != null
                && StringUtils.hasText(previousTurn.getLastMentionedDishName());
        if (!prevDish && !StringUtils.hasText(standalone) && !prevMentioned) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(qi.getPathCode())) {
            qi.setPathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
            qi.setIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        }
        AiQuerySemanticLexicon.mergeDishProfitCuesInto(qi, normalized);
        boolean hasDishRef = StringUtils.hasText(standalone)
                || prevMentioned;
        if (hasDishRef
                && !AiQuerySemanticLexicon.STRUCTURED_DISH_LOW_PROFIT_REASON.equals(qi.getStructuredIntentDetail())) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_LOW_PROFIT_REASON);
        }
    }

    private static boolean equalsNormalizedStoreLabel(String dishHint, String storeLabel) {
        if (!StringUtils.hasText(dishHint) || !StringUtils.hasText(storeLabel)) {
            return false;
        }
        String a = dishHint.replace(" ", "").trim();
        String b = storeLabel.replace(" ", "").trim();
        return !a.isEmpty() && a.equals(b);
    }

    private record NormalizedDept(Long storeDepartmentId, String storeName) {
    }
}
