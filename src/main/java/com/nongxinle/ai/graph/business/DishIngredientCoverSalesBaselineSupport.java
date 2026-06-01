package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;

/**
 * {@code dish.ingredient_cover_days.v1} 日均销量基线：默认近 7 天；仅当 Time Layer 判定本句显式时间时用用户时间窗。
 * 不读 rawMessage；不改语义合同。
 */
public final class DishIngredientCoverSalesBaselineSupport {
    static final String COST_DATA_BASELINE_KEY = "dishIngredientCoverSalesBaseline";

    private static final DateTimeFormatter CN_MD =
            DateTimeFormatter.ofPattern("M月d日", Locale.CHINA);

    private DishIngredientCoverSalesBaselineSupport() {}

    public static DishIngredientCoverSalesBaseline resolve(AiRunState state, AiResolvedQueryContext rq) {
        LocalDate anchor = resolveAnchorDate(state, rq);
        if (isUserExplicitSalesBaselineTime(rq)) {
            LocalDate start = readStartDate(state, rq);
            LocalDate end = readEndDate(state, rq);
            if (start == null || end == null) {
                return defaultLast7Days(anchor);
            }
            if (end.isBefore(start)) {
                LocalDate tmp = start;
                start = end;
                end = tmp;
            }
            int days = (int) Math.max(1, ChronoUnit.DAYS.between(start, end) + 1);
            String label = formatExplicitBaselineLabel(rq, start, end);
            return DishIngredientCoverSalesBaseline.builder()
                    .startDateIso(start.toString())
                    .stopDateIso(end.toString())
                    .baselineDays(days)
                    .baselineSource(DishIngredientCoverSalesBaseline.SOURCE_USER_EXPLICIT_TIME_WINDOW)
                    .displayLabel(label)
                    .build();
        }
        return defaultLast7Days(anchor);
    }

    public static DishIngredientCoverSalesBaseline fromCostData(Map<String, Object> costData) {
        if (costData == null) {
            return null;
        }
        return DishIngredientCoverSalesBaseline.fromWireMap(costData.get(COST_DATA_BASELINE_KEY));
    }

    public static boolean isUserExplicitSalesBaselineTime(AiResolvedQueryContext rq) {
        if (rq == null || !StringUtils.hasText(rq.getEffectiveTimeWindowSource())) {
            return false;
        }
        return SemanticTimeContractCheck.SOURCE_CURRENT_MESSAGE_EXPLICIT.equals(
                rq.getEffectiveTimeWindowSource().trim());
    }

    private static DishIngredientCoverSalesBaseline defaultLast7Days(LocalDate anchor) {
        LocalDate end = anchor == null ? LocalDate.now() : anchor;
        LocalDate start = end.minusDays(DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS - 1L);
        String label = String.format(
                Locale.CHINA,
                "最近%d天（%s至%s）",
                DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS,
                CN_MD.format(start),
                CN_MD.format(end));
        return DishIngredientCoverSalesBaseline.builder()
                .startDateIso(start.toString())
                .stopDateIso(end.toString())
                .baselineDays(DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS)
                .baselineSource(DishIngredientCoverSalesBaseline.SOURCE_DEFAULT_LAST_7_DAYS)
                .displayLabel(label)
                .build();
    }

    private static LocalDate resolveAnchorDate(AiRunState state, AiResolvedQueryContext rq) {
        if (state != null && StringUtils.hasText(state.getStatEndDate())) {
            try {
                return LocalDate.parse(state.getStatEndDate().trim());
            } catch (Exception ignored) {
                // fall through
            }
        }
        AiResolvedTimeWindow tw = rq == null ? null : rq.getTimeWindow();
        if (tw != null && tw.getEndDate() != null) {
            return tw.getEndDate();
        }
        return LocalDate.now();
    }

    private static LocalDate readStartDate(AiRunState state, AiResolvedQueryContext rq) {
        AiResolvedTimeWindow tw = rq == null ? null : rq.getTimeWindow();
        if (tw != null && tw.getStartDate() != null) {
            return tw.getStartDate();
        }
        if (state != null && StringUtils.hasText(state.getStatStartDate())) {
            try {
                return LocalDate.parse(state.getStatStartDate().trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static LocalDate readEndDate(AiRunState state, AiResolvedQueryContext rq) {
        AiResolvedTimeWindow tw = rq == null ? null : rq.getTimeWindow();
        if (tw != null && tw.getEndDate() != null) {
            return tw.getEndDate();
        }
        if (state != null && StringUtils.hasText(state.getStatEndDate())) {
            try {
                return LocalDate.parse(state.getStatEndDate().trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String formatExplicitBaselineLabel(AiResolvedQueryContext rq, LocalDate start, LocalDate end) {
        if (rq != null && StringUtils.hasText(rq.getTimeWindowLabel())) {
            return "销量基线：" + rq.getTimeWindowLabel().trim();
        }
        return String.format(Locale.CHINA, "销量基线：%s至%s", CN_MD.format(start), CN_MD.format(end));
    }
}
