package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.dto.DepartmentPurchaseAggRow;
import com.nongxinle.dto.PurchaseMethodLegacyAggRow;
import com.nongxinle.dto.PurchaseTypeAggRow;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 批发商采购商品Mapper接口
 */
@Mapper
public interface GbDistributerPurchaseGoodsMapper extends BaseMapper<GbDistributerPurchaseGoodsEntity> {

    List<GbDistributerPurchaseGoodsEntity> querySimplePurGoods(Map<String, Object> map);

    GbDistributerPurchaseGoodsEntity queryPurchaseGoodsLastItem(Map<String, Object> map);

    List<NxJrdhSupplierEntity> queryDisPurGoodsSupplierList(Map<String, Object> map);

    Integer queryPurchaseGoodsCount(Map<String, Object> map);

    Double queryPurchaseGoodsSubTotal(Map<String, Object> map);

    Integer queryGbPurchaseGoodsCount(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount} 同条件（含 batch join），汇总 {@code gb_DPG_buy_subtotal}。
     */
    Double queryGbPurchaseGoodsBuySubtotalSum(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount} 同条件，{@code gb_DPG_purchase_type} 分组统计行数与金额。
     */
    List<PurchaseTypeAggRow> queryGbPurchaseGoodsAggByPurchaseType(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount} 同条件；采购方式按旧版「供货属性摘要」（{@code GbAiChatServiceImpl#appendPurchaseSupplyMixSummary}）：
     * type=5；type=1 且 nx 正整数归入供货商侧；type=1 且 nx 空/-1 为自采；其余为其它。
     */
    List<PurchaseMethodLegacyAggRow> queryGbPurchaseGoodsAggByLegacyPurchaseMethod(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount} 同条件；按商品名称+标准名合并集团内多 {@code dis_goods_id} 后按采购频次 Top。
     */
    List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopTimesMerged(Map<String, Object> map);

    /**
     * 同上合并规则，按采购金额 Top。
     */
    List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopSubtotalMerged(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount} 条件一致，汇总采购重量（{@code gb_DPG_buy_quantity}）。
     */
    Double queryPurchaseGoodsWeightTotal(Map<String, Object> map);

    /** 加权平均单价：∑{@code gb_DPG_buy_subtotal} / ∑{@code gb_DPG_buy_quantity}，条件同 {@link #queryGbPurchaseGoodsCount} */
    Double queryPurchaseGoodsWeightedAvgBuyPrice(Map<String, Object> map);

    String queryPurGoodsMaxPrice(Map<String, Object> map);

    String queryPurGoodsMinPrice(Map<String, Object> map);

    String queryPurchaseGoodsPrice(Map<String, Object> map);

    String queryPurchaseGoodsWeight(Map<String, Object> map);

    Integer queryGbGoodsCount(Map<String, Object> map);

    List<GbDepartmentUserEntity> queryPurUserList(Map<String, Object> map);

    List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopTimes(Map<String, Object> map);

    List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopSubtotal(Map<String, Object> map);

    Double queryGbPurchaseSubtotalTopSubtotal(Map<String, Object> map);

    List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopPriceFluctuation(Map<String, Object> map);

    Integer queryGbDisGoodsTreeCount(Map<String, Object> map);

    List<GbDistributerGoodsEntity> queryDisTreeGoodsWithPurList(Map<String, Object> map);

    List<GbDistributerGoodsEntity> queryPurchaseGoodsFatherTypeByParams(Map<String, Object> map);

    List<GbDistributerPurchaseGoodsEntity> queryPurchaseGoodsWithDetailByParams(Map<String, Object> map);

    /** 按 batchId + status 查采购商品 + 部门订单（供货商称重等），非小程序库存明细。 */
    List<GbDistributerPurchaseGoodsEntity> queryPurchaseGoodsWithOrdersByBatch(Map<String, Object> map);

    List<GbDistributerPurchaseGoodsEntity> queryOnlyPurGoods(Map<String, Object> map);

    /**
     * 按与树形列表相同的筛选条件，批量查询多个商品的采购单及入库库存（用于 wastePurGoodsEntities）。
     * <p>Map 须含 {@code disGoodsIds}（List&lt;Integer&gt;），以及 {@code disId}、日期、{@code dayuStatus}、
     * {@code typeNotEqual}、{@code supplierBuy}、{@code purUserId} 或 {@code supplierId} 等与树查询一致的条件。
     */
    List<GbDistributerPurchaseGoodsEntity> queryPurchaseGoodsWithStocksDetailForGoodsIds(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount} 条件一致，按 {@code gb_DPG_dis_goods_id} 汇总入库采购数量。
     */
    List<Map<String, Object>> queryPurchaseBuyQtyAggByDisGoods(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount} 条件一致，按入库采购部门聚合 {@code gb_DPG_buy_subtotal}。
     * 须与同类查询一致：单 {@link Map} 参数且<strong>不要</strong>加 {@code @Param("map")}，否则动态 SQL 无法解析 {@code disId} / {@code purDepId} 等键。
     */
    List<DepartmentPurchaseAggRow> sumPurchaseSubtotalGroupedByPurDepartmentId(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount} 条件一致，按供货商汇总采购入库金额 Top10。
     */
    List<Map<String, Object>> queryGbPurchaseSupplierSpendTop(Map<String, Object> map);

}
