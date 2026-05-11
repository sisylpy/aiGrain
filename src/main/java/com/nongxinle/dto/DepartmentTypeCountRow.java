package com.nongxinle.dto;

import lombok.Data;

/**
 * {@link com.nongxinle.mapper.GbDepartmentMapper#countDepartmentTypesByIds} 单行结果。
 */
@Data
public class DepartmentTypeCountRow {
    private Integer deptType;
    private Integer cnt;
}
