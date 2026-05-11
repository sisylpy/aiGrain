package com.nongxinle.ai.followup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话追问：上一轮成功的「经营工作台」语义快照，仅存 DISH_PROFIT / BUSINESS_OVERVIEW / WAREHOUSE_STOCK /
 * COST_INSIGHT / PURCHASE_COST。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiFollowUpIntentSnapshot {

    public static final int VERSION = 1;

    /** 对齐 JSON 契约 */
    private int v = VERSION;

    /**
     * 上一轮 DataPlanner 已识别且跑通后的「有效问句」文本（与用户原始输入对齐的 normalized），
     * 追问时仅替换其中首个时间用语。
     */
    private String effectiveQuestion;

    private FollowUpPathKind pathKind;

}
