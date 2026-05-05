package com.nongxinle.ai.scope;

import cn.hutool.core.util.StrUtil;

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
}
