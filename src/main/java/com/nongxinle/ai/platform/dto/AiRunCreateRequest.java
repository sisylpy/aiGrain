package com.nongxinle.ai.platform.dto;

import lombok.Data;

@Data
public class AiRunCreateRequest {

    /**
     * 可选。首轮可不传，由服务端插入 {@code gb_ai_conversation} 后返回；
     * 后续轮次必须带回同一 id（与 {@code gb_ai_conversation.gb_ai_conversation_id} 一致），勿由前端自行编造。
     */
    private Long conversationId;
    /**
     * 可选。单店默认 {@link com.nongxinle.ai.scope.AiConversationScopeMode#STORE}（需 {@code departmentId}）；
     * 集团 {@code GROUP}（需 {@code distributerId}）。未传时按 {@code departmentId} / {@code distributerId} 推断。
     */
    private String scopeMode;
    /** 对齐现有前端：必填 */
    private Long userId;

    private Long departmentId;
    private Long distributerId;

    /**
     * 可选。正式环境以 {@code gb_department_user.gb_du_admin} 经 {@link com.nongxinle.ai.mapping.AiRoleMapper}
     * 推导 {@code roleCode}；仅过渡期保留合成角色 {@code FINANCE_MANAGER} / {@code MARKETING_MANAGER}（单测等）。
     * 详见 {@code docs/PERMISSION_MODEL.md}。
     */
    private String roleCode;

    private String message;
}
