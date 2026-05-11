package com.nongxinle.ai.context;

import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.security.AiRoleCodes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 由用户角色 + {@code gb_department_user} 锚点生成过渡用 {@link AiOrgScope}（旧越权拦截等）。
 * 新业务流程请以 {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} 产出的
 * {@link AiResolvedOrgScope} 为准，勿再扩展本解析器的职责。
 */
@Component
public class AiOrgScopeResolver {

    public static final String SCOPE_GROUP = "GROUP";
    public static final String SCOPE_REGION = "REGION";
    public static final String SCOPE_DEPARTMENT = "DEPARTMENT";
    public static final String SCOPE_STORE = "STORE";
    public static final String SCOPE_DISTRIBUTER = "DISTRIBUTER";

    public AiOrgScope resolve(AiUserContext ctx, AiRunCreateRequest req) {
        if (ctx == null) {
            return AiOrgScope.builder().scopeType(SCOPE_GROUP).build();
        }
        Long reqDept = req != null ? req.getDepartmentId() : null;
        Long reqDis = req != null ? req.getDistributerId() : null;
        Long dis = reqDis != null ? reqDis : ctx.getDistributerId();

        return switch (ctx.getRoleCode()) {
            case AiRoleCodes.GROUP_MANAGER -> AiOrgScope.builder()
                    .scopeType(SCOPE_GROUP)
                    .groupId(ctx.getGroupId())
                    .departmentId(reqDept != null ? reqDept : ctx.getDepartmentId())
                    .distributerId(dis)
                    .storeIds(copyIds(ctx.getAllowedStoreIds()))
                    .build();
            case AiRoleCodes.REGION_MANAGER,
                 AiRoleCodes.REGION_PURCHASER,
                 AiRoleCodes.REGION_WAREHOUSE -> AiOrgScope.builder()
                    .scopeType(SCOPE_REGION)
                    .regionId(ctx.getRegionId())
                    .departmentId(ctx.getDepartmentId())
                    .distributerId(dis)
                    .storeIds(copyIds(ctx.getAllowedStoreIds()))
                    .build();
            case AiRoleCodes.STORE_MANAGER,
                 AiRoleCodes.STORE_PURCHASER,
                 AiRoleCodes.STORE_ORDER,
                 AiRoleCodes.WINDOW_ORDER -> AiOrgScope.builder()
                    .scopeType(reqDept != null ? SCOPE_STORE : SCOPE_DEPARTMENT)
                    .storeId(ctx.getStoreId())
                    .departmentId(ctx.getDepartmentId())
                    .distributerId(dis)
                    .storeIds(copyIds(ctx.getAllowedStoreIds()))
                    .build();
            case AiRoleCodes.GROUP_PURCHASER -> AiOrgScope.builder()
                    .scopeType(SCOPE_DEPARTMENT)
                    .departmentId(ctx.getDepartmentId())
                    .distributerId(dis)
                    .storeIds(copyIds(ctx.getAllowedStoreIds()))
                    .build();
            case AiRoleCodes.WAREHOUSE_MANAGER,
                 AiRoleCodes.WAREHOUSE_PURCHASER,
                 AiRoleCodes.CENTRAL_KITCHEN_MANAGER,
                 AiRoleCodes.CENTRAL_KITCHEN_PURCHASER -> AiOrgScope.builder()
                    .scopeType(SCOPE_DEPARTMENT)
                    .departmentId(ctx.getDepartmentId())
                    .distributerId(dis)
                    .storeIds(copyIds(ctx.getAllowedStoreIds()))
                    .build();
            case AiRoleCodes.COUPON_OPERATOR -> AiOrgScope.builder()
                    .scopeType(SCOPE_DEPARTMENT)
                    .departmentId(ctx.getDepartmentId())
                    .distributerId(dis)
                    .storeIds(copyIds(ctx.getAllowedStoreIds()))
                    .build();
            case AiRoleCodes.DELIVERY_SUPPLIER,
                 AiRoleCodes.DELIVERY_DRIVER -> AiOrgScope.builder()
                    .scopeType(SCOPE_DISTRIBUTER)
                    .departmentId(ctx.getDepartmentId())
                    .distributerId(dis != null ? dis : ctx.getDistributerId())
                    .storeIds(copyIds(ctx.getAllowedStoreIds()))
                    .build();
            default -> AiOrgScope.builder()
                    .scopeType(SCOPE_DEPARTMENT)
                    .departmentId(ctx.getDepartmentId())
                    .distributerId(dis)
                    .storeIds(copyIds(ctx.getAllowedStoreIds()))
                    .build();
        };
    }

    private static List<Long> copyIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(ids);
    }
}
