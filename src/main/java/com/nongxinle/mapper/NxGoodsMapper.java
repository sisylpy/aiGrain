package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.NxGoodsEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 农鑫商品Mapper接口
 */
@Mapper
public interface NxGoodsMapper extends BaseMapper<NxGoodsEntity> {

    /**
     * 老项目迁移: 查询单个商品（包含父子级联）
     * 使用自定义ResultMap填充fatherGoods、grandGoods、greatGrandGoods
     */
    NxGoodsEntity queryObject(Integer nxGoodsId);

    /**
     * 获取商品分类列表
     */
    List<NxGoodsEntity> getNxGoodsCateList();

    /**
     * 查询商品ID列表
     */
    List<Integer> queryOnlyGoodsIds(Map<String, Object> map);

    /**
     * 根据一级分类(greatGrandId)查询商品数量
     * 原方法名: queryNxGoodsCountByFatherId（已废弃）
     */
    int queryNxGoodsCountByGreatGrandId(Map<String, Object> map);

    /**
     * 根据一级分类(greatGrandId)分页查询商品
     * 原方法名: queryNxGoodsPageByFatherId（已废弃）
     */
    List<NxGoodsEntity> queryNxGoodsPageByGreatGrandId(Map<String, Object> map);

    /**
     * 根据一级分类(greatGrandId)查询商品，包含批发商商品、部门商品、订单、库存等完整信息
     * 原方法名: queryGbDepNxGrandGoodsByGreatId（来自老项目 NxGoodsDao.xml）
     */
    List<NxGoodsEntity> queryGbDepNxGrandGoodsByGreatId(Map<String, Object> map);

    /**
     * 农鑫商品精确匹配（名称 / 别名 / 拼音 / 首字母）+ 批发商商品 + 部门商品 + 部门订单
     */
    List<NxGoodsEntity> queryDisGoodsEqualSearchStrWithDepOrders(Map<String, Object> map);

    /**
     * 农鑫商品名称 / 别名模糊 + 同上关联
     */
    List<NxGoodsEntity> queryDisGoodsQuickSearchStrWithDepOrders(Map<String, Object> map);

    /**
     * 农鑫商品拼音 / 首字母模糊 + 同上关联
     */
    List<NxGoodsEntity> queryDisGoodsQuickSearchPyWithDepOrders(Map<String, Object> map);
}
