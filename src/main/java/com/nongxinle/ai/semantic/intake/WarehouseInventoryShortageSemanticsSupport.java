package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.inventory.WarehouseNearExpiryRiskFilterSupport;
import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.frame.SemanticFrameValidationResult;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 库房「库存偏少/报警/快缺货/临期」与账面金额低排行（WH-C）边界。
 * <p>仅读取 Intake 结构化字段 {@code warehouseInventorySemantics} 与 {@code reason} 固定 marker；
 * 不解析用户原文。
 */
public final class WarehouseInventoryShortageSemanticsSupport {

    /** Intake JSON：库存偏少/快缺货/报警/补货紧迫（非金额排行）。 */
    public static final String SEMANTICS_UNDERSTOCK_QUERY = "UNDERSTOCK_QUERY";

    /** Intake JSON：缺货/断货风险（非金额排行）。 */
    public static final String SEMANTICS_OUT_OF_STOCK = "OUT_OF_STOCK";

    /** Intake JSON：临期/保质期风险（非金额排行）。 */
    public static final String SEMANTICS_NEAR_EXPIRY = "NEAR_EXPIRY";

    /** Intake JSON：明确问账面库存金额从低到高排行（可走 WH-C）。 */
    public static final String SEMANTICS_EXPLICIT_AMOUNT_RANKING_LOW =
            "EXPLICIT_AMOUNT_RANKING_LOW";

    /** Intake JSON / reason 过渡别名：账面库存金额低排行（归一到 {@link #SEMANTICS_EXPLICIT_AMOUNT_RANKING_LOW}）。 */
    public static final String SEMANTICS_INVENTORY_AMOUNT_LOW = "INVENTORY_AMOUNT_LOW";

    /** @deprecated 与 {@link #SEMANTICS_UNDERSTOCK_QUERY} 同义，Intake 过渡别名 */
    @Deprecated
    public static final String SEMANTICS_SHORTAGE_OR_ALERT = "SHORTAGE_OR_ALERT";

    /** Intake reason 自报：账面金额低排行（WH-C），优先于 risk marker。 */
    public static final String REASON_MARKER_AMOUNT_RANKING_LOW =
            "warehouse_inventory_amount_ranking_low";

    /** Intake reason 自报：库存风险/偏少/快缺货（非 WH-C）。 */
    public static final String REASON_MARKER_PRIMARY = "warehouse_inventory_shortage_semantics";

    /** Intake reason 自报：库存报警专链（planned/known gap）。 */
    public static final String REASON_MARKER_ALERT = "warehouse_inventory_alert_semantics";

    /** 过渡观测别名（Harness/debug）。 */
    public static final String REASON_MARKER_ALIAS = "query_contains_inventory_shortage_keywords";

    public static final String CONTRACT_GOODS_AMOUNT_RANKING_LOW =
            "warehouse.goods_amount_ranking_low";

    /** P1 ACTIVE：库存风险列表（偏少/快缺货/需关注）。 */
    public static final String CONTRACT_INVENTORY_RISK_LIST = "warehouse.inventory_risk_list";

    /** ACTIVE：库存批次临期/过期风险。 */
    public static final String CONTRACT_NEAR_EXPIRY = "warehouse.near_expiry";

    private static final String CLARIFICATION_UNDERSTOCK_QUERY =
            "判断库存是否偏少需要结合当前库存、保质期与近期消耗/销量；"
                    + "当前库存报警与库存偏少筛查能力尚未开放，暂不能生成库存偏少原料或商品列表。"
                    + "若只需查看账面剩余库存金额偏低的商品，可改问「哪些商品账面库存金额较低」。";

    private static final String CLARIFICATION_ALERT =
            "库存报警需要结合商品保质期、当前库存与近期消耗/销量综合判断；"
                    + "当前库存报警专链尚未开放，暂不能生成库存报警清单。"
                    + "若只需查看账面剩余库存金额偏低的商品，可改问「哪些商品账面库存金额较低」。";

    private static final String CLARIFICATION_OUT_OF_STOCK =
            "判断缺货/断货需要结合当前库存、保质期与近期消耗/销量；"
                    + "当前缺货筛查专链尚未开放，暂不能生成缺货商品或原料列表。"
                    + "若只需查看账面剩余库存金额偏低的商品，可改问「哪些商品账面库存金额较低」。";

    private static final String CLARIFICATION_NEAR_EXPIRY =
            "临期/保质期风险筛查需要结合批次保质期与当前库存；"
                    + "当前临期预警专链尚未开放，暂不能生成临期商品或原料清单。"
                    + "若只需查看账面剩余库存金额偏低的商品，可改问「哪些商品账面库存金额较低」。";

    private WarehouseInventoryShortageSemanticsSupport() {}

    /**
     * 当前轮结构化库房库存风险主权信号（{@code warehouseInventorySemantics} 风险枚举，或 shortage reason
     * 且非单菜配料 cover reason）。不读用户原文。
     */
    public static boolean intakeHasAuthoritativeInventoryRisk(SemanticIntakeResult intake) {
        if (intake == null || intakeExplicitAmountRankingLow(intake)) {
            return false;
        }
        if (SemanticIntakeDishIngredientCoverDaysSupport.rawWarehouseSemanticsDeclaresDishCoverMislabel(
                intake.getWarehouseInventorySemantics())) {
            return false;
        }
        String semantics = normalizeSemantics(intake.getWarehouseInventorySemantics());
        if (isInventoryRiskSemantics(semantics)) {
            return true;
        }
        if (intakeDeclaresNearExpiryRiskFilter(intake)) {
            return true;
        }
        if (reasonDeclaresShortageSemantics(intake.getReason())) {
            return !SemanticIntakeDishIngredientCoverDaysSupport.reasonDeclaresDishIngredientCoverDays(
                    intake.getReason());
        }
        return false;
    }

    public static boolean intakeSignalsInventoryShortageSemantics(SemanticIntakeResult intake) {
        if (SemanticIntakeDishIngredientCoverDaysSupport.mustNotApplyWarehouseInventoryShortagePipeline(
                intake)) {
            return false;
        }
        if (SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(intake)) {
            return false;
        }
        return intake != null && signalsInventoryRisk(intake);
    }

    public static boolean reasonDeclaresExplicitAmountRankingLow(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT);
        return normalized.contains(REASON_MARKER_AMOUNT_RANKING_LOW)
                || normalized.contains("inventory_amount_low")
                || normalized.contains("explicit_amount_ranking_low");
    }

    public static boolean reasonDeclaresShortageSemantics(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        if (reasonDeclaresExplicitAmountRankingLow(reason)) {
            return false;
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT);
        return normalized.contains(REASON_MARKER_PRIMARY)
                || normalized.contains(REASON_MARKER_ALERT)
                || normalized.contains(REASON_MARKER_ALIAS)
                || normalized.contains("understock_query")
                || normalized.contains("out_of_stock")
                || normalized.contains("warehouse_inventory_risk");
    }

    /**
     * LLM 原始 JSON 上的库房风险枚举 / shortage reason（不含与菜品 cover 的互斥判断，避免与
     * {@link SemanticIntakeDishIngredientCoverDaysSupport#parsedDeclaresDishIngredientCoverDays} 循环调用）。
     */
    static boolean parsedRawInventoryRiskSemantics(
            com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed parsed) {
        if (parsed == null) {
            return false;
        }
        if (SemanticIntakeDishIngredientCoverDaysSupport.rawWarehouseSemanticsDeclaresDishCoverMislabel(
                parsed.getWarehouseInventorySemantics())) {
            return false;
        }
        if (WarehouseInventorySupervisionSemanticsSupport.parsedDeclaresSupervision(parsed)) {
            return false;
        }
        String semantics = normalizeSemantics(parsed.getWarehouseInventorySemantics());
        if (SEMANTICS_EXPLICIT_AMOUNT_RANKING_LOW.equals(semantics)) {
            return false;
        }
        if (WarehouseNearExpiryRiskFilterSupport.isKnownFilter(parsed.getExpiryRiskFilter())) {
            return true;
        }
        if (reasonDeclaresShortageSemantics(parsed.getReason())) {
            return true;
        }
        return isInventoryRiskSemantics(semantics);
    }

    /** Intake LLM 原始 JSON 是否声明库房库存风险（仅结构化字段，不读用户原文）。 */
    public static boolean parsedDeclaresInventoryRisk(
            com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed parsed) {
        if (parsed == null) {
            return false;
        }
        if (SemanticIntakeGoodsSupportedDishCoverSupport.parsedDeclaresGoodsSupportedDishCover(parsed)) {
            return false;
        }
        if (SemanticIntakeDishIngredientCoverDaysSupport.reasonDeclaresDishIngredientCoverDays(
                parsed.getReason())) {
            return false;
        }
        if (SemanticIntakeDishIngredientCoverDaysSupport.rawWarehouseSemanticsDeclaresDishCoverMislabel(
                parsed.getWarehouseInventorySemantics())) {
            return false;
        }
        String primary = SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain());
        if (SemanticIntakePrimaryDomain.DISH_COST.equals(primary) && !parsedRawInventoryRiskSemantics(parsed)) {
            return false;
        }
        return parsedRawInventoryRiskSemantics(parsed);
    }

    public static boolean isInventoryRiskSemantics(String normalizedSemantics) {
        return SEMANTICS_UNDERSTOCK_QUERY.equals(normalizedSemantics)
                || SEMANTICS_OUT_OF_STOCK.equals(normalizedSemantics)
                || SEMANTICS_NEAR_EXPIRY.equals(normalizedSemantics);
    }

    /** Intake 结构化 {@code expiryRiskFilter}（非用户原文）即 near_expiry 主权信号。 */
    public static boolean intakeDeclaresNearExpiryRiskFilter(SemanticIntakeResult intake) {
        return intake != null
                && WarehouseNearExpiryRiskFilterSupport.isKnownFilter(intake.getExpiryRiskFilter());
    }

    /** V2 {@code semanticSlots.expiryRiskFilter} 即 near_expiry 主权信号。 */
    public static boolean parseDeclaresNearExpiryRiskFilter(AiQuerySemanticParseResult sem) {
        return sem != null
                && sem.getSemanticSlots() != null
                && WarehouseNearExpiryRiskFilterSupport.isKnownFilter(
                        sem.getSemanticSlots().getExpiryRiskFilter());
    }

    public static boolean parseOrIntakeDeclaresNearExpiryRisk(
            AiQuerySemanticParseResult sem, SemanticIntakeResult intake) {
        return intakeDeclaresNearExpiryRiskFilter(intake) || parseDeclaresNearExpiryRiskFilter(sem);
    }

    /** Intake LLM 原始 JSON：{@code expiryRiskFilter} 优先于 {@code warehouseInventorySemantics}。 */
    public static String resolveEffectiveSemanticsFromParsed(
            com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed parsed) {
        if (parsed != null
                && WarehouseNearExpiryRiskFilterSupport.isKnownFilter(parsed.getExpiryRiskFilter())) {
            return SEMANTICS_NEAR_EXPIRY;
        }
        return normalizeSemantics(parsed != null ? parsed.getWarehouseInventorySemantics() : null);
    }

    /** 当前轮库房风险语义：{@code expiryRiskFilter} 优先于 {@code warehouseInventorySemantics} / reason。 */
    static String resolveEffectiveInventoryRiskSemantics(SemanticIntakeResult intake) {
        if (intake == null) {
            return null;
        }
        if (intakeDeclaresNearExpiryRiskFilter(intake)) {
            return SEMANTICS_NEAR_EXPIRY;
        }
        if (StringUtils.hasText(intake.getWarehouseInventorySemantics())) {
            return normalizeSemantics(intake.getWarehouseInventorySemantics());
        }
        if (reasonDeclaresShortageSemantics(intake.getReason())) {
            return inferSemanticsFromReason(intake.getReason());
        }
        return null;
    }

    public static boolean intakeExplicitAmountRankingLow(SemanticIntakeResult intake) {
        if (intake == null) {
            return false;
        }
        String semantics = normalizeSemantics(intake.getWarehouseInventorySemantics());
        if (SEMANTICS_EXPLICIT_AMOUNT_RANKING_LOW.equals(semantics)) {
            return true;
        }
        if (isInventoryRiskSemantics(semantics)) {
            return false;
        }
        return reasonDeclaresExplicitAmountRankingLow(intake.getReason());
    }

    public static String normalizeSemantics(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String n = raw.trim().toUpperCase(Locale.ROOT);
        return switch (n) {
            case SEMANTICS_UNDERSTOCK_QUERY, SEMANTICS_SHORTAGE_OR_ALERT -> SEMANTICS_UNDERSTOCK_QUERY;
            case SEMANTICS_OUT_OF_STOCK, SEMANTICS_NEAR_EXPIRY -> n;
            case SEMANTICS_EXPLICIT_AMOUNT_RANKING_LOW, SEMANTICS_INVENTORY_AMOUNT_LOW -> n;
            case "INVENTORY_AMOUNT_RANKING_LOW" -> SEMANTICS_EXPLICIT_AMOUNT_RANKING_LOW;
            default -> null;
        };
    }

    /**
     * Intake 后处理：库存风险语义 → 强制 WAREHOUSE + 澄清；纠正误路由到 PURCHASE。
     * 明确金额排行语义（EXPLICIT_AMOUNT_RANKING_LOW）且无误报 risk marker 时不介入。
     */
    public static SemanticIntakeResult reconcileIntake(
            SemanticIntakeInput input, SemanticIntakeResult mapped) {
        if (mapped == null || mapped.getStatus() == SemanticIntakeStatus.INVALID) {
            return mapped;
        }
        if (SemanticIntakeDishIngredientCoverDaysSupport.intakeDeclaresDishIngredientCoverDays(mapped)
                || SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(
                        mapped)) {
            return mapped;
        }
        if (WarehouseInventorySupervisionSemanticsSupport.intakeDeclaresSupervisionQuery(mapped)) {
            return mapped;
        }
        if (intakeExplicitAmountRankingLow(mapped)) {
            return promoteExplicitAmountRankingIntake(mapped);
        }
        if (!signalsInventoryRisk(mapped)) {
            return mapped;
        }
        String semantics = resolveEffectiveInventoryRiskSemantics(mapped);
        if (!StringUtils.hasText(semantics)) {
            semantics =
                    inferSemanticsFromReason(
                            StringUtils.hasText(mapped.getReason())
                                    ? mapped.getReason().trim()
                                    : REASON_MARKER_PRIMARY);
        }
        if (SEMANTICS_NEAR_EXPIRY.equals(semantics)) {
            return promoteNearExpiryReadyIntake(mapped, semantics);
        }
        return promoteInventoryRiskReadyIntake(mapped, semantics);
    }

    private static SemanticIntakeResult promoteNearExpiryReadyIntake(
            SemanticIntakeResult mapped, String semantics) {
        String reason =
                StringUtils.hasText(mapped.getReason())
                        ? mapped.getReason().trim()
                        : REASON_MARKER_PRIMARY;
        if (!reason.contains("warehouse_inventory_near_expiry")) {
            reason = reason + ";warehouse_inventory_near_expiry";
        }
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.READY)
                .questionMode(mapped.getQuestionMode())
                .normalizationType(mapped.getNormalizationType())
                .canonicalUserQuery(mapped.getCanonicalUserQuery())
                .isFollowUp(mapped.getIsFollowUp())
                .usedPreviousContext(mapped.getUsedPreviousContext())
                .primaryDomain(SemanticIntakePrimaryDomain.WAREHOUSE)
                .candidateDomains(
                        mapped.getCandidateDomains() != null
                                ? mapped.getCandidateDomains()
                                : List.of(SemanticIntakePrimaryDomain.WAREHOUSE))
                .routeType("EXPLICIT")
                .confidence(mapped.getConfidence())
                .needClarification(false)
                .clarificationQuestion(null)
                .reason(reason)
                .warehouseInventorySemantics(semantics)
                .expiryRiskFilter(mapped.getExpiryRiskFilter())
                .subQuestions(mapped.getSubQuestions())
                .promptId(mapped.getPromptId())
                .llmRawText(mapped.getLlmRawText())
                .parseError(mapped.getParseError())
                .intakeRepairAttempted(mapped.getIntakeRepairAttempted())
                .intakeRepairSuccess(mapped.getIntakeRepairSuccess())
                .intakeRepairReason(mapped.getIntakeRepairReason())
                .failureCode(mapped.getFailureCode())
                .failureStage(mapped.getFailureStage())
                .build();
    }

    private static SemanticIntakeResult promoteInventoryRiskReadyIntake(
            SemanticIntakeResult mapped, String semantics) {
        String reason =
                StringUtils.hasText(mapped.getReason())
                        ? mapped.getReason().trim()
                        : REASON_MARKER_PRIMARY;
        if (!reason.contains("warehouse_inventory_risk")) {
            reason = reason + ";warehouse_inventory_risk_p1";
        }
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.READY)
                .questionMode(mapped.getQuestionMode())
                .normalizationType(mapped.getNormalizationType())
                .canonicalUserQuery(mapped.getCanonicalUserQuery())
                .isFollowUp(mapped.getIsFollowUp())
                .usedPreviousContext(mapped.getUsedPreviousContext())
                .primaryDomain(SemanticIntakePrimaryDomain.WAREHOUSE)
                .candidateDomains(
                        mapped.getCandidateDomains() != null
                                ? mapped.getCandidateDomains()
                                : List.of(SemanticIntakePrimaryDomain.WAREHOUSE))
                .routeType("EXPLICIT")
                .confidence(mapped.getConfidence())
                .needClarification(false)
                .clarificationQuestion(null)
                .reason(reason)
                .warehouseInventorySemantics(semantics)
                .expiryRiskFilter(mapped.getExpiryRiskFilter())
                .subQuestions(mapped.getSubQuestions())
                .promptId(mapped.getPromptId())
                .llmRawText(mapped.getLlmRawText())
                .parseError(mapped.getParseError())
                .intakeRepairAttempted(mapped.getIntakeRepairAttempted())
                .intakeRepairSuccess(mapped.getIntakeRepairSuccess())
                .intakeRepairReason(mapped.getIntakeRepairReason())
                .failureCode(mapped.getFailureCode())
                .failureStage(mapped.getFailureStage())
                .build();
    }

    /**
     * 供 Intake 澄清 / V2 后兜底统一使用的业务化话术；非风险语义时返回 null。
     */
    public static String resolveClarificationQuestion(SemanticIntakeResult intake) {
        if (intake == null || !signalsInventoryRisk(intake)) {
            return null;
        }
        String semantics = resolveEffectiveInventoryRiskSemantics(intake);
        if (SEMANTICS_NEAR_EXPIRY.equals(semantics)) {
            return CLARIFICATION_NEAR_EXPIRY;
        }
        if (SEMANTICS_OUT_OF_STOCK.equals(semantics)) {
            return CLARIFICATION_OUT_OF_STOCK;
        }
        if (reasonDeclaresAlert(intake.getReason())) {
            return CLARIFICATION_ALERT;
        }
        return CLARIFICATION_UNDERSTOCK_QUERY;
    }

    /**
     * V2 allowed 合同：库存风险语义下收窄 allowedContracts（结构化）；边界见 Intake/V2 Prompt，不注入中文 hints。
     */
    public static DomainContractSelectionResult filterContractSelection(
            DomainContractSelectionResult selection, SemanticIntakeResult intake) {
        if (selection == null || !intakeSignalsInventoryShortageSemantics(intake)) {
            return selection;
        }
        if (!SemanticIntakePrimaryDomain.WAREHOUSE.equals(
                blank(selection.getSelectedDomain()))) {
            return selection;
        }
        SemanticParserAllowedOutputContract contract = selection.getParserAllowedOutputContract();
        if (contract == null) {
            return selection;
        }
        String semantics = resolveEffectiveInventoryRiskSemantics(intake);
        if (SEMANTICS_NEAR_EXPIRY.equals(semantics)) {
            List<SemanticParserAllowedOutputContract.AllowedContractEntry> allowed = new ArrayList<>();
            if (contract.getAllowedContracts() != null) {
                for (SemanticParserAllowedOutputContract.AllowedContractEntry e : contract.getAllowedContracts()) {
                    if (e != null && CONTRACT_NEAR_EXPIRY.equals(e.getContractId())) {
                        allowed.add(e);
                    }
                }
            }
            if (allowed.isEmpty() && contract.getKnownGapContracts() != null) {
                for (SemanticParserAllowedOutputContract.AllowedContractEntry e : contract.getKnownGapContracts()) {
                    if (e != null && CONTRACT_NEAR_EXPIRY.equals(e.getContractId())) {
                        allowed.add(e);
                    }
                }
            }
            return buildFilteredSelection(selection, contract, allowed, allowed.size());
        }
        List<SemanticParserAllowedOutputContract.AllowedContractEntry> allowed = new ArrayList<>();
        if (contract.getAllowedContracts() != null) {
            for (SemanticParserAllowedOutputContract.AllowedContractEntry e : contract.getAllowedContracts()) {
                if (e != null && CONTRACT_INVENTORY_RISK_LIST.equals(e.getContractId())) {
                    allowed.add(e);
                }
            }
        }
        if (allowed.isEmpty() && contract.getKnownGapContracts() != null) {
            for (SemanticParserAllowedOutputContract.AllowedContractEntry e : contract.getKnownGapContracts()) {
                if (e != null && CONTRACT_INVENTORY_RISK_LIST.equals(e.getContractId())) {
                    allowed.add(e);
                }
            }
        }
        return buildFilteredSelection(selection, contract, allowed, allowed.size());
    }

    private static DomainContractSelectionResult buildFilteredSelection(
            DomainContractSelectionResult selection,
            SemanticParserAllowedOutputContract contract,
            List<SemanticParserAllowedOutputContract.AllowedContractEntry> allowedContracts,
            int activeCount) {
        SemanticParserAllowedOutputContract enriched =
                SemanticParserAllowedOutputContract.builder()
                        .selectedDomain(contract.getSelectedDomain())
                        .allowedContracts(allowedContracts)
                        .knownGapContracts(contract.getKnownGapContracts())
                        .contractSelectionBoundaryHints(null)
                        .allowedWires(contract.getAllowedWires())
                        .allowedQueryObjects(contract.getAllowedQueryObjects())
                        .allowedOperations(contract.getAllowedOperations())
                        .allowedMetrics(contract.getAllowedMetrics())
                        .allowedSourceFacets(contract.getAllowedSourceFacets())
                        .allowedDetailWanted(contract.getAllowedDetailWanted())
                        .allowedAnswerPlanTypes(contract.getAllowedAnswerPlanTypes())
                        .build();
        return DomainContractSelectionResult.builder()
                .selectedDomain(selection.getSelectedDomain())
                .selectedCapabilityContractCount(selection.getSelectedCapabilityContractCount())
                .selectedActiveContractCount(activeCount)
                .selectedKnownGapCount(selection.getSelectedKnownGapCount())
                .capabilityContractMissing(selection.isCapabilityContractMissing())
                .contractSelectionSkippedReason(selection.getContractSelectionSkippedReason())
                .parserAllowedOutputContract(enriched)
                .build();
    }

    /**
     * V2 后兜底：库存风险语义却选了 WH-C 或仍用采购域澄清 → 业务化澄清。
     */
    public static SemanticFrameValidationResult validateGoodsAmountRankingLowBlocked(
            AiQuerySemanticParseResult rawParse, SemanticIntakeResult intake) {
        if (rawParse == null
                || SemanticIntakeDishIngredientCoverDaysSupport.mustNotApplyWarehouseInventoryShortagePipeline(
                        intake, rawParse)
                || SemanticIntakeGoodsSupportedDishCoverSupport.mustNotApplyWarehouseInventoryShortagePipeline(
                        intake, rawParse)) {
            return SemanticFrameValidationResult.success();
        }
        if (!intakeSignalsInventoryShortageSemantics(intake)) {
            return SemanticFrameValidationResult.success();
        }
        String selected = SemanticContractCompletionEngine.extractSelectedContractId(rawParse);
        if (CONTRACT_INVENTORY_RISK_LIST.equals(blank(selected))
                || CONTRACT_NEAR_EXPIRY.equals(blank(selected))) {
            return SemanticFrameValidationResult.success();
        }
        if (CONTRACT_GOODS_AMOUNT_RANKING_LOW.equals(blank(selected))) {
            return SemanticFrameValidationResult.clarify(
                    firstNonBlank(resolveClarificationQuestion(intake), CLARIFICATION_UNDERSTOCK_QUERY),
                    List.of("WAREHOUSE_INVENTORY_SHORTAGE_NOT_AMOUNT_RANKING_LOW"));
        }
        if (Boolean.TRUE.equals(rawParse.getNeedClarification())) {
            String q = rawParse.getClarificationQuestion();
            if (isGenericDomainClarification(q) || isScopeAskingClarification(q)) {
                return SemanticFrameValidationResult.clarify(
                        firstNonBlank(resolveClarificationQuestion(intake), CLARIFICATION_UNDERSTOCK_QUERY),
                        List.of("WAREHOUSE_INVENTORY_RISK_GENERIC_CLARIFICATION_REPLACED"));
            }
        }
        return SemanticFrameValidationResult.success();
    }

    public static AiQuerySemanticParseResult applyRiskClarificationToParse(
            AiQuerySemanticParseResult sem, SemanticIntakeResult intake) {
        if (sem == null) {
            return sem;
        }
        if (intake != null
                && (intakeExplicitAmountRankingLow(intake)
                        || SemanticIntakeDishIngredientCoverDaysSupport
                                .mustNotApplyWarehouseInventoryShortagePipeline(intake)
                        || SemanticIntakeGoodsSupportedDishCoverSupport
                                .intakeDeclaresGoodsSupportedDishCover(intake))) {
            return sem;
        }
        if (parseOrIntakeDeclaresNearExpiryRisk(sem, intake)) {
            String selected = blank(SemanticContractCompletionEngine.extractSelectedContractId(sem));
            if (CONTRACT_NEAR_EXPIRY.equals(selected)) {
                return mergeExpiryRiskFilterIntoParse(
                        sem.toBuilder().needClarification(false).clarificationQuestion(null).build(),
                        intake);
            }
            String question = resolveClarificationQuestion(intake);
            return sem.toBuilder()
                    .needClarification(true)
                    .clarificationQuestion(
                            StringUtils.hasText(question) ? question : CLARIFICATION_NEAR_EXPIRY)
                    .build();
        }
        if (intake == null || !signalsInventoryRisk(intake)) {
            return sem;
        }
        String semantics = resolveEffectiveInventoryRiskSemantics(intake);
        if (SEMANTICS_NEAR_EXPIRY.equals(semantics)) {
            String selected = blank(SemanticContractCompletionEngine.extractSelectedContractId(sem));
            if (CONTRACT_NEAR_EXPIRY.equals(selected)) {
                return mergeExpiryRiskFilterIntoParse(
                        sem.toBuilder().needClarification(false).clarificationQuestion(null).build(),
                        intake);
            }
            String question = resolveClarificationQuestion(intake);
            return sem.toBuilder()
                    .needClarification(true)
                    .clarificationQuestion(
                            StringUtils.hasText(question) ? question : CLARIFICATION_NEAR_EXPIRY)
                    .build();
        }
        String selected = blank(SemanticContractCompletionEngine.extractSelectedContractId(sem));
        if (CONTRACT_GOODS_AMOUNT_RANKING_LOW.equals(selected)) {
            String question = resolveClarificationQuestion(intake);
            return sem.toBuilder()
                    .needClarification(true)
                    .clarificationQuestion(
                            StringUtils.hasText(question) ? question : CLARIFICATION_UNDERSTOCK_QUERY)
                    .build();
        }
        if (CONTRACT_INVENTORY_RISK_LIST.equals(selected)) {
            return sem.toBuilder().needClarification(false).clarificationQuestion(null).build();
        }
        String question = resolveClarificationQuestion(intake);
        return sem.toBuilder()
                .needClarification(true)
                .clarificationQuestion(
                        StringUtils.hasText(question) ? question : CLARIFICATION_UNDERSTOCK_QUERY)
                .build();
    }

    /**
     * 结构化金额排行语义优先：纠正 LLM 误标 risk marker 或误开澄清，保证 WH-C 可执行。
     */
    private static SemanticIntakeResult promoteExplicitAmountRankingIntake(SemanticIntakeResult mapped) {
        String reason =
                reasonDeclaresExplicitAmountRankingLow(mapped.getReason())
                        ? mapped.getReason().trim()
                        : REASON_MARKER_AMOUNT_RANKING_LOW;
        boolean alreadyReady =
                mapped.getStatus() == SemanticIntakeStatus.READY
                        && !Boolean.TRUE.equals(mapped.getNeedClarification());
        if (alreadyReady
                && SEMANTICS_EXPLICIT_AMOUNT_RANKING_LOW.equals(
                        normalizeSemantics(mapped.getWarehouseInventorySemantics()))) {
            return mapped;
        }
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.READY)
                .questionMode(mapped.getQuestionMode())
                .normalizationType(mapped.getNormalizationType())
                .canonicalUserQuery(mapped.getCanonicalUserQuery())
                .isFollowUp(mapped.getIsFollowUp())
                .usedPreviousContext(mapped.getUsedPreviousContext())
                .primaryDomain(SemanticIntakePrimaryDomain.WAREHOUSE)
                .candidateDomains(
                        mapped.getCandidateDomains() != null
                                ? mapped.getCandidateDomains()
                                : List.of(SemanticIntakePrimaryDomain.WAREHOUSE))
                .routeType("EXPLICIT")
                .confidence(mapped.getConfidence())
                .needClarification(false)
                .clarificationQuestion(null)
                .reason(reason)
                .warehouseInventorySemantics(SEMANTICS_EXPLICIT_AMOUNT_RANKING_LOW)
                .subQuestions(mapped.getSubQuestions())
                .promptId(mapped.getPromptId())
                .llmRawText(mapped.getLlmRawText())
                .parseError(mapped.getParseError())
                .intakeRepairAttempted(mapped.getIntakeRepairAttempted())
                .intakeRepairSuccess(mapped.getIntakeRepairSuccess())
                .intakeRepairReason(mapped.getIntakeRepairReason())
                .failureCode(mapped.getFailureCode())
                .failureStage(mapped.getFailureStage())
                .build();
    }

    private static boolean signalsInventoryRisk(SemanticIntakeResult intake) {
        if (intake == null || intakeExplicitAmountRankingLow(intake)) {
            return false;
        }
        if (WarehouseInventorySupervisionSemanticsSupport.intakeDeclaresSupervisionQuery(intake)) {
            return false;
        }
        if (SemanticIntakeDishIngredientCoverDaysSupport.reasonDeclaresDishIngredientCoverDays(
                intake.getReason())) {
            return false;
        }
        if (SemanticIntakeDishIngredientCoverDaysSupport.rawWarehouseSemanticsDeclaresDishCoverMislabel(
                intake.getWarehouseInventorySemantics())) {
            return false;
        }
        String semantics = normalizeSemantics(intake.getWarehouseInventorySemantics());
        if (isInventoryRiskSemantics(semantics)) {
            return true;
        }
        if (intakeDeclaresNearExpiryRiskFilter(intake)) {
            return true;
        }
        return reasonDeclaresShortageSemantics(intake.getReason());
    }

    private static boolean reasonDeclaresAlert(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        return reason.toLowerCase(Locale.ROOT).contains(REASON_MARKER_ALERT);
    }

    private static String inferSemanticsFromReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return SEMANTICS_UNDERSTOCK_QUERY;
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("near_expiry")) {
            return SEMANTICS_NEAR_EXPIRY;
        }
        if (normalized.contains("out_of_stock")) {
            return SEMANTICS_OUT_OF_STOCK;
        }
        if (reasonDeclaresAlert(reason)) {
            return SEMANTICS_UNDERSTOCK_QUERY;
        }
        return SEMANTICS_UNDERSTOCK_QUERY;
    }

    private static boolean isGenericDomainClarification(String question) {
        if (!StringUtils.hasText(question)) {
            return true;
        }
        String q = question.trim();
        return q.contains("采购的哪一类")
                || q.contains("采购总览")
                || q.contains("营业额、采购、库存")
                || q.contains("总览、排行还是明细")
                || q.contains("总览、商品排行还是门店排行")
                || q.equals("能再具体说一下您想问的内容吗？")
                || isScopeAskingClarification(q);
    }

    /** Intake/V2 误生成的「追问范围」式澄清，应替换为 known gap 说明（非用户原文 NL 分流）。 */
    private static boolean isScopeAskingClarification(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String q = question.trim();
        return q.contains("具体哪些")
                || q.contains("所有原料")
                || q.contains("哪些原料还是")
                || q.contains("原料还是所有")
                || (q.contains("范围") && (q.contains("原料") || q.contains("商品")));
    }

    private static String firstNonBlank(String a, String b) {
        return StringUtils.hasText(a) ? a.trim() : b;
    }

    private static String blank(String s) {
        return s == null ? null : s.trim();
    }

    private static AiQuerySemanticParseResult mergeExpiryRiskFilterIntoParse(
            AiQuerySemanticParseResult sem, SemanticIntakeResult intake) {
        String filter = resolveMergedExpiryRiskFilter(sem, intake);
        if (!StringUtils.hasText(filter)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = sem.getSemanticSlots();
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                slots == null
                        ? AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .expiryRiskFilter(filter)
                                .build()
                        : AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId(slots.getSelectedContractId())
                                .queryObject(slots.getQueryObject())
                                .operation(slots.getOperation())
                                .metric(slots.getMetric())
                                .sourceFacet(slots.getSourceFacet())
                                .anchorPolicy(slots.getAnchorPolicy())
                                .detailWanted(slots.getDetailWanted())
                                .structuredIntentDetailWire(slots.getStructuredIntentDetailWire())
                                .answerPlanType(slots.getAnswerPlanType())
                                .mentionedDishName(slots.getMentionedDishName())
                                .mentionedGoodsName(slots.getMentionedGoodsName())
                                .requestedTargetGrossMarginRate(slots.getRequestedTargetGrossMarginRate())
                                .expiryRiskFilter(filter)
                                .build();
        return sem.toBuilder().semanticSlots(updated).build();
    }

    private static String resolveMergedExpiryRiskFilter(
            AiQuerySemanticParseResult sem, SemanticIntakeResult intake) {
        if (sem != null && sem.getSemanticSlots() != null) {
            String fromSlots =
                    WarehouseNearExpiryRiskFilterSupport.normalizeFilter(
                            sem.getSemanticSlots().getExpiryRiskFilter());
            if (fromSlots != null) {
                return fromSlots;
            }
        }
        if (intake != null) {
            return WarehouseNearExpiryRiskFilterSupport.normalizeFilter(intake.getExpiryRiskFilter());
        }
        return null;
    }

}
