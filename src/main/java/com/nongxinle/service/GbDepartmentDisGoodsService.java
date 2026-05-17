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
     * 按批发商分页查询商品（顺序为分类 sort → 商品 sort，与 {@code queryOnlyDisGoodsIds} 一致）
     */
    List<GbDistributerGoodsEntity> disQueryDisGoodsWithOrderForAiTree(Map<String, Object> map);

    /**
     * 按部门分页查询商品（从部门商品表查询，返回分销商商品实体）
     */
    List<GbDistributerGoodsEntity> disQueryDepGoodsWithOrderForAiTree(Map<String, Object> map);

    /**
     * 部门已关联批发商商品的快速检索（老项目 queryDepDisGoodsQuickSearchStrGb，按部门商品主键去重排序）
     */
    TreeSet<GbDepartmentDisGoodsEntity> queryDepDisGoodsQuickSearchStrGb(Map<String, Object> map);

    /**
     * 为部门订货（Jj）创建并保存部门商品（部门与批发商商品的关联行）。
     * <p>适用于：农鑫导入后新建批发商商品、或选用已有批发商商品两种场景。
     * <p>部门侧商品名称取自批发商商品；拼音/首字母优先用商品表已有值，否则按该名称生成；
     * 大类 ID 优先用商品上的 greatId，为空时按 grand 分类解析。
     *
     * @param gbDepartmentOrders 订货申请（部门、订货规格等）
     * @param gbDisGoods         已落库的批发商商品
     * @return 已保存的部门商品实体（含生成的主键）
     */
    GbDepartmentDisGoodsEntity createDepDisGoodsForJjOrder(
            GbDepartmentOrdersEntity gbDepartmentOrders,
            GbDistributerGoodsEntity gbDisGoods);
}
