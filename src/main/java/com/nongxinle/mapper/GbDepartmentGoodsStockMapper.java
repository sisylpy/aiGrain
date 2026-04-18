package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockSimpleEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 部门商品库存Mapper接口
 */
@Mapper
public interface GbDepartmentGoodsStockMapper extends BaseMapper<GbDepartmentGoodsStockEntity> {

    /**
     * 根据参数查询部门库存
     */
    List<GbDepartmentGoodsStockEntity> queryGoodsStockByParams(Map<String, Object> map);

    /**
     * 门店时段统计用：与树查询相同条件的一次性库存列表
     */
    List<GbDepartmentGoodsStockEntity> queryGoodsStockListForMendianPeriod(Map<String, Object> map);

    /**
     * 查询库存商品数量
     */
    Integer queryGoodsStockCount(Map<String, Object> map);

    /**
     * 查询部门商品剩余总量
     */
    Double queryDepGoodsRestTotal(Map<String, Object> map);

    /**
     * 与 {@link #queryDepGoodsRestTotal} 条件一致，汇总剩余重量 {@code gb_dgs_rest_weight}
     */
    Double queryDepGoodsRestWeightTotal(Map<String, Object> map);

    /**
     * 查询部门库存树形父商品
     */
    List<GbDistributerFatherGoodsEntity> queryDepStockTreeFatherGoodsByParams(Map<String, Object> map);

    /**
     * 根据参数查询分销商商品库存
     */
    List<GbDistributerGoodsEntity> queryDisGoodsStockByParams(Map<String, Object> map);

    /**
     * 查询部门商品损耗总量
     */
    Double queryDepGoodsWasteTotal(Map<String, Object> map);

    /**
     * 查询分销库存商品数量
     */
    Integer queryDisStockGoodsCount(Map<String, Object> map);

    /**
     * 根据参数查询部门库存（简化版，一次性查询所有关联数据）
     */
    List<GbDepartmentGoodsStockSimpleEntity> queryGoodsStockSimpleByParams(Map<String, Object> map);

}
