package com.nongxinle.ai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 部门 / 库房等在解析结果中的轻量 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDepartmentScopeDTO {

    private Long departmentId;
    private String departmentName;
    /** {@code gb_department.gb_department_father_id} */
    private Long fatherId;
}
