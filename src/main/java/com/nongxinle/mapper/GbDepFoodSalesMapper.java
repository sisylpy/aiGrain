package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GbDepFoodSalesMapper extends BaseMapper<GbDepFoodSalesEntity> {

    /**
     * 按批发商菜品 id（gb_dep_food.gb_df_food_id）统计部门菜品销售行数。
     */
    long countSalesByDistributerFoodId(@Param("distributerFoodId") Integer distributerFoodId);
}
