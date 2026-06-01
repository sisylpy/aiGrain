package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.semantic.dimension.BareRankingDimensionSwitchSupport;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Intake 后处理：完整显式多菜排行问法（非裸维度切换）与 {@code DISH_COST} 单菜明细域的边界。
 * 仅读 Intake 结构化字段（canonical / primaryDomain / reason），不解析用户 rawMessage。
 * <p>部分显式排行观测码（如 {@code dish_actual_cost_ranking_high_explicit}）暂存于 {@code reason}，
 * 与裸维度切换 token 同属 schema v1 过渡 marker；见 {@code docs/ai/semantic-intake-schema-evolution.md}。
 */
public final class SemanticIntakeMultiDishRankingSupport {

    private SemanticIntakeMultiDishRankingSupport() {}

    public static boolean isNamedDishIntakeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        return reason.trim().toLowerCase(Locale.ROOT).startsWith("named_dish_");
    }

    /** canonical 是否表达多菜排行（哪些/最高/排行等），不判断是否成本/销量指标。 */
    public static boolean looksLikeMultiDishRankingCanonical(String canonical) {
        if (!StringUtils.hasText(canonical)) {
            return false;
        }
        String c = canonical.trim();
        return c.contains("排行")
                || c.contains("最高")
                || c.contains("最低")
                || c.contains("哪些")
                || c.contains("有哪些")
                || c.contains("哪个菜")
                || c.contains("哪道菜")
                || c.contains("哪些菜");
    }

    /** canonical 是否表达多菜实际成本排行（含「菜品成本排行」等完整显式问法）。 */
    public static boolean looksLikeMultiDishCostRankingCanonical(String canonical) {
        if (!StringUtils.hasText(canonical)) {
            return false;
        }
        String c = canonical.trim();
        if (c.contains("成本排行")
                || c.contains("成本排名")
                || c.contains("实际成本排名")
                || c.contains("实际成本排行")
                || c.contains("菜品成本排行")) {
            return true;
        }
        return looksLikeMultiDishRankingCanonical(c)
                && (c.contains("成本") || c.contains("实际成本"));
    }

    /**
     * 完整显式多菜排行 Intake：禁止从 rewrite / previousTurn 注入 Top1 菜名 anchor。
     */
    public static boolean suppressRewriteAnchorInjection(SemanticIntakeResult intake) {
        return isExplicitMultiDishRankingIntake(intake);
    }

    public static boolean isExplicitMultiDishRankingIntake(SemanticIntakeResult intake) {
        if (intake == null || intake.getStatus() != SemanticIntakeStatus.READY) {
            return false;
        }
        if (isNamedDishIntakeReason(intake.getReason())) {
            return false;
        }
        if (hasExplicitRankingReason(intake.getReason())) {
            return true;
        }
        return looksLikeMultiDishRankingCanonical(intake.getCanonicalUserQuery());
    }

    private static boolean hasExplicitRankingReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String r = reason.trim().toLowerCase(Locale.ROOT);
        return r.contains("dish_actual_cost_ranking")
                || r.contains("dish_profit_actual_cost_ranking")
                || r.contains("dish_sales_ranking")
                || r.contains("_to_cost_ranking")
                || r.contains("_to_margin_ranking")
                || r.contains("_to_profit_amount_ranking")
                || r.contains("_to_sales_ranking")
                || r.contains("_to_amount_ranking");
    }

    /**
     * 将 LLM 误输出的 {@code DISH_COST + 多菜成本排行 canonical} 纠正为 {@code DISH_PROFIT} 排行域。
     */
    public static SemanticIntakeResult reconcileExplicitMultiDishRankingDomain(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (intake == null || intake.getStatus() != SemanticIntakeStatus.READY) {
            return intake;
        }
        if (BareRankingDimensionSwitchSupport.isOutsideBareRankingDimensionSwitchScope(intake)) {
            return intake;
        }
        if (isNamedDishIntakeReason(intake.getReason())) {
            return intake;
        }
        String canonical = trim(intake.getCanonicalUserQuery());
        if (!looksLikeMultiDishCostRankingCanonical(canonical)) {
            return reconcileMisroutedMultiDishRankingOnDishCost(intake, canonical);
        }
        String primary = SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain());
        if (SemanticIntakePrimaryDomain.DISH_PROFIT.equals(primary)) {
            return normalizeDishProfitCostRankingIntake(intake);
        }
        if (SemanticIntakePrimaryDomain.DISH_COST.equals(primary)) {
            return promoteCostRankingToDishProfit(intake);
        }
        return intake;
    }

    private static SemanticIntakeResult reconcileMisroutedMultiDishRankingOnDishCost(
            SemanticIntakeResult intake, String canonical) {
        if (!looksLikeMultiDishRankingCanonical(canonical)) {
            return intake;
        }
        String primary = SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain());
        if (!SemanticIntakePrimaryDomain.DISH_COST.equals(primary)) {
            return intake;
        }
        if (canonical.contains("毛利") || canonical.contains("毛利率")) {
            return promoteDomain(intake, SemanticIntakePrimaryDomain.DISH_PROFIT, "dish_profit_ranking_reconciled");
        }
        if (canonical.contains("销量")
                || canonical.contains("卖得")
                || canonical.contains("卖得多")
                || canonical.contains("销售额")) {
            return promoteDomain(intake, SemanticIntakePrimaryDomain.DISH_SALES, "dish_sales_ranking_reconciled");
        }
        return intake;
    }

    private static SemanticIntakeResult promoteCostRankingToDishProfit(SemanticIntakeResult intake) {
        return promoteDomain(
                intake,
                SemanticIntakePrimaryDomain.DISH_PROFIT,
                "dish_actual_cost_ranking_high_explicit_reconciled");
    }

    private static SemanticIntakeResult normalizeDishProfitCostRankingIntake(SemanticIntakeResult intake) {
        String reason = trim(intake.getReason());
        if (!StringUtils.hasText(reason) || !hasExplicitRankingReason(reason)) {
            reason = appendReconciledSuffix("dish_actual_cost_ranking_high_explicit");
        } else if (!reason.endsWith("_reconciled")) {
            reason = reason + "_reconciled";
        }
        return copyReadyIntake(
                intake,
                SemanticIntakePrimaryDomain.DISH_PROFIT,
                List.of(SemanticIntakePrimaryDomain.DISH_PROFIT),
                "EXPLICIT",
                false,
                null,
                reason);
    }

    private static SemanticIntakeResult promoteDomain(
            SemanticIntakeResult intake, String targetDomain, String reason) {
        return copyReadyIntake(
                intake,
                targetDomain,
                List.of(targetDomain),
                "EXPLICIT",
                false,
                null,
                appendReconciledSuffix(reason));
    }

    private static SemanticIntakeResult copyReadyIntake(
            SemanticIntakeResult intake,
            String primaryDomain,
            List<String> candidateDomains,
            String routeType,
            boolean needClarification,
            String clarificationQuestion,
            String reason) {
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.READY)
                .questionMode(intake.getQuestionMode())
                .normalizationType(intake.getNormalizationType())
                .canonicalUserQuery(intake.getCanonicalUserQuery())
                .isFollowUp(intake.getIsFollowUp())
                .usedPreviousContext(intake.getUsedPreviousContext())
                .primaryDomain(primaryDomain)
                .candidateDomains(candidateDomains)
                .routeType(routeType)
                .confidence(intake.getConfidence())
                .needClarification(needClarification)
                .clarificationQuestion(clarificationQuestion)
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

    private static String appendReconciledSuffix(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "multi_dish_ranking_reconciled";
        }
        return reason.endsWith("_reconciled") ? reason : reason + "_reconciled";
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
