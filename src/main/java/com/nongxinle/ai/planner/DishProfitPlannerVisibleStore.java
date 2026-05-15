package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 菜品毛利计划可见门店切片（C-26）；不含用户聊天原文。 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DishProfitPlannerVisibleStore {

    private Long departmentId;
    private String displayLabel;
}
