package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 出库/核销只读桥接入参（C-21）。字段须来自已解析上下文及编排注入；
 * <strong>不得</strong>包含用户聊天原文。
 *
 * @see StockReducePlannerReadBridge
 * @see StockReducePlannerAgentAdapter
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class StockReducePlannerReadRequest {

    private String resolvedQueryContextRef;

    private LocalDate timeStart;
    private LocalDate timeEnd;

    private String timeLabel;

    private String scopeType;

    @Builder.Default
    private List<StockReducePlannerVisibleStore> visibleStores = new ArrayList<>();

    @Builder.Default
    private List<Long> queryDepartmentIds = new ArrayList<>();

    private Long targetStoreDepartmentId;

    /** 与 {@link com.nongxinle.ai.dto.business.StockReduceAnswerPlan} reduce 分型对齐（如 ALL / TYPE1…）。 */
    private String reduceType;

    /** 与 {@link com.nongxinle.ai.conversation.AiQuerySemanticLexicon} structured wire 对齐（如 stock_reduce_overview）。 */
    private String structuredIntentDetail;

    /**
     * 与 Tool 内层口径对齐，如 {@code CALENDAR_NATURAL_DAY} / {@code DAILY_REVENUE_DAYS_ONLY}（仅结构化标签，C-21 不接 DB）。
     */
    private String totalsBasis;

    private String answerPlanRef;
}
