package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.NxGoodsEntity;

import java.util.List;
import java.util.Map;

public interface NxGoodsService extends IService<NxGoodsEntity> {

    /**
     * 查询单个商品（与老项目兼容）
     * 使用自定义SQL填充fatherGoods、grandGoods、greatGrandGoods关联对象
     */
    NxGoodsEntity queryObject(Integer nxGoodsId);

    /**
     * 获取商品分类列表
     */
    List<NxGoodsEntity> getiBookCoverData();

    /**
     * 查询商品ID列表
     */
    List<Integer> queryOnlyGoodsIds(Map<String, Object> map);

    /**
     * 根据一级分类查询商品数量
     */
    int queryNxGoodsCountByGreatGrandId(Map<String, Object> map);

    /**
     * 根据一级分类分页查询商品
     */
    List<NxGoodsEntity> queryNxGoodsPageByGreatGrandId(Map<String, Object> map);

    /**
     * 根据一级分类查询商品，包含批发商商品、部门商品、订单、库存等完整信息
     */
    List<NxGoodsEntity> queryGbDepNxGrandGoodsByGreatId(Map<String, Object> map);
}
