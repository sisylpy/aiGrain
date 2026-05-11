package com.nongxinle.ai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 过渡期的粗粒度组织权限快照（角色 + 挂靠部门锚点），由 {@link AiOrgScopeResolver} 从 {@link AiUserContext}
 * 生成，仍挂在 {@link com.nongxinle.ai.core.AiRunState#getAiOrgScope()} 上供旧链路兼容。
 * <p>
 * <b>新业务 Agent / Tool</b> 请只读 {@link com.nongxinle.ai.context.AiResolvedQueryContext#getOrgScope()}
 * 中的 {@link AiResolvedOrgScope}，不要把本类当作首选入口，避免与 {@link AiResolvedOrgScope} 双轨并行扩展。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOrgScope {

    /** GROUP / REGION / STORE / DEPARTMENT / DISTRIBUTER */
    private String scopeType;

    private Long groupId;
    private Long regionId;
    private Long storeId;
    private Long departmentId;
    private Long distributerId;

    @Builder.Default
    private List<Long> storeIds = new ArrayList<>();
}
