package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解析层可见门店切片（C-8）；不含用户聊天原文。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RevenuePlannerVisibleStore {

    private Long departmentId;
    /** 可选展示名，仅供结构化输出 / trace，非路由依据。 */
    private String displayLabel;
}
