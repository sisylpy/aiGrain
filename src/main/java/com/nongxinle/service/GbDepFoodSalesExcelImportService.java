package com.nongxinle.service;

import com.nongxinle.entity.GbDepartmentEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    /**
     * 从上传的 Excel 完整处理：校验文件、部门、解析表格并调用 {@link #importFoodSales}。
     * 返回 Map 含 {@code rows} 及 importFoodSales 的统计字段。
     */
    Map<String, Object> importFoodSalesFromExcelMultipart(MultipartFile file, Integer departmentId, Integer distributerId)
            throws IOException;
}
