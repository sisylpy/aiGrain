package com.nongxinle.ai.conversation;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.harness.AiHarnessUnknownPurchaseSemanticLogger;
import com.nongxinle.ai.followup.FollowUpIntentResolveService;
import com.nongxinle.ai.followup.FollowUpPathKind;
import com.nongxinle.ai.resolver.AiMultiTurnOrgScopePolicy;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无 LLM 的多轮追问与采购语义词匹配；合并结果写入 {@link com.nongxinle.ai.context.AiResolvedQueryContext}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiFollowUpResolver {

    private static final Pattern SCOPE_HINT_NA = Pattern.compile("^(?:那|那么)(.+?)呢[？?！!。…]*$");
    private static final Pattern SCOPE_HINT_ONLY = Pattern.compile("^只看(.+?)(?:吧|行吗|行|可以吗)?[？?！!。…]*$");
    private static final Pattern PAT_STORE_SUFFIX_NE = Pattern.compile("^(.+?)门店呢[？?！!。…]*$");
    private static final Pattern PAT_SWITCH_STORE = Pattern.compile("^换成(.+?)(?:门店)?[？?！!。…]*$");
    private static final Pattern PAT_LOOK_STORE = Pattern.compile("^看一下(.+?)[？?！!。…]*$");
    private static final Pattern PAT_BARE_NE = Pattern.compile("^(.+?)呢[？?！!。…]*$");
    /** 门店范围追问句一般较短；避免把长问句误切 */
    private static final int MAX_STORE_SCOPE_FOLLOW_LEN = 80;

    private final FollowUpIntentResolveService followUpIntentResolveService;
    private final GbDepartmentMapper gbDepartmentMapper;
    private final AiHarnessUnknownPurchaseSemanticLogger unknownPurchaseSemanticLogger;

    public AiFollowUpResolution resolve(
            String rawMessage,
            AiConversationTurnMemory previousTurn,
            AiResolvedQueryIntent keywordIntent,
            AiResolvedTimeWindow tentativeTime,
            AiResolvedOrgScope tentativeOrg,
            AiRunCreateRequest request,
            LocalDate today,
            Long runId) {

        String norm = rawMessage == null ? "" : rawMessage.trim();
        AiFollowUpResolution.AiFollowUpResolutionBuilder b = AiFollowUpResolution.builder()
                .followUp(false)
                .inheritIntent(false)
                .inheritTimeWindow(false)
                .inheritOrgScope(false)
                .inheritFocus(false)
                .normalizedInputExpandedAtResolvePhase(false);

        AiResolvedQueryIntent merged = copyIntent(keywordIntent);
        AiQuerySemanticLexicon.mergePurchaseCuesInto(merged, norm);
        AiQuerySemanticLexicon.mergeStockReduceCuesInto(merged, norm);
        AiQuerySemanticLexicon.mergeDishProfitCuesInto(merged, norm);
        AiResolvedTimeWindow mergedTw = tentativeTime;
        AiResolvedOrgScope mergedOrg = tentativeOrg;

        b.mergedQueryIntent(merged);
        b.mergedTimeWindow(mergedTw);
        b.mergedOrgScope(mergedOrg);

        if (previousTurn == null || !StringUtils.hasText(previousTurn.getLastPathCode())) {
            b.purchaseStructuredIntent(merged.getStructuredIntentDetail());
            b.purchaseSourceType(merged.getPurchaseSourceType());
            return fillSources(b, merged, mergedTw, mergedOrg);
        }

        FollowUpPathKind lastKind = pathCodeToKind(previousTurn.getLastPathCode());

        // 0) 回到全集团/全部门店可见范围：必须在「店名+呢」解析之前，否则「全部门店呢」会被当成假店名且匹配不到任何店，导致 intent/path 落空。
        if (lastKind != null
                && tentativeOrg != null
                && AiMultiTurnOrgScopePolicy.messageDeclaresBroadGroupReset(norm)
                && !FollowUpIntentResolveService.currentMessageDeclaresDomainPath(norm)
                && !followUpIntentResolveService.conflictsWithPreviousPath(norm, lastKind)) {
            AiFollowUpResolution.AiFollowUpResolutionBuilder gb = b.followUp(true).followUpType("GROUP_SCOPE_EXPAND_FOLLOW_UP")
                    .inheritIntent(true).inheritTimeWindow(true).inheritOrgScope(true)
                    .overrideIntentCode(previousTurn.getLastIntentCode())
                    .overridePathCode(previousTurn.getLastPathCode());
            merged = inheritIntentFromMemory(previousTurn, norm);
            AiQuerySemanticLexicon.mergePurchaseCuesInto(merged, norm);
            AiQuerySemanticLexicon.mergeStockReduceCuesInto(merged, norm);
            AiQuerySemanticLexicon.mergeDishProfitCuesInto(merged, norm);
            mergedTw = pickTimeForScopeShift(tentativeTime, previousTurn, today);
            mergedOrg = tentativeOrg;
            gb.mergedQueryIntent(merged);
            gb.mergedTimeWindow(mergedTw);
            gb.mergedOrgScope(mergedOrg);
            gb.purchaseStructuredIntent(merged.getStructuredIntentDetail());
            gb.purchaseSourceType(merged.getPurchaseSourceType());
            log.info(
                    "[AiFollowUpResolver] GROUP_SCOPE_EXPAND_FOLLOW_UP prevPath={} structured={}",
                    previousTurn.getLastPathCode(),
                    merged.getStructuredIntentDetail());
            return fillSources(gb, merged, mergedTw, mergedOrg);
        }

        // 0b) stock_reduce_query_path：仅补结构化子意图（如「损耗呢」「生产耗用了多少」）；继承上一轮时间与门店，不重算为本月至今
        if (lastKind == FollowUpPathKind.STOCK_REDUCE_QUERY && tentativeOrg != null
                && stockReduceStructuredIntentFromUtterance(norm)
                && !extractStoreScopeMentionRaw(norm).isPresent()
                && !AiQuerySemanticLexicon.looksLikeGoodsOutboundRanking(norm)) {
            b.followUp(true).followUpType("STOCK_REDUCE_DETAIL_FOLLOW_UP");
            b.inheritIntent(true).inheritTimeWindow(true).inheritOrgScope(true);
            merged = inheritIntentFromMemory(previousTurn, norm);
            AiQuerySemanticLexicon.mergePurchaseCuesInto(merged, norm);
            AiQuerySemanticLexicon.mergeStockReduceCuesInto(merged, norm);
            AiQuerySemanticLexicon.mergeDishProfitCuesInto(merged, norm);
            mergedTw = resolveTimeForPurchaseFollowUp(norm, tentativeTime, previousTurn, today);
            b.mergedQueryIntent(merged);
            b.mergedTimeWindow(mergedTw);
            b.mergedOrgScope(mergedOrg);
            b.purchaseStructuredIntent(merged.getStructuredIntentDetail());
            b.purchaseSourceType(merged.getPurchaseSourceType());
            log.info("[AiFollowUpResolver] STOCK_REDUCE_DETAIL_FOLLOW_UP prevPath={} structured={}",
                    previousTurn.getLastPathCode(), merged.getStructuredIntentDetail());
            return fillSources(b, merged, mergedTw, mergedOrg);
        }

        // 0c) dish_profit_path：仅补结构化子意图（如「理论成本呢」「哪个菜毛利最低」）；继承上一轮时间与门店
        if (lastKind == FollowUpPathKind.DISH_PROFIT && tentativeOrg != null
                && AiQuerySemanticLexicon.dishProfitStructuredIntentFromUtterance(norm)
                && !extractStoreScopeMentionRaw(norm).isPresent()) {
            b.followUp(true).followUpType("DISH_PROFIT_DETAIL_FOLLOW_UP");
            b.inheritIntent(true).inheritTimeWindow(true).inheritOrgScope(true);
            merged = inheritIntentFromMemory(previousTurn, norm);
            AiQuerySemanticLexicon.mergePurchaseCuesInto(merged, norm);
            AiQuerySemanticLexicon.mergeStockReduceCuesInto(merged, norm);
            AiQuerySemanticLexicon.mergeDishProfitCuesInto(merged, norm);
            mergedTw = resolveTimeForPurchaseFollowUp(norm, tentativeTime, previousTurn, today);
            b.mergedQueryIntent(merged);
            b.mergedTimeWindow(mergedTw);
            b.mergedOrgScope(mergedOrg);
            b.purchaseStructuredIntent(merged.getStructuredIntentDetail());
            b.purchaseSourceType(merged.getPurchaseSourceType());
            log.info("[AiFollowUpResolver] DISH_PROFIT_DETAIL_FOLLOW_UP prevPath={} structured={}",
                    previousTurn.getLastPathCode(), merged.getStructuredIntentDetail());
            return fillSources(b, merged, mergedTw, mergedOrg);
        }

        // 1a) 采购 path 下的细分追问（仅补 purchaseSourceType / structuredIntentDetail，不重置组织；范围由 Resolver 公共策略继承）
        if (lastKind == FollowUpPathKind.PURCHASE_OVERVIEW && tentativeOrg != null
                && isPurchasePathLexicalRefinement(merged, norm)
                && !AiQuerySemanticLexicon.looksLikeSupplierRanking(norm)
                && !extractStoreScopeMentionRaw(norm).isPresent()) {
            b.followUp(true).followUpType("PURCHASE_DETAIL_FOLLOW_UP");
            b.inheritIntent(true).inheritTimeWindow(false).inheritOrgScope(true);
            merged = inheritIntentFromMemory(previousTurn, norm);
            AiQuerySemanticLexicon.mergePurchaseCuesInto(merged, norm);
            AiQuerySemanticLexicon.mergeStockReduceCuesInto(merged, norm);
            AiQuerySemanticLexicon.mergeDishProfitCuesInto(merged, norm);
            if (AiQuerySemanticLexicon.augmentPurchaseOverviewSourceFromShortCue(merged, norm, previousTurn)) {
                unknownPurchaseSemanticLogger.recordPurchaseOverviewAugmentUnresolved(
                        norm,
                        previousTurn,
                        merged,
                        request != null ? request.getConversationId() : null,
                        runId);
            }
            b.mergedQueryIntent(merged);
            b.mergedTimeWindow(mergedTw);
            b.mergedOrgScope(mergedOrg);
            b.purchaseStructuredIntent(merged.getStructuredIntentDetail());
            b.purchaseSourceType(merged.getPurchaseSourceType());
            log.info(
                    "[AiFollowUpResolver] PURCHASE_DETAIL_FOLLOW_UP inheritPurchasePath prevPurchase={} "
                            + "lexExplicitSource={} mergedPurchase={} mergedStructured={}",
                    previousTurn.getLastPurchaseSourceType(),
                    AiQuerySemanticLexicon.messageDeclaresExplicitPurchaseSource(norm),
                    merged.getPurchaseSourceType(),
                    merged.getStructuredIntentDetail());
            return fillSources(b, merged, mergedTw, mergedOrg);
        }

        // 1) 门店范围追问：仅店名/换店用语，无新业务意图；门店根来自 gb_department_father_id=0 + 分销户，DataScope 再展直属子部门
        Optional<String> storeMention = extractStoreScopeMentionRaw(norm);
        if (storeMention.isPresent() && lastKind != null && tentativeOrg != null
                && !FollowUpIntentResolveService.currentMessageDeclaresDomainPath(norm)
                && !followUpIntentResolveService.conflictsWithPreviousPath(norm, lastKind)) {
            String rawHint = storeMention.get();
            Long disPk = resolveDistributerPk(request, tentativeOrg);
            Optional<GbDepartmentEntity> rootEntity =
                    disPk == null ? Optional.empty() : resolveStoreRootByNameHint(rawHint, disPk);
            Optional<AiStoreScopeDTO> hitDto = Optional.empty();
            if (rootEntity.isPresent()) {
                GbDepartmentEntity r = rootEntity.get();
                hitDto = Optional.of(AiStoreScopeDTO.builder()
                        .storeDepartmentId(r.getGbDepartmentId() != null ? r.getGbDepartmentId().longValue() : null)
                        .storeName(r.getGbDepartmentName())
                        .build());
            }
            if (hitDto.isEmpty()) {
                hitDto = matchSingleStore(tentativeOrg.getVisibleStores(), rawHint);
            }
            if (hitDto.isPresent() && hitDto.get().getStoreDepartmentId() != null) {
                AiResolvedOrgScope narrowed = narrowToSingleStoreForScopeFollowUp(tentativeOrg, hitDto.get());
                AiFollowUpResolution.AiFollowUpResolutionBuilder sb = b.followUp(true).followUpType("STORE_SCOPE_FOLLOW_UP")
                        .inheritIntent(true).inheritTimeWindow(true).inheritOrgScope(false)
                        .overrideIntentCode(previousTurn.getLastIntentCode())
                        .overridePathCode(previousTurn.getLastPathCode())
                        .storeScopeFollowUpMentionedName(rawHint)
                        .storeScopeFollowUpMatchedStoreRootId(hitDto.get().getStoreDepartmentId());
                merged = inheritIntentFromMemory(previousTurn, norm);
                AiQuerySemanticLexicon.mergePurchaseCuesInto(merged, norm);
                AiQuerySemanticLexicon.mergeStockReduceCuesInto(merged, norm);
                AiQuerySemanticLexicon.mergeDishProfitCuesInto(merged, norm);
                if (lastKind == FollowUpPathKind.PURCHASE_OVERVIEW
                        && !AiQuerySemanticLexicon.messageDeclaresExplicitPurchaseSource(norm)
                        && !AiQuerySemanticLexicon.isSupplierAmountRankingDetail(
                                previousTurn.getLastStructuredIntentDetail())
                        && !AiQuerySemanticLexicon.looksLikeSupplierRanking(norm)) {
                    merged.setPurchaseSourceType(null);
                    merged.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY);
                }
                if (lastKind == FollowUpPathKind.STOCK_REDUCE_QUERY) {
                    if (AiQuerySemanticLexicon.messageDeclaresExplicitStockReduceOverview(norm)) {
                        merged.setStructuredIntentDetail(
                                AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
                    } else if (AiQuerySemanticLexicon.isNonOverviewStockReduceStructuredDetail(
                            merged.getStructuredIntentDetail())) {
                        // 保留商品排行 / 生产耗用等子意图，仅换店不换题
                    } else if (!AiQuerySemanticLexicon.looksLikeGoodsOutboundRanking(norm)
                            && !stockReduceStructuredIntentFromUtterance(norm)) {
                        merged.setStructuredIntentDetail(
                                AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
                    }
                }
                if (lastKind == FollowUpPathKind.DISH_PROFIT) {
                    if (AiQuerySemanticLexicon.messageDeclaresExplicitDishProfitOverview(norm)) {
                        merged.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW);
                    } else if (AiQuerySemanticLexicon.isNonOverviewDishProfitStructuredDetail(
                            merged.getStructuredIntentDetail())) {
                        // 保留排行/理论/实际等子意图，仅换店不换题
                    } else if (!AiQuerySemanticLexicon.dishProfitStructuredIntentFromUtterance(norm)) {
                        merged.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW);
                    }
                }
                mergedTw = AiResolvedTimeWindow.tryParseExplicitFromUserMessage(
                        rawMessage, today != null ? today : LocalDate.now());
                if (mergedTw == null) {
                    mergedTw = pickTimeForScopeShift(tentativeTime, previousTurn, today);
                } else {
                    mergedTw.setInheritedFromPreviousTurn(false);
                }
                mergedOrg = narrowed;
                sb.mergedQueryIntent(merged);
                sb.mergedTimeWindow(mergedTw);
                sb.mergedOrgScope(mergedOrg);
                sb.purchaseStructuredIntent(merged.getStructuredIntentDetail());
                sb.purchaseSourceType(merged.getPurchaseSourceType());
                AiFollowUpResolution built = fillSources(sb, merged, mergedTw, mergedOrg);
                log.info(
                        "[AiFollowUpResolver] STORE_SCOPE_FOLLOW_UP mentioned={} matchedStoreRootId={} matchedName={} "
                                + "inheritIntent=true inheritTimeWindow=true overrideScope=true prevPath={}",
                        rawHint,
                        hitDto.get().getStoreDepartmentId(),
                        hitDto.get().getStoreName(),
                        previousTurn.getLastPathCode());
                return built;
            }
        }

        // 1b) 时间短追问
        boolean temporalConflict = lastKind != null
                && followUpIntentResolveService.conflictsWithPreviousPath(norm, lastKind);
        if (StringUtils.hasText(previousTurn.getLastPathCode())
                && FollowUpIntentResolveService.isShortTemporalFollowUp(norm)
                && !FollowUpIntentResolveService.currentMessageDeclaresDomainPath(norm)
                && !temporalConflict) {
            Optional<String> phrase = FollowUpIntentResolveService.extractNewTemporalPhrase(norm);
            if (phrase.isPresent()) {
                String baseQ = StringUtils.hasText(previousTurn.getLastEffectiveQuestion())
                        ? previousTurn.getLastEffectiveQuestion().trim()
                        : minimalCarriedQuestionFromTurnMemory(previousTurn);
                if (StringUtils.hasText(baseQ)) {
                    String expanded = FollowUpIntentResolveService.spliceTemporal(baseQ, phrase.get());
                    if (!StringUtils.hasText(expanded)) {
                        expanded = phrase.get() + baseQ;
                    }
                    b.followUp(true).followUpType("TIME_SHIFT");
                    b.inheritIntent(true).inheritTimeWindow(false).inheritOrgScope(true);
                    b.expandedNormalizedQuestion(expanded);
                    b.normalizedInputExpandedAtResolvePhase(!expanded.equals(baseQ));
                    b.overrideIntentCode(previousTurn.getLastIntentCode());
                    b.overridePathCode(previousTurn.getLastPathCode());
                    merged = inheritIntentFromMemory(previousTurn, norm);
                    AiQuerySemanticLexicon.mergePurchaseCuesInto(merged, norm);
                    AiQuerySemanticLexicon.mergeStockReduceCuesInto(merged, norm);
                    AiQuerySemanticLexicon.mergeDishProfitCuesInto(merged, norm);
                    mergedTw = AiResolvedTimeWindow.tryParseExplicitFromUserMessage(norm, today != null ? today : LocalDate.now());
                    if (mergedTw == null) {
                        mergedTw = AiResolvedTimeWindow.defaultMonthToDate(today != null ? today : LocalDate.now());
                    }
                    mergedTw.setInheritedFromPreviousTurn(false);
                    b.mergedQueryIntent(merged);
                    b.mergedTimeWindow(mergedTw);
                    b.mergedOrgScope(tentativeOrg);
                    b.purchaseStructuredIntent(merged.getStructuredIntentDetail());
                    b.purchaseSourceType(merged.getPurchaseSourceType());
                    return fillSources(b, merged, mergedTw, tentativeOrg);
                } else {
                    // 上轮未落 effectiveQuestion 且无模板句时：仍继承 path/intent，用当前句解析时间（如「上个月呢？」）
                    String template = minimalCarriedQuestionFromTurnMemory(previousTurn);
                    String expanded = StringUtils.hasText(template)
                            ? FollowUpIntentResolveService.spliceTemporal(template, phrase.get())
                            : phrase.get();
                    if (!StringUtils.hasText(expanded)) {
                        expanded = norm;
                    }
                    b.followUp(true).followUpType("TIME_SHIFT");
                    b.inheritIntent(true).inheritTimeWindow(false).inheritOrgScope(true);
                    b.expandedNormalizedQuestion(expanded);
                    b.normalizedInputExpandedAtResolvePhase(!expanded.isBlank() && !expanded.equals(norm));
                    b.overrideIntentCode(previousTurn.getLastIntentCode());
                    b.overridePathCode(previousTurn.getLastPathCode());
                    merged = inheritIntentFromMemory(previousTurn, norm);
                    AiQuerySemanticLexicon.mergePurchaseCuesInto(merged, norm);
                    AiQuerySemanticLexicon.mergeStockReduceCuesInto(merged, norm);
                    AiQuerySemanticLexicon.mergeDishProfitCuesInto(merged, norm);
                    mergedTw = AiResolvedTimeWindow.tryParseExplicitFromUserMessage(norm, today != null ? today : LocalDate.now());
                    if (mergedTw == null) {
                        mergedTw = AiResolvedTimeWindow.defaultMonthToDate(today != null ? today : LocalDate.now());
                    }
                    mergedTw.setInheritedFromPreviousTurn(false);
                    b.mergedQueryIntent(merged);
                    b.mergedTimeWindow(mergedTw);
                    b.mergedOrgScope(tentativeOrg);
                    b.purchaseStructuredIntent(merged.getStructuredIntentDetail());
                    b.purchaseSourceType(merged.getPurchaseSourceType());
                    return fillSources(b, merged, mergedTw, tentativeOrg);
                }
            }
        }

        // 2b) 采购：供货商排行追问（继承上一轮时间/范围）
        if (lastKind == FollowUpPathKind.PURCHASE_OVERVIEW && previousTurn != null
                && AiQuerySemanticLexicon.looksLikeSupplierRanking(norm)) {
            b.followUp(true).followUpType("SUPPLIER_RANKING");
            b.inheritIntent(true).inheritTimeWindow(true).inheritOrgScope(true);
            b.overrideIntentCode(previousTurn.getLastIntentCode());
            b.overridePathCode(previousTurn.getLastPathCode());
            merged = inheritIntentFromMemory(previousTurn, norm);
            AiQuerySemanticLexicon.mergePurchaseCuesInto(merged, norm);
            AiQuerySemanticLexicon.mergeStockReduceCuesInto(merged, norm);
            AiQuerySemanticLexicon.mergeDishProfitCuesInto(merged, norm);
            merged.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING);
            merged.setPurchaseSourceType(null);
            mergedTw = resolveTimeForPurchaseFollowUp(norm, tentativeTime, previousTurn, today);
            if (mergedTw != null) {
                mergedTw.setInheritedFromPreviousTurn(
                        FollowUpIntentResolveService.extractNewTemporalPhrase(norm).isEmpty());
            }
            mergedOrg = tentativeOrg;
            b.mergedQueryIntent(merged);
            b.mergedTimeWindow(mergedTw);
            b.mergedOrgScope(mergedOrg);
            b.purchaseStructuredIntent(merged.getStructuredIntentDetail());
            b.purchaseSourceType(merged.getPurchaseSourceType());
            return fillSources(b, merged, mergedTw, mergedOrg);
        }

        // 3) 同轮采购/出库/菜品毛利语义（未触发继承时仍合并词典）
        AiQuerySemanticLexicon.mergePurchaseCuesInto(merged, norm);
        AiQuerySemanticLexicon.mergeStockReduceCuesInto(merged, norm);
        AiQuerySemanticLexicon.mergeDishProfitCuesInto(merged, norm);
        b.purchaseStructuredIntent(merged.getStructuredIntentDetail());
        b.purchaseSourceType(merged.getPurchaseSourceType());
        return fillSources(b, merged, mergedTw, mergedOrg);
    }

    private static boolean stockReduceStructuredIntentFromUtterance(String norm) {
        if (!StringUtils.hasText(norm)) {
            return false;
        }
        AiResolvedQueryIntent probe = AiResolvedQueryIntent.builder().build();
        AiQuerySemanticLexicon.mergeStockReduceCuesInto(probe, norm);
        return StringUtils.hasText(probe.getStructuredIntentDetail());
    }

    /**
     * 本句未形成完整领域 path（经营/库存等），但已由 {@link AiQuerySemanticLexicon} 带出采购细分字段。
     */
    private static boolean isPurchasePathLexicalRefinement(AiResolvedQueryIntent merged, String norm) {
        if (merged == null) {
            return false;
        }
        if (StringUtils.hasText(merged.getPathCode())) {
            return false;
        }
        if (StringUtils.hasText(merged.getPurchaseSourceType()) || StringUtils.hasText(merged.getStructuredIntentDetail())) {
            return true;
        }
        if (!StringUtils.hasText(norm)) {
            return false;
        }
        String c = norm.replace(" ", "");
        return c.contains("采购") || c.contains("进货") || c.contains("订货")
                || c.contains("供货商") || c.contains("供应商") || c.contains("送货商") || c.contains("配送商")
                || c.contains("自采");
    }

    /**
     * 无新时间片语时继承上一轮 stat 窗口；否则以当前句解析为准。
     */
    private static AiResolvedTimeWindow resolveTimeForPurchaseFollowUp(
            String rawNorm,
            AiResolvedTimeWindow tentative,
            AiConversationTurnMemory previousTurn,
            LocalDate today) {
        if (FollowUpIntentResolveService.extractNewTemporalPhrase(rawNorm).isPresent()) {
            return tentative;
        }
        if (previousTurn == null || !StringUtils.hasText(previousTurn.getLastStartDate())) {
            return tentative;
        }
        try {
            LocalDate s = LocalDate.parse(previousTurn.getLastStartDate());
            LocalDate e = LocalDate.parse(previousTurn.getLastEndDate());
            return AiResolvedTimeWindow.builder()
                    .timeLabel(previousTurn.getLastTimeLabel() != null ? previousTurn.getLastTimeLabel()
                            : AiResolvedTimeWindow.CUSTOM)
                    .startDate(s)
                    .endDate(e)
                    .displayText("继承上一轮时间窗")
                    .inheritedFromPreviousTurn(true)
                    .explicitTimeMentioned(false)
                    .build();
        } catch (Exception ex) {
            return tentative;
        }
    }

    private static AiResolvedTimeWindow pickTimeForScopeShift(
            AiResolvedTimeWindow tentative,
            AiConversationTurnMemory previousTurn,
            LocalDate today) {
        if (tentative != null) {
            return tentative;
        }
        if (previousTurn == null || !StringUtils.hasText(previousTurn.getLastStartDate())) {
            return AiResolvedTimeWindow.defaultMonthToDate(today != null ? today : LocalDate.now());
        }
        try {
            LocalDate s = LocalDate.parse(previousTurn.getLastStartDate());
            LocalDate e = LocalDate.parse(previousTurn.getLastEndDate());
            return AiResolvedTimeWindow.builder()
                    .timeLabel(previousTurn.getLastTimeLabel() != null ? previousTurn.getLastTimeLabel()
                            : AiResolvedTimeWindow.CUSTOM)
                    .startDate(s)
                    .endDate(e)
                    .displayText("继承上一轮时间窗")
                    .inheritedFromPreviousTurn(true)
                    .explicitTimeMentioned(false)
                    .build();
        } catch (Exception ex) {
            return AiResolvedTimeWindow.defaultMonthToDate(today != null ? today : LocalDate.now());
        }
    }

    private static AiFollowUpResolution fillSources(
            AiFollowUpResolution.AiFollowUpResolutionBuilder b,
            AiResolvedQueryIntent merged,
            AiResolvedTimeWindow mergedTw,
            AiResolvedOrgScope mergedOrg) {
        AiFollowUpResolution r = b.build();
        if (merged != null) {
            if (mergedTw == null) {
                r.setEffectiveTimeWindowSource("UNRESOLVED");
            } else if (mergedTw.isInheritedFromPreviousTurn()) {
                r.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
            } else if (mergedTw.isExplicitTimeMentioned()) {
                r.setEffectiveTimeWindowSource("CURRENT_MESSAGE_EXPLICIT");
            } else {
                r.setEffectiveTimeWindowSource("DEFAULT_MONTH_TO_DATE");
            }
        }
        String fut = r.getFollowUpType();
        boolean scopeInherited = fut == null
                || (!"SCOPE_SHIFT".equals(fut) && !"STORE_SCOPE_FOLLOW_UP".equals(fut)
                        && !"GROUP_SCOPE_EXPAND_FOLLOW_UP".equals(fut));
        if ("STORE_SCOPE_FOLLOW_UP".equals(fut)) {
            r.setEffectiveScopeSource("CURRENT_MESSAGE_STORE_OVERRIDE");
        } else if ("GROUP_SCOPE_EXPAND_FOLLOW_UP".equals(fut)) {
            r.setEffectiveScopeSource("CURRENT_MESSAGE_GROUP_EXPAND");
        } else if ("SCOPE_SHIFT".equals(fut)) {
            r.setEffectiveScopeSource("FOLLOWUP_NARROW_VISIBLE_STORE");
        } else {
            r.setEffectiveScopeSource(scopeInherited && r.isInheritOrgScope() ? "INHERITED_PREVIOUS" : "CURRENT_MESSAGE");
        }
        if (merged != null) {
            r.setEffectiveIntentCode(merged.getIntentCode());
            r.setEffectivePathCode(merged.getPathCode());
        }
        if (r.isFollowUp() && r.isInheritIntent()) {
            if ("PURCHASE_DETAIL_FOLLOW_UP".equals(fut)) {
                r.setEffectiveIntentSource("CURRENT_MESSAGE_PURCHASE_DETAIL");
            } else {
                r.setEffectiveIntentSource("INHERITED_PREVIOUS");
            }
        } else {
            r.setEffectiveIntentSource("CURRENT_MESSAGE_EXPLICIT");
        }
        return r;
    }

    private static FollowUpPathKind pathCodeToKind(String pathCode) {
        if (pathCode == null) {
            return null;
        }
        return switch (pathCode) {
            case AiResolvedQueryIntent.PATH_DISH_PROFIT -> FollowUpPathKind.DISH_PROFIT;
            case AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW -> FollowUpPathKind.BUSINESS_OVERVIEW;
            case AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK -> FollowUpPathKind.WAREHOUSE_STOCK;
            case AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW -> FollowUpPathKind.PURCHASE_OVERVIEW;
            case AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY -> FollowUpPathKind.STOCK_REDUCE_QUERY;
            case AiResolvedQueryIntent.PATH_COST_DIAGNOSIS -> FollowUpPathKind.COST_INSIGHT;
            default -> null;
        };
    }

    private static AiResolvedQueryIntent copyIntent(AiResolvedQueryIntent src) {
        if (src == null) {
            return AiResolvedQueryIntent.builder().build();
        }
        return AiResolvedQueryIntent.builder()
                .intentCode(src.getIntentCode())
                .pathCode(src.getPathCode())
                .topic(src.getTopic())
                .inheritedFromPreviousTurn(src.isInheritedFromPreviousTurn())
                .inheritedFromIntentCode(src.getInheritedFromIntentCode())
                .structuredIntentDetail(src.getStructuredIntentDetail())
                .purchaseSourceType(src.getPurchaseSourceType())
                .build();
    }

    /**
     * 上一轮未落 effectiveQuestion 时，用 path + 结构化字段拼出带「本月」的模板问句，供时间片语替换（如本月→上个月）。
     */
    static String minimalCarriedQuestionFromTurnMemory(AiConversationTurnMemory prev) {
        if (prev == null || !StringUtils.hasText(prev.getLastPathCode())) {
            return "";
        }
        String path = prev.getLastPathCode();
        if (AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(path)) {
            String sid = prev.getLastStructuredIntentDetail();
            if (AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(sid)) {
                return "本月哪个商品出库金额最高";
            }
            if (AiQuerySemanticLexicon.STRUCTURED_PRODUCE_CONSUME.equals(sid)) {
                return "本月生产耗用了多少钱";
            }
            if (AiQuerySemanticLexicon.STRUCTURED_WASTE.equals(sid)) {
                return "本月废弃出库多少钱";
            }
            if (AiQuerySemanticLexicon.STRUCTURED_LOSS.equals(sid)) {
                return "本月损耗出库多少钱";
            }
            if (AiQuerySemanticLexicon.STRUCTURED_RETURN.equals(sid)) {
                return "本月退货出库多少钱";
            }
            return "本月出库一共多少钱";
        }
        if (AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(path)) {
            StringBuilder sb = new StringBuilder("本月");
            String src = prev.getLastPurchaseSourceType();
            if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(src)) {
                sb.append("自采");
            } else if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(src)) {
                sb.append("供货商采购");
            }
            String sid = prev.getLastStructuredIntentDetail();
            if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(sid)) {
                if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(src)
                        || AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(src)) {
                    sb.append("金额是多少");
                } else {
                    sb.append("采购金额是多少");
                }
            } else if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(sid)) {
                sb.append("采购了哪些商品");
            } else if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY.equals(sid)) {
                sb.append("有多少");
            } else if (AiQuerySemanticLexicon.isSupplierAmountRankingDetail(sid)) {
                sb.append("哪个供货商采购最多");
            } else {
                sb.append("采购怎么样");
            }
            return sb.toString();
        }
        if (AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(path)) {
            return "本月经营怎么样";
        }
        if (AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(path)) {
            return "本月库存怎么样";
        }
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(path)) {
            String sid = prev.getLastStructuredIntentDetail();
            String dish = prev.getLastMentionedDishName();
            if (StringUtils.hasText(dish)) {
                if (AiQuerySemanticLexicon.STRUCTURED_DISH_THEORETICAL_COST.equals(sid)) {
                    return dish + "本月理论成本是多少";
                }
                if (AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_OUTBOUND_COST.equals(sid)
                        || AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY.equals(sid)) {
                    return dish + "本月菜品毛利怎么样";
                }
                if (AiQuerySemanticLexicon.STRUCTURED_DISH_COST_GAP.equals(sid)) {
                    return dish + "本月理论和实际成本差异怎么样";
                }
                return dish + "本月菜品毛利怎么样";
            }
            if (AiQuerySemanticLexicon.STRUCTURED_DISH_THEORETICAL_COST.equals(sid)) {
                return "本月菜品理论成本情况";
            }
            if (AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_OUTBOUND_COST.equals(sid)) {
                return "本月菜品实际出库成本情况";
            }
            if (AiQuerySemanticLexicon.STRUCTURED_DISH_GAP_RANKING_MAX.equals(sid)
                    || AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(sid)
                    || AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH.equals(sid)
                    || AiQuerySemanticLexicon.STRUCTURED_DISH_LOW_PROFIT_REASON.equals(sid)) {
                return "本月菜品毛利排行情况";
            }
            return "本月菜品毛利怎么样";
        }
        if (AiResolvedQueryIntent.PATH_COST_DIAGNOSIS.equals(path)) {
            return "本月成本怎么样";
        }
        return "";
    }

    /**
     * 继承上轮 purchaseSourceType：仅采购/成本诊断类 path 携带渠道；菜品毛利、经营、库存等追问不得粘上「供货商采购」以免串域。
     */
    private static String resolveInheritedPurchaseSourceType(AiConversationTurnMemory prev, AiResolvedQueryIntent lex) {
        if (lex != null && StringUtils.hasText(lex.getPurchaseSourceType())) {
            return lex.getPurchaseSourceType();
        }
        if (prev == null || !StringUtils.hasText(prev.getLastPathCode())) {
            return null;
        }
        String pp = prev.getLastPathCode();
        if (AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(pp)
                || AiResolvedQueryIntent.PATH_COST_DIAGNOSIS.equals(pp)) {
            return prev.getLastPurchaseSourceType();
        }
        return null;
    }

    /** 上一轮若只 persisted path（少见），由 path 反推 intent，避免 effective* 链路断档。 */
    private static String inferIntentCodeFromPath(String pathCode) {
        if (!StringUtils.hasText(pathCode)) {
            return null;
        }
        if (AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(pathCode)) {
            return AiResolvedQueryIntent.PURCHASE_OVERVIEW;
        }
        if (AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(pathCode)) {
            return AiResolvedQueryIntent.BUSINESS_OVERVIEW;
        }
        if (AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(pathCode)) {
            return AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW;
        }
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(pathCode)) {
            return AiResolvedQueryIntent.DISH_PROFIT;
        }
        if (AiResolvedQueryIntent.PATH_COST_DIAGNOSIS.equals(pathCode)) {
            return AiResolvedQueryIntent.COST_DIAGNOSIS;
        }
        if (AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(pathCode)) {
            return AiResolvedQueryIntent.STOCK_REDUCE_QUERY;
        }
        return null;
    }

    /**
     * 将上一轮会话意图叠到当前空的 queryIntent（兜底：如「全部门店呢」误入假店名片语分支后未 return）。
     */
    public static void overlayTurnMemoryOntoBlankIntent(AiConversationTurnMemory prev, String norm,
            AiResolvedQueryIntent qi) {
        if (prev == null || qi == null || !StringUtils.hasText(prev.getLastPathCode())) {
            return;
        }
        if (StringUtils.hasText(qi.getPathCode()) && StringUtils.hasText(qi.getIntentCode())) {
            return;
        }
        AiResolvedQueryIntent fromMem = inheritIntentFromMemory(prev, norm != null ? norm : "");
        if (!StringUtils.hasText(qi.getPathCode())) {
            qi.setPathCode(fromMem.getPathCode());
        }
        if (!StringUtils.hasText(qi.getIntentCode())) {
            qi.setIntentCode(fromMem.getIntentCode());
        }
        if (!StringUtils.hasText(qi.getStructuredIntentDetail())) {
            qi.setStructuredIntentDetail(fromMem.getStructuredIntentDetail());
        }
        if (!StringUtils.hasText(qi.getPurchaseSourceType())) {
            qi.setPurchaseSourceType(fromMem.getPurchaseSourceType());
        }
        qi.setInheritedFromPreviousTurn(true);
    }

    private static AiResolvedQueryIntent inheritIntentFromMemory(
            AiConversationTurnMemory prev, String norm) {
        AiResolvedQueryIntent lex = AiResolvedQueryIntent.builder().build();
        if (StringUtils.hasText(norm)) {
            AiQuerySemanticLexicon.mergePurchaseCuesInto(lex, norm);
            AiQuerySemanticLexicon.mergeStockReduceCuesInto(lex, norm);
        }
        String path = prev.getLastPathCode();
        String structured = StringUtils.hasText(lex.getStructuredIntentDetail())
                ? lex.getStructuredIntentDetail()
                : prev.getLastStructuredIntentDetail();
        if (AiQuerySemanticLexicon.messageDeclaresExplicitStockReduceOverview(norm)
                && AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(path)) {
            structured = AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY;
        }
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(path)) {
            AiResolvedQueryIntent dishLex = AiResolvedQueryIntent.builder().pathCode(path).build();
            if (StringUtils.hasText(norm)) {
                AiQuerySemanticLexicon.mergeDishProfitCuesInto(dishLex, norm);
            }
            if (StringUtils.hasText(dishLex.getStructuredIntentDetail())) {
                structured = dishLex.getStructuredIntentDetail();
            }
        }
        String purchase = resolveInheritedPurchaseSourceType(prev, lex);
        String intent = StringUtils.hasText(prev.getLastIntentCode())
                ? prev.getLastIntentCode()
                : inferIntentCodeFromPath(path);
        return AiResolvedQueryIntent.builder()
                .intentCode(intent)
                .pathCode(path)
                .structuredIntentDetail(structured)
                .purchaseSourceType(purchase)
                .inheritedFromPreviousTurn(true)
                .inheritedFromIntentCode(
                        StringUtils.hasText(prev.getLastIntentCode())
                                ? prev.getLastIntentCode()
                                : intent)
                .topic(null)
                .build();
    }

    /**
     * 门店范围追问：先摘掉常见时间词（如「AAA门店上个月呢」→「AAA门店呢」），再按短语模式提取店名片语。
     */
    private Optional<String> extractStoreScopeMentionRaw(String norm) {
        if (!StringUtils.hasText(norm) || norm.length() > MAX_STORE_SCOPE_FOLLOW_LEN) {
            return Optional.empty();
        }
        String work = FollowUpIntentResolveService.stripKnownTemporalPhrases(norm);
        if (!StringUtils.hasText(work)) {
            return Optional.empty();
        }
        List<Pattern> order = List.of(
                PAT_STORE_SUFFIX_NE,
                PAT_SWITCH_STORE,
                PAT_LOOK_STORE,
                SCOPE_HINT_ONLY,
                SCOPE_HINT_NA,
                PAT_BARE_NE);
        for (Pattern p : order) {
            Optional<String> m = tryMentionPattern(p, work);
            if (m.isEmpty()) {
                continue;
            }
            if (isPurchaseSourceChannelShorthandNotStoreName(m.get())) {
                continue;
            }
            return m;
        }
        return Optional.empty();
    }

    /**
     * 「供货商呢？」会命中裸「…呢」店名模式，但此处仅为采购渠道口语，不能当作门店名解析。
     */
    private static boolean isPurchaseSourceChannelShorthandNotStoreName(String mention) {
        if (!StringUtils.hasText(mention)) {
            return false;
        }
        String c = mention.replace(" ", "").toLowerCase(Locale.ROOT);
        if (c.equals("供货商") || c.equals("供应商") || c.equals("送货商") || c.equals("配送商")
                || c.equals("自采") || c.equals("自采购")) {
            return true;
        }
        // 「供货商订货呢？」裸提取为「供货商订货」，不可当店名；否则阻断 PURCHASE_DETAIL 走 STORE_SCOPE
        boolean supplierWord = c.contains("供货商") || c.contains("供应商") || c.contains("送货商") || c.contains("配送商");
        if (supplierWord && (c.contains("订货") || c.contains("采购") || c.contains("进货"))) {
            return true;
        }
        if (c.startsWith("自采") || c.startsWith("自采购")) {
            return true;
        }
        return false;
    }

    private static Optional<String> tryMentionPattern(Pattern p, String s) {
        Matcher m = p.matcher(s.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        String g = m.group(1);
        if (!StringUtils.hasText(g)) {
            return Optional.empty();
        }
        String cleaned = sanitizeMention(g);
        return StringUtils.hasText(cleaned) ? Optional.of(cleaned) : Optional.empty();
    }

    private static String sanitizeMention(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    private Optional<GbDepartmentEntity> resolveStoreRootByNameHint(String rawHint, Long disPk) {
        if (!StringUtils.hasText(rawHint) || disPk == null) {
            return Optional.empty();
        }
        int disPkInt;
        try {
            disPkInt = Math.toIntExact(disPk);
        } catch (ArithmeticException ex) {
            return Optional.empty();
        }
        String normHint = normalizeForStoreMatch(rawHint);
        if (!StringUtils.hasText(normHint)) {
            return Optional.empty();
        }
        List<Integer> ids = gbDepartmentMapper.selectStoreDepartmentIdsUnderDistributer(disPkInt);
        if (ids == null || ids.isEmpty()) {
            return Optional.empty();
        }
        List<GbDepartmentEntity> hits = new ArrayList<>();
        for (Integer id : ids) {
            if (id == null) {
                continue;
            }
            GbDepartmentEntity e = gbDepartmentMapper.selectById(id);
            if (e == null) {
                continue;
            }
            if (e.getGbDepartmentFatherId() == null || e.getGbDepartmentFatherId() != 0) {
                continue;
            }
            String name = e.getGbDepartmentName();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            if (departmentNameMatches(normHint, name)) {
                hits.add(e);
            }
        }
        if (hits.isEmpty()) {
            return Optional.empty();
        }
        if (hits.size() == 1) {
            return Optional.of(hits.get(0));
        }
        List<GbDepartmentEntity> exact = new ArrayList<>();
        for (GbDepartmentEntity e : hits) {
            if (normalizeForStoreMatch(e.getGbDepartmentName()).equals(normHint)) {
                exact.add(e);
            }
        }
        if (exact.size() == 1) {
            return Optional.of(exact.get(0));
        }
        return Optional.empty();
    }

    private static boolean departmentNameMatches(String normHint, String dbName) {
        String n = normalizeForStoreMatch(dbName);
        if (!StringUtils.hasText(n)) {
            return false;
        }
        if (n.equals(normHint)) {
            return true;
        }
        return n.contains(normHint) || normHint.contains(n);
    }

    private static String normalizeForStoreMatch(String s) {
        if (s == null) {
            return "";
        }
        String x = s.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        x = x.replace("门店", "").replace("餐厅", "");
        if (x.endsWith("店") && x.length() > 1) {
            x = x.substring(0, x.length() - 1);
        }
        return x.trim();
    }

    private static Long resolveDistributerPk(AiRunCreateRequest request, AiResolvedOrgScope org) {
        if (org != null && org.getDistributerId() != null) {
            return org.getDistributerId();
        }
        if (request != null && request.getDistributerId() != null) {
            return request.getDistributerId();
        }
        return null;
    }

    private static AiResolvedOrgScope narrowToSingleStoreForScopeFollowUp(
            AiResolvedOrgScope org, AiStoreScopeDTO store) {
        return rewriteOrgToSingleStore(org, store);
    }

    /**
     * 集团可见多店时：若用户句中<b>唯一</b>点到一家门店名（如「汀兰餐厅采购总额是多少」），将组织范围收窄为该门店根，
     * 供采购/经营/库存等工具统一使用 {@link com.nongxinle.ai.context.AiResolvedQueryContext}。
     */
    /**
     * 集团 + 多店：仅依据 {@code visibleStores} 在正文中的点名做收窄（需唯一、最长店名胜出）。
     */
    public static AiResolvedOrgScope maybeNarrowGroupScopeToExplicitStoreMention(
            String rawMessage,
            AiResolvedOrgScope org,
            @SuppressWarnings("unused") AiRunCreateRequest request) {
        if (org == null || !AiResolvedOrgScope.SCOPE_GROUP.equals(org.getScopeType())) {
            return org;
        }
        List<AiStoreScopeDTO> stores = org.getVisibleStores();
        if (stores == null || stores.size() <= 1) {
            return org;
        }
        String norm = rawMessage == null ? "" : rawMessage.trim();
        if (!StringUtils.hasText(norm)) {
            return org;
        }
        String work = FollowUpIntentResolveService.stripKnownTemporalPhrases(norm);
        if (!StringUtils.hasText(work)) {
            work = norm;
        }
        work = work.replace(" ", "");
        Optional<AiStoreScopeDTO> fromVisible =
                uniquelyMentionedStoreFromVisibleList(work, stores);
        if (fromVisible.isEmpty()) {
            return org;
        }
        log.info(
                "[AiFollowUpResolver] explicitStoreInMessage visibleListHit storeRootId={} storeName={}",
                fromVisible.get().getStoreDepartmentId(),
                fromVisible.get().getStoreName());
        return rewriteOrgToSingleStore(org, fromVisible.get());
    }

    public static AiResolvedOrgScope copyOrgNarrowedToSingleStore(AiResolvedOrgScope org, AiStoreScopeDTO store) {
        return rewriteOrgToSingleStore(org, store);
    }

    private static AiResolvedOrgScope rewriteOrgToSingleStore(AiResolvedOrgScope org, AiStoreScopeDTO store) {
        if (org == null || store == null) {
            return org;
        }
        List<AiStoreScopeDTO> one = new ArrayList<>();
        one.add(store);
        String banner = "单店：" + (StringUtils.hasText(store.getStoreName())
                ? store.getStoreName() : ("门店" + store.getStoreDepartmentId()));
        return AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                .distributerId(org.getDistributerId())
                .requestDepartmentId(store.getStoreDepartmentId())
                .currentStoreDepartmentId(store.getStoreDepartmentId())
                .currentDepartmentId(store.getStoreDepartmentId())
                .visibleStores(one)
                .visibleWarehouses(org.getVisibleWarehouses() == null
                        ? new ArrayList<>() : new ArrayList<>(org.getVisibleWarehouses()))
                .visibleDepartments(org.getVisibleDepartments() == null
                        ? new ArrayList<>() : new ArrayList<>(org.getVisibleDepartments()))
                .scopeName(org.getScopeName())
                .queryScopeBanner(banner)
                .coverageDetail(org.getCoverageDetail())
                .build();
    }

    /**
     * 在正文里按店名包含关系收集命中，若「最长店名」仍唯一则视为用户点名该店。
     */
    public static Optional<AiStoreScopeDTO> uniquelyMentionedStoreFromVisibleList(
            String compactMessage,
            List<AiStoreScopeDTO> stores) {
        if (!StringUtils.hasText(compactMessage) || stores == null || stores.isEmpty()) {
            return Optional.empty();
        }
        String msg = compactMessage;
        List<AiStoreScopeDTO> hits = new ArrayList<>();
        for (AiStoreScopeDTO st : stores) {
            if (st == null || !StringUtils.hasText(st.getStoreName())) {
                continue;
            }
            String name = st.getStoreName().trim().replace(" ", "");
            if (name.length() < 2) {
                continue;
            }
            if (msg.contains(name) || messageContainsNormalizedStoreHint(msg, name)) {
                hits.add(st);
            }
        }
        if (hits.isEmpty()) {
            return Optional.empty();
        }
        int maxLen = hits.stream()
                .mapToInt(s -> s.getStoreName() != null ? s.getStoreName().trim().replace(" ", "").length() : 0)
                .max().orElse(0);
        if (maxLen < 2) {
            return Optional.empty();
        }
        List<AiStoreScopeDTO> longest = new ArrayList<>();
        for (AiStoreScopeDTO h : hits) {
            if (h.getStoreName() == null) {
                continue;
            }
            int len = h.getStoreName().trim().replace(" ", "").length();
            if (len == maxLen) {
                longest.add(h);
            }
        }
        return longest.size() == 1 ? Optional.of(longest.get(0)) : Optional.empty();
    }

    private static boolean messageContainsNormalizedStoreHint(String compactMessage, String storeName) {
        if (!StringUtils.hasText(compactMessage) || !StringUtils.hasText(storeName)) {
            return false;
        }
        String n = normalizeForStoreMatch(storeName);
        if (n.length() < 2) {
            return false;
        }
        String m = normalizeForStoreMatch(compactMessage);
        return m.contains(n);
    }

    static Optional<String> extractScopeStoreHint(String norm) {
        if (!StringUtils.hasText(norm)) {
            return Optional.empty();
        }
        String s = norm.trim();
        Matcher m1 = SCOPE_HINT_NA.matcher(s);
        if (m1.matches()) {
            return Optional.ofNullable(sanitizeHint(m1.group(1)));
        }
        Matcher m2 = SCOPE_HINT_ONLY.matcher(s);
        if (m2.matches()) {
            return Optional.ofNullable(sanitizeHint(m2.group(1)));
        }
        return Optional.empty();
    }

    private static String sanitizeHint(String h) {
        if (h == null) {
            return null;
        }
        String x = h.replace("门店", "").replace("店", "").trim();
        return x.isEmpty() ? h.trim() : x.trim();
    }

    static Optional<AiStoreScopeDTO> matchSingleStore(List<AiStoreScopeDTO> stores, String hint) {
        if (stores == null || stores.isEmpty() || !StringUtils.hasText(hint)) {
            return Optional.empty();
        }
        String h = hint.trim().toLowerCase(Locale.ROOT);
        List<AiStoreScopeDTO> hits = new ArrayList<>();
        for (AiStoreScopeDTO st : stores) {
            if (st == null) {
                continue;
            }
            String name = st.getStoreName();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String nl = name.toLowerCase(Locale.ROOT);
            if (nl.contains(h) || h.contains(nl)) {
                hits.add(st);
            }
        }
        if (hits.size() == 1) {
            return Optional.of(hits.get(0));
        }
        return Optional.empty();
    }

    static AiResolvedOrgScope narrowToSingleStore(AiResolvedOrgScope org, AiStoreScopeDTO store) {
        if (org == null || store == null) {
            return org;
        }
        List<AiStoreScopeDTO> one = new ArrayList<>();
        one.add(store);
        String banner = "单店：" + (StringUtils.hasText(store.getStoreName())
                ? store.getStoreName() : ("门店" + store.getStoreDepartmentId()));
        return AiResolvedOrgScope.builder()
                .scopeType(org.getScopeType())
                .distributerId(org.getDistributerId())
                .requestDepartmentId(store.getStoreDepartmentId())
                .currentStoreDepartmentId(store.getStoreDepartmentId())
                .currentDepartmentId(store.getStoreDepartmentId())
                .visibleStores(one)
                .visibleWarehouses(org.getVisibleWarehouses() == null ? new ArrayList<>() : new ArrayList<>(org.getVisibleWarehouses()))
                .visibleDepartments(org.getVisibleDepartments() == null ? new ArrayList<>() : new ArrayList<>(org.getVisibleDepartments()))
                .scopeName(org.getScopeName())
                .queryScopeBanner(banner)
                .coverageDetail(org.getCoverageDetail())
                .build();
    }
}
