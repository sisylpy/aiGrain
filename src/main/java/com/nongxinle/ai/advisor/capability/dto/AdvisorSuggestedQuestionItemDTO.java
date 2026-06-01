package com.nongxinle.ai.advisor.capability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvisorSuggestedQuestionItemDTO {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMING_SOON = "COMING_SOON";

    /** 与 {@link #questionCode} 相同，供前端统一字段名 */
    private String questionId;
    private String questionCode;
    private String text;
    private Long workflowId;
    private String workflowCode;
    /** 是否参与查询/可见 */
    private boolean enabled;
    private String status;
    /**
     * 是否可点击填入输入框：{@code enabled && ACTIVE}。
     * COMING_SOON 可见但 clickable=false。
     */
    private boolean clickable;
    private int sort;
    private String scene;
    /** debug：不参与 run 路由 */
    private String intentHint;
    /** debug：不参与 run 路由 */
    private String contractHint;
}
