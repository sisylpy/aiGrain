package com.nongxinle.ai.scope;

import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 根据 {@link GbDepartmentUserEntity#getGbDuAdmin()} 与用户挂靠部门，解析「权限子树」的根部门 ID。
 * <p>
 * 结果仅用于当次请求中与会话 {@link AiQueryScope} 求交，不写入会话表或其它持久化存储。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiDepartmentUserExpansionResolver {

    private static final int IS_PARENT_STORE_FLAG = 1;

    private final GbDepartmentMapper departmentMapper;

    /**
     * @return 子树展开的根节点；若挂靠部门为空则返回 null
     */
    public ResolvedExpansion resolve(AiUserOrgContext user) {
        if (user == null || user.getDepartmentId() == null) {
            return new ResolvedExpansion(null, AiUserScopeExpansionKind.ANCHOR_SUBTREE);
        }
        int anchor = user.getDepartmentId();
        Integer admin = user.getAdminFlag();

        if (matches(admin, GbConstants.DepartmentUserRole.STORE_MANAGER_APP)) {
            Integer storeRoot = walkToParentStoreRoot(anchor);
            log.debug("[AI-USER-EXPANSION] STORE_MANAGER anchor={} storeRoot={}", anchor, storeRoot);
            return new ResolvedExpansion(storeRoot != null ? storeRoot : anchor, AiUserScopeExpansionKind.PARENT_STORE_SUBTREE);
        }
        if (matches(admin, GbConstants.DepartmentUserRole.REGION_MANAGER_APP)
                || matches(admin, GbConstants.DepartmentUserRole.REGION_PURCHASER_APP)
                || matches(admin, GbConstants.DepartmentUserRole.REGION_WAREHOUSE_APP)) {
            Integer regionRoot = walkToRegionRoot(anchor);
            log.debug("[AI-USER-EXPANSION] REGION_ROLE anchor={} regionRoot={}", anchor, regionRoot);
            return new ResolvedExpansion(regionRoot != null ? regionRoot : anchor, AiUserScopeExpansionKind.REGION_SUBTREE);
        }

        // 集团管理/集采、门店订货/采购/库房/其它：默认以挂靠部门为根的子树
        return new ResolvedExpansion(anchor, AiUserScopeExpansionKind.ANCHOR_SUBTREE);
    }

    private static boolean matches(Integer admin, Integer role) {
        return admin != null && role != null && Objects.equals(admin, role);
    }

    /**
     * 从 anchor 沿 father 上溯，找到第一个父级门店（{@code gb_department_is_group_dep = 1}）。
     */
    private Integer walkToParentStoreRoot(int anchorDepartmentId) {
        Integer cur = anchorDepartmentId;
        int guard = 0;
        final int maxHops = 64;
        while (cur != null && cur > 0 && guard++ < maxHops) {
            GbDepartmentEntity e = departmentMapper.selectById(cur);
            if (e == null) {
                break;
            }
            if (IS_PARENT_STORE_FLAG == zeroAsAbsent(e.getGbDepartmentIsGroupDep())) {
                return cur;
            }
            Integer father = e.getGbDepartmentFatherId();
            if (father == null || father == 0) {
                break;
            }
            cur = father;
        }
        return null;
    }

    /**
     * 从 anchor 沿 father 上溯，找到第一个片区根（{@link GbConstants.DepartmentType#REGION}）。
     */
    private Integer walkToRegionRoot(int anchorDepartmentId) {
        Integer cur = anchorDepartmentId;
        int guard = 0;
        final int maxHops = 64;
        while (cur != null && cur > 0 && guard++ < maxHops) {
            GbDepartmentEntity e = departmentMapper.selectById(cur);
            if (e == null) {
                break;
            }
            if (Objects.equals(e.getGbDepartmentType(), GbConstants.DepartmentType.REGION)) {
                return cur;
            }
            Integer father = e.getGbDepartmentFatherId();
            if (father == null || father == 0) {
                break;
            }
            cur = father;
        }
        return null;
    }

    private static int zeroAsAbsent(Integer v) {
        return v != null ? v : 0;
    }

    public record ResolvedExpansion(Integer expansionRootDepartmentId, AiUserScopeExpansionKind kind) {
    }
}
