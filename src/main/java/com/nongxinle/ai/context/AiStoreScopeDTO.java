package com.nongxinle.ai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 门店根（{@code gb_department.gb_department_father_id = 0}）在解析结果中的轻量 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStoreScopeDTO {

    private Long storeDepartmentId;
    private String storeName;
}
