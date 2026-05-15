package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜品毛利只读桥接入参（C-26）。字段须来自已解析上下文及编排注入；
 * <strong>不得</strong>包含用户聊天原文。
 *
 * @see DishProfitPlannerReadBridge
 * @see DishProfitPlannerAgentAdapter
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DishProfitPlannerReadRequest {

    private String resolvedQueryContextRef;

    private LocalDate timeStart;
    private LocalDate timeEnd;

    private String timeLabel;

    private String scopeType;

    @Builder.Default
    private List<DishProfitPlannerVisibleStore> visibleStores = new ArrayList<>();

    @Builder.Default
    private List<Long> queryDepartmentIds = new ArrayList<>();

    private Long targetStoreDepartmentId;

    /** 与 {@link com.nongxinle.ai.conversation.AiQuerySemanticLexicon} structured wire 对齐（如 dish_profit_overview）。 */
    private String structuredIntentDetail;

    /** 与 {@link com.nongxinle.ai.context.AiResolvedQueryContext#getMentionedDishName()} 对齐；可为空。 */
    private String mentionedDishName;

    /** 与 {@link com.nongxinle.ai.context.AiResolvedQueryContext#getDishProfitMetricType()} 对齐；可为空。 */
    private String dishProfitMetricType;

    private String answerPlanRef;
}
