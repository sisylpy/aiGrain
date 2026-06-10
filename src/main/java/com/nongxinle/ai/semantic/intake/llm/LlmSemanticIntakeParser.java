package com.nongxinle.ai.semantic.intake.llm;

import com.nongxinle.ai.gateway.LlmGateway;
import com.nongxinle.ai.prompt.AiPromptIds;
import com.nongxinle.ai.prompt.AiPromptService;
import com.nongxinle.ai.semantic.intake.*;
import com.nongxinle.ai.inventory.WarehouseNearExpiryRiskFilterSupport;
import com.nongxinle.ai.semantic.intake.grounding.CoverDaysEntityGroundingService;
import com.nongxinle.ai.semantic.intake.grounding.CoverDaysEntityType;
import com.nongxinle.ai.semantic.dimension.BareRankingDimensionSwitchSupport;
import com.nongxinle.ai.semantic.inheritance.StructuredRankingTimeOnlyIntakeSupport;
import com.nongxinle.ai.semantic.SemanticLlmFailureClassification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * SemanticIntake LLM：话术规范化 + 一级业务域选择 + 多问题识别。
 * Java 仅做 schema/enum 校验，不通过关键词修正 domain。
 * <p>协议层暂通过 {@code reason} wire token 校验维度切换（过渡方案）；见
 * {@code docs/ai/semantic-intake-schema-evolution.md}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmSemanticIntakeParser {

    private final LlmGateway llmGateway;
    private final AiPromptService aiPromptService;
    private final CoverDaysEntityGroundingService coverDaysEntityGroundingService;

    @Value("${ai.agent.semanticIntakeLlm.minConfidence:0.55}")
    private double minConfidence;

    public SemanticIntakeResult parse(SemanticIntakeInput input) {
        if (input == null || !StringUtils.hasText(input.getNormalizedUserMessage())) {
            return SemanticIntakeResult.invalid(
                    "empty_message", AiPromptIds.SEMANTIC_INTAKE_V1, null, "empty_message");
        }
        String pid = AiPromptIds.SEMANTIC_INTAKE_V1;
        String systemPrompt;
        try {
            systemPrompt = aiPromptService.require(pid);
        } catch (RuntimeException ex) {
            log.warn("[LlmSemanticIntakeParser] prompt load failed: {}", ex.toString());
            return SemanticIntakeResult.invalid(
                    "prompt_load_failed", pid, null, ex.getClass().getSimpleName());
        }
        String userPayload = LlmSemanticIntakePromptBuilder.toUserJson(input);
        String raw = null;
        try {
            raw = llmGateway.chatSimple(systemPrompt, userPayload);
            if (!StringUtils.hasText(raw)) {
                return SemanticIntakeResult.invalid(
                        "empty_llm_response", pid, truncateRaw(raw), "empty_llm_response");
            }
            LlmSemanticIntakeParsed parsed = LlmSemanticIntakeJsonParser.parseRaw(raw);
            if (parsed.isParseFailed()) {
                return SemanticIntakeResult.invalid(
                        "parse_failed", pid, truncateRaw(raw), parsed.getParseError());
            }
            List<String> enumErrors = collectEnumErrors(parsed, input);
            if (!enumErrors.isEmpty()) {
                return parseWithProtocolRepair(input, pid, systemPrompt, truncateRaw(raw), parsed, enumErrors);
            }
            return applyIntakeReconcilers(input, mapParsed(input, pid, truncateRaw(raw), parsed));
        } catch (Exception e) {
            log.warn("[LlmSemanticIntakeParser] llm intake failed: {}", e.toString());
            return invalidIntake(
                    "llm_exception", pid, truncateRaw(raw), e.getClass().getSimpleName());
        }
    }

    private static SemanticIntakeResult invalidIntake(
            String reason, String promptId, String raw, String parseError) {
        return SemanticIntakeResult.invalid(reason, promptId, raw, parseError);
    }

    private SemanticIntakeResult mapParsed(
            SemanticIntakeInput input,
            String promptId,
            String rawObs,
            LlmSemanticIntakeParsed parsed) {
        if (!LlmSemanticIntakeJsonParser.isValidQuestionMode(parsed.getQuestionMode())) {
            return invalidFromParsed(parsed, promptId, rawObs, "invalid_question_mode");
        }
        if (!LlmSemanticIntakeJsonParser.isValidNormalizationType(parsed.getNormalizationType())) {
            return invalidFromParsed(parsed, promptId, rawObs, "invalid_normalization_type");
        }
        if (!StringUtils.hasText(parsed.getCanonicalUserQuery())) {
            return invalidFromParsed(parsed, promptId, rawObs, "missing_canonical_user_query");
        }
        if (!LlmSemanticIntakeJsonParser.isValidRouteType(parsed.getRouteType())) {
            return invalidFromParsed(parsed, promptId, rawObs, "invalid_route_type");
        }
        if (!LlmSemanticIntakeJsonParser.isValidPrimaryDomain(parsed.getPrimaryDomain())) {
            return invalidFromParsed(parsed, promptId, rawObs, "invalid_primary_domain");
        }
        boolean deferClarificationToGrounding =
                CoverDaysEntityGroundingService.signalsCoverDaysParsed(parsed);
        if (!deferClarificationToGrounding
                && (parsed.getConfidence() == null || parsed.getConfidence() < minConfidence)) {
            return needClarificationFromParsed(
                    parsed,
                    promptId,
                    rawObs,
                    "low_confidence",
                    firstNonBlank(
                            parsed.getClarificationQuestion(), "能再具体说一下您想问的内容吗？"));
        }
        if (!deferClarificationToGrounding
                && parsed.isNeedClarification()
                && StringUtils.hasText(parsed.getClarificationQuestion())) {
            return needClarificationFromParsed(
                    parsed, promptId, rawObs, parsed.getReason(), parsed.getClarificationQuestion().trim());
        }

        SemanticIntakeQuestionMode questionMode =
                SemanticIntakeQuestionMode.valueOf(parsed.getQuestionMode().trim().toUpperCase());
        if (questionMode == SemanticIntakeQuestionMode.MULTI_QUESTION) {
            String question =
                    StringUtils.hasText(parsed.getClarificationQuestion())
                            ? parsed.getClarificationQuestion().trim()
                            : "您一次问了多个问题，请先告诉我您想先查哪一个方向？";
            return SemanticIntakeResult.builder()
                    .status(SemanticIntakeStatus.NEED_CLARIFICATION)
                    .questionMode(questionMode)
                    .normalizationType(parseNormalizationType(parsed.getNormalizationType()))
                    .canonicalUserQuery(parsed.getCanonicalUserQuery().trim())
                    .isFollowUp(parsed.isFollowUp())
                    .usedPreviousContext(parsed.isUsedPreviousContext())
                    .primaryDomain(SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain()))
                    .candidateDomains(parsed.getCandidateDomains())
                    .routeType(parsed.getRouteType().trim().toUpperCase())
                    .confidence(parsed.getConfidence())
                    .needClarification(true)
                    .clarificationQuestion(question)
                    .reason(firstNonBlank(parsed.getReason(), "multi_question"))
                    .contextRelation(contextRelationFromParsed(parsed))
                    .warehouseInventorySemantics(warehouseSemanticsFromParsed(parsed))
                .expiryRiskFilter(parsed.getExpiryRiskFilter())
                .coverDaysEntityType(coverDaysEntityTypeForIntake(parsed.getCoverDaysEntityType()))
                .coverDaysEntityName(trimCoverDaysEntityName(parsed.getCoverDaysEntityName()))
                .subQuestions(parsed.getSubQuestions())
                .promptId(promptId)
                .llmRawText(rawObs)
                .build();
        }

        String routeType = parsed.getRouteType().trim().toUpperCase();
        String primary = SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain());
        if (!deferClarificationToGrounding
                && ("AMBIGUOUS".equals(routeType)
                        || "UNKNOWN".equals(routeType)
                        || "MULTI_DOMAIN".equals(routeType)
                        || SemanticIntakePrimaryDomain.MULTI_DOMAIN.equals(primary)
                        || SemanticIntakePrimaryDomain.UNKNOWN.equals(primary)
                        || !SemanticIntakePrimaryDomain.isExecutable(primary))) {
            String question =
                    StringUtils.hasText(parsed.getClarificationQuestion())
                            ? parsed.getClarificationQuestion().trim()
                            : "请问您想查的是营业额、采购、库存还是其他哪一类数据？";
            return needClarificationFromParsed(
                    parsed, promptId, rawObs, firstNonBlank(parsed.getReason(), routeType), question);
        }

        if (deferClarificationToGrounding
                && (!SemanticIntakePrimaryDomain.isExecutable(primary)
                        || SemanticIntakePrimaryDomain.UNKNOWN.equals(primary)
                        || SemanticIntakePrimaryDomain.MULTI_DOMAIN.equals(primary))) {
            primary = SemanticIntakePrimaryDomain.DISH_COST;
            routeType = "EXPLICIT";
        }

        return buildReadyIntakeFromParsed(parsed, promptId, rawObs, questionMode, routeType, primary);
    }

    private SemanticIntakeResult buildReadyIntakeFromParsed(
            LlmSemanticIntakeParsed parsed,
            String promptId,
            String rawObs,
            SemanticIntakeQuestionMode questionMode,
            String routeType,
            String primary) {
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.READY)
                .questionMode(questionMode)
                .normalizationType(parseNormalizationType(parsed.getNormalizationType()))
                .canonicalUserQuery(parsed.getCanonicalUserQuery().trim())
                .isFollowUp(parsed.isFollowUp())
                .usedPreviousContext(parsed.isUsedPreviousContext())
                .primaryDomain(primary)
                .candidateDomains(parsed.getCandidateDomains())
                .routeType(routeType)
                .confidence(parsed.getConfidence())
                .needClarification(false)
                .clarificationQuestion(null)
                .reason(parsed.getReason())
                .contextRelation(contextRelationFromParsed(parsed))
                .warehouseInventorySemantics(warehouseSemanticsFromParsed(parsed))
                .expiryRiskFilter(parsed.getExpiryRiskFilter())
                .coverDaysEntityType(
                        coverDaysEntityTypeForIntake(parsed.getCoverDaysEntityType()))
                .coverDaysEntityName(trimCoverDaysEntityName(parsed.getCoverDaysEntityName()))
                .followUpIntent(
                        parsed.getFollowUpIntent() != null
                                ? parsed.getFollowUpIntent()
                                : SemanticIntakeFollowUpIntentNormalizer.fromParsedJson(parsed))
                .subQuestions(parsed.getSubQuestions())
                .promptId(promptId)
                .llmRawText(rawObs)
                .build();
    }

    private static String coverDaysEntityTypeForIntake(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return CoverDaysEntityType.UNKNOWN;
        }
        String normalized = CoverDaysEntityType.normalize(rawType);
        return normalized != null ? normalized : CoverDaysEntityType.UNKNOWN;
    }

    private static String warehouseSemanticsFromParsed(LlmSemanticIntakeParsed parsed) {
        return WarehouseInventoryShortageSemanticsSupport.resolveEffectiveSemanticsFromParsed(parsed);
    }

    private static SemanticIntakeResult invalidFromParsed(
            LlmSemanticIntakeParsed parsed, String promptId, String rawObs, String reason) {
        SemanticIntakeResult result =
                SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.INVALID)
                .questionMode(safeQuestionMode(parsed.getQuestionMode()))
                .normalizationType(safeNormalizationType(parsed.getNormalizationType()))
                .canonicalUserQuery(parsed.getCanonicalUserQuery())
                .isFollowUp(parsed.isFollowUp())
                .usedPreviousContext(parsed.isUsedPreviousContext())
                .primaryDomain(SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain()))
                .candidateDomains(parsed.getCandidateDomains())
                .routeType(parsed.getRouteType())
                .confidence(parsed.getConfidence())
                .needClarification(false)
                .reason(reason)
                .contextRelation(contextRelationFromParsed(parsed))
                .subQuestions(parsed.getSubQuestions())
                .promptId(promptId)
                .llmRawText(rawObs)
                .parseError(reason)
                .build();
        SemanticLlmFailureClassification.enrichIntakeFailureMeta(result);
        return result;
    }

    private static SemanticIntakeResult needClarificationFromParsed(
            LlmSemanticIntakeParsed parsed,
            String promptId,
            String rawObs,
            String reason,
            String clarificationQuestion) {
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.NEED_CLARIFICATION)
                .questionMode(safeQuestionMode(parsed.getQuestionMode()))
                .normalizationType(safeNormalizationType(parsed.getNormalizationType()))
                .canonicalUserQuery(
                        StringUtils.hasText(parsed.getCanonicalUserQuery())
                                ? parsed.getCanonicalUserQuery().trim()
                                : null)
                .isFollowUp(parsed.isFollowUp())
                .usedPreviousContext(parsed.isUsedPreviousContext())
                .primaryDomain(SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain()))
                .candidateDomains(parsed.getCandidateDomains())
                .routeType(parsed.getRouteType())
                .confidence(parsed.getConfidence())
                .needClarification(true)
                .clarificationQuestion(clarificationQuestion)
                .reason(reason)
                .contextRelation(contextRelationFromParsed(parsed))
                .warehouseInventorySemantics(warehouseSemanticsFromParsed(parsed))
                .expiryRiskFilter(parsed.getExpiryRiskFilter())
                .coverDaysEntityType(coverDaysEntityTypeForIntake(parsed.getCoverDaysEntityType()))
                .coverDaysEntityName(trimCoverDaysEntityName(parsed.getCoverDaysEntityName()))
                .subQuestions(parsed.getSubQuestions())
                .promptId(promptId)
                .llmRawText(rawObs)
                .build();
    }

    private static SemanticIntakeQuestionMode safeQuestionMode(String mode) {
        if (!LlmSemanticIntakeJsonParser.isValidQuestionMode(mode)) {
            return null;
        }
        return SemanticIntakeQuestionMode.valueOf(mode.trim().toUpperCase());
    }

    private static SemanticIntakeNormalizationType safeNormalizationType(String type) {
        if (!LlmSemanticIntakeJsonParser.isValidNormalizationType(type)) {
            return null;
        }
        return SemanticIntakeNormalizationType.valueOf(type.trim().toUpperCase());
    }

    private static SemanticIntakeNormalizationType parseNormalizationType(String type) {
        return SemanticIntakeNormalizationType.valueOf(type.trim().toUpperCase());
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        return StringUtils.hasText(b) ? b.trim() : null;
    }

    private static String truncateRaw(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        int max = 4000;
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /**
     * 收集所有非空但枚举值非法的字段错误，用于触发协议纠错重试。
     */
    static List<String> collectEnumErrors(LlmSemanticIntakeParsed parsed) {
        return collectEnumErrors(parsed, null);
    }

    static List<String> collectEnumErrors(LlmSemanticIntakeParsed parsed, SemanticIntakeInput input) {
        List<String> errors = new ArrayList<>();
        collectEnumFieldErrors(parsed, errors);
        SemanticIntakeContextRelationSupport.collectContextRelationProtocolErrors(parsed, errors);
        collectDimensionSwitchReasonProtocolErrors(parsed, errors);
        collectDishSalesBossShortPhraseProtocolErrors(parsed, errors);
        collectBareRankingDimensionSwitchIntakeProtocolErrors(parsed, input, errors);
        collectExplicitMultiDishCostRankingIntakeProtocolErrors(parsed, errors);
        SemanticIntakeDishIngredientCoverDaysSupport.collectDishIngredientCoverProtocolErrors(
                parsed, errors);
        SemanticIntakeGoodsSupportedDishCoverSupport.collectGoodsSupportedDishCoverProtocolErrors(
                parsed, errors);
        SemanticIntakeGoodsStockBatchDetailSupport.collectGoodsStockBatchDetailProtocolErrors(
                parsed, errors);
        SemanticIntakeGoodsAnchorInventoryBundleSupport.collectGoodsAnchorInventoryBundleProtocolErrors(
                parsed, errors);
        CoverDaysEntityGroundingService.collectCoverDaysEntityProtocolErrors(parsed, errors);
        collectWarehouseInventoryShortageProtocolErrors(parsed, errors);
        return errors;
    }

    /**
     * 库房 shortage marker 协议：自报 marker 时必须 needClarification，禁止 READY 进入 V2 选 WH-C。
     */
    private static void collectWarehouseInventorySemanticsEnumErrors(
            LlmSemanticIntakeParsed parsed, List<String> errors) {
        if (parsed == null || !StringUtils.hasText(parsed.getWarehouseInventorySemantics())) {
            return;
        }
        if (SemanticIntakeDishIngredientCoverDaysSupport.parsedDeclaresDishIngredientCoverDays(parsed)) {
            return;
        }
        if (SemanticIntakeGoodsSupportedDishCoverSupport.parsedDeclaresGoodsSupportedDishCover(parsed)) {
            return;
        }
        if (SemanticIntakeGoodsAnchorInventoryBundleSupport.parsedDeclaresGoodsAnchorInventoryBundle(
                parsed)) {
            return;
        }
        if (SemanticIntakeGoodsStockBatchDetailSupport.parsedDeclaresGoodsStockBatchDetail(parsed)) {
            return;
        }
        if (WarehouseInventorySupervisionSemanticsSupport.parsedDeclaresSupervision(parsed)) {
            if (StringUtils.hasText(parsed.getWarehouseInventorySemantics())
                    && WarehouseInventorySupervisionSemanticsSupport.normalizeSemantics(
                                    parsed.getWarehouseInventorySemantics())
                            == null) {
                errors.add(
                        "warehouseInventorySemantics: got \""
                                + parsed.getWarehouseInventorySemantics().trim()
                                + "\", allowed SUPERVISION_QUERY (aliases INVENTORY_STATUS/CURRENT_STATUS/"
                                + "STOCK_HEALTH_OVERVIEW normalize to SUPERVISION_QUERY) for §13e");
            }
            return;
        }
        if (WarehouseInventoryShortageSemanticsSupport.normalizeSemantics(
                        parsed.getWarehouseInventorySemantics())
                == null) {
            errors.add(
                    "warehouseInventorySemantics: got \""
                            + parsed.getWarehouseInventorySemantics().trim()
                            + "\", allowed UNDERSTOCK_QUERY, OUT_OF_STOCK, NEAR_EXPIRY, SUPERVISION_QUERY, "
                            + "EXPLICIT_AMOUNT_RANKING_LOW, INVENTORY_AMOUNT_LOW (§13d); "
                            + "dish cover days use DISH_COST + reason=dish_ingredient_cover_days (§34a)");
        }
    }

    static void collectWarehouseInventoryShortageProtocolErrors(
            LlmSemanticIntakeParsed parsed, List<String> errors) {
        if (parsed == null
                || SemanticIntakeDishIngredientCoverDaysSupport.parsedDeclaresDishIngredientCoverDays(
                        parsed)
                || SemanticIntakeGoodsSupportedDishCoverSupport.parsedDeclaresGoodsSupportedDishCover(
                        parsed)) {
            return;
        }
        String semanticsRaw = parsed.getWarehouseInventorySemantics();
        if (StringUtils.hasText(semanticsRaw) && "PURCHASE".equals(parsed.getPrimaryDomain())) {
            errors.add(
                    "warehouse_inventory_risk: warehouseInventorySemantics set requires "
                            + "primaryDomain=WAREHOUSE, never PURCHASE (§13b)");
        }
        String amountSemantics =
                WarehouseInventoryShortageSemanticsSupport.normalizeSemantics(semanticsRaw);
        if (WarehouseInventoryShortageSemanticsSupport.SEMANTICS_EXPLICIT_AMOUNT_RANKING_LOW.equals(
                amountSemantics)) {
            if (parsed.isNeedClarification()) {
                errors.add(
                        "warehouse_inventory_amount_ranking: EXPLICIT_AMOUNT_RANKING_LOW requires "
                                + "needClarification=false and must not use shortage/alert markers "
                                + "(§13d)");
            }
            if (WarehouseInventoryShortageSemanticsSupport.reasonDeclaresShortageSemantics(
                    parsed.getReason())) {
                errors.add(
                        "warehouse_inventory_amount_ranking: reason must use "
                                + "warehouse_inventory_amount_ranking_low, not shortage/alert markers "
                                + "(§13d)");
            }
            return;
        }
        if (!WarehouseInventoryShortageSemanticsSupport.parsedDeclaresInventoryRisk(parsed)) {
            if (WarehouseInventorySupervisionSemanticsSupport.parsedDeclaresSupervision(parsed)) {
                if ("PURCHASE".equals(parsed.getPrimaryDomain())) {
                    errors.add(
                            "warehouse_inventory_supervision: inventory supervision must "
                                    + "primaryDomain=WAREHOUSE, never PURCHASE (§13e)");
                }
                if ("WAREHOUSE".equals(parsed.getPrimaryDomain()) && parsed.isNeedClarification()) {
                    errors.add(
                            "warehouse_inventory_supervision: must needClarification=false "
                                    + "and route to warehouse.inventory_supervision.v1 (§13e)");
                }
            }
            return;
        }
        if ("PURCHASE".equals(parsed.getPrimaryDomain())) {
            errors.add(
                    "warehouse_inventory_risk: inventory shortage/alert/near-expiry must "
                            + "primaryDomain=WAREHOUSE, never PURCHASE (§13b)");
        }
        String riskSemantics =
                WarehouseInventoryShortageSemanticsSupport.resolveEffectiveSemanticsFromParsed(parsed);
        if (WarehouseInventoryShortageSemanticsSupport.SEMANTICS_NEAR_EXPIRY.equals(riskSemantics)) {
            if ("WAREHOUSE".equals(parsed.getPrimaryDomain()) && parsed.isNeedClarification()) {
                errors.add(
                        "warehouse_inventory_near_expiry: must needClarification=false "
                                + "and route to warehouse.near_expiry (§13a)");
            }
            if (StringUtils.hasText(parsed.getExpiryRiskFilter())
                    && !WarehouseNearExpiryRiskFilterSupport.isKnownFilter(
                            parsed.getExpiryRiskFilter())) {
                errors.add(
                        "expiryRiskFilter: invalid for NEAR_EXPIRY; allowed NEAR_EXPIRY, EXPIRED, "
                                + "DUE_TODAY, ALL_RISK (§13a)");
            }
            return;
        }
        if ("WAREHOUSE".equals(parsed.getPrimaryDomain()) && parsed.isNeedClarification()) {
            errors.add(
                    "warehouse_inventory_risk: understock/alert/out_of_stock must needClarification=false "
                            + "and route to warehouse.inventory_risk_list (§13a)");
        }
    }

    private SemanticIntakeResult applyIntakeReconcilers(
            SemanticIntakeInput input, SemanticIntakeResult mapped) {
        mapped = SemanticIntakeGoodsStockBatchDetailSupport.reconcile(input, mapped);
        mapped = SemanticIntakeGoodsAnchorInventoryBundleSupport.reconcile(input, mapped);
        mapped = SemanticIntakeGoodsSupportedDishCoverSupport.reconcile(input, mapped);
        mapped = coverDaysEntityGroundingService.reconcileIntake(input, mapped);
        mapped = SemanticIntakeGoodsAnchorFollowUpSupport.reconcile(input, mapped);
        mapped = SemanticIntakeDishIngredientCoverDaysSupport.reconcile(input, mapped);
        mapped = SemanticIntakeDishFollowUpInheritanceSupport.reconcile(input, mapped);
        mapped = SemanticIntakeMultiDishRankingSupport.reconcileExplicitMultiDishRankingDomain(input, mapped);
        mapped = BareRankingDimensionSwitchSupport.reconcileIntakeDomain(input, mapped);
        mapped = WarehouseInventorySupervisionSemanticsSupport.reconcileIntake(input, mapped);
        mapped = WarehouseInventoryShortageSemanticsSupport.reconcileIntake(input, mapped);
        return SemanticIntakeFollowUpIntentNormalizer.reconcile(input, mapped);
    }

    private static String trimCoverDaysEntityName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return name.trim();
    }

    private static String contextRelationFromParsed(LlmSemanticIntakeParsed parsed) {
        if (parsed == null) {
            return null;
        }
        return SemanticIntakeContextRelation.normalize(parsed.getContextRelation());
    }

    private static void collectEnumFieldErrors(LlmSemanticIntakeParsed parsed, List<String> errors) {
        collectWarehouseInventorySemanticsEnumErrors(parsed, errors);
        String nt = parsed.getNormalizationType();
        if (nt != null && !LlmSemanticIntakeJsonParser.isValidNormalizationType(nt)) {
            errors.add(
                    "normalizationType: got \""
                            + nt
                            + "\", allowed: PASS_THROUGH, REWRITE");
        }
        String qm = parsed.getQuestionMode();
        if (qm != null && !LlmSemanticIntakeJsonParser.isValidQuestionMode(qm)) {
            errors.add(
                    "questionMode: got \""
                            + qm
                            + "\", allowed: SINGLE_QUESTION, MULTI_QUESTION");
        }
        String rt = parsed.getRouteType();
        if (rt != null && !LlmSemanticIntakeJsonParser.isValidRouteType(rt)) {
            errors.add(
                    "routeType: got \""
                            + rt
                            + "\", allowed: EXPLICIT, INHERITED, AMBIGUOUS, UNKNOWN, MULTI_DOMAIN");
        }
    }

    /**
     * 维度切换 reason 协议：self-declared dimension_switch 必须含 wire token 后缀。
     * 仅校验 LLM 输出的 reason 字段，不解析用户原文。
     */
    static void collectDimensionSwitchReasonProtocolErrors(
            LlmSemanticIntakeParsed parsed, List<String> errors) {
        if (parsed == null
                || BareRankingDimensionSwitchSupport.isOutsideBareRankingDimensionSwitchScope(parsed)
                || !StringUtils.hasText(parsed.getReason())) {
            return;
        }
        String reason = parsed.getReason().trim();
        if (StructuredRankingTimeOnlyIntakeSupport.isStructuredRankingTimeOnlyIntakeReason(reason)) {
            return;
        }
        String normalized = reason.toLowerCase(java.util.Locale.ROOT);
        if (!normalized.contains("dimension_switch")) {
            return;
        }
        if (normalized.contains("_to_cost_ranking")
                || normalized.contains("_to_margin_ranking")
                || normalized.contains("_to_profit_amount_ranking")
                || normalized.contains("_to_sales_ranking")
                || normalized.contains("_to_amount_ranking")) {
            return;
        }
        errors.add(
                "reason: dimension_switch must include one of "
                        + "_to_cost_ranking, _to_margin_ranking, _to_profit_amount_ranking, "
                        + "_to_sales_ranking, _to_amount_ranking; got \""
                        + reason
                        + "\"");
    }

    /**
     * 老板销量短句协议：LLM 自报 DISH_SALES 时不得与 BUSINESS_OVERVIEW 双候选并 AMBIGUOUS 澄清。
     * 仅校验 LLM 输出字段，不解析用户原文。
     */
    static void collectDishSalesBossShortPhraseProtocolErrors(
            LlmSemanticIntakeParsed parsed, List<String> errors) {
        if (parsed == null || !"DISH_SALES".equals(parsed.getPrimaryDomain())) {
            return;
        }
        List<String> candidates = parsed.getCandidateDomains();
        boolean hasBusinessOverview =
                candidates != null
                        && candidates.stream().anyMatch("BUSINESS_OVERVIEW"::equals);
        String routeType = parsed.getRouteType();
        boolean ambiguousRoute =
                routeType != null
                        && ("AMBIGUOUS".equals(routeType.trim().toUpperCase())
                                || "UNKNOWN".equals(routeType.trim().toUpperCase())
                                || "MULTI_DOMAIN".equals(routeType.trim().toUpperCase()));
        if (hasBusinessOverview && (ambiguousRoute || parsed.isNeedClarification())) {
            errors.add(
                    "dish_sales_boss_short_phrase: primaryDomain=DISH_SALES must not pair "
                            + "candidateDomains containing BUSINESS_OVERVIEW with AMBIGUOUS/needClarification; "
                            + "use routeType=EXPLICIT, needClarification=false, candidateDomains=[DISH_SALES] only (§26a–26h)");
        }
        String reason = parsed.getReason();
        if (!StringUtils.hasText(reason)) {
            return;
        }
        String normalizedReason = reason.trim().toLowerCase(java.util.Locale.ROOT);
        boolean bossShortReason =
                normalizedReason.startsWith("dish_sales_ranking_short_phrase")
                        || normalizedReason.startsWith("dish_sales_quantity_short_phrase")
                        || normalizedReason.startsWith("dish_sales_amount_short_phrase");
        if (!bossShortReason) {
            return;
        }
        if (ambiguousRoute || parsed.isNeedClarification() || hasBusinessOverview) {
            errors.add(
                    "dish_sales_boss_short_phrase: reason="
                            + reason
                            + " requires routeType=EXPLICIT, needClarification=false, "
                            + "candidateDomains=[DISH_SALES] only (§26a–26h)");
        }
    }

    /**
     * 裸维度切换 Intake 协议：仅校验 LLM 输出（canonical / primaryDomain / reason），不解析用户原文。
     */
    static void collectBareRankingDimensionSwitchIntakeProtocolErrors(
            LlmSemanticIntakeParsed parsed, SemanticIntakeInput input, List<String> errors) {
        if (parsed == null || BareRankingDimensionSwitchSupport.isOutsideBareRankingDimensionSwitchScope(parsed)) {
            return;
        }
        String reason = parsed.getReason();
        String normalizedReason =
                StringUtils.hasText(reason)
                        ? reason.trim().toLowerCase(java.util.Locale.ROOT)
                        : null;
        boolean namedDishReason =
                normalizedReason != null && normalizedReason.startsWith("named_dish_");
        String primary = parsed.getPrimaryDomain();
        String canonical = parsed.getCanonicalUserQuery();
        boolean multiDishRankingCanonical =
                SemanticIntakeMultiDishRankingSupport.looksLikeMultiDishRankingCanonical(canonical);

        if (multiDishRankingCanonical
                && SemanticIntakePrimaryDomain.DISH_COST.equals(primary)
                && !namedDishReason) {
            if (SemanticIntakeMultiDishRankingSupport.looksLikeMultiDishCostRankingCanonical(canonical)) {
                errors.add(
                        "primaryDomain: explicit multi-dish cost ranking canonical must use DISH_PROFIT, "
                                + "reason=dish_actual_cost_ranking_high_explicit (§38b–38c); not DISH_COST");
            } else {
                errors.add(
                        "primaryDomain: multi-dish ranking canonical must use DISH_PROFIT (actual cost ranking) "
                                + "or DISH_SALES, not DISH_COST; use reason with _to_*_ranking token (§38f–38g)");
            }
        }

        if (StringUtils.hasText(normalizedReason)) {
            if (normalizedReason.contains("_to_cost_ranking")
                    && !SemanticIntakePrimaryDomain.DISH_PROFIT.equals(primary)) {
                errors.add(
                        "primaryDomain: reason contains _to_cost_ranking so primaryDomain must be DISH_PROFIT (§38g)");
            }
            if (normalizedReason.contains("_to_margin_ranking")
                    && !SemanticIntakePrimaryDomain.DISH_PROFIT.equals(primary)) {
                errors.add(
                        "primaryDomain: reason contains _to_margin_ranking so primaryDomain must be DISH_PROFIT (§38g)");
            }
            if (normalizedReason.contains("_to_sales_ranking")
                    && !SemanticIntakePrimaryDomain.DISH_SALES.equals(primary)) {
                errors.add(
                        "primaryDomain: reason contains _to_sales_ranking so primaryDomain must be DISH_SALES (§38g)");
            }
            if (normalizedReason.contains("_to_amount_ranking")
                    && !SemanticIntakePrimaryDomain.DISH_SALES.equals(primary)) {
                errors.add(
                        "primaryDomain: reason contains _to_amount_ranking so primaryDomain must be DISH_SALES (§38g)");
            }
        }

        if (input == null
                || !input.isHasPreviousTurn()
                || !parsed.isFollowUp()
                || namedDishReason
                || StructuredRankingTimeOnlyIntakeSupport.isStructuredRankingTimeOnlyIntakeReason(reason)
                || !BareRankingDimensionSwitchSupport.hasPreviousDishRankingTurn(input)
                || !multiDishRankingCanonical) {
            return;
        }
        if (BareRankingDimensionSwitchSupport.hasDimensionSwitchReasonToken(reason)) {
            return;
        }
        errors.add(
                "reason: bare ranking dimension switch after previous dish ranking requires "
                        + "_to_cost_ranking / _to_margin_ranking / _to_profit_amount_ranking / _to_sales_ranking / _to_amount_ranking "
                        + "(e.g. dimension_switch_sales_to_cost_ranking); without token BareRankingDimensionSwitchPlan stays inactive (§38g)");
    }

    static boolean looksLikeMultiDishRankingCanonical(String canonical) {
        return SemanticIntakeMultiDishRankingSupport.looksLikeMultiDishRankingCanonical(canonical);
    }

    /**
     * 完整显式多菜成本排行 Intake 协议：canonical 已是成本排行语义时禁止 DISH_COST。
     */
    static void collectExplicitMultiDishCostRankingIntakeProtocolErrors(
            LlmSemanticIntakeParsed parsed, List<String> errors) {
        if (parsed == null) {
            return;
        }
        String reason = parsed.getReason();
        if (SemanticIntakeMultiDishRankingSupport.isNamedDishIntakeReason(reason)) {
            return;
        }
        String canonical = parsed.getCanonicalUserQuery();
        if (!SemanticIntakeMultiDishRankingSupport.looksLikeMultiDishCostRankingCanonical(canonical)) {
            return;
        }
        if (SemanticIntakePrimaryDomain.DISH_COST.equals(parsed.getPrimaryDomain())) {
            errors.add(
                    "primaryDomain: dish cost ranking canonical requires DISH_PROFIT + "
                            + "reason=dish_actual_cost_ranking_high_explicit (§38b–38c)");
        }
    }

    /**
     * 构建协议纠错 user message，仅要求修正非法字段，不做业务语义推断。
     */
    static String buildRepairUserMessage(String originalRaw, List<String> enumErrors) {
        StringBuilder sb = new StringBuilder();
        sb.append("protocol_repair_request\n");
        sb.append(
                "Your JSON output contained invalid enum or protocol values. Correct ONLY the invalid fields while keeping all other fields unchanged. Re-output one line of corrected JSON.\n\n");
        sb.append("Invalid fields:\n");
        for (String err : enumErrors) {
            sb.append("- ").append(err).append("\n");
        }
        sb.append("\nOriginal output:\n");
        sb.append(originalRaw);
        return sb.toString();
    }

    /**
     * 协议纠错重试：JSON 可解析但枚举非法时，重试一次要求模型修正枚举值。
     * 这是输出协议层面的修复，不做业务语义推断。
     */
    private SemanticIntakeResult parseWithProtocolRepair(
            SemanticIntakeInput input,
            String promptId,
            String systemPrompt,
            String originalRaw,
            LlmSemanticIntakeParsed originalParsed,
            List<String> enumErrors) {
        String repairUserMessage = buildRepairUserMessage(originalRaw, enumErrors);
        String repairedRaw = null;
        try {
            repairedRaw = llmGateway.chatSimple(systemPrompt, repairUserMessage);
            if (!StringUtils.hasText(repairedRaw)) {
                return markRepairedInvalid(
                        originalParsed,
                        promptId,
                        originalRaw,
                        enumErrors,
                        false,
                        "empty_repair_response");
            }
            LlmSemanticIntakeParsed repaired =
                    LlmSemanticIntakeJsonParser.parseRaw(repairedRaw);
            if (repaired.isParseFailed()) {
                return markRepairedInvalid(
                        originalParsed, promptId, originalRaw, enumErrors, false, repaired.getParseError());
            }
            List<String> repairedErrors = collectEnumErrors(repaired, input);
            if (!repairedErrors.isEmpty()) {
                return markRepairedInvalid(
                        originalParsed,
                        promptId,
                        originalRaw,
                        enumErrors,
                        false,
                        "repair_still_invalid:" + String.join(";", repairedErrors));
            }
            SemanticIntakeResult result =
                    applyIntakeReconcilers(
                            input, mapParsed(input, promptId, truncateRaw(repairedRaw), repaired));
            result.setIntakeRepairAttempted(true);
            result.setIntakeRepairSuccess(true);
            result.setIntakeRepairReason(buildRepairReasonCode(enumErrors));
            return result;
        } catch (Exception e) {
            log.warn("[LlmSemanticIntakeParser] protocol repair failed: {}", e.toString());
            return markRepairedInvalid(
                    originalParsed,
                    promptId,
                    originalRaw,
                    enumErrors,
                    false,
                    "repair_exception:" + e.getClass().getSimpleName());
        }
    }

    private static SemanticIntakeResult markRepairedInvalid(
            LlmSemanticIntakeParsed originalParsed,
            String promptId,
            String rawObs,
            List<String> enumErrors,
            boolean repairSuccess,
            String detailError) {
        String reasonCode = buildRepairReasonCode(enumErrors);
        if (StringUtils.hasText(detailError)) {
            reasonCode = reasonCode + ";" + detailError;
        }
        SemanticIntakeResult result =
                SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.INVALID)
                .questionMode(safeQuestionMode(originalParsed.getQuestionMode()))
                .normalizationType(safeNormalizationType(originalParsed.getNormalizationType()))
                .canonicalUserQuery(originalParsed.getCanonicalUserQuery())
                .isFollowUp(originalParsed.isFollowUp())
                .usedPreviousContext(originalParsed.isUsedPreviousContext())
                .primaryDomain(
                        SemanticIntakePrimaryDomain.normalize(originalParsed.getPrimaryDomain()))
                .candidateDomains(originalParsed.getCandidateDomains())
                .routeType(originalParsed.getRouteType())
                .confidence(originalParsed.getConfidence())
                .needClarification(false)
                .reason(reasonCode)
                .contextRelation(contextRelationFromParsed(originalParsed))
                .subQuestions(originalParsed.getSubQuestions())
                .promptId(promptId)
                .llmRawText(rawObs)
                .parseError(reasonCode)
                .intakeRepairAttempted(true)
                .intakeRepairSuccess(repairSuccess)
                .intakeRepairReason(buildRepairReasonCode(enumErrors))
                .build();
        SemanticLlmFailureClassification.enrichIntakeFailureMeta(result);
        return result;
    }

    private static String buildRepairReasonCode(List<String> enumErrors) {
        if (enumErrors == null || enumErrors.isEmpty()) {
            return "unknown_repair";
        }
        if (enumErrors.stream().anyMatch(e -> e.startsWith("normalizationType:"))) {
            if (enumErrors.stream().anyMatch(e -> e.startsWith("routeType:"))) {
                return "invalid_normalization_type;invalid_route_type";
            }
            return "invalid_normalization_type";
        }
        if (enumErrors.stream().anyMatch(e -> e.startsWith("routeType:"))) {
            return "invalid_route_type";
        }
        if (enumErrors.stream().anyMatch(e -> e.startsWith("questionMode:"))) {
            return "invalid_question_mode";
        }
        if (enumErrors.stream().anyMatch(e -> e.startsWith("reason:"))) {
            return "invalid_dimension_switch_reason_token";
        }
        return "invalid_enum";
    }
}
