package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 部门商品库存减少Mapper接口
 */
@Mapper
public interface GbDepartmentGoodsStockReduceMapper extends BaseMapper<GbDepartmentGoodsStockReduceEntity> {

    /**
     * 根据类型查询记录数量
     */
    Integer queryReduceTypeCount(@Param("params") Map<String, Object> map);

    /**
     * 查询成本小计
     */
    Double queryReduceCostSubtotal(@Param("params") Map<String, Object> map);

    /**
     * 根据 type 查询 subtotal 总和 (1=produce, 2=waste, 3=loss, 4=return)
     */
    Double queryReduceByTypeTotal(@Param("params") Map<String, Object> map);

    /**
     * 查询所有类型的 subtotal 总和
     */
    Map<String, Object> queryReduceAllTypesTotal(@Param("params") Map<String, Object> map);

    /**
     * 按 subtotal 查询 Top 商品 (根据 type 过滤)
     */
    List<GbDistributerGoodsEntity> queryStockSubtotalTopTimes(@Param("params") Map<String, Object> map);

    /**
     * 按日查询支出
     */
    List<Map<String, Object>> queryGbPurchaseGoodsTopDay(@Param("params") Map<String, Object> map);

}
