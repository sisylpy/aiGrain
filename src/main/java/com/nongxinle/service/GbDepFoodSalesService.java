package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDepFoodSalesEntity;

public interface GbDepFoodSalesService extends IService<GbDepFoodSalesEntity> {

    /** 按批发商菜品 id 统计关联的部门菜品销售行数（见 Mapper JOIN gb_dep_food）。 */
    long countSalesByDistributerFoodId(Integer distributerFoodId);
}
