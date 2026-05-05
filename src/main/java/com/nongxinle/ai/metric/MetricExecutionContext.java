package com.nongxinle.ai.metric;

import com.nongxinle.ai.scope.AiQueryScope;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 单指标在执行层可用的已解析上下文：部门列表（含 scope_strategy）+ 时间窗。
 * <p>后续 Provider / query* 逐步只消费此对象而非各自猜口径。</p>
 */
@Value
@Builder
public class MetricExecutionContext {

    MetricDefinition metric;
    AiQueryScope sessionScope;
    /** 登录用户锚点部门（gb_DU_department_id），可为 null 则退回会话父部门 */
    Integer userAnchorDepartmentId;
    List<Integer> effectiveDepartmentIds;
    MetricTimeWindows.Range timeRange;
}
