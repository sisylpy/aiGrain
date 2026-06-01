package com.nongxinle.ai.scope;

import cn.hutool.core.util.StrUtil;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.resolver.SemanticStoreNarrowingScopeSupport;

/**
 * AI 对话统计范围：与前台约定一致。
 * <ul>
 *   <li>{@link #STORE}：传入门店父部门 ID，统计该节点子树内全部部门。</li>
 *   <li>{@link #GROUP}：传入批发商/集团 disId，统计该 dis 下挂载的全部部门。</li>
 * </ul>
 */
public enum AiConversationScopeMode {
    STORE(0),
    GROUP(1);

    private final int code;

    AiConversationScopeMode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static AiConversationScopeMode fromCode(Integer code) {
        if (code == null) {
            return STORE;
        }
        for (AiConversationScopeMode m : values()) {
            if (m.code == code) {
                return m;
            }
        }
        return STORE;
    }

    public static AiConversationScopeMode fromApiString(String raw) {
        if (StrUtil.isBlank(raw)) {
            return STORE;
        }
        String s = raw.trim();
        if (s.equalsIgnoreCase("GROUP") || s.equals("1")) {
            return GROUP;
        }
        return STORE;
    }

    /**
     * Run 链路 SSOT：请求 {@code scopeMode} 优先，其次会话持久化值，再按 departmentId / distributerId 推断。
     */
    public static AiConversationScopeMode inferForRun(AiRunCreateRequest req, Integer conversationScopeModeCode) {
        if (req != null && StrUtil.isNotBlank(req.getScopeMode())) {
            return fromApiString(req.getScopeMode());
        }
        if (conversationScopeModeCode != null) {
            return fromCode(conversationScopeModeCode);
        }
        if (req != null && req.getDepartmentId() != null) {
            return STORE;
        }
        if (req != null && req.getDistributerId() != null) {
            return GROUP;
        }
        return STORE;
    }

    /**
     * 是否按 distributer 广角枚举门店：显式 GROUP 请求 / 已解析 GROUP org / 会话 GROUP；
     * {@code null} 会话模式时保持旧行为（集团角色仍可广角枚举门店）。
     */
    public static boolean enumeratesDistributerStores(AiResolvedQueryContext rq) {
        if (rq == null) {
            return true;
        }
        if (SemanticStoreNarrowingScopeSupport.isSemanticStoreNarrowingActive(rq)) {
            return false;
        }
        if (rq.getConversationScopeMode() == GROUP) {
            return true;
        }
        if (rq.getOrgScope() != null
                && com.nongxinle.ai.context.AiResolvedOrgScope.SCOPE_GROUP.equals(
                        rq.getOrgScope().getScopeType())) {
            return true;
        }
        com.nongxinle.ai.context.ScopeResolutionTrace trace = rq.getScopeResolutionTrace();
        if (trace != null && "true".equalsIgnoreCase(trace.getExplicitGroupRequest())) {
            return true;
        }
        if (rq.getConversationScopeMode() == null) {
            return true;
        }
        return false;
    }
}
