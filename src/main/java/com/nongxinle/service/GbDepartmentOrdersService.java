package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;

import java.util.List;
import java.util.Map;

/**
 * 部门订单Service接口
 */
public interface GbDepartmentOrdersService extends IService<GbDepartmentOrdersEntity> {

    Integer queryGbDepartmentOrderAmount(Map<String, Object> map);

    /**
     * 获取部门父级AI申请订单
     * @param depId 部门ID
     * @return 订单列表
     */
    List<GbDepartmentOrdersEntity> queryApplyAiFatherOrders(Integer depId);

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
     * 更新订单（与老项目兼容）
     */
    void update(GbDepartmentOrdersEntity gbDepartmentOrders);

    /**
     * 根据参数查询订单列表
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
     * 根据ID查询订单（老项目兼容方法）
     * @param orderId 订单ID
     * @return 订单实体
     */
    default GbDepartmentOrdersEntity queryObject(Integer orderId) {
        return getById(orderId);
    }

    /**
     * 退货流程：按库存扣减记录 ID 查找关联的部门订单（gb_do_dgsr_return_id）。
     */
    GbDepartmentOrdersEntity queryReturnOrderByReduceId(Integer reduceId);

    Integer countDepGoodsReorderCandidates(Map<String, Object> map);

    List<Integer> selectDepGoodsReorderCandidatesPage(Map<String, Object> map);

    List<Integer> selectDepGoodsIdsSingleOrderInWindow(Map<String, Object> map);

}
