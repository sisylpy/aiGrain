package com.nongxinle.ai.scope;

import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.mapper.GbDepartmentUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 根据 {@code gb_department_user_id} 组装 {@link AiUserOrgContext}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiUserOrgContextLoader {

    private final GbDepartmentUserMapper departmentUserMapper;
    private final GbDepartmentMapper departmentMapper;

    public Optional<AiUserOrgContext> load(Long departmentUserId) {
        if (departmentUserId == null) {
            return Optional.empty();
        }
        if (departmentUserId > Integer.MAX_VALUE || departmentUserId < Integer.MIN_VALUE) {
            log.warn("[AI-USER-CTX] departmentUserId out of int range: {}", departmentUserId);
            return Optional.empty();
        }
        GbDepartmentUserEntity u = departmentUserMapper.selectById(departmentUserId.intValue());
        if (u == null) {
            return Optional.empty();
        }
        Integer depId = u.getGbDuDepartmentId();
        Integer depType = null;
        if (depId != null) {
            GbDepartmentEntity dep = departmentMapper.selectById(depId);
            if (dep != null) {
                depType = dep.getGbDepartmentType();
            }
        }
        return Optional.of(AiUserOrgContext.builder()
                .departmentUserId(departmentUserId)
                .departmentId(depId)
                .departmentFatherId(u.getGbDuDepartmentFatherId())
                .distributerId(u.getGbDuDistributerId())
                .departmentType(depType)
                .adminFlag(u.getGbDuAdmin())
                .build());
    }
}
