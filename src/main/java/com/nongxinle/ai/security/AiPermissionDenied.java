package com.nongxinle.ai.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * error 帧内 {@code data.permissionDenied} 的结构化载荷（不抛 HTTP 异常）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPermissionDenied {

    @Builder.Default
    private boolean allowed = false;

    private String reason;
    private String suggestedScope;
    private String requiredPermission;
    /** 可选：被拒的 Tool / Agent 名称，便于排障 */
    private String subject;

    public Map<String, Object> asDataMap() {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("allowed", allowed);
        if (reason != null) {
            m.put("reason", reason);
        }
        if (suggestedScope != null) {
            m.put("suggestedScope", suggestedScope);
        }
        if (requiredPermission != null) {
            m.put("requiredPermission", requiredPermission);
        }
        if (subject != null) {
            m.put("subject", subject);
        }
        return m;
    }
}
