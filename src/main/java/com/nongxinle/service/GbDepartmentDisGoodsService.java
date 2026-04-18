package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 部门商品关联Service接口
 */
public interface GbDepartmentDisGoodsService extends IService<GbDepartmentDisGoodsEntity> {

    /**
     * 获取部门商品分类
     */
    List<GbDistributerFatherGoodsEntity> disGetDepDisGoodsCataGb(Map<String, Object> map);

    /**
     * 获取部门商品ID列表
     */
    List<Integer> queryOnlyDepGoodsIds(Map<String, Object> map);

    /**
     * 获取部门商品总数
     */
    int queryDepGoodsCount(Map<String, Object> mapC);

    /**
     * 分页查询部门商品（AI用）
     */
    List<GbDepartmentDisGoodsEntity> depQueryDepGoodsWithOrderForAi(Map<String, Object> map);

    /**
     * 根据参数查询部门商品
     */
    List<GbDepartmentDisGoodsEntity> queryGbDepDisGoodsByParams(Map<String, Object> map);

    /**
     * 查询分销商商品ID列表
     */
    List<Integer> queryOnlyDisGoodsIds(Map<String, Object> map);

    /**
     * 根据参数查询部门商品（AI用）
     */
    GbDepartmentDisGoodsEntity queryDepartmentGoodsForAi(Map<String, Object> map);

    /**
     * 按批发商分页查询商品数量
     */
    int queryDisGoodsCount(Map<String, Object> map);

    /**
     * 按批发商分页查询商品
     */
    List<GbDepartmentDisGoodsEntity> disQueryDisGoodsWithOrderForAi(Map<String, Object> map);

    /**
     * 按批发商分页查询商品（TreeSet版本）
     */
    TreeSet<GbDistributerGoodsEntity> disQueryDisGoodsWithOrderForAiTree(Map<String, Object> map);

    /**
     * 农鑫导入批发商商品后，为部门订货（Jj）创建并保存部门商品（部门侧与批发商商品的关联行）。
     * <p>字段赋值与老项目在 Controller 中的逻辑一致。
     *
     * @param gbDepartmentOrders 订货申请（含部门、订货规格、展示名称等）
     * @param gbNewGoods         已落库的批发商商品
     * @return 已保存的部门商品实体（含生成的主键）
     */
    GbDepartmentDisGoodsEntity createDepDisGoodsForJjOrderAfterNxImport(
            GbDepartmentOrdersEntity gbDepartmentOrders,
            GbDistributerGoodsEntity gbNewGoods);

    /**
     * 已有批发商商品时，为部门订货（Jj）创建并保存部门商品关联行。
     * <p>名称、拼音等取自批发商商品；部门取自订货申请。
     */
    GbDepartmentDisGoodsEntity createDepDisGoodsForJjOrderFromExistingDisGoods(
            GbDepartmentOrdersEntity gbDepartmentOrders,
            GbDistributerGoodsEntity gbDistributerGoodsEntity);
}
