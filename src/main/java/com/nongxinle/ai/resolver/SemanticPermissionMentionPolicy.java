package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolver;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.security.AiAnswerBoundary;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.matrix.DishSalesSemanticCapabilityMatrix;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 语义点名实体与可见组织范围的权限边界（门店 / 菜品）；不处理 Tool / AnswerPlan。
 */
@Component
public class SemanticPermissionMentionPolicy {

    @Value("${ai.agent.querySemanticLlm.minConfidence:0.55}")
    private double querySemanticMinConfidence;

    public Optional<AiPermissionDenied> maybeDenialForSemanticMentionsOutsideVisibleStores(
            AiResolvedQueryContext rq) {
        if (rq == null || rq.isNeedSemanticClarification()) {
            return Optional.empty();
        }
        AiResolvedOrgScope org = rq.getOrgScope();
        if (org == null) {
            return Optional.empty();
        }
        String scopeType = org.getScopeType();
        if (!AiResolvedOrgScope.SCOPE_STORE.equals(scopeType)
                && !AiResolvedOrgScope.SCOPE_PURCHASER.equals(scopeType)) {
            return Optional.empty();
        }
        List<AiStoreScopeDTO> visible = org.getVisibleStores();
        if (visible == null || visible.isEmpty()) {
            return Optional.empty();
        }
        AiQuerySemanticParseResult sem = rq.getQuerySemanticParse();
        if (sem == null || sem.isParseMissing() || !sem.isStructuralConfidenceOk(querySemanticMinConfidence)) {
            return Optional.empty();
        }
        List<String> mentions = sem.effectiveMentionedStoreNames();
        if (mentions == null || mentions.isEmpty()) {
            return Optional.empty();
        }
        List<AiStoreScopeDTO> lexicalCandidates =
                visible.stream()
                        .filter(s -> s != null && StringUtils.hasText(s.getStoreName()))
                        .collect(Collectors.toList());
        if (lexicalCandidates.isEmpty()) {
            return Optional.empty();
        }
        LinkedHashSet<String> outside = new LinkedHashSet<>();
        for (String raw : mentions) {
            AiFollowUpResolver.SemanticLexicalSingleStoreHit hit =
                    AiFollowUpResolver.matchSemanticSingleStoreLexically(raw.trim(), lexicalCandidates);
            if (hit.kind() == AiFollowUpResolver.SemanticLexicalSingleStoreKind.NONE) {
                outside.add(raw.trim());
            }
        }
        if (outside.isEmpty()) {
            return Optional.empty();
        }
        LinkedHashSet<String> visNamesOrdered = new LinkedHashSet<>();
        for (AiStoreScopeDTO v : visible) {
            if (v != null && StringUtils.hasText(v.getStoreName())) {
                visNamesOrdered.add(v.getStoreName().trim());
            }
        }
        if (visNamesOrdered.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                AiAnswerBoundary.forMentionedStoresOutsideVisibleScope(
                        List.copyOf(outside), List.copyOf(visNamesOrdered)));
    }

    public String resolveMentionedDishName(
            AiResolvedQueryIntent qi,
            AiConversationTurnMemory previousTurn,
            AiResolvedOrgScope mergedOrg,
            com.nongxinle.ai.conversation.AiFollowUpResolution followUp,
            AiQuerySemanticParseResult semLlm) {
        if (qi == null) {
            return null;
        }
        String path = qi.getPathCode();
        boolean dishProfitPath = AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(path);
        boolean diagnosisSingleDishTail = AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(path)
                && AiQuerySemanticLexicon.isSingleDishMetricOrReasonStructuredDetail(qi.getStructuredIntentDetail());
        if (!dishProfitPath && !diagnosisSingleDishTail) {
            return null;
        }
        if (semLlm != null && StringUtils.hasText(semLlm.getMentionedDishName())) {
            String dish = discardIfHintIsScopedStoreName(semLlm.getMentionedDishName().trim(), mergedOrg, followUp);
            if (StringUtils.hasText(dish)) {
                return AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit(dish);
            }
        }
        if (AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(qi.getStructuredIntentDetail())) {
            return null;
        }
        if (previousTurn != null && StringUtils.hasText(previousTurn.getLastMentionedDishName())) {
            String inherited =
                    discardIfHintIsScopedStoreName(
                            previousTurn.getLastMentionedDishName().trim(), mergedOrg, followUp);
            if (StringUtils.hasText(inherited)) {
                return AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit(inherited);
            }
        }
        if (previousTurn != null && previousTurn.getLastResultAnchors() != null) {
            AiResultAnchor salesAnchor =
                    DishSalesSemanticCapabilityMatrix.resolveUniqueDishSalesRankingAnchor(
                            previousTurn.getLastResultAnchors());
            if (salesAnchor != null && StringUtils.hasText(salesAnchor.getEntityName())) {
                return AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit(
                        salesAnchor.getEntityName().trim());
            }
        }
        return null;
    }

    private static String discardIfHintIsScopedStoreName(
            String dishHint,
            AiResolvedOrgScope org,
            com.nongxinle.ai.conversation.AiFollowUpResolution followUp) {
        if (!StringUtils.hasText(dishHint)) {
            return null;
        }
        if (equalsNormalizedStoreLabel(
                dishHint, followUp != null ? followUp.getStoreScopeFollowUpMentionedName() : null)) {
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

    private static boolean equalsNormalizedStoreLabel(String dishHint, String storeLabel) {
        if (!StringUtils.hasText(dishHint) || !StringUtils.hasText(storeLabel)) {
            return false;
        }
        String a = dishHint.replace(" ", "").trim();
        String b = storeLabel.replace(" ", "").trim();
        return !a.isEmpty() && a.equals(b);
    }
}
