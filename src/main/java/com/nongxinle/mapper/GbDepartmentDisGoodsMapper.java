package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 部门商品关联Mapper接口
 */
@Mapper
public interface GbDepartmentDisGoodsMapper extends BaseMapper<GbDepartmentDisGoodsEntity> {

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
    java.util.TreeSet<com.nongxinle.entity.GbDistributerGoodsEntity> disQueryDisGoodsWithOrderForAiTree(Map<String, Object> map);
}
