package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 采购计划可见门店切片（C-16）；不含用户聊天原文。 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PurchasePlannerVisibleStore {

    private Long departmentId;
    private String displayLabel;
}
