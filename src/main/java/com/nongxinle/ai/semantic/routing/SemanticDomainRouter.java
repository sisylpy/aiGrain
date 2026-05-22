package com.nongxinle.ai.semantic.routing;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.contract.DomainRoutingContract;
import com.nongxinle.ai.semantic.contract.DomainRoutingContractCatalog;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Step 1 业务域路由器：基于 {@link DomainRoutingContractCatalog} businessObjects + taskType 信号计分选域。
 * <p>不输出 wire / answerPlanType / Tool；不做业务执行。
 */
public final class SemanticDomainRouter {

    public static final SemanticDomainRouter INSTANCE = new SemanticDomainRouter();

    private static final double EXPLICIT_MIN_SCORE = 2.0;
    private static final double AMBIGUOUS_SCORE_DELTA = 1.0;
    private static final double TASK_TYPE_BONUS = 1.0;

    private static final Map<String, String> PATH_CODE_TO_DOMAIN =
            Map.ofEntries(
                    Map.entry(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW, "PURCHASE"),
                    Map.entry(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW, "REVENUE"),
                    Map.entry(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY, "STOCK_REDUCE"),
                    Map.entry(AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK, "WAREHOUSE"),
                    Map.entry(AiResolvedQueryIntent.PATH_DISH_SALES_QUERY, "DISH_SALES"),
                    Map.entry(AiResolvedQueryIntent.PATH_DISH_PROFIT, "DISH_PROFIT"),
                    Map.entry(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS, "BUSINESS_DIAGNOSIS"),
                    Map.entry(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW, "REVENUE"));

    private SemanticDomainRouter() {
    }

    public SemanticDomainRouteResult route(SemanticDomainRouterInput input) {
        if (input == null || !StringUtils.hasText(input.getRewrittenUserMessage())) {
            return emptyUnknown("empty_message");
        }
        String message = input.getRewrittenUserMessage().trim();
        Set<String> taskSignals = DomainRoutingTaskTypeSignals.detect(message);
        List<DomainRouteScore> scores = scoreAllDomains(message, taskSignals);
        scores.sort(Comparator.comparingDouble(DomainRouteScore::score).reversed());

        DomainRouteScore top = scores.isEmpty() ? null : scores.get(0);
        DomainRouteScore second = scores.size() > 1 ? scores.get(1) : null;

        if (top != null && top.score() >= EXPLICIT_MIN_SCORE) {
            boolean clearWinner =
                    second == null || top.score() - second.score() >= AMBIGUOUS_SCORE_DELTA;
            if (clearWinner) {
                return buildResult(
                        SemanticDomainRouteType.EXPLICIT,
                        top.domainCode(),
                        scores,
                        top.matchedObjects(),
                        false,
                        false,
                        List.of("business_object_and_task_match:" + top.domainCode()));
            }
            if (shouldRouteExplicitDomainWin(top, second, taskSignals)) {
                return buildResult(
                        SemanticDomainRouteType.EXPLICIT,
                        top.domainCode(),
                        scores,
                        top.matchedObjects(),
                        false,
                        false,
                        List.of("domain_specific_win:" + top.domainCode()));
            }
            if (shouldRouteExplicitStrongDomainWithTask(top, second, taskSignals)) {
                return buildResult(
                        SemanticDomainRouteType.EXPLICIT,
                        top.domainCode(),
                        scores,
                        top.matchedObjects(),
                        false,
                        false,
                        List.of("strong_domain_task_match:" + top.domainCode()));
            }
        }

        DomainRouteScore singleDomain = findSingleDomainCandidate(scores);
        if (singleDomain != null
                && !taskSignals.isEmpty()
                && taskTypeSupported(singleDomain.domainCode(), taskSignals)
                && !hasCloseCompetingDomain(scores, singleDomain)) {
            return buildResult(
                    SemanticDomainRouteType.EXPLICIT,
                    singleDomain.domainCode(),
                    scores,
                    singleDomain.matchedObjects(),
                    false,
                    false,
                    List.of("single_domain_task_signal:" + singleDomain.domainCode()));
        }

        String inheritedDomain = domainFromPreviousTurn(input.getPreviousTurn());
        if (StringUtils.hasText(inheritedDomain)) {
            List<String> matched = findMatchedObjects(message, inheritedDomain);
            return buildResult(
                    SemanticDomainRouteType.INHERITED,
                    inheritedDomain,
                    scores,
                    matched,
                    true,
                    false,
                    List.of("previous_path:" + trimPath(input.getPreviousTurn())));
        }

        if (top != null && top.score() > 0) {
            List<String> candidates = candidateDomainsAbove(scores, top.score() - AMBIGUOUS_SCORE_DELTA);
            return SemanticDomainRouteResult.builder()
                    .routeType(SemanticDomainRouteType.AMBIGUOUS)
                    .candidateDomains(candidates)
                    .confidence(normalizeConfidence(top.score(), second != null ? second.score() : 0))
                    .reasonCode("ambiguous_business_object_scores")
                    .matchedBusinessObjects(top.matchedObjects())
                    .usedPreviousContext(false)
                    .needsClarification(true)
                    .build();
        }

        return emptyUnknown("no_domain_signal");
    }

    private static DomainRouteScore findSingleDomainCandidate(List<DomainRouteScore> scores) {
        DomainRouteScore candidate = null;
        for (DomainRouteScore score : scores) {
            if (score.score() <= 0 || score.matchedObjects().isEmpty()) {
                continue;
            }
            if (candidate != null) {
                return null;
            }
            candidate = score;
        }
        return candidate;
    }

    private static boolean hasCloseCompetingDomain(List<DomainRouteScore> scores, DomainRouteScore winner) {
        for (DomainRouteScore score : scores) {
            if (score.domainCode().equals(winner.domainCode())) {
                continue;
            }
            if (score.score() <= 0 || score.matchedObjects().isEmpty()) {
                continue;
            }
            if (winner.score() - score.score() < AMBIGUOUS_SCORE_DELTA) {
                return true;
            }
        }
        return false;
    }

    private static boolean taskTypeSupported(String domainCode, Set<String> taskSignals) {
        DomainRoutingContract contract = DomainRoutingContractCatalog.findByDomainCode(domainCode);
        return contract != null
                && DomainRoutingTaskTypeSignals.intersectsSupported(taskSignals, contract.getSupportedTaskTypes());
    }

    /**
     * 明确域 + 任务信号胜出：域专属 businessObject 压过 BUSINESS_DIAGNOSIS；泛词-only 竞争者不拉平。
     */
    private static boolean shouldRouteExplicitDomainWin(
            DomainRouteScore top, DomainRouteScore second, Set<String> taskSignals) {
        if (top == null
                || top.score() < EXPLICIT_MIN_SCORE
                || !DomainRoutingBusinessObjectScorer.hasStrongMatchedObject(top.matchedObjects())) {
            return false;
        }
        if (second == null || second.score() <= 0) {
            return true;
        }
        if (taskSignals.contains(DomainRoutingTaskTypeSignals.ANOMALY)
                && !"BUSINESS_DIAGNOSIS".equals(top.domainCode())
                && "BUSINESS_DIAGNOSIS".equals(second.domainCode())
                && taskTypeSupported(top.domainCode(), taskSignals)
                && top.score() > second.score()) {
            return true;
        }
        return DomainRoutingBusinessObjectScorer.matchedObjectsAllWeak(second.matchedObjects())
                && top.score() > second.score();
    }

    /**
     * 明确域词 + 任务信号：竞争者仅有弱泛词或未命中域词时，仍应 EXPLICIT（如「出库情况」「菜品销售情况」）。
     */
    private static boolean shouldRouteExplicitStrongDomainWithTask(
            DomainRouteScore top, DomainRouteScore second, Set<String> taskSignals) {
        if (top == null
                || top.score() < EXPLICIT_MIN_SCORE
                || !DomainRoutingBusinessObjectScorer.hasStrongMatchedObject(top.matchedObjects())) {
            return false;
        }
        if (!taskTypeSupported(top.domainCode(), taskSignals)) {
            return false;
        }
        if (second == null || second.score() <= 0 || second.matchedObjects().isEmpty()) {
            return true;
        }
        if (DomainRoutingBusinessObjectScorer.matchedObjectsAllWeak(second.matchedObjects())) {
            return top.score() > second.score() * 0.5;
        }
        return false;
    }

    private static double adjustBusinessDiagnosisScore(
            String domainCode, double score, LinkedHashSet<String> matched, Set<String> taskSignals) {
        if (!"BUSINESS_DIAGNOSIS".equals(domainCode)) {
            return score;
        }
        boolean compositeDiagnosis =
                taskSignals.contains(DomainRoutingTaskTypeSignals.DIAGNOSIS)
                        || taskSignals.contains(DomainRoutingTaskTypeSignals.OVERVIEW);
        if (compositeDiagnosis) {
            return score;
        }
        if (DomainRoutingBusinessObjectScorer.matchedObjectsAllWeak(List.copyOf(matched))) {
            return score * 0.5;
        }
        return score;
    }

    private static SemanticDomainRouteResult buildResult(
            SemanticDomainRouteType routeType,
            String primaryDomain,
            List<DomainRouteScore> scores,
            List<String> matchedObjects,
            boolean usedPreviousContext,
            boolean needsClarification,
            List<String> reasonCodes) {
        double topScore = scores.isEmpty() ? 0 : scores.get(0).score();
        double secondScore = scores.size() > 1 ? scores.get(1).score() : 0;
        SemanticDomainRouteResult.SemanticDomainRouteResultBuilder b =
                SemanticDomainRouteResult.builder()
                        .routeType(routeType)
                        .primaryDomain(primaryDomain)
                        .confidence(normalizeConfidence(topScore, secondScore))
                        .matchedBusinessObjects(matchedObjects)
                        .usedPreviousContext(usedPreviousContext)
                        .needsClarification(needsClarification);
        if (reasonCodes != null) {
            reasonCodes.forEach(b::reasonCode);
        }
        return b.candidateDomains(buildCandidateDomains(scores, primaryDomain)).build();
    }

    /** Debug hygiene：primaryDomain 与 score 列表去重，保持 primary 优先顺序。 */
    private static List<String> buildCandidateDomains(List<DomainRouteScore> scores, String primaryDomain) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> ordered = new ArrayList<>();
        if (StringUtils.hasText(primaryDomain)) {
            String pd = primaryDomain.trim().toUpperCase(Locale.ROOT);
            seen.add(pd);
            ordered.add(pd);
        }
        for (DomainRouteScore s : scores) {
            if (s.score() > 0 && seen.add(s.domainCode())) {
                ordered.add(s.domainCode());
            }
        }
        return List.copyOf(ordered);
    }

    private static SemanticDomainRouteResult emptyUnknown(String reason) {
        return SemanticDomainRouteResult.builder()
                .routeType(SemanticDomainRouteType.UNKNOWN)
                .confidence(0.0)
                .reasonCode(reason)
                .needsClarification(true)
                .build();
    }

    private static List<DomainRouteScore> scoreAllDomains(String message, Set<String> taskSignals) {
        List<DomainRouteScore> out = new ArrayList<>();
        for (DomainRoutingContract contract : DomainRoutingContractCatalog.listDomainRoutingContracts()) {
            out.add(scoreDomain(message, contract, taskSignals));
        }
        return out;
    }

    private static DomainRouteScore scoreDomain(
            String message, DomainRoutingContract contract, Set<String> taskSignals) {
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        double score = 0;
        if (contract.getBusinessObjects() != null) {
            for (String obj : contract.getBusinessObjects()) {
                if (!StringUtils.hasText(obj)) {
                    continue;
                }
                String token = obj.trim();
                if (message.contains(token)) {
                    matched.add(token);
                    score += DomainRoutingBusinessObjectScorer.scoreToken(token);
                }
            }
        }
        if (!matched.isEmpty()) {
            score += taskTypeScoreBonus(taskSignals, contract.getSupportedTaskTypes());
            score = adjustBusinessDiagnosisScore(contract.getDomainCode(), score, matched, taskSignals);
        }
        return new DomainRouteScore(contract.getDomainCode(), score, List.copyOf(matched));
    }

    private static double taskTypeScoreBonus(Set<String> taskSignals, List<String> supportedTaskTypes) {
        if (taskSignals.isEmpty() || supportedTaskTypes == null || supportedTaskTypes.isEmpty()) {
            return 0;
        }
        double bonus = 0;
        for (String supported : supportedTaskTypes) {
            if (!StringUtils.hasText(supported)) {
                continue;
            }
            if (taskSignals.contains(supported.trim().toUpperCase(Locale.ROOT))) {
                bonus += TASK_TYPE_BONUS;
            }
        }
        return bonus;
    }

    private static List<String> findMatchedObjects(String message, String domainCode) {
        DomainRoutingContract contract = DomainRoutingContractCatalog.findByDomainCode(domainCode);
        if (contract == null || contract.getBusinessObjects() == null) {
            return List.of();
        }
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (String obj : contract.getBusinessObjects()) {
            if (StringUtils.hasText(obj) && message.contains(obj.trim())) {
                matched.add(obj.trim());
            }
        }
        return List.copyOf(matched);
    }

    private static List<String> candidateDomainsAbove(List<DomainRouteScore> scores, double minScore) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (DomainRouteScore s : scores) {
            if (s.score() >= minScore && s.score() > 0) {
                out.add(s.domainCode());
            }
        }
        return List.copyOf(out);
    }

    private static String domainFromPreviousTurn(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null || !StringUtils.hasText(previousTurn.getLastPathCode())) {
            return null;
        }
        return PATH_CODE_TO_DOMAIN.get(previousTurn.getLastPathCode().trim());
    }

    private static String trimPath(AiConversationTurnMemory previousTurn) {
        return previousTurn != null && StringUtils.hasText(previousTurn.getLastPathCode())
                ? previousTurn.getLastPathCode().trim()
                : null;
    }

    private static double normalizeConfidence(double topScore, double secondScore) {
        if (topScore <= 0) {
            return 0.0;
        }
        double margin = topScore - secondScore;
        double raw = 0.55 + Math.min(0.4, topScore / 20.0) + Math.min(0.05, margin / 10.0);
        return Math.min(0.99, Math.max(0.0, raw));
    }

    private record DomainRouteScore(String domainCode, double score, List<String> matchedObjects) {}
}
