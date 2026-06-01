package com.nongxinle.ai.dto.business;

import com.alibaba.fastjson2.annotation.JSONField;
import com.nongxinle.ai.semantic.matrix.DishProfitSemanticCapabilityMatrix;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜品毛利链路：服务端生成的回答计划（Replay / Debug / Composer 同源）。
 * <p>
 * 排序与选行在 Java 完成；Composer 只消费 {@link #focusRows} / {@link #secondaryRows} 中已有字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishProfitAnswerPlan {

    public static final String TYPE_DISH_LOWEST_MARGIN = "DISH_LOWEST_MARGIN";
    /** 高毛利率排行（综合毛利率由高到低）。 */
    public static final String TYPE_DISH_HIGHEST_MARGIN = "DISH_HIGHEST_MARGIN";
    /** 低毛利额排行（标价收入−实际成本，由低到高）。 */
    public static final String TYPE_DISH_LOWEST_PROFIT_AMOUNT = "DISH_LOWEST_PROFIT_AMOUNT";
    /** 高毛利额排行（标价收入−实际成本，由高到低）。 */
    public static final String TYPE_DISH_HIGHEST_PROFIT_AMOUNT = "DISH_HIGHEST_PROFIT_AMOUNT";
    public static final String TYPE_DISH_HIGHEST_ACTUAL_COST = "DISH_HIGHEST_ACTUAL_COST";
    public static final String TYPE_DISH_PROFIT_REASON = "DISH_PROFIT_REASON";

    /** 单菜理论/标准/配方成本（buildInsight 行字段） */
    public static final String TYPE_DISH_THEORETICAL_COST = "DISH_THEORETICAL_COST";
    /** 单菜实际出库成本（type1+2+3 汇总口径，同 buildInsight） */
    public static final String TYPE_DISH_ACTUAL_OUTBOUND_COST = "DISH_ACTUAL_OUTBOUND_COST";
    /** 单菜综合毛利率（blendedGrossMarginRateOnListPrice） */
    public static final String TYPE_DISH_PROFIT_RATE = "DISH_PROFIT_RATE";
    /** 单菜理论与实际成本差异关切 */
    public static final String TYPE_DISH_COST_GAP = "DISH_COST_GAP";

    /**
     * 经营诊断 path：无结构化子意图挂载时的菜品侧概览（由 {@code AiDishProfitOverviewResult} 派生，不重算）。
     */
    public static final String TYPE_BUSINESS_DIAGNOSIS_DISH_OVERVIEW = "BUSINESS_DIAGNOSIS_DISH_OVERVIEW";

    /**
     * 经营概览 Multi-Agent / 菜品专线：结构化子意图未能挂载 AnswerPlan 时，
     * 仅凭工具快照挂载的聚合菜品档位（与同诊断 fallback 数据结构一致；类型区分以便调试与契约比对）。
     */
    public static final String TYPE_AGGREGATED_DISH_PORTFOLIO_FALLBACK = "AGGREGATED_DISH_PORTFOLIO_FALLBACK";

    /**
     * D-13.3A：原料成本构成下钻（当前 Tool 无 ingredient 明细；协议与 Composer 诚实降级）。
     */
    public static final String TYPE_DISH_INGREDIENT_COST_BREAKDOWN = "DISH_INGREDIENT_COST_BREAKDOWN";

    /** 排行类无销量证据时的 EMPTY/NO_DATA 态（非聚合 portfolio fallback）。 */
    public static final String TYPE_DISH_PROFIT_RANKING_NO_DATA = "DISH_PROFIT_RANKING_NO_DATA";

    /**
     * 菜品成本/毛利排行卡（{@code focusRows + secondaryRows} 投影，不重算）；
     * 含 EMPTY 无数据态。
     */
    public static final String CARD_TYPE_DISH_PROFIT_RANKING = "DISH_PROFIT_RANKING_CARD";

    /** @deprecated 请读 {@link #CARD_TYPE_DISH_PROFIT_RANKING}；保留常量供旧断言/前端过渡。 */
    @Deprecated
    public static final String CARD_TYPE_DISH_PROFIT_COST_RANKING = CARD_TYPE_DISH_PROFIT_RANKING;

    /** payload.rankingType：实际成本由高到低。 */
    public static final String RANKING_TYPE_ACTUAL_COST_HIGH = "ACTUAL_COST_HIGH";
    /** payload.rankingType：实际成本由低到高（预留，与 AnswerPlan 扩展对齐）。 */
    public static final String RANKING_TYPE_ACTUAL_COST_LOW = "ACTUAL_COST_LOW";
    /** payload.rankingType：毛利率由高到低。 */
    public static final String RANKING_TYPE_MARGIN_HIGH = "MARGIN_HIGH";
    /** payload.rankingType：毛利率由低到高。 */
    public static final String RANKING_TYPE_MARGIN_LOW = "MARGIN_LOW";
    /** payload.rankingType：毛利额（元）由高到低。 */
    public static final String RANKING_TYPE_PROFIT_AMOUNT_HIGH = "PROFIT_AMOUNT_HIGH";
    /** payload.rankingType：毛利额（元）由低到高。 */
    public static final String RANKING_TYPE_PROFIT_AMOUNT_LOW = "PROFIT_AMOUNT_LOW";
    /** payload.rankingType：实际与理论成本差额由高到低（{@link #TYPE_DISH_COST_GAP}）。 */
    public static final String RANKING_TYPE_COST_GAP_HIGH = "COST_GAP_HIGH";
    /** Card 投影无法从明确 {@link #planType} 解析排行展示语义时使用；须带 payload 警告，禁止用 sortDirection 兜底。 */
    public static final String RANKING_TYPE_UNKNOWN = "UNKNOWN";

    /** 与文档 {@code answerPlan.type} 对齐 */
    @JSONField(name = "type")
    private String planType;

    private String scopeLabel;
    private String timeLabel;

    /** 选行排序键（如 {@code blendedGrossMarginRateOnListPrice}）；单菜原因类可为 null */
    private String sortKey;

    /** {@code ASC} / {@code DESC}；单菜原因类可为 null */
    private String sortDirection;

    private Integer topN;

    @Builder.Default
    private List<Map<String, Object>> focusRows = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> secondaryRows = new ArrayList<>();

    /** Debug：供面板与回归比对，非 LLM 输入必需 */
    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();

    /** D-13.3：多轮 DISH 锚点（低/高毛利排行、单菜指标等）；无菜名不得写入。 */
    @Builder.Default
    private List<AiResultAnchor> resultAnchors = new ArrayList<>();

    // --- D-13.3B：原料成本构成（与 dish_ingredient_cost_breakdown 工具同源；Composer 只读，不重算）---

    private Boolean ingredientBreakdownAvailable;
    private String ingredientBreakdownUnavailableReason;

    @Builder.Default
    private List<Map<String, Object>> ingredientRows = new ArrayList<>();

    private String totalIngredientCost;

    @Builder.Default
    private List<Map<String, Object>> missingPriceItems = new ArrayList<>();

    public static Map<String, Object> emptyDebug() {
        return new LinkedHashMap<>();
    }

    /** 产出可被「原料构成」类锚 execution 承接的上一轮锚点类型（不含聚合 fallback / 原料 wire 自身）。 */
    public static boolean isDishAnchorExecutionSourcePlanType(String sourcePlanType) {
        return DishProfitSemanticCapabilityMatrix.isDishAnchorSourcePlanType(sourcePlanType);
    }

    /** 本轮 AnswerPlan 是否在服务端生成 DISH {@link AiResultAnchor}（矩阵 {@link DishProfitSemanticCapabilityMatrix#emitsDishResultAnchor}）。 */
    public static boolean planTypeEmitsResultAnchor(String planType) {
        return DishProfitSemanticCapabilityMatrix.emitsDishResultAnchor(planType);
    }
}
