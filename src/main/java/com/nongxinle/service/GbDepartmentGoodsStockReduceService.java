package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;

import java.util.List;
import java.util.Map;

/**
 * 部门商品库存减少Service接口
 */
public interface GbDepartmentGoodsStockReduceService extends IService<GbDepartmentGoodsStockReduceEntity> {

    /**
     * 根据类型查询记录数量
     */
    Integer queryReduceTypeCount(Map<String, Object> map);

    /**
     * 查询成本小计
     */
    Double queryReduceCostSubtotal(Map<String, Object> map);

    /**
     * 根据 type 查询 subtotal 总和
     */
    Double queryReduceByTypeTotal(Map<String, Object> map);

    /**
     * 查询所有类型的 subtotal 总和
     */
    Map<String, Object> queryReduceAllTypesTotal(Map<String, Object> map);

    /**
     * 按 subtotal 查询 Top 商品
     */
    List<GbDistributerGoodsEntity> queryStockSubtotalTopTimes(Map<String, Object> map);

    /**
     * 按日查询支出
     */
    List<Map<String, Object>> queryGbPurchaseGoodsTopDay(Map<String, Object> map);

    /**
     * 根据ID查询记录（老项目兼容方法）
     * @param id 记录ID
     * @return 实体
     */
    default GbDepartmentGoodsStockReduceEntity queryObject(Integer id) {
        return getById(id);
    }

    /**
     * 更新记录（老项目兼容方法）
     * @param entity 实体
     * @return 是否成功
     */
    default boolean update(GbDepartmentGoodsStockReduceEntity entity) {
        return updateById(entity);
    }

}
