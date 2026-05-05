package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDepartmentEntity;
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
     * 与 {@link #queryReduceCostSubtotal} 条件一致，汇总 {@code gb_dgsr_weight}
     */
    Double queryReduceWeightSum(@Param("params") Map<String, Object> map);

    /**
     * 与 {@link #queryReduceTypeCount} 条件一致，按 gb_dgsr_type 汇总重量（produce/loss/waste/return）。
     * 返回键：produceWeight、lossWeight、wasteWeight、returnWeight。
     */
    Map<String, Object> queryReduceTypeWeightTotalsByScope(@Param("params") Map<String, Object> map);

    /**
     * 根据 type 查询 subtotal 总和 (1=produce, 2=waste, 3=loss, 4=return)
     */
    Double queryReduceByTypeTotal(@Param("params") Map<String, Object> map);

    /**
     * 查询所有类型的 subtotal 总和
     */
    Map<String, Object> queryReduceAllTypesTotal(@Param("params") Map<String, Object> map);

    /**
     * 同 {@link #queryReduceAllTypesTotal}，但仅统计 {@code gb_dgsr_date} 在 gb_ai_daily_revenue 中已有上传记录的日期（同部门 {@code matchDailyRevenueDepartmentId}）。
     */
    Map<String, Object> queryReduceAllTypesTotalOnDailyRevenueDays(@Param("params") Map<String, Object> map);

    /**
     * 按 subtotal 查询 Top 商品 (根据 type 过滤)
     */
    List<GbDistributerGoodsEntity> queryStockSubtotalTopTimes(@Param("params") Map<String, Object> map);

    /**
     * 按日查询支出
     */
    List<Map<String, Object>> queryGbPurchaseGoodsTopDay(@Param("params") Map<String, Object> map);

    /**
     * 去重商品种数（分页 totalCount）
     */
    Integer queryReduceDistinctGoodsCount(@Param("params") Map<String, Object> map);

    /**
     * 成本统计分页：按商品聚合 reduce 的 produce / waste / loss 小计
     */
    List<GbDistributerGoodsEntity> queryGoodsCostGoodsPageByReduce(@Param("params") Map<String, Object> map);

    List<GbDepartmentGoodsStockReduceEntity> queryReduceCostDetailRows(@Param("params") Map<String, Object> map);

    /**
     * 与 {@link #queryReduceTypeCount} 条件一致，返回存在核销行的部门（type in 1,2,3），去重。
     */
    List<GbDepartmentEntity> queryReduceDepartment(@Param("params") Map<String, Object> map);

    /**
     * 与 {@link #queryReduceTypeCount} 条件一致，列出核销明细（type in 1,2,3,4），按日/区间与商品、部门筛选。
     */
    List<GbDepartmentGoodsStockReduceEntity> queryStockReduceListByParams(@Param("params") Map<String, Object> map);

    /**
     * 生产成本（type=1）按 {@code gb_dgsr_gb_dis_goods_id} 汇总重量与金额，条件与成本统计 reduce 查询一致。
     */
    List<Map<String, Object>> queryProductionReduceAggByDisGoods(@Param("params") Map<String, Object> map);

    /**
     * 生产 + 损耗 + 损失（type in 1,2,3）按 {@code gb_dgsr_gb_dis_goods_id} 汇总重量与金额；与 {@link #queryProductionReduceAggByDisGoods} 条件相同，仅类型范围不同。
     */
    List<Map<String, Object>> queryProduceLossWasteReduceAggByDisGoods(@Param("params") Map<String, Object> map);

    /**
     * 指定 type（1/2/3）按商品汇总 weightSum、subtotalSum；与 {@link #queryProductionReduceAggByDisGoods} 条件与分组一致，{@code params.reduceType} 为类型。
     */
    List<Map<String, Object>> queryReduceAggByDisGoodsByType(@Param("params") Map<String, Object> map);

}
