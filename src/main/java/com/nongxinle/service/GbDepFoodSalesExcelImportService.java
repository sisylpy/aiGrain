package com.nongxinle.service;

import com.nongxinle.entity.GbDepartmentEntity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 部门菜品日销售 Excel 导入：写入菜品销售并按配方展开为原料消耗。
 */
public interface GbDepFoodSalesExcelImportService {

    Map<String, Object> importFoodSales(
            Integer departmentId,
            Integer distributerId,
            GbDepartmentEntity department,
            Set<Integer> allowedFoodIds,
            List<Map.Entry<Date, Map<Integer, BigDecimal>>> cellQuantities);
}
