package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 部门订单Mapper接口
 */
@Mapper
public interface GbDepartmentOrdersMapper extends BaseMapper<GbDepartmentOrdersEntity> {

    Integer queryGbDepartmentOrderAmount(Map<String, Object> map);

    /**
     * 指定分销商商品与时间范围内，已有订单的到货日期（去重、升序）。
     */
    List<String> queryDisGoodsDistinctArriveDates(Map<String, Object> map);

    /**
     * 获取部门父级AI申请订单
     * @param depId 部门ID
     * @return 订单列表
     */
    List<GbDepartmentOrdersEntity> queryApplyAiFatherOrders(@Param("depId") Integer depId);

    /**
     * 查询父级商品订单（用于AI申请）
     * @param map 查询参数
     * @return 父级商品列表
     */
    List<GbDistributerFatherGoodsEntity> queryGrandGoodsOrder(Map<String, Object> map);

    /**
     * 根据参数查询订单列表
     * @param map 查询参数
     * @return 订单列表
     */
    List<GbDepartmentOrdersEntity> queryDisOrdersListByParams(Map<String, Object> map);

    /**
     * 根据参数查询订单列表（用于入库）
     */
    List<GbDepartmentOrdersEntity> queryDisOrdersByParams(Map<String, Object> map);

    /**
     * 根据采购商品ID查询订单重量总和
     * @param purGoodsId 采购商品ID
     * @return 重量总和
     */
    Double queryOrderWeightTotalByPurGoodsId(Integer purGoodsId);

    /**
     * 根据参数查询订单小计总和
     * @param map 查询参数
     * @return 小计总和
     */
    Double queryGbOrdersSubtotal(Map<String, Object> map);

    /**
     * 统计在时间窗口内订货次数达到阈值的不同部门商品（gb_do_dep_dis_goods_id）数量。
     */
    Integer countDepGoodsReorderCandidates(Map<String, Object> map);

    /**
     * 分页返回候选部门商品 id（按订货次数降序）。
     */
    List<Integer> selectDepGoodsReorderCandidatesPage(Map<String, Object> map);

    /**
     * 窗口内恰好 1 笔已收货到货单的部门商品 id（按到货次数聚合）。
     */
    List<Integer> selectDepGoodsIdsSingleOrderInWindow(Map<String, Object> map);

}
