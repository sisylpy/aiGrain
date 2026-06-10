package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDepartmentEntity;
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
     * 与 {@link #queryReduceTypeCount} 相同过滤条件，返回当日（或区间）内有生产/废弃/损耗核销的部门列表。
     */
    List<GbDepartmentEntity> queryReduceDepartment(Map<String, Object> map);

    /**
     * 与统计口径一致的核销明细列表（含退货 type=4），用于替代原 gb_department_goods_daily + 日报关联查询。
     */
    List<GbDepartmentGoodsStockReduceEntity> queryStockReduceListByParams(Map<String, Object> map);

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

    Double queryReduceEmployeeMealTotal(Map<String, Object> map);

    Double queryReduceEmployeeMealWeightTotal(Map<String, Object> map);

    /**
     * 与 {@link #queryReduceTypeCount} 条件一致，按类型汇总 reduce 重量（单日或区间）。
     * 返回键：produceWeight、lossWeight、wasteWeight、returnWeight、employeeMealWeight。
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
     * 多门店零售父部门汇总：与 Mapper {@code queryReduceAllTypesTotalForRetailDepartmentFathers} 一致，
     * {@code params.departmentFatherIds} + {@code params.disId} + 日期区间。
     */
    Map<String, Object> queryReduceAllTypesTotalForRetailDepartmentFathers(Map<String, Object> map);

    /**
     * 同 {@link #queryReduceAllTypesTotal}，仅计入「该日已有日营业额」的核销日期。
     */
    Map<String, Object> queryReduceAllTypesTotalOnDailyRevenueDays(Map<String, Object> map);

    /**
     * 按 subtotal 查询 Top 商品
     */
    List<GbDistributerGoodsEntity> queryStockSubtotalTopTimes(Map<String, Object> map);

    /**
     * 按出库/核销次数 Top 商品（与 {@link #queryReduceAllTypesTotalForRetailDepartmentFathers} 同口径范围）。
     */
    List<Map<String, Object>> queryStockOutboundTimesTopForRetailFathers(Map<String, Object> map);

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
     * 为出库明细回填库存批次、采购行，并计算每条出库前的批次剩余量（{@code purchaseBatchInfo}）。
     *
     * @param unit 展示单位（如商品规格名），可空
     */
    void enrichReducesWithStockAndPurchaseBatch(List<GbDepartmentGoodsStockReduceEntity> rows, String unit);

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
     * 同 {@link #queryProduceLossWasteReduceAggByDisGoods}，且仅计入已有日营业额上传的自然日出库。
     */
    List<Map<String, Object>> queryProduceLossWasteReduceAggByDisGoodsOnDailyRevenueDays(Map<String, Object> map);

    /** type1+2+3 按商品 + 出库日汇总，供配料核销按销售宽限期拆分。 */
    List<Map<String, Object>> queryProduceLossWasteReduceAggByDisGoodsAndDate(Map<String, Object> map);

    /**
     * 指定 type（1/2/3/4/6 等）按商品汇总的出库重量、金额，条件与 {@link #queryProductionReduceAggByDisGoods} 相同。
     */
    List<Map<String, Object>> queryReduceAggByDisGoodsByType(Map<String, Object> map, Integer stockReduceType);

    /**
     * 员工餐（type=6）按商品汇总（重量、金额）；等价于 {@link #queryReduceAggByDisGoodsByType} 且 type={@link com.nongxinle.utils.GbConstants.StockReduceType#EMPLOYEE_MEAL}。
     */
    default List<Map<String, Object>> queryEmployeeMealReduceAggByDisGoods(Map<String, Object> map) {
        return queryReduceAggByDisGoodsByType(map, com.nongxinle.utils.GbConstants.StockReduceType.EMPLOYEE_MEAL);
    }

}
