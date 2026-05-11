package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import com.nongxinle.dto.DepartmentPurchaseAggRow;
import com.nongxinle.dto.PurchaseMethodLegacyAggRow;
import com.nongxinle.dto.PurchaseTypeAggRow;

import java.util.List;
import java.util.Map;

/**
 * 批发商采购商品Service接口
 */
public interface GbDistributerPurchaseGoodsService extends IService<GbDistributerPurchaseGoodsEntity> {

    // 老项目的 save 方法，使用 default 委托给 IService 的 save
    default boolean save(GbDistributerPurchaseGoodsEntity entity) {
        // 调用 IService 的 save 方法
        return IService.super.save(entity);
    }

    List<GbDistributerPurchaseGoodsEntity> querySimplePurGoods(Map<String, Object> map);

    GbDistributerPurchaseGoodsEntity queryPurchaseGoodsLastItem(Map<String, Object> map);

    List<NxJrdhSupplierEntity> queryDisPurGoodsSupplierList(Map<String, Object> map);

    /**
     * 按采购表条件统计条数（日期字段为 {@code gb_DPG_purchase_date}）。
     * <p>参数为<strong>平铺</strong> {@code Map}，与 {@link #queryGbPurchaseGoodsCount(Map)} 风格一致，常用键：
     * {@code disId}、{@code dayuStatus}、{@code status}、{@code equalStatus}、{@code purchaseType}、
     * {@code date}、{@code startDate}、{@code stopDate}、{@code equalInputType}、{@code batchId}（1 表示 batch_id&gt;0，-1 表示 null）、
     * {@code purUserId}、{@code purDepId}、{@code supplierBuy}/{@code supplierId}、{@code typeNotEqual}。
     */
    Integer queryPurchaseGoodsCount(Map<String, Object> map);

    /**
     * 按采购表条件汇总 {@code gb_DPG_buy_subtotal}。
     * <p>默认按 {@code gb_DPG_purchase_date} 过滤起止；若传入 {@code useStockFinishDate=true}，则按 {@code gb_DPG_stock_finish_date}。
     * <p>可选用：{@code purUserId}、{@code purDepId}、{@code supplierBuy}/{@code supplierId}（见 XML 片段）、{@code typeNotEqual}、{@code purchaseType}、{@code dayuStatus}。
     */
    Double queryPurchaseGoodsSubTotal(Map<String, Object> map);

    Integer queryGbPurchaseGoodsCount(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount} 同筛选（含 batch join），汇总 {@code gb_DPG_buy_subtotal}。
     */
    Double queryGbPurchaseGoodsBuySubtotalSum(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount} 同条件，按 {@code gb_DPG_purchase_type} 分行数与金额。
     */
    List<PurchaseTypeAggRow> queryGbPurchaseGoodsAggByPurchaseType(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount} 同条件；按旧版供货属性口径聚合（type=5；type=1 按 nx 拆桶后再对用户合并为供货商/自采）。
     */
    List<PurchaseMethodLegacyAggRow> queryGbPurchaseGoodsAggByLegacyPurchaseMethod(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount} 同条件；按商品名+标准名合并后采购频次 Top。
     */
    List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopTimesMerged(Map<String, Object> map);

    /**
     * 同上合并规则，采购金额 Top。
     */
    List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopSubtotalMerged(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount} 条件一致，汇总 {@code gb_DPG_buy_quantity}。
     */
    Double queryPurchaseGoodsWeightTotal(Map<String, Object> map);

    /** 同上条件，加权平均采购单价 ∑小计÷∑数量；无采购或重量为 0 则为 0。 */
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

    Integer queryGbDisGoodsTreeCount(Map<String, Object> queryMap);

    List<GbDistributerGoodsEntity> queryDisTreeGoodsWithPurList(Map<String, Object> queryMap);

    List<GbDistributerGoodsEntity> queryPurchaseGoodsFatherTypeByParams(Map<String, Object> queryMap);

    List<GbDistributerPurchaseGoodsEntity> queryPurchaseGoodsWithDetailByParams(Map<String, Object> map);

    List<GbDistributerPurchaseGoodsEntity> queryPurchaseGoodsWithOrdersByBatch(Map<String, Object> map);

    List<GbDistributerPurchaseGoodsEntity> queryOnlyPurGoods(Map<String, Object> map);

    /**
     * 采购入库完成后，根据关联订单批量写入部门商品库存（GbDepartmentGoodsStock）。
     * <p>含部门商品上次订货价与本次价差（GbDoPriceDifferent）的内存赋值，行为与原 Controller 私有方法一致。
     * <p>整段写入在同一事务中；部门分销商品与批发商商品会预加载，避免逐条 getById。
     *
     * @param ordersEntityList 部门订单列表（通常按采购商品 ID 查询得到）；null 或空列表则直接返回
     * @param purGoodsId       批发商采购商品 ID，不可为 null
     * @throws IllegalArgumentException {@code purGoodsId} 为空
     * @throws IllegalStateException 采购单、部门分销商品或批发商商品在库中不存在，或订单缺少必要外键
     */
    void saveDepartmentStockEntriesByPurchase(List<GbDepartmentOrdersEntity> ordersEntityList, Integer purGoodsId);

    /**
     * 按批发商商品的最高/最低限价校验采购单价，并写入 {@code gbDpgBuyPriceReason}（偏高/偏低/正常）。
     * <p>与原先 Controller 私有方法逻辑一致。
     *
     * @param purchaseGoodsEntity 采购商品（需含采购价、采购数量、批发商商品 ID）
     * @return 同一实体引用（已设置价格原因）
     */
    GbDistributerPurchaseGoodsEntity annotatePurchaseGoodsPriceReason(GbDistributerPurchaseGoodsEntity purchaseGoodsEntity);

    /**
     * 为「按采购员/供货商 + 树」返回的商品列表填充 {@code wastePurGoodsEntities}（每条采购含库存行及部门名称）。
     * <p>{@code queryMap} 与 {@link #queryDisTreeGoodsWithPurList(Map)} 使用同一套筛选键；实现内会汇总商品 ID 并去掉分页键后查询。
     *
     * @param goodsList 树查询得到的商品列表（按 {@code gbDistributerGoodsId} 分组填充）
     * @param queryMap  与树查询相同的筛选条件
     */
    void fillWastePurGoodsForDisTreeGoods(List<GbDistributerGoodsEntity> goodsList, Map<String, Object> queryMap);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount(Map)} 条件一致，按 {@code gb_DPG_dis_goods_id} 汇总采购入库数量。
     */
    List<Map<String, Object>> queryPurchaseBuyQtyAggByDisGoods(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount(Map)} 同一筛选与 join，按入库采购部门汇总金额。
     */
    List<DepartmentPurchaseAggRow> sumPurchaseSubtotalGroupedByPurDepartmentId(Map<String, Object> map);

    /**
     * 与 {@link #queryGbPurchaseGoodsCount} 条件一致，按供货商汇总采购金额 Top10。
     */
    List<Map<String, Object>> queryGbPurchaseSupplierSpendTop(Map<String, Object> map);

}
