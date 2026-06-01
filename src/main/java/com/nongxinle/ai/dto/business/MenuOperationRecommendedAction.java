package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** MenuOperation 结构化建议动作；Composer 只宣读，不得现场推断。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuOperationRecommendedAction {

    public static final String KEEP_AND_PROMOTE = "KEEP_AND_PROMOTE";
    public static final String RAISE_PRICE = "RAISE_PRICE";
    public static final String REDUCE_COST = "REDUCE_COST";
    public static final String CONSIDER_DROP = "CONSIDER_DROP";
    public static final String RECIPE_REVIEW = "RECIPE_REVIEW";
    public static final String CHECK_STOCK_REDUCE = "CHECK_STOCK_REDUCE";

    private String actionCode;
    private int priority;
    @Builder.Default
    private List<String> targetDishIds = new ArrayList<>();
    private String rationaleKey;
    @Builder.Default
    private List<String> evidenceRefIds = new ArrayList<>();
}
