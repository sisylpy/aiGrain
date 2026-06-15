package com.nongxinle.ai.workrecord;

import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.service.GbDepartmentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class WorkRecordScopeGuard {

    private final GbDepartmentUserService departmentUserService;
    private final GbDepartmentMapper departmentMapper;

    public ResolvedScope resolveAndValidate(Long userId, Long departmentId, Long distributerId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (userId > Integer.MAX_VALUE || userId < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("userId out of range: " + userId);
        }
        GbDepartmentUserEntity user = departmentUserService.getById(userId.intValue());
        if (user == null) {
            throw new IllegalArgumentException("user not found: " + userId);
        }
        if (user.getGbDuAdmin() == null) {
            throw new IllegalArgumentException("gb_du_admin is null for userId=" + userId);
        }
        AiRoleMapper.AiRoleDefinition role = AiRoleMapper.requireAdmin(user.getGbDuAdmin());

        Long userDis = user.getGbDuDistributerId() != null ? user.getGbDuDistributerId().longValue() : null;
        Long userDept = user.getGbDuDepartmentId() != null ? user.getGbDuDepartmentId().longValue() : null;
        Long storeRoot = normalizeToStoreRootDepartmentId(userDept);

        Long effDepartmentId = departmentId != null ? departmentId : storeRoot;
        if (effDepartmentId == null) {
            throw new IllegalArgumentException("departmentId required");
        }
        Long storeAnchor = normalizeToStoreRootDepartmentId(effDepartmentId);

        Long effDistributerId = distributerId;
        if (effDistributerId == null) {
            GbDepartmentEntity dept = departmentMapper.selectById(storeAnchor.intValue());
            if (dept != null && dept.getGbDepartmentDisId() != null) {
                effDistributerId = dept.getGbDepartmentDisId().longValue();
            }
        }
        if (effDistributerId == null && userDis != null) {
            effDistributerId = userDis;
        }
        if (effDistributerId == null) {
            throw new IllegalArgumentException("distributerId required");
        }

        if (!AiRoleMapper.isGroupWideOrgScope(role.roleCode())) {
            if (userDis != null && !Objects.equals(userDis, effDistributerId)) {
                throw new IllegalArgumentException("distributerId not allowed for current user");
            }
        }

        String storeName = null;
        GbDepartmentEntity storeDept = departmentMapper.selectById(storeAnchor.intValue());
        if (storeDept != null) {
            storeName = storeDept.getGbDepartmentName();
        }

        return new ResolvedScope(userId, storeAnchor, effDistributerId, storeName, role.roleCode());
    }

    private Long normalizeToStoreRootDepartmentId(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        Long current = departmentId;
        for (int i = 0; i < 32 && current != null; i++) {
            if (current > Integer.MAX_VALUE || current < Integer.MIN_VALUE) {
                return departmentId;
            }
            GbDepartmentEntity dep = departmentMapper.selectById(current.intValue());
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

    public record ResolvedScope(
            Long userId,
            Long departmentId,
            Long distributerId,
            String storeName,
            String roleCode) {
    }
}
