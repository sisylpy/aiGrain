package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 出库/核销计划可见门店切片（C-21）；不含用户聊天原文。 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class StockReducePlannerVisibleStore {

    private Long departmentId;
    private String displayLabel;
}
