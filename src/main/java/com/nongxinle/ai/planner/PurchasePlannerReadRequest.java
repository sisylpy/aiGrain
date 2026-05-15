package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购只读桥接入参（C-16）。字段须来自已解析上下文及编排注入；
 * <strong>不得</strong>包含用户聊天原文。
 *
 * @see PurchasePlannerReadBridge
 * @see PurchasePlannerAgentAdapter
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PurchasePlannerReadRequest {

    private String resolvedQueryContextRef;

    private LocalDate timeStart;
    private LocalDate timeEnd;

    private String timeLabel;

    private String scopeType;

    @Builder.Default
    private List<PurchasePlannerVisibleStore> visibleStores = new ArrayList<>();

    @Builder.Default
    private List<Long> queryDepartmentIds = new ArrayList<>();

    private Long targetStoreDepartmentId;

    /** 与 {@link com.nongxinle.ai.conversation.AiQuerySemanticLexicon} 采购来源对齐（如 ALL / SELF_PURCHASE / SUPPLIER_PURCHASE）。 */
    private String purchaseSourceType;

    /** 与 {@link com.nongxinle.ai.conversation.AiQuerySemanticLexicon} structured wire 对齐（如 purchase_overview_summary）。 */
    private String structuredIntentDetail;

    private String answerPlanRef;
}
