package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 批发商采购批次Mapper接口
 */
@Mapper
public interface GbDistributerPurchaseBatchMapper extends BaseMapper<GbDistributerPurchaseBatchEntity> {

    GbDistributerPurchaseBatchEntity queryBatchWithOrders(@Param("batchId") Integer batchId);

    List<GbDistributerPurchaseBatchEntity> queryDisPurchaseBatch(@Param("map") Map<String, Object> map);

    Integer queryDisPurchaseBatchCount(@Param("map") Map<String, Object> map);

    Double querySupplierUnSettleSubtotal(@Param("map") Map<String, Object> map);

    /**
     * 根据批次ID查询采购商品（包含商品名称和部门订单）
     */
    List<GbDistributerPurchaseGoodsEntity> queryPurchaseGoodsByBatchId(@Param("batchId") Integer batchId);

    /**
     * 根据采购商品ID查询部门订单
     */
    List<com.nongxinle.entity.GbDepartmentOrdersEntity> queryDepartmentOrdersByPurchaseGoodsId(@Param("purchaseGoodsId") Integer purchaseGoodsId);

    /**
     * 根据ID查询采购人员
     */
    com.nongxinle.entity.GbDepartmentUserEntity queryPurUserById(@Param("purUserId") Integer purUserId);

    /**
     * 根据ID查询卖方
     */
    com.nongxinle.entity.NxJrdhUserEntity querySellerById(@Param("sellUserId") Integer sellUserId);

    /**
     * 根据ID查询供货商
     */
    com.nongxinle.entity.NxJrdhSupplierEntity querySupplierById(@Param("supplierId") Integer supplierId);

    /**
     * 查询采购批次详细信息（老项目兼容方法）
     */
    List<GbDistributerPurchaseBatchEntity> queryDisPurchaseBatchInfo(@Param("map") Map<String, Object> map);

}
