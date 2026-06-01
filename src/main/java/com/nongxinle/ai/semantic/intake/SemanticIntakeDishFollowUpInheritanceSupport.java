package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Intake 后处理：显式换菜追问（Intake 结构化 {@code isFollowUp}+REWRITE）且上一轮为菜品销售/毛利/成本上下文时，
 * 仅在当前轮 Intake 未给出与上一轮不同的可执行 primaryDomain 时，才从 structured previousTurn 继承一级业务域，
 * 以抑制 DISH_SALES / DISH_PROFIT / DISH_COST 子域澄清。
 * <p>当前轮已明确 primaryDomain（如 DISH_PROFIT / DISH_COST）时不得被上一轮域（如 DISH_SALES）覆盖；
 * {@code usedPreviousContext} / follow-up 只继承 time / scope，不无条件继承业务 domain。
 * 不读 rawMessage 猜语义，不用 alias；时间短句追问由 V2 {@code timeAction}/{@code anchorPolicy} 主链处理。
 */
public final class SemanticIntakeDishFollowUpInheritanceSupport {

    private static final Set<String> DISH_SUB_DOMAINS =
            Set.of(
                    SemanticIntakePrimaryDomain.DISH_SALES,
                    SemanticIntakePrimaryDomain.DISH_PROFIT,
                    SemanticIntakePrimaryDomain.DISH_COST);

    private SemanticIntakeDishFollowUpInheritanceSupport() {}

    public static SemanticIntakeResult reconcile(SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (input == null || intake == null || !input.isHasPreviousTurn()) {
            return intake;
        }
        if (WarehouseInventoryShortageSemanticsSupport.intakeHasAuthoritativeInventoryRisk(intake)
                || SemanticIntakeDishIngredientCoverDaysSupport
                        .isDishCoverToWarehouseInventoryRiskCrossFollowUp(input, intake)) {
            return intake;
        }
        String inheritedDomain = resolveInheritableDishDomain(input);
        if (!StringUtils.hasText(inheritedDomain)) {
            return intake;
        }
        String intakePrimary = SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain());
        if (SemanticIntakePrimaryDomain.isExecutable(intakePrimary)
                && !inheritedDomain.equals(intakePrimary)) {
            return intake;
        }
        if (!isExplicitDishSwapFollowUp(input, intake)) {
            return intake;
        }
        if (!needsDishSubDomainClarification(intake)) {
            return intake;
        }
        return promoteToReadyInherited(intake, inheritedDomain);
    }

    private static String resolveInheritableDishDomain(SemanticIntakeInput input) {
        String path = trim(input.getPreviousPathCode());
        if (AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(path)) {
            return SemanticIntakePrimaryDomain.DISH_SALES;
        }
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(path)) {
            return SemanticIntakePrimaryDomain.DISH_PROFIT;
        }
        if (AiResolvedQueryIntent.PATH_DISH_COST_ANALYSIS.equals(path)) {
            return SemanticIntakePrimaryDomain.DISH_COST;
        }
        String prevStructured = trim(input.getPreviousStructuredIntentDetail());
        if (StringUtils.hasText(prevStructured)) {
            String canon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(prevStructured);
            if (AiQuerySemanticLexicon.isStructuredDishCostAnalysisDetail(canon)
                    || AiQuerySemanticLexicon.isStructuredDishProfitPrescriptionDetail(canon)
                    || AiQuerySemanticLexicon.isStructuredDishIngredientCoverDaysDetail(canon)) {
                return SemanticIntakePrimaryDomain.DISH_COST;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart prevSlots = input.getPreviousSemanticSlots();
        if (prevSlots == null || !StringUtils.hasText(prevSlots.getStructuredIntentDetailWire())) {
            return null;
        }
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        prevSlots.getStructuredIntentDetailWire().trim());
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        if (AiQuerySemanticLexicon.isStructuredDishSalesDetail(wire)) {
            return SemanticIntakePrimaryDomain.DISH_SALES;
        }
        if (AiQuerySemanticLexicon.isNonOverviewDishProfitStructuredDetail(wire)) {
            return SemanticIntakePrimaryDomain.DISH_PROFIT;
        }
        if (AiQuerySemanticLexicon.isStructuredDishCostAnalysisDetail(wire)
                || AiQuerySemanticLexicon.isStructuredDishProfitPrescriptionDetail(wire)
                || AiQuerySemanticLexicon.isStructuredDishIngredientCoverDaysDetail(wire)) {
            return SemanticIntakePrimaryDomain.DISH_COST;
        }
        return null;
    }

    /**
     * 换菜追问：Intake 结构化 {@code isFollowUp=true} + REWRITE，且 canonical 未继续锚定 previousTurn 菜名。
     */
    private static boolean isExplicitDishSwapFollowUp(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (!Boolean.TRUE.equals(intake.getIsFollowUp())) {
            return false;
        }
        if (intake.getNormalizationType() != SemanticIntakeNormalizationType.REWRITE) {
            return false;
        }
        String canonical = trim(intake.getCanonicalUserQuery());
        if (!StringUtils.hasText(canonical)) {
            return false;
        }
        String prevDish = trim(input.getPreviousMentionedDishName());
        if (!StringUtils.hasText(prevDish)) {
            return true;
        }
        return !canonical.contains(prevDish);
    }

    private static boolean needsDishSubDomainClarification(SemanticIntakeResult intake) {
        if (intake.getStatus() == SemanticIntakeStatus.NEED_CLARIFICATION
                || Boolean.TRUE.equals(intake.getNeedClarification())) {
            return isDishSubDomainAmbiguity(intake);
        }
        String routeType = trim(intake.getRouteType());
        if ("AMBIGUOUS".equals(routeType) || "UNKNOWN".equals(routeType)) {
            return isDishSubDomainAmbiguity(intake);
        }
        String primary = SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain());
        if (SemanticIntakePrimaryDomain.MULTI_DOMAIN.equals(primary)
                || SemanticIntakePrimaryDomain.UNKNOWN.equals(primary)) {
            return isDishSubDomainAmbiguity(intake);
        }
        return false;
    }

    private static boolean isDishSubDomainAmbiguity(SemanticIntakeResult intake) {
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        String primary = SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain());
        if (StringUtils.hasText(primary)) {
            domains.add(primary);
        }
        List<String> candidates = intake.getCandidateDomains();
        if (candidates != null) {
            for (String c : candidates) {
                String n = SemanticIntakePrimaryDomain.normalize(c);
                if (StringUtils.hasText(n)) {
                    domains.add(n);
                }
            }
        }
        if (domains.isEmpty()) {
            return false;
        }
        for (String d : domains) {
            if (!DISH_SUB_DOMAINS.contains(d)) {
                return false;
            }
        }
        return true;
    }

    private static SemanticIntakeResult promoteToReadyInherited(
            SemanticIntakeResult intake, String inheritedDomain) {
        String reason = trim(intake.getReason());
        if (!StringUtils.hasText(reason)) {
            reason = "dish_swap_inherited_domain";
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
                .primaryDomain(inheritedDomain)
                .candidateDomains(intake.getCandidateDomains())
                .routeType("INHERITED")
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

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
