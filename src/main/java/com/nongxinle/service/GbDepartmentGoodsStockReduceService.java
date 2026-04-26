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
     * 与 {@link #queryReduceCostSubtotal} 条件一致，汇总 reduce 行重量
     */
    Double queryReduceWeightSum(Map<String, Object> map);

    /**
     * 老接口兼容：按「生产」类型汇总 reduce 小计（等价于 {@link #queryReduceCostSubtotal} 且 {@code type=1}）。
     * <p>若 Map 中含 {@code equalType} 而无 {@code type}，实现会先映射为 {@code type} 再查询（与 {@link #queryReduceTypeCount} 一致）。
     */
    Double queryReduceProduceTotal(Map<String, Object> map);

    Double queryReduceProduceWeightTotal(Map<String, Object> map);

    Double queryReduceLossTotal(Map<String, Object> map);

    Double queryReduceLossWeightTotal(Map<String, Object> map);

    Double queryReduceWasteTotal(Map<String, Object> map);

    Double queryReduceWasteWeightTotal(Map<String, Object> map);

    Double queryReduceReturnTotal(Map<String, Object> map);

    Double queryReduceReturnWeightTotal(Map<String, Object> map);

    /**
     * 与 {@link #queryReduceTypeCount} 条件一致，按类型汇总 reduce 重量（单日或区间）。
     * 返回键：produceWeight、lossWeight、wasteWeight、returnWeight。
     */
    Map<String, Object> queryReduceTypeWeightTotalsByScope(Map<String, Object> map);

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

    Integer queryReduceDistinctGoodsCount(Map<String, Object> map);

    List<GbDistributerGoodsEntity> queryGoodsCostGoodsPageByReduce(Map<String, Object> map);

    /**
     * 成本分页列表：聚合行 + 按部门嵌套的 reduce 明细及库存/采购信息（供小程序展开）。
     */
    List<GbDistributerGoodsEntity> queryGoodsCostGoodsPageWithDetails(Map<String, Object> map);

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

    /**
     * 生产成本扣减（仅 type=1）按商品汇总（重量、金额）；仅生产线领料口径时使用。
     */
    List<Map<String, Object>> queryProductionReduceAggByDisGoods(Map<String, Object> map);

    /**
     * 生产 + 损耗 + 损失（type 1、2、3）按商品汇总（重量、金额），供菜品成本/毛利分析使用。
     */
    List<Map<String, Object>> queryProduceLossWasteReduceAggByDisGoods(Map<String, Object> map);

    /**
     * 指定 type（1/2/3）按商品汇总的出库重量、金额，条件与 {@link #queryProductionReduceAggByDisGoods} 相同。
     */
    List<Map<String, Object>> queryReduceAggByDisGoodsByType(Map<String, Object> map, Integer stockReduceType);

}
