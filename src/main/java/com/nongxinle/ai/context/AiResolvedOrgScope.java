package com.nongxinle.ai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 本轮 Run <b>最终</b>的业务组织范围（门店根列表、库房锚点、收窄后的可见门店等），是 Agent / Tool 的<b>首选</b>组织入口。
 * 表示已结合请求部门、角色规则与子部门归一后的解析结果；{@link AiResolvedQueryContext#getDataScope()} 中的查库部门列表由本对象推导。
 * <p>
 * 规则见 {@code docs/DOMAIN_ORG_MODEL.md} 与 {@code docs/AI_AGENT_DEVELOPMENT_GUIDE.md}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResolvedOrgScope {

    public static final String SCOPE_GROUP = "GROUP";
    public static final String SCOPE_REGION = "REGION";
    public static final String SCOPE_STORE = "STORE";
    public static final String SCOPE_PURCHASER = "PURCHASER";
    public static final String SCOPE_WAREHOUSE = "WAREHOUSE";
    public static final String SCOPE_DEPARTMENT = "DEPARTMENT";
    public static final String SCOPE_USER = "USER";

    private String scopeType;

    private Long distributerId;
    /** 请求体或会话声明的部门；可能与登录部门一致 */
    private Long requestDepartmentId;

    /** 归一化后的门店根部门 ID（子部门时为其父部门中的门店根） */
    private Long currentStoreDepartmentId;
    private Long currentDepartmentId;

    @Builder.Default
    private List<AiStoreScopeDTO> visibleStores = new ArrayList<>();
    @Builder.Default
    private List<AiDepartmentScopeDTO> visibleWarehouses = new ArrayList<>();
    @Builder.Default
    private List<AiDepartmentScopeDTO> visibleDepartments = new ArrayList<>();

    private String scopeName;
    private String queryScopeBanner;
    private String coverageDetail;
}
