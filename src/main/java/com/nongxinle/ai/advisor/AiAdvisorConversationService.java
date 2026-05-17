package com.nongxinle.ai.advisor;

import com.nongxinle.ai.history.dto.AiAdvisorConversationBootstrapDTO;

/**
 * 顾问页「长期会话线程」初始化：幂等会话 + {@code gb_ai_message} 历史。
 */
public interface AiAdvisorConversationService {

    /**
     * 校验顾问启用；若无则插入顾问会话线程；装载消息。
     *
     * @param scopeMode 可选；未传则按 departmentId / distributerId 推断 STORE / GROUP
     */
    AiAdvisorConversationBootstrapDTO getOrBootstrap(
            Long advisorId,
            Long userId,
            Long departmentId,
            Long distributerId,
            String scopeMode);
}
