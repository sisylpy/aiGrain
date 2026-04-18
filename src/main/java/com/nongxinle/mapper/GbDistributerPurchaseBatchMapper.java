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

    List<GbDistributerPurchaseBatchEntity> queryDisPurchaseBatchInfo(@Param("map") Map<String, Object> map);

    Integer queryDisPurchaseBatchCount(@Param("map") Map<String, Object> map);

    Double querySupplierUnSettleSubtotal(@Param("map") Map<String, Object> map);


    List<GbDistributerPurchaseBatchEntity> queryDisPurchaseBatchListWithOrders(@Param("map") Map<String, Object> map);
}
