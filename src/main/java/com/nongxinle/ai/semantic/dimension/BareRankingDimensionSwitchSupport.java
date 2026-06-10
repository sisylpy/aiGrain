package com.nongxinle.ai.semantic.dimension;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelector;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import com.nongxinle.ai.semantic.inheritance.CanonicalContractFrameSupport;
import com.nongxinle.ai.semantic.inheritance.SemanticContractFamilySupport;
import com.nongxinle.ai.semantic.inheritance.StructuredRankingTimeOnlyIntakeSupport;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.intake.SemanticIntakeInput;
import com.nongxinle.ai.semantic.intake.SemanticIntakePrimaryDomain;
import com.nongxinle.ai.semantic.intake.SemanticIntakeQuestionMode;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeStatus;
import com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteResult;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteType;
import lombok.Value;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 菜品排行裸维度切换：Intake / Resolver / Inheritance 唯一入口。
 * 仅消费 Intake 结构化 reason token、previous frame/path/wire/slots、Catalog contract；
 * 不解析 canonicalUserQuery / rawMessage 推断 cost/margin/sales/amount。
 * <p><b>技术债</b>：reason 中的 {@code _to_*_ranking} token 为 schema v1 过渡 marker；
 * 目标为 Intake {@code followUpIntent} 独立字段，Java 不再 parse reason 字符串。
 * 见 {@code docs/ai/semantic-intake-schema-evolution.md}。
 */
public final class BareRankingDimensionSwitchSupport {

    private BareRankingDimensionSwitchSupport() {}

    public static final String FACET_SOURCE_INTAKE_REASON_TOKEN = "intake_reason_token";
    public static final String FACET_SOURCE_UNRESOLVED = "unresolved";
    /** @deprecated 仅保留常量兼容；Java 不再从 canonical 推断 facet */
    @Deprecated
    public static final String FACET_SOURCE_CURRENT_TURN_REASON = FACET_SOURCE_INTAKE_REASON_TOKEN;
    /** @deprecated Java 不再从 canonical 推断 facet */
    @Deprecated
    public static final String FACET_SOURCE_CURRENT_TURN_CANONICAL = "current_turn_canonical";
    /** @deprecated Java 不再用 transition fallback 推断 facet */
    @Deprecated
    public static final String FACET_SOURCE_TRANSITION_FALLBACK = "transition_fallback";

    /** targetFacet 解析结果（供测试 / debug 共用）。 */
    @Value
    static class TargetFacetResolution {
        RankingMetricFacet facet;
        String source;
    }

    static TargetFacetResolution resolveTargetFacetFromIntakeReason(String intakeReason) {
        DimensionSwitchReasonToken token = DimensionSwitchReasonToken.fromReason(intakeReason);
        if (token == null) {
            return new TargetFacetResolution(null, FACET_SOURCE_UNRESOLVED);
        }
        return new TargetFacetResolution(token.facet(), FACET_SOURCE_INTAKE_REASON_TOKEN);
    }

    public static BareRankingDimensionSwitchPlan buildPlan(
            SemanticIntakeInput input, SemanticIntakeResult intake, AiConversationTurnMemory previousTurn) {
        if (intake != null
                && StructuredRankingTimeOnlyIntakeSupport.isStructuredRankingTimeOnlyIntakeReason(
                        intake.getReason())) {
            return inactiveStructuredRankingTimeOnlyFollowUp(intake);
        }
        if (!isBareRankingDimensionSwitch(input, intake)) {
            return inactive();
        }
        DimensionSwitchReasonToken token = DimensionSwitchReasonToken.fromReason(intake.getReason());
        if (token == null) {
            return inactiveUnresolved(intake);
        }
        String previousRankingDomain = resolvePreviousDishRankingDomain(input);
        RankingPolarity polarity = resolveTargetPolarity(previousTurn, input);
        String contractId =
                resolveTargetContractId(token.targetDomain(), token.facet(), polarity);
        if (!StringUtils.hasText(contractId)) {
            return inactiveUnresolved(intake);
        }
        return BareRankingDimensionSwitchPlan.builder()
                .active(true)
                .intakeReason(trim(intake.getReason()))
                .canonicalUserQuery(trim(intake.getCanonicalUserQuery()))
                .previousRankingDomain(previousRankingDomain)
                .previousContractId(
                        SemanticContractFamilySupport.contractIdFromPreviousTurn(previousTurn))
                .targetDomain(token.targetDomain())
                .targetFacet(token.facet())
                .targetFacetResolveSource(FACET_SOURCE_INTAKE_REASON_TOKEN)
                .targetFacetFallbackUsed(false)
                .targetPolarity(polarity)
                .targetContractId(contractId)
                .build();
    }

    public static BareRankingDimensionSwitchPlan buildPlanFromPreviousTurn(
            SemanticIntakeResult intake, AiConversationTurnMemory previousTurn) {
        SemanticIntakeInput input = intakeInputFromPreviousTurn(intake, previousTurn);
        if (input == null) {
            return inactive();
        }
        return buildPlan(input, intake, previousTurn);
    }

    /** Intake 后：纠正 primaryDomain 分裂，与 plan.targetDomain 对齐。 */
    public static SemanticIntakeResult reconcileIntakeDomain(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        BareRankingDimensionSwitchPlan plan = buildPlan(input, intake, null);
        if (!plan.isActive()) {
            return intake;
        }
        String currentPrimary = SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain());
        if (plan.getTargetDomain().equals(currentPrimary)) {
            return intake;
        }
        return promoteRankingDomain(intake, plan.getTargetDomain());
    }

    /** active plan 时 route 与 DomainContractSelector 唯一以 targetDomain 为准。 */
    public static SemanticDomainRouteResult routeForPlan(
            BareRankingDimensionSwitchPlan plan, SemanticDomainRouteResult fallback) {
        if (plan == null || !plan.isActive() || !StringUtils.hasText(plan.getTargetDomain())) {
            return fallback;
        }
        return SemanticDomainRouteResult.builder()
                .routeType(SemanticDomainRouteType.EXPLICIT)
                .primaryDomain(plan.getTargetDomain())
                .usedPreviousContext(true)
                .build();
    }

    /** active plan 时重选 allowed contracts（targetDomain 与 completion 同源）。 */
    public static DomainContractSelectionResult contractSelectionForPlan(
            BareRankingDimensionSwitchPlan plan, DomainContractSelectionResult fallback) {
        if (plan == null || !plan.isActive() || !StringUtils.hasText(plan.getTargetDomain())) {
            return fallback;
        }
        return DomainContractSelector.select(routeForPlan(plan, null));
    }

    public static final String CONTRACT_SELECTION_SOURCE_PLAN = "bare_ranking_dimension_switch_plan";
    public static final String CONTRACT_SELECTION_SOURCE_INTAKE = "intake_domain_route";
    public static final String COMPLETION_CONTRACT_SOURCE_PLAN = "plan_target_contract";
    public static final String COMPLETION_CONTRACT_SOURCE_V2 = "v2_selected_contract";

    /** Plan active 后强制 Business Frame 与 targetContractId 一致（主链唯一决策收口）。 */
    public static AiQuerySemanticParseResult enforcePlanSovereignFrame(
            AiQuerySemanticParseResult current, BareRankingDimensionSwitchPlan plan) {
        if (plan == null || !plan.isActive() || current == null || current.isParseMissing()) {
            return current;
        }
        String targetContractId = lookupActiveContractId(plan);
        if (!StringUtils.hasText(targetContractId)) {
            return current;
        }
        SemanticCapabilityContract contract =
                SemanticContractFamilySupport.lookupActiveContract(
                        targetContractId, plan.getTargetDomain());
        if (contract == null) {
            return current;
        }
        CanonicalContractFrameSupport.CanonicalBusinessFrame frame =
                CanonicalContractFrameSupport.fromActiveContract(
                        contract, AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS);
        if (frame == null) {
            return current;
        }
        AiQuerySemanticParseResult merged =
                CanonicalContractFrameSupport.applyBusinessFrameWhitelist(current, frame);
        merged =
                merged.toBuilder()
                        .semanticDomain(plan.getTargetDomain())
                        .mentionedDishName(null)
                        .build();
        Map<String, Object> trace =
                merged.getMultiTurnInheritanceTrace() != null
                        ? new LinkedHashMap<>(merged.getMultiTurnInheritanceTrace())
                        : new LinkedHashMap<>();
        trace.put("bareRankingPlanSovereignEnforced", true);
        trace.put("planTargetContractId", targetContractId);
        return merged.toBuilder().multiTurnInheritanceTrace(trace).build();
    }

    public static Map<String, Object> buildDebugTrace(
            BareRankingDimensionSwitchPlan plan,
            String contractSelectionSource,
            String completionContractSource,
            boolean anchorSuppressed) {
        Map<String, Object> trace = new LinkedHashMap<>();
        boolean active = plan != null && plan.isActive();
        trace.put("active", active);
        trace.put("anchorSuppressed", anchorSuppressed);
        trace.put("contractSelectionSource", contractSelectionSource);
        trace.put("completionContractSource", completionContractSource);
        if (plan != null) {
            trace.put("intakeReason", plan.getIntakeReason());
            trace.put("canonicalUserQuery", plan.getCanonicalUserQuery());
            trace.put("previousContractId", plan.getPreviousContractId());
            trace.put("targetFacetResolveSource", plan.getTargetFacetResolveSource());
            trace.put("fallbackUsed", plan.isTargetFacetFallbackUsed());
            if (StructuredRankingTimeOnlyIntakeSupport.FOLLOW_UP_PATH_RANKING_TIME_ONLY.equals(
                    plan.getTargetFacetResolveSource())) {
                trace.put(
                        "followUpPath",
                        StructuredRankingTimeOnlyIntakeSupport.FOLLOW_UP_PATH_RANKING_TIME_ONLY);
            }
        }
        if (!active) {
            return trace;
        }
        trace.put(
                "followUpPath",
                StructuredRankingTimeOnlyIntakeSupport.FOLLOW_UP_PATH_DIMENSION_SWITCH);
        trace.put("targetDomain", plan.getTargetDomain());
        trace.put("targetContractId", plan.getTargetContractId());
        trace.put(
                "targetMetric",
                plan.getTargetFacet() != null ? plan.getTargetFacet().name() : null);
        trace.put("resolvedTargetFacet", trace.get("targetMetric"));
        trace.put(
                "targetPolarity",
                plan.getTargetPolarity() != null ? plan.getTargetPolarity().name() : null);
        return trace;
    }

    public static String resolveCompletionContractSource(
            BareRankingDimensionSwitchPlan plan, AiQuerySemanticParseResult sem) {
        if (plan == null || !plan.isActive() || sem == null || sem.getSemanticSlots() == null) {
            return COMPLETION_CONTRACT_SOURCE_V2;
        }
        String selected = blank(sem.getSemanticSlots().getSelectedContractId());
        if (StringUtils.hasText(selected)
                && selected.equalsIgnoreCase(blank(plan.getTargetContractId()))) {
            return COMPLETION_CONTRACT_SOURCE_PLAN;
        }
        return COMPLETION_CONTRACT_SOURCE_V2;
    }

    /**
     * 裸维度切换作用域外：多问题、多域、菜单经营组合筛选、澄清态等。
     * 仅读 Intake 结构化字段，不解析用户 canonical/rawMessage。
     */
    public static boolean isOutsideBareRankingDimensionSwitchScope(SemanticIntakeResult intake) {
        if (intake == null) {
            return true;
        }
        if (intake.getQuestionMode() == SemanticIntakeQuestionMode.MULTI_QUESTION) {
            return true;
        }
        if (Boolean.TRUE.equals(intake.getNeedClarification())) {
            return true;
        }
        return isOutsideBareRankingDimensionSwitchScope(
                intake.getPrimaryDomain(),
                intake.getRouteType(),
                intake.getCandidateDomains(),
                intake.getSubQuestions(),
                intake.getReason());
    }

    /** Intake 协议校验阶段（尚未 map 为 {@link SemanticIntakeResult}）的同等边界。 */
    public static boolean isOutsideBareRankingDimensionSwitchScope(LlmSemanticIntakeParsed parsed) {
        if (parsed == null) {
            return true;
        }
        if (isMultiQuestionMode(parsed.getQuestionMode())) {
            return true;
        }
        if (parsed.isNeedClarification()) {
            return true;
        }
        return isOutsideBareRankingDimensionSwitchScope(
                parsed.getPrimaryDomain(),
                parsed.getRouteType(),
                parsed.getCandidateDomains(),
                parsed.getSubQuestions(),
                parsed.getReason());
    }

    private static boolean isOutsideBareRankingDimensionSwitchScope(
            String primaryDomain,
            String routeType,
            List<String> candidateDomains,
            List<?> subQuestions,
            String reason) {
        String primary = SemanticIntakePrimaryDomain.normalize(primaryDomain);
        if (SemanticIntakePrimaryDomain.MULTI_DOMAIN.equals(primary)
                || SemanticIntakePrimaryDomain.UNKNOWN.equals(primary)
                || SemanticIntakePrimaryDomain.MENU_OPERATION.equals(primary)) {
            return true;
        }
        String route = blank(routeType);
        if (route != null) {
            String rt = route.toUpperCase(Locale.ROOT);
            if ("MULTI_DOMAIN".equals(rt) || "AMBIGUOUS".equals(rt) || "UNKNOWN".equals(rt)) {
                return true;
            }
        }
        if (candidateDomains != null) {
            for (String candidate : candidateDomains) {
                if (SemanticIntakePrimaryDomain.MULTI_DOMAIN.equals(
                        SemanticIntakePrimaryDomain.normalize(candidate))) {
                    return true;
                }
            }
        }
        if (subQuestions != null && !subQuestions.isEmpty()) {
            return true;
        }
        return isCompositeOrKnownGapIntakeReason(reason);
    }

    static boolean isCompositeOrKnownGapIntakeReason(String reason) {
        String normalized = normalizeReason(reason);
        if (normalized == null) {
            return false;
        }
        return normalized.startsWith("multi_question")
                || normalized.startsWith("multi_domain")
                || normalized.startsWith("menu_operation")
                || normalized.startsWith("known_gap")
                || normalized.startsWith("composite_");
    }

    private static boolean isMultiQuestionMode(String questionMode) {
        return questionMode != null
                && "MULTI_QUESTION".equalsIgnoreCase(questionMode.trim());
    }

    public static boolean isBareRankingDimensionSwitch(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (input == null || intake == null || !input.isHasPreviousTurn()) {
            return false;
        }
        if (intake.getStatus() != SemanticIntakeStatus.READY || Boolean.TRUE.equals(intake.getNeedClarification())) {
            return false;
        }
        if (isOutsideBareRankingDimensionSwitchScope(intake)) {
            return false;
        }
        if (hasExplicitNamedDishReference(input, intake)) {
            return false;
        }
        String previousRankingDomain = resolvePreviousDishRankingDomain(input);
        if (!StringUtils.hasText(previousRankingDomain)) {
            return false;
        }
        return isBareDimensionSwitchFollowUp(intake);
    }

    /** 上一轮是否为菜品排行上下文（供 Intake 协议校验，不解析用户原文）。 */
    public static boolean hasPreviousDishRankingTurn(SemanticIntakeInput input) {
        return StringUtils.hasText(resolvePreviousDishRankingDomain(input));
    }

    public static boolean hasDimensionSwitchReasonToken(String intakeReason) {
        return DimensionSwitchReasonToken.fromReason(intakeReason) != null;
    }

    public static boolean isBareRankingDimensionSwitchFromPreviousTurn(
            SemanticIntakeResult intake, AiConversationTurnMemory previousTurn) {
        SemanticIntakeInput input = intakeInputFromPreviousTurn(intake, previousTurn);
        if (input == null) {
            return false;
        }
        return isBareRankingDimensionSwitch(input, intake);
    }

    public static SemanticIntakeInput intakeInputFromPreviousTurn(
            SemanticIntakeResult intake, AiConversationTurnMemory previousTurn) {
        if (intake == null || previousTurn == null) {
            return null;
        }
        return SemanticIntakeInput.builder()
                .hasPreviousTurn(true)
                .normalizedUserMessage(trim(intake.getCanonicalUserQuery()))
                .previousPathCode(trim(previousTurn.getLastPathCode()))
                .previousStructuredIntentDetail(trim(previousTurn.getLastStructuredIntentDetail()))
                .previousMentionedDishName(trim(previousTurn.getLastMentionedDishName()))
                .previousSemanticSlots(previousTurn.getLastSemanticSlots())
                .resultAnchors(previousTurn.getLastResultAnchors())
                .build();
    }

    static String resolveTargetContractId(
            String targetDomain, RankingMetricFacet facet, RankingPolarity polarity) {
        if (!StringUtils.hasText(targetDomain) || facet == null) {
            return null;
        }
        boolean low = polarity == RankingPolarity.LOW;
        if (SemanticIntakePrimaryDomain.DISH_SALES.equals(targetDomain)) {
            return switch (facet) {
                case SALES_AMOUNT ->
                        low ? "dish_sales.amount_ranking_low" : "dish_sales.amount_ranking_high";
                case SOLD_PORTIONS ->
                        low ? "dish_sales.count_ranking_low" : "dish_sales.count_ranking_high";
                default -> null;
            };
        }
        if (SemanticIntakePrimaryDomain.DISH_PROFIT.equals(targetDomain)) {
            return switch (facet) {
                case GROSS_MARGIN_RATE ->
                        low ? "dish_profit.ranking_low_margin" : "dish_profit.ranking_high_margin";
                case GROSS_PROFIT_AMOUNT ->
                        low ? "dish_profit.ranking_low_profit_amount" : "dish_profit.ranking_high_profit_amount";
                case ACTUAL_COST -> "dish_profit.ranking_high_actual_cost";
                default -> null;
            };
        }
        return null;
    }

    static RankingMetricFacet resolveTargetFacet(String intakeReason) {
        return resolveTargetFacetFromIntakeReason(intakeReason).getFacet();
    }

    private static BareRankingDimensionSwitchPlan inactive() {
        return BareRankingDimensionSwitchPlan.builder().active(false).build();
    }

    private static BareRankingDimensionSwitchPlan inactiveStructuredRankingTimeOnlyFollowUp(
            SemanticIntakeResult intake) {
        return BareRankingDimensionSwitchPlan.builder()
                .active(false)
                .intakeReason(intake != null ? trim(intake.getReason()) : null)
                .canonicalUserQuery(intake != null ? trim(intake.getCanonicalUserQuery()) : null)
                .targetFacetResolveSource(
                        StructuredRankingTimeOnlyIntakeSupport.FOLLOW_UP_PATH_RANKING_TIME_ONLY)
                .targetFacetFallbackUsed(false)
                .build();
    }

    /** token 缺失时 inactive，debug 仍保留 intake reason 供 harness 观测 known gap。 */
    private static BareRankingDimensionSwitchPlan inactiveUnresolved(SemanticIntakeResult intake) {
        return BareRankingDimensionSwitchPlan.builder()
                .active(false)
                .intakeReason(intake != null ? trim(intake.getReason()) : null)
                .canonicalUserQuery(intake != null ? trim(intake.getCanonicalUserQuery()) : null)
                .targetFacetResolveSource(FACET_SOURCE_UNRESOLVED)
                .targetFacetFallbackUsed(false)
                .build();
    }

    static RankingPolarity resolveTargetPolarity(
            AiConversationTurnMemory previousTurn, SemanticIntakeInput input) {
        String wire = previousRankingWire(previousTurn, input);
        if (!StringUtils.hasText(wire)) {
            return RankingPolarity.HIGH;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        if (!StringUtils.hasText(canon)) {
            return RankingPolarity.HIGH;
        }
        if (canon.contains("_low")
                || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW.equals(canon)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_RANKING_LOW.equals(canon)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(canon)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_COST_RANKING_LOW.equals(canon)) {
            return RankingPolarity.LOW;
        }
        return RankingPolarity.HIGH;
    }

    private static boolean isBareDimensionSwitchFollowUp(SemanticIntakeResult intake) {
        if (!Boolean.TRUE.equals(intake.getIsFollowUp())) {
            return false;
        }
        String reason = normalizeReason(intake.getReason());
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        if (StructuredRankingTimeOnlyIntakeSupport.isStructuredRankingTimeOnlyIntakeReason(
                intake.getReason())) {
            return false;
        }
        // §38g 裸维度切换：reason 须显式 dimension_switch + _to_*_ranking token，禁止 dish_sales_* 短句 reason 误触发
        if (!reason.contains("dimension_switch")) {
            return false;
        }
        return DimensionSwitchReasonToken.fromReason(intake.getReason()) != null;
    }

    private static boolean hasExplicitNamedDishReference(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        String reason = normalizeReason(intake.getReason());
        if (reason != null && reason.startsWith("named_dish_")) {
            return true;
        }
        return textReferencesStructuredDishEntity(input.getNormalizedUserMessage(), input, intake)
                || textReferencesStructuredDishEntity(intake.getCanonicalUserQuery(), input, intake);
    }

    private static boolean textReferencesStructuredDishEntity(
            String text, SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String t = text.trim();
        String prevDish = trim(input.getPreviousMentionedDishName());
        if (StringUtils.hasText(prevDish) && t.contains(prevDish)) {
            return true;
        }
        List<AiResultAnchor> anchors = input.getResultAnchors();
        if (anchors == null) {
            return false;
        }
        for (AiResultAnchor anchor : anchors) {
            if (anchor == null || !StringUtils.hasText(anchor.getEntityName())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_DISH.equalsIgnoreCase(trim(anchor.getEntityType()))) {
                continue;
            }
            if (t.contains(anchor.getEntityName().trim())) {
                return true;
            }
        }
        return false;
    }

    private static String resolvePreviousDishRankingDomain(SemanticIntakeInput input) {
        String path = trim(input.getPreviousPathCode());
        if (AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(path)) {
            return previousDishSalesRankingDomain(input);
        }
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(path)) {
            return previousDishProfitRankingDomain(input);
        }
        String prevStructured = trim(input.getPreviousStructuredIntentDetail());
        if (StringUtils.hasText(prevStructured)) {
            String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(prevStructured);
            if (AiQuerySemanticLexicon.isDishSalesRankingStructuredDetail(canon)) {
                return SemanticIntakePrimaryDomain.DISH_SALES;
            }
            if (AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(canon)) {
                return SemanticIntakePrimaryDomain.DISH_PROFIT;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart prevSlots = input.getPreviousSemanticSlots();
        if (prevSlots != null && StringUtils.hasText(prevSlots.getStructuredIntentDetailWire())) {
            String wire =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            prevSlots.getStructuredIntentDetailWire().trim());
            if (AiQuerySemanticLexicon.isDishSalesRankingStructuredDetail(wire)) {
                return SemanticIntakePrimaryDomain.DISH_SALES;
            }
            if (AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(wire)) {
                return SemanticIntakePrimaryDomain.DISH_PROFIT;
            }
        }
        return null;
    }

    private static String previousDishSalesRankingDomain(SemanticIntakeInput input) {
        String prevStructured = trim(input.getPreviousStructuredIntentDetail());
        if (StringUtils.hasText(prevStructured)) {
            String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(prevStructured);
            if (AiQuerySemanticLexicon.isDishSalesSingleDishStructuredDetail(canon)) {
                return null;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart prevSlots = input.getPreviousSemanticSlots();
        if (prevSlots != null && StringUtils.hasText(prevSlots.getStructuredIntentDetailWire())) {
            String wire =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            prevSlots.getStructuredIntentDetailWire().trim());
            if (AiQuerySemanticLexicon.isDishSalesSingleDishStructuredDetail(wire)) {
                return null;
            }
        }
        return SemanticIntakePrimaryDomain.DISH_SALES;
    }

    private static String previousDishProfitRankingDomain(SemanticIntakeInput input) {
        String prevStructured = trim(input.getPreviousStructuredIntentDetail());
        if (StringUtils.hasText(prevStructured)) {
            String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(prevStructured);
            if (AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(canon)) {
                return SemanticIntakePrimaryDomain.DISH_PROFIT;
            }
            if (AiQuerySemanticLexicon.isSingleDishMetricOrReasonStructuredDetail(canon)) {
                return null;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart prevSlots = input.getPreviousSemanticSlots();
        if (prevSlots != null && StringUtils.hasText(prevSlots.getStructuredIntentDetailWire())) {
            String wire =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            prevSlots.getStructuredIntentDetailWire().trim());
            if (AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(wire)) {
                return SemanticIntakePrimaryDomain.DISH_PROFIT;
            }
            if (AiQuerySemanticLexicon.isSingleDishMetricOrReasonStructuredDetail(wire)) {
                return null;
            }
        }
        return null;
    }

    private static String previousRankingWire(
            AiConversationTurnMemory previousTurn, SemanticIntakeInput input) {
        if (previousTurn != null) {
            AiQuerySemanticParseResult.SemanticSlotsPart slots = previousTurn.getLastSemanticSlots();
            if (slots != null && StringUtils.hasText(slots.getStructuredIntentDetailWire())) {
                return slots.getStructuredIntentDetailWire().trim();
            }
            if (StringUtils.hasText(previousTurn.getLastStructuredIntentDetail())) {
                return previousTurn.getLastStructuredIntentDetail().trim();
            }
        }
        if (input != null && StringUtils.hasText(input.getPreviousStructuredIntentDetail())) {
            return input.getPreviousStructuredIntentDetail().trim();
        }
        return null;
    }

    private static SemanticIntakeResult promoteRankingDomain(
            SemanticIntakeResult intake, String targetDomain) {
        String reason = trim(intake.getReason());
        if (!StringUtils.hasText(reason)) {
            reason = "dimension_switch_ranking_reconciled";
        } else if (!reason.endsWith("_reconciled")) {
            reason = reason + "_reconciled";
        }
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.READY)
                .questionMode(intake.getQuestionMode())
                .normalizationType(intake.getNormalizationType())
                .canonicalUserQuery(intake.getCanonicalUserQuery())
                .isFollowUp(intake.getIsFollowUp())
                .usedPreviousContext(Boolean.TRUE)
                .primaryDomain(targetDomain)
                .candidateDomains(List.of(targetDomain))
                .routeType("EXPLICIT")
                .confidence(intake.getConfidence())
                .needClarification(false)
                .clarificationQuestion(null)
                .reason(reason)
                .subQuestions(intake.getSubQuestions())
                .promptId(intake.getPromptId())
                .llmRawText(intake.getLlmRawText())
                .parseError(intake.getParseError())
                .intakeRepairAttempted(intake.getIntakeRepairAttempted())
                .intakeRepairSuccess(intake.getIntakeRepairSuccess())
                .intakeRepairReason(intake.getIntakeRepairReason())
                .build();
    }

    /** Intake 结构化 reason token → 目标域 / 指标面（仅 wire suffix，不含中文 NL）。 */
    enum DimensionSwitchReasonToken {
        TO_AMOUNT(
                SemanticIntakePrimaryDomain.DISH_SALES,
                RankingMetricFacet.SALES_AMOUNT,
                "_to_amount_ranking",
                "amount_ranking",
                "sales_amount"),
        TO_COST(
                SemanticIntakePrimaryDomain.DISH_PROFIT,
                RankingMetricFacet.ACTUAL_COST,
                "_to_cost_ranking",
                "cost_ranking",
                "_actual_cost",
                "actual_cost"),
        TO_MARGIN(
                SemanticIntakePrimaryDomain.DISH_PROFIT,
                RankingMetricFacet.GROSS_MARGIN_RATE,
                "_to_margin_ranking",
                "margin_ranking",
                "_gross_margin",
                "gross_margin"),
        TO_PROFIT_AMOUNT(
                SemanticIntakePrimaryDomain.DISH_PROFIT,
                RankingMetricFacet.GROSS_PROFIT_AMOUNT,
                "_to_profit_amount_ranking",
                "profit_amount_ranking",
                "_profit_amount",
                "gross_profit_amount"),
        TO_SALES(
                SemanticIntakePrimaryDomain.DISH_SALES,
                RankingMetricFacet.SOLD_PORTIONS,
                "_to_sales_ranking",
                "sales_ranking",
                "_dish_sales",
                "sold_portions");

        private final String targetDomain;
        private final RankingMetricFacet facet;
        private final String[] markers;

        DimensionSwitchReasonToken(
                String targetDomain,
                RankingMetricFacet facet,
                String... markers) {
            this.targetDomain = targetDomain;
            this.facet = facet;
            this.markers = markers;
        }

        String targetDomain() {
            return targetDomain;
        }

        RankingMetricFacet facet() {
            return facet;
        }

        boolean matchesReason(String normalizedLowerReason) {
            if (!StringUtils.hasText(normalizedLowerReason)) {
                return false;
            }
            for (String marker : markers) {
                if (normalizedLowerReason.contains(marker.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }

        static DimensionSwitchReasonToken fromReason(String reason) {
            String r = normalizeReason(reason);
            if (!StringUtils.hasText(r)) {
                return null;
            }
            // 长 token 优先，避免 `_amount_` 误匹配 `_to_profit_amount_ranking` 子串
            for (DimensionSwitchReasonToken token : RESOLUTION_ORDER) {
                if (token.matchesReason(r)) {
                    return token;
                }
            }
            return null;
        }

        private static final DimensionSwitchReasonToken[] RESOLUTION_ORDER = {
            TO_PROFIT_AMOUNT,
            TO_COST,
            TO_MARGIN,
            TO_SALES,
            TO_AMOUNT
        };
    }

    /** 校验 catalog 中 contract 存在（Applier 兜底查找 allowed 列表时使用）。 */
    public static String lookupActiveContractId(BareRankingDimensionSwitchPlan plan) {
        if (plan == null || !plan.isActive() || !StringUtils.hasText(plan.getTargetContractId())) {
            return null;
        }
        if (SemanticContractFamilySupport.lookupActiveContract(
                        plan.getTargetContractId(), plan.getTargetDomain())
                != null) {
            return plan.getTargetContractId();
        }
        return plan.getTargetContractId();
    }

    private static String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        return reason.trim().toLowerCase(Locale.ROOT);
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String blank(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
