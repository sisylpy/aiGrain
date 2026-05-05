package com.nongxinle.ai.scope;

import lombok.Builder;
import lombok.Value;

/**
 * 登录订货用户（gb_department_user）+ 挂靠部门类型，用于 AI 统计范围与数据权限收窄。
 */
@Value
@Builder
public class AiUserOrgContext {

    long departmentUserId;
    /** {@code gb_department_user.gb_DU_department_id} */
    Integer departmentId;
    /** 冗余父部门，同用户表 */
    Integer departmentFatherId;
    /** {@code gb_department_user.gb_DU_distributer_id} */
    Integer distributerId;
    /** {@code gb_department.gb_department_type}，与 {@link com.nongxinle.utils.GbConstants.DepartmentType} 对齐 */
    Integer departmentType;
    /** {@code gb_department_user.gb_DU_admin}，非空且非 0 视为管理员 */
    Integer adminFlag;

    public boolean isDepartmentAdmin() {
        return adminFlag != null && adminFlag != 0;
    }
}
