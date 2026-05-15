package com.nongxinle.ai.context;

import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.service.GbDepartmentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 装配 {@link AiUserContext}：从 {@code gb_department_user} 按主键读取 {@code gb_du_admin}，
 * 经 {@link AiRoleMapper} 映射为可读 {@code roleCode}。
 */
@Component
@RequiredArgsConstructor
public class AiUserContextResolver {

    private final GbDepartmentUserService departmentUserService;
    private final GbDepartmentMapper gbDepartmentMapper;

    public AiUserContext resolve(AiRunCreateRequest req) {
        if (req == null || req.getUserId() == null) {
            throw new IllegalArgumentException("AiRunCreateRequest.userId required for AiUserContext");
        }

        long uidLong = req.getUserId();
        if (uidLong > Integer.MAX_VALUE || uidLong < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("userId out of gb_department_user PK range: " + uidLong);
        }
        int uidPk = (int) uidLong;
        GbDepartmentUserEntity row = departmentUserService.getById(uidPk);
        if (row == null) {
            throw new IllegalArgumentException(
                    "gb_department_user not found for userId=" + uidLong + " (AI Run 必须从该表读取 admin)。");
        }
        Integer admin = row.getGbDuAdmin();
        if (admin == null) {
            throw new IllegalArgumentException("gb_du_admin is null for gb_department_user id=" + uidPk);
        }
        AiRoleMapper.AiRoleDefinition def = AiRoleMapper.requireAdmin(admin);
        return buildFromGbRow(row, def);
    }

    private AiUserContext buildFromGbRow(GbDepartmentUserEntity row, AiRoleMapper.AiRoleDefinition def) {
        Integer pk = row.getGbDepartmentUserId();
        Long uid = pk != null ? pk.longValue() : null;
        Long dept = row.getGbDuDepartmentId() != null ? row.getGbDuDepartmentId().longValue() : null;
        Long dis = row.getGbDuDistributerId() != null ? row.getGbDuDistributerId().longValue() : null;
        Integer father = row.getGbDuDepartmentFatherId();

        Long storeRoot = normalizeToStoreRootDepartmentId(dept);
        List<Long> allowed = new ArrayList<>();
        if (storeRoot != null && !AiRoleMapper.isGroupWideOrgScope(def.roleCode())) {
            allowed.add(storeRoot);
        }

        Long regionHint = inferRegionAnchored(def.roleCode(), dept);

        return AiUserContext.builder()
                .userId(uid)
                .sourceAdminRole(def.sourceAdminValue())
                .roleCode(def.roleCode())
                .roleName(def.roleNameChinese())
                .departmentFatherId(father)
                .departmentId(dept)
                .storeId(storeRoot != null ? storeRoot : dept)
                .distributerId(dis)
                .regionId(regionHint)
                .groupId(null)
                .allowedStoreIds(allowed)
                .permissions(new ArrayList<>(AiRoleMapper.permissionsForAiRole(def.roleCode())))
                .build();
    }

    /**
     * 沿 {@code gb_department_father_id} 一直追溯到 {@code father_id = 0} 的门店根；数据缺失时退回原 id，
     * 避免将挂靠子部门直接当作门店根。
     */
    private Long normalizeToStoreRootDepartmentId(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        Long current = departmentId;
        for (int i = 0; i < 32 && current != null; i++) {
            if (current > Integer.MAX_VALUE || current < Integer.MIN_VALUE) {
                return departmentId;
            }
            GbDepartmentEntity dep = gbDepartmentMapper.selectById(current.intValue());
            if (dep == null) {
                return departmentId;
            }
            Integer father = dep.getGbDepartmentFatherId();
            if (father == null || father == 0) {
                Integer self = dep.getGbDepartmentId();
                return self != null ? self.longValue() : current;
            }
            current = father.longValue();
        }
        return departmentId;
    }

    private static Long inferRegionAnchored(String roleCode, Long deptId) {
        if (deptId == null) {
            return null;
        }
        return switch (roleCode) {
            case AiRoleCodes.REGION_MANAGER, AiRoleCodes.REGION_PURCHASER, AiRoleCodes.REGION_WAREHOUSE -> deptId;
            default -> null;
        };
    }
}
